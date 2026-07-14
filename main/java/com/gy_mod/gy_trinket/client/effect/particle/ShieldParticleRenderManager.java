package com.gy_mod.gy_trinket.client.effect.particle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShieldParticleRenderManager {
    
    private static ShieldParticleRenderManager instance;
    private final List<ShieldParticleData> particles = new CopyOnWriteArrayList<>();
    
    private ShieldParticleRenderManager() {}
    
    public static ShieldParticleRenderManager getInstance() {
        if (instance == null) {
            instance = new ShieldParticleRenderManager();
        }
        return instance;
    }
    
    public void addParticle(int entityId,
                           double originOffsetX, double originOffsetY, double originOffsetZ,
                           double offsetX, double offsetY, double offsetZ,
                           double dirX, double dirY, double dirZ) {
        particles.add(new ShieldParticleData(entityId, originOffsetX, originOffsetY, originOffsetZ,
                                             offsetX, offsetY, offsetZ, dirX, dirY, dirZ));
    }
    
    public List<ShieldParticleData> getParticles() {
        return particles;
    }
    
    public void tick() {
        particles.removeIf(p -> {
            p.tick();
            return p.age >= p.lifetime;
        });
    }
    
    public void clear() {
        particles.clear();
    }
}
