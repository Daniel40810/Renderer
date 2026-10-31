// terrain_fog_common.glsl — Nebel über Gelände, Zeile für Zeile wie com.dan.fog.TerrainFog.density (gleicher
// Ganzzahl-Hash wie fog_common.glsl). Seit Brocken Phase 6.
// uTfSea: Untergrenze, Obergrenze (m), σ (1/m), Wogen (± m) · uTfValley: Füllung, σ, —, — · uTfCap: Basis (m), σ, Gürtel (m), —
// uTfNoise: Schwadengröße (m), Schwaden 0..1, Versatz x, z (m) · uTfSeed · uTfMap (RGBA: Boden, Kaltluft, Gürtel) über uTfMapRect
// Seit Brocken Phase 7: Schattenwerfer im Sonnenlicht (uTfCast: je Körper zwei vec4, Achse a.xyz + Halbmesser, b.xyz)
// und die Sonne für ihren Schatten (uTfSun: Richtung zur Sonne, scheinbarer Halbmesser in rad), wie TerrainFog.sunShadow.
uniform vec4 uTfSea, uTfValley, uTfCap, uTfNoise;
uniform int uTfSeed;
uniform sampler2D uTfMap;
uniform vec4 uTfMapRect;    // x0, z0, Kantenlänge, Zellen je Seite
uniform vec4 uTfCast[16];
uniform int uTfCastN;
uniform vec4 uTfSun;

uint tfHash(int x, int z, int salt) {
    uint h = (uint(x) & 255u) * 0x8da6b343u ^ (uint(z) & 255u) * 0xd8163841u ^ uint(salt) * 0xcb1ab31fu;
    h ^= h >> 16;
    h *= 0x7feb352du;
    h ^= h >> 15;
    h *= 0x846ca68bu;
    h ^= h >> 16;
    return h;
}

float tfH01(int x, int z, int salt) { return float(tfHash(x, z, salt) >> 8) * (1.0 / 16777216.0); }

float tfNoise(vec2 p, int salt) {
    vec2 i0 = floor(p);
    ivec2 i = ivec2(i0);
    vec2 f = p - i0;
    f = f * f * (3.0 - 2.0 * f);
    float a = tfH01(i.x, i.y, salt), b = tfH01(i.x + 1, i.y, salt), c = tfH01(i.x, i.y + 1, salt), d = tfH01(i.x + 1, i.y + 1, salt);
    return a + (b - a) * f.x + (c - a) * f.y + (a - b - c + d) * f.x * f.y;
}

float tfSmooth(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// Karte am Ort: Boden, Kaltluft, Gürtel; w = 0 außerhalb
vec4 tfMap(vec2 xz) {
    vec2 m = (xz - uTfMapRect.xy) / uTfMapRect.z;
    if (m.x < 0.0 || m.y < 0.0 || m.x > 1.0 || m.y > 1.0) return vec4(0.0);
    return vec4(texture(uTfMap, m).rgb, 1.0);
}

float tfSeaTop(vec2 xz) {
    vec2 q = (xz + uTfNoise.zw) / uTfNoise.x;
    float n = 0.7 * tfNoise(q + vec2(3.0, 1.0), uTfSeed + 11) + 0.3 * tfNoise(q * 3.1 + vec2(7.0, 2.0), uTfSeed + 12);
    return uTfSea.y + uTfSea.w * (2.0 * n - 1.0);
}

// Dichte am Ort p, m = tfMap(p.xz), top = tfSeaTop(p.xz) (einmal je Punkt gerechnet)
float tfDensityMT(vec3 p, vec4 m, float top) {
    float d = 0.0;
    if (uTfSea.z > 1e-7) {
        d += uTfSea.z * tfSmooth(uTfSea.x - 15.0, uTfSea.x + 15.0, p.y) * tfSmooth(top + 6.0, top - 6.0, p.y);
    }
    if (m.w > 0.5) {
        float hag = p.y - m.x;
        if (uTfValley.x > 1e-3 && uTfValley.y > 1e-7 && m.y > 0.5 && hag > -3.0) {
            float depth = m.y * uTfValley.x;
            d += uTfValley.y * tfSmooth(depth + 4.0, depth * 0.75 - 2.0, hag);
        }
        if (uTfCap.y > 1e-7 && m.z > 0.5) {
            float lb = uTfCap.x - m.z;
            d += uTfCap.y * tfSmooth(lb - 15.0, lb + 25.0, p.y) * tfSmooth(uTfCap.x + 40.0, uTfCap.x, p.y);
        }
    }
    if (d <= 0.0) return 0.0;
    if (uTfNoise.y > 0.0) {
        float q = tfNoise(vec2((p.x + uTfNoise.z * 1.15) / 90.0 + p.y / 37.0, (p.z + uTfNoise.w * 1.15) / 90.0 - p.y / 29.0), uTfSeed + 5);
        d *= max(0.0, 1.0 + uTfNoise.y * (q - 0.5) * 1.6);
    }
    return d;
}

float tfDensityM(vec3 p, vec4 m) { return tfDensityMT(p, m, uTfSea.z > 1e-7 ? tfSeaTop(p.xz) : 0.0); }

float tfDensity(vec3 p) { return tfDensityM(p, tfMap(p.xz)); }

// Seit Brocken Phase 7: Anteil direkten Sonnenlichts am Ort p hinter den Schattenwerfern (1 = frei). Je Körper eine
// Kapsel; ihr Schatten ist eine Säule von der Sonne weg, deren Rand mit dem Abstand s um s · tan(Sonnenhalbmesser)
// weich wird. Hinter dem Kernschatten bleibt nur der Halbschatten, flach wie (r / Rand)². Wie TerrainFog.sunShadow.
float tfSunShadow(vec3 p) {
    float lit = 1.0;
    for (int i = 0; i < uTfCastN; i++) {
        vec3 a = uTfCast[2 * i].xyz, b = uTfCast[2 * i + 1].xyz;
        float r = uTfCast[2 * i].w;
        vec3 L = uTfSun.xyz;
        // Achse und Punkt in die Ebene senkrecht zur Sonne
        vec3 pa = p - a, ab = b - a;
        float s = -dot(pa, L);                       // wie weit hinter dem Körper (von der Sonne aus)
        if (s <= 0.0) continue;
        vec3 q = pa - L * dot(pa, L);                // in der Ebene senkrecht zur Sonne
        vec3 e = ab - L * dot(ab, L);
        float ee = dot(e, e);
        float h = ee > 1e-8 ? clamp(dot(q, e) / ee, 0.0, 1.0) : 0.0;
        float dist = length(q - e * h);
        float w = max(1e-3, s * tan(uTfSun.w));
        float occ = clamp((r + w - dist) / (2.0 * w), 0.0, 1.0) * min(1.0, (r * r) / (w * w));
        lit *= 1.0 - occ;
    }
    return lit;
}
