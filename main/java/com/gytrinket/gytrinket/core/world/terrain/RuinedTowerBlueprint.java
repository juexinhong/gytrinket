package com.gytrinket.gytrinket.core.world.terrain;

/**
 * 奥术高塔（手工定稿，无随机）。
 * <p>
 * 建筑词汇在这里被显式实现，而不是靠"圆柱 + 开洞"：
 * <ul>
 *   <li>壁柱：2 格宽的竖向凸起，顶端有柱头；</li>
 *   <li>窗套：凹进 2 格，深色内衬 + 窗框 + 拱顶；</li>
 *   <li>敞廊：柱 + 半圆拱 + 拱肩；</li>
 *   <li>栏杆：立柱 + 扶手；</li>
 *   <li>檐口：三层出挑 + 金色压顶 + 托檐；</li>
 *   <li>石带：每 8 格一道石带 + 金线。</li>
 * </ul>
 * 层高：基座 0~4，A 段 4~58，B 段（敞廊）62~104，C 段 108~146，
 * 顶层柱廊 150~184，冠部 184~204，尖刺与宝珠到 250。
 */
public final class RuinedTowerBlueprint {

    public enum Material {
        WHITE_BRICK, WHITE_BLOCK, WHITE_SMOOTH, WHITE_POLISHED, WHITE_STONE,
        WHITE_TRIM, PILLAR, STONE_BRICK, SMOOTH_STONE, GILDED, GLOW, CRYSTAL,
        RUNE, GLASS, BARS, RUBBLE, DARK, DARK_STAIR, ROOF, ROOF_STAIR, WOOD, LEAVES, PANE, STAIR, FENCE, TRAPDOOR, SLAB, CARPET, LANTERN, AIR
    }

    public interface Sink {
        void set(int x, int y, int z, Material material);
    }

    private static final int A0 = 4, A1 = 54;
    private static final int B0 = 58, B1 = 96;
    private static final int C0 = 100, C1 = 134;
    private static final int D0 = 138, D1 = 168;
    private static final int CROWN1 = 186;
    private static final int ORB_Y = 210;
    private static final int TOP = 232;

    private static final double RA = 26.0;
    private static final double RB = 23.0;
    private static final double RC = 19.0;
    private static final double RD = 17.0;

    private static final int BAYS_A = 24;
    private static final int BAYS_B = 24;
    private static final int BAYS_C = 24;
    private static final int COLUMNS_D = 20;

    /** 运行时可调参数（控制台 build 命令用） */
    public int overrideHeight = 0;
    public int overrideWidth = 0;

    public RuinedTowerBlueprint(long seed) {
    }

    public int baseRadius() {
        return 74;
    }

    public int totalHeight() {
        return TOP + 8;
    }

    public int footprint() {
        return 80;
    }

    /** 是否连同自带的程序城一起建（命令 buildtower 会关掉） */
    public boolean includeCity = true;

    // ==================================================================
    public void build(Sink sink) {
        buildPlinth(sink);
        buildTierA(sink);
        buildTierB(sink);
        buildTierC(sink);
        buildColonnade(sink);
        buildCrown(sink);
        buildSpires(sink);
        buildOrb(sink);
        if (includeCity) {
            buildCity(sink);
        }
    }

    // ------------------------------------------------------------------
    private void buildPlinth(Sink sink) {
        for (int step = 0; step < 3; step++) {
            double r = 34.0 - step * 2.5;
            int y = -6 + step * 2;
            annulus(sink, y, r - 2.0, r, step == 2 ? Material.WHITE_TRIM : Material.WHITE_STONE);
            annulus(sink, y + 1, r - 0.8, r, Material.WHITE_POLISHED);
        }
        disc(sink, 1, 28.5, Material.SMOOTH_STONE, Material.WHITE_SMOOTH);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0 + Math.PI / 8.0;
            archOpening(sink, a, 26.0, 1, 8, 3.4, 4);
        }
    }

    // ------------------------------------------------------------------
    private void buildTierA(Sink sink) {
        shell(sink, A0, 30, RA, 4.0, (dx, y, dz, d, a) -> {
            if (isPilaster(a, RA, BAYS_A, 1.1)) {
                return Material.WHITE_BLOCK;
            }
            if (windowBay(y, a, RA, 14, 24, 40, 10)) {
                return d > RA - 2.0 ? null : Material.DARK;
            }
            if (y % 6 == 0) {
                return Material.STONE_BRICK;
            }
            if (y % 8 == 1) {
                return Material.GILDED;
            }
            return Material.WHITE_BRICK;
        });
        for (int i = 0; i < BAYS_A; i++) {
            double a = i * Math.PI * 2.0 / BAYS_A;
            for (int y = A1 - 3; y <= A1 - 1; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        sink.set((int) Math.round(Math.cos(a) * RA) + dx, y,
                                (int) Math.round(Math.sin(a) * RA) + dz, Material.WHITE_TRIM);
                    }
                }
            }
        }
        buildTierAArcade(sink);
        cornice(sink, A1, RA, 7.0);
        balustrade(sink, A1 + 4, RA + 6.2, 90);
    }

    /** 第一段上层：敞廊（内墙 + 24 柱 + 柱间拱） */
    private void buildTierAArcade(Sink sink) {
        // 内墙
        shell(sink, 31, A1, RA - 3.4, 2.4, (dx, y, dz, d, a) ->
                windowBay(y, a, RA - 3.4, 34, 46, 30, 30) ? Material.DARK : Material.WHITE_BRICK);
        // 廊下地面
        annulus(sink, 31, RA - 3.6, RA, Material.WHITE_SMOOTH);
        // 24 根柱
        for (int i = 0; i < BAYS_A; i++) {
            double a = i * Math.PI * 2.0 / BAYS_A;
            column(sink, a, RA, 32, A1 - 8, 2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = (int) Math.round(Math.cos(a) * RA) + dx;
                    int z = (int) Math.round(Math.sin(a) * RA) + dz;
                    sink.set(x, A1 - 8, z, Material.WHITE_TRIM);
                    sink.set(x, 32, z, Material.WHITE_TRIM);
                }
            }
        }
        // 柱间拱
        for (int i = 0; i < BAYS_A; i++) {
            double a0 = i * Math.PI * 2.0 / BAYS_A;
            double a1 = (i + 1) * Math.PI * 2.0 / BAYS_A;
            arcBetween(sink, a0, a1, RA, A1 - 14, 6, 2);
        }
    }
    // ------------------------------------------------------------------
    private void buildTierB(Sink sink) {
        shell(sink, B0, B1, RB - 4.0, 2.4, (dx, y, dz, d, a) ->
                windowBay(y, a, RB - 4.0, 10, 20, 34, 10) ? Material.DARK : Material.WHITE_BRICK);
        double r = RB;
        for (int i = 0; i < BAYS_B; i++) {
            double a = i * Math.PI * 2.0 / BAYS_B;
            column(sink, a, r, B0, B1 - 3, 2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    sink.set((int) Math.round(Math.cos(a) * r) + dx, B1 - 3,
                            (int) Math.round(Math.sin(a) * r) + dz, Material.WHITE_TRIM);
                }
            }
        }
        for (int i = 0; i < BAYS_B; i++) {
            double a0 = i * Math.PI * 2.0 / BAYS_B;
            double a1 = (i + 1) * Math.PI * 2.0 / BAYS_B;
            arcBetween(sink, a0, a1, r, B1 - 10, 7, 2);
        }
        for (int y = B1 - 2; y <= B1; y++) {
            annulus(sink, y, RB - 1.0, RB + 1.2, y == B1 ? Material.GILDED : Material.WHITE_TRIM);
        }
        cornice(sink, B1, RB, 6.0);
        balustrade(sink, B1 + 4, RB + 5.2, 80);
    }

    // ------------------------------------------------------------------
    private void buildTierC(Sink sink) {
        shell(sink, C0, C1, RC, 3.4, (dx, y, dz, d, a) -> {
            if (isPilaster(a, RC, BAYS_C, 1.0)) {
                return Material.WHITE_BLOCK;
            }
            if (windowBay(y, a, RC, 12, 22, 34, 10)) {
                return d > RC - 1.8 ? null : Material.DARK;
            }
            if (y % 6 == 0) {
                return Material.STONE_BRICK;
            }
            if (y % 8 == 1) {
                return Material.GILDED;
            }
            return Material.WHITE_BRICK;
        });
        cornice(sink, C1, RC, 6.0);
        balustrade(sink, C1 + 4, RC + 5.0, 70);
    }

    // ------------------------------------------------------------------
    private void buildColonnade(Sink sink) {
        for (int i = 0; i < COLUMNS_D; i++) {
            double a = i * Math.PI * 2.0 / COLUMNS_D;
            column(sink, a, RD, D0, D1 - 6, 2);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = (int) Math.round(Math.cos(a) * RD) + dx;
                    int z = (int) Math.round(Math.sin(a) * RD) + dz;
                    sink.set(x, D0, z, Material.WHITE_TRIM);
                    sink.set(x, D1 - 6, z, Material.WHITE_TRIM);
                }
            }
        }
        for (int layer = 0; layer < 4; layer++) {
            double rr = RD + 0.6 + layer * 0.8;
            annulus(sink, D1 - 5 + layer, rr - 1.2, rr,
                    layer == 3 ? Material.GILDED : (layer == 0 ? Material.WHITE_BRICK : Material.WHITE_TRIM));
        }
    }

    // ------------------------------------------------------------------
    private void buildCrown(Sink sink) {
        for (int y = D1; y <= CROWN1; y++) {
            double t = (y - D1) / (double) (CROWN1 - D1);
            double r = 16.0 - 9.0 * t;
            annulus(sink, y, Math.max(0.0, r - 2.2), r, (y % 4 == 0) ? Material.WHITE_TRIM : Material.WHITE_BRICK);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0 + Math.PI / 12.0;
            for (int h = 0; h < 8; h++) {
                double rr = 13.0 - h * 0.4;
                sink.set((int) Math.round(Math.cos(a) * rr), CROWN1 + h,
                        (int) Math.round(Math.sin(a) * rr), h == 7 ? Material.GLOW : Material.WHITE_TRIM);
            }
        }
    }

    // ------------------------------------------------------------------
    private void buildSpires(Sink sink) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0 + Math.PI / 12.0;
            for (int h = 0; h < 30; h++) {
                double t = h / 30.0;
                double rr = 11.0 - 9.5 * t;
                int x = (int) Math.round(Math.cos(a) * rr);
                int z = (int) Math.round(Math.sin(a) * rr);
                int y = CROWN1 + 6 + h;
                sink.set(x, y, z, h % 6 == 0 ? Material.GILDED : Material.WHITE_TRIM);
                if (h > 5) {
                    sink.set(x, y, z + 1, Material.WHITE_BRICK);
                }
            }
        }
        for (int h = 0; h < 34; h++) {
            double t = h / 34.0;
            int r = (int) Math.round(3.4 * (1.0 - t));
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        sink.set(dx, CROWN1 + 4 + h, dz,
                                h > 30 ? Material.GLOW : (h % 5 == 0 ? Material.GILDED : Material.WHITE_TRIM));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    private void buildOrb(Sink sink) {
        int r = 18;
        int half = 20;
        for (int dy = -half; dy <= half; dy++) {
            double k = Math.abs(dy) / (double) half;
            double rr = r * Math.pow(1.0 - k, 0.8);
            for (int dx = -(int) rr; dx <= (int) rr; dx++) {
                for (int dz = -(int) rr; dz <= (int) rr; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rr) {
                        continue;
                    }
                    if (d < rr - 1.8) {
                        sink.set(dx, ORB_Y + dy, dz, Material.AIR);
                        continue;
                    }
                    double a = ((Math.atan2(dz, dx) + Math.PI * 4.0) % (Math.PI / 4.0)) - Math.PI / 8.0;
                    sink.set(dx, ORB_Y + dy, dz,
                            Math.abs(a) * Math.max(rr, 1.0) < 1.1 ? Material.CRYSTAL : Material.GLASS);
                }
            }
        }
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= 20) {
                        sink.set(dx, ORB_Y + dy, dz, Math.abs(dy) < 2 ? Material.CRYSTAL : Material.GLOW);
                    }
                }
            }
        }
        // 主环：半径大、粗（径向 3 格、竖向 2 格）
        drawRing(sink, ORB_Y, 28.0, 3, 2, 0.18, true);
        // 附属环：更小更细
        drawRing(sink, ORB_Y - 10, 21.0, 1, 1, 0.10, false);
        drawRing(sink, ORB_Y + 6, 35.0, 1, 1, -0.12, false);
        drawRing(sink, ORB_Y + 14, 41.0, 1, 1, 0.06, false);
    }

    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // 城：三层台地 + 环城建筑 + 外墙与角楼 + 桥
    // ------------------------------------------------------------------
    private void buildCity(Sink sink) {
        buildCityPlateau(sink);
        buildCityWall(sink);
        buildCityHouses(sink);
        buildCityBridges(sink);
    }

    private static double cityFloor(double d) {
        return d <= 46.0 ? 6.0 : 0.0;
    }

    private void buildCityPlateau(Sink sink) {
        // 外圈台地
        annulus(sink, 0, 46.0, 74.0, Material.WHITE_SMOOTH);
        // 内圈台地（高 6 格）
        annulus(sink, 6, 34.0, 46.0, Material.WHITE_SMOOTH);
        // 内圈挡土墙
        for (int i = 0; i < 480; i++) {
            double a = i * Math.PI * 2.0 / 480.0;
            int x = (int) Math.round(Math.cos(a) * 46.0);
            int z = (int) Math.round(Math.sin(a) * 46.0);
            for (int y = -6; y <= 5; y++) {
                sink.set(x, y, z, (y == 5) ? Material.WHITE_TRIM : Material.WHITE_STONE);
            }
        }
        // 外圈挡土墙
        for (int i = 0; i < 480; i++) {
            double a = i * Math.PI * 2.0 / 480.0;
            int x = (int) Math.round(Math.cos(a) * 74.0);
            int z = (int) Math.round(Math.sin(a) * 74.0);
            for (int y = -12; y <= 1; y++) {
                sink.set(x, y, z, (y == 1 || y % 6 == 0) ? Material.WHITE_TRIM : Material.WHITE_STONE);
            }
        }
        // 四条大台阶：外台地 -> 内台地
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI * 0.5;
            for (int s = 0; s <= 8; s++) {
                double r = 44.0 + s * 0.5;
                int y = 6 - s * 6 / 8;
                for (double w = -3.0; w <= 3.0; w += 0.5) {
                    double aa = a + w * 0.008;
                    sink.set((int) Math.round(Math.cos(aa) * r), y,
                            (int) Math.round(Math.sin(aa) * r),
                            Math.abs(w) > 2.0 ? Material.WHITE_TRIM : Material.WHITE_SMOOTH);
                }
            }
        }
        // 环街
        for (double ring : new double[]{40.0, 56.0, 68.0}) {
            int steps = (int) (ring * 8);
            for (int i = 0; i < steps; i++) {
                double a = i * Math.PI * 2.0 / steps;
                for (double w = -1.0; w <= 1.0; w += 0.5) {
                    sink.set((int) Math.round(Math.cos(a) * (ring + w)), (int) cityFloor(ring) + 1,
                            (int) Math.round(Math.sin(a) * (ring + w)), Material.SMOOTH_STONE);
                }
            }
        }
        // 放射大街
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI * 0.5;
            for (double d = 30.0; d <= 72.0; d += 0.5) {
                for (double w = -2.0; w <= 2.0; w += 0.5) {
                    double aa = a + w * 0.008;
                    sink.set((int) Math.round(Math.cos(aa) * d), (int) cityFloor(d) + 1,
                            (int) Math.round(Math.sin(aa) * d),
                            Math.abs(w) > 1.5 ? Material.WHITE_TRIM : Material.SMOOTH_STONE);
                }
            }
        }
    }
    private void buildCityWall(Sink sink) {
        int height = 12;
        for (int i = 0; i < 480; i++) {
            double a = i * Math.PI * 2.0 / 480.0;
            boolean gate = Math.abs(normalizeAngle(a - 0.0)) < 0.10
                    || Math.abs(normalizeAngle(a - Math.PI * 0.5)) < 0.10
                    || Math.abs(normalizeAngle(a - Math.PI)) < 0.10
                    || Math.abs(normalizeAngle(a - Math.PI * 1.5)) < 0.10;
            if (gate) {
                continue;
            }
            int x = (int) Math.round(Math.cos(a) * 73.0);
            int z = (int) Math.round(Math.sin(a) * 73.0);
            for (int y = 1; y <= height; y++) {
                sink.set(x, y, z, (y == height || y == 6) ? Material.WHITE_TRIM : Material.WHITE_STONE);
            }
            if (i % 8 == 0) {
                sink.set(x, height + 1, z, Material.WHITE_TRIM);
            }
            // 内侧步道
            sink.set((int) Math.round(Math.cos(a) * 71.0), height,
                    (int) Math.round(Math.sin(a) * 71.0), Material.WHITE_SMOOTH);
        }
        // 八座角楼
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 0.25 + Math.PI / 8.0;
            buildCityTower(sink, Math.cos(a) * 73.0, Math.sin(a) * 73.0, 5, 24);
        }
        // 四座门楼
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI * 0.5;
            buildCityTower(sink, Math.cos(a) * 73.0, Math.sin(a) * 73.0, 6, 30);
            // 门洞
            for (int w = -3; w <= 3; w++) {
                double aa = a + w * 0.02;
                for (int y = 1; y <= 5; y++) {
                    sink.set((int) Math.round(Math.cos(aa) * 73.0), y,
                            (int) Math.round(Math.sin(aa) * 73.0), Material.AIR);
                }
            }
        }
    }

    private void buildCityTower(Sink sink, double cx, double cz, int radius, int height) {
        for (int y = 1; y <= height; y++) {
            double r = radius * (1.0 - 0.08 * y / height);
            int span = (int) Math.ceil(r) + 1;
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r || d < r - 2.0) {
                        continue;
                    }
                    sink.set((int) Math.round(cx) + dx, y, (int) Math.round(cz) + dz,
                            (y % 8 == 4) ? Material.WHITE_TRIM : Material.WHITE_BRICK);
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2.0 / 12.0;
            if (i % 3 != 0) {
                sink.set((int) Math.round(cx + Math.cos(a) * (radius - 0.5)), height + 1,
                        (int) Math.round(cz + Math.sin(a) * (radius - 0.5)), Material.WHITE_TRIM);
            }
        }
        // 锥顶
        for (int i = 1; i <= radius * 2; i++) {
            double r = Math.max(0.0, (radius - 1.0) * (1.0 - i / (double) (radius * 2)));
            for (int dx = -(int) r; dx <= (int) r; dx++) {
                for (int dz = -(int) r; dz <= (int) r; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= r) {
                        sink.set((int) Math.round(cx) + dx, height + i, (int) Math.round(cz) + dz,
                                Material.STONE_BRICK);
                    }
                }
            }
        }
        sink.set((int) Math.round(cx), height + radius * 2 + 1, (int) Math.round(cz), Material.GLOW);
    }

    private void buildCityHouses(Sink sink) {
        // 8 个扇区，每扇区沿径向 3 栋，角度居中于放射大街之间
        for (int sector = 0; sector < 8; sector++) {
            double a = sector * Math.PI / 4.0 + Math.PI / 8.0;
            for (int row = 0; row < 3; row++) {
                double dist = 38.0 + row * 12.0;
                int variant = (sector + row) % 3;
                buildTownHouse(sink, Math.cos(a) * dist, Math.sin(a) * dist, a, variant);
            }
        }
    }

    /** 三栋样品房，供近距离观察（不参与结构生成） */
    public void buildSamples(Sink sink) {
        buildCottage(sink, 0, -30);
        buildCottage(sink, 0, 0);
        // 标记柱：便于截图时对准样品区原点（局部坐标 0,0）
        for (int y = 0; y <= 12; y++) {
            sink.set(0, y, 40, Material.GLOW);
        }
    }

    /**
     * 一栋可进入的民居。
     *
     * @param variant 0 平房 / 1 两层楼 / 2 塔屋
     */
    // ==================================================================
    // 民居：统一白色系 + 深瓦屋顶 + 浅木点缀（四个变体）
    // ==================================================================
    private void buildTownHouse(Sink sink, double cx, double cz, double facing, int variant) {
        if (variant == 3) {
            buildRoundHouse(sink, cx, cz, 16, 14);
            return;
        }
        int w = overrideWidth > 0 ? overrideWidth : (variant == 2 ? 8 : 6);
        int d = variant == 1 ? 9 : 8;
        int h = overrideHeight > 0 ? overrideHeight : (variant == 1 ? 15 : (variant == 2 ? 12 : 10));
        double dirX = Math.cos(facing);
        double dirZ = Math.sin(facing);
        double sideX = -dirZ;
        double sideZ = dirX;

        // 台基（比墙宽 1）
        for (int along = -d - 1; along <= d + 1; along++) {
            for (int across = -w - 1; across <= w + 1; across++) {
                int x = (int) Math.round(cx + dirX * along + sideX * across);
                int z = (int) Math.round(cz + dirZ * along + sideZ * across);
                sink.set(x, 0, z, Material.WHITE_POLISHED);
                sink.set(x, 1, z, Material.STONE_BRICK);
            }
        }
        for (int along = -d; along <= d; along++) {
            for (int across = -w; across <= w; across++) {
                int x = (int) Math.round(cx + dirX * along + sideX * across);
                int z = (int) Math.round(cz + dirZ * along + sideZ * across);
                boolean edge = Math.abs(across) == w || Math.abs(along) == d;
                if (!edge) {
                    for (int y = 2; y < h; y++) {
                        sink.set(x, y, z, Material.AIR);
                    }
                    continue;
                }
                for (int y = 2; y <= h; y++) {
                    // 门洞
                    if (along == d && Math.abs(across) <= 1 && y <= 6) {
                        sink.set(x, y, z, Material.AIR);
                        continue;
                    }
                    // 门框
                    if (along == d && Math.abs(across) == 2 && y <= 7) {
                        sink.set(x, y, z, Material.WOOD);
                        continue;
                    }
                    // 转角壁柱
                    if (Math.abs(across) == w && Math.abs(along) == d) {
                        sink.set(x, y, z, Material.WHITE_POLISHED);
                        continue;
                    }
                    // 窗：3 宽 x 4 高，两排
                    int cell = Math.abs(Math.abs(across) == w ? along : across);
                    boolean winCol = (cell >= 2 && cell <= 4) || (cell >= 6 && cell <= 8);
                    boolean face = (along == d && Math.abs(across) > 2) || (Math.abs(across) == w && Math.abs(along) < d - 2);
                    boolean winRow = (y >= 4 && y <= 7 && !(variant == 1)) || (variant == 1 && ((y >= 4 && y <= 7) || (y >= 11 && y <= 14)));
                    if (winCol && face && winRow) {
                        sink.set(x, y, z, Material.PANE);
                        continue;
                    }
                    if (winCol && face && (y == 3 || y == 8 || (variant == 1 && (y == 10 || y == 15)))) {
                        sink.set(x, y, z, Material.SLAB);
                        continue;
                    }
                    sink.set(x, y, z, Material.WHITE_SMOOTH);
                }
            }
        }

        // 门前台阶
        for (int across = -1; across <= 1; across++) {
            int x = (int) Math.round(cx + dirX * (d + 1) + sideX * across);
            int z = (int) Math.round(cz + dirZ * (d + 1) + sideZ * across);
            sink.set(x, 1, z, Material.WHITE_STONE);
        }
        // 门廊：细栅栏柱 + 薄顶
        for (int side = -1; side <= 1; side += 2) {
            int x = (int) Math.round(cx + dirX * (d + 2) + sideX * (side * 2));
            int z = (int) Math.round(cz + dirZ * (d + 2) + sideZ * (side * 2));
            for (int y = 2; y <= 7; y++) {
                sink.set(x, y, z, Material.FENCE);
            }
        }
        for (int across = -2; across <= 2; across++) {
            int x = (int) Math.round(cx + dirX * (d + 2) + sideX * across);
            int z = (int) Math.round(cz + dirZ * (d + 2) + sideZ * across);
            sink.set(x, 8, z, Material.SLAB);
        }
        // 窗下花箱
        for (int across = -w + 1; across <= w - 1; across++) {
            if (Math.abs(across) % 3 != 0) {
                continue;
            }
            int x = (int) Math.round(cx + dirX * (d + 1) + sideX * across);
            int z = (int) Math.round(cz + dirZ * (d + 1) + sideZ * across);
            sink.set(x, 3, z, Material.WHITE_STONE);
            sink.set(x, 4, z, Material.LEAVES);
        }

        // 双坡屋顶
        for (int along = -d - 1; along <= d + 1; along++) {
            for (int across = -(w + 1); across <= w + 1; across++) {
                int x = (int) Math.round(cx + dirX * along + sideX * across);
                int z = (int) Math.round(cz + dirZ * along + sideZ * across);
                int top = h + 1 + Math.round((w + 1 - Math.abs(across)) * 0.55f);
                for (int y = h + 1; y <= top; y++) {
                    sink.set(x, y, z, (Math.abs(across) <= 1 && y == top) ? Material.WHITE_TRIM : Material.DARK);
                }
                if (Math.abs(across) == w + 1) {
                    sink.set(x, h + 1, z, Material.STAIR);
                }
            }
        }
        // 山墙：白色三角 + 封檐板 + 小窗
        for (int across = -(w + 1); across <= w + 1; across++) {
            int top = h + 1 + Math.round((w + 1 - Math.abs(across)) * 0.55f);
            for (int e = -1; e <= 1; e += 2) {
                int along = e * (d + 1);
                int x = (int) Math.round(cx + dirX * along + sideX * across);
                int z = (int) Math.round(cz + dirZ * along + sideZ * across);
                for (int y = h + 1; y <= top; y++) {
                    boolean board = (Math.abs(across) == w + 1 || Math.abs(across) == w || Math.abs(across) <= 1);
                    sink.set(x, y, z, board && y == top ? Material.WOOD : Material.WHITE_SMOOTH);
                }
                if (Math.abs(across) <= 1 && Math.abs(across) >= 0) {
                    sink.set(x, h + 2, z, Material.PANE);
                }
            }
        }
        // 烟囱
        int chX = (int) Math.round(cx - dirX * (d - 3) + sideX * (w - 3));
        int chZ = (int) Math.round(cz - dirZ * (d - 3) + sideZ * (w - 3));
        for (int y = h + 1; y <= h + 6; y++) {
            sink.set(chX, y, chZ, Material.WHITE_STONE);
        }
        sink.set(chX, h + 7, chZ, Material.SLAB);

        // ==== 室内 ====
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                if (Math.abs(a) + Math.abs(b) <= 2) {
                    sink.set((int) Math.round(cx + dirX * (a + 2) + sideX * b), 2,
                            (int) Math.round(cz + dirZ * (a + 2) + sideZ * b), Material.CARPET);
                }
            }
        }
        int tx = (int) Math.round(cx - dirX * 3);
        int tz = (int) Math.round(cz - dirZ * 3);
        sink.set(tx, 2, tz, Material.FENCE);
        sink.set(tx, 3, tz, Material.SLAB);
        sink.set((int) Math.round(cx - dirX * 3 + sideX * 2), 2,
                (int) Math.round(cz - dirZ * 3 + sideZ * 2), Material.STAIR);
        sink.set((int) Math.round(cx - dirX * 3 - sideX * 2), 2,
                (int) Math.round(cz - dirZ * 3 - sideZ * 2), Material.STAIR);
        sink.set((int) Math.round(cx - dirX * (d - 2)), 4,
                (int) Math.round(cz - dirZ * (d - 2)), Material.TRAPDOOR);
        sink.set((int) Math.round(cx), h - 2, (int) Math.round(cz), Material.LANTERN);
        if (variant == 1) {
            for (int a = -d + 1; a <= d - 1; a++) {
                for (int b = -w + 1; b <= w - 1; b++) {
                    if (Math.abs(a - (d - 3)) <= 1 && Math.abs(b) <= 1) {
                        continue;
                    }
                    sink.set((int) Math.round(cx + dirX * a + sideX * b), 9,
                            (int) Math.round(cz + dirZ * a + sideZ * b), Material.WHITE_SMOOTH);
                }
            }
            // 阳台栏杆
            for (int across = -w + 1; across <= w - 1; across++) {
                int x = (int) Math.round(cx + dirX * (d + 1) + sideX * across);
                int z = (int) Math.round(cz + dirZ * (d + 1) + sideZ * across);
                sink.set(x, 10, z, Material.FENCE);
            }
        }
    }
    /** 长方体填充（世界坐标，手工用） */
    private void box(Sink sink, int x0, int z0, int x1, int z1, int y0, int y1, Material m) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int y = y0; y <= y1; y++) {
                    sink.set(x, y, z, m);
                }
            }
        }
    }

    /**
     * 手工定稿的cottage：13 宽 x 9 深 x 9 高。
     * 所有尺寸都是写死的常量，改这个方法的数字就能改房子。
     */
    /** 公开入口：在局部原点砌一个变体（供客户端控制台实时调用） */
    public void placeHouse(int variant, Sink sink) {
        if (variant == 3) {
            buildRoundHouse(sink, 0, 0, 16, 14);
        } else {
            buildTownHouse(sink, 0, 0, 0, Math.min(variant, 2));
        }
    }

    public void buildCottage(Sink sink, int cx, int cz) {
        int x0 = cx - 8, x1 = cx + 8, z0 = cz - 5, z1 = cz + 5;
        // 台基（外扩 1）
        box(sink, x0 - 1, z0 - 1, x1 + 1, z1 + 1, 0, 0, Material.WHITE_POLISHED);
        box(sink, x0 - 1, z0 - 1, x1 + 1, z1 + 1, 1, 1, Material.STONE_BRICK);
        // 墙体
        box(sink, x0, z0, x1, z0, 2, 12, Material.WHITE_SMOOTH);
        box(sink, x0, z1, x1, z1, 2, 12, Material.WHITE_SMOOTH);
        box(sink, x0, z0, x0, z1, 2, 12, Material.WHITE_SMOOTH);
        box(sink, x1, z0, x1, z1, 2, 12, Material.WHITE_SMOOTH);
        // 转角壁柱
        for (int x : new int[]{x0, x1}) {
            for (int z : new int[]{z0, z1}) {
                box(sink, x, z, x, z, 2, 12, Material.WHITE_POLISHED);
            }
        }
        // 门 + 门框 + 台阶
        box(sink, cx - 1, z1, cx, z1, 2, 5, Material.AIR);
        box(sink, cx - 2, z1, cx - 2, z1, 2, 6, Material.WOOD);
        box(sink, cx + 1, z1, cx + 1, z1, 2, 6, Material.WOOD);
        box(sink, cx - 2, z1 + 1, cx + 1, z1 + 1, 1, 1, Material.WHITE_STONE);
        // 门廊：雨棚 + 两根细柱
        box(sink, cx - 3, z1 + 2, cx + 2, z1 + 3, 7, 7, Material.WHITE_TRIM);
        for (int px : new int[]{cx - 3, cx + 2}) {
            box(sink, px, z1 + 3, px, z1 + 3, 2, 6, Material.FENCE);
        }
        // 正面窗（带深色框）
        for (int wx : new int[]{cx - 6, cx + 4}) {
            box(sink, wx, z1, wx + 1, z1, 4, 6, Material.GLASS);
            box(sink, wx - 1, z1, wx - 1, z1, 4, 6, Material.WOOD);
            box(sink, wx + 2, z1, wx + 2, z1, 4, 6, Material.WOOD);
            box(sink, wx - 1, z1, wx + 2, z1, 3, 3, Material.SLAB);
            box(sink, wx - 1, z1, wx + 2, z1, 7, 7, Material.WOOD);
        }
        for (int wx : new int[]{cx - 6, cx + 4}) {
            box(sink, wx, z1, wx + 1, z1, 9, 11, Material.GLASS);
            box(sink, wx - 1, z1, wx + 2, z1, 8, 8, Material.SLAB);
        }
        // 侧窗
        for (int sideX : new int[]{x0, x1}) {
            box(sink, sideX, cz - 3, sideX, cz - 2, 4, 6, Material.GLASS);
            box(sink, sideX, cz - 4, sideX, cz - 1, 3, 3, Material.SLAB);
            box(sink, sideX, cz - 4, sideX, cz - 1, 7, 7, Material.WOOD);
        }
        // 腰线
        box(sink, x0 - 1, z0 - 1, x1 + 1, z0 - 1, 12, 12, Material.WHITE_TRIM);
        box(sink, x0 - 1, z1 + 1, x1 + 1, z1 + 1, 12, 12, Material.WHITE_TRIM);
        box(sink, x0 - 1, z0 - 1, x0 - 1, z1 + 1, 12, 12, Material.WHITE_TRIM);
        box(sink, x1 + 1, z0 - 1, x1 + 1, z1 + 1, 12, 12, Material.WHITE_TRIM);
        // 双坡屋顶（楼梯做坡面，每层收 1）
        // 屋面：只铺两片坡面（空腔），每层内收 1、抬高 1
        for (int i = 0; i < 6; i++) {
            int zz0 = z0 - 2 + i;
            int zz1 = z1 + 2 - i;
            int y = 13 + i;
            box(sink, x0 - 1, zz0, x1 + 1, zz0, y, y, Material.ROOF);
            box(sink, x0 - 1, zz1, x1 + 1, zz1, y, y, Material.ROOF);
            box(sink, x0 - 1, zz0 - 1, x1 + 1, zz0 - 1, y, y, Material.ROOF_STAIR);
            box(sink, x0 - 1, zz1 + 1, x1 + 1, zz1 + 1, y, y, Material.ROOF_STAIR);
        }
        // 山墙封口
        for (int i = 0; i < 6; i++) {
            int zz0 = z0 - 2 + i;
            int zz1 = z1 + 2 - i;
            int y = 13 + i;
            for (int z = zz0; z <= zz1; z++) {
                sink.set(x0 - 1, y, z, (y >= 14 && y <= 16 && Math.abs(z - cz) <= 1) ? Material.GLASS : Material.WHITE_SMOOTH);
                sink.set(x1 + 1, y, z, (y >= 14 && y <= 16 && Math.abs(z - cz) <= 1) ? Material.GLASS : Material.WHITE_SMOOTH);
            }
        }
        // 屋脊
        box(sink, x0 - 1, cz, x1 + 1, cz, 19, 19, Material.ROOF);
        box(sink, x0, cz, x1, cz, 20, 20, Material.ROOF_STAIR);
        // 烟囱
        box(sink, x1 - 2, z0 + 1, x1 - 2, z0 + 1, 13, 18, Material.WHITE_STONE);
        sink.set(x1 - 2, 19, z0 + 1, Material.SLAB);
        // 室内
        box(sink, x0 + 2, z0 + 2, x1 - 2, z1 - 2, 2, 2, Material.CARPET);
        sink.set(x0 + 3, 3, z0 + 2, Material.FENCE);
        sink.set(x0 + 3, 4, z0 + 2, Material.SLAB);
        sink.set(x0 + 5, 2, z0 + 2, Material.STAIR);
        sink.set(x0 + 7, 2, z0 + 2, Material.STAIR);
        sink.set(cx, 8, cz, Material.LANTERN);
        // 通向二层的梯子（用楼梯块堆）
        for (int i = 0; i < 6; i++) {
            sink.set(x0 + 2, 2 + i, cz + 2 - i, Material.STAIR);
        }
    }
    /** 局部坐标填充 */
    private void fillLocal(Sink sink, double cx, double cz, double dirX, double dirZ,
                           double sideX, double sideZ, int u0, int u1, int v0, int v1,
                           int y0, int y1, Material m) {
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                for (int y = y0; y <= y1; y++) {
                    sink.set((int) Math.round(cx + dirX * u + sideX * v), y,
                            (int) Math.round(cz + dirZ * u + sideZ * v), m);
                }
            }
        }
    }

    /** 一个矩形体块：外墙 + 窗 + 阶梯顶 */
    private void room(Sink sink, double cx, double cz, double dirX, double dirZ,
                      double sideX, double sideZ, int u0, int u1, int v0, int v1, int h) {
        for (int u = u0; u <= u1; u++) {
            for (int v = v0; v <= v1; v++) {
                boolean edge = u == u0 || u == u1 || v == v0 || v == v1;
                int x = (int) Math.round(cx + dirX * u + sideX * v);
                int z = (int) Math.round(cz + dirZ * u + sideZ * v);
                if (!edge) {
                    for (int y = 1; y < h; y++) {
                        sink.set(x, y, z, Material.AIR);
                    }
                    continue;
                }
                for (int y = 1; y <= h; y++) {
                    boolean win = ((y >= 4 && y <= 6) || (y >= 9 && y <= 11)) && (Math.abs(u + v) % 4 == 0);
                    if (win) {
                        sink.set(x, y, z, Material.PANE);
                        continue;
                    }
                    if (y == 3 || y == 7) {
                        sink.set(x, y, z, Material.WHITE_TRIM);
                        continue;
                    }
                    sink.set(x, y, z, y <= 2 ? Material.STONE_BRICK : Material.WHITE_SMOOTH);
                }
                // 双坡顶（沿 u 起脊）
                int vc = (v0 + v1) / 2;
                int roofY = h + Math.max(0, 4 - Math.abs(v - vc));
                for (int y = h + 1; y <= roofY; y++) {
                    sink.set(x, y, z, Material.DARK);
                }
            }
        }
    }

    /** L 形庄园：主楼 + 前翼 + 侧翼 + 角塔 */
    private void buildVilla(Sink sink, double cx, double cz, double facing) {
        double dirX = Math.cos(facing);
        double dirZ = Math.sin(facing);
        double sideX = -dirZ;
        double sideZ = dirX;
        // 基座
        fillLocal(sink, cx, cz, dirX, dirZ, sideX, sideZ, -9, 15, -8, 15, 0, 1, Material.WHITE_POLISHED);
        // 主楼
        room(sink, cx, cz, dirX, dirZ, sideX, sideZ, -9, 5, -8, 6, 13);
        // 前翼
        room(sink, cx, cz, dirX, dirZ, sideX, sideZ, 5, 14, -4, 4, 10);
        // 侧翼
        room(sink, cx, cz, dirX, dirZ, sideX, sideZ, -7, 2, 6, 14, 10);
        // 角塔
        int tx = (int) Math.round(cx + dirX * 13 + sideX * 10);
        int tz = (int) Math.round(cz + dirZ * 13 + sideZ * 10);
        for (int y = 0; y <= 18; y++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    double dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd > 4) {
                        continue;
                    }
                    if (dd < 2.6) {
                        sink.set(tx + dx, y, tz + dz, Material.AIR);
                        continue;
                    }
                    boolean slit = (y >= 6 && y <= 9) && ((dx == 0) || (dz == 0));
                    sink.set(tx + dx, y, tz + dz, slit ? Material.PANE
                            : (y <= 2 ? Material.STONE_BRICK : Material.WHITE_SMOOTH));
                }
            }
        }
        for (int i = 1; i <= 10; i++) {
            double r = 4.6 * (1.0 - i / 10.0);
            for (int dx = -(int) r; dx <= (int) r; dx++) {
                for (int dz = -(int) r; dz <= (int) r; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= r) {
                        sink.set(tx + dx, 18 + i, tz + dz, Material.DARK);
                    }
                }
            }
        }
        // 门廊
        fillLocal(sink, cx, cz, dirX, dirZ, sideX, sideZ, 15, 17, -3, 3, 8, 8, Material.WHITE_TRIM);
        for (int v = -3; v <= 3; v += 6) {
            for (int y = 1; y <= 7; y++) {
                sink.set((int) Math.round(cx + dirX * 16 + sideX * v), y,
                        (int) Math.round(cz + dirZ * 16 + sideZ * v), Material.FENCE);
            }
        }
        // 门洞
        fillLocal(sink, cx, cz, dirX, dirZ, sideX, sideZ, 14, 14, -1, 1, 1, 5, Material.AIR);
    }
    /** 圆塔民居：白色圆身 + 收分锥顶 */
    private void buildRoundHouse(Sink sink, double cx, double cz, int wallH, int coneH) {
        int radius = 7;
        for (int y = 0; y <= wallH; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd > radius) {
                        continue;
                    }
                    int x = (int) Math.round(cx) + dx;
                    int z = (int) Math.round(cz) + dz;
                    if (y == 0) {
                        sink.set(x, 0, z, Material.WHITE_POLISHED);
                        continue;
                    }
                    if (dd < radius - 1.2) {
                        sink.set(x, y, z, Material.AIR);
                        continue;
                    }
                    if (dx > radius - 2 && Math.abs(dz) <= 1 && y <= 4) {
                        sink.set(x, y, z, Material.AIR);
                        continue;
                    }
                    boolean slit = (y >= 5 && y <= 8)
                            && ((Math.abs(dz) == 0 && dx > 0) || (Math.abs(dx) == 0 && dz > 0));
                    sink.set(x, y, z, slit ? Material.PANE
                            : (y <= 2 ? Material.STONE_BRICK : (y % 5 == 0 ? Material.WHITE_TRIM : Material.WHITE_SMOOTH)));
                }
            }
        }
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) <= radius + 1) {
                    sink.set((int) Math.round(cx) + dx, wallH, (int) Math.round(cz) + dz, Material.WHITE_TRIM);
                }
            }
        }
        for (int i = 1; i <= coneH; i++) {
            double r = (radius + 1) * 0.85 * (1.0 - i / (double) coneH);
            for (int dx = -(int) r; dx <= (int) r; dx++) {
                for (int dz = -(int) r; dz <= (int) r; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= r) {
                        sink.set((int) Math.round(cx) + dx, wallH + i, (int) Math.round(cz) + dz, Material.DARK);
                    }
                }
            }
        }
        sink.set((int) Math.round(cx), wallH + coneH + 1, (int) Math.round(cz), Material.LANTERN);
        sink.set((int) Math.round(cx), wallH - 2, (int) Math.round(cz), Material.LANTERN);
    }
    private void buildCityBridges(Sink sink) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 0.25 + Math.PI / 8.0;
            if (i % 2 == 1) {
                continue;
            }
            int cx = (int) Math.round(Math.cos(a) * 73.0);
            int cz = (int) Math.round(Math.sin(a) * 73.0);
            int nx = (int) Math.round(Math.cos(a + Math.PI / 8.0) * 73.0);
            int nz = (int) Math.round(Math.sin(a + Math.PI / 8.0) * 73.0);
            for (int s = 0; s <= 20; s++) {
                double t = s / 20.0;
                int x = (int) Math.round(cx + (nx - cx) * t);
                int z = (int) Math.round(cz + (nz - cz) * t);
                int y = 13 + (int) Math.round(Math.sin(t * Math.PI) * 3.0);
                sink.set(x, y, z, Material.WHITE_SMOOTH);
                if (s % 4 == 0) {
                    sink.set(x, y + 1, z, Material.WHITE_TRIM);
                }
                if (s % 5 == 0) {
                    for (int h = 1; h <= y - 12; h++) {
                        sink.set(x, y - h, z, Material.WHITE_STONE);
                    }
                }
            }
        }
    }
    // ==================================================================
    private interface WallFn {
        Material at(int dx, int y, int dz, double d, double angle);
    }

    private void shell(Sink sink, int y0, int y1, double rOuter, double thickness, WallFn fn) {
        double inner = Math.max(2.0, rOuter - thickness);
        int span = (int) Math.ceil(rOuter) + 1;
        for (int y = y0; y <= y1; y++) {
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > rOuter || d < inner) {
                        continue;
                    }
                    Material m = fn.at(dx, y, dz, d, Math.atan2(dz, dx));
                    if (m != null) {
                        sink.set(dx, y, dz, m);
                    }
                }
            }
        }
    }

    private void annulus(Sink sink, int y, double rInner, double rOuter, Material m) {
        int span = (int) Math.ceil(rOuter);
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= rOuter && d >= rInner) {
                    sink.set(dx, y, dz, m);
                }
            }
        }
    }

    private void disc(Sink sink, int y, double r, Material outer, Material inner) {
        int span = (int) Math.ceil(r);
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r) {
                    sink.set(dx, y, dz, d > r - 2.0 ? outer : inner);
                }
            }
        }
    }

    private void cornice(Sink sink, int y, double rOuter, double overhang) {
        for (int layer = 0; layer < 3; layer++) {
            double r = rOuter + overhang * (layer + 1) / 3.0;
            annulus(sink, y + layer, r - 1.8, r,
                    layer == 2 ? Material.GILDED : (layer == 1 ? Material.WHITE_TRIM : Material.WHITE_BLOCK));
        }
        int count = (int) (rOuter * 4);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2.0 / count;
            sink.set((int) Math.round(Math.cos(a) * (rOuter + overhang * 0.4)), y - 1,
                    (int) Math.round(Math.sin(a) * (rOuter + overhang * 0.4)), Material.WHITE_TRIM);
        }
    }

    private void balustrade(Sink sink, int y, double r, int count) {
        annulus(sink, y, r - 1.2, r, Material.WHITE_SMOOTH);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2.0 / count;
            int x = (int) Math.round(Math.cos(a) * r);
            int z = (int) Math.round(Math.sin(a) * r);
            if (i % 2 == 0) {
                sink.set(x, y + 1, z, Material.WHITE_TRIM);
                sink.set(x, y + 2, z, Material.WHITE_TRIM);
            }
        }
        annulus(sink, y + 2, r - 0.6, r, Material.WHITE_TRIM);
    }

    private void column(Sink sink, double angle, double r, int y0, int y1, int thick) {
        for (int y = y0; y <= y1; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (thick == 2 && Math.abs(dx) + Math.abs(dz) > 1) {
                        continue;
                    }
                    sink.set((int) Math.round(Math.cos(angle) * r) + dx, y,
                            (int) Math.round(Math.sin(angle) * r) + dz,
                            y % 12 == 0 ? Material.GILDED : Material.PILLAR);
                }
            }
        }
    }

    private void arcBetween(Sink sink, double a0, double a1, double r, int yBase, int rise, int thick) {
        int steps = 18;
        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;
            double a = a0 + (a1 - a0) * t;
            int y = yBase + (int) Math.round(Math.sin(t * Math.PI) * rise);
            for (int w = 0; w < thick; w++) {
                sink.set((int) Math.round(Math.cos(a) * (r + w * 0.6)), y,
                        (int) Math.round(Math.sin(a) * (r + w * 0.6)),
                        w == 0 ? Material.WHITE_BRICK : Material.WHITE_TRIM);
            }
        }
    }

    private void archOpening(Sink sink, double angle, double r, int yBottom, int yTop, double halfWidth, int depth) {
        for (double w = -halfWidth; w <= halfWidth; w += 0.5) {
            double a = angle + w * 0.035;
            double limit = yTop - Math.max(0.0, Math.abs(w) - 0.8) * 2.0;
            for (int y = yBottom; y <= (int) limit; y++) {
                for (int d = 0; d < depth; d++) {
                    double rr = r - d;
                    sink.set((int) Math.round(Math.cos(a) * rr), y, (int) Math.round(Math.sin(a) * rr),
                            Material.AIR);
                }
            }
        }
    }

    private boolean isPilaster(double angle, double r, int count, double width) {
        double sector = Math.PI * 2.0 / count;
        double a = ((angle + Math.PI * 8.0) % sector) - sector * 0.5;
        return Math.abs(a) * r < width;
    }

    private boolean windowBay(int y, double angle, double r, int base1, int base2, int height, int height2) {
        for (int k = 0; k < 2; k++) {
            int base = k == 0 ? base1 : base2;
            int h = k == 0 ? height : height2;
            int local = y - base;
            if (local < 0 || local > h) {
                continue;
            }
            int windows = 12;
            for (int i = 0; i < windows; i++) {
                double a = (i + 0.5) * Math.PI * 2.0 / windows;
                double delta = Math.abs(normalizeAngle(angle - a));
                double half = local > h - 3 ? 0.6 : (local > h - 4 ? 1.0 : 1.6);
                if (delta * r < half) {
                    return true;
                }
            }
        }
        return false;
    }

    private void drawRing(Sink sink, int yBase, double radius, int radialThick, int vertThick,
                          double tilt, boolean main) {
        int steps = (int) (radius * 12);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2.0 / steps;
            for (int r = 0; r < radialThick; r++) {
                double rr = radius + r * 0.9;
                double x = Math.cos(a) * rr;
                double z = Math.sin(a) * rr;
                for (int v = 0; v < vertThick; v++) {
                    double y = yBase + v + x * tilt + Math.sin(a * 3.0) * 1.6;
                    Material m = main
                            ? (i % 8 == 0 ? Material.GLOW : Material.CRYSTAL)
                            : (i % 10 == 0 ? Material.GLOW : Material.CRYSTAL);
                    sink.set((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), m);
                }
            }
        }
    }
    private static double normalizeAngle(double a) {
        while (a > Math.PI) {
            a -= Math.PI * 2.0;
        }
        while (a < -Math.PI) {
            a += Math.PI * 2.0;
        }
        return a;
    }
}
