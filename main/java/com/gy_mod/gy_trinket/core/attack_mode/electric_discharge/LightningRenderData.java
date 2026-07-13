package com.gy_mod.gy_trinket.core.attack_mode.electric_discharge;

import java.util.List;

public class LightningRenderData {
    private final List<ElectricDischargeManager.LightningSegment> segments;
    private final long startTime;
    private final int duration;
    private final double totalLength;
    /** 自定义最大宽度，若 > 0 则覆盖 LightningRenderManager 的自动计算。 */
    private final float maxWidthOverride;

    public LightningRenderData(List<ElectricDischargeManager.LightningSegment> segments, long startTime, int duration, double totalLength) {
        this(segments, startTime, duration, totalLength, -1.0f);
    }

    public LightningRenderData(List<ElectricDischargeManager.LightningSegment> segments, long startTime, int duration, double totalLength, float maxWidthOverride) {
        this.segments = segments;
        this.startTime = startTime;
        this.duration = duration;
        this.totalLength = totalLength;
        this.maxWidthOverride = maxWidthOverride;
    }

    public List<ElectricDischargeManager.LightningSegment> getSegments() {
        return segments;
    }

    public long getStartTime() {
        return startTime;
    }

    public int getDuration() {
        return duration;
    }

    public double getTotalLength() {
        return totalLength;
    }

    /** 若 > 0，表示使用此值作为最大宽度而非自动计算。 */
    public float getMaxWidthOverride() {
        return maxWidthOverride;
    }

    public boolean isExpired(long currentTime) {
        return currentTime - startTime >= duration;
    }

    public float getProgress(long currentTime) {
        return Math.min((float) (currentTime - startTime) / duration, 1.0f);
    }
}
