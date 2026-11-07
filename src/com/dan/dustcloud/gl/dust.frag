// dust.frag — com.dan.dustcloud: Staubwolke und Staubfahne als Volumen über dem Bild (DustGl; Strahlengang aus
// LituyaBay1958 medien.frag, Werkbuch Welle W3/W3c/W4). Ausgabe (L, T): Ziel · T + L (Mischung ONE, SRC_ALPHA).
// Licht einfach aus Uniforms (vorbelichtet): Sonne mit Vorwärtsstreuung (Henyey-Greenstein g = 0,6, „Staub im
// Gegenlicht“), Himmel, Dunst zum Auge; das Himmelslicht dringt nur ein Stück ein (innen und unten dunkler) [G].
#include "dust.glsl"
in vec2 vUV;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uCam, uSun, uFwd;
uniform vec3 uSunColor, uSkyColor, uHazeColor;
uniform float uFogDist, uLevel, uTime;
uniform sampler2D uDepth;
uniform float uNear, uFar;
uniform int uRev, uSteps;
uniform float uFrame;
uniform int uLayerOn, uPlumeOn;
uniform sampler2D uDcTop, uDcGround;   // RG32F (Oberkante, optische Dicke), R32F Grund; Zeile j bei z0 + (j + ½) · Zelle
uniform vec4 uDcGeo;                   // x0, z0, Zelle, —
uniform vec2 uDcSize;                  // Zellen nx, nz
uniform vec2 uDcY;                     // Hülle in der Höhe: unten, oben

vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    float zn = uRev == 1 ? 1.0 : -1.0, zm = uRev == 1 ? 0.001 : 0.0;
    vec4 a = uInvVP * vec4(ndc, zn, 1.0);
    vec4 b = uInvVP * vec4(ndc, zm, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
}

float sceneDist(vec3 d) {
    float z = texture(uDepth, vUV).r;
    float vz;
    if (uRev == 1) vz = z <= 0.0 ? 1e9 : uNear / z;
    else { float zn = z * 2.0 - 1.0; vz = z >= 1.0 ? 1e9 : 2.0 * uFar * uNear / ((uFar + uNear) - zn * (uFar - uNear)); }
    return vz / max(dot(d, uFwd), 1e-4);
}

vec2 box(vec3 lo, vec3 hi, vec3 inv, float dist) {
    vec3 t0 = (lo - uCam) * inv, t1 = (hi - uCam) * inv;
    vec3 tn = min(t0, t1), tf = max(t0, t1);
    return vec2(max(max(tn.x, tn.y), max(tn.z, 0.0)), min(min(tf.x, tf.y), min(tf.z, dist)));
}

void main() {
    vec3 d = viewRay(vUV);
    float dist = sceneDist(d);
    vec3 L = vec3(0.0);
    float T = 1.0;
    vec3 inv = 1.0 / (d + vec3(1e-9));
    vec2 abS = vec2(1e30, -1e30), abF = abS;
    float a = 1e30, b = -1e30;
    vec2 ext = uDcSize * uDcGeo.z;
    if (uLayerOn == 1) {
        abS = box(vec3(uDcGeo.x, uDcY.x, uDcGeo.y), vec3(uDcGeo.x + ext.x, uDcY.y + 0.3 * (uDcY.y - uDcY.x), uDcGeo.y + ext.y), inv, dist);
        if (abS.y > abS.x) { a = min(a, abS.x); b = max(b, abS.y); }
    }
    if (uPlumeOn == 1) {
        float Rm = uDcPlumeR.x * 2.2 + 0.25 * uDcPlume.z + 120.0;
        abF = box(vec3(uDcPlume.x - Rm, uLevel - 5.0, uDcPlume.y - Rm), vec3(uDcPlume.x + Rm, uDcPlume.z + 240.0, uDcPlume.y + Rm), inv, dist);
        if (abF.y > abF.x) { a = min(a, abF.x); b = max(b, abF.y); }
    }
    if (b > a) {
        int N = clamp(uSteps, 16, 96);
        float ds = (b - a) / float(N);
        float j = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453 + uFrame * 0.61803399);
        float cs = dot(d, uSun);
        float ph = dustPhaseHG(cs, 0.6) * 4.0 * 3.14159265;
        vec3 himmel = uSkyColor * 0.6 + uHazeColor * 0.6 * (0.5 + 0.5 * cs);
        vec3 alb = vec3(0.62, 0.57, 0.50) * 0.8;
        for (int i = 0; i < N; i++) {
            float tq = a + (float(i) + j) * ds;
            vec3 p = uCam + d * tq;
            float sigS = 0.0, ueber = 0.0;
            if (uLayerOn == 1 && tq >= abS.x && tq <= abS.y) {
                vec2 uv = (p.xz - uDcGeo.xy) / ext;
                if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) {
                    vec2 s = texture(uDcTop, uv).rg;
                    float base = texture(uDcGround, uv).r;
                    sigS = dustLayerDensity(p, s, base, uTime);
                    float dicke = max(s.x - base, 5.0);
                    ueber = s.y * clamp((s.x - p.y) / dicke, 0.0, 1.0) / max(uSun.y + 0.15, 0.1);
                }
            }
            vec2 fh = uPlumeOn == 1 && tq >= abF.x && tq <= abF.y ? dustPlumeDensity(p, uLevel) : vec2(0.0);
            float sig = sigS + fh.x;
            if (sig < 1e-6) continue;
            vec3 Ls = alb * 1.25 * (uSunColor * ph * exp(-ueber) + himmel * (0.42 + 0.58 * exp(-ueber * 0.25)));
            vec3 glut = uHazeColor * (0.35 + 0.65 * dustPhaseHG(cs, 0.45) * 4.0 * 3.14159265 * 0.35) * smoothstep(250.0, 1150.0, p.y - uLevel);
            vec3 Lf = vec3(0.86, 0.82, 0.76) * (uSunColor * ph * exp(-fh.y) * 1.3 + himmel * (0.5 + 0.5 * exp(-fh.y * 0.25)) + glut * 1.6 * exp(-fh.y * 0.12));
            vec3 Li = (Ls * sigS + Lf * fh.x) / sig;
            float tr = exp(-sig * ds);
            Li = mix(Li, uHazeColor, 1.0 - exp(-tq / uFogDist));
            L += T * Li * (1.0 - tr);
            T *= tr;
            if (T < 0.01) break;
        }
    }
    frag = vec4(L, T);
}
