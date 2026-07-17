package com.gy_mod.gy_trinket.core.entity.construct;

import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.drone.DroneConstructTypes;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructEntity;
import com.gy_mod.gy_trinket.core.entity.construct.swarm.SwarmConstructTypes;
import com.gy_mod.gy_trinket.core.hostile_target.HostileTargetManager;
import com.gy_mod.gy_trinket.core.shield.ShieldData;
import com.gy_mod.gy_trinket.core.shield.ShieldManager;
import com.gy_mod.gy_trinket.core.shield_transfer.ShieldTransferManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 构造体组共享缓存
 * <p>
 * 将同玩家的构造体每tick重复计算的数据缓存为每玩家每tick计算一次，
 * 所有同类型构造体实体共享结果，避免 O(N²) 重复计算。
 * <p>
 * 缓存项：
 * <ul>
 *   <li>Boid邻居快照 — 同类构造体的位置/速度列表，每tick只做一次 getEntitiesOfClass</li>
 *   <li>索敌结果 — 同玩家构造体共享的敌人列表，以玩家位置为中心只查询一次</li>
 *   <li>护盾状态 — 避免每个蜂群独立读取 ShieldManager</li>
 *   <li>修复分配 — 预计算哪些蜂群参与修复，O(1)查询</li>
 * </ul>
 */
public final class ConstructGroupCache {

    private static final ConstructGroupCache INSTANCE = new ConstructGroupCache();

    /** 缓存过期阈值：超过此tick数自动失效 */
    private static final long CACHE_TTL = 2;

    // ===== 缓存存储 =====

    /** 玩家UUID → Boid邻居快照（按构造体类型分） */
    private final Map<UUID, Map<String, CachedBoidSnapshot>> boidSnapshots = new ConcurrentHashMap<>();

    /** 玩家UUID → 索敌结果快照 */
    private final Map<UUID, CachedTargetSnapshot> targetSnapshots = new ConcurrentHashMap<>();

    /** 玩家UUID → 护盾状态快照 */
    private final Map<UUID, CachedShieldState> shieldStates = new ConcurrentHashMap<>();

    /** 玩家UUID → 蜂群修复分配集合 */
    private final Map<UUID, CachedRepairAssignment> repairAssignments = new ConcurrentHashMap<>();

    private ConstructGroupCache() {}

    public static ConstructGroupCache getInstance() {
        return INSTANCE;
    }

    // ===== Boid 邻居快照 =====

    /**
     * 获取指定玩家、指定类型构造体的Boid邻居数据。
     * 只在缓存过期时重新查询 getEntitiesOfClass，否则直接返回缓存。
     */
    public CachedBoidSnapshot getBoidSnapshot(UUID ownerUUID, String constructTypeId, Level level, Vec3 groupCenter) {
        long currentTick = level.getGameTime();

        Map<String, CachedBoidSnapshot> typeMap = boidSnapshots.computeIfAbsent(ownerUUID, k -> new ConcurrentHashMap<>());
        CachedBoidSnapshot snapshot = typeMap.get(constructTypeId);

        if (snapshot != null && snapshot.isValid(currentTick)) {
            return snapshot;
        }

        // 计算新的快照
        snapshot = computeBoidSnapshot(ownerUUID, constructTypeId, level, groupCenter, currentTick);
        typeMap.put(constructTypeId, snapshot);
        return snapshot;
    }

    private CachedBoidSnapshot computeBoidSnapshot(UUID ownerUUID, String constructTypeId, Level level, Vec3 groupCenter, long currentTick) {
        double scanRange = 8.0;
        // 以组中心为基准扩大搜索范围
        Vec3 center = groupCenter != null ? groupCenter : Vec3.ZERO;
        AABB scanBox = new AABB(
            center.x - scanRange, center.y - scanRange, center.z - scanRange,
            center.x + scanRange, center.y + scanRange, center.z + scanRange
        );

        List<Vec3> positions = new ArrayList<>();
        List<Vec3> velocities = new ArrayList<>();
        Map<UUID, Integer> entityIndexMap = new HashMap<>();

        int index = 0;
        if (SwarmConstructTypes.SWARM.equals(constructTypeId)) {
            List<SwarmConstructEntity> nearby = level.getEntitiesOfClass(
                SwarmConstructEntity.class, scanBox,
                other -> other.isAlive()
                         && other.getOwnerUUID() != null
                         && other.getOwnerUUID().equals(ownerUUID)
            );
            for (SwarmConstructEntity entity : nearby) {
                entityIndexMap.put(entity.getUUID(), index++);
                positions.add(entity.position());
                velocities.add(entity.getDeltaMovement());
            }
        } else if (DroneConstructTypes.DRONE.equals(constructTypeId)) {
            List<DroneConstructEntity> nearby = level.getEntitiesOfClass(
                DroneConstructEntity.class, scanBox,
                other -> other.isAlive()
                         && other.getOwnerUUID() != null
                         && other.getOwnerUUID().equals(ownerUUID)
            );
            for (DroneConstructEntity entity : nearby) {
                entityIndexMap.put(entity.getUUID(), index++);
                positions.add(entity.position());
                velocities.add(entity.getDeltaMovement());
            }
        }

        return new CachedBoidSnapshot(positions, velocities, entityIndexMap, currentTick);
    }

    /**
     * 获取指定实体在邻居列表中的位置和速度数据（排除自身）。
     * 直接返回列表引用，调用方不应修改。
     */
    public NeighborData getNeighborData(UUID ownerUUID, String constructTypeId, UUID selfUUID, Level level, Vec3 groupCenter) {
        CachedBoidSnapshot snapshot = getBoidSnapshot(ownerUUID, constructTypeId, level, groupCenter);

        Integer selfIndex = snapshot.entityIndexMap.get(selfUUID);
        if (selfIndex == null) {
            return new NeighborData(snapshot.positions, snapshot.velocities);
        }

        // 排除自身
        List<Vec3> filteredPositions = new ArrayList<>(snapshot.positions.size() - 1);
        List<Vec3> filteredVelocities = new ArrayList<>(snapshot.velocities.size() - 1);
        for (int i = 0; i < snapshot.positions.size(); i++) {
            if (i != selfIndex) {
                filteredPositions.add(snapshot.positions.get(i));
                filteredVelocities.add(snapshot.velocities.get(i));
            }
        }
        return new NeighborData(filteredPositions, filteredVelocities);
    }

    // ===== 索敌结果快照 =====

    /**
     * 获取指定玩家为中心的索敌结果。
     * 以玩家位置为中心查询，所有同玩家构造体共享。
     */
    public CachedTargetSnapshot getTargetSnapshot(UUID ownerUUID, LivingEntity owner, Level level, float searchRange) {
        long currentTick = level.getGameTime();

        CachedTargetSnapshot snapshot = targetSnapshots.get(ownerUUID);
        if (snapshot != null && snapshot.isValid(currentTick) && snapshot.searchRange >= searchRange) {
            return snapshot;
        }

        // 以玩家位置为中心查询
        Vec3 ownerPos = owner.position();
        AABB searchBox = new AABB(
            ownerPos.x - searchRange, ownerPos.y - searchRange, ownerPos.z - searchRange,
            ownerPos.x + searchRange, ownerPos.y + searchRange, ownerPos.z + searchRange
        );

        Player player = owner instanceof Player p ? p : null;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity == owner) return false;
                    if (!entity.isAlive()) return false;
                    if (entity instanceof AbstractGolem) return false;
                    if (entity instanceof IConstructEntity ce) {
                        UUID entOwner = ce.getOwnerUUID();
                        if (entOwner != null && entOwner.equals(ownerUUID)) return false;
                    }
                    if (player != null && HostileTargetManager.isEntityProtectedByPlayer(entity, player)) return false;
                    return HostileTargetManager.shouldAttackPlayer(entity, player);
                });

        // 按距离玩家由近到远排序
        targets.sort(Comparator.comparingDouble(t -> ownerPos.distanceToSqr(t.position())));

        snapshot = new CachedTargetSnapshot(targets, ownerPos, searchRange, currentTick);
        targetSnapshots.put(ownerUUID, snapshot);
        return snapshot;
    }

    /**
     * 从共享索敌缓存中查找距离指定位置最近的敌人。
     * 先查共享缓存，再按到查询位置的距离重排取最近。
     */
    @Nullable
    public LivingEntity findNearestTarget(UUID ownerUUID, LivingEntity owner, Vec3 queryPos, float searchRange) {
        CachedTargetSnapshot snapshot = getTargetSnapshot(ownerUUID, owner, owner.level(), searchRange);

        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (LivingEntity target : snapshot.targets) {
            if (!target.isAlive()) continue;
            double dist = queryPos.distanceToSqr(target.position());
            if (dist < nearestDist && dist <= (double) searchRange * searchRange) {
                nearestDist = dist;
                nearest = target;
            }
        }
        return nearest;
    }

    /**
     * 从共享索敌缓存中查找指定位置范围内的所有敌人。
     */
    public List<LivingEntity> findTargetsInRange(UUID ownerUUID, LivingEntity owner, Vec3 queryPos, float range) {
        CachedTargetSnapshot snapshot = getTargetSnapshot(ownerUUID, owner, owner.level(), range);

        double rangeSq = (double) range * range;
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : snapshot.targets) {
            if (!target.isAlive()) continue;
            if (queryPos.distanceToSqr(target.position()) <= rangeSq) {
                result.add(target);
            }
        }
        result.sort(Comparator.comparingDouble(t -> queryPos.distanceToSqr(t.position())));
        return result;
    }

    // ===== 护盾状态快照 =====

    /**
     * 获取指定玩家的护盾状态缓存。
     */
    public CachedShieldState getShieldState(UUID ownerUUID, Level level) {
        long currentTick = level.getGameTime();

        CachedShieldState state = shieldStates.get(ownerUUID);
        if (state != null && state.isValid(currentTick)) {
            return state;
        }

        ShieldData shieldData = ShieldManager.getShieldData(ownerUUID);
        double currentShield = shieldData != null ? shieldData.getCurrentShield() : 0.0;
        double maxShield = shieldData != null ? shieldData.getMaxShield() : 0.0;

        // 破裂：护盾值≤0 或 没有护盾数据，与移植无关
        boolean broken = shieldData == null || currentShield <= 0.0;
        // 可修复：护盾未移植 且 护盾值>0 且 护盾未满
        boolean transferred = ShieldTransferManager.hasTransferredShield(ownerUUID);
        boolean canRepair = !transferred && currentShield > 0.0 && currentShield < maxShield;

        state = new CachedShieldState(broken, canRepair, transferred, currentShield, maxShield, currentTick);
        shieldStates.put(ownerUUID, state);
        return state;
    }

    // ===== 修复分配 =====

    /**
     * 获取蜂群修复分配结果。
     * 每tick预计算哪些蜂群被分配到修复模式，每个蜂群只需 O(1) 查询。
     */
    public CachedRepairAssignment getRepairAssignment(UUID ownerUUID, Level level) {
        long currentTick = level.getGameTime();

        CachedRepairAssignment assignment = repairAssignments.get(ownerUUID);
        if (assignment != null && assignment.isValid(currentTick)) {
            return assignment;
        }

        CachedShieldState shieldState = getShieldState(ownerUUID, level);

        // 获取所有蜂群实体并排序
        ConstructManager cm = ConstructManager.getInstance();
        Map<UUID, Entity> swarmEntities = cm.getActiveConstructEntities(ownerUUID, SwarmConstructTypes.SWARM);

        List<SwarmConstructEntity> swarms = new ArrayList<>();
        for (Entity entity : swarmEntities.values()) {
            if (entity instanceof SwarmConstructEntity swarm && swarm.isAlive()) {
                swarms.add(swarm);
            }
        }
        swarms.sort(Comparator.comparing(Entity::getUUID));

        Set<UUID> repairSet = new HashSet<>();

        if (!swarms.isEmpty() && shieldState.canRepair) {
            double damageRatio = shieldState.maxShield > 0.0
                    ? (shieldState.maxShield - shieldState.currentShield) / shieldState.maxShield
                    : 0.0;

            if (damageRatio > 0.0) {
                double repairRatio = 0.25 + 0.25 * damageRatio;
                int repairCount = (int) Math.round(swarms.size() * repairRatio);
                repairCount = Math.max(1, Math.min(repairCount, swarms.size()));

                for (int i = 0; i < repairCount; i++) {
                    repairSet.add(swarms.get(i).getUUID());
                }
            }
        }

        assignment = new CachedRepairAssignment(repairSet, currentTick);
        repairAssignments.put(ownerUUID, assignment);
        return assignment;
    }

    // ===== 清理 =====

    /**
     * 清理指定玩家的所有缓存（玩家退出时调用）
     */
    public void clearPlayerCache(UUID ownerUUID) {
        boidSnapshots.remove(ownerUUID);
        targetSnapshots.remove(ownerUUID);
        shieldStates.remove(ownerUUID);
        repairAssignments.remove(ownerUUID);
    }

    /**
     * 强制使指定玩家的索敌缓存失效（目标死亡等场景）
     */
    public void invalidateTargetCache(UUID ownerUUID) {
        targetSnapshots.remove(ownerUUID);
    }

    // ===== 缓存数据结构 =====

    public static final class CachedBoidSnapshot {
        public final List<Vec3> positions;
        public final List<Vec3> velocities;
        /** 实体UUID -> 在列表中的索引 */
        public final Map<UUID, Integer> entityIndexMap;
        private final long createdTick;

        CachedBoidSnapshot(List<Vec3> positions, List<Vec3> velocities, Map<UUID, Integer> entityIndexMap, long createdTick) {
            this.positions = positions;
            this.velocities = velocities;
            this.entityIndexMap = entityIndexMap;
            this.createdTick = createdTick;
        }

        boolean isValid(long currentTick) {
            return currentTick - createdTick < CACHE_TTL;
        }
    }

    public static final class NeighborData {
        public final List<Vec3> positions;
        public final List<Vec3> velocities;

        NeighborData(List<Vec3> positions, List<Vec3> velocities) {
            this.positions = positions;
            this.velocities = velocities;
        }
    }

    public static final class CachedTargetSnapshot {
        public final List<LivingEntity> targets;
        public final Vec3 queryCenter;
        public final float searchRange;
        private final long createdTick;

        CachedTargetSnapshot(List<LivingEntity> targets, Vec3 queryCenter, float searchRange, long createdTick) {
            this.targets = targets;
            this.queryCenter = queryCenter;
            this.searchRange = searchRange;
            this.createdTick = createdTick;
        }

        boolean isValid(long currentTick) {
            return currentTick - createdTick < CACHE_TTL;
        }
    }

    public static final class CachedShieldState {
        /** 破裂：护盾值≤0 或没有护盾数据，与是否移植无关 */
        public final boolean broken;
        /** 可修复：护盾未移植 且 护盾值>0 且 护盾未满 */
        public final boolean canRepair;
        /** 护盾是否已移植到其他实体 */
        public final boolean transferred;
        public final double currentShield;
        public final double maxShield;
        private final long createdTick;

        CachedShieldState(boolean broken, boolean canRepair, boolean transferred, double currentShield, double maxShield, long createdTick) {
            this.broken = broken;
            this.canRepair = canRepair;
            this.transferred = transferred;
            this.currentShield = currentShield;
            this.maxShield = maxShield;
            this.createdTick = createdTick;
        }

        boolean isValid(long currentTick) {
            return currentTick - createdTick < CACHE_TTL;
        }
    }

    public static final class CachedRepairAssignment {
        public final Set<UUID> repairEntityUUIDs;
        private final long createdTick;

        CachedRepairAssignment(Set<UUID> repairEntityUUIDs, long createdTick) {
            this.repairEntityUUIDs = repairEntityUUIDs;
            this.createdTick = createdTick;
        }

        boolean isValid(long currentTick) {
            return currentTick - createdTick < CACHE_TTL;
        }

        public boolean isAssigned(UUID entityUUID) {
            return repairEntityUUIDs.contains(entityUUID);
        }
    }
}
