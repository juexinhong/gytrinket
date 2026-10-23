package com.gy_mod.gy_trinket.core.entity.meteor;

import com.gy_mod.gy_trinket.gytrinket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 陨石撞地时挖掉球形范围内的方块。
 * <p>
 * 半径 1.5× 自身半径后，特大陨石一次要处理约 19 万个方块。全部放在一 tick 里会卡死服务器，
 * 因此这里分两步：
 * <ol>
 *   <li>先同步挖掉撞击点周围 6 格的核心，保证陨石分裂体有空间下落；</li>
 *   <li>剩余部分进入队列，每 tick 处理固定数量的格位，逐帧扩大弹坑。</li>
 * </ol>
 * 不掉落物品；跳过不可破坏方块（基岩/屏障等）、流体和带方块实体的容器。
 */
@Mod.EventBusSubscriber(modid = gytrinket.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorCrater {

    /** 每 tick 最多检查多少个格位（含空气）；实际挖掉的方块数通常更少。 */
    private static final int MAX_POSITIONS_PER_TICK = 2048;
    /** 撞击时立刻挖掉的核心半径。 */
    private static final double CORE_RADIUS = 6.0;
    /** 只把 30% 的被毁方块转成 FallingBlock，其余直接删除，降低实体数量。 */
    private static final float FALLING_BLOCK_CHANCE = 0.30F;

    private static final Deque<CraterJob> JOBS = new ArrayDeque<>();

    private MeteorCrater() {
    }

    /**
     * 安排一次球形摧毁。核心区域立即生效，外围进入逐 tick 队列。
     */
    public static void breakSphere(ServerLevel level, Vec3 center, double radius) {
        breakEllipsoid(level, center, radius, radius, radius);
    }

    /**
     * 安排一次椭球形摧毁：横向/纵向可不同，用来做撞击弹坑而不是完整圆球。
     * 核心区域立即生效，外围进入逐 tick 队列。
     */
    public static void breakEllipsoid(ServerLevel level, Vec3 center,
                                      double rx, double ry, double rz) {
        BlockPos origin = BlockPos.containing(center);
        double maxRadius = Math.max(rx, Math.max(ry, rz));
        double coreScale = Math.min(1.0, CORE_RADIUS / Math.max(0.001, maxRadius));
        breakEllipsoidImmediate(level, origin, center,
                rx * coreScale, ry * coreScale, rz * coreScale);

        if (maxRadius > CORE_RADIUS + 0.25) {
            JOBS.add(new CraterJob(level, origin, center, Mth.ceil(maxRadius), rx, ry, rz));
        }
    }

    private static void breakEllipsoidImmediate(ServerLevel level, BlockPos origin, Vec3 center,
                                                double rx, double ry, double rz) {
        int r = Mth.ceil(Math.max(rx, Math.max(ry, rz)));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    // 以方块中心而不是方块最小角计算，椭圆圆心才和撞击点重合。
                    double wx = origin.getX() + dx + 0.5 - center.x;
                    double wy = origin.getY() + dy + 0.5 - center.y;
                    double wz = origin.getZ() + dz + 0.5 - center.z;
                    double nx = wx / Math.max(0.001, rx);
                    double ny = wy / Math.max(0.001, ry);
                    double nz = wz / Math.max(0.001, rz);
                    if (nx * nx + ny * ny + nz * nz > 1.0) {
                        continue;
                    }
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    breakBlock(level, pos, center, Math.max(rx, Math.max(ry, rz)));
                }
            }
        }
    }

    /**
     * @return 被挖掉的方块状态；没有挖掉任何东西时返回 null
     */
    private static BlockState breakBlock(ServerLevel level, BlockPos pos,
                                         Vec3 center, double effectRadius) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.liquid() || state.hasBlockEntity()) {
            return null;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return null;
        }
        if (level.random.nextFloat() < FALLING_BLOCK_CHANCE) {
            // FallingBlockEntity.fall() 自己会把该格设为空气/流体状态；
            // 不再先 setBlock，避免同一格两次方块更新。
            spawnFallingBlock(level, pos, state, center, effectRadius);
        } else {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return state;
    }

    /** 被摧毁的方块变成朝向爆心外侧 + 向上飞出的 FallingBlock。 */
    private static void spawnFallingBlock(ServerLevel level, BlockPos pos, BlockState state,
                                          Vec3 center, double effectRadius) {
        FallingBlockEntity debris = FallingBlockEntity.fall(level, pos.immutable(), state);
        Vec3 blockCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Vec3 delta = blockCenter.subtract(center);
        double distance = delta.length();
        double closeness = 1.0 - Math.min(1.0, distance / Math.max(0.001, effectRadius));
        double radiusFactor = 0.6 + effectRadius * 0.08;
        double speed = (0.08 + 0.92 * closeness * closeness) * radiusFactor;
        double up = 0.10 + 0.70 * closeness * radiusFactor;

        Vec3 horizontal = new Vec3(delta.x, 0.0, delta.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(1.0, 0.0, 0.0);
        }
        horizontal = horizontal.normalize();
        debris.setDeltaMovement(horizontal.x * speed, up, horizontal.z * speed);
        // fall() 已经 spawn，必须强制发一次运动包，否则客户端会先按零速度下落再瞬移。
        debris.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (JOBS.isEmpty()) {
            return;
        }

        int budget = MAX_POSITIONS_PER_TICK;
        while (budget > 0 && !JOBS.isEmpty()) {
            CraterJob job = JOBS.peek();
            // 世界/维度已经换掉时丢弃旧任务。
            if (event.getServer().getLevel(job.level.dimension()) != job.level) {
                JOBS.poll();
                continue;
            }
            budget -= job.tick(budget);
            if (job.done) {
                JOBS.poll();
            }
        }
    }

    private static final class CraterJob {

        private final ServerLevel level;
        private final BlockPos origin;
        private final Vec3 center;
        private final int radius;
        private final double rx;
        private final double ry;
        private final double rz;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        /** 按切比雪夫层从中心向外推进，避免弹坑从某个角先出现。 */
        private int layer;
        private int dx;
        private int dy;
        private int dz;
        private boolean done;

        private CraterJob(ServerLevel level, BlockPos origin, Vec3 center, int radius,
                          double rx, double ry, double rz) {
            this.level = level;
            this.origin = origin;
            this.center = center;
            this.radius = radius;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.layer = 0;
            this.dx = 0;
            this.dy = 0;
            this.dz = 0;
        }

        private int tick(int budget) {
            int used = 0;
            while (used < budget && !this.done) {
                used++;

                int cx = this.dx;
                int cy = this.dy;
                int cz = this.dz;
                this.advance();

                double wx = this.origin.getX() + cx + 0.5 - this.center.x;
                double wy = this.origin.getY() + cy + 0.5 - this.center.y;
                double wz = this.origin.getZ() + cz + 0.5 - this.center.z;
                double nx = wx / Math.max(0.001, this.rx);
                double ny = wy / Math.max(0.001, this.ry);
                double nz = wz / Math.max(0.001, this.rz);
                if (nx * nx + ny * ny + nz * nz > 1.0) {
                    continue;
                }

                this.cursor.set(this.origin.getX() + cx, this.origin.getY() + cy, this.origin.getZ() + cz);
                breakBlock(this.level, this.cursor, this.center,
                        Math.max(this.rx, Math.max(this.ry, this.rz)));
            }
            return used;
        }

        private void advance() {
            do {
                this.dz++;
                if (this.dz > this.layer) {
                    this.dz = -this.layer;
                    this.dy++;
                    if (this.dy > this.layer) {
                        this.dy = -this.layer;
                        this.dx++;
                        if (this.dx > this.layer) {
                            this.layer++;
                            if (this.layer > this.radius) {
                                this.done = true;
                                return;
                            }
                            this.dx = -this.layer;
                            this.dy = -this.layer;
                            this.dz = -this.layer;
                        }
                    }
                }
            } while (!this.isShell());
        }

        /** 当前坐标是否位于当前层的立方体表面。 */
        private boolean isShell() {
            return Math.abs(this.dx) == this.layer
                    || Math.abs(this.dy) == this.layer
                    || Math.abs(this.dz) == this.layer;
        }
    }
}
