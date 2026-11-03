// bear.frag — com.dan.bear (Phase 18a): Fell des Braunbären. Vom dunklen Schokoladenbraun bis Blond nach dem Fell des
// Tiers, der Rücken heller mit hellen Spitzen über dem Buckel (grizzled), die Beine dunkel, die Schnauze heller, die
// Nase schwarz, kleine dunkle Augen, vorn helle Krallen. Haare als feine Strähnen längs des Körpers; der Wind bewegt
// sie (Normale), am Rand leuchtet das Fell gegen die Sonne. Nass dunkler, in Strähnen verklebt und glänzend [G, nach
// Fotos von Küstenbraunbären]. Licht wie die übrige Szene: Sonne und Mond als Bestrahlung, Himmel als Strahldichte.
in vec3 vWorld, vNrm;
in vec4 vBody;
in vec4 vInst;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSkyTop, uSkyHor;
uniform float uFogDist, uTime;
uniform vec2 uWind;                     // Wind (m/s) als Richtung · Stärke in x, z
const float PI = 3.14159265;

float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p), u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), u.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), u.x), u.y);
}

void main() {
    float s = vBody.x, ang = vBody.y, part = vBody.z, wet = clamp(vInst.x, 0.0, 1.0), tone = vInst.y;
    float top = cos(ang);
    vec3 dark = mix(vec3(0.045, 0.028, 0.016), vec3(0.12, 0.075, 0.040), tone);
    vec3 light = mix(vec3(0.16, 0.10, 0.05), vec3(0.42, 0.28, 0.14), tone);
    vec3 col;
    // Strähnen: lang längs s, kurz um die Achse
    vec2 hp = vec2(s * 140.0, ang * 9.0) + tone * 17.0;
    float strand = vnoise(hp) * 0.6 + vnoise(hp * vec2(2.7, 3.1) + 4.0) * 0.4;
    if (part < 0.5) {
        col = mix(dark, light, smoothstep(-0.6, 0.8, top) * 0.55 + 0.25);
        float grizzle = smoothstep(0.55, 0.85, strand) * smoothstep(0.2, 0.9, top) * (smoothstep(0.22, 0.30, s) - smoothstep(0.55, 0.70, s));
        col = mix(col, light * 1.6, grizzle * 0.5);
        col = mix(col, light * vec3(1.15, 1.05, 0.95), smoothstep(0.09, 0.03, s) * 0.8);
        float aa = abs(ang > PI ? ang - 2.0 * PI : ang);
        float eye = 1.0 - smoothstep(0.7, 1.0, length(vec2((s - 0.10) / 0.010, (aa - 0.80) / 0.13)));
        col = mix(col, vec3(0.01, 0.008, 0.006), eye);
        col = mix(col, vec3(0.018, 0.015, 0.013), 1.0 - smoothstep(0.008, 0.02, s));
    } else if (part < 1.5) {
        col = mix(dark, light, 0.35);
    } else {
        int seg = int(part + 0.5) - 10;
        seg -= 3 * (seg / 3);
        col = mix(dark, light, seg == 0 ? 0.22 : 0.06);
        if (seg == 2 && ang > 0.5) col = vec3(0.26, 0.22, 0.17);   // Krallen
    }
    col *= 0.82 + 0.36 * strand;
    if (vInst.z > 0.5) col = mix(col, light, 0.15);               // Junges: etwas heller
    // nass: dunkler, Strähnen verkleben (mehr Kontrast)
    col *= mix(1.0, 0.5, wet) * mix(1.0, 0.7 + 0.6 * strand, wet * 0.6);

    vec3 N = normalize(vNrm), V = normalize(uCam - vWorld);
    if (dot(N, V) < -0.2) N = -N;
    // Wind bewegt die Haare: die Normale kippt mit dem Wind und zittert
    float wv = length(uWind);
    if (wv > 0.1) {
        vec3 wd = normalize(vec3(uWind.x, 0.0, uWind.y));
        float flick = vnoise(hp * 0.25 + vec2(uTime * (1.0 + wv * 0.3), 0.0)) - 0.5;
        N = normalize(N + wd * min(0.35, wv * 0.025) * (0.6 + flick) * (1.0 - wet * 0.8));
    }
    float up = N.y * 0.5 + 0.5;
    vec3 amb = mix(uSkyHor * 0.25, mix(uSkyHor, uSkyTop, 0.5), up) * mix(0.55, 1.0, up);
    float ns = max(dot(N, uSunDir), 0.0), nm = max(dot(N, uMoonDir), 0.0);
    float wrap = max((dot(N, uSunDir) + 0.3) / 1.3, 0.0) * 0.28;
    vec3 c = col * ((uSunCol * (ns * 0.78 + wrap) + uMoonCol * nm) / PI + amb);
    // Gegenlicht am Rand (Haare streuen), trocken stärker
    float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * max(dot(-V, uSunDir), 0.0);
    c += uSunCol * light * rim * 0.12 * (1.0 - wet * 0.7) / PI;
    // Glanz: nass breit und deutlich, trocken kaum
    vec3 H = normalize(uSunDir + V);
    float shin = mix(8.0, 30.0, wet);
    float spec = pow(max(dot(N, H), 0.0), shin) * (shin + 2.0) / (8.0 * PI) * mix(0.01, 0.08, wet) * step(0.0, uSunDir.y) * (0.4 + strand);
    vec3 Rf = reflect(-V, N);
    vec3 env = mix(uSkyHor, uSkyTop, clamp(Rf.y * 1.5, 0.0, 1.0));
    float fres = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    c += uSunCol * spec * ns + env * fres * mix(0.02, 0.25, wet) * mix(0.3, 1.0, up);
    float d = length(vWorld - uCam);
    c = mix(c, uSkyHor, 1.0 - exp(-d / max(1.0, uFogDist)));
    frag = vec4(c, 1.0);
}
