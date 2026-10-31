#version 430 core
// dune.frag — Sand mit Windrippeln und Sandfahnen (seit Sylt Phase 8).
// Rippel quer zum Wind: Luv lang (70 %), Lee kurz (30 %), Kämme mit gröberem, dunklerem Korn. Die Phase läuft mit einer
// Wellenlänge für das Feld (uLambda), damit die Kämme über weite Wege zusammenpassen; Höhe und Ausprägung je Ecke. Das
// Wandern mit c je Ecke wird wie eine Flusskarte aus zwei um eine halbe Periode versetzten Lagen überblendet (der Versatz
// bleibt so beschränkt, auch wo c sich ändert). Die Kämme schlängeln sich leicht. Fern werden sie ausgeblendet, bevor sie
// flimmern. Darüber zieht, wo Sand fliegt, ein heller Schleier in Fahnen mit dem Wind (gleiche Form wie Dune.gust:
// lang in Windrichtung, schmal quer). Nasser Sand dunkel, Bewuchs olivgrün getönt.
// uDisplay > 0: gleich mit Filmkurve und Gamma (Vorführung ohne Nachbearbeitung), sonst linear und vorbelichtet.
in vec3 vPos;
in vec4 vState;
in vec4 vRipple;
out vec4 frag;
uniform vec3 uEye, uSunDir, uSunColor, uSkyTop, uSkyHorizon, uFogColor;
uniform vec2 uWindTo;
uniform float uFogDist, uTime, uDisplay, uStreamLen, uStreamWid, uStreamSpeed, uLambda, uFlowPeriod;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i), b = hash(i + vec2(1, 0)), c = hash(i + vec2(0, 1)), d = hash(i + vec2(1, 1));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    vec2 to = normalize(uWindTo), side = vec2(-to.y, to.x);
    float q = vState.x, wet = clamp(vState.y / 0.06, 0.0, 1.0), cover = clamp(vState.z / 0.08, 0.0, 1.0), shadow = vState.w;
    float lam = max(uLambda, 0.02), H = vRipple.y * uLambda / max(vRipple.x, 0.02), c = vRipple.z;
    // Grundnormale aus dem Gitter
    vec3 n = normalize(cross(dFdx(vPos), dFdy(vPos)));
    if (n.y < 0.0) n = -n;
    // Rippel: zwei Lagen, je um c·τ gewandert, dreieckig überblendet
    float s = dot(vPos.xz, to), u = dot(vPos.xz, side);
    float wob = 0.18 * lam * sin(u / (5.0 * lam) * 6.2832 + vnoise(vPos.xz / (8.0 * lam)) * 4.0) + 0.25 * lam * (vnoise(vPos.xz / (14.0 * lam)) - 0.5);
    float fade = 1.0 - smoothstep(0.15, 0.45, fwidth(s / lam));
    float slope = 0.0, crest = 0.0;
    for (int k = 0; k < 2; k++) {
        float f = fract(uTime / uFlowPeriod + 0.5 * float(k));
        float wgt = 1.0 - abs(2.0 * f - 1.0);
        float p = fract((s + wob - c * f * uFlowPeriod) / lam);
        // Steigung des Profils: Luv 0…0,7 hinauf, Lee 0,7…1 hinab (weich an Kamm und Trog)
        float up = smoothstep(0.0, 0.08, p) * (1.0 - smoothstep(0.62, 0.72, p));
        float dn = smoothstep(0.66, 0.74, p) * (1.0 - smoothstep(0.94, 1.0, p));
        slope += wgt * (H / lam) * (up / 0.7 - dn / 0.3);
        crest += wgt * exp(-pow((p - 0.7) / 0.08, 2.0));
    }
    slope *= fade;
    crest *= fade * clamp(H / 0.004, 0.0, 1.0);
    vec3 rn = normalize(n - vec3(to.x, 0.0, to.y) * slope * 1.6);
    // Farbe des Sands
    vec3 dry = vec3(0.74, 0.62, 0.44), damp = vec3(0.42, 0.35, 0.25), grass = vec3(0.42, 0.45, 0.28);
    vec3 alb = mix(dry, damp, wet);
    alb = mix(alb, alb * vec3(0.78, 0.74, 0.70), crest * 0.6);            // gröberes Korn auf den Kämmen
    alb *= 0.93 + 0.14 * vnoise(vPos.xz * 3.1) * (1.0 - wet * 0.5);        // Körnung
    alb = mix(alb, grass, cover * 0.75);
    // Licht
    vec3 L = normalize(uSunDir);
    float sun = max(dot(rn, L), 0.0);
    vec3 sky = mix(uSkyHorizon, uSkyTop, rn.y * 0.5 + 0.5);
    vec3 col = alb * (uSunColor * sun + sky * 0.9);
    // Sandfahnen: heller Schleier, wo Sand fliegt, in Fahnen mit dem Wind
    float sa = s - uTime * uStreamSpeed;
    float st = vnoise(vec2(sa / uStreamLen, u / uStreamWid)) * 0.65 + vnoise(vec2(sa / (uStreamLen * 4.0), u / (uStreamWid * 6.0)) + 3.7) * 0.35;
    float veil = sqrt(clamp(q, 0.0, 1.5)) * smoothstep(0.3, 0.7, st) * (1.0 - shadow) * (1.0 - wet);
    vec3 haze = vec3(0.86, 0.78, 0.62) * (uSunColor * 0.55 + sky);
    col = mix(col, haze, veil * 0.5);
    // Dunst
    float dist = length(vPos - uEye);
    col = mix(uFogColor, col, exp(-dist / uFogDist));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
