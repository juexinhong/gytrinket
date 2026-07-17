package com.gy_mod.gy_trinket.core.entity.construct.swarm;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.*;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModDamageSources;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.BoidCalculator;
import com.gy_mod.gy_trinket.core.execute.ExecuteToggleManager;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.modifier.player.knockback.KnockbackManager;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.network.NetworkHandler;
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
 * <p>
 * 性能优化：通过 ConstructGroupCache 共享 Boid 邻居数据、索敌结果、护盾状态和修复分配，
 * 避免每实体独立重复查询导致的 O(N²) 开销。
 */
public class SwarmConstructEntity extends AbstractConstructEntity {

    /** 等阶：0=基础 1=标准 2=高阶 */
    private int tier = SwarmConstructTypes.TIER_BASIC;

    private SwarmConstruct swarmConstruct;

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
    /** 护盾破裂攻击速度增益倍率：+100%攻速 */
    private static final double SHIELD_BROKEN_ATTACK_SPEED_MULT = 2.0;
    /** 护盾破裂移动速度增益倍率：+50%移速 */
    private static final double SHIELD_BROKEN_MOVE_SPEED_MULT = 1.5;

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

    public SwarmConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.baseMaxHealth = Config.getSwarmBaseHealth();
        this.baseAttackDamage = Config.getSwarmBaseDamage();
        // 攻击冷却错峰：创建时随机偏移初始冷却，避免所有蜂群同一帧攻击
        this.attackCooldown = level.random.nextInt(Math.max(1, (int)(Config.getSwarmAttackInterval() * 20)));
    }

    public SwarmConstructEntity(Level level, UUID ownerUUID, SwarmConstruct swarmConstruct) {
        this(com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities.SWARM_CONSTRUCT.get(), level);
        setOwnerUUID(ownerUUID);
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

    // ===== 抽象方法实现 =====

    @Override
    protected String getConstructTypeId() {
        return SwarmConstructTypes.SWARM;
    }

    @Override
    protected ConstructData createConstructDataForRegistration(ServerPlayer ownerPlayer) {
        SwarmConstructData newData = new SwarmConstructData(
            SwarmConstructTypes.SWARM,
            this.getUUID(),
            this.getBaseMaxHealth()
        );
        newData.setTier(this.tier);
        return newData;
    }

    @Override
    protected void applyConstructAttributes(UUID playerUUID, Map<String, Double> attributes) {
        ConstructAttributeApplier.applyAttributesToSwarm(playerUUID, this, attributes);
    }

    @Override
    public void refreshConstructAttributes() {
        if (getOwnerUUID() == null) return;
        UUID playerUUID = getOwnerUUID();
        ServerPlayer player = null;
        if (this.level().getServer() != null) {
            player = this.level().getServer().getPlayerList().getPlayer(playerUUID);
        }
        if (player != null) {
            ConstructAttributeApplier.applyAttributesToSwarm(playerUUID, this, ConstructAttributeApplier.computeConstructAttributes(playerUUID));
        }
    }

    // ===== 蜂群特有属性 =====

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

    /**
     * 蜂群重写：根据等阶重算基础生命/伤害，再应用 MAX_HEALTH + refreshConstructAttributes。
     * 当蜂群数量超过极限值时，溢出倍率会放大基础属性以保持等效战力。
     */
    @Override
    public void applyAttributeModifiers() {
        double tierMult = getTierMultiplier();
        double overflowMult = MothershipManager.getOverflowMultiplier(getOwnerUUID());
        this.baseMaxHealth = Config.getSwarmBaseHealth() * tierMult * overflowMult;
        this.baseAttackDamage = Config.getSwarmBaseDamage() * tierMult * overflowMult;
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseMaxHealth);
        }
        refreshConstructAttributes();
    }

    // ===== NBT 蜂群特有数据 =====

    @Override
    protected void addTypeSpecificSaveData(CompoundTag tag) {
        tag.putInt("tier", this.tier);
    }

    @Override
    protected void readTypeSpecificSaveData(CompoundTag tag) {
        if (tag.contains("tier")) {
            this.tier = tag.getInt("tier");
        }
    }

    // ===== 属性 =====

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 1.6)
            .add(Attributes.FOLLOW_RANGE, 20.0)
            .add(Attributes.ATTACK_DAMAGE, 0.08);
    }

    // ===== Boid 集群力（使用共享缓存） =====

    /**
     * 计算Boid集群力，使用 ConstructGroupCache 共享邻居数据。
     * 同玩家的所有蜂群共享一次 getEntitiesOfClass 查询结果。
     */
    private Vec3 calculateBoidForce() {
        LivingEntity owner = getOwner() instanceof LivingEntity l ? l : null;
        if (owner == null) return Vec3.ZERO;

        UUID ownerUUID = owner.getUUID();
        ConstructGroupCache cache = ConstructGroupCache.getInstance();

        // 使用缓存获取排除自身的邻居数据
        ConstructGroupCache.NeighborData neighborData = cache.getNeighborData(
            ownerUUID, SwarmConstructTypes.SWARM, this.getUUID(), this.level(), owner.position());

        Vec3 pos = this.position();
        Vec3 velocity = this.getDeltaMovement();

        Vec3 separation = BoidCalculator.separation(pos, neighborData.positions,
                BOID_COMFORT_RANGE, BOID_SEPARATION_RANGE, BOID_SEPARATION_STRENGTH);
        Vec3 cohesion = BoidCalculator.cohesion(pos, neighborData.positions,
                BOID_COMFORT_RANGE, BOID_COHESION_RANGE, BOID_COHESION_STRENGTH);
        Vec3 alignment = BoidCalculator.alignment(velocity, neighborData.velocities,
                BOID_ALIGNMENT_RANGE, BOID_ALIGNMENT_STRENGTH);

        return separation.add(cohesion).add(alignment);
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

        // ===== 护盾状态判定（使用共享缓存） =====
        // 破裂加成：护盾值≤0 → 攻速+100%，移速+50%，与是否移植无关
        // 修复模式：护盾未移植 且 护盾值>0 且 护盾未满 → 部分蜂群修复护盾
        boolean shieldBroken;
        boolean repairMode;
        double speedMult;
        double attackSpeedMult;

        if (this.level().isClientSide) {
            shieldBroken = this.entityData.get(DATA_SHIELD_BROKEN);
            repairMode = this.entityData.get(DATA_REPAIR_MODE);
            speedMult = shieldBroken ? SHIELD_BROKEN_MOVE_SPEED_MULT : 1.0;
            attackSpeedMult = 1.0;
        } else {
            // 使用共享缓存获取护盾状态，避免每蜂群独立读取 ShieldManager
            ConstructGroupCache cache = ConstructGroupCache.getInstance();
            ConstructGroupCache.CachedShieldState shieldState = cache.getShieldState(owner.getUUID(), this.level());

            shieldBroken = shieldState.broken;
            speedMult = shieldBroken ? SHIELD_BROKEN_MOVE_SPEED_MULT : 1.0;
            attackSpeedMult = shieldBroken ? SHIELD_BROKEN_ATTACK_SPEED_MULT : 1.0;

            // 修复模式：可修复 且 被分配修复
            repairMode = shieldState.canRepair && cache.getRepairAssignment(owner.getUUID(), this.level()).isAssigned(this.getUUID());

            this.entityData.set(DATA_SHIELD_BROKEN, shieldBroken);
            this.entityData.set(DATA_REPAIR_MODE, repairMode);
        }

        if (repairMode) {
            repairMovement(this, owner);
            Vec3 repairFacePos = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
            facePositionWithInterpolation(repairFacePos, 20.0f);
            executeShieldRepair(this, owner);
        } else {
            // 使用共享缓存索敌
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

    // ===== 索敌（使用共享缓存） =====

    /**
     * 使用 ConstructGroupCache 共享索敌结果。
     * 同玩家的所有蜂群共享一次以玩家为中心的索敌查询，
     * 此方法仅做距离过滤取最近目标。
     */
    private LivingEntity findTarget(LivingEntity owner) {
        float searchRange = (float) Config.getSwarmSearchRange();
        return ConstructGroupCache.getInstance().findNearestTarget(
            owner.getUUID(), owner, this.position(), searchRange);
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

        double baseAttackRange = Config.getSwarmAttackRange();
        // 使用目标身高7/10处为检查点
        Vec3 targetCheckPos = target.position().add(0, target.getBbHeight() * 0.7, 0);
        // 大碰撞箱优化：目标碰撞箱宽度每有1格，攻击范围增加1格
        double effectiveAttackRange = baseAttackRange + (int) target.getBbWidth();

        double distance = swarm.position().distanceTo(targetCheckPos);
        if (distance > effectiveAttackRange) return;

        Player player = owner instanceof Player p ? p : null;
        float damage = (float) this.baseAttackDamage;
        float vulnValue = (float) (Config.getSwarmVulnerabilityValue() * MothershipManager.getOverflowMultiplier(owner.getUUID()));

        // 使用共享索敌缓存获取攻击范围内目标，而非独立做 getEntitiesOfClass
        float arcSearchRange = (float) (baseAttackRange + 4.0);
        List<LivingEntity> hits = ConstructGroupCache.getInstance().findTargetsInRange(
            owner.getUUID(), owner, swarm.position(), arcSearchRange);

        boolean hitAny = false;
        for (LivingEntity hit : hits) {
            // 使用目标身高7/10处为检查点，并根据碰撞箱宽度调整攻击范围
            Vec3 hitCheckPos = hit.position().add(0, hit.getBbHeight() * 0.7, 0);
            double hitEffectiveRange = baseAttackRange + (int) hit.getBbWidth();
            if (swarm.position().distanceTo(hitCheckPos) > hitEffectiveRange) continue;

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
}
