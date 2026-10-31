// strand.vert — com.dan.fall: eine Strähne als Band zum Auge, von der Kante bis zum Aufprall. Ecke: Strähne, Lage
// 0..1 entlang der Flugzeit (oben dichter), Seite −1..1. Nach dem Zerfall wird das Band breiter und blendet aus;
// den Rest trägt der Schleier (veil.vert). Seit Phase 7 (Lauf über Fels): wo das Wasser auf dem Fels liegt, bleibt das
// Band sichtbar und breiter (weißes, gleitendes Wasser), und es rückt ein Stück zum Auge, damit die Feinform der Wand
// es nicht verdeckt.
#include "fall.glsl"
layout(location = 0) in vec3 aS;
uniform mat4 uVP;
uniform float uTime;
out vec2 vUV;
out float vT;
out float vA;
out vec3 vP;
flat out int vI;
void main() {
    int i = int(aS.x + 0.5);
    vec4 B = uStrandB[i];
    float t = B.z * (uCourse == 1 ? aS.y : pow(aS.y, 1.6));
    vec4 p = fallParcel(i, t, B.y, 0.0, 0.0);
    float onRock = p.w < 0.0 ? 1.0 : 0.0;
    vec4 p2 = fallParcel(i, t + 0.05, B.y, 0.0, 0.0);
    vec3 tg = normalize(p2.xyz - p.xyz + vec3(0.0, -1e-4, 0.0));
    vec3 toEye = normalize(uEye - p.xyz);
    vec3 side = cross(tg, toEye);
    side = length(side) > 1e-4 ? normalize(side) : uFallAlong;
    float tb = B.x;
    float grow = uCourse == 1 ? 1.0 + 0.25 * onRock + 0.08 * min(max(t - tb, 0.0), 4.0) : 1.0 + 0.5 * max(t - tb, 0.0) / max(tb, 0.3);
    vec3 pos = p.xyz + side * aS.z * B.w * 0.5 * grow;
    if (uCourse == 1) pos += toEye * min(3.0, 0.004 * distance(uEye, p.xyz));
    vA = mix(1.0 - smoothstep(tb, tb + 2.5, t), 1.0, max(uIce, onRock * 0.85));
    vUV = vec2(aS.z, t);
    vT = t;
    vP = pos;
    vI = i;
    gl_Position = uVP * vec4(pos, 1.0);
}
