#version 460
// haus.frag – render.bau (P12a, F126 A): Licht auf den Häusern wie auf dem Gelände (Lambert; Sonne und Mond mit der
// Durchlässigkeit der Luft und dem Schatten der Häuser, Himmel und Boden aus den Kugelflächenfunktionen von licht.comp).
// Material nach Daniel (19.11.2026): rote Backsteinwände, Reetdächer; Fenster mit weißen Rahmen und Sprossen, Tür,
// Licht in den Fenstern nach der Uhrzeit (Anteil aus dem Kopf). Gestaltet [G]: Maße der Fenster und Steine, Fugen,
// Halme, Moos, First. Alle Muster haben das Mittel 1 der Albedo (Backstein mit Fugen: das Mittel ist die Albedo der Wand),
// und sie blenden zum Mittel über, sobald ein Bildpunkt sie nicht mehr auflöst.
// Ausgabe: Leuchtdichte (cd/m²) mal Vorbelichtung; die Luftperspektive legt der Himmel darüber.
#include "/com/dan/render/bau/bau.glsl"
#include "/com/dan/render/bau/kugel.glsl"
layout(set = 0, binding = 5, std430) readonly buffer Licht { vec4 sh[9]; };
layout(set = 0, binding = 6) uniform sampler2D ansichtT;
#define BAU_SCHATTEN_BILD 7
#define BAU_SCHATTEN_WERTE 8
#include "/com/dan/core/gpu/bauschatten.glsl"
layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vN;
layout(location = 2) in vec3 vUv;
layout(location = 3) flat in ivec2 vArt;
layout(location = 0) out vec4 farbe;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec3 sonne(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}

// Bestrahlung aus Himmel und Boden auf eine Fläche mit Normale n.
vec3 himmelE(vec3 n) {
    float y[9];
    kugelY(n, y);
    const float A[9] = float[](3.14159265, 2.0943951, 2.0943951, 2.0943951, 0.78539816, 0.78539816, 0.78539816, 0.78539816, 0.78539816);
    vec3 e = vec3(0.0);
    for (int k = 0; k < 9; k++) e += A[k] * sh[k].rgb * y[k];
    return max(e, vec3(0.0));
}

// Glätte eines Musters: 1, solange ein Bildpunkt (fuss, m) die Größe g auflöst, darunter 0.
float aufgeloest(float g, float fuss) { return clamp(g / (2.0 * max(fuss, 1e-5)) - 1.0, 0.0, 1.0); }

// Fenster und Tür auf einer Wand (u, v, w wie core.szene.Bauten). Ergebnis: x 0 Wand, 1 Rahmen, 2 Glas, 3 Tür;
// y Nummer des Fensters (für das Licht).
vec2 oeffnung(vec3 uv, Haus hs, vec3 n, float fuss) {
    float u = uv.x, v = uv.y, w = uv.z;
    float traufe = hs.info.x - hs.wand.w, first = hs.info.y - hs.wand.w;
    if (w < 2.6) return vec2(0.0);
    // Erdgeschoss: Fenster 0,95 × 1,20 m, Brüstung 0,85 m [G], Abstand bis 2,4 m
    float hoch = traufe;                    // Höhe der Wand unter der Traufe (über dem Boden)
    float wb = 0.95, wh = 1.20, br = 0.85, rahmen = 0.07, sprosse = 0.035;
    int anzahl = int(floor((w - 1.0) / 2.4)) + 1;
    float teil = w / float(anzahl);
    float k = floor((u + 0.5 * w) / teil);
    float mitte = (k + 0.5) * teil - 0.5 * w, du = u - mitte;
    float seed = hash(vec2(float(int(k)) + 17.0 * hs.info.z, floor(n.x * 3.0 + 7.0) * 13.0 + floor(n.z * 3.0 + 7.0)));
    // Tür: in der Mitte einer langen Wand nach Süden oder Osten, unter dem Zwerchgiebel (F129 A) [G]; die Fenster lassen
    // ihr Platz
    bool tuerWand = w >= 6.0 && (n.z > 0.6 || n.x > 0.6);
    if (tuerWand && abs(u) < 0.55 && v < 2.0 && v > -0.2) {
        if (abs(u) > 0.48 || v > 1.93) return vec2(1.0, k);
        return vec2(3.0, k);
    }
    if (!(tuerWand && abs(mitte) < 1.1) && hoch >= 2.2 && abs(du) < 0.5 * wb && v > br && v < br + wh) {
        float x = abs(du), y = v - br;
        if (x > 0.5 * wb - rahmen || y < rahmen || y > wh - rahmen) return vec2(1.0, k);
        // Sprossen: ein Kreuz, darüber eine zweite Querteilung (sechs Scheiben) [G]
        float s = sprosse * aufgeloest(sprosse, fuss);
        if (x < 0.5 * s || abs(y - 0.5 * wh) < 0.5 * s || abs(y - 0.75 * wh) < 0.5 * s) return vec2(1.0, k);
        return vec2(2.0, k);
    }
    // Giebel: ein Fenster mittig über der Traufe, wenn der Giebel hoch genug ist [G]
    if (first - traufe > 3.2 && abs(u) < 0.42 && v > traufe + 0.7 && v < traufe + 1.7) {
        float x = abs(u), y = v - traufe - 0.7;
        if (x > 0.42 - rahmen || y < rahmen || y > 1.0 - rahmen || x < 0.5 * sprosse || abs(y - 0.5) < 0.5 * sprosse) return vec2(1.0, 100.0);
        return vec2(2.0, 100.0);
    }
    return vec2(0.0);
}

void main() {
    Haus hs = haus[vArt.y];
    vec3 n = normalize(vN);
    vec3 v = normalize(-vRel);
    if (dot(n, v) < 0.0) n = -n;                            // Rückseite (zum Beispiel unter dem Dach)
    float fuss = max(length(dFdx(vRel)), length(dFdy(vRel)));
    float h = max(0.5, vRel.y + augeLo.w);
    vec3 a, Le = vec3(0.0);
    float glas = 0.0;
    if (vArt.x == 0) {
        // ---- Wand: Backstein im Läuferverband (NF 240 × 71 mm, Fuge 10 mm [G]); Mittel = Albedo der Wand
        vec3 mittel = hs.wand.rgb, fuge = vec3(0.20, 0.19, 0.17);
        const float FUGE = 1.0 - (0.24 * 0.073) / (0.25 * 0.0833);
        vec3 stein = max((mittel - FUGE * fuge) / (1.0 - FUGE), vec3(0.005));
        float sv = vUv.y / 0.0833, reihe = floor(sv), su = vUv.x / 0.25 + 0.5 * mod(reihe, 2.0), spalte = floor(su);
        float fx = fract(su) * 0.25, fy = fract(sv) * 0.0833;
        bool istFuge = fx < 0.010 || fy < 0.0103;
        float streu = 0.82 + 0.36 * hash(vec2(spalte, reihe) + hs.info.z * 91.0);
        vec3 muster = istFuge ? fuge : stein * streu;
        float auf = aufgeloest(0.0833, fuss);
        a = mix(mittel, muster, auf);
        // Sockel: unter dem Boden dunkler (Teer, Nässe) [G]
        if (vUv.y < 0.25) a *= 0.7;
        vec2 o = oeffnung(vUv, hs, n, fuss);
        if (o.x > 0.5) {
            if (o.x < 1.5) a = vec3(0.78, 0.78, 0.76);                                  // Rahmen, weiß gestrichen
            else if (o.x < 2.5) {                                                        // Glas
                a = vec3(0.025);
                glas = 1.0;
                float an = hash(vec2(o.y * 7.31 + hs.info.z * 113.0, floor(n.x * 5.0) + 3.0 * floor(n.z * 5.0)));
                if (fenster.w > 0.5 && an < fenster.x) Le = fenster.y * vec3(1.0, 0.72, 0.42) * (0.6 + 0.8 * hash(vec2(an, 3.7)));
            } else a = vec3(0.045, 0.085, 0.06);                                         // Tür, dunkelgrün [G]
        }
    } else if (vArt.x >= 2) {
        // ---- seit P12b: Körper der Gestalt [G]. Unter der Linie des mittleren Hochwassers (1,4 m NHN [G]) nass und dunkler,
        // darunter bis 0,9 m mit Algen grün
        float yW = vRel.y + augeLo.w + dot(vRel.xz, vRel.xz) * augeHi.w;
        vec2 p = vec2(vUv.x, vUv.y);
        if (vArt.x == 2) a = vec3(0.10, 0.085, 0.065) * (0.8 + 0.4 * hash(vec2(floor(p.x / 0.02), floor(yW / 0.6))));      // Holz: Maserung längs
        else if (vArt.x == 3) {                                                                                            // Stein: Schüttung
            vec2 u = p / 0.35, i = floor(u);
            float d1 = 9.0, d2 = 9.0, hell = 1.0;
            for (int y = -1; y <= 1; y++)
                for (int x = -1; x <= 1; x++) {
                    vec2 c = i + vec2(x, y), m = c + vec2(hash(c), hash(c + 17.0));
                    float d = length(u - m);
                    if (d < d1) { d2 = d1; d1 = d; hell = 0.7 + 0.6 * hash(c + 3.7); } else if (d < d2) d2 = d;
                }
            a = vec3(0.17, 0.16, 0.15) * mix(0.4, hell, smoothstep(0.02, 0.1, d2 - d1));
        } else if (vArt.x == 4) a = vec3(0.30, 0.29, 0.27) * (0.9 + 0.2 * hash(floor(p / 0.3)));                           // Beton
        else a = vec3(0.065, 0.055, 0.04) * (0.6 + 0.8 * hash(floor(p / 0.05)));                                           // Buschwerk
        if (yW < 1.4) a *= 0.55;
        if (yW < 0.9) a = mix(a, vec3(0.03, 0.05, 0.02), 0.5);
    } else {
        int art = int(hs.dach.w + 0.5);
        vec3 alb = hs.dach.rgb;
        if (art == 0) {
            // ---- Reet: Halme die Falllinie hinab (u quer, v längs), Bündel 4–6 cm, Moos auf der Nordseite, dunkler First [G]
            float b = hash(vec2(floor(vUv.x / 0.05), floor(vUv.y / 0.9 + hash(vec2(floor(vUv.x / 0.05), 1.0)))));
            float halm = hash(vec2(floor(vUv.x / 0.008), floor(vUv.y / 0.35)));
            float m = 0.85 + 0.2 * b + 0.1 * (halm - 0.5) * aufgeloest(0.008, fuss);
            m = mix(1.0, m, aufgeloest(0.05, fuss));
            float nord = clamp(-n.z / max(length(n.xz), 1e-3), 0.0, 1.0) * clamp(1.0 - n.y, 0.0, 1.0);
            vec3 moos = vec3(0.055, 0.075, 0.035);
            a = mix(alb * m, moos, 0.18 * nord);
            if (hs.info.y - (vRel.y + augeLo.w + dot(vRel.xz, vRel.xz) * augeHi.w) < 0.35) a = vec3(0.055, 0.055, 0.05);
        } else if (art == 1) {
            // ---- Ziegel: Reihen von 0,33 m die Falllinie hinab, Schatten unter jeder Reihe [G]
            float r = fract(vUv.y / 0.33);
            float m = mix(1.0, (r > 0.88 ? 0.55 : 1.06) * (0.9 + 0.2 * hash(vec2(floor(vUv.x / 0.21 + 0.5 * floor(vUv.y / 0.33)), floor(vUv.y / 0.33)))), aufgeloest(0.33, fuss));
            a = alb * m;
        } else a = alb;
    }
    // Licht: Sonne und Mond (mit dem Schatten der Häuser), Himmel und Boden
    float bs = bauSchatten(vRel, n);
    float bsS = bauInfo.y < 0.5 ? bs : 1.0, bsM = bauInfo.y > 0.5 ? bs : 1.0;
    vec3 E = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * max(0.0, dot(n, hw.sonne.xyz)) * bsS
           + sonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h) * max(0.0, dot(n, hw.mond.xyz)) * bsM
           + himmelE(n);
    vec3 L = a / L_PI * E + Le;
    if (glas > 0.5) {
        // Spiegelung im Glas: Fresnel nach Schlick (n = 1,52), der Himmel in der gespiegelten Richtung
        vec3 r = reflect(-v, n);
        float c = clamp(dot(n, v), 0.0, 1.0), F = 0.043 + (1.0 - 0.043) * pow(1.0 - c, 5.0);
        L += F * ansicht(ansichtT, normalize(vec3(r.x, max(r.y, 0.01), r.z)), hw.sonne.w);
    }
    farbe = vec4(min(L * hw.sonneE.w, vec3(60000.0)), 1.0);
}
