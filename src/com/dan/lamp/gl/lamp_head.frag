#version 430 core
// lamp_head.frag — additiv in den HDR-Puffer (seit Timmendorf Phase 11).
in vec2 vQ;
in vec3 vColor;
uniform float uGain;
out vec4 frag;
void main() {
    float q = dot(vQ, vQ);
    if (q > 9.0) discard;
    frag = vec4(vColor * exp(-0.5 * q) * uGain, 0.0);
}
