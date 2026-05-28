package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.core.attribute.AttributeManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public class PlayerDamageLastHandler implements LastDamageHandler {

    private static final int PRIORITY = 100;

    @Override
    public void handle(LastDamageContext context) {
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.FINAL_DAMAGE ||
            damageType == ModDamageTypes.SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE) {
            return;
        }

        double damageReduction = AttributeManager.getPlayerAttribute(player.getUUID(), "player_damage_reduction");
        damageReduction = Math.max(damageReduction, 0.0);

        float currentDamage = context.getCurrentDamage();
        float reducedDamage = (float) (currentDamage * damageReduction);
        context.setCurrentDamage(reducedDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
