// nachtsicht.frag — die Demo-Szene um das Polarlicht auf der Grafikkarte: Nachthimmel (2·10⁻⁴ cd/m², zum Horizont
// heller), ein paar tausend Sterne aus einem Hash, eine Bergkette als Silhouette, dunkler Boden; dazu das Polarlicht
// aus dem HDR-Bild (cd/m²). Dann die Kamera (Lichtwertformel, weiches Knie) oder das dunkeladaptierte Auge
// (Stäbchen unter 10⁻³ cd/m², Farbe bis 0,3) und die sRGB-Kennlinie, wie Spektrum.kamera und Spektrum.auge in Java.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uAurora;
uniform mat4 uInvVP;
uniform float uZeit, uIso, uBlende, uAnpassung, uHimmel;
uniform int uAuge;

vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    vec4 a = uInvVP * vec4(ndc, -1.0, 1.0);
    vec4 b = uInvVP * vec4(ndc, 0.0, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

/** Sterne: ein Gitter von 0,5° über Azimut und Höhe, in jeder Zelle mit Wahrscheinlichkeit ein Stern, Helligkeit nach der Häufigkeit der Größenklassen. */
vec3 sterne(vec3 d) {
    float az = atan(d.x, -d.z), el = asin(clamp(d.y, -1.0, 1.0));
    if (el <= 0.0) return vec3(0.0);
    vec2 g = vec2(az * cos(el), el) / radians(0.5);
    vec3 sum = vec3(0.0);
    for (int i = -1; i <= 1; i++)
        for (int j = -1; j <= 1; j++) {
            vec2 c = floor(g) + vec2(i, j);
            float h = hash(c);
            if (h > 0.22) continue;
            vec2 pos = c + vec2(hash(c + 7.3), hash(c + 3.1));
            float m = 6.5 + 2.0 * log2(max(1e-4, hash(c + 11.9))) * 0.30103;   // Größenklasse: 6,5 + 2·log10(u)
            float lux = 2.54e-6 * pow(10.0, -0.4 * max(-1.5, m));
            float r2 = dot(g - pos, g - pos) * radians(0.5) * radians(0.5);
            float sigma = radians(0.06);
            float w = lux / (6.2832 * sigma * sigma) * exp(-r2 / (2.0 * sigma * sigma));
            float bv = hash(c + 5.5);
            vec3 farbe = normalize(vec3(0.75 + 0.5 * bv, 1.0, 1.25 - 0.5 * bv)) * 1.7;
            sum += w * farbe;
        }
    return sum;
}

float kamm(float az) {
    return radians(1.2 + 2.6 * (0.5 + 0.5 * sin(3.0 * az + 1.0) * sin(7.0 * az + 0.4)) + 1.4 * max(0.0, sin(13.0 * az + 2.0)) * (0.5 + 0.5 * sin(2.0 * az)));
}

vec3 kamera(vec3 L) {
    float k = 0.18 * uZeit * uIso / (12.5 * uBlende * uBlende);
    return 1.0 - exp(-max(L, 0.0) * k);
}

vec3 auge(vec3 L) {
    float y = dot(L, vec3(0.2126, 0.7152, 0.0722));
    if (y <= 0.0) return vec3(0.0);
    float ys = dot(L, vec3(0.07, 1.1, 1.33)) / 2.5;
    float s = smoothstep(0.0, 1.0, (log(y) / log(10.0) + 3.0) / 2.5);
    float ym = mix(ys, y, s);
    vec3 farbe = L * (ym / y);
    vec3 v = mix(vec3(ym), farbe, s);
    return pow(max(v / uAnpassung, 0.0), vec3(0.6));
}

vec3 srgb(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(12.92 * c, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

void main() {
    vec3 d = viewRay(vUV);
    float el = asin(clamp(d.y, -1.0, 1.0)), az = atan(d.x, -d.z);
    vec3 L;
    if (el < kamm(az)) {
        // Berg oder Boden: Albedo 0,25 (Schnee und Fels gemischt) im Licht des Himmels (π · Leuchtdichte des Zenits)
        float schnee = el > 0.0 ? 0.25 : 0.08;
        L = vec3(schnee / 3.14159) * 3.14159 * uHimmel * 1.3 * vec3(0.82, 1.06, 0.92);
    } else {
        float grund = uHimmel * (1.0 + 1.2 * (1.0 - max(d.y, 0.0)));
        L = grund * vec3(0.82, 1.06, 0.92) + sterne(d) + texture(uAurora, vUV).rgb;
    }
    vec3 v = uAuge == 1 ? auge(L) : kamera(L);
    frag = vec4(srgb(v), 1.0);
}
