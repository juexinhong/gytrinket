#version 150

// ============================================================================
//  Gytrinket - Island dimension sky  (v19 - base LOD, softened highlights)
//
//  Full-screen pass: the view ray is reconstructed per pixel from the inverse
//  projection, exactly like Solas' deferred sky.  There is no sky dome and no
//  independent FOV boost; vanilla's FOV modifier is used as-is.
//
//  Upper hemisphere: deep blue/cyan nebula with a pale Milky Way river.
//  The river is carried by larger, bright, color-varied stars and dark dust
//  lanes; teal/emerald and lavender patches break up the blue.  Overall star
//  density is 30% lower than v5, and broad nebula highlights stay soft.
//  Lower hemisphere: a dim, slightly rippled mirror image of the same sky,
//  like a still dark sea.  The horizon is the true horizontal plane at eye
//  level, so it is stable with camera altitude; terrain fog meets it exactly.
//
//  No flat discs, rings, halo sprites or hard geometry edges.
//
//  v17: base large-scale fields use 2-octave fbm; warp uses two fbm3
//  instead of three; void/wide reuse veil/mist. Stars -30% and filaments
//  -30% relative to the packaged v7 shader; dark dust is 30% lighter and
//  stars are 20% above the previous tuned value; ionization rims and heart
//  highlights are dimmed so the white filaments do not dominate.
// ============================================================================

in vec2 vScreen;

uniform float SkyTime;      // game ticks, continuous
uniform float PixelAngle;   // radians per vertical pixel
uniform vec3 SkyFogColor;   // terrain fog colour (display space)
uniform mat4 ModelViewMat;  // camera rotation only
uniform mat4 SkyInvProj;    // inverse of the current projection (FOV included)
uniform sampler2D SkyDepth; // Iris scene depth (late pass only)
uniform float SkyDepthEnabled;
/** 当前 framebuffer 尺寸（用于把 gl_FragCoord 归一化到深度纹理坐标） */
uniform float SkyScreenW;
uniform float SkyScreenH;
/** 深度采样压缩比 = gbuffer 尺寸 / framebuffer 尺寸（Iris render target 未跟随窗口 resize 时 <1） */
uniform float SkyDepthScaleX;
uniform float SkyDepthScaleY;

out vec4 fragColor;

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;

// ----------------------------------------------------------------------------
//  Hashing / noise
// ----------------------------------------------------------------------------
float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

float vnoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);

    float n000 = hash13(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);
    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);
    return mix(nxy0, nxy1, f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * vnoise(p);
        p = p.zxy * 2.03 + vec3(3.17, 1.71, 7.33);
        amp *= 0.5;
    }
    return sum * 1.07;
}

float fbm2(vec3 p) {
    float sum = 0.0;
    float amp = 0.62;
    for (int i = 0; i < 2; i++) {
        sum += amp * vnoise(p);
        p = p.zxy * 2.11 + vec3(2.73, 1.19, 6.41);
        amp *= 0.5;
    }
    return sum * 1.26;
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.55;
    for (int i = 0; i < 3; i++) {
        sum += amp * vnoise(p);
        p = p.zxy * 2.07 + vec3(1.37, 4.79, 2.11);
        amp *= 0.5;
    }
    return sum * 1.12;
}

float ridged(vec3 p) {
    float sum = 0.0;
    float amp = 0.58;
    for (int i = 0; i < 3; i++) {
        float n = 1.0 - abs(vnoise(p) * 2.0 - 1.0);
        n *= n;
        sum += n * amp;
        p = p.zyx * 2.11 + vec3(5.31, 0.73, 3.97);
        amp *= 0.5;
    }
    return sum * 1.10;
}

vec3 tone(vec3 x) {
    x = 1.0 - exp(-x * 0.88);
    return pow(x, vec3(0.98));
}

// ----------------------------------------------------------------------------
//  Star layers
// ----------------------------------------------------------------------------
vec3 starLayer(vec3 d, float scale, float density, float size, float seed,
               float brightness, float twinkleSpeed, float visibility) {
    // 30% fewer stars overall than the previous version.
    density *= 0.588;
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 rnd = hash33(cell + seed);
    if (rnd.z > density * visibility) {
        return vec3(0.0);
    }

    // Put the star exactly on the unit sphere near the cell centre.  Using a
    // 3D point inside the cell would make most stars miss the visible shell.
    vec3 starDir = normalize(cell + 0.5 + (hash33(cell + seed + 31.73) - 0.5) * 0.55);
    float dist = length(d - starDir) * scale;

    float px = max(PixelAngle * scale, 0.0001);
    float radius = min(max(size, px * 0.86), 0.42);
    float star = 1.0 - smoothstep(radius * 0.12, radius, dist);
    star *= star;

    float power = pow(rnd.x, 3.4) * 0.88 + 0.12;
    float twinkle = 0.78 + 0.22 * sin(SkyTime * twinkleSpeed + rnd.x * 61.0);

    float temp = hash13(cell + seed + 7.17);
    vec3 tint = mix(vec3(0.50, 0.68, 1.0), vec3(0.82, 0.90, 1.0),
                    smoothstep(0.15, 0.85, temp));
    tint = mix(tint, vec3(0.60, 0.78, 1.0), smoothstep(0.93, 1.0, temp) * 0.5);
    float temp2 = hash13(cell + seed + 19.31);
    tint = mix(tint, vec3(0.42, 0.95, 0.82), smoothstep(0.78, 0.94, temp2) * 0.40);
    tint = mix(tint, vec3(1.00, 0.76, 0.42), smoothstep(0.92, 1.00, temp2) * 0.30);

    return tint * (star * power * brightness * twinkle);
}

void main() {
    if (SkyDepthEnabled > 0.5) {
        // 深度纹理的尺寸不一定等于当前 framebuffer（Iris 可能按旧窗口尺寸分配/缩放）。
        // 用 framebuffer 尺寸归一化屏幕坐标，再采样深度纹理；直接除 textureSize
        // 在尺寸不一致时会让右上角落到纹理外/未写入区域，表现为“右上角漏出天空”。
        vec2 duv = gl_FragCoord.xy / vec2(max(1.0, SkyScreenW), max(1.0, SkyScreenH));
        duv *= vec2(SkyDepthScaleX, SkyDepthScaleY);
        if (texture(SkyDepth, clamp(duv, 0.0, 1.0)).r < 0.999999) {
            discard;
        }
    }
    // Reconstruct the exact per-pixel view ray from the inverse projection.
    // This is the same principle as Solas' deferred sky pass, and it removes
    // the extra edge warp a low-poly sky dome introduces.  The projection
    // already contains vanilla's FOV modifier; the sky never applies its own.
    vec4 ray4 = SkyInvProj * vec4(vScreen, 1.0, 1.0);
    vec3 viewRay = normalize(ray4.xyz / ray4.w);
    vec3 viewDir = normalize(mat3(transpose(ModelViewMat)) * viewRay);
    float t = SkyTime;
    float ts = t * 0.05;

    // ------------------------------------------------------------------
    //  World-space mirror horizon
    //
    //  The lower hemisphere is a dark mirror sea.  Its horizon is the true
    //  horizon of that infinite plane: eye level (viewEl = 0), independent of
    //  camera altitude.  The island's own rim remains visible as terrain in
    //  front of it, but the sky/sea horizon is stable and cannot slide with
    //  the camera-centred sky sphere.
    // ------------------------------------------------------------------
    float viewEl = asin(clamp(viewDir.y, -1.0, 1.0));
    float horizonElev = 0.0;

    // 1 below the horizon, 0 above.  At the line the reflected ray equals the
    // view ray, so the mirror flip itself is continuous.
    float mirror = 1.0 - smoothstep(horizonElev - 0.0035, horizonElev + 0.0035, viewEl);

    // The reflection is slightly rippled close to the horizon, like a dark
    // mirror sea, then settles into a still mirror further down.
    vec3 mirrorDir = vec3(viewDir.x, -viewDir.y, viewDir.z);
    float ripple = exp(-pow((viewEl - horizonElev) / 0.22, 2.0)) * 0.010;
    mirrorDir.x += ripple * sin(viewDir.z * 55.0 + SkyTime * 0.04);
    mirrorDir.z += ripple * cos(viewDir.x * 48.0 + SkyTime * 0.035);
    mirrorDir = normalize(mirrorDir);

    // Everything below is evaluated at the sampled direction: the sky above,
    // the mirror image below.
    vec3 d = normalize(mix(viewDir, mirrorDir, mirror));

    // ------------------------------------------------------------------
    //  Galactic frame: a curved, tilted band
    // ------------------------------------------------------------------
    vec3 N = normalize(vec3(0.96, 0.06, 0.28));
    vec3 T = normalize(cross(N, vec3(0.0, 1.0, 0.0)));
    float along = dot(d, T);
    float bandCurve = 0.15 * sin(along * 2.3 + 0.7)
                    + 0.10 * (vnoise(d * 2.2 + vec3(2.1, 7.7, 4.3)) - 0.5);
    float bd = dot(d, N) - bandCurve;
    float band = exp(-bd * bd * 7.0);

    // Three hearts distributed around the sky: rose/gold NW, teal/blue NE,
    // a dim violet one south so no direction is empty.
    vec3 coreA = normalize(vec3(-0.52, 0.38, -0.76));
    vec3 coreB = normalize(vec3(0.82, 0.22, -0.52));
    vec3 coreC = normalize(vec3(0.05, 0.30, 0.95));
    float angA = acos(clamp(dot(d, coreA), -1.0, 1.0));
    float angB = acos(clamp(dot(d, coreB), -1.0, 1.0));
    float angC = acos(clamp(dot(d, coreC), -1.0, 1.0));
    float heartA = exp(-pow(angA / 0.58, 1.7));
    float heartB = exp(-pow(angB / 0.46, 1.8));
    float heartC = exp(-pow(angC / 0.62, 1.9)) * 0.55;

    // ------------------------------------------------------------------
    //  Base: a textured deep-space field, not a flat gradient
    // ------------------------------------------------------------------
    float hue = fbm2(d * 1.2 + vec3(3.1, 1.7, 8.2));
    float baseN1 = fbm2(d * 2.1 + vec3(4.0, 2.0, 9.0));
    float baseN2 = fbm2(d * 4.3 + vec3(-2.0, 7.0, 1.0));
    vec3 base = mix(vec3(0.015, 0.028, 0.060), vec3(0.003, 0.012, 0.030),
                    smoothstep(-0.10, 0.82, d.y));
    base += mix(vec3(0.010, 0.018, 0.060), vec3(0.003, 0.034, 0.050),
                smoothstep(0.28, 0.72, baseN1)) * (0.50 + 0.70 * baseN2) * 0.92;

    float veil = fbm2(d * 1.0 + vec3(ts * 0.003, 4.0, 1.0));
    vec3 col = base + mix(vec3(0.010, 0.022, 0.065), vec3(0.004, 0.042, 0.060),
                          smoothstep(0.34, 0.72, veil)) * veil * 0.85;

    // Faint background mist everywhere: huge soft structures in the empty sky.
    float mist = fbm2(d * 2.6 + vec3(1.0, 5.0, 8.0) + vec3(0.0, ts * 0.0015, 0.0));
    float mistMask = smoothstep(0.38, 0.72, mist);
    col += mix(vec3(0.008, 0.024, 0.075), vec3(0.004, 0.042, 0.056),
               smoothstep(0.30, 0.75, baseN2)) * mistMask * 0.75;

    // ------------------------------------------------------------------
    //  Nebula clouds (large layer)
    // ------------------------------------------------------------------
    float wA = fbm3(d * 2.0 + vec3(1.7, 8.2, 4.1) + vec3(0.0, 0.0, ts * 0.006));
    float wB = fbm3(d * 2.0 + vec3(5.2, 1.3, 2.8) - vec3(ts * 0.004, 0.0, 0.0));
    vec3 w1 = vec3(wA, wB, fract(wA * 0.73 + wB * 0.41 + 0.17));
    vec3 np = d * 5.0 + (w1 - 0.5) * 1.8;

    float cloud = fbm3(np);
    float cloudMask = smoothstep(0.34, 0.62, cloud);

    // Large-scale voids: carve dark windows so the nebula has composition.
    float voidN = veil;
    cloudMask *= 0.30 + 0.70 * smoothstep(0.28, 0.52, voidN);

    // A second, independent wide cloud field keeps the sky from going empty
    // between the bright cores. It is dim, cool and very large scale.
    float wide = mist;
    float wideMask = smoothstep(0.40, 0.70, wide)
                   * (0.30 + 0.70 * band + 0.40 * heartA + 0.30 * heartB);
    col += mix(vec3(0.018, 0.038, 0.140), vec3(0.016, 0.080, 0.110),
               smoothstep(0.30, 0.75, baseN2)) * wideMask * 0.75;

    float sel = smoothstep(0.38, 0.60, hue);
    vec3 cVio  = vec3(0.10, 0.18, 0.72);
    vec3 cMag  = vec3(0.40, 0.14, 0.78);
    vec3 cCyan = vec3(0.02, 0.46, 0.96);
    vec3 cTeal = vec3(0.02, 0.78, 0.70);
    vec3 cGreen = vec3(0.04, 0.62, 0.42);
    vec3 cLav  = vec3(0.42, 0.30, 0.70);
    vec3 cPale = vec3(0.45, 0.65, 0.95);

    vec3 neb = mix(cVio, cMag, sel);
    neb = mix(neb, cCyan, smoothstep(0.50, 0.64, w1.y) * 0.95);
    neb = mix(neb, cTeal, smoothstep(0.52, 0.66, fbm(d * 3.4 + vec3(7.0, 2.0, 9.0))) * 0.80);
    neb = mix(neb, cGreen, smoothstep(0.58, 0.76, fbm(d * 4.6 + vec3(2.0, 8.0, 5.0))) * 0.35);
    neb = mix(neb, cLav, smoothstep(0.55, 0.74, fbm(d * 2.7 + vec3(9.0, 3.0, 1.0))) * 0.32);
    neb = mix(neb, cPale, heartA * smoothstep(0.44, 0.70, cloud) * 0.55);
    neb = mix(neb, mix(cCyan, cTeal, 0.5), heartC * 0.40);

    float emission = cloudMask * (0.12 + 0.55 * band + 0.50 * heartA + 0.28 * heartB + 0.22 * heartC);
    col += neb * emission * 0.52;

    // ------------------------------------------------------------------
    //  Mid filaments: bright ropes inside the clouds
    // ------------------------------------------------------------------
    float fil = ridged(np * 1.75 + w1 * 1.35);
    fil = pow(fil, 1.7);
    float filMask = smoothstep(0.30, 0.62, fil) * smoothstep(0.30, 0.58, cloud) * 0.70;
    vec3 filCol = mix(vec3(0.08, 0.48, 0.95), vec3(0.45, 0.25, 0.80),
                      smoothstep(0.30, 0.72, w1.z));
    filCol = mix(filCol, vec3(0.60, 0.78, 1.00), heartA * 0.45);
    filCol = mix(filCol, vec3(0.10, 0.85, 0.80), heartB * 0.50);
    col += filCol * filMask * (0.20 + 0.24 * band + 0.30 * heartA + 0.26 * heartB
                               + 0.20 * heartC) * 0.42;

    // Fine high-frequency texture: keeps the clouds from looking like soft mush.
    float fine = fbm(np * 7.5 + vec3(w1.z * 2.2, w1.x * 1.7, ts * 0.004));
    float fineMask = smoothstep(0.55, 0.74, fine) * smoothstep(0.36, 0.60, cloud) * 0.70;
    vec3 fineCol = mix(vec3(0.10, 0.50, 0.95), vec3(0.40, 0.55, 0.90),
                       smoothstep(0.3, 0.7, w1.x));
    fineCol = mix(fineCol, vec3(0.55, 0.75, 1.00), heartA * 0.45);
    col += fineCol * fineMask * (0.18 + 0.20 * band + 0.26 * heartA + 0.22 * heartB
                                 + 0.18 * heartC) * 0.36;

    // Long, stretched filaments: ionised ropes rather than round blobs.
    float strand = ridged(vec3(np.x * 0.55, np.y * 0.55, np.z * 2.20) + w1 * 0.80);
    strand = pow(strand, 2.2);
    float strandMask = smoothstep(0.42, 0.70, strand) * smoothstep(0.26, 0.55, cloud) * 0.70;
    vec3 strandCol = mix(vec3(0.08, 0.42, 0.95), vec3(0.35, 0.30, 0.85),
                         smoothstep(0.30, 0.70, w1.z));
    col += strandCol * strandMask * (0.15 + 0.22 * band + 0.24 * heartA + 0.18 * heartB)
           * 0.40;

    // Fine lace: the thin, high-frequency fibres that give a nebula its scale.
    float lace = ridged(np * 6.2 + w1 * 3.0 + vec3(ts * 0.010, 0.0, 0.0));
    lace = pow(lace, 3.2);
    float laceMask = smoothstep(0.48, 0.68, lace) * smoothstep(0.30, 0.62, cloud) * 0.70;
    vec3 laceCol = mix(vec3(0.15, 0.48, 0.95), vec3(0.45, 0.35, 0.90),
                       smoothstep(0.30, 0.70, w1.x));
    col += laceCol * laceMask * (0.12 + 0.14 * band + 0.21 * heartA + 0.16 * heartB)
           * 0.26;

    // ------------------------------------------------------------------
    //  Bright heart cores (much smaller / cooler than before)
    // ------------------------------------------------------------------
    float heartShape = smoothstep(0.55, 0.84, cloud * (0.52 + 0.72 * fil));
    vec3 heartCol = mix(vec3(0.35, 0.65, 1.00), vec3(0.70, 0.88, 1.00),
                        smoothstep(0.30, 0.78, fine));
    col += heartCol * heartShape * heartA * 0.22;
    col += vec3(0.22, 0.55, 1.00) * heartShape * heartB * 0.14;
    col += vec3(0.35, 0.45, 1.00) * heartShape * heartC * 0.10;
    // A small, pale nucleus inside the brightest cloud.
    col += vec3(0.75, 0.90, 1.00) * pow(heartShape, 4.0) * heartA * 0.18;
    col += vec3(0.90, 0.96, 1.00) * pow(max(heartShape, 0.0), 6.0)
           * pow(heartA, 2.2) * 0.28;

    // ------------------------------------------------------------------
    //  Galactic band: cool star clouds, blue haze, then a broad Milky Way
    // ------------------------------------------------------------------
    float bandTex = fbm(d * 6.0 + vec3(2.0, 9.0, 4.0));
    col += mix(vec3(0.08, 0.14, 0.28), vec3(0.18, 0.32, 0.48), hue)
           * band * (0.08 + 0.14 * bandTex);
    col += vec3(0.06, 0.35, 0.55)
           * smoothstep(0.60, 0.90, fbm(d * 10.0 + vec3(5.0, 1.0, 7.0)))
           * band * 0.28;
    col += mix(vec3(0.015, 0.030, 0.120), vec3(0.010, 0.085, 0.120),
               smoothstep(0.35, 0.70, w1.y)) * band * (0.20 + 0.12 * bandTex);
    col += vec3(0.04, 0.38, 0.34)
           * smoothstep(0.62, 0.86, fbm(d * 8.5 + vec3(4.0, 1.0, 6.0)))
           * band * 0.18;

    // A second, broader galactic river, tilted across the main nebula.  This
    // is the Milky Way analogue: pale blue star clouds, a soft core and dark
    // dust lanes.  It is deliberately kept below the old highlight peaks.
    vec3 mwN = normalize(vec3(0.75, 0.55, 0.35));
    float mwD = dot(d, mwN) + 0.07 * (fbm(d * 2.2 + vec3(4.0, 8.0, 1.0)) - 0.5);
    float mwHalo = exp(-mwD * mwD * 4.0);
    float mwRidge = exp(-mwD * mwD * 10.0);
    float mwTex = fbm(d * 6.5 + vec3(3.0, 7.0, 1.0));
    float mwCloud = smoothstep(0.32, 0.62, mwTex);
    vec3 mwTint = mix(vec3(0.30, 0.48, 0.72), vec3(0.82, 0.90, 1.00),
                      smoothstep(0.30, 0.70, mwTex));
    float mwGreen = smoothstep(0.58, 0.82, fbm(d * 4.1 + vec3(8.0, 2.0, 6.0)));
    mwTint = mix(mwTint, vec3(0.30, 0.76, 0.64), mwGreen * 0.30);
    col += mwTint * (mwHalo * 0.12 + mwRidge * mwCloud * 0.38);
    float mwKnots = smoothstep(0.55, 0.78, fbm(d * 13.0 + vec3(6.0, 2.0, 9.0))) * mwRidge;
    col += vec3(0.78, 0.88, 1.00) * mwKnots * 0.35;
    float mwDust = smoothstep(0.45, 0.62, fbm(d * 11.0 + vec3(2.0, 6.0, 9.0)))
                 * (mwHalo * 0.9);
    col *= 1.0 - 0.336 * mwDust;

    // The Great Rift: a thin dark lane splitting the pale river.
    float mwRift = exp(-pow(mwD / 0.040, 2.0))
                 * smoothstep(0.25, 0.55, fbm(d * 9.0 + vec3(5.0, 1.0, 3.0)));
    col *= 1.0 - 0.385 * mwRift;

    // A small, faint galactic core, warm enough to add a second hue.
    vec3 mwCoreDir = normalize(vec3(0.55, 0.30, -0.78));
    float mwCore = exp(-pow(acos(clamp(dot(d, mwCoreDir), -1.0, 1.0)) / 0.32, 2.0));
    vec3 mwCoreCol = mix(vec3(0.55, 0.72, 0.95), vec3(1.00, 0.64, 0.36),
                         smoothstep(0.45, 0.85, mwTex) * 0.55);
    col += mwCoreCol * mwCore * 0.20;

    // ------------------------------------------------------------------
    //  Foreground dust lanes
    // ------------------------------------------------------------------
    float dust = smoothstep(0.50, 0.70, fbm(np * 2.0 + vec3(4.4, 0.7, 8.8)));
    dust = max(dust, smoothstep(0.62, 0.80, fbm(d * 3.3 + vec3(11.0, 2.0, 5.0)))
                         * smoothstep(0.10, 0.62, band));
    dust = max(dust, smoothstep(0.58, 0.76, fbm(np * 5.0 + vec3(1.0, 6.0, 2.0)))
                         * smoothstep(0.20, 0.70, heartA));
    float dustAmt = clamp(dust * (0.45 + 0.55 * band + 0.25 * heartA), 0.0, 1.0);
    vec3 dustCol = vec3(0.018, 0.020, 0.045);
    col = mix(col, dustCol + col * 0.12, dustAmt * 0.525);
    col += vec3(0.080, 0.030, 0.050) * dustAmt * 0.175;

    // Sharp dust filaments inside the clouds: thin absorbing lanes with a
    // bright ionisation rim on one side. This is what gives the cloud volume.
    float df = ridged(np * 3.4 + w1 * 2.0 + vec3(5.0, 1.0, 8.0));
    df = pow(df, 3.0);
    float dfMask = smoothstep(0.50, 0.68, df) * smoothstep(0.30, 0.62, cloud);
    col = mix(col, vec3(0.016, 0.018, 0.040), dfMask * 0.224);
    float dfRim = smoothstep(0.38, 0.46, df) * (1.0 - smoothstep(0.46, 0.56, df));
    col += mix(vec3(0.50, 0.14, 0.22), vec3(0.08, 0.36, 0.55),
               smoothstep(0.30, 0.70, w1.x)) * dfRim * smoothstep(0.30, 0.70, cloud) * 0.06;

    // A long, coherent foreground rift cuts through the bright clouds and gives
    // the sky a clear near/far layering instead of uniform turbulence.
    vec3 riftN = normalize(vec3(0.28, 0.12, 0.95));
    float riftD = dot(d, riftN)
                + 0.10 * (fbm(d * 2.0 + vec3(5.0, 1.0, 7.0)) - 0.5);
    float rift = exp(-riftD * riftD * 55.0)
               * smoothstep(0.25, 0.65, cloud * 0.7 + fil * 0.3);
    col = mix(col, vec3(0.014, 0.018, 0.040), rift * 0.49);
    float riftRim = exp(-pow(abs(riftD) / 0.085, 3.0))
                  * (1.0 - rift) * smoothstep(0.30, 0.70, cloud);
    col += mix(vec3(0.55, 0.12, 0.20), vec3(0.06, 0.35, 0.65),
               smoothstep(0.30, 0.70, w1.z)) * riftRim * 0.18;

    // ------------------------------------------------------------------
    //  Aurora: broad, layered curtains low on the western horizon
    // ------------------------------------------------------------------
    float az = atan(d.z, d.x);
    float el = asin(clamp(d.y, -1.0, 1.0));
    float da = mod(az - (-2.55) + PI, TAU) - PI;
    float region = exp(-pow(da / 1.05, 2.0))
                 * smoothstep(-0.05, 0.12, el)
                 * (1.0 - smoothstep(0.52, 0.92, el));
    float a1 = fbm(vec3(da * 3.2, el * 1.4 - ts * 0.012, 2.0 + ts * 0.006));
    float a2 = fbm(vec3(da * 8.0, el * 2.0 - ts * 0.020, 7.0));
    float auroraVeil = smoothstep(0.40, 0.72, a1) * (0.55 + 0.45 * smoothstep(0.35, 0.7, a2));
    vec3 auroraCol = mix(vec3(0.02, 0.88, 0.58), vec3(0.18, 0.40, 1.0),
                         smoothstep(0.02, 0.64, el));
    col += auroraCol * auroraVeil * region * 0.36;

    // ------------------------------------------------------------------
    //  Stars
    // ------------------------------------------------------------------
    float starMask = (1.0 - 0.80 * dustAmt) * (0.90 + 0.80 * band + 0.45 * heartA + 0.30 * heartC);
    vec3 stars = vec3(0.0);
    stars += starLayer(d, 190.0, 0.035, 0.070, 3.0, 1.70, 0.060, 1.0);
    stars += starLayer(d, 95.0, 0.038, 0.100, 29.0, 3.40, 0.050, starMask);
    stars += starLayer(d, 42.0, 0.030, 0.130, 71.0, 6.80, 0.038, starMask);
    stars += starLayer(d, 20.0, 0.022, 0.030, 113.0, 12.00, 0.026, starMask * 0.9);
    stars += starLayer(d, 11.0, 0.014, 0.020, 157.0, 20.00, 0.018, starMask * 0.8);
    // A dense river of faint stars hugging the galactic plane.
    stars += starLayer(d, 85.0, 0.085, 0.090, 5.0, 1.55, 0.070, band * 1.2);
    // The Milky Way star river: sparse overall, but larger and brighter near
    // the band so the galaxy stands out from the background stars.
    stars += starLayer(d, 55.0, 0.260, 0.135, 501.0, 4.20, 0.050,
                       mwRidge * 2.8 + mwHalo * 0.7);
    stars += starLayer(d, 30.0, 0.160, 0.150, 601.0, 6.50, 0.040,
                       mwKnots * 2.2 + mwHalo * 0.25);
    stars += starLayer(d, 120.0, 0.120, 0.100, 311.0, 1.60, 0.050, mwHalo * 1.7);
    stars += starLayer(d, 18.0, 0.045, 0.090, 701.0, 12.00, 0.030, mwRidge * 1.4);
    // Embedded clusters: stars born inside the bright clouds.
    stars += starLayer(d, 55.0, 0.130, 0.080, 211.0, 2.00, 0.060, cloudMask * 1.3);
    col += stars;

    // Sparse unresolved starlight, mostly in the band.
    float dustStars = pow(vnoise(d * 210.0 + w1 * 4.0), 10.0);
    col += vec3(0.55, 0.70, 1.0) * dustStars * band * 0.25;

    // ------------------------------------------------------------------
    //  Saturation and tone mapping (quiet sky, bright stars)
    // ------------------------------------------------------------------
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.07);
    col = tone(col);
    float lum2 = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum2), col, 1.04);
    // Cool the whole sky a little: less red, more blue/cyan.  Applied after
    // tone mapping so it also cools the stars and the mirror sea.
    col = vec3(col.r * 0.90, col.g * 0.99, col.b * 1.06);

    // ------------------------------------------------------------------
    //  Mirror sea + fog horizon
    // ------------------------------------------------------------------
    // The reflected sky is dimmer, cooler and sits on a dark base.  It fades
    // with depth so the lower hemisphere reads as a deep mirror, not a copy.
    float depthBelow = clamp((horizonElev - viewEl) / 0.90, 0.0, 1.0);
    float reflStrength = mix(0.70, 0.42, depthBelow);
    vec3 reflCol = col * vec3(0.58, 0.68, 0.92) * reflStrength
                 + vec3(0.010, 0.010, 0.024) + SkyFogColor * 0.20;
    col = mix(col, reflCol, mirror);

    // A soft fog band hides the terrain edge and any chunk pop-in.  It uses
    // the exact terrain fog colour, so terrain, sky and mirror meet cleanly.
    float horizonBand = exp(-pow((viewEl - horizonElev) / 0.045, 2.0));
    col = mix(col, SkyFogColor, horizonBand * 0.40);

    // A thin, low-contrast horizon highlight makes the line readable without
    // looking like a hard geometric edge.
    vec3 hCol = SkyFogColor * 1.25 + vec3(0.012, 0.006, 0.020);
    col += hCol * 0.45 * exp(-pow((viewEl - horizonElev) / 0.010, 2.0));

    // Broader atmospheric haze hugging the horizon.
    float lowSky = exp(-pow((viewEl - horizonElev) / 0.38, 2.0)) * (1.0 - horizonBand);
    col = mix(col, col * 0.74 + SkyFogColor * 0.08, lowSky * 0.22);

    // Dither the huge dark gradients.
    float dither = (hash13(vec3(gl_FragCoord.xy, mod(t, 100.0))) - 0.5) / 255.0;
    fragColor = vec4(max(col + dither, 0.0), 1.0);
}
