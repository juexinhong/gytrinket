package com.gytrinket.gytrinket.core.shield.cooldown;

import com.gytrinket.gytrinket.core.attribute.AttributeManager;

public class DamageReductionModifier implements IShieldCooldownModifier {

    public static final String NAME = "damage_reduction";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public void onDamageTaken(ShieldCooldownManager.CooldownData state, CooldownContext context, float damage) {
        if (state.isComplete() || state.getCurrentCooldown() <= 0) {
            return;
        }

        float reducedDamage = context.getCurrentDamage();
        if (reducedDamage <= 0) {
            return;
        }

        float cooldownExtend = (float) AttributeManager.getPlayerAttribute(context.getPlayerUUID(), "shield_hit_cooldown_extend");
        float multiplierValue = (float) AttributeManager.getPlayerAttribute(context.getPlayerUUID(), "shield_hit_cooldown_extend_multiplier");
        float finalMultiplier = (float) AttributeManager.getPlayerAttribute(context.getPlayerUUID(), "shield_hit_cooldown_extend_final_multiplier");

        // 伤害<=1时，冷却延长值-10，但不低于10（原值<10时取原值，不可为负）
        if (reducedDamage <= 1.0f) {
            cooldownExtend = Math.max(cooldownExtend < 5 ? cooldownExtend : 5, cooldownExtend - 35);
        }

        if (cooldownExtend == 0) {
            state.reset();
            return;
        }

        float multiplier = multiplierValue - 1;
        float multiplierResult = 1.0f + Math.max(0, reducedDamage - 1) * multiplier;
        if (multiplierResult < 1.0f) {
            multiplierResult = 1.0f;
        }

        float finalResult = multiplierResult * cooldownExtend;

        int reduction = (int) (finalResult * finalMultiplier);
        if (reduction <= 0) {
            return;
        }

        int newCooldown = Math.max(0, state.getCurrentCooldown() - reduction);
        state.setCurrentCooldown(newCooldown);
    }
}