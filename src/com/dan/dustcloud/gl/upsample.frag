// upsample.frag — com.dan.dustcloud (aus LituyaBay1958, Werkbuch Welle W4): ein Bild (L, T) halber Auflösung mit der
// Tiefe hochgerechnet. Je Bildpunkt die 4 × 4 nächsten Bildpunkte der halben Auflösung mit Gewichten des kubischen
// B-Splines und nach der Tiefe: was in einer anderen Tiefe lag (ein Berg vor dem Staub, eine Welle vor dem Himmel), zählt
// kaum. Ausgabe (L, T) wie die halbe Auflösung (Mischung ONE, SRC_ALPHA).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHalb, uDepth;
uniform vec2 uHalbPx;
uniform float uNear, uFar;
uniform int uRev;

float tiefe(float z) {
    if (uRev == 1) return z <= 0.0 ? 1e9 : uNear / z;
    float zn = z * 2.0 - 1.0;
    return z >= 1.0 ? 1e9 : 2.0 * uFar * uNear / ((uFar + uNear) - zn * (uFar - uNear));
}

// kubischer B-Spline (Gewichte der vier Stützstellen bei Bruchteil f): glättet das Rauschen der halben Auflösung
// (Lituya W4 Probe 3: der Versatz des Strahlengangs kam bei bilinear als grobes dunkles Gesprenkel durch)
vec4 bspline(float f) {
    float g = 1.0 - f;
    return vec4(g * g * g, 4.0 - 6.0 * f * f + 3.0 * f * f * f, 4.0 - 6.0 * g * g + 3.0 * g * g * g, f * f * f) / 6.0;
}

void main() {
    float d0 = tiefe(texture(uDepth, vUV).r);
    vec2 p = vUV * uHalbPx - 0.5;
    vec2 i0 = floor(p), f = p - i0;
    vec4 wx = bspline(f.x), wy = bspline(f.y);
    vec4 acc = vec4(0.0), bil = vec4(0.0);
    float ws = 0.0, wbs = 0.0;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            ivec2 ic = clamp(ivec2(i0) + ivec2(x - 1, y - 1), ivec2(0), ivec2(uHalbPx) - 1);
            vec4 v = texelFetch(uHalb, ic, 0);
            float dz = tiefe(texture(uDepth, (vec2(ic) + 0.5) / uHalbPx).r);
            float wb = wx[x] * wy[y];
            float rel = abs(dz - d0) / (0.04 * min(d0, dz) + 1.0);
            float w = wb * exp(-rel * rel);
            acc += v * w;
            ws += w;
            // ohne passende Tiefe: die nächsten vier bilinear
            float wl = (x == 1 || x == 2) && (y == 1 || y == 2) ? (x == 2 ? f.x : 1.0 - f.x) * (y == 2 ? f.y : 1.0 - f.y) : 0.0;
            bil += v * wl;
        }
    }
    frag = ws > 0.02 ? acc / ws : bil;
}
