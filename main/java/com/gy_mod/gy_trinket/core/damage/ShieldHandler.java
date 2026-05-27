package com.gy_mod.gy_trinket.core.damage;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield.type.ShieldTypeManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import com.gy_mod.gy_trinket.damage.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ShieldHandler implements DamageHandler {

    private static final int PRIORITY = 20;

    @Override
    public void handle(DamageContext context) {
        Player shieldOwner = context.getShieldOwner();
        UUID shieldOwnerUUID = shieldOwner.getUUID();
        LivingEntity attackedEntity = context.getAttackedEntity();

        if (attackedEntity.isInvulnerable()) {
            context.setCanceled(true);
            return;
        }

        ResourceKey<DamageType> damageType = context.getSource().typeHolder().unwrapKey().orElse(null);
        if (damageType == ModDamageTypes.PLAYER_SELF_DAMAGE || damageType == ModDamageTypes.PROTOCOL_PLAYER_SELF_DAMAGE) {
            return;
        }

        boolean isShieldSelfDamage = damageType == ModDamageTypes.SHIELD_SELF_DAMAGE || damageType == ModDamageTypes.PROTOCOL_SHIELD_SELF_DAMAGE;

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
            if (shieldOwner instanceof ServerPlayer serverPlayer) {
                ShieldManager.setCurrentShield(serverPlayer, currentShield - originalDamage);
            } else {
                ShieldManager.setCurrentShield(shieldOwnerUUID, currentShield - originalDamage);
            }
            context.setCanceled(true);
            
            // 当承受护盾自伤或协议护盾自伤时，不施加无敌状态
            if (!isShieldSelfDamage) {
                setInvulnerableTemporarily(attackedEntity, Config.SHIELD_BLOCK_INVULNERABLE_TICKS.get());
            }
        } else {
            float remainingDamage = (float) (originalDamage - currentShield);
            if (shieldOwner instanceof ServerPlayer serverPlayer) {
                ShieldManager.setCurrentShield(serverPlayer, 0);
            } else {
                ShieldManager.setCurrentShield(shieldOwnerUUID, 0);
            }
            context.setCurrentDamage(remainingDamage);
        }

        if (!isShieldSelfDamage) {
            ShieldTypeManager.processReflectAfterShieldDamage(shieldOwner, attackedEntity);
        }
    }

    /**
     * 临时设置实体为无敌状态
     * @param entity 目标实体
     * @param ticks 无敌持续时间（刻）
     */
    private void setInvulnerableTemporarily(LivingEntity entity, int ticks) {
        if (entity.isInvulnerable()) {
            return;
        }
        
        entity.setInvulnerable(true);
        
        if (!entity.level().isClientSide()) {
            MinecraftServer server = entity.level().getServer();
            if (server != null) {
                new Thread(() -> {
                    try {
                        Thread.sleep((long) ticks * 50L);
                        server.execute(() -> {
                            if (entity.isAlive()) {
                                entity.setInvulnerable(false);
                            }
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }
}