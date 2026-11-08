// boden.frag — Licht je Bildpunkt wie Malweise (Sonne mit Schatten von oben, Himmel nach der Blattfläche darüber), so
// haben die Lichtflecken am Boden die Form der Lücken im Laub; Stufe ohne Akzent (nur die Streu darauf ist Akzent).
// Seit LituyaBay1958 W10 nach der Normale des Geländes (in der Ebene: nach oben, wie bisher).
in vec3 vP;
in vec3 vN;
in float vNass;
uniform vec3 uBoden;
uniform vec3 uWasserFarbe;
uniform vec4 uBodenRect;   // x0, z0, x1, z1: Land nur hier (seit W10b; das Wasser auf dem ganzen Gelände)
layout(location = 0) out vec4 oFarbe;
layout(location = 1) out float oY;

void main() {
    if (vNass <= 0.0 && (vP.x < uBodenRect.x || vP.z < uBodenRect.y || vP.x > uBodenRect.z || vP.z > uBodenRect.w)) discard;
    vec3 n = normalize(vN), rgb;
    if (vNass > 0.0) {
        // seit W10b das Wasser (wie Malweise): Himmel nach Fresnel gespiegelt, darunter der Körper des Wassers
        float v = sonne(vP + vec3(0.0, 0.01, 0.0)), h = himmel(vP + vec3(0.0, 0.05, 0.0));
        float c = clamp(normalize(uAuge - vP).y, 0.0, 1.0), m = 1.0 - c, fr = 0.02 + 0.98 * m * m * m * m * m;
        vec3 koerper = uWasserFarbe * (uSonneFarbe * max(0.0, uZurSonne.y) * v + uHimmelFarbe * h);
        rgb = (1.0 - fr) * koerper + fr * uHimmelFarbe * h;
    } else {
        float v = sonne(vP + n * 0.01), h = himmel(vP + vec3(0.0, 0.05, 0.0)) * (0.55 + 0.45 * n.y);
        rgb = uBoden * (uSonneFarbe * max(0.0, dot(n, uZurSonne)) * v + uHimmelFarbe * h);
    }
    float y = lum(rgb);
    int band = bandRoh(log2(max(y, 1e-12)));
    oFarbe = vec4(farbeZu(stufeZu(rgb, band, false), band), 1.0);
    oY = y;
}
