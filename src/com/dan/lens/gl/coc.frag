// coc.frag — halbe Auflösung: Farbe (Mittel aus 2 × 2) und Unschärfekreis mit Vorzeichen (Durchmesser in halben Bildpunkten,
// + hinter dem Fokus) aus der nächsten der vier Tiefen, damit Ränder im Vordergrund nicht ausfransen (com.dan.lens).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uColor;
uniform sampler2D uDepth;
uniform sampler2D uState;       // Schärfezieher: x = Dioptrien
uniform float uMaxCoc;          // größter Kreis (halbe Bildpunkte)

float focusMm() {
    float x = texelFetch(uState, ivec2(0), 0).x;
    return x > 0.0 ? 1000.0 / x : uFocusFallback;
}

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy) * 2;
    ivec2 mx = ivec2(uFull) - 1;
    vec3 c = vec3(0.0);
    float zmin = 1e30;
    for (int j = 0; j < 2; j++)
        for (int i = 0; i < 2; i++) {
            ivec2 q = min(p + ivec2(i, j), mx);
            c += texelFetch(uColor, q, 0).rgb;
            zmin = min(zmin, viewDepth(texelFetch(uDepth, q, 0).r));
        }
    float coc = cocMm(zmin, focusMm()) / (2.0 * uPitch);
    frag = vec4(c * 0.25, clamp(coc, -uMaxCoc, uMaxCoc));
}
