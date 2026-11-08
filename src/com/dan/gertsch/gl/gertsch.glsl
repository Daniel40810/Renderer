// gertsch.glsl — gemeinsam für alle Shader des Pakets (GertschGl hängt es vor jede Datei): Zufall, Blatt und Wind wie
// in Java (Zufall, Dickicht.Zweig.blatt, Wind), Licht wie in Licht/Malweise, Lab, Tonband mit Hysterese wie in Stufen.

// ------------------------------------------------------------------ Zufall (PCG-Hash, wie Zufall.java)
uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float eins(uint h) { return float(pcg(h) >> 8u) * (1.0 / 16777216.0); }
float zug(uint seed, uint i, uint k) { return eins(seed + pcg(i) + k * 0x9E3779B9u); }

// ------------------------------------------------------------------ Zweige (je 32 Byte)
// mr = Mitte (m), Radius der Blattwolke; a.x = Blätter | Boden << 16, a.y = seed, a.z = erster Platz, a.w = halb(Länge, Schmal)
struct Zweig { vec4 mr; uvec4 a; };

uniform float uNeigung;        // Streuung der Blattneigung (Grad)
uniform float uTrocken;        // Anteil trockener Blätter
uniform vec3 uLaub, uLaubTrocken, uStreu;   // Albedo linear

struct Blatt { vec3 p, n, a; float laenge, breite, zufall, phase; vec3 albedo; };

uint blaetter(Zweig z) { return z.a.x & 0xFFFFu; }
bool boden(Zweig z) { return (z.a.x >> 16u) != 0u; }
float zweigPhase(Zweig z) { return eins(z.a.y ^ 0x5BD1E995u); }

Blatt blatt(Zweig z, uint i) {
    uint s = z.a.y;
    vec2 ls = unpackHalf2x16(z.a.w);
    bool bo = boden(z);
    float neig = bo ? 8.0 : uNeigung, trocken = bo ? 1.0 : uTrocken;
    Blatt b;
    b.zufall = zug(s, i, 0u);
    float cz = 2.0 * zug(s, i, 1u) - 1.0, sxy = sqrt(max(0.0, 1.0 - cz * cz));
    float ph = 6.2831853 * zug(s, i, 2u), rr = pow(max(zug(s, i, 3u), 1e-12), 1.0 / 3.0);
    b.p = z.mr.xyz + z.mr.w * rr * vec3(sxy * cos(ph), 0.75 * cz, sxy * sin(ph));
    float gauss = sqrt(-2.0 * log(max(zug(s, i, 4u), 1e-7))) * cos(6.2831853 * zug(s, i, 5u));
    float th = radians(min(85.0, abs(gauss) * neig)), phn = 6.2831853 * zug(s, i, 6u);
    b.n = vec3(sin(th) * cos(phn), cos(th), sin(th) * sin(phn));
    vec3 t = vec3(2.0 * zug(s, i, 7u) - 1.0, 0.6 * zug(s, i, 8u) - 0.3, 2.0 * zug(s, i, 9u) - 1.0);
    t -= dot(t, b.n) * b.n;
    float l = length(t);
    if (l < 1e-4) { t = vec3(b.n.y, -b.n.x, 0.0); l = length(t); }
    b.a = t / l;
    b.laenge = ls.x * (0.75 + 0.5 * zug(s, i, 10u));
    b.breite = b.laenge * ls.y * (0.85 + 0.3 * zug(s, i, 11u));
    bool tr = zug(s, i, 12u) < trocken;
    vec3 f = bo ? uStreu : (tr ? uLaubTrocken : uLaub);
    float hellig = 0.75 + 0.5 * zug(s, i, 13u), gelb = 0.3 * zug(s, i, 14u) - 0.15;
    b.albedo = f * hellig * vec3(1.0 + gelb, 1.0, 1.0 - gelb);
    b.phase = zug(s, i, 15u);
    return b;
}

// ------------------------------------------------------------------ Wind (wie Wind.java)
uniform vec4 uWind;            // Stärke (m/s), Richtung woher (Grad), Böigkeit, Zeit (s)

vec2 windVersatz(vec3 p, float phase) {
    float r = radians(uWind.y + 180.0), u = uWind.x, t = uWind.w;
    vec2 d = vec2(sin(r), cos(r));
    float a = 0.0016 * u * u, hoehe = 0.35 + 0.65 * min(1.0, max(0.0, p.y) / 8.0);
    float k = 6.2831853 / 18.0, boe = 1.0 + uWind.z * 0.5 * (1.0 + sin(k * dot(p.xz, d) - k * 0.7 * u * t));
    float schw = sin(6.2831853 * ((0.6 + 0.4 * phase) * t + phase));
    return d * a * hoehe * boe * (0.6 + 0.4 * schw);
}
float flattern(float phase) {
    return 0.45 * min(1.0, uWind.x / 6.0) * sin(6.2831853 * ((2.5 + 1.5 * phase) * uWind.w + phase));
}
// Blatt im Wind: der Zweig wandert, das Blatt dreht sich um seine Längsachse (die Streu am Boden bleibt liegen)
void imWind(inout Blatt b, Zweig z) {
    if (boden(z) || uWind.x <= 0.0) return;
    b.p.xz += windVersatz(z.mr.xyz, zweigPhase(z));
    float w = flattern(b.phase);
    b.n = normalize(b.n * cos(w) + cross(b.a, b.n) * sin(w));
}

// ------------------------------------------------------------------ Licht (wie Licht.java / Malweise.beleuchten)
uniform vec3 uZurSonne, uSonneFarbe, uHimmelFarbe;
uniform float uDurch;           // Durchscheinen
// Schattenkarten entlang der Sonne in zwei Stufen: nah (die Empfänger bis 22 m vor der Kamera, fein) und fern (alle
// Empfänger im Bild). Ein Werfer liegt entlang der Sonne über seinem Empfänger, darum reicht das Rechteck der Empfänger.
uniform mat4 uSonneM, uSonneMNah;           // Welt → Schattenkarte (Clip, orthografisch)
uniform sampler2DShadow uSchatten, uSchattenNah;
uniform float uSchattenBias, uSchattenBiasNah;   // in Tiefe 0..1
uniform sampler3D uLai;         // Blattfläche über jeder Zelle
uniform vec3 uLaiUrsprung, uLaiZelle, uLaiZahl;
uniform float uAusloeschung;
uniform vec3 uAuge;

float pcf(sampler2DShadow karte, vec3 s, float bias) {
    vec2 tx = 1.0 / vec2(textureSize(karte, 0));
    float v = 0.0;
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++) v += texture(karte, vec3(s.xy + vec2(i, j) * tx, s.z - bias));
    return v / 9.0;
}
float sonne(vec3 p) {
    vec3 sn = (uSonneMNah * vec4(p, 1.0)).xyz * 0.5 + 0.5;
    vec2 rn = 3.0 / vec2(textureSize(uSchattenNah, 0));
    if (all(greaterThan(sn.xy, rn)) && all(lessThan(sn.xy, 1.0 - rn))) return pcf(uSchattenNah, sn, uSchattenBiasNah);
    vec3 s = (uSonneM * vec4(p, 1.0)).xyz * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.0))) || any(greaterThan(s.xy, vec2(1.0)))) return 1.0;
    return pcf(uSchatten, s, uSchattenBias);
}
float himmel(vec3 p) {
    vec3 c = (p - uLaiUrsprung) / uLaiZelle;
    return exp(-uAusloeschung * texture(uLai, c / uLaiZahl).r);
}
// Licht am Punkt (zweiseitig: die zum Auge gewandte Seite zählt); vis < 0: aus der Schattenkarte
vec3 licht(vec3 p, vec3 n, vec3 albedo, bool duenn, float vis) {
    if (dot(n, uAuge - p) < 0.0) n = -n;
    float c = dot(n, uZurSonne), v = vis < 0.0 ? sonne(p) : vis, h = himmel(p) * (0.55 + 0.45 * max(0.0, n.y));
    vec3 s = c >= 0.0 ? albedo * c : (duenn ? albedo * vec3(1.15, 1.0, 0.5) * (-c * uDurch) : vec3(0.0));
    return uSonneFarbe * s * v + uHimmelFarbe * albedo * h;
}
float lum(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

// ------------------------------------------------------------------ Lab (wie Lab.java)
float labF(float t) { return t > 216.0 / 24389.0 ? pow(t, 1.0 / 3.0) : (24389.0 / 27.0 * t + 16.0) / 116.0; }
vec3 labAus(vec3 c) {
    float x = dot(c, vec3(0.4124564, 0.3575761, 0.1804375)) / 0.95047, y = dot(c, vec3(0.2126729, 0.7151522, 0.0721750));
    float z = dot(c, vec3(0.0193339, 0.1191920, 0.9503041)) / 1.08883;
    float fx = labF(x), fy = labF(y), fz = labF(z);
    return vec3(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
}
float labFInv(float t) { float t3 = t * t * t; return t3 > 216.0 / 24389.0 ? t3 : (116.0 * t - 16.0) / (24389.0 / 27.0); }
vec3 srgbAusLab(vec3 lab) {
    float fy = (lab.x + 16.0) / 116.0, fx = fy + lab.y / 500.0, fz = fy - lab.z / 200.0;
    float y = lab.x > 8.0 ? fy * fy * fy : lab.x / (24389.0 / 27.0);
    vec3 xyz = vec3(0.95047 * labFInv(fx), y, 1.08883 * labFInv(fz));
    vec3 l = vec3(dot(xyz, vec3(3.2404542, -1.5371385, -0.4985314)), dot(xyz, vec3(-0.9692660, 1.8760108, 0.0415560)),
                  dot(xyz, vec3(0.0556434, -0.2040259, 1.0572252)));
    l = clamp(l, 0.0, 1.0);
    return mix(12.92 * l, 1.055 * pow(l, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, l));
}

// ------------------------------------------------------------------ Tonstufen (wie Stufen.java und Malweise.stufe)
uniform int uStufen;            // Zahl der Stufen k
uniform float uGrenze[7];       // Grenzen der Bänder in log2 Y (k − 1)
uniform vec3 uStufeLab[8];      // Lab der Stufen der Vorlage
uniform vec3 uStufeRgb[8];      // sRGB 0..1
uniform float uFarbGewicht, uBelichtung;
uniform vec2 uAkzentGrenze;     // Buntheit ab, Winkel ab (Grad) für einen Akzent
uniform float uFarbton;         // herrschender Farbton der Vorlage (Grad)
uniform vec3 uAkzentLab;        // Helligkeit, a, b der Akzente (a, b mit sicherem Abstand im Farbton)

int bandRoh(float x) {
    int b = 0;
    for (int j = 0; j < uStufen - 1; j++) if (x >= uGrenze[j]) b++;
    return b;
}
int bandMit(float x, int vorher, float h) {
    int roh = bandRoh(x);
    if (vorher < 0 || vorher >= uStufen) return roh;
    float lo = vorher == 0 ? -1e30 : uGrenze[vorher - 1] - h, hi = vorher == uStufen - 1 ? 1e30 : uGrenze[vorher] + h;
    return x >= lo && x < hi ? vorher : roh;
}
// Stufe zur Farbe: Helligkeit des Bands, Farbe nach a*, b* (g · (Δa² + Δb²)); −1 = Akzent
int stufeZu(vec3 lin, int band, bool mitAkzent) {
    vec3 lab = labAus(lin * uBelichtung);
    if (mitAkzent) {
        float cb = length(lab.yz), h = degrees(atan(lab.z, lab.y));
        float d = abs(mod(h - uFarbton + 540.0, 360.0) - 180.0);
        if (cb >= uAkzentGrenze.x && d > uAkzentGrenze.y) return -1;
    }
    float l = uStufeLab[band].x;
    int best = 0;
    float bd = 1e30;
    for (int i = 0; i < uStufen; i++) {
        vec3 q = uStufeLab[i];
        float d = (l - q.x) * (l - q.x) + uFarbGewicht * dot(lab.yz - q.yz, lab.yz - q.yz);
        if (d < bd) { bd = d; best = i; }
    }
    return best;
}
vec3 farbeZu(int stufe, int band) {
    if (stufe >= 0) return uStufeRgb[stufe];
    float l = clamp(uStufeLab[band].x, uAkzentLab.x - 12.0, uAkzentLab.x + 14.0);
    return srgbAusLab(vec3(l, uAkzentLab.yz));
}
