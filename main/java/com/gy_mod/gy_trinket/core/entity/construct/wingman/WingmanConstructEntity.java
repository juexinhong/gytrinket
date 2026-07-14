package com.gy_mod.gy_trinket.core.entity.construct.wingman;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.AbstractConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructAttributeApplier;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructData;
import com.gy_mod.gy_trinket.core.entity.construct.drone.ModEntities;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.BoidConfig;
import com.gy_mod.gy_trinket.core.entity.construct.drone.behavior.BoidHelper;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
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
    private static final float SEARCH_RANGE = 20.0f;
    private static final float MOVE_SPEED = 0.45f;
    private static final float LEAVE_SPEED = 0.2f;
    private static final float HEIGHT_ADJUST_SPEED = 0.2f;
    private static final float STANDBY_HEIGHT = 4.0f;
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

    private LivingEntity findTarget(LivingEntity owner) {
        Player player = owner instanceof Player p ? p : null;
        return findTarget(owner, SEARCH_RANGE, entity -> {
            if (entity == owner || entity == this) return false;
            if (!entity.isAlive()) return false;
            if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
            if (isOwnConstruct(entity, owner.getUUID())) return false;
            if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) return false;
            return entity.distanceTo(owner) <= PLAYER_MAX_TARGET_RANGE;
        });
    }

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
            float excessDistance = (float) (horizontalDist - 8.0);
            float speedMultiplier = 1.0f + excessDistance * 0.10f;
            speed = MOVE_SPEED * speedMultiplier;
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
        } else if (horizontalDist > 7.0) {
            speed = LEAVE_SPEED;
            Vec3 toTarget = targetPos.subtract(pos).normalize();
            direction = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        } else if (horizontalDist > 6.0) {
            speed = 0;
            direction = Vec3.ZERO;
        } else {
            speed = LEAVE_SPEED;
            Vec3 awayDirection = pos.subtract(targetPos).normalize();
            direction = new Vec3(awayDirection.x, 0, awayDirection.z).normalize();
        }

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

        Vec3 finalMovement = direction.scale(speed);
        if (verticalDirection != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDirection);
        }

        Vec3 boidForce = BoidHelper.calculateBoidForce(wingman, owner, WingmanConstructEntity.class, BOID_CONFIG);
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = MOVE_SPEED * 2.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

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

        Vec3 boidForce = BoidHelper.calculateBoidForce(wingman, owner, WingmanConstructEntity.class, BOID_CONFIG);
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = MOVE_SPEED * 2.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

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

    private void fireExplosiveProjectiles(Entity wingman, LivingEntity owner, LivingEntity target) {
        if (wingman.level().isClientSide) return;

        Vec3 wingmanPos = wingman.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);

        int projectileCount = Config.getWingmanExplosiveCount();
        float damage = (float) this.baseAttackDamage;

        for (int i = 0; i < projectileCount; i++) {
            Vec3 direction = targetPos.subtract(wingmanPos).normalize();

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
