// forestgpu_imp.frag — mischt die vier Ansichten des Impostors nach ihren Gewichten (Deckung als Gewicht), verwirft unter
// halber Deckung und beleuchtet wie forest.frag (Laub beidseitig und durchscheinend, Sonnenschatten, Wolkenschatten,
// Mond, Himmel, Dunst, Tönung je Baum). Ausgabe: HDR, linear (mit uTone für Demos).
in vec2 vUV0, vUV1, vUV2, vUV3;
flat in vec4 vW;
flat in vec2 vF;
flat in float vLayer;
flat in vec2 vRot;
flat in float vTint;
in vec3 vWorld;
out vec4 frag;
uniform sampler2DArray uAlb, uNrm;
uniform float uFrames, uFramePx;
uniform vec3 uCam;
uniform vec3 uSunDir, uSunColor, uSkyColor, uFogColor, uMoonDir, uMoonColor;
uniform float uFogDist, uSkyWeight, uWet;
uniform sampler2D uShadow;
uniform vec4 uShadowRect;
uniform sampler2D uSky;
uniform int uSkyOn;
uniform sampler2DShadow uSunShadow;
uniform mat4 uSunShadowM;
uniform vec2 uSunShadowP;
uniform vec2 uTone;

float sunShadow(vec3 w, vec3 n) {
    if (uSunShadowP.x < 0.5) return 1.0;
    vec4 q = uSunShadowM * vec4(w + n * 0.3 + uSunDir * 1.5, 1.0);
    vec3 s = q.xyz / q.w * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.002))) || any(greaterThan(s.xy, vec2(0.998))) || s.z > 1.0) return 1.0;
    return texture(uSunShadow, vec3(s.xy, s.z - uSunShadowP.y));
}

void take(vec2 uv, vec2 f, float w, inout vec4 alb, inout vec4 nrm) {
    vec2 loc = uv * uFrames - f;
    float m = 0.5 / uFramePx;
    if (w <= 0.0 || any(lessThan(loc, vec2(0.0))) || any(greaterThan(loc, vec2(1.0)))) return;
    vec2 u = (f + clamp(loc, vec2(m), vec2(1.0 - m))) / uFrames;
    vec4 a = texture(uAlb, vec3(u, vLayer));
    vec4 n = texture(uNrm, vec3(u, vLayer));
    alb += vec4(a.rgb * a.a, a.a) * w;
    nrm += vec4(n.xyz * 2.0 - 1.0, n.a) * a.a * w;
}

void main() {
    vec4 alb = vec4(0.0), nm = vec4(0.0);
    take(vUV0, vF, vW.x, alb, nm);
    take(vUV1, vF + vec2(1.0, 0.0), vW.y, alb, nm);
    take(vUV2, vF + vec2(0.0, 1.0), vW.z, alb, nm);
    take(vUV3, vF + vec2(1.0, 1.0), vW.w, alb, nm);
    if (alb.a < 0.5) discard;
    vec3 col0 = alb.rgb / alb.a * mix(vec3(0.84, 0.88, 0.86), vec3(1.14, 1.08, 0.96), vTint);
    vec3 nmod = normalize(nm.xyz + vec3(0.0, 1e-4, 0.0));
    float leafy = nm.a / alb.a;
    vec3 n = normalize(vec3(vRot.x * nmod.x - vRot.y * nmod.z, nmod.y, vRot.y * nmod.x + vRot.x * nmod.z));
    vec3 V = normalize(uCam - vWorld);
    bool leaf = leafy > 0.4;
    float cloud = uShadowRect.w > 0.0 ? textureLod(uShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r : 1.0;
    float sh = sunShadow(vWorld, n) * cloud;
    float up = smoothstep(-0.02, 0.05, uSunDir.y);
    float nl = dot(n, uSunDir);
    float diff = leaf ? max(nl, 0.0) * 0.8 + 0.2 * max(-nl, 0.0) : max(nl, 0.0);
    float back = leaf ? pow(max(dot(-V, uSunDir), 0.0), 3.0) * max(-nl, 0.0) * 0.6 + pow(max(dot(-V, uSunDir), 0.0), 8.0) * 0.25 : 0.0;
    vec3 sun = uSunColor * up * sh;
    vec3 moon = uMoonColor * smoothstep(-0.02, 0.05, uMoonDir.y) * cloud * max(dot(n, uMoonDir) * 0.7 + 0.3, 0.0);
    float skyAmt = uSkyWeight * (0.55 + 0.2 * n.y);
    vec3 alb3 = col0 * (leaf ? 1.0 : 1.0 - 0.35 * uWet);
    vec3 col = alb3 * (sun * diff + moon + uSkyColor * skyAmt) + alb3 * vec3(0.75, 1.0, 0.4) * sun * back * 0.5;
    float d = length(uCam - vWorld);
    vec3 fogC = uFogColor;
    if (uSkyOn == 1) {
        vec3 hd = normalize(vec3(vWorld.x - uCam.x, 0.0, vWorld.z - uCam.z) + vec3(0.0, 1e-4, 0.0));
        fogC = textureLod(uSky, vec2(atan(hd.x, -hd.z) / 6.28318530718 + 0.5, 0.5), 0.0).rgb;
    }
    col = mix(col, fogC, 1.0 - exp(-d / uFogDist));
    if (uTone.x > 0.5) {
        vec3 x = col * uTone.y;
        col = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
    }
    frag = vec4(col, 1.0);
}
