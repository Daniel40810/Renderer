// halation.frag — Halation, Schritt 2 (com.dan.film): auf dem kleinen Puffer mit dem Kern gefaltet, K ∗ H_R. Der Kern ist
// Film.halationTable (das Profil R(θ) · cos⁴θ · 10^(−2 D_T / cos θ), mit der Streuung der Schichten gefaltet) an
// ρ = |(dx / r_x, dy / r_y)|, durch die Summe über die Scheibe geteilt (Film.halationNorm), bis Film.HALATION_REACH
// Ringhalbmesser. Außerhalb des Bildfensters ist kein Licht (das Fenster des Projektors deckt es ab). Gleich wie Film.halate.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDown;
uniform vec2 uRc;               // Ringhalbmesser im Puffer (Bildpunkte, x und y; anamorphotisch verschieden)
uniform ivec2 uK;               // Reichweite (Bildpunkte)
uniform float uReach;           // Reichweite (Ringhalbmesser)
uniform float uTable[64];
uniform float uNorm;            // 1 / Summe der Gewichte
float tableAt(float rho) {
    float u = rho / uReach * 63.0;
    if (u >= 63.0) return 0.0;
    int i = int(u);
    return mix(uTable[i], uTable[i + 1], u - float(i));
}
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    ivec2 sz = textureSize(uDown, 0);
    float acc = 0.0;
    for (int dy = -uK.y; dy <= uK.y; dy++) {
        int yy = p.y + dy;
        if (yy < 0 || yy >= sz.y) continue;
        for (int dx = -uK.x; dx <= uK.x; dx++) {
            int xx = p.x + dx;
            if (xx < 0 || xx >= sz.x) continue;
            float w = tableAt(length(vec2(float(dx), float(dy)) / uRc));
            if (w > 0.0) acc += w * texelFetch(uDown, ivec2(xx, yy), 0).r;
        }
    }
    frag = vec4(acc * uNorm, 0.0, 0.0, 1.0);
}
