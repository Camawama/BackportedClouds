#version 150

// Vanilla's position_tex_color_normal (the cloud shader of 1.20.1) with the distance the clouds of 1.21.6 and later
// fade by: the straight-line distance from the camera (not the fog shape's).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float vertexDistance;
out vec4 vertexColor;
out vec4 normal;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    texCoord0 = UV0;
    vertexDistance = length(viewPos.xyz);
    vertexColor = Color;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
