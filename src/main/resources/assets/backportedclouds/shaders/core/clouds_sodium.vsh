#version 150

// For Embeddium's cloud renderer (position and colour, the shade baked into the colour) with the distance the clouds
// of 1.21.6 and later fade by: the straight-line distance from the camera (Embeddium's own shader leaves the height
// out of it).

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec4 vertexColor;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexDistance = length(viewPos.xyz);
    vertexColor = Color;
}
