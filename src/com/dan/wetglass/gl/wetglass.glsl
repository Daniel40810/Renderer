// wetglass.glsl — Baustein für einen fremden Fragment-Shader, der eine nasse Scheibe zeichnet (com.dan.wetglass).
// Die Karte enthält je Ort: Höhe des Wassers in mm, nassen Film 0..1, Steigung dh/dx und dh/dy. Dazu kommen die
// Feintropfen: Ihre Bedeckung steht grob in uWgFilm (Kanal G), gezeichnet werden sie hier aus Zellen von 1,1 mm,
// unabhängig von der Auflösung der Karte. Ein Tropfen ist eine Linse: Er zeigt die Welt dahinter kopfüber und
// verkleinert. Darum wird der Hintergrund dort in Richtung der Steigung (zur Mitte des Tropfens) verschoben, um so
// mehr, je steiler; am Rand wird der Tropfen dunkel, weil das Licht dort im Wasser zurückgeworfen wird.
uniform sampler2D uWgMap;     // RGBA16F mit Mipmaps: Höhe mm, Film, dh/dx, dh/dy
uniform sampler2D uWgFilm;    // RGBA32F grob: Film, Bedeckung mit Feintropfen, Beschlag innen, Salz außen
uniform vec4 uWgSheet;        // Wasserhaut einer Überspülung: Dicke mm, Zeit s, Fließrichtung x, y
uniform vec2 uWgSize;         // Fläche in m
uniform float uWgSeed;

const float WG_FINE_CELL = 0.0011;

float wgHash(vec2 c, float k) { return fract(sin(dot(c + uWgSeed, vec2(127.1 + k, 311.7 - k * 3.1))) * 43758.5453); }

// Feintropfen am Ort p: (Höhe mm, 0, dh/dx, dh/dy); sie verblassen, wenn ein Bildpunkt größer ist als eine Zelle
vec4 wgFine(vec2 p) {
    float cov = texture(uWgFilm, p / uWgSize).g;
    float px = max(fwidth(p.x), fwidth(p.y));
    float detail = 1.0 - smoothstep(WG_FINE_CELL * 0.12, WG_FINE_CELL * 0.4, px);
    if (cov < 0.01 || detail <= 0.0) return vec4(0.0);
    vec2 c = floor(p / WG_FINE_CELL);
    vec4 best = vec4(0.0);
    const float S = 0.86602540378, C = 0.5, K = 1.0 / (S * S);
    for (int dy = -1; dy <= 1; dy++)
        for (int dx = -1; dx <= 1; dx++) {
            vec2 cc = c + vec2(dx, dy);
            if (wgHash(cc, 1.0) > cov) continue;
            vec2 ctr = (cc + 0.15 + 0.7 * vec2(wgHash(cc, 2.0), wgHash(cc, 3.0))) * WG_FINE_CELL;
            float a = (0.08 + 0.42 * pow(wgHash(cc, 4.0), 2.0)) * 1e-3 * (0.6 + 0.6 * cov);
            vec2 d = p - ctr;
            float q2 = dot(d, d) / (a * a);
            if (q2 >= 1.0) continue;
            float w = sqrt(K - q2);
            float h = (w - C / S) * a * 1000.0;
            if (h > best.x) best = vec4(h, 0.0, -(d / a) / w);
        }
    best.x *= detail;
    best.zw *= detail;
    return best;
}

// Wie milchig die Feintropfen die Scheibe machen, wo sie zu klein für einen Bildpunkt sind (0..1)
float wgHaze(vec2 p) {
    float cov = texture(uWgFilm, p / uWgSize).g;
    float px = max(fwidth(p.x), fwidth(p.y));
    return cov * smoothstep(WG_FINE_CELL * 0.12, WG_FINE_CELL * 0.4, px);
}

// Wertrauschen 0..1 (weich), für Beschlag und Salz
float wgNoise(vec2 q) {
    vec2 i = floor(q), f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float a = wgHash(i, 7.0), b = wgHash(i + vec2(1, 0), 7.0), c = wgHash(i + vec2(0, 1), 7.0), d = wgHash(i + vec2(1, 1), 7.0);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

// Wasserhaut einer Überspülung am Ort p: (Höhe mm, 0, dh/dx, dh/dy). Wellen laufen in Fließrichtung ab, quer dazu
// zerfällt die Haut in Rinnsale.
vec4 wgSheet(vec2 p) {
    float h0 = uWgSheet.x;
    if (h0 <= 0.0) return vec4(0.0);
    vec2 q = p * 1000.0, fl = normalize(uWgSheet.zw + vec2(1e-6)), cr = vec2(-fl.y, fl.x);
    float t = uWgSheet.y;
    // unregelmäßig: der Ort wird verwirbelt (Wirbel ziehen mit dem Wasser), die Höhe der Wellen schwankt
    vec2 drift = fl * t * 60.0;
    vec2 warp = vec2(wgNoise((q - drift) / 23.0), wgNoise((q - drift) / 23.0 + 9.7)) - 0.5;
    q += warp * 9.0;
    float m = 0.45 + 1.1 * wgNoise((q - drift * 1.5) / 31.0 + 3.1);
    float a = dot(q, fl), b = dot(q, cr);
    // drei Wellen längs (Wellenlänge 5, 8, 13 mm, 120..180 mm/s) und eine quer (Rinnsale, 6 mm)
    float k1 = 6.2832 / 5.0, k2 = 6.2832 / 8.0, k3 = 6.2832 / 13.0, kc = 6.2832 / 6.0;
    float p1 = k1 * (a - 180.0 * t) + 1.7 * sin(b * 0.21), p2 = k2 * (a + 0.4 * b - 140.0 * t), p3 = k3 * (a - 0.3 * b - 120.0 * t);
    float pc = kc * b + 0.8 * sin(a * 0.05 - t * 2.0);
    // Kapillarwellen und Rinnsale: auch eine dünne Haut ist uneben (sonst sähe man sie nicht)
    float amp = (0.06 + 0.2 * h0) * m, ampc = (0.05 + 0.15 * h0) * (1.4 - m * 0.6);
    float h = h0 + amp * (sin(p1) * 0.5 + sin(p2) * 0.3 + sin(p3) * 0.2) + ampc * sin(pc);
    float da = amp * (cos(p1) * 0.5 * k1 + cos(p2) * 0.3 * k2 + cos(p3) * 0.2 * k3);
    float db = amp * (cos(p1) * 0.5 * k1 * 1.7 * 0.21 * cos(b * 0.21) + cos(p2) * 0.3 * k2 * 0.4 - cos(p3) * 0.2 * k3 * 0.3) + ampc * cos(pc) * kc;
    vec2 g = fl * da + cr * db;
    return vec4(max(h, 0.0), 0.0, g);
}

// Karte am Ort p (Flächenkoordinaten in m), mit den Feintropfen und der Wasserhaut
vec4 wgSample(vec2 p) {
    vec4 s = texture(uWgMap, p / uWgSize);
    vec4 f = wgFine(p);
    if (f.x > s.x) { s.x = f.x; s.zw = f.zw; }
    vec4 w = wgSheet(p);
    if (w.x > s.x) { s.x = w.x; s.zw = w.zw; s.y = 1.0; }
    return s;
}

// Beschlag auf der Innenseite am Ort p (0..1), mit feiner Körnung, wo die Pixel klein genug sind
float wgFog(vec2 p) {
    // Ränder gewischter Stellen nicht als Treppe der groben Zellen: Ort etwas verwirbeln
    vec2 j = vec2(wgNoise(p / 0.003), wgNoise(p / 0.003 + 17.0)) - 0.5;
    float f = texture(uWgFilm, (p + j * 0.004) / uWgSize).b;
    if (f <= 0.0) return 0.0;
    f = smoothstep(0.0, 1.0, f);
    float px = max(fwidth(p.x), fwidth(p.y));
    float grain = mix(1.0, 0.75 + 0.5 * wgNoise(p / 0.0004), 1.0 - smoothstep(0.0002, 0.001, px));
    return clamp(f * grain * (0.9 + 0.2 * wgNoise(p / 0.02)), 0.0, 1.0);
}

// Salzkruste außen am Ort p: Deckkraft 0..1 der weißen Kruste (fleckig in Ringen eingetrockneter Tropfen)
float wgSalt(vec2 p) {
    float a = texture(uWgFilm, p / uWgSize).a;
    if (a <= 0.0) return 0.0;
    float px = max(fwidth(p.x), fwidth(p.y));
    float fine = mix(0.5, wgNoise(p / 0.0006), 1.0 - smoothstep(0.0003, 0.0015, px));
    float blot = wgNoise(p / 0.004);
    return smoothstep(0.05, 0.9, a * (0.45 + 0.6 * blot + 0.5 * (fine - 0.5))) * 0.75;
}

// Wie deutlich der dunkle Rand gezeichnet werden darf: 1, solange ein Bildpunkt kleiner als 0,3 mm ist, 0 ab 1,5 mm
// (sonst flimmern die Ränder als dunkles Rauschen)
float wgRimDetail(vec2 p) { return 1.0 - smoothstep(0.3e-3, 1.5e-3, max(fwidth(p.x), fwidth(p.y))); }

// Anteil Tropfen am Ort (weich über die ersten Hundertstel Millimeter)
float wgCover(vec4 s) { return smoothstep(0.004, 0.03, s.x); }

// Wie steil der Tropfen am Ort ist: 0 in der Mitte, 1 am Rand (tan 60°)
float wgRim(vec4 s) { return clamp(length(s.zw) / 1.732, 0.0, 1.0); }

// Normale der Wasseroberfläche in Weltkoordinaten aus den Achsen der Fläche (T = +x, B = +y, N = nach außen zum Auge)
vec3 wgNormal(vec4 s, vec3 T, vec3 B, vec3 N) { return normalize(N - s.z * T - s.w * B); }

// Richtungen der Flächenachsen im Bild (UV je Bruchteil der Bildhöhe) aus den Ableitungen des Flächenorts
void wgScreenAxes(vec2 p, vec2 viewport, out vec2 ax, out vec2 ay) {
    vec2 dx = dFdx(p), dy = dFdy(p);           // m je Bildpunkt
    float det = dx.x * dy.y - dy.x * dx.y;
    if (abs(det) < 1e-14) { ax = vec2(viewport.y / viewport.x, 0.0); ay = vec2(0.0, 1.0); return; }
    // Bildpunkte je m entlang +x und +y der Fläche
    vec2 px = vec2(dy.y, -dx.y) / det, py = vec2(-dy.x, dx.x) / det;
    ax = normalize(px) * vec2(viewport.y / viewport.x, 1.0);
    ay = normalize(py) * vec2(viewport.y / viewport.x, 1.0);
}

// Verschiebung des Hintergrunds im Bild (UV), damit der Tropfen die Welt kopfüber zeigt. lens: halbes Bildfeld eines
// Tropfens als Bruchteil der Bildhöhe (rund 0,3).
vec2 wgOffset(vec4 s, vec2 ax, vec2 ay, float lens) { return (s.z * ax + s.w * ay) / 1.732 * lens; }
