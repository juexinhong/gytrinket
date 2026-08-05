#version 150

in vec2 texCoord;
in vec3 viewPos;

uniform mat4 InvModelViewMat;
uniform vec3 SegStart;
uniform vec3 SegEnd;
uniform vec3 SegAxis;
uniform float RadiusStart;
uniform float RadiusEnd;
uniform vec4 CylColor;
uniform float Brightness;
uniform float BoxRad;
uniform float RenderMode; // 0 = 泛光底层，1 = 核心层

out vec4 fragColor;

// 泛光层参数：蓝色、亮度15（跟随闪电段，消退时从15降至10）、跟随闪电段透明度
const vec3 BLOOM_COLOR = vec3(0.35, 0.65, 1.0);
const vec3 WHITE_COLOR = vec3(1.0, 1.0, 1.0);
const float BLOOM_INTENSITY = 0.08;
const float BLOOM_SURFACE_OFFSET = 0.05;
const float BLOOM_FALLOFF = 6.0;
const float GLOW_RANGE = 0.4;

// 胶囊体 SDF（圆柱 + 球形端帽）：正=内部，负=外部
float sdCapsule(vec3 p, vec3 a, vec3 b, float ra, float rb) {
    vec3 ab = b - a;
    float len = length(ab);
    if (len < 1e-4) {
        return mix(ra, rb, 0.5) - length(p - a);
    }
    vec3 ax = ab / len;
    float t = clamp(dot(p - a, ax), 0.0, len);
    vec3 q = a + ax * t;
    return mix(ra, rb, t / len) - length(p - q);
}

void main() {
    // 相机相对坐标（相机在原点）
    vec3 ro = (InvModelViewMat * vec4(viewPos, 1.0)).xyz;
    vec3 rd = normalize(ro);

    float maxRadius = max(RadiusStart, RadiusEnd);
    float maxDist = length(SegEnd - SegStart) + BoxRad * 2.0 + 1.0;
    float baseStep = 0.35;
    float minStep = 0.008;

    vec3 accColor = vec3(0.0);
    float transmittance = 1.0;
    float t = 0.0;

    for (int i = 0; i < 220; i++) {
        vec3 p = ro + rd * t;

        float d = sdCapsule(p, SegStart, SegEnd, RadiusStart, RadiusEnd);
        float radial = length(p - (SegStart + (SegEnd - SegStart) * clamp(dot(p - SegStart, SegEnd - SegStart) / max(length(SegEnd - SegStart) * length(SegEnd - SegStart), 1e-6), 0.0, 1.0)));

        if (RenderMode >= 0.5) {
            // ===== 核心层：只累积核心（不透明，覆盖下方的泛光底层） =====
            if (d >= 0.0) {
                float depth = clamp(d / max(maxRadius, 0.001), 0.0, 1.0);
                float density = minStep * 70.0 * CylColor.a * (0.5 + 0.5 * (1.0 - depth));
                vec3 ccol = CylColor.rgb * (1.0 + Brightness * 0.08);
                accColor += ccol * density * transmittance;
                transmittance *= (1.0 - density);
            }
        } else {
            // ===== 泛光底层：只累积泛光（在核心层之下，被所有闪电段覆盖） =====
            if (d < 0.0) {
                vec3 nWorld = normalize(p - (SegStart + (SegEnd - SegStart) * clamp(dot(p - SegStart, SegEnd - SegStart) / max(length(SegEnd - SegStart) * length(SegEnd - SegStart), 1e-6), 0.0, 1.0)));
                float rim = pow(1.0 - abs(dot(rd, nWorld)), 3.0);
                // 端帽抑制：表面法线含轴向分量（端帽球形区）时抑制描边，避免连接处出现圆形分割
                float axialN = abs(dot(nWorld, SegAxis));
                rim *= smoothstep(0.5, 0.15, axialN);

                float dist = -d;
                float surfaceProfile = smoothstep(0.0, BLOOM_SURFACE_OFFSET, dist) * exp(-dist * BLOOM_FALLOFF);
                float radialFade = exp(-(radial / GLOW_RANGE) * 2.5);
                float bloomDensity = rim * surfaceProfile * radialFade * baseStep * Brightness * BLOOM_INTENSITY * CylColor.a;
                if (bloomDensity > 0.0001) {
                    accColor += BLOOM_COLOR * bloomDensity * transmittance;
                    transmittance *= (1.0 - bloomDensity);
                }
            }
        }

        if (transmittance < 0.01) break;

        float step = max(abs(d) * 0.5, minStep);
        step = min(step, baseStep);
        t += step;
        if (t > maxDist) break;
    }

    float alpha = 1.0 - transmittance;
    if (alpha <= 0.01) discard;
    fragColor = vec4(accColor, alpha);
}
