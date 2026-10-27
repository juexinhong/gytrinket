package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.core.damage.ModDamageTypes;
import com.gytrinket.gytrinket.storage.PlayerStoreUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class BinaryProtocolHandler implements DamageHandler {

    private static final int PRIORITY = 90;

    @Override
    public void handle(DamageContext context) {
        Player player = context.getPlayer();
        LivingEntity attackedEntity = context.getAttackedEntity();

        if (!hasBinaryProtocolItem(player)) {
            return;
        }

        if (ShieldManager.getCurrentShield(player.getUUID()) <= 0) {
            return;
        }

        if (attackedEntity instanceof Player attackedPlayer) {
            if (!ShieldTransferManager.shouldProtectPlayer(attackedPlayer)) {
                return;
            }
        }

        GyDamageType damageType = ModDamageTypes.typeOf(context.getSource());

        if (context.isProtocolSelfDamage()) {
            return;
        }

        float currentDamage = context.getCurrentDamage();
        if (currentDamage <= 0) {
            return;
        }
        float splitDamage = currentDamage / 2.0f;

        // 二元协议的另一半由"被保护的一方"承担：
        // 未移植时 attackedEntity 就是玩家；护盾移植后是被保护实体，
        // 不能让护盾主人在远处替它扣血。
        LivingEntity damageTarget = attackedEntity;

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
                player.hurt(ModDamageTypes.getProtocolShieldSelfDamageSource(player.getLevel()), actualShieldDamage);
            }

            float victimDamage = splitDamage + shieldRemainingDamage;
            if (victimDamage > 0) {
                damageTarget.hurt(ModDamageTypes.getProtocolPlayerSelfDamageSource(damageTarget.getLevel()), victimDamage);
            }

            context.setCanceled(true);
            return;
        }

        DamageSource protocolDamageSource = ModDamageTypes.getProtocolPlayerSelfDamageSource(damageTarget.getLevel());

        float actualProtocolDamage = splitDamage;
        float remainingDamage = 0;

        float currentHealth = damageTarget.getHealth();
        if (splitDamage > currentHealth - 1) {
            actualProtocolDamage = currentHealth - 1;
            remainingDamage = splitDamage - actualProtocolDamage;
        }

        if (actualProtocolDamage > 0) {
            damageTarget.hurt(protocolDamageSource, actualProtocolDamage);
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
