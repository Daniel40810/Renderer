#version 430 core
// cloth.frag — Tuch beidseitig: Sonne und Himmel, Durchscheinen im Gegenlicht (Licht von hinten tönt mit der Farbe
// des Stoffs), Nässe dunkelt und glänzt etwas, Dunst; Farbbänder je Tuch.
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
flat in int vCloth;
struct Look { vec4 c0, c1, c2, opt; };   // opt: Zahl der Bänder, Bänder längs u (1) oder v (0), Durchscheinen, Nässe
layout(std430, binding = 2) readonly buffer Looks { Look L[]; };
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uGroundColor;
uniform vec4 uFog;
uniform float uExposure;
uniform int uTonemap;
out vec4 fragColor;
void main() {
    Look lk = L[vCloth];
    float bands = max(1.0, lk.opt.x), t = lk.opt.y > 0.5 ? vUv.x : vUv.y;
    int b = int(min(bands - 1.0, floor(t * bands)));
    vec3 alb = b == 0 ? lk.c0.rgb : b == 1 ? lk.c1.rgb : lk.c2.rgb;
    float wet = lk.opt.w;
    alb *= mix(1.0, 0.55, wet);
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uCam - vWorld);
    if (dot(n, v) < 0.0) n = -n;
    float ndl = dot(n, uSunDir);
    float diff = max(ndl, 0.0);
    float trans = max(-ndl, 0.0) * lk.opt.z * (0.6 + 0.4 * pow(max(dot(-v, uSunDir), 0.0), 4.0));
    float hemi = 0.5 + 0.5 * n.y;
    vec3 h = normalize(v + uSunDir);
    float spec = pow(max(dot(n, h), 0.0), mix(24.0, 80.0, wet)) * mix(0.03, 0.18, wet) * step(0.0, ndl);
    vec3 c = alb * (uSunColor * (diff + trans) + uSkyColor * hemi + uGroundColor * (1.0 - hemi)) + uSunColor * spec;
    float dist = length(uCam - vWorld);
    c = mix(c, uFog.rgb, 1.0 - exp(-dist / max(uFog.w, 1.0)));
    if (uTonemap == 1) {
        c *= uExposure;
        c = c / (1.0 + c * 0.25);
        c = pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
    }
    fragColor = vec4(c, 1.0);
}
