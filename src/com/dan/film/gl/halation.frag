// halation.frag — Halation, Schritt 2 (com.dan.film): auf dem kleinen Puffer mit dem Kern gefaltet, K ∗ H_R. Der Kern ist
// Film.halationWeights (das Profil R(θ) · cos⁴θ · 10^(−2 D_T / cos θ), mit der Streuung der Schichten gefaltet, an
// ρ = |(dx / r_x, dy / r_y)|, bis Film.HALATION_REACH Ringhalbmesser, durch die Summe geteilt) als kleine Textur: je Punkt ein
// Zugriff. Außerhalb des Bildfensters ist kein Licht (das Fenster des Projektors deckt es ab). Gleich wie Film.halate.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDown;
uniform sampler2D uW;           // Gewichte (2kx + 1) × (2ky + 1)
uniform ivec2 uK;               // Reichweite (Bildpunkte)
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
            float w = texelFetch(uW, ivec2(dx + uK.x, dy + uK.y), 0).r;
            if (w > 0.0) acc += w * texelFetch(uDown, ivec2(xx, yy), 0).r;
        }
    }
    frag = vec4(acc, 0.0, 0.0, 1.0);
}
