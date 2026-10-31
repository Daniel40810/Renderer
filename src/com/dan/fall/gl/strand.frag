// strand.frag — com.dan.fall: Wasser einer Strähne. Oben glatt und dicht, zum Zerfall hin streifig; die Muster wandern
// mit dem Wasser (was jetzt bei Flugzeit t ist, ging um uTime − t an der Kante ab). Bei Frost steht es still.
#include "fall.glsl"
in vec2 vUV;
in float vT;
in float vA;
in vec3 vP;
flat in int vI;
out vec4 frag;
uniform float uTime;
void main() {
    uint sd = uint(uStrandC[vI].x * 1048576.0);
    float flow = uTime * (1.0 - uIce) - vT;
    float across = vUV.x * 0.5 + 0.5;
    float n1 = fallNoise2(vec2(across * 7.0, flow * 2.2), sd);
    float n2 = fallNoise2(vec2(across * 19.0, flow * 6.0), sd + 7u);
    float edge = smoothstep(0.0, 0.22, across) * (1.0 - smoothstep(0.78, 1.0, across));
    float cov = edge * (0.45 + 0.55 * n1) * (0.75 + 0.5 * n2);
    float tb = uStrandB[vI].x;
    cov = mix(cov, edge * 0.9, 1.0 - smoothstep(0.15 * tb, tb, vT));
    float a = clamp(cov * vA, 0.0, 1.0);
    if (a < 0.003) discard;
    vec3 alb = mix(vec3(0.80, 0.85, 0.87), vec3(0.78, 0.88, 0.95), uIce);
    frag = fallFinish(vP, fallLight(vP, alb, 0.5 + 0.5 * uIce), a);
}
