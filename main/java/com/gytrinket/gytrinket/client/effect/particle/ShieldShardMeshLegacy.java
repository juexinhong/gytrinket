package com.gytrinket.gytrinket.client.effect.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 旧版「立体宝石」网格（git 版本的原样几何，供 config 的 style = 1 使用）。
 * <p>
 * 外形：尖顶正六边形轮廓沿球面法线挤出，带 0.06 格厚度；正/背面切成
 * 三圈微起伏环带 + 中央台面，共 84 个独立三角切面，外轮廓仍是六边形。
 * 六边形的角点与边中点共 12 个采样点，每条边上的点共线，所以轮廓与侧面
 * 依旧是干净的六棱柱。
 * <p>
 * 和 {@link ShieldShardMesh} 的区别只在几何；取色规则（色带位置 / 光带位置）
 * 完全共用 {@link ShieldShardMesh#faceColor}，所以切换形象时配色逻辑一致。
 */
final class ShieldShardMeshLegacy {

    private ShieldShardMeshLegacy() {
    }

    /** 棱柱半厚（格） */
    static final float THICKNESS_HALF = 0.03F;

    /** 一圈采样数（6 个角点 + 6 个边中点） */
    private static final int SEGMENTS = 12;
    /** 外接圆半径（尖角方向） */
    private static final float CIRCUMRADIUS = 0.90F;
    /** 内切圆半径 = 0.90 × cos30°，与旧贴图轮廓一致 */
    private static final float APOTHEM = CIRCUMRADIUS * 0.8660254F;

    private static final float[] OUT_U = new float[SEGMENTS];
    private static final float[] OUT_V = new float[SEGMENTS];
    private static final float[] SIDE_NX = new float[SEGMENTS];
    private static final float[] SIDE_NY = new float[SEGMENTS];

    // ===== 晶面起伏：缩放、轴向高度（相对 size）、波纹幅度（角点/边中点交替）=====
    private static final float[] RING_SCALE = {1.00F, 0.90F, 0.79F, 0.54F};
    private static final float[] RING_HEIGHT = {0.000F, 0.032F, 0.058F, 0.092F};
    private static final float[] RING_WAVE = {0.000F, 0.006F, 0.009F, 0.012F};
    /** 台面中心高度（相对 size） */
    private static final float TABLE_HEIGHT = 0.104F;

    static {
        for (int j = 0; j < SEGMENTS; j++) {
            double angle = Math.PI * j / 6.0;
            float radius = (j % 2 == 0) ? APOTHEM : CIRCUMRADIUS;
            OUT_U[j] = (float) (Math.cos(angle) * radius);
            OUT_V[j] = (float) (Math.sin(angle) * radius);
        }
        for (int j = 0; j < SEGMENTS; j++) {
            int next = (j + 1) % SEGMENTS;
            float ex = OUT_U[next] - OUT_U[j];
            float ey = OUT_V[next] - OUT_V[j];
            float nx = ey;
            float ny = -ex;
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len > 1.0E-6F) {
                nx /= len;
                ny /= len;
            }
            float midU = (OUT_U[j] + OUT_U[next]) * 0.5F;
            float midV = (OUT_V[j] + OUT_V[next]) * 0.5F;
            if (nx * midU + ny * midV < 0.0F) {
                nx = -nx;
                ny = -ny;
            }
            SIDE_NX[j] = nx;
            SIDE_NY[j] = ny;
        }
    }

    /**
     * 生成一片旧版六边形晶体。
     *
     * @param heightT 未使用（保留与新网格一致的签名）
     */
    static void buildShard(BufferBuilder buf, Matrix4f matrix,
                           float centerX, float centerY, float centerZ,
                           Vector3f right, Vector3f up, Vector3f dir,
                           float size, float alpha, float seedA, float seedB, float heightT,
                           float viewDirX, float viewDirY, float viewDirZ) {
        float unit = CIRCUMRADIUS * size;

        float fcx = centerX + dir.x * THICKNESS_HALF;
        float fcy = centerY + dir.y * THICKNESS_HALF;
        float fcz = centerZ + dir.z * THICKNESS_HALF;
        float bcx = centerX - dir.x * THICKNESS_HALF;
        float bcy = centerY - dir.y * THICKNESS_HALF;
        float bcz = centerZ - dir.z * THICKNESS_HALF;

        float facingDot = dir.x * viewDirX + dir.y * viewDirY + dir.z * viewDirZ;
        if (facingDot >= 0.0F) {
            buildFacetedCap(buf, matrix, fcx, fcy, fcz, right, up, dir, size, 1.0F,
                    alpha, seedA, centerX, centerY, centerZ, unit);
        } else {
            buildFacetedCap(buf, matrix, bcx, bcy, bcz, right, up, dir, size, -1.0F,
                    alpha, seedA, centerX, centerY, centerZ, unit);
        }

        // 侧面：六边形每条边一个平面，只画朝向摄像机的
        for (int j = 0; j < SEGMENTS; j++) {
            int next = (j + 1) % SEGMENTS;
            float nx = SIDE_NX[j] * right.x + SIDE_NY[j] * up.x;
            float ny = SIDE_NX[j] * right.y + SIDE_NY[j] * up.y;
            float nz = SIDE_NX[j] * right.z + SIDE_NY[j] * up.z;
            if (nx * viewDirX + ny * viewDirY + nz * viewDirZ <= 0.0F) {
                continue;
            }

            float u0 = OUT_U[j], v0 = OUT_V[j];
            float u1 = OUT_U[next], v1 = OUT_V[next];

            float f0x = fcx + u0 * size * right.x + v0 * size * up.x;
            float f0y = fcy + u0 * size * right.y + v0 * size * up.y;
            float f0z = fcz + u0 * size * right.z + v0 * size * up.z;
            float f1x = fcx + u1 * size * right.x + v1 * size * up.x;
            float f1y = fcy + u1 * size * right.y + v1 * size * up.y;
            float f1z = fcz + u1 * size * right.z + v1 * size * up.z;
            float b0x = bcx + u0 * size * right.x + v0 * size * up.x;
            float b0y = bcy + u0 * size * right.y + v0 * size * up.y;
            float b0z = bcz + u0 * size * right.z + v0 * size * up.z;
            float b1x = bcx + u1 * size * right.x + v1 * size * up.x;
            float b1y = bcy + u1 * size * right.y + v1 * size * up.y;
            float b1z = bcz + u1 * size * right.z + v1 * size * up.z;

            triOriented(buf, matrix, colorAt(alpha, seedA, unit,
                            (f0x + f1x + b0x) / 3.0F, (f0y + f1y + b0y) / 3.0F, (f0z + f1z + b0z) / 3.0F,
                            centerX, centerY, centerZ),
                    f0x, f0y, f0z, u0, v0,
                    f1x, f1y, f1z, u1, v1,
                    b0x, b0y, b0z, u0, v0,
                    nx, ny, nz, 0.7F);
            triOriented(buf, matrix, colorAt(alpha, seedA, unit,
                            (f1x + b1x + b0x) / 3.0F, (f1y + b1y + b0y) / 3.0F, (f1z + b1z + b0z) / 3.0F,
                            centerX, centerY, centerZ),
                    f1x, f1y, f1z, u1, v1,
                    b1x, b1y, b1z, u1, v1,
                    b0x, b0y, b0z, u0, v0,
                    nx, ny, nz, 0.7F);
        }
    }

    /** 带微起伏的六边形晶面（每个三角面单独取色）。 */
    private static void buildFacetedCap(BufferBuilder buf, Matrix4f matrix,
                                        float cx, float cy, float cz,
                                        Vector3f right, Vector3f up, Vector3f dir,
                                        float size, float side,
                                        float alpha, float seedA,
                                        float gemX, float gemY, float gemZ, float unit) {
        int rings = RING_SCALE.length;
        float[][] px = new float[rings][SEGMENTS];
        float[][] py = new float[rings][SEGMENTS];
        float[][] pz = new float[rings][SEGMENTS];
        float[][] pu = new float[rings][SEGMENTS];
        float[][] pv = new float[rings][SEGMENTS];

        for (int r = 0; r < rings; r++) {
            float scale = RING_SCALE[r];
            float baseHeight = RING_HEIGHT[r] * size * side;
            float wave = RING_WAVE[r] * size * side;
            for (int j = 0; j < SEGMENTS; j++) {
                double angle = Math.PI * j / 6.0;
                float radius = (j % 2 == 0) ? APOTHEM : CIRCUMRADIUS;
                float u = (float) (Math.cos(angle) * radius * scale);
                float v = (float) (Math.sin(angle) * radius * scale);
                float height = baseHeight + ((j % 2 == 0) ? wave : -wave);
                pu[r][j] = u;
                pv[r][j] = v;
                px[r][j] = cx + u * size * right.x + v * size * up.x + dir.x * height;
                py[r][j] = cy + u * size * right.y + v * size * up.y + dir.y * height;
                pz[r][j] = cz + u * size * right.z + v * size * up.z + dir.z * height;
            }
        }

        float refX = dir.x * side;
        float refY = dir.y * side;
        float refZ = dir.z * side;

        // 环带：每段拆成两个三角面
        for (int r = 0; r < rings - 1; r++) {
            int r1 = r + 1;
            for (int j = 0; j < SEGMENTS; j++) {
                int next = (j + 1) % SEGMENTS;
                triOriented(buf, matrix, colorAt(alpha, seedA, unit,
                                (px[r][j] + px[r][next] + px[r1][j]) / 3.0F,
                                (py[r][j] + py[r][next] + py[r1][j]) / 3.0F,
                                (pz[r][j] + pz[r][next] + pz[r1][j]) / 3.0F,
                                gemX, gemY, gemZ),
                        px[r][j], py[r][j], pz[r][j], pu[r][j], pv[r][j],
                        px[r][next], py[r][next], pz[r][next], pu[r][next], pv[r][next],
                        px[r1][j], py[r1][j], pz[r1][j], pu[r1][j], pv[r1][j],
                        refX, refY, refZ, 1.0F);
                triOriented(buf, matrix, colorAt(alpha, seedA, unit,
                                (px[r][next] + px[r1][next] + px[r1][j]) / 3.0F,
                                (py[r][next] + py[r1][next] + py[r1][j]) / 3.0F,
                                (pz[r][next] + pz[r1][next] + pz[r1][j]) / 3.0F,
                                gemX, gemY, gemZ),
                        px[r][next], py[r][next], pz[r][next], pu[r][next], pv[r][next],
                        px[r1][next], py[r1][next], pz[r1][next], pu[r1][next], pv[r1][next],
                        px[r1][j], py[r1][j], pz[r1][j], pu[r1][j], pv[r1][j],
                        refX, refY, refZ, 1.0F);
            }
        }

        // 中央台面
        float tableOffset = TABLE_HEIGHT * size * side;
        float tcx = cx + dir.x * tableOffset;
        float tcy = cy + dir.y * tableOffset;
        float tcz = cz + dir.z * tableOffset;
        int last = rings - 1;
        for (int j = 0; j < SEGMENTS; j++) {
            int next = (j + 1) % SEGMENTS;
            triOriented(buf, matrix, colorAt(alpha, seedA, unit,
                            (tcx + px[last][j] + px[last][next]) / 3.0F,
                            (tcy + py[last][j] + py[last][next]) / 3.0F,
                            (tcz + pz[last][j] + pz[last][next]) / 3.0F,
                            gemX, gemY, gemZ),
                    tcx, tcy, tcz, 0.0F, 0.0F,
                    px[last][j], py[last][j], pz[last][j], pu[last][j], pv[last][j],
                    px[last][next], py[last][next], pz[last][next], pu[last][next], pv[last][next],
                    refX, refY, refZ, 1.0F);
        }
    }

    private static int colorAt(float alpha, float seedA, float unit,
                               float fx, float fy, float fz,
                               float gemX, float gemY, float gemZ) {
        return ShieldShardMesh.faceColor(seedA, alpha, unit, fx, fy, fz, gemX, gemY, gemZ);
    }

    /**
     * 写入带自动法线的三角形：法线由三点叉积求得，并按参考方向（晶面朝外）定向。
     *
     * @param nScale 法线长度缩放：晶面 1.0、侧壁 0.7（供着色器区分面类型）
     */
    private static void triOriented(BufferBuilder buf, Matrix4f matrix, int color,
                                    float ax, float ay, float az, float au, float av,
                                    float bx, float by, float bz, float bu, float bv,
                                    float cx, float cy, float cz, float cu, float cv,
                                    float rx, float ry, float rz, float nScale) {
        float ux = bx - ax, uy = by - ay, uz = bz - az;
        float vx = cx - ax, vy = cy - ay, vz = cz - az;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-9F) {
            return;
        }
        nx = nx / len * nScale;
        ny = ny / len * nScale;
        nz = nz / len * nScale;
        if (nx * rx + ny * ry + nz * rz < 0.0F) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        buf.addVertex(matrix, ax, ay, az).setUv(au, av).setColor(color).setNormal(nx, ny, nz);
        buf.addVertex(matrix, bx, by, bz).setUv(bu, bv).setColor(color).setNormal(nx, ny, nz);
        buf.addVertex(matrix, cx, cy, cz).setUv(cu, cv).setColor(color).setNormal(nx, ny, nz);
    }
}
