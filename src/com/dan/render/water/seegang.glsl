// seegang.glsl – render.water, Stufe 2 (P8b, Werkbuch HalligHooge Teil A, 12.9, F76 A): der örtliche Seegang aus
// waves.* im Bild. Nach wasser.glsl einbinden. Dieselben Formeln wie core.szene.Seegangsoptik.
// Puffer: je Netz zwei Plätze zu sechs Feldern (Hs, Tp, cos θ_m, sin θ_m, mss, diss) je Zelle, ab seeN[g].x:
// Wert = see_[seeN.x + (Platz · 6 + Feld) · nx · ny + Zelle]. θ_m ist die Richtung, wohin die Wellen laufen (0 = Ost).
#ifndef SEE_BINDUNG
#define SEE_BINDUNG 10
#endif
layout(set = 0, binding = SEE_BINDUNG, std430) readonly buffer Seegang { float see_[]; };

float seeZelle(int g, int c, int feld) {
    int n = seeN[g].y * seeN[g].z, pa = int(seeZ[g].y);
    float a = see_[seeN[g].x + (pa * 6 + feld) * n + c], b = see_[seeN[g].x + ((1 - pa) * 6 + feld) * n + c];
    return a + seeZ[g].x * (b - a);
}

// Übergang am Rand eines feineren Netzes zum nächsten gröberen (m) [G]; seit S3c-6 (SF31 A, Seegang Werkbuch 21/22),
// wie core.szene.Seegangsnetz.RAND_MISCHUNG. Seit S3c-7 (SF33 A) nach der Art in seeZ[0].z (Seegangsnetz.RAND_ART):
// 0 aus, 1 innen (S3c-6), 2 außen (Vorgabe).
const float SEE_RAND = 500.0;

// Die sechs Werte des Netzes g an der Stelle (ux, uy) in Zellen, bilinear zwischen den Zellmitten (am Rand der
// Randwert), linear in der Zeit.
void seeWerteU(int g, float ux, float uy, out float w[6]) {
    int nx = seeN[g].y, ny = seeN[g].z;
    float bx = clamp(ux - 0.5, 0.0, float(nx - 1)), by = clamp(uy - 0.5, 0.0, float(ny - 1));
    int i0 = min(int(bx), nx - 2), j0 = min(int(by), ny - 2);
    i0 = max(i0, 0);
    j0 = max(j0, 0);
    float fx = bx - float(i0), fy = by - float(j0);
    int i1 = min(i0 + 1, nx - 1), j1 = min(j0 + 1, ny - 1);
    for (int f = 0; f < 6; f++) {
        float v00 = seeZelle(g, i0 + nx * j0, f), v10 = seeZelle(g, i1 + nx * j0, f);
        float v01 = seeZelle(g, i0 + nx * j1, f), v11 = seeZelle(g, i1 + nx * j1, f);
        w[f] = mix(mix(v00, v10, fx), mix(v01, v11, fx), fy);
    }
}

// Die sechs Werte des Netzes g am Ort p; rand: Abstand des Orts vom Rand des Netzes (m). false: das Netz hat den Ort nicht.
bool seeWerteNetz(int g, vec2 p, out float w[6], out float rand) {
    rand = 0.0;
    for (int f = 0; f < 6; f++) w[f] = 0.0;
    if (seeN[g].w == 0) return false;
    float ux = (p.x - seeF[g].x) * seeF[g].w, uy = (-p.y - seeF[g].y) * seeF[g].w;
    int nx = seeN[g].y, ny = seeN[g].z;
    if (ux < 0.0 || uy < 0.0 || ux >= float(nx) || uy >= float(ny)) return false;
    rand = min(min(ux, float(nx) - ux), min(uy, float(ny) - uy)) * seeF[g].z;
    seeWerteU(g, ux, uy, w);
    return true;
}

// Seit S3c-7 (SF33 A): die Randwerte des Netzes g für einen Ort p außerhalb; d: Abstand vom Netz (m, euklidisch, 0
// innen). false: das Netz ist nicht gesetzt.
bool seeWerteRand(int g, vec2 p, out float w[6], out float d) {
    d = 1e30;
    for (int f = 0; f < 6; f++) w[f] = 0.0;
    if (seeN[g].w == 0) return false;
    float ux = (p.x - seeF[g].x) * seeF[g].w, uy = (-p.y - seeF[g].y) * seeF[g].w;
    float nx = float(seeN[g].y), ny = float(seeN[g].z);
    d = length(vec2(max(0.0, max(-ux, ux - nx)), max(0.0, max(-uy, uy - ny)))) * seeF[g].z;
    seeWerteU(g, clamp(ux, 0.0, nx), clamp(uy, 0.0, ny), w);
    return true;
}

// Die sechs Werte am Ort p (x Ost, z Süd relativ zum Auge) im feinsten Netz, das ihn hat (seeWerteNetz), mit dem
// Übergang am Rand eines feineren Netzes (vorher sprang der Seegang am Rand von G3w, B49b):
// innen (S3c-6): im Streifen von SEE_RAND am Rand des feineren mit dem nächsten gröberen gemischt, das den Ort hat;
// außen (S3c-7, SF33 A): im feineren nur das feinere; außerhalb über SEE_RAND von dessen Randwert in das gröbere.
// false: kein Netz hat den Ort. Wie core.szene.Seegangsnetz.gemischt.
bool seeWerte(vec2 p, out float w[6]) {
    for (int f = 0; f < 6; f++) w[f] = 0.0;
    int N = int(see.x), art = int(seeZ[0].z + 0.5);
    for (int g = 0; g < N; g++) {
        float r1;
        if (!seeWerteNetz(g, p, w, r1)) continue;
        if (art == 1 && r1 < SEE_RAND) {
            for (int g2 = g + 1; g2 < N; g2++) {
                float v[6];
                float r2;
                if (!seeWerteNetz(g2, p, v, r2)) continue;
                float a = r1 / SEE_RAND;
                for (int f = 0; f < 6; f++) w[f] = v[f] + a * (w[f] - v[f]);
                break;
            }
        } else if (art == 2) {
            for (int h = 0; h < g; h++) {
                float v[6];
                float d;
                if (!seeWerteRand(h, p, v, d) || d >= SEE_RAND) continue;
                float a = d / SEE_RAND;
                for (int f = 0; f < 6; f++) w[f] = v[f] + a * (w[f] - v[f]);
                break;
            }
        }
        return true;
    }
    for (int f = 0; f < 6; f++) w[f] = 0.0;
    return false;
}

float seeCdf(float x) {
    float u = (x + 1.0) / 4.0 * float(CDF_N - 1);
    if (u <= 0.0) return 0.0;
    if (u >= float(CDF_N - 1)) return 1.0;
    int i = int(u);
    float a = cdf[i / 4][i % 4], b = cdf[(i + 1) / 4][(i + 1) % 4];
    return a + (u - float(i)) * (b - a);
}

float seeCdq(float x) {
    float u = (x + 1.0) / 4.0 * float(CDF_N - 1);
    if (u <= 0.0) return 1.0;
    if (u >= float(CDF_N - 1)) return 0.0;
    int i = int(u);
    float a = cdq[i / 4][i % 4], b = cdq[(i + 1) / 4][(i + 1) % 4];
    return a + (u - float(i)) * (b - a);
}

// Anteil der Energie zwischen ln(f/f_p) = a und b: unter der Spitze aus dem Anteil darunter, darüber aus dem darüber
float seeAnteil(float a, float b) { return a > 0.0 ? seeCdq(a) - seeCdq(b) : seeCdf(b) - seeCdf(a); }

// Die langen Wellen am Ort p: Neigung nach Ost und Nord und die Restvarianz der Neigung für das Glitzern
// (mss − aufgelöst, nicht negativ). aufg: die aufgelöste Varianz (für die Prüfung). fuss: Größe eines Bildpunkts (m).
// Ein Band, dessen längste Welle (band[b].z) kürzer ist als zwei Bildpunkte, trägt nichts bei (dort ist g = 0 für
// jede seiner Wellen) und wird übersprungen (Prüfstein 10 von P8b). Das Gewicht der Richtung |cos((θ − θ_m)/2)|^2s
// steht als ((1 + cos(θ − θ_m)) / 2)^s, mit cos(θ − θ_m) aus den Einheitsvektoren: gleich, ohne atan je Welle.
vec3 seegangLang(vec2 p, float fuss, float w[6], out float aufg) {
    aufg = 0.0;
    float hs = w[0], tp = w[1];
    if (!(hs > 0.0) || !(tp > 0.0)) return vec3(0.0, 0.0, max(0.0, w[4]));
    float m0 = hs * hs / 16.0, lnfp = -log(tp), s = seeAuge.w;
    vec2 dm = vec2(w[2], w[3]);
    float ldm = length(dm);
    dm = ldm > 0.0 ? dm / ldm : vec2(1.0, 0.0);
    float gx = 0.0, gn = 0.0;
    float wj[JE_BAND];
    for (int b = 0; b < BAENDER; b++) {
        if (band[b].z <= 2.0 * fuss) continue;
        float v = m0 * seeAnteil(band[b].x - lnfp, band[b].y - lnfp);
        if (v <= 0.0) continue;
        float sw = 0.0;
        for (int j = 0; j < JE_BAND; j++) {
            vec4 q = lang[b * JE_BAND + j];
            float h = 0.5 * (1.0 + dot(q.xy, dm) / length(q.xy));
            wj[j] = h > 0.0 ? exp(s * log(h)) : 0.0;
            sw += wj[j];
        }
        if (sw <= 0.0) continue;
        for (int j = 0; j < JE_BAND; j++) {
            vec4 q = lang[b * JE_BAND + j];
            float k = length(q.xy), a2 = 2.0 * v * wj[j] / sw, lam = 2.0 * L_PI / k;
            float g = clamp(lam / (2.0 * fuss) - 1.0, 0.0, 1.0);
            if (g > 0.0) {
                float sl = sqrt(a2) * k * g * cos(q.x * p.x - q.y * p.y + q.z);
                gx += sl * q.x / k;
                gn += sl * q.y / k;
                aufg += g * g * 0.5 * a2 * k * k;
            }
        }
    }
    return vec3(gx, gn, max(0.0, w[4] - aufg));
}

float seeHash(vec2 q) { return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453); }

float seeRausch(vec2 q) {
    vec2 i = floor(q), f = fract(q), u = f * f * (3.0 - 2.0 * f);
    return mix(mix(seeHash(i), seeHash(i + vec2(1.0, 0.0)), u.x), mix(seeHash(i + vec2(0.0, 1.0)), seeHash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Anteil Schaum im Bildpunkt am Ort p: Bedeckung W = min(1, diss / see.z), als Muster aus Rauschen auf 3 m und 0,8 m,
// das mit 1 m/s in Richtung der Wellen wandert [G]. Ohne diss genau 0.
float schaum(vec2 p, float w[6]) {
    float W = clamp(w[5] / see.z, 0.0, 1.0);
    if (W <= 0.0) return 0.0;
    vec2 d = vec2(w[2], w[3]);
    float ld = length(d);
    d = ld > 1e-6 ? d / ld : vec2(0.0);
    vec2 q = vec2(p.x + seeAuge.x, -p.y + seeAuge.y) - d * seeAuge.z;
    float n = 0.65 * seeRausch(q / 3.0) + 0.35 * seeRausch(q / 0.8 + vec2(17.0, 5.0));
    return smoothstep(1.0 - W - 0.06, 1.0 - W + 0.06, n);
}

// ------------------------------------------------------------------ seit P13c (Werkbuch HalligHooge 12.14, F159 A, F161 A)
// Dieselben Formeln wie core.szene.Seegangsoptik (amplituden, hoehe, kamm, phi, phiInv, schaum, brechen). Nur mit
// seeMehr.z = 1 (Schalter brandung); sonst gelten seegangLang und schaum wie bis P13b.
const float GAMMA_B = 0.78, AMIN = 1e-3;
const float TAU1 = 0.5, TAU2 = 1.5, ABKLING = 2.0;
const float C0 = 0.7, C4 = 0.35, FLECK1 = 3.0, FLECK2 = 0.8, STREIF_LANG = 20.0, STREIF_QUER = 1.5, C1 = 0.5, C2 = 0.3, C3 = 0.8;
const float STREIF_AB = 17.0, STREIF_VOLL = 25.0;

// Amplituden je Welle (a_j g_j in m) für die Größe eines Bildpunkts fuss; Ergebnis: x ganze Varianz der Höhe m0,
// y aufgelöste Varianz der Höhe, z aufgelöste Neigungsvarianz. Wellen unter AMIN tragen 0.
vec3 seeAmplituden(float fuss, float w[6], out float a[LANG]) {
    for (int j = 0; j < LANG; j++) a[j] = 0.0;
    vec3 m = vec3(0.0);
    float hs = w[0], tp = w[1];
    if (!(hs > 0.0) || !(tp > 0.0)) return m;
    float m0 = hs * hs / 16.0, lnfp = -log(tp), s = seeAuge.w;
    vec2 dm = vec2(w[2], w[3]);
    float ldm = length(dm);
    dm = ldm > 0.0 ? dm / ldm : vec2(1.0, 0.0);
    float wj[JE_BAND];
    for (int b = 0; b < BAENDER; b++) {
        float v = m0 * seeAnteil(band[b].x - lnfp, band[b].y - lnfp);
        if (v <= 0.0) continue;
        float sw = 0.0;
        for (int j = 0; j < JE_BAND; j++) {
            vec4 q = lang[b * JE_BAND + j];
            float h = 0.5 * (1.0 + dot(q.xy, dm) / length(q.xy));
            // h^s; für s = 8 (Seegangsoptik.S) durch dreimal Quadrieren statt exp und log (Lauf C2: Kosten)
            float h2 = h * h, h4 = h2 * h2;
            wj[j] = h > 0.0 ? (s == 8.0 ? h4 * h4 : exp(s * log(h))) : 0.0;
            sw += wj[j];
        }
        if (sw <= 0.0) continue;
        for (int j = 0; j < JE_BAND; j++) {
            vec4 q = lang[b * JE_BAND + j];
            float k = length(q.xy), a2 = 2.0 * v * wj[j] / sw, lam = 2.0 * L_PI / k;
            float g = clamp(lam / (2.0 * fuss) - 1.0, 0.0, 1.0);
            float am = sqrt(a2) * g;
            m.x += 0.5 * a2;
            if (am < AMIN) continue;
            a[b * JE_BAND + j] = am;
            m.y += 0.5 * am * am;
            m.z += 0.5 * am * am * k * k;
        }
    }
    return m;
}

// Höhe der linearen langen Wellen am Ort p (x Ost, z Süd relativ zum Auge) zur Zeit des Bilds minus tau (s):
// x ζ = Σ a sin φ, y ∂ζ/∂Ost, z ∂ζ/∂Nord (passend zur Neigung in seegangLang). Wellen mit a ≤ amin zählen nicht
// (amin = 0: alle; die Vorsuche des Strahls nimmt nur die tragenden).
vec3 seeHoehe(vec2 p, float a[LANG], float tau, float amin) {
    vec3 r = vec3(0.0);
    for (int j = 0; j < LANG; j++) {
        if (a[j] <= amin) continue;
        vec4 q = lang[j];
        float ph = q.x * p.x - q.y * p.y + q.z;
        if (tau != 0.0) {
            float k = length(q.xy);
            ph -= sqrt(9.81 * k + 7.2195122e-5 * k * k * k) * tau;
        }
        float cs = cos(ph);
        r += a[j] * vec3(sin(ph), cs * q.x, cs * q.y);
    }
    return r;
}

// Anteil des Kamms an der Wellenhöhe (0,5 tief … 0,8 flach) wie com.dan.surf.BreakerShape.crestShare [G]
float seeKammAnteil(float hs, float h) { return 0.5 + 0.3 * clamp((hs / max(0.05, h) - 0.1) / 0.6, 0.0, 1.0); }

// Spitze Kämme, flache Täler: x ζ_s, y Faktor der Neigung (Seegangsoptik.kamm)
vec2 seeKamm(float z, float sig, float beta) {
    if (!(sig > 0.0) || !(beta > 0.0)) return vec2(z, 1.0);
    float n = sqrt(1.0 + 0.5 * beta * beta), zt = -sig / beta, f;
    if (z < zt) { z = zt; f = 0.0; } else f = (1.0 + beta * z / sig) / n;
    return vec2((z + beta * (z * z - sig * sig) / (2.0 * sig)) / n, f);
}

// Normalverteilung: Φ über erf (Abramowitz und Stegun 7.1.26) und Φ⁻¹ nach Acklam
float seePhi(float x) {
    float z = abs(x) * 0.70710678, t = 1.0 / (1.0 + 0.3275911 * z);
    float y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-z * z);
    return x >= 0.0 ? 0.5 * (1.0 + y) : 0.5 * (1.0 - y);
}
float seePhiInv(float p) {
    p = clamp(p, 1e-7, 1.0 - 1e-7);
    const float pl = 0.02425;
    if (p < pl || p > 1.0 - pl) {
        float q = sqrt(-2.0 * log(p < pl ? p : 1.0 - p));
        float x = (((((-7.784894002430293e-03 * q - 3.223964580411365e-01) * q - 2.400758277161838e+00) * q - 2.549732539343734e+00) * q + 4.374664141464968e+00) * q + 2.938163982698783e+00)
                / ((((7.784695709041462e-03 * q + 3.224671290700398e-01) * q + 2.445134137142996e+00) * q + 3.754408661907416e+00) * q + 1.0);
        return p < pl ? x : -x;
    }
    float q = p - 0.5, r = q * q;
    return (((((-3.969683028665376e+01 * r + 2.209460984245205e+02) * r - 2.759285104469687e+02) * r + 1.383577518672690e+02) * r - 3.066479806614716e+01) * r + 2.506628277459239e+00) * q
         / (((((-5.447609879822406e+01 * r + 1.615858368580409e+02) * r - 1.556989798598866e+02) * r + 6.680131188771972e+01) * r - 1.328068155288572e+01) * r + 1.0);
}

// Seit Lauf C2 (Datei 101): ganzzahliger Hash statt fract(sin(x) · 43758). Dessen Argument wächst hier über 10⁶; in float
// zerfällt er dann zu Streifen (A35, auch mit Filter noch sichtbar). Gleich in Java (BrandungCheck.rauschN), bitgleich.
uint seeHashU(uint x) { x ^= x >> 16; x *= 0x7feb352du; x ^= x >> 15; x *= 0x846ca68bu; x ^= x >> 16; return x; }
float seeHashI(ivec2 i) { return float(seeHashU(uint(i.x) * 0x9E3779B1u ^ seeHashU(uint(i.y) + 0x632BE5ABu)) >> 8) * (1.0 / 16777216.0); }
// Normalverteiltes Rauschen (Lauf C2, Datei 101): je Gitterpunkt ein Wert nach Box-Muller, bilinear mit glatten Gewichten
// und auf die Streuung 1 zurückgeführt (Σw² je Ort) – damit ist jeder Ort genau N(0, 1) und das Mittel der Schaumköpfe
// genau W (Wertrauschen hat dünnere Ränder: bei W = 2 % zu wenig Köpfe, 0,95).
float seeGauss(ivec2 i) {
    float u1 = max(seeHashI(i), 1e-7), u2 = seeHashI(i + ivec2(7919, 104729));
    return sqrt(-2.0 * log(u1)) * cos(6.2831853 * u2);
}
float seeRauschN(vec2 q) {
    vec2 f0 = floor(q), f = q - f0, u = f * f * (3.0 - 2.0 * f);
    ivec2 i = ivec2(f0);
    vec4 w = vec4((1.0 - u.x) * (1.0 - u.y), u.x * (1.0 - u.y), (1.0 - u.x) * u.y, u.x * u.y);
    float n = w.x * seeGauss(i) + w.y * seeGauss(i + ivec2(1, 0)) + w.z * seeGauss(i + ivec2(0, 1)) + w.w * seeGauss(i + ivec2(1, 1));
    return n / sqrt(dot(w, w));
}

// Schaumköpfe (F161 A, Seegangsoptik.schaum): Bedeckung W = diss / D_KAL; Lage über der Schwelle von X = ζ/√m0 + Muster;
// was der Bildpunkt nicht auflöst, als Wahrscheinlichkeit (im Mittel W, auch in der Ferne: A35). zLin: lineare Höhe am Ort.
float schaumKoepfe(vec2 p, float fuss, float w[6], float zLin, vec3 m) {
    float W = clamp(w[5] / see.z, 0.0, 1.0);
    if (W <= 0.0) return 0.0;
    if (W >= 1.0) return 1.0;
    // A35 (Lauf C4, Datei 103): das Muster treibt mit 1 m/s in EINER Richtung für das ganze Bild (Wind). Bis P13b trieb es
    // mit der örtlichen Laufrichtung d über die Zeit mod 4096 s: q = Ort − d · t. Ändert sich d über 100 m um 1°, schiebt das
    // bei t ≈ 4 000 s das Muster um rund 70 m – es wird gestreckt und geschert, daraus die langen, harten Streifen.
    float U = length(seeMehr.xy), ws = clamp((U - STREIF_AB) / (STREIF_VOLL - STREIF_AB), 0.0, 1.0);
    vec2 wr = U > 1e-3 ? seeMehr.xy / U : vec2(1.0, 0.0);
    vec2 q = vec2(p.x + seeAuge.x, -p.y + seeAuge.y) - wr * seeAuge.z;
    vec2 qs = vec2(dot(q, wr) / STREIF_LANG, dot(q, vec2(-wr.y, wr.x)) / STREIF_QUER);
    float g1 = clamp(FLECK1 / (2.0 * fuss) - 1.0, 0.0, 1.0), g2 = clamp(FLECK2 / (2.0 * fuss) - 1.0, 0.0, 1.0);
    float g3 = clamp(STREIF_QUER / (2.0 * fuss) - 1.0, 0.0, 1.0), c3 = C3 * ws;
    // C4: Feinstruktur unter 0,3 m, nie aufgelöst (F164 A) – weiche Ränder, dichte und lichte Stellen; das Mittel bleibt W
    float v = C0 * C0 + C1 * C1 + C2 * C2 + c3 * c3 + C4 * C4;
    float xa = (m.x > 0.0 ? C0 * zLin / sqrt(m.x) : 0.0) + g1 * C1 * seeRauschN(q / FLECK1) + g2 * C2 * seeRauschN(q / FLECK2 + vec2(17.0, 5.0)) + g3 * c3 * seeRauschN(qs + vec2(3.0, 11.0));
    float va = (m.x > 0.0 ? C0 * C0 * m.y / m.x : 0.0) + g1 * g1 * C1 * C1 + g2 * g2 * C2 * C2 + g3 * g3 * c3 * c3;
    float zq = sqrt(v) * seePhiInv(1.0 - W), su = sqrt(max(v - va, 1e-4));
    return 1.0 - seePhi((zq - xa) / su);
}

// Brechen an einem Kamm (McCowan): ζ_s / Kammanteil gegen γ h, ± 10 % weich [G] (Seegangsoptik.brechen)
float seeBrechen(float zs, float ka, float h) {
    if (!(h > 0.0) || zs <= 0.0) return 0.0;
    return smoothstep(0.9, 1.1, zs / ka / (GAMMA_B * h));
}
