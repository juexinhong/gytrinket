#version 150

// ============================================================================
// 立体护盾粒子 · 三渲二水晶着色器
// ----------------------------------------------------------------------------
// 参考：B 站 BV1izTWzbEsV《（44）blender三渲二水晶-风格化》，取其最终成片
// （23:30 起）的六层做法，逐条落在本文件里：
//
//   ① 垂直色带（ColorRamp）  全局海拔 + 每颗自己的上下 + 个性偏差
//   ② 三渲二光照（Toon）      光照量化成 3 档；网格是 14 个平面切面，一档一个色块
//   ③ 轮廓（LineArt）         腰环外沿一圈亮线 + 冠部 6 条棱线 + 菲涅尔
//   ④ 裂纹（Voronoi）         每颗独立取样、随时间漂移与脉动（动态裂纹）
//   ⑤ 能量流动                域扭曲 fbm 沿晶面缓慢上行 + 叠加流动高光
//   ⑥ 后期辉光（Glare）       加色光晕 Pass
//
// 网格：ShieldShardMesh 的「三环 14 面」六边形宝石。
// 顶点约定：
//   UV0 六边形局部坐标（腰环外接圆 = 1，小面环 ≈ 0.844）
//   Color = (种子 A, 壳层海拔 0..1, 种子 B, 透明度)
//   Normal 长度 = 面类型：1.00 外小面 / 0.86 外斜面 / 0.74 内斜面 / 0.62 内小面
//
// 三道 Pass：
//   Pass 0：晶体本体   Pass 1：加色光晕   Pass 2：加色高光
// ============================================================================

in vec2 vUv;
in vec3 vNormalView;
in vec3 vNormalWorld;
in vec3 vViewPos;
in vec3 vSeed;
in float vAlpha;

uniform mat4 ViewRot;
uniform mat4 ModelViewMat;
uniform float GemTime;
uniform float PassMode;
/** 形象：0 = 三渲二水晶（默认），1 = 旧立体宝石 */
uniform float StyleMode;

out vec4 fragColor;

/** 整体亮度系数：1.20.1 这条管线比 1.21.1 偏亮，先按 -30% 压一档（0.70） */
const float BRIGHTNESS = 0.70;

const float TAU = 6.28318530718;
const float PI = 3.14159265359;

// ---- 调色板：从成片帧逐点采样得到的实际像素，再往深处压了一点 ----
const vec3 C_DEEP  = vec3(0.030, 0.055, 0.260); // #080E42 底部深蓝
const vec3 C_BLUE  = vec3(0.100, 0.380, 0.800); // #1A61CC 中段蓝
const vec3 C_CYAN  = vec3(0.560, 0.960, 1.000); // #8FF5FF 上段亮青
const vec3 C_PALE  = vec3(0.624, 1.000, 0.984); // #9FFFFB 亮面
const vec3 C_WHITE = vec3(0.898, 1.000, 1.000); // #E5FFFF 棱线/高光

// ---------------------------------------------------------------------------
// 稳定噪声
// ---------------------------------------------------------------------------
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.02 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// Voronoi 细胞边界：1 = 正好在壁上
float voronoiEdge(vec2 p) {
    vec2 ip = floor(p);
    vec2 fp = fract(p);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = vec2(hash21(ip + g), hash21(ip + g + vec2(17.31, 9.17)));
            float d = length(g + o - fp);
            if (d < f1) {
                f2 = f1;
                f1 = d;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    return 1.0 - smoothstep(0.0, 0.10, f2 - f1);
}

void main() {
    if (vAlpha <= 0.002) {
        discard;
    }

    float t = GemTime;
    float seedA = vSeed.x;
    // G = 该面的色带位置（0 底 1 顶），B = 该面的光带位置 —— 都是 CPU 侧按面心算好的
    // 面常量，所以一个面只会取到一个颜色、一个光带值。
    float hFace  = clamp(vSeed.y, 0.0, 1.0);
    float bandU  = clamp(vSeed.z, 0.0, 1.0);
    float seedB  = fract(seedA * 7.31 + 0.17);
    vec3 V = normalize(-vViewPos);

    // =======================================================================
    // Pass 1：加色光晕（成片里的 Glare/Bloom）
    // =======================================================================
    if (PassMode > 0.5 && PassMode < 1.5) {
        vec2 p = vUv;
        float r = length(p);
        float core = exp(-pow(r * 1.30, 2.0));
        float ring = exp(-pow((r - 0.66) * 3.10, 2.0));
        float rayH = pow(max(0.0, 1.0 - abs(p.x) * 6.5 - abs(p.y) * 2.2), 12.0);
        float rayV = pow(max(0.0, 1.0 - abs(p.y) * 6.5 - abs(p.x) * 2.2), 12.0);
        float pulse = 0.82 + 0.18 * sin(t * 2.1 + seedA * 31.0);
        vec3 tint = mix(C_CYAN, C_WHITE, 0.35);
        float amount = (core * 0.020 + ring * 0.028 + (rayH + rayV) * 0.008) * pulse;
        fragColor = vec4(tint * amount * BRIGHTNESS, vAlpha);
        return;
    }

    // =======================================================================
    // 面类型 / 法线 / 观察方向
    // =======================================================================
    float nl = length(vNormalWorld);
    vec3 Nw = vNormalWorld / max(nl, 1.0e-4);
    vec3 N = normalize(mat3(ModelViewMat) * Nw);
    if (dot(N, V) < 0.0) {
        N = -N;
    }
    vec3 Vw = normalize(transpose(mat3(ModelViewMat)) * V);

    // 14 个面按法线长度分四类：外小面 / 外斜面 / 内斜面 / 内小面
    float capOut = step(0.93, nl);
    float bevelOut = step(0.80, nl) * (1.0 - capOut);
    float bevelIn = step(0.68, nl) * (1.0 - step(0.80, nl));
    float capIn = 1.0 - step(0.68, nl);

    float ndv = clamp(dot(N, V), 0.0, 1.0);
    float fres = pow(1.0 - ndv, 2.2);

    // 六边形局部坐标：腰环外接圆 = 1
    float hd = max(abs(vUv.x), max(abs(vUv.x * 0.5 + vUv.y * 0.8660254), abs(-vUv.x * 0.5 + vUv.y * 0.8660254)));
    float rho = length(vUv);

    // ② 三渲二光照：压成 3 档
    vec3 Lw = normalize(vec3(0.35, 0.72, 0.60));
    float lit = 0.5 + 0.5 * dot(Nw, Lw);
    float band = floor(lit * 3.0 + 0.5) / 3.0;
    float bandFine = floor(lit * 6.0 + 0.5) / 6.0;

    // =======================================================================
    // ① 面颜色：固定三档（仿视频）——
    //    上 = 青、中 = 蓝、下 = 深蓝。不再做连续渐变，每个面取一档。
    //    h 是 CPU 按面心算好的 0..1（每颗宝石都走完整条）。
    // =======================================================================
    float h = hFace;
    vec3 ramp;
    if (StyleMode > 0.5) {
        // 旧立体宝石：平滑渐变 + 一点虹彩，不做固定三档
        ramp = mix(C_DEEP, C_CYAN, smoothstep(0.0, 1.0, h));
        vec3 irid = 0.5 + 0.5 * cos(TAU * (vec3(0.0, 0.33, 0.67) + ndv * 1.7 + seedA * 2.3));
        ramp = mix(ramp, irid, 0.18);
    } else {
        // 三渲二水晶：固定三档（上亮青 / 中蓝 / 下深蓝）
        ramp = mix(C_DEEP, C_BLUE, step(0.34, h));
        ramp = mix(ramp, C_CYAN, step(0.67, h));
    }

    // =======================================================================
    // ④ 动态裂纹：每颗宝石自己一张网，随时间缩放、漂移、逐格亮起
    // =======================================================================
    float crackScale = 2.35 * (1.0 + 0.06 * sin(t * 0.7 + seedA * 6.2831));
    vec2 cp = vUv * crackScale + vec2(seedA * 11.0, seedB * 7.0) + vec2(t * 0.045, -t * 0.035);
    float crack = voronoiEdge(cp);
    float crackLit = 0.55 + 0.45 * sin(t * 1.7 + hash21(floor(cp)) * TAU);

    // ⑤ 能量流动：竖向拉长的域扭曲 fbm —— 像沿晶面往上走的能量条，不是斑点
    vec2 fp = vec2(vUv.x * 3.0 + seedA * 5.0, vUv.y * 1.25 + t * 0.55);
    vec2 warp = vec2(fbm(fp * 1.5 - vec2(0.0, t * 0.20)), fbm(fp * 1.5 + vec2(3.1, 0.0)));
    float f = fbm(fp + 1.2 * warp);
    float vein = smoothstep(0.44, 0.86, f);

    // =======================================================================
    // ③ 棱边：三条边都要描 —— 腰环外沿 / 台面边界 / 6 条冠部棱线。
    //    颜色跟着色带走（底部偏蓝、顶部偏白），不是一条死白。
    //    hd 在两条六边形边界上的取值：腰环（外接圆 1.0）= 0.866，
    //    台面（0.70/0.90 = 0.778）= 0.674 —— 改 ShieldShardMesh 的
    //    CAP_RADIUS / GIRDLE_RADIUS 时，这两个常数也要跟着改。
    // =======================================================================
    float edgeGirdle;
    float edgeCap;
    float edgeRadial;
    vec3  edgeTint;
    float ang = atan(vUv.y, vUv.x);
    if (StyleMode > 0.5) {
        // 旧立体宝石：网格外接圆 0.90、内切圆 0.779，环带在 RING_SCALE {1.00,0.90,0.79,0.54}。
        // 按这几圈画边线，才不会像水晶阈值那样落在网格外/横穿面中间。
        float rim = 0.7789;                 // 0.90 × cos30°
        float w = 0.014;
        edgeGirdle = 1.0 - smoothstep(0.0, w, abs(hd - rim));
        edgeCap = 1.0 - smoothstep(0.0, w, abs(hd - rim * 0.90))
                + 1.0 - smoothstep(0.0, w, abs(hd - rim * 0.79))
                + 1.0 - smoothstep(0.0, w, abs(hd - rim * 0.54));
        edgeCap = clamp(edgeCap, 0.0, 1.0);
        // 12 段（每 30°）都有面边界，只在冠部（rho < rim）画
        float sector = abs(fract(ang / (PI / 6.0)) - 0.5);
        edgeRadial = (1.0 - smoothstep(0.0, 0.020, sector * rho))
                   * smoothstep(rim * 0.42, rim * 0.50, rho)
                   * (1.0 - smoothstep(rim * 0.92, rim * 1.00, rho));
        edgeTint = mix(ramp * 1.5, C_WHITE, 0.30 + 0.40 * h);
    } else {
        edgeGirdle = 1.0 - smoothstep(0.0, 0.030, abs(hd - 0.8660254));
        edgeCap    = 1.0 - smoothstep(0.0, 0.028, abs(hd - 0.6744));
        float cornerPhase = abs(fract(ang / (PI / 3.0)) - 0.5);
        // 冠部棱线只画在斜面上（法线长度 0.68..0.93），台面是一整片，不画星形线
        float isBevel = step(0.68, nl) * (1.0 - step(0.93, nl));
        edgeRadial = (1.0 - smoothstep(0.0, 0.024, cornerPhase * rho))
                   * smoothstep(0.62, 0.70, rho) * isBevel;
        edgeTint = mix(ramp * 1.5, C_WHITE, 0.30 + 0.40 * h);
    }
    float edgeMask = clamp(edgeGirdle + edgeCap * 0.80 + edgeRadial * 0.85, 0.0, 1.0);

    // =======================================================================
    // Pass 2：加色高光
    // =======================================================================
    if (PassMode > 1.5) {
        vec3 hi = vec3(0.0);

        hi += C_WHITE * fres * 0.22;
        hi += edgeTint * edgeMask * 0.85;

        // 动态裂纹：逐格亮起，下半身更明显
        hi += mix(C_CYAN, C_WHITE, 0.60) * crack * crackLit * 0.30 * (0.35 + 0.65 * (1.0 - h));

        // 流动高光（让"流动"在加色层也看得见）
        hi += mix(C_CYAN, C_WHITE, 0.55) * vein * 0.12;

        // ---- 受光高光：按「面」选面（回退到 19:21 那版）----
        // 面法线是常量，所以一个面要么整面亮、要么整面不亮：
        // 既不是后处理叠条，也不会横穿单个面；所有宝石共用同一个反射判据。
        vec3 Rv = reflect(-V, N);
        float ry = clamp(Rv.y, -1.0, 1.0);
        float bigBand   = smoothstep(-0.02, 0.06, ry) * (1.0 - smoothstep(0.26, 0.36, ry));
        float smallBand = smoothstep(0.86, 0.91, ry) * (1.0 - smoothstep(0.97, 1.00, ry));
        vec3 bandTint = mix(C_PALE, C_WHITE, 0.60);
        if (StyleMode < 0.5) {
            // 水晶形象才有"按面选面"的光带；旧立体宝石只保留边缘光
            hi += bandTint * (bigBand * 0.50 + smallBand * 1.05);
        }

        fragColor = vec4(hi * BRIGHTNESS, vAlpha);
        return;
    }

    // =======================================================================
    // Pass 0：晶体本体
    // =======================================================================
    vec3 body = ramp * (0.60 + 0.32 * band);

    // 每个切面略微不同
    body *= 0.94 + 0.12 * hash21(floor(Nw.xz * 30.0 + 0.5) + seedA);

    // 流动的能量（本体层，透过面也能看到）
    body += mix(C_CYAN, C_BLUE, 0.35) * vein * (0.10 + 0.10 * capOut) * (0.6 + 0.4 * band);

    // 裂纹处压暗：裂开的地方先显出一条暗线，亮线交给 Pass 2
    body *= 1.0 - crack * 0.38 * (0.4 + 0.6 * (1.0 - h));

    // 最亮那一档提一点
    body += C_PALE * pow(bandFine, 3.0) * 0.12;

    // 通透度：内斜面上薄一点，外小面实
    float alpha = 0.60 + 0.16 * fres + 0.10 * edgeMask + 0.06 * capOut;
    alpha = clamp(alpha, 0.0, 0.96);

    fragColor = vec4(body * BRIGHTNESS, alpha * vAlpha);
}
