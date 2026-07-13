package com.gy_mod.gy_trinket.core.taskmaster;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructCategory;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;

import net.minecraft.world.entity.player.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 督战者管理器
 * <p>
 * 督战者机制：
 * - 玩家获得x%动态百分比构造体构建速度属性
 * - 玩家获得-x%动态独立乘区移动速度属性
 * - x = 2 × (所有可构建的标准构造体类型的最终数量上限之和 + 所有可构建的高阶构造体类型的最终数量上限之和)
 * - 禁用玩家攻击（不允许攻击实体，但允许破坏方块）
 */
public class TaskmasterManager {

    private static final String NAMESPACE = "taskmaster";

    /** 拥有督战者效果的玩家集合 */
    private static final Set<UUID> PLAYER_HAS_TASKMASTER = new CopyOnWriteArraySet<>();

    private TaskmasterManager() {}

    /**
     * 判断玩家是否拥有督战者效果
     */
    public static boolean hasTaskmaster(Player player) {
        return PLAYER_HAS_TASKMASTER.contains(player.getUUID());
    }

    /**
     * 计算并更新督战者的动态属性
     *
     * @param playerUUID 玩家UUID
     * @param player     玩家实例
     */
    public static void updateDynamicAttributes(UUID playerUUID, Player player) {
        PLAYER_HAS_TASKMASTER.add(playerUUID);

        double standardLimit = getConstructLimitByTier(playerUUID, player, ConstructCategory.STANDARD);
        double advancedLimit = getConstructLimitByTier(playerUUID, player, ConstructCategory.ADVANCED);

        double x = 2.0 * (standardLimit + advancedLimit);

        // 设置动态属性：+x% 构造体构建速度
        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE,
                "construct_build_speed_percent", x / 100.0);

        // 设置动态属性：-x% 独立乘区移动速度
        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE,
                "movement_speed_independent", -x / 100.0);
    }

    /**
     * 移除督战者的动态属性
     *
     * @param playerUUID 玩家UUID
     */
    public static void removeDynamicAttributes(UUID playerUUID) {
        PLAYER_HAS_TASKMASTER.remove(playerUUID);
        AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE,
                "construct_build_speed_percent");
        AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE,
                "movement_speed_independent");
    }

    /**
     * 获取指定等级的所有可构建构造体类型的最终数量上限之和
     * <p>
     * 遍历所有已注册的构造体类型，筛选包含指定等级类别的类型，
     * 仅计入玩家满足构建条件的类型，累加其最终数量上限。
     *
     * @param playerUUID 玩家UUID
     * @param player     玩家实例
     * @param tier       等级类别（STANDARD 或 ADVANCED）
     * @return 符合条件的可构建构造体类型的最终数量上限之和
     */
    private static double getConstructLimitByTier(UUID playerUUID, Player player, ConstructCategory tier) {
        ConstructManager manager = ConstructManager.getInstance();
        double totalLimit = 0;
        for (ConstructType type : manager.getAllConstructTypes()) {
            if (type.getCategories().contains(tier) && manager.canPlayerBuildConstruct(player, type.getId())) {
                totalLimit += ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);
            }
        }
        return totalLimit;
    }
}
