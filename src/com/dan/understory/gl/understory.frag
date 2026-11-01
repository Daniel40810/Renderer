// understory.frag — Licht im Unterwuchs: Blätter und Wedel beidseitig, gegen die Sonne durchscheinend; Sonne mit
// Schattenkarte (falls gesetzt) und Licht unter dem Kronendach (uCanopy: Anteil, der durchkommt, von der App), Himmel,
// Dunst; Laubwerfer im Herbst gelb, kahl ausgedünnt; am Rand der Reichweite gestreut ausgeblendet. Ausgabe HDR linear.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vLeafy;
in float vFade;
in float vH;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
uniform float uFogDist, uCanopy, uWet;
uniform sampler2D uCanopyTex;
uniform vec4 uCanopyRect;  // x, z, Kante, an (1) / aus (0)
uniform vec2 uSeason;       // Herbstfarbe 0..1, kahl 0..1 (nur Laubwerfer)
uniform int uDeciduous;
uniform sampler2DShadow uSunShadow;
uniform mat4 uSunShadowM;
uniform vec2 uSunShadowP;
uniform vec2 uTone;

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
float sunShadow(vec3 w, vec3 n) {
    if (uSunShadowP.x < 0.5) return 1.0;
    vec4 q = uSunShadowM * vec4(w + n * 0.05 + uSunDir * 0.2, 1.0);
    vec3 s = q.xyz / q.w * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.002))) || any(greaterThan(s.xy, vec2(0.998))) || s.z > 1.0) return 1.0;
    return texture(uSunShadow, vec3(s.xy, s.z - uSunShadowP.y));
}
void main() {
    if (hash(gl_FragCoord.xy) > vFade) discard;
    bool leaf = vLeafy > 0.5;
    vec3 alb = vColor;
    if (uDeciduous == 1 && leaf) {
        if (hash(floor(vWorld.xz * 6.0) + vWorld.y) < uSeason.y) discard;
        alb = mix(alb, vec3(0.32, 0.20, 0.03) * (0.7 + 0.6 * hash(floor(vWorld.xz * 3.0))), uSeason.x);
    }
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    if (leaf) { if (dot(n, V) < 0.0) n = -n; }
    else if (!gl_FrontFacing) n = -n;
    alb *= leaf ? 1.0 : 1.0 - 0.35 * uWet;
    float can = uCanopyRect.w > 0.5 ? textureLod(uCanopyTex, (vWorld.xz - uCanopyRect.xy) / uCanopyRect.z, 0.0).r : uCanopy;
    float sh = sunShadow(vWorld, n) * can;
    float up = smoothstep(-0.02, 0.05, uSunDir.y);
    float nl = dot(n, uSunDir);
    float diff = leaf ? max(nl, 0.0) * 0.8 + 0.2 * max(-nl, 0.0) : max(nl, 0.0);
    float back = leaf ? pow(max(dot(-V, uSunDir), 0.0), 3.0) * max(-nl, 0.0) * 0.7 : 0.0;
    vec3 sun = uSunColor * up * sh;
    float sky = (0.55 + 0.45 * n.y) * (0.6 + 0.4 * vH) * (0.35 + 0.65 * sqrt(can));
    vec3 col = alb * (sun * diff + uSkyColor * sky) + alb * vec3(0.8, 1.0, 0.45) * sun * back;
    if (uWet > 0.0) col += uSunColor * up * sh * pow(max(dot(reflect(-uSunDir, n), V), 0.0), 30.0) * 0.05 * uWet;
    float d = length(uCam - vWorld);
    col = mix(col, uFogColor, 1.0 - exp(-d / uFogDist));
    if (uTone.x > 0.5) {
        vec3 x = col * uTone.y;
        col = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
    }
    frag = vec4(col, 1.0);
}
