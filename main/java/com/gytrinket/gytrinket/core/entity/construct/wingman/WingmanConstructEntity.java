package com.gytrinket.gytrinket.core.entity.construct.wingman;

import com.gytrinket.gytrinket.Config;
import com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.ConstructAttributeApplier;
import com.gytrinket.gytrinket.core.entity.construct.ConstructData;
import com.gytrinket.gytrinket.core.entity.construct.drone.ModEntities;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.BoidConfig;
import com.gytrinket.gytrinket.core.entity.construct.drone.behavior.BoidHelper;
import com.gytrinket.gytrinket.core.hostile_target.HostileTargetManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;

/**
 * 僚机构造体实体类
 * <p>
 * 高阶其他构造体，行为接近无人机追击阵列。
 * 攻击时发射多枚爆破弹，爆破弹命中造成伤害并在销毁时产生模拟爆炸。
 * 无阵列系统，常驻无物理效果。
 */
public class WingmanConstructEntity extends AbstractConstructEntity {

    private WingmanConstruct wingmanConstruct;

    // ===== 追击行为参数 =====
    /** 索敌范围 */
    private static final float SEARCH_RANGE = 20.0f;
    /** 基础移动速度（无人机1.5倍） */
    private static final float MOVE_SPEED = 0.45f; // 无人机0.3 * 1.5
    /** 远离速度 */
    private static final float LEAVE_SPEED = 0.2f; // 无人机0.2
    /** 高度调整速度 */
    private static final float HEIGHT_ADJUST_SPEED = 0.2f; // 无人机0.2
    /** 待机跟随高度 */
    private static final float STANDBY_HEIGHT = 4.0f;
    /** 待机跟随触发距离 */
    private static final float STANDBY_RANGE = 7.0f;

    // Boid参数（僚机较松散）
    private static final BoidConfig BOID_CONFIG = new BoidConfig(
            2.0,  // comfortRange
            3.0,  // separationRange
            0.06, // separationStrength
            8.0,  // cohesionRange
            0.015,// cohesionStrength
            5.0,  // alignmentRange
            0.02  // alignmentStrength
    );
    private static final double VELOCITY_DAMPING = 0.8;

    public WingmanConstructEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.baseMaxHealth = Config.getWingmanBaseHealth();
        this.baseAttackDamage = Config.getWingmanExplosiveDamage();
    }

    public WingmanConstructEntity(Level level, UUID ownerUUID, WingmanConstruct wingmanConstruct) {
        this(ModEntities.WINGMAN_CONSTRUCT.get(), level);
        setOwnerUUID(ownerUUID);
        this.wingmanConstruct = wingmanConstruct;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    public WingmanConstruct getWingmanConstruct() {
        return wingmanConstruct;
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

        Entity owner = this.getOwner();
        if (owner != null && owner.isAlive() && this.isAlive()) {
            // 搜索目标
            LivingEntity target = findTarget((LivingEntity) owner);

            if (target != null) {
                // 追击模式
                pursuitMovement(this, (LivingEntity) owner, target);

                // 朝向目标（插值旋转）
                faceTargetWithInterpolation(target);

                // 攻击
                executeAttack(this, (LivingEntity) owner, target);
            } else {
                // 待机模式
                standbyMovement(this, (LivingEntity) owner);

                // 朝向玩家方向
                faceOwnerDirection((LivingEntity) owner);
            }
        }
    }

    // ===== 追击行为逻辑 =====

    /**
     * 搜索目标
     * 不可选择以玩家为中心半径 PLAYER_MAX_TARGET_RANGE 格之外的敌人
     */
    private LivingEntity findTarget(LivingEntity owner) {
        Player player = owner instanceof Player p ? p : null;
        return findTarget(owner, SEARCH_RANGE, entity -> {
            if (entity == owner || entity == this) return false;
            if (!entity.isAlive()) return false;
            if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
            // 排除玩家自己的构造体（避免友伤）
            if (isOwnConstruct(entity, owner.getUUID())) return false;
            if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) return false;
            // 限制：不可选择玩家 PLAYER_MAX_TARGET_RANGE 格外的敌人
            return entity.distanceTo(owner) <= PLAYER_MAX_TARGET_RANGE;
        });
    }

    /**
     * 追击模式移动逻辑
     * <p>
     * 根据与目标的距离执行不同的移动策略：
     * - 距离>8格：向自身朝向移动，速度随距离增加（每额外1格+10%）
     * - 距离7-8格：以慢速接近目标
     * - 距离6-7格：保持位置，仅调整高度
     * - 距离<6格：以慢速离开目标
     * 高度保持在目标身高的80%-100%之间
     * 所有状态下都叠加Boid集群力（分离+聚合+对齐）
     */
    private void pursuitMovement(Entity wingman, LivingEntity owner, LivingEntity target) {
        Vec3 pos = wingman.position();
        Vec3 targetPos = target.position();

        double horizontalDist = Math.sqrt(
            Math.pow(pos.x - targetPos.x, 2) + Math.pow(pos.z - targetPos.z, 2)
        );

        double speed = 0;
        Vec3 direction = Vec3.ZERO;
        float yaw = wingman.getYRot() * (float) Math.PI / 180.0f;

        if (horizontalDist > 8.0) {
            // 远距离：向朝向方向移动，速度随距离增加（每额外1格+10%）
            float excessDistance = (float) (horizontalDist - 8.0);
            float speedMultiplier = 1.0f + excessDistance * 0.10f;
            speed = MOVE_SPEED * speedMultiplier;
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
        } else if (horizontalDist > 7.0) {
            // 中远距离：慢速接近目标
            speed = LEAVE_SPEED;
            Vec3 toTarget = targetPos.subtract(pos).normalize();
            direction = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        } else if (horizontalDist > 6.0) {
            // 理想距离：不水平移动，仅调整高度
            speed = 0;
            direction = Vec3.ZERO;
        } else {
            // 过近：慢速离开目标
            speed = LEAVE_SPEED;
            Vec3 awayDirection = pos.subtract(targetPos).normalize();
            direction = new Vec3(awayDirection.x, 0, awayDirection.z).normalize();
        }

        // 高度调整逻辑：目标身高的80%-100%
        double targetHeightMin = targetPos.y + target.getBbHeight() * 0.8;
        double targetHeightMax = targetPos.y + target.getBbHeight() * 1.0;

        Vec3 verticalDirection = Vec3.ZERO;

        if (pos.y >= targetHeightMin && pos.y <= targetHeightMax) {
            // 在目标高度范围内
        } else if (pos.y > targetHeightMax) {
            double heightDiff = pos.y - targetHeightMax;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, -HEIGHT_ADJUST_SPEED * speedFactor, 0);
        } else if (pos.y < targetHeightMin) {
            double heightDiff = targetHeightMin - pos.y;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, HEIGHT_ADJUST_SPEED * speedFactor, 0);
        }

        // 合并水平和垂直方向
        Vec3 finalMovement = direction.scale(speed);
        if (verticalDirection != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDirection);
        }

        // 叠加Boid集群力（分离+聚合+对齐）
        Vec3 boidForce = BoidHelper.calculateBoidForce(wingman, owner, WingmanConstructEntity.class, BOID_CONFIG);
        finalMovement = finalMovement.add(boidForce);

        // 限制最大速度
        double maxSpeed = MOVE_SPEED * 2.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        // 速度阻尼：防止过冲振荡
        finalMovement = finalMovement.scale(VELOCITY_DAMPING);

        wingman.setDeltaMovement(finalMovement);
    }

    private void standbyMovement(Entity wingman, LivingEntity owner) {
        Vec3 pos = wingman.position();
        Vec3 ownerPos = owner.position();

        Vec3 standbyTarget = ownerPos.add(0, STANDBY_HEIGHT, 0);

        double horizontalDist = Math.sqrt(
            Math.pow(pos.x - ownerPos.x, 2) + Math.pow(pos.z - ownerPos.z, 2)
        );

        if (horizontalDist > 20.0) {
            wingman.teleportTo(ownerPos.x, ownerPos.y + STANDBY_HEIGHT, ownerPos.z);
            return;
        }

        Vec3 toOwner = new Vec3(ownerPos.x - pos.x, 0, ownerPos.z - pos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 0.001 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 verticalDir = Vec3.ZERO;
        double heightDiff = standbyTarget.y - pos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = HEIGHT_ADJUST_SPEED * (1.0 + Math.abs(heightDiff) * 0.5);
            verticalDir = new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0);
        }

        Vec3 finalMovement = Vec3.ZERO;

        if (horizontalDist > STANDBY_RANGE) {
            double speedBoost = 1.0 + (horizontalDist - STANDBY_RANGE) * 0.2;
            finalMovement = horizontalDir.scale(MOVE_SPEED * speedBoost);
        } else if (horizontalDist > 3.0) {
            finalMovement = horizontalDir.scale(LEAVE_SPEED);
        }

        if (verticalDir != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDir);
        }

        // 叠加Boid集群力
        Vec3 boidForce = BoidHelper.calculateBoidForce(wingman, owner, WingmanConstructEntity.class, BOID_CONFIG);
        finalMovement = finalMovement.add(boidForce);

        // 限制最大速度
        double maxSpeed = MOVE_SPEED * 2.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        // 速度阻尼
        finalMovement = finalMovement.scale(VELOCITY_DAMPING);

        wingman.setDeltaMovement(finalMovement);
    }

    // ===== 攻击逻辑 =====

    private void executeAttack(Entity wingman, LivingEntity owner, LivingEntity target) {
        if (this.level().isClientSide) return;
        if (this.attackCooldown > 0) return;

        double distance = wingman.distanceTo(target);
        float attackRange = Config.getWingmanAttackRange().floatValue();
        if (distance > attackRange) return;

        fireExplosiveProjectiles(this, owner, target);

        float attackInterval = (float) Config.getWingmanAttackInterval();
        int cooldown = (int) (attackInterval * 20.0f / this.attackSpeedMultiplier);
        this.attackCooldown = Math.max(1, cooldown);
    }

    /**
     * 发射多枚爆破弹
     */
    private void fireExplosiveProjectiles(Entity wingman, LivingEntity owner, LivingEntity target) {
        if (wingman.level().isClientSide) return;

        Vec3 wingmanPos = wingman.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);

        int projectileCount = Config.getWingmanExplosiveCount();
        float damage = (float) this.baseAttackDamage;

        for (int i = 0; i < projectileCount; i++) {
            Vec3 direction = targetPos.subtract(wingmanPos).normalize();

            // 添加少量散布
            double spread = 0.08;
            direction = direction.add(
                (this.random.nextDouble() - 0.5) * spread,
                (this.random.nextDouble() - 0.5) * spread * 0.5,
                (this.random.nextDouble() - 0.5) * spread
            ).normalize();

            ExplosiveProjectile projectile = new ExplosiveProjectile(
                wingman.level(), (LivingEntity) wingman, damage
            );
            projectile.setPos(wingmanPos.x, wingmanPos.y + 0.4, wingmanPos.z);
            projectile.shoot(direction.x, direction.y, direction.z, 1.3f, 0.0f);
            wingman.level().addFreshEntity(projectile);
        }
    }

    // ===== 属性和动画 =====

    public static AttributeSupplier.Builder createAttributes() {
        // 注册时使用默认值，实际值由applyAttributeModifiers()从Config覆盖
        return PathfinderMob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.FOLLOW_RANGE, 16.0)
            .add(Attributes.ATTACK_DAMAGE, 0.5);
    }

    // ===== 抽象方法实现 =====

    @Override
    protected String getConstructTypeId() {
        return WingmanConstructTypes.WINGMAN;
    }

    @Override
    protected ConstructData createConstructDataForRegistration(ServerPlayer ownerPlayer) {
        return new WingmanConstructData(
            WingmanConstructTypes.WINGMAN,
            this.getUUID(),
            this.getBaseMaxHealth()
        );
    }

    @Override
    protected void applyConstructAttributes(UUID playerUUID, Map<String, Double> attributes) {
        ConstructAttributeApplier.applyAttributesToWingman(playerUUID, this, attributes);
    }
}
