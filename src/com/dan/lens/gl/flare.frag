// flare.frag — die Sonne im Objektiv (com.dan.lens), addiert über das Bild auf dem Sensor:
//  • Geisterbilder: je Doppelspiegelung ein Bild der Blende, je Farbkanal mit eigener Lage (Vielfaches der Lage der Sonne),
//    eigener Größe und Stärke (Fluss durch die Pupille · Reflexionsgrade der Vergütung / Fläche), aus Lens.optics;
//  • Stern: Beugung an den geraden Kanten der Blende, je Strahl Leuchtdichte E · k / x² · Profil(y), das Profil ist die
//    Sehne der Sonnenscheibe (Faltung mit ihrem Bild); x längs, y quer zum Strahl (mm auf dem Sensor).
// Sichtbarkeit der Sonne: Anteil von 13 Punkten auf ihrer Scheibe, an denen die Tiefe Himmel zeigt, mal uSunVis (Wolken).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDepth;
uniform vec2 uSun;              // Sonne im Bild (Bildpunkte, voll); außerhalb: keine Reflexe
uniform vec3 uSunE;             // Bestrahlungsstärke der Sonne senkrecht (Einheiten der Szene)
uniform float uSunVis;
uniform float uSunMm;           // Halbmesser des Sonnenbilds (mm)
uniform int uGhosts;
uniform vec4 uG[144];           // je Geisterbild 3 × vec4: (Lage R, G, B, Vorzeichen), (Halbmesser R, G, B), (Stärke R, G, B)
uniform int uSpikes;
uniform float uSpikeA[22];
uniform vec3 uSpikeK;
uniform float uGhostGain, uStarGain;
uniform int uGhostOn, uStarOn;

float sunVisible() {
    if (any(lessThan(uSun, vec2(0.0))) || any(greaterThan(uSun, uFull))) return 0.0;
    float rp = max(uSunMm / uPitch, 1.0);
    float n = 0.0, s = 0.0;
    for (int i = 0; i < 13; i++) {
        float a = float(i) * 2.39996, rr = i == 0 ? 0.0 : sqrt(float(i) / 12.0) * rp * 0.9;
        vec2 p = clamp(uSun + rr * vec2(cos(a), sin(a)), vec2(0.5), uFull - 0.5);
        float d = texture(uDepth, p / uFull).r;
        float z = viewDepth(d);
        s += ((uReversed == 1 && d <= 0.0) || z > uFar * 0.98) ? 1.0 : 0.0;
        n += 1.0;
    }
    return s / n;
}

void main() {
    float vis = sunVisible() * uSunVis;
    if (vis <= 0.0) { frag = vec4(0.0); return; }
    vec2 c = 0.5 * uFull;
    vec2 xmm = (vUV * uFull - c) * uPitch;          // dieser Bildpunkt, mm vom Bildmittelpunkt
    vec2 smm = (uSun - c) * uPitch;                 // die Sonne
    vec3 o = vec3(0.0);
    if (uGhostOn == 1) {
        for (int g = 0; g < uGhosts; g++) {
            vec4 ra = uG[3 * g], rd = uG[3 * g + 1], it = uG[3 * g + 2];
            for (int ch = 0; ch < 3; ch++) {
                vec2 ctr = smm * ra[ch];
                float rad = rd[ch];
                vec2 dv = xmm - ctr;
                if (dot(dv, dv) > rad * rad * 2.2) continue;
                vec2 q = dv / rad * ra.w;                      // Pupillenmaß; umgekehrt, wenn das Bild der Blende kopfsteht
                float w = 0.06 + uPitch / rad;
                o[ch] += it[ch] * pupil(q, w, 0.0, vec2(1.0, 0.0));
            }
        }
        o *= uGhostGain;
    }
    if (uStarOn == 1 && uSpikes > 0) {
        vec2 dv = xmm - smm;
        float rs = max(uSunMm, 0.7 * uPitch);
        vec3 st = vec3(0.0);
        for (int i = 0; i < uSpikes; i++) {
            vec2 e = vec2(cos(uSpikeA[i]), sin(uSpikeA[i]));
            float x = dot(dv, e), y = dot(dv, vec2(-e.y, e.x));
            if (x <= 0.0 || abs(y) >= rs) continue;
            float prof = 2.0 * sqrt(rs * rs - y * y) / (PI * rs * rs);
            st += uSpikeK * prof / (x * x + rs * rs);
        }
        o += st * uStarGain;
    }
    frag = vec4(o * uSunE * vis, 0.0);
}
