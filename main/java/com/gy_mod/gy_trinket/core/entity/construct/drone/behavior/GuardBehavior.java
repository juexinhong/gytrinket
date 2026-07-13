package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructManager;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
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
    private static final float ARC_LENGTH_PER_DRONE = (float) (Math.PI * 0.5 * 0.65);
    private static final float THREAT_SEARCH_RANGE = 30.0f;
    private static final float REPEL_SPEED = 0.05f;
    private static final double HORIZONTAL_SPEED = 0.7;
    private static final double VERTICAL_SPEED_NORMAL = 0.04;
    private static final double VERTICAL_SPEED_FAST = 0.7;
    private static final double FAST_VERTICAL_THRESHOLD = 1.5;
    private static final double SPEED_BOOST_DISTANCE = 3.0;
    private static final double SPEED_BOOST_PER_BLOCK = 0.2;

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
        droneEntity.setDeltaMovement(motionX, motionY, motionZ);

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
            double angle = Math.atan2(playerToThreat.z, playerToThreat.x);
            playerTargetAngles.put(ownerUUID, angle);
            return angle;
        }

        return playerTargetAngles.getOrDefault(ownerUUID, 0.0);
    }

    private Optional<Vec3> findNearestThreat(DroneConstructEntity drone, LivingEntity owner) {
        if (!(owner instanceof Player player)) return Optional.empty();

        Vec3 ownerPos = owner.position();
        double nearestDistance = Double.MAX_VALUE;
        Vec3 nearestThreatPos = null;

        AABB searchArea = new AABB(
                owner.getX() - THREAT_SEARCH_RANGE, owner.getY() - THREAT_SEARCH_RANGE, owner.getZ() - THREAT_SEARCH_RANGE,
                owner.getX() + THREAT_SEARCH_RANGE, owner.getY() + THREAT_SEARCH_RANGE, owner.getZ() + THREAT_SEARCH_RANGE
        );

        List<Entity> threats = drone.level().getEntitiesOfClass(Entity.class, searchArea,
                entity -> HostileTargetManager.shouldAttackPlayer(entity, player));

        for (Entity threat : threats) {
            if (threat == drone || threat == owner) continue;

            if (threat instanceof Projectile proj) {
                if (isFriendlyProjectile(proj, drone, player)) continue;

                if (proj instanceof AbstractArrow arrow) {
                    if (isArrowInGround(arrow)) continue;
                }
            }

            double dist = threat.distanceToSqr(ownerPos);
            if (dist < nearestDistance) {
                nearestDistance = dist;
                nearestThreatPos = new Vec3(threat.getX(), 0, threat.getZ());
            }
        }

        return Optional.ofNullable(nearestThreatPos);
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
        Level level = drone.level();
        Vec3 dronePos = drone.position();
        float searchRange = getConfigAttackRange();

        AABB searchBox = new AABB(
                dronePos.x - searchRange, dronePos.y - searchRange, dronePos.z - searchRange,
                dronePos.x + searchRange, dronePos.y + searchRange, dronePos.z + searchRange
        );

        Player player = owner instanceof Player ? (Player) owner : null;

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == owner || entity == drone) return false;
                    if (!entity.isAlive()) return false;
                    if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
                    return HostileTargetManager.shouldAttackPlayer(entity, player);
                });

        return entities.stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(dronePos)))
                .collect(Collectors.toList());
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
