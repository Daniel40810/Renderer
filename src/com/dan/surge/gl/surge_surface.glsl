// surge_surface.glsl — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W3b–W5): die Oberfläche einer Riesenwelle
// [G] für den Bildpunktformer eines Wirts, der die Welle als Höhenfeld zeichnet (Eckenformer mit surge_front.glsl).
//   · Körper fjordgrün mit Gletschermilch, nach der Front Schlamm und Gesteinsmehl;
//   · Kräusel in vier Lagen in Windrichtung, ruhig 0,32, aufgewühlt 1; Spiegel des Himmels, steile Wände spiegeln die
//     dunklen Hänge statt des Himmels an der Kimm; der Spiegel der Szene kommt vom Wirt (surgeMirror, SURGE_MIRROR);
//   · W4 „Die Wand“: Durchlicht im dünnen Kamm (türkis, im Schlamm braun, Adern), Schatten unter der Lippe, Weißwasser an
//     der Front, Walze am Fuß der Wand mit Fahnen aus Schaum, Kamm, der nicht überall zugleich bricht, durchlüfteter
//     Sturz hoher steiler Wände, Licht im Schaum mit Rinnen, Stämme in der Walze;
//   · Schaumstraßen und Teppich mit Wirbeln hinter der Front (W4 E10), Saum am Ufer, Aufruhr im Nahfeld der Quelle,
//     Treibgut in Feldern, die mit der Zeit dichter werden;
//   · W5 „524 m“: die Zunge am Hang: Vorderkante in Fingern und Loben (bis rund 1 s verschoben), durchlüftetes Weißwasser
//     in Fäden und Ballen, braunes Wasser voll Erde dazwischen, Saum an der Kante, Stämme längs der Strömung; beim
//     Rückfall dünnes, glänzendes Wasser in Rinnen;
//   · W4b: unter der Lippe als Netz (uSgLipMesh) die dunkle Rückwand der Röhre.
// Der Lichtvertrag (light.glsl oder der Lichtblock des Wirts) und surge_flood.glsl müssen vorher eingebunden sein.
// Weitere Uniforms: uSgWind (Richtung, in die der Wind weht, x, z), uSgLipMesh (1: Lippe als Netz), uSgEarly (1: ganz
// ausgeblendete Lagen nicht rechnen, gleiches Ergebnis). Eingang: SurgeSurf aus dem Eckenformer.
#include "/com/dan/surge/gl/surge_noise.glsl"
#include "/com/dan/surge/gl/surge_flood.glsl"
uniform vec2 uSgWind;
uniform int uSgLipMesh;
uniform int uSgEarly;

struct SurgeSurf {
    vec3 w;        // Punkt der Fläche (Szene, mit Erdkrümmung)
    vec3 n;        // Normale aus dem Eckenformer
    float wet;     // nass 0..1 (unter 0,35 verworfen)
    float h;       // Höhe der Fläche (m)
    float rise;    // Steigen (m je Bild der Rechnung): Brandung an der Front
    float depth;   // Wasser über dem Grund (m), 99 ohne Grund
    float source;  // Aufruhr im Nahfeld einer Quelle 0..1 (0 ohne)
    float lip;     // Anteil der Lippe (sgLipShare)
    vec2 run;      // Laufrichtung der Front (x, z), 0 ohne
    float since;   // s seit der Ankunft der Front (−1e9 ohne)
    float stand;   // höchster Stand hier (m)
};

#ifdef SURGE_MIRROR
vec3 surgeMirror(vec3 refl, vec3 w, vec3 n, float dist, float stir, float above, float steep);
#endif

float sgNF(vec2 q, float p, vec2 o, float px) { return sgNoiseF(q, p, o, px, uSgEarly); }
vec2 sgSL(vec2 q, float p, vec2 o, float px) { return sgSlope(q, p, o, px, uSgEarly); }

// Runde 4: Muster der Zunge in einer festen Richtung D. Acht Richtungen zu 45°, weich gemischt. Vorher lief das Muster
// längs der Laufrichtung je Fragment; mit Lagen von einigen Kilometern verschob schon ein Grad Drehung es um zig Meter,
// an jeder Kante des Netzes anders (dieselbe Falle wie in W4b): daraus die hellen und braunen Vielecke in der Zunge
struct SgTongue { float faden; float ball; vec2 gb; float st; float stHell; float stFern; };
SgTongue sgTongue(vec2 D, vec2 xz, float seitL, float t, float px) {
    SgTongue m;
    vec2 Dq = vec2(-D.y, D.x);
    float la = dot(xz, Dq), qu = dot(xz, D);
    vec2 qL = vec2(la, qu - t * (seitL < 6.0 ? 22.0 : -9.0));
    // (Runde 2: die Lage zu 17 m gab aus der Ferne große Flecken wie Schnee auf dem Hang; jetzt schwach)
    m.faden = sgNF(qL * vec2(1.0, 0.18), 6.0, vec2(0.0), px) * 0.6 + sgNF(qL * vec2(1.0, 0.25) + 5.3, 2.4, vec2(0.0), px) * 0.3
            + sgNF(qL * vec2(1.0, 0.12) + 9.1, 17.0, vec2(0.0), px) * 0.1;
    // Runde 3: Schaum als Ballen [G], zwei Lagen gewölbter Ballen (4 und 10 m), die mit dem Wasser wandern
    vec2 qb = vec2(la, qu - t * (seitL < 6.0 ? 13.0 : -6.0));
    #define BALL(q) ((1.0 - abs(2.0 * sgNF(q, 4.0, vec2(0.0), px) - 1.0)) * 0.55 + (1.0 - abs(2.0 * sgNF((q) + 11.0, 10.0, vec2(0.0), px) - 1.0)) * 0.45)
    m.ball = BALL(qb);
    vec2 gb = vec2(0.0);   // Gefälle nur, solange ein Ballen mehr als rund drei Bildpunkte deckt (Tempo)
    if (px < 1.3) gb = vec2(BALL(qb + vec2(0.8, 0.0)) - m.ball, BALL(qb + vec2(0.0, 0.8)) - m.ball) / 0.8 * 3.0 * (1.0 - smoothstep(0.4, 1.3, px));
    #undef BALL
    m.gb = Dq * gb.x + D * gb.y;
    // Stämme in der Zunge, längs der Strömung; nur nah als Striche, fern als dunklere Schlieren
    vec2 zs = floor(vec2(qL.x / 7.0, qL.y / 13.0)), fs = vec2(qL.x / 7.0, qL.y / 13.0) - zs;
    float wink = (sgHash(zs + 2.9) - 0.5) * 1.2;
    vec2 rs = vec2(cos(wink) * (fs.x - 0.5) - sin(wink) * (fs.y - 0.5), sin(wink) * (fs.x - 0.5) + cos(wink) * (fs.y - 0.5));
    m.st = step(0.80, sgHash(zs + 5.1)) * (1.0 - smoothstep(0.03, 0.06, abs(rs.x))) * (1.0 - smoothstep(0.30, 0.38, abs(rs.y)));
    m.stHell = step(0.5, sgHash(zs + 8.8));
    m.stFern = smoothstep(0.55, 0.8, sgNF(qL * vec2(1.0, 0.12) + 3.3, 9.0, vec2(0.0), px));
    return m;
}
float sgFinger(vec2 D, vec2 xz, float tE) {
    float la = dot(xz, vec2(-D.y, D.x)), qu = dot(xz, D);
    return sgNoise(vec2(la / 31.0, qu / 90.0 + tE * 0.05)) * 0.55 + sgNoise(vec2(la / 11.0 + 3.1, qu / 40.0)) * 0.30
         + sgNoise(vec2(la / 4.3 + 7.7, qu / 15.0)) * 0.15;
}


vec4 surgeShade(SurgeSurf s) {
    if (s.wet < 0.35) discard;
    // W3b (K11): was nach dem Ablaufen als dünner Film am Hang liegt (unter rund 1 m), ist kein Wasser mehr im Bild,
    // sondern nasser Grund. Solange die Front aufläuft (der Stand steigt), bleibt der Film als weißes Wasser.
    if (s.depth < 1.2 && s.rise < 0.15 && s.source <= 0.0) discard;
    vec3 d = s.w - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    vec2 q = s.w.xz;
    float px = max(length(fwidth(q)), 1e-4);   // Bildpunkt auf der Fläche (m)
    vec3 N = normalize(s.n);
    // W4b: auf steilen Wänden die Normale des Dreiecks selbst: die Wand ist eine Reihe langer schmaler Dreiecke, die
    // gemittelte Normale gab dort Zickzack-Bänder (Probe Röhre)
    // (zurückgenommen: die Normale je Dreieck machte jedes Dreieck zur eigenen Facette, Sägezahn über die Wand)
    float steil = 1.0 - N.y;
    float hoch = s.h - uLevel;
    // ---- wie lange die Front schon da ist (Flutkarte), Aufruhr
    float seit = -1.0, stand = uLevel;
    if (uSgFloodOn == 1) {
        vec2 fu = vec2((s.w.x - uSgFloodRect.x) / uSgFloodRect.z, (uSgFloodRect.y - s.w.z) / uSgFloodRect.w);
        if (fu.x > 0.0 && fu.y > 0.0 && fu.x < 1.0 && fu.y < 1.0) {
            vec2 am = texture(uSgFlood, fu).rg;
            if (am.x < 1e5) { seit = uSgEvent - am.x; stand = am.y; }
        }
    }
    // ---- Werkbuch Welle W5 (E11) „524 m“: die Zunge am Hang [G]. Wo die Welle über Land aufläuft (Grund über dem Pegel),
    // läuft ihre Vorderkante nicht als glatte Linie, sondern in Fingern und Loben den Hang hinauf (Swash): die Ankunft
    // wird längs der Front um bis zu rund eine Sekunde verschoben, und wo das Wasser „noch nicht da“ ist, bleibt der Hang
    // (mit seinen Bäumen) sichtbar.
    float grundW = s.depth < 98.0 ? s.h - s.depth : uLevel - 50.0;
    float landW = smoothstep(uLevel + 3.0, uLevel + 25.0, grundW);
    bool hatL0 = dot(s.run, s.run) > 0.25 && s.since > -1e8;
    vec2 TL = hatL0 ? normalize(s.run) : vec2(1.0, 0.0), TLq = vec2(-TL.y, TL.x);
    // Runde 4: zwei der acht festen Richtungen, nach dem Winkel der Laufrichtung gemischt (siehe zungeMuster)
    float aL = atan(TL.y, TL.x) / 0.7853982, kL = floor(aL), wL = smoothstep(0.32, 0.68, aL - kL);
    vec2 DL0 = vec2(cos(kL * 0.7853982), sin(kL * 0.7853982)), DL1 = vec2(cos((kL + 1.0) * 0.7853982), sin((kL + 1.0) * 0.7853982));
    float fingerN = wL <= 0.0 ? sgFinger(DL0, s.w.xz, uSgEvent) : wL >= 1.0 ? sgFinger(DL1, s.w.xz, uSgEvent)
                  : 0.5 + (mix(sgFinger(DL0, s.w.xz, uSgEvent), sgFinger(DL1, s.w.xz, uSgEvent), wL) - 0.5) * inversesqrt(wL * wL + (1.0 - wL) * (1.0 - wL));
    float vorkante = (fingerN - 0.45) * 1.6;   // s, wie weit die Kante hier vor- oder zurückliegt
    if (landW > 0.05 && stand > uLevel + 0.5 && seit < vorkante * landW) discard;
    float nach = seit >= 0.0 ? 1.0 : 0.0;
    float frisch = nach * (1.0 - smoothstep(15.0, 300.0, seit));          // die ersten Minuten nach der Front
    float aufruhr = clamp(smoothstep(0.5, 4.0, s.rise) + smoothstep(0.05, 0.28, steil) + s.source + frisch * 0.7, 0.0, 1.0);
    // ---- Kräusel: vier Lagen in Windrichtung gestreckt [G]; ruhig 0,2, aufgewühlt bis 1
    vec2 wd = length(uSgWind) > 1e-3 ? normalize(uSgWind) : vec2(0.8, 0.6);
    vec2 wq = vec2(-wd.y, wd.x);
    vec2 qr = vec2(dot(q, wd), dot(q, wq));   // längs und quer zum Wind
    vec2 qs = vec2(qr.x * 0.55, qr.y);   // quer zum Wind länger
    float t = uTime;
    // W4b: Muster aus dem Grundriss (Kräusel, Straßen, Treibgut) nicht auf steilen Wänden, dort zogen sie sich zu
    // waagerechten Zickzack-Bändern zusammen (Probe Röhre)
    float flach = 1.0 - smoothstep(0.30, 0.60, 1.0 - abs(N.y));
    // Runde 10: die Kräusel nur, wo sie wirken (flach) oder die Zunge sie braucht; sonst ist ihr Beitrag 0
    vec2 g = vec2(0.0);
    if (uSgEarly == 0 || flach > 0.0 || landW > 0.0) {
        g = sgSL(qs, 23.0, vec2(-t * 0.035, 0.0), px) * 0.55 + sgSL(qs + 7.0, 9.0, vec2(-t * 0.07, t * 0.01), px) * 0.45
          + sgSL(qs + 3.0, 3.7, vec2(-t * 0.16, 0.0), px) * 0.35 + sgSL(qs + 11.0, 1.4, vec2(-t * 0.33, t * 0.05), px) * 0.25;
        g = (wd * (g.x * 0.55) + wq * g.y) * 0.35;
    }
    float kr = mix(0.32, 1.0, aufruhr) * (1.0 - 0.5 * smoothstep(1500.0, 9000.0, dist)) * flach;
    vec3 n = normalize(N + vec3(g.x, 0.0, g.y) * kr);
    vec3 v = -d;
    // ---- Körper: fjordgrün mit Gletschermilch, nach der Front Schlamm und Gesteinsmehl [G]
    float schlamm = clamp(nach * (0.35 + 0.65 * (1.0 - smoothstep(120.0, 1800.0, seit))) + smoothstep(8.0, 40.0, hoch) * 0.6 + s.source * 0.8, 0.0, 1.0);
    vec3 eDown = lightIrradiance() + lightSun(s.w) * max(uSun.y, 0.0);
    vec3 klar = vec3(0.020, 0.050, 0.046), milch = vec3(0.060, 0.105, 0.092), lehm = vec3(0.085, 0.070, 0.050);
    vec3 body = mix(mix(klar, milch, 0.55), lehm, schlamm) * eDown * 0.32;
    vec3 col;
    float fres = 0.02;
    if (dot(v, N) >= 0.0) {
        fres = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        vec3 r = reflect(d, n);
        vec3 refl = lightSkyDir(vec3(r.x, max(r.y, 0.01), r.z));
        // W4: eine steile Wand sieht im Spiegel flach hinaus, und dort stehen in der Bucht die Hänge (dunkel, im Dunst),
        // nicht der helle Himmel am Horizont; der Spiegel der Szene (surgeMirror) gilt nur nahe am Pegel [G]
        float wandS = smoothstep(0.10, 0.35, 1.0 - N.y);
        if (wandS > 0.0) {
            vec3 hang = lightHaze(normalize(vec3(r.x, 0.0, r.z) + vec3(1e-4))) * 0.20 * (0.6 + 0.4 * smoothstep(-0.2, 0.3, r.y));
            refl = mix(refl, hang, wandS * (1.0 - smoothstep(0.10, 0.40, r.y)));
        }
        // Spiegel der Szene vom Wirt (nur nahe am Pegel und wo die Fläche flach liegt; Lituya: das Spiegelbild der Berge)
#ifdef SURGE_MIRROR
        refl = surgeMirror(refl, s.w, n, dist, aufruhr, hoch, steil);
#endif
        col = mix(body, refl, fres);
        vec3 hv = normalize(uSun + v);
        float shin = mix(600.0, 80.0, smoothstep(100.0, 8000.0, dist));
        col += lightSun(s.w) * pow(max(dot(n, hv), 0.0), shin) * (shin / 600.0) * 2.0;
    } else if (uCam.y > uLevel + 0.5 && hoch > 1.0) {
        // W4b: die Unterseite der Lippe, von vorn in die Röhre gesehen: Licht kommt durch das dünne Wasser der Lippe
        // (türkis, wo klar, braun im Schlamm), innen dunkel, zur Spitze hin heller [G]
        vec3 durch = lightSkyDir(vec3(0.0, 1.0, 0.0)) * mix(vec3(0.16, 0.48, 0.42), vec3(0.36, 0.28, 0.16), clamp(schlamm * 0.8, 0.0, 1.0));
        float spitze = clamp(s.lip * 1.6, 0.0, 1.0);
        float hr = clamp((s.h - uLevel) / max(s.stand - uLevel, 1.0), 0.0, 1.0);
        // oben unter der Lippe Licht durch das dünne Wasser, nach unten in die Röhre dunkel, Schlieren in der Fallrichtung
        float schl = sgNoise((vec2(dot(s.w.xz, vec2(-s.run.y, s.run.x)) * 0.25, s.w.y * 0.08 - uTime * 0.6)));
        col = mix(body * 0.55, durch * (0.5 + 0.35 * schl), smoothstep(0.35, 0.95, hr) * (0.5 + 0.5 * spitze));
        fres = 0.0;
    } else {
        // von unten: im Snellschen Fenster (48,6°) der Himmel gebrochen, sonst der Körper gespiegelt
        float c = dot(-v, n);
        float fenster = smoothstep(0.62, 0.70, c);
        col = mix(body * 1.4, lightSkyDir(normalize(vec3(-v.x, 1.0, -v.z))) * 0.5, fenster);   // W3c: Körper heller (Unterwasserblick)
    }
    // ---- Werkbuch Welle W4 „Die Wand“ (E7–E9, E12) [G]: wo die Front gerade durchläuft, auf der Wand eigene
    // Koordinaten (längs der Front und in der Höhe), damit das Rauschen keine senkrechten Streifen aus dem Grundriss zieht
    bool hatLauf = dot(s.run, s.run) > 0.25 && s.since > -1e8;
    vec2 T = hatLauf ? normalize(s.run) : wd, Tq = vec2(-T.y, T.x);
    float hs = max((hatLauf ? s.stand : stand) - uLevel, 1.0);
    float hrel = clamp(hoch / hs, 0.0, 1.2);
    float wandZeit = hatLauf ? smoothstep(-1.5, 0.3, s.since) * (1.0 - smoothstep(4.0, 16.0, s.since)) : 0.0;
    float wandSteil = smoothstep(0.06, 0.30, steil);
    float wand = wandZeit * smoothstep(4.0, 22.0, hoch) * max(wandSteil, s.lip);
    vec2 qw = vec2(dot(q, Tq), s.w.y);
    float pxw = max(length(fwidth(qw)), 1e-4);
    float vorn = smoothstep(-0.1, 0.5, dot(normalize(v.xz + vec2(1e-5)), T));   // das Auge steht vor der Front
    // E8 Durchlicht: der dünne Kamm leuchtet von hinten, türkis wo klar, braun wo Schlamm
    float duenn = clamp(s.lip * 1.4, 0.0, 1.0) * smoothstep(0.80, 0.98, hrel) * vorn * wandSteil;
    if (duenn > 0.0) {
        vec3 hinten = lightSkyDir(normalize(vec3(d.x, max(d.y, 0.03) + 0.05, d.z)));
        vec3 farbe = mix(vec3(0.20, 0.72, 0.62), vec3(0.55, 0.42, 0.22), clamp(schlamm * 0.8, 0.0, 1.0));
        float adern = sgNF(qw + vec2(0.0, t * 3.0), 7.0, vec2(0.0), pxw) * 0.6 + sgNF(qw * vec2(1.0, 0.4), 2.0, vec2(t * 0.6, 0.0), pxw) * 0.4;
        col = mix(col, hinten * farbe * (0.45 + 0.35 * adern) + body * 0.5, duenn * 0.6);
    }
    // unter der Lippe: Schatten in der Röhre
    col *= 1.0 - 0.5 * clamp(s.lip * 1.5, 0.0, 1.0) * smoothstep(0.25, 0.5, hrel) * (1.0 - smoothstep(0.7, 0.9, hrel)) * vorn;
    // ---- Schaum [G]
    vec3 weissL = lightSun(s.w) * max(dot(n, uSun), 0.0) * 0.9 + lightSky(n) * 1.05;
    // 1) Weißwasser an der Front und an steilen Flanken, ausgefranst (auf der Wand in Wandkoordinaten)
    vec2 qf = mix(q, qw * vec2(1.0, 1.6), wandSteil);
    float pf = mix(px, pxw, wandSteil);
    // Runde 10: die Lagen des Schaums nur rechnen, wo ihr Gewicht nicht 0 ist (gleiches Ergebnis; Schalter uSgEarly)
    float frontB = clamp(smoothstep(0.08, 0.35, steil) + smoothstep(0.8, 4.0, s.rise), 0.0, 1.0);
    float roh = 0.5;
    if (uSgEarly == 0 || frontB > 0.0) roh = sgNF(qf, 6.0, vec2(t * 0.35, -t * 0.5 * wandSteil), pf) * 0.55 + sgNF(qf, 1.8, vec2(0.0, t * 0.6), pf) * 0.45;
    float front = frontB * smoothstep(0.30, 0.70, roh + 0.25);
    float sturz = smoothstep(0.22, 0.45, steil) * smoothstep(18.0, 55.0, hoch);
    bool wandAn = uSgEarly == 0 || wand > 0.0 || sturz > 0.0;
    // E9 Walze am Fuß der Wand: dichtes Weißwasser, das an der Wand aufsteigt; Kammgischt oben, die nach hinten weht
    float walzeN = 0.5, gross = 0.5;
    if (wandAn) walzeN = sgNF(qw * vec2(1.0, 0.7) + vec2(0.0, -t * 4.0), 9.0, vec2(0.0), pxw) * 0.55 + sgNF(qw + vec2(t * 1.5, -t * 7.0), 3.0, vec2(0.0), pxw) * 0.45;
    // große Formen (Fahnen aus Schaum, die über die Wand herabfallen; Lücken, durch die das dunkle Wasser sieht): längs
    // der Front 25–70 m, in der Höhe gestreckt; sie bleiben, wenn das feine Rauschen in der Ferne zum Mittel geht
    if (wandAn) gross = sgNF(vec2(qw.x, qw.y * 0.22 + t * 1.2), 38.0, vec2(0.0), pxw) * 0.65 + sgNF(vec2(qw.x, qw.y * 0.4 + t * 2.0) + 17.0, 13.0, vec2(0.0), pxw) * 0.35;
    float fahnen = smoothstep(0.42, 0.66, gross);
    float walze = wand * (1.0 - smoothstep(0.35, 0.62, hrel + (walzeN - 0.5) * 0.3)) * smoothstep(0.25, 0.55, walzeN + 0.15);
    walze = max(walze, wand * fahnen * (1.0 - smoothstep(0.55, 0.9, hrel)) * 0.85);
    float kamm = 0.0;
    if (uSgEarly == 0 || (wandZeit > 0.0 && hoch > 8.0)) {
        float kammN = sgNF(vec2(dot(q, Tq), dot(q, T)) * vec2(1.0, 0.35) + vec2(0.0, t * 4.0), 5.0, vec2(0.0), px);
        float kammG = sgNF(vec2(dot(q, Tq), t * 0.8), 24.0, vec2(0.0), px);   // der Kamm bricht nicht überall zugleich
        float kammB = smoothstep(mix(0.80, 0.62, kammG), 0.93, hrel);
        kamm = wandZeit * kammB * smoothstep(8.0, 25.0, hoch) * smoothstep(0.30, 0.62, kammN * 0.5 + kammG * 0.5 + 0.35 * clamp(s.lip * 2.0, 0.0, 1.0));
    }
    // zwischen Kamm und Walze: die Wand selbst, glasig und dunkel, mit wenig Schaum
    front = max(front * (1.0 - 0.75 * wand), max(walze, kamm));
    // Sturz: steiles Wasser hoch über dem Pegel (die Wände im Gilbert Inlet, 50–150 m) ist ganz durchlüftet, Weißwasser
    // in Kaskaden mit Lücken; glasig bleibt nur die niedrigere Wand weiter draußen (Probe 4: dunkle Wände wie Fels)
    front = max(front, sturz * smoothstep(0.34, 0.58, gross + 0.2 * walzeN));
    // Licht im Schaum: Kaskaden heller, wo sie vorstehen, Rinnen dazwischen im Schatten (in der Höhe gestreckt), damit
    // die Wand aus der Ferne nicht als glatte Schneewand steht
    float rinne = wandAn ? sgNF(vec2(qw.x * 1.0, qw.y * 0.12 + t * 1.6) + 5.0, 9.0, vec2(0.0), pxw) : 0.5;
    float hell = mix(0.74, 1.12, smoothstep(0.25, 0.75, gross * 0.6 + rinne * 0.4));
    // 2) Schaumstraßen und Flecken hinter der Front: in Laufrichtung gestreckt (die Strömung zieht sie mit, W4 E10),
    //    sonst nach dem Wind; dicht gleich hinter der Front, klingen in rund 10 min ab
    vec2 qz = hatLauf ? vec2(dot(q, T) * 0.18, dot(q, Tq)) : vec2(qr.x * 0.18, qr.y);
    float str = 0.0;
    if (uSgEarly == 0 || (nach > 0.0 && seit < 900.0)) str = sgNF(qz, 14.0, vec2(-t * 0.02, 0.0), px) * 0.6 + sgNF(qz + 5.0, 4.5, vec2(-t * 0.05, 0.0), px) * 0.4;
    float strasse = nach * (1.0 - smoothstep(60.0, 900.0, seit)) * smoothstep(mix(0.66, 0.50, 1.0 - smoothstep(4.0, 60.0, seit)), 0.80, str) * 0.6;
    // W4 E10: gleich hinter der Front ein Teppich aus Schaum mit Wirbeln und Lücken
    float tepZeit = hatLauf ? smoothstep(2.0, 6.0, s.since) * (1.0 - smoothstep(25.0, 140.0, s.since)) : 0.0;
    float wirbel = 0.0;
    if (uSgEarly == 0 || tepZeit > 0.0) wirbel = sgNF(q + vec2(31.0, 7.0) + T * t * 1.5, 26.0, vec2(0.0), px) * 0.6 + sgNF(q * 1.7 + 3.0, 9.0, vec2(t * 0.1, 0.0), px) * 0.4;
    float teppich = tepZeit * smoothstep(0.38, 0.62, wirbel);
    strasse = max(strasse, teppich * 0.85) * flach;
    // 3) Saum am Ufer: dünnes Wasser über dem Grund
    float saum = 0.0;
    if (uSgEarly == 0 || s.depth < 3.5) saum = (1.0 - smoothstep(0.6, 3.5, s.depth)) * smoothstep(0.35, 0.7, sgNF(q + 13.0, 2.5, vec2(t * 0.2, 0.0), px) + 0.25);
    // 4) Aufruhr im Nahfeld der Quelle: Weißwasser und Schlamm in Fetzen
    float quelle = 0.0;
    if (uSgEarly == 0 || s.source > 0.0) quelle = s.source * smoothstep(0.52, 0.80, sgNF(q + 31.0, 24.0, vec2(t * 0.05, -t * 0.04), px) * 0.45 + sgNF(q + 5.0, 8.0, vec2(t * 0.25, -t * 0.18), px) * 0.35 + sgNF(q, 2.6, vec2(-t * 0.4, 0.0), px) * 0.2);
    float schaum = clamp(max(max(front * 0.9, strasse), max(saum * 0.8, quelle * 0.9)), 0.0, 1.0);
    vec3 schaumF = mix(vec3(0.82, 0.83, 0.82), vec3(0.55, 0.49, 0.40), clamp(schlamm * 0.55 + quelle * 0.3, 0.0, 1.0));
    // Weißwasser ist durchlüftet und streut das Licht des ganzen Himmels darüber, nicht wie eine matte senkrechte Fläche
    // (Probe 9: die Wand war voll Schaum und doch so dunkel wie die Hänge im Schatten)
    float wandW = clamp(max(wand, sturz), 0.0, 1.0);
    vec3 weissW = mix(weissL, lightSun(s.w) * max(dot(n, uSun), 0.0) * 0.9 + lightSky(normalize(n + vec3(0.0, 1.2, 0.0))) * 1.15, wandW);
    col = mix(col, schaumF * weissW * mix(1.0, hell, wandW), schaum);
    // E12 Stämme in der Walze: wild gedreht, quer zur Front, mitgerissen und an der Wand nach oben geworfen [G]
    if (walze > 0.05) {
        vec2 qs2 = qw + vec2(0.0, -t * 5.0);
        // Zellen 11 × 6 m, je Zelle ein Stamm an zufälliger Stelle (Probe 7: im festen Raster standen sie in Reih und Glied)
        vec2 zl = floor(vec2(qs2.x / 11.0, qs2.y / 6.0)), fz = vec2(qs2.x / 11.0, qs2.y / 6.0) - zl;
        fz -= (vec2(sgHash(zl + 4.1), sgHash(zl + 6.7)) - 0.5) * 0.5;
        float wink = (sgHash(zl + 1.7) - 0.5) * 2.4;
        vec2 rr = vec2(cos(wink) * (fz.x - 0.5) - sin(wink) * (fz.y - 0.5), sin(wink) * (fz.x - 0.5) + cos(wink) * (fz.y - 0.5));
        float st = step(0.72, sgHash(zl + 8.3)) * (1.0 - smoothstep(0.04, 0.07, abs(rr.y))) * (1.0 - smoothstep(0.22, 0.30, abs(rr.x)));
        st *= 1.0 - smoothstep(0.25, 0.7, pxw);
        vec3 hz = mix(vec3(0.06, 0.045, 0.03), vec3(0.52, 0.46, 0.38), step(0.45, sgHash(zl + 2.2)));
        col = mix(col, hz * weissL, st * walze);
    }
    // ---- Treibgut hinter der Front [G]: Stämme (geschält hell, mit Rinde dunkel), in Feldern gehäuft, die mit der Zeit
    // dichter werden (Treibholzteppich); Striche quer zum Wind; in der Ferne als Mittel (Fleckung) statt Gesprenkel
    if (nach > 0.0 && landW < 0.99) {   // Runde 4: am Hang zeichnet die Zunge ihre Stämme selbst
        float feld = smoothstep(0.45, 0.75, sgNF(q + 41.0, 120.0, vec2(0.0), px)) * smoothstep(20.0, 240.0, seit);
        float i0 = floor(qr.x / 9.0);
        vec2 qd = vec2(qr.x + 9.0 * (sgHash(vec2(i0, 7.0)) - 0.5), qr.y + 40.0 * sgHash(vec2(i0, 3.0)));
        vec2 zl = floor(vec2(qd.x / 9.0, qd.y / 1.6)), fz = vec2(qd.x / 9.0, qd.y / 1.6) - zl;
        float da = sgHash(zl + 3.3);
        float dicht = mix(0.92, 0.55, feld);
        float stamm = step(dicht, da) * (1.0 - smoothstep(0.18, 0.30, abs(fz.y - 0.5))) * smoothstep(0.08, 0.16, fz.x) * (1.0 - smoothstep(0.7, 0.85, fz.x));
        float nahS = 1.0 - smoothstep(0.25, 0.9, px);
        vec3 holzF = mix(vec3(0.05, 0.04, 0.03), vec3(0.50, 0.45, 0.38), step(0.5, sgHash(zl + 9.1)));
        float anteil = (stamm * nahS + (1.0 - nahS) * (1.0 - dicht) * 0.55) * flach;
        col = mix(col, holzF * weissL * 0.85, clamp(anteil, 0.0, 0.9) * (1.0 - front) * (1.0 - quelle) * (1.0 - landW));
    }
    // ---- W5 (E11) die Zunge am Hang [G]: durchlüftetes Weißwasser, das hangauf schießt, längs der Laufrichtung in Fäden
    // gerissen; zwischen den Fäden braunes Wasser voll Erde; an der Kante ein Saum aus Schaum und Spritzern; Stämme
    // treiben darin. Fällt der Stand wieder (Rückfall), laufen Schleier und Rinnen hangab, das Wasser wird dünn und glänzt.
    if (landW > 0.0) {
        float seitL = max(seit - vorkante * landW, 0.0);
        SgTongue m = sgTongue(wL < 0.5 ? DL0 : DL1, s.w.xz, seitL, t, px);
        if (wL > 0.0 && wL < 1.0) {
            SgTongue o = sgTongue(wL < 0.5 ? DL1 : DL0, s.w.xz, seitL, t, px);
            float a = wL < 0.5 ? wL : 1.0 - wL;
            // Runde 5: zwei unabhängige Muster gemischt sind flauer (Streuung × √(a² + (1−a)²)); auf die alte Streuung zurück
            float vk = inversesqrt(a * a + (1.0 - a) * (1.0 - a));
            m.faden = 0.5 + (mix(m.faden, o.faden, a) - 0.5) * vk; m.ball = 0.5 + (mix(m.ball, o.ball, a) - 0.5) * vk;
            m.gb = mix(m.gb, o.gb, a) * vk;
            // Runde 6: Stämme nicht gemischt (in der Mitte standen sie halb so kräftig): die der Hauptrichtung voll, die der
            // zweiten kommen kurz vor der Mitte voll dazu (dort kurz doppelt so dicht); die Farbe vom kräftigeren
            float s1 = o.st * smoothstep(0.3, 0.5, a);
            m.stHell = s1 > m.st ? o.stHell : m.stHell; m.st = max(m.st, s1); m.stFern = mix(m.stFern, o.stFern, a);
        }
        float faden = m.faden, ball = m.ball, pxL = px;
        float sinkt = 1.0 - smoothstep(-0.9, -0.05, s.rise + 0.5 * (faden - 0.5));
        float jung = 1.0 - smoothstep(4.0, 14.0, seitL);
        float duenn = 1.0 - smoothstep(1.5, 8.0, s.depth);
        // Weißwasser: hangauf fast überall, beim Rückfall in Rinnen
        // Runde 3: Schaum als Ballen [G]. Aus 1,5 km las sich die Zunge als flache helle Streifen (wie Schnee): jetzt zwei
        // Lagen gewölbter Ballen (4 und 10 m, „Billow“), die mit dem Wasser wandern, aus ihrem Gefälle eine Normale; Licht
        // von oben auf die Kuppen, die Furchen dazwischen im Eigenschatten
        // (Probe W5Q: mit 0,30–0,62 war die Zunge braun mit hellen Flecken; sie ist durchlüftet, also fast ganz weiß)
        // Runde 3: die Ränder der Fäden in Ballen aufgebrochen (vorher glatte Bänder von 30 m Länge: wie Schnee)
        float bruch = (ball - 0.62) * 0.7;
        float wsL = mix(smoothstep(0.12, 0.42, faden + 0.30 * jung + bruch), smoothstep(0.50, 0.78, faden + bruch) * 0.75, sinkt);
        wsL = max(wsL, (1.0 - smoothstep(0.0, 1.4, seitL)) * smoothstep(0.25, 0.55, faden + 0.3));   // Saum an der Kante
        vec3 erde = mix(vec3(0.30, 0.24, 0.17), vec3(0.20, 0.15, 0.10), duenn) * eDown * 0.45;   // Wasser voll Erde und Nadeln
        vec3 colL = mix(erde, col, 0.25 * (1.0 - duenn));
        // dünnes Wasser beim Rückfall glänzt (Himmel im Wasserfilm), dickes ist trüb
        colL = mix(colL, mix(erde, lightSkyDir(reflect(d, normalize(N + vec3(g.x, 0.0, g.y) * 0.6))) * 0.5, 0.6 * fres + 0.25 * sinkt * duenn), sinkt * 0.7);
        vec3 nB = normalize(N + vec3(m.gb.x, 0.0, m.gb.y));
        vec3 lichtB = lightSun(s.w) * max(dot(nB, uSun), 0.0) * 0.9 + lightSky(normalize(nB + vec3(0.0, 0.6, 0.0))) * 1.15;
        float furche = mix(0.38, 1.15, smoothstep(0.15, 0.85, ball));
        vec3 schaumL = mix(vec3(0.86, 0.85, 0.82), vec3(0.62, 0.55, 0.45), 0.35 * duenn + 0.25 * sinkt) * mix(weissW, lichtB * furche, 0.8);
        colL = mix(colL, schaumL, clamp(wsL * (0.95 - 0.35 * duenn * sinkt), 0.0, 1.0));
        // Stämme in der Zunge, längs der Strömung, dunkel mit hellen geschälten (Runde 4: nur nah als Striche, in der
        // Ferne standen sie als kleine helle Rechtecke; fern als dunklere Schlieren)
        float leben = smoothstep(0.5, 3.0, seitL) * (1.0 - smoothstep(30.0, 90.0, seitL));
        float stNah = 1.0 - smoothstep(0.08, 0.3, pxL);
        float stL = m.st * leben * stNah;
        colL *= 1.0 - 0.18 * (1.0 - stNah) * leben * m.stFern;
        colL = mix(colL, mix(vec3(0.05, 0.04, 0.03), vec3(0.45, 0.40, 0.33), m.stHell) * weissW * 0.9, stL * 0.85);
        col = mix(col, colL, landW);
    }
    // W4b: unter dem Netz der Lippe ist die steile Wand die Rückwand der Röhre: dunkel, oben etwas Licht durch die Lippe;
    // ihre Treppe aus dem Höhenfeld (Zickzack) geht im Dunkel unter [G]
    if (uSgLipMesh == 1 && hatLauf) {
        // Runde 2: auch die flacheren Teile unter der Lippe (in den offenen Abschnitten sah man dort hellen Dunst)
        // Runde 4: die Maske hängt nur noch an Zeit, Höhe und Blick (vorher an der Normale des Höhenfelds, die auf der
        // Treppe der Zellen zickzackt: daraus die geschwungenen Linien auf der Rückwand); ganz dunkel statt 95 %, damit
        // die Streifen des Schaums darunter nicht mehr durchscheinen
        // (zweite Probe: der Fuß der Wand kommt später an; mit −0,6 s lag sein unterer Teil halb außerhalb, dort standen
        // die Linien weiter; jetzt ab −2,5 s, vor der Front hält die Höhe über dem Pegel die Maske zu)
        float roehre = smoothstep(-2.5, -1.2, s.since) * (1.0 - smoothstep(1.8, 3.5, s.since)) * smoothstep(0.0, 0.05, hrel) * (1.0 - smoothstep(0.92, 1.04, hrel))
                     * smoothstep(8.0, 16.0, hs) * (1.0 - smoothstep(45.0, 80.0, hs)) * smoothstep(0.0, 0.35, vorn);
        float rs = sgNoise(vec2(dot(s.w.xz, Tq) * 0.12, s.w.y * 0.05 - uTime * 0.45)) * 0.6 + sgNoise(vec2(dot(s.w.xz, Tq) * 0.03 + 4.0, s.w.y * 0.02 - uTime * 0.2)) * 0.4;
        // (dritte Probe: ganz ohne die Linien war die Röhre ein schwarzes Loch; etwas mehr Licht von oben durch die Lippe)
        vec3 innenL = lightSkyDir(vec3(0.0, 1.0, 0.0)) * vec3(0.16, 0.50, 0.44) * (0.05 + 0.30 * smoothstep(0.15, 1.0, hrel)) * (0.7 + 0.6 * rs);
        // ohne die Sonne: die Rückwand liegt im Schatten der Lippe, und der Schatten der Treppe aus dem Höhenfeld auf sich
        // selbst (Schattenkarte) zog die dunklen Linien (Probe Runde 4)
        vec3 eAmb = lightIrradiance();
        vec3 bodyR = mix(mix(klar, milch, 0.55), lehm, schlamm) * eAmb * 0.32;
        col = mix(col, bodyR * 0.5 + innenL, roehre);
    }
    col = lightAerial(col, d, dist);
    return vec4(col, 1.0);
}
