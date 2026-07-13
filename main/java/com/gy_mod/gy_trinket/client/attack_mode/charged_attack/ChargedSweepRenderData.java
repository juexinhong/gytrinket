package com.gy_mod.gy_trinket.client.attack_mode.charged_attack;

/**
 * 充能横扫渲染数据
 * (使用普通类替代Java record，兼容Forge 1.20.1)
 */
public class ChargedSweepRenderData {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float scale;
    private final long creationTime;
    private final int lifetime;

    public ChargedSweepRenderData(double x, double y, double z,
                                  float yaw, float pitch,
                                  float scale,
                                  long creationTime,
                                  int lifetime) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.scale = scale;
        this.creationTime = creationTime;
        this.lifetime = lifetime;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float scale() { return scale; }
    public long creationTime() { return creationTime; }
    public int lifetime() { return lifetime; }
}
