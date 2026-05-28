package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ReshapingLastDamageHandler implements LastDamageHandler {

    private static final int PRIORITY = 95;

    @Override
    public void handle(LastDamageContext context) {
        if (context.isCanceled()) return;

        LivingEntity entity = context.getEntity();
        if (!(entity instanceof Player player)) return;

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.FINAL_DAMAGE ||
            damageType == ModDamageTypes.SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE) {
            return;
        }

        float reduction = ReshapingBehavior.getPlayerDamageReduction(player);
        if (reduction <= 0.0f) return;

        UUID playerUUID = player.getUUID();
        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield > 0 && !ShieldTransferManager.hasTransferredShield(playerUUID)) {
            return;
        }

        float currentDamage = context.getCurrentDamage();
        float reductionMultiplier = 1.0f - (reduction / 100.0f);
        reductionMultiplier = Math.max(0.0f, reductionMultiplier);
        float newDamage = currentDamage * reductionMultiplier;

        context.setCurrentDamage(newDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
