// tree.frag — Sonne, Himmel, Durchscheinen der Blätter (beidseitig), Dunst; wie demo/SoftRenderer.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vLeaf;
in float vAo;
in vec2 vUv;
in float vTex;
uniform sampler2D uBarkTex, uLeafTex;  // sRGB-Texturen, liefern linear
uniform vec3 uCam;
uniform vec3 uSunDir;                   // zur Sonne
uniform vec3 uSunColor, uSkyColor, uGroundColor;
uniform vec4 uFog;                      // Farbe, Sichtweite (m)
uniform float uExposure;
uniform int uTonemap;                   // 1: Belichtung, Reinhard, sRGB hier; 0: linear ausgeben
// seit LituyaBay1958 W6 Runde 4 (wie forest.frag): Schatten von oben, Sonnenschatten (3×3-PCF), Himmel für den Dunst
uniform sampler2D uShadow;
uniform vec4 uShadowRect;               // x0, z0, Kante (m), an
uniform sampler2DShadow uSunShadow;
uniform mat4 uSunShadowM;
uniform vec2 uSunShadowP;               // an, Versatz
uniform sampler2D uSky;
uniform int uSkyOn;
uniform float uSkyWeight;               // > 0: Himmelslicht wie forest; 0: Halbkugel Himmel/Boden
out vec4 fragColor;

float sunShadow(vec3 w, vec3 n) {
    if (uSunShadowP.x < 0.5) return 1.0;
    vec4 q = uSunShadowM * vec4(w + n * 0.12 + uSunDir * 0.6, 1.0);
    vec3 s = q.xyz / q.w * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.002))) || any(greaterThan(s.xy, vec2(0.998))) || s.z > 1.0) return 1.0;
    vec2 tx = 1.0 / vec2(textureSize(uSunShadow, 0));
    float sum = 0.0;
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++)
            sum += texture(uSunShadow, vec3(s.xy + vec2(i, j) * tx, s.z - uSunShadowP.y));
    return sum / 9.0;
}

void main() {
    vec3 albedo = vColor;
    if (vTex > 1.5) {
        vec4 t = texture(uLeafTex, vUv);
        if (t.a < 0.5) discard;
        albedo *= t.rgb;
    } else if (vTex > 0.5) {
        albedo *= texture(uBarkTex, vUv).rgb;
    }
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uCam - vWorld);
    float trans = 0.0;
    float ndl;
    if (vLeaf > 0.5) {
        if (dot(n, v) < 0.0) n = -n;
        ndl = dot(n, uSunDir);
        trans = max(0.0, -ndl) * 0.45;
        ndl = max(0.0, ndl * 0.8 + 0.2);
    } else {
        ndl = max(0.0, dot(n, uSunDir));
    }
    float hemi = 0.5 + 0.5 * n.y;
    float cloud = uShadowRect.w > 0.5 ? textureLod(uShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r : 1.0;
    float sh = sunShadow(vWorld, n) * cloud * (uSkyWeight > 0.0 ? smoothstep(-0.02, 0.05, uSunDir.y) : 1.0);
    vec3 amb = uSkyWeight > 0.0 ? uSkyColor * uSkyWeight * (0.45 + 0.35 * vAo + 0.2 * n.y)
                                : (uSkyColor * hemi + uGroundColor * (1.0 - hemi)) * (0.4 + 0.6 * vAo);
    vec3 light = uSunColor * sh * (ndl + trans) * (0.35 + 0.65 * vAo) + amb;
    vec3 c = albedo * light;
    float dist = length(uCam - vWorld);
    vec3 fogC = uFog.rgb;
    if (uSkyOn == 1) {
        vec3 hd = normalize(vec3(vWorld.x - uCam.x, 0.0, vWorld.z - uCam.z) + vec3(0.0, 1e-4, 0.0));
        fogC = textureLod(uSky, vec2(atan(hd.x, -hd.z) / 6.28318530718 + 0.5, 0.5), 0.0).rgb;
    }
    c = mix(c, fogC, 1.0 - exp(-dist / max(uFog.w, 1.0)));
    if (uTonemap == 1) {
        c *= uExposure;
        c = c / (1.0 + c * 0.25);
        c = pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
    }
    fragColor = vec4(c, 1.0);
}
