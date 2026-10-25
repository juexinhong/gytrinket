package com.gytrinket.gytrinket.core.world.terrain;

/**
 * 战场地形核心（纯 Java，无 Minecraft 依赖）。
 * <p>
 * 保留原「孤岛三区」布局，但把地表换成被反复轰炸后的灰白死寂世界：
 * <ol>
 *   <li><b>中心孤岛（r &lt; 256）</b>：抬高的灰白高原，只有浅坑与风化沟壑，
 *       是残塔唯一会生成的地方；</li>
 *   <li><b>环带（256 ≤ r &lt; 640）</b>：整体塌陷的轰炸盆地，中/大弹坑最密集；</li>
 *   <li><b>外围（r ≥ 640）</b>：常规战场，多层次弹坑 + 长条轰炸带。</li>
 * </ol>
 * 两条交界都是 40 格左右的断崖过渡带，崖线随角度抖动、崖面有缺口噪声。
 * <p>
 * 所有地貌由种子哈希确定性生成，游戏内与离线预览结果一致。
 */
public final class BattlefieldTerrain {

    /** 世界最低高度（与维度 min_y 一致） */
    public static final int MIN_Y = -64;
    /** 中心孤岛基准高度 */
    public static final double ISLAND_HEIGHT = 30.0;
    /** 环带（轰炸盆地）基准高度 */
    public static final double RING_HEIGHT = -20.0;
    /** 外围基准高度 */
    public static final double OUTER_HEIGHT = 15.0;
    /** 孤岛半径（崖线均值） */
    public static final double ISLAND_RADIUS = 256.0;
    /** 环带外缘半径 */
    public static final double RING_OUTER = 640.0;
    /** 断崖过渡总宽 */
    private static final double CLIFF_WIDTH = 44.0;
    /** 原点落脚地半径（覆盖整座城镇，减少地基周围的起伏） */
    private static final double PAD_RADIUS = 200.0;
    /** 落脚地台面高度（城镇 1 模板的地面层对齐到这里） */
    public static final int PAD_TOP = 32;
    /** 落脚地高度 */
    private static final double PAD_HEIGHT = PAD_TOP;
    private static final double PAD_FADE = 30.0;

    /** 残塔只允许生成在这个半径内（留出断崖余量） */
    public static final double TOWER_RADIUS = 145.0;

    /** 单个弹坑层的参数 */
    private record CraterLayer(int cell, double rMin, double rMax, double dMin, double dMax,
                               double density, double rim, double edge, double stretchMax,
                               double peakMinRadius, long salt) {
    }

    /** 浅坑：孤岛上也有，让地面不至于太平 */
    private static final CraterLayer SMALL = new CraterLayer(
            20, 2.5, 7.0, 1.2, 3.2, 0.55, 0.45, 0.32, 1.30, 0.0, 101L);
    /** 中坑 */
    private static final CraterLayer MEDIUM = new CraterLayer(
            62, 8.0, 26.0, 5.0, 16.0, 0.62, 1.8, 0.27, 1.45, 18.0, 202L);
    /** 大坑 */
    private static final CraterLayer LARGE = new CraterLayer(
            250, 34.0, 92.0, 16.0, 38.0, 0.48, 3.6, 0.22, 1.50, 40.0, 303L);
    /** 巨型坑：罕见地标 */
    private static final CraterLayer GIANT = new CraterLayer(
            880, 105.0, 190.0, 24.0, 46.0, 0.55, 5.0, 0.18, 1.55, 78.0, 404L);

    private static final CraterLayer[] LAYERS = {SMALL, MEDIUM};

    /** 轰炸带参数（只在外围出现） */
    private static final int TRENCH_CELL = 620;
    private static final double TRENCH_DENSITY = 0.55;
    private static final double TRENCH_MIN_LEN = 140.0;
    private static final double TRENCH_MAX_LEN = 420.0;
    private static final double TRENCH_MIN_WIDTH = 4.0;
    private static final double TRENCH_MAX_WIDTH = 8.0;
    private static final double TRENCH_MIN_DEPTH = 1.0;
    private static final double TRENCH_MAX_DEPTH = 3.0;

    /**
     * 单列地表采样结果。
     *
     * @param height 地表方块顶部高度
     * @param crater 坑内程度（0 坑外，1 坑底中心）
     * @param rim    坑缘抬起程度（0~1）
     * @param ejecta 抛射物堆积程度（0~1）
     * @param zone   0 孤岛 / 1 环带 / 2 外围（带小数点，用于材质过渡）
     */
    public record Sample(double height, double crater, double rim, double ejecta, double zone) {
    }

    private final HashNoise noise;

    public BattlefieldTerrain(long seed) {
        this.noise = new HashNoise(seed);
    }

    public Sample sample(double x, double z) {
        double r = Math.sqrt(x * x + z * z);
        double angle = Math.atan2(z, x);

        // ---- 1. 三区权重：崖线随角度抖动，过渡带内平滑混合 ----
        double islandEdge = ISLAND_RADIUS
                + angularNoise(7.0, angle) * 14.0;
        double outerEdge = RING_OUTER
                + angularNoise(9.0, angle) * 16.0;

        double islandWeight = 1.0 - smoothstep((r - (islandEdge - CLIFF_WIDTH * 0.5)) / CLIFF_WIDTH);
        double outerWeight = smoothstep((r - (outerEdge - CLIFF_WIDTH * 0.5)) / CLIFF_WIDTH);
        double ringWeight = Math.max(0.0, 1.0 - islandWeight - outerWeight);

        // ---- 2. 域扭曲 + 基底起伏 ----
        double warpX = noise.fbm2(x * 0.0031, z * 0.0031, 3, 2.0, 0.5);
        double warpZ = noise.fbm2(x * 0.0031 + 31.7, z * 0.0031 - 12.3, 3, 2.0, 0.5);
        double wx = x + warpX * 90.0;
        double wz = z + warpZ * 90.0;

        double hills = noise.fbm2(wx * 0.0048, wz * 0.0048, 3, 2.1, 0.5) * 5.0
                + (noise.ridged2(wx * 0.011, wz * 0.011, 3) - 0.45) * 5.0
                + noise.fbm2(wx * 0.045, wz * 0.045, 3, 2.0, 0.5) * 0.8;

        // 孤岛起伏收敛（高原），环带进一步压低，外围保留大部分起伏
        double baseHeight = ISLAND_HEIGHT * islandWeight
                + RING_HEIGHT * ringWeight
                + OUTER_HEIGHT * outerWeight;
        double relief = hills * (0.02 * islandWeight + 0.20 * ringWeight + 0.30 * outerWeight);

        // ---- 3. 弹坑：按区域缩放（孤岛浅、环带重、外围全开） ----
        double bowl = 0.0;
        double rim = 0.0;
        double peak = 0.0;
        double ejecta = 0.0;
        double craterness = 0.0;

        for (CraterLayer layer : LAYERS) {
            double scale = layerScale(layer, islandWeight, ringWeight, outerWeight);
            if (scale <= 0.01) {
                continue;
            }
            long cellX = Math.floorDiv((long) Math.floor(x), (long) layer.cell());
            long cellZ = Math.floorDiv((long) Math.floor(z), (long) layer.cell());
            for (int ix = -1; ix <= 1; ix++) {
                for (int iz = -1; iz <= 1; iz++) {
                    long cx = cellX + ix;
                    long cz = cellZ + iz;
                    if (noise.hash01(cx, cz, layer.salt()) > layer.density()) {
                        continue;
                    }
                    double centerX = cx * (double) layer.cell()
                            + layer.cell() * (0.5 + 0.82 * (noise.hash01(cx, cz, layer.salt() + 1) - 0.5));
                    double centerZ = cz * (double) layer.cell()
                            + layer.cell() * (0.5 + 0.82 * (noise.hash01(cx, cz, layer.salt() + 2) - 0.5));
                    double dx = x - centerX;
                    double dz = z - centerZ;

                    double rot = noise.hash01(cx, cz, layer.salt() + 3) * Math.PI * 2.0;
                    double cos = Math.cos(rot);
                    double sin = Math.sin(rot);
                    double rx = (dx * cos + dz * sin);
                    double rz = (-dx * sin + dz * cos);
                    double stretch = 1.0 + (layer.stretchMax() - 1.0) * noise.hash01(cx, cz, layer.salt() + 4);
                    rx /= stretch;

                    double dist = Math.sqrt(rx * rx + rz * rz);
                    double radius = layer.rMin()
                            + (layer.rMax() - layer.rMin()) * Math.pow(noise.hash01(cx, cz, layer.salt() + 5), 1.25);
                    if (dist > radius * 2.0) {
                        continue;
                    }

                    double pitAngle = Math.atan2(dz, dx);
                    double effR = radius * (1.0 + layer.edge() * angularPerturb(cx, cz, layer.salt() + 6, pitAngle));
                    if (dist > effR * 1.85) {
                        continue;
                    }
                    double depth = (layer.dMin()
                            + (layer.dMax() - layer.dMin()) * Math.pow(noise.hash01(cx, cz, layer.salt() + 8), 0.8)) * scale * 0.55;

                    double u = dist / effR;
                    if (u < 1.0) {
                        double profile = Math.pow(1.0 - u * u, 1.25);
                        bowl = Math.max(bowl, depth * profile);
                        craterness = Math.max(craterness, Math.min(1.0, (1.0 - u) * 1.7) * scale);
                        if (layer.peakMinRadius() > 0.0 && radius >= layer.peakMinRadius() && u < 0.34) {
                            peak = Math.max(peak, depth * 0.20 * Math.pow(1.0 - u / 0.34, 1.7));
                        }
                    } else {
                        double overshoot = u - 1.0;
                        if (overshoot < 0.30) {
                            rim = Math.max(rim, layer.rim() * 0.40 * Math.pow(1.0 - overshoot / 0.30, 2.0) * scale);
                        }
                        double e = overshoot / 0.75;
                        if (e < 1.0) {
                            ejecta += layer.rim() * 0.10 * (1.0 - e) * (1.0 - e) * scale
                                    * (0.55 + 0.45 * noise.noise2(x * 0.07, z * 0.07));
                        }
                    }
                }
            }
        }

        // ---- 4. 轰炸带：只在外围 ----
        if (outerWeight > 0.2) {
            double[] trench = sampleTrenches(x, z, craterness);
            if (trench != null) {
                bowl = Math.max(bowl, trench[0] * outerWeight);
                craterness = Math.max(craterness, Math.min(1.0, trench[1]) * outerWeight);
                rim = Math.max(rim, trench[2] * outerWeight);
            }
        }

        // ---- 5. 合成 ----
        double h = baseHeight + relief - bowl + peak + rim + ejecta;
        h += noise.fbm2(x * 0.33, z * 0.33, 2, 2.0, 0.5) * (0.08 + 0.22 * craterness);

        // 断崖：在过渡带内叠加钟形缺口噪声，让崖壁不是一刀切
        double cliffWeight = bell(islandWeight) + bell(outerWeight);
        if (cliffWeight > 0.01) {
            h += noise.fbm2(x * 0.05 + 77.0, z * 0.05, 3, 2.0, 0.5) * 4.0 * cliffWeight;
            h += noise.ridged2(x * 0.12, z * 0.12, 2) * 2.0 * cliffWeight;
        }

        // 落脚地：孤岛中心一块绝对平整的石台，正好托住城镇 1 的 291×291 地基
        if (r < PAD_RADIUS) {
            h = PAD_HEIGHT;
            craterness = 0.0;
            rim = 0.0;
            ejecta = 0.0;
        } else if (r < PAD_RADIUS + PAD_FADE) {
            double w = 1.0 - smoothstep((r - PAD_RADIUS) / PAD_FADE);
            h += (PAD_HEIGHT - h) * w;
            craterness *= (1.0 - w * 0.7);
            rim *= (1.0 - w * 0.6);
        }

        h = Math.max(-60.0, Math.min(200.0, h));
        return new Sample(h, clamp01(craterness), clamp01(rim * 0.5), clamp01(ejecta * 1.4),
                islandWeight * 1.0 + ringWeight * 2.0);
    }

    public double height(double x, double z) {
        return sample(x, z).height();
    }

    /** 是否在可放残塔的孤岛范围内 */
    public static boolean isTowerSite(int x, int z) {
        return (double) x * x + (double) z * z <= TOWER_RADIUS * TOWER_RADIUS;
    }

    /** 各弹坑层在不同区域的强度 */
    private static double layerScale(CraterLayer layer, double islandW, double ringW, double outerW) {
        if (layer == SMALL) {
            return 0.10 * islandW + 0.75 * ringW + 0.75 * outerW;
        }
        if (layer == MEDIUM) {
            return 0.02 * islandW + 0.75 * ringW + 0.65 * outerW;
        }
        if (layer == LARGE) {
            return 0.0 * islandW + 0.0 * ringW + 0.0 * outerW;
        }
        // GIANT
        return 0.0;
    }

    private double[] sampleTrenches(double x, double z, double craterness) {
        long cellX = Math.floorDiv((long) Math.floor(x), (long) TRENCH_CELL);
        long cellZ = Math.floorDiv((long) Math.floor(z), (long) TRENCH_CELL);
        for (int ix = -1; ix <= 1; ix++) {
            for (int iz = -1; iz <= 1; iz++) {
                long cx = cellX + ix;
                long cz = cellZ + iz;
                if (noise.hash01(cx, cz, 900L) > TRENCH_DENSITY) {
                    continue;
                }
                double startX = cx * (double) TRENCH_CELL
                        + TRENCH_CELL * (0.15 + 0.7 * noise.hash01(cx, cz, 901L));
                double startZ = cz * (double) TRENCH_CELL
                        + TRENCH_CELL * (0.15 + 0.7 * noise.hash01(cx, cz, 902L));
                double dir = noise.hash01(cx, cz, 903L) * Math.PI;
                double length = TRENCH_MIN_LEN + (TRENCH_MAX_LEN - TRENCH_MIN_LEN) * noise.hash01(cx, cz, 904L);
                double endX = startX + Math.cos(dir) * length;
                double endZ = startZ + Math.sin(dir) * length;
                double width = TRENCH_MIN_WIDTH
                        + (TRENCH_MAX_WIDTH - TRENCH_MIN_WIDTH) * noise.hash01(cx, cz, 905L);

                double dist = distanceToSegment(x, z, startX, startZ, endX, endZ);
                if (dist > width * 2.2) {
                    continue;
                }
                double wobble = 0.75 + 0.5 * noise.fbm2(x * 0.05, z * 0.05, 2, 2.0, 0.5);
                double u = dist / (width * wobble);
                double depth = TRENCH_MIN_DEPTH
                        + (TRENCH_MAX_DEPTH - TRENCH_MIN_DEPTH) * noise.hash01(cx, cz, 906L);
                if (u < 1.0) {
                    return new double[]{depth * Math.pow(1.0 - u * u, 0.9),
                            Math.min(1.0, (1.0 - u) * 1.5 + craterness * 0.3), 0.0};
                }
                double overshoot = (u - 1.0) / 1.2;
                if (overshoot < 1.0) {
                    return new double[]{0.0, craterness, 0.8 * (1.0 - overshoot) * (1.0 - overshoot)};
                }
            }
        }
        return null;
    }

    private static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;
        double lenSq = abx * abx + abz * abz;
        double t = lenSq <= 1.0e-9 ? 0.0 : (apx * abx + apz * abz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = px - (ax + abx * t);
        double dz = pz - (az + abz * t);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 角度扰动：8 个哈希方向插值，保证连续 */
    private double angularPerturb(long cx, long cz, long salt, double angle) {
        double a = (angle + Math.PI) / (Math.PI * 2.0) * 8.0;
        int i0 = (int) Math.floor(a);
        double t = a - i0;
        double v0 = noise.hashSigned(cx, cz, salt + (i0 & 7));
        double v1 = noise.hashSigned(cx, cz, salt + ((i0 + 1) & 7));
        double s = t * t * (3.0 - 2.0 * t);
        return v0 + (v1 - v0) * s;
    }

    /** 只依赖角度的连续噪声（用于崖线抖动） */
    private double angularNoise(double radius, double angle) {
        double a = (angle + Math.PI) / (Math.PI * 2.0) * 10.0;
        int i0 = (int) Math.floor(a);
        double t = a - i0;
        int i1 = Math.floorMod(i0, 10);
        int i2 = Math.floorMod(i0 + 1, 10);
        double v0 = noise.hashSigned(i1, 1234L, 77L);
        double v1 = noise.hashSigned(i2, 1234L, 77L);
        double s = t * t * (3.0 - 2.0 * t);
        return v0 + (v1 - v0) * s;
    }

    private static double bell(double weight) {
        return Math.sin(Math.PI * Math.max(0.0, Math.min(1.0, weight)));
    }

    public static double smoothstep(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }
}
