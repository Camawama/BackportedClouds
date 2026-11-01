#version 150

// For Embeddium's cloud renderer, with the horizon fade of 1.21.6 and later: no tint toward the fog colour (Embeddium's
// own shader mixes 30% of it in); each cloud's opacity falls in a straight line with its distance from the camera,
// gone at FogEnd (the cloud fog distance). Vanilla: color.a *= 1 - linear_fog_value(vertexDistance, 0, FogCloudsEnd).

uniform vec4 ColorModulator;
uniform float FogEnd;
uniform float PlanetBelowAlpha;

in vec4 vertexColor;
in float vertexDistance;

out vec4 fragColor;

// The clouds fade into VS Genesis' planet below as it fades in (Backported Clouds 1.0.7): alpha is the planet's own
// fade-in, the cloud's opacity goes with it from 32 blocks off on (all of it from 96), as the far world fades into the
// planet (Space Events), and the planet's own painted clouds take their place.
float planet_below_fade(float distance, float alpha) {
    return 1.0 - clamp(alpha, 0.0, 1.0) * smoothstep(32.0, 96.0, distance);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    color.a *= 1.0 - clamp(vertexDistance / max(FogEnd, 1.0), 0.0, 1.0);
    color.a *= planet_below_fade(vertexDistance, PlanetBelowAlpha);
    fragColor = color;
}
