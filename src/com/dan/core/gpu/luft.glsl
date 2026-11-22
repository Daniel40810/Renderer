// luft.glsl – core.gpu (seit P6c, Werkbuch HalligHooge Teil A, 12.7; seit P7e, 12.8, F58 A): die Atmosphäre für alle
// Bildmodule, Zeile für Zeile wie core.szene.Scattering. Die erste Streuung rechnet der Strahlengang genau, ab der
// zweiten liest er die Quelle J(h, μ, μ_s, φ) aus core.szene.Streuordnungen (statt Ψ nach Hillaire). render.air legt
// die Tabellen im Bildziel ab („Himmel/Durchlässigkeit“, „Himmel/Mehrfachstreuung“ = J, „Himmel/Ansicht“);
// Gelände und Wasser lesen sie mit diesen Funktionen. Es steht im Kern, weil die Bildmodule einander nicht kennen.
//
// Raum: lokal am Auge, x Ost, y oben, z Süd; das Auge über dem Erdmittelpunkt auf der y-Achse. Längen in m.
// Genauigkeit in float: Statt r² − R² (Auslöschung bei r ≈ 6,36 · 10⁶ m) wird q = r² − R² aus der Höhe über dem
// Boden geführt: q = h (2R + h). So bleibt die Höhe eines Punkts auf Millimeter genau, nicht auf einen halben Meter.
// Die Tabellen werden ohne Filter der Karte gelesen (bilinear von Hand, wie Scattering.bilinear): RGBA32F muss
// auf einer Karte nicht linear filterbar sein, und so rechnen Karte und Prozessor gleich.
// atm: x Mie-Streuung, y Mie-Auslöschung (je m am Boden), z Bodenalbedo, w g der Mie-Phase.

const float L_PI = 3.14159265359;
const float R_BOTTOM = 6360000.0, R_TOP = 6460000.0, H_ATM = 1132254.4;
const vec3 RAYLEIGH = vec3(5.802e-6, 13.558e-6, 33.1e-6);
const float RAYLEIGH_H = 8000.0, MIE_H = 1200.0;
const vec3 OZONE = vec3(0.650e-6, 1.881e-6, 0.085e-6);
const float OZONE_CENTER = 25000.0, OZONE_HALF = 15000.0;
const int TRANS_W = 256, TRANS_H = 64, ANSICHT_W = 256, ANSICHT_H = 128;
// Gitter von J wie Streuordnungen: h = 100 km x³ (NR, dicht am Boden), μ = s|s| (NMU), μ_s = s|s| ab −0,2 (NMS, tiefer 0), φ = π t² (NPHI);
// als Bild (2 NPHI NMS) × (NMU NR): x = m + NPHI k (J_R) bzw. + NPHI NMS (J_M), y = j + NMU i; rgb drei Wellenlängen.
// Linear in μ und φ, in μ_s und h logarithmisch (wo ein Wert 0 ist, linear), wie Streuordnungen.vierfach.
const int J_NR = 24, J_NMU = 32, J_NMS = 40, J_NPHI = 32;
const float J_MS_MIN = -0.2, J_MS0 = -0.447213595;   // −√0,2

float rayleighDichte(float h) { return exp(-max(h, 0.0) / RAYLEIGH_H); }
float mieDichte(float h) { return exp(-max(h, 0.0) / MIE_H); }
float ozonDichte(float h) { return max(0.0, 1.0 - abs(h - OZONE_CENTER) / OZONE_HALF); }
vec3 ausloeschung(float h, vec4 atm) { return RAYLEIGH * rayleighDichte(h) + atm.y * mieDichte(h) + OZONE * ozonDichte(h); }
/** Wie ausloeschung mit eigenen Beiwerten (Bänder der Himmelsansicht, seit P7e). */
vec3 ausloeschungK(float h, vec4 atm, vec3 ray, vec3 oz) { return ray * rayleighDichte(h) + atm.y * mieDichte(h) + oz * ozonDichte(h); }

/** q = r² − R² aus der Höhe h über dem Boden. */
float qAus(float h) { return h * (2.0 * R_BOTTOM + h); }
/** Höhe über dem Boden aus q. */
float hAus(float q) { return q / (sqrt(R_BOTTOM * R_BOTTOM + q) + R_BOTTOM); }
/** q eines Punkts im Abstand t vom Ort (r, q) in Richtung mu. */
float qWeiter(float r, float q, float mu, float t) { return q + t * t + 2.0 * r * mu * t; }

float bisOben(float r, float mu) {
    float disc = (R_TOP - r) * (R_TOP + r) + r * r * mu * mu;
    return max(0.0, -r * mu + sqrt(max(0.0, disc)));
}
float bodenDisc(float r, float q, float mu) { return -q + r * r * mu * mu; }
bool trifftBoden(float r, float q, float mu) { return mu < 0.0 && bodenDisc(r, q, mu) >= 0.0; }
/** Abstand bis zum Boden, ohne Auslöschung: q / (−r mu + √D) statt −r mu − √D (sonst bei 4 m Augenhöhe ±0,4 m). */
float bisBoden(float r, float q, float mu) { return max(0.0, q / max(1e-30, -r * mu + sqrt(max(0.0, bodenDisc(r, q, mu))))); }

vec2 durchUv(float r, float q, float mu) {
    float rho = sqrt(max(0.0, q));
    float d = bisOben(r, mu), dMin = R_TOP - r, dMax = rho + H_ATM;
    return vec2((d - dMin) / max(1e-6, dMax - dMin), rho / H_ATM);
}

/** Umkehrung von durchUv: Höhe über dem Boden h, r und mu zu den Tabellenkoordinaten. */
void durchRMu(vec2 x, out float h, out float r, out float mu) {
    float rho = H_ATM * x.y;
    r = sqrt(rho * rho + R_BOTTOM * R_BOTTOM);
    h = rho * rho / (r + R_BOTTOM);
    float dMin = R_TOP - r, dMax = rho + H_ATM, d = dMin + x.x * (dMax - dMin);
    mu = d <= 0.0 ? 1.0 : clamp((H_ATM * H_ATM - rho * rho - d * d) / (2.0 * r * d), -1.0, 1.0);
}

/** Bilinear zwischen Stützstellen bei i/(n−1), wie Scattering.bilinear (ohne den Filter der Karte). */
vec3 tabelle(sampler2D t, vec2 x, ivec2 n) {
    vec2 p = clamp(x, 0.0, 1.0) * vec2(n - 1);
    ivec2 i = min(n - 2, ivec2(p));
    vec2 f = p - vec2(i);
    vec3 a = texelFetch(t, i, 0).rgb, b = texelFetch(t, i + ivec2(1, 0), 0).rgb;
    vec3 c = texelFetch(t, i + ivec2(0, 1), 0).rgb, d = texelFetch(t, i + ivec2(1, 1), 0).rgb;
    return (a + (b - a) * f.x) * (1.0 - f.y) + (c + (d - c) * f.x) * f.y;
}
/** Durchlässigkeit bis zur Oberkante: die Tabelle trägt seit P7e (F59 A) die optische Tiefe, bilinear gelesen, dann e^−τ. */
vec3 durchlaessigkeit(sampler2D t, float r, float q, float mu) { return exp(-tabelle(t, durchUv(r, q, mu), ivec2(TRANS_W, TRANS_H))); }

// Lage in J (wie Streuordnungen.ih, imu, ims, iphi, phi)
float jIh(float h) { return clamp(pow(max(0.0, h) / (R_TOP - R_BOTTOM), 1.0 / 3.0) * float(J_NR - 1), 0.0, float(J_NR - 1)); }
float jImu(float mu) { float s = sign(mu) * sqrt(abs(mu)); return clamp((s + 1.0) * 0.5 * float(J_NMU - 1), 0.0, float(J_NMU - 1)); }
float jIms(float ms) { float s = sign(ms) * sqrt(abs(ms)); return clamp((s - J_MS0) / (1.0 - J_MS0) * float(J_NMS - 1), 0.0, float(J_NMS - 1)); }
float jIphi(float mu, float ms, float nu) {
    float a = sqrt(max(0.0, 1.0 - mu * mu) * max(0.0, 1.0 - ms * ms));
    float phi = a < 1e-6 ? 0.0 : acos(clamp((nu - mu * ms) / a, -1.0, 1.0));
    return clamp(sqrt(phi / L_PI) * float(J_NPHI - 1), 0.0, float(J_NPHI - 1));
}
/** Zwischen a und b in w logarithmisch, wo einer 0 ist linear (je Kanal), wie Streuordnungen.geo. */
vec3 geo(vec3 a, vec3 b, float w) {
    vec3 lin = mix(a, b, w);
    bvec3 pos = greaterThan(min(a, b), vec3(1e-30));
    vec3 lg = exp(mix(log(max(a, vec3(1e-30))), log(max(b, vec3(1e-30))), w));
    return mix(lin, lg, vec3(pos));
}

/** J_R und J_M (je drei Wellenlängen) an Höhe h, Blick μ, Sonne μ_s, cos des Winkels ν: wie Streuordnungen.j. */
void jAus(sampler2D t, float h, float mu, float ms, float nu, out vec3 jr, out vec3 jm) {
    jr = vec3(0.0);
    jm = vec3(0.0);
    if (ms < J_MS_MIN) return;
    float fi = jIh(h), fj = jImu(mu), fk = jIms(ms), fm = jIphi(mu, ms, nu);
    int i = min(J_NR - 2, int(fi)), j = min(J_NMU - 2, int(fj)), k = min(J_NMS - 2, int(fk)), m = min(J_NPHI - 2, int(fm));
    float wi = fi - float(i), wj = fj - float(j), wk = fk - float(k), wm = fm - float(m);
    // je j: in μ_s und dann in der Höhe logarithmisch, φ zuerst linear (wie Streuordnungen.vierfach)
    for (int dj = 0; dj < 2; dj++) {
        float gj = dj == 0 ? 1.0 - wj : wj;
        vec3 ra[2], ma[2];
        for (int di = 0; di < 2; di++) {
            int x0 = m + J_NPHI * k, x1 = x0 + J_NPHI, y = (j + dj) + J_NMU * (i + di), xm = J_NPHI * J_NMS;
            vec3 r0 = mix(texelFetch(t, ivec2(x0, y), 0).rgb, texelFetch(t, ivec2(x0 + 1, y), 0).rgb, wm);
            vec3 r1 = mix(texelFetch(t, ivec2(x1, y), 0).rgb, texelFetch(t, ivec2(x1 + 1, y), 0).rgb, wm);
            vec3 m0 = mix(texelFetch(t, ivec2(x0 + xm, y), 0).rgb, texelFetch(t, ivec2(x0 + xm + 1, y), 0).rgb, wm);
            vec3 m1 = mix(texelFetch(t, ivec2(x1 + xm, y), 0).rgb, texelFetch(t, ivec2(x1 + xm + 1, y), 0).rgb, wm);
            ra[di] = geo(r0, r1, wk);
            ma[di] = geo(m0, m1, wk);
        }
        jr += gj * geo(ra[0], ra[1], wi);
        jm += gj * geo(ma[0], ma[1], wi);
    }
}

float phaseRayleigh(float c) { return 3.0 / (16.0 * L_PI) * (1.0 + c * c); }
float phaseMie(float c, float g) {
    float g2 = g * g;
    return 3.0 / (8.0 * L_PI) * (1.0 - g2) * (1.0 + c * c) / ((2.0 + g2) * pow(max(1e-6, 1.0 + g2 - 2.0 * g * c), 1.5));
}

/** Licht einer Quelle an einem Punkt des Strahls: erste Streuung genau, ab der zweiten J (jr, jm, schon gelesen). */
void licht(inout vec3 S, vec3 oben, float rr, float qq, vec3 l, vec3 e, float pR, float pM, vec3 scR, float scM, vec3 jr, vec3 jm, sampler2D tD) {
    float muL = dot(oben, l);
    vec3 tl = durchlaessigkeit(tD, rr, qq, muL);
    float schatten = trifftBoden(rr, qq, muL) ? 0.0 : 1.0;
    S += e * (schatten * tl * (scR * pR + scM * pM) + scR * jr + scM * jm);
}

/**
 * Strahlengang von Höhe h über dem Boden in Richtung v mit zwei Lichtquellen (e2 = 0 für eine), wie
 * Scattering.integrate: tEnde < 0 heißt bis zum Boden oder zur Oberkante, mit Boden am Ende; sonst bis tEnde
 * (Luftperspektive, ohne Boden). Beiwerte ray, oz der Wellenlängen (drei Kanäle: RAYLEIGH, OZONE), Tabellen tD, tJ dazu.
 * jMitte: J nur einmal je Quelle in der Mitte des Wegs (Luftperspektive, kurze Wege [G]), sonst an jedem Schritt.
 * Ausgabe: gestreutes Licht, Durchlässigkeit.
 */
void strahl(float h, vec3 v, vec3 l1, vec3 e1, vec3 l2, vec3 e2, int schritte, bool quadratisch, float tEnde, vec4 atm,
            vec3 ray, vec3 oz, sampler2D tD, sampler2D tJ, bool jMitte, out vec3 L, out vec3 tp) {
    float r = R_BOTTOM + h, q = qAus(h), mu = v.y;
    bool boden = tEnde < 0.0 && trifftBoden(r, q, mu);
    float tMax = tEnde >= 0.0 ? min(tEnde, bisOben(r, mu)) : boden ? bisBoden(r, q, mu) : bisOben(r, mu);
    L = vec3(0.0); tp = vec3(1.0);
    float g = atm.w;
    float c1 = dot(v, l1), c2 = dot(v, l2);
    float pR1 = phaseRayleigh(c1), pM1 = phaseMie(c1, g), pR2 = phaseRayleigh(c2), pM2 = phaseMie(c2, g);
    bool zwei = dot(e2, e2) > 0.0;
    vec3 jr1 = vec3(0.0), jm1 = vec3(0.0), jr2 = vec3(0.0), jm2 = vec3(0.0);
    if (jMitte) {
        float tm = 0.5 * tMax, qq = max(0.0, qWeiter(r, q, mu, tm)), rr = sqrt(R_BOTTOM * R_BOTTOM + qq);
        vec3 oben = (vec3(0.0, r, 0.0) + v * tm) / rr;
        float hh = hAus(qq), muV = dot(oben, v);
        jAus(tJ, hh, muV, dot(oben, l1), c1, jr1, jm1);
        if (zwei) jAus(tJ, hh, muV, dot(oben, l2), c2, jr2, jm2);
    }
    for (int k = 0; k < schritte; k++) {
        float a = float(k) / float(schritte), b = float(k + 1) / float(schritte);
        float t0 = quadratisch ? tMax * a * a : tMax * a, t1 = quadratisch ? tMax * b * b : tMax * b;
        float tm = 0.5 * (t0 + t1), dt = t1 - t0;
        vec3 p = vec3(0.0, r, 0.0) + v * tm;
        float qq = max(0.0, qWeiter(r, q, mu, tm)), rr = sqrt(R_BOTTOM * R_BOTTOM + qq), hh = hAus(qq);
        vec3 oben = p / rr;
        float dR = rayleighDichte(hh), dM = mieDichte(hh), scM = atm.x * dM;
        vec3 scR = ray * dR;
        if (!jMitte) {
            float muV = dot(oben, v);
            jAus(tJ, hh, muV, dot(oben, l1), c1, jr1, jm1);
            if (zwei) jAus(tJ, hh, muV, dot(oben, l2), c2, jr2, jm2);
        }
        vec3 S = vec3(0.0);
        licht(S, oben, rr, qq, l1, e1, pR1, pM1, scR, scM, jr1, jm1, tD);
        if (zwei) licht(S, oben, rr, qq, l2, e2, pR2, pM2, scR, scM, jr2, jm2, tD);
        vec3 ext = max(ausloeschungK(hh, atm, ray, oz), vec3(1e-12));
        vec3 sT = exp(-ext * dt);
        L += tp * (S - S * sT) / ext;
        tp *= sT;
    }
    if (boden) {
        vec3 oben = normalize(vec3(0.0, r, 0.0) + v * tMax);
        float m1 = dot(oben, l1), m2 = dot(oben, l2);
        if (m1 > 0.0) L += tp * e1 * durchlaessigkeit(tD, R_BOTTOM, 0.0, m1) * m1 * atm.z / L_PI;
        if (zwei && m2 > 0.0) L += tp * e2 * durchlaessigkeit(tD, R_BOTTOM, 0.0, m2) * m2 * atm.z / L_PI;
    }
}

// Himmelsansicht: u = Azimut / 2π (von Nord über Ost, umlaufend), v nach Hillaire (0 Zenit, 0,5 Kimm, 1 Nadir).
vec3 ansichtRichtung(vec2 uv, float h) {
    float r = R_BOTTOM + h, beta = atan(R_BOTTOM, sqrt(qAus(h))), kimm = L_PI - beta, zen;
    if (uv.y < 0.5) {
        float c = 1.0 - 2.0 * uv.y;
        zen = kimm * (1.0 - c * c);
    } else {
        float c = uv.y * 2.0 - 1.0;
        zen = kimm + beta * c * c;
    }
    float az = uv.x * 2.0 * L_PI;
    return vec3(sin(zen) * sin(az), cos(zen), -sin(zen) * cos(az));
}
vec2 ansichtUv(vec3 d, float h) {
    float beta = atan(R_BOTTOM, sqrt(qAus(h))), kimm = L_PI - beta;
    float zen = atan(length(d.xz), d.y), v;     // nicht acos: auf manchen Karten auf 10⁻⁴ ungenau
    if (zen < kimm) v = (1.0 - sqrt(max(0.0, 1.0 - zen / kimm))) * 0.5;
    else v = sqrt(max(0.0, (zen - kimm) / beta)) * 0.5 + 0.5;
    return vec2(fract(atan(d.x, -d.z) / (2.0 * L_PI)), v);
}
/** Strahldichte aus der Himmelsansicht: bilinear zwischen den Texelmitten, im Azimut umlaufend, in der Höhe am Rand gehalten. */
vec3 ansicht(sampler2D a, vec3 d, float h) {
    vec2 p = ansichtUv(d, h) * vec2(ANSICHT_W, ANSICHT_H) - 0.5;
    p.y = clamp(p.y, 0.0, float(ANSICHT_H - 1));
    ivec2 i = ivec2(floor(p));
    vec2 f = p - vec2(i);
    int x0 = (i.x % ANSICHT_W + ANSICHT_W) % ANSICHT_W, x1 = (x0 + 1) % ANSICHT_W, y0 = min(i.y, ANSICHT_H - 2), y1 = y0 + 1;
    f.y = p.y - float(y0);
    vec3 l0 = mix(texelFetch(a, ivec2(x0, y0), 0).rgb, texelFetch(a, ivec2(x1, y0), 0).rgb, f.x);
    vec3 l1 = mix(texelFetch(a, ivec2(x0, y1), 0).rgb, texelFetch(a, ivec2(x1, y1), 0).rgb, f.x);
    return mix(l0, l1, f.y);
}

// Die Werte des Himmels je Bild (render.air legt sie als Puffer „Himmel/Werte“ ab, std430). Richtungen relativ zum
// Auge (x Ost, y oben, z Süd), Licht in lx und cd/m² ohne Vorbelichtung.
struct Himmelswerte {
    vec4 atm;       // Mie-Streuung, Mie-Auslöschung, Bodenalbedo, g
    vec4 sonne;     // Richtung zur Sonne, aus der das Licht kommt (seit P10d, F100 A, die gehobene; bis P10c die wahre); w Augenhöhe über dem Boden (m)
    vec4 sonneE;    // Beleuchtungsstärke außerhalb der Luft (lx je Kanal); w Vorbelichtung
    vec4 mond;      // Richtung zum Mond (seit P10d die gehobene); w Leuchtdichte des Nachthimmels im Zenit (cd/m²)
    vec4 mondE;     // Beleuchtungsstärke des Monds außerhalb der Luft; w Winkel eines Bildpunkts (rad)
    vec4 rechts;    // Bild: rechts; w a / t (Seitenverhältnis durch Brennweite)
    vec4 oben;      // Bild: oben; w 1 / t
    vec4 vorn;      // Blickachse; w nah (m)
    vec4 sonneS;    // scheinbare Richtung (mit Strahlenbrechung); w cos des Radius
    vec4 sonneL;    // x Leuchtdichte der Scheibe in der Mitte außerhalb der Luft (cd/m²), y Randverdunklung u, z sin des Radius
    vec4 mondS;     // scheinbare Richtung; w cos des Radius
    vec4 mondK;     // Leuchtdichte des Monds je cos des Lichteinfalls (cd/m²); w sin des Radius
    vec4 bild;      // Breite, Höhe, Schritte der Luftperspektive, 0
    vec4 farbe;     // Stärke und Farbe der direkten Sonne je Kanal als Faktor auf sonneE mal Durchlässigkeit der drei Kanäle (Gelände, Wasser; seit P7k; seit P10d nach der Luft, je Bild für die Höhe der Sonne); 0
    vec4 farbeM;    // dasselbe für den Mond (auf mondE), für seine Höhe (seit P10d); 0
};
