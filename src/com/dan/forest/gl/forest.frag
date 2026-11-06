// forest.frag — Licht auf Stamm, Ästen und Laub: Laub beidseitig und gegen die Sonne durchscheinend, Rinde nur von
// vorn; Sonne mit der Sonnenschattenkarte des Benutzers (3×3-PCF) und den Wolkenschatten von oben; Mond, Himmel von
// oben (unten in der Krone weniger), Dunst nach Entfernung; nasse Rinde dunkler. Ausgabe: HDR, linear.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
in float vLeafy;
in float vH;
in float vRime;
in vec3 vLocal;
out vec4 frag;
uniform vec3 uCam;
uniform vec3 uSunDir, uSunColor, uSkyColor, uFogColor, uMoonDir, uMoonColor;
uniform float uFogDist, uSkyWeight, uWet;
uniform sampler2D uShadow;
uniform vec4 uShadowRect;
uniform sampler2D uSky;     // Rundumbild des Himmels für den Dunst (uSkyOn), sonst uFogColor
uniform int uSkyOn;
uniform sampler2DShadow uSunShadow;
uniform mat4 uSunShadowM;
uniform vec2 uSunShadowP;   // an (1) / aus (0), Versatz
uniform vec4 uRime;          // Eis: Dicke (m), Art, woher x, z (wie forest.vert)
uniform vec2 uTone;         // Tonemapping an (1) / aus (0), Belichtung (nur Demos)
uniform int uBark;          // seit W6 Runde 5: Rinde mit Muster (ForestGpu.barkDetail)
uniform int uShoot;         // seit W6 Runde 5: Nadeln als Triebe (Volumen) statt als Blattebenen (ForestGpu.shootLight)
uniform vec3 uMossSide;     // seit W6 Runde 6: feuchte Seite der Stämme (Welt, waagerecht; 0 = keine), Light.mossSide
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
    vec3 n = normalize(vNormal);
    vec3 V = normalize(uCam - vWorld);
    bool leaf = vLeafy > 0.25;
    float glow = vLeafy > 0.75 ? 1.0 : 0.12;   // Kronenhülle: kaum Durchscheinen
    if (leaf) { if (dot(n, V) < 0.0) n = normalize(n - 1.3 * dot(n, V) * V); }
    else if (!gl_FrontFacing) n = -n;
    vec3 alb = vColor * (leaf ? 1.0 : 1.0 - 0.35 * uWet);
    // seit W6 Runde 5: Muster der Rinde (vLeafy < 0: −(Stil + Flechten/10 + Moos/100), je in Neunteln, ForestGl.barkCode);
    // je Dreieck nur Holz oder Laub. Muster nur nah (unter 8 cm je Bildpunkt): weiter weg trägt kein Bildpunkt mehr
    // Schuppen oder Korkporen, und das Muster kostet (Zellrauschen); dazwischen weich. Moos (Runde 6) in jeder Entfernung.
    float bkFp = length(fwidth(vLocal));
    if (uBark == 1 && vLeafy < -0.5) {
        float code = -vLeafy;
        int style = int(floor(code + 0.004));
        float fr = code - float(style);   // Stil: floor(code + 0,004), Bruchteil bis 0,991
        float l9 = floor(fr * 10.0 + 0.002), m9 = floor(fr * 100.0 - l9 * 10.0 + 0.02);
        float flechte = clamp(l9 / 9.0, 0.0, 1.0) * (1.0 - 0.6 * uWet), moos = clamp(m9 / 9.0, 0.0, 1.0);
        float hb = 0.5;
        if (bkFp < 0.08) {
            vec4 b = barkPattern(style, vLocal, alb, flechte, max(bkFp, 1e-4));
            float nahW = 1.0 - smoothstep(0.05, 0.08, bkFp);
            alb = mix(alb, b.rgb, nahW);
            hb = mix(0.5, b.a, nahW);
        }
        vec4 mo = barkMoss(alb, hb, vLocal, n, uMossSide, moos, max(bkFp, 1e-4));
        alb = mo.rgb;
        float tiefe = style == 1 ? 0.004 : style == 5 ? 0.003 : style == 3 ? 0.012 : 0.03;
        if (bkFp < 0.08) n = barkNormal(n, vWorld, mo.a, tiefe);
    }
    // Eis (wie rime.glsl, ohne es einzubinden): Raueis auf der Luvseite, mit der Dicke um die Kanten; in Federn
    // aufgebrochen; Raureif überall, oben mehr; Klareis dunkel und glänzend
    float rimeC = 0.0;
    if (vRime > 0.0) {
        float d = smoothstep(0.0005, 0.02, vRime);
        vec3 von = vec3(uRime.z, 0.0, uRime.w);
        if (uRime.y > 1.5 && uRime.y < 2.5) rimeC = d * (0.55 + 0.45 * max(n.y, 0.0));
        else if (uRime.y > 2.5) rimeC = d;
        else {
            float um = smoothstep(0.04, 0.25, vRime);
            rimeC = d * smoothstep(-0.2 - 0.6 * um, 0.6 - 0.45 * um, dot(n, von));
            float h = fract(sin(dot(floor(vWorld.xz * 23.0 + vWorld.y * 17.0), vec2(127.1, 311.7))) * 43758.5453);
            rimeC *= smoothstep(h * 0.5, h * 0.5 + 0.4, rimeC) * (leaf ? 1.0 : 0.85);
        }
        alb = uRime.y > 2.5 ? mix(alb, alb * 0.7 + vec3(0.04, 0.05, 0.06), rimeC) : mix(alb, vec3(0.80, 0.84, 0.88), rimeC);
    }
    float cloud = uShadowRect.w > 0.0 ? textureLod(uShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r : 1.0;
    float sh = sunShadow(vWorld, n) * cloud;
    float up = smoothstep(-0.02, 0.05, uSunDir.y);
    float nl = dot(n, uSunDir);
    float diff = leaf ? max(nl, 0.0) * 0.8 + 0.2 * max(-nl, 0.0) : max(nl, 0.0);
    float back = leaf ? pow(max(dot(-V, uSunDir), 0.0), 3.0) * max(-nl, 0.0) * 0.6 + pow(max(dot(-V, uSunDir), 0.0), 8.0) * 0.25 : 0.0;
    // seit W6 Runde 5: ein Nadeltrieb (Laubwert 0,95) ist ein kleines Volumen aus vielen Nadeln. Seine Helligkeit hängt
    // kaum davon ab, wie die Ebene der Bürste liegt, wohl aber vom Winkel zwischen Sonne und Blick: von der Sonnenseite
    // gesehen sind fast nur besonnte Nadeln sichtbar (Hot Spot), im Gegenlicht fast nur beschattete. Durch einen Trieb
    // scheint wenig Licht (Nadeln dick, sie überdecken sich); Ebenen-Lambert und volles Durchscheinen machten aus den
    // gekreuzten Bürsten der Stufen 1 und 2 helle Flocken.
    if (uShoot == 1 && vLeafy > 0.9 && vLeafy < 0.97) {
        float hot = 0.5 + 0.5 * dot(V, uSunDir);
        diff = mix(diff, 0.15 + 0.55 * hot * hot, 0.75);
        glow = 0.25;
    }
    vec3 sun = uSunColor * up * sh;
    vec3 moon = uMoonColor * smoothstep(-0.02, 0.05, uMoonDir.y) * cloud * max(dot(n, uMoonDir) * 0.7 + 0.3, 0.0);
    float skyAmt = uSkyWeight * (0.45 + 0.35 * vH + 0.2 * n.y);
    vec3 col = alb * (sun * diff + moon + uSkyColor * skyAmt) + alb * vec3(0.75, 1.0, 0.4) * sun * back * glow * (1.0 - rimeC);
    // nasses Laub glänzt matt im Gegenlicht
    if (uWet > 0.0) col += uSunColor * up * sh * pow(max(dot(reflect(-uSunDir, n), V), 0.0), 24.0) * 0.06 * uWet;
    float d = length(uCam - vWorld);
    vec3 fogC = uFogColor;
    if (uSkyOn == 1) {
        vec3 hd = normalize(vec3(vWorld.x - uCam.x, 0.0, vWorld.z - uCam.z) + vec3(0.0, 1e-4, 0.0));
        fogC = textureLod(uSky, vec2(atan(hd.x, -hd.z) / 6.28318530718 + 0.5, 0.5), 0.0).rgb;
    }
    col = mix(col, fogC, 1.0 - exp(-d / uFogDist));
    if (uTone.x > 0.5) {
        vec3 x = col * uTone.y;
        col = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
    }
    frag = vec4(col, 1.0);
}
