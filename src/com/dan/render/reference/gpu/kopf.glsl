// Referenz Stufe 2 (P11a, Werkbuch HalligHooge Teil A, 12.12): Kopf – Bindungen, Werte der Szene, Zufall, Tafeln,
// Vektorrechnung. Folgt Vek, Wellen, Licht aus com.dan.render.reference (Stufe 1). Rechnet in float mit der Lage
// relativ zum Auge; wo float an Stufe 1 nicht herankommt, ist die Formel umgestellt (steht jeweils dabei).

#extension GL_EXT_control_flow_attributes : require

layout(local_size_x = 64) in;

#ifdef WELLENFRONT
#define BQ restrict                                                       // Wellenfront: Puffer überlappen nicht
#else
#define BQ
#endif
layout(std430, set = 0, binding = 0) BQ readonly buffer WerteB { float W[]; };
layout(std430, set = 0, binding = 1) BQ readonly buffer SpekB { float S[]; };
layout(std430, set = 0, binding = 2) BQ readonly buffer FfB { float FF[]; };
layout(std430, set = 0, binding = 3) BQ buffer AusB { float A[]; };

layout(push_constant) uniform Push {
    int modus, pfade, runde;
    uint saat;
    int breite, hoehe, faeden, versatz, pfadBasis, gesamt;
} pc;

// ------------------------------------------------------------------ Werte der Szene (ReferenzKarte.W_*)
#define W_HAUGE 0
#define W_BODEN 1
#define W_HAT_BODEN 2
#define W_WASSER 3
#define W_HAT_WASSER 4
#define W_KUGEL 5
#define W_SZ_SIGMA2 6
#define W_ALB 7
#define W_ETA 10
#define W_ALPHA2 11
#define W_SPEKTRAL 12
#define W_NB 13
#define W_MIE_EXT 14
#define W_MIE_SCA 15
#define W_BWASSER 16
#define W_SCHWEB 17
#define W_AS 18
#define W_RAY 21
#define W_OZ 24
#define W_POPE 27
#define W_SONNE_K 30
#define W_MOND_K 33
#define W_NACHT_K 36
#define W_MONDG 39
#define W_NACHTF 42
#define W_KORR 43
#define W_A443 52
#define W_SNAP 53
#define W_BBSTERN 54
#define W_BBZUB 55
#define W_OFEN 56
#define W_EINFACH 57
#define W_MIT_R 58
#define W_MIT_M 59
#define W_MIT_O 60
#define W_NACHT_AN 61
#define W_OHNE_ABS 62
#define W_JEDER 63
#define W_KEULE 64
#define W_DICHTE 65
#define W_OHNE_SAEULE 66
#define W_WWEITE 67
#define W_MAXTIEFE 68
#define W_NL 70
#define W_L0 72
#define W_L1 80
#define W_CAM 88
#define W_DIR 97
#define W_P0 100
#define W_S 103
#define W_MIEG 105
#define W_RH 106
#define W_MH 107
#define W_OC 108
#define W_OHW 109
#define W_R 110
#define W_RT 111
#define W_TEILE 112

// ------------------------------------------------------------------ Tafeln
// Spektrum je 1 nm von 360 bis 830 (471 Werte je Tafel), Reihenfolge wie ReferenzKarte.spektrumTafel
#define SN 471
#define S_X 0
#define S_Y 471
#define S_Z 942
#define S_SONNE 1413
#define S_AW 1884
#define S_O3 2355
#define S_B0 2826
#define S_B1 3297
#define S_B2 3768
// Fournier-Forand (ReferenzKarte.ffTafel): 1 − cos θ_i, F_i, 1 − F_i (je 8 193), Dichte je Stufe (8 192)
#define FF_N 8192
#define FF_OM 0
#define FF_F 8193
#define FF_G 16386
#define FF_D 24579

#define PI 3.14159265358979
#define KEULE_G 0.8
#define KM 683.0
#define LMIN 360.0
#define LBREITE 470.0

const int ENDE = 0, STREUUNG = 1, ABSORBIERT = 2;
const int BODEN = 1, WASSER = 2;

// ------------------------------------------------------------------ Zufall: PCG (O'Neill 2014), RXS-M-XS 32
uint rs;

uint pcgHash(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}

uint pcgNext() {
    rs = rs * 747796405u + 2891336453u;
    uint w = ((rs >> ((rs >> 28u) + 4u)) ^ rs) * 277803737u;
    return (w >> 22u) ^ w;
}

/** Größte Komponente. */
float max4(vec4 v) { return max(max(v.x, v.y), max(v.z, v.w)); }

/** Gleichverteilt in [0, 1), 24 Bit. */
float zufall() { return float(pcgNext() >> 8) * (1.0 / 16777216.0); }

// ------------------------------------------------------------------ Wache (seit Lauf A von P11a)
// Jede Schleife zählt ihre Schritte; über GRENZE_SCHRITTE je Pfad bricht der Pfad ab (gezählt in der Ausgabe). So hängt
// kein Faden, wenn etwas NaN wird oder ein Pfad nicht endet; Windows bräche den ganzen Aufruf sonst nach 2 s ab.
#define GRENZE_SCHRITTE 2000000
int schritte;
bool abbruch;

bool wache() {
    if (++schritte > GRENZE_SCHRITTE) abbruch = true;
    return abbruch;
}

// ------------------------------------------------------------------ Vektorrechnung (Vek)

/** Zwei Einheitsvektoren senkrecht zu n und zueinander (Duff u. a. 2017). */
void basis(vec3 n, out vec3 b0, out vec3 b1) {
    float s = n.z >= 0.0 ? 1.0 : -1.0, a = -1.0 / (s + n.z), b = n.x * n.y * a;
    b0 = vec3(1.0 + s * n.x * n.x * a, s * b, -s * n.x);
    b1 = vec3(b, s + n.y * n.y * a, -n.y);
}

/** Eine Richtung um die Achse a mit cos mu und sin s (φ gleichverteilt); s getrennt, damit kleine Winkel genau bleiben. */
vec3 umAchse(vec3 a, float mu, float s) {
    float phi = 2.0 * PI * zufall();
    vec3 b0, b1;
    basis(a, b0, b1);
    return b0 * (s * cos(phi)) + b1 * (s * sin(phi)) + a * mu;
}

vec3 umAchse(vec3 a, float mu) { return umAchse(a, mu, sqrt(max(0.0, 1.0 - mu * mu))); }

/** 1 − cos des Winkels zwischen zwei Einheitsvektoren, ohne Auslöschung bei kleinen Winkeln. */
float einsMinusCos(vec3 a, vec3 b) { vec3 d = a - b; return 0.5 * dot(d, d); }

/** Brechung von d an m mit eta = n_vorher / n_nachher (m zu beiden Seiten); false bei Totalreflexion. */
bool breche(vec3 d, vec3 m, float eta, out vec3 t) {
    float c = -dot(d, m);
    vec3 mm = m;
    if (c < 0.0) { c = -c; mm = -m; }
    float k = 1.0 - eta * eta * (1.0 - c * c);
    if (k < 0.0) return false;
    float f = eta * c - sqrt(k);
    t = eta * d + f * mm;
    return true;
}

vec3 spiegle(vec3 d, vec3 m) { return d - 2.0 * dot(d, m) * m; }

/** Versatz von einer Fläche: 10⁻⁴ m wie Stufe 1, dazu einige Stellen von float bei großer Entfernung vom Auge. */
float eps(vec3 x) { return 1e-4 + 4e-7 * max(abs(x.x), max(abs(x.y), abs(x.z))); }

// ------------------------------------------------------------------ Spektrum (Spektrum.lerp)

float tafel(int off, float l) {
    float u = l - LMIN;
    if (!(u > 0.0)) return S[off];                                        // auch NaN: nie außerhalb der Tafel lesen
    if (u >= float(SN - 1)) return S[off + SN - 1];
    int i = min(int(u), SN - 2);
    float f = u - float(i);
    return S[off + i] + f * (S[off + i + 1] - S[off + i]);
}

/** Reflexion bei λ zu Gewichten der Basis (Spektrum.reflexion). */
float reflexion(vec3 g, float l) { return g.x * tafel(S_B0, l) + g.y * tafel(S_B1, l) + g.z * tafel(S_B2, l); }

vec3 gewichte(vec3 rgb) {
    return vec3(W[W_KORR] * rgb.x + W[W_KORR + 1] * rgb.y + W[W_KORR + 2] * rgb.z,
                W[W_KORR + 3] * rgb.x + W[W_KORR + 4] * rgb.y + W[W_KORR + 5] * rgb.z,
                W[W_KORR + 6] * rgb.x + W[W_KORR + 7] * rgb.y + W[W_KORR + 8] * rgb.z);
}

/** Rayleigh-Streuung am Boden (1/m) nach Bodhaine u. a. (1999), Gl. 30, durch die Skalenhöhe (Spektrum.rayleigh). */
float rayleighSp(float l) {
    float u = l / 1000.0, a = 1.0 / (u * u), b = u * u;
    return 0.0021520 * (1.0455996 - 341.29061 * a - 0.90230850 * b) / (1.0 + 0.0027059889 * a - 85.968563 * b) / W[W_RH];
}

// ------------------------------------------------------------------ Wellen (Bahnen eines Pfads)
bool spektral;
int NB;
vec4 aktiv, lam, ray, ozo, aW, son, mon, nac;

void wellenKanaele() {
    lam = vec4(680.0, 550.0, 440.0, 0.0);
    ray = vec4(W[W_RAY], W[W_RAY + 1], W[W_RAY + 2], 0.0);
    ozo = vec4(W[W_OZ], W[W_OZ + 1], W[W_OZ + 2], 0.0);
    aW = vec4(W[W_POPE] + W[W_AS], W[W_POPE + 1] + W[W_AS + 1], W[W_POPE + 2] + W[W_AS + 2], 0.0);
    son = vec4(W[W_SONNE_K], W[W_SONNE_K + 1], W[W_SONNE_K + 2], 0.0);
    mon = vec4(W[W_MOND_K], W[W_MOND_K + 1], W[W_MOND_K + 2], 0.0);
    nac = W[W_NACHT_AN] > 0.5 ? vec4(W[W_NACHT_K], W[W_NACHT_K + 1], W[W_NACHT_K + 2], 0.0) : vec4(0.0);
}

/** Werte einer Bahn bei λ: Rayleigh, Ozon, Wasser, Sonne, Mond, Nachthimmel. */
void bahn(float l, vec3 mg, bool nacht, out float r, out float o, out float a, out float s, out float m, out float n) {
    r = rayleighSp(l);
    o = tafel(S_O3, l);
    a = tafel(S_AW, l) + W[W_A443] * W[W_SCHWEB] * exp(-W[W_SNAP] * (l - 443.0));
    s = tafel(S_SONNE, l);
    m = s * reflexion(mg, l);
    n = nacht ? s * W[W_NACHTF] : 0.0;
}

/**
 * Neue Wellenlängen für einen Pfad (Wellen.setze): die Heldenwellenlänge gleichverteilt, die anderen um je ein Viertel.
 * Ohne Index über die Bahnen (der Compiler legte indizierte Vektoren in den Scratch, Befund Lauf A2).
 */
void neueWellen() {
    if (!spektral) return;
    float l0 = zufall() * LBREITE;
    vec3 mg = vec3(W[W_MONDG], W[W_MONDG + 1], W[W_MONDG + 2]);
    bool na = W[W_NACHT_AN] > 0.5;
    vec4 l = LMIN + mod(vec4(l0) + vec4(0.0, 0.25, 0.5, 0.75) * LBREITE, LBREITE);
    lam = l;
    float r0, o0, a0, s0, m0, n0, r1, o1, a1, s1, m1, n1, r2, o2, a2, s2, m2, n2, r3, o3, a3, s3, m3, n3;
    bahn(l.x, mg, na, r0, o0, a0, s0, m0, n0);
    bahn(l.y, mg, na, r1, o1, a1, s1, m1, n1);
    bahn(l.z, mg, na, r2, o2, a2, s2, m2, n2);
    bahn(l.w, mg, na, r3, o3, a3, s3, m3, n3);
    ray = vec4(r0, r1, r2, r3);
    ozo = vec4(o0, o1, o2, o3);
    aW = vec4(a0, a1, a2, a3);
    son = vec4(s0, s1, s2, s3);
    mon = vec4(m0, m1, m2, m3);
    nac = vec4(n0, n1, n2, n3);
}

/** Albedo je Bahn aus linearem RGB (Wellen.albedo). */
vec4 albedoBahnen(vec3 rgb) {
    if (!spektral) return vec4(rgb, 0.0);
    vec3 g = gewichte(rgb);
    return vec4(reflexion(g, lam.x), reflexion(g, lam.y), reflexion(g, lam.z), reflexion(g, lam.w));
}

/** Beitrag der Bahnen zu XYZ (spektral) bzw. RGB (Wellen.sammle). */
vec3 xyzBei(float l) { return vec3(tafel(S_X, l), tafel(S_Y, l), tafel(S_Z, l)); }

vec3 sammle(vec4 Lb) {
    if (!spektral) return Lb.xyz;
    float f = KM * LBREITE / 4.0;
    return f * (Lb.x * xyzBei(lam.x) + Lb.y * xyzBei(lam.y) + Lb.z * xyzBei(lam.z) + Lb.w * xyzBei(lam.w));
}

/** Absorption des Wassers je Bahn bei der Trübung c (Wellen.aWasserBei). */
vec4 aWasserBei(float c) {
    if (!spektral) {
        const vec3 LK = vec3(680.0, 550.0, 440.0);
        vec3 a = vec3(W[W_POPE], W[W_POPE + 1], W[W_POPE + 2]) + W[W_A443] * c * exp(-W[W_SNAP] * (LK - 443.0));
        return vec4(a, 0.0);
    }
    vec4 aw = vec4(tafel(S_AW, lam.x), tafel(S_AW, lam.y), tafel(S_AW, lam.z), tafel(S_AW, lam.w));
    return aw + W[W_A443] * c * exp(-W[W_SNAP] * (lam - 443.0));
}

// ------------------------------------------------------------------ Licht (Sonne und Mond)
// Je Lichtquelle in W ab W_L0 / W_L1: Richtung (3), 1 − cos des Winkelradius (genau in double gerechnet), Randverdunklung,
// Faktor, Sonne (1/0).

int nLicht;

vec3 lRichtung(int i) { int o = i == 0 ? W_L0 : W_L1; return vec3(W[o], W[o + 1], W[o + 2]); }
float lOm(int i) { return W[(i == 0 ? W_L0 : W_L1) + 3]; }
float lRand(int i) { return W[(i == 0 ? W_L0 : W_L1) + 4]; }
float lFaktor(int i) { return W[(i == 0 ? W_L0 : W_L1) + 5]; }
bool lSonne(int i) { return W[(i == 0 ? W_L0 : W_L1) + 6] > 0.5; }
float lRaumwinkel(int i) { return 2.0 * PI * lOm(i); }

/** Richtung gleichverteilt auf der Scheibe (Licht.ziehe), mit 1 − cos statt cos. */
vec3 lZiehe(int i) {
    float x = zufall() * lOm(i);
    return umAchse(lRichtung(i), 1.0 - x, sqrt(x * (2.0 - x)));
}

bool lTrifft(int i, vec3 d) { return einsMinusCos(d, lRichtung(i)) <= lOm(i); }

/** Strahldichte in Richtung d als Faktor auf die Beleuchtungsstärke der Bahn (Licht.strahldichte). */
float lStrahldichte(int i, vec3 d) {
    float x = einsMinusCos(d, lRichtung(i)), om = lOm(i);
    if (x > om) return 0.0;
    float u = lRand(i), rw = lRaumwinkel(i);
    if (u == 0.0) return lFaktor(i) / rw;
    float sr = sqrt(max(0.0, x * (2.0 - x))), sm = sqrt(om * (2.0 - om));
    float xx = min(1.0, sr / sm), cg = sqrt(1.0 - xx * xx);
    return lFaktor(i) / rw * (1.0 - u * (1.0 - cg)) / (1.0 - u / 3.0);
}

float lGewicht(int i) {
    float h = lRichtung(i).y;
    return max(1e-30, lFaktor(i)) * (h > -0.1045 ? 1.0 : h > -0.309 ? 0.01 : 1e-8);
}

// ------------------------------------------------------------------ Treffer (Szene.Treffer)
struct Treffer {
    float t;
    vec3 n;
    int art;
    vec3 albedo;
    float sigma2;
};
