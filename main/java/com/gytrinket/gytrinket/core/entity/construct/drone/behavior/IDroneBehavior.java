package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * 无人机行为接口
 * <p>
 * 行为通过标签匹配选择，标签可叠加：如 "array+orbit+combat+assault" 选择对应行为。
 */
public interface IDroneBehavior {
    /**
     * 获取匹配该行为的标签集合
     */
    Set<String> getRequiredTags();

    /**
     * 更新无人机位置
     */
    Vec3 updatePosition(Entity drone, LivingEntity owner, float orbitAngle, float deltaTime);

    /**
     * 搜索目标
     */
    List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range);

    /**
     * 执行攻击
     */
    void executeAttack(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack);

    /**
     * 获取攻击间隔（秒）
     */
    float getAttackInterval();

    /**
     * 获取攻击范围
     */
    float getAttackRange();

    /**
     * 检查是否处于战斗模式
     */
    boolean isCombatMode();
}
