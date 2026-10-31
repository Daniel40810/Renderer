// longship.frag — Holz, Teer, Tuch und Eisen der Schiffe aus Rauschen. Außenhaut geteert mit Nietreihen an jeder
// Überlappung, innen helle Eiche, Segel in roten und weißen Bahnen (von hinten durchscheinend), Schilde bemalt.
// Unter der Wasserlinie dunkel und nass, darüber ein nasser Saum. Licht: Sonne, Mond, Himmel, Dunst (wie com.dan.timber).
in vec3 vWorld;
in vec3 vNormal;
in vec3 vLocal;
in vec2 vUV;
flat in int vMat;
out vec4 frag;
uniform vec3 uCam;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyColor, uFogColor;
uniform float uSkyWeight, uFogDist, uExposure, uWaterY, uWet;
uniform int uTonemap, uSkyOn;
uniform sampler2D uSky;

const int HULL = 0, INNER = 1, WOOD = 2, SAIL = 3, SHIELD = 4, IRON = 5, ROPE = 6, DECK = 7, CARGO = 8;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) { return 0.5 * vnoise(p) + 0.27 * vnoise(p * 2.03 + 3.1) + 0.15 * vnoise(p * 4.1 - 1.7) + 0.08 * vnoise(p * 8.3 + 7.7); }

vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }

vec3 oak(vec2 uv, float shade) {
    float grain = fbm(vec2(uv.x * 0.8, uv.y * 22.0)) * 0.6 + 0.4 * vnoise(vec2(uv.x * 3.0, uv.y * 60.0));
    return vec3(0.21, 0.145, 0.085) * shade * (0.75 + 0.45 * grain);
}

vec3 albedo(out float spec) {
    spec = 0.0;
    vec2 uv = vUV;
    if (vMat == HULL) {
        // Teer über Eiche: fast schwarz, Faser schimmert durch, Nieten an der Unterkante jeder Planke
        float grain = fbm(vec2(uv.x * 0.7, uv.y * 30.0));
        vec3 c = vec3(0.020, 0.015, 0.011) * (0.8 + 0.5 * grain);
        float rivet = step(uv.y, 0.05) * smoothstep(0.035, 0.0, abs(fract(uv.x / 0.17) - 0.5) * 0.17);
        c = mix(c, vec3(0.05, 0.045, 0.04), rivet);
        spec = 0.35;
        return c;
    }
    if (vMat == INNER) return oak(uv, 1.0);
    if (vMat == WOOD) return oak(uv, 0.85);
    if (vMat == DECK) return oak(uv * vec2(4.0, 1.0), 0.75) * (0.85 + 0.15 * step(0.5, fract(uv.y / 0.28)));
    if (vMat == SAIL) {
        // Wolltuch in Bahnen von 0,9 m: rot und naturweiß, grob gewebt, verschmutzt
        float band = step(0.5, fract(uv.x / 1.8));
        vec3 c = mix(vec3(0.42, 0.06, 0.04), vec3(0.58, 0.52, 0.42), band);
        float weave = 0.92 + 0.08 * sin(uv.x * 220.0) * sin(uv.y * 220.0);
        c *= weave * (0.85 + 0.25 * fbm(uv * 0.6));
        return c;
    }
    if (vMat == SHIELD) {
        int k = int(floor(uv.y)) % 5;
        vec3 pal[5] = vec3[](vec3(0.55, 0.42, 0.08), vec3(0.03, 0.03, 0.03), vec3(0.42, 0.05, 0.04), vec3(0.6, 0.57, 0.5), vec3(0.08, 0.2, 0.09));
        float worn = fbm(uv * 9.0);
        return mix(pal[k], vec3(0.18, 0.13, 0.08), smoothstep(0.6, 0.8, worn) * 0.6);
    }
    if (vMat == IRON) { spec = 0.6; return vec3(0.045, 0.042, 0.04); }
    if (vMat == ROPE) return vec3(0.22, 0.17, 0.10) * (0.8 + 0.4 * vnoise(uv * vec2(40.0, 4.0)));
    return vec3(0.30, 0.24, 0.15) * (0.8 + 0.4 * fbm(uv * 2.0));   // Ladung
}

void main() {
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    float spec;
    vec3 alb = albedo(spec);
    // nass: unter dem Wasserspiegel dunkel, ein Saum darüber
    float hw = vWorld.y - uWaterY;
    float wet = max(uWet, 1.0 - smoothstep(0.0, 0.35, hw));
    if (vMat != SAIL) alb *= mix(1.0, 0.6, wet);
    vec3 v = normalize(uCam - vWorld);
    float ndl = max(dot(n, uSunDir), 0.0) * smoothstep(-0.02, 0.04, uSunDir.y);
    float ndm = max(dot(n, uMoonDir), 0.0) * smoothstep(-0.02, 0.04, uMoonDir.y);
    vec3 col = alb * (uSunColor * ndl + uMoonColor * ndm + uSkyColor * uSkyWeight * (0.55 + 0.45 * n.y));
    // Segel: Licht von hinten scheint durch das Tuch
    if (vMat == SAIL) col += alb * uSunColor * max(dot(-n, uSunDir), 0.0) * 0.45 * smoothstep(-0.02, 0.04, uSunDir.y);
    vec3 h = normalize(uSunDir + v);
    col += uSunColor * (spec + wet * 0.4) * 0.06 * pow(max(dot(n, h), 0.0), 40.0) * ndl;
    float d = length(uCam - vWorld);
    vec3 fogC = uFogColor;
    if (uSkyOn == 1) {
        vec3 hd = normalize(vec3(vWorld.x - uCam.x, 0.0, vWorld.z - uCam.z) + vec3(0.0, 1e-4, 0.0));
        fogC = textureLod(uSky, vec2(atan(hd.x, -hd.z) / 6.28318530718 + 0.5, 0.5), 0.0).rgb;
    }
    col = mix(col, fogC, 1.0 - exp(-d / max(1.0, uFogDist)));
    if (uTonemap == 1) col = pow(aces(col * uExposure), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
