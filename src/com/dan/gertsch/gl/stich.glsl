// stich.glsl — gemeinsam für die Shader des Holzschnitts (StichGl hängt es vor jede Datei): Zufall wie Zufall.java,
// Punkte wie Punkte.java (Rang, Form, Abstand, Einzug), Wolken und Korn wie Stich.java, Ton aus der Tabelle.

// ------------------------------------------------------------------ Zufall (PCG-Hash, wie Zufall.java)
uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float eins(uint h) { return float(pcg(h) >> 8u) * (1.0 / 16777216.0); }

// ------------------------------------------------------------------ Punkte (wie Punkte.java)
uniform sampler2D uBlau;        // Kachel der Blue-Noise-Ränge, 256 × 256, R32F
uniform uint uSeedP;            // pcg(seed) der Punkte
uniform float uOrdnung, uZittern, uGroesse, uStreuung, uLaenge;
uniform float uKurve[257];      // Eichkurve: Anteil Papier, wenn alle Punkte mit Rang unter k / 256 gestochen sind
uniform int uReich;             // so viele Zellen reicht ein Punkt in die Nachbarn
uniform float uZelle;           // Kante einer Zelle (px des Drucks)

uint zelle(ivec2 c) { return pcg(uint(c.x) + pcg(uint(c.y) + uSeedP)); }
float zug(uint h, uint k) { return eins(h + k * 0x9E3779B9u); }

float mischen(float b, float u, float o) {
    float a = min(o, 1.0 - o), c = max(o, 1.0 - o);
    if (a < 1e-4) return o >= 0.5 ? b : u;
    float v = o * b + (1.0 - o) * u;
    if (v < a) return v * v / (2.0 * a * c);
    if (v <= c) return (v - 0.5 * a) / c;
    float q = 1.0 - v;
    return 1.0 - q * q / (2.0 * a * c);
}

float rang(ivec2 c) {
    float b = texelFetch(uBlau, ivec2(c.x & 255, c.y & 255), 0).r;
    return mischen(b, zug(zelle(c), 0u), uOrdnung);
}

// Mitte (Zellen), Halbachsen a, b, Richtung cos, sin
struct Form { vec2 m; vec2 ab; vec2 cs; };

Form form(ivec2 c) {
    uint h = zelle(c);
    float r = 0.5 * uGroesse * (1.0 + uStreuung * (2.0 * zug(h, 3u) - 1.0));
    float asp = 1.0 + (uLaenge - 1.0) * zug(h, 4u), wu = sqrt(asp), wi = 3.14159265 * zug(h, 5u);
    Form f;
    f.m = vec2(c) + 0.5 + uZittern * (2.0 * vec2(zug(h, 1u), zug(h, 2u)) - 1.0);
    f.ab = vec2(r * wu, r / wu);
    f.cs = vec2(cos(wi), sin(wi));
    return f;
}

// Abstand vom Rand der Ellipse (Zellen), negativ innen (Näherung nach Quílez, am Rand genau)
float abstand(vec2 q, Form f) {
    vec2 d = q - f.m;
    vec2 p = vec2(f.cs.x * d.x + f.cs.y * d.y, -f.cs.y * d.x + f.cs.x * d.y);
    float k0 = length(p / f.ab), k1 = length(p / (f.ab * f.ab));
    return k1 < 1e-12 ? -min(f.ab.x, f.ab.y) : k0 * (k0 - 1.0) / k1;
}

// Einzug der Kante (px), damit die geglättete Kante die Fläche trifft (Steiner)
float einzug(Form f, float aa) {
    float rq = sqrt(0.5 * dot(f.ab, f.ab)) * uZelle;
    return min(0.5 * min(f.ab.x, f.ab.y) * uZelle, aa * aa / (24.0 * rq));
}

float schwelle(float r) {
    float x = clamp(r, 0.0, 1.0) * 256.0;
    int k = min(255, int(x));
    return uKurve[k] + (x - float(k)) * (uKurve[k + 1] - uKurve[k]);
}

// ------------------------------------------------------------------ Ton (wie Stich.ton)
uniform float uTon[258];        // [0] für Y ≤ 0, [1 + k] an der Grenze k der Fächer in log2 Y
uniform float uLo, uHi;

float tonLog(float l2) {
    float x = clamp((l2 - uLo) / (uHi - uLo) * 256.0, 0.0, 256.0);
    int k = min(255, int(x));
    return uTon[1 + k] + (x - float(k)) * (uTon[2 + k] - uTon[1 + k]);
}

// ------------------------------------------------------------------ Wolken und Korn (wie Stich.java)
uniform uint uSeedW, uSeedK;    // pcg(seed · 0x2545F491 + 17), pcg(seed + 0x3C6EF372)
uniform float uWBreite, uWLaenge, uWBahnen, uWFlecken, uWFleck;

float gitter(ivec2 i, uint s, vec2 d) {
    float w = 6.2831853 * eins(uint(i.x) + pcg(uint(i.y) + s));
    return cos(w) * d.x + sin(w) * d.y;
}

float rauschen(vec2 p, uint s) {
    vec2 f = floor(p), t = p - f;
    ivec2 i = ivec2(f);
    float a = gitter(i, s, t), b = gitter(i + ivec2(1, 0), s, t - vec2(1.0, 0.0)),
          c = gitter(i + ivec2(0, 1), s, t - vec2(0.0, 1.0)), d = gitter(i + ivec2(1, 1), s, t - vec2(1.0));
    t = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    float u = mix(a, b, t.x), v = mix(c, d, t.x);
    return 1.4 * mix(u, v, t.y);
}

float wolken(vec2 uv) {
    uint s = uSeedW;
    float bw = uWBreite, bl = uWLaenge;
    float streifen = 0.85 * rauschen(vec2(uv.x / bw, uv.y / bl), s) + 0.35 * rauschen(vec2(2.0 * uv.x / bw, 1.3 * uv.y / bl), s ^ 0x7FEB352Du)
            + 0.15 * rauschen(vec2(4.0 * uv.x / bw, 1.6 * uv.y / bl), s ^ 0x846CA68Bu);
    float bahnen = rauschen(vec2(uv.x / (5.0 * bw), uv.y / (1.5 * bl)), s ^ 0x68E31DA4u), flecken = rauschen(uv / uWFleck, s ^ 0x1B873593u);
    float gs = max(0.0, 1.0 - uWBahnen - uWFlecken);
    return 2.4 * (sqrt(gs) * streifen + sqrt(uWBahnen) * bahnen + sqrt(uWFlecken) * flecken);
}

float korn(ivec2 p) { return 2.0 * eins(uint(p.x) + pcg(uint(p.y) + uSeedK)) - 1.0; }
