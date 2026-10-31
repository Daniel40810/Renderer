// veil.vert — com.dan.fall: ein Bündel des Schleiers als Fleck zum Auge, gestreckt in Bahnrichtung. Die Bahn rechnet
// fallParcel (geschlossene Form), die Bündel laufen um: Flugzeit = fract(Zeit / Lebensdauer + Phase) · Lebensdauer.
#include "fall.glsl"
layout(location = 0) in vec4 aB0;   // Strähne, Phase, seitlich, vorwärts
layout(location = 1) in vec4 aB1;   // Endgeschwindigkeit, Größe, Lebensdauer, Zufall
uniform mat4 uVP;
uniform float uTime, uAlpha;
uniform float uSizeGain;            // seit Phase 7: in der Ferne weniger, dafür größere Bündel (FallGl.lodShare)
uniform vec2 uNear;                 // seit Phase 8: größter Winkel eines Bündels (rad), Ausblenden unter … m (FallGl.nearCap)
out vec2 vUV;
out float vA;
out vec3 vP;
out float vR;
void main() {
    int i = int(aB0.x + 0.5);
    float T = max(aB1.z, 0.1), tb = uStrandB[i].x;
    float t = fract(uTime * (1.0 - uIce) / T + aB0.y) * T;
    vec4 p = fallParcel(i, t, aB1.x, aB0.z, aB0.w);
    vec4 p2 = fallParcel(i, t + 0.08, aB1.x, aB0.z, aB0.w);
    vec3 vel = (p2.xyz - p.xyz) / 0.08;
    float k = smoothstep(tb * 0.7, tb * 1.6, t) * (1.0 - smoothstep(T - 0.4, T, t));
    float size = aB1.y * (0.5 + 0.9 * smoothstep(tb, T, t));
    vec2 c = vec2(float(gl_VertexID & 1) * 2.0 - 1.0, float(gl_VertexID >> 1) * 2.0 - 1.0);
    vec3 toEye = normalize(uEye - p.xyz);
    vec3 up = vel - toEye * dot(vel, toEye);
    up = length(up) > 1e-4 ? normalize(up) : vec3(0.0, 1.0, 0.0);
    vec3 right = normalize(cross(up, toEye));
    float stretch = 1.0 + clamp(length(vel) * 0.05, 0.0, 1.5);
    size *= uSizeGain;
    float de = distance(uEye, p.xyz);
    if (uNear.x > 0.0) {
        size = min(size, uNear.x * de);
        stretch = min(stretch, 1.0 + uNear.x * de);
        k *= smoothstep(uNear.y * 0.3, uNear.y, de);
    }
    vec3 pos = p.xyz + right * c.x * size * 0.5 + up * c.y * size * 0.5 * stretch;
    if (uCourse == 1) pos += toEye * min(2.5, 0.003 * distance(uEye, p.xyz));
    vUV = c;
    vA = k * uAlpha * (1.0 - 0.85 * uIce);
    vP = pos;
    vR = aB1.w;
    gl_Position = vA < 0.002 ? vec4(2.0, 2.0, 2.0, 1.0) : uVP * vec4(pos, 1.0);   // unsichtbar: nichts rastern
}
