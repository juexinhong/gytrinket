#version 150

in vec2 texCoord;
in vec4 vertexColor;
in vec3 viewPos;

uniform sampler2D Sampler0;
uniform float Brightness;

out vec4 fragColor;

// Schlick's Fresnel for gem/crystal (IOR ≈ 1.8, R0 ≈ 0.08)
const float R0 = 0.08;

float schlickFresnel(float cosTheta) {
    return R0 + (1.0 - R0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

void main() {
    vec4 texColor = texture(Sampler0, texCoord);

    if (texColor.a < 0.01) {
        discard;
    }

    vec3 baseColor = texColor.rgb;
    float alpha = texColor.a * vertexColor.a;

    // 从顶点颜色 RGB 解码法线（已从 [-1,1] 打包到 [0,1]）
    vec3 norm = normalize(vertexColor.rgb * 2.0 - 1.0);

    // 视线方向（从片元指向相机）
    vec3 viewDir = normalize(-viewPos);

    // 确保法线朝向相机（双面渲染支持）
    if (dot(norm, viewDir) < 0.0) {
        norm = -norm;
    }

    float cosTheta = max(dot(viewDir, norm), 0.0);
    float fresnel = schlickFresnel(cosTheta);

    // === 统一宝石渲染（无辉光） ===

    // 主面（正面/背面）显示材质本身，强制为暗面
    // 侧面大部分也是暗面，只有小部分边缘为亮面
    // edgeFactor: 0 = 暗面（大面积），1 = 亮面（小面积，仅侧面边缘）
    float edgeFactor = smoothstep(0.3, 0.9, fresnel);

    // 暗面：保留材质颜色，亮度较低但始终可见
    // 亮面：亮度高，带宝石闪烁感
    float brightness = mix(0.3, 1.2, edgeFactor) * (1.0 + Brightness * 0.08);
    vec3 gemColor = baseColor * brightness;

    // 亮面镜面高光（宝石切面闪烁）
    vec3 specular = vec3(pow(fresnel, 2.0) * 0.4 * edgeFactor);

    // 透明度：暗面有一定不透明度，亮面更不透明
    float gemAlpha = alpha * mix(0.8, 0.9, edgeFactor);

    fragColor = vec4(gemColor + specular, gemAlpha);
}
