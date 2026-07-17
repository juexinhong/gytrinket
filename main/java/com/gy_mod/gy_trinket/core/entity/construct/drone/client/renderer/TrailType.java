package com.gy_mod.gy_trinket.core.entity.construct.drone.client.renderer;

/**
 * 轨迹类型，区分不同弹射物的拖尾表现。
 */
public enum TrailType {
    /** 无人机子弹 - 细轨迹，亮黄色 */
    DRONE_BULLET(0.048F, 1.0F, 1.0F, 0.0F, 0.7F),
    /** 爆破弹 - 粗轨迹，橙红色 */
    EXPLOSIVE(0.08F, 1.0F, 0.5F, 0.1F, 0.8F);

    /** 最大宽度 */
    public final float maxWidth;
    /** 颜色 RGB */
    public final float r, g, b;
    /** 透明度系数 */
    public final float alphaMult;

    TrailType(float maxWidth, float r, float g, float b, float alphaMult) {
        this.maxWidth = maxWidth;
        this.r = r;
        this.g = g;
        this.b = b;
        this.alphaMult = alphaMult;
    }
}
