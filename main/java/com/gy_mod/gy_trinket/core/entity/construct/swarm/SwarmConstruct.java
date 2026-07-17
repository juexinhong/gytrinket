package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructType;
import com.gy_mod.gy_trinket.core.entity.construct.IConstruct;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 蜂群构造体逻辑类
 * <p>
 * 蜂群是基础其他构造体，行为类似无人机追击阵列（含鸟群算法）。
 * 攻击时发射电弧（范围伤害，单次伤害，施加可叠加易伤）。
 * 玩家护盾受损时部分蜂群转为修复模式；护盾破裂时全员获得攻速/移速增益。
 * 单实例构建时有小概率提升等阶（标准/高阶），获得属性加成。
 */
public class SwarmConstruct implements IConstruct {

    private final String constructId;
    private final LivingEntity owner;
    private final double maxHealth;
    private double health;
    private boolean active;
    private UUID entityUUID;

    /** 单实例等阶：0=基础 1=标准 2=高阶 */
    private final int tier;

    public SwarmConstruct(String constructId, LivingEntity owner, double maxHealth, int tier) {
        this.constructId = constructId;
        this.owner = owner;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.active = true;
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    /**
     * 根据等阶返回生命/伤害加成倍率。
     * 基础=1.0，标准=1.5，高阶=2.0。
     */
    public double getTierStatMultiplier() {
        switch (tier) {
            case SwarmConstructTypes.TIER_STANDARD: return 1.5;
            case SwarmConstructTypes.TIER_ADVANCED: return 2.0;
            default: return 1.0;
        }
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
        // 行为逻辑已在 SwarmConstructEntity 中实现
    }

    @Override
    public void onCreated() {
        spawnEntity();
    }

    private void spawnEntity() {
        Level level = owner.level();
        if (level.isClientSide) return;

        SwarmConstructEntity swarm = new SwarmConstructEntity(level, owner.getUUID(), this);

        Vec3 spawnPos = owner.position().add(0, 2, 0);
        swarm.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        swarm.setHealth(swarm.getMaxHealth());

        level.addFreshEntity(swarm);
        entityUUID = swarm.getUUID();

        ConstructManager.getInstance().registerConstructEntity(owner.getUUID(), constructId, swarm);
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

    @Override
    public ConstructData createData(UUID entityUUID) {
        SwarmConstructData data = new SwarmConstructData(constructId, entityUUID, maxHealth);
        data.setTier(tier);
        return data;
    }

    @Override
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
}
