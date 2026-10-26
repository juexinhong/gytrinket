package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.core.defs.DefsTypes.TooltipRuleDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ParamValue;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ShieldTypeOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinition;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.shield.DisableSystem;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 定义数据的<b>服务端运行时查询</b>（P5-5c 从 DefsManager 拆出）。
 * <p>
 * 机制/护盾/属性参数的多层合并解析：items 统一覆写 → 权威 item_definitions → 类型级默认值 → fallback；
 * 多物品合并规则：可叠加项求和、不可叠加项取最大，最终值 = max(可叠加和, 最大的不可叠加项)。
 * 也提供同步到客户端的服务端视图。
 */
public final class DefsQueries {

    private DefsQueries() {}

    /** 物品当前生效的特殊机制集合（统一 items 覆写优先，其次旧段覆盖，最后数据驱动声明）。
     *  按物品 id 直接查它的机制声明（special_mechanics 路径定义），
     *  而非遍历 DefsManager.ITEM_SETS——避免把 item_sets 普通集合（如 primary_shield_amplification_items）误当机制 */
    public static List<String> getEffectiveSpecialMechanicSets(String itemId) {
        ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null) {
            if (item.removed()) {
                return new ArrayList<>();
            }
            if (item.mechanic() != null) {
                // mechanic 显式声明：true 时 sets 整体覆盖（null = 无分类），false 时无机制
                return item.mechanic() ? new ArrayList<>(item.sets() != null ? item.sets() : List.of()) : new ArrayList<>();
            }
            // mechanic=null：部分定义不接管机制，落到旧逻辑
        }
        SpecialMechanicOverride ov = DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov != null) {
            return new ArrayList<>(ov.removed() ? List.of() : ov.sets());
        }
        List<String> sets = DefsManager.SPECIAL_MECHANIC_SETS.get(itemId);
        return sets == null ? new ArrayList<>() : new ArrayList<>(sets);
    }
    /**
     * 服务端：玩家是否装备了声明指定特殊机制集合的物品（覆盖层优先）。
     */
    public static boolean playerHasEquippedMechanic(MinecraftServer server, UUID playerUUID, String mechanicSet) {
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = Registry.ITEM.getKey(stack.getItem()).toString();
            if (getEffectiveSpecialMechanicSets(itemId).contains(mechanicSet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 服务端：解析玩家某机制参数的生效数值（物品级覆盖 + 多物品合并）。
     * <p>
     * 遍历所有声明该机制集合且未被禁用的装备物品，每件贡献一份参数值（有 UI 覆盖取覆盖值，
     * 否则取 fallback = Config 默认值，未覆盖物品视为不可叠加）。合并规则：
     * 可叠加项求和，不可叠加项各自独立参与比对，最终值 = max(可叠加和, 最大的不可叠加项)。
     * 未装备声明物品时返回 fallback（机制判定由调用方的 playerHasEquippedMechanic 负责）。
     * server==null 时直接回退（客户端上下文防御）。
     */
    public static double resolveMechanicValue(MinecraftServer server, UUID playerUUID, String mechanicSet, String paramKey, double fallback) {
        if (server == null) return fallback;
        return mergeMechanicValue(playerUUID, PlayerStoreUtils.getEquippedStacks(playerUUID),
                itemId -> getEffectiveSpecialMechanicSets(itemId).contains(mechanicSet),
                DefsQueries::effectiveMechanicValuesOf, mechanicSet, paramKey, fallback);
    }

    /** 物品级机制数值覆盖查询（统一 items 覆写优先，旧段覆盖回退；供多物品合并管线取值） */
    static Map<String, Map<String, ParamValue>> effectiveMechanicValuesOf(String itemId) {
        ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && !item.removed() && item.values() != null) {
            return item.values();
        }
        SpecialMechanicOverride ov = DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        return ov != null && !ov.removed() ? ov.values() : null;
    }

    /**
     * 服务端：枚举玩家装备的、声明指定特殊机制集合的物品（覆盖层优先）。
     * <p>
     * 跳过空栈与被禁用的物品；按物品种类去重——同一物品装备多件只算一个实例，
     * 实例粒度为物品种类而非装备件数。
     */
    public static List<ItemStack> getEquippedMechanicStacks(MinecraftServer server, UUID playerUUID, String mechanicSet) {
        List<ItemStack> result = new ArrayList<>();
        if (server == null || playerUUID == null) return result;
        Set<String> seen = new HashSet<>();
        for (ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = Registry.ITEM.getKey(stack.getItem()).toString();
            if (!seen.add(itemId)) continue;
            if (getEffectiveSpecialMechanicSets(itemId).contains(mechanicSet)) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * 服务端：按物品实例解析特殊机制参数值（单物品直接查询，不跨物品合并）。
     * <p>
     * 实例化特殊机制（仿护盾类型）：每件提供该机制的物品独立触发、各用各的数值；
     * 未定义该物品覆盖参数时返回 fallback（Config 默认值）。
     */
    public static double resolveMechanicValueForItem(MinecraftServer server, String itemId, String mechanicSet, String paramKey, double fallback) {
        if (server == null || itemId == null) return fallback;
        // 统一 items 覆写优先（条目存在即短路，不再回退旧段）
        ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && !item.removed() && item.values() != null) {
            Map<String, ParamValue> params = item.values().get(mechanicSet);
            ParamValue pv = params == null ? null : params.get(paramKey);
            if (pv != null) return pv.value();
            return fallback;
        }
        SpecialMechanicOverride ov = DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov == null || ov.removed()) return fallback;
        Map<String, ParamValue> params = ov.values().get(mechanicSet);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /**
     * 多物品机制参数合并（服务端/客户端共用算法）：遍历装备物品逐件取值——有 UI 覆盖取覆盖值，
     * 否则取 fallback（未覆盖物品视为不可叠加）。可叠加项求和，不可叠加项比对取最大，
     * 最终值 = max(可叠加和, 最大的不可叠加项)。未装备声明物品时返回 fallback。
     */
    static double mergeMechanicValue(UUID playerUUID, List<ItemStack> stacks,
                                             Predicate<String> hasMechanicSet,
                                             Function<String, Map<String, Map<String, ParamValue>>> valuesOf,
                                             String mechanicSet, String paramKey, double fallback) {
        return mergeParamValue(playerUUID, stacks, hasMechanicSet, itemId -> {
            Map<String, Map<String, ParamValue>> values = valuesOf.apply(itemId);
            return values == null ? null : values.get(mechanicSet);
        }, paramKey, fallback);
    }

    /** 多物品参数合并通用核心（机制/护盾类型共用）：paramsOf 给出物品在目标集合/类型下的参数覆盖（无则 null） */
    static double mergeParamValue(UUID playerUUID, List<ItemStack> stacks,
                                          Predicate<String> hasSet,
                                          Function<String, Map<String, ParamValue>> paramsOf,
                                          String paramKey, double fallback) {
        double stackableSum = 0.0D;
        double bestNonStackable = Double.NEGATIVE_INFINITY;
        boolean hasProvider = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            String itemId = Registry.ITEM.getKey(stack.getItem()).toString();
            if (!hasSet.test(itemId)) continue;
            hasProvider = true;
            double value = fallback;
            boolean stackable = false;
            Map<String, ParamValue> params = paramsOf.apply(itemId);
            ParamValue pv = params == null ? null : params.get(paramKey);
            if (pv != null) {
                value = pv.value();
                stackable = pv.stackable();
            }
            if (stackable) {
                stackableSum += value;
            } else if (value > bestNonStackable) {
                bestNonStackable = value;
            }
        }
        if (!hasProvider) return fallback;
        return Math.max(stackableSum, bestNonStackable);
    }
    /**
     * 服务端：解析某物品实例在指定护盾类型上的参数生效值（物品级覆盖，不跨物品合并）。
     * 统一 items 覆写优先：shieldValues 拍平结构按参数键查询（忽略类型分组）；
     * 未覆盖该参数时回退旧护盾类型覆写段，再回退 fallback（调用方传入 Config 默认值）。
     * server==null 时直接回退（客户端上下文防御）。
     */
    public static double resolveShieldTypeValueForItem(MinecraftServer server, String itemId, String shieldType, String paramKey, double fallback) {
        if (server == null || itemId == null) return fallback;
        ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && item.shieldValues() != null) {
            ParamValue pv = item.shieldValues().get(paramKey);
            if (pv != null) return pv.value();
        }
        ShieldTypeOverride ov = DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov == null) return fallback;
        Map<String, Double> params = ov.values().get(shieldType);
        if (params == null) return fallback;
        Double v = params.get(paramKey);
        return v == null ? fallback : v;
    }

    /**
     * 服务端：解析护盾实例参数（shield_base、shield_cooldown_time、shield_hit_cooldown_extend 等）。
     * <p>
     * 查询顺序：统一 items 覆写（DefsManager.SERVER_ITEM_OVERRIDES.shieldValues）→
     * 权威物品定义（DefsManager.ITEM_DEFINITIONS.shieldValues，JAR 数据与覆写合并结果）→
     * 护盾类型定义默认值（DefsManager.SHIELD_TYPE_PARAM_DEFAULTS，shield_types/*.json 的 shieldValues 段，
     * 物品未声明该键时采用类型级默认）→
     * 旧护盾类型覆写段（DefsManager.SERVER_SHIELD_TYPE_OVERRIDES，任意类型组内同键参数）→
     * fallback（调用方传入的玩家全局聚合值：无护盾类型物品基础属性全局生效的通道）。
     * <p>
     * server 为 null 时跳过覆写/权威层（静态缓存不可用），仅走类型默认值层（loadFrom 静态缓存）。
     */
    public static double resolveShieldParam(MinecraftServer server, String itemId, String shieldTypeName,
                                            String paramKey, double fallback) {
        if (itemId == null) return fallback;
        if (server != null) {
            ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
            if (item != null && !item.removed() && item.shieldValues() != null) {
                ParamValue pv = item.shieldValues().get(paramKey);
                if (pv != null) return pv.value();
            }
            ItemDefinition def = DefsManager.ITEM_DEFINITIONS.get(itemId);
            if (def != null && !def.removed() && def.shieldValues() != null) {
                ParamValue pv = def.shieldValues().get(paramKey);
                if (pv != null) return pv.value();
            }
        }
        if (shieldTypeName != null) {
            Map<String, ParamValue> typeDefaults = DefsManager.SHIELD_TYPE_PARAM_DEFAULTS.get(shieldTypeName);
            if (typeDefaults != null) {
                ParamValue pv = typeDefaults.get(paramKey);
                if (pv != null) return pv.value();
            }
        }
        if (server != null) {
            ShieldTypeOverride ov = DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.get(itemId);
            if (ov != null) {
                for (Map<String, Double> params : ov.values().values()) {
                    Double v = params.get(paramKey);
                    if (v != null) return v;
                }
            }
        }
        return fallback;
    }

    /**
     * 服务端：物品当前生效的属性贡献（属性聚合管线数据源）。
     * <p>
     * 查询顺序与 {@link #resolveShieldParam} 一致：统一 items 覆写
     * （DefsManager.SERVER_ITEM_OVERRIDES.attributes）→ 权威物品定义
     * （DefsManager.ITEM_DEFINITIONS.attributes，JAR 数据）。覆写 attributes 非 null
     * 即整体接管（<b>显式空 map = 覆盖为无属性</b>，支持 UI 删除权威属性）；
     * 条目被撤销（removed）或未声明 attributes 时回退权威；均无则返回 null
     * （该物品无属性贡献）。
     */
    public static Map<String, ParamValue> getEffectiveItemAttributes(String itemId) {
        if (itemId == null) return null;
        ItemDefinition item = DefsManager.SERVER_ITEM_OVERRIDES.get(itemId);
        if (item != null && !item.removed() && item.attributes() != null) {
            return item.attributes();
        }
        ItemDefinition def = DefsManager.ITEM_DEFINITIONS.get(itemId);
        if (def != null && !def.removed() && def.attributes() != null) {
            return def.attributes();
        }
        return null;
    }

    /**
     * 服务端：枚举声明了物品属性的物品 id（配置同步物品列表的属性来源）。
     * <p>
     * 覆写层 attributes 非 null（含显式空接管）∪ 权威定义 attributes 非 null，
     * 均排除 removed 撤销条目。
     */
    public static Set<String> getEffectiveItemAttributeItemIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (var e : DefsManager.SERVER_ITEM_OVERRIDES.entrySet()) {
            if (!e.getValue().removed() && e.getValue().attributes() != null) {
                ids.add(e.getKey());
            }
        }
        for (var e : DefsManager.ITEM_DEFINITIONS.entrySet()) {
            if (!e.getValue().removed() && e.getValue().attributes() != null) {
                ids.add(e.getKey());
            }
        }
        return ids;
    }

    /**
     * 服务端：收集全部有属性物品的当前生效属性（登录/编辑后经 ConfigDefsSyncMessage 下发客户端）。
     * 复用 {@link #getEffectiveItemAttributeItemIds()} + {@link #getEffectiveItemAttributes(String)}
     * （覆写层 → 权威定义），ParamValue 提取为纯数值。
     */
    public static Map<String, Map<String, Double>> collectEffectiveItemAttributes() {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        for (String itemId : getEffectiveItemAttributeItemIds()) {
            Map<String, ParamValue> attrs = getEffectiveItemAttributes(itemId);
            if (attrs == null || attrs.isEmpty()) {
                continue;
            }
            Map<String, Double> values = new LinkedHashMap<>();
            attrs.forEach((name, pv) -> values.put(name, pv.value()));
            if (!values.isEmpty()) {
                result.put(itemId, values);
            }
        }
        return result;
    }
    public static Map<String, Boolean> getServerShieldTypes() {
        return DefsManager.SHIELD_TYPES;
    }

    /** 服务端查询：护盾类型基础参数默认值（转 Double 形式，供网络同步到客户端显示） */
    public static Map<String, Map<String, Double>> getServerShieldTypeParamDefaultDoubles() {
        Map<String, Map<String, Double>> result = new HashMap<>();
        for (var e : DefsManager.SHIELD_TYPE_PARAM_DEFAULTS.entrySet()) {
            Map<String, Double> values = new HashMap<>();
            for (var p : e.getValue().entrySet()) {
                values.put(p.getKey(), p.getValue().value());
            }
            result.put(e.getKey(), values);
        }
        return result;
    }

    public static List<String> getServerSpecialMechanicItems() {
        return new ArrayList<>(DefsManager.SPECIAL_MECHANIC_ITEMS);
    }

    public static Map<String, List<String>> getServerAllEffectiveSets() {
        Map<String, List<String>> map = new HashMap<>();
        for (String itemId : DefsManager.SPECIAL_MECHANIC_ITEMS) {
            map.put(itemId, getEffectiveSpecialMechanicSets(itemId));
        }
        return map;
    }

    public static List<TooltipRuleDef> getServerTooltipRules() {
        return DefsManager.TOOLTIP_RULES;
    }

    /**
     * 服务端：获取当前覆盖数据（供同步到客户端，过渡期将 items 统一结构转译回旧分段结构）。
     * items 条目优先（mechanic 非 null 才产生条目）；旧分段条目仅在未被 items 字段级接管时补充。
     */
    public static Map<String, SpecialMechanicOverride> getServerSpecialMechanicOverrides() {
        Map<String, SpecialMechanicOverride> result = new LinkedHashMap<>();
        for (var e : DefsManager.SERVER_ITEM_OVERRIDES.entrySet()) {
            ItemDefinition def = e.getValue();
            if (def.mechanic() == null) continue;
            if (def.mechanic()) {
                result.put(e.getKey(), SpecialMechanicOverride.declared(
                        def.sets() != null ? def.sets() : List.of(), def.values()));
            } else {
                result.put(e.getKey(), SpecialMechanicOverride.removedState());
            }
        }
        for (var e : DefsManager.SERVER_SPECIAL_MECHANIC_OVERRIDES.entrySet()) {
            if (!result.containsKey(e.getKey()) && !DefsLoader.itemOverrideOwnsMechanic(e.getKey())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    /**
     * 服务端：获取当前覆盖数据（供同步到客户端，过渡期将 items 统一结构转译回旧分段结构）。
     * items 条目仅 shieldTypes 非 null 才产生条目（多实例语义已取代兼容设置）；
     * shieldValues（ParamValue 拍平）按声明类型回填为 Double 分组结构。
     */
    public static Map<String, ShieldTypeOverride> getServerShieldTypeOverrides() {
        Map<String, ShieldTypeOverride> result = new LinkedHashMap<>();
        for (var e : DefsManager.SERVER_ITEM_OVERRIDES.entrySet()) {
            ItemDefinition def = e.getValue();
            if (def.shieldTypes() == null) continue;
            Map<String, Map<String, Double>> values = new LinkedHashMap<>();
            if (def.shieldValues() != null) {
                Map<String, Double> flat = new LinkedHashMap<>();
                for (var pv : def.shieldValues().entrySet()) {
                    flat.put(pv.getKey(), pv.getValue().value());
                }
                for (String type : def.shieldTypes()) {
                    values.put(type, flat);
                }
            }
            result.put(e.getKey(), new ShieldTypeOverride(new ArrayList<>(def.shieldTypes()), values));
        }
        for (var e : DefsManager.SERVER_SHIELD_TYPE_OVERRIDES.entrySet()) {
            if (!result.containsKey(e.getKey()) && !DefsLoader.itemOverrideOwnsShieldTypes(e.getKey())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
