package com.gy_mod.gy_trinket.core.shield.cooldown;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;

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

        if (cooldownExtend == 0) {
            state.reset();
            return;
        }

        float multiplier = multiplierValue - 1;
        float multiplierResult = 1.0f + reducedDamage * multiplier;
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