#version 460
// gelaende.frag – render.terrain (P6b): Licht auf dem Gelände. Albedo der Klasse mit Struktur, auf Watt und Sand
// dunkler mit der Nässe aus water.wetAge (Halbwertszeit 2 h [G] über der Restnässe, wie mudflat) oder, seit P10b, mit der
// Sättigung surf.wet aus physics.atmos (nass.w = 1); Sonne und Mond mit der
// Durchlässigkeit der Luft und dem Schatten aus der Sicht je Scheitel, Himmelslicht aus der Himmelsansicht. Lambert.
// Seit P9b (12.10) dazu der Boden aus dem Sediment: Rippel als Relief der Normalen, solange ein Bildpunkt sie auflöst, und
// dunkleres Watt mit dem Schlickanteil [G].
// Seit P10c (F96 A) dazu das Sandfegen: ein Schleier aus Körnern des trockenen Sands, wo aeol.q einen Fluss trägt.
// Ausgabe: Leuchtdichte (cd/m²) mal Vorbelichtung; die Luftperspektive legt der Himmel darüber.
#include "/com/dan/render/terrain/gelaende.glsl"
layout(set = 0, binding = 5, std430) readonly buffer Licht { vec4 himmel; vec4 boden; };
layout(set = 0, binding = 6) uniform sampler2D naesse;
layout(set = 0, binding = 7) uniform sampler2D bodenT;   // je Zelle: Rippelhöhe (m), Rippellänge (m), Richtung (rad; 0 = Ost, gegen den Uhrzeigersinn), Schlickanteil; seit S6d-1 2 · ny Zeilen, oben die Wellenrippel (Höhe, Länge, Richtung, 0)
layout(set = 0, binding = 9) uniform sampler2D flugT;    // seit P10c: je Zelle der Fluss des Flugsands nach Ost und nach Nord (kg/(m s)); 1 × 1: keiner
// seit P12a: der Schatten der Häuser (render.bau legt ihn ab; ohne Häuser ist der Faktor genau 1)
#define BAU_SCHATTEN_BILD 10
#define BAU_SCHATTEN_WERTE 11
#include "/com/dan/core/gpu/bauschatten.glsl"
// seit P12b (F132–F134 A): die Gestaltkarte (Atlas der Kacheln: Art, Wasserstand), ihr Kopf mit der Tafel der Kacheln und
// die Himmelsansicht für die Spiegelung im Wasser der Gräben und Fethinge
layout(set = 0, binding = 12) uniform sampler2D gestaltT;
layout(set = 0, binding = 13, std430) readonly buffer GestaltKopf {
    vec4 gLage;     // Südwestecke des Gitters der Kacheln relativ zum Auge (Ost, Nord), Zelle (m), 1 = an
    ivec4 gN;       // Kacheln nach Ost und Nord, Kacheln je Zeile im Atlas, Bildpunkte je Kachel
    vec4 gInfo;     // Zeit (s, für die Kräuselung), Fuß für das Wasser, Fuß für Belag und Gras (m), Zonen der Salzwiese an (1, seit P13b)
    vec4 gProbe;    // für die Prüfung: Stärke der Kräuselung (1), Stärke der Muster (1); sonst 1, 1
    int gSlot[];    // je Kachel des Gitters ihr Platz im Atlas, −1: keine
};
layout(set = 0, binding = 14) uniform sampler2D gestaltAnsicht;
// seit S6c-2 (Seegang Werkbuch 82, SF93 A): das Bild des Auflaufs (physics.swash, swash_bild.comp) – je Zelle (h, Stand
// relativ zum Ruhewasser, Sekunden seit nass oder −1, versickert oberhalb der Front), danach je Zeile der Spülsaum (m quer)
layout(set = 0, binding = 15, std430) readonly buffer SwashBild { vec4 swb[]; };
layout(push_constant) uniform Push { ivec4 p; };
layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vN;
layout(location = 2) in vec3 vAlb;
layout(location = 3) in float vWatt;
layout(location = 4) in float vSicht;
layout(location = 0) out vec4 farbe;

// Nässe einer Zelle (0 … 1); außerhalb des Gitters und NaN: 0
float nasseZelle(ivec2 c) {
    if (c.x < 0 || c.y < 0 || c.x >= nassN.x || c.y >= nassN.y) return 0.0;
    float t = texelFetch(naesse, c, 0).r;
    if (isnan(t)) return 0.0;
    if (nass.w > 0.5) return clamp(t, 0.0, 1.0);          // seit P10b: surf.wet, die Sättigung selbst
    return nass.x + (1.0 - nass.x) * exp2(-max(t, 0.0) / nass.z);
}

// Seit C2b (Werkbuch HalligHooge 138, A52): bilinear zwischen den vier Zellmitten um den Ort; vorher galt die Zelle am Ort,
// und die Nässe stand in Kacheln auf dem Watt (unter flachem Wasser sichtbar). Außerhalb des Gitters wie bisher 0.
float nasse() {
    if (nassN.z == 0) return 0.0;
    vec2 u = vec2((vRel.x - nassLage.x) / nassLage.z, (-vRel.z - nassLage.y) / nassLage.w);
    ivec2 c = ivec2(floor(u));
    if (c.x < 0 || c.y < 0 || c.x >= nassN.x || c.y >= nassN.y) return 0.0;
    vec2 b = u - 0.5, f = b - floor(b);
    ivec2 o = ivec2(floor(b));
    float a00 = nasseZelle(o), a10 = nasseZelle(o + ivec2(1, 0)), a01 = nasseZelle(o + ivec2(0, 1)), a11 = nasseZelle(o + ivec2(1, 1));
    return mix(mix(a00, a10, f.x), mix(a01, a11, f.x), f.y);
}

// Rippel am Ort (P9b): Neigung der Oberfläche nach Ost und Nord; schlick: der Schlickanteil dort. Jede der vier Zellen um
// den Ort trägt ihr eigenes Muster (Kämme quer zur Richtung, Länge und Höhe der Zelle, Phase an ihrer Mitte verankert,
// leicht geschwungen [G]); dazwischen wird die Neigung bilinear gemischt. fuss: Größe eines Bildpunkts am Boden (m) –
// Rippel, die er nicht auflöst, bleiben weg.
vec2 rippel(float fuss, out float schlick) {
    schlick = 0.0;
    if (bodLage.w < 0.5) return vec2(0.0);
    vec2 u = vec2(vRel.x - bodLage.x, -vRel.z - bodLage.y) / bodLage.z, b = u - 0.5;
    ivec2 c0 = ivec2(floor(b));
    vec2 f = b - vec2(c0), g = vec2(0.0);
    float gew = 0.0, sl = 0.0;
    for (int k = 0; k < 4; k++) {
        ivec2 c = c0 + ivec2(k & 1, k >> 1);
        if (c.x < 0 || c.y < 0 || c.x >= bodN.x || c.y >= bodN.y) continue;
        float w = ((k & 1) == 0 ? 1.0 - f.x : f.x) * ((k >> 1) == 0 ? 1.0 - f.y : f.y);
        vec4 t = texelFetch(bodenT, c, 0);
        gew += w;
        sl += w * t.a;
        // seit S6d-1: wo die Welle die Rippel zuletzt geformt hat (obere Hälfte des Bilds), gelten ihre statt der des Stroms.
        // Wellenrippel: symmetrisch, lange gerade Kämme mit spitzem Kamm (zweite Harmonische 0,2 [G]): z = η/2 (cos φ + 0,2 cos 2φ).
        if (bod.w > 0.5) {
            vec4 wt = texelFetch(bodenT, c + ivec2(0, bodN.y), 0);
            if (wt.x > 0.0 && wt.y > 0.0) {
                float aw = clamp(wt.y / (2.0 * fuss) - 1.0, 0.0, 1.0);
                if (aw <= 0.0) continue;
                vec2 dw = vec2(cos(wt.z), sin(wt.z)), rw = (u - (vec2(c) + 0.5)) * bodLage.z;
                float sw = dot(rw, dw), qw = dot(rw, vec2(-dw.y, dw.x));
                // Kämme leicht gebogen und längs ungleich hoch (L4u: ganz gerade sahen sie wie Kord aus) [G]
                float pw = 6.2831853 * sw / wt.y + 0.8 * sin(6.2831853 * qw / (11.0 * wt.y)) + 0.45 * sin(6.2831853 * (qw + 0.6 * sw) / (31.0 * wt.y) + 1.3)
                         + float(c.x * 7 + c.y * 13);
                float aq = 0.8 + 0.2 * sin(6.2831853 * qw / (17.0 * wt.y) + 0.7);
                g += dw * (-w * aw * aq * 3.14159265 * wt.x / wt.y * (sin(pw) + 0.4 * sin(2.0 * pw)));
                continue;
            }
        }
        float auf = clamp(t.y / (2.0 * fuss) - 1.0, 0.0, 1.0);
        if (t.x <= 0.0 || t.y <= 0.0 || auf <= 0.0) continue;
        vec2 dir = vec2(cos(t.z), sin(t.z)), r = (u - (vec2(c) + 0.5)) * bodLage.z;
        float s = dot(r, dir), q = dot(r, vec2(-dir.y, dir.x));
        float ph = 6.2831853 * s / t.y + 1.3 * sin(6.2831853 * q / (7.0 * t.y)) + float(c.x * 7 + c.y * 13);
        g += dir * (-w * auf * 3.14159265 * t.x / t.y * sin(ph));
    }
    if (gew > 0.0) schlick = sl / gew;
    return g * bod.z;
}

// Sandfegen (P10c, F96 A): Deckung des Schleiers aus fliegenden Körnern über dem Boden (0 … 1). Der Fluss q am Ort
// (bilinear zwischen den Mitten der Zellen) gibt die senkrechte optische Dicke q · flug.x; der Blick durchquert die
// Schicht schräg (durch sinB geteilt). Streifen längs des Winds ziehen mit ihm (Mittel rund 1), solange ein Bildpunkt
// quer zum Wind schmaler ist als sie.
float streu(vec2 v) {
    vec3 q = fract(vec3(v.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}
float rausch(vec2 v) {
    vec2 i = floor(v), f = v - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(streu(i), streu(i + vec2(1.0, 0.0)), f.x), mix(streu(i + vec2(0.0, 1.0)), streu(i + vec2(1.0, 1.0)), f.x), f.y);
}
float schleier(float sinB) {
    if (flugLage.w < 0.5) return 0.0;
    vec2 ort = vec2(vRel.x - flugLage.x, -vRel.z - flugLage.y), u = ort / flugLage.z - 0.5;
    ivec2 c0 = ivec2(floor(u));
    vec2 f = u - vec2(c0), q = vec2(0.0);
    for (int k = 0; k < 4; k++) {
        ivec2 c = c0 + ivec2(k & 1, k >> 1);
        if (c.x < 0 || c.y < 0 || c.x >= flugN.x || c.y >= flugN.y) continue;
        vec2 t = texelFetch(flugT, c, 0).rg;
        if (isnan(t.x) || isnan(t.y)) continue;
        q += ((k & 1) == 0 ? 1.0 - f.x : f.x) * ((k >> 1) == 0 ? 1.0 - f.y : f.y) * t;
    }
    float betrag = length(q);
    if (betrag <= 0.0) return 0.0;
    vec2 dir = q / betrag, quer = vec2(-dir.y, dir.x);
    float s = dot(ort, dir) - flug.w * flug.y, c = dot(ort, quer);
    // Größe eines Bildpunkts quer zum Wind (m); Streifen von 0,6 m bleiben, solange er sie auflöst
    vec2 dx = vec2(dFdx(vRel.x), -dFdx(vRel.z)), dy = vec2(dFdy(vRel.x), -dFdy(vRel.z));
    float fq = max(abs(dot(dx, quer)), abs(dot(dy, quer)));
    float auf = flug.z * clamp(0.6 / (2.0 * max(fq, 1e-6)) - 1.0, 0.0, 1.0);
    float a = rausch(vec2(c / 0.6 + 0.9 * sin(s / 11.0), s / 9.0)), b = rausch(vec2(c / 4.0 + 17.0, s / 40.0 + 5.0));
    float m = mix(1.0, 3.4 * a * a * (0.4 + 1.2 * b), auf);
    return 1.0 - exp(-betrag * flug.x * m / sinB);
}

// Lambert-Kugel: mittlere Leuchtdichte ihrer Scheibe je Bestrahlungsstärke quer zum Strahl, mal π / Albedo; c = Kosinus des
// Phasenwinkels α (Sonne – Korn – Auge; α = 0: die Sonne im Rücken des Betrachters).
float kugel(float c) {
    float al = acos(clamp(c, -1.0, 1.0));
    return 2.0 / 3.0 * (sin(al) + (L_PI - al) * c) / L_PI;
}

vec3 sonne(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}

// ---- Gestalt (P12b) -------------------------------------------------------------------------------------------------
// Art und Wasserstand eines Texels der Karte (i nach Ost, j nach Nord, vom Südwesten des Gitters); (0, 0) außerhalb.
// Die Kachel und ihr Platz im Atlas werden gemerkt: Die vier Texel um einen Ort liegen fast immer in derselben Kachel.
ivec2 gKachel = ivec2(-1);
int gPlatz = -1;
ivec2 gestaltTexel(ivec2 ij) {
    int N = gN.w;
    if (ij.x < 0 || ij.y < 0) return ivec2(0);
    ivec2 t = ij / N;
    if (t.x >= gN.x || t.y >= gN.y) return ivec2(0);
    if (t != gKachel) { gKachel = t; gPlatz = gSlot[t.y * gN.x + t.x]; }
    int s = gPlatz;
    if (s < 0) return ivec2(0);
    ivec2 l = ij - t * N;
    ivec2 a = ivec2((s % gN.z) * N + l.x, (s / gN.z) * N + (N - 1 - l.y));
    return ivec2(round(texelFetch(gestaltT, a, 0).rg * 255.0));
}

float hashG(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}
float rauschG(vec2 v) {
    vec2 i = floor(v), f = v - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hashG(i), hashG(i + vec2(1.0, 0.0)), f.x), mix(hashG(i + vec2(0.0, 1.0)), hashG(i + vec2(1.0, 1.0)), f.x), f.y);
}
// Steine (Deckwerk, Schotter): Zellen nach Voronoi, je Stein eine Helligkeit, dunkle Fugen; Mittel rund 1 [G].
// Muster blenden mit dem Fuß des Bildpunkts zu ihrem Mittel aus: volle Struktur bis zum Fuß s (kleinste Struktur des
// Musters), keine ab 2 s – so flimmern Fugen nicht; das Mittel bleibt (Prüfstein 13). gProbe.y = 0: kein Muster.
float gFuss;
float detail(float s) { return gProbe.y * clamp(2.0 - gFuss / s, 0.0, 1.0); }
float muster(float m, float mittel, float s) { return mix(mittel, m, detail(s)) / mittel; }
float steine(vec2 p, float groesse) {
    vec2 u = p / groesse, i = floor(u);
    float d1 = 9.0, d2 = 9.0, hell = 1.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 c = i + vec2(x, y), m = c + vec2(hashG(c), hashG(c + 17.0));
            float d = length(u - m);
            if (d < d1) { d2 = d1; d1 = d; hell = 0.75 + 0.5 * hashG(c + 3.7); }
            else if (d < d2) d2 = d;
        }
    float fuge = smoothstep(0.02, 0.10, d2 - d1);
    return mix(0.45, hell, fuge) * 1.08;
}
// Albedo eines Wegbelags am Ort p (Ost, Nord) [G]; Muster mit Mittel rund 1.
vec3 belag(int art, vec2 p, vec3 grund) {
    if (art == 20) return vec3(0.075, 0.075, 0.08) * muster(0.9 + 0.2 * rauschG(p * 9.0), 1.0, 0.11);                 // Asphalt
    if (art == 21) {                                                                                                     // Klinker im Läuferverband
        vec2 u = p / vec2(0.2, 0.1);
        float r = floor(u.y), q = fract(u.x + 0.5 * mod(r, 2.0)), fu = fract(u.y);
        float fuge = (q < 0.05 || fu < 0.1) ? 0.55 : 1.0;                                                                // Mittel 1 − 0,145 · 0,45
        float ton = 0.85 + 0.3 * hashG(vec2(floor(u.x + 0.5 * mod(r, 2.0)), r));
        return vec3(0.20, 0.085, 0.06) * muster(fuge, 0.93475, 0.015) * muster(ton, 1.0, 0.1);
    }
    if (art == 22) return vec3(0.24, 0.22, 0.19) * muster(steine(p, 0.04), 1.0, 0.04);                                  // Schotter
    if (art == 23) {                                                                                                     // Betonspur, Platten 2 m
        float fuge = (fract(p.x / 2.0) < 0.01 || fract(p.y / 2.0) < 0.01) ? 0.6 : 1.0;                                 // Mittel 1 − 0,0199 · 0,4
        return vec3(0.30, 0.29, 0.27) * muster(fuge, 0.99204, 0.02) * muster(0.92 + 0.16 * rauschG(p * 3.0), 1.0, 0.33);
    }
    if (art == 24) return grund * vec3(1.15, 1.0, 0.8) * muster(0.8 + 0.4 * rauschG(p * 4.0), 1.0, 0.25);              // Grasweg: ausgetreten
    if (art == 25) return vec3(0.12, 0.10, 0.07) * muster((fract(p.x / 0.15) < 0.08) ? 0.5 : 1.0, 0.96, 0.015);         // Holz, Bohlen 15 cm
    return grund;
}
// Gras auf Fenne, Salzwiese, Deich und Warft: Feinform unter 1 m (Bulten, Halmbüschel), Mittel 1 [G].
float gras(vec2 p, int art) {
    float m = 1.0 + 0.44 * (rauschG(p * 1.7) - 0.5) * detail(0.6) + 0.36 * (rauschG(p * 7.3 + 11.0) - 0.5) * detail(0.14)
            + 0.24 * (rauschG(p * 23.0 + 5.0) - 0.5) * detail(0.043);
    if (art == 2) m *= muster(0.8 + 0.4 * rauschG(p * 0.6 + 40.0), 1.0, 1.7);                                          // Salzwiese fleckiger
    // P13b (F152 A): Zonen der Salzwiese, je Mittel 1 [G]: Pionierzone lückig (Schlick zwischen den Quellerinseln),
    // untere Salzwiese fleckig wie die Salzwiese, obere dicht mit Horsten, Fenne unten feiner
    if (art == 6) m *= muster(0.55 + 0.9 * step(0.45, rauschG(p * 1.3 + 7.0)), 1.06, 2.0);
    if (art == 7) m *= muster(0.8 + 0.4 * rauschG(p * 0.6 + 40.0), 1.0, 1.7);
    if (art == 8) m *= muster(0.85 + 0.3 * rauschG(p * 2.2 + 17.0), 1.0, 0.5);
    if (art == 9) m *= muster(0.9 + 0.2 * rauschG(p * 1.1 + 23.0), 1.0, 0.9);
    return m;
}
// Rauschen mit seiner Ableitung (Wert, d/dx, d/dy), dasselbe Feld wie rauschG.
vec3 rauschGD(vec2 v) {
    vec2 i = floor(v), f = v - i;
    vec2 u = f * f * (3.0 - 2.0 * f), du = 6.0 * f * (1.0 - f);
    float a = hashG(i), b = hashG(i + vec2(1.0, 0.0)), c = hashG(i + vec2(0.0, 1.0)), d = hashG(i + vec2(1.0, 1.0));
    float k = a - b - c + d;
    return vec3(a + (b - a) * u.x + (c - a) * u.y + k * u.x * u.y, du.x * ((b - a) + k * u.y), du.y * ((c - a) + k * u.x));
}
// ---- Auflauf (S6c-2) -------------------------------------------------------------------------------------------------
// Im Rechteck des Lösers: Film (Spiegel), nasser Sand (glänzt, solange er eben abgelaufen ist; dunkel, solange er nass ist;
// trocknet mit der Halbwertszeit der Nässe), Spülsaum an der höchsten Linie. Über SW_NETZ zeichnet das Wasser (netz) den
// Löser; darunter das Gelände den Film [G]. Konstanten [G]:
const float SW_FILM_MIN = 0.0005, SW_FILM_VOLL = 0.003, SW_NETZ = 0.03;   // m: Film sichtbar ab, voll ab; Netz ab
const float SW_GLANZ_TAU = 4.0;       // s: Glanz des eben abgelaufenen Sands
const float SW_F_DUNKEL = 0.005;      // m: versickertes Wasser oberhalb der Front für volle Abdunklung
const float SW_SAUM_BREITE = 0.3;     // m: Breite des Spülsaums
const float SW_GRAS = 0.35;           // Anteil der Abdunklung auf Gras, Klei, Deckwerk (Watt und Sand: 1)
float swF = 0.0, swGlanz = 0.0, swNass = 0.0, swSaum = 0.0;   // Film (0 … 1), Glanz, Nässe, Saum
vec2 swNeig = vec2(0.0);                                        // Neigung des Films nach Ost, Nord
// Werte der Zelle (i, j): (Film, Glanz, Nässe, Stand); außerhalb 0
vec4 swZelle(int i, int j) {
    i = clamp(i, 0, swN.x - 1); j = clamp(j, 0, swN.y - 1);
    vec4 c = swb[j * swN.x + i];
    if (c.y >= 1e28) return vec4(0.0);
    float film = smoothstep(SW_FILM_MIN, SW_FILM_VOLL, c.x);
    float alter = c.z <= -1e29 ? 1e9 : max(0.0, intBitsToFloat(swN.w) - c.z);   // seit S6c-2b: zuletzt nass absolut
    float glanz = max(film, exp(-alter / SW_GLANZ_TAU));
    float nass0 = c.z <= -1e29 ? 0.0 : exp2(-alter / max(nass.z, 1.0));
    float n = max(max(film, glanz), max(clamp(c.w / SW_F_DUNKEL, 0.0, 1.0), nass0));
    return vec4(film, glanz, n, c.y);
}
// Wasser der Zelle (m; Fels 0), seit S6c-2b
float swH(int i, int j) { vec4 c = swb[clamp(j, 0, swN.y - 1) * swN.x + clamp(i, 0, swN.x - 1)]; return c.y >= 1e28 ? 0.0 : c.x; }
// Liest das Bild am Ort (relativ zum Auge); false außerhalb des Rechtecks
bool swashAmOrt() {
    if (swN.z == 0) return false;
    vec2 d = vec2(vRel.x, -vRel.z) - swLage.xy;
    float x = dot(d, swLage.zw), y = dot(d, swAchse.xy), dx = swAchse.z;
    if (x < 0.0 || y < 0.0 || x >= float(swN.x) * dx || y >= float(swN.y) * dx) return false;
    vec2 u = vec2(x, y) / dx - 0.5;
    // seit S6e (Seegang Werkbuch 85, A21): Film, Glanz, Nässe und Wasser als quadratischer B-Spline über die 3 × 3 Zellen um
    // die nächste Mitte wie das Netz (wasser.glsl, swFlaeche); bilinear zeigten die Ränder an 1-m-Zellen Treppen (L4y)
    ivec2 mc = ivec2(floor(u + 0.5));
    vec2 dd = u - vec2(mc);
    vec3 wx = vec3(0.5 * (0.5 - dd.x) * (0.5 - dd.x), 0.75 - dd.x * dd.x, 0.5 * (0.5 + dd.x) * (0.5 + dd.x));
    vec3 wy = vec3(0.5 * (0.5 - dd.y) * (0.5 - dd.y), 0.75 - dd.y * dd.y, 0.5 * (0.5 + dd.y) * (0.5 + dd.y));
    vec3 dwx = vec3(dd.x - 0.5, -2.0 * dd.x, 0.5 + dd.x), dwy = vec3(dd.y - 0.5, -2.0 * dd.y, 0.5 + dd.y);
    vec4 m = vec4(0.0);
    float hb = 0.0, hmin = 1e9, gx = 0.0, gy = 0.0;
    for (int bb = 0; bb < 3; bb++)
        for (int aa = 0; aa < 3; aa++) {
            ivec2 cc = mc + ivec2(aa - 1, bb - 1);
            vec4 z = swZelle(cc.x, cc.y);
            float hz = swH(cc.x, cc.y), w = wx[aa] * wy[bb];
            m += w * z;
            hb += w * hz;
            hmin = min(hmin, hz);
            gx += dwx[aa] * wy[bb] * z.w;
            gy += wx[aa] * dwy[bb] * z.w;
        }
    // seit S6c-2b (Befund L4p: helle Flecken unter tiefem Wasser): Film, Glanz und Saum nur, wo das Netz nicht zeichnet; unter
    // dem Netz spiegelt dessen Fläche, und der Glanz des Grunds ging als Albedo in die Säule (ρ = π L_u / E über 1)
    float ohneNetz = 1.0 - smoothstep(SW_NETZ - 0.01, SW_NETZ, hb);
    swF = m.x * ohneNetz;
    swGlanz = m.y * ohneNetz;
    swNass = m.z;
    // Neigung des Spiegels aus dem Stand der Nachbarn (nur wo alle Wasser tragen), in Ost und Nord; seit S6e aus dem B-Spline
    gx /= dx;
    gy /= dx;
    if (hmin <= 0.0) { gx = 0.0; gy = 0.0; }
    swNeig = clamp(gx, -0.3, 0.3) * swLage.zw + clamp(gy, -0.3, 0.3) * swAchse.xy;
    // Spülsaum: an der höchsten Linie der Zeile, unregelmäßig (Rauschen längs und quer) [G]; seit S6e zwischen den Mitten
    // zweier Zeilen linear (je Zeile fest gab an 1-m-Zellen eine Treppe, L4y)
    int j0 = clamp(int(floor(u.y)), 0, swN.y - 1), j1 = min(j0 + 1, swN.y - 1);
    float fj = clamp(u.y - float(j0), 0.0, 1.0);
    int k0 = floatBitsToInt(swb[swN.x * swN.y + j0].x), k1 = floatBitsToInt(swb[swN.x * swN.y + j1].x);   // seit S6c-2b: Index der Zelle + 1 (0: keine)
    int ks = max(k0, k1);
    float xs = k0 > 0 && k1 > 0 ? (mix(float(k0), float(k1), fj) - 0.5) * dx : (float(ks) - 0.5) * dx;
    if (ks > 0) {
        float wob = 0.12 * (rausch(vec2(y * 0.9, 3.1)) - 0.5) + 0.05 * (rausch(vec2(y * 4.0, 7.7)) - 0.5);
        float d0 = abs(x - xs - wob);
        swSaum = (1.0 - smoothstep(0.25 * SW_SAUM_BREITE, 0.5 * SW_SAUM_BREITE, d0)) * (0.55 + 0.45 * rausch(vec2(x * 6.0, y * 6.0))) * ohneNetz;
    }
    return true;
}

// Kräuselung auf kleinem Wasser [G]: Neigung aus zwei ziehenden Rauschfeldern (Ableitung geschlossen), Stärke rund 0,04.
vec2 kraeusel(vec2 p, float t) {
    vec3 a = rauschGD(p * 1.3 + vec2(t * 0.35, t * 0.2)), b = rauschGD(p * 4.1 - vec2(t * 0.6, -t * 0.4));
    return 0.006 * (1.3 * a.yz + 0.5 * 4.1 * b.yz);
}

void main() {
    float fuss = max(length(vec2(dFdx(vRel.x), dFdx(vRel.z))), length(vec2(dFdy(vRel.x), dFdy(vRel.z))));
    if (verdeckt(p.x, vRel.xz)) discard;
    vec3 n = normalize(vN);
    float h = max(0.5, vRel.y + auge.x), schlick, watt = clamp(vWatt, 0.0, 1.0);
    vec2 gr = rippel(fuss, schlick) * watt;
    n = normalize(n + vec3(-gr.x, 0.0, gr.y));
    // seit S6c-2: im Rechteck des Auflaufs die Nässe aus dem Löser (die stärkere von beiden; auf Gras, Klei, Deckwerk schwächer)
    bool imSwash = swashAmOrt();
    float nasseAll = nasse() * watt;
    if (imSwash) nasseAll = max(nasseAll, swNass * mix(SW_GRAS, 1.0, watt));
    vec3 a = vAlb * (1.0 - nass.y * nasseAll) * (1.0 - bod.x * clamp(schlick / max(bod.y, 1e-6), 0.0, 1.0) * watt);
    // Seit P12a der Schatten der Häuser auf Sonne oder Mond; ohne Häuser (bauInfo.x = 0) genau der Ausdruck wie vorher
    float bs = bauSchatten(vRel, normalize(vN)), bsS = bauInfo.y < 0.5 ? bs : 1.0, bsM = bauInfo.y > 0.5 ? bs : 1.0;
    vec3 E;
    if (bauInfo.x > 0.5)
        E = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * max(0.0, dot(n, hw.sonne.xyz)) * (vSicht * bsS)
          + sonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h) * max(0.0, dot(n, hw.mond.xyz)) * bsM
          + himmel.rgb * (0.5 + 0.5 * n.y) + boden.rgb * (0.5 - 0.5 * n.y);
    else
        E = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * max(0.0, dot(n, hw.sonne.xyz)) * vSicht
          + sonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h) * max(0.0, dot(n, hw.mond.xyz))
          + himmel.rgb * (0.5 + 0.5 * n.y) + boden.rgb * (0.5 - 0.5 * n.y);
    vec3 L = a / L_PI * E;
    // ---- seit P12b: Gestalt des Geländes (ohne Karte oder ausgeschaltet bleibt L, wie es ist)
    gFuss = fuss;
    float grundFaktor = 1.0;
    if (gLage.w > 0.5 && fuss < 2.0 * gInfo.y) {
        vec2 ort = vec2(vRel.x, -vRel.z);                                       // Ost, Nord relativ zum Auge
        vec2 u = (ort - gLage.xy) / gLage.z - 0.5;
        ivec2 i0 = ivec2(floor(u));
        vec2 f = u - vec2(i0);
        float hWelt = vRel.y + auge.x + dot(vRel.xz, vRel.xz) * auge.y;          // Höhe des Geländes (m NHN), ohne die Senkung der Erdkrümmung
        float wW = 0.0, wD = 0.0, wB = 0.0, wG = 0.0, bestB = 0.0;
        int belagArt = 0, grasArt = 0;
        for (int k = 0; k < 4; k++) {
            ivec2 o = ivec2(k & 1, k >> 1);
            ivec2 t = gestaltTexel(i0 + o);
            float w = (o.x == 0 ? 1.0 - f.x : f.x) * (o.y == 0 ? 1.0 - f.y : f.y);
            int art = t.x;
            if (gInfo.w < 0.5 && (art >= 6 && art <= 9 || art == 13)) art = art == 9 ? 1 : 2;      // P13b: Salzwiese aus, die Art ohne Zone
            if (art == 13) art = 0;                                                  // F158 A: Schlick unter der Pionierzone, das Gelände wie es ist
            if (art >= 10 && art <= 12) {
                float stand = float(t.y) / 50.0 - 1.0;
                if (art == 12 || hWelt <= stand + 0.05) wW += w;                    // F134 A: im Graben Wasser, wo das Gelände unter dem Stand liegt
                else wG += w;                                                   // Böschung: Gras
            } else if (art >= 20) { wB += w; if (w > bestB) { bestB = w; belagArt = art; } }
            else if (art == 5) wD += w;
            else if (art >= 1) { wG += w; grasArt = art; }
        }
        vec2 welt = ort - gLage.xy;                                              // Ort im Gitter (m), fest in der Welt
        float fB = clamp(2.0 - fuss / gInfo.z, 0.0, 1.0), fW = clamp(2.0 - fuss / gInfo.y, 0.0, 1.0);
        vec3 aG = a;
        if (fB > 0.0) {
            if (wG > 0.0) aG = mix(aG, a * gras(welt, grasArt), wG * fB);
            if (wD > 0.0) aG = mix(aG, a * muster(steine(welt, 0.35), 1.0, 0.35), wD * fB);
            if (wB > 0.0) aG = mix(aG, belag(belagArt, welt, a), wB * fB);
            if (wG + wD + wB > 0.0) L = aG / L_PI * E;
        }
        if (wW > 0.0) {
            // Wasser: Spiegel mit Kräuselung (Fresnel, n = 1,33), Himmel aus der Ansicht, Glanz der Sonne (Beckmann, m² 0,006 [G]),
            // dunkler, trüber Körper (Albedo 0,03 [G]); Schatten der Häuser auf dem Glanz
            vec2 g = kraeusel(welt, gInfo.x) * gProbe.x;
            vec3 nW = normalize(vec3(-g.x, 1.0, g.y));
            vec3 v = -normalize(vRel), r = reflect(-v, nW);
            float cv = clamp(dot(nW, v), 1e-3, 1.0), F = 0.02 + 0.98 * pow(1.0 - cv, 5.0);
            vec3 Lh = ansicht(gestaltAnsicht, normalize(vec3(r.x, max(r.y, 0.005), r.z)), hw.sonne.w);
            vec3 l = hw.sonne.xyz, hv = normalize(l + v);
            float nh = clamp(dot(nW, hv), 1e-3, 1.0), m2 = 0.006, t2 = (1.0 - nh * nh) / (nh * nh);
            float D = exp(-t2 / m2) / (L_PI * m2 * nh * nh * nh * nh);
            float bsG = bauInfo.y < 0.5 ? bs : 1.0;
            vec3 Es = sonne(l, hw.sonneE.rgb * hw.farbe.rgb, h) * vSicht * bsG;
            vec3 glanz = Es * F * D / (4.0 * cv);
            vec3 Ew = himmel.rgb + Es * max(0.0, l.y);
            vec3 Lw = F * Lh + (1.0 - F) * vec3(0.03, 0.035, 0.025) / L_PI * Ew + glanz;
            vec3 Lgrund = L;
            L = mix(L, Lw, wW * fW);
            // F134 A: Steht hydro höher, gilt hydro. Das Wasserbild schätzt die Albedo des Grunds aus diesem Bild; unter der
            // Flut ist der Grund das Gelände, nicht der Spiegel des Gestaltwassers: Faktor zum Grund im Alpha (sonst 1)
            float yL = dot(L, vec3(0.2126, 0.7152, 0.0722));
            grundFaktor = yL > 0.0 ? clamp(dot(Lgrund, vec3(0.2126, 0.7152, 0.0722)) / yL, 0.0, 1000.0) : 1.0;
        }
    }
    // ---- seit S6c-2: Film, Glanz und Spülsaum im Rechteck des Auflaufs. Film: Spiegel wie das Wasser der Gräben (Fresnel,
    // n = 1,33, Himmel aus der Ansicht, Glanz der Sonne Beckmann m² 0,006) über dem nassen Grund; eben abgelaufener Sand:
    // derselbe Spiegel mit rauerer Fläche (m² 0,03) und dem Anteil swGlanz [G]; Saum: Schaum von gestern, Albedo 0,55 [G].
    if (imSwash && (swF > 0.0 || swGlanz > 0.0 || swSaum > 0.0)) {
        // seit S6c-2b (Befund L4p: die nasse Böschung als waagerechter Spiegel, hell wie der Himmel, mit Zähnen): der Film folgt
        // dem Grund – Normale des Geländes, dazu die Kräuselung; vorher waagerecht mit der Neigung des Stands (auf ± 0,3
        // begrenzt und 0 neben trockenen Zellen), auf einer Böschung 1:2,5 also streifend und fast ganz Himmel
        vec2 g = kraeusel(vec2(vRel.x, -vRel.z), gInfo.x) * 0.3;
        vec3 nW = normalize(n + vec3(-g.x, 0.0, g.y));
        vec3 v = -normalize(vRel), r = reflect(-v, nW);
        float cv = clamp(dot(nW, v), 1e-3, 1.0), F = 0.02 + 0.98 * pow(1.0 - cv, 5.0);
        vec3 Lh = ansicht(gestaltAnsicht, normalize(vec3(r.x, max(r.y, 0.005), r.z)), hw.sonne.w);
        vec3 l = hw.sonne.xyz, hv = normalize(l + v);
        float nh = clamp(dot(nW, hv), 1e-3, 1.0), t2 = (1.0 - nh * nh) / (nh * nh);
        float bsG = bauInfo.y < 0.5 ? bs : 1.0;
        vec3 Es = sonne(l, hw.sonneE.rgb * hw.farbe.rgb, h) * vSicht * bsG;
        float mF = 0.006, mG = 0.03;
        float DF = exp(-t2 / mF) / (L_PI * mF * nh * nh * nh * nh), DG = exp(-t2 / mG) / (L_PI * mG * nh * nh * nh * nh);
        vec3 spiegelF = F * Lh + Es * F * DF / (4.0 * cv), spiegelG = F * Lh + Es * F * DG / (4.0 * cv);
        // Film: Spiegel über dem Grund, der durch das dünne Wasser scheint; Glanz: auf den Grund dazu
        L = L * (1.0 - F * swF) + spiegelF * swF + spiegelG * (swGlanz * (1.0 - swF));
        if (swSaum > 0.0) {
            vec3 Es0 = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * max(0.0, dot(n, hw.sonne.xyz)) * vSicht + himmel.rgb * (0.5 + 0.5 * n.y);
            L = mix(L, vec3(0.55, 0.54, 0.50) / L_PI * Es0, swSaum * (1.0 - swF));
        }
    }
    // Sandfegen: Körner in der Luft sind Kugeln im Licht, keine waagerechte Fläche. Eine Lambert-Kugel zeigt im Mittel über
    // ihre Scheibe 2/3 der Leuchtdichte einer zur Sonne gewandten Fläche, mal der Phase (sin α + (π − α) cos α) / π; vom
    // Himmel und vom Boden bekommt sie je die Hälfte. Bei tiefer Sonne leuchtet der Schleier darum heller als der Boden.
    vec3 blick = normalize(vRel);
    float deck = schleier(max(abs(dot(blick, normalize(vN))), 0.01));
    if (deck > 0.0) {
        vec3 Ek = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * kugel(dot(hw.sonne.xyz, -blick)) * (bauInfo.x > 0.5 ? vSicht * bsS : vSicht)
                + sonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h) * (bauInfo.x > 0.5 ? kugel(dot(hw.mond.xyz, -blick)) * bsM : kugel(dot(hw.mond.xyz, -blick)))
                + 0.5 * (himmel.rgb + boden.rgb);
        L = mix(L, flugFarbe.rgb / L_PI * Ek, deck);
    }
    farbe = vec4(min(L * hw.sonneE.w, vec3(60000.0)), grundFaktor);
}
