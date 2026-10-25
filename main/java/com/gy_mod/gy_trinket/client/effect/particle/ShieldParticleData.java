package com.gy_mod.gy_trinket.client.effect.particle;

import java.util.concurrent.ThreadLocalRandom;

public class ShieldParticleData {

    public final int entityId;
    // 从实体脚底到球心的偏移
    public final double originOffsetX, originOffsetY, originOffsetZ;
    // 从球心到粒子位置的偏移
    public final double offsetX, offsetY, offsetZ;
    // 从粒子指向球心的方向（归一化）
    public final double dirX, dirY, dirZ;
    // 每颗粒子的随机种子：驱动自旋、尺寸抖动、切面闪烁相位
    public final float seedA, seedB;

    public int age = 0;
    public final int lifetime = 20;

    /** 这一次受击的编号（同一批共用一个；0 = 不计入让位体系，比如待机粒子） */
    public final int burstId;

    /**
     * 让位状态。
     * <p>
     * 被标记后不会立刻消失：先把「出现动画」走完（{@link #yieldMarkAge} 之前如果还没出场完，
     * 就等到出场完），再在 2 刻内把透明度压到 0 —— 连续受击时才有波浪涌动感。
     */
    public boolean yielding = false;
    public int yieldMarkAge = 0;

    public ShieldParticleData(int entityId,
                             double originOffsetX, double originOffsetY, double originOffsetZ,
                             double offsetX, double offsetY, double offsetZ,
                             double dirX, double dirY, double dirZ) {
        this(entityId, originOffsetX, originOffsetY, originOffsetZ,
             offsetX, offsetY, offsetZ, dirX, dirY, dirZ,
             ThreadLocalRandom.current().nextFloat(),
             ThreadLocalRandom.current().nextFloat(), 0);
    }

    public ShieldParticleData(int entityId,
                             double originOffsetX, double originOffsetY, double originOffsetZ,
                             double offsetX, double offsetY, double offsetZ,
                             double dirX, double dirY, double dirZ,
                             float seedA, float seedB) {
        this(entityId, originOffsetX, originOffsetY, originOffsetZ,
             offsetX, offsetY, offsetZ, dirX, dirY, dirZ, seedA, seedB, 0);
    }

    public ShieldParticleData(int entityId,
                             double originOffsetX, double originOffsetY, double originOffsetZ,
                             double offsetX, double offsetY, double offsetZ,
                             double dirX, double dirY, double dirZ,
                             float seedA, float seedB, int burstId) {
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
        this.seedA = seedA;
        this.seedB = seedB;
        this.burstId = burstId;
    }

    public void tick() {
        age++;
    }
}
