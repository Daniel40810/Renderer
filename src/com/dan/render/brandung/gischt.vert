#version 460
// gischt.vert – render.brandung (P13c, F162 A): Tropfen der Gischt als kleine Scheiben zum Auge. Je Bündel (vier vec4,
// core.szene.Brandung.buendel) werte.z Tropfen; ein Tropfen ist eine Funktion von Bündel, Nummer und Alter wie
// com.dan.spray.Parcel.particle: Schwerkraft und lineare Reibung k zum Wind, x(t) = x0 + u t + (v0 − u)(1 − e^(−k t))/k,
// u = Wind + Schwerkraft/k. Streuung von Ort und Geschwindigkeit nach Zufall aus dem Samen [G].
#include "/com/dan/render/brandung/brandung.glsl"
layout(set = 0, binding = 6, std430) readonly buffer Buendel { vec4 bu[]; };
layout(location = 0) out vec2 vUv;
layout(location = 1) out float vAlpha;
layout(location = 2) out vec3 vRel;

uint hashU(uint x) {
    x ^= x >> 16; x *= 0x7feb352du; x ^= x >> 15; x *= 0x846ca68bu; x ^= x >> 16;
    return x;
}
float rnd(uint seed, int i, int j) { return float(hashU(hashU(uint(i * 16 + j)) ^ seed) >> 8) * (1.0 / 16777216.0); }

void main() {
    int nt = int(werte.z), q = gl_VertexIndex / 6, ecke = gl_VertexIndex % 6;
    int b = q / nt, i = q % nt;
    vec4 A = bu[4 * b], B = bu[4 * b + 1], C = bu[4 * b + 2], D = bu[4 * b + 3];
    uint seed = uint(D.w) & 0xFFFFu;
    int art = int(D.w) >> 16;
    float alter = A.w - rnd(seed, i, 0) * 0.15;
    float r2 = rnd(seed, i, 1), r3 = rnd(seed, i, 2);
    float k = B.w * (art == 0 ? 0.5 + 1.5 * r2 * r2 : 0.6 + 0.8 * r2);
    float f = art == 0 ? 0.35 + 0.8 * sqrt(r3) : 1.0;
    vec3 tang = vec3(D.x, 0.0, D.y);
    float a = (rnd(seed, i, 3) - 0.5) * 2.0 * C.z;
    vec3 p0 = A.xyz + tang * a + vec3(0.0, (rnd(seed, i, 5) - 0.5) * 0.3 * C.z, 0.0);
    vec3 j = (vec3(rnd(seed, i, 6), rnd(seed, i, 7), rnd(seed, i, 8)) - 0.5) * 2.0 * C.w;
    vec3 v0 = B.xyz * f + j;
    vec3 u = vec3(C.x, -9.81 / k, C.y);
    float tt = max(alter, 0.0), e = (1.0 - exp(-k * tt)) / k;
    vec3 p = p0 + u * tt + (v0 - u) * e;
    float leben = art == 0 ? 2.5 : 3.0;
    bool lebt = alter >= 0.0 && alter <= leben && (art != 0 || p.y >= A.y - 0.3);
    float groesse = D.z * (0.6 + 0.8 * rnd(seed, i, 9)) * (art == 2 ? 1.0 + 0.5 * tt : 1.0);
    if (!lebt) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vUv = vec2(0.0); vAlpha = 0.0; vRel = vec3(0.0); return; }
    vec3 rel = lageRel(p);
    // mindestens ein Bildpunkt groß, die Deckung dafür kleiner (Fläche bleibt) [G]
    float px = obenV.w * length(rel), g = max(groesse, 1.2 * px);
    vAlpha = (groesse / g) * (groesse / g) * (1.0 - smoothstep(0.6 * leben, leben, tt)) * (art == 2 ? 0.35 : 0.8);
    const vec2 E[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));
    vUv = E[ecke];
    vRel = rel;
    gl_Position = vp * vec4(rel + (rechtsV.xyz * E[ecke].x + obenV.xyz * E[ecke].y) * g, 1.0);
}
