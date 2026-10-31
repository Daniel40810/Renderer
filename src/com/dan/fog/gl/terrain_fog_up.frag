// terrain_fog_up.frag — seit Brocken Phase 7: der Nebel aus halber (oder drittel) Auflösung auf das volle Bild. Je
// Bildpunkt die vier nächsten Texel (ihre Mitte ist der Bildpunkt, den terrain_fog.frag gerechnet hat), gewichtet nach
// Lage (bilinear) und danach, wie gut ihre Entfernung zu der passt, die die eigene Fläche dort hätte (Gefälle aus den
// Nachbarn, einseitig zur ruhigeren Seite). Passt keiner (Kante einer Kuppe vor dem fernen Nebelmeer) oder springt die
// Durchlässigkeit zwischen den vieren um mehr als 0,3 (der Rand des Nebels am Hang, flach gesehen), wird der Bildpunkt
// selbst gerechnet (fogMarch) – das sind nur die Kanten. Ausgabe (Streulicht,
// Durchlässigkeit) für die Mischung ONE, SRC_ALPHA wie terrain_fog.frag.
#include "terrain_fog_common.glsl"
#include "terrain_fog_march.glsl"
uniform sampler2D uLow, uLowDist;
uniform int uScale;
out vec4 frag;

// Entfernung am Nachbarpunkt (am Bildrand der eigene)
float distAt(ivec2 q) {
    vec3 P;
    return pixelDist(clamp(q, ivec2(0), ivec2(uViewport) - 1), P);
}

// Die vier Texel gegen die Entfernung d am Bildpunkt (mit dem Gefälle g je Bildpunkt); Rückgabe gewichtetes Mittel,
// in fit die beste Übereinstimmung, in spread der Sprung der Durchlässigkeit
vec4 gather(ivec2 px, float d, vec2 g, out float fit, out float spread) {
    ivec2 ls = textureSize(uLow, 0) - 1;
    int off = (uScale - 1) / 2;
    vec2 f = (vec2(px) - float(off)) / float(uScale);   // Texelmitte = gerechneter Bildpunkt
    ivec2 b = ivec2(floor(f));
    vec2 fr = f - vec2(b);
    vec4 sum = vec4(0.0);
    float ws = 0.0, tMin = 1.0, tMax = 0.0;
    fit = 1e30;
    for (int j = 0; j < 2; j++)
        for (int i = 0; i < 2; i++) {
            ivec2 c = clamp(b + ivec2(i, j), ivec2(0), ls);
            vec4 col = texelFetch(uLow, c, 0);
            float dl = texelFetch(uLowDist, c, 0).r;
            vec2 rep = vec2(min(c * uScale + ivec2(off), ivec2(uViewport) - 1) - px);
            float rel = abs(dl - (d + dot(g, rep))) / max(d, 1.0);   // gegen die Entfernung, die die Fläche dort hätte
            float bil = (i == 0 ? 1.0 - fr.x : fr.x) * (j == 0 ? 1.0 - fr.y : fr.y);
            float wgt = (bil + 1e-3) * exp(-rel * 150.0);
            sum += col * wgt;
            ws += wgt;
            fit = min(fit, rel);
            tMin = min(tMin, col.a);
            tMax = max(tMax, col.a);
        }
    spread = tMax - tMin;
    return sum / ws;
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float d = distAt(px), fit, spread;
    // meist passt es schon ohne Gefälle; sonst mit dem Gefälle aus den Nachbarn, einseitig zur ruhigeren Seite
    vec4 c = gather(px, d, vec2(0.0), fit, spread);
    if (fit > 0.02) {
        float xr = distAt(px + ivec2(1, 0)) - d, xl = d - distAt(px - ivec2(1, 0));
        float yu = distAt(px + ivec2(0, 1)) - d, yd = d - distAt(px - ivec2(0, 1));
        vec2 g = vec2(abs(xr) < abs(xl) ? xr : xl, abs(yu) < abs(yd) ? yu : yd);
        c = gather(px, d, g, fit, spread);
    }
    // keine passende Entfernung, oder der Rand des Nebels selbst (Ufer am Hang, flach gesehen): selbst rechnen
    if (fit > 0.02 || spread > 0.3) {
        float t;
        c = fogMarch(px, t);
    }
    frag = c;
}
