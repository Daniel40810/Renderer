// precip_common.glsl — die Physik von com.dan.precip.Drops Zeile für Zeile, dazu Zufall, Karte der Schauer, Raster des
// Grunds, Unterstände, Optik (Phasentabelle, Umgebung im Tropfen). Alles in SI, Durchmesser in mm.
uniform vec4 uPA;        // Auge x, y, z, halbe Kastenbreite
uniform vec4 uPB;        // halbe Kastenhöhe, Zeit (s, im Kreis), dt (s), Belichtung (s, in der Zeit der Tropfen)
uniform vec4 uPC;        // Wind x, z (m/s), Böe x, z (m/s)
uniform vec4 uPD;        // Regen (mm/h), Λ (1/mm), N0 (m⁻³ mm⁻¹), dmin (mm)
uniform vec4 uPE;        // dmax (mm), Wirbel (m/s), Faktor dünne Luft (ρ0/ρ)^0,4, Wolkenuntergrenze (m)
uniform vec4 uPF;        // Verteilung a, b (Λ = a R^b), Exponent von N0, Wasserspiegel (m)
uniform vec4 uPG;        // Wald: Durchfall, Verdunstung, Tropfgröße von, bis (mm)
uniform vec4 uPMap;      // Karte: Ecke x, z, Zelle (m), Zellen je Seite (0 = überall gleich)
uniform vec4 uPGround;   // Grund: Ecke x, z, Kante (m), Zellen je Seite
uniform vec4 uPBox;      // Tropfen im Kasten, Rauminhalt (m³), Blende (m), Schärfe (m)
uniform uint uPSeed;
uniform sampler2D uPMapTex;      // R32F: Schauer 0..1
uniform sampler2D uPGroundTex;   // RGBA32F: Höhe, Art (0 Wasser, 1 Grund), Kronendach, frei
uniform vec4 uPShelter[4];       // Unterstände: x, z, Halbmesser, Oberkante
uniform int uPShelterN;

const float P_G = 9.81, P_RHO_W = 998.2, P_SIGMA = 0.0728, P_MU_W = 1.002e-3, P_PI = 3.14159265359;

// ------------------------------------------------------------------ Zufall (wie Rain, Precip)

uint pHash(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float pRnd(uint seed, uint i, uint j) { return float(pHash(pHash(i * 16u + j) ^ seed) >> 8u) * (1.0 / 16777216.0); }

// ------------------------------------------------------------------ Verteilung und Fall

float pLambda(float rate) { return uPF.x * pow(max(0.01, rate), uPF.y); }
float pN0(float rate) { return uPD.z * pow(max(1e-4, rate / max(1e-4, uPD.x)), uPF.z); }
float pNumber(float n0, float lam, float d0, float d1) { return n0 / lam * (exp(-lam * d0) - exp(-lam * d1)); }
float pSample(float lam, float d0, float d1, float u) {
    float a = exp(-lam * d0), b = exp(-lam * d1);
    return -log(a - u * (a - b)) / lam;
}

float pStokes(float d) { return P_G * (d * 1e-3) * (d * 1e-3) * (P_RHO_W - 1.225) / (18.0 * 1.81e-5); }
float pBrandes(float d) {
    float c = min(d, 5.9);
    return -0.1021 + c * (4.932 + c * (-0.9551 + c * (0.07934 - 0.002362 * c)));
}
const float P_GK[7] = float[7](0.27, 0.72, 1.17, 1.62, 2.06, 2.47, 2.87);
float pMeasured(float d) {
    if (d <= 0.1) return pStokes(d) * (1.0 - (1.0 - 0.27 / pStokes(0.1)) * (d / 0.1) * (d / 0.1));
    float x = (d - 0.1) / 0.1;
    int i = min(5, int(x));
    return mix(P_GK[i], P_GK[i + 1], x - float(i));
}
// Fallgeschwindigkeit am Boden (Gunn und Kinzer, Brandes u. a.) mal Faktor der dünnen Luft
float pTerminal(float d) {
    if (d <= 0.0) return 0.0;
    float v = d >= 0.8 ? pBrandes(d) : d <= 0.6 ? pMeasured(d) : mix(pMeasured(d), pBrandes(d), (d - 0.6) / 0.2);
    return v * uPE.z;
}
float pAxisRatio(float d) {
    float c = min(d, 8.0) * 0.1;
    return min(1.0, 1.0048 + c * (0.0057 + c * (-2.628 + c * (3.682 - 1.677 * c))));
}
float pOscHz(float d) {
    float r = d * 0.5e-3;
    return sqrt(8.0 * P_SIGMA / (P_RHO_W * r * r * r)) / (2.0 * P_PI);
}
float pWeber(float d, float v) { return P_RHO_W * v * v * d * 1e-3 / P_SIGMA; }
float pSplashK(float d, float v) {
    float oh = P_MU_W / sqrt(P_RHO_W * P_SIGMA * d * 1e-3), re = P_RHO_W * v * d * 1e-3 / P_MU_W;
    return oh * pow(re, 1.25);
}
// Art des Einschlags wie Splash.kind: 0 fließt ein, 1 Strahl, 2 Krone, 3 haftet, 4 spritzt
int pKind(float d, float v, bool water, bool wet) {
    if (water) {
        float we = pWeber(d, v);
        if (d < 0.6 || we < 40.0) return 0;
        if (d < 2.6 || we < 900.0) return 1;
        return 2;
    }
    return pSplashK(d, v) * (wet ? 1.6 : 1.0) > 57.7 ? 4 : 3;
}

// Fußabdruck (m) eines Punkts im Abstand z: Bildpunkt pw und Unschärfekreis der Blende (dünne Linse), quadratisch addiert
float pFootprint(float pw, float z) {
    float b = uPBox.z * abs(1.0 - z / max(0.05, uPBox.w));
    return sqrt(pw * pw + b * b);
}

// ------------------------------------------------------------------ Karte, Grund, Unterstand

// Schauer 0..1 am Ort: bilinear zwischen den Zellmitten (Textur mit linearem Filter, am Rand fest), wie Snapshot.curtainAt
float pCurtain(vec2 xz) {
    if (uPMap.w < 0.5) return 1.0;
    return texture(uPMapTex, (xz - uPMap.xy) / (uPMap.z * uPMap.w)).r;
}
vec4 pGround(vec2 xz) {
    int n = int(uPGround.w + 0.5);
    ivec2 c = ivec2(floor((xz - uPGround.xy) / uPGround.z * float(n)));
    return texelFetch(uPGroundTex, clamp(c, ivec2(0), ivec2(n - 1)), 0);
}
// Höhe der Fläche, bilinear (Grund und Wasser), für den Aufschlag
float pSurface(vec2 xz) {
    int n = int(uPGround.w + 0.5);
    vec2 g = (xz - uPGround.xy) / uPGround.z * float(n) - 0.5;
    vec2 f = floor(g), t = g - f;
    ivec2 i = ivec2(f);
    float a = texelFetch(uPGroundTex, clamp(i, ivec2(0), ivec2(n - 1)), 0).r, b = texelFetch(uPGroundTex, clamp(i + ivec2(1, 0), ivec2(0), ivec2(n - 1)), 0).r;
    float c = texelFetch(uPGroundTex, clamp(i + ivec2(0, 1), ivec2(0), ivec2(n - 1)), 0).r, d = texelFetch(uPGroundTex, clamp(i + ivec2(1), ivec2(0), ivec2(n - 1)), 0).r;
    return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}
bool pSheltered(vec3 p) {
    for (int i = 0; i < 4; i++) {
        if (i >= uPShelterN) break;
        vec4 s = uPShelter[i];
        vec2 d = p.xz - s.xy;
        if (dot(d, d) < s.z * s.z && p.y < s.w) return true;
    }
    return false;
}

// Anteil der simulierten Tropfen, die bei der Regenmenge am Ort leben (wie Snapshot.liveFraction)
float pLive(float localRate) {
    if (localRate <= 0.0 || uPD.x <= 0.0) return 0.0;
    float lf = pLambda(localRate), nf = pN0(localRate), full = uPD.z / uPD.y * exp(-uPD.y * uPD.w);
    return min(1.0, nf / lf * exp(-lf * uPD.w) / max(1e-12, full));
}
// unter dem Kronendach: abgetropfte große Tropfen (wie Snapshot.dripFraction)
float pDrip(float localRate) {
    if (localRate <= 0.0) return 0.0;
    float d = 0.5 * (uPG.z + uPG.w), flux = localRate / 3.6e6 * max(0.0, 1.0 - uPG.x - uPG.y);
    float n = flux / (P_PI / 6.0 * pow(d * 1e-3, 3.0) * pTerminal(d));
    return min(1.0, n / max(1e-9, uPBox.x / uPBox.y));
}

// ------------------------------------------------------------------ Wind um das Auge

float pVn(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    uvec3 c = uvec3(ivec3(i) + 32768);
    float a = float(pHash(c.x + pHash(c.y + pHash(c.z))) >> 8u), b = float(pHash(c.x + 1u + pHash(c.y + pHash(c.z))) >> 8u);
    float e = float(pHash(c.x + pHash(c.y + 1u + pHash(c.z))) >> 8u), g = float(pHash(c.x + 1u + pHash(c.y + 1u + pHash(c.z))) >> 8u);
    float a2 = float(pHash(c.x + pHash(c.y + pHash(c.z + 1u))) >> 8u), b2 = float(pHash(c.x + 1u + pHash(c.y + pHash(c.z + 1u))) >> 8u);
    float e2 = float(pHash(c.x + pHash(c.y + 1u + pHash(c.z + 1u))) >> 8u), g2 = float(pHash(c.x + 1u + pHash(c.y + 1u + pHash(c.z + 1u))) >> 8u);
    float lo = mix(mix(a, b, u.x), mix(e, g, u.x), u.y), hi = mix(mix(a2, b2, u.x), mix(e2, g2, u.x), u.y);
    return mix(lo, hi, u.z) * (2.0 / 16777216.0) - 1.0;
}
// Wind am Ort: Mittel, Böe, kleine Wirbel (1,5 m, 0,7 s) [G]; unter dem Kronendach nur ein Fünftel
vec3 pWind(vec3 p, float t) {
    vec3 w = vec3(uPC.x + uPC.z, 0.0, uPC.y + uPC.w);
    vec3 q = p / 1.5 + vec3(0.0, 0.0, t / 0.7);
    w += uPE.y * vec3(pVn(q), 0.5 * pVn(q + vec3(17.3, 5.1, 3.7)), pVn(q + vec3(-9.1, 31.7, 11.3)));
    return w;
}

// ------------------------------------------------------------------ Optik

uniform sampler2D uPPhase;   // 720 × 2 RGBA16F: Zeile 0 Phasenfunktion (1/sr) je 0,25°, Zeile 1 geglättet (für die Umgebung)
uniform sampler2D uPEnv;     // 64 × 32 RGBA16F: ∫ L p dΩ je Blickrichtung (wie das Rundumbild abgebildet)
uniform vec3 uPSunDir, uPSunE, uPMoonDir, uPMoonE;   // Richtung zur Quelle, Bestrahlung (vorbelichtet)

vec3 pPhase(float cosTheta, float row) {
    float th = degrees(acos(clamp(cosTheta, -1.0, 1.0)));
    return texture(uPPhase, vec2(th / 180.0, (row + 0.5) / 2.0)).rgb;
}
vec2 pPanoUv(vec3 d) { return vec2(atan(d.x, -d.z) / 6.28318530718 + 0.5, asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5); }
// Strahldichte eines Tropfens (über die Scheibe gemittelt), den man in Richtung V (vom Auge weg) sieht; sun: Anteil
// der Sonne, der am Tropfen ankommt
vec3 pDropRadiance(vec3 V, float sun) {
    vec3 L = texture(uPEnv, pPanoUv(V)).rgb;
    L += uPSunE * pPhase(dot(V, uPSunDir), 0.0) * sun;
    L += uPMoonE * pPhase(dot(V, uPMoonDir), 0.0);
    return L;
}
