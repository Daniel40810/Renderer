// beam.frag — der sichtbare Strahl: Einfachstreuung des Feuers längs jedes Blicks bis zur Szene (oder bis zur
// Wolkenuntergrenze), in verkleinerter Auflösung. Gleichwinklige Abtastung um das Feuer wie
// Beacon.Snapshot.beamRadiance: t = Δ + D·tan θ, das 1/r² fällt heraus, die Schritte liegen nah am Feuer dicht.
// Streudichte aus dem Kugelgitter (Regen, Gischt, Nebel) plus Dunst; Streufunktion zweier Henyey-Greenstein-Keulen.
// Trifft der Blick die Wolkenuntergrenze, leuchtet sie, wo der Strahl sie erreicht. Ausgabe: RGB vorbelichtet, A = Weg.
#include "beacon.glsl"
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform sampler2D uDepth;       // Tiefe der Szene (volle Auflösung)
uniform int uReversed;
uniform sampler3D uGrid;        // Streukoeffizient 1/m (Azimut, Höhe, Abstand)
uniform vec3 uGridP;            // größter Höhenwinkel (Grad), r0, r1
uniform float uHaze;            // Dunst 1/m
uniform int uSteps;
uniform float uMaxDist;
uniform float uJitter;          // < 0: Rauschen je Bildpunkt, sonst fester Versatz 0..1
uniform int uFrame;
uniform vec4 uCeil;             // Höhe der Wolkenuntergrenze, Albedo, an (1)/aus, —
uniform sampler2D uCeilMap;     // Anteil durchgelassenen Lichts (1 = klar)
uniform vec4 uCeilRect;         // x, z der Ecke, Kantenlänge, an
uniform vec2 uCheckRays;        // > 0: Prüfmodus (Strahlen aus uRay*)
uniform vec4 uRayO[64];
uniform vec4 uRayD[64];

float hg(float c, float g) { return (1.0 - g * g) / (4.0 * BCN_PI * pow(1.0 + g * g - 2.0 * g * c, 1.5)); }
float phaseF(float c) { return 0.85 * hg(c, 0.8) + 0.15 * hg(c, -0.3); }

float gridSigma(vec3 w, float r) {
    float az = atan(w.x, -w.z) / (2.0 * BCN_PI);
    float el = degrees(asin(clamp(w.y, -1.0, 1.0)));
    float e = clamp(el / uGridP.x, -1.0, 1.0);
    float v = 0.5 + 0.5 * sign(e) * sqrt(abs(e));
    float wr = log(clamp(r, uGridP.y, uGridP.z) / uGridP.y) / log(uGridP.z / uGridP.y);
    return textureLod(uGrid, vec3(fract(az + 1.0), v, wr), 0.0).r;
}

// Rauschen je Bildpunkt (Interleaved Gradient Noise), läuft mit dem Bild weiter
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)) + 0.1 * float(uFrame % 16))); }

uniform float uWedge;           // halbe Keilbreite um einen Strahl der Drehlinse (Grad)
uniform int uPanelSteps;

// Gleichwinklige Schritte auf t1..t2 (wie Beacon.Snapshot.segment)
vec3 segment(vec3 o, vec3 d, float t1, float t2, int steps, float jit) {
    vec3 L = bcnPos.xyz;
    float delta = dot(L - o, d);
    float D = max(0.05, length(o + d * delta - L));
    float ta = atan((t1 - delta) / D), tb = atan((t2 - delta) / D);
    float dth = (tb - ta) / float(steps);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 256; i++) {
        if (i >= steps) break;
        float th = ta + (float(i) + jit) * dth;
        float t = delta + D * tan(th);
        vec3 q = o + d * t - L;
        float r = max(1e-3, length(q));
        vec3 w = q / r;
        float sig = uHaze + gridSigma(w, r);
        vec3 I = textureLod(bcnPattern, bcnUV(w), 0.0).rgb;
        float cs = -dot(w, d);
        float tr = exp(-sig * (r + max(0.0, t)));
        acc += I * (sig * phaseF(cs) * tr * dth / D);
    }
    return acc;
}

bool inWedge(vec2 p, float alpha, float w) {
    if (dot(p, p) < 1e-12) return true;
    return abs(bcnWrap180(degrees(atan(p.x, -p.y)) - alpha)) <= w;
}

// Stück des Blicks im Keil alpha ± w (wie Beacon.Snapshot.wedge); x > y: leer
vec2 wedge(vec3 o, vec3 d, float alpha, float w, float tmax) {
    vec2 a = o.xz - bcnPos.xz, dd = d.xz;
    float lo = 1e30, hi = -1e30;
    if (inWedge(a, alpha, w)) { lo = 0.0; hi = 0.0; }
    if (inWedge(a + dd * tmax, alpha, w)) { lo = min(lo, tmax); hi = max(hi, tmax); }
    for (int k = -1; k <= 1; k += 2) {
        float b = radians(alpha + float(k) * w);
        vec2 u = vec2(sin(b), -cos(b));
        float det = u.x * dd.y - dd.x * u.y;
        if (abs(det) < 1e-12) continue;
        float t = (a.x * u.y - u.x * a.y) / det, s = (dd.y * a.x - dd.x * a.y) / det;
        if (s >= 0.0 && t >= 0.0 && t <= tmax) { lo = min(lo, t); hi = max(hi, t); }
    }
    return vec2(lo, hi);
}

vec3 march(vec3 o, vec3 d, float tmax, float jit) {
    if (bcnRot.x < 0.5) return segment(o, d, 0.0, tmax, uSteps, jit);
    vec3 acc = vec3(0.0);
    for (int j = 0; j < 64; j++) {
        if (j >= bcnPanelCount) break;
        vec2 iv = wedge(o, d, bcnPanels[j] + bcnRot.y, uWedge, tmax);
        if (iv.y > iv.x) acc += segment(o, d, iv.x, iv.y, uPanelSteps, jit);
    }
    return acc;
}

void main() {
    if (uCheckRays.x > 0.0) {
        int i = int(gl_FragCoord.x) + int(gl_FragCoord.y) * 8;
        vec3 L = march(uRayO[i].xyz, normalize(uRayD[i].xyz), uRayO[i].w, 0.5);
        frag = vec4(L, 1.0);
        return;
    }
    if (bcnPos.w <= 0.01) { frag = vec4(0.0); return; }
    float dz = textureLod(uDepth, vUV, 0.0).r;
    vec4 c = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? 0.5 : 0.0, 1.0);
    vec3 far = c.xyz / c.w;
    vec3 d = normalize(far - uCam);
    bool sky = uReversed == 1 ? dz <= 0.0 : dz >= 1.0;
    float tmax = uMaxDist;
    if (!sky) {
        vec4 p = uInvVP * vec4(vUV * 2.0 - 1.0, uReversed == 1 ? dz : dz * 2.0 - 1.0, 1.0);
        tmax = min(uMaxDist, length(p.xyz / p.w - uCam));
    }
    // Wolkenuntergrenze: der Blick endet dort, die Unterseite leuchtet im Strahl
    vec3 ceilCol = vec3(0.0);
    if (uCeil.z > 0.5 && d.y > 1e-4 && uCam.y < uCeil.x) {
        float tc = (uCeil.x - uCam.y) / d.y;
        if (tc < tmax) {
            tmax = tc;
            vec3 P = uCam + d * tc;
            vec3 q = P - bcnPos.xyz;
            float r = length(q);
            vec3 w = q / r;
            float cover = 1.0;
            if (uCeilRect.w > 0.5) {
                vec2 cu = (P.xz - uCeilRect.xy) / uCeilRect.z;
                cover = all(greaterThan(cu, vec2(0.0))) && all(lessThan(cu, vec2(1.0))) ? 1.0 - textureLod(uCeilMap, cu, 0.0).r : 0.5;
            }
            float trc = exp(-(uHaze + gridSigma(w, r)) * (r + tc));
            vec3 E = textureLod(bcnPattern, bcnUV(w), 0.0).rgb * max(0.0, w.y) / (r * r);
            ceilCol = E * uCeil.y / BCN_PI * cover * trc;
        }
    }
    float jit = uJitter >= 0.0 ? uJitter : ign(gl_FragCoord.xy);
    vec3 L = march(uCam, d, tmax, jit) + ceilCol;
    frag = vec4(min(L * bcnPre, vec3(60000.0)), tmax);
}
