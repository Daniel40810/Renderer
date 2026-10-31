// pool.frag — com.dan.fall: Schaumringe, die vom Aufprall nach außen laufen.
#include "fall.glsl"
in vec2 vUV;
in vec3 vP;
flat in int vI;
out vec4 frag;
uniform float uTime, uFoam;
void main() {
    float r = length(vUV);
    if (r > 1.0) discard;
    float ang = atan(vUV.y, vUV.x);
    uint sd = uint(uStrandC[vI].x * 1048576.0) + 3u;
    float n = fallNoise2(vec2(ang * 3.0 + 10.0, r * 6.0 - uTime * (1.0 - uIce) * 1.4), sd);
    float n2 = fallNoise2(vec2(vUV * 9.0 + uTime * 0.3), sd + 1u);
    float a = (1.0 - smoothstep(0.35, 1.0, r)) * smoothstep(0.35, 0.75, n * 0.7 + n2 * 0.5) * uFoam * (1.0 - uIce);
    if (a < 0.003) discard;
    vec3 col = vec3(0.86, 0.9, 0.9) * (uSunColor * max(uSunDir.y, 0.0) * 0.3 + uAmbient * 1.4);
    frag = fallFinish(vP, col, a);
}
