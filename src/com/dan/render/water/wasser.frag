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
// Seit S3c-7 (SF32 A): Zähler je Bildpunkt (netz_zaehler.glsl, Binding 15): Aufrufe, im Netz nach der Suche verworfen,
// bis zur Optik. Seit S3c-8 (SF34 A): wo das Netz gezeichnet hat (Maske, Binding 16), fällt der Bildpunkt vor der Suche
// weg; vorher suchte der alte Weg auch dort und verwarf erst danach (rund 1,5 ms je Bild mit viel Wasser, L2j).
#define SWASH_BINDUNG 17    // seit S6c-2: das Bild des Auflaufs (wasser.glsl)
#define SPIEGEL_GROB_BINDUNG 18   // seit S9b-1: die grobe Tiefe für die Spiegelung (wasser_optik.glsl)
#include "/com/dan/render/water/wasser.glsl"
#include "/com/dan/render/water/seegang.glsl"
#include "/com/dan/render/water/wasser_optik.glsl"
#include "/com/dan/render/water/netz.glsl"
#define ZAEHL_BINDUNG 15
#include "/com/dan/render/water/netz_zaehler.glsl"
layout(set = 0, binding = 16) uniform usampler2D netzMaske;             // seit S3c-8: hier zeichnet das Netz (gilt mit nsch.w = 1)
layout(location = 0) out vec4 farbe;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    zaehle(px, 6u);                                                      // seit S3c-7 (SF32 A): Zähler des alten Wegs
    if (nsch.w == 1 && texelFetch(netzMaske, px, 0).r != 0u) discard;   // seit S3c-8 (SF34 A): das Netz hat hier gezeichnet
    float nx = gl_FragCoord.x / bild.x * 2.0 - 1.0, ny = gl_FragCoord.y / bild.y * 2.0 - 1.0;
    vec3 d = normalize(vorn.xyz + rechts.xyz * nx - oben.xyz * ny);
    float t, eta;
    if (!suche(d, t, eta)) discard;
    float tg = texelFetch(tiefeUnten, px, 0).r, cosA = dot(d, vorn.xyz);
    float wegG = tg > 0.0 ? vorn.w / tg / cosA : 1e30;
    if (t >= wegG) discard;
    if (imNetz((d * t).xz)) { zaehle(px, 5u); discard; }                 // seit S3c: dort zeichnet das Netz (netz.frag)
    zaehle(px, 7u);
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
            // seit S3c-5 (B49a): zeichnet das Netz, gilt außerhalb dieselbe Regel wie dort (SF25 A: nur jetzt), sonst springt
            // das Weißwasser am Rand der Clipmap
            float bw = 0.0;
            if (zMax >= 0.9 * ka * GAMMA_B * h0) {
                float b0 = seeBrechen(ks.x, ka, h0);
                if (snN.z == 1) bw = b0;
                else {
                    float b1 = seeBrechen(seeKamm(seeHoehe(P.xz, a, TAU1 * tp, 0.0).x, sr, be).x, ka, h0);
                    float b2 = seeBrechen(seeKamm(seeHoehe(P.xz, a, TAU2 * tp, 0.0).x, sr, be).x, ka, h0);
                    bw = max(b0, max(b1 * exp(-TAU1 / ABKLING), b2 * exp(-TAU2 / ABKLING)));
                }
            }
            float fk = schaumKoepfe(P.xz, fuss, sw, z0.x, m);
            foam = 1.0 - (1.0 - fk) * (1.0 - bw);
        } else {
            lang = seegangLang(P.xz, fuss, sw, aufgL);
            foam = schaum(P.xz, sw);
        }
    }
    vec3 o = wasserOptik(d, P, senk, fuss, eta, tg, wegG, px, lang, aufgL, foam);
    farbe = vec4(min(o, vec3(HDR_MAX)), 1.0);
    gl_FragDepth = vorn.w / (t * cosA);
}
