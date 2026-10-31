// Schirm der Ohrenqualle: milchiger Körper mit dichterem Rand, vier ringförmige Gonaden (violett bis rosa), vier
// Mundarme in der Mitte, feine Radialkanäle, Saum aus kurzen Tentakeln. Gesehen durch die Oberfläche: Durchlass nach
// Fresnel (Schlick, n = 1,33), Abschwächung mit der Tiefe und Farbe des Wassers auf dem Weg hinab und hinauf.
in vec2 vUv;
in vec3 vWorld;
flat in vec4 vA1;
flat in vec4 vA2;
uniform vec3 uEye, uSunDir, uSunColor, uMoonDir, uMoonColor, uSky, uFogColor, uExt;
uniform float uFogDist;
out vec4 oColor;

const float PI = 3.14159265;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    float contr = vA1.y, depth = vA1.z, strand = vA1.w;
    float hue = vA2.x, seenAmt = vA2.y, size = vA2.z;
    float r = length(vUv), ang = atan(vUv.y, vUv.x);
    // Saum: kurze Tentakel bis 1,1 (nur schwimmend, beim Schlag eingezogen)
    float fringe = 0.0;
    if (r > 1.0) {
        if (strand > 0.5) discard;
        float k = fract(ang * 96.0 / (2.0 * PI));
        fringe = smoothstep(0.35, 0.0, abs(k - 0.5) - 0.12) * smoothstep(1.1 - 0.05 * contr, 1.0, r) * 0.5;
        if (fringe < 0.01) discard;
    }
    // Körper: dünn in der Mitte, dichter zum Rand (die Glocke wird zur Seite dicker gesehen), beim Schlag dichter
    float body = 0.16 + 0.24 * smoothstep(0.55, 0.98, r) + 0.10 * contr * smoothstep(0.6, 1.0, r);
    body *= smoothstep(1.0, 0.97, r);
    // acht Radialkanäle mit Verzweigung nach außen
    float canal = 0.0;
    if (r > 0.42 && r < 0.97) {
        float k = fract(ang * 8.0 / (2.0 * PI) + 0.0625);
        float k2 = fract(ang * 32.0 / (2.0 * PI));
        canal = smoothstep(0.03, 0.0, abs(k - 0.5) - 0.005) * 0.10 + smoothstep(0.08, 0.0, abs(k2 - 0.5)) * 0.04 * smoothstep(0.7, 0.95, r);
    }
    // vier Gonaden: Ringe um 0,36 vom Mittelpunkt, nach innen offen (Hufeisen)
    float gon = 0.0;
    for (int i = 0; i < 4; i++) {
        float a = PI * 0.25 + float(i) * PI * 0.5;
        vec2 c = 0.36 * vec2(cos(a), sin(a));
        vec2 d = vUv - c;
        float rr = length(d);
        float ring = smoothstep(0.055, 0.0, abs(rr - 0.15) - 0.018);
        float open = smoothstep(0.55, 0.9, dot(normalize(d + 1e-5), normalize(-c)));
        gon = max(gon, ring * (1.0 - 0.85 * open));
    }
    // vier Mundarme: kurze, gekräuselte Bänder aus der Mitte
    float arms = 0.0;
    for (int i = 0; i < 4; i++) {
        float a = float(i) * PI * 0.5;
        vec2 u = vec2(cos(a), sin(a));
        float along = dot(vUv, u), across = abs(dot(vUv, vec2(-u.y, u.x)) + 0.02 * sin(along * 60.0));
        arms = max(arms, smoothstep(0.045, 0.0, across - 0.01) * smoothstep(0.32, 0.0, along) * step(0.0, along));
    }
    vec3 milk = vec3(0.86, 0.89, 0.95);
    vec3 gcol = mix(vec3(0.55, 0.25, 0.88), vec3(0.95, 0.38, 0.62), hue);
    float a = max(body, fringe) + canal + 0.8 * gon + 0.3 * arms;
    vec3 alb = mix(milk, gcol, clamp(0.8 * gon / max(a, 1e-3), 0.0, 1.0));
    a = clamp(a, 0.0, 0.85);
    // Licht: Sonne und Mond von oben (unter Wasser gebrochen und abgeschwächt), Himmel
    vec3 E = uSunColor * max(uSunDir.y, 0.0) + uMoonColor * max(uMoonDir.y, 0.0);
    vec3 col;
    vec3 V = normalize(uEye - vWorld);
    float cv = clamp(V.y, 0.02, 1.0);
    if (strand > 0.5) {
        // gestrandet: Gallert auf dem Sand, glänzt, wird mit dem Trocknen flacher und blasser
        float dry = clamp(strand - 1.0, 0.0, 1.0);
        a *= mix(1.15, 0.35, dry);
        a = max(a, 0.28 * (1.0 - dry) * smoothstep(1.0, 0.85, r));
        col = alb / PI * E + alb * uSky;
        vec3 Hh = normalize(normalize(uSunDir) + V);
        col += uSunColor * 0.25 * pow(max(Hh.y, 0.0), 120.0) * (1.0 - dry) * smoothstep(1.0, 0.6, r);
    } else {
        // gebrochener Blick: Weg unter Wasser hinauf ≈ Tiefe / cos des Winkels im Wasser
        float sinT = sqrt(max(0.0, 1.0 - cv * cv)) / 1.33, cosT = sqrt(1.0 - sinT * sinT);
        vec3 att = exp(-uExt * (depth + depth / cosT));
        col = (alb / PI * E * 0.85 + alb * uSky) * att;
        float F = 0.02 + 0.98 * pow(1.0 - cv, 5.0);
        a *= (1.0 - F) * seenAmt;
    }
    float dist = length(uEye - vWorld);
    col = mix(col, uFogColor, 1.0 - exp(-dist / max(uFogDist, 1.0)));
    if (a < 0.004) discard;
    oColor = vec4(col, a);
}
