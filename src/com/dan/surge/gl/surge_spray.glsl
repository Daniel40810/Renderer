// surge_spray.glsl — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W3b „Wand statt Ball“, W4b Kammgischt, W5 Gischt
// am Hang): Dichten der Gischt für einen Strahlengang des Wirts, aus der Flutkarte.
//   surgeSprayDensity: wo die Front ankommt, steht über dem Wasser eine Wand aus Gischt, umso höher, je höher die Welle
//     dort über dem Pegel aufläuft; sie steigt in 1,5 s, fällt in Sekunden zurück und verweht als Schleier. An Land nach
//     der Tiefe der Zunge (8 m + 1,3 × Tiefe, höchstens rund 60 m), kürzer und lichter. Dazu die Kammgischt, die von der
//     Krone abreißt und gegen die Laufrichtung zurückweht, in Fäden längs der Laufrichtung. Ergebnis: (Gischt, Schlamm) 1/m.
//   surgeTubeMask: vor und unter der Krone einer brechenden Welle (8–80 m, erste Sekunden) 1: dort lichten sich Gischt und
//     Staub, die sonst als Schleier vor der Röhre lägen [G].
// Uniforms: die Flutkarte (surge_flood.glsl), uSgSpray (an, Ereigniszeit jetzt, Nachlauf s), uSgBed (Grund, R32F) mit
// uSgBedRect (x0, z0, Kante; Zeilen nach Süden), uSgNoise (3D-Rauschen 64³, SurgeGl.noiseTexture, Texturstelle des Wirts).
#include "/com/dan/surge/gl/surge_flood.glsl"
uniform vec3 uSgSpray;
uniform sampler2D uSgBed;
uniform vec3 uSgBedRect;
uniform sampler3D uSgNoise;
float sgTex3(vec3 x) { return texture(uSgNoise, x / 64.0).r; }
float sgTexFbm2(vec3 p) { return sgTex3(p) * 0.67 + sgTex3(p * 2.03 + 7.1) * 0.33; }

float surgeTubeMask(vec3 p, float level) {
    if (p.y > level + 85.0) return 0.0;   // über jeder Krone: ohne Abfrage weiter (Kosten im Strahl)
    vec2 fu = vec2((p.x - uSgFloodRect.x) / uSgFloodRect.z, (uSgFloodRect.y - p.z) / uSgFloodRect.w);
    if (any(lessThan(fu, vec2(0.0))) || any(greaterThan(fu, vec2(1.0)))) return 0.0;
    vec2 am = textureLod(uSgFlood, fu, 0.0).rg;
    if (am.x > 1e5) return 0.0;
    float alter = uSgSpray.y - am.x, hw = am.y - level;
    return smoothstep(8.0, 14.0, hw) * (1.0 - smoothstep(60.0, 80.0, hw))
         * smoothstep(-6.0, -2.0, alter) * (1.0 - smoothstep(0.8, 2.5, alter)) * (1.0 - smoothstep(am.y - 0.15 * hw, am.y + 0.05 * hw, p.y));
}

vec2 surgeSprayDensity(vec3 p, float level) {
    // Ort der Abfrage um bis zu 25 m verschoben, damit die Wand keine senkrechten Kanten aus dem Gitter bekommt
    vec2 jit = (vec2(sgTex3(p / 40.0 + 11.0), sgTex3(p / 40.0 + 29.0)) - 0.5) * 50.0;
    vec2 fu = vec2((p.x + jit.x - uSgFloodRect.x) / uSgFloodRect.z, (uSgFloodRect.y - p.z - jit.y) / uSgFloodRect.w);
    if (any(lessThan(fu, vec2(0.0))) || any(greaterThan(fu, vec2(1.0)))) return vec2(0.0);
    vec2 am = textureLod(uSgFlood, fu, 0.0).rg;
    float alter = uSgSpray.y - am.x;
    if (alter < -0.3 || alter > uSgSpray.z) return vec2(0.0);
    float hw = am.y - level;                        // wie hoch die Welle hier über dem Pegel stand
    if (hw < 2.0) return vec2(0.0);
    vec2 gu = (p.xz - uSgBedRect.xy) / uSgBedRect.z;
    float grund = textureLod(uSgBed, gu, 0.0).r;
    float fuss = max(grund, level);
    float k = pow(clamp((hw - 2.0) / 60.0, 0.0, 1.0), 0.7);
    // am Hang (Auflauf) nach der Tiefe der Zunge; über dem Wasser nahe dem Hang, wo die Welle über 150 m aufläuft, dieselbe
    // Form wie an Land (Lituya W5: sonst lag der Hang hinter einem rosa Schleier)
    float land = max(smoothstep(level + 4.0, level + 40.0, grund), smoothstep(150.0, 260.0, hw) * 0.85);
    float tiefeMax = max(am.y - grund, 0.0);
    float hwE = mix(hw, min(tiefeMax, hw), smoothstep(level + 4.0, level + 40.0, grund));
    k = pow(clamp((hwE - 2.0) / 60.0, 0.0, 1.0), 0.7);
    // Wand: in rund 1,5 s auf ihre Höhe über dem höchsten Stand, dann fällt sie zurück; danach ein Schleier
    float H = mix(12.0 + 110.0 * k, 8.0 + min(1.3 * tiefeMax, 52.0), land);
    float auf = 1.0 - exp(-max(alter, 0.0) / 1.5);
    float top = am.y + H * auf - 4.9 * max(alter - 3.0, 0.0) * max(alter - 3.0, 0.0) * 0.35;
    float schleierTop = am.y + H * 0.8;
    float y = p.y;
    if (y < fuss - 3.0) return vec2(0.0);
    float wand = smoothstep(-0.3, 0.4, alter) * exp(-max(alter, 0.0) / mix(4.0, 2.0, land))
               * smoothstep(fuss - 3.0, fuss + 4.0, y) * (1.0 - smoothstep(top - 0.45 * H, top, y));
    // vor und unter der Krone, wo die Lippe die Röhre bildet, lichtet sich die Gischt [G]
    float roehre = surgeTubeMask(p, level);
    wand *= 1.0 - 0.85 * roehre;
    float schleier = smoothstep(2.0, 8.0, alter) * exp(-alter / 18.0) * 0.07
               * smoothstep(fuss - 3.0, fuss + 8.0, y) * (1.0 - smoothstep(fuss + 0.2 * (schleierTop - fuss), schleierTop + 30.0, y));
    schleier *= 1.0 - 0.75 * land;
    float d = (wand + schleier) * (1.0 - 0.45 * land);
    // Kammgischt [G]: von der Krone reißt Gischt ab und weht gegen die Laufrichtung zurück. Dicht über dem höchsten Stand,
    // steigt in rund 1 s bis gut die halbe Wellenhöhe, lebt einige Sekunden hinter der Front; Fäden längs der Laufrichtung
    float kammG = 0.0;
    if (alter > -0.2 && alter < 7.0 && hwE > 8.0) {
        float e = 16.0;
        vec2 ex = vec2(e / uSgFloodRect.z, 0.0), ez = vec2(0.0, e / uSgFloodRect.w);
        float ax = textureLod(uSgFlood, fu + ex, 0.0).r, bx = textureLod(uSgFlood, fu - ex, 0.0).r;
        float zp = textureLod(uSgFlood, fu - ez, 0.0).r, zm = textureLod(uSgFlood, fu + ez, 0.0).r;   // +z ist −v
        if (ax < 1e5 && bx < 1e5 && zp < 1e5 && zm < 1e5) {
            vec2 g = vec2(ax - bx, zp - zm);
            vec2 T = length(g) > 1e-5 ? normalize(g) : vec2(1.0, 0.0), Tq = vec2(-T.y, T.x);
            float lo = am.y - 0.25 * hwE, hi = am.y + hwE * (0.12 + 0.55 * (1.0 - exp(-max(alter, 0.0) / 1.0)));
            float env = smoothstep(-0.2, 0.3, alter) * exp(-max(alter, 0.0) / 2.2)
                      * smoothstep(lo, lo + 0.2 * hwE, p.y) * (1.0 - smoothstep(mix(lo, hi, 0.55), hi, p.y));
            env *= 1.0 - 0.9 * roehre;
            if (env > 1e-3) {
                vec3 qk = vec3(dot(p.xz, T) / 40.0 + uSgSpray.y * 0.9, p.y / 9.0, dot(p.xz, Tq) / 13.0);
                float fil = smoothstep(0.40, 0.72, sgTexFbm2(qk)) * (0.5 + 0.9 * smoothstep(0.35, 0.8, sgTex3(vec3(dot(p.xz, T) / 14.0 + uSgSpray.y * 2.0, p.y / 3.0, dot(p.xz, Tq) / 4.0))));
                kammG = env * fil * smoothstep(8.0, 30.0, hwE) * 0.25 * (1.0 - 0.6 * land);
            }
        }
    }
    if (d < 1e-3 && kammG < 1e-4) return vec2(0.0);
    // zerrissen: Fetzen, die steigen (Wand) und verwehen; senkrechte Strähnen [G]
    vec3 q = p / vec3(20.0, 64.0, 20.0) + vec3(0.0, -uSgSpray.y * 0.35, 0.0);
    float nv = sgTexFbm2(q + vec3(am.x * 0.05));
    float f = smoothstep(0.42, 0.66, nv) * (0.25 + 1.4 * smoothstep(0.3, 0.75, sgTex3(p / vec3(6.0, 26.0, 6.0) + vec3(0.0, -uSgSpray.y * 0.8, 0.0))));
    float dichte = d * f * k * 0.05 + kammG;
    float schlamm = wand * f * k * 0.02 * (1.0 - smoothstep(fuss + 10.0, fuss + 60.0, y)) * smoothstep(5.0, 40.0, hwE);
    return vec2(dichte, schlamm);
}
