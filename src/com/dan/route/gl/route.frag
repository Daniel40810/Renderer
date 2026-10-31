#version 430 core
// route.frag — Platzhalterbild der Hülle (Wegenetz, seit Timmendorf Phase 1); die echte Darstellung folgt mit der Phase des Packages.
in vec2 vUV;
out vec4 frag;
uniform float uStrength;
uniform float uTime;
uniform float uSeed;
void main() {
    vec2 g = abs(fract(vUV * 8.0) - 0.5); float v = (1.0 - smoothstep(0.0, 0.03, min(g.x, g.y))) * (0.4 + 0.6 * uStrength);
    frag = vec4(mix(vec3(0.07,0.09,0.09), vec3(0.95,0.78,0.40), clamp(v, 0.0, 1.0)), 1.0);
}
