package com.gy_mod.gy_trinket.client.effect.particle;

public class ShieldParticleData {
    
    public final double x, y, z;
    public final double dirX, dirY, dirZ;
    public final double originX, originY, originZ;
    
    public int age = 0;
    public final int lifetime = 20;
    
    public ShieldParticleData(double x, double y, double z,
                             double dirX, double dirY, double dirZ,
                             double originX, double originY, double originZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dirX = dirX;
        this.dirY = dirY;
        this.dirZ = dirZ;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }
    
    public void tick() {
        age++;
    }
}