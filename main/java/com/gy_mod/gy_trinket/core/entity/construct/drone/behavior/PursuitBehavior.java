package com.gy_mod.gy_trinket.core.entity.construct.drone.behavior;

import com.gy_mod.gy_trinket.Config;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneArrayType;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneBullet;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
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

/**
 * 追击阵列行为处理器
 * <p>
 * 追击阵列的无人机具有以下特性：
 * - 20格索敌范围和20格攻击范围
 * - 7格/秒的基础移速
 * - 优先攻击距离玩家6格范围内的敌人（优先攻击目标）
 * - 尝试将自身位置维持在目标头顶3-5格范围内
 * - 高度保持在目标身高的50%-70%
 * - 无目标时保持在玩家10格范围内待机
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

    /** 存储每个玩家的优先攻击目标信息 */
    private final Map<UUID, PriorityTargetInfo> priorityTargetMap = new HashMap<>();

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

    private static final double DRONE_REPEL_RANGE = 1.5;
    private static final double DRONE_SEPARATION_STRENGTH = 0.05;

    private static List<List<Vec3>> clusterPositions(List<Vec3> positions, double threshold) {
        List<List<Vec3>> clusters = new java.util.ArrayList<>();
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
        java.util.Map<Integer, List<Vec3>> clusterMap = new java.util.HashMap<>();
        for (int i = 0; i < positions.size(); i++) {
            int root = findRoot(parent, i);
            clusterMap.computeIfAbsent(root, k -> new java.util.ArrayList<>()).add(positions.get(i));
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

    private void applyDroneSeparation(Entity drone, Vec3 dronePos) {
        Level level = drone.level();
        List<DroneConstructEntity> nearbyDrones = level.getEntitiesOfClass(
            DroneConstructEntity.class,
            new AABB(dronePos.x - DRONE_REPEL_RANGE, dronePos.y - DRONE_REPEL_RANGE, dronePos.z - DRONE_REPEL_RANGE,
                     dronePos.x + DRONE_REPEL_RANGE, dronePos.y + DRONE_REPEL_RANGE, dronePos.z + DRONE_REPEL_RANGE),
            other -> other != drone && other.isAlive()
        );

        List<DroneConstructEntity> closeDrones = new java.util.ArrayList<>();
        for (DroneConstructEntity other : nearbyDrones) {
            double dist = dronePos.distanceTo(other.position());
            if (dist < DRONE_REPEL_RANGE) {
                closeDrones.add(other);
            }
        }

        if (closeDrones.isEmpty()) {
            return;
        }

        Vec3 center = dronePos;
        for (DroneConstructEntity other : closeDrones) {
            center = center.add(other.position());
        }
        center = center.scale(1.0 / (closeDrones.size() + 1));

        Vec3 awayFromCenter = dronePos.subtract(center);
        Vec3 separationDir;
        if (awayFromCenter.lengthSqr() > 0.001) {
            separationDir = awayFromCenter.normalize();
        } else {
            separationDir = new Vec3(level.random.nextDouble() - 0.5, 0, level.random.nextDouble() - 0.5);
            if (separationDir.lengthSqr() < 0.001) {
                return;
            }
            separationDir = separationDir.normalize();
        }

        double closestDist = Double.MAX_VALUE;
        for (DroneConstructEntity other : closeDrones) {
            double dist = dronePos.distanceTo(other.position());
            if (dist < closestDist) {
                closestDist = dist;
            }
        }

        double factor = Math.max(0, 1.0 - closestDist / DRONE_REPEL_RANGE);
        double offset = DRONE_SEPARATION_STRENGTH * factor;

        drone.setPos(dronePos.x + separationDir.x * offset,
                     dronePos.y,
                     dronePos.z + separationDir.z * offset);
    }

    @Override
    public Set<String> getRequiredTags() {
        return Set.of(DroneArrayType.Tags.ARRAY, DroneArrayType.Tags.PURSUIT);
    }

    /**
     * 更新无人机位置
     * 根据是否有攻击目标选择追击模式或待机模式
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @param orbitAngle 环绕角度（追击阵列不使用）
     * @param deltaTime 时间增量
     * @return 新位置
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
     * 在无人机周围搜索最近的敌人目标
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @return 最近的敌人目标，若无则返回null
     */
    private LivingEntity findTarget(Entity drone, LivingEntity owner) {
        Level level = drone.level();
        Vec3 dronePos = drone.position();

        AABB searchBox = new AABB(
            dronePos.x - SEARCH_RANGE,
            dronePos.y - SEARCH_RANGE,
            dronePos.z - SEARCH_RANGE,
            dronePos.x + SEARCH_RANGE,
            dronePos.y + SEARCH_RANGE,
            dronePos.z + SEARCH_RANGE
        );

        Player player = owner instanceof Player ? (Player) owner : null;

        List<LivingEntity> allTargets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == owner || entity == drone) return false;
                    if (!entity.isAlive()) return false;
                    if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
                    if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
                    return HostileTargetManager.shouldAttackPlayer(entity, player);
                });

        if (allTargets.isEmpty()) {
            return null;
        }

        return allTargets.stream()
                .min(Comparator.comparingDouble(target -> drone.position().distanceTo(target.position())))
                .orElse(null);
    }

    /**
     * 查找玩家附近的优先攻击目标
     * 优先攻击目标有效期为3秒，重复检测到相同目标时刷新有效期
     * 切换目标时会移除旧目标的记录
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @return 优先攻击目标，若无则返回null
     */
    public LivingEntity findPriorityTarget(Entity drone, LivingEntity owner) {
        Level level = drone.level();
        Vec3 ownerPos = owner.position();
        long currentTick = level.getGameTime();

        AABB searchBox = new AABB(
            ownerPos.x - PRIORITY_RANGE,
            ownerPos.y - PRIORITY_RANGE,
            ownerPos.z - PRIORITY_RANGE,
            ownerPos.x + PRIORITY_RANGE,
            ownerPos.y + PRIORITY_RANGE,
            ownerPos.z + PRIORITY_RANGE
        );

        Player player = owner instanceof Player ? (Player) owner : null;

        List<LivingEntity> nearbyTargets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == owner || entity == drone) return false;
                    if (!entity.isAlive()) return false;
                    if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
                    if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
                    return HostileTargetManager.shouldAttackPlayer(entity, player);
                });

        UUID ownerUUID = owner.getUUID();

        if (nearbyTargets.isEmpty()) {
            priorityTargetMap.remove(ownerUUID);
            return null;
        }

        LivingEntity newTarget = nearbyTargets.stream()
                .min(Comparator.comparingDouble(target -> ownerPos.distanceTo(target.position())))
                .orElse(null);

        if (newTarget != null) {
            PriorityTargetInfo existingInfo = priorityTargetMap.get(ownerUUID);

            if (existingInfo != null) {
                if (existingInfo.target == newTarget) {
                    // 相同目标，刷新有效期
                    existingInfo.endTick = currentTick + PRIORITY_TARGET_DURATION;
                } else {
                    // 切换目标，移除旧记录
                    priorityTargetMap.remove(ownerUUID);
                    priorityTargetMap.put(ownerUUID, new PriorityTargetInfo(newTarget, currentTick + PRIORITY_TARGET_DURATION));
                }
            } else {
                priorityTargetMap.put(ownerUUID, new PriorityTargetInfo(newTarget, currentTick + PRIORITY_TARGET_DURATION));
            }
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
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @return true 如果存在有效的优先攻击目标
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
     * 根据与目标的距离执行不同的移动策略：
     * - 距离>6格：向自身朝向移动，速度随距离增加（每额外1格+10%）
     * - 距离5-6格：以慢速接近目标
     * - 距离3-5格：保持位置，仅调整高度
     * - 距离<3格：以慢速离开目标
     * 高度保持在目标身高的50%-70%之间
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @param target 普通攻击目标
     * @param priorityTarget 优先攻击目标
     * @param deltaTime 时间增量
     * @return 新位置
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
            // 远距离：向朝向方向移动，速度随距离增加
            float excessDistance = (float) (horizontalDist - 6.0);
            float speedMultiplier = 1.0f + excessDistance * 0.15f;
            speed = MOVE_SPEED * speedMultiplier;
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
        } else if (horizontalDist > 5.0) {
            // 中远距离：慢速接近目标
            speed = LEAVE_SPEED;
            Vec3 toTarget = targetPos.subtract(dronePos).normalize();
            direction = new Vec3(toTarget.x, 0, toTarget.z).normalize();
        } else if (horizontalDist > 3.0) {
            // 理想距离：不水平移动，仅调整高度
            speed = 0;
            direction = Vec3.ZERO;
        } else {
            // 过近：慢速离开目标
            speed = LEAVE_SPEED;
            Vec3 awayDirection = dronePos.subtract(targetPos).normalize();
            direction = new Vec3(awayDirection.x, 0, awayDirection.z).normalize();
        }

        // 高度调整逻辑 - 只要有目标就总是触发
        // 目标高度范围为目标身高的50%-60%，在这个范围内不需要调整
        double targetHeightMin = targetPos.y + actualTarget.getBbHeight() * 0.5;
        double targetHeightMax = targetPos.y + actualTarget.getBbHeight() * 0.6;

        Vec3 verticalDirection = Vec3.ZERO;

        if (dronePos.y >= targetHeightMin && dronePos.y <= targetHeightMax) {
            // 在目标范围内，不调整高度
        } else if (dronePos.y > targetHeightMax) {
            // 需要下降，速度与高度差距成正比，每1格差距提升50%速度
            double heightDiff = dronePos.y - targetHeightMax;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, -HEIGHT_ADJUST_SPEED * speedFactor, 0);
        } else if (dronePos.y < targetHeightMin) {
            // 需要上升，速度与高度差距成正比，每1格差距提升50%速度
            double heightDiff = targetHeightMin - dronePos.y;
            double speedFactor = 1.0 + heightDiff * 0.5;
            verticalDirection = new Vec3(0, HEIGHT_ADJUST_SPEED * speedFactor, 0);
        }

        // 合并水平和垂直方向，保持水平移动速度
        Vec3 moveDirection = direction;
        double actualSpeed = speed;
        
        if (verticalDirection != Vec3.ZERO) {
            if (direction != Vec3.ZERO) {
                actualSpeed = speed;
            }
        }

        Vec3 newPos = dronePos.add(moveDirection.scale(actualSpeed * deltaTime));
        if (verticalDirection != Vec3.ZERO) {
            newPos = newPos.add(verticalDirection.scale(deltaTime));
        }

        Vec3 finalMovement = moveDirection.scale(actualSpeed);
        if (verticalDirection != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDirection);
        }
        drone.setDeltaMovement(finalMovement);

        applyDroneSeparation(drone, dronePos);

        return newPos;
    }

    /**
     * 待机模式移动逻辑
     * 无目标时的行为：
     * - 距离玩家>8格：向朝向方向移动，超过20格时立即传送回玩家
     * - 距离玩家<=8格：保持静止，与其他无人机保持2格距离
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @param deltaTime 时间增量
     * @return 新位置
     */
    private Vec3 standbyMovement(Entity drone, LivingEntity owner, float deltaTime) {
        Vec3 dronePos = drone.position();
        Vec3 ownerPos = owner.position();

        double horizontalDist = Math.sqrt(
            Math.pow(dronePos.x - ownerPos.x, 2) +
            Math.pow(dronePos.z - ownerPos.z, 2)
        );

        Vec3 toOwner = new Vec3(ownerPos.x - dronePos.x, 0, ownerPos.z - dronePos.z);
        Vec3 horizontalDir = toOwner.lengthSqr() > 0.001 ? toOwner.normalize() : Vec3.ZERO;

        Vec3 verticalDir = Vec3.ZERO;
        double heightDiff = ownerPos.y + 3.0 - dronePos.y;
        if (Math.abs(heightDiff) > 0.5) {
            verticalDir = new Vec3(0, Math.signum(heightDiff) * HEIGHT_ADJUST_SPEED, 0);
        }

        Vec3 finalMovement = Vec3.ZERO;

        if (horizontalDist > STANDBY_RANGE) {
            Level level = drone.level();
            List<DroneConstructEntity> allDrones = level.getEntitiesOfClass(
                DroneConstructEntity.class,
                new AABB(ownerPos.x - 30, ownerPos.y - 30, ownerPos.z - 30,
                         ownerPos.x + 30, ownerPos.y + 30, ownerPos.z + 30),
                other -> other.isAlive() && other.getOwnerUUID() != null && other.getOwnerUUID().equals(owner.getUUID())
            );

            List<Vec3> movingPositions = new java.util.ArrayList<>();
            movingPositions.add(dronePos);
            for (DroneConstructEntity other : allDrones) {
                if (other == drone) continue;
                Vec3 otherPos = other.position();
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
                myCluster = java.util.Collections.singletonList(dronePos);
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
        }

        if (verticalDir != Vec3.ZERO) {
            finalMovement = finalMovement.add(verticalDir);
        }

        drone.setDeltaMovement(finalMovement);

        applyDroneSeparation(drone, dronePos);

        return dronePos.add(finalMovement);
    }

    @Override
    public List<LivingEntity> searchTargets(Entity drone, LivingEntity owner, float range) {
        Level level = drone.level();
        Vec3 dronePos = drone.position();

        AABB searchBox = new AABB(
            dronePos.x - range,
            dronePos.y - range,
            dronePos.z - range,
            dronePos.x + range,
            dronePos.y + range,
            dronePos.z + range
        );

        Player player = owner instanceof Player ? (Player) owner : null;

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == owner || entity == drone) return false;
                    if (!entity.isAlive()) return false;
                    if (entity instanceof net.minecraft.world.entity.animal.AbstractGolem) return false;
                    if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
                    if (!HostileTargetManager.shouldAttackPlayer(entity, player)) return false;
                    return drone.distanceTo(entity) <= range;
                });

        return entities.stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(drone)))
                .collect(Collectors.toList());
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

        // 检查攻击冷却
        if (drone instanceof DroneConstructEntity droneEntity && droneEntity.getAttackCooldown() > 0) {
            return;
        }

        double distance = drone.distanceTo(target);
        if (distance > getConfigAttackRange()) {
            return;
        }

        // 检查视线
        boolean hasLineOfSight = drone instanceof LivingEntity livingDrone && livingDrone.hasLineOfSight(target);
        if (!hasLineOfSight) {
            return;
        }

        fireBullet(drone, owner, target);
    }

    /**
     * 发射无人机子弹
     * 子弹从无人机身高一半位置发射
     * @param drone 无人机实体
     * @param owner 玩家实体
     * @param target 目标实体
     */
    private void fireBullet(Entity drone, LivingEntity owner, LivingEntity target) {
        if (drone.level().isClientSide) {
            return;
        }

        Vec3 dronePos = drone.position();
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.5, 0);

        Vec3 direction = targetPos.subtract(dronePos).normalize();

        float damage = DroneBullet.BASE_DAMAGE;
        float cooldown = getConfigAttackInterval() * 20.0f;

        if (drone instanceof DroneConstructEntity droneEntity) {
            damage = (float) droneEntity.getAttributeValue(Attributes.ATTACK_DAMAGE);
            cooldown /= (float) droneEntity.getAttackSpeedMultiplier();
            droneEntity.setAttackCooldown((int) cooldown);

            // 创建并发射子弹
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