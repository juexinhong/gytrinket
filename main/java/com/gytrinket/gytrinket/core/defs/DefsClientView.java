package com.gytrinket.gytrinket.core.defs;

import com.gytrinket.gytrinket.core.defs.DefsTypes.AttributeEntry;
import com.gytrinket.gytrinket.core.defs.DefsTypes.TooltipRuleDef;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ParamValue;
import com.gytrinket.gytrinket.core.defs.DefsTypes.SpecialMechanicOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ShieldTypeOverride;
import com.gytrinket.gytrinket.core.defs.DefsTypes.ItemDefinition;
import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.engine.math.AttributeType;
import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定义数据的<b>客户端只读视图</b>（P5-5c 从 DefsManager 拆出）。
 * <p>
 * 网络线程在 {@link #applyClientSync} 时构建不可变 {@code ClientSnapshot} 并原子发布，
 * 渲染线程（tooltip/面板）只读该快照，避免 ConcurrentModificationException；
 * 快照为 null（尚未同步）时回退本地静态缓存（单人/集成服务器）。
 */
public final class DefsClientView {

    private DefsClientView() {}

    /**
     * 客户端定义数据不可变快照。
     * 由网络线程在 {@link #applyClientSync} 时一次性构建并原子发布；
     * 渲染线程（tooltip/面板）只读该快照，从根本上杜绝 ConcurrentModificationException。
     */
    static final class ClientSnapshot {
        final Map<String, Boolean> shieldTypes;
        final Set<String> specialMechanicItems;
        final Map<String, Set<String>> effectiveSets;
        final List<TooltipRuleDef> tooltipRules;
        final List<AttributeEntry> attributeDefs;
        final Map<String, SpecialMechanicOverride> smOverrides;
        final Map<String, ShieldTypeOverride> stOverrides;
        final Map<String, List<String>> itemShieldTypes;
        final Map<String, Set<String>> itemSets;
        final Map<String, Map<String, Double>> shieldTypeParamDefaults;

        ClientSnapshot(Map<String, Boolean> shieldTypes,
                       Set<String> specialMechanicItems,
                       Map<String, Set<String>> effectiveSets,
                       List<TooltipRuleDef> tooltipRules,
                       List<AttributeEntry> attributeDefs,
                       Map<String, SpecialMechanicOverride> smOverrides,
                       Map<String, ShieldTypeOverride> stOverrides,
                       Map<String, List<String>> itemShieldTypes,
                       Map<String, Set<String>> itemSets,
                       Map<String, Map<String, Double>> shieldTypeParamDefaults) {
            this.shieldTypes = shieldTypes;
            this.specialMechanicItems = specialMechanicItems;
            this.effectiveSets = effectiveSets;
            this.tooltipRules = tooltipRules;
            this.attributeDefs = attributeDefs;
            this.smOverrides = smOverrides;
            this.stOverrides = stOverrides;
            this.itemShieldTypes = itemShieldTypes;
            this.itemSets = itemSets;
            this.shieldTypeParamDefaults = shieldTypeParamDefaults;
        }
    }

    /** 客户端不可变快照（原子发布；null = 尚未同步，回退读静态缓存） */
    static final AtomicReference<ClientSnapshot> CLIENT_SNAPSHOT = new AtomicReference<>();

    /** 判断指定物品是否属于某个物品集合（客户端 tooltip 使用，快照优先） */
    public static boolean itemSetContains(String setName, String itemId) {
        DefsLoader.ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            Set<String> effective = snap.effectiveSets.get(itemId);
            if (effective != null && effective.contains(setName)) {
                return true;
            }
            return snap.itemSets.getOrDefault(setName, Set.of()).contains(itemId);
        }
        // 快照未构建（尚未同步）：回退静态缓存（联机客户端此时数据为空，单人可读到服务端数据）
        Set<String> eff2 = CLIENT_EFFECTIVE_SETS.get(itemId);
        if (eff2 != null && eff2.contains(setName)) {
            return true;
        }
        return DefsManager.ITEM_SETS.getOrDefault(setName, Set.of()).contains(itemId);
    }

    /** 获取物品的护盾类型列表（客户端 tooltip 使用，覆盖层优先） */
    public static List<String> clientItemShieldTypes(String itemId) {
        DefsLoader.ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            if (snap.stOverrides.containsKey(itemId)) {
                return List.copyOf(snap.stOverrides.get(itemId).types());
            }
            return snap.itemShieldTypes.getOrDefault(itemId, List.of());
        }
        ShieldTypeOverride ov = DefsManager.CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
        if (ov != null) {
            return List.copyOf(ov.types());
        }
        return DefsManager.ITEM_SHIELD_TYPES.getOrDefault(itemId, List.of());
    }

    /** 客户端查询：物品级护盾类型覆盖条目（含独占集合），无覆盖返回 null */
    public static ShieldTypeOverride getClientShieldTypeOverride(String itemId) {
        DefsLoader.ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            return snap.stOverrides.get(itemId);
        }
        return DefsManager.CLIENT_SHIELD_TYPE_OVERRIDES.get(itemId);
    }

    /**
     * 客户端：按物品解析护盾类型参数生效值（tooltip 描述用）。
     * 有物品级覆盖取覆盖值，否则回退 fallback（Config 默认值）——
     * 与服务端 {@link #resolveShieldTypeValueForItem} 语义一致，
     * 保证物品描述显示的数值与实际生效数值相同
     */
    public static double clientShieldTypeValueForItem(String itemId, String shieldType,
                                                      String paramKey, double fallback) {
        ShieldTypeOverride ov = getClientShieldTypeOverride(itemId);
        if (ov == null) return fallback;
        Map<String, Double> params = ov.values().get(shieldType);
        Double v = params == null ? null : params.get(paramKey);
        return v != null ? v : fallback;
    }

    /**
     * 客户端：按物品解析护盾实例基础参数生效值（tooltip 显示用）。
     * 查询顺序与服务端 {@link #resolveShieldParam} 的客户端可见层对齐：
     * UI 覆写（快照/同步层：getServerShieldTypeOverrides 的 items 统一结构转译段
     * + 旧护盾类型覆写段）→
     * 权威物品定义（DefsManager.ITEM_DEFINITIONS 静态缓存：单人下集成服务器已加载；
     * 联机客户端无数据包数据，该层自然为空跳过）→
     * 护盾类型定义默认值（{@link #clientShieldTypeParamDefault}，快照同步）→
     * fallback（调用方传入：ShieldValueDefs 静态默认），
     * 保证物品描述显示的数值与实际生效数值同源
     */
    public static double clientResolveShieldParam(String itemId, String shieldTypeName,
                                                  String paramKey, double fallback) {
        if (itemId == null) return fallback;
        // UI 覆写层：优先取声明类型组内的覆盖值，未命中再扫全部组（兼容旧段"任意类型组内同键参数"语义）
        ShieldTypeOverride ov = getClientShieldTypeOverride(itemId);
        if (ov != null && ov.values() != null) {
            Double v = null;
            if (shieldTypeName != null) {
                Map<String, Double> params = ov.values().get(shieldTypeName);
                v = params == null ? null : params.get(paramKey);
            }
            if (v == null) {
                for (Map<String, Double> params : ov.values().values()) {
                    Double cand = params.get(paramKey);
                    if (cand != null) {
                        v = cand;
                        break;
                    }
                }
            }
            if (v != null) return v;
        }
        DefsLoader.ensureClientLoaded();
        // 权威物品定义层（item_definitions 数据文件的 shieldValues 段）
        ItemDefinition def = DefsManager.ITEM_DEFINITIONS.get(itemId);
        if (def != null && !def.removed() && def.shieldValues() != null) {
            ParamValue pv = def.shieldValues().get(paramKey);
            if (pv != null) return pv.value();
        }
        // 护盾类型定义默认值层（shield_types/*.json 的 shieldValues 段）
        Double typeDefault = clientShieldTypeParamDefault(shieldTypeName, paramKey);
        if (typeDefault != null) return typeDefault;
        return fallback;
    }

    /** 客户端查询：物品的特殊机制覆盖条目（含机制集合与数值覆盖；无覆盖时返回 null） */
    public static SpecialMechanicOverride getClientSpecialMechanicOverride(String itemId) {
        DefsLoader.ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            return snap.smOverrides.get(itemId);
        }
        return DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
    }

    /**
     * 客户端：按物品解析特殊机制参数生效值（tooltip/客户端模拟用）。
     * 有物品级覆盖取覆盖值，否则回退 fallback ——
     * 与服务端 {@link #resolveMechanicValueForItem} 语义一致，
     * 保证物品描述显示的数值与实际生效数值相同
     */
    public static double clientResolveMechanicValueForItem(String itemId, String mechanicSet,
                                                           String paramKey, double fallback) {
        if (itemId == null) return fallback;
        SpecialMechanicOverride ov = getClientSpecialMechanicOverride(itemId);
        if (ov == null || ov.removed()) return fallback;
        Map<String, ParamValue> params = ov.values().get(mechanicSet);
        ParamValue pv = params == null ? null : params.get(paramKey);
        return pv != null ? pv.value() : fallback;
    }

    /** 查询属性的组合方式（客户端 tooltip 格式化使用），未找到返回 null */
    public static AttributeType clientAttributeType(String attrName) {
        DefsLoader.ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        List<AttributeEntry> defs = snap != null ? snap.attributeDefs : DefsManager.ATTRIBUTE_DEFS;
        for (AttributeEntry e : defs) {
            if (e.name().equals(attrName)) {
                try {
                    return AttributeType.valueOf(e.combine());
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 读取工具提示规则（客户端 tooltip 使用） */
    public static List<TooltipRuleDef> clientTooltipRules() {
        DefsLoader.ensureClientLoaded();
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        return snap != null ? snap.tooltipRules : DefsManager.TOOLTIP_RULES;
    }
    /**
     * 客户端：物品当前生效的属性（权威 item_definitions attributes 段，仅本地数据兜底）。
     * <p>
     * 1.20.1 无数据包 registry 网络同步，客户端经 {@link #DefsLoader.ensureClientLoaded()} 惰性加载
     * 本地 JAR/资源包数据，服务端运行时覆写层对客户端不可见；权威属性以
     * ConfigDefsSyncMessage 下发为准，此方法仅在静态表未同步时兜底。
     * 过滤 removed 撤销与未声明/空 attributes 的条目。
     */
    public static Map<String, Double> clientEffectiveItemAttributes(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return Map.of();
        }
        DefsLoader.ensureClientLoaded();
        ItemDefinition def = DefsManager.ITEM_DEFINITIONS.get(itemId);
        if (def == null || def.removed() || def.attributes() == null || def.attributes().isEmpty()) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        def.attributes().forEach((name, pv) -> result.put(name, pv.value()));
        return result;
    }
    /** 客户端查询：物品是否声明为特殊机制（覆盖层优先） */
    public static boolean clientIsSpecialMechanic(String itemId) {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            SpecialMechanicOverride ov = snap.smOverrides.get(itemId);
            if (ov != null) {
                return !ov.removed();
            }
            return snap.specialMechanicItems.contains(itemId);
        }
        SpecialMechanicOverride ov2 = DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov2 != null) {
            return !ov2.removed();
        }
        return DefsManager.SPECIAL_MECHANIC_ITEMS.contains(itemId);
    }

    /** 客户端查询：物品当前生效的特殊机制集合（覆盖层优先，其次服务端同步的生效集合） */
    public static List<String> clientSpecialMechanicSets(String itemId) {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            SpecialMechanicOverride ov = snap.smOverrides.get(itemId);
            if (ov != null) {
                return ov.removed() ? List.of() : List.copyOf(ov.sets());
            }
            Set<String> sets = snap.effectiveSets.get(itemId);
            return sets == null ? List.of() : List.copyOf(sets);
        }
        SpecialMechanicOverride ov2 = DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
        if (ov2 != null) {
            return ov2.removed() ? List.of() : List.copyOf(ov2.sets());
        }
        Set<String> sets2 = CLIENT_EFFECTIVE_SETS.get(itemId);
        return sets2 == null ? List.of() : List.copyOf(sets2);
    }

    /** 客户端查询：所有可选的特殊机制集合名（覆盖层优先合并） */
    public static List<String> clientAllMechanicSets() {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            Set<String> sets = new LinkedHashSet<>();
            for (String itemId : snap.specialMechanicItems) {
                sets.addAll(clientSpecialMechanicSets(itemId));
            }
            for (SpecialMechanicOverride ov : snap.smOverrides.values()) {
                if (!ov.removed()) {
                    sets.addAll(ov.sets());
                }
            }
            return new ArrayList<>(sets);
        }
        Set<String> sets = new LinkedHashSet<>();
        for (String itemId : DefsManager.SPECIAL_MECHANIC_ITEMS) {
            sets.addAll(clientSpecialMechanicSets(itemId));
        }
        for (SpecialMechanicOverride ov : DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.values()) {
            if (!ov.removed()) {
                sets.addAll(ov.sets());
            }
        }
        return new ArrayList<>(sets);
    }

    /** 客户端查询：机制集合显示名（tooltip_rules titleKey 翻译；无条目则回退：去掉 _items 后缀按集合名翻译，再回退原集合名） */
    public static String clientMechanicDisplayName(String mechanicSet) {
        if (mechanicSet == null || mechanicSet.isEmpty()) {
            return mechanicSet;
        }
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        List<TooltipRuleDef> rules = snap != null ? snap.tooltipRules : DefsManager.getTooltipRules();
        TooltipRuleDef matched = null;
        for (TooltipRuleDef rule : rules) {
            if (mechanicSet.equals(rule.itemSet()) && rule.titleKey() != null && !rule.titleKey().isEmpty()) {
                matched = rule;
                break;
            }
        }
        if (matched != null) {
            return translateMechanicTitle(matched.titleKey());
        }
        // 回退1：去掉 _items 后缀按集合名翻译（如 journey_module_items → tooltip.gytrinket.journey_module）
        String base = mechanicSet.endsWith("_items")
                ? mechanicSet.substring(0, mechanicSet.length() - "_items".length()) : mechanicSet;
        String key = "tooltip.gytrinket." + base;
        String translated = net.minecraft.network.chat.Component.translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }
        // 回退2：集合名含 _required 时再尝试去掉（如 pursuit_array_required_items → tooltip.gytrinket.pursuit_array）
        if (base.endsWith("_required")) {
            String base2 = base.substring(0, base.length() - "_required".length());
            String key2 = "tooltip.gytrinket." + base2;
            String t2 = net.minecraft.network.chat.Component.translatable(key2).getString();
            if (!t2.equals(key2)) {
                return t2;
            }
        }
        return mechanicSet;
    }

    /** 翻译机制标题键（tooltip.gytrinket.<titleKey>，缺翻译时回退原键名） */
    static String translateMechanicTitle(String titleKey) {
        String key = "tooltip.gytrinket." + titleKey;
        String translated = net.minecraft.network.chat.Component.translatable(key).getString();
        return translated.equals(key) ? titleKey : translated;
    }

    /** 客户端查询：物品声明为特殊机制时的机制名称列表 */
    public static List<String> clientSpecialMechanicNames(String itemId) {
        List<String> sets = clientSpecialMechanicSets(itemId);
        if (sets.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (String set : sets) {
            names.add(clientMechanicDisplayName(set));
        }
        return names;
    }

    /**
     * 客户端：解析机制数值参数的物品级覆盖值（与服务端 resolveMechanicValue 语义一致）。
     * 遍历本地装备物品逐件合并——可叠加求和、不可叠加比对取最大；未覆盖物品按
     * fallback（Config 默认）不可叠加参与。player 为 null 或未装备时返回 fallback。
     * 客户端机制声明读快照（clientSpecialMechanicSets），数值读客户端覆盖层。
     */
    public static double clientResolveMechanicValue(Player player, String mechanicSet, String paramKey, double fallback) {
        if (player == null) return fallback;
        return DefsQueries.mergeMechanicValue(player.getUUID(), PlayerStoreUtils.getAllEquippedStacks(player),
                itemId -> clientSpecialMechanicSets(itemId).contains(mechanicSet),
                itemId -> {
                    SpecialMechanicOverride ov = DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.get(itemId);
                    return ov != null && !ov.removed() ? ov.values() : null;
                }, mechanicSet, paramKey, fallback);
    }

    /** 客户端查询：护盾类型名 -> 是否兼容（shield_types 定义） */
    public static Map<String, Boolean> clientShieldTypes() {
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            return new HashMap<>(snap.shieldTypes);
        }
        return new HashMap<>(DefsManager.SHIELD_TYPES);
    }

    /**
     * 客户端查询：护盾类型基础参数默认值（shield_types/*.json 的 shieldValues 段）。
     * 快照优先（服务端同步），未同步时回退本地静态缓存（集成服务器/单人直读数据包）。
     * 返回 null 表示该类型未定义此参数（调用方回退 ParamDef 默认值）。
     */
    public static Double clientShieldTypeParamDefault(String typeName, String paramKey) {
        if (typeName == null || paramKey == null) return null;
        ClientSnapshot snap = CLIENT_SNAPSHOT.get();
        if (snap != null) {
            Map<String, Double> values = snap.shieldTypeParamDefaults.get(typeName);
            return values != null ? values.get(paramKey) : null;
        }
        Map<String, ParamValue> values = DefsManager.SHIELD_TYPE_PARAM_DEFAULTS.get(typeName);
        return values != null && values.containsKey(paramKey) ? values.get(paramKey).value() : null;
    }

    /** 客户端：从服务端同步的覆盖数据更新本地覆盖层（面板显示实时生效） */
    public static void setClientOverrides(Map<String, SpecialMechanicOverride> specialMechanics, Map<String, ShieldTypeOverride> shieldTypes) {
        DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.clear();
        DefsManager.CLIENT_SPECIAL_MECHANIC_OVERRIDES.putAll(specialMechanics);
        DefsManager.CLIENT_SHIELD_TYPE_OVERRIDES.clear();
        DefsManager.CLIENT_SHIELD_TYPE_OVERRIDES.putAll(shieldTypes);
    }

    /** 客户端：服务端同步的物品->生效机制集合（绕过客户端无数据包的限制） */
    static final Map<String, Set<String>> CLIENT_EFFECTIVE_SETS = new ConcurrentHashMap<>();

    /** 客户端：接收服务端完整定义同步（护盾类型/特殊机制/提示规则/覆盖层/类型参数默认值），替代客户端数据包读取 */
    public static void applyClientSync(Map<String, Boolean> shieldTypes,
                                       List<String> specialMechanicItems,
                                       Map<String, List<String>> itemToSets,
                                       List<TooltipRuleDef> tooltipRules,
                                       Map<String, SpecialMechanicOverride> smOverrides,
                                       Map<String, ShieldTypeOverride> stOverrides,
                                       Map<String, Map<String, Double>> typeParamDefaults,
                                       long revision) {
        DefsManager.clientRevision = revision;
        DefsManager.SHIELD_TYPES.clear();
        DefsManager.SHIELD_TYPES.putAll(shieldTypes);
        DefsManager.SHIELD_TYPE_PARAM_DEFAULTS.clear();
        for (var e : typeParamDefaults.entrySet()) {
            Map<String, ParamValue> converted = new HashMap<>();
            for (var p : e.getValue().entrySet()) {
                converted.put(p.getKey(), ParamValue.of(p.getValue()));
            }
            DefsManager.SHIELD_TYPE_PARAM_DEFAULTS.put(e.getKey(), Map.copyOf(converted));
        }
        DefsManager.SPECIAL_MECHANIC_ITEMS.clear();
        DefsManager.SPECIAL_MECHANIC_ITEMS.addAll(specialMechanicItems);
        CLIENT_EFFECTIVE_SETS.clear();
        for (var e : itemToSets.entrySet()) {
            CLIENT_EFFECTIVE_SETS.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        // 反推「集合名 -> 物品ID」：Config.applyClientDefs() 要按集合名重建模块物品集合。
        // 专用服联机时客户端不跑 datapack 重载，DefsManager.ITEM_SETS 原本会一直是空的。
        DefsManager.ITEM_SETS.clear();
        for (var e : itemToSets.entrySet()) {
            for (String setName : e.getValue()) {
                DefsManager.ITEM_SETS.computeIfAbsent(setName, k -> new HashSet<>()).add(e.getKey());
            }
        }
        DefsManager.TOOLTIP_RULES.clear();
        DefsManager.TOOLTIP_RULES.addAll(tooltipRules);
        setClientOverrides(smOverrides, stOverrides);

        // 构建并发布不可变快照：此后渲染线程只读快照（不可变），彻底避免并发修改
        Map<String, Set<String>> eff = new HashMap<>();
        for (var e : itemToSets.entrySet()) {
            eff.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        Map<String, List<String>> itemShieldTypes = new HashMap<>();
        for (var e : DefsManager.ITEM_SHIELD_TYPES.entrySet()) {
            itemShieldTypes.put(e.getKey(), List.copyOf(e.getValue()));
        }
        Map<String, Set<String>> itemSets = new HashMap<>();
        for (var e : DefsManager.ITEM_SETS.entrySet()) {
            itemSets.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        CLIENT_SNAPSHOT.set(new ClientSnapshot(
                Map.copyOf(shieldTypes),
                Set.copyOf(specialMechanicItems),
                Map.copyOf(eff),
                List.copyOf(tooltipRules),
                List.copyOf(DefsManager.ATTRIBUTE_DEFS),
                Map.copyOf(smOverrides),
                Map.copyOf(stOverrides),
                Map.copyOf(itemShieldTypes),
                Map.copyOf(itemSets),
                Map.copyOf(typeParamDefaults)
        ));
    }
}
