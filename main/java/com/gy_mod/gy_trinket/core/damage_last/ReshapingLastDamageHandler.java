package com.gy_mod.gy_trinket.core.damage_last;

import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
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

        float reduction = ReshapingBehavior.getPlayerDamageReduction(player);
        if (reduction <= 0.0f) return;

        UUID playerUUID = player.getUUID();
        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield > 0) return;

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
