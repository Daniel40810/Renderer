// veil.frag — com.dan.fall: weicher Fleck für Schleier und Nebel, gefleckt, Licht mit Vorwärtsstreuung.
#include "fall.glsl"
in vec2 vUV;
in float vA;
in vec3 vP;
in float vR;
out vec4 frag;
void main() {
    float d2 = dot(vUV, vUV);
    if (d2 > 1.0 || vA < 0.002) discard;
    float n = fallNoise2(vUV * 2.5 + vR * 37.0, 11u);
    float a = vA * exp(-d2 * 2.6) * (0.55 + 0.6 * n) * 0.42;
    frag = fallFinish(vP, fallLight(vP, vec3(0.86, 0.89, 0.92), 0.0), a);
}
