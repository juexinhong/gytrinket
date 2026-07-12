package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import net.minecraft.world.entity.LivingEntity;

/**
 * 目标记忆数据结构
 * <p>
 * 用于构造体短暂记忆最近选定的攻击目标，避免在视野内无目标时立即丢失追击。
 * 当记忆未过期且目标仍存活时，可继续沿用该目标。
 */
public class TargetMemory {
    public LivingEntity target;
    public long endTick;

    public TargetMemory(LivingEntity target, long endTick) {
        this.target = target;
        this.endTick = endTick;
    }
}
