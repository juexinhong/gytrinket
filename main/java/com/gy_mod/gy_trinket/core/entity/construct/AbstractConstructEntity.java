package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.SelfDestructBehavior;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.TargetMemory;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 构造体实体抽象基类
 * <p>
 * 提取无人机构造体、僚机构造体的公共逻辑。
 * 子类需实现：
 * - getConstructTypeId() - 返回构造体类型 ID
 * - createConstructDataForRegistration(ServerPlayer) - 创建注册用的构造体数据
 * - applyConstructAttributes(UUID, Map) - 调用类型特定的属性应用方法
 */
public abstract class AbstractConstructEntity extends PathfinderMob implements GeoEntity, IConstructEntity {

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    // 归属者UUID客户端同步
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(AbstractConstructEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    @Nullable
    protected UUID ownerUUID;

    protected int attackCooldown = 0;

    protected double baseMaxHealth;
    protected double baseAttackDamage;
    protected double attackSpeedMultiplier = 1.0;

    // 索敌通用参数与状态
    protected static final float PLAYER_MAX_TARGET_RANGE = 35.0f;
    protected static final long TARGET_MEMORY_DURATION = 60L;
    protected final Map<UUID, TargetMemory> targetMemoryMap = new HashMap<>();

    protected AbstractConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_OWNER_UUID, Optional.empty());
    }

    // 归属者管理

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        // 优先从SynchedEntityData读取（客户端也能获取同步后的值）
        Optional<UUID> synced = entityData.get(DATA_OWNER_UUID);
        if (synced.isPresent()) return synced.get();
        return this.ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
        entityData.set(DATA_OWNER_UUID, Optional.ofNullable(uuid));
    }

    @Nullable
    public Entity getOwner() {
        UUID uuid = this.getOwnerUUID();
        if (uuid == null) return null;
        return this.level().getPlayerByUUID(uuid);
    }

    // 攻击冷却

    public int getAttackCooldown() {
        return this.attackCooldown;
    }

    public void setAttackCooldown(int cooldown) {
        this.attackCooldown = cooldown;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
    }

    // 碰撞规则

    @Override
    public boolean isPickable() {
        // 构造体不阻挡玩家射线追踪，使玩家可以穿过构造体交互方块/实体/攻击
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        // 弹射物仍可命中构造体（敌人箭矢等），玩家弹射物穿透由 ConstructPenetrationHandler 处理
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // 基础属性

    @Override
    public double getBaseMaxHealth() {
        return baseMaxHealth;
    }

    public void setBaseMaxHealth(double baseMaxHealth) {
        this.baseMaxHealth = baseMaxHealth;
    }

    @Override
    public double getBaseAttackDamage() {
        return baseAttackDamage;
    }

    public void setBaseAttackDamage(double baseAttackDamage) {
        this.baseAttackDamage = baseAttackDamage;
    }

    @Override
    public double getAttackSpeedMultiplier() {
        return attackSpeedMultiplier;
    }

    @Override
    public void setAttackSpeedMultiplier(double multiplier) {
        this.attackSpeedMultiplier = multiplier;
    }

    // 朝向控制

    public void facePositionWithInterpolation(Vec3 targetPos, float rotationSpeed) {
        Vec3 pos = this.position();

        double dx = targetPos.x - pos.x;
        double dy = targetPos.y - pos.y;
        double dz = targetPos.z - pos.z;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float targetPitch = -(float) (Math.atan2(dy, horizontalDistance) * (180.0 / Math.PI));

        float currentYaw = this.getYRot();

        float deltaYaw = targetYaw - currentYaw;
        while (deltaYaw > 180.0f) deltaYaw -= 360.0f;
        while (deltaYaw < -180.0f) deltaYaw += 360.0f;

        float yawStep = Math.min(Math.abs(deltaYaw), rotationSpeed);
        if (deltaYaw < 0) yawStep = -yawStep;
        float newYaw = currentYaw + yawStep;

        this.setYRot(newYaw);
        this.setXRot(targetPitch);
        this.setYHeadRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
    }

    public void faceTargetWithInterpolation(LivingEntity target) {
        facePositionWithInterpolation(target.position().add(0, target.getEyeHeight() * 0.5, 0), 20.0f);
    }

    public void faceOwnerDirection(LivingEntity owner) {
        float ownerYaw = owner.getYRot();
        float currentYaw = this.getYRot();

        float deltaYaw = ownerYaw - currentYaw;
        while (deltaYaw > 180.0f) deltaYaw -= 360.0f;
        while (deltaYaw < -180.0f) deltaYaw += 360.0f;

        float rotationSpeed = 15.0f;
        float yawStep = Math.min(Math.abs(deltaYaw), rotationSpeed);
        if (deltaYaw < 0) yawStep = -yawStep;
        float newYaw = currentYaw + yawStep;

        this.setYRot(newYaw);
        this.setXRot(0);
        this.setYHeadRot(newYaw);
        this.yBodyRot = newYaw;
        this.yHeadRot = newYaw;
    }

    // 友方构造体判定

    protected boolean isOwnConstruct(LivingEntity entity, UUID ownerUUID) {
        if (entity instanceof IConstructEntity constructEntity) {
            UUID entOwner = constructEntity.getOwnerUUID();
            return entOwner != null && entOwner.equals(ownerUUID);
        }
        return false;
    }

    // 索敌模板方法

    protected LivingEntity findTarget(LivingEntity owner, float searchRange,
                                      Predicate<LivingEntity> isValidTarget) {
        Level level = this.level();
        Vec3 pos = this.position();
        long currentTick = level.getGameTime();
        UUID myUUID = this.getUUID();

        AABB searchBox = new AABB(
            pos.x - searchRange, pos.y - searchRange, pos.z - searchRange,
            pos.x + searchRange, pos.y + searchRange, pos.z + searchRange
        );

        List<LivingEntity> allTargets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                isValidTarget::test);

        if (!allTargets.isEmpty()) {
            LivingEntity newTarget = allTargets.stream()
                    .min(Comparator.comparingDouble(t -> pos.distanceTo(t.position())))
                    .orElse(null);

            if (newTarget != null) {
                TargetMemory existing = targetMemoryMap.get(myUUID);
                if (existing != null && existing.target == newTarget) {
                    existing.endTick = currentTick + TARGET_MEMORY_DURATION;
                } else {
                    targetMemoryMap.put(myUUID, new TargetMemory(newTarget, currentTick + TARGET_MEMORY_DURATION));
                }
                return newTarget;
            }
        }

        TargetMemory memory = targetMemoryMap.get(myUUID);
        if (memory != null) {
            if (memory.endTick > currentTick && memory.target.isAlive()) {
                if (memory.target.distanceTo(owner) <= PLAYER_MAX_TARGET_RANGE) {
                    return memory.target;
                }
            }
            targetMemoryMap.remove(myUUID);
        }

        return null;
    }

    // 飞行粒子

    protected void addFlightParticles() {
        Vec3 pos = this.position();
        Vec3 lookAngle = new Vec3(
            Mth.cos((this.getYRot() + 90.0f) * Mth.DEG_TO_RAD),
            0.0,
            Mth.sin((this.getYRot() + 90.0f) * Mth.DEG_TO_RAD)
        );

        Vec3 leftOffset = pos.add(-lookAngle.x * 0.3, -0.2, -lookAngle.z * 0.3);
        Vec3 rightOffset = pos.add(lookAngle.x * 0.3, -0.2, lookAngle.z * 0.3);

        this.level().addParticle(ParticleTypes.SMOKE, leftOffset.x, leftOffset.y, leftOffset.z,
             0.0, 0.0, 0.0);
        this.level().addParticle(ParticleTypes.SMOKE, rightOffset.x, rightOffset.y, rightOffset.z,
             0.0, 0.0, 0.0);
    }

    // 伤害和死亡

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 免疫挤压伤害（实体过多导致的伤害）
        if ("cramming".equals(source.getMsgId())) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player playerAttacker) {
            if (this.getOwnerUUID() != null && this.getOwnerUUID().equals(playerAttacker.getUUID())) {
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        triggerSelfDestructIfAvailable();
        super.die(source);
        removeFromConstructManager();
    }

    /**
     * 自毁装置：当构造体死亡时触发爆炸。
     * 爆炸基础伤害为1，基础半径为1。每点最大生命值增加1点爆炸伤害和0.3格爆炸半径。
     * 仅当玩家光点核心中拥有自毁装置物品时触发。
     * <p>
     * 子类若需在死亡时进行免疫判定（如宽限协议/最终指令），
     * 应在覆写 die() 时优先检查免疫条件，若免疫则提前返回，
     * 不调用 super.die()，从而阻止自毁装置触发。
     */
    protected void triggerSelfDestructIfAvailable() {
        if (SelfDestructBehavior.hasRequiredItems(this)) {
            SelfDestructBehavior.triggerSelfDestructExplosion(this);
        }
    }

    protected void removeFromConstructManager() {
        if (this.ownerUUID != null && !this.level().isClientSide) {
            ConstructManager manager = ConstructManager.getInstance();
            manager.removeConstruct(this.ownerUUID, this.getUUID());
            manager.markConstructDead(this.ownerUUID, getConstructTypeId(), this.getUUID());
            manager.unregisterConstructEntity(this.ownerUUID, getConstructTypeId(), this.getUUID());
            onRemoveFromConstructManager();
        }
    }

    protected void onRemoveFromConstructManager() {}

    // 管理器注册检查

    protected void checkManagerRegistration() {
        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        ConstructManager cm = ConstructManager.getInstance();

        Map<UUID, Entity> activeEntities = cm.getActiveConstructEntities(ownerUUID, getConstructTypeId());
        boolean inActiveEntities = activeEntities != null && activeEntities.containsKey(this.getUUID());

        boolean inPlayerConstructs = false;
        Map<String, List<ConstructData>> constructsMap = cm.getPlayerConstructs(ownerUUID);
        List<ConstructData> list = constructsMap.get(getConstructTypeId());
        if (list != null) {
            for (ConstructData data : list) {
                if (this.getUUID().equals(data.getEntityUUID())) {
                    inPlayerConstructs = true;
                    break;
                }
            }
        }

        if (!inActiveEntities && !inPlayerConstructs) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (inActiveEntities && inPlayerConstructs) {
            return;
        }

        if (this.level().getServer() == null) {
            return;
        }

        ServerPlayer ownerPlayer = this.level().getServer().getPlayerList().getPlayer(ownerUUID);
        if (ownerPlayer == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        if (!inActiveEntities) {
            cm.registerConstructEntity(ownerUUID, getConstructTypeId(), this);
        }

        if (!inPlayerConstructs) {
            ConstructData newData = createConstructDataForRegistration(ownerPlayer);
            float maxH = this.getMaxHealth();
            newData.setHealthRatio(maxH > 0 ? this.getHealth() / maxH : 1.0);
            cm.addConstruct(ownerPlayer, newData);
        }
    }

    // NBT 序列化

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("owner", this.ownerUUID);
        }
        addTypeSpecificSaveData(tag);
        float maxH = this.getMaxHealth();
        tag.putFloat("health_ratio", maxH > 0 ? this.getHealth() / maxH : 1.0f);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("owner")) {
            this.ownerUUID = tag.getUUID("owner");
            entityData.set(DATA_OWNER_UUID, Optional.of(this.ownerUUID));
        }
        readTypeSpecificSaveData(tag);
        applyAttributeModifiers();
        onAttributesApplied();

        if (tag.contains("health_ratio")) {
            float healthRatio = tag.getFloat("health_ratio");
            this.setHealth(this.getMaxHealth() * healthRatio);
        }
    }

    protected void addTypeSpecificSaveData(CompoundTag tag) {}

    protected void readTypeSpecificSaveData(CompoundTag tag) {}

    protected void onAttributesApplied() {}

    // 属性应用

    @Override
    public void refreshConstructAttributes() {
        if (this.ownerUUID == null) return;
        UUID playerUUID = this.ownerUUID;
        ServerPlayer player = null;
        if (this.level().getServer() != null) {
            player = this.level().getServer().getPlayerList().getPlayer(playerUUID);
        }
        if (player != null) {
            applyConstructAttributes(playerUUID, ConstructAttributeApplier.computeConstructAttributes(playerUUID));
        }
    }

    protected void applyAttributeModifiers() {
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseMaxHealth);
        }
        refreshConstructAttributes();
    }

    protected abstract void applyConstructAttributes(UUID playerUUID, Map<String, Double> attributes);

    // 抽象方法

    protected abstract String getConstructTypeId();

    protected abstract ConstructData createConstructDataForRegistration(ServerPlayer ownerPlayer);

    /**
     * 从当前实体状态创建数据快照，用于待机备份或玩家退出保存。
     * <p>
     * 基类填充通用字段（healthRatio, position, dimension, active），
     * 子类覆盖 {@link #populateTypeSpecificData(ConstructData)} 填充类型特有字段。
     *
     * @return 包含当前实体状态的 ConstructData 快照
     */
    public ConstructData snapshotToData() {
        ConstructData data = createConstructDataForRegistration(
                this.getOwner() instanceof ServerPlayer sp ? sp : null);
        if (data == null) return null;

        // 通用字段
        double currentMaxHealth = this.getMaxHealth();
        float currentHealth = this.getHealth();
        data.setHealthRatio(currentMaxHealth > 0 ? currentHealth / currentMaxHealth : 1.0);
        data.setSavedPos(this.getX(), this.getY(), this.getZ());
        data.setDimension(this.level().dimension().location().toString());
        data.setActive(true);

        // 类型特有字段
        populateTypeSpecificData(data);

        return data;
    }

    /**
     * 子类覆盖此方法以填充类型特有的数据字段。
     * 默认实现为空。
     *
     * @param data 要填充的构造体数据
     */
    protected void populateTypeSpecificData(ConstructData data) {
        // 默认无类型特有字段
    }

    // GeckoLib

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }
}
