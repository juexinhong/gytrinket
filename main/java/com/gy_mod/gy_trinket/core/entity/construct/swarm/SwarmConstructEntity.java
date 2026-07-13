package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.IConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModDamageSources;
import com.gy_mod.gy_trinket.core.execute.ExecuteToggleManager;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.vulnerability.VulnerabilityApplyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 蜂群构造体实体类
 * <p>
 * 基础其他构造体，行为类似无人机追击阵列（含鸟群算法 Boid）。
 * 攻击时发射电弧（范围伤害，单次伤害，施加可叠加易伤）。
 * 玩家护盾受损时部分蜂群转为修复模式（伤害转化为护盾恢复）；
 * 玩家护盾破裂时全员获得攻速/移速增益，且不修复护盾。
 * 单实例构建时有小概率提升等阶（标准/高阶），获得生命与伤害加成。
 */
public class SwarmConstructEntity extends PathfinderMob implements GeoEntity, IConstructEntity {

    /** 等阶：0=基础 1=标准 2=高阶 */
    private int tier = SwarmConstructTypes.TIER_BASIC;

    private SwarmConstruct swarmConstruct;

    // ===== 归属者 =====
    @Nullable
    private UUID ownerUUID;

    // ===== 攻击冷却 =====
    private int attackCooldown = 0;

    // ===== 基础属性 =====
    protected double baseMaxHealth;
    protected double baseAttackDamage;
    protected double attackSpeedMultiplier = 1.0;

    // ===== 客户端同步标志 =====
    /** 修复模式标志（服务端计算，客户端读取，避免客户端因无护盾数据而误入待机分支） */
    private static final EntityDataAccessor<Boolean> DATA_REPAIR_MODE =
            SynchedEntityData.defineId(SwarmConstructEntity.class, EntityDataSerializers.BOOLEAN);
    /** 护盾破裂标志（服务端计算，客户端读取，用于移动速度倍率） */
    private static final EntityDataAccessor<Boolean> DATA_SHIELD_BROKEN =
            SynchedEntityData.defineId(SwarmConstructEntity.class, EntityDataSerializers.BOOLEAN);

    // ===== 行为参数 =====
    /** 待机跟随高度（朝向玩家上方此高度处） */
    private static final float STANDBY_HEIGHT = 3.5f;
    /** 待机跟随触发距离 */
    private static final float STANDBY_RANGE = 5.0f;
    /** 护盾破裂增益倍率 */
    private static final double SHIELD_BROKEN_BOOST = 1.5;

    // 追击距离分级（与目标的水平距离，单位：格）
    /** d < DIST_LEAVE：太近，尝试离开（远离目标） */
    private static final double DIST_LEAVE = 1.0;
    /** d <= DIST_STOP_MAX：停止水平移动（下界为 DIST_LEAVE） */
    private static final double DIST_STOP_MAX = 2.5;
    /** d <= DIST_SLOW_MAX：慢速接近；超过则全速追击 */
    private static final double DIST_SLOW_MAX = 5.0;
    /** 慢速接近速度倍率 */
    private static final double SLOW_APPROACH_SPEED_MULT = 0.5;

    // Boid 参数（蜂群更紧凑）
    private static final double BOID_COMFORT_RANGE = 0.6;
    private static final double BOID_SEPARATION_RANGE = 1.2;
    private static final double BOID_SEPARATION_STRENGTH = 0.035;
    private static final double BOID_COHESION_RANGE = 4.0;
    private static final double BOID_COHESION_STRENGTH = 0.02;
    private static final double BOID_ALIGNMENT_RANGE = 3.0;
    private static final double BOID_ALIGNMENT_STRENGTH = 0.025;
    private static final double VELOCITY_DAMPING = 0.85;
    private static final double NEIGHBOR_SCAN_RANGE = 8.0;

    /** 玩家最大索敌距离限制 */
    private static final float PLAYER_MAX_TARGET_RANGE = 35.0f;

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

    public SwarmConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.baseMaxHealth = Config.getSwarmBaseHealth();
        this.baseAttackDamage = Config.getSwarmBaseDamage();
    }

    public SwarmConstructEntity(Level level, UUID ownerUUID, SwarmConstruct swarmConstruct) {
        this(com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities.SWARM_CONSTRUCT.get(), level);
        this.ownerUUID = ownerUUID;
        this.swarmConstruct = swarmConstruct;
        this.tier = swarmConstruct.getTier();
        applyAttributeModifiers();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_REPAIR_MODE, false);
        entityData.define(DATA_SHIELD_BROKEN, false);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    // ===== 归属者管理 =====

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Nullable
    public Entity getOwner() {
        if (this.ownerUUID == null) return null;
        return this.level().getPlayerByUUID(this.ownerUUID);
    }

    // ===== IConstructEntity =====

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

    @Override
    public void refreshConstructAttributes() {
        if (this.ownerUUID == null) return;
        UUID playerUUID = this.ownerUUID;
        ServerPlayer player = null;
        if (this.level().getServer() != null) {
            player = this.level().getServer().getPlayerList().getPlayer(playerUUID);
        }
        if (player != null) {
            ConstructAttributeApplier.applyAttributesToSwarm(playerUUID, this, ConstructAttributeApplier.computeConstructAttributes(playerUUID));
        }
    }

    public SwarmConstruct getSwarmConstruct() {
        return swarmConstruct;
    }

    public int getTier() {
        return tier;
    }

    /** 设置等阶并重新应用属性修饰器 */
    public void setTier(int tier) {
        this.tier = tier;
        applyAttributeModifiers();
    }

    /** 根据等阶返回生命/伤害加成倍率：基础1.0 标准2.0 高阶3.0 */
    public double getTierMultiplier() {
        switch (tier) {
            case SwarmConstructTypes.TIER_STANDARD: return 2.0;
            case SwarmConstructTypes.TIER_ADVANCED: return 3.0;
            default: return 1.0;
        }
    }

    // ===== 攻击冷却 =====

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

    // ===== 伤害和死亡 =====

    @Override
    public boolean hurt(DamageSource source, float amount) {
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
        super.die(source);
        removeFromConstructManager();
    }

    private void removeFromConstructManager() {
        if (this.ownerUUID != null && !this.level().isClientSide) {
            ConstructManager manager = ConstructManager.getInstance();
            manager.removeConstruct(this.ownerUUID, this.getUUID());
            manager.markConstructDead(this.ownerUUID, SwarmConstructTypes.SWARM, this.getUUID());
            manager.unregisterConstructEntity(this.ownerUUID, SwarmConstructTypes.SWARM, this.getUUID());
        }
    }

    // ===== 管理器注册检查 =====

    private void checkManagerRegistration() {
        UUID ownerUUID = this.getOwnerUUID();
        if (ownerUUID == null) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        ConstructManager cm = ConstructManager.getInstance();

        Map<UUID, Entity> activeEntities = cm.getActiveConstructEntities(ownerUUID, SwarmConstructTypes.SWARM);
        boolean inActiveEntities = activeEntities != null && activeEntities.containsKey(this.getUUID());

        boolean inPlayerConstructs = false;
        Map<String, List<ConstructData>> constructsMap = cm.getPlayerConstructs(ownerUUID);
        List<ConstructData> list = constructsMap.get(SwarmConstructTypes.SWARM);
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
            cm.registerConstructEntity(ownerUUID, SwarmConstructTypes.SWARM, this);
        }

        if (!inPlayerConstructs) {
            SwarmConstructData newData = new SwarmConstructData(
                SwarmConstructTypes.SWARM,
                this.getUUID(),
                this.getBaseMaxHealth()
            );
            newData.setTier(this.tier);
            float maxH = this.getMaxHealth();
            newData.setHealthRatio(maxH > 0 ? this.getHealth() / maxH : 1.0);
            cm.addConstruct(ownerPlayer, newData);
        }
    }

    // ===== 朝向控制 =====

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

    // ===== 友方构造体判定 =====

    private boolean isOwnConstruct(LivingEntity entity, UUID ownerUUID) {
        if (entity instanceof IConstructEntity constructEntity) {
            UUID entOwner = constructEntity.getOwnerUUID();
            return entOwner != null && entOwner.equals(ownerUUID);
        }
        return false;
    }

    // ===== Boid 集群力 =====

    private Vec3 calculateBoidForce() {
        LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
        if (owner == null) return Vec3.ZERO;

        List<Vec3> neighborPositions = new ArrayList<>();
        List<Vec3> neighborVelocities = new ArrayList<>();
        collectBoidNeighborData(owner, neighborPositions, neighborVelocities);

        Vec3 pos = this.position();
        Vec3 velocity = this.getDeltaMovement();

        Vec3 separation = boidSeparation(pos, neighborPositions);
        Vec3 cohesion = boidCohesion(pos, neighborPositions);
        Vec3 alignment = boidAlignment(velocity, neighborVelocities);

        return separation.add(cohesion).add(alignment);
    }

    private void collectBoidNeighborData(LivingEntity owner, List<Vec3> positions, List<Vec3> velocities) {
        Vec3 pos = this.position();
        AABB scanBox = new AABB(
            pos.x - NEIGHBOR_SCAN_RANGE, pos.y - NEIGHBOR_SCAN_RANGE, pos.z - NEIGHBOR_SCAN_RANGE,
            pos.x + NEIGHBOR_SCAN_RANGE, pos.y + NEIGHBOR_SCAN_RANGE, pos.z + NEIGHBOR_SCAN_RANGE
        );
        List<SwarmConstructEntity> nearby = this.level().getEntitiesOfClass(
            SwarmConstructEntity.class, scanBox,
            other -> other != this && other.isAlive()
                     && other.getOwnerUUID() != null && other.getOwnerUUID().equals(owner.getUUID())
        );
        for (SwarmConstructEntity other : nearby) {
            positions.add(other.position());
            velocities.add(other.getDeltaMovement());
        }
    }

    private Vec3 boidSeparation(Vec3 pos, List<Vec3> neighbors) {
        if (neighbors.isEmpty()) return Vec3.ZERO;
        Vec3 force = Vec3.ZERO;
        int count = 0;
        for (Vec3 nPos : neighbors) {
            Vec3 diff = pos.subtract(nPos);
            double dist = diff.length();
            if (dist < 0.001) {
                diff = new Vec3(Math.random() - 0.5, 0, Math.random() - 0.5).normalize().scale(BOID_COMFORT_RANGE * 0.5);
                dist = diff.length();
            }
            if (dist < BOID_COMFORT_RANGE) {
                double weight = 1.0 - (dist / BOID_COMFORT_RANGE);
                force = force.add(diff.normalize().scale(weight));
                count++;
            }
        }
        if (count == 0) return Vec3.ZERO;
        force = force.scale(1.0 / count);
        if (force.length() > 0.001) {
            force = force.normalize().scale(BOID_SEPARATION_STRENGTH);
        }
        return force;
    }

    private Vec3 boidCohesion(Vec3 pos, List<Vec3> neighbors) {
        if (neighbors.isEmpty()) return Vec3.ZERO;
        Vec3 center = Vec3.ZERO;
        int count = 0;
        for (Vec3 nPos : neighbors) {
            double dist = pos.distanceTo(nPos);
            if (dist >= BOID_COMFORT_RANGE && dist < BOID_COHESION_RANGE) {
                center = center.add(nPos);
                count++;
            }
        }
        if (count == 0) return Vec3.ZERO;
        center = center.scale(1.0 / count);
        Vec3 toCenter = center.subtract(pos);
        if (toCenter.length() < 0.001) return Vec3.ZERO;
        return toCenter.normalize().scale(BOID_COHESION_STRENGTH);
    }

    private Vec3 boidAlignment(Vec3 velocity, List<Vec3> neighborVelocities) {
        if (neighborVelocities.isEmpty()) return Vec3.ZERO;
        Vec3 avg = Vec3.ZERO;
        for (Vec3 vel : neighborVelocities) { avg = avg.add(vel); }
        avg = avg.scale(1.0 / neighborVelocities.size());
        Vec3 steering = avg.subtract(velocity);
        if (steering.length() > BOID_ALIGNMENT_STRENGTH) {
            steering = steering.normalize().scale(BOID_ALIGNMENT_STRENGTH);
        }
        return steering;
    }

    // ===== tick =====

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.tickCount > 100 && this.tickCount % 20 == 0) {
            checkManagerRegistration();
        }

        if (this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.98, 0.98, 0.98));
        }

        Entity ownerEntity = this.getOwner();
        if (ownerEntity == null || !ownerEntity.isAlive() || !this.isAlive()) return;
        LivingEntity owner = (LivingEntity) ownerEntity;

        // ===== 护盾状态判定 =====
        boolean shieldBroken;
        boolean repairMode;
        double speedMult;
        double attackSpeedMult;

        if (this.level().isClientSide) {
            shieldBroken = this.entityData.get(DATA_SHIELD_BROKEN);
            repairMode = this.entityData.get(DATA_REPAIR_MODE);
            speedMult = shieldBroken ? SHIELD_BROKEN_BOOST : 1.0;
            attackSpeedMult = 1.0;
        } else {
            ShieldData shieldData = ShieldManager.getShieldData(owner.getUUID());
            double currentShield = shieldData != null ? shieldData.getCurrentShield() : 0.0;
            double maxShield = shieldData != null ? shieldData.getMaxShield() : 0.0;

            shieldBroken = maxShield <= 0.0 || currentShield <= 0.0;
            boolean shieldDamaged = maxShield > 0.0 && currentShield > 0.0 && currentShield < maxShield;
            speedMult = shieldBroken ? SHIELD_BROKEN_BOOST : 1.0;
            attackSpeedMult = shieldBroken ? SHIELD_BROKEN_BOOST : 1.0;

            repairMode = shieldDamaged && isAssignedToRepair(owner, currentShield, maxShield);

            this.entityData.set(DATA_SHIELD_BROKEN, shieldBroken);
            this.entityData.set(DATA_REPAIR_MODE, repairMode);
        }

        if (repairMode) {
            repairMovement(this, owner);
            Vec3 repairFacePos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
            facePositionWithInterpolation(repairFacePos, 20.0f);
            executeShieldRepair(this, owner);
        } else {
            LivingEntity target = findTarget(owner);
            if (target != null) {
                pursuitMovement(this, owner, target, speedMult);
                faceTargetWithInterpolation(target);
                executeArcAttack(this, owner, target, attackSpeedMult);
            } else {
                Vec3 standbyFacePos = owner.position().add(0, STANDBY_HEIGHT, 0);
                standbyMovement(this, owner, speedMult);
                facePositionWithInterpolation(standbyFacePos, 20.0f);
                if (Math.abs(this.getY() - standbyFacePos.y) <= 2.0) {
                    this.setXRot(0.0f);
                }
            }
        }
    }

    // ===== 护盾修复分配 =====

    private boolean isAssignedToRepair(LivingEntity owner, double currentShield, double maxShield) {
        UUID myUUID = this.getUUID();
        UUID ownerUUID = owner.getUUID();

        List<SwarmConstructEntity> swarms = new ArrayList<>();
        for (Entity entity : ConstructManager.getInstance()
                .getActiveConstructEntities(ownerUUID, SwarmConstructTypes.SWARM).values()) {
            if (entity instanceof SwarmConstructEntity swarm
                    && swarm.isAlive()
                    && swarm.level() == this.level()) {
                swarms.add(swarm);
            }
        }

        if (swarms.isEmpty()) return false;

        swarms.sort(Comparator.comparing(Entity::getUUID));

        int totalCount = swarms.size();

        double damageRatio = maxShield > 0.0 ? (maxShield - currentShield) / maxShield : 0.0;
        if (damageRatio <= 0.0) return false;

        double repairRatio = 0.25 + 0.25 * damageRatio;
        int repairCount = (int) Math.round(totalCount * repairRatio);

        if (repairCount < 1) repairCount = 1;
        if (repairCount > totalCount) repairCount = totalCount;

        int myIndex = -1;
        for (int i = 0; i < swarms.size(); i++) {
            if (swarms.get(i).getUUID().equals(myUUID)) {
                myIndex = i;
                break;
            }
        }
        return myIndex >= 0 && myIndex < repairCount;
    }

    // ===== 索敌 =====

    private LivingEntity findTarget(LivingEntity owner) {
        Player player = owner instanceof Player p ? p : null;
        float searchRange = (float) Config.getSwarmSearchRange();

        Vec3 pos = this.position();
        AABB searchBox = new AABB(
            pos.x - searchRange, pos.y - searchRange, pos.z - searchRange,
            pos.x + searchRange, pos.y + searchRange, pos.z + searchRange
        );

        List<LivingEntity> allTargets = this.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> isValidAttackTarget(entity, owner, player));

        if (!allTargets.isEmpty()) {
            return allTargets.stream()
                    .min(Comparator.comparingDouble(t -> pos.distanceTo(t.position())))
                    .orElse(null);
        }

        return null;
    }

    private boolean isValidAttackTarget(LivingEntity entity, LivingEntity owner, @Nullable Player player) {
        if (entity == owner || entity == this) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
        if (isOwnConstruct(entity, owner.getUUID())) return false;
        if (player != null) {
            if (HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) return false;
            if (entity.distanceTo(owner) > PLAYER_MAX_TARGET_RANGE) return false;
        }
        return true;
    }

    // ===== 移动逻辑 =====

    private void pursuitMovement(Entity swarm, LivingEntity owner, LivingEntity target, double speedMult) {
        Vec3 pos = swarm.position();
        Vec3 targetPos = target.position();

        double horizontalDist = Math.sqrt(
            Math.pow(pos.x - targetPos.x, 2) + Math.pow(pos.z - targetPos.z, 2)
        );

        double moveSpeed = Config.getSwarmMoveSpeed();

        double speed = 0;
        Vec3 direction = Vec3.ZERO;

        if (horizontalDist < DIST_LEAVE) {
            Vec3 away = pos.subtract(targetPos);
            Vec3 horizontalAway = new Vec3(away.x, 0, away.z);
            if (horizontalAway.lengthSqr() > 1.0E-4) {
                direction = horizontalAway.normalize();
            }
            speed = moveSpeed * 0.5;
        } else if (horizontalDist <= DIST_STOP_MAX) {
            speed = 0;
        } else if (horizontalDist <= DIST_SLOW_MAX) {
            Vec3 toTarget = targetPos.subtract(pos);
            Vec3 horizontalToTarget = new Vec3(toTarget.x, 0, toTarget.z);
            if (horizontalToTarget.lengthSqr() > 1.0E-4) {
                direction = horizontalToTarget.normalize();
            }
            speed = moveSpeed * SLOW_APPROACH_SPEED_MULT;
        } else {
            Vec3 toTarget = targetPos.subtract(pos);
            Vec3 horizontalToTarget = new Vec3(toTarget.x, 0, toTarget.z);
            if (horizontalToTarget.lengthSqr() > 1.0E-4) {
                direction = horizontalToTarget.normalize();
            }
            double excess = horizontalDist - DIST_SLOW_MAX;
            double speedMultiplier = 1.0 + Math.min(excess, 20.0) * 0.15;
            speed = moveSpeed * speedMultiplier;
        }

        double targetHeightMin = targetPos.y + target.getBbHeight() * 0.5;
        double targetHeightMax = targetPos.y + target.getBbHeight() * 0.9;

        Vec3 verticalDirection = Vec3.ZERO;
        if (pos.y > targetHeightMax) {
            double diff = pos.y - targetHeightMax;
            verticalDirection = new Vec3(0, -moveSpeed * (1.0 + diff * 0.3) * speedMult, 0);
        } else if (pos.y < targetHeightMin) {
            double diff = targetHeightMin - pos.y;
            verticalDirection = new Vec3(0, moveSpeed * (1.0 + diff * 0.3) * speedMult, 0);
        }

        Vec3 finalMovement = direction.scale(speed * speedMult);
        if (verticalDirection != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDirection);
        }

        Vec3 boidForce = calculateBoidForce();
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = moveSpeed * 5.0 * speedMult;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);
        swarm.setDeltaMovement(finalMovement);
    }

    private void standbyMovement(Entity swarm, LivingEntity owner, double speedMult) {
        Vec3 pos = swarm.position();
        Vec3 ownerPos = owner.position();
        double standbyTargetY = ownerPos.y + STANDBY_HEIGHT;

        double horizontalDist = Math.sqrt(
            Math.pow(pos.x - ownerPos.x, 2) + Math.pow(pos.z - ownerPos.z, 2)
        );

        double dist3D = pos.distanceTo(new Vec3(ownerPos.x, standbyTargetY, ownerPos.z));
        if (dist3D > 40.0) {
            swarm.teleportTo(ownerPos.x, standbyTargetY, ownerPos.z);
            return;
        }

        double moveSpeed = Config.getSwarmMoveSpeed();

        Vec3 toOwner = new Vec3(ownerPos.x - pos.x, 0, ownerPos.z - pos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 1.0E-4 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 finalMovement = Vec3.ZERO;
        if (horizontalDist > STANDBY_RANGE) {
            double excess = horizontalDist - STANDBY_RANGE;
            double speedBoost = 1.0 + Math.min(excess, 20.0) * 0.15;
            finalMovement = horizontalDir.scale(moveSpeed * speedBoost * speedMult);
        }

        double heightDiff = standbyTargetY - pos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = moveSpeed * (1.0 + Math.abs(heightDiff) * 0.3) * speedMult;
            finalMovement = finalMovement.add(new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0));
        }

        Vec3 boidForce = calculateBoidForce();
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = moveSpeed * 5.0 * speedMult;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);
        swarm.setDeltaMovement(finalMovement);
    }

    private void repairMovement(Entity swarm, LivingEntity owner) {
        Vec3 pos = swarm.position();
        Vec3 ownerPos = owner.position();
        double attackRange = Config.getSwarmAttackRange();

        double horizontalDist = Math.sqrt(
            Math.pow(pos.x - ownerPos.x, 2) + Math.pow(pos.z - ownerPos.z, 2)
        );

        double moveSpeed = Config.getSwarmMoveSpeed();

        Vec3 toOwner = new Vec3(ownerPos.x - pos.x, 0, ownerPos.z - pos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 0.001 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 finalMovement = Vec3.ZERO;
        if (horizontalDist > attackRange) {
            double excess = horizontalDist - attackRange;
            double speedMultiplier = 1.0 + Math.min(excess, 20.0) * 0.15;
            finalMovement = horizontalDir.scale(moveSpeed * speedMultiplier);
        }

        double targetY = ownerPos.y + owner.getBbHeight() * 0.5;
        double heightDiff = targetY - pos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = moveSpeed * (1.0 + Math.abs(heightDiff) * 0.3);
            finalMovement = finalMovement.add(new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0));
        }

        Vec3 boidForce = calculateBoidForce();
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = moveSpeed * 5.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);
        swarm.setDeltaMovement(finalMovement);
    }

    // ===== 攻击逻辑 =====

    private void executeArcAttack(Entity swarm, LivingEntity owner, LivingEntity target, double attackSpeedMult) {
        if (this.level().isClientSide) return;
        if (this.attackCooldown > 0) return;

        double attackRange = Config.getSwarmAttackRange();
        double distance = swarm.distanceTo(target);
        if (distance > attackRange) return;

        Player player = owner instanceof Player p ? p : null;
        float damage = (float) this.baseAttackDamage;
        float vulnValue = (float) (Config.getSwarmVulnerabilityValue() * MothershipManager.getOverflowMultiplier(owner.getUUID()));

        Vec3 swarmPos = swarm.position();
        AABB arcBox = new AABB(
            swarmPos.x - attackRange, swarmPos.y - attackRange, swarmPos.z - attackRange,
            swarmPos.x + attackRange, swarmPos.y + attackRange, swarmPos.z + attackRange
        );

        List<LivingEntity> hits = this.level().getEntitiesOfClass(LivingEntity.class, arcBox,
                entity -> isValidAttackTarget(entity, owner, player)
                        && swarm.distanceTo(entity) <= attackRange);

        boolean hitAny = false;
        for (LivingEntity hit : hits) {
            KnockbackManager.markNoKnockback(hit.getUUID());
            hit.invulnerableTime = 0;

            if (player != null && hit.getHealth() < damage) {
                DamageSource executeSource = ModDamageSources.getExecuteDamageSource(hit, player, swarm);
                hit.hurt(executeSource, damage * 2.0f);
                if (ExecuteToggleManager.isExecuteEnabled(player)) {
                    hit.setLastHurtByMob(player);
                }
            } else {
                DamageSource source = hit.damageSources().indirectMagic(swarm, swarm);
                hit.hurt(source, damage);
            }

            hit.invulnerableTime = 0;

            if (vulnValue > 0.0f) {
                MinecraftForge.EVENT_BUS.post(
                    new VulnerabilityApplyEvent("swarm_arc", vulnValue, hit, true)
                );
            }

            hitAny = true;
        }

        if (hitAny) {
            spawnAttackEnergyWave(swarm);
            this.level().playSound(null, swarm.blockPosition(), SoundEvents.SHULKER_SHOOT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.4f, 1.5f);
        }

        double attackInterval = Config.getSwarmAttackInterval();
        int cooldown = (int) (attackInterval * 20.0 / (attackSpeedMult * this.attackSpeedMultiplier));
        this.attackCooldown = Math.max(1, cooldown);
    }

    private void executeShieldRepair(Entity swarm, LivingEntity owner) {
        if (this.level().isClientSide) return;
        if (this.attackCooldown > 0) return;

        double attackRange = Config.getSwarmAttackRange();
        double distance = swarm.distanceTo(owner);
        if (distance > attackRange) return;

        double damage = this.baseAttackDamage;
        double restore = damage * Config.getSwarmShieldRepairMultiplier();

        ShieldManager.addShield(owner.getUUID(), restore);

        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 swarmMidPos = swarm.position().add(0, swarm.getBbHeight() * 0.5, 0);
            Vec3 ownerMidPos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
            Vec3 direction = ownerMidPos.subtract(swarmMidPos).normalize();
            NetworkHandler.sendSwarmEnergyWaveToAll(serverLevel, swarm.getId(), swarmMidPos, direction, true);
        }

        this.level().playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.5f);

        double attackInterval = Config.getSwarmAttackInterval();
        int cooldown = (int) (attackInterval * 20.0 / this.attackSpeedMultiplier);
        this.attackCooldown = Math.max(1, cooldown);
    }

    private void spawnAttackEnergyWave(Entity swarm) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Vec3 swarmMidPos = swarm.position().add(0, swarm.getBbHeight() * 0.5, 0);
        Vec3 lookDir = this.getLookAngle().normalize();
        NetworkHandler.sendSwarmEnergyWaveToAll(serverLevel, swarm.getId(), swarmMidPos, lookDir, false);
    }

    // ===== 属性 =====

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 1.6)
            .add(Attributes.FOLLOW_RANGE, 20.0)
            .add(Attributes.ATTACK_DAMAGE, 0.08);
    }

    /**
     * 蜂群重写：根据等阶重算基础生命/伤害，再应用 MAX_HEALTH + refreshConstructAttributes。
     * 当蜂群数量超过极限值时，溢出倍率会放大基础属性以保持等效战力。
     */
    protected void applyAttributeModifiers() {
        double tierMult = getTierMultiplier();
        double overflowMult = MothershipManager.getOverflowMultiplier(getOwnerUUID());
        this.baseMaxHealth = Config.getSwarmBaseHealth() * tierMult * overflowMult;
        this.baseAttackDamage = Config.getSwarmBaseDamage() * tierMult * overflowMult;
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseMaxHealth);
        }
        refreshConstructAttributes();
    }

    // ===== NBT =====

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerUUID != null) {
            tag.putUUID("owner", this.ownerUUID);
        }
        tag.putInt("tier", this.tier);
        float maxH = this.getMaxHealth();
        tag.putFloat("health_ratio", maxH > 0 ? this.getHealth() / maxH : 1.0f);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("owner")) {
            this.ownerUUID = tag.getUUID("owner");
        }
        if (tag.contains("tier")) {
            this.tier = tag.getInt("tier");
        }
        applyAttributeModifiers();

        if (tag.contains("health_ratio")) {
            float healthRatio = tag.getFloat("health_ratio");
            this.setHealth(this.getMaxHealth() * healthRatio);
        }
    }

    // ===== GeckoLib =====

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableInstanceCache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }
}
