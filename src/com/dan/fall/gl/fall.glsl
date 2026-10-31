// fall.glsl — com.dan.fall: die Bahn eines Pakets in geschlossener Form, Zeile für Zeile wie Fall.parcel.
// Einbinden über eine Include-Zeile mit dem absoluten Pfad /com/dan/fall/gl/fall.glsl; die Uniforms setzt FallGl.
//   fallParcel(i, t, vt, lat, fwd)   Ort (xyz) und Geschwindigkeit (w) eines Pakets der Strähne i zur Flugzeit t
//   Seit Phase 7 (Lauf über Fels, uCourse = 1): der Weg aus der Tabelle uFallPath (Zeile = Strähne, 192 Proben in
//   gleichen Zeitschritten bis zum Aufprall), w negativ, wo das Wasser auf dem Fels liegt; wie Fall.course.
uniform vec4 uStrandA[16];   // Fuß auf der Kante x, y, z; Geschwindigkeit am Abriss
uniform vec4 uStrandB[16];   // Zerfallszeit, Endgeschwindigkeit der Pakete, Zeit bis zum Aufprall, Breite
uniform vec4 uStrandC[16];   // Startwert / 2^20, Lage auf der Kante, Zerfallshöhe, auf Wasser (1)
uniform vec4 uStrandI[16];   // Aufprall x, y, z, Geschwindigkeit
uniform int uStrands;
uniform vec3 uFallDir, uFallWind, uFallAlong;
uniform vec2 uFallPhys;      // g, Endgeschwindigkeit des Strahls
uniform float uIce;          // Eisanteil 0..1
uniform int uCourse;         // 1: Lauf über Fels (Weg aus uFallPath)
uniform sampler2D uFallPath; // RGBA32F, 192 × 16: x, y, z, Geschwindigkeit (negativ auf dem Fels)
const int FALL_PATH = 192;

float fallLnCosh(float x) { float a = abs(x); return a + log(1.0 + exp(-2.0 * a)) - 0.693147181; }
float fallLnSinh(float x) { return x + log(1.0 - exp(-2.0 * x)) - 0.693147181; }
float fallAtanh(float x) { return 0.5 * log((1.0 + x) / (1.0 - x)); }

// senkrecht: Strecke nach unten und Geschwindigkeit nach unten nach t, Start mit v0, Endgeschwindigkeit vt
vec2 fallDrop(float vt, float v0, float t, float g) {
    float s = g * t / vt;
    if (v0 < vt * 0.9999) {
        float a = fallAtanh(v0 / vt);
        return vec2(vt * vt / g * (fallLnCosh(a + s) - fallLnCosh(a)), vt * tanh(a + s));
    }
    float b = fallAtanh(vt / max(v0, vt * 1.0001));
    return vec2(vt * vt / g * (fallLnSinh(b + s) - fallLnSinh(b)), vt / tanh(b + s));
}

float fallSlide(float r0, float vt, float t, float g) { float k = g / (vt * vt); return r0 < 1e-9 ? 0.0 : log(1.0 + k * r0 * t) / k; }
float fallSlideSpeed(float r0, float vt, float t, float g) { return r0 / (1.0 + g / (vt * vt) * r0 * t); }

vec4 fallCourse(int i, float t, float lat, float fwd) {
    float u = clamp(t / max(1e-6, uStrandB[i].z), 0.0, 1.0) * float(FALL_PATH - 1);
    int k = min(int(floor(u)), FALL_PATH - 2);
    float f = u - float(k);
    vec4 a = texelFetch(uFallPath, ivec2(k, i), 0), b = texelFetch(uFallPath, ivec2(k + 1, i), 0);
    vec4 P = a + (b - a) * f;
    vec3 T = b.xyz - a.xyz;
    float tl = length(T);
    T = tl < 1e-5 ? vec3(0.0, -1.0, 0.0) : T / tl;
    vec2 L = vec2(-T.z, T.x);
    float ll = length(L);
    L = ll < 1e-3 ? uFallAlong.xz : L / ll;
    vec3 N = vec3(-L.y * T.y, L.y * T.x - L.x * T.z, L.x * T.y);
    float nl = length(N);
    N = nl < 1e-5 ? vec3(uFallDir.x, 0.0, uFallDir.z) : N / nl;
    if (N.y + 0.5 * dot(N.xz, uFallDir.xz) < 0.0) N = -N;
    float sp = min(t, 6.0);
    float ql = lat * sp, qn = abs(fwd) * sp * 1.5;
    return vec4(P.x + L.x * ql + N.x * qn, P.y + N.y * qn, P.z + L.y * ql + N.z * qn, P.w);
}

vec4 fallParcel(int i, float t, float vt, float lat, float fwd) {
    if (uCourse == 1) return fallCourse(i, t, lat, fwd);
    vec4 A = uStrandA[i], B = uStrandB[i];
    float g = uFallPhys.x, vc = uFallPhys.y;
    vec2 r = A.w * uFallDir.xz - uFallWind.xz;
    float r0 = length(r);
    vec2 h = r0 > 1e-9 ? r / r0 : vec2(0.0);
    float t1 = min(t, B.x);
    vec2 v1 = fallDrop(vc, 0.0, t1, g);
    float sl = fallSlide(r0, vc, t1, g);
    vec2 xz = A.xz + uFallWind.xz * t1 + h * sl;
    float y = A.y - v1.x;
    float rb = fallSlideSpeed(r0, vc, t1, g);
    if (t <= B.x) {
        vec2 hv = uFallWind.xz + h * rb;
        return vec4(xz.x, y, xz.y, sqrt(dot(hv, hv) + v1.y * v1.y));
    }
    float tau = t - B.x;
    vec2 q = h * rb + uFallAlong.xz * lat + uFallDir.xz * fwd;
    float q0 = length(q);
    vec2 u = q0 > 1e-9 ? q / q0 : vec2(0.0);
    vec2 v2 = fallDrop(vt, v1.y, tau, g);
    float s2 = fallSlide(q0, vt, tau, g), qs = fallSlideSpeed(q0, vt, tau, g);
    vec2 hv = uFallWind.xz + u * qs;
    vec2 p = xz + uFallWind.xz * tau + u * s2;
    return vec4(p.x, y - v2.x, p.y, sqrt(dot(hv, hv) + v2.y * v2.y));
}

// Licht des Wassers: Sonne mit Vorwärtsstreuung (Henyey-Greenstein, Schleier leuchtet im Gegenlicht), Himmel, Dunst
uniform vec3 uSunDir, uSunColor, uAmbient, uFogColor, uEye;
uniform float uFogDist, uDisplay;
float fallHG(float c, float g) { float g2 = g * g; return (1.0 - g2) / (12.566370614 * pow(1.0 + g2 - 2.0 * g * c, 1.5)); }
vec3 fallLight(vec3 p, vec3 alb, float thick) {
    vec3 v = normalize(p - uEye);
    float c = dot(v, uSunDir);                        // Blick mit der Sonne: Gegenlicht
    float ph = mix(fallHG(c, 0.75) * 6.0, 0.35, thick); // dünn: Vorwärtsstreuung, dick: matt
    vec3 col = alb * (uSunColor * (ph + 0.25 * max(uSunDir.y, 0.0)) * 0.55 + uAmbient * 1.6);
    return col;
}
vec4 fallFinish(vec3 p, vec3 col, float a) {
    float d = distance(p, uEye);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(uFogDist, 1.0)));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    return vec4(col * a, a);
}

uint fallHash(uint x) { x ^= x >> 16u; x *= 0x7feb352du; x ^= x >> 15u; x *= 0x846ca68bu; x ^= x >> 16u; return x; }
float fallNoise2(vec2 p, uint seed) {
    vec2 i = floor(p), f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    uint ix = uint(int(i.x)), iy = uint(int(i.y));
    float a = float(fallHash(ix * 73856093u ^ iy * 19349663u ^ seed) >> 8u) / 16777216.0;
    float b = float(fallHash((ix + 1u) * 73856093u ^ iy * 19349663u ^ seed) >> 8u) / 16777216.0;
    float c = float(fallHash(ix * 73856093u ^ (iy + 1u) * 19349663u ^ seed) >> 8u) / 16777216.0;
    float d = float(fallHash((ix + 1u) * 73856093u ^ (iy + 1u) * 19349663u ^ seed) >> 8u) / 16777216.0;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
