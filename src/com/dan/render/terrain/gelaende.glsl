// gelaende.glsl – render.terrain (P6b, Werkbuch HalligHooge Teil A, 12.7): Stufen der Clipmap auf der Karte, wie
// com.dan.clipmap.gl/clipmap.glsl, aber aus Puffern und relativ zum Auge. Raum: x Ost, z Süd (Gitter UTM 32), y oben.
// Daten: fünf Ebenen (0 Höhe m NHN, 1–3 Albedo R G B trocken, 4 Anteil Watt) je Stufe als Ring T × T; Gitterpunkt
// (i, j) einer Stufe steht an (i mod T, j mod T). Stufen: Kopf und je Stufe Index des Scheitels (0, 0), seine Lage
// relativ zum Auge, Zellweite und das Rechteck der feineren Stufe (relativ zum Auge).
//
// Bindungen (gleich in allen Shadern des Moduls): 0 Daten, 1 Sicht der Sonne je Scheitel, 2 Stufen, 3 Himmel/Werte,
// 4 Himmel/Durchlaessigkeit, 5 Himmelslicht, 6 Nässe (water.wetAge eines Gitters, s); gelaende.frag dazu 7 Boden aus
// dem Sediment (P9b: je Zelle Rippelhöhe, Rippellänge, Richtung, Schlickanteil), gelaende.vert 8 Bodenänderung (P9c),
// gelaende.frag 9 Flugsand (P10c: aeol.q je Zelle, nach Ost und nach Nord).
#include "/com/dan/core/gpu/luft.glsl"

struct Stufe {
    ivec4 idx;      // i0, j0 (Index des Scheitels (0, 0)), 1 = es gibt eine gröbere Stufe, 0
    vec4 geo;       // Lage des Scheitels (0, 0) relativ zum Auge x, z; Zellweite; 0
    vec4 innen;     // Rechteck der feineren Stufe relativ zum Auge (xmin, zmin, xmax, zmax); xmin > xmax: keins
};

layout(set = 0, binding = 0, std430) readonly buffer Daten { float d[]; };
#ifdef SICHT_SCHREIBEN
layout(set = 0, binding = 1, std430) writeonly buffer Sicht { float sicht[]; };
#else
layout(set = 0, binding = 1, std430) readonly buffer Sicht { float sicht[]; };
#endif
layout(set = 0, binding = 2, std430) readonly buffer Stufen {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung)
    vec4 auge;          // Augenhöhe (m NHN), 1/(2 R') für die Erdkrümmung, T, M
    vec4 lc;            // Zahl der Stufen, Vierecke je Block, Stufen mit Schatten, Tiefe des Schattens (m)
    vec4 nassLage;      // Südwestecke des Gitters der Nässe relativ zum Auge (Ost, Nord), dx, dy
    ivec4 nassN;        // nx, ny, 1 = Nässe vorhanden, 0
    vec4 nass;          // Restnässe, Abdunklung, Halbwertszeit (s), 1 = das Bild der Nässe trägt die Sättigung (surf.wet) statt wetAge
    vec4 bodLage;       // seit P9b: Südwestecke des Gitters des Sediments relativ zum Auge (Ost, Nord), Zelle (m), 1 = Boden vorhanden
    ivec4 bodN;         // nx, ny, 0, 0
    vec4 bod;           // Abdunklung durch Schlick, Schlickanteil für die volle Abdunklung, Stärke des Reliefs der Rippel, seit S6d-1: 1 = Wellenrippel gesetzt
    vec4 flugLage;      // seit P10c: Südwestecke des Gitters des Flugsands relativ zum Auge (Ost, Nord), Zelle (m), 1 = Flugsand vorhanden
    ivec4 flugN;        // nx, ny, 0, 0
    vec4 flug;          // senkrechte optische Dicke des Schleiers je Fluss (m s/kg), Zeit (s), Stärke der Streifen (0 … 1), Zug der Streifen (m/s)
    vec4 flugFarbe;     // Albedo des trockenen Sands (R, G, B), 0
    // seit S6c-2 (Seegang Werkbuch 82, SF93 A): das Rechteck des Auflaufs (physics.swash, Bild über lauf.Auflauf)
    vec4 swLage;        // Ecke der Zelle (0, 0) relativ zum Auge (Ost, Nord), Richtung x (quer, landwärts) Ost, Nord
    vec4 swAchse;       // Richtung y (längs) Ost, Nord, Zelle (m), Kontur des Spülsaums (m)
    ivec4 swN;          // nx, ny, 1 = Bild vorhanden, Zeit der Karte (s, Bits von float; seit S6c-2b)
    Stufe st[];
};
layout(set = 0, binding = 3, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 4) uniform sampler2D durch;

int T() { return int(auge.z); }
int M() { return int(auge.w); }
int Lc() { return int(lc.x); }

// i mod T auch für negative i (wie clipmap.glsl, Befund Lituya Phase 4)
int ring(int i, int t) { return i - t * int(floor(float(i) / float(t))); }

int platz(int ebene, int L, int i, int j) {
    int t = T();
    return ((ebene * Lc() + L) * t + ring(j, t)) * t + ring(i, t);
}
float wert(int ebene, int L, int i, int j) { return d[platz(ebene, L, i, j)]; }

// Wert der gröberen Stufe am Gitterpunkt (i, j) dieser Stufe: bilinear zwischen ihren Punkten, wie Clipmap.coarseAt.
float grob(int ebene, int L, int i, int j) {
    int ci = i >> 1, cj = j >> 1, G = L + 1;
    bool ox = (i & 1) != 0, oz = (j & 1) != 0;
    float a = wert(ebene, G, ci, cj);
    if (!ox && !oz) return a;
    if (ox && !oz) return 0.5 * (a + wert(ebene, G, ci + 1, cj));
    if (!ox) return 0.5 * (a + wert(ebene, G, ci, cj + 1));
    return 0.25 * (a + wert(ebene, G, ci + 1, cj) + wert(ebene, G, ci, cj + 1) + wert(ebene, G, ci + 1, cj + 1));
}

struct Scheitel {
    vec3 rel;       // relativ zum Auge, mit Erdkrümmung
    vec3 n;         // Normale mit Übergang
    float h;        // Höhe (m NHN) mit Übergang, ohne Krümmung
    float a;        // Übergang 0 innen … 1 am Rand
    vec3 albedo;
    float watt;
};

// Übergang wie Clipmap.morph
float uebergang(int k, int l) {
    int m = M(), hf = (m - 1) / 2, w = max(2, (m - 1) / 10);
    int dd = max(abs(k - hf), abs(l - hf));
    return clamp(float(dd - (hf - w - 1)) / float(w), 0.0, 1.0);
}

Scheitel scheitel(int L, int k, int l) {
    Stufe s = st[L];
    int m = M(), i = s.idx.x + k, j = s.idx.y + l;
    float c = s.geo.z;
    Scheitel o;
    float h = wert(0, L, i, j);
    int kl = max(k - 1, 0), kr = min(k + 1, m - 1), lu = max(l - 1, 0), ld = min(l + 1, m - 1);
    float gx = (wert(0, L, s.idx.x + kr, j) - wert(0, L, s.idx.x + kl, j)) / (float(kr - kl) * c);
    float gz = (wert(0, L, i, s.idx.y + ld) - wert(0, L, i, s.idx.y + lu)) / (float(ld - lu) * c);
    vec3 n = normalize(vec3(-gx, 1.0, -gz));
    vec3 alb = vec3(wert(1, L, i, j), wert(2, L, i, j), wert(3, L, i, j));
    float wt = wert(4, L, i, j);
    float a = 0.0;
    if (s.idx.z == 1) {
        a = uebergang(k, l);
        if (a > 0.0) {
            float hc = grob(0, L, i, j);
            float cx = (grob(0, L, i + 2, j) - grob(0, L, i - 2, j)) / (4.0 * c);
            float cz = (grob(0, L, i, j + 2) - grob(0, L, i, j - 2)) / (4.0 * c);
            h = h + (hc - h) * a;
            n = normalize(mix(n, normalize(vec3(-cx, 1.0, -cz)), a));
            alb = mix(alb, vec3(grob(1, L, i, j), grob(2, L, i, j), grob(3, L, i, j)), a);
            wt = mix(wt, grob(4, L, i, j), a);
        }
    }
    vec2 xz = s.geo.xy + vec2(float(k), float(l)) * c;
    o.rel = vec3(xz.x, h - dot(xz, xz) * auge.y - auge.x, xz.y);
    o.n = n;
    o.h = h;
    o.a = a;
    o.albedo = alb;
    o.watt = wt;
    return o;
}

// Liegt der Ort im Rechteck der feineren Stufe? Dann zeichnet diese ihn.
bool verdeckt(int L, vec2 xz) {
    vec4 r = st[L].innen;
    return xz.x > r.x && xz.x < r.z && xz.y > r.y && xz.y < r.w;
}

// Höhe (m NHN) an einem Ort relativ zum Auge aus der feinsten Stufe ab L0, die ihn innen hat (bilinear, ohne Übergang).
float hoeheBei(vec2 xz, int L0) {
    int m = M();
    for (int L = L0; L < Lc(); L++) {
        Stufe s = st[L];
        vec2 u = (xz - s.geo.xy) / s.geo.z;
        if (u.x < 1.0 || u.y < 1.0 || u.x > float(m - 3) || u.y > float(m - 3)) continue;
        ivec2 p = ivec2(floor(u));
        vec2 f = u - vec2(p);
        int i = s.idx.x + p.x, j = s.idx.y + p.y;
        float a = wert(0, L, i, j), b = wert(0, L, i + 1, j), c = wert(0, L, i, j + 1), e = wert(0, L, i + 1, j + 1);
        return mix(mix(a, b, f.x), mix(c, e, f.x), f.y);
    }
    return -1e9;
}
