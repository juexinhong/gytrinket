#version 150

in vec2 texCoord;

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

// Evaluate parabolic curve: f = L * (1 - |s/W|^3.6)
float curveValue(float s, float hw, float len) {
    float ns = abs(s) / hw;
    return len * (1.0 - pow(ns, 3.6));
}

// Approximate signed distance from a layer shape (parabolic + semicircle)
// Positive inside, negative outside
float shapeSDF(float s, float f, float hw, float len) {
    if (f >= 0.0) {
        // Parabolic part: g = f - curveValue(s, hw, len)
        float fBound = curveValue(s, hw, len);
        float g = f - fBound;
        // Gradient-based SDF approximation
        float ns = abs(s) / hw;
        float dfds = -3.6 * len / hw * sign(s) * pow(max(ns, 0.001), 2.6);
        float gradLen = sqrt(dfds * dfds + 1.0);
        return -g / gradLen;
    } else {
        // Semicircular bottom: center at origin, radius = hw
        return -(sqrt(s * s + f * f) - hw);
    }
}

// Glow factor: radial gradient from glow center (at semicircle edge)
float glowFactor(float s, float f) {
    // Glow center is at (0, -CenterHW) - the bottom of the center layer's semicircle
    float dx = s;
    float dy = f + CenterHW;
    float dist = sqrt(dx * dx + dy * dy);
    if (dist >= GlowRadius) return 0.0;
    return (1.0 - dist / GlowRadius) * GlowStrength;
}

// Apply glow: blend color towards white
vec3 applyGlow(vec3 color, float glow) {
    float strength = glow;
    return vec3(
        min(1.0, color.r + (1.0 - color.r) * strength),
        min(1.0, color.g + (1.0 - color.g) * strength),
        min(1.0, color.b + (1.0 - color.b) * strength)
    );
}

void main() {
    // Convert UV to local coordinates
    float s = (texCoord.x * 2.0 - 1.0) * MaxSpan;
    float f = texCoord.y * (MaxForward + MaxBack) - MaxBack;

    // Calculate SDF for each layer
    float centerSDF = shapeSDF(s, f, CenterHW, CenterLen);
    float colorSDF  = shapeSDF(s, f, ColorHW, ColorLen);
    float outerSDF  = shapeSDF(s, f, OuterHW, OuterLen);

    // Determine inner layer membership
    bool inCenter = centerSDF > 0.0;
    bool inColor  = colorSDF > 0.0;

    // Anti-aliasing edge width
    float edgeWidth = 0.015;

    // Edge anti-aliasing for the outer boundary
    float outerEdge = smoothstep(-edgeWidth, edgeWidth, outerSDF);

    // Bloom: smooth falloff outside the outer shape, seamlessly continuing the outer layer
    // Uses max(-outerSDF, 0) so bloomFade = 1.0 at the boundary, decreasing outward
    float bloomFade = 0.0;
    if (BloomMaxDist > 0.0) {
        float d = max(-outerSDF, 0.0); // distance outside the outer shape (0 inside or at boundary)
        float distFromOrigin = sqrt(s * s + f * f);
        float t = clamp(distFromOrigin / max(OuterLen, 0.001), 0.0, 1.0);
        float localBloomMax = BloomMaxDist * (1.0 - t);
        if (d < localBloomMax) {
            bloomFade = 1.0 - d / localBloomMax;
        }
    }

    // Combined outer + bloom alpha (max prevents alpha stacking)
    // Outer layer covers inside, bloom seamlessly extends outside - no overlap
    float outerA = OuterLayerColor.a * outerEdge;
    float bloomA = BloomColor.a * bloomFade;
    float outerBloomAlpha = max(outerA, bloomA);

    // Early exit if nothing visible
    if (outerBloomAlpha <= 0.001 && !inColor && !inCenter) discard;

    // Glow factor
    float glow = glowFactor(s, f);

    // Outer and bloom: no glow applied
    vec3 outerBloomColor = OuterLayerColor.rgb;

    // Source-over compositing from outside to inside
    vec3 finalColor = vec3(0.0);
    float finalAlpha = 0.0;

    // Outer + bloom layer (combined, no stacking)
    finalColor = outerBloomColor * outerBloomAlpha;
    finalAlpha = outerBloomAlpha;

    // Color layer (source over outer+bloom)
    if (inColor) {
        float srcA = ColorLayerColor.a;
        vec3 srcColor = applyGlow(ColorLayerColor.rgb, glow);
        finalColor = srcColor * srcA + finalColor * (1.0 - srcA);
        finalAlpha = srcA + finalAlpha * (1.0 - srcA);
    }

    // Center layer (source over color)
    if (inCenter) {
        float srcA = CenterColor.a;
        vec3 srcColor = applyGlow(CenterColor.rgb, glow);
        finalColor = srcColor * srcA + finalColor * (1.0 - srcA);
        finalAlpha = srcA + finalAlpha * (1.0 - srcA);
    }

    if (finalAlpha <= 0.001) discard;

    fragColor = vec4(finalColor, finalAlpha);
}
