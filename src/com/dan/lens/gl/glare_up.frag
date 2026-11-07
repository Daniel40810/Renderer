// glare_up.frag — eine Stufe größer: die kleinere Stufe mit einem Zeltfilter 3 × 3, dazu diese Stufe mit ihrem Gewicht.
// Über alle Stufen ergibt das Σ w_l · Unschärfe_l mit w_l = 2^(−3l/4): ein Kern, der wie r^−2,75 abfällt [G] (com.dan.lens).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uLow;         // größere Stufe (kleiner)
uniform sampler2D uHere;        // diese Stufe (aus dem Abstieg)
uniform vec2 uTexel;            // Bildpunkt von uLow (uv)
uniform float uWeight;
uniform int uFirst;             // 1: unterste Stufe, uLow nicht lesen
void main() {
    vec3 t = vec3(0.0);
    if (uFirst == 0) {
        for (int j = -1; j <= 1; j++)
            for (int i = -1; i <= 1; i++) t += texture(uLow, vUV + vec2(i, j) * uTexel).rgb * ((i == 0 ? 2.0 : 1.0) * (j == 0 ? 2.0 : 1.0));
        t /= 16.0;
    }
    frag = vec4(t + texture(uHere, vUV).rgb * uWeight, 1.0);
}
