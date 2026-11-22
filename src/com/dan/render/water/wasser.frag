#version 460
// wasser.frag – render.water (P6d): je Bildpunkt der Spiegel aus water.eta (wasser.glsl), wenn er vor dem Gelände
// liegt. Leuchtdichte: Fresnel (n = 1,34) mal Himmel aus der Himmelsansicht in Richtung der Spiegelung, Glitzern von
// Sonne und Mond nach Cox und Munk mit der Restvarianz, durch die Fläche gebrochen das Licht der Wassersäule und des
// Grunds aus der Tafel der Säule (P7d, core.szene.Saeule: Mehrfachstreuung nach Fournier-Forand aus Sonne, Mond und
// Himmel; der Grund nach Lambert mit ρ = π L_u / E aus dem Bild darunter).
// Ausgabe cd/m² mal Vorbelichtung und die Tiefe des Spiegels (für die Luftperspektive des Himmels).
// Spiegelung des Geländes (F45 A, P6e): der gespiegelte Strahl im Bild darunter verfolgt; ohne Treffer der Himmel.
// Stufe 2 (P8b, F76 A, seegang.glsl): mit Seegang dazu die langen Wellen aus dem örtlichen Spektrum, ihre Restvarianz
// ins Glitzern und Schaum aus waves.diss; ohne Seegang (see.x = 0) wie Stufe 1.
// Seit P13c (Brandung, F159 A, F161 A; seeMehr.z = 1): die langen Wellen mit Höhe und spitzen Kämmen (der Strahl trifft
// die bewegte Fläche), Weißwasser, wo ein Kamm γ h überschreitet, Schaumköpfe auf den Kämmen, gefiltert nach Bildpunkt.
#include "/com/dan/render/water/wasser.glsl"
#include "/com/dan/render/water/seegang.glsl"
layout(set = 0, binding = 6) uniform sampler2D unten;
layout(set = 0, binding = 7) uniform sampler2D tiefeUnten;
// Tafel der Säule je Kanal (Saeule.tafel): L [x][μ_s][μ_v], C [x][μ_s][μ_v], ln T [x][μ_s], ln U [x][μ_v], R [x],
// L des Himmels [x][μ_v], ln T des Himmels [x]; x in 32 Stufen x_j = 40 (j/31)⁴ (F61 A), μ_s in 8 Punkten, μ_v in 8 Fächern
// ab 0,665. Zwischen den Stufen: L, C, L des Himmels linear in q = 1 − e^(−3,5 x), ln T, ln U, R linear in x.
layout(set = 0, binding = 8, std430) readonly buffer Saeule { float sae[]; };
// Licht des Himmels (licht.comp): auf die Fläche an der Luft, durch sie ins Wasser, je Stufe von μ_s (seit P10d, F104 A) und daraus
// je Tafel der Säule (Grundtafel, dann die Stufen der Trübung) und Kanal L des Himmels [x][μ_v] und ln T des Himmels [x]
layout(set = 0, binding = 9, std430) readonly buffer Licht { vec4 himmelLuft; vec4 himmelWasser; vec4 himmelStufe[8]; float hl[]; };
const int HL_JE = 32 * 8 + 32;
// Trübung am Ort (P9b): C_opt (g/m³) je Zelle des Netzes des Sediments in zwei Plätzen: tr[Platz · nx · ny + Zelle]
layout(set = 0, binding = 11, std430) readonly buffer Truebung { float tr[]; };
// seit P12a: der Schatten der Häuser auf dem Wasser (render.bau; ohne Häuser ist der Faktor genau 1)
#define BAU_SCHATTEN_BILD 12
#define BAU_SCHATTEN_WERTE 13
#include "/com/dan/core/gpu/bauschatten.glsl"
const int S_NX = 32;
const int S_JE = 2 * S_NX * 64 + S_NX * 8 + S_NX * 8 + S_NX + S_NX * 8 + S_NX, S_C = S_NX * 64, S_T = 2 * S_NX * 64, S_U = S_T + S_NX * 8, S_R = S_U + S_NX * 8, S_LH = S_R + S_NX, S_TH = S_LH + S_NX * 8;
const float S_MU = 0.665;
// Wirksame Fläche (P7g, core.szene.Flaeche), hinter der Tafel der Säule: [μ][σ²]{Spiegelung, sin der Höhe des Spiegelbilds,
// Breite (rad), Durchlass, seit P11d sin der Höhe an den Quantilen 1/8, 3/8, 5/8, 7/8}; μ_i = (i/31)², σ²_j = 0,12 (j/11)²
const int S_FL = 3 * S_JE, FL_NMU = 32, FL_NS = 12, FL_JE = 8;
const float FL_S2 = 0.12;
// Tafeln der Säule für die Stufen der Trübung (P9b), hinter der wirksamen Fläche: je Stufe drei Kanäle wie vorn
const int S_STUFEN = 6, S_ORT = S_FL + FL_NMU * FL_NS * FL_JE;
layout(location = 0) out vec4 farbe;
const float HDR_MAX = 60000.0;
const int SPIEGEL_SCHRITTE = 64;

// Bildpunkt eines Orts relativ zum Auge (x, y in Bildpunkten, z Abstand längs der Blickachse)
vec3 bildpunkt(vec3 Q) {
    float z = dot(Q, vorn.xyz);
    vec2 n = vec2(dot(Q, rechts.xyz) / dot(rechts.xyz, rechts.xyz), -dot(Q, oben.xyz) / dot(oben.xyz, oben.xyz)) / z;
    return vec3((n * 0.5 + 0.5) * bild.xy, z);
}

// Spiegelung im Bild darunter: Schritte wachsend um 15 % ab 0,2 m [G], ein Treffer liegt hinter dem Gelände im Bild,
// höchstens um die Länge des Schritts und 1 m [G]; dann vier Schritte Halbierung. Gewicht: zum Bildrand hin 0 [G].
vec3 spiegelung(vec3 P, vec3 r, out float w) {
    w = 0.0;
    float s = max(0.2, 2e-3 * length(P)), s0 = 0.0;
    for (int k = 0; k < SPIEGEL_SCHRITTE; k++) {
        vec3 b = bildpunkt(P + r * s);
        if (b.z <= vorn.w || b.x < 0.0 || b.y < 0.0 || b.x >= bild.x || b.y >= bild.y) return vec3(0.0);
        float tg = texelFetch(tiefeUnten, ivec2(b.xy), 0).r;
        if (tg > 0.0) {
            float zt = vorn.w / tg, dicke = (s - s0) * abs(dot(r, vorn.xyz)) + 1.0;
            if (b.z > zt && b.z - zt < dicke) {
                float a = s0, e = s;
                for (int i = 0; i < 4; i++) {
                    float m = 0.5 * (a + e);
                    vec3 bm = bildpunkt(P + r * m);
                    float tm = texelFetch(tiefeUnten, ivec2(clamp(bm.xy, vec2(0.0), bild.xy - 1.0)), 0).r;
                    if (tm > 0.0 && bm.z > vorn.w / tm) e = m; else a = m;
                }
                b = bildpunkt(P + r * e);
                vec2 rand = min(b.xy, bild.xy - b.xy) / (0.05 * bild.y);
                w = clamp(min(rand.x, rand.y), 0.0, 1.0);
                return texelFetch(unten, ivec2(clamp(b.xy, vec2(0.0), bild.xy - 1.0)), 0).rgb;
            }
        }
        s0 = s;
        s *= 1.15;
    }
    return vec3(0.0);
}

// Lage in der Tafel (wie Saeule.wert) und Interpolation (wie Saeule.eins, zwei, drei)
float sIms(float mu) { return clamp((mu - S_MU) / (1.0 - S_MU) * 7.0, 0.0, 7.0); }
float sImv(float mu) { return clamp((mu - S_MU) / (1.0 - S_MU) * 8.0 - 0.5, 0.0, 7.0); }
float sX(int j) { float a = float(j) / float(S_NX - 1); a *= a; return 40.0 * a * a; }
float sQ(float x) { return 1.0 - exp(-3.5 * x); }
// Stufe j und die Brüche in q und in x für die Tiefe x (in a + b_b)
void sLage(float x, out int j, out float fq, out float fx) {
    x = clamp(x, 0.0, 40.0);
    j = min(S_NX - 2, int(float(S_NX - 1) * sqrt(sqrt(x / 40.0))));
    float x0 = sX(j), x1 = sX(j + 1);
    float dq = sQ(x1) - sQ(x0);
    fx = clamp((x - x0) / (x1 - x0), 0.0, 1.0);
    fq = dq > 1e-4 ? clamp((sQ(x) - sQ(x0)) / dq, 0.0, 1.0) : fx;
}
float sEins(int o, int j, float f) { return mix(sae[o + j], sae[o + j + 1], f); }
// dieselbe Lage in den Tafeln des Himmels aus licht.comp (seit P10d)
float hEins(int o, int j, float f) { return mix(hl[o + j], hl[o + j + 1], f); }
float hZwei(int o, int j, float f, float c) {
    int k = min(6, int(c)), i = o + j * 8 + k;
    float fc = c - float(k);
    return mix(mix(hl[i], hl[i + 1], fc), mix(hl[i + 8], hl[i + 9], fc), f);
}
// Seit P10d (F103 A, F104 A): die ganze Neigungsvarianz der Fläche um die mittlere Normale (gezeichnet und Rest); 0 ohne wirksame Fläche
float s2Ganz = 0.0;
float sZwei(int o, int j, float f, float c) {
    int k = min(6, int(c)), i = o + j * 8 + k;
    float fc = c - float(k);
    return mix(mix(sae[i], sae[i + 1], fc), mix(sae[i + 8], sae[i + 9], fc), f);
}
float sDrei(int o, int j, float f, float c, float e) {
    int k = min(6, int(c)), m = min(6, int(e)), i = o + (j * 8 + k) * 8 + m;
    float fc = c - float(k), fe = e - float(m);
    float a0 = mix(mix(sae[i], sae[i + 1], fe), mix(sae[i + 8], sae[i + 9], fe), fc);
    float a1 = mix(mix(sae[i + 64], sae[i + 65], fe), mix(sae[i + 72], sae[i + 73], fe), fc);
    return mix(a0, a1, f);
}

vec4 flaeche(float mu, float s2);

// Säule aus einer Quelle (Richtung l, Licht E senkrecht zur Richtung): addiert die Leuchtdichte unter der Fläche über
// schwarzem Grund zu Lc und die Bestrahlung des Grunds zu Gs (je Kanal); v zum Auge, j, fq, fx die Lage der Tiefe.
void quelle(int basis, vec3 l, vec3 E, vec3 senk, vec3 v, ivec3 j, vec3 fq, vec3 fx, float e, inout vec3 Lc, inout vec3 Gs) {
    float mu = dot(l, senk);
    if (mu <= 0.0 || dot(E, vec3(1.0)) <= 0.0) return;
    // seit P10d (F104 A): durch die wirksame Fläche hinein (Durchlass für die Richtung der Quelle), ohne sie wie bisher glatt
    vec3 Ew = E * mu * (licht.w > 0.5 ? flaeche(mu, s2Ganz).w : 1.0 - fresnel(mu, aW.w));
    float c = sIms(gebrochen(mu, aW.w));
    vec3 hl = -l + senk * mu, hv = v - senk * dot(v, senk);
    float nl = length(hl), nv = length(hv), cphi = nl > 1e-6 && nv > 1e-6 ? dot(hl, hv) / (nl * nv) : 0.0;
    for (int k = 0; k < 3; k++) {
        int o = basis + k * S_JE;
        Lc[k] += Ew[k] * (sDrei(o, j[k], fq[k], c, e) + 2.0 * cphi * sDrei(o + S_C, j[k], fq[k], c, e));
        Gs[k] += Ew[k] * exp(sZwei(o + S_T, j[k], fx[k], c));
    }
}

// Die Säule der Tiefe D mit der Dämpfung K aus den Tafeln ab basis: Leuchtdichte über schwarzem Grund aus Sonne und Mond
// (Lc) und aus dem Himmel (Lch), Bestrahlung des Grunds (Gs), Weg vom Grund zum Auge (Ug), Rückwurf der Säule (Rg).
void saeule(int basis, vec3 K, float D, vec3 Es, vec3 Em, vec3 senk, vec3 v, float e, out vec3 Lc, out vec3 Lch, out vec3 Gs, out vec3 Ug, out vec3 Rg) {
    vec3 fq, fx;
    ivec3 j;
    Lc = vec3(0.0);
    Gs = vec3(0.0);
    for (int k = 0; k < 3; k++) sLage(K[k] * D, j[k], fq[k], fx[k]);
    quelle(basis, hw.sonne.xyz, Es, senk, v, j, fq, fx, e, Lc, Gs);
    quelle(basis, hw.mond.xyz, Em, senk, v, j, fq, fx, e, Lc, Gs);
    // seit P10d (F104 A): das Himmelslicht nach seinen Stufen unter der Fläche (Tafeln aus licht.comp) statt gleich verteilt
    int hs = basis == 0 ? 0 : 1 + (basis - S_ORT) / (3 * S_JE);
    for (int k = 0; k < 3; k++) {
        int o = basis + k * S_JE, ho = (hs * 3 + k) * HL_JE;
        Lch[k] = hZwei(ho, j[k], fq[k], e);
        Gs[k] += exp(hEins(ho + 256, j[k], fx[k]));
        Ug[k] = exp(sZwei(o + S_U, j[k], fx[k], e));
        Rg[k] = sEins(o + S_R, j[k], fx[k]);
    }
}

// Trübung am Ort p (x Ost, z Süd relativ zum Auge; P9b): C_opt (g/m³) aus dem Netz des Sediments, bilinear zwischen den
// Zellmitten über die Zellen mit Wasser (trockene tragen 0 und zählen nicht), linear in der Zeit; zum Rand des Netzes
// hin und außerhalb der Schwebstoff der Szene (aP.w).
float truebungOrt(vec2 p) {
    float szene = aP.w;
    if (bodN.x == 0) return szene;
    float ux = (p.x - bodF.x) * bodF.w, uy = (-p.y - bodF.y) * bodF.w;
    int nx = bodN.y, ny = bodN.z;
    if (ux < 0.0 || uy < 0.0 || ux >= float(nx) || uy >= float(ny)) return szene;
    float bx = clamp(ux - 0.5, 0.0, float(nx - 1)), by = clamp(uy - 0.5, 0.0, float(ny - 1));
    int i0 = max(min(int(bx), nx - 2), 0), j0 = max(min(int(by), ny - 2), 0);
    float fx = bx - float(i0), fy = by - float(j0);
    int i1 = min(i0 + 1, nx - 1), j1 = min(j0 + 1, ny - 1), n = nx * ny, pa = bodN.w;
    float summe = 0.0, gewicht = 0.0;
    for (int k = 0; k < 4; k++) {
        int c = ((k & 1) == 0 ? i0 : i1) + nx * (k < 2 ? j0 : j1);
        float w = ((k & 1) == 0 ? 1.0 - fx : fx) * (k < 2 ? 1.0 - fy : fy);
        float a = tr[pa * n + c], b = tr[(1 - pa) * n + c], wert = a + bodZ.x * (b - a);
        if (wert > 0.0) { summe += w * wert; gewicht += w; }
    }
    if (gewicht < 1e-4) return szene;
    float rand = clamp(min(min(ux, float(nx) - ux), min(uy, float(ny) - uy)) / bodZ.w, 0.0, 1.0);
    return mix(szene, summe / gewicht, rand);
}

vec4 flWert(int o) { return vec4(sae[o], sae[o + 1], sae[o + 2], sae[o + 3]); }
// Die wirksame Fläche für den Blick mu (cos zur Normalen) und die Varianz s2 der nicht aufgelösten Neigungen (wie Flaeche.lies);
// teil 0: {Spiegelung, sin Höhe, Breite, Durchlass}, teil 4: sin der Höhe an den vier Quantilen (seit P11d)
vec4 flaecheTeil(float mu, float s2, int teil) {
    float a = float(FL_NMU - 1) * sqrt(clamp(mu, 0.0, 1.0)), b = float(FL_NS - 1) * sqrt(clamp(s2 / FL_S2, 0.0, 1.0));
    int i = min(FL_NMU - 2, int(a)), j = min(FL_NS - 2, int(b));
    float fa = a - float(i), fb = b - float(j);
    int o = S_FL + (i * FL_NS + j) * FL_JE + teil;
    return mix(mix(flWert(o), flWert(o + FL_JE), fb), mix(flWert(o + FL_NS * FL_JE), flWert(o + FL_NS * FL_JE + FL_JE), fb), fa);
}
vec4 flaeche(float mu, float s2) { return flaecheTeil(mu, s2, 0); }

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float nx = gl_FragCoord.x / bild.x * 2.0 - 1.0, ny = gl_FragCoord.y / bild.y * 2.0 - 1.0;
    vec3 d = normalize(vorn.xyz + rechts.xyz * nx - oben.xyz * ny);
    float t, eta;
    if (!suche(d, t, eta)) discard;
    float tg = texelFetch(tiefeUnten, px, 0).r, cosA = dot(d, vorn.xyz);
    float wegG = tg > 0.0 ? vorn.w / tg / cosA : 1e30;
    if (t >= wegG) discard;
    vec3 P = d * t;
    vec3 senk = normalize(vec3(2.0 * auge.y * P.x, 1.0, 2.0 * auge.y * P.z));
    float fuss = t * licht.x / max(abs(dot(d, senk)), 0.02), rest;
    float sw[6];
    float aufgL = 0.0, foam = 0.0;
    vec3 lang = vec3(0.0);
    if (see.x > 0.5 && seeWerte(P.xz, sw)) {
        if (seeMehr.z > 0.5 && sw[0] > 0.0 && sw[1] > 0.0) {
            // P13c (F159 A): die langen Wellen mit Höhe. Tiefe h0 unter der Ebene aus dem Bild darunter; Kämme spitz nach
            // dem Kammanteil; der Strahl trifft die Fläche η_Bild + ζ_s: zwischen der Höhe der höchsten Kämme und der
            // tiefsten Täler in 16 Schritten, dann 5-mal halbiert.
            float a[LANG];
            vec3 m = seeAmplituden(fuss, sw, a);
            float h0 = tg > 0.0 ? max(0.0, dot(P - d * wegG, senk)) : 1e4;
            float ka = seeKammAnteil(sw[0], h0), be = (ka - 0.5) / 0.375, sr = sqrt(m.y);
            // Der Strahl sucht die Höhe nur, wo die Spanne der Kämme mehr als 1,5 Bildpunkte hoch ist (weiter draußen ist
            // eine Welle kleiner als ein Bildpunkt; die Normale kommt dort trotzdem aus der Höhe) [G]. Die 4 bis 16 Schritte der
            // Vorsuche nehmen nur die tragenden Wellen (a > 0,15 σ), die 5 Halbierungen alle (Lauf C: +6,3 ms an der Westspitze).
            float nrm = sqrt(1.0 + 0.5 * be * be);
            float zMax = (3.5 + 5.625 * be) * sr / nrm, zMin = -3.5 * sr;
            if (m.y > 1e-6 && (zMax - zMin) / (t * licht.x) > 1.5) {
                float tA = treffen(d, eta + zMax), tB = treffen(d, eta + zMin);
                if (tA < 0.0) tA = vorn.w;
                if (tB < 0.0 || tB <= tA) tB = 2.0 * t - tA;
                float rk = auge.y * dot(d.xz, d.xz), agrob = 0.15 * sr;
                float tP = tA, tH = tB;
                // Schritte nach der Spanne in Bildpunkten: 4 … 16 (Lauf C2: Kosten an der Westspitze)
                int ns = int(clamp(ceil((zMax - zMin) / (t * licht.x) / 3.0), 4.0, 16.0));
                for (int i = 1; i <= ns; i++) {
                    float ti = mix(tA, tB, float(i) / float(ns));
                    float fi = auge.x + d.y * ti - rk * ti * ti - eta - seeKamm(seeHoehe(d.xz * ti, a, 0.0, agrob).x, sr, be).x;
                    if (fi <= 0.0) {
                        float lo = tP, hi = ti;
                        for (int k = 0; k < 5; k++) {
                            float tm = 0.5 * (lo + hi);
                            float fm = auge.x + d.y * tm - rk * tm * tm - eta - seeKamm(seeHoehe(d.xz * tm, a, 0.0, 0.0).x, sr, be).x;
                            if (fm > 0.0) lo = tm; else hi = tm;
                        }
                        tH = 0.5 * (lo + hi);
                        break;
                    }
                    tP = ti;
                }
                t = tH;
                if (t >= wegG || t <= 0.0) discard;
                P = d * t;
                senk = normalize(vec3(2.0 * auge.y * P.x, 1.0, 2.0 * auge.y * P.z));
            }
            vec3 z0 = seeHoehe(P.xz, a, 0.0, 0.0);
            vec2 ks = seeKamm(z0.x, sr, be);
            lang = vec3(z0.yz * ks.y, max(0.0, sw[4] - m.z));
            aufgL = m.z;
            // Weißwasser (McCowan) am Kamm jetzt und eine halbe und anderthalb Perioden zurück, abklingend [G]
            float tp = sw[1];
            // nur, wo der höchste Kamm die Grenze erreichen kann (sonst ist seeBrechen überall 0)
            float bw = 0.0;
            if (zMax >= 0.9 * ka * GAMMA_B * h0) {
                float b0 = seeBrechen(ks.x, ka, h0);
                float b1 = seeBrechen(seeKamm(seeHoehe(P.xz, a, TAU1 * tp, 0.0).x, sr, be).x, ka, h0);
                float b2 = seeBrechen(seeKamm(seeHoehe(P.xz, a, TAU2 * tp, 0.0).x, sr, be).x, ka, h0);
                bw = max(b0, max(b1 * exp(-TAU1 / ABKLING), b2 * exp(-TAU2 / ABKLING)));
            }
            float fk = schaumKoepfe(P.xz, fuss, sw, z0.x, m);
            foam = 1.0 - (1.0 - fk) * (1.0 - bw);
        } else {
            lang = seegangLang(P.xz, fuss, sw, aufgL);
            foam = schaum(P.xz, sw);
        }
    }
    float aufgK;
    vec3 n = kraeuselungMit(P.xz, senk, fuss, lang, rest, aufgK);
    vec3 v = -d;
    s2Ganz = licht.w > 0.5 ? rest + aufgK + aufgL : 0.0;
    float nw = aW.w, mun = max(dot(v, n), 0.0), F = fresnel(mun, nw), durchlass = 1.0 - F, vb = hw.sonneE.w, h = max(0.5, eta);
    // Spiegelung des Himmels (über der Kimm gehalten)
    vec3 r = reflect(d, n);
    float ru = dot(r, senk);
    if (ru < 0.002) r = normalize(r + senk * (0.002 - ru));
    vec3 Lh = ansicht(ansichtT, r, hw.sonne.w) * hw.sonneE.w;
    if (licht.w > 0.5) {
        // P7g (F63 A): die Neigungen, die der Bildpunkt nicht auflöst, im Mittel über die sichtbaren Flanken – sie
        // spiegeln schwächer und einen höheren Himmel und lassen mehr Licht aus dem Wasser. Der Himmel in der Höhe des
        // Spiegelbilds über der Ebene der Normalen, bis P11d drei Proben über seine Breite (Gauß-Hermite), seit P11d vier an den Quantilen.
        vec3 rt = r - n * dot(r, n);
        float lt = length(rt);
        if (lt > 1e-5) {
            rt /= lt;
            // seit P11d (A26): vier Höhen an den Quantilen der Verteilung des Spiegelbilds zu je ¼ statt drei nach Gauß
            vec4 qh = flaecheTeil(mun, rest, 4);
            vec3 Ls = vec3(0.0);
            for (int q = 0; q < 4; q++) {
                float eh = asin(clamp(qh[q], 0.0, 1.0));
                vec3 rk = rt * cos(eh) + n * sin(eh);
                float rku = dot(rk, senk);
                if (rku < 0.002) rk = normalize(rk + senk * (0.002 - rku));
                Ls += 0.25 * ansicht(ansichtT, rk, hw.sonne.w);
            }
            Lh = Ls * hw.sonneE.w;
        }
        // seit P10d (F103 A): Stärke der Spiegelung und Durchlass aus der Tafel mit der ganzen Neigungsvarianz um die mittlere
        // Normale; die gezeichnete Normale bestimmt nur, welcher Himmel gespiegelt wird (oben) und das Glitzern
        vec4 flG = flaeche(max(dot(v, senk), 0.0), s2Ganz);
        F = flG.x;
        durchlass = flG.w;
    }
    if (licht.y > 0.5) {
        float ws;
        vec3 Lg = spiegelung(P, r, ws);
        Lh = mix(Lh, Lg, ws);
    }
    vec3 Es = lichtAm(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h), Em = lichtAm(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h);
    if (bauInfo.x > 0.5) {                                   // seit P12a: der Schatten der Häuser (ohne Häuser unverändert)
        float bs = bauSchatten(P, senk);
        if (bauInfo.y < 0.5) Es *= bs; else Em *= bs;
    }
    vec3 G = glanz(v, n, hw.sonne.xyz, Es, rest) + glanz(v, n, hw.mond.xyz, Em, rest);
    // Wassersäule und Grund aus der Tafel (Tiefe senkrecht aus dem Bild darunter; ohne Grund tief)
    float ci = clamp(dot(v, senk), 0.0, 1.0), e = sImv(gebrochen(ci, nw));
    float D = 1e4;
    if (tg > 0.0) D = max(0.0, dot(P - d * wegG, senk));
    vec3 Lc, Lch, Gs, Ug, Rg;
    if (bodN.x == 0) saeule(0, daempfung(), D, Es, Em, senk, v, e, Lc, Lch, Gs, Ug, Rg);
    else {
        // P9b: Trübung am Ort – zwischen zwei Stufen der Tafeln, linear in ln C; jede Stufe mit ihrer Dämpfung
        float lc = clamp(log(max(truebungOrt(P.xz), 1e-3) / bodZ.y) * bodZ.z, 0.0, float(S_STUFEN - 1));
        int k0 = min(int(lc), S_STUFEN - 2);
        float fc = lc - float(k0), c0 = bodZ.y * exp(float(k0) / bodZ.z), c1 = bodZ.y * exp(float(k0 + 1) / bodZ.z);
        vec3 je = (aP.rgb + bb.rgb) / max(aP.w, 1e-6);
        vec3 Lc1, Lch1, Gs1, Ug1, Rg1;
        saeule(S_ORT + k0 * 3 * S_JE, aW.rgb + je * c0, D, Es, Em, senk, v, e, Lc, Lch, Gs, Ug, Rg);
        saeule(S_ORT + (k0 + 1) * 3 * S_JE, aW.rgb + je * c1, D, Es, Em, senk, v, e, Lc1, Lch1, Gs1, Ug1, Rg1);
        Lc = mix(Lc, Lc1, fc);
        Lch = mix(Lch, Lch1, fc);
        Gs = mix(Gs, Gs1, fc);
        Ug = mix(Ug, Ug1, fc);
        Rg = mix(Rg, Rg1, fc);
    }
    vec3 Lg = vec3(0.0);
    if (tg > 0.0) {
        // Albedo des Grunds aus dem Bild darunter: ρ = π L_u / E an der Luft (L_u mit Vorbelichtung) [G]
        // seit P12b: Alpha des Geländes ist der Faktor zum Grund ohne den Spiegel des Gestaltwassers (sonst 1, F134 A)
        vec4 Lu4 = texelFetch(unten, px, 0);
        vec3 Lu = Lu4.rgb * Lu4.a;
        vec3 Ea = Es * max(dot(hw.sonne.xyz, senk), 0.0) + Em * max(dot(hw.mond.xyz, senk), 0.0) + himmelLuft.rgb;
        vec3 rho = clamp(L_PI * Lu / max(vb * Ea, vec3(1e-30)), vec3(0.0), vec3(0.95));
        Lg = rho * Gs * Ug / (vec3(1.0) - rho * Rg);
    }
    float hinaus = durchlass / (nw * nw);
    vec3 o = F * Lh + vb * (G + hinaus * (Lc + Lch + Lg));
    if (foam > 0.0) {
        // Schaum (P8b): Lambert mit der Albedo see.w unter Sonne, Mond und Himmel [G]
        vec3 Ef = Es * max(dot(hw.sonne.xyz, senk), 0.0) + Em * max(dot(hw.mond.xyz, senk), 0.0) + himmelLuft.rgb;
        o = mix(o, vb * see.w * Ef / L_PI, foam);
    }
    // P7f: ein Teil allein (licht.z: 1 Spiegelung, 2 Glitzern, 3 Säule aus Sonne und Mond, 4 Säule aus dem Himmel, 5 Grund)
    int teil = int(licht.z + 0.5);
    if (teil == 1) o = F * Lh;
    else if (teil == 2) o = vb * G;
    else if (teil == 3) o = vb * hinaus * Lc;
    else if (teil == 4) o = vb * hinaus * Lch;
    else if (teil == 5) o = vb * hinaus * Lg;
    farbe = vec4(min(o, vec3(HDR_MAX)), 1.0);
    gl_FragDepth = vorn.w / (t * cosA);
}
