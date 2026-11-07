// drops.frag — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W4b, Runde 4): ein Tropfen als weicher, runder Fleck aus
// Weißwasser [G]; oben im Licht von Himmel und Sonne, unten etwas dunkler; deckend mit gestreuter Durchsicht (schreibt
// Tiefe, so liegen Staub und Gischt eines Strahlengangs richtig davor).
#include "light.glsl"
#include "/com/dan/surge/gl/surge_noise.glsl"
in vec3 vW;
in float vA;
in float vK;
out vec4 frag;


void main() {
    vec2 c = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(c, c);
    if (r2 > 1.0) discard;
    float a = vA * exp(-3.0 * r2) * (1.0 - smoothstep(0.7, 1.0, r2));   // weich, kein Rand (Probe: harte Scheiben wie Kugeln)
    if (sgHash(gl_FragCoord.xy + vec2(uFrame * 3.7, vK * 0.917)) >= a) discard;
    vec3 d = vW - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    vec3 N = normalize(vec3(0.0, 1.0, 0.0) - d * 0.6);
    vec3 weiss = lightSun(vW) * max(dot(N, uSun), 0.0) * 0.8 + lightSky(normalize(N + vec3(0.0, 1.2, 0.0))) * 1.1;
    vec3 col = vec3(0.80, 0.80, 0.78) * weiss * (0.85 - 0.2 * c.y);
    col = lightAerial(col, d, dist);
    frag = vec4(col, 1.0);
}
