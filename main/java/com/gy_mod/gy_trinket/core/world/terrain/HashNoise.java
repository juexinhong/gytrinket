package com.gy_mod.gy_trinket.core.world.terrain;

/**
 * 纯 Java 确定性噪声（不依赖 Minecraft 的 NormalNoise）。
 * <p>
 * 用梯度噪声 + fBm 组合，好处是同一个种子在任何环境（游戏内 / 离线预览工具）里
 * 都能算出完全一致的结果，方便离线渲染调参，也不会被 RandomState 的噪声实现细节影响。
 */
public final class HashNoise {

    /** 8 个二维梯度方向（单位向量 x2，避免开方） */
    private static final double[] GX = {1, -1, 0, 0, 0.7071, -0.7071, 0.7071, -0.7071};
    private static final double[] GZ = {0, 0, 1, -1, 0.7071, 0.7071, -0.7071, -0.7071};

    private final long seed;

    public HashNoise(long seed) {
        this.seed = seed;
    }

    /** 三次哈希混合（murmur3 finalizer），保证相邻整数格点输出差异充分 */
    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /** 确定性格点哈希 → [0,1) */
    public double hash01(long a, long b, long salt) {
        long h = mix(a * 341873128712L ^ (b * 132897987541L) ^ ((salt + seed) * 2654435761L));
        return (h >>> 11) / 9007199254740992.0;
    }

    /** 确定性格点哈希 → [-1,1) */
    public double hashSigned(long a, long b, long salt) {
        return hash01(a, b, salt) * 2.0 - 1.0;
    }

    /** 三维整数哈希 → [0,1)，用于残塔的块级侵蚀/缺口 */
    public double hash3(int x, int y, int z, long salt) {
        long h = mix(x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ ((salt + seed) * 2654435761L));
        return (h >>> 11) / 9007199254740992.0;
    }

    /** 单层二维梯度噪声，输出约 [-1,1] */
    public double noise2(double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;

        double n00 = dot(x0, z0, fx, fz);
        double n10 = dot(x0 + 1, z0, fx - 1.0, fz);
        double n01 = dot(x0, z0 + 1, fx, fz - 1.0);
        double n11 = dot(x0 + 1, z0 + 1, fx - 1.0, fz - 1.0);

        double u = fade(fx);
        double v = fade(fz);
        double a = n00 + (n10 - n00) * u;
        double b = n01 + (n11 - n01) * u;
        return (a + (b - a) * v) * 1.41;
    }

    private double dot(int ix, int iz, double dx, double dz) {
        int g = (int) (hash01(ix, iz, 7L) * 8.0) & 7;
        return GX[g] * dx + GZ[g] * dz;
    }

    /** fBm：多层噪声叠加，输出约 [-1,1] */
    public double fbm2(double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double fx = x;
        double fz = z;
        for (int i = 0; i < octaves; i++) {
            sum += noise2(fx, fz) * amp;
            norm += amp;
            amp *= gain;
            fx *= lacunarity;
            fz *= lacunarity;
        }
        return norm == 0.0 ? 0.0 : sum / norm;
    }

    /** 脊状噪声：0 平缓、1 尖脊，用于炸裂状山脊与坑缘凹凸 */
    public double ridged2(double x, double z, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double norm = 0.0;
        double fx = x;
        double fz = z;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise2(fx, fz));
            sum += n * n * amp;
            norm += amp;
            amp *= 0.5;
            fx *= 2.0;
            fz *= 2.0;
        }
        return norm == 0.0 ? 0.0 : sum / norm;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}
