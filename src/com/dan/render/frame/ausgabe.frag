#version 460
// ausgabe.frag – render.frame (P6a, Werkbuch 8.1, 12.7): HDR-Bild mal Belichtung durch die Kurve aus film
// (Vision3 auf 2383, ohne Korn) auf den Bildschirm. Die Kurve: Matrix auf die Schichten, je Schicht eine Tabelle über
// log10 H (genau linear zwischen zwei Werten), Projektion mit den Farbstoffen, 10^-e, Verstärkung, Knie
// (wie render.frame.Filmkurve.anwenden). Ein Ziel in sRGB verschlüsselt die Karte selbst.
// F50 A (12.7, Nachlauf P6): vor der Kurve das Sehen mit den Stäbchen nach Krawczyk, Myszkowski und Seidel (2005),
// Gl. 7 und 15: Y = Leuchtdichte des Bildpunkts in cd/m² (HDR / Vorbelichtung), σ = K / (K + Y), die Farbe geht um σ in
// ein Grau gleicher Leuchtdichte mit der Farbe der Nacht über (wie render.frame.Belichtung.staebchen).
layout(set = 0, binding = 0) uniform sampler2D hdr;
layout(std430, set = 0, binding = 1) readonly buffer Tabelle { vec4 t[]; };
layout(std430, set = 0, binding = 2) readonly buffer Parameter { float p[24]; };
layout(push_constant) uniform Q { float faktor; int kurve; int n; float ohneVorbelichtung; int nacht; } q;
layout(location = 0) out vec4 aus;

const float STAEBCHEN_K = 0.04;
const vec3 NACHTFARBE = vec3(1.05, 0.97, 1.27);
const vec3 LUM = vec3(0.2126, 0.7152, 0.0722);
const float FARBTREU = 1.0;                                                     // wie Belichtung.FARBTREU

float knie(float x) { return x <= 0.8 ? x : 0.8 + 0.2 * (1.0 - exp(-(x - 0.8) / 0.2)); }

void main() {
    vec3 roh = texelFetch(hdr, ivec2(gl_FragCoord.xy), 0).rgb;
    vec3 c = roh * q.faktor;
    if (q.nacht != 0) {
        float y = max(0.0, dot(roh, LUM) * q.ohneVorbelichtung);
        float s = STAEBCHEN_K / (STAEBCHEN_K + y);
        c = c * (1.0 - s) + NACHTFARBE * (dot(c, LUM) * s);
        // seit L5e1 (Seegang Werkbuch 100, Befund 1): Überstrahltes bei Nacht farbtreu gestaucht (Belichtung.farbtreu), sonst
        // sättigt die Kurve jede Schicht für sich, und ein grünes Feuer wird weiß; ganz ab Vorbelichtung 1, weich ab 0,01 [G]
        float wf = smoothstep(-2.0, 0.0, -log(q.ohneVorbelichtung) / log(10.0));
        float mf = max(c.r, max(c.g, c.b));
        if (wf > 0.0 && mf > 0.0) c *= 1.0 - wf + wf * pow(1.0 + pow(mf / FARBTREU, 4.0), -0.25);
    }
    if (q.kurve == 0) { aus = vec4(c, 1.0); return; }
    mat3 m = mat3(p[0], p[3], p[6], p[1], p[4], p[7], p[2], p[5], p[8]);       // Zeilen von Film.negative.matrix()
    vec3 h = m * c * p[9];
    vec3 dd;
    for (int k = 0; k < 3; k++) {
        float x = log(max(1e-7, h[k])) / log(10.0);
        float u = (clamp(x, p[20], p[21]) - p[20]) / (p[21] - p[20]) * float(q.n - 1);
        int i = min(q.n - 2, int(floor(u)));
        float w = u - float(i);
        dd[k] = mix(t[i][k], t[i + 1][k], w);
    }
    vec3 o;
    for (int i = 0; i < 3; i++) o[i] = knie(pow(10.0, -(p[10 + 3 * i] * dd.x + p[11 + 3 * i] * dd.y + p[12 + 3 * i] * dd.z)) * p[19]);
    aus = vec4(o, 1.0);
}
