// film.frag — vom Licht der Szene auf die Leinwand (com.dan.film), wie Film.develop, dazu Korn, Halation, Bildstand, Flackern.
//  1 Belichtung je Schicht: H = (M · rgb) · Belichtung / 0,18, Halation in die Schichten (rot am meisten)
//  2 Negativ: D = D_min + γ (s_t ln(1 + e^((x − x_t)/s_t)) − s_s ln(1 + e^((x − x_s)/s_s))), x = log₁₀ H; Korn in der Dichte
//    σ = RMS/1000 · Schicht · √(A48 / A) · √(D − D_min) (Selwyn), je Schicht und Filmbild neu, leicht verbunden
//  3 Kopie: log E = −D_N + b + Kopierlicht; ihre Kurve; Korn der Kopie; Farbstoffe mit Nebenabsorption (Zeilen auf 1)
//  4 Leinwand: 10^(−(D − D_min)) · Verstärkung, weiches Knie, sRGB, Zittern gegen Stufen
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform sampler2D uHal;         // K ∗ H_R (kleiner Puffer, bilinear)
uniform int uHalOn;
uniform vec3 uHalRgb;           // Halation je Schicht (Stärke · Anteil)
uniform float uExposure;        // Belichtung der App · Flackern · 2^Blenden / 0,18
uniform mat3 uMatrix;           // sRGB → Schichten: uMatrix * rgb
uniform mat3 uDye;              // Nebenabsorption der Kopie (Zeilen auf 1): uDye * D
uniform vec3 uKeep = vec3(1.0); // was von den Farbstoffen der Kopie bleibt (alte Kopie)
uniform float uNeg[18], uPrt[18]; // je Kanal 6 Zahlen: D_min, γ, x_t, s_t, x_s, s_s
uniform vec3 uOffset;           // b + Kopierlicht (log E)
uniform vec2 uWeave;            // Bildstand (uv)
uniform float uGain;
uniform int uGrainOn;
uniform vec3 uSigmaNeg;         // σ bei D_net = 1 je Schicht für diesen Bildpunkt
uniform float uSigmaPrt;
uniform vec2 uCloud;            // Größe einer Farbstoffwolke in Bildpunkten, x und y (anamorphotisch breiter als hoch)
uniform float uFrame;           // Nummer des Filmbilds (Korn wechselt)
uniform vec2 uOut;              // Größe des Ziels (Bildpunkte)

float sp(float z) { return z > 30.0 ? z : log(1.0 + exp(z)); }
float curve(float c[18], int i, float x) {
    int k = 6 * i;
    return c[k] + c[k + 1] * (c[k + 3] * sp((x - c[k + 2]) / c[k + 3]) - c[k + 5] * sp((x - c[k + 4]) / c[k + 5]));
}
uint hash(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}
float u01(uvec3 v) { return (float(hash(v) >> 8u) + 0.5) / 16777216.0; }
float gauss(uvec3 v) { return sqrt(-2.0 * log(u01(v))) * cos(6.2831853 * u01(v + uvec3(17u, 31u, 7u))); }
// Rauschen mit Streuung 1 auf Zellen der Größe uCloud (bilinear; der Verlust an Streuung, je Achse √(2/3), ausgeglichen), je Kanal ch
float grainNoise(vec2 px, uint ch) {
    uint f = uint(uFrame);
    if (uCloud.x <= 1.05 && uCloud.y <= 1.05) return gauss(uvec3(uvec2(px), f * 4u + ch));
    vec2 cl = max(uCloud, vec2(1.0));
    vec2 q = px / cl;
    vec2 i = floor(q), t = q - i;
    if (cl.x <= 1.05) t.x = 0.0;   // in dieser Richtung jeder Bildpunkt eine Zelle
    if (cl.y <= 1.05) t.y = 0.0;
    uvec2 b = uvec2(ivec2(i) + 4096);
    float a = gauss(uvec3(b, f * 4u + ch)), c = gauss(uvec3(b + uvec2(1u, 0u), f * 4u + ch)),
          d = gauss(uvec3(b + uvec2(0u, 1u), f * 4u + ch)), e = gauss(uvec3(b + uvec2(1u, 1u), f * 4u + ch));
    float k = (cl.x > 1.05 ? 1.2247449 : 1.0) * (cl.y > 1.05 ? 1.2247449 : 1.0);
    return mix(mix(a, c, t.x), mix(d, e, t.x), t.y) * k;
}
float srgb(float c) { c = clamp(c, 0.0, 1.0); return c <= 0.0031308 ? 12.92 * c : 1.055 * pow(c, 1.0 / 2.4) - 0.055; }
float knee(float x) { return x <= 0.8 ? x : 0.8 + 0.2 * (1.0 - exp(-(x - 0.8) / 0.2)); }

void main() {
    vec2 uv = vUV + uWeave;
    vec3 rgb = max(textureLod(uHdr, uv, 0.0).rgb, vec3(0.0));
    vec3 h = (uMatrix * rgb) * uExposure;
    if (uHalOn == 1) h += uHalRgb * texture(uHal, uv).r;
    vec2 px = vUV * uOut;
    vec3 dn;
    float nAll = uGrainOn == 1 ? grainNoise(px, 3u) : 0.0;
    for (int c = 0; c < 3; c++) {
        float x = log(max(h[c], 1e-7)) / log(10.0);
        float d = curve(uNeg, c, x);
        if (uGrainOn == 1) {
            float n = 0.88 * grainNoise(px, uint(c)) + 0.47 * nAll;   // Schichten leicht verbunden (ρ ≈ 0,22) [G]
            d += uSigmaNeg[c] * sqrt(max(d - uNeg[6 * c], 0.05)) * n;
        }
        dn[c] = d;
    }
    vec3 dp;
    for (int c = 0; c < 3; c++) {
        float d = curve(uPrt, c, -dn[c] + uOffset[c]);
        if (uGrainOn == 1) d += uSigmaPrt * sqrt(max(d - uPrt[6 * c], 0.05)) * gauss(uvec3(uvec2(px) + uvec2(9001u), uint(uFrame) * 4u + uint(c)));
        dp[c] = (d - uPrt[6 * c]) * uKeep[c];
    }
    vec3 de = uDye * dp;
    vec3 o;
    for (int c = 0; c < 3; c++) o[c] = srgb(knee(pow(10.0, -de[c]) * uGain));
    float dz = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    frag = vec4(o + (dz - 0.5) / 255.0, 1.0);
}
