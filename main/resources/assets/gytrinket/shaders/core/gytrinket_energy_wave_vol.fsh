#version 150

in vec2 texCoord;
in vec3 viewPos;

uniform mat4 InvModelViewMat;
uniform vec3 CamRight;
uniform vec3 CamUp;
uniform vec3 Forward;
uniform vec3 WaveCenter;

uniform float MaxSpan;
uniform float MaxBack;
uniform float MaxForward;

uniform float OuterHW;
uniform float OuterLen;
uniform float ColorHW;
uniform float ColorLen;
uniform float CenterHW;
uniform float CenterLen;

uniform vec4 CenterColor;
uniform vec4 ColorLayerColor;
uniform vec4 OuterLayerColor;
uniform vec4 BloomColor;

uniform float BloomMaxDist;
uniform float GlowRadius;
uniform float GlowStrength;

out vec4 fragColor;

// 局部坐标系：WaveCenter为原点，CamRight=X轴，CamUp=Y轴，Forward=Z轴
// SDF约定：正=内部，负=外部

vec3 toLocal(vec3 worldPos) {
    vec3 offset = worldPos - WaveCenter;
    return vec3(dot(offset, CamRight), dot(offset, CamUp), dot(offset, Forward));
}

float revolutionSDF(vec3 p, float hw, float len) {
    float r = length(p.xy);
    float z = p.z;
    if (z >= 0.0) {
        if (r >= hw) {
            float dz = z;
            float dr = r - hw;
            return -sqrt(dz * dz + dr * dr);
        }
        float ns = r / hw;
        float zBound = len * (1.0 - pow(ns, 3.6));
        float dfdr = -3.6 * len / hw * pow(max(ns, 0.001), 2.6);
        float gradLen = sqrt(dfdr * dfdr + 1.0);
        return (zBound - z) / gradLen;
    } else {
        return -(sqrt(r * r + z * z) - hw);
    }
}

float bloomMaxAt(vec3 p) {
    if (BloomMaxDist <= 0.0) return 0.0;
    float dist = length(p);
    float t = clamp(dist / max(OuterLen, 0.001), 0.0, 1.0);
    return BloomMaxDist * (1.0 - t) * (1.0 - t);
}

float glowFactor3D(vec3 p) {
    vec3 glowCenter = vec3(0.0, 0.0, -CenterHW);
    float dist = length(p - glowCenter);
    if (dist >= GlowRadius) return 0.0;
    return (1.0 - dist / GlowRadius) * GlowStrength;
}

vec3 applyGlow(vec3 color, float glow) {
    return vec3(
        min(1.0, color.r + (1.0 - color.r) * glow),
        min(1.0, color.g + (1.0 - color.g) * glow),
        min(1.0, color.b + (1.0 - color.b) * glow)
    );
}

float boxFade(vec3 p) {
    float cr = length(p.xy);
    float fadeR = smoothstep(MaxSpan, MaxSpan * 0.75, cr);
    float fadeZf = smoothstep(MaxForward, MaxForward * 0.75, p.z);
    float fadeZb = smoothstep(-MaxBack, -MaxBack * 0.75, p.z);
    return fadeR * fadeZf * fadeZb;
}

void main() {
    vec4 worldPos4 = InvModelViewMat * vec4(viewPos, 1.0);
    vec3 worldPos = worldPos4.xyz;
    vec4 camWorld4 = InvModelViewMat * vec4(0.0, 0.0, 0.0, 1.0);
    vec3 camWorld = camWorld4.xyz;

    vec3 localOrigin = toLocal(worldPos);
    vec3 localCamPos = toLocal(camWorld);
    vec3 rd = normalize(localOrigin - localCamPos);
    vec3 ro = localOrigin;

    float baseStep = min(min(MaxSpan, MaxForward), 1.0) * 0.035;
    float minStep = baseStep * 0.08;
    float maxDist = (MaxSpan + MaxBack + MaxForward) * 3.0;

    // ===== Phase 1: 光线行进，收集数据 =====
    // 收集表面交叉（只记录进入交叉：SDF从负变正）
    // 同时累积泛光体积

    const int MAX_CROSSINGS = 6;
    int numCrossings = 0;
    vec3 crossingColors[MAX_CROSSINGS];
    float crossingAlphas[MAX_CROSSINGS];

    // 泛光体积累积
    vec3 bloomAccumColor = vec3(0.0);
    float bloomTransmittance = 1.0;

    float t = 0.0;
    float prevOuterSDF = revolutionSDF(ro, OuterHW, OuterLen);
    float prevColorSDF = revolutionSDF(ro, ColorHW, ColorLen);
    float prevCenterSDF = revolutionSDF(ro, CenterHW, CenterLen);

    for (int i = 0; i < 150; i++) {
        vec3 p = ro + rd * t;

        float r = length(p.xy);
        if (p.z > MaxForward * 1.2 || p.z < -MaxBack * 1.2 || r > MaxSpan * 1.2) {
            t += baseStep * 2.0;
            if (t > maxDist) break;
            continue;
        }

        float outerSDF = revolutionSDF(p, OuterHW, OuterLen);
        float colorSDF = revolutionSDF(p, ColorHW, ColorLen);
        float centerSDF = revolutionSDF(p, CenterHW, CenterLen);

        // --- 收集表面进入交叉 ---
        // 外层交叉（最先遇到，将是合成时的最底层）
        if (prevOuterSDF < 0.0 && outerSDF >= 0.0 && numCrossings < MAX_CROSSINGS) {
            float edgeSoft = smoothstep(-0.02, 0.02, outerSDF);
            crossingColors[numCrossings] = OuterLayerColor.rgb;
            crossingAlphas[numCrossings] = OuterLayerColor.a * edgeSoft;
            numCrossings++;
        }

        // 颜色层交叉
        if (prevColorSDF < 0.0 && colorSDF >= 0.0 && numCrossings < MAX_CROSSINGS) {
            float glow = glowFactor3D(p);
            crossingColors[numCrossings] = applyGlow(ColorLayerColor.rgb, glow);
            crossingAlphas[numCrossings] = ColorLayerColor.a;
            numCrossings++;
        }

        // 中心层交叉（最后遇到，将是合成时的最顶层）
        if (prevCenterSDF < 0.0 && centerSDF >= 0.0 && numCrossings < MAX_CROSSINGS) {
            float glow = glowFactor3D(p);
            crossingColors[numCrossings] = applyGlow(CenterColor.rgb, glow);
            crossingAlphas[numCrossings] = CenterColor.a;
            numCrossings++;
        }

        // --- 泛光体积（外表面的外部空间） ---
        if (outerSDF < 0.0) {
            float dist = abs(outerSDF);
            float localMax = bloomMaxAt(p);
            if (localMax > 0.001 && dist < localMax) {
                float bloom = 1.0 - dist / localMax;
                bloom = bloom * bloom;
                float bloomDensity = BloomColor.a * bloom * baseStep * 2.5;
                bloomDensity *= boxFade(p);
                bloomAccumColor += BloomColor.rgb * bloomDensity * bloomTransmittance;
                bloomTransmittance *= (1.0 - bloomDensity);
            }
        }

        prevOuterSDF = outerSDF;
        prevColorSDF = colorSDF;
        prevCenterSDF = centerSDF;

        float minAbsSDF = min(abs(outerSDF), min(abs(colorSDF), abs(centerSDF)));
        float step = max(minAbsSDF * 0.7, minStep);
        step = min(step, baseStep * 2.0);
        t += step;
        if (t > maxDist) break;
    }

    // ===== Phase 2: 后处理合成（Screen Blending）=====
    // Screen混合: result = 1 - (1-a)(1-b)(1-c)...
    // 每层独立贡献，不会被其他层遮挡，所有层级清晰可见
    // 解决source-over下外焰层alpha遮挡泛光层的问题：
    //   source-over: bloom贡献被外焰层(1-outerAlpha)衰减
    //   screen: bloom贡献独立叠加，不受任何层遮挡

    vec3 invResult = vec3(1.0);
    float invAlpha = 1.0;

    // 泛光体积贡献
    vec3 bloomContrib = min(bloomAccumColor, vec3(1.0));
    float bloomAlpha = min(1.0 - bloomTransmittance, 1.0);
    invResult *= (1.0 - bloomContrib);
    invAlpha *= (1.0 - bloomAlpha);

    // 表面交叉贡献（Screen混合与顺序无关，每层独立叠加）
    for (int i = 0; i < numCrossings; i++) {
        vec3 contrib = min(crossingColors[i] * crossingAlphas[i], vec3(1.0));
        invResult *= (1.0 - contrib);
        invAlpha *= (1.0 - crossingAlphas[i]);
    }

    vec3 resultColor = 1.0 - invResult;
    float resultAlpha = 1.0 - invAlpha;

    if (resultAlpha <= 0.001) discard;

    fragColor = vec4(resultColor, resultAlpha);
}