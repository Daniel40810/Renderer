// bewuchs.glsl – render.bewuchs (seit P12c, Werkbuch HalligHooge Teil A, 12.13, F135–F137 A): Gehölze und Halme.
// Raum wie render.bau: Lage im Raum der Bauten (x = Ost − e0, y = Höhe m NHN, z = n0 − Nord), relativ zum Auge mit
// Erdkrümmung (Senkung r²/2R').
//
// Bindungen der Gehölze (Bild und Schatten): 0 Scheitel, 1 Pflanzen, 2 Kopf, 3 Liste der Instanzen, 4 Bau/Schattenwerte;
// nur im Bild: 5 Himmel/Werte, 6 Himmel/Durchlaessigkeit, 7 Himmelslicht (Kugelflächenfunktionen), 8 Himmel/Ansicht,
// 9 Bau/Schatten.
#include "/com/dan/core/gpu/luft.glsl"

struct Pflanze {
    vec4 p;     // x, Boden (m NHN), z, Drehung (rad)
    vec4 s;     // Streckung x, y, z, Phase des Winds
    vec4 o;     // Versatz der Krone nach x und z (m, oben voll), Höhe des Netzes (m), 0
};

layout(set = 0, binding = 0, std430) readonly buffer Scheitel { vec4 sch[]; };   // je Scheitel: Lage + Biegung, Normale + Teil, Farbe + Phase
layout(set = 0, binding = 1, std430) readonly buffer Pflanzen { Pflanze pfl[]; };
layout(set = 0, binding = 2, std430) readonly buffer Kopf {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung)
    vec4 augeHi;        // Auge im Raum der Bauten (float), w 1/(2R')
    vec4 augeLo;        // Rest des Auges, w Augenhöhe (m NHN)
    vec4 wind;          // Wind 10 m (m/s) nach x und z, Zeit (s), Böigkeit
};
layout(set = 0, binding = 3, std430) readonly buffer Liste { int liste[]; };

vec3 rel(vec3 p) {
    vec3 r = (p - augeHi.xyz) - augeLo.xyz;
    r.y -= dot(r.xz, r.xz) * augeHi.w;
    return r;
}

// Ein Scheitel des Netzes i auf der Pflanze q: gestreckt, Krone versetzt, gedreht, im Wind gebogen [G].
// Biegung b (0 Fuß … 1 Spitze), Teil t (0 Rinde, 1 Laub), Phase f. Ergebnis im Raum der Bauten; n: Normale.
vec3 welt(int i, Pflanze q, out vec3 n) {
    vec4 a = sch[3 * i], b = sch[3 * i + 1], c = sch[3 * i + 2];
    vec3 v = a.xyz * q.s.xyz;
    float hf = clamp(a.y / max(q.o.z, 0.1), 0.0, 1.0);
    v.xz += q.o.xy * smoothstep(0.1, 1.0, hf);
    float cy = cos(q.p.w), sy = sin(q.p.w);
    v.xz = vec2(cy * v.x - sy * v.z, sy * v.x + cy * v.z);
    vec3 nn = b.xyz / q.s.xyz;
    nn.xz = vec2(cy * nn.x - sy * nn.z, sy * nn.x + cy * nn.z);
    n = normalize(nn);
    // Wind: mittlere Biegung nach Lee mit U² (Gehölze der Hallig sind steif und windgeformt, [G]), dazu Schwanken und
    // für das Laub Flattern
    vec2 w = wind.xy;
    float U = length(w);
    if (U > 0.1) {
        vec2 d = w / U;
        float t = wind.z, hoehe = a.y * q.s.y;
        float mitte = min(0.0009 * U * U, 0.08), boe = 1.0 + wind.w * sin(0.31 * t + q.s.w * 6.283 + 0.013 * dot(q.p.xz, d));
        float schwank = 0.25 * mitte * sin(t * (1.1 + 0.4 * fract(q.s.w * 7.0)) + q.s.w * 6.283);
        v.xz += d * (mitte * boe + schwank) * hoehe * a.w;
        if (b.w > 0.5) {
            float fl = 0.012 * min(U, 20.0) * sin(t * 7.0 + c.w * 40.0);
            v += vec3(d.y, 0.7, -d.x) * fl * a.w;
        }
    }
    return q.p.xyz + v;
}
