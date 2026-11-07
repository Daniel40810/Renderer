#version 450
// lamp.frag — Lichthof: heller Kern, weicher Hof (Streuung im Auge und im Dunst), additiv.
in vec2 vQ;
in vec3 vRad;
in float vSize;
uniform float uGlow;
out vec4 frag;
void main() {
    float r = length(vQ);
    if (r > 1.0) discard;
    float core = exp(-r * r / max(vSize * vSize, 1e-4) * 2.0);
    float halo = exp(-r * 6.0) * 0.015 + exp(-r * 16.0) * 0.05;
    frag = vec4(vRad * (core * 0.5 + halo) * uGlow, 0.0);
}
