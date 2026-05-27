package com.gy_mod.gy_trinket.core.entity.construct.drone.effect;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 防御效果
 * <p>
 * 只是一个标签效果，不会改变无人机行为。
 * 会提供生命上限+150%的属性修改。
 */
public class DefenseEffect implements IDroneEffect {
    @Override
    public boolean isCombatEffect() {
        return false;
    }

    @Override
    public void onTick(Entity drone, LivingEntity owner) {
        // 防御效果只是一个标签，属性修改已经在实体中处理
    }

    @Override
    public String getName() {
        return "防御";
    }

    @Override
    public String getTagId() {
        return "defense";
    }
}
