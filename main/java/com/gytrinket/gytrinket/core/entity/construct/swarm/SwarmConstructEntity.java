package com.gytrinket.gytrinket.core.entity.construct.swarm;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeApplier;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModDamageSources;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.BoidConfig;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.BoidHelper;
import com.gytrinket.gytrinket.core.execute.ExecuteToggleManager;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import com.gytrinket.gytrinket.core.modifier.player.knockback.KnockbackManager;
import com.gytrinket.gytrinket.network.NetworkHandler;
import com.gytrinket.gytrinket.core.shield.ShieldData;
import com.gytrinket.gytrinket.core.shield.ShieldManager;
import com.gytrinket.gytrinket.core.vulnerability.VulnerabilityApplyEvent;
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
import net.neoforged.neoforge.common.NeoForge;

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
    private static final BoidConfig BOID_CONFIG = new BoidConfig(
            0.6,  // comfortRange
            1.2,  // separationRange
            0.035,// separationStrength
            4.0,  // cohesionRange
            0.02, // cohesionStrength
            3.0,  // alignmentRange
            0.025 // alignmentStrength
    );
    private static final double VELOCITY_DAMPING = 0.85;

    public SwarmConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.baseMaxHealth = Config.getSwarmBaseHealth();
        this.baseAttackDamage = Config.getSwarmBaseDamage();
    }

    public SwarmConstructEntity(Level level, UUID ownerUUID, SwarmConstruct swarmConstruct) {
        this(ModEntities.SWARM_CONSTRUCT.get(), level);
        setOwnerUUID(ownerUUID);
        this.swarmConstruct = swarmConstruct;
        this.tier = swarmConstruct.getTier();
        applyAttributeModifiers();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REPAIR_MODE, false);
        builder.define(DATA_SHIELD_BROKEN, false);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    public SwarmConstruct getSwarmConstruct() {
        return swarmConstruct;
    }

    public int getTier() {
        return tier;
    }

    /** 设置等阶并重新应用属性修饰器（用于退出待机/重登恢复时重建实体） */
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
        // 服务端：从 ShieldManager 计算并同步给客户端
        // 客户端：ShieldManager 数据不可用，读取同步标志
        boolean shieldBroken;
        boolean repairMode;
        double speedMult;
        double attackSpeedMult;

        if (this.level().isClientSide) {
            // 客户端：使用服务端同步的标志
            shieldBroken = this.entityData.get(DATA_SHIELD_BROKEN);
            repairMode = this.entityData.get(DATA_REPAIR_MODE);
            speedMult = shieldBroken ? SHIELD_BROKEN_BOOST : 1.0;
            attackSpeedMult = 1.0; // 客户端不执行攻击，无需
        } else {
            // 服务端：从护盾数据计算
            ShieldData shieldData = ShieldManager.getShieldData(owner.getUUID());
            double currentShield = shieldData != null ? shieldData.getCurrentShield() : 0.0;
            double maxShield = shieldData != null ? shieldData.getMaxShield() : 0.0;

            // 护盾破裂：未激活护盾（maxShield <= 0）或护盾值耗尽（currentShield <= 0）
            shieldBroken = maxShield <= 0.0 || currentShield <= 0.0;
            boolean shieldDamaged = maxShield > 0.0 && currentShield > 0.0 && currentShield < maxShield;
            speedMult = shieldBroken ? SHIELD_BROKEN_BOOST : 1.0;
            attackSpeedMult = shieldBroken ? SHIELD_BROKEN_BOOST : 1.0;

            // 修复模式：仅在护盾受损（未破裂）时分配部分蜂群
            repairMode = shieldDamaged && isAssignedToRepair(owner, currentShield, maxShield);

            // 同步给客户端（避免客户端因无护盾数据而误入待机分支覆盖朝向）
            this.entityData.set(DATA_SHIELD_BROKEN, shieldBroken);
            this.entityData.set(DATA_REPAIR_MODE, repairMode);
        }

        if (repairMode) {
            repairMovement(this, owner);
            // 修复模式：朝向玩家身高一半处
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
                // 无攻击目标：朝向玩家位置高 STANDBY_HEIGHT 格处，移动方向为朝向方向
                Vec3 standbyFacePos = owner.position().add(0, STANDBY_HEIGHT, 0);
                standbyMovement(this, owner, speedMult);
                facePositionWithInterpolation(standbyFacePos, 20.0f);
                // 高度达到跟随高度（2格容差）时，忽略俯仰角，避免朝向诡异
                if (Math.abs(this.getY() - standbyFacePos.y) <= 2.0) {
                    this.setXRot(0.0f);
                }
            }
        }
    }

    // ===== 护盾修复分配 =====

    /**
     * 判定本蜂群是否被分配到护盾修复模式。
     * <p>
     * 取同一玩家所有存活蜂群，按 UUID 排序。
     * 修复数量随护盾受损程度从 0% 线性提高到 50%（最多 totalCount/2）。
     * 护盾受损时至少分配 1 只修复（只要存在蜂群）。
     */
    private boolean isAssignedToRepair(LivingEntity owner, double currentShield, double maxShield) {
        UUID myUUID = this.getUUID();
        UUID ownerUUID = owner.getUUID();

        // 从 ConstructManager 注册表获取同玩家所有蜂群实体（避免 64 格 AABB 扫描）
        List<SwarmConstructEntity> swarms = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : ConstructManager.getInstance()
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

        // 护盾受损程度 (0.0 ~ 1.0)
        double damageRatio = maxShield > 0.0 ? (maxShield - currentShield) / maxShield : 0.0;
        if (damageRatio <= 0.0) return false;

        // 修复比例：随受损程度从 25% 线性提高到 50%
        double repairRatio = 0.25 + 0.25 * damageRatio;
        int repairCount = (int) Math.round(totalCount * repairRatio);

        // 护盾受损时至少 1 只修复（只要存在蜂群）
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

    /**
     * 搜索目标：以自身为中心 searchRange 内查找，但不可选择玩家 PLAYER_MAX_TARGET_RANGE 格外的敌人。
     */
    private LivingEntity findTarget(LivingEntity owner) {
        Player player = owner instanceof Player p ? p : null;
        float searchRange = (float) Config.getSwarmSearchRange();
        return findTarget(owner, searchRange, entity -> isValidAttackTarget(entity, owner, player));
    }

    /**
     * 判断实体是否为合法攻击目标。
     * 排除：自身、归属者、死亡、傀儡、玩家保护的实体、非敌对实体、玩家自己的构造体。
     */
    private boolean isValidAttackTarget(LivingEntity entity, LivingEntity owner, @Nullable Player player) {
        if (entity == owner || entity == this) return false;
        if (!entity.isAlive()) return false;
        if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
        // 排除玩家自己的构造体（避免友伤）
        if (isOwnConstruct(entity, owner.getUUID())) return false;
        if (player != null) {
            if (HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) return false;
            if (entity.distanceTo(owner) > PLAYER_MAX_TARGET_RANGE) return false;
        }
        return true;
    }

    // ===== 移动逻辑 =====

    /**
     * 追击移动：基于与目标的水平距离分级控制行为，叠加 Boid 集群力。
     * 距离分级：
     *   d < 1          : 太近，尝试离开（远离目标）
     *   1 <= d <= 2.5  : 理想距离，停止水平移动
     *   2.5 < d <= 5   : 边缘，慢速接近
     *   d > 5          : 超出范围，追击（全速接近，远处略加速）
     */
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
            // 离开：远离目标
            Vec3 away = pos.subtract(targetPos);
            Vec3 horizontalAway = new Vec3(away.x, 0, away.z);
            if (horizontalAway.lengthSqr() > 1.0E-4) {
                direction = horizontalAway.normalize();
            }
            speed = moveSpeed;
        } else if (horizontalDist <= DIST_STOP_MAX) {
            // 停止移动
            speed = 0;
        } else if (horizontalDist <= DIST_SLOW_MAX) {
            // 慢速接近
            Vec3 toTarget = targetPos.subtract(pos);
            Vec3 horizontalToTarget = new Vec3(toTarget.x, 0, toTarget.z);
            if (horizontalToTarget.lengthSqr() > 1.0E-4) {
                direction = horizontalToTarget.normalize();
            }
            speed = moveSpeed * SLOW_APPROACH_SPEED_MULT;
        } else {
            // 追击：全速接近，远处略加速
            Vec3 toTarget = targetPos.subtract(pos);
            Vec3 horizontalToTarget = new Vec3(toTarget.x, 0, toTarget.z);
            if (horizontalToTarget.lengthSqr() > 1.0E-4) {
                direction = horizontalToTarget.normalize();
            }
            double excess = horizontalDist - DIST_SLOW_MAX;
            double speedMultiplier = 1.0 + Math.min(excess, 20.0) * 0.15;
            speed = moveSpeed * speedMultiplier;
        }

        // 高度调整：保持在目标身高 50%~90% 区间
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

        // 叠加 Boid 集群力
        Vec3 boidForce = BoidHelper.calculateBoidForce(swarm, owner, SwarmConstructEntity.class, BOID_CONFIG);
        finalMovement = finalMovement.add(boidForce);

        // 限制最大速度
        double maxSpeed = moveSpeed * 5.0 * speedMult;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);
        swarm.setDeltaMovement(finalMovement);
    }

    /**
     * 待机移动：水平朝向玩家移动，垂直保持在 STANDBY_HEIGHT 高度（与朝向位置高度一致）。
     * <p>
     * 高度调整速度与自身移动速度（moveSpeed * speedMult）相关，距离越远调整越快。
     * 叠加 Boid 集群力。
     */
    private void standbyMovement(Entity swarm, LivingEntity owner, double speedMult) {
        Vec3 pos = swarm.position();
        Vec3 ownerPos = owner.position();
        // 待机保持高度 = 朝向位置高度（均使用 STANDBY_HEIGHT，保证一致）
        double standbyTargetY = ownerPos.y + STANDBY_HEIGHT;

        double horizontalDist = Math.sqrt(
            Math.pow(pos.x - ownerPos.x, 2) + Math.pow(pos.z - ownerPos.z, 2)
        );

        // 过远时传送（3D 距离）
        double dist3D = pos.distanceTo(new Vec3(ownerPos.x, standbyTargetY, ownerPos.z));
        if (dist3D > 40.0) {
            swarm.teleportTo(ownerPos.x, standbyTargetY, ownerPos.z);
            return;
        }

        double moveSpeed = Config.getSwarmMoveSpeed();

        // 水平移动：朝向玩家，超过 STANDBY_RANGE 时加速接近
        Vec3 toOwner = new Vec3(ownerPos.x - pos.x, 0, ownerPos.z - pos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 1.0E-4 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 finalMovement = Vec3.ZERO;
        if (horizontalDist > STANDBY_RANGE) {
            double excess = horizontalDist - STANDBY_RANGE;
            double speedBoost = 1.0 + Math.min(excess, 20.0) * 0.15;
            finalMovement = horizontalDir.scale(moveSpeed * speedBoost * speedMult);
        }

        // 高度调整：保持在 STANDBY_HEIGHT，速度与自身速度相关
        double heightDiff = standbyTargetY - pos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = moveSpeed * (1.0 + Math.abs(heightDiff) * 0.3) * speedMult;
            finalMovement = finalMovement.add(new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0));
        }

        Vec3 boidForce = BoidHelper.calculateBoidForce(swarm, owner, SwarmConstructEntity.class, BOID_CONFIG);
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = moveSpeed * 5.0 * speedMult;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);
        swarm.setDeltaMovement(finalMovement);
    }

    /**
     * 修复模式移动：靠近归属者（用于触发护盾恢复），叠加 Boid 集群力。
     */
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

        // 高度贴近玩家
        double targetY = ownerPos.y + owner.getBbHeight() * 0.5;
        double heightDiff = targetY - pos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = moveSpeed * (1.0 + Math.abs(heightDiff) * 0.3);
            finalMovement = finalMovement.add(new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0));
        }

        Vec3 boidForce = BoidHelper.calculateBoidForce(swarm, owner, SwarmConstructEntity.class, BOID_CONFIG);
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

    /**
     * 电弧攻击（范围）：对攻击范围内所有合法敌人造成单次伤害，并施加可叠加易伤。
     */
    private void executeArcAttack(Entity swarm, LivingEntity owner, LivingEntity target, double attackSpeedMult) {
        if (this.level().isClientSide) return;
        if (this.attackCooldown > 0) return;

        double attackRange = Config.getSwarmAttackRange();
        double distance = swarm.distanceTo(target);
        if (distance > attackRange) return;

        Player player = owner instanceof Player p ? p : null;
        float damage = (float) this.baseAttackDamage;
        float vulnValue = (float) (Config.getSwarmVulnerabilityValue() * MothershipManager.getOverflowMultiplier(owner.getUUID()));

        // 范围攻击：攻击范围内所有合法敌人
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
            // 攻击前：取消击退标记和无敌时间（参考光束炮/无人机子弹）
            KnockbackManager.markNoKnockback(hit.getUUID());
            hit.invulnerableTime = 0;

            // 斩杀判定：目标当前血量低于伤害时触发斩杀（伤害翻倍，归属玩家）
            if (player != null && hit.getHealth() < damage) {
                DamageSource executeSource = ModDamageSources.getExecuteDamageSource(hit, player, swarm);
                hit.hurt(executeSource, damage * 2.0f);
                if (ExecuteToggleManager.isExecuteEnabled(player)) {
                    hit.setLastHurtByMob(player);
                }
            } else {
                // 普通伤害（间接魔法伤害，非灼烧，归属蜂群构造体）
                DamageSource source = hit.damageSources().indirectMagic(swarm, swarm);
                hit.hurt(source, damage);
            }

            // 攻击后：取消无敌时间
            hit.invulnerableTime = 0;

            // 施加可叠加易伤
            if (vulnValue > 0.0f) {
                NeoForge.EVENT_BUS.post(
                    new VulnerabilityApplyEvent("swarm_arc", vulnValue, hit, true)
                );
            }

            hitAny = true;
        }

        // 只生成一次能量波（不因多目标重复）
        if (hitAny) {
            spawnAttackEnergyWave(swarm);
            this.level().playSound(null, swarm.blockPosition(), SoundEvents.SHULKER_SHOOT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.4f, 1.5f);
        }

        double attackInterval = Config.getSwarmAttackInterval();
        int cooldown = (int) (attackInterval * 20.0 / (attackSpeedMult * this.attackSpeedMultiplier));
        this.attackCooldown = Math.max(1, cooldown);
    }

    /**
     * 护盾修复：通过攻击行为触发，将伤害转化为归属者护盾恢复。
     * 转化率 = 伤害值 * SWARM_SHIELD_REPAIR_MULTIPLIER（默认5倍）。
     */
    private void executeShieldRepair(Entity swarm, LivingEntity owner) {
        if (this.level().isClientSide) return;
        if (this.attackCooldown > 0) return;

        double attackRange = Config.getSwarmAttackRange();
        double distance = swarm.distanceTo(owner);
        if (distance > attackRange) return;

        double damage = this.baseAttackDamage;
        double restore = damage * Config.getSwarmShieldRepairMultiplier();

        ShieldManager.addShield(owner.getUUID(), restore);

        // 修复能量波：从蜂群半身高处朝向玩家半身高处发射
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

    /**
     * 攻击能量波：从蜂群半身高处沿自身朝向发射。
     */
    private void spawnAttackEnergyWave(Entity swarm) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Vec3 swarmMidPos = swarm.position().add(0, swarm.getBbHeight() * 0.5, 0);
        Vec3 lookDir = this.getLookAngle().normalize();
        NetworkHandler.sendSwarmEnergyWaveToAll(serverLevel, swarm.getId(), swarmMidPos, lookDir, false);
    }

    // ===== 属性和动画 =====

    public static AttributeSupplier.Builder createAttributes() {
        // 注册时使用默认值，实际值由 applyAttributeModifiers() 从 Config + 等阶覆盖
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 1.6)
            .add(Attributes.FOLLOW_RANGE, 20.0)
            .add(Attributes.ATTACK_DAMAGE, 0.08);
    }

    // ===== NBT 钩子实现 =====

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

    // ===== 属性应用 =====

    /**
     * 蜂群重写：根据等阶重算基础生命/伤害，再调用基类应用 MAX_HEALTH + refreshConstructAttributes。
     * 当蜂群数量超过极限值时，溢出倍率会放大基础属性以保持等效战力。
     */
    @Override
    protected void applyAttributeModifiers() {
        double tierMult = getTierMultiplier();
        double overflowMult = MothershipManager.getOverflowMultiplier(getOwnerUUID());
        this.baseMaxHealth = Config.getSwarmBaseHealth() * tierMult * overflowMult;
        this.baseAttackDamage = Config.getSwarmBaseDamage() * tierMult * overflowMult;
        super.applyAttributeModifiers();
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
}
