// curtain.frag — Vorhänge der Schauer jenseits der Schichten (ab 60 m) bis zur Szene, zum Rand der Karte oder zur
// Wolkenuntergrenze. Auslöschung aus der Verteilung am Ort: σ = N0 π/4 ∫ D² e^(−ΛD) dD = N0 π / (2 Λ³) (die
// geometrische Hälfte; die gebeugte Hälfte bleibt im Bildpunkt). Gestreut wird mit derselben Phasenfunktion wie in den
// Tropfen: Umgebung (uPEnv) und Sonne – wo die Sonne unter den Wolken auf den Regen fällt und hinter dem Auge steht,
// steht so von selbst der Regenbogen im Vorhang, mit Nebenbogen und Alexanders dunklem Band. Senkrechte Schleier, die
// mit dem Wind ziehen, geben ihm Struktur (in der Ferne gemittelt, nah gleichmäßig).
// Ausgabe vormultipliziert über die Szene.
#include "precip_common.glsl"
in vec2 vUv;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uEye;
uniform sampler2D uSceneDepth;
uniform int uReversed, uHasDepth;
uniform float uStart;
uniform int uSteps;
uniform float uStrength;
uniform int uFrame;
uniform float uPSunVis;
uniform float uPFogDist;
uniform vec3 uPFogColor;
uniform sampler2D uPShadow;   // Sonne durch die Wolken (R), von oben, optional
uniform vec4 uPShadowRect;    // x0, z0, Kante, Höhe der Fläche; Kante 0 = ohne

float vn(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.55), b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.55);
    float c = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.55), d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.55);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float sceneDist(vec3 dir) {
    if (uHasDepth == 0) return 1e9;
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if ((uReversed == 1 && d <= 0.0) || (uReversed == 0 && d >= 1.0)) return 1e9;
    vec4 w = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? d : d * 2.0 - 1.0, 1.0);
    return length(w.xyz / w.w - uEye);
}

float sunAt(vec3 p) {
    if (uPShadowRect.z <= 0.0 || dot(uPSunE, vec3(1.0)) <= 1e-5) return uPSunVis;
    vec3 d = uPSunDir;
    if (d.y < 0.02) return uPSunVis;
    vec2 q = p.xz + d.xz / d.y * (uPShadowRect.w - p.y);
    vec2 u = (q - uPShadowRect.xy) / uPShadowRect.z;
    if (u.x < 0.0 || u.y < 0.0 || u.x > 1.0 || u.y > 1.0) return uPSunVis;
    return texture(uPShadow, u).r;
}

void main() {
    if (uPD.x <= 0.0) discard;
    vec4 p0 = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? 1.0 : -1.0, 1.0);
    vec4 p1 = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? 0.0001 : 1.0, 1.0);
    vec3 dir = normalize(p1.xyz / p1.w - p0.xyz / p0.w);
    float tEnd = min(sceneDist(dir), uPMap.w > 0.5 ? 0.49 * uPMap.z * uPMap.w : 20000.0);
    if (dir.y > 1e-4) tEnd = min(tEnd, (uPE.w - uEye.y) / dir.y);
    if (uEye.y > uPE.w && dir.y >= 0.0) discard;
    float tStart = uStart;
    if (uEye.y > uPE.w && dir.y < 0.0) tStart = max(tStart, (uPE.w - uEye.y) / dir.y);
    if (tEnd <= tStart) discard;
    vec3 env = texture(uPEnv, pPanoUv(dir)).rgb;
    vec3 ph = pPhase(dot(dir, uPSunDir), 0.0), phm = pPhase(dot(dir, uPMoonDir), 0.0);
    float ratio = tEnd / tStart;
    vec2 wind = normalize(uPC.xy + vec2(1e-4, 0.0));
    vec2 side = vec2(-wind.y, wind.x);
    vec3 acc = vec3(0.0);
    float T = 1.0, tPrev = tStart;
    vec2 px = gl_FragCoord.xy + float(uFrame % 64) * 5.588238;
    float jit = fract(52.9829189 * fract(dot(px, vec2(0.06711056, 0.00583715))));
    for (int s = 1; s <= 64; s++) {
        if (s > uSteps) break;
        float t = tStart * pow(ratio, float(s) / float(uSteps));
        float dt = t - tPrev, tm = tPrev + jit * dt;
        tPrev = t;
        vec3 p = uEye + dir * tm;
        float local = uPD.x * pCurtain(p.xz);
        if (local <= 0.001) continue;
        vec2 q = p.xz - uPC.xy * uPB.y;
        float streak = 0.55 + 0.9 * vn(vec2(dot(q, side) / 35.0, p.y / 420.0 + uPB.y * 0.05)) * (0.6 + 0.4 * vn(vec2(dot(q, side) / 9.0, dot(q, wind) / 120.0)));
        streak = mix(0.91, streak, clamp(70.0 / max(dt, 1.0), 0.0, 1.0) * smoothstep(250.0, 1200.0, tm));
        float lam = pLambda(local), n0 = pN0(local);
        float sigma = n0 * P_PI / (2.0 * lam * lam * lam) * 1e-6 * streak;
        float dT = exp(-sigma * dt);
        vec3 src = env + uPSunE * ph * sunAt(p) + uPMoonE * phm;
        float fd = 1.0 - exp(-tm / max(1.0, uPFogDist));
        acc += T * (1.0 - dT) * mix(src, uPFogColor, fd);
        T *= dT;
        if (T < 0.01) break;
    }
    float alpha = (1.0 - T) * uStrength;
    if (alpha < 1e-5) discard;
    frag = vec4(acc * uStrength, alpha);
}
