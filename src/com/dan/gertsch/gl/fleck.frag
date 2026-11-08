// fleck.frag — die Form des Einheitsblatts wie Malweise.BLATT (zwei quadratische Bögen, spitz an beiden Enden): Breite
// bei t aus der Bézierkurve y(τ) = 1 − 1,8τ − 0,2τ², x(τ) = 4τ(1 − τ). Kante über die Abdeckung (Alpha → Abtastungen).
in vec2 vST;
flat in vec3 vFarbe;
flat in float vY;
uniform int uModus;
layout(location = 0) out vec4 oFarbe;
layout(location = 1) out float oY;

void main() {
    float t = clamp(vST.y, -1.0, 1.0);
    float tau = (-1.8 + sqrt(max(0.0, 3.24 - 0.8 * (t - 1.0)))) / 0.4;
    float halb = 4.0 * tau * (1.0 - tau), d = halb - abs(vST.x);
    float deckt = clamp(d / max(fwidth(vST.x), 1e-5) + 0.5, 0.0, 1.0);
    if (uModus == 1) { if (d <= 0.0) discard; return; }
    if (deckt <= 0.0) discard;
    oFarbe = vec4(vFarbe, deckt);
    oY = vY;
}
