#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ViewRot;

out vec2 vUv;
out vec3 vNormalView;
out vec3 vNormalWorld;
out vec3 vViewPos;
out vec3 vSeed;
out float vAlpha;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    vViewPos = view.xyz;
    gl_Position = ProjMat * view;

    vUv = UV0;
    vNormalWorld = Normal;
    // 顶点法线是世界空间，转到视觉空间做光照
    vNormalView = mat3(ViewRot) * Normal;

    // RGB = 随机种子 A/B + 生命进度，A = 粒子透明度
    vSeed = Color.rgb;
    vAlpha = Color.a;
}
