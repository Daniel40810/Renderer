// scene.frag — Demo-Szene: Wasser mit Fresnel, Himmel und dem Wellenfeld aus com.dan.precip (fest in der Welt: im
// Fenster echt, draußen zwei gedrehte Abfragen, wie com.dan.ocean detailMode 1); Pfähle und Steg matt, nass dunkler;
// ein dunkler Waldrand (Albedo 0,04), vor dem man Regen sieht. Linear, HDR (Filmkurve in post.frag).
in vec3 vPos, vNormal;
flat in int vKind;
uniform vec3 uEye, uSunDir, uSunColor, uSkyTop, uSkyHorizon;
uniform sampler2D uRipple;
uniform vec4 uRippleP;     // Kante in m, Stärke, an, —
uniform vec2 uRippleC;     // Mitte des Fensters
uniform vec4 uRippleR;     // W7b Runde 5: mittlere quadratische Steigung unter dem Raster, im Raster; Anteile der Fläche dazu
uniform int uRippleN;      // Bildzähler
uniform float uFogDist, uWet;
out vec4 frag;
vec3 sky(vec3 d) { return mix(uSkyHorizon, uSkyTop, clamp(d.y * 2.0, 0.0, 1.0)); }
void main() {
    vec3 N = normalize(vNormal), V = normalize(uEye - vPos);
    float dist = length(uEye - vPos);
    vec3 col;
    if (vKind == 0) {
        float hid = 0.0;
        if (uRippleP.z > 0.0) {
            vec2 rel = abs(vPos.xz - uRippleC) / uRippleP.x;
            float win = 1.0 - smoothstep(0.30, 0.47, max(rel.x, rel.y));
            vec2 nearS = texture(uRipple, vPos.xz / uRippleP.x).rg;
            const mat2 R1 = mat2(0.8, 0.6, -0.6, 0.8), R2 = mat2(-0.28, 0.96, -0.96, -0.28);
            vec2 a = texture(uRipple, (R1 * vPos.xz) / uRippleP.x + 0.37).rg, b = texture(uRipple, (R2 * vPos.xz) / uRippleP.x + 0.71).rg;
            vec2 farS = (transpose(R1) * a + transpose(R2) * b) * 0.7071;
            float fern = exp(-dist / 40.0);
            vec2 s = (nearS * win + farS * (1.0 - win) * fern) * uRippleP.y;
            N = normalize(N + vec3(-s.x, 0.0, -s.y));
            // Varianz erhalten: was das Feld hier nicht zeigt, tragen einzelne Facetten (Anteil p, Varianz/p), wie
            // com.dan.ocean
            float shown = win + (1.0 - win) * fern;
            shown *= shown * exp2(-max(textureQueryLod(uRipple, vPos.xz / uRippleP.x).y, 0.0));
            hid = uRippleR.y * max(0.0, 1.0 - shown);
        }
        // Anteil 1 − p ungestört (genau), Anteil p aus acht geneigten Facetten; unter dem Raster erst, wo ein
        // Bildpunkt viele Einschläge deckt (Fußabdruck ab rund 1 cm) – näher wäre es Korn statt Ringe [G]
        float s2 = uRippleP.y * uRippleP.y, fSub = smoothstep(0.004, 0.03, length(fwidth(vPos.xz)));
        float vS = uRippleP.z > 0.0 ? fSub * uRippleR.x * s2 : 0.0, vR = hid * s2, vT = vS + vR;
        float pE = vT > 0.0 ? clamp((vS * uRippleR.z + vR * uRippleR.w) / vT, 0.05, 1.0) : 1.0;
        float sig = sqrt(0.5 * vT / pE);
        col = vec3(0.0);
        for (int k = 0; k < 9; k++) {
            if (k > 0 && vT <= 0.0) break;
            float wk = k == 0 ? (vT > 0.0 ? 1.0 - pE : 1.0) : pE / 8.0;
            vec3 Nk = N;
            if (k > 0) {
                uint hs = uint(gl_FragCoord.x) * 1973u ^ uint(gl_FragCoord.y) * 9277u ^ uint(uRippleN * 8 + k) * 26699u;
                hs = (hs ^ (hs >> 16u)) * 0x45d9f3bu; hs = (hs ^ (hs >> 16u)) * 0x45d9f3bu; hs ^= hs >> 16u;
                vec2 ua = (vec2(hs & 0xffffu, hs >> 16u) + 0.5) / 65536.0;
                vec2 j = sig * sqrt(-2.0 * log(ua.x)) * vec2(cos(6.2831853 * ua.y), sin(6.2831853 * ua.y));
                Nk = normalize(N + vec3(-j.x, 0.0, -j.y));
            }
            float F = 0.02 + 0.98 * pow(1.0 - max(dot(Nk, V), 0.0), 5.0);
            vec3 r = reflect(-V, Nk); r.y = abs(r.y);
            col += wk * mix(vec3(0.012, 0.04, 0.04) * (uSkyTop * 3.0 + 0.05), sky(r), F);
            col += wk * uSunColor * pow(max(dot(Nk, normalize(uSunDir + V)), 0.0), 300.0) * 0.6;
        }
    } else if (vKind == 1) {
        vec3 alb = vPos.y < 0.8 ? vec3(0.06, 0.07, 0.05) : vec3(0.36, 0.30, 0.24);
        alb *= 1.0 - 0.45 * uWet;
        col = alb * (uSunColor * max(dot(N, uSunDir), 0.0) / 3.14159 + uSkyTop * 3.0 * (0.6 + 0.4 * N.y));
        vec3 r = reflect(-V, N);
        col += sky(r) * 0.08 * uWet * pow(1.0 - max(dot(N, V), 0.0), 3.0);
    } else {
        // Waldrand: dunkel, leicht fleckig
        float f = fract(sin(dot(floor(vPos.xz * 0.5 + vPos.y * 0.3), vec2(12.9898, 78.233))) * 43758.5453);
        vec3 alb = vec3(0.025, 0.04, 0.025) * (0.7 + 0.6 * f);
        col = alb * (uSunColor * max(dot(N, uSunDir), 0.0) / 3.14159 + uSkyTop * 3.0 * 0.7);
    }
    float f = 1.0 - exp(-dist / uFogDist);
    frag = vec4(mix(col, uSkyHorizon, f), 1.0);
}
