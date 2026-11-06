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
// seit LituyaBay1958 W6 Runde 5: Rinde mit Muster auf Holz ohne Textur (TreeGl.barkDetail, TreeSpecies.barkStyle)
in vec3 vLocal;
uniform vec4 uBarkPat;                  // an, Stil (wie com.dan.forest.Species.BarkStyle), Flechten 0..1, Moos 0..1 (Runde 6)
uniform vec3 uMossSide;                 // seit W6 Runde 6: feuchte Seite (Welt, waagerecht; 0 = keine), Light.mossSide
out vec4 fragColor;

// RINDE BEGINN (bis RINDE ENDE gleich in forest.frag und tree.frag, geprüft von TreeCheck)
// Rinde mit Muster (seit LituyaBay1958 W6 Runde 5) [G]; derselbe Text steht in com.dan.tree/gl/tree.frag.
// q: Ort im Baum (m, vor Wind und Drehung, Fuß bei 0, y oben), n: Normale (Welt), wp: Ort (Welt), alb: Rindenfarbe der Art.
// Stil: 1 glatt (Erle, Buche), 2 gefurcht (Hemlock, Douglasie, Eiche), 3 schuppig (Fichte), 4 Platten (Kiefer),
// 5 Papier (Birke). flechte: Anteil Flechten 0..1. Das Muster blendet nach der Größe eines Bildpunkts zum Mittel aus.
float bkHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}
float bkNoise(vec3 x) {
    vec3 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(bkHash(i), bkHash(i + vec3(1, 0, 0)), f.x), mix(bkHash(i + vec3(0, 1, 0)), bkHash(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(bkHash(i + vec3(0, 0, 1)), bkHash(i + vec3(1, 0, 1)), f.x), mix(bkHash(i + vec3(0, 1, 1)), bkHash(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}
float bkFbm(vec3 x) { return 0.55 * bkNoise(x) + 0.30 * bkNoise(x * 2.03 + 17.0) + 0.15 * bkNoise(x * 4.11 + 31.0); }
// Zellen: x Abstand zur nächsten Mitte, y Abstand zur Grenze (F2 − F1), z Kennung
// drei Zufallswerte aus einem Abruf (Hoskins, hash33): ein Drittel der Rechnung für das Zellrauschen
vec3 bkHash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

vec3 bkCell(vec3 x) {
    // 3 × 3 × 3 Nachbarn, je Zelle ein Abruf für den Punkt; die Nummer nur für die nächste Zelle (W6 Runde 5: vorher drei
    // Abrufe je Zelle und einer je Treffer, das Muster kostete im Nadelwald nah mehrere Millisekunden)
    vec3 i = floor(x), f = fract(x);
    float d1 = 9.0, d2 = 9.0;
    vec3 best = vec3(0.0);
    for (int k = -1; k <= 1; k++)
        for (int j = -1; j <= 1; j++)
            for (int h = -1; h <= 1; h++) {
                vec3 c = vec3(h, j, k);
                float d = length(c + bkHash3(i + c) - f);
                if (d < d1) { d2 = d1; d1 = d; best = c; } else if (d < d2) d2 = d;
            }
    return vec3(d1, d2 - d1, bkHash(i + best + 5.3));
}
float bkFade(float scale, float fp) { return 1.0 - smoothstep(0.35 * scale, 1.2 * scale, fp); }

// Ergebnis: Farbe in rgb, Höhe des Reliefs (0..1) in a
vec4 barkPattern(int style, vec3 q, vec3 alb, float flechte, float fp) {
    vec3 c = alb;
    // Flechten und Borke im Verhältnis zur Helligkeit der Rinde (Bezug 0,30 [G]), nicht als feste Farbe: alb trägt schon
    // die Verdeckung durch die Krone (forest backt sie ein), sonst leuchteten Flechten im Schatten (W6 Runde 5)
    float rel = min(dot(alb, vec3(0.2126, 0.7152, 0.0722)) / 0.30, 2.5);
    float h = 0.5;
    if (style == 1) {
        // glatt (Erle, Buche): hellgraue Rinde mit Flecken, quer liegende Korkporen (Lentizellen), weiße Krustenflechten
        // in einzelnen Flecken mit scharfem Rand [G]
        float w1 = bkFade(0.25, fp), w2 = bkFade(0.03, fp), w3 = bkFade(0.12, fp);
        float m = bkFbm(q * vec3(3.0, 1.2, 3.0));
        c *= mix(1.0, 0.82 + 0.36 * m, w1);
        float len = bkNoise(q * vec3(28.0, 140.0, 28.0));
        float pore = smoothstep(0.78, 0.86, len) * w2;
        c *= 1.0 - 0.45 * pore;
        float fl = bkFbm(q * vec3(3.0, 2.0, 3.0) + 7.0);
        float schwelle = 0.78 - 0.22 * flechte;
        float lich = smoothstep(schwelle, schwelle + 0.025, fl) * w3 + (1.0 - w3) * 0.2 * flechte;
        vec3 lc = mix(vec3(0.52, 0.54, 0.50), vec3(0.64, 0.66, 0.61), bkNoise(q * 30.0)) * rel;
        c = mix(c, lc, lich * 0.8);
        h = 0.5 + 0.15 * (m - 0.5) * w1 - 0.3 * pore + 0.12 * lich;
    } else if (style == 2) {
        // gefurcht (Hemlock, Douglasie, Eiche): lange, schmale Borkenrippen, die sich verzweigen und wieder treffen;
        // dazwischen tiefe, dunkle Furchen; quer über die Rippen feine Risse [G]
        float w1 = bkFade(0.10, fp), w2 = bkFade(0.04, fp);
        vec3 z = bkCell(q * vec3(9.0, 1.6, 9.0) + vec3(0.0, bkNoise(q * vec3(3.0, 0.5, 3.0)) * 0.8, 0.0));
        float furche = 1.0 - smoothstep(0.05, 0.22, z.y);
        float riss = smoothstep(0.82, 0.9, bkNoise(q * vec3(10.0, 22.0, 10.0))) * (1.0 - furche);
        float rippe = (1.0 - furche) * (0.88 + 0.24 * z.z);
        vec3 cr = c * mix(0.30, 1.15 * (0.9 + 0.2 * bkNoise(q * vec3(30.0, 8.0, 30.0))), rippe) * (1.0 - 0.45 * riss);
        c = mix(c * 0.78, cr, w1);
        float fl = bkFbm(q * vec3(3.0, 1.5, 3.0) + 3.0);
        float lich = smoothstep(0.80 - 0.2 * flechte, 0.83 - 0.2 * flechte, fl) * (1.0 - furche) * w2;
        c = mix(c, vec3(0.40, 0.46, 0.34) * rel, lich * 0.55);
        h = mix(0.5, (1.0 - furche) * (0.75 + 0.25 * z.x) * (1.0 - 0.4 * riss), w1);
    } else if (style == 3) {
        // schuppig (Fichte): dünne, runde Schuppen (3–6 cm), die sich überlappen: der untere Rand jeder Schuppe hebt
        // sich und ist heller, darunter ein schmaler Schatten [G]
        float w1 = bkFade(0.05, fp);
        vec3 x = q * vec3(20.0, 13.0, 20.0) + (vec3(bkNoise(q * 9.0), bkNoise(q * 9.0 + 5.0), bkNoise(q * 9.0 + 9.0)) - 0.5) * 0.7;
        vec3 z = bkCell(x);
        float rand = 1.0 - smoothstep(0.02, 0.10, z.y);
        float wolb = 1.0 - smoothstep(0.0, 0.65, z.x);                       // Mitte der Schuppe höher
        float ton = 0.84 + 0.32 * z.z;
        vec3 cs = c * ton * (0.82 + 0.3 * wolb) * mix(vec3(1.0), vec3(1.10, 0.95, 0.90), step(0.72, z.z));
        cs *= 1.0 - 0.32 * rand;
        c = mix(c * 0.92, cs, w1);
        float fl = bkFbm(q * vec3(3.5, 2.0, 3.5) + 9.0);
        float lich = smoothstep(0.82 - 0.2 * flechte, 0.85 - 0.2 * flechte, fl) * bkFade(0.05, fp);
        c = mix(c, vec3(0.46, 0.52, 0.42) * rel, lich * 0.6);
        h = mix(0.5, wolb * (1.0 - 0.5 * rand), w1);
    } else if (style == 4) {
        // Platten: große Borke in Tafeln (15–30 cm), tiefe dunkle Fugen
        float w1 = bkFade(0.22, fp);
        vec3 z = bkCell(q * vec3(4.5, 1.9, 4.5) + (vec3(bkNoise(q * 3.0), bkNoise(q * 3.0 + 5.0), bkNoise(q * 3.0 + 9.0)) - 0.5) * 0.9);
        float fuge = 1.0 - smoothstep(0.04, 0.14, z.y);
        vec3 cp = c * (0.85 + 0.3 * z.z) * (0.9 + 0.2 * bkNoise(q * 25.0));
        cp *= 1.0 - 0.7 * fuge;
        c = mix(c * 0.8, cp, w1);
        h = mix(0.5, 1.0 - fuge, w1);
    } else if (style == 5) {
        // Papier: helle Rinde, dunkle Korkporen quer, unten dunkle rissige Borke
        float w2 = bkFade(0.05, fp);
        float len = bkNoise(q * vec3(10.0, 70.0, 10.0));
        float pore = smoothstep(0.74, 0.82, len) * w2;
        c *= 1.0 - 0.75 * pore;
        float unten = 1.0 - smoothstep(0.6, 2.5, q.y + 0.6 * bkNoise(q * 2.0));
        float r = smoothstep(0.4, 0.6, bkFbm(q * vec3(8.0, 1.2, 8.0)));
        c = mix(c, vec3(0.06, 0.055, 0.05) * mix(0.6, 1.4, r) * rel * 0.43, unten * bkFade(0.1, fp));   // Birke: Bezug 0,70
        h = 0.5 - 0.3 * pore + 0.3 * unten * (r - 0.5);
    }
    return vec4(c, clamp(h, 0.0, 1.0));
}

// Moos (seit LituyaBay1958 W6 Runde 6) [G]: Polster am Stammfuß (dort läuft das Wasser am Stamm ab, die Luft ist feucht),
// auf der Oberseite waagerechter Äste und weiter hinauf auf der feuchten Seite (side: Richtung in der Welt, waagerecht;
// Länge 0 = keine). Fleckig mit weichem Rand, gewölbte Polster als Relief. moos 0..1 (Art × App), nW: Normale (Welt),
// fp: Fußabdruck (m). In der Ferne das Mittel ohne Muster. Rückgabe: Farbe, Höhe des Reliefs.
vec4 barkMoss(vec3 c, float h, vec3 q, vec3 nW, vec3 side, float moos, float fp) {
    if (moos <= 0.0) return vec4(c, h);
    float rel = min(dot(c, vec3(0.2126, 0.7152, 0.0722)) / 0.30, 2.5);
    float sl = length(side.xz), nl = length(nW.xz);
    float seite = sl > 1e-3 && nl > 1e-3 ? 0.5 + 0.5 * dot(nW.xz / nl, side.xz / sl) : 0.5;
    float w = bkFade(0.08, fp);
    float hoch = (0.4 + 2.2 * moos) * mix(0.5, 1.6, seite) * (1.0 + 0.4 * (bkNoise(q * vec3(1.3, 0.4, 1.3)) - 0.5) * w);
    float fuss = 1.0 - smoothstep(0.6 * hoch, hoch, q.y);
    float oben = smoothstep(0.35, 0.8, nW.y) * smoothstep(1.0, 3.0, q.y);   // Oberseite der Äste (nicht der Wurzelanlauf)
    float cover = moos * max(fuss, 0.8 * oben);
    if (cover <= 0.001) return vec4(c, h);
    float fleck = mix(0.5, bkFbm(q * vec3(4.0, 2.5, 4.0) + 13.0), w);
    float m = smoothstep(0.78 - 0.65 * cover, 0.86 - 0.65 * cover, fleck) * w + (1.0 - w) * clamp(cover * 1.2, 0.0, 1.0) * 0.8;
    vec3 mc = mix(vec3(0.060, 0.105, 0.028), vec3(0.125, 0.185, 0.045), bkNoise(q * 25.0)) * rel;
    float polster = 0.65 + 0.25 * bkNoise(q * 35.0) * bkFade(0.02, fp);
    return vec4(mix(c, mc, m), mix(h, polster, m));
}

// Normale aus dem Relief über die Ableitungen im Bild (gleichmäßiger Ablauf: je Dreieck nur Rinde)
vec3 barkNormal(vec3 n, vec3 wp, float h, float depth) {
    vec3 dpx = dFdx(wp), dpy = dFdy(wp);
    float dhx = dFdx(h) * depth, dhy = dFdy(h) * depth;
    vec3 r1 = cross(dpy, n), r2 = cross(n, dpx);
    float det = dot(dpx, r1);
    if (abs(det) < 1e-10) return n;
    vec3 g = sign(det) * (dhx * r1 + dhy * r2);
    return normalize(abs(det) * n - g);
}
// RINDE ENDE

float sunShadow(vec3 w, vec3 n) {
    if (uSunShadowP.x < 0.5) return 1.0;
    vec4 q = uSunShadowM * vec4(w + n * 0.12 + uSunDir * 0.6, 1.0);
    vec3 s = q.xyz / q.w * 0.5 + 0.5;
    if (any(lessThan(s.xy, vec2(0.002))) || any(greaterThan(s.xy, vec2(0.998))) || s.z > 1.0) return 1.0;
    // seit W6 Runde 5 (Light.sunShadowTaps = 1): ein Abruf, die Karte mischt die vier Nachbarn selbst (Laub hat viel Überdeckung)
    if (uSunShadowP.x > 1.5) return texture(uSunShadow, vec3(s.xy, s.z - uSunShadowP.y));
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
    float bkFp = length(fwidth(vLocal));   // Muster nur nah (unter 8 cm je Bildpunkt), Moos überall, wie forest.frag
    if (uBarkPat.x > 0.5 && vLeaf < 0.5 && vTex < 0.5) {
        if (!gl_FrontFacing) n = -n;
        int style = int(uBarkPat.y + 0.5);
        float hb = 0.5;
        if (bkFp < 0.08) {
            vec4 b = barkPattern(style, vLocal, albedo, uBarkPat.z, max(bkFp, 1e-4));
            float nahW = 1.0 - smoothstep(0.05, 0.08, bkFp);
            albedo = mix(albedo, b.rgb, nahW);
            hb = mix(0.5, b.a, nahW);
        }
        vec4 mo = barkMoss(albedo, hb, vLocal, n, uMossSide, uBarkPat.w, max(bkFp, 1e-4));
        albedo = mo.rgb;
        float tiefe = style == 1 ? 0.004 : style == 5 ? 0.003 : style == 3 ? 0.012 : 0.03;
        if (bkFp < 0.08) n = barkNormal(n, vWorld, mo.a, tiefe);
    }
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
