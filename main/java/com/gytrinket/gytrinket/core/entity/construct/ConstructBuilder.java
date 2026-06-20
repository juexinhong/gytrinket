package com.gytrinket.gytrinket.core.entity.construct;

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

        if (progress >= constructType.getBuildTime()) {
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