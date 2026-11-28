// see_gemein.glsl — die Oberfläche des Seegangs auf der Karte (Seegang Werkbuch, S3b): dieselben Formeln wie
// com.dan.core.szene.Oberflaeche.punkt in float. Orte relativ zum Bezugspunkt B (nahe dem Auge); was groß wird
// (Phase der ebenen Welle k₀ s und ω t, Laufzeit s/c_g₀ − t), rechnet der Prozessor in double modulo 2π bzw. der
// Periode der Hülle vor (Stuetz, Fern).
//
//  0 Werte     I[32], F[64] (Indizes unten)
//  1 Stuetz    vec4 × 2 je Stützstelle bq = b·nd + q: (cos θ, sin θ, Phase bei B, Laufzeit bei B), (ω_b, k₀, c_g₀, Periode)
//  2 Huellen   vec4 je Stützstelle und Stelle: (Re G, Im G, Re G', Im G'), Index bq · N + j
//  3, 4        gezeichnete Varianz fein, grob: float, Index (c · nb + b) · nd + q
//  5, 6        Phase fein, grob: vec2 (δΦ, δT), Index bq · nc + c
//  7, 8        Zelle fein, grob: vec2 (Tiefe, Gefälle)
//  9, 10       Kennwerte fein, grob: float, je Zelle 2 + 2 nb: m0, Σ (k coth)² E, je Band Σ E, je Band Σ E k
// 11 Fern      vec4 je Welle der Stufe 2: (k_x, k_y, Amplitude, Phase bei B)
// 12 BG        vec4 je Stelle der Tafel nach Battjes–Groenendijk: (x, H̃₁, H̃₂, –) über H̃_tr von BG_VON bis BG_BIS

layout(std430, binding = 0) readonly buffer Werte { int I[32]; float F[64]; } W;
layout(std430, binding = 1) readonly buffer Stuetz { vec4 stuetz[]; };
layout(std430, binding = 2) readonly buffer Huellen { vec4 huelle[]; };
layout(std430, binding = 3) readonly buffer EFein { float eFein[]; };
layout(std430, binding = 4) readonly buffer EGrob { float eGrob[]; };
layout(std430, binding = 5) readonly buffer PFein { vec2 pFein[]; };
layout(std430, binding = 6) readonly buffer PGrob { vec2 pGrob[]; };
layout(std430, binding = 7) readonly buffer ZFein { vec2 zFein[]; };
layout(std430, binding = 8) readonly buffer ZGrob { vec2 zGrob[]; };
layout(std430, binding = 9) readonly buffer KFein { float kFein[]; };
layout(std430, binding = 10) readonly buffer KGrob { float kGrob[]; };
layout(std430, binding = 11) readonly buffer Fern { vec4 fern[]; };
layout(std430, binding = 12) readonly buffer BG { vec4 bgTafel[]; };
// 17 Liste der Stützstellen, die irgendwo über der Schwelle liegen (B41): aktiv[0 .. nb] Anfang je Band, dann die Nummern
layout(std430, binding = 17) readonly buffer Aktiv { int aktiv[]; };

// Werte.I
const int I_NB = 0, I_ND = 1, I_N = 2, I_SCHALTER = 3, I_FEIN = 4, I_GROB = 5, I_FERN = 6, I_PUNKTE = 7;
const int I_F_NX = 8, I_F_NY = 9, I_F_RAND = 10, I_G_NX = 11, I_G_NY = 12, I_G_RAND = 13, I_STUFEN = 14, I_BG = 15, I_FERN_N = 16, I_RAND = 17;   // I_RAND: Breite des Übergangs ins Fernfeld am Rand der äußersten Stufe (Stellen; S3c)
// Werte.F
const int F_LAMBDA = 0, F_FALTUNG = 1, F_TIEFE_FERN = 2, F_E_MIN = 3, F_F_X0 = 4, F_F_Y0 = 5, F_F_DX = 6, F_G_X0 = 7, F_G_Y0 = 8, F_G_DX = 9;
const int F_FERN_M0 = 10, F_FERN_SXI2 = 11, F_FERN_KP = 12, F_FERN_W = 13, F_BG_VON = 14, F_BG_BIS = 15, F_LAGRANGE = 16, F_LW_NENNER = 17;
const int F_D0 = 18, F_MITTE = 19;   // Abstand der feinsten Stufe; F_MITTE.. F_MITTE+15: Mitte (x, y) je Stufe der Clipmap
const int F_E_REL = 35, F_S_REL = 52;   // Schwellen der Varianz und der Neigung (Oberflaeche.faktor, SF23 A)
const int F_NAH = 53;   // seit S3d (SF35 A): Gewicht des Nahfelds gegen das Fernfeld (Überblenden in der Anwendung), sonst 1
const int F_K_UNTEN = 36;   // F_K_UNTEN.. +15: kleinste Wellenzahl der Dispersion je Band (Phasenfeld.kMin, Befund B34)

// Schalter
const int S_FORM = 1, S_HOEHEN = 2, S_LANGWELLE = 4, S_VERSCHIEBUNG = 8;
// nur zur Messung der Rechenzeit (SeegangKarte.diagnose, Befund B38): ohne Hülle, ohne Phasenfeld, Energie aus einer Ecke
const int S_D_HUELLE = 16, S_D_PHASE = 32, S_D_ENERGIE = 64;
// Ablage der Phasenfelder je Zelle (Index c · nb · nd + bq) statt je Stützstelle (bq · nc + c); dasselbe Bild (B38)
const int S_PHASE_JE_ZELLE = 128;
// Varianz und Kennwerte je Zelle abgelegt (Index c · n + j, wie bis L2c) statt je Stützstelle bzw. Kennwert (j · nc + c,
// Vorgabe seit L2c: benachbarte Fäden lesen benachbarte Zellen, B38)
const int S_E_JE_ZELLE = 256;

int ixE(bool zell, int c, int j, int nj, int nc) { return zell ? c * nj + j : j * nc + c; }
float sumEF(ivec4 c, vec4 w, int j, int nj, int nc, bool z) { return w.x * eFein[ixE(z, c.x, j, nj, nc)] + w.y * eFein[ixE(z, c.y, j, nj, nc)] + w.z * eFein[ixE(z, c.z, j, nj, nc)] + w.w * eFein[ixE(z, c.w, j, nj, nc)]; }
float sumEG(ivec4 c, vec4 w, int j, int nj, int nc, bool z) { return w.x * eGrob[ixE(z, c.x, j, nj, nc)] + w.y * eGrob[ixE(z, c.y, j, nj, nc)] + w.z * eGrob[ixE(z, c.z, j, nj, nc)] + w.w * eGrob[ixE(z, c.w, j, nj, nc)]; }
float sumKF(ivec4 c, vec4 w, int j, int nj, int nc, bool z) { return w.x * kFein[ixE(z, c.x, j, nj, nc)] + w.y * kFein[ixE(z, c.y, j, nj, nc)] + w.z * kFein[ixE(z, c.z, j, nj, nc)] + w.w * kFein[ixE(z, c.w, j, nj, nc)]; }
float sumKG(ivec4 c, vec4 w, int j, int nj, int nc, bool z) { return w.x * kGrob[ixE(z, c.x, j, nj, nc)] + w.y * kGrob[ixE(z, c.y, j, nj, nc)] + w.z * kGrob[ixE(z, c.z, j, nj, nc)] + w.w * kGrob[ixE(z, c.w, j, nj, nc)]; }

/** Kennwert k am Ort, fein und grob gewichtet wie in Oberflaeche.punkt (kv). */
float kennW(int k, float wf, float wg, ivec4 cf, vec4 bf, ivec4 cg, vec4 bg, int kw, int ncf, int ncg, bool z) {
    float v = 0.0;
    if (wf > 0.0) v += wf * sumKF(cf, bf, k, kw, ncf, z);
    if (wg > 0.0) v += wg * sumKG(cg, bg, k, kw, ncg, z);
    return v;
}

/** Oberflaeche.faktor: Anteil der Amplitude, 0 unter beiden Schwellen, 1 über dem Doppelten einer, dazwischen linear. */
float faktor(float e, float eS, float kq, float sS) {
    float f = e > eS ? min(1.0, e / eS - 1.0) : 0.0;
    if (e > W.F[F_E_MIN] && sS > 0.0 && kq * e > sS) f = max(f, min(1.0, kq * e / sS - 1.0));
    return f;
}

const float G = 9.81, ZWEI_PI = 6.28318530718, PI = 3.14159265359, H_MIN = 0.05;
const int NB_MAX = 16, HUELLE_N = 4096;

// Seit S5b-4 (Seegang Werkbuch 46): Versatz der Zeit gegen die Zeit der Karte (s), nur wenn der Kernel ZEITVERSATZ vor
// dem Einbinden setzt (see_fuss.comp). Ohne ihn fallen die Terme weg; die übrigen Kernel bleiben Zeichen für Zeichen gleich.
// Phase der Stützstelle φ − ω_b Δ, Laufzeit der Hülle τ − Δ, Wellen des Fernfelds φ − ω(k) Δ (Wasseroptik.omega). Dazu
// m0 des Punkts (fussM0) für H_s am Fuß und Σ E, Σ E ω über die Bänder (fussE, fussEW) für die mittlere Frequenz.
#ifdef ZEITVERSATZ
float zeitVersatz = 0.0, fussM0 = 0.0, fussE = 0.0, fussEW = 0.0;
float omegaFern(float k) { return sqrt(9.81 * k + 0.074 / 1025.0 * k * k * k); }
#define ZV_PHASE(w) - (w) * zeitVersatz
#define ZV_TAU - zeitVersatz
#define ZV_FERN(k) - omegaFern(k) * zeitVersatz
#define ZV_M0(m) fussM0 = (m);
#define ZV_BAND(e, b) fussE += (e); fussEW += (e) * stuetz[2 * ((b) * nd) + 1].x;
#define ZV_FERNE(e, kp) fussE += (e); fussEW += (kp) > 0.0 ? (e) * sqrt(G * (kp)) : 0.0;
#else
#define ZV_PHASE(w)
#define ZV_TAU
#define ZV_FERN(k)
#define ZV_M0(m)
#define ZV_BAND(e, b)
#define ZV_FERNE(e, kp)
#endif

// Seit S5b-4b (Befund L3a: die Suche wartete Schleife für Schleife auf den Speicher, 1,66 ms für 500 Füße auf der 680M):
// mit ZV_PARALLEL (und ZV_LANES Fäden je Gruppe) rechnen die Fäden einer Gruppe denselben Punkt; jeder nimmt jede
// ZV_LANES-te Stützstelle und Welle des Fernfelds, danach summiert die Gruppe (geteilter Speicher). Alle Fäden müssen
// punktK gemeinsam aufrufen. Ohne den Schalter bleiben die Schleifen Zeichen für Zeichen wie vorher.
#ifdef ZV_PARALLEL
#define ZV_START + int(gl_LocalInvocationID.x)
#define ZV_SCHRITT += ZV_LANES
#define ZV_SUMMEN zvSummen(zr, zi, xr, yr, mitt, neig, varG);
shared float zvTeil[7 * ZV_LANES];
void zvSummen(inout float a0, inout float a1, inout float a2, inout float a3, inout float a4, inout float a5, inout float a6) {
    int l = int(gl_LocalInvocationID.x);
    zvTeil[l] = a0; zvTeil[ZV_LANES + l] = a1; zvTeil[2 * ZV_LANES + l] = a2; zvTeil[3 * ZV_LANES + l] = a3;
    zvTeil[4 * ZV_LANES + l] = a4; zvTeil[5 * ZV_LANES + l] = a5; zvTeil[6 * ZV_LANES + l] = a6;
    barrier();
    for (int s = ZV_LANES / 2; s > 0; s >>= 1) {
        if (l < s) for (int k = 0; k < 7; k++) zvTeil[k * ZV_LANES + l] += zvTeil[k * ZV_LANES + l + s];
        barrier();
    }
    a0 = zvTeil[0]; a1 = zvTeil[ZV_LANES]; a2 = zvTeil[2 * ZV_LANES]; a3 = zvTeil[3 * ZV_LANES];
    a4 = zvTeil[4 * ZV_LANES]; a5 = zvTeil[5 * ZV_LANES]; a6 = zvTeil[6 * ZV_LANES];
    barrier();
}
#else
#define ZV_START
#define ZV_SCHRITT ++
#define ZV_SUMMEN
#endif

// Seit S6b-2 (Seegang Werkbuch 67, SF61 A, SF62 A): mit TIEFENMITTEL vor dem Einbinden summiert punktK dazu den
// tiefenintegrierten Fluss je Welle q = (ω/k) η k̂ und die Richtung der Energie und gibt nach der Form den Antrieb des
// Auflaufs aus (antriebEta mit der gebundenen langen Welle, antriebQ), wie core.szene.Oberflaeche.Punkt.qx, .qy,
// .etaAntrieb. Ohne den Schalter fallen die Terme im Präprozessor weg.
#ifdef TIEFENMITTEL
float antriebEta = 0.0;
vec2 antriebQ = vec2(0.0);
float omegaTm(float k) { return sqrt(9.81 * k + 0.074 / 1025.0 * k * k * k); }
#define TM_WELLE(c, re, kx, ky, k, ee) qxr += (c) * (re) * (kx) / (k); qyr += (c) * (re) * (ky) / (k); ex += (ee) * (kx) / (k); ey += (ee) * (ky) / (k);
#ifdef ZV_PARALLEL
#define TM_SUMMEN zvSummen4(qxr, qyr, ex, ey);
void zvSummen4(inout float a0, inout float a1, inout float a2, inout float a3) {
    int l = int(gl_LocalInvocationID.x);
    zvTeil[l] = a0; zvTeil[ZV_LANES + l] = a1; zvTeil[2 * ZV_LANES + l] = a2; zvTeil[3 * ZV_LANES + l] = a3;
    barrier();
    for (int s = ZV_LANES / 2; s > 0; s >>= 1) {
        if (l < s) for (int k = 0; k < 4; k++) zvTeil[k * ZV_LANES + l] += zvTeil[k * ZV_LANES + l + s];
        barrier();
    }
    a0 = zvTeil[0]; a1 = zvTeil[ZV_LANES]; a2 = zvTeil[2 * ZV_LANES]; a3 = zvTeil[3 * ZV_LANES];
    barrier();
}
#else
#define TM_SUMMEN
#endif
#else
#define TM_WELLE(c, re, kx, ky, k, ee)
#define TM_SUMMEN
#endif

// Seit S6c-1b (Seegang Werkbuch 78, SF85 A): mit RANDRICHTUNG (nur zusammen mit TIEFENMITTEL, see_antrieb.comp) erhält der
// Antrieb des Auflaufs die Richtung jeder Welle im Löser. Der Löser rechnet Flachwasser ohne Dispersion: Bei ω hat er die
// Wellenzahl ω/√(g h); was längs des Rands schneller wechselt, läuft nicht hinein (Datei 77). Je Stützstelle werden Phase und
// Laufzeit der Hülle längs der Punkte um den Bezugspunkt randBezug (Mitte des Rands, relativ zu B) gestreckt:
// Φ′ = Φ_m + s (Φ − Φ_m), s = (ω/√(g h_R)) / |∇Φ_m|, T′ = T_m + (T − T_m) / (|∇T_m| √(g h_R)); der Fluss je Welle mit √(g h_R)
// statt ω/k; die Wellen des Fernfelds ebenso. randTiefe ≤ 0: aus (wie bis S6c-1). Ohne den Schalter fallen die Terme im
// Präprozessor weg; die übrigen Kernel bleiben Zeichen für Zeichen gleich.
#ifdef RANDRICHTUNG
vec2 randBezug = vec2(0.0);
float randTiefe = 0.0;
#define RR_C(c) (rr ? cR : (c))
#define RR_PHI(x) (rrS ? phiR : (x))
#define RR_TAU(x) (rrS ? tauR : (x))
#define RR_FERN(phi0, f, k) (rr ? (f).w + dot((f).xy, randBezug) + omegaTm(k) / ((k) * cR) * dot((f).xy, p - randBezug) : (phi0))
#else
#define RR_C(c) (c)
#define RR_PHI(x) (x)
#define RR_TAU(x) (x)
#define RR_FERN(phi0, f, k) (phi0)
#endif

// Seit S7b-1 (Seegang Werkbuch 88): mit BAENDER vor dem Einbinden (see_koerper.comp) sammelt punktK für den Körper je Band
// der Synthese die rohen Summen der linearen Wellen am Punkt, solange bdAn gilt, und schreibt sie in die Bindung 22 ab
// bdBasis: je Band vier vec4 (Σ |Z|, Σ |Z| k, Re C0, Im C0), (Re K1, Im K1, Re Gx, Im Gx), (Re Gy, Im Gy, Re Ux, Im Ux),
// (Re Uy, Im Uy, Re W, Im W) mit Z = re + i im je Welle, C0 = Σ Z, K1 = Σ k Z, G = Σ k⃗ Z, U = Σ ω coth(k h) k̂ Z, W = Σ ω Z.
// Die Wellen des Fernfelds kommen in das Band mit dem nächsten ω. Dazu bdKopf = (h, k_p, m0). Daraus setzt der Körper die
// Druckhöhe, ihr Gefälle und die Geschwindigkeit in der Tiefe zusammen (physics.auftrieb.Bandwasser). Ohne den Schalter
// fallen die Terme im Präprozessor weg; die übrigen Kernel bleiben Zeichen für Zeichen gleich.
#ifdef BAENDER
layout(std430, binding = 22) buffer Koerper { int zahlKopf; int bdR1; int bdR2; int bdR3; vec4 bdPk[]; };
bool bdAn = false;
int bdBasis = 0;
vec3 bdKopf = vec3(0.0);
vec2 bdGitter = vec2(0.0);                                              // seit S7d3: Gewichte fein und grob am Punkt (Befund L5d2 2)
#ifdef KOERPERFLAECHE
vec2 bdKoerper = vec2(0.0);                                             // seit S7d4: Anteile fein und grob der Körperfläche am Punkt
#define BD_KOERPER bdKoerper = vec2(sK * kpwf, sK * kpwg);
#else
#define BD_KOERPER
#endif
float bdSa = 0.0, bdSak = 0.0;
vec2 bdC0 = vec2(0.0), bdK1 = vec2(0.0), bdGx = vec2(0.0), bdGy = vec2(0.0), bdUx = vec2(0.0), bdUy = vec2(0.0), bdW = vec2(0.0);
float omegaBd(float k) { return sqrt(9.81 * k + 0.074 / 1025.0 * k * k * k); }
void bdWelle(float re, float im, float kx, float ky, float k, float om, float ct) {
    vec2 z = vec2(re, im);
    float az = length(z);
    bdSa += az; bdSak += az * k;
    bdC0 += z; bdK1 += k * z; bdGx += kx * z; bdGy += ky * z;
    if (k > 0.0) { float c = om * ct / k; bdUx += c * kx * z; bdUy += c * ky * z; }
    bdW += om * z;
}
void bdNull() { bdSa = 0.0; bdSak = 0.0; bdC0 = vec2(0.0); bdK1 = vec2(0.0); bdGx = vec2(0.0); bdGy = vec2(0.0); bdUx = vec2(0.0); bdUy = vec2(0.0); bdW = vec2(0.0); }
void bdSchreibe(int b) {
    int o = bdBasis + 4 * b;
    bdPk[o] = vec4(bdSa, bdSak, bdC0);
    bdPk[o + 1] = vec4(bdK1, bdGx);
    bdPk[o + 2] = vec4(bdGy, bdUx);
    bdPk[o + 3] = vec4(bdUy, bdW);
}
void bdDazu(int b) {
    int o = bdBasis + 4 * b;
    bdPk[o] += vec4(bdSa, bdSak, bdC0);
    bdPk[o + 1] += vec4(bdK1, bdGx);
    bdPk[o + 2] += vec4(bdGy, bdUx);
    bdPk[o + 3] += vec4(bdUy, bdW);
}
/** Das Band mit dem nächsten ω (für die Wellen des Fernfelds). */
int bdBandZu(float om, int nb, int nd) {
    int best = 0;
    float bd = 1e30;
    for (int b = 0; b < nb; b++) { float d = abs(stuetz[2 * (b * nd) + 1].x - om); if (d < bd) { bd = d; best = b; } }
    return best;
}
#define BD_KOPF(hh, kp, m0) if (bdAn) { bdKopf = vec3(hh, kp, m0); bdGitter = vec2(wf, wg); BD_KOERPER }
#define BD_ANFANG if (bdAn) bdNull();
#define BD_WELLE(re, im, kx, ky, k, om, ct) if (bdAn) bdWelle(re, im, kx, ky, k, om, ct);
#ifdef ZV_PARALLEL
// Seit S7d2 (Seegang Werkbuch 90, Befund L5d 1): mit ZV_PARALLEL teilen sich die Fäden einer Gruppe auch die Summen je Band.
// Am Ende eines Bands summiert die Gruppe die 16 Werte (geteilter Speicher), Faden 0 schreibt. Die Wellen des Fernfelds
// legt jeder Faden mit ihrem Band in den geteilten Speicher (bdFernTeil, höchstens BD_FERN_MAX); nach der Schleife summieren
// die Fäden b < nb je ein Band, Faden 0 schreibt. Alle Fäden müssen punktK gemeinsam aufrufen (wie ZV_PARALLEL).
#define BD_FERN_MAX 96
shared float bdTeil[16 * ZV_LANES];
shared float bdFernTeil[16 * BD_FERN_MAX];
shared int bdFernBand[BD_FERN_MAX];
shared float bdFernSumme[16 * 16];
void bdLege(int o) {
    bdTeil[o] = bdSa; bdTeil[o + 1] = bdSak; bdTeil[o + 2] = bdC0.x; bdTeil[o + 3] = bdC0.y;
    bdTeil[o + 4] = bdK1.x; bdTeil[o + 5] = bdK1.y; bdTeil[o + 6] = bdGx.x; bdTeil[o + 7] = bdGx.y;
    bdTeil[o + 8] = bdGy.x; bdTeil[o + 9] = bdGy.y; bdTeil[o + 10] = bdUx.x; bdTeil[o + 11] = bdUx.y;
    bdTeil[o + 12] = bdUy.x; bdTeil[o + 13] = bdUy.y; bdTeil[o + 14] = bdW.x; bdTeil[o + 15] = bdW.y;
}
void bdNimm(int o) {
    bdSa = bdTeil[o]; bdSak = bdTeil[o + 1]; bdC0 = vec2(bdTeil[o + 2], bdTeil[o + 3]);
    bdK1 = vec2(bdTeil[o + 4], bdTeil[o + 5]); bdGx = vec2(bdTeil[o + 6], bdTeil[o + 7]);
    bdGy = vec2(bdTeil[o + 8], bdTeil[o + 9]); bdUx = vec2(bdTeil[o + 10], bdTeil[o + 11]);
    bdUy = vec2(bdTeil[o + 12], bdTeil[o + 13]); bdW = vec2(bdTeil[o + 14], bdTeil[o + 15]);
}
/** Summe der 16 Werte über die Gruppe; danach haben alle Fäden sie. */
void bdReduziere() {
    int l = int(gl_LocalInvocationID.x);
    bdLege(16 * l);
    barrier();
    for (int s = ZV_LANES / 2; s > 0; s >>= 1) {
        if (l < s) for (int k = 0; k < 16; k++) bdTeil[16 * l + k] += bdTeil[16 * (l + s) + k];
        barrier();
    }
    bdNimm(0);
    barrier();
}
void bdFernLeg(int j, int band) {
    if (j >= BD_FERN_MAX) return;
    int o = 16 * j;
    bdFernBand[j] = band;
    bdFernTeil[o] = bdSa; bdFernTeil[o + 1] = bdSak; bdFernTeil[o + 2] = bdC0.x; bdFernTeil[o + 3] = bdC0.y;
    bdFernTeil[o + 4] = bdK1.x; bdFernTeil[o + 5] = bdK1.y; bdFernTeil[o + 6] = bdGx.x; bdFernTeil[o + 7] = bdGx.y;
    bdFernTeil[o + 8] = bdGy.x; bdFernTeil[o + 9] = bdGy.y; bdFernTeil[o + 10] = bdUx.x; bdFernTeil[o + 11] = bdUx.y;
    bdFernTeil[o + 12] = bdUy.x; bdFernTeil[o + 13] = bdUy.y; bdFernTeil[o + 14] = bdW.x; bdFernTeil[o + 15] = bdW.y;
}
void bdFernAnfang(int nfern) {
    for (int j = int(gl_LocalInvocationID.x); j < min(nfern, BD_FERN_MAX); j += ZV_LANES) bdFernBand[j] = -1;
    barrier();
}
/** Nach der Schleife des Fernfelds: Faden b < nb summiert Band b, Faden 0 gibt jedes Band mit Wellen dazu. */
void bdFernEnde(int nfern, int nb) {
    barrier();
    int l = int(gl_LocalInvocationID.x), n = min(nfern, BD_FERN_MAX);
    if (l < nb && l < 16) {
        float acc[16];
        for (int k = 0; k < 16; k++) acc[k] = 0.0;
        for (int j = 0; j < n; j++) if (bdFernBand[j] == l) for (int k = 0; k < 16; k++) acc[k] += bdFernTeil[16 * j + k];
        for (int k = 0; k < 16; k++) bdFernSumme[16 * l + k] = acc[k];
    }
    barrier();
    if (l == 0) {
        for (int b = 0; b < min(nb, 16); b++) {
            bool hat = false;
            for (int j = 0; j < n; j++) if (bdFernBand[j] == b) hat = true;
            if (!hat) continue;
            int o = 16 * b;
            bdSa = bdFernSumme[o]; bdSak = bdFernSumme[o + 1]; bdC0 = vec2(bdFernSumme[o + 2], bdFernSumme[o + 3]);
            bdK1 = vec2(bdFernSumme[o + 4], bdFernSumme[o + 5]); bdGx = vec2(bdFernSumme[o + 6], bdFernSumme[o + 7]);
            bdGy = vec2(bdFernSumme[o + 8], bdFernSumme[o + 9]); bdUx = vec2(bdFernSumme[o + 10], bdFernSumme[o + 11]);
            bdUy = vec2(bdFernSumme[o + 12], bdFernSumme[o + 13]); bdW = vec2(bdFernSumme[o + 14], bdFernSumme[o + 15]);
            bdDazu(b);
        }
    }
    barrier();
}
#define BD_ENDE(b) if (bdAn) { bdReduziere(); if (gl_LocalInvocationID.x == 0u) bdSchreibe(b); }
#define BD_FERN(re, im, kx, ky, k, hh) if (bdAn) { float omF = omegaBd(k); bdNull(); bdWelle(re, im, kx, ky, k, omF, 1.0 / tanh(clamp((k) * (hh), -15.0, 15.0))); bdFernLeg(j, bdBandZu(omF, nb, nd)); }
#define BD_FERN_ANFANG(n) if (bdAn) bdFernAnfang(n);
#define BD_FERN_ENDE(n) if (bdAn) bdFernEnde(n, nb);
#else
#define BD_ENDE(b) if (bdAn) bdSchreibe(b);
#define BD_FERN(re, im, kx, ky, k, hh) if (bdAn) { float omF = omegaBd(k); bdNull(); bdWelle(re, im, kx, ky, k, omF, 1.0 / tanh(clamp((k) * (hh), -15.0, 15.0))); bdDazu(bdBandZu(omF, nb, nd)); }
#define BD_FERN_ANFANG(n)
#define BD_FERN_ENDE(n)
#endif
#else
#define BD_KOPF(hh, kp, m0)
#define BD_ANFANG
#define BD_WELLE(re, im, kx, ky, k, om, ct)
#define BD_ENDE(b)
#define BD_FERN(re, im, kx, ky, k, hh)
#define BD_FERN_ANFANG(n)
#define BD_FERN_ENDE(n)
#endif

// ------------------------------------------------------------------ Gitter

/** Gewicht einer Stufe am Ort: 0 außerhalb und auf dem Rand, 1 ab rand Zellen innen (glatt). */
float gewicht(vec2 p, float x0, float y0, float dx, int nx, int ny, int rand) {
    float fx = (p.x - x0) / dx, fy = (p.y - y0) / dx;
    float d = min(min(fx, float(nx) - fx), min(fy, float(ny) - fy));
    if (d <= 0.5) return 0.0;
    float u = min(1.0, (d - 0.5) / float(rand));
    return u * u * (3.0 - 2.0 * u);
}

/** Die vier Zellmitten (c00, c10, c01, c11) und die Gewichte (ax, ay), wie Stufe.zellen. */
void zellen(vec2 p, float x0, float y0, float dx, int nx, int ny, out ivec4 c, out vec2 a) {
    float fx = clamp((p.x - x0) / dx - 0.5, 0.0, float(nx) - 1.000001), fy = clamp((p.y - y0) / dx - 0.5, 0.0, float(ny) - 1.000001);
    int i = int(fx), j = int(fy), i1 = min(nx - 1, i + 1), j1 = min(ny - 1, j + 1);
    c = ivec4(j * nx + i, j * nx + i1, j1 * nx + i, j1 * nx + i1);
    a = vec2(fx - float(i), fy - float(j));
}

vec4 bilGewichte(vec2 a) { return vec4((1.0 - a.x) * (1.0 - a.y), a.x * (1.0 - a.y), (1.0 - a.x) * a.y, a.x * a.y); }

#ifdef KOERPERFLAECHE
// Seit S7d4 (Seegang Werkbuch 92, SF121 A): mit KOERPERFLAECHE vor dem Einbinden (see_koerper*.comp) nimmt punktK die Energie
// (Varianz je Stützstelle, Kennwerte, Tiefe) außerhalb des feinen Gitters des Auges aus der Körperfläche, einer festen
// Fläche um den Hafen (Bindungen 25–31; Orte relativ zum B des Auges). Die Phasen bleiben die des Auges, damit die Schiffe
// auf den gezeichneten Wellen liegen. Was die Fläche des Auges ans Fernfeld abgibt (Rand, Überblenden), rechnet sie mit der
// Energie der Körperfläche als ebene Wellen (δΦ = 0) weiter; so hängt die Energie unter einem Körper nicht vom Auge ab.
// Ohne den Schalter fallen die Terme im Präprozessor weg; die übrigen Kernel bleiben Zeichen für Zeichen gleich.
layout(std430, binding = 25) readonly buffer KWerte { int KI[8]; float KF[8]; } KW;
layout(std430, binding = 26) readonly buffer KEFein { float keFein[]; };
layout(std430, binding = 27) readonly buffer KEGrob { float keGrob[]; };
layout(std430, binding = 28) readonly buffer KZFein { vec2 kzFein[]; };
layout(std430, binding = 29) readonly buffer KZGrob { vec2 kzGrob[]; };
layout(std430, binding = 30) readonly buffer KKFein { float kkFein[]; };
layout(std430, binding = 31) readonly buffer KKGrob { float kkGrob[]; };
// seit S7d4b (Befund L5d4 2): je Stützstelle der Versatz von Phase und Laufzeit vom Ursprung des Auges zu dem der
// Körperfläche (k₀ ΔU · e mod 2π, ΔU · e / c_g₀ mod Periode): Die ebenen Wellen hängen so nicht an der Fläche des Auges
// und springen nicht, wenn sie wechselt.
layout(std430, binding = 32) readonly buffer KPhase { vec2 kpPhase[]; };
const int KI_FEIN = 0, KI_GROB = 1, KI_F_NX = 2, KI_F_NY = 3, KI_F_RAND = 4, KI_G_NX = 5, KI_G_NY = 6, KI_G_RAND = 7;
const int KF_F_X0 = 0, KF_F_Y0 = 1, KF_F_DX = 2, KF_G_X0 = 3, KF_G_Y0 = 4, KF_G_DX = 5, KF_GEWICHT = 6, KF_ZELL = 7;
// am Punkt: Anteile fein und grob der Körperfläche (Summe 1, wo sie liegt), ihre Zellen und Gewichte
float kpwf = 0.0, kpwg = 0.0;
ivec4 kpcf = ivec4(0), kpcg = ivec4(0);
vec4 kpbf = vec4(0.0), kpbg = vec4(0.0);
int kpncf = 0, kpncg = 0;
bool kpz = false;
float kpSumme(int welche, ivec4 c, vec4 w, int j, int nj, int nc) {
    ivec4 ix = ivec4(ixE(kpz, c.x, j, nj, nc), ixE(kpz, c.y, j, nj, nc), ixE(kpz, c.z, j, nj, nc), ixE(kpz, c.w, j, nj, nc));
    if (welche == 0) return w.x * keFein[ix.x] + w.y * keFein[ix.y] + w.z * keFein[ix.z] + w.w * keFein[ix.w];
    if (welche == 1) return w.x * keGrob[ix.x] + w.y * keGrob[ix.y] + w.z * keGrob[ix.z] + w.w * keGrob[ix.w];
    if (welche == 2) return w.x * kkFein[ix.x] + w.y * kkFein[ix.y] + w.z * kkFein[ix.z] + w.w * kkFein[ix.w];
    return w.x * kkGrob[ix.x] + w.y * kkGrob[ix.y] + w.z * kkGrob[ix.z] + w.w * kkGrob[ix.w];
}
/** Kennwert k der Körperfläche am Punkt (wie kennW). */
float kennK(int k, int kw) {
    float v = 0.0;
    if (kpwf > 0.0) v += kpwf * kpSumme(2, kpcf, kpbf, k, kw, kpncf);
    if (kpwg > 0.0) v += kpwg * kpSumme(3, kpcg, kpbg, k, kw, kpncg);
    return v;
}
/** Varianz der Stützstelle bq in der Körperfläche am Punkt. */
float energieK(int bq, int m) {
    float v = 0.0;
    if (kpwf > 0.0) v += kpwf * kpSumme(0, kpcf, kpbf, bq, m, kpncf);
    if (kpwg > 0.0) v += kpwg * kpSumme(1, kpcg, kpbg, bq, m, kpncg);
    return v;
}
/** Setzt die Zellen der Körperfläche am Punkt p; gibt ihr Gewicht (0 … 1, mit dem Überblenden) und Tiefe, Gefälle. */
float koerperflaeche(vec2 p, out float hK, out float tK) {
    hK = 0.0; tK = 0.0;
    kpwf = 0.0; kpwg = 0.0;
    float gw = KW.KF[KF_GEWICHT];
    if (!(gw > 0.0)) return 0.0;
    float a0 = KW.KI[KI_FEIN] != 0 ? gewicht(p, KW.KF[KF_F_X0], KW.KF[KF_F_Y0], KW.KF[KF_F_DX], KW.KI[KI_F_NX], KW.KI[KI_F_NY], KW.KI[KI_F_RAND]) : 0.0;
    float b0 = KW.KI[KI_GROB] != 0 ? (1.0 - a0) * gewicht(p, KW.KF[KF_G_X0], KW.KF[KF_G_Y0], KW.KF[KF_G_DX], KW.KI[KI_G_NX], KW.KI[KI_G_NY], KW.KI[KI_G_RAND]) : 0.0;
    float s = a0 + b0;
    if (!(s > 0.0)) return 0.0;
    kpwf = a0 / s; kpwg = b0 / s;
    kpz = KW.KF[KF_ZELL] > 0.5;
    kpncf = KW.KI[KI_F_NX] * KW.KI[KI_F_NY]; kpncg = KW.KI[KI_G_NX] * KW.KI[KI_G_NY];
    vec2 a;
    if (kpwf > 0.0) {
        zellen(p, KW.KF[KF_F_X0], KW.KF[KF_F_Y0], KW.KF[KF_F_DX], KW.KI[KI_F_NX], KW.KI[KI_F_NY], kpcf, a);
        kpbf = bilGewichte(a);
        vec2 z = kpbf.x * kzFein[kpcf.x] + kpbf.y * kzFein[kpcf.y] + kpbf.z * kzFein[kpcf.z] + kpbf.w * kzFein[kpcf.w];
        hK += kpwf * z.x; tK += kpwf * z.y;
    }
    if (kpwg > 0.0) {
        zellen(p, KW.KF[KF_G_X0], KW.KF[KF_G_Y0], KW.KF[KF_G_DX], KW.KI[KI_G_NX], KW.KI[KI_G_NY], kpcg, a);
        kpbg = bilGewichte(a);
        vec2 z = kpbg.x * kzGrob[kpcg.x] + kpbg.y * kzGrob[kpcg.y] + kpbg.z * kzGrob[kpcg.z] + kpbg.w * kzGrob[kpcg.w];
        hK += kpwg * z.x; tK += kpwg * z.y;
    }
    return s * gw;
}
#define KENN(k) (kennW(k, wf, wgE, cf, bf, cg, bg, kw, ncf, ncg, zellE) + (sK > 0.0 ? sK * kennK(k, kw) : 0.0))
#define WG_E wgE
#define WFERN_E wfernE
#else
#define KENN(k) kennW(k, wf, wg, cf, bf, cg, bg, kw, ncf, ncg, zellE)
#define WG_E wg
#define WFERN_E wfern
#endif

// ------------------------------------------------------------------ Hülle (Profilpuffer.wert)

vec4 huelleWert(int bq, float tau, float tp) {
    float dt = tp / float(HUELLE_N);
    float t = tau - tp * floor(tau / tp);
    float x = t / dt;
    int j = min(int(x), HUELLE_N - 1);
    float u = x - float(j);
    int j1 = j + 1 == HUELLE_N ? 0 : j + 1;
    vec4 a = huelle[bq * HUELLE_N + j], c = huelle[bq * HUELLE_N + j1];
    float u2 = u * u, u3 = u2 * u;
    float h00 = 2.0 * u3 - 3.0 * u2 + 1.0, h10 = u3 - 2.0 * u2 + u, h01 = -2.0 * u3 + 3.0 * u2, h11 = u3 - u2;
    vec2 g = h00 * a.xy + h10 * dt * a.zw + h01 * c.xy + h11 * dt * c.zw;
    return vec4(g, 0.0, 0.0);
}

// ------------------------------------------------------------------ Form (Seegangsform)

/** tanh mit Klemme (Befund B37): Der Treiber der 680M rechnet tanh über exp(2x) und gibt ab x ≈ 44 NaN. */
float th(float x) { return tanh(clamp(x, -15.0, 15.0)); }

float ruessinkB(float ur) { return 0.857 / (1.0 + exp((-0.471 - log(ur)) / 0.297)); }
float ruessinkPsi(float ur) { return radians(-90.0 + 90.0 * th(0.815 / pow(ur, 0.672))); }

/** Beiwert der zweiten Ordnung: (Re c, Im c, s), wie Seegangsform.beiwert. */
vec3 beiwert(float sk, float as_, float sigma, float skLag) {
    if (!(sigma > 0.0)) return vec3(0.0, 0.0, 1.0);
    float zr = max(0.0, sk - skLag), zi = as_, tr = zr, ti = zi, s = 1.0;
    for (int it = 0; it < 20; it++) {
        s = 1.0 / sqrt(1.0 + (tr * tr + ti * ti) / 9.0);
        float s3 = s * s * s;
        tr = zr / s3; ti = zi / s3;
    }
    s = 1.0 / sqrt(1.0 + (tr * tr + ti * ti) / 9.0);
    float sl = s * sigma;
    return vec3(tr / (6.0 * sl), ti / (6.0 * sl), s);
}

/** Faktor der Hülle nach Battjes–Groenendijk, wie Seegangsform.huelle (Tafel statt Bisektion). */
float huelleBG(float z, float m0, float h, float tanA) {
    if (!(m0 > 0.0) || !(z > 0.0)) return 1.0;
    float hm0 = 4.0 * sqrt(m0);
    float htr = max(W.F[F_BG_VON], (0.35 + 5.8 * max(0.0, tanA)) * h / ((0.6725 + 0.2025 * hm0 / max(1e-6, h)) * hm0));
    if (htr > 4.0) return 1.0;
    int n = W.I[I_BG];
    float von = W.F[F_BG_VON], bis = W.F[F_BG_BIS];
    float fx = clamp((htr - von) / (bis - von) * float(n - 1), 0.0, float(n - 1) - 0.0001);
    int i = int(fx);
    vec4 p = mix(bgTafel[i], bgTafel[i + 1], fx - float(i));
    float l = z * z / (2.0 * m0);
    return l < p.x ? p.y : p.z * pow(l, 1.0 / 3.6 - 0.5);
}

float dispN(float k, float d) { float kd = k * d; return kd > 30.0 ? 0.5 : 0.5 * (1.0 + 2.0 * kd / sinh(2.0 * kd)); }

float langeWelle(float k, float h) {
    float hh = max(H_MIN, h), n = dispN(k, hh), sg = sqrt(G * k * th(k * hh)), cg = n * sg / k;
    float nenner = max(G * hh - cg * cg, W.F[F_LW_NENNER] * G * hh);
    return G * (2.0 * n - 0.5) / (2.0 * nenner);
}

float aufgeloest(float k, float d) { return d > 0.0 ? clamp(ZWEI_PI / k / (4.0 * d) - 1.0, 0.0, 1.0) : 1.0; }

#ifdef RANDRICHTUNG
/**
 * Seit S6c-1b: δΦ, δT (dM) und ihre Gradienten (∂δΦ/∂x, ∂δΦ/∂y, ∂δT/∂x, ∂δT/∂y; gM) der Stützstelle bq am Bezugspunkt des
 * Rands, fein und grob gewichtet wie am Punkt (u Anteil von fein), wie core.szene.Oberflaeche.punktRand.
 */
void randPhase(int bq, float wf, float wg, float u, ivec4 cf, vec2 af, ivec4 cg, vec2 ag, int m, int ncf, int ncg, bool jeZelle, out vec2 dM, out vec4 gM) {
    dM = vec2(0.0);
    gM = vec4(0.0);
    if (wf > 0.0) {
        ivec4 ix = jeZelle ? cf * m + bq : cf + bq * ncf;
        vec2 p00 = pFein[ix.x], p10 = pFein[ix.y], p01 = pFein[ix.z], p11 = pFein[ix.w];
        vec2 v = (1.0 - af.y) * ((1.0 - af.x) * p00 + af.x * p10) + af.y * ((1.0 - af.x) * p01 + af.x * p11);
        vec2 gx = ((1.0 - af.y) * (p10 - p00) + af.y * (p11 - p01)) / W.F[F_F_DX], gy = ((1.0 - af.x) * (p01 - p00) + af.x * (p11 - p10)) / W.F[F_F_DX];
        dM += u * v;
        gM += u * vec4(gx.x, gy.x, gx.y, gy.y);
    }
    if (wg > 0.0) {
        ivec4 ix = jeZelle ? cg * m + bq : cg + bq * ncg;
        vec2 p00 = pGrob[ix.x], p10 = pGrob[ix.y], p01 = pGrob[ix.z], p11 = pGrob[ix.w];
        vec2 v = (1.0 - ag.y) * ((1.0 - ag.x) * p00 + ag.x * p10) + ag.y * ((1.0 - ag.x) * p01 + ag.x * p11);
        vec2 gx = ((1.0 - ag.y) * (p10 - p00) + ag.y * (p11 - p01)) / W.F[F_G_DX], gy = ((1.0 - ag.x) * (p01 - p00) + ag.x * (p11 - p10)) / W.F[F_G_DX];
        float w = 1.0 - u;
        dM += w * v;
        gM += w * vec4(gx.x, gy.x, gx.y, gy.y);
    }
}
#endif

// ------------------------------------------------------------------ Punkt (Oberflaeche.punkt)

/**
 * Der Punkt der ruhenden Fläche bei p (relativ zu B) für die Größe d der Stelle. Seit S4a dazu khp = k_p h (Wellenzahl
 * des Gipfels mal Tiefe, mindestens H_MIN) für die Schwelle des Brechens im Schaumfeld. a: (η, ξx, ξy, |Z|),
 * b: (Re Z, Im Z, λ_eff, Faktor der Hülle). nah: Anteil von fein und grob (Oberflaeche.punkt mit nah; am Rand der
 * Clipmap 0, dort zeichnet sie das Fernfeld); auf: aufgelöste Neigungsvarianz Σ k² E g² und Varianz der Höhe Σ E g²
 * (Punkt.neigung, Punkt.gezeichnet; S3c, für Glitzern und Schaumköpfe).
 */
// Seit S5a: Brechen und Walze wie core.szene.Walze (Weißwasser w, Dicke t), ohne Zustand
const float W_GAMMA = 0.73, W_Q_VON = 0.71, W_Q_BIS = 0.91, W_FLAECHE = 2.5, W_DICKE = 0.5, W_HINTEN = 0.15, W_HEBUNG = 0.0;   // seit S5a-2; Hebung seit S5b-1, 0 nach B67 (Walze.HEBUNG)
vec2 walze(float zr2, float zi2, float hh, float kp) {
    float hoehe = 2.0 * sqrt(zr2 * zr2 + zi2 * zi2), hm = W_GAMMA * hh;
    float q = hm > 0.0 ? smoothstep(W_Q_VON, W_Q_BIS, hoehe / hm) : 0.0;
    if (!(q > 0.0) || !(kp > 0.0) || !(hoehe > 0.0)) return vec2(0.0);
    float a = W_FLAECHE * hoehe * hoehe, th = atan(zi2, zr2), thr = min(kp * 2.0 * a / (W_DICKE * hoehe), 1.5707963);
    float t0 = 2.0 * a * kp / thr;
    float wv = smoothstep(-W_HINTEN - 0.1, -W_HINTEN, th) * (1.0 - smoothstep(0.8 * thr, thr, th));
    float t = th >= 0.0 ? q * t0 * max(0.0, 1.0 - th / thr) : 0.0;
    return vec2(q * wv, t);
}

void punktK(vec2 p, float d, float nah, out vec4 a, out vec4 b, out vec2 auf, out float khp, out vec2 wl) {
    wl = vec2(0.0);
    int nb = W.I[I_NB], nd = W.I[I_ND], m = nb * nd, kw = 2 + 2 * nb;
    bool hatF = W.I[I_FEIN] != 0, hatG = W.I[I_GROB] != 0, hatFern = W.I[I_FERN] != 0;
    float wf = hatF ? gewicht(p, W.F[F_F_X0], W.F[F_F_Y0], W.F[F_F_DX], W.I[I_F_NX], W.I[I_F_NY], W.I[I_F_RAND]) : 0.0;
    float wg = hatG ? (1.0 - wf) * gewicht(p, W.F[F_G_X0], W.F[F_G_Y0], W.F[F_G_DX], W.I[I_G_NX], W.I[I_G_NY], W.I[I_G_RAND]) : 0.0;
    float nahG = nah * W.F[F_NAH];                                      // seit S3d: Überblenden (SeegangKarte.nahGewicht)
    wf *= nahG; wg *= nahG;
    float wfern = max(0.0, 1.0 - wf - wg);
    if (!hatFern) { float s = wf + wg; if (s > 0.0) { wf /= s; wg /= s; } wfern = 0.0; }
    ivec4 cf = ivec4(0), cg = ivec4(0);
    vec2 af = vec2(0.0), ag = vec2(0.0);
    vec4 bf = vec4(0.0), bg = vec4(0.0);
    float hf = 0.0, hg = 0.0, tf = 0.0, tg = 0.0;
    if (wf > 0.0) {
        zellen(p, W.F[F_F_X0], W.F[F_F_Y0], W.F[F_F_DX], W.I[I_F_NX], W.I[I_F_NY], cf, af);
        bf = bilGewichte(af);
        vec2 z00 = zFein[cf.x], z10 = zFein[cf.y], z01 = zFein[cf.z], z11 = zFein[cf.w];
        hf = (1.0 - af.y) * ((1.0 - af.x) * z00.x + af.x * z10.x) + af.y * ((1.0 - af.x) * z01.x + af.x * z11.x);
        tf = (1.0 - af.y) * ((1.0 - af.x) * z00.y + af.x * z10.y) + af.y * ((1.0 - af.x) * z01.y + af.x * z11.y);
    }
    if (hatG) {
        zellen(p, W.F[F_G_X0], W.F[F_G_Y0], W.F[F_G_DX], W.I[I_G_NX], W.I[I_G_NY], cg, ag);
        bg = bilGewichte(ag);
        vec2 z00 = zGrob[cg.x], z10 = zGrob[cg.y], z01 = zGrob[cg.z], z11 = zGrob[cg.w];
        hg = (1.0 - ag.y) * ((1.0 - ag.x) * z00.x + ag.x * z10.x) + ag.y * ((1.0 - ag.x) * z01.x + ag.x * z11.x);
        tg = (1.0 - ag.y) * ((1.0 - ag.x) * z00.y + ag.x * z10.y) + ag.y * ((1.0 - ag.x) * z01.y + ag.x * z11.y);
    }
    float wn = wf + wg;
    float h = wn > 0.0 ? (wf * hf + wg * hg) / wn : (hatG ? hg : W.F[F_TIEFE_FERN]);
    float tanA = wn > 0.0 ? (wf * tf + wg * tg) / wn : (hatG ? tg : 0.0);
#ifdef KOERPERFLAECHE
    // seit S7d4: Anteil sK der Körperfläche = ihr Gewicht mal alles außer dem feinen Gitter des Auges; davon wKA mit den
    // Phasen des Auges (wo es grob zeichnet), wKB als ebene Wellen (wo das Auge ans Fernfeld abgibt)
    float hK, tK;
    float wK = koerperflaeche(p, hK, tK);
    float sK = wK * (1.0 - wf), wgE = wg * (1.0 - wK), wKA = wg * wK, wKB = wfern * wK, wfernE = wfern * (1.0 - wK);
    if (sK > 0.0) {
        float nn = wf + wgE + sK;
        h = (wf * hf + wgE * hg + sK * hK) / nn;
        tanA = (wf * tf + wgE * tg + sK * tK) / nn;
    }
#endif
    float hh = max(H_MIN, h);
    // Kennwerte der Form aus dem ganzen Spektrum
    float m0 = 0.0, sxi2 = 0.0, emax = 0.0, kp = 0.0;
    int ncf = W.I[I_F_NX] * W.I[I_F_NY], ncg = W.I[I_G_NX] * W.I[I_G_NY];
    bool zellE = (W.I[I_SCHALTER] & S_E_JE_ZELLE) != 0;
    m0 = KENN(0);
    sxi2 = KENN(1);
    float sN = 0.0;                                                          // S = Σ k̄_b² E_b (Oberflaeche.neigung)
    for (int bb = 0; bb < nb; bb++) {
        float eb = KENN(2 + bb), ek = KENN(2 + nb + bb);
        if (eb > emax) { emax = eb; kp = ek / eb; }
        if (eb > 0.0) sN += ek * ek / eb;
        ZV_BAND(eb, bb)
    }
    if (WFERN_E > 0.0) {
        float mf = W.F[F_FERN_M0] * WFERN_E;
        m0 += mf;
        sxi2 += W.F[F_FERN_SXI2] * WFERN_E;
        if (mf > emax && W.F[F_FERN_KP] > 0.0) kp = W.F[F_FERN_KP];
        ZV_FERNE(mf, W.F[F_FERN_KP])
    }
    khp = kp * hh;                                                           // seit S4a: k_p h für den Kamm (see_stufe, Kamm)
    BD_KOPF(hh, kp, m0)
    // die aufgelösten Wellen
    float zr = 0.0, zi = 0.0, xr = 0.0, yr = 0.0, mitt = 0.0, neig = 0.0, varG = 0.0, eMin = max(W.F[F_E_MIN], W.F[F_E_REL] * m0), sMin = W.F[F_S_REL] * sN;
#ifdef TIEFENMITTEL
    float qxr = 0.0, qyr = 0.0, ex = 0.0, ey = 0.0;
#endif
    float u = wn > 0.0 ? wf / wn : 0.0;
    float dxf = W.F[F_F_DX], dxg = W.F[F_G_DX];
    int dg = W.I[I_SCHALTER];
    bool dHuelle = (dg & S_D_HUELLE) != 0, dPhase = (dg & S_D_PHASE) != 0, dEnergie = (dg & S_D_ENERGIE) != 0;
    bool jeZelle = (dg & S_PHASE_JE_ZELLE) != 0;
#ifdef RANDRICHTUNG
    // seit S6c-1b: Zellen und Gewichte von fein und grob am Bezugspunkt des Rands (nur ihr Verhältnis zählt)
    float wfM = 0.0, wgM = 0.0;
    ivec4 cfM = ivec4(0), cgM = ivec4(0);
    vec2 afM = vec2(0.0), agM = vec2(0.0);
    if (randTiefe > 0.0) {
        wfM = hatF ? gewicht(randBezug, W.F[F_F_X0], W.F[F_F_Y0], W.F[F_F_DX], W.I[I_F_NX], W.I[I_F_NY], W.I[I_F_RAND]) : 0.0;
        wgM = hatG ? (1.0 - wfM) * gewicht(randBezug, W.F[F_G_X0], W.F[F_G_Y0], W.F[F_G_DX], W.I[I_G_NX], W.I[I_G_NY], W.I[I_G_RAND]) : 0.0;
        if (wfM > 0.0) zellen(randBezug, W.F[F_F_X0], W.F[F_F_Y0], W.F[F_F_DX], W.I[I_F_NX], W.I[I_F_NY], cfM, afM);
        if (wgM > 0.0) zellen(randBezug, W.F[F_G_X0], W.F[F_G_Y0], W.F[F_G_DX], W.I[I_G_NX], W.I[I_G_NY], cgM, agM);
    }
    float uM = wfM + wgM > 0.0 ? wfM / (wfM + wgM) : 0.0;
    bool rr = randTiefe > 0.0 && wfM + wgM > 0.0;
    float cR = sqrt(G * max(H_MIN, randTiefe));
#endif
    for (int bb = 0; bb < nb; bb++) {
        float kU = W.F[F_K_UNTEN + bb];
        if (d > 0.0 && ZWEI_PI / kU < 4.0 * d) continue;                   // überall zu kurz: g = 0 für das ganze Band
        int von = aktiv[bb], bis = aktiv[bb + 1];
        if (von == bis) continue;
        BD_ANFANG
        float ebB = KENN(2 + bb), ekB = KENN(2 + nb + bb);
        float kqB = ebB > 0.0 ? (ekB / ebB) * (ekB / ebB) : 0.0;
        for (int ia = von ZV_START; ia < bis; ia ZV_SCHRITT) {               // nur Stützstellen, die irgendwo über der Schwelle liegen
            int bq = aktiv[nb + 1 + ia];
            float e = 0.0;
            if (dEnergie) e = wf > 0.0 ? eFein[ixE(zellE, cf.x, bq, m, ncf)] : (wg > 0.0 ? eGrob[ixE(zellE, cg.x, bq, m, ncg)] : 0.0);
            else {
                if (wf > 0.0) e += wf * sumEF(cf, bf, bq, m, ncf, zellE);
                if (WG_E > 0.0) e += WG_E * sumEG(cg, bg, bq, m, ncg, zellE);
            }
#ifdef KOERPERFLAECHE
            float eK = sK > 0.0 ? energieK(bq, m) : 0.0, eB = wKB * eK;     // seit S7d4: die Körperfläche
            e += wKA * eK;
            float fa = faktor(e + eB, eMin, kqB, sMin);
#else
            float fa = faktor(e, eMin, kqB, sMin);
#endif
            if (!(fa > 0.0)) continue;
            vec4 s0 = stuetz[2 * bq], sq = stuetz[2 * bq + 1];
#ifdef KOERPERFLAECHE
            if (e > 0.0) {                                                   // seit S7d4b: ohne Energie mit den Phasen des Auges nichts
#endif
            // Phase: δΦ, δT bilinear mit Gradient, dazu die ebene Welle (k₀ e · p, p/c_g₀) und die Werte bei B
            float dp = 0.0, dtt = 0.0, gx = 0.0, gy = 0.0;
            if (wf > 0.0 && !dPhase) {
                ivec4 ix = jeZelle ? cf * m + bq : cf + bq * ncf;
                vec2 p00 = pFein[ix.x], p10 = pFein[ix.y], p01 = pFein[ix.z], p11 = pFein[ix.w];
                float f0 = (1.0 - af.y) * ((1.0 - af.x) * p00.x + af.x * p10.x) + af.y * ((1.0 - af.x) * p01.x + af.x * p11.x);
                float t0 = (1.0 - af.y) * ((1.0 - af.x) * p00.y + af.x * p10.y) + af.y * ((1.0 - af.x) * p01.y + af.x * p11.y);
                float fxx = ((1.0 - af.y) * (p10.x - p00.x) + af.y * (p11.x - p01.x)) / dxf, fyy = ((1.0 - af.x) * (p01.x - p00.x) + af.x * (p11.x - p10.x)) / dxf;
                dp += u * f0; dtt += u * t0; gx += u * fxx; gy += u * fyy;
            }
            if (wg > 0.0 && !dPhase) {
                ivec4 ix = jeZelle ? cg * m + bq : cg + bq * ncg;
                vec2 p00 = pGrob[ix.x], p10 = pGrob[ix.y], p01 = pGrob[ix.z], p11 = pGrob[ix.w];
                float f0 = (1.0 - ag.y) * ((1.0 - ag.x) * p00.x + ag.x * p10.x) + ag.y * ((1.0 - ag.x) * p01.x + ag.x * p11.x);
                float t0 = (1.0 - ag.y) * ((1.0 - ag.x) * p00.y + ag.x * p10.y) + ag.y * ((1.0 - ag.x) * p01.y + ag.x * p11.y);
                float fxx = ((1.0 - ag.y) * (p10.x - p00.x) + ag.y * (p11.x - p01.x)) / dxg, fyy = ((1.0 - ag.x) * (p01.x - p00.x) + ag.x * (p11.x - p10.x)) / dxg;
                float v = 1.0 - u;
                dp += v * f0; dtt += v * t0; gx += v * fxx; gy += v * fyy;
            }
            float sp = dot(s0.xy, p);
            float kx = gx + sq.y * s0.x, ky = gy + sq.y * s0.y, k = sqrt(kx * kx + ky * ky);
            float g = fa * aufgeloest(max(k, kU), d);
            if (!(g > 0.0)) continue;
#ifdef RANDRICHTUNG
            // seit S6c-1b: Phase und Laufzeit längs des Rands um den Bezugspunkt gestreckt (s. oben)
            bool rrS = false;
            float phiR = 0.0, tauR = 0.0;
            if (rr && !dPhase) {
                vec2 dM;
                vec4 gM;
                randPhase(bq, wfM, wgM, uM, cfM, afM, cgM, agM, m, ncf, ncg, jeZelle, dM, gM);
                vec2 kM = vec2(gM.x + sq.y * s0.x, gM.y + sq.y * s0.y), tM = vec2(gM.z + s0.x / sq.z, gM.w + s0.y / sq.z);
                float lk = length(kM), lt = length(tM);
                if (lk > 0.0 && lt > 0.0) {
                    float spM = dot(s0.xy, randBezug), ds = dot(s0.xy, p - randBezug);
                    phiR = s0.z + sq.y * spM + dM.x ZV_PHASE(sq.x) + sq.x / (cR * lk) * (sq.y * ds + dp - dM.x);
                    tauR = s0.w + spM / sq.z + dM.y ZV_TAU + (ds / sq.z + dtt - dM.y) / (lt * cR);
                    rrS = true;
                }
            }
#endif
            vec4 gv = dHuelle ? vec4(1.0, 0.0, 0.0, 0.0) : huelleWert(bq, RR_TAU(s0.w + sp / sq.z + dtt ZV_TAU), sq.w);
            float a = g * sqrt(2.0 * e), phi = RR_PHI(s0.z + sq.y * sp + dp ZV_PHASE(sq.x));
            float co = cos(phi), si = sin(phi);
            float re = a * (gv.x * co - gv.y * si), im = a * (gv.x * si + gv.y * co);
            zr += re; zi += im;
            float ct = 1.0 / th(k * hh);
            if (k > 0.0) { xr -= ct * kx / k * im; yr -= ct * ky / k * im; TM_WELLE(RR_C(sq.x / k), re, kx, ky, k, e * g * g) }
            BD_WELLE(re, im, kx, ky, k, sq.x, ct)
            mitt += k * ct * e * g * g;
            neig += k * k * e * g * g;
            varG += e * g * g;
#ifdef KOERPERFLAECHE
            }
            if (eB > 0.0) {                                                  // seit S7d4: ihr Teil als ebene Welle (δΦ = 0)
                float kB = sq.y, gB = fa * aufgeloest(max(kB, kU), d), spB = dot(s0.xy, p);
                vec2 vB = kpPhase[bq];                                         // seit S7d4b: zum Ursprung der Körperfläche
                vec4 gvB = dHuelle ? vec4(1.0, 0.0, 0.0, 0.0) : huelleWert(bq, s0.w + vB.y + spB / sq.z ZV_TAU, sq.w);
                float aB = gB * sqrt(2.0 * eB), phB = s0.z + vB.x + sq.y * spB ZV_PHASE(sq.x), coB = cos(phB), siB = sin(phB);
                float reB = aB * (gvB.x * coB - gvB.y * siB), imB = aB * (gvB.x * siB + gvB.y * coB);
                float ctB = 1.0 / th(kB * hh);
                zr += reB; zi += imB;
                xr -= ctB * s0.x * imB; yr -= ctB * s0.y * imB;
                BD_WELLE(reB, imB, kB * s0.x, kB * s0.y, kB, sq.x, ctB)
                mitt += kB * ctB * eB * gB * gB;
                neig += kB * kB * eB * gB * gB;
                varG += eB * gB * gB;
            }
#endif
        }
        BD_ENDE(bb)
    }
    if (WFERN_E > 0.0) {
        float sw = sqrt(WFERN_E);
        int nfern = W.I[I_FERN_N];
        BD_FERN_ANFANG(nfern)
        for (int j = 0 ZV_START; j < nfern; j ZV_SCHRITT) {
            vec4 f = fern[j];
            float k = length(f.xy), g = aufgeloest(k, d), am = f.z * sw * g;
            if (am <= 0.0) continue;
            float phi = RR_FERN(f.w + dot(f.xy, p), f, k) ZV_FERN(k), co = cos(phi), si = sin(phi);
            zr += am * si; zi -= am * co;
            xr += f.x / k * am * co; yr += f.y / k * am * co;
            TM_WELLE(RR_C(omegaTm(k) / k), am * si, f.x, f.y, k, 0.5 * am * am)
            BD_FERN(am * si, -am * co, f.x, f.y, k, hh)
            mitt += k * 0.5 * am * am;
            neig += k * k * 0.5 * am * am;
            varG += 0.5 * am * am;
        }
        BD_FERN_ENDE(nfern)
    }
    ZV_SUMMEN
    TM_SUMMEN
    auf = vec2(neig, varG);
    b = vec4(zr, zi, 1.0, 1.0);
    ZV_M0(m0)
#ifdef TIEFENMITTEL
    antriebEta = 0.0;
    antriebQ = vec2(0.0);
#endif
    if (!(m0 > 0.0)) { a = vec4(0.0); return; }
    int sch = W.I[I_SCHALTER];
    float sigma = sqrt(m0), zabs = sqrt(zr * zr + zi * zi);
    float f = (sch & S_HOEHEN) != 0 ? huelleBG(zabs, m0, hh, tanA) : 1.0;
    float lam = (sch & S_VERSCHIEBUNG) != 0 ? min(W.F[F_LAMBDA], W.F[F_FALTUNG] / sqrt(max(1e-30, sxi2))) : 0.0;
    float kh = kp * hh;
    float ur = kp > 0.0 ? 0.375 * 4.0 * sigma * kp / (kh * kh * kh) : 0.0;
    vec3 c = vec3(0.0, 0.0, 1.0);
    if ((sch & S_FORM) != 0) {
        float sk = 0.0, as_ = 0.0;
        if (ur > 0.0) { float bb = ruessinkB(ur), ps = ruessinkPsi(ur); sk = bb * cos(ps); as_ = bb * sin(ps); }
        float skLag = kp > 0.0 ? W.F[F_LAGRANGE] * 3.0 * lam * kp / th(max(1e-6, kh)) * sigma : 0.0;
        c = beiwert(sk, as_, sigma, skLag);
    }
    float s = c.z, zr2 = s * f * zr, zi2 = s * f * zi;
    float eta = zr2 + c.x * (zr2 * zr2 - zi2 * zi2) - c.y * (2.0 * zr2 * zi2);
    float lw = (sch & S_LANGWELLE) != 0 && kp > 0.0 ? -langeWelle(kp, hh) * (zr2 * zr2 + zi2 * zi2 - 2.0 * m0 * s * s) : 0.0;
    eta += lw + lam * s * s * mitt;
    a = vec4(eta, lam * s * f * xr, lam * s * f * yr, sqrt(zr2 * zr2 + zi2 * zi2));
    b = vec4(zr, zi, lam, f);
    wl = walze(zr2, zi2, hh, kp);                                            // seit S5a
    a.x += W_HEBUNG * wl.y;                                                  // seit S5b-1: die Walze hebt die Front (Walze.HEBUNG)
#ifdef TIEFENMITTEL
    float lwA = kp > 0.0 ? -langeWelle(kp, hh) * (zr2 * zr2 + zi2 * zi2 - 2.0 * m0 * s * s) : 0.0;
    float eta2 = c.x * (zr2 * zr2 - zi2 * zi2) - c.y * (2.0 * zr2 * zi2), el = sqrt(ex * ex + ey * ey);
    vec2 dir = el > 0.0 ? vec2(ex, ey) / el : vec2(0.0);
    float cp = kp > 0.0 ? sqrt(G * kp * th(kp * hh)) / kp : 0.0, cgr = kp > 0.0 ? dispN(kp, hh) * cp : 0.0;
    antriebQ = s * f * vec2(qxr, qyr) + (eta2 * cp + lwA * cgr) * dir;
    antriebEta = a.x - lw + lwA;
#endif
}

/** Wie punktK, ohne k_p h (Prüfpunkte und alles bis S3d). */
void punkt(vec2 p, float d, float nah, out vec4 a, out vec4 b, out vec2 auf) {
    float khp;
    vec2 wl;
    punktK(p, d, nah, a, b, auf, khp, wl);
}
