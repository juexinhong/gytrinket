package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.shield.ShieldData;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield.type.ShieldTypeManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ShieldHandler implements DamageHandler {

    private static final int PRIORITY = 20;

    /**
     * 设置护盾值，根据持有者类型选择合适的方法
     */
    private static void setCurrentShieldForEntity(Player shieldOwner, UUID shieldOwnerUUID, double value) {
        if (shieldOwner instanceof ServerPlayer serverPlayer) {
            ShieldManager.setCurrentShield(serverPlayer, value);
        } else {
            ShieldManager.setCurrentShield(shieldOwnerUUID, value);
        }
    }

    @Override
    public void handle(DamageContext context) {
        Player shieldOwner = context.getShieldOwner();
        UUID shieldOwnerUUID = shieldOwner.getUUID();
        LivingEntity attackedEntity = context.getAttackedEntity();

        if (InvincibilityMarkerManager.hasMarker(attackedEntity)) {
            context.setCanceled(true);
            return;
        }

        if (context.isPlayerSelfDamage()) {
            return;
        }

        boolean isShieldSelfDamage = context.isShieldSelfDamage();

        if (!isShieldSelfDamage && attackedEntity instanceof Player) {
            if (!ShieldTransferManager.shouldProtectPlayer((Player) attackedEntity)) {
                return;
            }
        }

        ShieldData shieldData = ShieldManager.getShieldData(shieldOwnerUUID);
        if (shieldData == null) {
            return;
        }

        double currentShield = shieldData.getCurrentShield();
        if (currentShield <= 0) {
            return;
        }

        float originalDamage = context.getCurrentDamage();

        if (originalDamage <= 0) {
            return;
        }

        if (currentShield >= originalDamage) {
            setCurrentShieldForEntity(shieldOwner, shieldOwnerUUID, currentShield - originalDamage);
            context.setCanceled(true);
            
            // 当承受护盾自伤或协议护盾自伤时，不施加无敌标记
            if (!isShieldSelfDamage) {
                InvincibilityMarkerManager.addMarker(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
            }
        } else {
            setCurrentShieldForEntity(shieldOwner, shieldOwnerUUID, 0);
            context.setCanceled(true);

            // 当承受护盾自伤或协议护盾自伤时，不施加无敌标记
            if (!isShieldSelfDamage) {
                InvincibilityMarkerManager.addMarker(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
            }
        }

        if (!isShieldSelfDamage) {
            ShieldTypeManager.processReflectAfterShieldDamage(shieldOwner, attackedEntity);
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}