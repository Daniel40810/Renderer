// hydro.glsl — gemeinsamer Teil der Kernels von hydro auf der Karte (Werkbuch HalligHooge Teil A, 12.5, Plan P4c).
// Dieselben Formeln wie com.dan.physics.hydro.Hydro, in float. Alle Kernels binden dieselben Puffer (Satz 0) in dieser
// Reihenfolge; HydroKarte belegt sie. Tabellen aus der Packung (float16, je zwei in einem uint), Stufen relativ zur
// Basis wie Subgrid.Packung. Kanten: erst quer zu x ((nx + 1) · ny), dann quer zu y (nx · (ny + 1)).

layout(std430, binding = 0) readonly buffer Werte {
    int nx, ny, zellen, kantenX, kanten, stufen, nRand, randArt;   // randArt: 2 Bit je Seite (0 Wand, 1 Wasserstand, 2 Durchfluss)
    int antrieb;                                                     // Bits: 1 Wind, 2 Luftdruck, 4 Kraft aus dem Seegang
    int smagEin, pad0, pad1;
    float dx, dy, zf, g, rhoW, rhoL, smag, hWind, hTrocken, muPoleni;
    float randAbstand;                                               // Seiten mit Wasserstand: Abstand des Werts zur Mitte der inneren Zelle (Zellweiten)
    float pad3;
} W;
layout(std430, binding = 1) readonly buffer Tab { uint tab[]; };
layout(std430, binding = 2) readonly buffer Basis { float basis[]; };
layout(std430, binding = 3) buffer Eta { float eta[]; };
// Volumen in zwei floats (seit P5): vol[c] der Hauptteil, vol[zellen + c] der Rest; dahinter das Bezugsvolumen V(η_b)
// ebenso ([2 zellen, 4 zellen)), η_b = max(0, oberste Stufe). In großen, tiefen Zellen (G0) hätte V in einem float eine
// Stelle von 10⁴ m³; über der obersten Stufe wird η darum als η_b + (V − V_b) / A gerechnet, beide Teile genau.
layout(std430, binding = 4) buffer Vol { float vol[]; };
layout(std430, binding = 5) buffer U { float u[]; };
layout(std430, binding = 6) buffer UNeu { float uNeu[]; };
layout(std430, binding = 7) buffer Fluss { float fluss[]; };
layout(std430, binding = 8) buffer Ha { float ha[]; };
layout(std430, binding = 9) buffer Q { float q[]; };
layout(std430, binding = 10) buffer Faktor { float faktor[]; };
layout(std430, binding = 11) buffer Clip { float clip[]; };
// Ränder: je halbem Schritt m und Randkante r zwei Werte: Wasserstand oder Durchfluss (m²/s ins Gebiet), Boden der Geisterzelle
layout(std430, binding = 12) readonly buffer Rand { float rand[]; };
// Bilanz je Randkante: zu, Ausgleich, ab, Ausgleich (kompensierte Summe, m³)
layout(std430, binding = 13) buffer Bilanz { float bilanz[]; };
// Geometrie je Zeile (seit P5, geographische Gitter): f (ny), Zellfläche (ny), Breite (ny), Höhe (ny), tan φ / R (ny),
// je Kantenzeile quer zu y (ny + 1): Länge, Abstand der Mitten, tan φ / R; zuletzt der Rest der Zellfläche gegen double
// (ny). In UTM überall gleich (tan 0).
layout(std430, binding = 14) readonly buffer Zeilen { float fcor[]; };
// Antrieb je Zelle: Wind x, Wind y (m/s), Luftdruck (Pa), Kraft x, y (N/m²), je ein Block von zellen Werten
layout(std430, binding = 15) readonly buffer Antrieb { float antr[]; };
layout(std430, binding = 16) readonly buffer BauNr { int bauNr[]; };   // Nummer des Bauwerks, −1, oder GESPERRT
// je Bauwerk 8 Werte: Sohle, A (0: Deich), μ, binnen (−1, 0, +1), Stellen, Versatz im Profil, niedrigste Krone bzw. Sohle, –
layout(std430, binding = 17) readonly buffer BauDaten { float bau[]; };
layout(std430, binding = 18) readonly buffer BauProfil { float profil[]; };   // Krone je Stelle; ab 1e29: zu
layout(std430, binding = 19) buffer Red { float red[]; };

layout(push_constant) uniform Schritt { float dt; int m; } P;   // m: halber Schritt am Anfang im Randpuffer (beim Zeitschritt: Art)

const int WAND = 0, WASSERSTAND = 1, DURCHFLUSS = 2;
const float NICHTS = -1e30;
const int GESPERRT = -2;   // Kante des groben Gitters unter und an einem feinen: kein Fluss, kein Impuls (u setzt grobu)

// ------------------------------------------------------------------ Tabellen

float halbe(uint i) {
    vec2 v = unpackHalf2x16(tab[i >> 1]);
    return (i & 1u) == 0u ? v.x : v.y;
}

uint zellVersatz(int c) { return uint(c) * 3u * uint(W.stufen); }
uint kantenVersatz(int e) { return uint(W.zellen) * 3u * uint(W.stufen) + uint(e) * 3u * uint(W.stufen); }

/** Stufe l mit Stufe(l) ≤ x (relativ zur Basis), −1 unter der tiefsten. */
int stufe(uint o, float x) {
    if (!(x > halbe(o))) return -1;
    int l = W.stufen - 1;
    while (halbe(o + uint(l)) > x) l--;
    return l;
}

/** Tiefste Pixelhöhe der Zelle (m NHN). */
float grund(int c) { return basis[c] + halbe(zellVersatz(c)); }

float zflaeche(int j) { return fcor[W.ny + j]; }
float zbreite(int j) { return fcor[2 * W.ny + j]; }
float zhoehe(int j) { return fcor[3 * W.ny + j]; }
float ztan(int j) { return fcor[4 * W.ny + j]; }
float klaengeY(int j) { return fcor[5 * W.ny + j]; }
float kabstandY(int j) { return fcor[6 * W.ny + 1 + j]; }
float ktanY(int j) { return fcor[7 * W.ny + 2 + j]; }
float zflaecheRest(int j) { return fcor[8 * W.ny + 3 + j]; }

/** Tabelle der Zelle (welche: 1 Volumen, 2 benetzte Fläche) beim Wasserstand h, mal Zellfläche. */
float zellWert(int c, float h, int welche) {
    float zf = zflaeche(c / W.nx);
    uint o = zellVersatz(c);
    float x = h - basis[c];
    int l = stufe(o, x);
    if (l < 0) return 0.0;
    uint S = uint(W.stufen);
    float x0 = halbe(o + uint(l)), d0 = zf * halbe(o + uint(welche) * S + uint(l));
    if (l == W.stufen - 1) return welche == 1 ? d0 + zf * (x - x0) : zf;
    float x1 = halbe(o + uint(l + 1)), d1 = zf * halbe(o + uint(welche) * S + uint(l + 1));
    return d0 + (d1 - d0) * (x - x0) / (x1 - x0);
}
float volumen(int c, float h) { return zellWert(c, h, 1); }
float benetzt(int c, float h) { return zellWert(c, h, 2); }

/** Speicher dV/dη (m²): Sehne der Volumentabelle, an einer Stufe die kleinere (seit P5, wie Hydro.speicher). */
float speicher(int c, float h) {
    float zf = zflaeche(c / W.nx);
    uint o = zellVersatz(c), S = uint(W.stufen);
    float x = h - basis[c];
    int l = stufe(o, x);
    if (l < 0) return 0.0;
    if (l == W.stufen - 1) return zf;
    float x0 = halbe(o + uint(l)), x1 = halbe(o + uint(l + 1));
    float sl = zf * (halbe(o + S + uint(l + 1)) - halbe(o + S + uint(l))) / (x1 - x0);
    if (l > 0 && x - x0 < 1e-6) {
        float xm = halbe(o + uint(l - 1));
        sl = min(sl, zf * (halbe(o + S + uint(l)) - halbe(o + S + uint(l - 1))) / (x0 - xm));
    }
    return sl;
}

/** Wasserstand zum Volumen (Umkehrung); trocken: tiefste Pixelhöhe. */
float etaAus(int c, float v) {
    uint o = zellVersatz(c), S = uint(W.stufen);
    float zf = zflaeche(c / W.nx);
    if (!(v > 0.0)) return basis[c] + halbe(o);
    int l = W.stufen - 1;
    while (l > 0 && zf * halbe(o + S + uint(l)) > v) l--;
    float x0 = halbe(o + uint(l)), v0 = zf * halbe(o + S + uint(l));
    if (l == W.stufen - 1) return basis[c] + x0 + (v - v0) / zf;
    float dv = zf * halbe(o + S + uint(l + 1)) - v0;
    return dv > 0.0 ? basis[c] + x0 + (v - v0) / dv * (halbe(o + uint(l + 1)) - x0) : basis[c] + x0;
}

/** Bezugshöhe der Zelle: die oberste Stufe, wenigstens 0 m NHN (seit P5). */
float bezugEta(int c) { return max(0.0, basis[c] + halbe(zellVersatz(c) + uint(W.stufen) - 1u)); }

/**
 * Produkt (zf + zr) · d genau als p + e (seit P5; zf + zr die Zellfläche in double): d kommt aus float16 (11 Bit); zf
 * wird in einen Teil mit 13 Bit und den Rest geteilt, dann ist das Hauptprodukt in float exakt.
 */
void produkt(float zf, float zr, float d, out float p, out float e) {
    precise float zh = uintBitsToFloat(floatBitsToUint(zf) & 0xFFFFF800u);
    precise float zl = (zf - zh) + zr;
    p = zh * d;
    e = zl * d;
}

/**
 * Wasserstand zum Volumen hi + lo (zwei floats, seit P5). Über der obersten Stufe η_b + (V − V_b) / A mit dem
 * Bezugsvolumen; dazwischen von der näheren Stufe aus, die Volumen der Stufen als genaue Produkte. So bleibt η auch in
 * großen, tiefen Zellen (G0) auf float-Genauigkeit des Wasserstands, nicht des Volumens.
 */
float etaAus2(int c, float hi, float lo) {
    uint o = zellVersatz(c), S = uint(W.stufen);
    float zf = zflaeche(c / W.nx);
    if (!(hi + lo > 0.0)) return basis[c] + halbe(o);
    int l = W.stufen - 1;
    while (l > 0 && zf * halbe(o + S + uint(l)) > hi) l--;
    float x0 = halbe(o + uint(l));
    if (l == W.stufen - 1) {
        precise float rb = (hi - vol[2 * W.zellen + c]) + (lo - vol[3 * W.zellen + c]);
        return bezugEta(c) + rb / zf;
    }
    float d0 = halbe(o + S + uint(l)), d1 = halbe(o + S + uint(l + 1)), x1 = halbe(o + uint(l + 1));
    float dv = zf * (d1 - d0);
    if (!(dv > 0.0)) return basis[c] + x0;
    float p0, e0, p1, e1;
    float zr = zflaecheRest(c / W.nx);
    produkt(zf, zr, d0, p0, e0);
    produkt(zf, zr, d1, p1, e1);
    precise float r0 = ((hi - p0) + lo) - e0;
    precise float r1 = ((p1 - hi) - lo) + e1;
    return r0 <= r1 ? (basis[c] + x0) + r0 / dv * (x1 - x0) : (basis[c] + x1) - r1 / dv * (x1 - x0);
}

/** Tabelle der Kante (welche: 1 h_K, 2 h_A) beim Wasserstand h. */
float kantenWert(int e, float h, int welche) {
    uint o = kantenVersatz(e), S = uint(W.stufen);
    float x = h - basis[W.zellen + 2 * e];
    int l = stufe(o, x);
    if (l < 0) return 0.0;
    float x0 = halbe(o + uint(l)), d0 = halbe(o + uint(welche) * S + uint(l));
    if (l == W.stufen - 1) return d0 + (x - x0);
    float x1 = halbe(o + uint(l + 1)), d1 = halbe(o + uint(welche) * S + uint(l + 1));
    return d0 + (d1 - d0) * (x - x0) / (x1 - x0);
}
float hA(int e, float h) { return kantenWert(e, h, 2); }
float hK(int e, float h) { return kantenWert(e, h, 1); }
float kWeite(int e) { return basis[W.zellen + 2 * e + 1]; }
float kantenGrund(int e) { return basis[W.zellen + 2 * e] + halbe(kantenVersatz(e)); }

// ------------------------------------------------------------------ Kanten

int kanteX(int i, int j) { return i + (W.nx + 1) * j; }
int kanteY(int i, int j) { return W.kantenX + i + W.nx * j; }
float laenge(int e) { return e < W.kantenX ? zhoehe(e / (W.nx + 1)) : klaengeY((e - W.kantenX) / W.nx); }
/** Abstand der Mitten der beiden Zellen einer Kante (m). */
float abstand(int e) { return e < W.kantenX ? zbreite(e / (W.nx + 1)) : kabstandY((e - W.kantenX) / W.nx); }

int links(int e) {
    if (e < W.kantenX) { int i = e % (W.nx + 1), j = e / (W.nx + 1); return i > 0 ? (i - 1) + W.nx * j : -1; }
    int k = e - W.kantenX, i = k % W.nx, j = k / W.nx;
    return j > 0 ? i + W.nx * (j - 1) : -1;
}
int rechts(int e) {
    if (e < W.kantenX) { int i = e % (W.nx + 1), j = e / (W.nx + 1); return i < W.nx ? i + W.nx * j : -1; }
    int k = e - W.kantenX, i = k % W.nx, j = k / W.nx;
    return j < W.ny ? i + W.nx * j : -1;
}
/** Seite des Rands (0 West, 1 Ost, 2 Süd, 3 Nord), −1 innen. */
int seite(int e) {
    if (e < W.kantenX) { int i = e % (W.nx + 1); return i == 0 ? 0 : i == W.nx ? 1 : -1; }
    int j = (e - W.kantenX) / W.nx;
    return j == 0 ? 2 : j == W.ny ? 3 : -1;
}
int art(int s) { return (W.randArt >> (2 * s)) & 3; }
/** Nummer der Randkante: West (j), Ost (j), Süd (i), Nord (i). */
int randIndex(int e) {
    if (e < W.kantenX) { int i = e % (W.nx + 1), j = e / (W.nx + 1); return i == 0 ? j : W.ny + j; }
    int k = e - W.kantenX, i = k % W.nx, j = k / W.nx;
    return j == 0 ? 2 * W.ny + i : 2 * W.ny + W.nx + i;
}
float randWert(int m, int r) { return rand[(m * W.nRand + r) * 2]; }
float randBoden(int m, int r) { return rand[(m * W.nRand + r) * 2 + 1]; }

/**
 * Wasserstand stromauf (nach dem Vorzeichen von u, bei u = 0 die höhere Seite) zum halben Schritt m. false: stromauf
 * liegt eine trockene Zelle, eine trockene Geisterzelle oder eine Wand.
 */
bool obenEta(int e, float ue, int m, out float h) {
    int a = links(e), b = rechts(e), s = seite(e);
    float ea = a >= 0 ? eta[a] : 0.0, eb = b >= 0 ? eta[b] : 0.0;
    h = 0.0;
    int r = 0;
    if (s >= 0) {
        int t = art(s);
        if (t == WAND) return false;
        if (t == DURCHFLUSS) {
            int innen = a >= 0 ? a : b;
            h = eta[innen];
            return vol[innen] > 0.0;
        }
        r = randIndex(e);
        float rv = randWert(m, r);
        if (a < 0) ea = rv;
        else eb = rv;
    }
    bool nachB = ue > 0.0 || ue == 0.0 && ea >= eb;
    int von = nachB ? a : b;
    h = nachB ? ea : eb;
    if (von >= 0 && !(vol[von] > 0.0)) return false;
    if (von < 0 && !(h > randBoden(m, r))) return false;
    return true;
}

/**
 * V ← V + d in zwei floats (seit P5; Summe nach Knuth, ohne Umstellen gerechnet): negatives Volumen wird abgeschnitten
 * und gezählt, η neu aus der Tabelle.
 */
void volumenDazu(int c, float d) {
    precise float a = vol[c];
    precise float s = a + d;
    precise float bb = s - a;
    precise float err = (a - (s - bb)) + (d - bb);
    precise float lo = vol[W.zellen + c] + err;
    precise float t = s + lo;
    lo = lo - (t - s);
    if (t + lo < 0.0) { clip[c] += -(t + lo); t = 0.0; lo = 0.0; }
    vol[c] = t;
    vol[W.zellen + c] = lo;
    eta[c] = etaAus2(c, t, lo);
}
