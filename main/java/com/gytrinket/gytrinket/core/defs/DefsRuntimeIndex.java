package com.gytrinket.gytrinket.core.defs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 定义数据派生的 <b>物品级运行时索引</b>（P5-5c）。
 * <p>
 * 数据包定义（{@link DefsManager} 快照）以字符串 id 保存，热路径（tick/伤害/渲染）需要按
 * {@link Item} 实例直接判定。本类把 5 类派生表编译成不可变索引，构建后整体原子替换：
 * <ul>
 *   <li>模块物品集合（module name → 物品集合）；</li>
 *   <li>物品 → 护盾类型（{@link IdentityHashMap}，物品 equals 即引用）；</li>
 *   <li>特殊机制物品集合；</li>
 *   <li>危险实体集合；</li>
 *   <li>护盾类型兼容性。</li>
 * </ul>
 * 构建时机与旧 {@code Config} 派生表一致：服务端数据包重载（全量重建）与客户端
 * {@code ConfigDefsSyncMessage} 同步（仅物品集合/特殊机制重建，其余沿用上一份索引）。
 * 读侧只做一次 volatile 读 + identity 查表，不分配。
 */
public final class DefsRuntimeIndex {

    /** 模块名 → datapack 物品集合名 的绑定表（服务端 applyDefs 与客户端 applyClientDefs 共用） */
    private static final String[][] ITEM_SET_BINDINGS = {
            {"body", "body_items"},
            {"adaptive_armor", "adaptive_armor_items"},
            {"adaptive_armor_shield_effect", "adaptive_armor_shield_effect_items"},
            {"electric_discharge", "electric_discharge_items"},
            {"attack_cooldown_efficiency", "attack_cooldown_efficiency_items"},
            {"barrier", "barrier_items"},
            {"shield_natural_recovery", "shield_natural_recovery_items"},
            {"reflect_damage", "reflect_damage_items"},
            {"explosive_shield", "explosive_shield_items"},
            {"shield_transfer", "shield_transfer_items"},
            {"binary_protocol", "binary_protocol_items"},
            {"weaponized_shield", "weaponized_shield_items"},
            {"near_death_protection", "near_death_protection_items"},
            {"near_death_explosion", "near_death_explosion_items"},
            {"self_destruct", "self_destruct_items"},
            {"taskmaster", "taskmaster_items"},
            {"furnace_core", "furnace_core_items"},
            {"drone_module", "drone_module_items"},
            {"assault_drone_module", "assault_drone_module_items"},
            {"commander", "commander_required_items"},
            {"defense_drone_module", "defense_drone_module_items"},
            {"wingman_module", "wingman_module_items"},
            {"interceptor_module", "wingman_interceptor_module_items"},
            {"evolution_module", "wingman_evolution_module_items"},
            {"nano_regen_module", "wingman_nano_regen_module_items"},
            {"shockwave_module", "wingman_shockwave_module_items"},
            {"swarm_module", "swarm_module_items"},
            {"arc_barrier", "arc_barrier_items"},
            {"reshaping", "reshaping_items"},
            {"counter_pulse", "counter_pulse_items"},
            {"assault", "assault_items"},
            {"charged_attack", "charged_attack_items"},
            {"charged_shield", "charged_shield_items"},
            {"grudge", "grudge_items"},
            {"precision_construct", "precision_construct_items"},
            {"advanced_engineering", "advanced_engineering_items"},
            {"pursuit_array", "pursuit_array_required_items"},
            {"formation_array", "formation_array_required_items"},
            {"guard_array", "guard_array_required_items"},
            {"ghost_fuselage", "ghost_fuselage_items"},
            {"conversion", "conversion_items"},
            {"journey_module", "journey_module_items"},
    };

    private static final DefsRuntimeIndex EMPTY =
            new DefsRuntimeIndex(Map.of(), Map.of(), Set.of(), Set.of(), Map.of());

    private static final AtomicReference<DefsRuntimeIndex> CURRENT = new AtomicReference<>(EMPTY);

    private final Map<String, Set<Item>> moduleItems;
    private final Map<Item, List<String>> itemShieldTypes;
    private final Set<Item> specialMechanicItems;
    private final Set<String> dangerousEntities;
    private final Map<String, Boolean> shieldTypeCompatibility;

    private DefsRuntimeIndex(Map<String, Set<Item>> moduleItems,
                             Map<Item, List<String>> itemShieldTypes,
                             Set<Item> specialMechanicItems,
                             Set<String> dangerousEntities,
                             Map<String, Boolean> shieldTypeCompatibility) {
        this.moduleItems = moduleItems;
        this.itemShieldTypes = itemShieldTypes;
        this.specialMechanicItems = specialMechanicItems;
        this.dangerousEntities = dangerousEntities;
        this.shieldTypeCompatibility = shieldTypeCompatibility;
    }

    /** 当前索引（永不返回 null；首次加载前为全空索引）。 */
    public static DefsRuntimeIndex current() {
        return CURRENT.get();
    }

    /** 全量重建：服务端/集成服务器数据包重载后调用（对应旧 Config.applyDefs 的派生段）。 */
    public static void rebuild() {
        CURRENT.set(build());
    }

    /**
     * 客户端同步重建：专用服联机客户端收到 {@code ConfigDefsSyncMessage} 后调用，
     * 只重建物品集合与特殊机制；护盾类型/危险实体/兼容性沿用上一份索引（与旧行为一致）。
     */
    public static void rebuildClientSync() {
        DefsRuntimeIndex previous = CURRENT.get();
        DefsRuntimeIndex rebuilt = build();
        CURRENT.set(new DefsRuntimeIndex(rebuilt.moduleItems, previous.itemShieldTypes,
                rebuilt.specialMechanicItems, previous.dangerousEntities, previous.shieldTypeCompatibility));
    }

    private static DefsRuntimeIndex build() {
        // 护盾类型兼容性
        Map<String, Boolean> shieldTypeCompatibility = new HashMap<>(DefsManager.getShieldTypes());

        // 物品 -> 护盾类型
        Map<Item, List<String>> itemShieldTypes = new IdentityHashMap<>();
        for (Map.Entry<String, List<String>> entry : DefsManager.getItemShieldTypes().entrySet()) {
            Item item = itemFromId(entry.getKey());
            if (item != null) {
                itemShieldTypes.put(item, List.copyOf(entry.getValue()));
            }
        }

        // 模块物品集合
        Map<String, Set<Item>> moduleItems = new HashMap<>();
        for (String[] binding : ITEM_SET_BINDINGS) {
            Set<Item> itemSet = identitySet();
            for (String itemId : DefsManager.getItemSet(binding[1])) {
                Item item = itemFromId(itemId);
                if (item != null) {
                    itemSet.add(item);
                }
            }
            moduleItems.put(binding[0], Collections.unmodifiableSet(itemSet));
        }

        // 特殊机制物品集合
        Set<Item> specialMechanicItems = identitySet();
        for (String itemId : DefsManager.getSpecialMechanicItems()) {
            Item item = itemFromId(itemId);
            if (item != null) {
                specialMechanicItems.add(item);
            }
        }

        // 危险实体
        Set<String> dangerousEntities = new HashSet<>(DefsManager.getEntitySet("dangerous_entities"));

        return new DefsRuntimeIndex(Collections.unmodifiableMap(moduleItems),
                Collections.unmodifiableMap(itemShieldTypes),
                Collections.unmodifiableSet(specialMechanicItems),
                Collections.unmodifiableSet(dangerousEntities),
                Collections.unmodifiableMap(shieldTypeCompatibility));
    }

    private static Item itemFromId(String itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        return item == null || item == Items.AIR ? null : item;
    }

    private static Set<Item> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    // ===== 查询 =====

    /** 物品是否属于指定模块集合。 */
    public boolean isModuleItem(String moduleName, Item item) {
        Set<Item> itemSet = moduleItems.get(moduleName);
        return itemSet != null && itemSet.contains(item);
    }

    /** 物品是否声明了特殊机制。 */
    public boolean isSpecialMechanicItem(Item item) {
        return specialMechanicItems.contains(item);
    }

    /** 实体 id 是否属于危险实体集合。 */
    public boolean isDangerousEntity(String entityId) {
        return dangerousEntities.contains(entityId);
    }

    /** 物品声明的护盾类型（无声明返回空列表）。 */
    public List<String> itemShieldTypes(Item item) {
        return itemShieldTypes.getOrDefault(item, List.of());
    }

    /** 护盾类型是否兼容（未声明默认兼容）。 */
    public boolean isShieldTypeCompatible(String typeName) {
        return shieldTypeCompatibility.getOrDefault(typeName, true);
    }

    // ===== 诊断 dump（P5 golden 快照，稳定排序） =====

    /**
     * 把 5 类派生表写入诊断 dump（结构与 P5-0 golden 完全一致，供 {@code /gytrinket defs dump} diff=0 门禁）。
     */
    public void dumpDerivedForDiagnostics(Map<String, Object> out) {
        Map<String, List<String>> moduleSets = new TreeMap<>();
        for (Map.Entry<String, Set<Item>> entry : moduleItems.entrySet()) {
            moduleSets.put(entry.getKey(), sortedItemIds(entry.getValue()));
        }
        out.put("moduleItemSets", moduleSets);
        out.put("specialMechanicItems", sortedItemIds(specialMechanicItems));

        Map<String, List<String>> shieldTypesByItem = new TreeMap<>();
        for (Map.Entry<Item, List<String>> entry : itemShieldTypes.entrySet()) {
            String id = itemId(entry.getKey());
            if (id == null) {
                continue;
            }
            List<String> types = new ArrayList<>(entry.getValue());
            Collections.sort(types);
            shieldTypesByItem.put(id, types);
        }
        out.put("itemShieldTypes", shieldTypesByItem);
        out.put("shieldTypeCompatibility", new TreeMap<>(shieldTypeCompatibility));
        out.put("dangerousEntities", new ArrayList<>(new TreeSet<>(dangerousEntities)));
    }

    private static List<String> sortedItemIds(Set<Item> items) {
        List<String> ids = new ArrayList<>();
        for (Item item : items) {
            String id = itemId(item);
            if (id != null) {
                ids.add(id);
            }
        }
        Collections.sort(ids);
        return ids;
    }

    private static String itemId(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? null : key.toString();
    }

    /** 调试/诊断用：索引规模（不暴露数据本体）。 */
    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int moduleItemsTotal = 0;
        for (Set<Item> items : moduleItems.values()) {
            moduleItemsTotal += items.size();
        }
        counts.put("module_sets", moduleItems.size());
        counts.put("module_items", moduleItemsTotal);
        counts.put("item_shield_types", itemShieldTypes.size());
        counts.put("special_mechanic_items", specialMechanicItems.size());
        counts.put("dangerous_entities", dangerousEntities.size());
        counts.put("shield_types", shieldTypeCompatibility.size());
        return counts;
    }

    @Override
    public String toString() {
        return "DefsRuntimeIndex" + counts();
    }
}
