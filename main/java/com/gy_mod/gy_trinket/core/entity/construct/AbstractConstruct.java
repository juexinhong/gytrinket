package com.gy_mod.gy_trinket.core.entity.construct;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 构造体逻辑基类
 * <p>
 * 抽取 DroneConstruct / WingmanConstruct 共有的字段与方法。
 * 子类只需实现 spawnEntity() 完成具体实体生成与注册。
 */
public abstract class AbstractConstruct implements IConstruct {

    protected final String constructId;
    protected final LivingEntity owner;
    protected final double maxHealth;
    protected double health;
    protected boolean active;
    protected UUID entityUUID;

    protected AbstractConstruct(String constructId, LivingEntity owner, double maxHealth) {
        this.constructId = constructId;
        this.owner = owner;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.active = true;
    }

    @Override
    public String getConstructId() {
        return constructId;
    }

    @Override
    public ConstructType getConstructType() {
        return ConstructManager.getInstance().getConstructType(constructId);
    }

    @Override
    public Entity getEntity() {
        if (entityUUID == null || owner == null) return null;
        Level level = owner.level();
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(entityUUID);
        }
        // Fallback: AABB scan
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(-1000, -1000, -1000, 1000, 1000, 1000);
        java.util.List<Entity> entities = level.getEntities(null, searchBox);
        for (Entity entity : entities) {
            if (entity.getUUID().equals(entityUUID)) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public double getHealth() {
        return health;
    }

    @Override
    public void setHealth(double health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
    }

    @Override
    public double getMaxHealth() {
        return maxHealth;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void activate() {
        this.active = true;
    }

    @Override
    public void deactivate() {
        this.active = false;
    }

    @Override
    public void tick() {
    }

    @Override
    public void onCreated() {
        spawnEntity();
    }

    @Override
    public void onDestroyed() {
        if (entityUUID == null || owner == null) return;
        Level level = owner.level();
        if (level instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(entityUUID);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    @Override
    public boolean canBeCreated() {
        return owner != null && owner.isAlive();
    }

    @Override
    public void onBuildProgress(int progress, int total) {
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    public LivingEntity getOwner() {
        return owner;
    }

    @Override
    public Set<String> getCurrentTags() {
        Set<String> allTags = new HashSet<>();
        ConstructType type = getConstructType();
        if (type != null) {
            allTags.addAll(type.getTags());
        }
        return allTags;
    }

    protected abstract void spawnEntity();
}
