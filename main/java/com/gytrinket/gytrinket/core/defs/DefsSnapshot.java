package com.gytrinket.gytrinket.core.defs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一次成功定义加载的不可变快照。
 * <p>
 * 只在 {@link DefsManager} 的加载流程完全成功后发布；解析失败不会产生新快照。
 * revision 是内容哈希（map/set 用内容 hashCode，list 保留顺序），供客户端/服务端对齐诊断；
 * 它不是加密哈希，只用于“双方是否同一份定义”的快速判断。
 */
public final class DefsSnapshot {

    public final long loadId;
    public final long revision;

    public final Map<String, Set<String>> itemSets;
    public final Map<String, Set<String>> entitySets;
    public final Set<String> specialMechanicItems;
    public final Map<String, List<String>> specialMechanicSets;
    public final Map<String, Boolean> shieldTypes;
    public final Map<String, Map<String, DefsTypes.ParamValue>> shieldTypeParamDefaults;
    public final Map<String, List<String>> itemShieldTypes;
    public final List<DefsTypes.AttributeEntry> attributeDefs;
    public final Map<String, Set<String>> disableTargets;
    public final Map<String, Set<String>> dependencies;
    public final Map<String, Set<String>> disableCategories;
    public final Map<String, List<List<String>>> dependenciesAll;
    public final Map<String, DefsTypes.ModuleTreeDef> moduleTrees;
    public final Map<String, List<String>> upgradePaths;
    public final List<DefsTypes.TooltipRuleDef> tooltipRules;
    public final Map<String, DefsTypes.ItemDefinition> itemDefinitions;
    public final Set<String> itemDataPackIds;

    private DefsSnapshot(long loadId,
                         Map<String, Set<String>> itemSets,
                         Map<String, Set<String>> entitySets,
                         Set<String> specialMechanicItems,
                         Map<String, List<String>> specialMechanicSets,
                         Map<String, Boolean> shieldTypes,
                         Map<String, Map<String, DefsTypes.ParamValue>> shieldTypeParamDefaults,
                         Map<String, List<String>> itemShieldTypes,
                         List<DefsTypes.AttributeEntry> attributeDefs,
                         Map<String, Set<String>> disableTargets,
                         Map<String, Set<String>> dependencies,
                         Map<String, Set<String>> disableCategories,
                         Map<String, List<List<String>>> dependenciesAll,
                         Map<String, DefsTypes.ModuleTreeDef> moduleTrees,
                         Map<String, List<String>> upgradePaths,
                         List<DefsTypes.TooltipRuleDef> tooltipRules,
                         Map<String, DefsTypes.ItemDefinition> itemDefinitions,
                         Set<String> itemDataPackIds) {
        this.loadId = loadId;
        this.itemSets = copySetMap(itemSets);
        this.entitySets = copySetMap(entitySets);
        this.specialMechanicItems = Collections.unmodifiableSet(new LinkedHashSet<>(specialMechanicItems));
        this.specialMechanicSets = copyListMap(specialMechanicSets);
        this.shieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(shieldTypes));
        this.shieldTypeParamDefaults = copyNestedMap(shieldTypeParamDefaults);
        this.itemShieldTypes = copyListMap(itemShieldTypes);
        this.attributeDefs = List.copyOf(attributeDefs);
        this.disableTargets = copySetMap(disableTargets);
        this.dependencies = copySetMap(dependencies);
        this.disableCategories = copySetMap(disableCategories);
        this.dependenciesAll = copyNestedListMap(dependenciesAll);
        this.moduleTrees = Collections.unmodifiableMap(new LinkedHashMap<>(moduleTrees));
        this.upgradePaths = copyListMap(upgradePaths);
        this.tooltipRules = List.copyOf(tooltipRules);
        this.itemDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(itemDefinitions));
        this.itemDataPackIds = Collections.unmodifiableSet(new LinkedHashSet<>(itemDataPackIds));

        long first = Integer.toUnsignedLong(Objects.hash(this.itemSets, this.entitySets, this.specialMechanicItems,
                this.specialMechanicSets, this.shieldTypes, this.shieldTypeParamDefaults, this.itemShieldTypes));
        long second = Integer.toUnsignedLong(Objects.hash(this.attributeDefs, this.disableTargets, this.dependencies,
                this.disableCategories, this.dependenciesAll, this.moduleTrees, this.upgradePaths,
                this.tooltipRules, this.itemDefinitions, this.itemDataPackIds));
        this.revision = (first << 32) | second;
    }

    public static DefsSnapshot capture(long loadId,
                                       Map<String, Set<String>> itemSets,
                                       Map<String, Set<String>> entitySets,
                                       Set<String> specialMechanicItems,
                                       Map<String, List<String>> specialMechanicSets,
                                       Map<String, Boolean> shieldTypes,
                                       Map<String, Map<String, DefsTypes.ParamValue>> shieldTypeParamDefaults,
                                       Map<String, List<String>> itemShieldTypes,
                                       List<DefsTypes.AttributeEntry> attributeDefs,
                                       Map<String, Set<String>> disableTargets,
                                       Map<String, Set<String>> dependencies,
                                       Map<String, Set<String>> disableCategories,
                                       Map<String, List<List<String>>> dependenciesAll,
                                       Map<String, DefsTypes.ModuleTreeDef> moduleTrees,
                                       Map<String, List<String>> upgradePaths,
                                       List<DefsTypes.TooltipRuleDef> tooltipRules,
                                       Map<String, DefsTypes.ItemDefinition> itemDefinitions,
                                       Set<String> itemDataPackIds) {
        return new DefsSnapshot(loadId, itemSets, entitySets, specialMechanicItems, specialMechanicSets, shieldTypes,
                shieldTypeParamDefaults, itemShieldTypes, attributeDefs, disableTargets, dependencies, disableCategories,
                dependenciesAll, moduleTrees, upgradePaths, tooltipRules, itemDefinitions, itemDataPackIds);
    }

    /** 诊断用计数：各 kind 的条目数（不暴露数据本体）。 */
    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("item_sets", itemSets.size());
        counts.put("entity_sets", entitySets.size());
        counts.put("special_mechanic_items", specialMechanicItems.size());
        counts.put("special_mechanic_sets", specialMechanicSets.size());
        counts.put("shield_types", shieldTypes.size());
        counts.put("shield_type_param_defaults", shieldTypeParamDefaults.size());
        counts.put("item_shield_types", itemShieldTypes.size());
        counts.put("attribute_defs", attributeDefs.size());
        counts.put("disable_targets", disableTargets.size());
        counts.put("dependencies", dependencies.size());
        counts.put("disable_categories", disableCategories.size());
        counts.put("dependencies_all", dependenciesAll.size());
        counts.put("module_trees", moduleTrees.size());
        counts.put("upgrade_paths", upgradePaths.size());
        counts.put("tooltip_rules", tooltipRules.size());
        counts.put("item_definitions", itemDefinitions.size());
        counts.put("item_data_pack_ids", itemDataPackIds.size());
        return counts;
    }

    public String revisionHex() {
        return Long.toHexString(revision);
    }

    private static Map<String, Set<String>> copySetMap(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<String>> copyListMap(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Map<String, DefsTypes.ParamValue>> copyNestedMap(
            Map<String, Map<String, DefsTypes.ParamValue>> source) {
        Map<String, Map<String, DefsTypes.ParamValue>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, DefsTypes.ParamValue>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, List<List<String>>> copyNestedListMap(Map<String, List<List<String>>> source) {
        Map<String, List<List<String>>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<String>>> entry : source.entrySet()) {
            List<List<String>> tiers = new ArrayList<>(entry.getValue().size());
            for (List<String> tier : entry.getValue()) {
                tiers.add(List.copyOf(tier));
            }
            copy.put(entry.getKey(), List.copyOf(tiers));
        }
        return Collections.unmodifiableMap(copy);
    }
}
