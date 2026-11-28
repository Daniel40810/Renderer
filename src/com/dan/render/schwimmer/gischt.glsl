// gischt.glsl – render.schwimmer (Seegang Werkbuch, Datei 104, S8a, SF158 A): die Gischt am Bug. Ein Tropfen aus seinem
// Bündel, Zeile für Zeile wie com.dan.spray.Parcel.particle (dort gl/spray_common.glsl für OpenGL); geschlossene Lösung
// mit Schwerkraft und linearer Luftreibung, die ihn an den Wind angleicht:
//   x(t) = x0 + u t + (v0 − u)(1 − e^(−k t)) / k,  u = Wind + g / k (nach unten).
// Raum relativ zum Auge: x Ost, y oben, z Süd (wie schwimmer.glsl), Erdkrümmung erst beim Zeichnen.
//
// Bindungen: 0 Kopf, 1 Bündel (je Bündel sechs vec4), dann je Shader.
#include "/com/dan/core/gpu/luft.glsl"

// je Bündel: a = Ort beim Wurf relativ zum Auge (m), Alter (s); b = Geschwindigkeit beim Wurf (m/s), Lebensdauer (s);
// c = Reibung k (1/s), Streuung des Orts (m), Streuung der Geschwindigkeit (m/s), Anteil der Schwerkraft;
// d = Saat (Bits), Größe (m), Höhe des Wassers relativ zum Auge (m), Masse; e = quer (x, z), nach außen (x, z);
// f = Wind (x, z) (m/s), Dauer des Wurfs (s), Art (0 Spritzwasser, 1 Nebel)
struct Buendel { vec4 a, b, c, d, e, f; };

layout(set = 0, binding = 0, std430) readonly buffer Kopf {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung)
    vec4 augeHi;        // w 1/(2R')
    vec4 werte;         // x Belichtungszeit (s), y Tropfen je Spritzwasser, z Tropfen je Nebel, w Auslöschung der Luft β (1/m)
    vec4 mehr;          // x Augenhöhe über NHN (m), y Deckkraft (0 … 1), z Höhe des Auges über dem Boden für die Luft (m), w Winkel eines Bildpunkts (rad)
    vec4 vornV;         // Blickachse; w nah (m)
};
layout(set = 0, binding = 1, std430) readonly buffer Buendelpuffer { Buendel bu[]; };
#ifndef GISCHT_OHNE_LICHT
layout(set = 0, binding = 2, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 3) uniform sampler2D durch;
layout(set = 0, binding = 4) uniform sampler2D ansichtT;

float gHg(float c, float g) { return (1.0 - g * g) / (4.0 * L_PI * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }

vec3 gSonne(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}

// Leuchtdichte eines Tropfens (cd/m², vor der Vorbelichtung) in Blickrichtung V (vom Auge zum Tropfen): Sonne und Mond mit
// der Durchlässigkeit der Luft, nach Henyey–Greenstein gestreut (g 0,72 Spritzwasser, 0,55 Nebel; wie com.dan.spray), dazu
// der Himmel als mittlere Leuchtdichte aus fünf Richtungen; Albedo 0,9 [G]. Seit L6a im Eckenshader (Befund 2: im
// Bildpunkt kostete es 0,45 ms).
vec3 gLicht(vec3 V, int kind) {
    float g = kind == 1 ? 0.55 : 0.72, h = max(0.5, mehr.z);
    vec3 Es = gSonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h), Em = gSonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h);
    vec3 Lh = (ansicht(ansichtT, vec3(0.0, 1.0, 0.0), hw.sonne.w) + ansicht(ansichtT, vec3(1.0, 0.05, 0.0), hw.sonne.w) + ansicht(ansichtT, vec3(-1.0, 0.05, 0.0), hw.sonne.w)
            + ansicht(ansichtT, vec3(0.0, 0.05, 1.0), hw.sonne.w) + ansicht(ansichtT, vec3(0.0, 0.05, -1.0), hw.sonne.w)) * 0.2;
    return 0.9 * (Es * gHg(dot(V, hw.sonne.xyz), g) + Em * gHg(dot(V, hw.mond.xyz), g) + Lh);
}
#endif

const float G_G = 9.81;

uint gHash(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float gRnd(uint seed, int i, int j) { return float(gHash(gHash(uint(i * 16 + j)) ^ seed) >> 8u) * (1.0 / 16777216.0); }

vec3 gBewegung(vec3 p, vec3 v0, float k, float dt, vec2 wind, float grav) {
    vec3 u = vec3(wind.x, -G_G * grav / k, wind.y);
    float e = (1.0 - exp(-k * dt)) / k;
    return p + u * dt + (v0 - u) * e;
}

// Tropfen i des Bündels P: Lage, Geschwindigkeit, Größe, lebt (wie Parcel.particle zum Alter P.a.w)
void gTropfen(Buendel P, int i, out vec3 pos, out vec3 vel, out float size, out bool alive) {
    int kind = int(P.f.w + 0.5);
    uint seed = floatBitsToUint(P.d.x);
    float delay = gRnd(seed, i, 0) * P.f.z;
    float dt = P.a.w - delay;
    float r2 = gRnd(seed, i, 1), r3 = gRnd(seed, i, 2);
    bool cloudy = kind == 1 || kind == 3;
    float k = kind == 0 ? P.c.x * (0.5 + 1.5 * r2 * r2) : cloudy ? P.c.x : P.c.x * (0.6 + 0.8 * r2);
    float f = kind == 0 ? 0.35 + 0.8 * sqrt(r3) : 1.0;
    float sa = (gRnd(seed, i, 3) - 0.5) * 2.0 * P.c.y, sb = gRnd(seed, i, 4) * P.c.y * 0.3, sc = (gRnd(seed, i, 5) - 0.5) * P.c.y * 0.3;
    vec3 p0 = P.a.xyz + vec3(P.e.x * sa + P.e.z * sb, sc, P.e.y * sa + P.e.w * sb);
    vec3 j = (vec3(gRnd(seed, i, 6), gRnd(seed, i, 7), gRnd(seed, i, 8)) - 0.5) * 2.0 * P.c.z;
    vec3 v0 = P.b.xyz * f + j;
    float tt = max(dt, 0.0);
    pos = gBewegung(p0, v0, k, tt, P.f.xy, P.c.w);
    vec3 u = vec3(P.f.x, -G_G * P.c.w / k, P.f.y);
    vel = u + (v0 - u) * exp(-k * tt);
    size = P.d.y * (0.6 + 0.8 * gRnd(seed, i, 9)) * (kind == 1 ? 1.0 + 0.3 * tt : kind == 3 ? 1.0 + 0.35 * tt : 1.0);
    alive = dt >= 0.0 && dt <= P.b.w && (cloudy || pos.y >= P.d.z);
}

vec3 gKrumm(vec3 r) { r.y -= dot(r.xz, r.xz) * augeHi.w; return r; }
