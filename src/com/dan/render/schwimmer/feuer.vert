#version 460
// feuer.vert – render.schwimmer (S7e1): je Licht zwölf Scheitel. Die ersten sechs: ein Viereck zum Auge um das Licht, so
// groß wie der Hof (HOF_RAND · HOF Bildpunkte). Die zweiten sechs: das Spiegelbild im Wasser. Das Licht wird an der
// Wasserfläche (Höhe aus der Flotte, ruhig) gespiegelt; das Viereck steht an dem Punkt der Fläche, durch den der Strahl zum
// Spiegelbild geht (er hat im Bild denselben Ort), ein wenig zum Auge gerückt (werte.w), damit die Tiefe des Wassers es
// nicht verdeckt, wohl aber Rümpfe und Land davor. Stärke nach Fresnel (n = 1,34, Schlick) über den Weg Licht–Wasser–Auge;
// der Streif ist werte.z-mal so hoch wie breit [G] (Kräuselung, ohne den Seegang am Ort).
#include "/com/dan/render/schwimmer/feuer.glsl"
layout(location = 0) out vec2 vUv;       // Abstand vom Licht im Bild (Bildpunkte, x rechts, y oben)
layout(location = 1) out vec3 vE;        // Beleuchtungsstärke am Auge (lx je Kanal) mal Nacht
layout(location = 2) flat out vec2 vArt; // x: 0 Licht, 1 Spiegelbild; y: Streckung des Streifs
layout(location = 3) flat out float vOben; // seit L5e1: der Streif reicht im Bild höchstens so weit nach oben (Bildpunkte)

void aus() { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vUv = vec2(0.0); vE = vec3(0.0); vArt = vec2(0.0); vOben = 0.0; }

void main() {
    int k = gl_VertexIndex / 12, e = gl_VertexIndex % 12, ecke = e % 6;
    bool spiegel = e >= 6;
    vec4 a = li[2 * k], b = li[2 * k + 1];
    float n = nacht();
    if (a.w <= 0.0 || n <= 0.0) { aus(); return; }
    const vec2 E[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));
    float alpha = obenV.w;
    vec3 P = krumm(a.xyz);
    if (!spiegel) {
        float d = length(P);
        float rand = HOF_RAND * HOF;                                         // Bildpunkte
        vec3 Ee = b.rgb * a.w / (d * d) * exp(-werte.x * d) * n;
        vUv = E[ecke] * rand;
        vE = Ee;
        vArt = vec2(0.0, 1.0);
        vOben = 1e9;
        gl_Position = vp * vec4(P + (rechtsV.xyz * E[ecke].x + obenV.xyz * E[ecke].y) * rand * alpha * d, 1.0);
        return;
    }
    // Spiegelbild: Wasser bei y = yw (relativ zum Auge, mit der Krümmung am Ort des Lichts)
    float yw = b.w - dot(P.xz, P.xz) * augeHi.w;
    if (b.w > 1e8 || yw >= 0.0 || P.y <= yw) { aus(); return; }
    vec3 M = vec3(P.x, 2.0 * yw - P.y, P.z);
    vec3 W = M * (yw / M.y);                                                 // Punkt der Fläche auf dem Strahl zu M
    float dM = length(M), dW = length(W);
    float c = clamp(-W.y / dW, 0.0, 1.0);                                    // cos des Einfalls gegen die Senkrechte
    const float R0 = 0.0211;                                                 // ((1,34 − 1) / (1,34 + 1))²
    float F = R0 + (1.0 - R0) * pow(1.0 - c, 5.0);
    float S = werte.z;
    vec2 halb = vec2(4.0 * 1.5 * KERN, 4.0 * 1.5 * KERN * S);               // Bildpunkte
    vUv = E[ecke] * halb;
    vE = b.rgb * a.w * F / (dM * dM) * exp(-werte.x * dM) * n;
    vArt = vec2(1.0, S);
    // seit L5e1 (Befund 4): Spiegelpunkte liegen zwischen Auge und Licht; der Streif reicht nicht über das Bild des Wassers
    // senkrecht unter dem Licht hinaus (sonst stand er bei fernen Lichtern als Spitze über der Kimm)
    vec3 fuss = vec3(P.x, yw, P.z);
    vOben = (asin(clamp(fuss.y / length(fuss), -1.0, 1.0)) - asin(clamp(W.y / dW, -1.0, 1.0))) / alpha;
    vec3 Wn = W * (1.0 - werte.w);
    float dn = length(Wn);
    gl_Position = vp * vec4(Wn + (rechtsV.xyz * E[ecke].x * halb.x + obenV.xyz * E[ecke].y * halb.y) * alpha * dn, 1.0);
}
