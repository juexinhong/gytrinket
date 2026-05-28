package com.gy_mod.gy_trinket.core.damage_last;


import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.damage.AdaptiveArmorManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public class AdaptiveArmorLastDamageHandler implements LastDamageHandler {
    private static final int PRIORITY = 100;
    private final AdaptiveArmorManager armorManager;

    public AdaptiveArmorLastDamageHandler() {
        this.armorManager = AdaptiveArmorManager.getInstance();
    }

    @Override
    public void handle(LastDamageContext context) {
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }

        if (!armorManager.hasAdaptiveArmor(player)) {
            return;
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);

        if (damageType == ModDamageTypes.FINAL_DAMAGE ||
            damageType == ModDamageTypes.SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE) {
            return;
        }

        if (damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
            return;
        }

        if (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE) {
            double layersToAdd = context.getCurrentDamage() * Config.getAdaptiveArmorLayersPerDamage();
            armorManager.addArmorLayers(player, layersToAdd);
            return;
        }

        if (!ShieldTransferManager.hasTransferredShield(player.getUUID())) {
            return;
        }

        double currentShield = ShieldManager.getCurrentShield(player.getUUID());
        if (currentShield <= 0) {
            return;
        }

        double reduction = armorManager.calculateDamageReduction(player);
        float currentDamage = context.getCurrentDamage();
        float reducedDamage = currentDamage * (1 - (float) reduction);
        context.setCurrentDamage(reducedDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
