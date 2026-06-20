package com.gytrinket.gytrinket.core.damage;

import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.shield_transfer.ShieldTransferManager;
import com.gytrinket.gytrinket.event.ShieldIdleParticleEvent;
import com.gytrinket.gytrinket.network.NetworkHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShieldParticleHandler implements DamageHandler {
    
    private static final int PRIORITY = 300;
    private static final long MIN_INTERVAL_MS = 100;
    private static final Map<UUID, Long> lastTriggerTimes = new HashMap<>();
    
    @Override
    public void handle(DamageContext context) {
        Player shieldOwner = context.getShieldOwner();
        if (!(shieldOwner instanceof ServerPlayer)) {
            return;
        }
        
        ServerPlayer serverPlayer = (ServerPlayer) shieldOwner;
        double currentShield = ShieldManager.getCurrentShield(serverPlayer.getUUID());
        if (currentShield <= 0) {
            return;
        }
        
        if (context.getAttackedEntity() instanceof Player attackedPlayer) {
            if (!ShieldTransferManager.shouldProtectPlayer(attackedPlayer)) {
                return;
            }
        }
        
        if (context.isAnySelfDamage()) {
            return;
        }
        
        UUID playerUUID = serverPlayer.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastTriggerTimes.get(playerUUID);
        
        if (lastTime != null && currentTime - lastTime < MIN_INTERVAL_MS) {
            return;
        }
        
        lastTriggerTimes.put(playerUUID, currentTime);
        
        ShieldIdleParticleEvent.resetIdleTimer(playerUUID);
        
        generateShieldParticles(serverPlayer, context.getSource(), context.getAttackedEntity());
    }
    
    private void generateShieldParticles(ServerPlayer shieldOwner, DamageSource source, net.minecraft.world.entity.Entity attackedEntity) {
        double originX = attackedEntity.getX();
        double originY = attackedEntity.getY() + attackedEntity.getBbHeight() / 1.5;
        double originZ = attackedEntity.getZ();
        
        double radius = 1;
        
        Entity damageSourceEntity = source.getEntity();
        
        double particleX, particleY, particleZ;
        double dirX, dirY, dirZ;
        
        if (damageSourceEntity != null) {
            dirX = damageSourceEntity.getX() - originX;
            dirY = damageSourceEntity.getY() + damageSourceEntity.getBbHeight() / 1.5 - originY;
            dirZ = damageSourceEntity.getZ() - originZ;
            
            double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            
            if (length > 0) {
                dirX /= length;
                dirY /= length;
                dirZ /= length;
            } else {
                dirX = attackedEntity.getLookAngle().x;
                dirY = attackedEntity.getLookAngle().y;
                dirZ = attackedEntity.getLookAngle().z;
            }
            
            particleX = originX + dirX * radius;
            particleY = originY + dirY * radius;
            particleZ = originZ + dirZ * radius;
        } else {
            // 无方向伤害：触发完整的待机粒子流程（侧下方+延迟后侧上方）
            ShieldIdleParticleEvent.triggerIdleParticles(shieldOwner);
            return;
        }
        
        NetworkHandler.sendShieldParticleToPlayer(shieldOwner, attackedEntity, particleX, particleY, particleZ, dirX, dirY, dirZ, originX, originY, originZ, 0);
        
        double[] normal = new double[3];
        if (Math.abs(dirY) < 0.999) {
            normal[0] = -dirZ;
            normal[1] = 0;
            normal[2] = dirX;
        } else {
            normal[0] = 0;
            normal[1] = dirZ;
            normal[2] = -dirY;
        }
        
        double normalLength = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        if (normalLength > 0) {
            normal[0] /= normalLength;
            normal[1] /= normalLength;
            normal[2] /= normalLength;
        }
        
        double[] tangent = new double[3];
        tangent[0] = dirY * normal[2] - dirZ * normal[1];
        tangent[1] = dirZ * normal[0] - dirX * normal[2];
        tangent[2] = dirX * normal[1] - dirY * normal[0];
        
        double tangentLength = Math.sqrt(tangent[0] * tangent[0] + tangent[1] * tangent[1] + tangent[2] * tangent[2]);
        if (tangentLength > 0) {
            tangent[0] /= tangentLength;
            tangent[1] /= tangentLength;
            tangent[2] /= tangentLength;
        }
        
        double[] hexagonAngles = {0, Math.PI/3, 2*Math.PI/3, Math.PI, 4*Math.PI/3, 5*Math.PI/3};
        
        for (int circle = 0; circle < 3; circle++) {
            final double localCurrentInterval = 0.29 * Math.pow(0.92, circle);
            final double localCurrentRadius = localCurrentInterval * (circle + 1);
            
            double offsetAngle = circle * Math.PI / 6;
            
            int delayTicks = circle == 0 ? 2 : 4;
            
            for (double hexAngle : hexagonAngles) {
                double angle = hexAngle + offsetAngle;
                double shrinkFactor = 1.0;
                if (circle == 2) {
                    shrinkFactor = 0.86;
                }
                
                double[] pointDir = new double[3];
                pointDir[0] = dirX + localCurrentRadius * shrinkFactor * (normal[0] * Math.cos(angle) + tangent[0] * Math.sin(angle));
                pointDir[1] = dirY + localCurrentRadius * shrinkFactor * (normal[1] * Math.cos(angle) + tangent[1] * Math.sin(angle));
                pointDir[2] = dirZ + localCurrentRadius * shrinkFactor * (normal[2] * Math.cos(angle) + tangent[2] * Math.sin(angle));
                
                double pointDirLength = Math.sqrt(pointDir[0] * pointDir[0] + pointDir[1] * pointDir[1] + pointDir[2] * pointDir[2]);
                if (pointDirLength > 0) {
                    pointDir[0] /= pointDirLength;
                    pointDir[1] /= pointDirLength;
                    pointDir[2] /= pointDirLength;
                }
                
                double pointX = originX + pointDir[0] * radius;
                double pointY = originY + pointDir[1] * radius;
                double pointZ = originZ + pointDir[2] * radius;
                
                NetworkHandler.sendShieldParticleToPlayer(shieldOwner, attackedEntity, pointX, pointY, pointZ, pointDir[0], pointDir[1], pointDir[2], originX, originY, originZ, delayTicks);
            }
        }
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
}