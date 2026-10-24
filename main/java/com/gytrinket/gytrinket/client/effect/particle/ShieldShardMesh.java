package com.gytrinket.gytrinket.client.effect.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 护盾粒子「六边形宝石」网格生成器（完全程序化，不依赖贴图）。
 * <p>
 * 形状按「三个同心六边形环」拼出来：
 * <ul>
 *   <li>腰环：最大的一圈（外接圆半径 {@link #GIRDLE_RADIUS}），落在粒子所在的球面上；</li>
 *   <li>外小面 / 内小面：同心的两圈小六边形（半径 {@link #CAP_RADIUS}），
 *       沿大面法线分别外移 / 内移 {@link #CAP_OFFSET}，把腰环夹在中间；</li>
 *   <li>连接关系：腰环 ↔ 外小面 6 个梯形面、腰环 ↔ 内小面 6 个梯形面、
 *       两个小六边形各 1 个平顶面 —— 正好 <b>14 个面</b>。</li>
 * </ul>
 * <p>
 * 关键约定：<b>每个面只用一个颜色</b>（三渲二）。所以这里在 CPU 侧把两个"面常量"
 * 算好写进顶点色，着色器只做查表，面内不会有任何渐变：
 * <ul>
 *   <li><b>色带位置</b>：面心相对宝石中心的<b>世界 Y</b> ÷ 腰环半径 → 0..1，
 *       每颗宝石都完整走一遍色带（底 → 顶），不是所有粒子共用一个位置；</li>
 *   <li><b>光带位置</b>：面心在 {@link #BAND_AXIS} 这条世界轴上的投影 ÷ 腰环半径 → 0..1。
 *       所有宝石共用同一条世界轴，所以光带方向在整簇里是一致的；</li>
 *   <li>两者都是面常量 —— 一个面只会取色带上的一个颜色、光带上的一个位置。</li>
 * </ul>
 * 顶点格式 {@code POSITION_TEX_COLOR_NORMAL}：
 * <ul>
 *   <li>Position：世界坐标；</li>
 *   <li>UV0：六边形局部坐标，腰环外接圆 = 半径 1（小面环约 0.78），棱线按它算；</li>
 *   <li>Color：R = 每颗粒子的随机种子、G = 该面的色带位置、B = 该面的光带位置、A = 透明度；</li>
 *   <li>Normal：世界空间面法线，长度编码面类型（1.00 外小面 / 0.86 外斜面 / 0.74 内斜面 / 0.62 内小面）。</li>
 * </ul>
 */
final class ShieldShardMesh {

    private ShieldShardMesh() {
    }

    /** 光晕面片相对粒子尺寸的倍数 */
    static final float HALO_SCALE = 2.0F;

    /** 腰环外接圆半径（相对 size） */
    private static final float GIRDLE_RADIUS = 0.90F;
    /** 两个小面环的半径（"稍微缩小"） */
    private static final float CAP_RADIUS = 0.70F;
    /** 小面沿法线的偏移（相对 size），两小面间距 = 2 × 它 */
    private static final float CAP_OFFSET = 0.20F;

    /**
     * 光带轴向（世界空间，会被归一化）。所有宝石共用这一条轴，
     * 所以光带在整簇里方向一致；倾斜一点是为了让光带斜着切过宝石，而不是正横。
     */
    private static final float BAND_AXIS_X = 0.45F;
    private static final float BAND_AXIS_Y = 0.85F;
    private static final float BAND_AXIS_Z = 0.0F;

    // 法线长度编码的面类型（着色器按阈值解码）
    static final float N_CAP_OUT = 1.00F;
    static final float N_BEVEL_OUT = 0.86F;
    static final float N_BEVEL_IN = 0.74F;
    static final float N_CAP_IN = 0.62F;

    /** 六边形角点方向：30° + k·60°（尖顶朝上，与旧版轮廓一致） */
    private static final int CORNERS = 6;
    private static final float[] CORNER_COS = new float[CORNERS];
    private static final float[] CORNER_SIN = new float[CORNERS];

    static {
        for (int j = 0; j < CORNERS; j++) {
            double angle = Math.PI / 6.0 + Math.PI * j / 3.0;
            CORNER_COS[j] = (float) Math.cos(angle);
            CORNER_SIN[j] = (float) Math.sin(angle);
        }
    }

    /**
     * 生成一颗六边形宝石。
     *
     * @param heightT 保留参数（整簇海拔）：每颗宝石现在走自己的完整色带，不再需要它
     */
    static void buildShard(BufferBuilder buf, Matrix4f matrix,
                           float centerX, float centerY, float centerZ,
                           Vector3f right, Vector3f up, Vector3f dir,
                           float size, float alpha, float seedA, float seedB, float heightT,
                           float viewDirX, float viewDirY, float viewDirZ) {
        // 面心位置的归一化尺度 = 腰环半径
        float unit = GIRDLE_RADIUS * size;
        float capScale = CAP_RADIUS / GIRDLE_RADIUS;
        float offset = CAP_OFFSET * size;

        // ---- 三个同心环的顶点 ----
        float[] gu = new float[CORNERS], gv = new float[CORNERS];
        float[] gx = new float[CORNERS], gy = new float[CORNERS], gz = new float[CORNERS];
        float[] ox = new float[CORNERS], oy = new float[CORNERS], oz = new float[CORNERS];
        float[] ou = new float[CORNERS], ov = new float[CORNERS];
        float[] ix = new float[CORNERS], iy = new float[CORNERS], iz = new float[CORNERS];

        for (int j = 0; j < CORNERS; j++) {
            float c = CORNER_COS[j];
            float s = CORNER_SIN[j];

            gu[j] = c;
            gv[j] = s;
            gx[j] = centerX + c * GIRDLE_RADIUS * size * right.x + s * GIRDLE_RADIUS * size * up.x;
            gy[j] = centerY + c * GIRDLE_RADIUS * size * right.y + s * GIRDLE_RADIUS * size * up.y;
            gz[j] = centerZ + c * GIRDLE_RADIUS * size * right.z + s * GIRDLE_RADIUS * size * up.z;

            ou[j] = c * capScale;
            ov[j] = s * capScale;
            ox[j] = centerX + c * CAP_RADIUS * size * right.x + s * CAP_RADIUS * size * up.x + dir.x * offset;
            oy[j] = centerY + c * CAP_RADIUS * size * right.y + s * CAP_RADIUS * size * up.y + dir.y * offset;
            oz[j] = centerZ + c * CAP_RADIUS * size * right.z + s * CAP_RADIUS * size * up.z + dir.z * offset;

            ix[j] = centerX + c * CAP_RADIUS * size * right.x + s * CAP_RADIUS * size * up.x - dir.x * offset;
            iy[j] = centerY + c * CAP_RADIUS * size * right.y + s * CAP_RADIUS * size * up.y - dir.y * offset;
            iz[j] = centerZ + c * CAP_RADIUS * size * right.z + s * CAP_RADIUS * size * up.z - dir.z * offset;
        }

        float ocx = centerX + dir.x * offset;
        float ocy = centerY + dir.y * offset;
        float ocz = centerZ + dir.z * offset;
        float icx = centerX - dir.x * offset;
        float icy = centerY - dir.y * offset;
        float icz = centerZ - dir.z * offset;

        // ---- 12 个梯形侧面：面心算颜色，背面剔除 ----
        for (int j = 0; j < CORNERS; j++) {
            int n = (j + 1) % CORNERS;
            emitQuad(buf, matrix, seedA, alpha, size, unit,
                    centerX, centerY, centerZ,
                    gx[j], gy[j], gz[j], gu[j], gv[j],
                    gx[n], gy[n], gz[n], gu[n], gv[n],
                    ox[n], oy[n], oz[n], ou[n], ov[n],
                    ox[j], oy[j], oz[j], ou[j], ov[j],
                    N_BEVEL_OUT, viewDirX, viewDirY, viewDirZ);
            emitQuad(buf, matrix, seedA, alpha, size, unit,
                    centerX, centerY, centerZ,
                    ix[n], iy[n], iz[n], ou[n], ov[n],
                    ix[j], iy[j], iz[j], ou[j], ov[j],
                    gx[j], gy[j], gz[j], gu[j], gv[j],
                    gx[n], gy[n], gz[n], gu[n], gv[n],
                    N_BEVEL_IN, viewDirX, viewDirY, viewDirZ);
        }

        // ---- 两个小六边形平顶面：整面一个颜色（回到 14 面）----
        if (dir.x * viewDirX + dir.y * viewDirY + dir.z * viewDirZ > 0.0F) {
            int color = faceColor(seedA, alpha, unit, ocx, ocy, ocz, centerX, centerY, centerZ);
            float nx = dir.x * N_CAP_OUT, ny = dir.y * N_CAP_OUT, nz = dir.z * N_CAP_OUT;
            for (int j = 0; j < CORNERS; j++) {
                int n = (j + 1) % CORNERS;
                tri(buf, matrix, color,
                        ocx, ocy, ocz, 0.0F, 0.0F,
                        ox[j], oy[j], oz[j], ou[j], ov[j],
                        ox[n], oy[n], oz[n], ou[n], ov[n],
                        nx, ny, nz);
            }
        }
        if (-dir.x * viewDirX + -dir.y * viewDirY + -dir.z * viewDirZ > 0.0F) {
            int color = faceColor(seedA, alpha, unit, icx, icy, icz, centerX, centerY, centerZ);
            float nx = -dir.x * N_CAP_IN, ny = -dir.y * N_CAP_IN, nz = -dir.z * N_CAP_IN;
            for (int j = 0; j < CORNERS; j++) {
                int n = (j + 1) % CORNERS;
                tri(buf, matrix, color,
                        icx, icy, icz, 0.0F, 0.0F,
                        ix[n], iy[n], iz[n], ou[n], ov[n],
                        ix[j], iy[j], iz[j], ou[j], ov[j],
                        nx, ny, nz);
            }
        }
    }

    /**
     * 一个面的颜色：G = 该面在色带上的位置，B = 该面在光带轴上的位置。
     * 两个值都只跟面心相对宝石中心的位置有关 —— 面内所有顶点同一个值。
     * <p>
     * 新旧两套几何共用这一份取色规则（{@link ShieldShardMeshLegacy} 也会调它）。
     */
    static int faceColor(float seedA, float alpha, float unit,
                                 float fx, float fy, float fz,
                                 float cx, float cy, float cz) {
        float dx = fx - cx;
        float dy = fy - cy;
        float dz = fz - cz;

        // 色带：世界竖直方向，底 0 顶 1
        float ramp = 0.5F + 0.5F * (dy / unit);
        // 光带：沿固定世界轴投影，整簇方向一致
        float bandLen = (float) Math.sqrt(BAND_AXIS_X * BAND_AXIS_X + BAND_AXIS_Y * BAND_AXIS_Y + BAND_AXIS_Z * BAND_AXIS_Z);
        float band = 0.5F + 0.5F * ((dx * BAND_AXIS_X + dy * BAND_AXIS_Y + dz * BAND_AXIS_Z) / (unit * bandLen));

        return packColor(seedA, clamp01(ramp), clamp01(band), alpha);
    }

    /** 背面剔除后写入一个四边形（两个三角面），颜色按面心算一次。 */
    private static void emitQuad(BufferBuilder buf, Matrix4f matrix,
                                 float seedA, float alpha, float size, float unit,
                                 float cx, float cy, float cz,
                                 float ax, float ay, float az, float au, float av,
                                 float bx, float by, float bz, float bu, float bv,
                                 float qx, float qy, float qz, float qu, float qv,
                                 float dx, float dy, float dz, float du, float dv,
                                 float nScale, float viewX, float viewY, float viewZ) {
        float nx = (by - ay) * (qz - az) - (bz - az) * (qy - ay);
        float ny = (bz - az) * (qx - ax) - (bx - ax) * (qz - az);
        float nz = (bx - ax) * (qy - ay) - (by - ay) * (qx - ax);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-9F) {
            return;
        }
        nx /= len;
        ny /= len;
        nz /= len;

        // 面心相对宝石中心 → 把法线掰向外
        float mx = (ax + bx + qx + dx) * 0.25F - cx;
        float my = (ay + by + qy + dy) * 0.25F - cy;
        float mz = (az + bz + qz + dz) * 0.25F - cz;
        if (nx * mx + ny * my + nz * mz < 0.0F) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        if (nx * viewX + ny * viewY + nz * viewZ <= 0.0F) {
            return;
        }
        nx *= nScale;
        ny *= nScale;
        nz *= nScale;

        int color = faceColor(seedA, alpha, unit,
                (ax + bx + qx + dx) * 0.25F,
                (ay + by + qy + dy) * 0.25F,
                (az + bz + qz + dz) * 0.25F,
                cx, cy, cz);

        tri(buf, matrix, color, ax, ay, az, au, av, bx, by, bz, bu, bv, qx, qy, qz, qu, qv, nx, ny, nz);
        tri(buf, matrix, color, ax, ay, az, au, av, qx, qy, qz, qu, qv, dx, dy, dz, du, dv, nx, ny, nz);
    }

    /** 写入一个三角面（法线、颜色给定）。 */
    private static void tri(BufferBuilder buf, Matrix4f matrix, int color,
                            float ax, float ay, float az, float au, float av,
                            float bx, float by, float bz, float bu, float bv,
                            float cx, float cy, float cz, float cu, float cv,
                            float nx, float ny, float nz) {
        buf.addVertex(matrix, ax, ay, az).setUv(au, av).setColor(color).setNormal(nx, ny, nz);
        buf.addVertex(matrix, bx, by, bz).setUv(bu, bv).setColor(color).setNormal(nx, ny, nz);
        buf.addVertex(matrix, cx, cy, cz).setUv(cu, cv).setColor(color).setNormal(nx, ny, nz);
    }

    /** 生成面向摄像机的加色光晕面片（UV 为 -1..1 的方形） */
    static void buildHalo(BufferBuilder buf, Matrix4f matrix,
                          float cx, float cy, float cz,
                          Vector3f camRight, Vector3f camUp, Vector3f dir,
                          float size, float alpha, float seedA, float seedB, float heightT) {
        int color = packColor(seedA, 0.5F, 0.5F, alpha);
        float ox = cx + dir.x * size * 0.18F;
        float oy = cy + dir.y * size * 0.18F;
        float oz = cz + dir.z * size * 0.18F;
        float h = size * HALO_SCALE;

        float x0 = ox - camRight.x * h - camUp.x * h;
        float y0 = oy - camRight.y * h - camUp.y * h;
        float z0 = oz - camRight.z * h - camUp.z * h;
        float x1 = ox + camRight.x * h - camUp.x * h;
        float y1 = oy + camRight.y * h - camUp.y * h;
        float z1 = oz + camRight.z * h - camUp.z * h;
        float x2 = ox + camRight.x * h + camUp.x * h;
        float y2 = oy + camRight.y * h + camUp.y * h;
        float z2 = oz + camRight.z * h + camUp.z * h;
        float x3 = ox - camRight.x * h + camUp.x * h;
        float y3 = oy - camRight.y * h + camUp.y * h;
        float z3 = oz - camRight.z * h + camUp.z * h;

        buf.addVertex(matrix, x0, y0, z0).setUv(-1.0F, -1.0F).setColor(color).setNormal(0.0F, 0.0F, 1.0F);
        buf.addVertex(matrix, x1, y1, z1).setUv(1.0F, -1.0F).setColor(color).setNormal(0.0F, 0.0F, 1.0F);
        buf.addVertex(matrix, x2, y2, z2).setUv(1.0F, 1.0F).setColor(color).setNormal(0.0F, 0.0F, 1.0F);

        buf.addVertex(matrix, x0, y0, z0).setUv(-1.0F, -1.0F).setColor(color).setNormal(0.0F, 0.0F, 1.0F);
        buf.addVertex(matrix, x2, y2, z2).setUv(1.0F, 1.0F).setColor(color).setNormal(0.0F, 0.0F, 1.0F);
        buf.addVertex(matrix, x3, y3, z3).setUv(-1.0F, 1.0F).setColor(color).setNormal(0.0F, 0.0F, 1.0F);
    }

    /** 将 0~1 的 RGBA 打包为 ARGB int（1.21 的 setColor(int)）。 */
    static int packColor(float r, float g, float b, float a) {
        int ri = (int) (clamp01(r) * 255.0F);
        int gi = (int) (clamp01(g) * 255.0F);
        int bi = (int) (clamp01(b) * 255.0F);
        int ai = (int) (clamp01(a) * 255.0F);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }
}
