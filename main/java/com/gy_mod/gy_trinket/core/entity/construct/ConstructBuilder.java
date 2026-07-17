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

    /**
     * 构建完成时的通用流程：
     * 1. 通过 {@link ConstructType#createConstruct} 创建构造体逻辑实例
     * 2. 调用 {@link IConstruct#onCreated()} 生成实体
     * 3. 通过 {@link IConstruct#createData} 创建持久化数据
     * 4. 回写 entityUUID 并注册到 ConstructManager
     * <p>
     * 子类如需在构建完成时执行额外逻辑（如无人机的突击/防御模块检测、
     * 蜂群的随机等阶），应覆写 {@link #createConstruct()} 提供自定义的构造体实例，
     * 而不是直接覆写此方法。
     */
    protected void onBuildComplete() {
        IConstruct construct = createConstruct();
        if (construct == null) return;

        construct.onCreated();

        UUID entityUUID = construct.getEntityUUID() != null ? construct.getEntityUUID() : UUID.randomUUID();
        ConstructData data = construct.createData(entityUUID);

        ConstructManager.getInstance().addConstruct(player, data);
    }

    /**
     * 创建构造体逻辑实例。
     * 默认通过 {@link ConstructType#createConstruct} 工厂方法创建。
     * 子类可覆写此方法以提供自定义创建逻辑。
     */
    protected IConstruct createConstruct() {
        return constructType.createConstruct(player);
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