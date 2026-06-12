package com.gy_mod.gy_trinket.core.attack_mode.electric_discharge;

import java.util.List;

public class LightningRenderData {
    private final List<ElectricDischargeManager.LightningSegment> segments;
    private final long startTime;
    private final int duration;
    private final double totalLength;

    public LightningRenderData(List<ElectricDischargeManager.LightningSegment> segments, long startTime, int duration, double totalLength) {
        this.segments = segments;
        this.startTime = startTime;
        this.duration = duration;
        this.totalLength = totalLength;
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

    public boolean isExpired(long currentTime) {
        return currentTime - startTime >= duration;
    }

    public float getProgress(long currentTime) {
        return Math.min((float) (currentTime - startTime) / duration, 1.0f);
    }
}
