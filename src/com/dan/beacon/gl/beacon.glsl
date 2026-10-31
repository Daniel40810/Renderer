// beacon.glsl — Baustein von com.dan.beacon für fremde Shader (nach der Versionszeile einsetzen oder einbinden).
// Liest die Lichtverteilung des Feuers (Rundumbild: Azimut von Nord, Höhenwinkel quadratisch) und das Nahfeld der
// Linse. BeaconGl.bind() setzt alle bcn*-Größen. Lichtstärken in cd, Beleuchtungsstärken in lx, mal bcnPre
// (Vorbelichtung der Szene). Lage: x Osten, y oben, z Süden.
uniform sampler2D bcnPattern;   // RGB cd je Richtung vom Feuer
uniform sampler2D bcnNear;      // Nahfeld der Linse: R = Fluss je m Höhe und rad Azimut je lm, G = Steigung dy/dx
uniform sampler1D bcnLut;       // Sektortabelle RGB cd je Peilung von See (0,5°)
uniform vec4 bcnPos;            // Linsenmitte xyz, Helligkeit 0..1 (an/aus, Kennung bei der Trommel)
uniform vec4 bcnNearP;          // r0, r1, y0, y1 des Nahfelds (m)
uniform vec4 bcnLens;           // Außenhalbmesser, halbe Höhe der Linse, Halbmesser der Farbscheiben, Lichtstrom in lm
uniform vec4 bcnRot;            // Optik (0 Trommel, 1 Drehlinse), Drehwinkel (Grad), Breite einer Ringlinse (m), Unschärfe je m
uniform vec4 bcnTint;           // Farbe bei Wechselfeuern (sonst 1), w = stärkste Sektor-Lichtstärke (cd)
uniform float bcnPre;           // Vorbelichtung
uniform float bcnPanels[64];    // Lage der Ringlinsen (Grad, relativ zum Drehwinkel)
uniform int bcnPanelCount;

const float BCN_PI = 3.14159265359;

// Rundumbild: u = Azimut / 360°, v = ½ + ½ · sign(el) · √(|el| / 90°)
vec2 bcnUV(vec3 w) {
    float az = atan(w.x, -w.z) / (2.0 * BCN_PI);
    float el = degrees(asin(clamp(w.y, -1.0, 1.0)));
    return vec2(fract(az + 1.0), 0.5 + 0.5 * sign(el) * sqrt(abs(el) / 90.0));
}

// Lichtstärke (cd · bcnPre) in Richtung w (Einheitsvektor vom Feuer weg).
vec3 bcnIntensity(vec3 w) {
    return textureLod(bcnPattern, bcnUV(w), 0.0).rgb * bcnPre;
}

// Leuchtdichte der Linse (cd/m² · bcnPre), von außen aus Richtung w gesehen (vom Feuer zum Auge).
// Dazu das Grundleuchten: 1 % des Lampenlichts streut im Glas (Beacon.Snapshot.lensGlow).
vec3 bcnLensLuminance(vec3 w) {
    float glow = bcnLens.w * bcnPos.w * 0.01 / (BCN_PI * 2.0 * BCN_PI * bcnLens.x * 2.0 * bcnLens.y);
    return bcnIntensity(w) / (4.0 * bcnLens.x * bcnLens.y) + bcnTint.rgb * glow * bcnPre;
}

float bcnWrap180(float a) { return a - 360.0 * floor((a + 180.0) / 360.0); }

// Beleuchtungsstärke (lx · bcnPre) im Laternenraum am Ort p, Flächennormale n zum Raum hin. Wie
// Beacon.Snapshot.nearIrradiance: Trommel verteilt den Fluss je rad Azimut auf den Bogen r, eine Ringlinse hält ihre Breite.
vec3 bcnNearIrradiance(vec3 p, vec3 n) {
    vec3 rel = p - bcnPos.xyz;
    float r = length(rel.xz);
    if (r < 1e-4 || bcnPos.w <= 0.01) return vec3(0.0);
    vec2 ts = vec2(textureSize(bcnNear, 0));
    vec2 uv = vec2((r - bcnNearP.x) / (bcnNearP.y - bcnNearP.x), (rel.y - bcnNearP.z) / (bcnNearP.w - bcnNearP.z));
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec3(0.0);
    vec2 fs = textureLod(bcnNear, (uv * (ts - 1.0) + 0.5) / ts, 0.0).rg;
    vec2 u = rel.xz / r;
    float e = max(0.0, -(n.x * u.x + n.y * fs.y + n.z * u.y));
    float base;
    float az = degrees(atan(rel.x, -rel.z));
    if (bcnRot.x > 0.5) {
        float blur = max(0.01, r * bcnRot.w), h = 0.0, pw = bcnRot.z * 0.5;
        for (int i = 0; i < 64; i++) {
            if (i >= bcnPanelCount) break;
            float d = radians(bcnWrap180(az - bcnPanels[i] - bcnRot.y));
            if (cos(d) <= 0.0) continue;
            float lu = r * sin(d);
            h += smoothstep(-pw - blur, -pw + blur, lu) * (1.0 - smoothstep(pw - blur, pw + blur, lu));
        }
        base = fs.x / bcnLens.x * h;
    } else {
        base = fs.x / r;
    }
    vec3 c = bcnTint.rgb;
    if (r > bcnLens.z && bcnTint.w > 0.0) {
        float b = fract((az + 180.0) / 360.0 + 1.0);
        c *= textureLod(bcnLut, b, 0.0).rgb / bcnTint.w;
    }
    return c * base * e * bcnLens.w * bcnPos.w * bcnPre;
}
