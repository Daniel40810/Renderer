#version 430 core
// beachchair.frag — Baustoffe des Strandkorbs nach dem Foto: weißes Korbgeflecht (Strähnen über und unter, Lücken
// dunkel), Holz in Honig mit Maserung und Fugen, Markisenstoff in Streifen (dunkel je Korb, hell aus uStripeLight),
// Polster, Griffe aus Tau, abends ein Verschluss aus Latten. Im Korb dunkler (Umgebungslicht nur durch die Öffnung)
// und im eigenen Schatten: der Strahl zur Sonne muss vorn durch die Öffnung hinaus. In der Ferne gehen die Muster in
// ihr Mittel über (Bildpunkt in m). Licht als Zahlen: Sonne, Mond, Himmel oben und am Horizont, Dunst; Ausgabe linear und
// vorbelichtet, mit uDisplay > 0 fertig für den Bildschirm.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vLocal;
in vec2 vUv;
in vec3 vSunL;
flat in vec3 vInfo;
flat in vec4 vCol;
out vec4 frag;

uniform vec3 uEye;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor, uSkyTop, uSkyHorizon, uFogColor;
uniform float uFogDist, uDisplay;
uniform vec3 uWicker, uWood, uStripeLight, uRope;
uniform float uStripe;                 // Breite eines Streifens (m)
uniform vec4 uOpening;                 // innere halbe Breite, Oberkante Unterkasten, Ebene der Öffnung (z), Oberkante der Öffnung (y)

const float PI = 3.14159265;

float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}
// Muster der Größe s sichtbar? (1 nah, 0 wenn ein Bildpunkt größer ist als ein Drittel davon)
float sicht(float s, float pxm) { return 1.0 - smoothstep(0.12 * s, 0.4 * s, pxm); }

// Korbgeflecht: Felder zu 3 cm im Wechsel waagerecht und senkrecht (Leinwandbindung), je Feld drei runde Strähnen zu
// 1 cm, dazwischen dunkle Lücken; waagerechte Strähnen fangen mehr Licht als senkrechte. Weiter weg bleiben die Felder
// (je Feld ein Schatten), ganz fern das Mittel.
vec3 geflecht(vec3 c, vec2 uv, float pxm, out float bump) {
    float s = 0.03;
    vec2 g = uv / s, cell = floor(g), f = fract(g);
    bool quer = mod(cell.x + cell.y, 2.0) < 1.0;
    float a = quer ? f.y : f.x, along = quer ? f.x : f.y;
    float strang = sin(fract(a * 3.0) * PI);
    float rand = sin(a * PI);                       // zum Rand des Felds tauchen die Strähnen unter die querenden
    float lum = mix(0.4, 1.0, sqrt(max(strang, 0.0))) * mix(0.72, 1.0, sqrt(max(sin(along * PI), 0.0)));
    lum *= mix(0.55, 1.0, smoothstep(0.0, 0.3, strang)) * (quer ? 1.0 : 0.9) * (0.92 + 0.16 * hash2(cell));
    lum *= 0.85 + 0.15 * rand;
    float v = 1.0 - smoothstep(0.12 * s, 0.35 * s, pxm);
    bump = (strang - 0.5) * 0.5 * v;
    float feld = (quer ? 0.97 : 0.86) * (0.9 + 0.1 * sin(along * PI));
    float vf = 1.0 - smoothstep(0.35 * s, 1.0 * s, pxm);
    return c * mix(mix(0.84, feld * 0.95, vf), lum, v);
}

void main() {
    int part = int(vInfo.x + 0.5);
    bool inside = vInfo.z > 0.5;
    vec3 toEye = uEye - vWorld;
    float dist = length(toEye);
    vec3 V = toEye / max(dist, 1e-4);
    vec3 n = normalize(vNormal);
    if (dot(n, V) < 0.0) n = -n;
    float pxm = max(max(length(dFdx(vWorld)), length(dFdy(vWorld))), 1e-4);
    float seed = vCol.w;
    vec3 alb;
    float spec = 0.0, shin = 30.0, bump = 0.0;

    if (part == 0) {
        vec3 c = uWicker * (0.95 + 0.1 * fract(seed * 7.13));
        alb = geflecht(c, vUv, pxm, bump);
        alb *= 0.94 + 0.06 * vnoise(vUv * 3.0 + seed * 11.0);   // Wetter und Sand
        spec = 0.03;
    } else if (part == 1) {
        // Holz: Bretter 9 cm, Maserung längs, Fugen dunkel, lackiert
        vec2 uv = vUv;
        bool quer = abs(n.y) > 0.6;
        float brett = quer ? uv.x : uv.y;
        float fuge = 1.0 - smoothstep(0.0, 0.004, min(fract(brett / 0.09), 1.0 - fract(brett / 0.09)) * 0.09);
        float maser = vnoise(vec2((quer ? uv.y : uv.x) * 2.0, brett * 60.0 + seed * 9.0)) * 0.6 + vnoise(vec2((quer ? uv.y : uv.x) * 9.0, brett * 200.0)) * 0.4;
        alb = uWood * mix(0.92, (0.78 + 0.4 * maser) * (1.0 - 0.45 * fuge), sicht(0.03, pxm));
        spec = 0.12; shin = 40.0;
    } else if (part == 2 || part == 3) {
        // Markisenstoff: Streifen dunkel und hell im Wechsel quer zur Bahn; Gewebe fein; Polster weicher und satter
        float st = step(0.5, fract(vUv.x / (2.0 * uStripe) + 0.25));
        vec3 dunkel = vCol.rgb, hell = uStripeLight;
        float v = sicht(uStripe * 2.0, pxm);
        alb = mix(mix(dunkel, hell, 0.5), mix(dunkel, hell, st), v);
        float gewebe = 0.94 + 0.06 * vnoise(vUv * 900.0) * sicht(0.003, pxm);
        alb *= gewebe;
        if (part == 3) { alb *= 0.96; spec = 0.0; } else spec = 0.02;
    } else if (part == 4) {
        float drall = 0.85 + 0.15 * sin((vUv.x + vUv.y) * 600.0);
        alb = uRope * mix(0.92, drall, sicht(0.01, pxm));
    } else {
        // Verschluss: waagerechte Latten 10 cm mit Lücken, dahinter dunkel
        float l = fract(vUv.y / 0.10);
        float luecke = step(0.86, l);
        alb = mix(uWood * 0.85 * (0.85 + 0.3 * vnoise(vec2(vUv.x * 3.0, floor(vUv.y / 0.10) * 7.0 + seed))), vec3(0.02), luecke * sicht(0.1, pxm));
        alb = mix(alb, uWood * 0.75, 1.0 - sicht(0.1, pxm));
        spec = 0.06;
    }

    // Umgebungslicht: im Korb nur durch die Öffnung (hinten und oben dunkler), unten am Boden dunkler
    float ao = 1.0;
    float sun = 1.0;
    vec3 L = vLocal;
    if (inside) {
        float tief = smoothstep(-0.45, uOpening.z + 0.1, L.z);
        ao = mix(0.32, 0.75, tief) * mix(1.0, 0.8, smoothstep(uOpening.w - 0.5, uOpening.w, L.y));
        // eigener Schatten: der Strahl zur Sonne muss vorn durch die Öffnung (Rechteck in der Ebene z = Öffnung)
        vec3 s = normalize(vSunL);
        if (s.z <= 0.02) sun = 0.0;
        else {
            float t = max(0.0, (uOpening.z - L.z) / s.z);
            vec3 q = L + s * t;
            float rand = 0.04;
            float inX = 1.0 - smoothstep(uOpening.x - rand, uOpening.x + rand, abs(q.x));
            float inY = 1.0 - smoothstep(uOpening.w - rand, uOpening.w + rand, q.y);
            sun = inX * inY;
        }
    }
    ao *= 0.55 + 0.45 * smoothstep(0.0, 0.35, L.y);

    // Licht: Sonne (Lambert, wenig Glanz), Mond, Himmel nach der Neigung der Fläche
    vec3 nb = normalize(n + bump * 0.35 * V);
    float ndl = max(dot(nb, uSunDir), 0.0) * sun;
    vec3 H = normalize(uSunDir + V);
    float sp = spec * pow(max(dot(nb, H), 0.0), shin) * (shin + 8.0) / 8.0 * sun * step(0.0, dot(n, uSunDir));
    float up = 0.5 + 0.5 * n.y;
    vec3 sky = mix(uSkyHorizon, uSkyTop, up) * mix(0.6, 1.0, up);
    vec3 col = alb * (uSunColor * ndl / PI + uMoonColor * max(dot(nb, uMoonDir), 0.0) / PI + sky * ao) + uSunColor * sp / PI;
    // Dunst
    col = mix(uFogColor, col, exp(-dist / max(uFogDist, 1.0)));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
