package com.gytrinket.gytrinket.core.entity.construct.drone.behavior;

import com.gytrinket.gytrinket.config.Config;
import com.gytrinket.gytrinket.core.entity.construct.ConstructGroupCache;
import com.gytrinket.gytrinket.core.entity.construct.ConstructManager;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneArrayType;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneBullet;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructEntity;
import com.gytrinket.gytrinket.core.entity.construct.drone.DroneConstructTypes;
import com.gytrinket.gytrinket.core.entity.construct.HostileTargetManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class GuardBehavior implements IDroneBehavior {

    private static final float ORBIT_RADIUS = 3.0f;
    private static final float DEFENSE_ORBIT_RADIUS = 2.0f;
    private static final float VERTICAL_OFFSET = 0.3f;
    private static final float ARC_LENGTH_PER_DRONE = (float) (Math.PI * 0.26);
    private static final float THREAT_SEARCH_RANGE = 30.0f;
    private static final float REPEL_SPEED = 0.05f;
    private static final double HORIZONTAL_SPEED = 0.7;
    private static final double VERTICAL_SPEED_NORMAL = 0.04;
    private static final double VERTICAL_SPEED_FAST = 0.7;
    private static final double FAST_VERTICAL_THRESHOLD = 1.5;
    private static final double SPEED_BOOST_DISTANCE = 3.0;
    private static final double SPEED_BOOST_PER_BLOCK = 0.2;
    // 阵列整体旋转角速度限制：每tick最大6度
    private static final double MAX_ANGULAR_VELOCITY_PER_TICK = Math.toRadians(6.0);
    // 丢失距离：超出40格自毁
    private static final double LOST_DISTANCE = 40.0;

    private static Field ARROW_IN_GROUND_FIELD;

    static {
        for (String fieldName : new String[]{"f_36704_", "inGround"}) {
            try {
                Field f = AbstractArrow.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                ARROW_IN_GROUND_FIELD = f;
                break;
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    private static boolean isArrowInGround(AbstractArrow arrow) {
        if (ARROW_IN_GROUND_FIELD == null) {
            Vec3 velocity = arrow.getDeltaMovement();
            double speedSquared = velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z;
            return speedSquared < 0.01;
        }
        try {
            return ARROW_IN_GROUND_FIELD.getBoolean(arrow);
        } catch (IllegalAccessException e) {
            Vec3 velocity = arrow.getDeltaMovement();
            double speedSquared = velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z;
            return speedSquared < 0.01;
        }
    }

    private static float getConfigAttackRange() { return Config.GUARD_ATTACK_RANGE.get().floatValue(); }
    private static float getConfigAttackInterval() { return Config.GUARD_ATTACK_INTERVAL.get().floatValue(); }

    private final Map<UUID, Double> playerTargetAngles = new HashMap<>();

    @Override
    public Set<String> getRequiredTags() {
        Set<String> tags = new HashSet<>();
        tags.add(DroneArrayType.Tags.ARRAY);
        tags.add(DroneArrayType.Tags.GUARD);
        return tags;
    }

    @Override
    public Vec3 updatePosition(Entity drone, LivingEntity owner, float orbitAngle, float deltaTime) {
        if (!(drone instanceof DroneConstructEntity droneEntity)) return Vec3.ZERO;
        if (owner == null || !owner.isAlive()) return Vec3.ZERO;

        Map<UUID, Entity> entitiesMap = ConstructManager.getInstance()
                .getActiveConstructEntities(owner.getUUID(), DroneConstructTypes.DRONE);

        List<DroneConstructEntity> allDrones = new ArrayList<>();
        for (Entity entity : entitiesMap.values()) {
            if (entity instanceof DroneConstructEntity d && d.isAlive() && d.isGuardArray()) {
                allDrones.add(d);
            }
        }

        allDrones.sort(Comparator.comparingInt(Entity::getId));

        int totalDrones = allDrones.size();
        int droneIndex = allDrones.indexOf(drone);
        if (droneIndex < 0) droneIndex = 0;

        double targetAngle = computeTargetAngle(droneEntity, owner);

        boolean isDefenseDrone = droneEntity.isDefenseDrone();
        double radius = isDefenseDrone ? DEFENSE_ORBIT_RADIUS : ORBIT_RADIUS;

        double totalArcLength = ARC_LENGTH_PER_DRONE * totalDrones;
        double startAngle = targetAngle - (totalArcLength / 2.0) / radius;
        double droneAngle = startAngle + (droneIndex * ARC_LENGTH_PER_DRONE) / radius;

        double targetX = owner.getX() + Math.cos(droneAngle) * radius;
        double targetZ = owner.getZ() + Math.sin(droneAngle) * radius;
        double targetY = owner.getY() + VERTICAL_OFFSET;

        double dx = targetX - drone.getX();
        double dz = targetZ - drone.getZ();
        double dy = targetY - drone.getY();

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double verticalDist = Math.abs(dy);

        double currentHorizontalSpeed = HORIZONTAL_SPEED;
        if (horizontalDist > SPEED_BOOST_DISTANCE) {
            double extraDistance = horizontalDist - SPEED_BOOST_DISTANCE;
            currentHorizontalSpeed *= (1.0 + extraDistance * SPEED_BOOST_PER_BLOCK);
        }

        double verticalSpeed = verticalDist >= FAST_VERTICAL_THRESHOLD ? VERTICAL_SPEED_FAST : VERTICAL_SPEED_NORMAL;

        double motionX, motionY, motionZ;

        if (horizontalDist <= currentHorizontalSpeed) {
            motionX = dx;
            motionZ = dz;
        } else {
            double horizontalDirX = dx / horizontalDist;
            double horizontalDirZ = dz / horizontalDist;
            motionX = horizontalDirX * currentHorizontalSpeed;
            motionZ = horizontalDirZ * currentHorizontalSpeed;
        }

        if (Math.abs(dy) <= verticalSpeed) {
            motionY = dy;
        } else {
            motionY = Math.signum(dy) * verticalSpeed;
        }

        droneEntity.getNavigation().stop();

        // 检查与玩家距离，超出40格视为丢失，移除无人机
        if (drone.distanceTo(owner) > LOST_DISTANCE) {
            UUID ownerUUID = owner.getUUID();
            String constructId = droneEntity.getConstructTypeId();
            UUID entityUUID = droneEntity.getUUID();
            droneEntity.remove(Entity.RemovalReason.DISCARDED);
            ConstructManager manager = ConstructManager.getInstance();
            manager.unregisterConstructEntity(ownerUUID, constructId, entityUUID);
            manager.removeConstruct(ownerUUID, entityUUID);
            return Vec3.ZERO;
        }

        if (isDefenseDrone) {
            // 防御无人机：直接传送到目标位置，无距离限制
            droneEntity.setPos(targetX, targetY, targetZ);
            droneEntity.setDeltaMovement(0, 0, 0);
        } else {
            droneEntity.setDeltaMovement(motionX, motionY, motionZ);
        }

        repelEnemiesOnCollision(droneEntity, owner);

        return new Vec3(targetX, targetY, targetZ);
    }

    private double computeTargetAngle(DroneConstructEntity drone, LivingEntity owner) {
        UUID ownerUUID = owner.getUUID();

        Optional<Vec3> nearestThreat = findNearestThreat(drone, owner);

        if (nearestThreat.isPresent()) {
            Vec3 threatPos = nearestThreat.get();
            Vec3 ownerPos2D = new Vec3(owner.getX(), 0, owner.getZ());
            Vec3 playerToThreat = threatPos.subtract(ownerPos2D).normalize();
            double desiredAngle = Math.atan2(playerToThreat.z, playerToThreat.x);

            double currentAngle = playerTargetAngles.getOrDefault(ownerUUID, desiredAngle);

            // 计算角度差，归一化到[-PI, PI]
            double diff = desiredAngle - currentAngle;
            while (diff > Math.PI) diff -= 2 * Math.PI;
            while (diff < -Math.PI) diff += 2 * Math.PI;

            // 限制角速度：每tick最大6度
            if (Math.abs(diff) > MAX_ANGULAR_VELOCITY_PER_TICK) {
                diff = Math.signum(diff) * MAX_ANGULAR_VELOCITY_PER_TICK;
            }

            double newAngle = currentAngle + diff;
            playerTargetAngles.put(ownerUUID, newAngle);
            return newAngle;
        }

        return playerTargetAngles.getOrDefault(ownerUUID, 0.0);
    }

    private Optional<Vec3> findNearestThreat(DroneConstructEntity drone, LivingEntity owner) {
        if (!(owner instanceof Player player)) return Optional.empty();

        LivingEntity nearest = ConstructGroupCache.getInstance().findNearestTarget(
            owner.getUUID(), owner, owner.position(), THREAT_SEARCH_RANGE);

        if (nearest != null) {
            return Optional.of(new Vec3(nearest.getX(), 0, nearest.getZ()));
        }
        return Optional.empty();
    }

    private boolean isFriendlyProjectile(Projectile proj, DroneConstructEntity drone, Player player) {
        Entity projOwner = proj.getOwner();
        if (projOwner instanceof Player) return true;
        if (projOwner instanceof DroneConstructEntity droneShooter
                && droneShooter.getOwnerUUID() != null
                && droneShooter.getOwnerUUID().equals(drone.getOwnerUUID())) {
            return true;
        }
        if (projOwner == drone) return true;

        return false;
    }

    private void repelEnemiesOnCollision(DroneConstructEntity drone, LivingEntity owner) {
        if (!(owner instanceof Player player)) return;

        AABB droneAABB = drone.getBoundingBox();
        List<LivingEntity> entities = drone.level().getEntitiesOfClass(LivingEntity.class, droneAABB.inflate(0.0));

        for (LivingEntity entity : entities) {
            if (!HostileTargetManager.shouldAttackPlayer(entity, player)) continue;
            if (entity == drone || entity == owner) continue;

            Vec3 awayDir = entity.position().subtract(owner.position()).normalize();
            entity.setDeltaMovement(entity.getDeltaMovement().add(awayDir.scale(REPEL_SPEED)));
        }
    }

    @Override
    public List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range) {
        float searchRange = getConfigAttackRange();
        return ConstructGroupCache.getInstance().findTargetsInRange(
            owner.getUUID(), owner, drone.position(), searchRange);
    }

    @Override
    public void executeAttack(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
        if (!canAttack) return;

        Level level = drone.level();
        if (level.isClientSide) return;

        if (drone instanceof DroneConstructEntity droneEntity && droneEntity.getAttackCooldown() > 0) return;

        double distance = drone.distanceTo(target);
        if (distance > getConfigAttackRange()) return;

        boolean hasLineOfSight = drone instanceof LivingEntity livingDrone && livingDrone.hasLineOfSight(target);
        if (!hasLineOfSight) return;

        fireBullet(drone, owner, target);
    }

    private void fireBullet(Entity drone, LivingEntity owner, LivingEntity target) {
        if (drone.level().isClientSide) return;

        Vec3 dronePos = drone.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);
        Vec3 direction = targetPos.subtract(dronePos).normalize();

        float damage = DroneBullet.getBaseDamage();
        float cooldown = getConfigAttackInterval() * 20.0f;

        if (drone instanceof DroneConstructEntity droneEntity) {
            damage = (float) droneEntity.getAttributeValue(Attributes.ATTACK_DAMAGE);
            cooldown /= (float) droneEntity.getAttackSpeedMultiplier();
            droneEntity.setAttackCooldown((int) cooldown);

            DroneBullet bullet = new DroneBullet(drone.level(), droneEntity, damage);
            bullet.setPos(dronePos.x, dronePos.y + 0.4, dronePos.z);
            bullet.shoot(direction.x, direction.y, direction.z, 1.3f, 0.0f);
            drone.level().addFreshEntity(bullet);
        }
    }

    @Override
    public float getAttackInterval() {
        return getConfigAttackInterval();
    }

    @Override
    public float getAttackRange() {
        return getConfigAttackRange();
    }

    @Override
    public boolean isCombatMode() {
        return false;
    }
}
