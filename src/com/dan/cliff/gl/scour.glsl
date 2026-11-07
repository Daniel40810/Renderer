// scour.glsl — com.dan.cliff (aus LituyaBay1958, Werkbuch Welle W3b „Hänge mit Textur“, W3c, W5): der Hang, den eine
// Welle, eine Lawine oder ein Felssturz abgeschürft hat [G]. Großform, die auch aus Kilometern trägt: Flecken von Fels
// und Schutt (150 m, 45 m), Rinnen in der Falllinie (quer 6 und 22 m, längs 55 und 170 m), Bänke im Fels quer zum Hang
// (rund 9 m), Blöcke von 4 m und 1,2 m mit dunklen Fugen; frisch hell, verwittert dunkler; nass dunkler, in den Rinnen
// zuerst; dazu geschälte Stämme kreuz und quer in der Falllinie, fern als Teppich aus Holz (Fotos 1958).
//   CliffScour cliffScour(p, n, px, pyx, fels, kies, frisch)   Farbe, geneigte Normale, Rinne, Anteil Fels
//   vec3 cliffScourWet(c, rinne, nass)                          Nässe
//   vec3 cliffScourLogs(c, p, n, px, frisch, dichte)            Stämme
// p: Punkt (m), n: Normale des Geländes, px: Bildpunkt auf der Fläche (m), pyx: Bildpunkt in der Höhe (m). Ohne Uniforms.
struct CliffScour { vec3 color; vec3 normal; float gully; float rock; };

float cliffScHash(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float cliffScNoise(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(cliffScHash(i), cliffScHash(i + vec2(1, 0)), f.x), mix(cliffScHash(i + vec2(0, 1)), cliffScHash(i + vec2(1, 1)), f.x), f.y);
}
// Rauschen mit Periode p (m), zum Mittel 0,5, sobald ein Bildpunkt (px) mehr als ein Drittel der Periode deckt; ganz
// gemittelt ohne Abruf
float cliffScNoiseF(vec2 q, float p, float px) { float w = smoothstep(0.25, 0.6, px / p); return w >= 1.0 ? 0.5 : mix(cliffScNoise(q / p), 0.5, w); }
// gestrecktes Rauschen: quer Periode pa, längs pb (m), gefiltert nach der kurzen Periode
float cliffScGully(vec2 q, float pa, float pb, float px) { float w = smoothstep(0.25, 0.6, px / pa); return w >= 1.0 ? 0.5 : mix(cliffScNoise(vec2(q.x / pa, q.y / pb)), 0.5, w); }

// W3c: Zellen (Voronoi) für Blöcke im Grund: Helligkeit, Fuge (Abstand zur nächsten Zelle), Abstand
vec3 cliffScBlocks(vec2 q, float g) {
    vec2 i = floor(q / g), f = q / g - i;
    float d1 = 9.0, d2 = 9.0, id = 0.0;
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++) {
            vec2 c = vec2(x, y), o = vec2(cliffScHash(i + c), cliffScHash(i + c + 17.3));
            float d = length(c + o - f);
            if (d < d1) { d2 = d1; d1 = d; id = cliffScHash(i + c + 5.1); } else if (d < d2) d2 = d;
        }
    return vec3(id, d2 - d1, d1);
}

/*
 * Ein Hang ohne Wald (abgeschürftes Band, Abrissfläche; Lituya W3b, W3c) [G]. fels: Anteil anstehender Fels (sonst
 * Schutt, Mineralboden), kies: Ufer, frisch: 1 frisch gebrochen und hell, 0 verwittert. Nässe danach mit cliffScourWet.
 * Großform, die auch in Kilometern trägt: Flecken von Fels und Schutt (150 m, 45 m), Rinnen in der Falllinie (quer 6 und
 * 22 m, längs 55 und 170 m), im Fels Bänke quer zum Hang (rund 9 m); Rinnen dunkler und feucht, Rippen heller, dazu eine
 * leichte Neigung der Normale über die Rinnen (Relief im flachen Licht). Ergebnis: Farbe (Albedo), Normale, Rinne
 * (0 Rinne, 1 Rippe) und Anteil Fels; ein Wirt kann in der Nähe das Muster seiner Texturen auf die Farbe ziehen.
 */
CliffScour cliffScour(vec3 p, vec3 n, float px, float pyx, float fels, float kies, float frisch) {
    CliffScour s;
    vec3 gn = n;
    vec2 fall = length(gn.xz) > 1e-3 ? normalize(gn.xz) : vec2(0.0, 1.0);
    vec2 quer = vec2(-fall.y, fall.x);
    vec2 q = vec2(dot(p.xz, quer), dot(p.xz, fall));
    float fl = cliffScNoiseF(p.xz + 91.0, 150.0, px) * 0.6 + cliffScNoiseF(p.xz + 17.0, 45.0, px) * 0.4;
    float f = clamp(fels + (fl - 0.5) * 0.9, 0.0, 1.0);
    float r1 = cliffScGully(q + 5.0, 6.0, 55.0, px), r2 = cliffScGully(q + 31.0, 22.0, 170.0, px);
    float rin = r1 * 0.45 + r2 * 0.55;                      // 0 Rinne, 1 Rippe
    float r1b = cliffScGully(q + vec2(7.0, 5.0), 6.0, 55.0, px), r2b = cliffScGully(q + vec2(33.0, 31.0), 22.0, 170.0, px);
    float drin = (r1b * 0.45 + r2b * 0.55 - rin) * 0.5;      // Steigung quer (über 2 m)
    float bank = fract((p.y + 7.0 * cliffScNoiseF(p.xz + 3.0, 70.0, px)) / 9.0);
    float bk = mix(smoothstep(0.62, 0.92, bank) - smoothstep(0.92, 1.0, bank), 0.3, smoothstep(0.2, 0.5, pyx / 9.0));
    // W3c: wärmer und mit mehr Unterschied (vorher grau in grau: im Dämmerlicht nicht vom Wasser zu unterscheiden) [G]
    vec3 felsF = mix(vec3(0.25, 0.245, 0.225), vec3(0.52, 0.50, 0.46), frisch);
    vec3 schuttF = mix(vec3(0.25, 0.19, 0.13), vec3(0.42, 0.33, 0.22), frisch);
    vec3 kiesF = vec3(0.33, 0.31, 0.27);
    vec3 c = mix(schuttF, felsF, f);
    c *= mix(1.0, 0.82 + 0.36 * bk, f);                      // Bänke im Fels: Stirn hell, Absatz mit Schutt dunkler
    c *= 0.5 + 0.85 * rin;                                   // Rinne dunkel, Rippe hell
    c *= 0.85 + 0.3 * cliffScNoiseF(p.xz + 55.0, 12.0, px);
    c = mix(c, kiesF * (0.85 + 0.3 * cliffScNoiseF(p.xz, 7.0, px)), kies);
    // W3c: Blöcke und Steine (4 m und 1,2 m) mit dunklen Fugen: rauer Grund statt glatter Flecken, die wie Spiegelungen
    // aussahen; in der Ferne gemittelt [G]
    vec3 b4 = cliffScBlocks(p.xz + 3.7, 4.0), b1 = cliffScBlocks(p.xz + 19.0, 1.2);
    float k4 = 1.0 - smoothstep(0.5, 1.6, px / 4.0 * 3.0), k1 = 1.0 - smoothstep(0.5, 1.6, px / 1.2 * 3.0);
    c *= mix(1.0, 0.72 + 0.56 * b4.x, k4 * 0.8) * mix(1.0, mix(0.55, 1.0, smoothstep(0.02, 0.14, b4.y)), k4);
    c *= mix(1.0, (0.8 + 0.4 * b1.x) * mix(0.7, 1.0, smoothstep(0.02, 0.16, b1.y)), k1);
    c *= mix(1.0, 0.86, smoothstep(1.6, 6.0, px) * (1.0 - smoothstep(6.0, 30.0, px)));   // Mittel der Fugen in der Ferne
    s.normal = normalize(gn + vec3(quer.x, 0.0, quer.y) * (-drin) * 1.6 * (1.0 - smoothstep(4.0, 20.0, px)));
    s.color = c;
    s.gully = rin;
    s.rock = f;
    return s;
}
// Nässe auf dem abgeschürften Hang: dunkler, in den Rinnen zuerst, Schlamm bräunlich [G]
vec3 cliffScourWet(vec3 c, float rin, float nass) {
    c *= 1.0 - nass * (0.14 + 0.16 * (1.0 - rin));
    c = mix(c, c * vec3(1.06, 0.97, 0.86), nass * 0.6);
    return c;
}


/*
 * Stämme auf dem abgeschürften Hang (Lituya W3c) [G]. Die Welle brach den Wald und schälte die Stämme (Fotos 1958:
 * helle, entrindete Stämme kreuz und quer). Kurze helle Striche, grob in der Falllinie, mit Schatten daneben; in der Ferne
 * als leichte Aufhellung gemittelt. frisch: 1 im Jahr 1958, mit den Jahren grau und weniger.
 */
vec3 cliffScourLogs(vec3 c, vec3 p, vec3 n, float px, float frisch, float dichte) {
    vec2 fall = length(n.xz) > 1e-3 ? normalize(n.xz) : vec2(0.0, 1.0);
    float i = floor(dot(p.xz, vec2(0.7, 0.3)) / 11.0);
    float wink = (cliffScHash(vec2(i, 3.1)) - 0.5) * 1.4;                    // Streuung der Lage um die Falllinie
    vec2 a = vec2(cos(wink) * fall.x - sin(wink) * fall.y, sin(wink) * fall.x + cos(wink) * fall.y);
    vec2 b = vec2(-a.y, a.x);
    vec2 q = vec2(dot(p.xz, a), dot(p.xz, b));
    vec2 zelle = floor(vec2(q.x / 22.0, q.y / 3.0));
    float da = cliffScHash(zelle + 7.0), lage = cliffScHash(zelle + 13.0);
    vec2 f = vec2(q.x / 22.0, q.y / 3.0) - zelle;
    float schwelle = 1.0 - 0.45 * dichte;   // W5 Runde 4: dichter am Spülsaum
    float stamm = step(schwelle, da) * (1.0 - smoothstep(0.08, 0.16, abs(f.y - 0.5))) * smoothstep(0.05, 0.12, f.x) * (1.0 - smoothstep(0.75 + 0.2 * lage, 0.9 + 0.1 * lage, f.x));
    float schatten = step(schwelle, da) * (1.0 - smoothstep(0.16, 0.30, abs(f.y - 0.62))) * (1.0 - stamm) * smoothstep(0.05, 0.12, f.x) * (1.0 - smoothstep(0.8, 0.95, f.x));
    float nah = 1.0 - smoothstep(0.35, 1.1, px);
    vec3 hell = mix(vec3(0.42, 0.40, 0.37), vec3(0.62, 0.56, 0.47), frisch);
    c = mix(c, c * 0.55, schatten * nah * 0.8);
    c = mix(c, hell, stamm * nah);
    // W5 Runde 4: fern der Teppich aus Holz als Tönung mit Haufen (7 und 18 m, in der Falllinie gestreckt): helle
    // geschälte Stämme, dazwischen Schatten; vorher nur 12 % Aufhellung, aus 1,6 km nicht zu sehen
    vec2 qf = vec2(dot(p.xz, fall), dot(p.xz, vec2(-fall.y, fall.x)));
    float haufen = cliffScNoiseF(qf * vec2(0.45, 1.0) + 5.0, 7.0, px) * 0.6 + cliffScNoiseF(qf * vec2(0.5, 1.0) + 17.0, 18.0, px) * 0.4;
    float anteil = (0.10 + 0.55 * dichte) * smoothstep(0.30, 0.70, haufen + 0.25 * dichte);
    vec3 teppich = mix(c * 0.5, hell, 0.55 + 0.25 * haufen);
    return mix(c, mix(c, teppich, anteil), 1.0 - nah);
}
