package com.gy_mod.gy_trinket.core.entity.construct.drone.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 无人机效果接口
 * <p>
 * 效果是对无人机能力的修饰，如突击（增加攻击）、防御（增加护盾）等。
 * 效果不影响实体的创建，只影响实体的能力。
 */
public interface IDroneEffect {

    /**
     * 是否为战斗效果
     * @return 如果是战斗相关效果（如攻击）返回true
     */
    boolean isCombatEffect();

    /**
     * 应用效果到目标
     * @param drone 无人机实体
     * @param owner 拥有者
     * @param target 目标
     * @param canAttack 是否可以攻击
     */
    default void apply(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
    }

    /**
     * 每tick调用
     * @param drone 无人机实体
     * @param owner 拥有者
     */
    default void onTick(Entity drone, LivingEntity owner) {
    }

    String getName();

    default String getTagId() {
        return getName();
    }
}
