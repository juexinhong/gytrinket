#version 150

in vec3 Position;
in vec2 UV;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 viewPos;

void main() {
    vec4 viewPos4 = ModelViewMat * vec4(Position, 1.0);
    viewPos = viewPos4.xyz;
    gl_Position = ProjMat * viewPos4;
    texCoord = UV;
    vertexColor = Color;
}
