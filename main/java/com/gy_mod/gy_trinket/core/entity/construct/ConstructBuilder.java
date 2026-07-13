package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.world.entity.player.Player;
import java.util.UUID;

public class ConstructBuilder {
    private final Player player;
    private final ConstructType constructType;
    private int progress;
    private boolean completed;
    private double buildSpeedMultiplier = 1.0;

    public ConstructBuilder(Player player, ConstructType constructType) {
        this.player = player;
        this.constructType = constructType;
        this.progress = 0;
        this.completed = false;
    }

    public boolean tick() {
        if (completed) {
            return true;
        }

        updateBuildSpeed();

        int increment = Math.max(1, (int) buildSpeedMultiplier);
        double fractional = buildSpeedMultiplier - (int) buildSpeedMultiplier;
        if (fractional > 0 && Math.random() < fractional) {
            increment++;
        }
        progress += increment;

        if (progress >= getEffectiveBuildTime()) {
            completed = true;
            onBuildComplete();
            return true;
        }

        return false;
    }

    protected void updateBuildSpeed() {
        if (player != null) {
            buildSpeedMultiplier = ConstructAttributeApplier.getEffectiveBuildSpeed(player.getUUID(), constructType);
        }
    }

    protected void onBuildComplete() {
        UUID entityUUID = UUID.randomUUID();
        ConstructData data = new ConstructData(
                constructType.getId(),
                entityUUID,
                constructType.getMaxHealth()
        );

        ConstructManager.getInstance().addConstruct(player, data);
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return getEffectiveBuildTime();
    }

    /**
     * 获取实际构建所需时间（tick），可由子类重写以应用动态修正。
     * 默认返回构造体类型的基础构建时间。
     */
    protected int getEffectiveBuildTime() {
        return constructType.getBuildTime();
    }

    public boolean isCompleted() {
        return completed;
    }

    public ConstructType getConstructType() {
        return constructType;
    }

    public Player getPlayer() {
        return player;
    }

    public double getBuildSpeedMultiplier() {
        return buildSpeedMultiplier;
    }

    public void setBuildSpeedMultiplier(double multiplier) {
        this.buildSpeedMultiplier = multiplier;
    }
}