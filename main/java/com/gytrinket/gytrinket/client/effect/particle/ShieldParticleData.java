package com.gytrinket.gytrinket.client.effect.particle;

public class ShieldParticleData {

    public final int entityId;
    // 从实体脚底到球心的偏移
    public final double originOffsetX, originOffsetY, originOffsetZ;
    // 从球心到粒子位置的偏移
    public final double offsetX, offsetY, offsetZ;
    // 从粒子指向球心的方向（归一化）
    public final double dirX, dirY, dirZ;

    public int age = 0;
    public final int lifetime = 20;

    public ShieldParticleData(int entityId,
                             double originOffsetX, double originOffsetY, double originOffsetZ,
                             double offsetX, double offsetY, double offsetZ,
                             double dirX, double dirY, double dirZ) {
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
    }

    public void tick() {
        age++;
    }
}
