// lamp.glsl — Baustein für fremde Shader: Licht der Leuchten (seit Timmendorf Phase 11, com.dan.lamp, F18 A).
// Vorher: #version und die Bindung des Puffers als LAMP_BINDING (Standard 7). Die Verteilungen sind dieselben wie in
// Lamp.shape (Java). Alle Werte linear und ohne Vorbelichtung: lampIrradiance gibt lx je Farbkanal, lampSpecular cd/m².
#ifndef LAMP_BINDING
#define LAMP_BINDING 7
#endif
struct LampData {
    vec4 pos;    // xyz, Verteilung (0 STREET, 1 ROUND, 2 DOWN, 3 GLOBE)
    vec4 col;    // Lichtstärke je Einheit der Form, je Farbkanal (cd); w: Reichweite (m)
    vec4 dir;    // Richtung zur Straße (x, z), leuchtende Fläche (m²), frei
};
layout(std430, binding = LAMP_BINDING) readonly buffer LampBuffer { LampData lamps[]; };
uniform int uLampCount;

float lampSmooth(float a, float b, float v) { float t = clamp((v - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }

// Form der Verteilung in Richtung d (vom Leuchtenmittelpunkt weg, Einheitsvektor), Straße in Richtung f.
float lampShape(int dist, vec3 d, vec2 f) {
    float c = -d.y;
    if (dist == 2) return c <= 0.0 ? 0.0 : c * c;
    if (dist == 3) return 1.0;
    float s = sqrt(max(0.0, 1.0 - c * c));
    if (dist == 1) return c >= 0.0 ? 0.35 + 0.65 * s : 0.15 * (1.0 + c);
    if (c <= 0.0) return 0.0;
    vec2 h = s > 1e-6 ? d.xz / s : vec2(0.0);
    float along = abs(h.x * f.y - h.y * f.x), toStreet = dot(h, f);
    float t = (acos(min(1.0, c)) - 1.1344640) / 0.1919862;      // (θ − 65°) / 11°
    return (c + 3.0 * exp(-0.5 * t * t) * along * along) * (1.0 + 0.35 * toStreet) * lampSmooth(0.035, 0.17, c);
}

// Beleuchtungsstärke (lx je Kanal) am Ort p mit Normale n, ohne Schatten.
vec3 lampIrradiance(vec3 p, vec3 n) {
    vec3 e = vec3(0.0);
    for (int i = 0; i < uLampCount; i++) {
        LampData L = lamps[i];
        vec3 v = p - L.pos.xyz;
        float d2 = max(dot(v, v), 1e-4);
        if (d2 > L.col.w * L.col.w) continue;
        vec3 d = v * inversesqrt(d2);
        float cosn = max(0.0, -dot(d, n));
        e += L.col.rgb * lampShape(int(L.pos.w), d, L.dir.xy) * cosn / d2;
    }
    return e;
}

float lampGgx(float nh, float a) { float a2 = a * a, d = nh * nh * (a2 - 1.0) + 1.0; return a2 / (3.14159265 * d * d); }
float lampVis(float nl, float nv, float a) {
    float a2 = a * a;
    return 0.5 / max(1e-6, nl * sqrt(nv * nv * (1.0 - a2) + a2) + nv * sqrt(nl * nl * (1.0 - a2) + a2));
}

// Spiegelnde Leuchtdichte (cd/m² je Kanal) am Ort p, Normale n, Richtung zum Auge v, GGX-α, F0 (Wasser 0,02).
vec3 lampSpecular(vec3 p, vec3 n, vec3 v, float alpha, float f0) {
    vec3 r = vec3(0.0);
    float nv = max(dot(n, v), 1e-4);
    for (int i = 0; i < uLampCount; i++) {
        LampData L = lamps[i];
        vec3 w = L.pos.xyz - p;
        float d2 = max(dot(w, w), 1e-4);
        vec3 l = w * inversesqrt(d2);
        float nl = dot(n, l);
        if (nl <= 0.0) continue;
        vec3 h = normalize(l + v);
        float F = f0 + (1.0 - f0) * pow(1.0 - max(dot(v, h), 0.0), 5.0);
        float brdf = lampGgx(max(dot(n, h), 0.0), alpha) * lampVis(nl, nv, alpha) * F;
        r += L.col.rgb * lampShape(int(L.pos.w), -l, L.dir.xy) * nl / d2 * brdf;
    }
    return r;
}
