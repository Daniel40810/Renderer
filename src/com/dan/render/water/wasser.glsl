// wasser.glsl – render.water (P6d, Werkbuch HalligHooge Teil A, 12.7): der Spiegel aus water.eta und die Optik der
// Grundstufe. Raum relativ zum Auge: x Ost, y oben, z Süd (Gitter UTM 32); Erdkrümmung mit Refraktion wie das Gelände
// (y − k r², k = 1 / 2R', R' = R / (1 − 0,13)).
//
// Bindungen (gleich in allen Shadern des Moduls): 0 Kopf, 1 Stände, 2 Art (je Zelle Art und die Schwellen der West- und
// Südkante als float-Bits), 3 Himmel/Werte, 4 Himmel/Durchlaessigkeit,
// 5 Himmel/Ansicht; wasser.frag dazu 6 HDR unter dem Wasser, 7 Tiefe unter dem Wasser.
//
// η_Bild (die Regel, die WaterCheck auf dem Prozessor nachrechnet): im feinsten gesetzten Gitter, das den Ort hat.
// Ist die eigene Zelle zur Zeit t nicht nass, gibt es dort kein Wasser. Sonst bilinear zwischen den vier Zellmitten
// um den Ort; ein Nachbar zählt mit seinem Wert, wenn er nass ist, im Gitter liegt und kein Bauwerk ihn von der eigenen
// Zelle trennt (über Eck: einer der beiden Wege über eine Seitenzelle ohne Bauwerk), sonst mit dem Wert der eigenen
// Zelle; eine Kante ohne Bauwerk trennt ebenso, wenn das Wasser über sie fällt (F47 A, ueberfall). Je Zelle ist η linear in der Zeit zwischen den Ständen A und B: a + s (b − a), s in float wie Bus.lies.
// Nass zur Zeit t: (s < 1 und nass in A) oder (s > 0 und nass in B). Gerechnet in float mit „precise“ (ohne
// zusammengefasste Multiplikation und Addition) und ohne Division, damit Karte und Prozessor gleich rechnen.
// Saum (seit C1b, Werkbuch HalligHooge 137, A52; Netz.z.y = 1): Eine trockene Zelle zeigt den Spiegel ihres nächsten
// Seitennachbarn nach Ost/West und nach Nord/Süd (von zweien den höheren), wenn er nass ist und die Kante weder ein
// Bauwerk trägt noch über seinem Spiegel liegt; das Gelände schneidet dann die Wasserlinie wie in einer nassen Zelle.
#include "/com/dan/core/gpu/luft.glsl"

const int NETZE = 8, WELLEN = 48;
// Seegang (P8b, F76 A; core.szene.Seegangsoptik): Netze, lange Wellen, Bänder, Stellen der Tafel des JONSWAP
const int SEENETZE = 4, LANG = 72, BAENDER = 6, JE_BAND = 12, CDF_N = 64;
const float KEIN = -1.0e30;

struct Netz {
    ivec4 n;        // Zelle des Auges i, j (kann außerhalb liegen), nx, ny
    ivec4 lage;     // erste Zelle in Art (Stände ab 2 · lage.x), Platz von A (0 oder 1), 1 = gesetzt, Bauwerke
    vec4 f;         // Lage des Auges in seiner Zelle (0 … 1) nach Ost und Nord, Zelle (m), 1 / Zelle
    vec4 z;         // Gewicht s von B, Saum (1/0; seit C1b), 0, 0
};

layout(set = 0, binding = 0, std430) readonly buffer Kopf {
    vec4 auge;      // Augenhöhe (m NHN), k = 1 / 2R', Zahl der Netze, Pegel als Anfang der Suche (m NHN)
    vec4 rechts;    // rechts · a / t
    vec4 oben;      // oben / t
    vec4 vorn;      // Blickachse; w nah (m)
    vec4 bild;      // Breite, Höhe, Neigungsvarianz σ², kleinste Restvarianz
    vec4 aW;        // Absorption reines Wasser (1/m) R G B; Brechzahl
    vec4 aP;        // Absorption Schwebstoff (1/m) R G B; Schwebstoff (g/m³)
    vec4 bb;        // Rückstreuung (1/m) R G B; Zahl der Wellen
    vec4 licht;     // Winkel eines Bildpunkts (rad), 1 = Spiegelung des Geländes, Teil (P7f, 0 = alles), 1 = wirksame Fläche (P7g)
    Netz netz[NETZE];
    vec4 welle[WELLEN];   // Wellenzahl Ost, Nord (rad/m), Phase am Auge zur Zeit des Bilds, Neigungsamplitude
    // seit P8b (Stufe 2, F76 A); ohne Seegang ist see.x = 0 und alles bleibt wie in Stufe 1
    vec4 see;             // Zahl der Netze des Seegangs, Zahl der langen Wellen, diss für volle Schaumdecke (W/m²), Albedo des Schaums
    vec4 seeAuge;         // Ost, Nord des Auges modulo 4096 m (Muster des Schaums), Zeit modulo 4096 s, s der Richtungsverteilung
    ivec4 seeN[SEENETZE]; // erster Wert im Puffer des Seegangs, nx, ny, 1 = gesetzt
    vec4 seeF[SEENETZE];  // Südwestecke relativ zum Auge (Ost, Nord in m), Zelle (m), 1 / Zelle
    vec4 seeZ[SEENETZE];  // Gewicht s von B, Platz von A, 0, 0
    vec4 lang[LANG];      // Wellenzahl Ost, Nord (rad/m), Phase am Auge zur Zeit des Bilds, Band
    vec4 band[BAENDER];   // ln f unten, ln f oben (Hz), längste Welle des Bands (m), 0
    vec4 cdf[CDF_N / 4];  // Anteil des JONSWAP unter ln(f/f_p) von −1 bis 3
    vec4 cdq[CDF_N / 4];  // derselbe Anteil darüber (1 − cdf, für die Bänder über der Spitze ohne Auslöschung in float)
    // seit P9b (Trübung am Ort, 12.10); ohne Netz ist bodN.x = 0 und die Säule rechnet mit aP und bb des Kopfs
    ivec4 bodN;           // 1 = gesetzt, nx, ny, Platz von A
    vec4 bodF;            // Südwestecke relativ zum Auge (Ost, Nord in m), Zelle (m), 1 / Zelle
    vec4 bodZ;            // Gewicht s von B, C der ersten Stufe der Tafeln (g/m³), 1 / ln(Faktor der Stufen), Breite der Überblendung am Rand (Zellen)
    // seit P13c (Brandung, 12.14): Wind U₁₀ Ost, Nord (m/s), 1 = lange Wellen mit Höhe, Weißwasser und Schaumköpfe (Schalter brandung), 0
    vec4 seeMehr;
};
layout(set = 0, binding = 1, std430) readonly buffer Staende { float st[]; };
layout(set = 0, binding = 2, std430) readonly buffer Arten { uint art[]; };
layout(set = 0, binding = 3, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 4) uniform sampler2D durch;
layout(set = 0, binding = 5) uniform sampler2D ansichtT;

// ------------------------------------------------------------------ Spiegel

uint artBei(int g, int i, int j) { return art[3 * (netz[g].lage.x + i + netz[g].n.z * j)]; }

// Schwelle der West- (k = 1) bzw. Südkante (k = 2) der Zelle (m NHN; F47 A)
float schwelle(int g, int i, int j, int k) { return uintBitsToFloat(art[3 * (netz[g].lage.x + i + netz[g].n.z * j) + k]); }

bool nassJetzt(int g, uint a) {
    float s = netz[g].z.x;
    uint pa = uint(netz[g].lage.y), pb = 1u - pa;
    return (s < 1.0 && (a & (1u << pa)) != 0u) || (s > 0.0 && (a & (1u << pb)) != 0u);
}

float etaZelle(int g, int i, int j) {
    Netz q = netz[g];
    int n = q.n.z * q.n.w, c = i + q.n.z * j, b = 2 * q.lage.x;
    float a = st[b + q.lage.y * n + c], e = st[b + (1 - q.lage.y) * n + c];
    precise float w = a + q.z.x * (e - a);
    return w;
}

// Fällt das Wasser zur Zeit t über die Kante mit der Schwelle s zwischen (i, j) und (i2, j2)? (F47 A, vollkommener
// Überfall: beide nass, das Unterwasser steht weniger als 2/3 so hoch über der Schwelle wie das Oberwasser.)
bool ueberfall(int g, int i, int j, int i2, int j2, float s) {
    if (!nassJetzt(g, artBei(g, i, j)) || !nassJetzt(g, artBei(g, i2, j2))) return false;
    float a = etaZelle(g, i, j), b = etaZelle(g, i2, j2);
    precise float unten = min(a, b) - s;
    precise float oben = max(a, b) - s;
    precise float l = 3.0 * unten;
    precise float r = 2.0 * oben;
    return l < r;
}

// Ist die Kante zwischen den Nachbarn (i, j) und (i2, j2) offen: kein Bauwerk und kein Überfall?
bool offen(int g, int i, int j, int i2, int j2) {
    int ci = i, cj = j, k = 1;
    uint bit = 4u;
    if (i2 == i + 1) ci = i2;
    else if (j2 == j + 1) { cj = j2; k = 2; bit = 8u; }
    else if (j2 == j - 1) { k = 2; bit = 8u; }
    if ((artBei(g, ci, cj) & bit) != 0u) return false;
    return !ueberfall(g, i, j, i2, j2, schwelle(g, ci, cj, k));
}

// Saum (C1b, A52): Spiegel des Seitennachbarn (ni, nj) einer trockenen Zelle (ci, cj); KEIN, wenn er trocken ist, ein
// Bauwerk ihn trennt oder die Kante über seinem Spiegel liegt.
float saumNachbar(int g, int ci, int cj, int ni, int nj) {
    Netz q = netz[g];
    if (ni < 0 || nj < 0 || ni >= q.n.z || nj >= q.n.w) return KEIN;
    if (!nassJetzt(g, artBei(g, ni, nj))) return KEIN;
    int ki = ci, kj = cj, k = 1;
    uint bit = 4u;
    if (ni == ci + 1) ki = ni;
    else if (nj == cj + 1) { kj = nj; k = 2; bit = 8u; }
    else if (nj == cj - 1) { k = 2; bit = 8u; }
    if ((artBei(g, ki, kj) & bit) != 0u) return KEIN;
    float e = etaZelle(g, ni, nj);
    return e > schwelle(g, ki, kj, k) ? e : KEIN;
}

float saum(int g, int ci, int cj, int ni, int nj) {
    return max(saumNachbar(g, ci, cj, ni, cj), saumNachbar(g, ci, cj, ci, nj));
}

float nachbar(int g, int ci, int cj, int ni, int nj, float eo) {
    Netz q = netz[g];
    if (ni < 0 || nj < 0 || ni >= q.n.z || nj >= q.n.w || (ni == ci && nj == cj)) return eo;
    bool frei;
    if (ni != ci && nj != cj) frei = (offen(g, ci, cj, ni, cj) && offen(g, ni, cj, ni, nj)) || (offen(g, ci, cj, ci, nj) && offen(g, ci, nj, ni, nj));
    else frei = offen(g, ci, cj, ni, nj);
    if (!frei || !nassJetzt(g, artBei(g, ni, nj))) return eo;
    return etaZelle(g, ni, nj);
}

// η_Bild am Ort p = (x Ost, z Süd) relativ zum Auge; KEIN, wo kein Wasser ist. Gitter g (Ausgabe): das genutzte.
float etaBild(vec2 p, out int gitter) {
    gitter = -1;
    int N = int(auge.z);
    for (int g = 0; g < N; g++) {
        Netz q = netz[g];
        if (q.lage.z == 0) continue;
        precise float ux = p.x * q.f.w + q.f.x;
        precise float uy = -p.y * q.f.w + q.f.y;
        float fx0 = floor(ux), fy0 = floor(uy);
        int ci = q.n.x + int(fx0), cj = q.n.y + int(fy0);
        if (ci < 0 || cj < 0 || ci >= q.n.z || cj >= q.n.w) continue;
        gitter = g;
        precise float bx = ux - 0.5;
        precise float by = uy - 0.5;
        float gx = floor(bx), gy = floor(by);
        int i0 = q.n.x + int(gx), j0 = q.n.y + int(gy);
        if (!nassJetzt(g, artBei(g, ci, cj))) return q.z.y > 0.5 ? saum(g, ci, cj, i0 == ci ? ci + 1 : i0, j0 == cj ? cj + 1 : j0) : KEIN;
        float eo = etaZelle(g, ci, cj);
        precise float fx = bx - gx;
        precise float fy = by - gy;
        float v00 = nachbar(g, ci, cj, i0, j0, eo), v10 = nachbar(g, ci, cj, i0 + 1, j0, eo);
        float v01 = nachbar(g, ci, cj, i0, j0 + 1, eo), v11 = nachbar(g, ci, cj, i0 + 1, j0 + 1, eo);
        precise float e0 = v00 + (v10 - v00) * fx;
        precise float e1 = v01 + (v11 - v01) * fx;
        precise float e = e0 + (e1 - e0) * fy;
        return e;
    }
    return KEIN;
}

// Abstand längs d bis zur gekrümmten Fläche der Höhe eta (m NHN); < 0: kein Treffer.
float treffen(vec3 d, float eta) {
    float c = eta - auge.x, a = auge.y * dot(d.xz, d.xz), b = d.y;
    float D = b * b + 4.0 * a * c;
    if (c >= 0.0 || D < 0.0) return -1.0;
    float nenner = -b + sqrt(D);
    if (nenner <= 0.0) return -1.0;
    return -2.0 * c / nenner;
}

// Der Strahl d trifft den Spiegel: feste Stelle t = treffen(d, η_Bild(d t)), höchstens 8 Schritte. Ausgabe t, η.
bool suche(vec3 d, out float t, out float eta) {
    float e = auge.w;
    int g;
    t = -1.0;
    eta = KEIN;
    for (int k = 0; k < 8; k++) {
        float tt = treffen(d, e);
        if (tt < 0.0) return false;
        float en = etaBild(d.xz * tt, g);
        if (en == KEIN) return false;
        t = tt;
        eta = en;
        if (abs(en - e) < 1e-4) break;
        e = en;
    }
    t = treffen(d, eta);
    return t > 0.0;
}

// ------------------------------------------------------------------ Optik

float fresnel(float ci, float n) {
    ci = clamp(ci, 0.0, 1.0);
    float st2 = (1.0 - ci * ci) / (n * n), ct = sqrt(max(0.0, 1.0 - st2));
    float rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
    return 0.5 * (rs * rs + rp * rp);
}

// cos des gebrochenen Winkels im Wasser zu cos des Einfalls in Luft
float gebrochen(float ci, float n) { return sqrt(max(0.0, 1.0 - (1.0 - ci * ci) / (n * n))); }

// Dämpfung K = a_w + a_p + b_b (1/m)
vec3 daempfung() { return aW.rgb + aP.rgb + bb.rgb; }

// Normale aus den aufgelösten kurzen Wellen am Ort p (x Ost, z Süd) im Rahmen mit der Senkrechten oben; fuss: Größe
// eines Bildpunkts auf dem Wasser (m). rest: Neigungsvarianz, die der Bildpunkt nicht auflöst (für das Glitzern).
// lang (P8b): Neigung der langen Wellen nach Ost und Nord und ihre Restvarianz; null ohne Seegang (dann wie Stufe 1).
// seit P10d (F103 A): aufgK gibt die aufgelöste Varianz der Kräuselung zurück (für die ganze Varianz der Fläche)
vec3 kraeuselungMit(vec2 p, vec3 oben, float fuss, vec3 lang, out float rest, out float aufgK) {
    float gx = 0.0, gn = 0.0, aufg = 0.0;
    int nw = int(bb.w);
    for (int k = 0; k < nw; k++) {
        vec4 w = welle[k];
        float kk = length(w.xy), lam = 2.0 * L_PI / kk;
        float g = clamp(lam / (2.0 * fuss) - 1.0, 0.0, 1.0);
        if (g <= 0.0) continue;
        float c = cos(w.x * p.x - w.y * p.y + w.z) * w.w * g;
        gx += c * w.x / kk;
        gn += c * w.y / kk;
        aufg += g * g * 0.5 * w.w * w.w;
    }
    rest = max(bild.z - aufg, bild.w) + lang.z;
    aufgK = aufg;
    gx += lang.x;
    gn += lang.y;
    // Fläche y = η(x, z): Normale (−∂η/∂x, 1, −∂η/∂z) mit ∂η/∂z = −∂η/∂Nord
    vec3 o = oben, ost = normalize(vec3(1.0, 0.0, 0.0) - oben * oben.x), sued = normalize(vec3(0.0, 0.0, 1.0) - oben * oben.z);
    return normalize(o - ost * gx + sued * gn);
}

vec3 kraeuselung(vec2 p, vec3 oben, float fuss, out float rest) { float a; return kraeuselungMit(p, oben, fuss, vec3(0.0), rest, a); }

// Smith G1 für Beckmann (Walter u. a. 2007, Gl. 27) für cos zur Normalen c und α = √σ² (wie core.szene.Flaeche.g1)
float smithG1(float c, float alpha) {
    if (c <= 0.0) return 0.0;
    float t = sqrt(max(0.0, 1.0 - c * c)) / c;
    if (t <= 0.0) return 1.0;
    float a = 1.0 / (max(alpha, 1e-4) * t);
    return a < 1.6 ? (3.535 * a + 2.181 * a * a) / (1.0 + 2.276 * a + 2.577 * a * a) : 1.0;
}

// Glitzern einer Lichtquelle nach Cox und Munk: L = E⊥ F p / (4 μ_v μ_n⁴), p = exp(−tan²β / σ²) / (π σ²) um die Normale n;
// seit P11d (A24, Werkbuch HalligHooge Datei 60) mal Schattung und Verdeckung nach Smith G1(v) · G1(l) um n wie die Referenz
// (render.reference.Facette) – ohne sie war das Glitzern bei flachem Blick und tiefer Sonne 1,4- bis 5-mal zu hell
vec3 glanz(vec3 v, vec3 n, vec3 l, vec3 E, float s2) {
    float ln = dot(l, n);
    if (ln <= 0.0) return vec3(0.0);
    vec3 h = normalize(l + v);
    float mn = dot(h, n), mv = dot(v, n);
    if (mn <= 0.0 || mv <= 1e-3) return vec3(0.0);
    float c2 = mn * mn, t2 = (1.0 - c2) / c2;
    float p = exp(-t2 / s2) / (L_PI * s2);
    float al = sqrt(s2);
    return E * fresnel(dot(l, h), aW.w) * p / (4.0 * mv * c2 * c2) * smithG1(mv, al) * smithG1(ln, al);
}

// Licht einer Quelle außerhalb der Luft (lx) am Ort in der Höhe h über dem Meer, durch die Luft gedämpft
vec3 lichtAm(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}
