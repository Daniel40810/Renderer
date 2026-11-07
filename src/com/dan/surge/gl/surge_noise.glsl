// surge_noise.glsl — com.dan.surge: Wertrauschen in 2D und 3D aus Sinus-Hashes (wie in LituyaBay1958 welle.frag,
// lippe.frag, spur.vert), und ein Rauschen mit Periode p (m), das in der Ferne zum Mittel 0,5 geht, sobald ein Bildpunkt
// mehr als rund ein Drittel der Periode deckt (gegen Flimmern, Lituya W0 K4).
float sgHash(vec2 i) { return fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453); }
float sgNoise(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(sgHash(i), sgHash(i + vec2(1, 0)), f.x), mix(sgHash(i + vec2(0, 1)), sgHash(i + vec2(1, 1)), f.x), f.y);
}
float sgHash3(vec3 i) { return fract(sin(dot(i, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float sgNoise3(vec3 x) {
    vec3 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(sgHash3(i), sgHash3(i + vec3(1, 0, 0)), f.x), mix(sgHash3(i + vec3(0, 1, 0)), sgHash3(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(sgHash3(i + vec3(0, 0, 1)), sgHash3(i + vec3(1, 0, 1)), f.x), mix(sgHash3(i + vec3(0, 1, 1)), sgHash3(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}
// Rauschen mit Periode p, Versatz o, Bildpunkt px (m); early = 1: ganz ausgeblendete Lagen gar nicht rechnen (gleiches Ergebnis)
float sgNoiseF(vec2 q, float p, vec2 o, float px, int early) {
    float a = px / p;
    if (early == 1 && a >= 0.6) return 0.5;
    return mix(sgNoise(q / p + o), 0.5, smoothstep(0.25, 0.6, a));
}
// Steigung einer Lage (zentrale Differenz über ein Viertel der Periode), gefiltert wie sgNoiseF
vec2 sgSlope(vec2 q, float p, vec2 o, float px, int early) {
    if (early == 1 && px / p >= 0.55) return vec2(0.0);
    float e = 0.25;
    vec2 g = vec2(sgNoise(q / p + o + vec2(e, 0.0)) - sgNoise(q / p + o - vec2(e, 0.0)), sgNoise(q / p + o + vec2(0.0, e)) - sgNoise(q / p + o - vec2(0.0, e)));
    return g * (1.0 - smoothstep(0.2, 0.55, px / p));
}
