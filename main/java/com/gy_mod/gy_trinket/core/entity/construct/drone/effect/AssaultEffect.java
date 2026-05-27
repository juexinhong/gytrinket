package com.gy_mod.gy_trinket.core.entity.construct.drone.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;


/**
 * 突击效果
 * <p>
 * 只是一个标签效果，不会改变无人机行为。
 * 会提供攻击速度+20%的属性修改。
 */
public class AssaultEffect implements IDroneEffect {
    @Override
    public boolean isCombatEffect() {
        return false; // 不再是战斗效果
    }

    @Override
    public void apply(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
        // 突击效果只是一个标签，属性修改已经在实体中处理
    }

    @Override
    public void onTick(Entity drone, LivingEntity owner) {
        // 突击效果只是一个标签
    }

    @Override
    public String getName() {
        return "突击";
    }

    @Override
    public String getTagId() {
        return "assault";
    }
}
