package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;

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

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
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