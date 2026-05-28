package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.ReshapingBehavior;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ReshapingDamageHandler implements DamageHandler {

    private static final int PRIORITY = 25;

    @Override
    public void handle(DamageContext context) {
        if (context.isCanceled()) return;

        LivingEntity attackedEntity = context.getAttackedEntity();
        if (!(attackedEntity instanceof Player player)) return;

        if (!ShieldTransferManager.shouldProtectPlayer(player)) {
            return;
        }

        float reduction = ReshapingBehavior.getPlayerDamageReduction(player);
        if (reduction <= 0.0f) return;

        UUID playerUUID = player.getUUID();
        double currentShield = ShieldManager.getCurrentShield(playerUUID);
        if (currentShield <= 0) return;

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
