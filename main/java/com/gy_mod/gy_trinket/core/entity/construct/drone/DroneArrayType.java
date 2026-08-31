package com.gy_mod.gy_trinket.core.entity.construct.drone;

import com.gy_mod.gy_trinket.config.Config;
import com.gy_mod.gy_trinket.core.defs.DefsManager;
import com.gy_mod.gy_trinket.core.shield.DisableSystem;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.IDroneBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.OrbitBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.PursuitBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.StandbyBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.FormationBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.GuardBehavior;
import com.gy_mod.gy_trinket.storage.PlayerStoreUtils;

import java.util.*;

public class DroneArrayType {
    private final String id;
    private final String name;
    private final Set<String> tags;
    private final int priority;
    private final IDroneBehavior behavior;
    private final String requiredMechanicSet;

    DroneArrayType(String id, String name, Set<String> tags, int priority, IDroneBehavior behavior, String requiredMechanicSet) {
        this.id = id;
        this.name = name;
        this.tags = tags;
        this.priority = priority;
        this.behavior = behavior;
        this.requiredMechanicSet = requiredMechanicSet;
    }

    /** 获取唯一标识符 */
    public String getId() {
        return id;
    }

    /** 获取显示名称 */
    public String getName() {
        return name;
    }

    /** 获取所有标签 */
    public Set<String> getTags() {
        return tags;
    }

    /** 获取优先级 */
    public int getPriority() {
        return priority;
    }

    public IDroneBehavior getBehavior() {
        return behavior;
    }

    /**
     * 数据驱动的特殊机制集合名称（来自 special_mechanics 定义）。
     * 非空时要求玩家装备了声明该集合的物品（覆盖层优先）；为空时始终可用（环绕/待机）。
     */
    public String getRequiredMechanicSet() {
        return requiredMechanicSet;
    }

    public Set<String> getRequiredItemIds() {
        return getRequiredItemIdsFromConfig();
    }

    private Set<String> getRequiredItemIdsFromConfig() {
        return switch (id) {
            case "pursuit" -> new HashSet<>(DefsManager.getItemSet("pursuit_array_required_items"));
            case "formation" -> new HashSet<>(DefsManager.getItemSet("formation_array_required_items"));
            case "guard" -> new HashSet<>(DefsManager.getItemSet("guard_array_required_items"));
            default -> Collections.emptySet();
        };
    }

    public boolean hasRequiredItems(java.util.UUID playerUUID) {
        Set<String> required = getRequiredItemIdsFromConfig();
        if (required.isEmpty()) {
            return true;
        }
        // 已装备物品 = 光点核心存储 + Curios 饰品栏（光点核心内容扩展）
        Set<String> ownedItemIds = new HashSet<>();
        for (net.minecraft.world.item.ItemStack stack : PlayerStoreUtils.getEquippedStacks(playerUUID)) {
            if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
            ownedItemIds.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ownedItemIds.containsAll(required);
    }

    /** 检查是否具有指定标签 */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** 检查是否具有所有指定标签 */
    public boolean hasAllTags(Set<String> targetTags) {
        return tags.containsAll(targetTags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DroneArrayType that = (DroneArrayType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * 标签定义
     */
    public static final class Tags {
        /** 必须标签：表示是无人机阵列 */
        public static final String ARRAY = "array";
        /** 基础阵列：环绕 */
        public static final String ORBIT = "orbit";
        /** 基础阵列：待机（跟随玩家） */
        public static final String STANDBY = "standby";
        /** 基础阵列：追击 */
        public static final String PURSUIT = "pursuit";
        /** 基础阵列：列队 */
        public static final String FORMATION = "formation";
        public static final String GUARD = "guard";
        /** 可选标签：突击（攻击增强） */
        public static final String ASSAULT = "assault";
        /** 可选标签：防御（防御增强） */
        public static final String DEFENSE = "defense";
        /** 状态标签：战斗 */
        public static final String COMBAT = "combat";
    }

    /**
     * 预定义的阵列类型
     */
    public static class Types {
        private static final OrbitBehavior ORBIT_BEHAVIOR = new OrbitBehavior();
        private static final StandbyBehavior STANDBY_BEHAVIOR = new StandbyBehavior();
        private static final PursuitBehavior PURSUIT_BEHAVIOR = new PursuitBehavior();
        private static final FormationBehavior FORMATION_BEHAVIOR = new FormationBehavior();
        private static final GuardBehavior GUARD_BEHAVIOR = new GuardBehavior();

        public static final DroneArrayType ORBIT = new DroneArrayType(
                "orbit",
                "环绕",
                Set.of(Tags.ARRAY, Tags.ORBIT),
                10,
                ORBIT_BEHAVIOR,
                null
        );

        public static final DroneArrayType STANDBY = new DroneArrayType(
                "standby",
                "待机",
                Set.of(Tags.ARRAY, Tags.STANDBY),
                20,
                STANDBY_BEHAVIOR,
                null
        );

        public static final DroneArrayType PURSUIT = new DroneArrayType(
                "pursuit",
                "追击",
                Set.of(Tags.ARRAY, Tags.PURSUIT),
                15,
                PURSUIT_BEHAVIOR,
                "pursuit_array_required_items"
        );

        public static final DroneArrayType FORMATION = new DroneArrayType(
                "formation",
                "列队",
                Set.of(Tags.ARRAY, Tags.FORMATION),
                25,
                FORMATION_BEHAVIOR,
                "formation_array_required_items"
        );

        public static final DroneArrayType GUARD = new DroneArrayType(
                "guard",
                "守卫",
                Set.of(Tags.ARRAY, Tags.GUARD),
                30,
                GUARD_BEHAVIOR,
                "guard_array_required_items"
        );

        /** 所有阵列类型列表 */
        public static final List<DroneArrayType> ALL_TYPES = Arrays.asList(
                ORBIT,
                PURSUIT,
                STANDBY,
                FORMATION,
                GUARD
        );

        /** 根据ID获取阵列类型 */
        public static DroneArrayType fromId(String id) {
            return ALL_TYPES.stream()
                    .filter(type -> type.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        }

        /** 获取所有具有指定标签的阵列类型 */
        public static List<DroneArrayType> getByTag(String tag) {
            return ALL_TYPES.stream()
                    .filter(type -> type.hasTag(tag))
                    .sorted(Comparator.comparingInt(DroneArrayType::getPriority))
                    .toList();
        }

        /** 检查是否匹配所有标签 */
        public static boolean matchesTags(Set<String> requiredTags, DroneArrayType arrayType) {
            if (requiredTags == null || requiredTags.isEmpty()) {
                return true;
            }
            return arrayType.hasAllTags(requiredTags);
        }
    }
}

