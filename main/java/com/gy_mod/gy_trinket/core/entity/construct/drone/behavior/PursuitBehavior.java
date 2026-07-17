package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.ConstructGroupCache;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

/**
 * 追击阵列行为处理器
 * <p>
 * 追击阵列的无人机具有以下特性：
 * - 20格索敌范围和20格攻击范围
 * - 优先攻击距离玩家6格范围内的敌人（优先攻击目标）
 * - 有目标时：根据距离执行追击/接近/悬停/远离，高度保持在目标身高的50%-70%
 * - 无目标时：跟随玩家并保持在玩家头上1格的位置
 * - 无人机之间使用Boid鸟群算法保持适当间距（不会太挤也不会太松）
 * <p>
 * 性能优化：Boid邻居数据和索敌结果通过 ConstructGroupCache 共享，
 * 同玩家的无人机只做一次 getEntitiesOfClass 查询。
 */
public class PursuitBehavior implements IDroneBehavior {
    /** 索敌范围 */
    private static final float SEARCH_RANGE = 20.0f;
    /** 攻击范围 */
    private static float getConfigAttackRange() { return Config.PURSUIT_ATTACK_RANGE.get().floatValue(); }
    private static float getConfigAttackInterval() { return Config.PURSUIT_ATTACK_INTERVAL.get().floatValue(); }
    /** 基础移动速度（格/刻），7格/秒 = 0.35格/刻 */
    private static final float MOVE_SPEED = 0.3f;
    /** 远离速度（用于近距离撤离），3格/秒 = 0.15格/刻 */
    private static final float LEAVE_SPEED = 0.1f;
    /** 待机跟随触发距离 */
    private static final float STANDBY_RANGE = 8.0f;
    /** 优先攻击目标检测范围（玩家周围） */
    private static final float PRIORITY_RANGE = 6.0f;
    /** 高度调整速度（格/刻） */
    private static final float HEIGHT_ADJUST_SPEED = 0.1f;
    private static final long PRIORITY_TARGET_DURATION = 60L;
    /** 目标记忆持续时间（tick），3秒=60tick */
    private static final long TARGET_MEMORY_DURATION = 60L;

    // Boid参数
    private static final double BOID_COMFORT_RANGE = 2.0;        // 舒适区：此距离内不产生力，避免振荡
    private static final double BOID_SEPARATION_RANGE = 3.0;    // 分离检测外边界
    private static final double BOID_SEPARATION_STRENGTH = 0.06;
    private static final double BOID_COHESION_RANGE = 8.0;      // 聚合检测外边界
    private static final double BOID_COHESION_STRENGTH = 0.015;
    private static final double BOID_ALIGNMENT_RANGE = 5.0;
    private static final double BOID_ALIGNMENT_STRENGTH = 0.02;
    private static final double VELOCITY_DAMPING = 0.8;          // 速度阻尼：每tick保留80%速度，防止过冲

    /** 存储每个玩家的优先攻击目标信息 */
    private final Map<UUID, PriorityTargetInfo> priorityTargetMap = new HashMap<>();

    /** 存储每个无人机的目标记忆，避免索敌边缘抖动 */
    private final Map<UUID, TargetMemory> targetMemoryMap = new HashMap<>();

    /**
     * 获取无人机的记忆目标（用于转向逻辑）
     * 即使目标离开攻击范围，记忆仍有效时返回该目标
     */
    @Nullable
    public LivingEntity getMemoryTarget(Entity drone) {
        TargetMemory memory = targetMemoryMap.get(drone.getUUID());
        if (memory != null && memory.endTick > drone.level().getGameTime() && memory.target.isAlive()) {
            return memory.target;
        }
        return null;
    }

    /**
     * 目标记忆内部类
     * 当目标离开索敌范围后，保留3秒记忆，避免追击/待机频繁切换
     */
    private static class TargetMemory {
        LivingEntity target;
        long endTick;

        TargetMemory(LivingEntity target, long endTick) {
            this.target = target;
            this.endTick = endTick;
        }
    }

    /**
     * 优先攻击目标信息内部类
     */
    private static class PriorityTargetInfo {
        /** 目标实体 */
        LivingEntity target;
        /** 目标有效期结束时刻 */
        long endTick;

        PriorityTargetInfo(LivingEntity target, long endTick) {
            this.target = target;
            this.endTick = endTick;
        }
    }

    /**
     * 计算Boid集群力，使用 ConstructGroupCache 共享邻居数据。
     * 同玩家的所有追击无人机共享一次 getEntitiesOfClass 查询结果。
     */
    private Vec3 calculateBoidFlockForce(Entity drone, LivingEntity owner) {
        ConstructGroupCache cache = ConstructGroupCache.getInstance();
        ConstructGroupCache.NeighborData neighborData = cache.getNeighborData(
            owner.getUUID(), DroneConstructTypes.DRONE, drone.getUUID(), drone.level(), owner.position());

        Vec3 dronePos = drone.position();
        Vec3 droneVelocity = drone.getDeltaMovement();

        Vec3 separation = BoidCalculator.separation(dronePos, neighborData.positions, BOID_COMFORT_RANGE, BOID_SEPARATION_RANGE, BOID_SEPARATION_STRENGTH);
        Vec3 cohesion = BoidCalculator.cohesion(dronePos, neighborData.positions, BOID_COMFORT_RANGE, BOID_COHESION_RANGE, BOID_COHESION_STRENGTH);
        Vec3 alignment = BoidCalculator.alignment(droneVelocity, neighborData.velocities, BOID_ALIGNMENT_RANGE, BOID_ALIGNMENT_STRENGTH);

        return separation.add(cohesion).add(alignment);
    }

    private static List<List<Vec3>> clusterPositions(List<Vec3> positions, double threshold) {
        List<List<Vec3>> clusters = new ArrayList<>();
        int[] parent = new int[positions.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < positions.size(); i++) {
            for (int j = i + 1; j < positions.size(); j++) {
                double dist = positions.get(i).distanceTo(positions.get(j));
                if (dist <= threshold) {
                    int rootI = findRoot(parent, i);
                    int rootJ = findRoot(parent, j);
                    if (rootI != rootJ) {
                        parent[rootI] = rootJ;
                    }
                }
            }
        }
        Map<Integer, List<Vec3>> clusterMap = new HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            int root = findRoot(parent, i);
            clusterMap.computeIfAbsent(root, k -> new ArrayList<>()).add(positions.get(i));
        }
        clusters.addAll(clusterMap.values());
        return clusters;
    }

    private static int findRoot(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of(DroneArrayType.Tags.ARRAY, DroneArrayType.Tags.PURSUIT);
    }

    /**
     * 更新无人机位置
     * 根据是否有攻击目标选择追击模式或待机模式
     */
    @Override
    public Vec3 updatePosition(Entity drone, LivingEntity owner, float orbitAngle, float deltaTime) {
        Level level = drone.level();
        if (level.isClientSide) {
            return drone.position();
        }

        LivingEntity target = findTarget(drone, owner);
        LivingEntity priorityTarget = findPriorityTarget(drone, owner);

        if (target != null) {
            return pursuitMovement(drone, owner, target, priorityTarget, deltaTime);
        } else {
            return standbyMovement(drone, owner, deltaTime);
        }
    }

    /**
     * 使用共享缓存索敌。
     * 同玩家的所有无人机共享一次以玩家为中心的索敌查询。
     */
    private LivingEntity findTarget(Entity drone, LivingEntity owner) {
        long currentTick = drone.level().getGameTime();
        UUID droneUUID = drone.getUUID();

        // 使用共享缓存获取最近目标
        LivingEntity newTarget = ConstructGroupCache.getInstance().findNearestTarget(
            owner.getUUID(), owner, drone.position(), SEARCH_RANGE);

        if (newTarget != null) {
            // 更新或创建目标记忆
            TargetMemory existing = targetMemoryMap.get(droneUUID);
            if (existing != null && existing.target == newTarget) {
                existing.endTick = currentTick + TARGET_MEMORY_DURATION;
            } else {
                targetMemoryMap.put(droneUUID, new TargetMemory(newTarget, currentTick + TARGET_MEMORY_DURATION));
            }
            return newTarget;
        }

        // 索敌范围内无目标，检查记忆
        TargetMemory memory = targetMemoryMap.get(droneUUID);
        if (memory != null) {
            if (memory.endTick > currentTick && memory.target.isAlive()) {
                return memory.target;
            } else {
                targetMemoryMap.remove(droneUUID);
            }
        }

        return null;
    }

    /**
     * 查找玩家附近的优先攻击目标
     * 优先攻击目标有效期为3秒，重复检测到相同目标时刷新有效期
     * 切换目标时会移除旧目标的记录
     */
    public LivingEntity findPriorityTarget(Entity drone, LivingEntity owner) {
        long currentTick = drone.level().getGameTime();

        // 使用共享缓存获取玩家附近的敌人
        List<LivingEntity> nearbyTargets = ConstructGroupCache.getInstance().findTargetsInRange(
            owner.getUUID(), owner, owner.position(), PRIORITY_RANGE);

        UUID ownerUUID = owner.getUUID();

        if (nearbyTargets.isEmpty()) {
            priorityTargetMap.remove(ownerUUID);
            return null;
        }

        LivingEntity newTarget = nearbyTargets.get(0); // 已按距离排序

        PriorityTargetInfo existingInfo = priorityTargetMap.get(ownerUUID);

        if (existingInfo != null) {
            if (existingInfo.target == newTarget) {
                existingInfo.endTick = currentTick + PRIORITY_TARGET_DURATION;
            } else {
                priorityTargetMap.remove(ownerUUID);
                priorityTargetMap.put(ownerUUID, new PriorityTargetInfo(newTarget, currentTick + PRIORITY_TARGET_DURATION));
            }
        } else {
            priorityTargetMap.put(ownerUUID, new PriorityTargetInfo(newTarget, currentTick + PRIORITY_TARGET_DURATION));
        }

        PriorityTargetInfo info = priorityTargetMap.get(ownerUUID);
        if (info != null && info.endTick > currentTick && info.target.isAlive()) {
            return info.target;
        } else {
            priorityTargetMap.remove(ownerUUID);
            return null;
        }
    }

    /**
     * 检查是否存在有效的优先攻击目标
     */
    public boolean hasPriorityTarget(Entity drone, LivingEntity owner) {
        UUID ownerUUID = owner.getUUID();
        PriorityTargetInfo info = priorityTargetMap.get(ownerUUID);

        if (info == null) {
            return false;
        }

        long currentTick = drone.level().getGameTime();
        if (info.endTick <= currentTick || !info.target.isAlive()) {
            priorityTargetMap.remove(ownerUUID);
            return false;
        }

        return true;
    }

    /**
     * 追击模式移动逻辑
     */
    private Vec3 pursuitMovement(Entity drone, LivingEntity owner, LivingEntity target, LivingEntity priorityTarget, float deltaTime) {
        LivingEntity actualTarget = (priorityTarget != null) ? priorityTarget : target;

        Vec3 dronePos = drone.position();
        Vec3 targetPos = actualTarget.position();

        double horizontalDist = Math.sqrt(
            Math.pow(dronePos.x - targetPos.x, 2) +
            Math.pow(dronePos.z - targetPos.z, 2)
        );

        double speed = 0;
        Vec3 direction = Vec3.ZERO;
        float yaw = drone.getYRot() * (float) Math.PI / 180.0f;

        if (horizontalDist > 6.0) {
            float excessDistance = (float) (horizontalDist - 6.0);
            float speedMultiplier = 1.0f + excessDistance * 0.10f;
            speed = MOVE_SPEED * speedMultiplier;
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
        } else if (horizontalDist > 5.0) {
            speed = LEAVE_SPEED;
            Vec3 toTarget = targetPos.subtract(dronePos).normalize();
            direction = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        } else if (horizontalDist > 3.0) {
            speed = 0;
            direction = Vec3.ZERO;
        } else {
            speed = LEAVE_SPEED;
            Vec3 awayDirection = dronePos.subtract(targetPos).normalize();
            direction = new Vec3(awayDirection.x, 0, awayDirection.z).normalize();
        }

        double targetHeightMin = targetPos.y + actualTarget.getBbHeight() * 0.5;
        double targetHeightMax = targetPos.y + actualTarget.getBbHeight() * 0.7;

        Vec3 verticalDirection = Vec3.ZERO;

        if (dronePos.y >= targetHeightMin && dronePos.y <= targetHeightMax) {
            // 在目标范围内，不调整高度
        } else if (dronePos.y > targetHeightMax) {
            double heightDiff = dronePos.y - targetHeightMax;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, -HEIGHT_ADJUST_SPEED * speedFactor, 0);
        } else if (dronePos.y < targetHeightMin) {
            double heightDiff = targetHeightMin - dronePos.y;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, HEIGHT_ADJUST_SPEED * speedFactor, 0);
        }

        Vec3 finalMovement = direction.scale(speed);
        if (verticalDirection != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDirection);
        }

        // 叠加Boid集群力（使用共享缓存）
        Vec3 boidForce = calculateBoidFlockForce(drone, owner);
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = MOVE_SPEED * 2.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);

        drone.setDeltaMovement(finalMovement);

        return dronePos.add(finalMovement);
    }

    /**
     * 待机模式移动逻辑
     */
    private Vec3 standbyMovement(Entity drone, LivingEntity owner, float deltaTime) {
        Vec3 dronePos = drone.position();
        Vec3 ownerPos = owner.position();

        Vec3 standbyTarget = ownerPos.add(0, 3.0, 0);

        double horizontalDist = Math.sqrt(
            Math.pow(dronePos.x - ownerPos.x, 2) +
            Math.pow(dronePos.z - ownerPos.z, 2)
        );

        if (horizontalDist > 20.0) {
            drone.teleportTo(ownerPos.x, ownerPos.y + 3.0, ownerPos.z);
            return drone.position();
        }

        Vec3 toOwner = new Vec3(ownerPos.x - dronePos.x, 0, ownerPos.z - dronePos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 0.001 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 verticalDir = Vec3.ZERO;
        double heightDiff = standbyTarget.y - dronePos.y;
        if (Math.abs(heightDiff) > 0.3) {
            double heightSpeed = HEIGHT_ADJUST_SPEED * (1.0 + Math.abs(heightDiff) * 0.5);
            verticalDir = new Vec3(0, Math.signum(heightDiff) * heightSpeed, 0);
        }

        Vec3 finalMovement = Vec3.ZERO;

        if (horizontalDist > STANDBY_RANGE) {
            // 使用共享Boid缓存获取无人机位置数据来做集群中心计算
            ConstructGroupCache cache = ConstructGroupCache.getInstance();
            ConstructGroupCache.CachedBoidSnapshot snapshot = cache.getBoidSnapshot(
                owner.getUUID(), DroneConstructTypes.DRONE, drone.level(), owner.position());

            List<Vec3> movingPositions = new ArrayList<>();
            movingPositions.add(dronePos);
            for (Vec3 otherPos : snapshot.positions) {
                double otherDist = Math.sqrt(
                    Math.pow(otherPos.x - ownerPos.x, 2) +
                    Math.pow(otherPos.z - ownerPos.z, 2)
                );
                if (otherDist > STANDBY_RANGE) {
                    movingPositions.add(otherPos);
                }
            }

            List<List<Vec3>> clusters = clusterPositions(movingPositions, 5.0);

            List<Vec3> myCluster = null;
            for (List<Vec3> cluster : clusters) {
                if (cluster.contains(dronePos)) {
                    myCluster = cluster;
                    break;
                }
            }
            if (myCluster == null) {
                myCluster = Collections.singletonList(dronePos);
            }

            Vec3 groupCenter = Vec3.ZERO;
            for (Vec3 pos : myCluster) {
                groupCenter = groupCenter.add(pos);
            }
            groupCenter = groupCenter.scale(1.0 / myCluster.size());

            Vec3 centerToOwner = new Vec3(ownerPos.x - groupCenter.x, 0, ownerPos.z - groupCenter.z);
            Vec3 moveDir = centerToOwner.lengthSqr() > 0.001 ? centerToOwner.normalize() : horizontalDir;

            double speedBoost = 1.0 + (horizontalDist - STANDBY_RANGE) * 0.2;
            finalMovement = moveDir.scale(MOVE_SPEED * speedBoost);
        } else if (horizontalDist > 3.0) {
            finalMovement = horizontalDir.scale(LEAVE_SPEED);
        }

        if (verticalDir != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDir);
        }

        // 叠加Boid集群力（使用共享缓存）
        Vec3 boidForce = calculateBoidFlockForce(drone, owner);
        finalMovement = finalMovement.add(boidForce);

        double maxSpeed = MOVE_SPEED * 2.0;
        double currentSpeed = finalMovement.length();
        if (currentSpeed > maxSpeed) {
            finalMovement = finalMovement.normalize().scale(maxSpeed);
        }

        finalMovement = finalMovement.scale(VELOCITY_DAMPING);

        drone.setDeltaMovement(finalMovement);

        return dronePos.add(finalMovement);
    }

    @Override
    public List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range) {
        // 使用共享缓存索敌，从玩家中心的缓存结果中过滤当前位置附近的敌人
        return ConstructGroupCache.getInstance().findTargetsInRange(
            owner.getUUID(), owner, drone.position(), range);
    }

    @Override
    public void executeAttack(Entity drone, LivingEntity owner, LivingEntity target, boolean canAttack) {
        if (!canAttack) {
            return;
        }

        Level level = drone.level();
        if (level.isClientSide) {
            return;
        }

        if (drone instanceof DroneConstructEntity droneEntity && droneEntity.getAttackCooldown() > 0) {
            return;
        }

        double distance = drone.distanceTo(target);
        if (distance > getConfigAttackRange()) {
            return;
        }

        fireBullet(drone, owner, target);
    }

    private void fireBullet(Entity drone, LivingEntity owner, LivingEntity target) {
        if (drone.level().isClientSide) {
            return;
        }

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
