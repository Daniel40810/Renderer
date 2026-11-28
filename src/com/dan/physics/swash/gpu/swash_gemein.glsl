// swash_gemein.glsl — der Löser für Auflauf und Rücklauf auf der Karte (Seegang Werkbuch, S6b): dieselben Formeln wie
// com.dan.physics.swash.Swash in float. Gitter mit zwei Lagen Randzellen, Index (j + G) · px + i + G. Der Grund steht
// relativ zum Ruhewasser (bezug), damit dünne Filme nicht in der Auslöschung von η − b untergehen; Fels ist b ≥ FELS.
//
//  0 Werte    I[16], F[16] (Indizes unten)
//  1 Zelle    vec4 je Zelle: Grund (relativ), Manning n, Durchlässigkeit K, Grundwasser (relativ)
//  2 U        vec4 je Zelle: h, hu, hv, –
//  3 U0       vec4 je Zelle: Stand am Anfang des Schritts (Heun)
//  4 Steig    vec4 × 3 je Zelle (seit Swash 0.3, SF74 A): Kanten in x (h₋, h₊, η₋, η₊), in y (h₋, h₊, η₋, η₊),
//             Steigungen der Geschwindigkeit (sU, sV, tU, tV)
//  5 FX       vec4 je Kante in x (j · (nx + 1) + a, Kante zwischen a − 1 und a): Masse, längs, Druck links, Druck rechts
//  6 FY       vec4 je Kante in y (b · nx + i): Masse, längs, Druck unten, Druck oben
//  7 Theta    float je Zelle: Kürzung des Abflusses (Bollermann u. a. 2013)
//  8 Spur     vec4 je Zelle: versickert oberhalb der Front F, höchster Stand (relativ; ≤ −1e29: nie), zuletzt nass, versickert (Summe)
//  9 Bilanz   vec4 je Zelle: Zufluss über die Seiten (m³, Summe), abgeschnitten (m³, Summe), –, –
// 10 Antrieb  vec4 je Platz: Stand der einlaufenden Welle (relativ), Geschwindigkeit ins Gitter, längs, –
// 11 Tempo    float je Zeile: größte Wellengeschwindigkeit |u| + √(g h)
// 12 Kachel   int je Kachel 16 × 8, zwei Hälften im Wechsel je Stufe (seit S6b-1b): Bit 1 nass, Bit 2 Sickerwasser
// 13 UAus     nur mit VERSCHMOLZEN (swash_stufe): Ziel der Stufe
// 14 Bild     nur mit VERSCHMOLZEN (seit S6c-2b, Seegang Werkbuch 83): das Bild des Rechtecks (swash_bild.comp), als Worte;
//             Stufe 2 schreibt die Zellen, deren Stand sich ändert

layout(std430, binding = 0) readonly buffer Werte { int I[16]; float F[16]; } W;
layout(std430, binding = 1) readonly buffer Zelle { vec4 zelle[]; };
layout(std430, binding = 2) buffer UA { vec4 u[]; };
layout(std430, binding = 3) buffer UB { vec4 u0[]; };
layout(std430, binding = 4) buffer Steig { vec4 steig[]; };
layout(std430, binding = 5) buffer FX { vec4 fx[]; };
layout(std430, binding = 6) buffer FY { vec4 fy[]; };
layout(std430, binding = 7) buffer Theta { float theta[]; };
layout(std430, binding = 8) buffer Spur { vec4 spur[]; };
layout(std430, binding = 9) buffer Bilanz { vec4 bilanz[]; };
layout(std430, binding = 10) readonly buffer Antrieb { vec4 antrieb[]; };
layout(std430, binding = 11) buffer Tempo { float tempo[]; };
layout(std430, binding = 12) buffer Kachel { int kachel[]; };
#ifdef VERSCHMOLZEN
// 13 UAus   vec4 je Zelle: Ziel der Stufe im verschmolzenen Weg (Zustand im Wechsel zweier Puffer, seit S6b-1b)
layout(std430, binding = 13) writeonly buffer UAus { vec4 uAus[]; };
// 14 Bild   seit S6c-2b: je Zelle (h, Stand relativ, zuletzt nass (s, absolut; −1e30 nie), versickert F) als Bits von float,
//           danach je Zeile der Spülsaum (swash_bild.comp)
layout(std430, binding = 14) writeonly buffer BildS { uint bildU[]; };
int bildSchreiben = 0;   // swash_stufe setzt es aus dem Schub: 1 die Zellen in diesem Schritt schreiben (letzter Schritt eines Bilds)

#endif

// Werte.I
const int I_NX = 0, I_NY = 1, I_PX = 2, I_RAND = 3, I_AOFF = 7, I_ASLOT = 11, I_MMAX = 12, I_TEIL = 13;   // I_RAND, I_AOFF: je Seite W, E, S, N; I_TEIL: Front im Teilgitter (seit Swash 0.3)
// Werte.F
const int F_DX = 0, F_G = 1, F_HTR = 2, F_HGE = 3, F_VMAX = 4, F_HSP = 5, F_PSI = 6, F_DTH = 7;
const int G = 2, WAND = 0, OFFEN = 1, ANTRIEB = 2;
const int SW = 0, SE = 1, SS = 2, SN = 3;
const float FELS = 1e29;

int nx() { return W.I[I_NX]; }
int ny() { return W.I[I_NY]; }
int id(int i, int j) { return (j + G) * W.I[I_PX] + i + G; }
bool fels(int c) { return zelle[c].x >= FELS; }
float vel(float q, float h) { return h > W.F[F_HGE] ? q / h : 0.0; }
float minmod(float a, float b) { return a * b <= 0.0 ? 0.0 : (abs(a) < abs(b) ? a : b); }
#ifdef VERSCHMOLZEN
/** Seit S6c-2b: das Bild der Zelle (i, j) nach der Stufe (den Spülsaum je Zeile rechnet swash_bild.comp). */
void bildZelle(int i, int j, float h, float b, float tNass, float sick) {
    int o = 4 * (j * nx() + i);
    bildU[o] = floatBitsToUint(h);
    bildU[o + 1] = floatBitsToUint(h + b);
    bildU[o + 2] = floatBitsToUint(tNass <= -1e29 ? -1e30 : tNass);
    bildU[o + 3] = floatBitsToUint(sick);
}
#endif

// ---------------------------------------------------------------- Formeln (seit S6b-1b hier, für die einzelnen Kernel und swash_stufe)

/** Steigungen nach minmod (Swash.steigungen) aus der Zelle und ihren Nachbarn, je (h, hu, hv, Grund); Fels: Grund ≥ FELS. */
void steigung(vec4 c, vec4 l, vec4 r, vec4 d, vec4 o, out vec4 s, out vec4 t) {
    s = vec4(0.0);
    t = vec4(0.0);
    if (c.w >= FELS) return;
    float hc = c.x, ec = hc + c.w, uu = vel(c.y, hc), vv = vel(c.z, hc);
    if (!(l.w >= FELS || r.w >= FELS)) {
        s.x = minmod(hc - l.x, r.x - hc);
        s.y = minmod(ec - l.x - l.w, r.x + r.w - ec);
        s.z = minmod(uu - vel(l.y, l.x), vel(r.y, r.x) - uu);
        s.w = minmod(vv - vel(l.z, l.x), vel(r.z, r.x) - vv);
    }
    if (!(d.w >= FELS || o.w >= FELS)) {
        t.x = minmod(hc - d.x, o.x - hc);
        t.y = minmod(ec - d.x - d.w, o.x + o.w - ec);
        t.z = minmod(uu - vel(d.y, d.x), vel(o.y, o.x) - uu);
        t.w = minmod(vv - vel(d.z, d.x), vel(o.z, o.x) - vv);
    }
    if (hc <= W.F[F_HGE]) { s.z = 0.0; s.w = 0.0; t.z = 0.0; t.w = 0.0; }
}

/** Seit Swash 0.3 (SF74 A): Steigung des Grunds aus den Nachbarn (minmod; neben Fels 0), wie Swash.steigungGrund. */
float grundSteigung(float bl, float bc, float br) { return (bl >= FELS || br >= FELS) ? 0.0 : minmod(bc - bl, br - bc); }

/** Seit Swash 0.3: Tiefe für den Quellterm aus den Kanten (Swash.quellTiefe). */
float quellTiefe(float hm, float hp, float db) {
    if (hm > 0.0 && hp > 0.0) return 0.5 * (hm + hp);
    return db != 0.0 ? 0.5 * (hm * hm - hp * hp) / db : 0.0;
}

/** Seit Swash 0.3: Kanten (h₋, h₊, η₋, η₊) einer Zelle an der Front: ebener Spiegel w über dem linearen Grund b ± g/2. */
vec4 kantenFront(float w, float bc, float g) {
    float bm = bc - 0.5 * g, bp = bc + 0.5 * g, em = max(w, bm), ep = max(w, bp);
    return vec4(em - bm, ep - bp, em, ep);
}

/**
 * Seit Swash 0.3: Steigungen der Geschwindigkeit einer Zelle c in einer Richtung (Nachbarn a davor, z dahinter) nach
 * minmod, wie in steigung(): (aus hu, aus hv); 0 neben Fels und unter H_GE.
 */
vec2 geschwSteigung(vec4 c, vec4 a, vec4 z) {
    if (c.w >= FELS || a.w >= FELS || z.w >= FELS || c.x <= W.F[F_HGE]) return vec2(0.0);
    float uu = vel(c.y, c.x), vv = vel(c.z, c.x);
    return vec2(minmod(uu - vel(a.y, a.x), vel(z.y, z.x) - uu), minmod(vv - vel(a.z, a.x), vel(z.z, z.x) - vv));
}

/**
 * Seit Swash 0.3 (SF74 A): die Kanten einer Zelle c (h, hu, hv, Grund) mit den Nachbarn l, r, d, o (Swash.spiegelZelle,
 * .kanten): fxo, fyo = (h₋, h₊, η₋, η₊) je Richtung, gewöhnlich Mitte ± halbe Steigung (minmod wie steigung()). Mit I_TEIL
 * an der Front im Teilgitter: trocken der lineare Grund; nass mit trockenem Nachbarn oder nicht ganz bedeckt der ebene
 * Spiegel (im Keil w = b − S/2 + √(2 h S), S = |g_x| + |g_y|). Die Steigungen der Geschwindigkeit: geschwSteigung.
 */
void kantenZelle(vec4 c, vec4 l, vec4 r, vec4 d, vec4 o, out vec4 fxo, out vec4 fyo) {
    float hc = c.x, bc = c.w, ec = hc + bc, htr = W.F[F_HTR];
    fxo = vec4(hc, hc, ec, ec);
    fyo = fxo;
    if (bc >= FELS) return;
    bool teil = W.I[I_TEIL] != 0;
    float gx = 0.0, gy = 0.0;
    if (teil) {
        gx = grundSteigung(l.w, bc, r.w);
        gy = grundSteigung(d.w, bc, o.w);
        if (!(hc > htr)) {
            fxo = vec4(0.0, 0.0, bc - 0.5 * gx, bc + 0.5 * gx);
            fyo = vec4(0.0, 0.0, bc - 0.5 * gy, bc + 0.5 * gy);
            return;
        }
    }
    if (!(l.w >= FELS || r.w >= FELS)) {
        float sx = minmod(hc - l.x, r.x - hc), sy = minmod(ec - l.x - l.w, r.x + r.w - ec);
        fxo = vec4(hc - 0.5 * sx, hc + 0.5 * sx, ec - 0.5 * sy, ec + 0.5 * sy);
    }
    if (!(d.w >= FELS || o.w >= FELS)) {
        float tx = minmod(hc - d.x, o.x - hc), ty = minmod(ec - d.x - d.w, o.x + o.w - ec);
        fyo = vec4(hc - 0.5 * tx, hc + 0.5 * tx, ec - 0.5 * ty, ec + 0.5 * ty);
    }
    if (!teil) return;
    float sg = abs(gx) + abs(gy);
    bool keil = sg > 0.0 && hc < 0.5 * sg;
    bool front = keil || (l.w < FELS && !(l.x > htr)) || (r.w < FELS && !(r.x > htr)) || (d.w < FELS && !(d.x > htr)) || (o.w < FELS && !(o.x > htr));
    if (!front) return;
    float w = keil ? bc - 0.5 * sg + sqrt(2.0 * hc * sg) : ec;
    fxo = kantenFront(w, bc, gx);
    fyo = kantenFront(w, bc, gy);
}

/** HLL: links (hL, ηL, unL, utL), rechts; gibt (Masse, längs, Druck links, Druck rechts) mit den Korrekturen nach Audusse (Swash.hll). */
vec4 hll(float hL, float eL, float unL, float utL, float hR, float eR, float unR, float utR) {
    float g = W.F[F_G], htr = W.F[F_HTR];
    float bL = eL - hL, bR = eR - hR, bf = max(bL, bR);
    float hLs = max(0.0, eL - bf), hRs = max(0.0, eR - bf);
    float o0 = 0.0, o1 = 0.0, o2 = 0.0;
    if (!(hLs <= htr && hRs <= htr)) {
        float cL = sqrt(g * hLs), cR = sqrt(g * hRs);
        float sl, sr;
        if (hLs <= htr) { sl = unR - 2.0 * cR; sr = unR + cR; }
        else if (hRs <= htr) { sl = unL - cL; sr = unL + 2.0 * cL; }
        else { sl = min(unL - cL, unR - cR); sr = max(unL + cL, unR + cR); }
        float qL = hLs * unL, qR = hRs * unR;
        float fL0 = qL, fL1 = qL * unL + 0.5 * g * hLs * hLs, fL2 = qL * utL;
        float fR0 = qR, fR1 = qR * unR + 0.5 * g * hRs * hRs, fR2 = qR * utR;
        if (sl >= 0.0) { o0 = fL0; o1 = fL1; o2 = fL2; }
        else if (sr <= 0.0) { o0 = fR0; o1 = fR1; o2 = fR2; }
        else {
            float inv = 1.0 / (sr - sl);
            o0 = (sr * fL0 - sl * fR0 + sl * sr * (hRs - hLs)) * inv;
            o1 = (sr * fL1 - sl * fR1 + sl * sr * (qR - qL)) * inv;
            o2 = (sr * fL2 - sl * fR2 + sl * sr * (hRs * utR - hLs * utL)) * inv;
        }
    }
    float o3 = 0.5 * g * (hL * hL - hLs * hLs), o4 = 0.5 * g * (hR * hR - hRs * hRs);
    return vec4(o0, o2, o1 + o3, o1 + o4);
}

/**
 * Fluss über eine Kante (Swash.raten): Zelle a (links bzw. unten) und c mit (h, q normal, q längs, Grund), dazu je die
 * Kante zur Kante hin (h, η) und die Steigungen der Geschwindigkeit (normal, längs). Seit Swash 0.3 aus den Kanten der
 * Zelle (kantenZelle) statt Mitte ± halbe Steigung.
 */
vec4 kante(vec4 ua, vec2 fa, vec2 va, vec4 uc, vec2 fc, vec2 vc) {
    bool sa = ua.w >= FELS, sc = uc.w >= FELS;
    if (sa && sc) return vec4(0.0);
    float g = W.F[F_G];
    float hL = fa.x, eL = fa.y, uL = vel(ua.y, ua.x) + 0.5 * va.x, vL = vel(ua.z, ua.x) + 0.5 * va.y;
    float hR = fc.x, eR = fc.y, uR = vel(uc.y, uc.x) - 0.5 * vc.x, vR = vel(uc.z, uc.x) - 0.5 * vc.y;
    if (sc) return vec4(0.0, 0.0, 0.5 * g * hL * hL, 0.0);
    if (sa) return vec4(0.0, 0.0, 0.0, 0.5 * g * hR * hR);
    return hll(hL, eL, uL, vL, hR, eR, uR, vR);
}

/** log(1 + y) genau auch für kleine y (Goldberg 1991). */
float log1pG(float y) { float v = 1.0 + y; return v == 1.0 ? y : log(v) * y / (v - 1.0); }

float ga(float x, float f, float a, float kdt) { return (x - f) - a * log1pG((x - f) / (a + f)) - kdt; }

/** Swash.greenAmpt in float. */
float greenAmpt(float f, float a, float kdt) {
    if (!(kdt > 0.0)) return f;
    if (!(a > 0.0)) return f + kdt;
    float lo = f, hi = f + kdt + sqrt(2.0 * a * kdt);
    for (int k = 0; k < 64 && ga(hi, f, a, kdt) < 0.0; k++) hi = f + 2.0 * (hi - f);
    float x = hi;
    for (int it = 0; it < 48; it++) {
        float gx = ga(x, f, a, kdt);
        if (gx > 0.0) hi = x; else lo = x;
        float d = x / (a + x);
        float xn = d > 0.0 ? x - gx / d : 0.5 * (lo + hi);
        if (!(xn > lo && xn < hi)) xn = 0.5 * (lo + hi);
        if (abs(xn - x) <= 1e-7 * max(x, 1e-30)) return xn;
        x = xn;
    }
    return x;
}

/**
 * Eine Stufe nach Heun für die Zelle (i, j), c = id(i, j) (Swash.anwenden, in Stufe 2 dazu .reibung, .versickern, .spuren):
 * Kanten links, rechts, unten, oben schon nach dem Geber gekürzt, eigene Kanten kx, ky (kantenZelle), Zustand uc. Schreibt U (Stufe 1
 * auch U0), Spur, Bilanz. Gibt (h neu, versickertes Wasser F) zurück.
 */
vec2 stufeZelle(int i, int j, int c, vec4 xl, vec4 xr, vec4 yd, vec4 yu, vec4 kx, vec4 ky, vec4 uc, float dt, float tEnde, int stufe) {
    float dx = W.F[F_DX], g = W.F[F_G], htr = W.F[F_HTR], hge = W.F[F_HGE], inv = 1.0 / dx;
    vec4 z = zelle[c];
    float masse = -(xr.x - xl.x + yu.x - yd.x) * inv;
    float mx = -(xr.z - xl.w + yu.y - yd.y) * inv;
    float my = -(yu.z - yd.w + xr.y - xl.y) * inv;
    // Quellterm aus den eigenen Kanten (seit Swash 0.3 mit der Tiefe nach quellTiefe: an der Front im Keil die Waage)
    float dbx = (kx.w - kx.y) - (kx.z - kx.x), dby = (ky.w - ky.y) - (ky.z - ky.x);
    mx -= g * quellTiefe(kx.x, kx.y, dbx) * dbx * inv;
    my -= g * quellTiefe(ky.x, ky.y, dby) * dby * inv;

    vec3 neu;
#ifdef VERSCHMOLZEN
    if (stufe == 1) neu = uc.xyz + dt * vec3(masse, mx, my);                // U0 ist der Puffer, aus dem Stufe 1 liest
#else
    if (stufe == 1) { u0[c] = uc; neu = uc.xyz + dt * vec3(masse, mx, my); }
#endif
    else { vec4 a0 = u0[c]; neu = 0.5 * (a0.xyz + uc.xyz + dt * vec3(masse, mx, my)); }
    float cut = 0.0;
    if (!(neu.x >= 0.0)) { cut = isnan(neu.x) ? 0.0 : -neu.x; neu.x = 0.0; }
    if (neu.x <= htr || isinf(neu.y) || isnan(neu.y) || isinf(neu.z) || isnan(neu.z)) { neu.y = 0.0; neu.z = 0.0; }
    float sp = length(neu.yz), lim = W.F[F_VMAX] * neu.x;
    if (sp > lim) neu.yz *= lim / sp;

    float zu = 0.0;
    if (i == 0 && W.I[I_RAND + SW] != WAND) zu += xl.x;
    if (i == nx() - 1 && W.I[I_RAND + SE] != WAND) zu -= xr.x;
    if (j == 0 && W.I[I_RAND + SS] != WAND) zu += yd.x;
    if (j == ny() - 1 && W.I[I_RAND + SN] != WAND) zu -= yu.x;
    if (zu != 0.0 || cut != 0.0) {
        vec4 bil = bilanz[c];
        bil.x += 0.5 * zu * dx * dt;
        bil.y += cut * dx * dx;
        bilanz[c] = bil;
    }

    float fSick = 0.0;
    if (stufe == 2) {
        float nn = z.y;
        if (neu.x > hge && nn > 0.0) {
            vec2 v = neu.yz / neu.x;
            float k = 1.0 + dt * g * nn * nn * length(v) / pow(neu.x, 4.0 / 3.0);
            neu.yz /= k;
        }
        vec4 sp4 = spur[c];
        vec4 alt = sp4;
        float kk = z.z;
        if (kk > 0.0) {
            float frei = z.x - z.w;
            if (frei > 0.0) {
                float fmax = W.F[F_DTH] * frei, f = sp4.x, hh = neu.x;
                if (hh > htr) {
                    if (f < fmax) {
                        float fn = greenAmpt(f, (hh + W.F[F_PSI]) * W.F[F_DTH], kk * dt);
                        float d = min(fn - f, min(hh, fmax - f));
                        if (d > 0.0) {
                            float nh = hh - d;
                            if (nh <= htr) neu.yz = vec2(0.0); else neu.yz *= nh / hh;
                            neu.x = nh;
                            sp4.x = f + d;
                            sp4.w += d;
                        }
                    }
                } else if (f > 0.0) sp4.x = max(0.0, f - kk * dt);
            }
        }
        if (neu.x > W.F[F_HSP]) {
            float e = neu.x + z.x;
            if (!(sp4.y >= e)) sp4.y = e;
            sp4.z = tEnde;
        }
        if (sp4 != alt) spur[c] = sp4;
        fSick = sp4.x;
#ifdef VERSCHMOLZEN
        if (bildSchreiben != 0) bildZelle(i, j, neu.x, z.x, sp4.z, sp4.x);    // seit S6c-2b: im letzten Schritt des Bilds
#endif
    }
#ifdef VERSCHMOLZEN
    uAus[c] = vec4(neu, 0.0);
#else
    u[c] = vec4(neu, 0.0);
#endif
    return vec2(neu.x, fSick);
}
