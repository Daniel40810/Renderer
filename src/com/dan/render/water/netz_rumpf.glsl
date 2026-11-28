// netz_rumpf.glsl – render.water (bis S9c-1 netz.frag; seit S9c-1 eingebunden von netz.frag und, mit SPIEGEL_HALB,
// netz_halb.frag), seit S3c (Seegang Werkbuch): der Bildpunkt auf dem Netz der Clipmap des Seegangs. Wo am Ort
// kein Wasser ist (η_Bild, wasser.glsl), fällt er weg; das Gelände verdeckt über die Tiefe. Die Normale kommt aus der
// Clipmap in der Stufe, deren Stellen der Bildpunkt auflöst (d_L ≥ fuss / 2, nicht feiner als die Stufe des Scheitels;
// zwischen zwei Stufen linear), dazu die Kräuselung aus dem Wind. Restvarianz fürs Glitzern: mss aus waves minus das
// Aufgelöste der Clipmap (wie Oberflaeche.buch). Schaum (SF25 A): Weißwasser, wo ein Kamm der Fläche γ h überschreitet
// (nur jetzt), und Schaumköpfe auf ihren Kämmen – die Regeln aus P13c an der neuen Fläche; ohne Brandung die Flecken
// aus P8b. Danach die Optik wie wasser.frag (wasser_optik.glsl). Seit S4a gilt im Bereich des Schaumfelds (Bindung 21,
// render.seegang.Schaumfeld) dessen Schaum statt der Statistik.
// Seit S3c-5 (Seegang Werkbuch 19/20): B47 – weicht der Fuß des Netzes (η_Bild der Scheitel, gemischt) am Bildpunkt um mehr
// als FUSS_TOL von η_Bild ab (an Kanten zwischen zwei Spiegeln, an trockenen Scheiteln), sucht der Bildpunkt die Fläche
// η_Bild + η der Welle längs seines Strahls wie wasser.glsl (suche) und schreibt deren Tiefe. B48 – liegt das Gelände vor
// dem Bildpunkt (Tiefe darunter), bricht er vor der Optik ab (Schalter seeMehr.w; das Bild bleibt gleich, die Prüfung der
// Tiefe hätte ihn ohnehin verworfen).
// Seit S3c-6 (Seegang Werkbuch 22, N6): der Fuß steht in netz_fuss.glsl. Mit Vorlauf der Tiefe (nsch.x) hat
// netz_vorlauf.frag die Tiefe geschrieben und je Bildpunkt das siegende Dreieck vermerkt; dieser Durchgang (ohne Prüfung
// der Tiefe) schattiert nur dieses und verwirft die übrigen gleich am Anfang. Zähler je Bildpunkt (nsch.y) für den Befund.
// Seit S3c-8 (SF34 A): als zweites Ziel die Maske (Nummer des Dreiecks); der alte Weg (wasser.frag) verwirft Bildpunkte
// des Netzes damit vor seiner Suche.
#define SWASH_BINDUNG 22    // seit S6c-2: das Bild des Auflaufs (wasser.glsl)
#define SPIEGEL_GROB_BINDUNG 23   // seit S9b-1: die grobe Tiefe für die Spiegelung (wasser_optik.glsl)
#include "/com/dan/render/water/wasser.glsl"
#include "/com/dan/render/water/seegang.glsl"
#include "/com/dan/render/water/wasser_optik.glsl"
#include "/com/dan/render/water/netz.glsl"
#include "/com/dan/render/water/netz_daten.glsl"
#include "/com/dan/render/water/netz_fuss.glsl"
layout(location = 0) in vec3 vP;
layout(location = 1) in vec2 vX0;
layout(location = 2) in float vEta;
layout(location = 3) in float vWelle;
layout(location = 4) flat in int vL;
layout(location = 5) in float vNah;
layout(location = 6) flat in uint vId;  // seit S3c-6 (N6): Nummer des Dreiecks (ab 1)
layout(set = 0, binding = 19) uniform usampler2D sieger;                // N6: Sieger des Vorlaufs je Bildpunkt (0 keiner)
layout(location = 0) out vec4 farbe;
layout(location = 1) out uint maske;     // seit S3c-8 (SF34 A): Nummer des Dreiecks, das hier zeichnet (0 keins)
#ifdef SPIEGEL_HALB
// seit S9c-1 (SF175 A): für die Spiegelung in halber Auflösung – {M · L_Himmel, M} und {Strahl r, Abstand t} (t 0: keine)
layout(location = 2) out vec4 spA;
layout(location = 3) out vec4 spB;
#endif
// seit S4a (Seegang Werkbuch 35): das Schaumfeld aus render.seegang (Schaumfeld: Kopf, dann zwei Kopien, je Raster im Ring)
layout(set = 0, binding = 21, std430) readonly buffer SeeSchaum { ivec4 skA; vec4 skF; ivec4 skFi; vec4 skG; ivec4 skGi; vec4 skR; vec4 skLeer[2]; float skW[]; };

float skWert(int i, int j, ivec4 ix) {
    int n = ix.w;
    return skW[skA.y + ix.z + ((ix.y + j) % n) * n + (ix.x + i) % n];
}

// Menge im Raster an der ruhenden Stelle x0 (x Ost, z Süd, relativ zum Auge), bilinear zwischen den Zellmitten;
// wr: Gewicht des Rasters, 1 innen, über rand Zellen am Rand weich auf 0
float skRaster(vec2 x0, vec4 la, ivec4 ix, float rand, out float wr) {
    int n = ix.w;
    vec2 q = vec2(x0.x - la.x, -x0.y - la.y) / la.z - 0.5;
    float rd = min(min(q.x, q.y), min(float(n - 1) - q.x, float(n - 1) - q.y));
    wr = clamp((rd - 1.0) / max(rand, 1.0), 0.0, 1.0);
    if (wr <= 0.0) return 0.0;
    ivec2 i = ivec2(floor(q));
    vec2 a = q - vec2(i);
    return mix(mix(skWert(i.x, i.y, ix), skWert(i.x + 1, i.y, ix), a.x), mix(skWert(i.x, i.y + 1, ix), skWert(i.x + 1, i.y + 1, ix), a.x), a.y);
}

// Menge Schaum f aus dem Feld (1 frisch; f = e^(−Alter/τ), solange nichts zusammenläuft) und ihr Gewicht w gegen die
// Statistik (0 außerhalb des groben Rasters)
float schaumFeld(vec2 x0, out float w) {
    w = 0.0;
    if (skA.x == 0) return 0.0;
    float wf, wg;
    float ff = skRaster(x0, skF, skFi, skR.x, wf);
    float fg = skRaster(x0, skG, skGi, skR.y, wg);
    w = max(wf, wg);
    return max(mix(fg, ff, wf), 0.0);
}

// Seit S4b (Seegang Werkbuch 36): die Optik des Schaums aus dem Feld [G, Plan 6.4]. Frische f = min(1, f).
//  - Muster: zellig (Blasen und Fetzen) auf SK_ZELLE m, Worley F2 − F1: frischer Schaum deckt ganz, alternder bleibt als
//    Netz an den Grenzen der Zellen; was der Bildpunkt nicht auflöst, im Mittel.
//  - Albedo: frisch 0,55 (Whitlock u. a. 1982), alt 0,1, dazwischen mit der Frische² (seit S4b-2; vorher linear bis 0,2:
//    über die gedeckte Fläche 0,36 statt rund 0,22 nach Koepke 1984, Befund L2s); so im Mittel rund 0,25.
//  - Durchlass (seit S4b-2): alter, dünner Schaum ist eine Schicht über dem Wasser, die (1 − Frische)(1 − Albedo) von
//    Spiegelung und Säule durchlässt; frischer deckt. Vorher deckte jeder Schaum: bei tiefer Sonne wurden die Stege des
//    alten Schaums dunkler als das spiegelnde Wasser (Befund L2s, Xaver).
//  - Blasen unter frischem Schaum hellen die Säule auf (Faktor SK_BLASEN · Frische³, sie vergehen schneller als der
//    Schaum; Lamarre und Melville 1991).
//  - Glattwasser: wo Schaum liegt, dämpfen die Stoffe die unaufgelöste Kräuselung um bis zu SK_GLATT.
const float SK_ZELLE = 0.4, SK_ALB_FRISCH = 0.55, SK_ALB_ALT = 0.1, SK_BLASEN = 2.0, SK_GLATT = 0.6;

float skWorley(vec2 q) {
    vec2 c0 = floor(q);
    float f1 = 1e9, f2 = 1e9;
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++) {
            ivec2 c = ivec2(c0) + ivec2(i, j);
            vec2 pt = vec2(c) + vec2(seeHashI(c), seeHashI(c + ivec2(5113, 2711)));
            float dd = length(q - pt);
            if (dd < f1) { f2 = f1; f1 = dd; } else if (dd < f2) f2 = dd;
        }
    return f2 - f1;
}

// Wertrauschen 0 … 1, glatt (seit S5a-3)
float skRausch(vec2 q) {
    vec2 c = floor(q), a = q - c;
    a = a * a * (3.0 - 2.0 * a);
    ivec2 i = ivec2(c);
    float v00 = seeHashI(i), v10 = seeHashI(i + ivec2(1, 0)), v01 = seeHashI(i + ivec2(0, 1)), v11 = seeHashI(i + ivec2(1, 1));
    return mix(mix(v00, v10, a.x), mix(v01, v11, a.x), a.y);
}

// Anteil des Bildpunkts, den Schaum der Frische phi deckt (ruhende Stelle x0, Größe des Bildpunkts fuss).
// Seit S5a-3 (Befund L2v, B64: im Brandungsgebiet ein gleichmäßiges Wabennetz): die Zellen verzerrt (Rauschen auf
// 1,1 m), eine zweite, gröbere Lage Zellen (0,9 m), die Breite der Stege nach einem Rauschen auf 3,6 m zwischen 0,3 und
// 1,7 ihres Mittels [G]. Seit S5a-4 bleibt alter Schaum nur in Flecken, mit breiteren Stegen; der Anteil im Mittel ist
// dort kleiner als 1,5 e (die Prüfungen rechnen weiter mit 1,5 e, das ist nur Auskunft).
float skMuster(vec2 x0, float fuss, float phi) {
    float e = mix(0.08, 1.6, phi * phi);                     // Breite der Stege, in Zellen
    float mittel = clamp(1.5 * e, 0.0, 1.0);
    float g = clamp(SK_ZELLE / (2.0 * fuss) - 1.0, 0.0, 1.0);
    if (g <= 0.0 || messAus(MESS_MUSTER)) return mittel;               // seit S9a: Messschalter
    vec2 q = vec2(x0.x + seeAuge.x, -x0.y + seeAuge.y) / SK_ZELLE;
    float n1 = skRausch(q * 0.37), n2 = skRausch(q * 0.11 + vec2(7.3, 1.9));
    vec2 verz = 0.7 * vec2(n1 - 0.5, skRausch(q * 0.37 + vec2(3.7, 8.1)) - 0.5);
    float d = min(skWorley(q + verz), 1.4 * skWorley(q * 0.45 + vec2(11.2, 4.4)));
    float el = e * mix(0.3, 1.7, n2) * mix(1.8, 1.0, phi);
    float m = 1.0 - smoothstep(0.5 * el, el, d);
    // seit S5a-4 (Befund L2v): alter Schaum nur noch in Flecken (Rauschen auf rund 7 m), frischer überall; das Netz
    // deckte vorher alles, wo f über 0,01 lag
    float n3 = skRausch(q * 0.06 + vec2(2.3, 5.9)), s0 = 0.72 - 0.6 * phi;
    m *= smoothstep(s0, s0 + 0.2, n3);
    return mix(mittel, m, g);
}

// Normale (Ost, Nord, oben, J) und Aufgelöstes der Stufe L an der ruhenden Stelle x₀ (x Ost, z Süd), bilinear
vec4 lies(int L, vec2 x0, out vec2 au) {
    int n = snN.y;
    vec4 la = snLage[L];
    vec2 u = clamp(vec2((x0.x - la.x) / la.z, (la.y - x0.y) / la.z), vec2(0.0), vec2(float(n - 1) - 1e-3));
    ivec2 c = ivec2(floor(u));
    vec2 f = u - vec2(c);
    int b = (L * n + c.y) * n + c.x;
    vec4 n00 = snNormale[b], n10 = snNormale[b + 1], n01 = snNormale[b + n], n11 = snNormale[b + n + 1];
    vec2 a00 = snAufg[b], a10 = snAufg[b + 1], a01 = snAufg[b + n], a11 = snAufg[b + n + 1];
    au = mix(mix(a00, a10, f.x), mix(a01, a11, f.x), f.y);
    return mix(mix(n00, n10, f.x), mix(n01, n11, f.x), f.y);
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    zaehle(px, 1u);
    if (nsch.x == 1 && texelFetch(sieger, px, 0).r != vId) discard;     // N6: der Vorlauf hat einen anderen gewählt
    vec3 P, d;
    float t, eta, tiefe;
    bool b47;
    bool mitFuss = netzFuss(vP, vEta, vWelle, vNah, gl_FragCoord.z, P, d, t, eta, tiefe, b47);
    if (b47) zaehle(px, 4u);                                            // seit S3c-7: B47 je Bildpunkt (SF32 A)
    if (!mitFuss) discard;
    zaehle(px, 2u);
    float cosA = dot(d, vorn.xyz);
    gl_FragDepth = tiefe;
    float tg = texelFetch(tiefeUnten, px, 0).r;
    if (seeMehr.w > 0.5 && tg > tiefe) discard;                         // B48: das Gelände liegt davor
    zaehle(px, 3u);
    float wegG = tg > 0.0 ? vorn.w / tg / cosA : 1e30;
    vec3 senk = normalize(vec3(2.0 * auge.y * P.x, 1.0, 2.0 * auge.y * P.z));
    float fuss = t * licht.x / max(abs(dot(d, senk)), 0.02);
    int st = snN.x;
    float lo = max(float(vL), log2(max(0.5 * fuss / snLage[0].z, 1e-6)));
    int L1 = min(int(floor(lo)), st - 1), L2 = min(L1 + 1, st - 1);
    float fl = L1 == L2 ? 0.0 : clamp(lo - floor(lo), 0.0, 1.0);
    vec4 nn = vec4(0.0, 0.0, 1.0, 1.0);
    vec2 au = vec2(0.0);
    if (!messAus(MESS_CLIPMAP)) {                                       // seit S9a: Messschalter
        vec2 a1, a2;
        vec4 n1 = lies(L1, vX0, a1), n2 = lies(L2, vX0, a2);
        nn = mix(n1, n2, fl);
        au = mix(a1, a2, fl);
    }
    float nz = max(nn.z, 0.05);
    vec2 grad = vec2(-nn.x / nz, -nn.y / nz);                          // ∂η/∂Ost, ∂η/∂Nord
    // seit S6c-2 (Seegang Werkbuch 82): im Rechteck des Auflaufs die Neigung des Lösers. Weißwasser seit S6c-2b (Datei 83)
    // an der Front, wo Stand UND Tiefe steil sind: Auf dem dünnen Film einer Böschung ist der Stand so steil wie die Böschung
    // (L4o: die ganze Böschung weiß), am ruhenden Ufer die Tiefe (L4q: ein heller Streifen am Fuß); nur eine Walze hat beides.
    // Stärke s [G: ab 0,08, voll bei 0,3], als Muster des Schaumfelds mit der Frische 0,7 s (zellig), Albedo wie dort.
    float swg = messAus(MESS_AUFLAUF) ? 0.0 : swGewicht(P.xz), swSchaum = 0.0, swFront = 0.0;
    if (swg > 0.0) {
        vec2 sgr;
        float es = swEta(P.xz, sgr);
        if (es != KEIN) grad = mix(grad, sgr, swg);
        float hq;
        vec2 gh;
        if (es != KEIN && swFlaeche(P.xz, hq, gh) != KEIN && hq >= SW_NETZ_ZEIGEN) {
            swFront = smoothstep(0.08, 0.3, min(length(gh), length(sgr)));
            if (swFront > 0.0) swSchaum = swg * smoothstep(0.0, 0.25, swFront) * skMuster(vX0, fuss, 0.7 * swFront);
        }
    }
    float sw[6];
    bool hatSee = see.x > 0.5 && !messAus(MESS_SEEWERTE) && seeWerte(P.xz, sw);
    float mss = hatSee ? sw[4] : 0.0;
    vec3 lang = vec3(grad, max(0.0, mss - au.x));
    float foam = 0.0;
    if (hatSee) {
        if (seeMehr.z > 0.5 && sw[0] > 0.0) {
            float h0 = tg > 0.0 ? max(0.0, dot(P - d * wegG, senk)) : 1e4;
            float bw = messAus(MESS_WEISS) ? 0.0 : seeBrechen(vWelle, seeKammAnteil(sw[0], h0), h0);
            float fk = messAus(MESS_KOEPFE) ? 0.0 : schaumKoepfe(P.xz, fuss, sw, vWelle, vec3(sw[0] * sw[0] / 16.0, au.y, au.x));
            foam = 1.0 - (1.0 - fk) * (1.0 - bw);
        } else foam = schaum(P.xz, sw);
    }
    // seit S4a: im Bereich des Schaumfelds gilt das Feld (es kennt Schaumkronen und Brechen und hat ein Gedächtnis), am
    // Rand des groben Rasters weich in die Statistik von P13c
    float wSf = 0.0;
    float fSf = 0.0;
    if (!messAus(MESS_FELD)) fSf = schaumFeld(vX0, wSf);
    if (wSf > 0.0) {
        // seit S4b: Muster, Albedo nach dem Alter, Blasen, Glattwasser (wasser_optik.glsl liest schaumAlb, blasen, glatt)
        float phi = clamp(fSf, 0.0, 1.0), cov = smoothstep(0.01, 0.06, fSf) * skMuster(vX0, fuss, phi);
        float albF = mix(SK_ALB_ALT, SK_ALB_FRISCH, phi * phi);   // seit S4b-2 mit phi²
        schaumAlb = mix(see.w, albF, wSf);
        schaumT = wSf * (1.0 - phi) * (1.0 - albF);               // seit S4b-2: Durchlass des alten Schaums
        blasen = wSf * SK_BLASEN * phi * phi * phi;
        glatt = wSf * SK_GLATT * smoothstep(0.02, 0.2, fSf);
        foam = mix(foam, cov, wSf);
    }
    // seit S6c-2b (Befund L4r, A17): im Rechteck trägt der Löser die Welle (netz.comp: Verschiebung × (1 − Gewicht)), also
    // auch den Schaum – Schaumfeld und Statistik der Brandung gehören zu den Wellen der Synthese, nicht zu denen des Lösers,
    // und lagen dort als große, glatte Flecken auf 2–3 m tiefem Wasser
    if (swg > 0.0) {
        foam *= 1.0 - swg;
        blasen *= 1.0 - swg;
        glatt *= 1.0 - swg;
    }
    if (swSchaum > foam) {                                               // seit S6c-2b: Albedo der Front wie frischer Schaum
        schaumAlb = mix(schaumAlb >= 0.0 ? schaumAlb : see.w, mix(SK_ALB_ALT, SK_ALB_FRISCH, swFront * swFront), swg);
        schaumT *= 1.0 - swg;
        foam = swSchaum;
    }
    vec3 o = wasserOptik(d, P, senk, fuss, eta, tg, wegG, px, lang, au.x, foam);
    farbe = vec4(min(o, vec3(HDR_MAX)), 1.0);
    // seit S3c-8: hier zeichnet das Netz, im inneren Bereich (imNetz wie der alte Weg); im äußersten Ring zeichnen beide,
    // und die Tiefe entscheidet wie bisher
    maske = imNetz(P.xz) ? vId : 0u;
#ifdef SPIEGEL_HALB
    if (spAuf) { spA = vec4(spM * spLh, spM); spB = vec4(spR, t); }
    else { spA = vec4(0.0); spB = vec4(0.0); }
#endif
}
