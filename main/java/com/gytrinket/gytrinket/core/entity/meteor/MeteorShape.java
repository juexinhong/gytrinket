package com.gytrinket.gytrinket.core.entity.meteor;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一颗陨石的客户端外形：一组按整数格排列的外壳方块。
 * <p>
 * 形状由实体同步过来的随机种子确定性生成，服务端和客户端不需要传输方块列表。
 * 只保留「至少有一个面暴露在空气里」的壳层方块；内部方块不渲染。
 */
public final class MeteorShape {

    /** 最终要渲染的一个方块。坐标是体素格坐标，渲染时乘以 cellSize。 */
    public record RenderCell(int x, int y, int z, BlockState state) {
    }

    private record Cell(int x, int y, int z) {
    }

    private final float cellSize;
    private final List<RenderCell> cells;
    /** 渲染时的整体缩放：把体素外壳的最外侧面精确对齐到标称半径。 */
    private final float visualScale;

    private MeteorShape(float cellSize, List<RenderCell> cells, float visualScale) {
        this.cellSize = cellSize;
        this.cells = List.copyOf(cells);
        this.visualScale = visualScale;
    }

    public float cellSize() {
        return cellSize;
    }

    public List<RenderCell> cells() {
        return cells;
    }

    public float visualScale() {
        return visualScale;
    }

    /**
     * 生成不规则陨石外形。
     * <p>
     * 用低频噪声决定球面半径，再叠加 2~3 个随机凸起，最后挖掉噪声过低的区域，
     * 因此既不会是完全的圆球，也不会是方块。外壳尺寸被限制在实体碰撞箱内。
     */
    public static MeteorShape generate(long seed, MeteorSize size, boolean fragment) {
        float radius = size.radius();
        float cellSize = size.cellSize();
        // 单块方块最外侧面最多超出格中心 cellSize/2，所以形状半径要预留这部分。
        double shapeRadius = Math.max(radius * 0.55, radius - cellSize * 0.5);
        // 给正向噪声凸起留出格位，避免外壳被网格截断。
        int grid = Mth.ceil(shapeRadius * 1.12 / cellSize);

        ImprovedNoise noise = new ImprovedNoise(RandomSource.create(seed));
        RandomSource random = RandomSource.create(seed ^ 0x9E3779B97F4A7C15L);

        int lobeCount = 2 + random.nextInt(2);
        Vec3[] lobeCenters = new Vec3[lobeCount];
        double[] lobeRadii = new double[lobeCount];
        for (int i = 0; i < lobeCount; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double cosPhi = random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            double lx = Math.cos(theta) * sinPhi;
            double ly = cosPhi * 0.8;
            double lz = Math.sin(theta) * sinPhi;
            double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (len < 1.0E-6) {
                lx = 1.0;
                ly = 0.0;
                lz = 0.0;
                len = 1.0;
            }
            double centerDist = shapeRadius * (0.38 + random.nextDouble() * 0.16);
            lobeCenters[i] = new Vec3(lx / len * centerDist, ly / len * centerDist, lz / len * centerDist);
            lobeRadii[i] = shapeRadius * (0.30 + random.nextDouble() * 0.12);
        }

        // 分裂体不是小球，而是从母体上崩下来的一块：用随机平面切掉一部分，
        // 切口再叠加噪声，得到不规则断面。
        Vec3 fractureNormal = null;
        Vec3 fractureNormal2 = null;
        double fractureOffset = 0.0;
        double fractureOffset2 = 0.0;
        if (fragment) {
            double theta = random.nextDouble() * Math.PI * 2.0;
            double cosPhi = random.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));
            fractureNormal = new Vec3(
                    Math.cos(theta) * sinPhi,
                    cosPhi,
                    Math.sin(theta) * sinPhi).normalize();
            // 第一刀切掉大半，只留下偏一侧的石块，而不是 80% 的小球。
            fractureOffset = shapeRadius * (-0.20 + random.nextDouble() * 0.30);

            // 第二刀再切出一个角，得到带两个断面的碎块。
            double theta2 = theta + Math.PI * (0.5 + random.nextDouble() * 0.7);
            double cosPhi2 = random.nextDouble() * 2.0 - 1.0;
            double sinPhi2 = Math.sqrt(Math.max(0.0, 1.0 - cosPhi2 * cosPhi2));
            fractureNormal2 = new Vec3(
                    Math.cos(theta2) * sinPhi2,
                    cosPhi2,
                    Math.sin(theta2) * sinPhi2).normalize();
            fractureOffset2 = shapeRadius * (0.02 + random.nextDouble() * 0.20);
        }

        Set<Cell> solid = new HashSet<>();
        for (int x = -grid; x <= grid; x++) {
            for (int y = -grid; y <= grid; y++) {
                for (int z = -grid; z <= grid; z++) {
                    double wx = x * cellSize;
                    double wy = y * cellSize;
                    double wz = z * cellSize;
                    double distance = Math.sqrt(wx * wx + wy * wy + wz * wz);
                    if (distance > shapeRadius * 1.12) {
                        continue;
                    }

                    double n = noise.noise(wx * 0.16, wy * 0.16, wz * 0.16);
                    double n2 = noise.noise(wx * 0.42 + 37.0, wy * 0.42, wz * 0.42);
                    // 加大噪声幅度：凹陷和凸起都更明显，最终由 visualScale
                    // 把最外侧面归一化到标称半径，不会因此变小。
                    double limit = shapeRadius * (1.0 + 0.18 * n + 0.08 * n2);

                    boolean inside = distance <= limit || distance <= shapeRadius * 0.42;
                    if (!inside) {
                        for (int i = 0; i < lobeCount; i++) {
                            Vec3 center = lobeCenters[i];
                            double dx = wx - center.x;
                            double dy = wy - center.y;
                            double dz = wz - center.z;
                            double lobeRadius = lobeRadii[i]
                                    * (0.82 + 0.28 * noise.noise(wx * 0.5 + i * 17.0, wy * 0.5, wz * 0.5));
                            if (dx * dx + dy * dy + dz * dz <= lobeRadius * lobeRadius) {
                                inside = true;
                                break;
                            }
                        }
                    }

                    if (inside && fractureNormal != null) {
                        double dot = wx * fractureNormal.x + wy * fractureNormal.y + wz * fractureNormal.z;
                        double rough = shapeRadius * 0.10
                                * noise.noise(wx * 0.30 + 11.0, wy * 0.30, wz * 0.30);
                        if (dot > fractureOffset + rough) {
                            inside = false;
                        }
                        if (inside && fractureNormal2 != null) {
                            double dot2 = wx * fractureNormal2.x + wy * fractureNormal2.y + wz * fractureNormal2.z;
                            double rough2 = shapeRadius * 0.09
                                    * noise.noise(wx * 0.34 + 53.0, wy * 0.34, wz * 0.34);
                            if (dot2 > fractureOffset2 + rough2) {
                                inside = false;
                            }
                        }
                    }

                    if (inside) {
                        solid.add(new Cell(x, y, z));
                    }
                }
            }
        }

        List<RenderCell> shell = new ArrayList<>();
        for (Cell cell : solid) {
            boolean surface = !solid.contains(new Cell(cell.x + 1, cell.y, cell.z))
                    || !solid.contains(new Cell(cell.x - 1, cell.y, cell.z))
                    || !solid.contains(new Cell(cell.x, cell.y + 1, cell.z))
                    || !solid.contains(new Cell(cell.x, cell.y - 1, cell.z))
                    || !solid.contains(new Cell(cell.x, cell.y, cell.z + 1))
                    || !solid.contains(new Cell(cell.x, cell.y, cell.z - 1));
            if (surface) {
                shell.add(new RenderCell(cell.x, cell.y, cell.z, MeteorPalette.pick(hash(seed, cell))));
            }
        }

        if (shell.isEmpty()) {
            shell.add(new RenderCell(0, 0, 0, MeteorPalette.pick(seed)));
        }

        // 体素格中心是离散的，最外圈不一定正好落在 shapeRadius 上。
        // 用整体缩放把「最外侧面」精确推到标称半径，避免看起来比碰撞箱小一圈。
        double maxCenterDistance = 0.0;
        for (RenderCell cell : shell) {
            double wx = cell.x() * cellSize;
            double wy = cell.y() * cellSize;
            double wz = cell.z() * cellSize;
            maxCenterDistance = Math.max(maxCenterDistance, Math.sqrt(wx * wx + wy * wy + wz * wz));
        }
        double actualOuterRadius = maxCenterDistance + cellSize * 0.5;
        float visualScale = (float) (radius / Math.max(0.001, actualOuterRadius));

        return new MeteorShape(cellSize, shell, visualScale);
    }

    private static long hash(long seed, Cell cell) {
        long h = seed;
        h ^= (long) cell.x * 0x9E3779B97F4A7C15L;
        h ^= (long) cell.y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) cell.z * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }
}
