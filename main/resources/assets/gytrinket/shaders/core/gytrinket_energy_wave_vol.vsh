#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;
out vec3 viewPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    // 传递视空间位置（相机在原点）
    viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
}
