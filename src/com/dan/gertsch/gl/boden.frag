// boden.frag — Licht je Bildpunkt wie Malweise (Sonne mit Schatten von oben, Himmel nach der Blattfläche darüber), so
// haben die Lichtflecken am Boden die Form der Lücken im Laub; Stufe ohne Akzent (nur die Streu darauf ist Akzent).
in vec3 vP;
uniform vec3 uBoden;
layout(location = 0) out vec4 oFarbe;
layout(location = 1) out float oY;

void main() {
    float v = sonne(vP + vec3(0.0, 0.01, 0.0)), h = himmel(vP + vec3(0.0, 0.05, 0.0));
    vec3 rgb = uBoden * (uSonneFarbe * max(0.0, uZurSonne.y) * v + uHimmelFarbe * h);
    float y = lum(rgb);
    int band = bandRoh(log2(max(y, 1e-12)));
    oFarbe = vec4(farbeZu(stufeZu(rgb, band, false), band), 1.0);
    oY = y;
}
