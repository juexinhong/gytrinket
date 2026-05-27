package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.world.entity.Entity;

import java.util.Set;

public interface IConstruct {
    String getConstructId();

    ConstructType getConstructType();

    Entity getEntity();

    double getHealth();

    void setHealth(double health);

    double getMaxHealth();

    boolean isActive();

    void activate();

    void deactivate();

    void tick();

    void onCreated();

    void onDestroyed();

    boolean canBeCreated();

    void onBuildProgress(int progress, int total);

    Set<String> getCurrentTags();
}