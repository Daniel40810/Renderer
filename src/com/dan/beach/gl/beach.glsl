// beach.glsl — Relief und Korn des Strands (com.dan.beach) für den Geländeshader einer Szene: #include
// "/com/dan/beach/gl/beach.glsl", dann beachSurface(...) dort aufrufen, wo Sand liegt. Die Uniforms setzt BeachGl.bind.
// Eine Textur (RGBA16F, Umlauf, Mipmaps): dR/du, dR/dv, R (Relief, Abweichung 1, aus dem Asset), K (Korn, Abweichung 1).
// Zwei Oktaven des Reliefs (die zweite kleiner und gedreht), zwei des Korns; in der Ferne und wenn ein Bildpunkt die
// Mulden nicht mehr fasst, geht alles in die Ebene über. Kein #version: der Baustein wird eingesetzt.
uniform sampler2D uBeachTex;
uniform int uBeachOn;            // 0 = aus
uniform vec4 uBeachA;            // Kachel des Reliefs (m), Abweichung (m), Kachel des Korns (m), Kontrast des Korns
uniform vec4 uBeachB;            // zweite Oktave: Anteil, cos, sin der Drehung, Gewicht
uniform vec4 uBeachC;            // Anteil nass, Anteil ruhig, Mulden, Reichweite (m)
uniform vec4 uBeachOff;          // Verschiebung der Oktaven (Kacheln)
uniform float uBeachSlope;       // mittlere Neigung des Reliefs je Achse (je Kachel, Abweichung 1)

struct BeachSample {
    vec3 normal;     // gestörte Normale
    float albedo;    // Faktor auf die Helligkeit, um 1
    float height;    // Höhe des Reliefs über dem Gelände (m)
    float sun;       // Faktor auf das direkte Sonnenlicht: das Relief ist im Mittel so hell wie ebener Sand
};

// Mittel von max(0, n·l) über Neigungen σ je Achse bei der Sonnenhöhe e: σ cos e (φ(a) + a Φ(a)), a = tan e / σ;
// zurück kommt sin e / Mittel (Beach.sunFactor), begrenzt auf 0,2 … 1
float beachSunFactor(float sigma, float sinE) {
    if (sinE <= 0.0) return 0.0;
    if (sigma < 1e-5) return 1.0;
    float cosE = sqrt(max(0.0, 1.0 - sinE * sinE)), a = sinE / max(1e-6, cosE * sigma);
    float x = a / sqrt(2.0), t = 1.0 / (1.0 + 0.3275911 * x);
    float erfx = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x);
    float m = cosE * sigma * (exp(-a * a * 0.5) * 0.39894228 + a * 0.5 * (1.0 + erfx));
    return clamp(sinE / max(m, 1e-6), 0.2, 1.0);
}

// Ein Abgriff: Ort gedreht (cs = cos, sin), durch die Kachel, verschoben; Neigung zurück in die Welt (je m)
vec4 beachTap(vec2 p, float tile, vec2 cs, vec2 off) {
    vec2 q = vec2(cs.x * p.x + cs.y * p.y, -cs.y * p.x + cs.x * p.y);
    vec4 t = texture(uBeachTex, q / tile + off);
    vec2 g = t.xy / tile;
    return vec4(cs.x * g.x - cs.y * g.y, cs.y * g.x + cs.x * g.y, t.z, t.w);
}

// Der Strand am Ort p (x, z), außerhalb jeder ungleichmäßigen Verzweigung aufrufen (wo kein Sand ist, mit 0 mischen):
// n Normale des Geländes, pxm Größe eines Bildpunkts (m), dist Abstand vom Auge (m),
// trocken 0..1 (0 nass), zertreten 0..1 (0 niemand geht dort), sun Richtung zur Sonne
BeachSample beachSurface(vec2 p, vec3 n, float pxm, float dist, float trocken, float zertreten, vec3 sun) {
    BeachSample s;
    s.normal = n;
    s.albedo = 1.0;
    s.height = 0.0;
    s.sun = 1.0;
    if (uBeachOn == 0) return s;   // einheitlich für das ganze Bild
    // keine weitere Verzweigung vor den Abgriffen: die Mipmaps brauchen die Ableitungen im Bild
    float nah = 1.0 - smoothstep(uBeachC.w * 0.6, uBeachC.w, dist);
    float amp = uBeachA.y * mix(uBeachC.x, 1.0, clamp(trocken, 0.0, 1.0)) * mix(uBeachC.y, 1.0, clamp(zertreten, 0.0, 1.0));
    float t1 = uBeachA.x, t2 = t1 * uBeachB.x;
    // eine Mulde ist rund 1/20 Kachel groß: sichtbar, solange ein Bildpunkt kleiner ist als ein Fünftel davon
    float f1 = 1.0 - smoothstep(t1 * 0.004, t1 * 0.02, pxm), f2 = 1.0 - smoothstep(t2 * 0.004, t2 * 0.02, pxm);
    vec4 a = beachTap(p, t1, vec2(1.0, 0.0), uBeachOff.xy);
    vec4 b = beachTap(p, t2, uBeachB.yz, uBeachOff.zw);
    vec2 g = (a.xy * f1 + b.xy * (uBeachB.w * f2)) * amp * nah;
    float h = a.z * f1 + b.z * uBeachB.w * f2;
    s.height = h * amp * nah;
    // Normale: Neigung des Reliefs zur Normale des Geländes (der Strand ist fast eben)
    s.normal = normalize(n + vec3(-g.x, 0.0, -g.y));
    // Korn in zwei Oktaven, Mulden dunkler
    float gt = uBeachA.z;
    float fg = 1.0 - smoothstep(gt * 0.0015, gt * 0.008, pxm);
    float k = 0.6 * beachTap(p, gt, vec2(1.0, 0.0), uBeachOff.yx).w + 0.4 * beachTap(p, gt * 0.37, vec2(uBeachB.y, -uBeachB.z), uBeachOff.wz).w;
    // was vom Relief zu sehen ist, ist im Mittel nicht heller als ebener Sand
    float sa = uBeachSlope / t1 * f1, sb = uBeachSlope / t2 * f2 * uBeachB.w;
    s.sun = beachSunFactor(amp * nah * sqrt(sa * sa + sb * sb), dot(n, sun));
    float rel = uBeachA.y > 0.0 ? amp / uBeachA.y : 0.0;
    s.albedo = (1.0 + uBeachA.w * k * fg * nah) * (1.0 + uBeachC.z * clamp(h, -2.0, 2.0) * rel * 0.5 * f1 * nah);
    return s;
}
