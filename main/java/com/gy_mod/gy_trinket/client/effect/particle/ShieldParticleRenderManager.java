package com.gy_mod.gy_trinket.client.effect.particle;

import java.util.ArrayList;
import java.util.List;

public class ShieldParticleRenderManager {
    
    private static ShieldParticleRenderManager instance;
    private final List<ShieldParticleData> particles = new ArrayList<>();
    
    private ShieldParticleRenderManager() {}
    
    public static ShieldParticleRenderManager getInstance() {
        if (instance == null) {
            instance = new ShieldParticleRenderManager();
        }
        return instance;
    }
    
    public void addParticle(double x, double y, double z,
                           double dirX, double dirY, double dirZ,
                           double originX, double originY, double originZ) {
        particles.add(new ShieldParticleData(x, y, z, dirX, dirY, dirZ, originX, originY, originZ));
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