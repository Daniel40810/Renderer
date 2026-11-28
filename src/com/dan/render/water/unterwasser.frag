#version 460
// unterwasser.frag – render.water (S8d): der Schwimmerblick. Über der Wasserlinie bleibt das Bild (der Bildpunkt fällt
// weg); darunter zeigt es das Wasser von unten (unterwasser.glsl); an der Linie ein Meniskus [G]: das Wasser steigt am Glas
// hoch und bricht das Licht, ein heller Saum mit dunklem Rand, so breit wie rinf.w. Nur dort liest der Teil die Kopie des
// Bilds (seit L6b2: Unterwasser kopiert nur dieses Band).
#include "/com/dan/render/water/unterwasser.glsl"
layout(set = 0, binding = 5) uniform sampler2D kopie;
layout(location = 0) out vec4 aus;
void main() {
    vec2 uv = gl_FragCoord.xy / bild.xy;
    float D = uwTiefe(uv);
    float m = rinf.w;
    if (D <= -m) discard;
    float frei, t;
    vec3 L = uwLeuchtdichte(uwStrahl(uv), max(D, 0.005), frei, t) * hw.sonneE.w;
    float x = D / m;
    if (x >= 1.0) { aus = vec4(L, 1.0); return; }
    // Meniskus: um die Linie (|D| < m) hell (Licht von oben durch die gekrümmte Haut), am unteren Rand dunkel [G]
    vec3 c0 = texture(kopie, uv).rgb;
    vec3 c = D > 0.0 ? L : c0;
    float hell = exp(-8.0 * (x + 0.3) * (x + 0.3)), dunkel = exp(-10.0 * (x - 0.5) * (x - 0.5));
    c = mix(c, c0, 0.5 * clamp(1.0 - x, 0.0, 1.0) * step(0.0, D)) * (1.0 - 0.45 * dunkel) + hell * 0.35 * max(c0, L);
    aus = vec4(c, 1.0);
}
