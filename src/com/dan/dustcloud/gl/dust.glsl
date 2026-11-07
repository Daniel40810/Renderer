// dust.glsl — com.dan.dustcloud (aus LituyaBay1958, Werkbuch Welle W3 E3, W3c WF15 B): Dichten für einen Strahlengang.
//   dustLayerDensity: die Staubwolke aus DustCloud (Oberkante und optische Dicke je Zelle) als Schicht über dem Grund;
//     weicher Fuß, aufquellende Oberkante in Ballen, Turbulenz mit Lücken; die mittlere optische Dicke der Säule bleibt
//     die der Rechnung [G].
//   dustPlumeDensity: die Staubfahne (Plume) über dem Abbruch: Säule, Kopf wie ein Pilz, Ballen; dazu die Tiefe zur Sonne
//     für das Glühen oben und das Blaugrau unten [G].
//   dustPhaseHG: Henyey-Greenstein.
// Rauschen aus einer kachelbaren Textur 64³ (DustGl.noiseTexture), Texturstelle des Wirts.
uniform sampler3D uDcNoise;
float dcNoise3(vec3 x) { return texture(uDcNoise, x / 64.0).r; }
float dcFbm3(vec3 p) { return dcNoise3(p) * 0.6 + dcNoise3(p * 2.03 + 7.1) * 0.3 + dcNoise3(p * 4.1 + 3.3) * 0.1; }
float dcFbm2(vec3 p) { return dcNoise3(p) * 0.67 + dcNoise3(p * 2.03 + 7.1) * 0.33; }

// Staub: Dichte (1/m) an p aus Oberkante s.x und optischer Dicke s.y über dem Grund base; time in s.
float dustLayerDensity(vec3 p, vec2 s, float base, float time) {
    if (s.y < 1e-3) return 0.0;
    float dicke = max(s.x - base, 5.0);
    // Lituya W3c (fünfte Probe): die dichte Wolke stand mit glatter Oberkante und dunklem Inneren wie ein Tafelberg.
    // Jetzt quillt die Oberkante in Ballen (3D), sie läuft weicher aus
    float n = dcFbm3(p / 85.0 + vec3(0.0, -time * 0.012, time * 0.008));
    float n2 = dcNoise3(p / 30.0 + vec3(time * 0.01, -time * 0.02, 0.0));
    float oben = s.x + (n - 0.5) * 1.6 * dicke + (n2 - 0.5) * 0.35 * dicke;
    float f = smoothstep(base - 2.0, base + 0.12 * dicke, p.y) * (1.0 - smoothstep(oben - 0.6 * dicke, oben, p.y));
    // Turbulenz mit Lücken (erste Probe auf der Radeon: 0,35–1,65 ließ die dichte Wolke eine glatte Fläche bleiben)
    float t = dcFbm3(p / 28.0 + vec3(time * 0.02, -time * 0.03, 0.0));
    t = pow(t, 2.2) * 3.2 * (0.6 + 0.8 * dcNoise3(p / 90.0 + 3.7));
    return s.y / dicke * f * t * 0.7;
}

// Fahne: Achse x, z, Oberkante (m), Alter (s); Halbmesser der Platte (m), Richtung, in die der Wind weht (x, z)
uniform vec4 uDcPlume;
uniform vec3 uDcPlumeR;

// Fahne: Dichte (1/m) und Tiefe zur Sonne an p; level: Pegel (Fuß der Säule)
vec2 dustPlumeDensity(vec3 p, float level) {
    float alter = uDcPlume.w, top = uDcPlume.z;
    float y = p.y - level;
    if (y < -5.0 || p.y > top + 220.0) return vec2(0.0);
    float hrel = clamp(y / max(top - level, 30.0), 0.0, 1.2);
    // die Achse neigt sich mit der Höhe in den Wind, zum Kopf hin stärker
    vec2 ax = uDcPlume.xy + uDcPlumeR.yz * y * 0.10 * smoothstep(0.0, 90.0, alter) * (0.6 + 0.8 * hrel);
    if (length(p.xz - ax) > uDcPlumeR.x * 2.6 + 60.0) return vec2(0.0);   // grob außerhalb: ohne Rauschen weiter
    // Säule über der Platte, nach oben breiter, Kopf quillt pilzförmig auf [G]
    float R = uDcPlumeR.x * (0.55 + 0.6 * hrel) * (0.6 + 0.4 * smoothstep(0.0, 40.0, alter));
    R *= 1.0 + 0.7 * smoothstep(0.72, 1.0, hrel);
    float bq = dcFbm3(p / 150.0 + vec3(0.0, -alter * 0.02, alter * 0.006));
    float bq2 = dcNoise3(p / 55.0 + vec3(0.0, -alter * 0.06, 0.0));
    float r = length(p.xz - ax) + (bq - 0.5) * 2.6 * R + (bq2 - 0.5) * 0.5 * R;   // ausgefranster Rand
    float form = 1.0 - smoothstep(0.45 * R, R, r);
    float kopf = 1.0 - smoothstep(top - 0.18 * (top - level), top + 60.0, p.y + (bq - 0.5) * 380.0 + (bq2 - 0.5) * 90.0);
    float fuss = smoothstep(-5.0, 60.0, y);
    if (form * kopf * fuss < 1e-3) return vec2(0.0);   // außerhalb der Hülle: die Ballen sparen
    // Ballen (Blumenkohl): zwei Lagen, die mitsteigen; dazwischen Lücken
    float ballen = dcFbm3(p / 95.0 + vec3(alter * 0.01, -alter * 0.045, 0.0));
    ballen = smoothstep(0.40, 0.60, ballen) * (0.45 + 1.1 * smoothstep(0.3, 0.7, dcNoise3(p / 26.0 + vec3(0.0, -alter * 0.11, 0.0))));
    float lebt = smoothstep(0.0, 6.0, alter) * (1.0 - smoothstep(200.0, 900.0, alter));
    float dichte = form * kopf * fuss * ballen * lebt * 0.0045 * mix(1.3, 0.55, hrel);
    // Tiefe zur Sonne: bis zur Oberkante und zum Rand (grob) [G]
    float zurSonne = 0.0045 * lebt * (max(top - p.y, 0.0) * 0.35 + max(R - r, 0.0) * 0.6);
    return vec2(dichte, zurSonne);
}

float dustPhaseHG(float cs, float g) { return (1.0 - g * g) / (4.0 * 3.14159265 * pow(1.0 + g * g - 2.0 * g * cs, 1.5)); }
