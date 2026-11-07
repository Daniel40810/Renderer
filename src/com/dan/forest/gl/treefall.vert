// treefall.vert — com.dan.forest (aus LituyaBay1958, Werkbuch Welle W5, E11): ein Baum, der in einer Wasserzunge (Lawine,
// Druckwelle) fällt (TreeFall, gl/TreeFallGl) [G]. Netz aus forest (Fuß bei 0, örtlich), gedreht und skaliert wie in
// forest.vert, dann nach der Zeit seit seiner Ankunft gekippt: Luftstoß (bis 0,6 s vorher, einige Grad), dann Kippen um
// den Bruchpunkt in Laufrichtung, beschleunigt wie ein fallender Körper, bis zum Liegen auf dem Hang; die Krone schlägt
// etwas nach (Peitsche), der Stamm federt nach dem Aufschlag. Fort: danach Treiben mit dem Wasser und Sinken. Entrindet:
// Schwingen, die Krone reißt ab und fliegt fort. Erdkrümmung als Absenkung r² · uCurvature.
#include "treefall_light.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec2 aAux;      // Höhe im Baum 0..1, Laub (1 Blatt, 0,5 Hülle, 0 Holz)
layout(location = 4) in vec4 iA;        // x, y, z, Drehung
layout(location = 5) in vec4 iB;        // Maßstab, Tönung, Ankunft (s), Bruchhöhe (m)
layout(location = 6) in vec4 iC;        // Fallrichtung x, z, Winkel am Ende (rad), Falldauer (s)
layout(location = 7) in vec4 iD;        // Folge (1 gebrochen, 2 entrindet, 3 umgelegt, 4 fort), Drift (m/s), Höhe (m), Typ
uniform float uEventTime;
uniform float uCurvature;
out vec3 vW;
out vec3 vN;
out vec3 vCol;
out float vLaub;
out float vH;
out float vNass;

float hsh(float x) { return fract(sin(x * 91.17 + iA.x * 0.013 + iA.z * 0.007) * 43758.5453); }

// Drehung um die Achse k (Einheit) um w (Rodrigues)
vec3 dreh(vec3 v, vec3 k, float w) {
    float c = cos(w), s = sin(w);
    return v * c + cross(k, v) * s + k * dot(k, v) * (1.0 - c);
}

void main() {
    float c = cos(iA.w), s = sin(iA.w);
    vec3 p = aPos * iB.x;
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    vec3 n = vec3(c * aNrm.x - s * aNrm.z, aNrm.y, s * aNrm.x + c * aNrm.z);
    float tau = uEventTime - iB.z;
    int folge = int(iD.x + 0.5);
    float H = max(iD.z, 1.0), hb = iB.w, Tf = max(iC.w, 0.3), Wend = iC.z;
    vec3 F = vec3(iC.x, 0.0, iC.y);
    vec3 K = normalize(cross(vec3(0.0, 1.0, 0.0), F) + vec3(1e-5, 0.0, 0.0));   // dreht „oben“ nach F
    float laub = aAux.y;
    // Luftstoß vor der Zunge: einige Grad, oben mehr, zittert
    float vor = smoothstep(-0.6, 0.0, tau) * (0.05 + 0.02 * sin(uEventTime * 23.0 + iA.x));
    float w = vor * aAux.x;
    float u = clamp(tau / Tf, 0.0, 1.0);
    vec3 drift = vec3(0.0);
    if (folge == 2) {
        // entrindet: der Stamm schwingt und klingt aus, die Krone reißt ab und fliegt mit dem Wasser fort
        float sw = tau > 0.0 ? 0.16 * sin(tau * 2.6) * exp(-tau * 0.7) : 0.0;
        w = (vor + sw) * aAux.x;
        if (laub > 0.25) {
            float ab = smoothstep(0.1 + 0.4 * hsh(aPos.y), 0.9 + 0.5 * hsh(aPos.x), tau);
            // Runde 5: die Krone zerfällt (zieht sich auf einen Punkt zusammen) und geht mit dem Wasser fort und unter; vorher
            // blieb sie als schmale Spitze erhalten und flog über dem Grat (aus der Ferne: dünne Spitzen in der Luft)
            p = mix(p, vec3(0.0, 0.45 * H, 0.0), ab);
            p += F * ab * (8.0 + 18.0 * tau) + vec3(0.0, -ab * (4.0 + 8.0 * tau), 0.0);
        }
    } else if (tau > 0.0) {
        // fort und umgelegt in der Zunge: die Krone wird abgerissen, es bleibt der Stamm (Probe W5D: liegende Kronen standen
        // als schwarze Flecken auf dem dünnen Wasser am Rand)
        if (laub > 0.25 && folge >= 3) {
            float ab = smoothstep(Tf * 0.6, Tf + 1.2 + 0.8 * hsh(aPos.y), tau);
            p.xz *= 1.0 - 0.92 * ab;
            p.y = mix(p.y, max(p.y * 0.98, 2.0), ab);
        }
        // kippen: erst langsam, dann schnell, am Ende der Winkel auf dem Hang; die Krone schlägt nach
        float fall = u * u * (1.7 - 0.7 * u);
        float nach = (1.0 - smoothstep(0.0, 0.7, tau - Tf)) * step(Tf, tau) * sin(clamp((tau - Tf) / 0.7, 0.0, 1.0) * 3.14159) * 0.05;
        float rel = clamp((p.y - hb) / max(H - hb, 1.0), 0.0, 1.0);
        float peitsche = 0.12 * rel * sin(u * 3.14159) * (folge == 1 ? 1.0 : 0.6);
        w = vor * (1.0 - u) + Wend * fall * (1.0 - peitsche) - Wend * nach;
        if (folge == 4 && tau > Tf) {
            // fort: treibt mit der Zunge weiter und sinkt in sie ein (bis 4 m)
            float d = tau - Tf;
            drift = F * iD.y * 2.2 * (1.0 - exp(-d / 2.2)) + vec3(0.0, -min(12.0, 3.0 * d), 0.0);
        }
    }
    // nur oberhalb des Bruchs (gebrochen: der Stumpf bleibt stehen; sonst kippt alles um den Fuß)
    float oben = folge == 1 ? smoothstep(hb - 0.2, hb + 0.2, p.y) : 1.0;
    vec3 piv = vec3(0.0, folge == 1 ? hb : 0.0, 0.0);
    vec3 q = dreh(p - piv, K, w * oben) + piv;
    n = dreh(n, K, w * oben);
    if (folge == 3 || folge == 4) q.y += 0.6 * smoothstep(0.0, 0.6, tau) * (1.0 - laub) * step(p.y, 1.5);   // Wurzelteller hebt sich
    vec3 wp = iA.xyz + q + drift * oben;
    vec2 r = wp.xz - uCam.xz;
    vW = vec3(wp.x, wp.y - dot(r, r) * uCurvature, wp.z);
    vN = n;
    vCol = aCol * mix(vec3(0.84, 0.88, 0.86), vec3(1.14, 1.08, 0.96), iB.y);
    vLaub = laub;
    vH = aAux.x;
    vNass = smoothstep(-0.2, 0.6, tau);
    gl_Position = uVP * vec4(vW, 1.0);
}
