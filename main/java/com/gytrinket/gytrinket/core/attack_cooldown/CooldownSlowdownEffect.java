package com.gytrinket.gytrinket.core.attack_cooldown;

import com.gytrinket.gytrinket.core.shield.cooldown.ShieldCooldownManager;

/**
 * 冷却减慢效果
 * <p>
 * 在玩家攻击冷却期间减慢护盾冷却速度。
 * <p>
 * 效果机制：
 * - 固定减慢倍率：1.25x（即增加25%冷却时间）
 * - 当玩家处于攻击冷却状态时，护盾最大冷却时间增加25%
 * - 只在需要时应用效果，避免重复更新
 */
public class CooldownSlowdownEffect implements IAttackCooldownEffect {

    /** 效果名称 */
    public static final String NAME = "cooldown_slowdown";

    /** 减慢倍率：增加25%冷却时间 */
    private static final float SLOWDOWN_MULTIPLIER = 1.25f;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public void applyEffect(AttackCooldownContext context) {
        Integer baseMaxCooldown = ShieldCooldownManager.BASE_MAX_COOLDOWN.get(context.getPlayerUUID());
        if (baseMaxCooldown == null || baseMaxCooldown <= 0) {
            return;
        }

        int targetMaxCooldown = (int) (baseMaxCooldown * SLOWDOWN_MULTIPLIER);
        int currentMaxCooldown = context.getCooldownData().getMaxCooldown();

        if (currentMaxCooldown != targetMaxCooldown) {
            context.getCooldownData().updateMaxCooldown(targetMaxCooldown);
        }
    }
}
