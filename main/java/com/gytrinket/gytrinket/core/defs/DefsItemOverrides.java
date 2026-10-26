package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.core.defs.DefsTypes.ParamValue;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ShieldTypeOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinition;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.gytrinket;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定义运行时覆盖的<b>编辑写回</b>（P5-5c 从 DefsManager 拆出）。
 * <p>
 * 所有 UI 编辑操作都写统一 items 覆盖（{@code SERVER_ITEM_OVERRIDES}）+ 覆盖文件，
 * 然后立即 {@link #applyOverrides}（重读覆盖 → 重载定义 → 触发 Config/子系统刷新）。
 * 旧分段结构（specialMechanics/shieldTypes）在读取时升级为 items 统一结构，写入只写 items 段。
 */
public final class DefsItemOverrides {

    private DefsItemOverrides() {}

    /** 编辑操作：更新特殊机制声明（写入统一 items 覆写 + 覆盖文件，立即重新加载生效，不重载数据包） */
    public static void updateSpecialMechanicOverride(MinecraftServer server, String itemId, boolean removed) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        // 机制编辑仅改机制声明（removed=true 精确撤销机制 = mechanic:false，不误伤护盾定义）；其余字段保留既有
        ItemDefinition def = new ItemDefinition(removed ? Boolean.FALSE : Boolean.TRUE, false,
                removed ? null : List.of(),
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                base != null ? base.values() : null,
                base != null ? base.attributes() : null);
        DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, def);
        DefsManager.ITEM_UI_OWNED.add(itemId);
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }
    /** 拷贝物品既有的机制数值覆盖（统一 items 覆写优先，旧段覆盖回退；内层 map 深拷贝） */
    static Map<String, Map<String, ParamValue>> copyExistingValues(String itemId) {
        Map<String, Map<String, ParamValue>> values = new LinkedHashMap<>();
        ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && item.values() != null) {
            for (var e : item.values().entrySet()) {
                values.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            return values;
        }
        SpecialMechanicOverride existing = DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (existing != null && !existing.removed()) {
            for (var e : existing.values().entrySet()) {
                values.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
        }
        return values;
    }

    /**
     * 物品当前生效的合并 ItemDefinition（编辑 API 的保留字段基线，过渡期转译查询）：
     * 优先 DefsManager.SERVER_ITEM_OVERRIDES 条目；否则从 DefsManager.SPECIAL_MECHANIC_SETS 数据驱动 + 旧段覆写 + 生效护盾类型合成。
     */
    static ItemDefinition getEffectiveDefinition(MinecraftServer server, String itemId) {
        ItemDefinition own = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (own != null) {
            return own;
        }
        Boolean mechanic = null;
        List<String> sets = null;
        Map<String, Map<String, ParamValue>> values = null;
        List<String> declaredSets = DefsManager.SPECIAL_MECHANIC_SETS.get(itemId);
        if (declaredSets != null) {
            mechanic = Boolean.TRUE;
            sets = declaredSets;
        }
        SpecialMechanicOverride smOv = DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (smOv != null) {
            if (smOv.removed()) {
                mechanic = Boolean.FALSE;
                sets = null;
            } else {
                mechanic = Boolean.TRUE;
                sets = smOv.sets();
                values = smOv.values().isEmpty() ? null : smOv.values();
            }
        }
        List<String> shieldTypes = null;
        Map<String, ParamValue> shieldValues = null;
        ShieldTypeOverride stOv = DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (stOv != null) {
            shieldTypes = stOv.types();
            if (!stOv.values().isEmpty()) {
                // 旧段 Double 结构拍平为统一 ParamValue 结构（护盾物品均单类型，无键冲突）
                shieldValues = new LinkedHashMap<>();
                for (var typeEntry : stOv.values().entrySet()) {
                    for (var pvEntry : typeEntry.getValue().entrySet()) {
                        shieldValues.put(pvEntry.getKey(), ParamValue.of(pvEntry.getValue()));
                    }
                }
            }
        } else {
            List<String> declared = DefsManager.ITEM_SHIELD_TYPES.get(itemId);
            if (declared != null) {
                shieldTypes = declared;
            }
        }
        if (mechanic == null && sets == null && values == null && shieldTypes == null && shieldValues == null) {
            return null;
        }
        return new ItemDefinition(mechanic, false, sets, shieldTypes, shieldValues, values, null);
    }

    /**
     * 编辑操作：为物品添加/移除指定特殊机制（set）。
     * 添加 = 当前集合 ∪ {set}；移除 = 当前集合 − {set}，移除后为空则精确撤销机制声明（mechanic=false）。
     * 写入统一 items 覆写 + 覆盖文件后立即重新加载生效（不重载数据包）。
     */
    public static void updateSpecialMechanicSet(MinecraftServer server, String itemId, String mechanicSet, boolean remove) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        List<String> current = new ArrayList<>(DefsQueries.getEffectiveSpecialMechanicSets(itemId));
        // 保留该物品既有的机制数值覆盖（移除机制时同步删除对应数值）
        Map<String, Map<String, ParamValue>> values = copyExistingValues(itemId);
        List<String> updated = new ArrayList<>();
        if (remove) {
            for (String s : current) {
                if (!s.equals(mechanicSet)) {
                    updated.add(s);
                }
            }
            values.remove(mechanicSet);
        } else {
            updated.addAll(current);
            if (!updated.contains(mechanicSet)) {
                updated.add(mechanicSet);
            }
        }
        boolean declared = !remove || !updated.isEmpty();
        // 声明时 mechanic=true + sets 整体覆盖；全部移除时精确撤销 = mechanic:false（不误伤护盾定义）
        DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(declared ? Boolean.TRUE : Boolean.FALSE, false,
                declared ? updated : null,
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                values,
                base != null ? base.attributes() : null));
        DefsManager.ITEM_UI_OWNED.add(itemId);
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品某机制集合的数值覆盖（物品级数值，仅影响该物品）。
     * <p>
     * 目标物品必须已声明该机制集合（否则忽略）；空参数 map 表示清除该机制的数值覆盖（回退 Config 默认）。
     * stackables 与 params 平行（paramKey -> 是否可叠加），缺省视为可叠加。
     * 写入统一 items 覆写 + 覆盖文件后立即重新加载生效（不重载数据包）。
     */
    public static void updateSpecialMechanicValues(MinecraftServer server, String itemId, String mechanicSet, Map<String, Double> params, Map<String, Boolean> stackables) {
        List<String> current = DefsQueries.getEffectiveSpecialMechanicSets(itemId);
        if (!current.contains(mechanicSet)) {
            return;
        }
        Map<String, Map<String, ParamValue>> values = copyExistingValues(itemId);
        if (params == null || params.isEmpty()) {
            values.remove(mechanicSet);
        } else {
            Map<String, ParamValue> paramValues = new LinkedHashMap<>();
            for (var e : params.entrySet()) {
                boolean stackable = stackables == null || stackables.getOrDefault(e.getKey(), Boolean.TRUE);
                paramValues.put(e.getKey(), new ParamValue(e.getValue(), stackable));
            }
            values.put(mechanicSet, paramValues);
        }
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(Boolean.TRUE, false, current,
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                values,
                base != null ? base.attributes() : null));
        DefsManager.ITEM_UI_OWNED.add(itemId);
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品护盾类型（写入统一 items 覆写 + 覆盖文件，立即重新加载生效，不重载数据包）。
     * 护盾全部按实例生效，独占/兼容设置已取消。
     * 既有的护盾数值覆盖被保留（拍平结构，无法按类型过滤时全保留）。
     */
    public static void updateShieldTypeOverride(MinecraftServer server, String itemId, List<String> types) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(
                base != null ? base.mechanic() : null,
                false,
                base != null ? base.sets() : null,
                new ArrayList<>(types),
                base != null ? base.shieldValues() : null,
                base != null ? base.values() : null,
                base != null ? base.attributes() : null));
        DefsManager.ITEM_UI_OWNED.add(itemId);
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }

    /**
     * 编辑操作：更新物品某护盾类型的数值覆盖（物品级数值，仅影响该物品实例）。
     * <p>
     * 目标物品必须已声明该护盾类型（否则忽略）；空参数 map 表示清除该类型的数值覆盖（回退 Config 默认）。
     * 护盾类型数值不参与叠/单合并（每物品实例独立），因此无 stackables 参数。
     * shieldValues 按参数键拍平存储（护盾物品均单类型）；写入统一 items 覆写后立即重新加载生效。
     */
    public static void updateShieldTypeValues(MinecraftServer server, String itemId, String shieldType, Map<String, Double> params) {
        List<String> currentTypes = DefsManager.ITEM_SHIELD_TYPES.getOrDefault(itemId, List.of());
        if (!currentTypes.contains(shieldType)) {
            return;
        }
        ItemDefinition existing = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if ((params == null || params.isEmpty()) && (existing == null || existing.shieldValues() == null)) {
            return; // 清除数值且无护盾数值覆盖：无需处理
        }
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        // shieldValues 按参数键拍平合并；params 为空表示清除该类型的全部数值覆盖（单类型现实）
        Map<String, ParamValue> shieldValues = base != null && base.shieldValues() != null
                ? new LinkedHashMap<>(base.shieldValues()) : new LinkedHashMap<>();
        if (params == null || params.isEmpty()) {
            shieldValues.clear();
        } else {
            for (var e : params.entrySet()) {
                shieldValues.put(e.getKey(), new ParamValue(e.getValue(), false));
            }
        }
        DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(
                base != null ? base.mechanic() : null,
                false,
                base != null ? base.sets() : null,
                base != null ? base.shieldTypes() : null,
                shieldValues.isEmpty() ? null : shieldValues,
                base != null ? base.values() : null,
                base != null ? base.attributes() : null));
        DefsManager.ITEM_UI_OWNED.add(itemId);
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }

    // ======================== 物品属性编辑（统一 items 覆写，attributes 整体接管语义） ========================

    /**
     * 编辑操作：新增/更新物品属性（写入统一 items 覆写 + 覆盖文件，立即重新加载生效）。
     * <p>
     * 基线 = 当前生效属性（权威 + 覆写合并视图），整体写回覆写层（显式接管）；
     * 已有属性保留叠加设置，新增属性默认可叠加。权威属性物品由此获得覆写副本，
     * 后续删除操作能正确覆盖权威定义。
     */
    public static void updateItemAttribute(MinecraftServer server, String itemId, String attrName, double value) {
        Map<String, ParamValue> attrs = currentAttributeBaseline(itemId);
        ParamValue existing = attrs.get(attrName);
        attrs.put(attrName, new ParamValue(value, existing == null || existing.stackable()));
        writeItemAttributeOverride(server, itemId, attrs);
    }

    /**
     * 编辑操作：移除物品单条属性。剩余集（可为空）整体写回覆写层——
     * 显式空 map = 覆盖为无属性（权威属性也被删除）；属性本不存在时忽略。
     */
    public static void removeItemAttribute(MinecraftServer server, String itemId, String attrName) {
        Map<String, ParamValue> attrs = currentAttributeBaseline(itemId);
        if (attrs.remove(attrName) == null) {
            return;
        }
        writeItemAttributeOverride(server, itemId, attrs);
    }

    /**
     * 编辑操作：移除物品全部属性（UI 删除物品/清空属性）。
     * 写显式空接管（物品保留在配置列表、无属性行）；幂等。
     */
    public static void clearItemAttributes(MinecraftServer server, String itemId) {
        writeItemAttributeOverride(server, itemId, new LinkedHashMap<>());
    }

    /**
     * UI 删除物品（三态循环）：
     * <ul>
     * <li>当前为 UI 禁用态（覆写 removed=true）→ 移除覆写条目：有数据文件版本时回退数据文件效果，
     *     否则物品回到未注册状态</li>
     * <li>有数据文件版本（item_definitions 数据文件）→ 写 UI 禁用态（removed=true，本模组对该物品无任何效果）；
     *     禁用态经统一 items 合并派生链生效（机制声明/护盾类型从派生缓存移除，属性/参数查询全部短路）</li>
     * <li>无数据文件（仅 UI 覆写层）→ 真删除：整个移除覆写条目与旧段遗留覆盖，物品回到未注册状态</li>
     * </ul>
     */
    public static void deleteItemOverride(MinecraftServer server, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        ItemDefinition override = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (override != null && override.removed()) {
            removeItemOverrideEntry(itemId);
        } else if (DefsManager.ITEM_DATA_PACK_IDS.contains(itemId)) {
            DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, ItemDefinition.removedState());
            DefsManager.ITEM_UI_OWNED.add(itemId);
        } else {
            removeItemOverrideEntry(itemId);
        }
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }

    /** 移除物品的全部 UI 覆写痕迹：统一 items 条目 + 旧段遗留覆盖（机制/护盾类型），彻底回退到数据文件或未注册状态 */
    static void removeItemOverrideEntry(String itemId) {
        DefsManager.SERVER_ITEM_OVERRIDES.remove(itemId);
        DefsManager.ITEM_UI_OWNED.remove(itemId);
        DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.remove(itemId);
        DefsManager.SPECIAL_MECHANIC_UI_OWNED.remove(itemId);
        DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.remove(itemId);
        DefsManager.SHIELD_TYPE_UI_OWNED.remove(itemId);
    }

    /** 物品当前是否为 UI 禁用态（覆写 removed=true；禁用态物品保留在配置列表中供再次删除回退） */
    public static boolean isUiDisabledItem(String itemId) {
        ItemDefinition override = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        return override != null && override.removed();
    }

    /** 全部 UI 禁用态物品 id（配置同步列表补充禁用态物品用） */
    public static Set<String> getUiDisabledItemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (var e : DefsManager.SERVER_ITEM_OVERRIDES.entrySet()) {
            if (e.getValue().removed()) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    /** 编辑操作：确保物品存在于配置列表（UI 添加物品；已声明属性时幂等忽略） */
    public static void ensureItemOverrideEntry(MinecraftServer server, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        if (DefsQueries.getEffectiveItemAttributes(itemId) != null) {
            return;
        }
        writeItemAttributeOverride(server, itemId, new LinkedHashMap<>());
    }

    /** 当前生效属性基线副本（权威 + 覆写合并视图，编辑操作起点） */
    static Map<String, ParamValue> currentAttributeBaseline(String itemId) {
        Map<String, ParamValue> attrs = DefsQueries.getEffectiveItemAttributes(itemId);
        return attrs != null ? new LinkedHashMap<>(attrs) : new LinkedHashMap<>();
    }

    /** 属性整体接管写回（其余字段保留既有定义，不误伤机制/护盾声明） */
    static void writeItemAttributeOverride(MinecraftServer server, String itemId,
                                                   Map<String, ParamValue> attributes) {
        ItemDefinition base = getEffectiveDefinition(server, itemId);
        DefsManager.SERVER_ITEM_OVERRIDES.put(itemId, new ItemDefinition(
                base != null ? base.mechanic() : null,
                false,
                base != null ? base.sets() : null,
                base != null ? base.shieldTypes() : null,
                base != null ? base.shieldValues() : null,
                base != null ? base.values() : null,
                attributes));
        DefsManager.ITEM_UI_OWNED.add(itemId);
        DefsOverrideIO.saveOverridesToFile(server);
        applyOverrides(server);
    }
    /** 应用运行时覆盖：读取覆盖文件 -> 重新加载定义（含覆盖合并）-> 触发 Config.applyDefs 及各子系统刷新（编辑后立即调用） */
    public static void applyOverrides(MinecraftServer server) {
        DefsOverrideIO.loadOverridesFromFile(server);
        DefsLoader.loadFrom(server.getResourceManager());
        // 清理孤儿禁用态：物品曾被 UI 禁用，但数据文件已不再定义该物品（禁用失去基线意义），
        // 移除条目使 UI 直接不显示（等价于对该物品执行一次「已禁用态再删除」的回退动作）
        pruneOrphanDisabledOverrides();
    }

    /** 清理孤儿禁用态条目（removed=true 且数据文件无定义）：仅禁用态会孤立（UI 自建物品无数据文件但有实际定义，不会进入禁用态） */
    static void pruneOrphanDisabledOverrides() {
        for (String itemId : new ArrayList<>(DefsManager.SERVER_ITEM_OVERRIDES.keySet())) {
            ItemDefinition override = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
            if (override != null && override.removed() && !DefsManager.ITEM_DATA_PACK_IDS.contains(itemId)) {
                removeItemOverrideEntry(itemId);
            }
        }
    }

    /** 重置运行时覆盖：清空内存覆盖 + 删除覆盖文件，恢复数据包默认定义（「恢复默认」按钮使用） */
    public static void resetOverrides(MinecraftServer server) {
        DefsManager.SERVER_ITEM_OVERRIDES.clear();
        DefsManager.ITEM_UI_OWNED.clear();
        DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.clear();
        DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.clear();
        try {
            Files.deleteIfExists(DefsOverrideIO.getOverridesFile(server));
        } catch (Exception e) {
            gytrinket.LOGGER.error("删除定义覆盖文件失败", e);
        }
        applyOverrides(server);
    }
}
