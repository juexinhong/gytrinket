package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;

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

    /**
     * 创建此构造体实例对应的持久化数据。
     * 子类应覆写此方法以创建类型专属的 ConstructData（如 DroneConstructData、SwarmConstructData）。
     *
     * @param entityUUID 实体UUID
     * @return 构造体数据
     */
    ConstructData createData(UUID entityUUID);

    /**
     * 获取此构造体关联的实体UUID。
     * 用于在构建完成后回写到 ConstructData。
     */
    UUID getEntityUUID();
}