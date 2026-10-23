package com.gy_mod.gy_trinket.core.entity.meteor;

import com.gy_mod.gy_trinket.core.world.IslandTeleporter;
import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * 孤岛维度陨石自然生成。
 * <p>
 * 只在中心孤岛地形以外生成；城镇 1 的矩形范围额外保护。
 * 不再限制生成高度；在玩家周围尝试生成时，实际脚底位置比玩家高 250 格。
 * 每个在孤岛维度的玩家每 2 秒判定一次 13% 概率；生成成功时在附近再滚一个陨石群。
 * 陨石群和分裂体都不会再次触发这里的生成入口。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorSpawner {

    private static final int CHECK_INTERVAL_TICKS = 40;
    /** 为提高可见性暂时提高到 13%/2 秒/玩家。 */
    private static final double SPAWN_CHANCE = 0.13;
    private static final int MAX_ACTIVE = 24;

    /** 中心孤岛地形半径：这个圆内不生成。 */
    private static final double ISLAND_TERRAIN_RADIUS = 256.0;
    /** 城镇 1 模板范围：ORIGIN=(-93,32,-93)，尺寸 291×291。 */
    private static final double TOWN_MIN_X = -93.0;
    private static final double TOWN_MIN_Z = -93.0;
    private static final double TOWN_SIZE = 291.0;
    private static final double TOWN_SAFE_MARGIN = 16.0;

    private static final int MAX_CLUSTER_MEMBERS = 10;
    private static final double CLUSTER_FIRST_CHANCE = 0.80;
    private static final double CLUSTER_DECAY = 0.70;
    private static final double CLUSTER_MIN_CHANCE = 0.01;

    private MeteorSpawner() {
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MeteorEntity.clearActiveCounts();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(IslandTeleporter.ISLAND_DIMENSION);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        if (countMeteors(level) >= MAX_ACTIVE) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (level.random.nextDouble() < SPAWN_CHANCE) {
                tryNaturalSpawn(level, player);
            }
        }
    }

    private static int countMeteors(ServerLevel level) {
        // O(1)：MeteorEntity 在进入/离开世界时维护各维度活跃计数。
        return MeteorEntity.getActiveCount(level);
    }

    private static boolean tryNaturalSpawn(ServerLevel level, ServerPlayer player) {
        MeteorSize primarySize = pickWeighted(level.random);

        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double distance = 32.0 + level.random.nextDouble() * 64.0;
            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            if (!isAllowedSpawnXZ(x, z)) {
                continue;
            }

            // 实际脚底位置比玩家高 250 格；不再限制绝对高度。
            double centerY = player.getY() + 250.0 + primarySize.radius();
            Vec3 center = new Vec3(x, centerY, z);
            if (spawnOne(level, center, primarySize, true)) {
                spawnCluster(level, center, primarySize, true);
                return true;
            }
        }
        return false;
    }

    /**
     * 在指定位置生成一颗陨石。
     *
     * @param natural true 时只额外要求：生成点在中心孤岛地形以外，且不在城镇 1 的矩形保护范围内
     * @return 是否成功加入世界
     */
    public static boolean spawnOne(ServerLevel level, Vec3 center, MeteorSize size, boolean natural) {
        double radius = size.radius();
        double hitboxRadius = size.hitboxRadius();
        // center 是视觉/碰撞箱中心；实体位置（碰撞箱底）要减去碰撞箱半高。
        double feetY = center.y - hitboxRadius;

        if (center.y - radius < level.getMinBuildHeight() + 4.0) {
            return false;
        }
        // 250 格抬升后可能超过原版建筑高度上限；陨石允许在世界高度以上生成/下落。
        if (!level.isLoaded(BlockPos.containing(center.x, center.y, center.z))) {
            return false;
        }

        if (natural) {
            if (!isAllowedSpawnXZ(center.x, center.z)) {
                return false;
            }
        }

        // 用完整的视觉外壳做空间检查，避免模型卡进地形。
        AABB box = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        if (!level.noCollision(box)) {
            return false;
        }

        MeteorEntity meteor = MeteorEntities.create(size, level);
        meteor.setPos(center.x, feetY, center.z);

        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double speed = 0.02 + level.random.nextDouble() * 0.03;
        meteor.setDeltaMovement(Math.cos(angle) * speed, 0.0, Math.sin(angle) * speed);

        boolean added = level.addFreshEntity(meteor);
        if (added && natural) {
            // 自然生成时给远处的玩家一点听觉/视觉提示，否则 150 格高的陨石很容易被忽略。
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT,
                    0.45F, 0.5F + level.random.nextFloat() * 0.2F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z,
                    14, radius * 0.5, radius * 0.5, radius * 0.5, 0.03);
        }
        return added;
    }

    /** 中心孤岛地形以外，且不落在城镇 1 的矩形保护范围内。 */
    private static boolean isAllowedSpawnXZ(double x, double z) {
        if (x * x + z * z <= ISLAND_TERRAIN_RADIUS * ISLAND_TERRAIN_RADIUS) {
            return false;
        }
        double minX = TOWN_MIN_X - TOWN_SAFE_MARGIN;
        double maxX = TOWN_MIN_X + TOWN_SIZE + TOWN_SAFE_MARGIN;
        double minZ = TOWN_MIN_Z - TOWN_SAFE_MARGIN;
        double maxZ = TOWN_MIN_Z + TOWN_SIZE + TOWN_SAFE_MARGIN;
        return x < minX || x > maxX || z < minZ || z > maxZ;
    }

    /**
     * 在中心附近生成陨石群成员。调用方负责先放主陨石。
     * <p>
     * 概率逐只衰减，但保底 1%，最多 10 只额外成员。
     *
     * @return 实际尝试添加的成员数（位置不合适时可能少于这个数）
     */
    public static int spawnCluster(ServerLevel level, Vec3 center, MeteorSize primarySize, boolean natural) {
        int attempts = 0;
        int spawned = 0;
        double chance = CLUSTER_FIRST_CHANCE;

        while (attempts < MAX_CLUSTER_MEMBERS
                && level.random.nextDouble() < Math.max(CLUSTER_MIN_CHANCE, chance)) {
            attempts++;
            chance *= CLUSTER_DECAY;

            MeteorSize childSize = pickWeighted(level.random);
            double minDistance = primarySize.radius() + childSize.radius() + 4.0;

            for (int placementAttempt = 0; placementAttempt < 8; placementAttempt++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double distance = minDistance + level.random.nextDouble() * 20.0;
                double x = center.x + Math.cos(angle) * distance;
                double z = center.z + Math.sin(angle) * distance;
                double y = center.y + (level.random.nextDouble() - 0.5) * 16.0;
                if (spawnOne(level, new Vec3(x, y, z), childSize, natural)) {
                    spawned++;
                    break;
                }
            }
        }
        return spawned;
    }

    private static MeteorSize pickWeighted(RandomSource random) {
        int total = 0;
        for (MeteorSize size : MeteorSize.values()) {
            total += size.spawnWeight();
        }
        int roll = random.nextInt(total);
        for (MeteorSize size : MeteorSize.values()) {
            roll -= size.spawnWeight();
            if (roll < 0) {
                return size;
            }
        }
        return MeteorSize.SMALL;
    }
}
