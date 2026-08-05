package com.gytrinket.gytrinket.core.attack_mode.electric_discharge;

import java.util.List;

public class LightningRenderData {
    /** 延申阶段占整个生命周期的比例：闪电从根部向末端渐进式延申所花的时间占比 */
    public static final float EXTEND_FRACTION = 0.45f;

    private final List<ElectricDischargeManager.LightningSegment> segments;
    private final long startTime;
    private final int duration;
    private final double totalLength;
    /** 自定义最大宽度，若 > 0 则覆盖自动计算。 */
    private final float maxWidthOverride;
    /** 延申阶段持续时间（tick） */
    private final int extendTicks;
    /** 填满后消退持续时间（tick） */
    private final int fadeTicks;

    public LightningRenderData(List<ElectricDischargeManager.LightningSegment> segments, long startTime, int duration, double totalLength) {
        this(segments, startTime, duration, totalLength, -1.0f);
    }

    public LightningRenderData(List<ElectricDischargeManager.LightningSegment> segments, long startTime, int duration, double totalLength, float maxWidthOverride) {
        this.segments = segments;
        this.startTime = startTime;
        this.duration = duration;
        this.totalLength = totalLength;
        this.maxWidthOverride = maxWidthOverride;
        this.extendTicks = Math.max(1, Math.min(duration - 1, Math.round(duration * EXTEND_FRACTION)));
        this.fadeTicks = Math.max(1, duration - extendTicks);
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

    public float getMaxWidthOverride() {
        return maxWidthOverride;
    }

    public int getExtendTicks() {
        return extendTicks;
    }

    public int getFadeTicks() {
        return fadeTicks;
    }

    public boolean isExpired(long currentTime) {
        return currentTime - startTime >= duration;
    }

    public float getProgress(long currentTime) {
        return Math.min((float) (currentTime - startTime) / duration, 1.0f);
    }
}
