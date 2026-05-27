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
    
    public void addPendingParticle(double x, double y, double z,
                                   double dirX, double dirY, double dirZ,
                                   double originX, double originY, double originZ,
                                   long delayMs) {
        pendingParticles.add(new PendingParticle(x, y, z, dirX, dirY, dirZ, 
                                                 originX, originY, originZ, 
                                                 System.currentTimeMillis() + delayMs));
    }
    
    public void tick() {
        long currentTime = System.currentTimeMillis();
        pendingParticles.removeIf(p -> {
            if (currentTime >= p.delayTime) {
                ShieldParticleRenderManager.getInstance().addParticle(
                    p.x, p.y, p.z, 
                    p.dirX, p.dirY, p.dirZ, 
                    p.originX, p.originY, p.originZ
                );
                return true;
            }
            return false;
        });
    }
    
    private static class PendingParticle {
        final double x, y, z;
        final double dirX, dirY, dirZ;
        final double originX, originY, originZ;
        final long delayTime;
        
        PendingParticle(double x, double y, double z,
                       double dirX, double dirY, double dirZ,
                       double originX, double originY, double originZ,
                       long delayTime) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dirX = dirX;
            this.dirY = dirY;
            this.dirZ = dirZ;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.delayTime = delayTime;
        }
    }
}