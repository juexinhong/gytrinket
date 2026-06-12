package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.core.shield.cooldown.IShieldCooldownModifier;
import com.gy_mod.gy_trinket.core.shield.cooldown.CooldownContext;
import com.gy_mod.gy_trinket.core.shield.cooldown.ShieldCooldownManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public class DamageNotificationHandler implements DamageHandler {

    private static final int PRIORITY = 30;

    @Override
    public void handle(DamageContext context) {
        if (InvincibilityMarkerManager.hasMarker(context.getAttackedEntity())) {
            return;
        }

        var playerUUID = context.getPlayer().getUUID();

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE
                || damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE
                || damageType == ModDamageTypes.SHIELD_SELF_DAMAGE
                || damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE) {
            return;
        }

        if (!(context.getAttackedEntity() instanceof Player)) {
            return;
        }

        ShieldCooldownManager.CooldownData cooldownData = ShieldCooldownManager.getCooldownData(playerUUID);
        if (cooldownData == null || cooldownData.isComplete()) {
            return;
        }

        float originalDamage = context.getOriginalDamage();
        float currentDamage = context.getCurrentDamage();
        CooldownContext cooldownContext = ShieldCooldownManager.createContext(playerUUID);
        cooldownContext.setOriginalDamage(originalDamage);
        cooldownContext.setCurrentDamage(currentDamage);

        for (IShieldCooldownModifier modifier : ShieldCooldownManager.getModifiers()) {
            modifier.onDamageTaken(cooldownData, cooldownContext, originalDamage);
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}