// surge_front.glsl — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W4 „Die Wand“, W4b): Bausteine für den
// Eckenformer einer Wellenfläche, die als Höhenfeld auf einem Zellgitter vorliegt (die Lösung eines Flachwasserlösers).
//   sgFront:        Laufrichtung, Zeit seit der Ankunft und höchster Stand aus der Flutkarte (FloodMap)
//   sgLipShare:     Anteil der Lippe an einer Ecke (kurz nach der Ankunft, oben im Kamm, nur bei hohen Wellen)
//   sgLipAdvance:   eine Ecke mit Lippe: der Kamm zieht über den Fuß der Wand vor, die Spitze sinkt
//   sgAlongSmooth:  längs der Front glätten (gegen die Treppe des Gitters, wo die Front schräg über die Zeilen läuft)
//   sgSteepen:      Aufsteilen: jede Ecke kurz hinter der Ankunft nimmt die höchste Höhe bis D m hinter sich [G]
// Der Wirt stellt vor dem Einbinden bereit:
//   float surgeHeight(ivec2 c, out float wet);   Höhe der Fläche an der Zelle c (Zeilen nach Norden), wet 0/1
// und setzt die Uniforms (SurgeGl.bindFlood): uSgFloodOn, uSgFlood (RG32F: Ankunft s, höchster Stand m; 1e6 = nie),
// uSgFloodRect (x0, z Süd, Breite, Tiefe; Zeilen nach Norden), uSgEvent (Ereigniszeit jetzt), uSgLipAdvance (0 aus).
// uLevel (Pegel) kommt aus dem Lichtblock des Wirts.
#include "/com/dan/surge/gl/surge_noise.glsl"
#include "/com/dan/surge/gl/surge_flood.glsl"
uniform float uSgLipAdvance;
const float SG_FRONT_SPEED = 60.0;   // Schnelligkeit der Front für die Nachbarn (m/s) [G]

float surgeHeight(ivec2 c, out float wet);

// (Laufrichtung x, z; Zeit seit der Ankunft, −1e9 ohne; höchster Stand). Die Ankunft liegt in Stufen der Bilder der
// Vorrechnung; gemittelt über die Nachbarn (±24 m) und vier Diagonalen (±34 m) ist ihre Linie glatt (Lituya W4 Probe 8).
vec4 sgFront(vec2 xz) {
    if (uSgFloodOn == 0) return vec4(0.0, 0.0, -1e9, 0.0);
    vec2 u = sgFloodUV(xz);
    if (any(lessThan(u, vec2(0.0))) || any(greaterThan(u, vec2(1.0)))) return vec4(0.0, 0.0, -1e9, 0.0);
    vec2 am = textureLod(uSgFlood, u, 0.0).rg;
    if (am.x > 1e5) return vec4(0.0, 0.0, -1e9, am.y);
    float e = 24.0;
    float ax = textureLod(uSgFlood, sgFloodUV(xz + vec2(e, 0.0)), 0.0).r, az = textureLod(uSgFlood, sgFloodUV(xz + vec2(0.0, e)), 0.0).r;
    float bx = textureLod(uSgFlood, sgFloodUV(xz - vec2(e, 0.0)), 0.0).r, bz = textureLod(uSgFlood, sgFloodUV(xz - vec2(0.0, e)), 0.0).r;
    float gxa = ax < 1e5 && bx < 1e5 ? ax - bx : ax < 1e5 ? 2.0 * (ax - am.x) : bx < 1e5 ? 2.0 * (am.x - bx) : 0.0;
    float gza = az < 1e5 && bz < 1e5 ? az - bz : az < 1e5 ? 2.0 * (az - am.x) : bz < 1e5 ? 2.0 * (am.x - bz) : 0.0;
    vec2 g = vec2(gxa, gza);
    float lg = length(g);
    float sum = am.x * 2.0, n = 2.0;
    if (ax < 1e5) { sum += ax; n += 1.0; }
    if (bx < 1e5) { sum += bx; n += 1.0; }
    if (az < 1e5) { sum += az; n += 1.0; }
    if (bz < 1e5) { sum += bz; n += 1.0; }
    for (int k = 0; k < 4; k++) {
        vec2 o = vec2(k == 0 || k == 3 ? e : -e, k < 2 ? e : -e);
        float a = textureLod(uSgFlood, sgFloodUV(xz + o), 0.0).r;
        if (a < 1e5) { sum += a; n += 1.0; }
    }
    return vec4(lg > 1e-4 ? g / lg : vec2(0.0), uSgEvent - sum / n, am.y);
}

// Anteil der Lippe: kurz nach der Ankunft, oben im Kamm, nur bei hohen Wellen. Nur ein schmaler Saum hinter der Front
// (bis rund 3 s, 100 m) zieht vor (Lituya W4b: vorher zog der ganze Rücken bis 9 s mit, die Wand hing nicht über).
float sgLipShare(float seit, float H, float stand) {
    float hs = max(stand - uLevel, 1.0);
    float hrel = clamp(H / hs, 0.0, 1.2);
    float glocke = smoothstep(-0.8, 0.3, seit) * (1.0 - smoothstep(0.9, 3.2, seit));
    return glocke * smoothstep(6.0, 30.0, H) * smoothstep(0.45, 0.95, hrel);
}

// Lage einer Ecke mit Lippe: (x, z, Höhe). Abschnitte längs der Front (rund 100 m, wandern seitlich) brechen zu
// verschiedenen Zeiten; die Spitze sinkt um bis zu 0,35 des Vorlaufs. share: Anteil der Lippe an dieser Ecke.
vec3 sgLipAdvance(vec2 xz, float h, float seit, float stand, vec2 T, float k, float kap, float frei, out float share) {
    float H = h - uLevel;
    float a = dot(xz, vec2(-T.y, T.x));
    float ab = 0.6 + 0.4 * smoothstep(0.30, 0.62, sgNoise(vec2(a / 95.0 + uSgEvent * 0.05, 3.7)));
    float lp = sgLipShare(seit, H, stand) * ab;
    // dazu frei: über die Breite der Wand hinaus (sonst bleibt der Kamm über dem Fuß und nichts hängt über, W4b Probe 1)
    float vor = min(k * max(H, 0.0) * lp + frei * lp, kap) * uSgLipAdvance;
    share = lp;
    return vec3(xz + T * vor, h - vor * 0.35 * smoothstep(0.45, 1.0, lp));
}

// Längs der Front glätten (quer zur Laufrichtung, ±2 Zellen, rund eine Periode der Treppe): die Wand bleibt steil, die
// Treppe geht weg (Lituya W4b „Die Röhre“). Tq in Zellen-Richtung (x Ost, Zeilen nach Norden), f Stärke 0..1.
float sgAlongSmooth(ivec2 c, float h0, vec2 Tq, float f) {
    if (f <= 0.0) return h0;
    vec2 q = vec2(Tq.x, -Tq.y);
    float sum = h0, n = 1.0;
    for (int k = -2; k <= 2; k++) {
        if (k == 0) continue;
        float nb;
        float hb = surgeHeight(c + ivec2(floor(q * float(k) * 1.15 + 0.5)), nb);
        if (nb > 0.5) { sum += hb; n += 1.0; }
    }
    return mix(h0, sum / n, f);
}

// Aufsteilen [G]: ein Löser mit Gittern von 2–16 m läuft als sanfter Buckel von einigen hundert Metern; Zeugen sahen eine
// Wand. Gleich hinter der Ankunftslinie nimmt jede Ecke die höchste Höhe bis D m hinter sich (gegen die Laufrichtung T):
// eine Dehnung zur Front hin, die nicht falten kann; die Linie der Ankunft bleibt, wo die Rechnung sie hat, und hinter
// der Front läuft sie in S s auf die Rechnung zurück. Die Ecke gleitet über anderthalb Abstände der Ecken nach der stetigen
// Ankunftszeit (gegen Kerben in der Krone). cell: Zelle (m), step: Abstand der Ecken in Zellen.
float sgSteepen(ivec2 c, float h0, float n0, float seit, vec2 T, float D, float S, float cell, int step) {
    float w = 1.5 * float(step) * cell / SG_FRONT_SPEED;
    if (n0 < 0.5 || seit < -w || seit > S) return h0;
    float dl = D * (1.0 - smoothstep(0.0, S, max(seit, 0.0)));
    if (dl < cell) return h0;
    vec2 bk = vec2(-T.x, T.y) / cell;   // gegen T in Zellen (Zeilen laufen nach Norden, −z)
    float m = h0;
    for (int k = 1; k <= 4; k++) {
        ivec2 cb = c + ivec2(floor(bk * dl * float(k) * 0.25 + 0.5));
        float nb;
        float hb = surgeHeight(cb, nb);
        if (nb > 0.5) m = max(m, hb);
    }
    return mix(h0, m, smoothstep(-w, 0.3 * w, seit));
}

// Länge des Aufsteilens (m) für eine Welle von Hs m über dem Pegel: 5 Hs, höchstens 240 m, ab 6–16 m; über 45–80 m nicht
// mehr (dort ist die Rechnung schon steil, das Dehnen türmte Kamm auf Kamm zu Zacken, Lituya W4 Probe 6) [G]
float sgSteepenLength(float Hs, float strength) {
    float d = clamp(5.0 * Hs, 0.0, 240.0) * smoothstep(6.0, 16.0, Hs) * strength;
    return d * (1.0 - smoothstep(45.0, 80.0, Hs));
}
