// Referenz Stufe 2 (P11a): Phasen, Facette, Medien und der Pfadverfolger – dieselbe Rechnung wie die Klassen gleichen
// Namens in com.dan.render.reference (Stufe 1). Erwartet kopf.glsl und eine Szene (schneide, szeneTruebung, HA).
//
// Aufbau für die Karte (seit Lauf A2 von P11a): Ein Pfad ist ein Zustandsautomat. Jeder Schritt der Schleife in
// verfolge() tut genau eines – einen Auftrag zur Lichtquelle abarbeiten, ein Stück Flug durch die Luft (bis zum nächsten
// vorläufigen Stoß mit Ereignis), einen Flug durch das Wasser oder ein Ereignis (Streuung, Fläche, Boden, Austritt).
// Die Ereignisse zur Lichtquelle (Pfadverfolger.stossNee, neeLuftBoden, neeFlaeche, neeWasser) rechnen nicht selbst,
// sie legen Aufträge ab; der Schnitt mit der Szene und die Durchlässigkeit der Luft stehen so je nur an einer Stelle im
// Code. Mit dem Aufbau wie in Stufe 1 übersetzte der Compiler für RDNA 2 mit 256 Registern, 686 davon im Scratch und
// 245 KB Code; die Karte schaffte 477 Pfade/s. Keine Schleife über die Bahnen mit Index: Vektoren mit laufendem Index
// legt der Compiler in den Scratch.

// ================================================================== Phasen
float rayleighP(float mu) { return 3.0 / (16.0 * PI) * (1.0 + mu * mu); }

/** μ nach Rayleigh: Lösung von (3μ + μ³ + 4) / 8 = u. */
float rayleighZiehe(float u) {
    float z = 4.0 * u - 2.0, w = pow(z + sqrt(z * z + 1.0), 1.0 / 3.0);
    return w - 1.0 / w;
}

float cornetteShanks(float mu, float g) {
    float g2 = g * g;
    return 3.0 / (8.0 * PI) * (1.0 - g2) * (1.0 + mu * mu) / ((2.0 + g2) * pow(max(1e-6, 1.0 + g2 - 2.0 * g * mu), 1.5));
}

float hg(float mu, float g) {
    float g2 = g * g;
    return (1.0 / (4.0 * PI)) * (1.0 - g2) / pow(max(1e-12, 1.0 + g2 - 2.0 * g * mu), 1.5);
}

float hgZiehe(float u, float g) {
    if (abs(g) < 1e-6) return 1.0 - 2.0 * u;
    float s = (1.0 - g * g) / (1.0 - g + 2.0 * g * u);
    return clamp((1.0 + g * g - s * s) / (2.0 * g), -1.0, 1.0);
}

/**
 * 1 − cos θ nach Fournier-Forand (Phasen.ffZiehe): die Tafel invertiert, in jeder Stufe gleichverteilt in cos θ. Unter
 * u = ½ in F, darüber in 1 − F gesucht (float trägt F nahe 1 nicht).
 */
float ffZiehe(float u) {
    bool unten = u < 0.5;
    int off = unten ? FF_F : FF_G;
    float v = unten ? u : 1.0 - u, sg = unten ? 1.0 : -1.0;
    int lo = 0, hi = FF_N;
    while (hi - lo > 1) { int m = (lo + hi) >> 1; if (sg * (FF[off + m] - v) <= 0.0) lo = m; else hi = m; }
    float a = FF[off + lo], b = FF[off + lo + 1];
    float f = clamp(sg * (v - a) / max(1e-30, sg * (b - a)), 0.0, 1.0);
    return FF[FF_OM + lo] + f * (FF[FF_OM + lo + 1] - FF[FF_OM + lo]);
}

/** Dichte (1/sr) zu 1 − cos θ = x (Phasen.ffDichte): die Stufe, in der x liegt. */
float ffDichte(float x) {
    int lo = 0, hi = FF_N;
    while (hi - lo > 1) { int m = (lo + hi) >> 1; if (FF[FF_OM + m] <= x) lo = m; else hi = m; }
    return FF[FF_D + lo];
}

/** Richtung nach Fournier-Forand um d. */
vec3 ffRichtung(vec3 d) {
    float x = ffZiehe(zufall());
    return umAchse(d, 1.0 - x, sqrt(max(0.0, x * (2.0 - x))));
}

// ================================================================== Facette (Walter u. a. 2007, Cox und Munk)
// tan² über das Kreuzprodukt statt (1 − c²) / c²: bei glattem Wasser (α² 10⁻⁶) trüge float 1 − c² nicht.

float fD(vec3 m, vec3 n, float a2) {
    float c = dot(m, n);
    if (c <= 0.0) return 0.0;
    vec3 k = cross(m, n);
    float c2 = c * c, t2 = dot(k, k) / c2;
    return exp(-t2 / a2) / (PI * a2 * c2 * c2);
}

float fG1(vec3 v, vec3 m, vec3 n, float a2) {
    float vn = dot(v, n);
    if (dot(v, m) * vn <= 0.0) return 0.0;
    float c = abs(vn), t = length(cross(v, n)) / c;
    if (t == 0.0) return 1.0;
    float a = 1.0 / (sqrt(a2) * t);
    return a < 1.6 ? (3.535 * a + 2.181 * a * a) / (1.0 + 2.276 * a + 2.577 * a * a) : 1.0;
}

/** Mikronormale nach D(m) |m·n|. */
vec3 fZieheM(vec3 n, float a2) {
    float t2 = -a2 * log(1.0 - zufall()), c = 1.0 / sqrt(1.0 + t2);
    return umAchse(n, c, sqrt(t2) * c);
}

float fresnel(float ci, float eta) {
    float s2 = eta * eta * (1.0 - ci * ci);
    if (s2 >= 1.0) return 1.0;
    float ct = sqrt(1.0 - s2);
    float rs = (eta * ci - ct) / (eta * ci + ct), rp = (ci - eta * ct) / (ci + eta * ct);
    return 0.5 * (rs * rs + rp * rp);
}

/**
 * Facette.wert und Facette.dichte in einem Aufruf (dieselbe Halbnormale): x = BSDF · |i·n| für die Strahldichte von i
 * nach o (nO auf der Seite von o, nA auf der anderen), y = die Dichte, mit der fZiehe(o) die Richtung i gibt (no = nO,
 * nd = nA).
 */
vec2 fWertDichte(vec3 i, vec3 o, vec3 n, float nO, float nA, float a2) {
    float in_ = dot(i, n), on = dot(o, n);
    vec3 nn = on > 0.0 ? n : -n;
    bool spiegel = in_ * on > 0.0;
    vec3 h = spiegel ? normalize(i + o) : normalize(-(nA * i + nO * o));
    if (dot(h, nn) < 0.0) h = -h;
    float ih = dot(i, h), oh = dot(o, h);
    if (!spiegel && ih * oh >= 0.0) return vec2(0.0);
    float f = fresnel(abs(oh), nO / nA), dh = fD(h, nn, a2), g = fG1(i, h, nn, a2) * fG1(o, h, nn, a2), hn = abs(dot(h, nn));
    if (spiegel) return vec2(f * g * dh / (4.0 * abs(on)), oh == 0.0 ? 0.0 : f * dh * hn / (4.0 * abs(oh)));
    if (f >= 1.0) return vec2(0.0);
    float den2 = (nA * ih + nO * oh) * (nA * ih + nO * oh);
    return vec2(abs(ih) * abs(oh) / abs(on) * nO * nO * (1.0 - f) * g * dh / den2, (1.0 - f) * dh * hn * nA * nA * abs(ih) / den2);
}

/** Neue Richtung i zu o (Facette.ziehe): false, wenn keine; sonst i, Gewicht, gebrochen. */
bool fZiehe(vec3 o, vec3 n, float no, float nd, float a2, out vec3 i, out float gw, out bool durch) {
    vec3 nn = dot(o, n) >= 0.0 ? n : -n;
    vec3 m = fZieheM(nn, a2);
    float om = dot(o, m);
    i = vec3(0.0);
    gw = 0.0;
    durch = false;
    if (om <= 0.0) return false;
    float f = fresnel(om, no / nd);
    float on = abs(dot(o, nn)), mn = dot(m, nn);
    float k = om / (on * mn);
    if (zufall() < f) {
        i = spiegle(-o, m);
        if (dot(i, nn) <= 0.0) return false;
    } else {
        if (!breche(-o, m, no / nd, i) || dot(i, nn) >= 0.0) return false;
        durch = true;
        k *= (no / nd) * (no / nd);
    }
    gw = fG1(i, m, nn, a2) * fG1(o, m, nn, a2) * k;
    return true;
}

// ================================================================== Medien (Luft in Kugelschalen, Wasser)
// Höhe über dem Boden der Kugel ohne Auslöschung: mit q = p + (0, R + h_Auge, 0) ist |q|² − R² =
// x² + z² + (h_Auge + y)(2R + h_Auge + y), und längs p + t d kommt t (2 b + t) dazu (b = q·d). Daraus h = c / (|q| + R).

float R_, RT_, mieExt, mieSca, mieG;
bool mitR, mitM, mitO, ohneAbs;
float dichteL = 1.0;
vec4 sR, sM, ab, anteilR;
float tEreignis;
float truebungAkt = -1.0;
vec4 aOrt;
float bOrt;

float cVon(vec3 p) { float y = HA + p.y; return p.x * p.x + p.z * p.z + y * (2.0 * R_ + y); }
float bVon(vec3 p, vec3 d) { return dot(p, d) + (R_ + HA) * d.y; }
float hAusC(float c) { return c / (sqrt(max(0.0, R_ * R_ + c)) + R_); }
float hBei(float c0, float b, float t) { return hAusC(c0 + t * (2.0 * b + t)); }

/** Weg bis zur Oberkante der Luft (Medien.bisOben); die Wurzel ohne Auslöschung. */
float bisOben(vec3 p, vec3 d) {
    float c0 = cVon(p), b = bVon(p, d), c = c0 - (RT_ - R_) * (RT_ + R_), disk = b * b - c;
    if (disk < 0.0) return 0.0;
    float sq = sqrt(disk), t = b > 0.0 ? -c / (b + sq) : -b + sq;
    return max(0.0, t);
}

float tiefste(float c0, float b, float a, float e) { return hBei(c0, b, clamp(-b, a, e)); }

float dRay(float h) { return mitR ? exp(-max(0.0, h) / W[W_RH]) : 0.0; }
float dMie(float h) { return mitM ? exp(-max(0.0, h) / W[W_MH]) : 0.0; }
float dOzon(float h) { return max(0.0, 1.0 - abs(h - W[W_OC]) / W[W_OHW]); }

/** Streuung und Absorption je Bahn in der Höhe h (Medien.luft) nach sR, sM, ab. */
void luft(float h) {
    float r = dRay(h), m = dMie(h), o = mitO && !ohneAbs ? dOzon(h) : 0.0;
    float ms = ohneAbs ? mieExt : mieSca;
    sR = ray * r;
    sM = vec4(ms * m) * aktiv;
    ab = ozo * o + vec4(ohneAbs ? 0.0 : (mieExt - mieSca) * m) * aktiv;
}

/** Majorante der Luft zwischen hMin und hMax; der Faktor 1,0001 statt 1,000001 deckt die Stellen von float. */
float majoranteLuft(float hMin, float hMax) {
    float r = dRay(hMin), m = dMie(hMin);
    float hz = max(hMin, min(hMax, W[W_OC]));
    float o = mitO && !ohneAbs ? dOzon(hz) : 0.0;
    return (max4((ray * r + ozo * o) * aktiv) + mieExt * m) * 1.0001 + 1e-30;
}

vec4 w, L;
#ifdef TEILE
vec4 LT0, LT1, LT2, LT3;
#endif
bool getaucht, grund, nanPfad;

/** Kutz u. a. (2017): 0 Null, 1 Streuung, 2 Absorption; führt w nach (Medien.waehle). */
int waehle(float mj) {
    float sc = max4(abs(w));
    if (!(sc > 1e-30)) return 2;
    vec4 aw = abs(w) / sc, s = sR + sM;
    float pa = dot(aw, ab), ps = dot(aw, s), pn = dot(aw, abs(vec4(mj) - s - ab));
    float c = pa + ps + pn;
    if (c <= 0.0) return 0;
    float u = zufall() * c;
    if (u < pa) return 2;
    if (u < pa + ps) {
        w *= s / (mj * (ps / c));
        return 1;
    }
    w *= (vec4(mj) - s - ab) / (mj * (pn / c));
    return 0;
}

// ---- Durchlässigkeit der Luft durch Quadratur (Medien.integrale): Gauß-Legendre mit 6 Punkten in Abschnitten,
// geteilt an den Knicken der Dichten. Die Knicke ohne Feld: die nächste Grenze über t aus zwei Vektoren der Wurzeln.
const float GLX[6] = float[6](-0.9324695142031521, -0.6612093864662645, -0.2386191860831969, 0.2386191860831969, 0.6612093864662645, 0.9324695142031521);
const float GLW[6] = float[6](0.1713244923791704, 0.3607615730481386, 0.4679139345726910, 0.4679139345726910, 0.3607615730481386, 0.1713244923791704);
const float INF = 3.0e38;

/** Kleinste Wurzel über t (INF, wenn keine). */
float naechste(vec4 r1, vec4 r2, float t) {
    vec4 a = mix(vec4(INF), r1, greaterThan(r1, vec4(t))), b = mix(vec4(INF), r2, greaterThan(r2, vec4(t)));
    vec4 m = min(a, b);
    return min(min(m.x, m.y), min(m.z, m.w));
}

vec3 integrale(vec3 p, vec3 d, float tMax) {
    float tEnde = min(tMax, bisOben(p, d));
    vec3 aus = vec3(0.0);
    if (!(tEnde > 0.0)) return aus;
    float c0 = cVon(p), b = bVon(p, d);
    vec4 hk = vec4(0.0, W[W_OC] - W[W_OHW], W[W_OC], W[W_OC] + W[W_OHW]);
    vec4 c = vec4(c0) - hk * (2.0 * R_ + hk), D = vec4(b * b) - c, s = sqrt(max(D, vec4(0.0)));
    vec4 r1, r2;
    if (b >= 0.0) { r1 = -vec4(b) - s; r2 = c / r1; } else { r2 = -vec4(b) + s; r1 = c / r2; }
    bvec4 gilt = greaterThan(D, vec4(0.0));
    r1 = mix(vec4(-1.0), r1, gilt);
    r2 = mix(vec4(-1.0), r2, gilt);
    float t = 0.0;
    while (t < tEnde) {
        if (wache()) return aus;
        float ende = min(tEnde, naechste(r1, r2, t));
        float h0 = hBei(c0, b, t);
        float len = min(20000.0, max(200.0, 2.0 * (max(0.0, h0) + 500.0)));
        float e = min(ende, t + len), m = 0.5 * (t + e), hw = 0.5 * (e - t);
        if (!(e > t)) return aus;
        [[unroll]] for (int g = 0; g < 6; g++) {
            float x = m + hw * GLX[g], h = hBei(c0, b, x), wg = GLW[g] * hw;
            aus += wg * vec3(dRay(h), dMie(h), mitO ? dOzon(h) : 0.0);
        }
        t = e;
    }
    return aus;
}

// ---- Wasser

vec4 aWasserAkt() { return ohneAbs ? vec4(0.0) : (truebungAkt < 0.0 ? aW : aOrt); }
float bWasserAkt() { return truebungAkt < 0.0 ? W[W_BWASSER] : bOrt; }

void setzeTruebung(float c) {
    truebungAkt = c;
    if (c < 0.0) return;
    aOrt = aWasserBei(c);
    bOrt = W[W_BBSTERN] * c / W[W_BBZUB];
}

int fliegeWasser(float tMax) {
    vec4 a = aWasserAkt();
    float b = bWasserAkt(), mj = (max4(a * aktiv) + b) * 1.000001, t = 0.0;
    sR = vec4(0.0);
    sM = vec4(b) * aktiv;
    ab = a;
    if (!(mj > 0.0)) return ABSORBIERT;
    while (true) {
        if (wache()) return ABSORBIERT;
        t -= log(1.0 - zufall()) / mj;
        if (t >= tMax) { tEreignis = tMax; return ENDE; }
        int e = waehle(mj);
        if (e == 1) { tEreignis = t; return STREUUNG; }
        if (e == 2) return ABSORBIERT;
    }
    return ENDE;
}

// ================================================================== Pfadverfolger
bool ofen, einfach, jederStoss, ohneSaeule, einfachSchaetzer;
float keule, eta, alpha2Basis, fa2, wasserWeite, nachWahl = 1.0;
int maxTiefe;
const vec3 OBEN = vec3(0.0, 1.0, 0.0);

void plusV(vec4 c, bool quelle) {
    L += c;
#ifdef TEILE
    if (!getaucht) LT0 += c;
    else if (grund) LT3 += c;
    else if (quelle) LT1 += c;
    else LT2 += c;
#endif
}

/** Eine Lichtquelle nach ihrer Stärke (Pfadverfolger.licht); −1 ohne. Setzt nachWahl. */
int licht() {
    if (nLicht == 0) return -1;
    if (nLicht == 1) { nachWahl = 1.0; return 0; }
    float g0 = lGewicht(0), g1 = lGewicht(1), sum = g0 + g1, u = zufall() * sum;
    if (u < g0) { nachWahl = sum / g0; return 0; }
    nachWahl = sum / g1;
    return 1;
}

/** Index der stärksten Lichtquelle für die Keule; −1 ohne. */
int keulenLicht() {
    if (nLicht == 0) return -1;
    if (nLicht == 1) return lGewicht(0) > 0.0 ? 0 : -1;
    float g0 = lGewicht(0), g1 = lGewicht(1);
    return g1 > g0 ? 1 : (g0 > 0.0 ? 0 : -1);
}

vec4 strahl(int li) { return lSonne(li) ? son : mon; }

// ---- Aufträge zur Lichtquelle: höchstens zwei offen.
// Art 0: von o in Richtung r zur Quelle; nicht verdeckt: plus f · Durchlässigkeit der Luft. Art 1 (unter Wasser): von o
// in Richtung r bis zur Wasserfläche, dort die BTDF zur Quelle in Richtung ls, geteilt durch die Mischdichte pm; daraus
// ein Auftrag der Art 0 (Pfadverfolger.wasserBeitrag).
struct Auftrag {
    vec3 o, r, ls;
    vec4 f;
    float pm;
    int art;
    bool quelle;
};
Auftrag A0, A1;
int nAuftrag;

void auftrag(int art, vec3 o, vec3 r, vec4 f, bool quelle, vec3 ls, float pm) {
    Auftrag a;
    a.o = o; a.r = r; a.ls = ls; a.f = f; a.pm = pm; a.art = art; a.quelle = quelle;
    if (nAuftrag == 0) A0 = a; else A1 = a;
    nAuftrag++;
}

/**
 * Den ältesten Auftrag abarbeiten: der einzige Schnitt für Aufträge und die einzige Durchlässigkeit der Luft. Ein Auftrag
 * der Art 1 wird an Ort und Stelle zu einem der Art 0 (kein zweites Anlegen).
 */
void erledige() {
    Treffer t2;
    bool h = schneide(A0.o, A0.r, A0.art == 0 ? INF : wasserWeite, t2);
    bool fertig = true;
    if (A0.art == 0) {
        if (!h) {
            vec3 iq = integrale(A0.o, A0.r, INF);
            vec4 T = exp(-(ray * iq.x + vec4(mieExt * iq.y) * aktiv + (ohneAbs ? vec4(0.0) : ozo * iq.z)));
            plusV(A0.f * T, A0.quelle);
        }
    } else if (h && t2.art == WASSER) {
        vec3 y = A0.o + t2.t * A0.r, up = t2.n.y >= 0.0 ? t2.n : -t2.n;
        float a2 = t2.sigma2 > 0.0 ? max(1e-8, alpha2Basis + t2.sigma2) : alpha2Basis;
        float ft = fWertDichte(A0.ls, -A0.r, up, eta, 1.0, a2).x;
        if (ft > 0.0 && A0.pm > 0.0) {
            A0.f *= exp(-(aWasserAkt() + vec4(bWasserAkt()) * aktiv) * t2.t) * (ft / A0.pm);
            A0.o = y + eps(y) * up;
            A0.r = A0.ls;
            A0.art = 0;
            fertig = false;
        }
    }
    if (fertig) {
        A0 = A1;
        nAuftrag--;
    }
}

/** Auftrag der Art 0 zu einer gezogenen Lichtquelle mit dem Faktor fv je Bahn (Pfadverfolger.zurQuelleAusLuft). */
void zurQuelle(vec3 x, vec3 l, int li, vec4 fv, bool quelle) {
    float s = lStrahldichte(li, l) * lRaumwinkel(li) * nachWahl;
    auftrag(0, x, l, w * fv * strahl(li) * s, quelle, vec3(0.0), 0.0);
}

float dichteMikro(vec3 ls, vec3 t) {
    vec3 h = normalize(-(ls + eta * t));
    if (h.y < 0.0) h = -h;
    float den = dot(ls, h) + eta * dot(t, h);
    if (den == 0.0) return 0.0;
    return fD(h, OBEN, fa2) * abs(h.y) * eta * eta * abs(dot(t, h)) / (den * den);
}

/** Unter Wasser: das Licht der Quelle durch die raue Oberfläche, zwei Richtungen mit MIS (Pfadverfolger.neeWasser). */
void neeWasser(vec3 x, vec3 d, bool streuung, vec3 nn, vec4 albedo) {
    int li = licht();
    if (li < 0) return;
    vec3 ls = lZiehe(li);
    if (ls.y <= 0.0) return;
    float s = lStrahldichte(li, ls) * lRaumwinkel(li) * nachWahl;
    vec3 x0 = streuung ? x : x + eps(x) * nn;
    vec4 st = w * strahl(li) * s;
    [[dont_unroll]] for (int k = 0; k < 2; k++) {
        vec3 u;
        if (k == 0) {
            vec3 m = fZieheM(OBEN, fa2), t;
            if (!breche(-ls, m, 1.0 / eta, t) || !(t.y < 0.0)) continue;
            u = -t;
        } else {
            u = streuung ? ffRichtung(d) : umAchse(nn, sqrt(1.0 - zufall()));
            if (!(u.y > 0.0)) continue;
        }
        float fx;
        if (streuung) fx = ffDichte(einsMinusCos(u, d));
        else {
            float c = dot(u, nn);
            if (c <= 0.0) continue;
            fx = c / PI;
        }
        vec4 fk = streuung ? vec4(fx) * aktiv : fx * albedo;
        auftrag(1, x0, u, st * fk, true, ls, dichteMikro(ls, -u) + fx);
    }
}

/** Neue Richtung nach einer Streuung in der Luft (Pfadverfolger.streueLuft). */
vec3 streueLuft(vec3 d) {
    float piR = dot(anteilR, aktiv) / float(NB);
    int ki = keule > 0.0 && !ofen ? keulenLicht() : -1;
    vec3 ks = ki >= 0 ? lRichtung(ki) : vec3(0.0);
    vec3 neu;
    float mu;
    if (ki >= 0 && zufall() < keule) {
        neu = umAchse(ks, hgZiehe(zufall(), KEULE_G));
        mu = clamp(dot(neu, d), -1.0, 1.0);
    } else {
        mu = zufall() < piR ? rayleighZiehe(zufall()) : hgZiehe(zufall(), mieG);
        neu = umAchse(d, mu);
    }
    float pR = rayleighP(mu), pM = cornetteShanks(mu, mieG), q = piR * pR + (1.0 - piR) * hg(mu, mieG);
    if (ki >= 0) q = (1.0 - keule) * q + keule * hg(dot(neu, ks), KEULE_G);
    w *= (anteilR * pR + (aktiv - anteilR) * pM) / q;
    return neu;
}

// ---- Zustand des Pfads
vec3 P, D;
#ifdef OHNE_WASSER
const bool imWasser = false;                                              // Befund P11a: Kernel nur für Luft und Boden
bool ereignis, imFlug, hit;
#else
bool imWasser, ereignis, imFlug, hit;
#endif
int tiefe;
Treffer TR;
// Flug durch die Luft: Weg t, Ende des Abschnitts e, Ende tEnde, Majorante, Konstanten der Höhe
float fT, fE, fEnde, fMj, fC0, fB;
bool fStoss;
#define STOSS 3
#define LAEUFT 4

/** Den nächsten Abschnitt des Flugs beginnen (Medien.fliegeLuft, äußere Schleife); false am Ende. */
bool flugAbschnitt() {
    if (!(fT < fEnde)) return false;
    float h0 = hBei(fC0, fB, fT);
    float len = min(20000.0, max(200.0, 2.0 * (h0 + 500.0)));
    fE = min(fEnde, fT + len);
    float hMin = tiefste(fC0, fB, fT, fE), hMax = max(h0, hBei(fC0, fB, fE));
    fMj = majoranteLuft(hMin, hMax) * (fStoss && !einfachSchaetzer ? dichteL : 1.0);
    return fMj > 0.0 && fE > fT;
}

/**
 * Ein Stück Flug durch die Luft bis zum nächsten vorläufigen Stoß, der einen Auftrag gibt (STOSS), oder bis zum Ende
 * (ENDE, STREUUNG, ABSORBIERT). Mit einfachSchaetzer (Pfadverfolger.einfachStoesse): an jedem Stoß ein Auftrag je
 * Lichtquelle, dann w mal (1 − σ_t / σ̄), kein Ereignis.
 */
int flugSchritt() {
    while (true) {
        if (wache()) return ABSORBIERT;
        fT -= log(1.0 - zufall()) / fMj;
        if (fT >= fE) {
            fT = fE;
            if (!flugAbschnitt()) { tEreignis = fEnde; return ENDE; }
            continue;
        }
        luft(hBei(fC0, fB, fT));
        if (einfachSchaetzer) {
            vec3 x = P + fT * D;
            for (int li = 0; li < nLicht; li++) {
                vec3 l = lZiehe(li);
                float mu = dot(l, D);
                vec4 f = (sR * rayleighP(mu) + sM * cornetteShanks(mu, mieG)) / fMj;
                float s = lStrahldichte(li, l) * lRaumwinkel(li);
                auftrag(0, x, l, w * f * strahl(li) * s, false, vec3(0.0), 0.0);
            }
            w *= vec4(1.0) - (sR + sM + ab) / fMj;
            if (nLicht > 0) return STOSS;
            continue;
        }
        bool gab = false;
        if (fStoss) {                                                     // Pfadverfolger.stossNee, vor der Wahl
            int li = licht();
            if (li >= 0) {
                vec3 l = lZiehe(li);
                float mu = dot(l, D);
                zurQuelle(P + fT * D, l, li, (sR * rayleighP(mu) + sM * cornetteShanks(mu, mieG)) / fMj, false);
                gab = true;
            }
        }
        int ev = waehle(fMj);
        if (ev == 1) {
            tEreignis = fT;
            vec4 s = sR + sM;
            anteilR = mix(vec4(0.0), sR / max(s, vec4(1e-37)), greaterThan(s, vec4(0.0)));
            return STREUUNG;
        }
        if (ev == 2) return ABSORBIERT;
        if (gab) return STOSS;
    }
    return ENDE;
}

/** Ein Ereignis des Pfads (die Fortsetzung der Schleife von Pfadverfolger.verfolge nach dem Flug). false: Ende. */
bool ereignisBehandeln(int e) {
    if (e == ABSORBIERT) return false;
    bool streu = e == STREUUNG;
    if (!streu && !hit) {                                                 // verlässt die Luft
        plusV(w * (ofen ? aktiv : nac), false);
        if (tiefe == 0 && !ofen)
            for (int li = 0; li < nLicht; li++)
                if (lTrifft(li, D)) plusV(w * strahl(li) * lStrahldichte(li, D), false);
        return false;
    }
    bool boden = !streu && TR.art == BODEN, flaeche = !streu && !boden;
    vec3 x = P + (streu ? tEreignis : TR.t) * D;
    vec3 nn = streu ? vec3(0.0) : (boden && dot(TR.n, D) > 0.0 ? -TR.n : TR.n);
    vec4 alb = boden ? (ofen ? aktiv : albedoBahnen(TR.albedo)) : vec4(0.0);
    if (flaeche) fa2 = TR.sigma2 > 0.0 ? max(1e-8, alpha2Basis + TR.sigma2) : alpha2Basis;
    if (boden && imWasser) grund = true;
    // Licht der Quelle, je an einer Stelle: unter Wasser (Streuung, Grund; Pfadverfolger.neeWasser) und in der Luft
    // (Streuung ohne jederStoss, Boden, Wasserfläche von oben und unten; neeLuftStreuung, neeLuftBoden, neeFlaeche).
    if (!ofen) {
        if (imWasser && !flaeche) neeWasser(x, D, streu, nn, alb);
        else if ((streu && !jederStoss) || boden || (flaeche && !(imWasser && ereignis))) {
            int li = licht();
            if (li >= 0) {
                vec3 l = lZiehe(li), up = nn.y >= 0.0 ? nn : -nn, start = x;
                vec4 fv = vec4(0.0);
                if (streu) {
                    float mu = dot(l, D);
                    fv = anteilR * rayleighP(mu) + (aktiv - anteilR) * cornetteShanks(mu, mieG);
                } else if (boden) {
                    fv = alb / PI * max(0.0, dot(l, nn));
                    start = x + eps(x) * nn;
                } else if (dot(l, up) > 0.0) {
                    fv = vec4(fWertDichte(l, -D, up, imWasser ? eta : 1.0, imWasser ? 1.0 : eta, fa2).x) * aktiv;
                    start = x + eps(x) * up;
                }
                if (max4(fv) > 0.0) zurQuelle(start, l, li, fv, flaeche && imWasser);
            }
        }
    }
    if (streu) {
        if (imWasser) ereignis = true;
        if (einfach) return false;
        D = imWasser ? ffRichtung(D) : streueLuft(D);
        P = x;
        return true;
    }
    if (boden) {
        if (imWasser) ereignis = true;
        int ki = keule > 0.0 && !ofen && !imWasser ? keulenLicht() : -1;
        bool ausKeule = ki >= 0 && zufall() < keule;
        vec3 ks = ki >= 0 ? lRichtung(ki) : vec3(0.0);
        D = ausKeule ? umAchse(ks, hgZiehe(zufall(), KEULE_G)) : umAchse(nn, sqrt(1.0 - zufall()));
        if (ki < 0) w *= alb;
        else {
            float c = dot(D, nn);
            if (c <= 0.0) return false;
            float pm = (1.0 - keule) * c / PI + keule * hg(dot(D, ks), KEULE_G);
            w *= alb * (c / PI / pm);
        }
    } else {
        vec3 o = -D;
        float no = imWasser ? eta : 1.0, nd = imWasser ? 1.0 : eta;
        int ki = keule > 0.0 && !ofen ? keulenLicht() : -1;
        vec3 nu = dot(o, nn) >= 0.0 ? nn : -nn, luftN = nn.y >= 0.0 ? nn : -nn;
        vec3 i;
        float gw = 0.0;
        bool durch;
        bool ausKeule = ki >= 0 && zufall() < keule;
        vec3 ks = ki >= 0 ? lRichtung(ki) : vec3(0.0);
        bool mische;                                                       // Gewicht aus der Mischung mit der Keule
        if (ausKeule) {
            i = umAchse(ks, hgZiehe(zufall(), KEULE_G));
            if (dot(i, luftN) <= 0.0) return false;
            durch = imWasser;
            mische = true;
        } else {
            if (!fZiehe(o, nn, no, nd, fa2, i, gw, durch)) return false;
            mische = ki >= 0 && dot(i, luftN) > 0.0;
            if (ki >= 0 && !mische) gw /= 1.0 - keule;
        }
        if (mische) {
            vec2 wd = fWertDichte(i, o, nu, no, nd, fa2);
            float pm = (1.0 - keule) * wd.y + keule * hg(dot(i, ks), KEULE_G);
            gw = pm > 0.0 ? wd.x / pm : 0.0;
        }
        if (!(gw > 1e-30)) return false;
        w *= gw;
        if (durch && !imWasser && ohneSaeule) return false;
#ifdef OHNE_WASSER
        if (durch) return false;
#else
        if (durch) {
            imWasser = !imWasser;
            if (imWasser) {
                getaucht = true;
                float c = szeneTruebung(x);                                  // S8: Trübung am Eintrittspunkt
                if (c != truebungAkt) setzeTruebung(c);
            }
            ereignis = false;
        }
#endif
        D = i;
    }
    P = x + eps(x) * D;
    return true;
}

/** Strahldichte je Bahn längs des Strahls vom Auge (Pfadverfolger.verfolge); Ergebnis in L. */
void verfolge(vec3 p0, vec3 d0) {
    w = aktiv;
    L = vec4(0.0);
#ifdef TEILE
    LT0 = LT1 = LT2 = LT3 = vec4(0.0);
#endif
    getaucht = false;
    grund = false;
    fa2 = alpha2Basis;
    P = p0;
    D = d0;
#ifndef OHNE_WASSER
    imWasser = false;
#endif
    ereignis = false;
    imFlug = false;
    nAuftrag = 0;
    tiefe = 0;
    if (truebungAkt >= 0.0) setzeTruebung(-1.0);
    bool weiter = true;
    while (weiter || nAuftrag > 0) {
        if (wache()) break;
        if (nAuftrag > 0) {                                               // zuerst die Aufträge zur Lichtquelle
            erledige();
            continue;
        }
        int ev = LAEUFT;
        if (imFlug) {
            int r = flugSchritt();
            if (r != STOSS) { imFlug = false; ev = r; }
        } else {
            if (tiefe >= maxTiefe) break;
            if (any(isnan(w)) || any(isinf(w)) || any(isnan(P)) || any(isnan(D))) { nanPfad = true; break; }
            hit = schneide(P, D, imWasser ? wasserWeite : INF, TR);        // der einzige Schnitt des Pfads
            if (imWasser) {
                if (!hit && wasserWeite >= INF) break;
                ev = fliegeWasser(hit ? TR.t : wasserWeite);
                if (!hit && ev == ENDE) break;
            } else {
                fEnde = min(hit ? TR.t : INF, bisOben(P, D));
                fC0 = cVon(P);
                fB = bVon(P, D);
                fT = 0.0;
                fStoss = (jederStoss && !ofen) || einfachSchaetzer;
                imFlug = flugAbschnitt();
                if (!imFlug) { tEreignis = fEnde; ev = ENDE; }
            }
        }
        if (ev == LAEUFT) continue;
        weiter = ereignisBehandeln(ev);
        if (!weiter) continue;
        if (tiefe >= 8) {                                                 // Russisches Roulette
            float q = max4(abs(w));
            if (q < 0.25) {
                if (zufall() >= q / 0.25) weiter = false;
                else w /= q / 0.25;
            }
        }
        tiefe++;
    }
}

// ================================================================== Ablauf je Faden
// Modi (ReferenzKarte.MODUS_*): 0 Bild, 1 eine Richtung, 2 freier Flug durch Wasser, 3 Einfachstreuung; 4 Fournier-Forand
// und 5 Tafeln im eigenen Kernel tafeln.comp. Ausgabe je Faden 24 Werte ab A[24 i], aufaddiert über die Runden (Modus 5: gesetzt). Bild: 0–2 und 3–5 die
// Hälften, mit TEILE 6–17 die vier Teile; sonst 0–2 Summe, 3–5 Quadrate, 6, 7 Mittel der Bahnen und Quadrat, 8 Zahl.
// 22 Pfade mit NaN, 23 von der Wache abgebrochene.

void lies() {
    HA = W[W_HAUGE];
    R_ = W[W_R];
    RT_ = W[W_RT];
    mieExt = W[W_MIE_EXT];
    mieSca = W[W_MIE_SCA];
    mieG = W[W_MIEG];
    spektral = W[W_SPEKTRAL] > 0.5;
    NB = spektral ? 4 : 3;
    aktiv = spektral ? vec4(1.0) : vec4(1.0, 1.0, 1.0, 0.0);
    mitR = W[W_MIT_R] > 0.5;
    mitM = W[W_MIT_M] > 0.5;
    mitO = W[W_MIT_O] > 0.5;
    ohneAbs = W[W_OHNE_ABS] > 0.5;
    ofen = W[W_OFEN] > 0.5;
    einfach = W[W_EINFACH] > 0.5;
    jederStoss = W[W_JEDER] > 0.5;
    ohneSaeule = W[W_OHNE_SAEULE] > 0.5;
    keule = W[W_KEULE];
    dichteL = max(1.0, W[W_DICHTE]);
    eta = W[W_ETA];
    alpha2Basis = W[W_ALPHA2];
    wasserWeite = W[W_WWEITE];
    maxTiefe = int(W[W_MAXTIEFE]);
    nLicht = int(W[W_NL]);
    einfachSchaetzer = pc.modus == 3;
    if (!spektral) wellenKanaele();
}

#if !defined(NUR_TAFELN) && !defined(WELLENFRONT)
void main() {
    uint gi = gl_GlobalInvocationID.x;
    if (gi >= uint(pc.faeden)) return;                                    // faeden: Fäden dieses Aufrufs ab versatz
    uint fi = gi + uint(pc.versatz);
    rs = pcgHash(fi ^ pcgHash(uint(pc.runde) * 2654435769u ^ pcgHash(pc.saat)));
    lies();
    int base = int(fi) * 24;
    vec3 s0 = vec3(0.0), s1 = vec3(0.0);                                   // Bild: Hälften; sonst Summe und Quadrate
    float sm = 0.0, sm2 = 0.0, zahl = 0.0, abgebrochen = 0.0, nanZahl = 0.0;
#ifdef TEILE
    vec3 t0 = vec3(0.0), t1 = vec3(0.0), t2 = vec3(0.0), t3 = vec3(0.0);
#endif
    bool bild = pc.modus == 0;
    int px = int(fi) % max(1, pc.breite), py = int(fi) / max(1, pc.breite);
    for (int j = 0; j < pc.pfade; j++) {
        schritte = 0;
        abbruch = false;
        nanPfad = false;
        neueWellen();
        if (pc.modus == 2) {                                               // freier Flug durch Wasser
            w = aktiv;
            L = fliegeWasser(W[W_S]) == ENDE ? w : vec4(0.0);
        } else {                                                           // Bild, Richtung, Einfachstreuung: ein Aufruf
            vec3 p0 = vec3(W[W_P0], W[W_P0 + 1], W[W_P0 + 2]), d0 = vec3(W[W_DIR], W[W_DIR + 1], W[W_DIR + 2]);
            if (bild) {
                float nx = (float(px) + zufall()) / float(pc.breite) * 2.0 - 1.0, ny = (float(py) + zufall()) / float(pc.hoehe) * 2.0 - 1.0;
                vec3 b0 = vec3(W[W_CAM], W[W_CAM + 1], W[W_CAM + 2]), b1 = vec3(W[W_CAM + 3], W[W_CAM + 4], W[W_CAM + 5]), b2 = vec3(W[W_CAM + 6], W[W_CAM + 7], W[W_CAM + 8]);
                p0 = vec3(0.0);
                d0 = normalize(b0 * nx + b1 * ny + b2);
            }
            verfolge(p0, d0);
        }
        if (abbruch || nanPfad) {
            abgebrochen += abbruch ? 1.0 : 0.0;
            nanZahl += nanPfad ? 1.0 : 0.0;
            L = vec4(0.0);
#ifdef TEILE
            LT0 = LT1 = LT2 = LT3 = vec4(0.0);
#endif
        }
        vec3 v = pc.modus <= 1 ? sammle(L) : L.xyz;
        if (bild) {
            if (((pc.pfadBasis + j) & 1) == 0) s0 += v; else s1 += v;
#ifdef TEILE
            t0 += sammle(LT0); t1 += sammle(LT1); t2 += sammle(LT2); t3 += sammle(LT3);
#endif
        } else {
            float m = dot(L, aktiv) / float(NB);
            s0 += v;
            s1 += v * v;
            sm += m;
            sm2 += m * m;
            zahl += 1.0;
        }
    }
    A[base + 0] += s0.x; A[base + 1] += s0.y; A[base + 2] += s0.z;
    A[base + 3] += s1.x; A[base + 4] += s1.y; A[base + 5] += s1.z;
    if (!bild) { A[base + 6] += sm; A[base + 7] += sm2; A[base + 8] += zahl; }
#ifdef TEILE
    if (bild) {
        A[base + 6] += t0.x; A[base + 7] += t0.y; A[base + 8] += t0.z;
        A[base + 9] += t1.x; A[base + 10] += t1.y; A[base + 11] += t1.z;
        A[base + 12] += t2.x; A[base + 13] += t2.y; A[base + 14] += t2.z;
        A[base + 15] += t3.x; A[base + 16] += t3.y; A[base + 17] += t3.z;
    }
#endif
    A[base + 22] += nanZahl;
    A[base + 23] += abgebrochen;
}
#endif
