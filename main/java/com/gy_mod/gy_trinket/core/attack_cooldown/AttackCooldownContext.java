package com.gy_mod.gy_trinket.core.attack_cooldown;

import com.gy_mod.gy_trinket.core.shield.cooldown.CooldownContext;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;

import java.util.UUID;

/**
 * 攻击冷却上下文
 * <p>
 * 封装攻击冷却期间的上下文信息，供效果实现使用。
 */
public class AttackCooldownContext {

    /** 玩家UUID */
    private final UUID playerUUID;

    /** 攻击强度 (0.0f ~ 1.0f)，值越低表示攻击冷却越严重 */
    private final float attackStrength;

    /** 是否正在攻击（处于攻击冷却状态） */
    private final boolean isAttacking;

    /** 护盾冷却数据 */
    private final ShieldCooldownManager.CooldownData cooldownData;

    /** 护盾冷却上下文 */
    private final CooldownContext cooldownContext;

    /**
     * 构造攻击冷却上下文
     *
     * @param playerUUID      玩家UUID
     * @param attackStrength  攻击强度
     * @param isAttacking     是否正在攻击
     * @param cooldownData    护盾冷却数据
     * @param cooldownContext 护盾冷却上下文
     */
    public AttackCooldownContext(UUID playerUUID, float attackStrength, boolean isAttacking,
                                ShieldCooldownManager.CooldownData cooldownData, CooldownContext cooldownContext) {
        this.playerUUID = playerUUID;
        this.attackStrength = attackStrength;
        this.isAttacking = isAttacking;
        this.cooldownData = cooldownData;
        this.cooldownContext = cooldownContext;
    }

    /**
     * 获取玩家UUID
     *
     * @return 玩家UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * 获取攻击强度
     * <p>
     * 值范围：0.0f ~ 1.0f
     * - 0.0f 表示刚发起攻击，冷却最严重
     * - 1.0f 表示攻击冷却完全恢复
     *
     * @return 攻击强度
     */
    public float getAttackStrength() {
        return attackStrength;
    }

    /**
     * 判断是否正在攻击（处于攻击冷却状态）
     *
     * @return true表示正在攻击
     */
    public boolean isAttacking() {
        return isAttacking;
    }

    /**
     * 获取护盾冷却数据
     *
     * @return 护盾冷却数据
     */
    public ShieldCooldownManager.CooldownData getCooldownData() {
        return cooldownData;
    }

    /**
     * 获取护盾冷却上下文
     *
     * @return 护盾冷却上下文
     */
    public CooldownContext getCooldownContext() {
        return cooldownContext;
    }

    /**
     * 获取攻击进度
     * <p>
     * 计算公式：1.0f - attackStrength
     * 值范围：0.0f ~ 1.0f
     * - 0.0f 表示攻击冷却已完成
     * - 1.0f 表示刚发起攻击
     *
     * @return 攻击进度（冷却完成度的反向值）
     */
    public float getAttackProgress() {
        return 1.0f - attackStrength;
    }
}