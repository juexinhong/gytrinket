package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;

public class AdaptiveArmorHandler implements DamageHandler {
    private static final int PRIORITY = 100;
    private final AdaptiveArmorManager armorManager;

    public AdaptiveArmorHandler() {
        this.armorManager = AdaptiveArmorManager.getInstance();
    }

    @Override
    public void handle(DamageContext context) {
        if (!armorManager.hasAdaptiveArmor(context.getPlayer())) {
            return;
        }

        double currentShield = ShieldManager.getCurrentShield(context.getPlayer().getUUID());
        if (currentShield <= 0) {
            return;
        }

        if (context.isPlayerSelfDamage()) {
            return;
        }

        if (!context.isShieldTransferred() && ShieldTransferManager.hasTransferredShield(context.getPlayer().getUUID())) {
            double layersToAdd = context.getCurrentDamage() * Config.getAdaptiveArmorLayersPerDamage();
            armorManager.addArmorLayers(context.getPlayer(), layersToAdd);
            return;
        }

        double reduction = armorManager.calculateDamageReduction(context.getPlayer());
        float currentDamage = context.getCurrentDamage();
        float reducedDamage = currentDamage * (1 - (float) reduction);
        context.setCurrentDamage(reducedDamage);

        double layersToAdd = context.getCurrentDamage() * Config.getAdaptiveArmorLayersPerDamage();
        armorManager.addArmorLayers(context.getPlayer(), layersToAdd);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}