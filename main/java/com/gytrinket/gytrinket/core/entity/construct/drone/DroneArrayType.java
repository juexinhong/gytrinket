package com.gytrinket.gytrinket.core.entity.construct.drone;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.disable.DisableSystem;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.IDroneBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.OrbitBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.PursuitBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.StandbyBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.FormationBehavior;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.GuardBehavior;

import java.util.*;

public class DroneArrayType {
    private final String id;
    private final String name;
    private final Set<String> tags;
    private final int priority;
    private final IDroneBehavior behavior;

    DroneArrayType(String id, String name, Set<String> tags, int priority, IDroneBehavior behavior) {
        this.id = id;
        this.name = name;
        this.tags = tags;
        this.priority = priority;
        this.behavior = behavior;
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

    public Set<String> getRequiredItemIds() {
        return getRequiredItemIdsFromConfig();
    }

    private Set<String> getRequiredItemIdsFromConfig() {
        return switch (id) {
            case "pursuit" -> new HashSet<>(Config.PURSUIT_ARRAY_REQUIRED_ITEMS.get());
            case "formation" -> new HashSet<>(Config.FORMATION_ARRAY_REQUIRED_ITEMS.get());
            case "guard" -> new HashSet<>(Config.GUARD_ARRAY_REQUIRED_ITEMS.get());
            default -> Collections.emptySet();
        };
    }

    public boolean hasRequiredItems(java.util.UUID playerUUID) {
        Set<String> required = getRequiredItemIdsFromConfig();
        if (required.isEmpty()) {
            return true;
        }
        com.gytrinket.gytrinket.storage.PlayerStore store =
                com.gytrinket.gytrinket.storage.PlayerStoreManager.getPlayerStore(playerUUID);
        if (store == null) {
            return false;
        }
        Set<String> ownedItemIds = new HashSet<>();
        for (int i = 0; i < store.getItemHandler().getSlots(); i++) {
            net.minecraft.world.item.ItemStack stack = store.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (DisableSystem.isItemDisabled(playerUUID, stack)) continue;
                ownedItemIds.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
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
                ORBIT_BEHAVIOR
        );

        public static final DroneArrayType STANDBY = new DroneArrayType(
                "standby",
                "待机",
                Set.of(Tags.ARRAY, Tags.STANDBY),
                20,
                STANDBY_BEHAVIOR
        );

        public static final DroneArrayType PURSUIT = new DroneArrayType(
                "pursuit",
                "追击",
                Set.of(Tags.ARRAY, Tags.PURSUIT),
                15,
                PURSUIT_BEHAVIOR
        );

        public static final DroneArrayType FORMATION = new DroneArrayType(
                "formation",
                "列队",
                Set.of(Tags.ARRAY, Tags.FORMATION),
                25,
                FORMATION_BEHAVIOR
        );

        public static final DroneArrayType GUARD = new DroneArrayType(
                "guard",
                "守卫",
                Set.of(Tags.ARRAY, Tags.GUARD),
                30,
                GUARD_BEHAVIOR
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
