package com.gy_mod.gy_trinket.core.burn;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 灼烧数据类
 * <p>
 * 存储单个实体的灼烧状态信息
 * 包括：累加伤害、计时器、所有灼烧源贡献
 */
public class BurnData {

    /** 灼烧目标UUID */
    private final UUID targetUUID;
    /** 灼烧目标引用 */
    private LivingEntity target;
    /** 累加的灼烧伤害 */
    private float accumulatedDamage;
    /** 灼烧开始时间（游戏刻） */
    private long startTick;
    /** 灼烧持续时间（游戏刻，1秒=20刻） */
    private final int durationTicks;
    /** 是否已触发过一次斩杀检查 */
    private boolean killCheckPerformed;
    /** 灼烧源贡献映射（UUID -> 贡献伤害值） */
    private final Map<UUID, Float> sourceContributions;
    /** 灼烧源实体映射（UUID -> 实体引用） */
    private final Map<UUID, Entity> sourceEntities;

    /**
     * 构造函数
     * @param target 灼烧目标
     * @param durationTicks 持续时间（游戏刻，默认1秒=20刻）
     */
    public BurnData(LivingEntity target, int durationTicks) {
        this.targetUUID = target.getUUID();
        this.target = target;
        this.accumulatedDamage = 0f;
        this.startTick = -1;
        this.durationTicks = durationTicks;
        this.killCheckPerformed = false;
        this.sourceContributions = new HashMap<>();
        this.sourceEntities = new HashMap<>();
    }

    /**
     * 开始灼烧
     * @param currentTick 当前游戏刻
     */
    public void startBurn(long currentTick) {
        if (!isBurning()) {
            this.startTick = currentTick;
            this.accumulatedDamage = 0f;
            this.killCheckPerformed = false;
            this.sourceContributions.clear();
        }
    }

    /**
     * 检查是否正在灼烧
     * @return 如果正在灼烧返回true
     */
    public boolean isBurning() {
        return startTick >= 0;
    }

    /**
     * 添加灼烧充能
     * @param chargeAmount 充能量（伤害值）
     * @param source 灼烧源
     * @param currentTick 当前游戏刻
     */
    public void addCharge(float chargeAmount, IBurnSource source, long currentTick) {
        if (!isBurning()) {
            startBurn(currentTick);
        }
        this.accumulatedDamage += chargeAmount;

        UUID sourceUUID = source.getInitiatorUUID();
        if (sourceUUID != null) {
            sourceContributions.merge(sourceUUID, chargeAmount, Float::sum);
            Entity initiator = source.getInitiator();
            if (initiator != null) {
                sourceEntities.put(sourceUUID, initiator);
            }
        }
    }

    /**
     * 获取主要灼烧源（贡献最多的）
     * @return 主要灼烧源，如果没有则返回null
     */
    public IBurnSource getPrimarySource() {
        UUID primaryUUID = getPrimarySourceUUID();
        if (primaryUUID != null) {
            return sourceContributions.containsKey(primaryUUID) ? new IBurnSource.DefaultBurnSource(target) : null;
        }
        return null;
    }

    /**
     * 获取主要灼烧源的UUID
     * @return 贡献最多的灼烧源UUID
     */
    public UUID getPrimarySourceUUID() {
        UUID primaryUUID = null;
        float maxContribution = 0f;
        for (Map.Entry<UUID, Float> entry : sourceContributions.entrySet()) {
            if (entry.getValue() > maxContribution) {
                maxContribution = entry.getValue();
                primaryUUID = entry.getKey();
            }
        }
        return primaryUUID;
    }

    /**
     * 获取主要灼烧源的实体
     * @return 主要灼烧源实体
     */
    public net.minecraft.world.entity.Entity getPrimaryInitiator() {
        UUID primaryUUID = getPrimarySourceUUID();
        if (primaryUUID != null) {
            return sourceEntities.get(primaryUUID);
        }
        return null;
    }

    /**
     * 检查灼烧是否完成（达到指定时长）
     * @param currentTick 当前游戏刻
     * @return 如果灼烧完成返回true
     */
    public boolean isComplete(long currentTick) {
        if (!isBurning()) {
            return false;
        }
        return (currentTick - startTick) >= durationTicks;
    }

    /**
     * 获取灼烧进度（0.0 - 1.0）
     * @param currentTick 当前游戏刻
     * @return 灼烧进度
     */
    public float getProgress(long currentTick) {
        if (!isBurning()) {
            return 0f;
        }
        long elapsed = currentTick - startTick;
        return Math.min(1f, (float) elapsed / durationTicks);
    }

    /**
     * 获取累加伤害
     * @return 累加的伤害值
     */
    public float getAccumulatedDamage() {
        return accumulatedDamage;
    }

    /**
     * 获取累加伤害（至少1点，用于斩杀）
     * @return 至少为1的伤害值
     */
    public float getAccumulatedDamageOrMin() {
        return Math.max(1f, accumulatedDamage);
    }

    /**
     * 获取灼烧目标
     * @return 灼烧目标实体
     */
    public LivingEntity getTarget() {
        return target;
    }

    /**
     * 获取目标UUID
     * @return 目标UUID
     */
    public UUID getTargetUUID() {
        return targetUUID;
    }

    /**
     * 重置灼烧状态
     */
    public void reset() {
        this.startTick = -1;
        this.accumulatedDamage = 0f;
        this.killCheckPerformed = false;
        this.sourceContributions.clear();
    }

    /**
     * 获取指定灼烧源的贡献值
     * @param sourceUUID 灼烧源UUID
     * @return 贡献值，如果没有返回0
     */
    public float getContribution(UUID sourceUUID) {
        return sourceContributions.getOrDefault(sourceUUID, 0f);
    }

    /**
     * 获取所有灼烧源贡献
     * @return 不可修改的贡献映射副本
     */
    public Map<UUID, Float> getAllContributions() {
        return new HashMap<>(sourceContributions);
    }

    /**
     * 检查是否已进行过斩杀检查
     * @return 是否已检查
     */
    public boolean isKillCheckPerformed() {
        return killCheckPerformed;
    }

    /**
     * 设置斩杀检查已完成
     */
    public void setKillCheckPerformed() {
        this.killCheckPerformed = true;
    }
}