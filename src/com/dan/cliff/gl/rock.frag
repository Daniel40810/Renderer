// rock.frag — com.dan.cliff: Fels mit dem Material aus cliff.glsl, Licht von Sonne (mit Anteil uShade) und Himmel,
// Dunst zur Ferne. Ausgabe HDR linear, schon vorbelichtet wie das Licht, das hereinkommt.
#include "cliff.glsl"
in vec3 vWorld;
in vec3 vNormal;
out vec4 frag;
uniform vec3 uEye;
uniform float uPixelAngle;
uniform vec3 uSunDir, uSunColor, uAmbient, uFogColor;
uniform float uFogDist, uShade, uRainWet, uSnow;
uniform vec4 uRime;       // Eis: Dicke (m), Art (0 keins, 1 Raueis, 2 Raureif, 3 Klareis), woher x, z
uniform vec2 uRimeBand;   // darunter kein Eis, darüber die volle Dicke (m über NN)
uniform float uDisplay;   // > 0: Belichtung, Filmkurve und Gamma gleich hier (Vorführung ohne Nachbearbeitung)
void main() {
    vec3 n = normalize(vNormal);
    float d = distance(vWorld, uEye);
    CliffSample s = cliffMaterial(vWorld, n, uRainWet, uSnow, d * uPixelAngle);
    vec3 nn = s.normal;
    // Eis (wie rime.glsl, ohne es einzubinden): Raueis auf der Luvseite in Federn, an Kanten (Relief) mehr
    float dk = uRime.x * smoothstep(uRimeBand.x, uRimeBand.y, vWorld.y);
    if (dk > 0.0) {
        float c = smoothstep(0.0005, 0.02, dk), art = uRime.y;
        vec3 von = vec3(uRime.z, 0.0, uRime.w);
        if (art > 2.5) { s.albedo = mix(s.albedo, s.albedo * 0.7 + vec3(0.04, 0.05, 0.06), c); }
        else {
            if (art < 1.5) {
                float um = smoothstep(0.04, 0.25, dk);
                c *= smoothstep(-0.25 - 0.5 * um, 0.55 - 0.4 * um, dot(nn, von) + 0.6 * (1.0 - dot(nn, n)));
                float hsh = fract(sin(dot(floor(vWorld.xz * 31.0 + vWorld.y * 19.0), vec2(127.1, 311.7))) * 43758.5453);
                c *= smoothstep(hsh * 0.5, hsh * 0.5 + 0.4, c);
            } else c *= 0.55 + 0.45 * max(nn.y, 0.0);
            s.albedo = mix(s.albedo, vec3(0.80, 0.84, 0.88), c);
        }
    }
    float lam = max(dot(nn, uSunDir), 0.0);
    vec3 col = s.albedo * (uSunColor * lam * uShade / 3.14159265 + uAmbient * (0.6 + 0.4 * nn.y));
    // nasser Fels glänzt ein wenig
    vec3 v = normalize(uEye - vWorld);
    vec3 h = normalize(uSunDir + v);
    col += uSunColor * uShade * s.wet * 0.04 * pow(max(dot(nn, h), 0.0), 60.0);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(uFogDist, 1.0)));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
