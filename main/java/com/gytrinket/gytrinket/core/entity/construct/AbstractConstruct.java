package com.gytrinket.gytrinket.core.entity.construct;

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
 * 抽取 DroneConstruct / WingmanConstruct / SwarmConstruct 共有的字段与方法：
 * <ul>
 *   <li>身份与生命值管理（constructId, owner, maxHealth, health, active）</li>
 *   <li>实体 UUID 跟踪与查找（使用 {@link Level#getEntity(UUID)} O(1) 查询，避免全维度 AABB 扫描）</li>
 *   <li>默认生命周期回调（onCreated 调用 {@link #spawnEntity()}，onDestroyed 通过 UUID discard）</li>
 *   <li>标签聚合（{@link #getCurrentTags()} 来自 {@link ConstructType}）</li>
 * </ul>
 * 子类只需实现 {@link #spawnEntity()} 完成具体实体生成与注册。
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

    /**
     * 通过 UUID O(1) 查找关联实体。
     * <p>
     * 替代原先的 AABB(-1000..1000) 全维度扫描，避免每 tick 遍历维度内所有实体。
     * 仅在服务端有效（{@link ServerLevel#getEntity(UUID)} 提供 UUID 索引查询）。
     */
    @Override
    public Entity getEntity() {
        if (entityUUID == null || owner == null) return null;
        Level level = owner.level();
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return serverLevel.getEntity(entityUUID);
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
        // 行为逻辑已在 *ConstructEntity 中实现
    }

    @Override
    public void onCreated() {
        spawnEntity();
    }

    @Override
    public void onDestroyed() {
        if (entityUUID == null || owner == null) return;
        Level level = owner.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        Entity entity = serverLevel.getEntity(entityUUID);
        if (entity != null) {
            entity.discard();
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

    /**
     * 子类实现：生成对应实体并设置 {@link #entityUUID}，注册到 {@link ConstructManager}。
     */
    protected abstract void spawnEntity();
}
