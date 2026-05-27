package com.gy_mod.gy_trinket.core.ignite;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 点燃数据类
 * <p>
 * 存储单个点燃效果的状态信息
 * 包括：UUID、名称、单次伤害、施加者、持续时间、计时器、是否可叠加
 */
public class IgniteData {

    /** 点燃唯一UUID */
    private final UUID igniteUUID;
    /** 点燃名称（用于区分不同类型的点燃） */
    private final String igniteName;
    /** 单次伤害（每刻伤害） */
    private final float damagePerTick;
    /** 点燃施加者 */
    private final IIgniteSource source;
    /** 点燃持续时间（游戏刻，1秒=20刻） */
    private final int durationTicks;
    /** 是否可叠加（同名是否可以有多个） */
    private final boolean stackable;
    /** 目标UUID */
    private final UUID targetUUID;
    /** 目标引用 */
    private LivingEntity target;
    /** 点燃开始时间（游戏刻） */
    private long startTick;

    /**
     * 构造函数
     * @param target 目标实体
     * @param igniteName 点燃名称
     * @param damagePerTick 单次伤害
     * @param source 点燃施加者
     * @param durationTicks 持续时间
     * @param stackable 是否可叠加
     */
    public IgniteData(LivingEntity target, String igniteName, float damagePerTick, IIgniteSource source, int durationTicks, boolean stackable) {
        this.igniteUUID = UUID.randomUUID();
        this.igniteName = igniteName;
        this.damagePerTick = damagePerTick;
        this.source = source;
        this.durationTicks = durationTicks;
        this.stackable = stackable;
        this.targetUUID = target.getUUID();
        this.target = target;
        this.startTick = -1;
    }

    /**
     * 开始点燃
     * @param currentTick 当前游戏刻
     */
    public void startIgnite(long currentTick) {
        this.startTick = currentTick;
    }

    /**
     * 检查是否正在点燃
     * @return 如果正在点燃返回true
     */
    public boolean isIgniting() {
        return startTick >= 0;
    }

    /**
     * 检查点燃是否完成（达到指定时长）
     * @param currentTick 当前游戏刻
     * @return 如果点燃完成返回true
     */
    public boolean isComplete(long currentTick) {
        if (!isIgniting()) {
            return false;
        }
        return (currentTick - startTick) >= durationTicks;
    }

    /**
     * 获取点燃进度（0.0 - 1.0）
     * @param currentTick 当前游戏刻
     * @return 点燃进度
     */
    public float getProgress(long currentTick) {
        if (!isIgniting()) {
            return 0f;
        }
        long elapsed = currentTick - startTick;
        return Math.min(1f, (float) elapsed / durationTicks);
    }

    /**
     * 获取单次伤害
     * @return 每刻伤害
     */
    public float getDamagePerTick() {
        return damagePerTick;
    }

    /**
     * 获取点燃源
     * @return 点燃源
     */
    public IIgniteSource getSource() {
        return source;
    }

    /**
     * 获取点燃施加者实体
     * @return 施加者实体
     */
    public Entity getInitiator() {
        return source != null ? source.getInitiator() : null;
    }

    /**
     * 获取点燃名称
     * @return 点燃名称
     */
    public String getIgniteName() {
        return igniteName;
    }

    /**
     * 是否可叠加
     * @return 是否可叠加
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * 获取点燃UUID
     * @return UUID
     */
    public UUID getIgniteUUID() {
        return igniteUUID;
    }

    /**
     * 获取目标UUID
     * @return 目标UUID
     */
    public UUID getTargetUUID() {
        return targetUUID;
    }

    /**
     * 获取目标
     * @return 目标实体
     */
    public LivingEntity getTarget() {
        return target;
    }

    /**
     * 更新目标引用
     * @param target 目标实体
     */
    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    /**
     * 获取持续时间
     * @return 持续时间（游戏刻）
     */
    public int getDurationTicks() {
        return durationTicks;
    }

    /**
     * 获取开始时间
     * @return 开始时间（游戏刻）
     */
    public long getStartTick() {
        return startTick;
    }
}
