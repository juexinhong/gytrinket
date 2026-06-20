package com.gytrinket.gytrinket.core.damage_last;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.attribute.AttributeManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public class CoatingDamageLastHandler implements LastDamageHandler {

    private static final int PRIORITY = 10;

    @Override
    public void handle(LastDamageContext context) {
        if (!(context.getEntity() instanceof Player player)) {
            return;
        }

        DamageSource source = context.getSource();
        ResourceKey<DamageType> damageType = source.typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.PLAYER_SELF_DAMAGE ||
            damageType == ModDamageTypes.SHIELD_SELF_DAMAGE ||
            damageType == ModDamageTypes.FINAL_DAMAGE) {
            return;
        }

        double coating = AttributeManager.getPlayerAttribute(player.getUUID(), "coating");
        if (coating <= 0) {
            return;
        }

        double reductionPerLayer = Config.getCoatingReductionPerLayer();
        float currentDamage = context.getCurrentDamage();
        if (currentDamage < reductionPerLayer) {
            return;
        }

        double damageReduction = coating * reductionPerLayer;
        float newDamage = (float) Math.max(currentDamage - damageReduction, reductionPerLayer);

        context.setCurrentDamage(newDamage);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
