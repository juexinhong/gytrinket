package com.gy_mod.gy_trinket.core.engineering_fuselage;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructCategory;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 工程机身管理器
 * <p>
 * 工程机身机制：
 * - 玩家每有一个未构建的标准和高阶构造体（最大可部署数量 - 当前已部署数量），提供2%动态百分比构建速度
 * <p>
 * 使用活跃实体计数（而非 ConstructData），确保在 PlayerConstructListChangedEvent 触发时
 * 能获取到实时准确的构造体数量，避免数据更新滞后导致的计数偏差。
 */
public class EngineeringFuselageManager {

    private static final String NAMESPACE = "engineering_fuselage";
    private static final double BUILD_SPEED_PER_UNDEPLOYED = 0.02;

    private EngineeringFuselageManager() {}

    /**
     * 计算并更新工程机身的动态构建速度属性
     *
     * @param playerUUID 玩家UUID
     * @param player     玩家实例
     */
    public static void updateDynamicAttributes(UUID playerUUID, Player player) {
        ConstructManager manager = ConstructManager.getInstance();

        double maxCount = 0;
        int currentCount = 0;

        for (ConstructType type : manager.getAllConstructTypes()) {
            boolean isStandardOrAdvanced = type.getCategories().contains(ConstructCategory.STANDARD)
                    || type.getCategories().contains(ConstructCategory.ADVANCED);
            if (!isStandardOrAdvanced) {
                continue;
            }

            if (manager.canPlayerBuildConstruct(player, type.getId())) {
                maxCount += ConstructAttributeApplier.getEffectiveMaxCount(playerUUID, type);
            }

            // 统计活跃实体数量（过滤已死亡/移除的实体）
            currentCount += (int) manager.getActiveConstructEntities(playerUUID, type.getId())
                    .values().stream()
                    .filter(Entity::isAlive)
                    .count();
        }

        int undeployedCount = Math.max(0, (int) maxCount - currentCount);
        double buildSpeedBonus = undeployedCount * BUILD_SPEED_PER_UNDEPLOYED;

        AttributeManager.setDynamicAttribute(playerUUID, NAMESPACE,
                "construct_build_speed_percent", buildSpeedBonus);
    }

    /**
     * 移除工程机身的动态属性
     *
     * @param playerUUID 玩家UUID
     */
    public static void removeDynamicAttributes(UUID playerUUID) {
        AttributeManager.removeDynamicAttribute(playerUUID, NAMESPACE,
                "construct_build_speed_percent");
    }
}

