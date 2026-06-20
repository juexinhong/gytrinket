package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.damage.ModDamageTypes;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public class BinaryProtocolHandler implements DamageHandler {

    private static final int PRIORITY = 90;

    @Override
    public void handle(DamageContext context) {
        Player player = context.getPlayer();

        if (!hasBinaryProtocolItem(player)) {
            return;
        }

        if (ShieldManager.getCurrentShield(player.getUUID()) <= 0) {
            return;
        }

        if (context.getAttackedEntity() instanceof Player attackedPlayer) {
            if (!ShieldTransferManager.shouldProtectPlayer(attackedPlayer)) {
                return;
            }
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);

        if (context.isProtocolSelfDamage()) {
            return;
        }

        float currentDamage = context.getCurrentDamage();
        if (currentDamage <= 0) {
            return;
        }
        float splitDamage = currentDamage / 2.0f;

        boolean isPlayerSelfDamage = (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE);

        if (isPlayerSelfDamage) {
            float actualShieldDamage = splitDamage;
            float shieldRemainingDamage = 0;
            double currentShield = ShieldManager.getCurrentShield(player.getUUID());
            if (splitDamage > currentShield - 1) {
                actualShieldDamage = (float) (currentShield - 1);
                shieldRemainingDamage = splitDamage - actualShieldDamage;
            }

            if (actualShieldDamage > 0) {
                player.hurt(ModDamageTypes.getProtocolShieldSelfDamageSource(player.level()), actualShieldDamage);
            }

            float playerDamage = splitDamage + shieldRemainingDamage;
            if (playerDamage > 0) {
                player.hurt(ModDamageTypes.getProtocolPlayerSelfDamageSource(player.level()), playerDamage);
            }

            context.setCanceled(true);
            return;
        }

        DamageSource protocolDamageSource = ModDamageTypes.getProtocolPlayerSelfDamageSource(player.level());

        float actualProtocolDamage = splitDamage;
        float remainingDamage = 0;

        float currentHealth = player.getHealth();
        if (splitDamage > currentHealth - 1) {
            actualProtocolDamage = currentHealth - 1;
            remainingDamage = splitDamage - actualProtocolDamage;
        }

        if (actualProtocolDamage > 0) {
            player.hurt(protocolDamageSource, actualProtocolDamage);
        }

        context.setCurrentDamage(splitDamage + remainingDamage);
    }

    private boolean hasBinaryProtocolItem(Player player) {
        return PlayerStoreUtils.hasActiveItem(player, Config::isBinaryProtocolItem);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}
