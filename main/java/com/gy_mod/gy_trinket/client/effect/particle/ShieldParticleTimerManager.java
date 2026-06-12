package com.gy_mod.gy_trinket.client.effect.particle;

import java.util.ArrayList;
import java.util.List;

public class ShieldParticleTimerManager {
    
    private static ShieldParticleTimerManager instance;
    private final List<PendingParticle> pendingParticles = new ArrayList<>();
    
    private ShieldParticleTimerManager() {}
    
    public static ShieldParticleTimerManager getInstance() {
        if (instance == null) {
            instance = new ShieldParticleTimerManager();
        }
        return instance;
    }
    
    public void addPendingParticle(int entityId,
                                   double originOffsetX, double originOffsetY, double originOffsetZ,
                                   double offsetX, double offsetY, double offsetZ,
                                   double dirX, double dirY, double dirZ,
                                   int delayTicks) {
        pendingParticles.add(new PendingParticle(entityId, originOffsetX, originOffsetY, originOffsetZ,
                                                 offsetX, offsetY, offsetZ,
                                                 dirX, dirY, dirZ,
                                                 delayTicks));
    }
    
    public void tick() {
        pendingParticles.removeIf(p -> {
            if (p.delayTicks <= 0) {
                ShieldParticleRenderManager.getInstance().addParticle(
                    p.entityId, p.originOffsetX, p.originOffsetY, p.originOffsetZ,
                    p.offsetX, p.offsetY, p.offsetZ,
                    p.dirX, p.dirY, p.dirZ
                );
                return true;
            }
            p.delayTicks--;
            return false;
        });
    }
    
    private static class PendingParticle {
        final int entityId;
        final double originOffsetX, originOffsetY, originOffsetZ;
        final double offsetX, offsetY, offsetZ;
        final double dirX, dirY, dirZ;
        int delayTicks;
        
        PendingParticle(int entityId,
                       double originOffsetX, double originOffsetY, double originOffsetZ,
                       double offsetX, double offsetY, double offsetZ,
                       double dirX, double dirY, double dirZ,
                       int delayTicks) {
            this.entityId = entityId;
            this.originOffsetX = originOffsetX;
            this.originOffsetY = originOffsetY;
            this.originOffsetZ = originOffsetZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.delayTicks = delayTicks;
        }
    }
}
