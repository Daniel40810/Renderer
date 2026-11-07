// lip.frag — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W4b, E7–E9): die Lippe der Front [G].
//  · Rücken und Krone: Wasser, das den Himmel spiegelt, zur Krone hin Schaum in Fäden längs der Laufrichtung;
//  · Außenseite der Lippe: dünnes Wasser, durch das der Himmel scheint (türkis, wo klar, braun im Schlamm), Schlieren
//    in der Fallrichtung, die mit der Zeit nach unten laufen;
//  · Spitze: ausgefranster weißer Saum, der abreißt;
//  · Innenseite (aus der Röhre gesehen): dunkel, zur Spitze hin das Licht, das durch die Lippe fällt;
//  · der Vorhang (u ≥ 2): aus Strängen und Löchern nach dem Anteil „zu“, gestreut durchsichtig an den Rändern.
// Licht über den Lichtvertrag (light.glsl).
#include "light.glsl"
#include "/com/dan/surge/gl/surge_noise.glsl"
in vec3 vW;
in vec3 vN;
in float vU;
in float vH;
in vec2 vT;
in float vRand;
in float vZu;
in vec2 vB;
out vec4 frag;

float vRiss = 0.0;
// Rauschen längs der Front ohne eine Koordinate längs der Front: räumlich über x und z (waagrecht gleich in jeder
// Richtung), b ist die zweite Koordinate (Höhe, Zeit) der früheren vnf(vec2(längs · a, b), p, px)
float vnfP(vec2 xz, float a, float b, float p, float px) {
    return mix(sgNoise3(vec3(xz.x * a, b, xz.y * a + 31.7) / p), 0.5, smoothstep(0.25, 0.6, px * max(a, 1e-3) / p));
}
float vnfL(float a, float b, float p, float px) {
    // (am Punkt der Linie, nicht an der Ecke: sonst lief das Rauschen auch quer über die gewölbte Lippe und die Strähnen
    // zerfielen in Flecken)
    return mix(sgNoise3(vec3(vB.x * a, b, vB.y * a + 31.7) / p), 0.5, smoothstep(0.25, 0.6, px * max(a, 1e-3) / p));
}   // Runde 4: Nähe zum Rand eines Lochs im Vorhang (dort reißt das Wasser weiß auf)

void main() {
    // Runde 4: der Vorhang ist ein eigener Streifen (u + 2); zu sehen nach dem Anteil „zu“, mit gestreuter Durchsicht,
    // die die Kantenglättung mittelt: so geht ein offener Abschnitt weich in einen aufgeschlagenen über, ohne Spitzen
    bool vhg = vU > 1.5;
    float u = vhg ? vU - 2.0 : vU;
    vec3 d = vW - uCam;
    float dist = length(d);
    d /= max(dist, 1e-3);
    vec3 v = -d;
    vec3 N = normalize(vN);
    bool innen = dot(N, v) < 0.0;
    if (innen) N = -N;
    vec2 T = length(vT) > 0.5 ? normalize(vT) : vec2(1.0, 0.0), Tq = vec2(-T.y, T.x);
    float t = uTime;
    // Koordinaten: längs der Front (m), Lauf des Querschnitts (0 … 1)
    // Runde 4: keine Koordinate „längs der Front“ mehr als dot(Position, Tq): mit Positionen von einigen Kilometern
    // sprang sie, wo die Richtung zwischen zwei Ecken um ein Grad drehte, um zig Meter, und mit ihr jedes Rauschen
    // (senkrechte Kanten im Vorhang, Sprünge in den Strähnen). Statt dessen Rauschen im Raum,
    // waagrecht in beiden Richtungen gleich (dreidimensionales Rauschen über x, Höhe/Zeit, z)
    float px = max(length(fwidth(vec3(vB.x, vW.y, vB.y))), 1e-3);   // Meter je Bildpunkt längs der Front und in der Höhe
    // Probe Runde 4: gestreut durchsichtig blieb der halbe Vorhang ein körniger Schleier. Statt dessen bildet er sich aus
    // Strängen, die von der Spitze fallen: je mehr „zu“, desto mehr Stränge, bis er geschlossen ist; nur die Ränder der
    // Stränge sind gestreut (die Kantenglättung mittelt sie)
    if (vhg) {
        // zweite Probe: mit 30 m in der Höhe wurden die Stränge zu Brettern; jetzt Löcher, die nach unten länger werden
        // und mit dem Wasser fallen, oben an der Lippe hängt der Vorhang eher zusammen
        float st = vnfP(vW.xz, 1.0 / 2.0, (vW.y + uTime * 9.0) / 7.0, 1.0, px) * 0.45
                 + vnfP(vW.xz, 1.0 / 6.0, (vW.y + uTime * 6.0) / 16.0 + 3.0, 1.0, px) * 0.35
                 + vnfP(vW.xz, 1.0 / 18.0, (vW.y + uTime * 3.0) / 40.0 + 7.0, 1.0, px) * 0.20;
        float oben = clamp((1.0 - u) / 0.38, 0.0, 1.0);
        float schw = mix(-0.12, 1.12, vZu) - st + 0.18 * oben - 0.09;   // > 0: Wasser
        vRiss = 1.0 - smoothstep(0.0, 0.14, schw);
        float rand = max(0.05, 0.6 * px / 2.0);
        if (schw < -rand || (schw < rand && sgHash(gl_FragCoord.xy * 1.37 + vec2(uFrame * 5.3, uFrame * 1.9)) > 0.5 + 0.5 * schw / rand)) discard;
    }
    vec3 eDown = lightIrradiance() + lightSun(vW) * max(uSun.y, 0.0);
    vec3 body = mix(vec3(0.040, 0.078, 0.070), vec3(0.085, 0.070, 0.050), 0.55) * eDown * 0.32;
    vec3 col;
    // Durchlicht: Himmel hinter der Lippe, gefärbt nach der Dicke (zur Spitze dünner) [G]
    float duenn = smoothstep(0.30, 0.80, u) * (1.0 - 0.4 * smoothstep(0.88, 1.0, u));
    vec3 hinter = lightSkyDir(normalize(vec3(d.x, max(d.y, 0.0) + 0.25, d.z)));
    vec3 farbe = mix(vec3(0.16, 0.52, 0.45), vec3(0.42, 0.33, 0.19), 0.45);
    // (Runde 4: die scharfen Schlieren früherer Runden kamen zum Teil aus den Sprüngen der Koordinate längs der Front;
    // mit glattem Rauschen jetzt feiner längs der Front und kontrastreicher)
    float schl = vnfL(1.0, vW.y * 0.12 + t * 2.5, 2.5, px) * 0.55 + vnfL(1.0, vW.y * 0.05 + t * 1.2 + 9.0, 8.0, px) * 0.45;
    schl = smoothstep(0.25, 0.75, schl);
    if (!innen) {
        float fres = 0.02 + 0.98 * pow(1.0 - max(dot(N, v), 0.0), 5.0);
        vec3 r = reflect(d, N);
        vec3 refl = lightSkyDir(vec3(r.x, max(r.y, 0.01), r.z));
        // flach hinaus gespiegelt: die dunklen Hänge der Bucht (wie welle.frag)
        vec3 hang = lightHaze(normalize(vec3(r.x, 0.0, r.z) + vec3(1e-4))) * 0.20;
        refl = mix(refl, hang, 1.0 - smoothstep(0.10, 0.40, r.y));
        vec3 durch = hinter * farbe * (0.35 + 0.45 * schl) * duenn;
        col = mix(body + durch, refl, fres * (1.0 - 0.5 * duenn));
        vec3 hv = normalize(uSun + v);
        col += lightSun(vW) * pow(max(dot(N, hv), 0.0), 300.0) * 0.6;
    } else {
        // in der Röhre: dunkel, zur Lippe hin Licht, das durch das dünne Wasser fällt
        col = mix(body * 0.5, hinter * farbe * (0.30 + 0.30 * schl), smoothstep(0.45, 0.95, u) * 0.8);
    }
    // Schaum: auf der Krone in Fäden längs der Laufrichtung, an der Spitze ein ausgefranster Saum
    vec3 weiss = lightSun(vW) * max(dot(N, uSun), 0.0) * 0.9 + lightSky(normalize(N + vec3(0.0, 1.2, 0.0))) * 1.15;
    float krone = smoothstep(0.08, 0.30, u) * (1.0 - smoothstep(0.42, 0.60, u));
    float faeden = vnfL(1.0, (vW.y - uLevel) * 0.3 - t * 1.5, 2.5, px) * 0.6 + vnfL(1.0, t * 0.7, 12.0, px) * 0.4;
    float saumN = vnfL(1.0, vW.y * 0.5 + t * 4.0, 3.0, px) * 0.55 + vnfL(1.0, t, 14.0, px) * 0.45;
    // Spitze (u ≈ 0,62): ausgefranster Saum; darunter der Vorhang (u ≥ 0,7), der ins Wasser fällt: Strähnen, die nach
    // unten laufen, unten zum Aufprall hin dicht, dazwischen Lücken mit grünem Durchlicht
    float spitze = vhg ? 0.0 : smoothstep(0.55, 0.63, u + (saumN - 0.5) * 0.12) * (1.0 - smoothstep(0.68, 0.74, u));
    float vorhang = vhg ? smoothstep(0.62, 0.70, u) : 0.0;
    float faellt = vnfL(1.2, vW.y * 0.12 + t * 7.0, 4.0, px) * 0.6 + vnfL(1.0, vW.y * 0.05 + t * 3.0 + 3.0, 15.0, px) * 0.4;
    float schaum = max(krone * smoothstep(0.40, 0.66, faeden), spitze * smoothstep(0.30, 0.55, saumN + 0.15));
    // Runde 2: der Vorhang mehr grünes Durchlicht, weniger und schärfere weiße Strähnen; unten am Aufschlag dicht
    schaum = max(schaum, vorhang * clamp(smoothstep(0.46, 0.66, faellt) + smoothstep(0.90, 1.0, u) * 0.8, 0.0, 1.0));   // Runde 4: mehr Strähnen (der geschlossene Vorhang war Milchglas)
    schaum = max(schaum, (vhg ? 0.0 : 1.0 - vZu) * smoothstep(0.60, 0.66, u) * 0.95);
    // Runde 4: der Vorhang, der sich erst bildet, ist weißer; an den Rändern seiner Löcher reißt er weiß auf
    if (vhg) schaum = max(schaum, vorhang * max((1.0 - vZu) * 0.7, vRiss * 0.85));
    // oben, wo die Lippe umschlägt und in den Vorhang übergeht, Weißwasser (sonst stand dort eine gerade Naht);
    // der ganze Vorhang ist mehr Weißwasser als Glas (aus der Ferne, wo die Strähnen ins Mittel gehen, war er Milchglas)
    if (vhg) schaum = max(schaum, max((1.0 - smoothstep(0.62, 0.72, u + (saumN - 0.5) * 0.08)) * 0.9, 0.35 + 0.25 * smoothstep(0.4, 0.6, faellt)));   // der Saum der offenen Lippe ist Weißwasser
    schaum *= innen ? 0.5 : 1.0;
    col = mix(col, vec3(0.80, 0.80, 0.78) * weiss, clamp(schaum, 0.0, 1.0));
    // (Probe: Strähnen als ausgeschnittene Löcher gaben harte Lamellen; die offene Lippe endet mit einem weißen Saum)
    // die Ränder der Lippe (wo die Welle niedriger wird) gehen in die Wellenfläche über
    if (vRand < 0.15 && sgHash(gl_FragCoord.xy + vec2(uFrame * 3.1)) > vRand / 0.15) discard;
    col = lightAerial(col, d, dist);
    frag = vec4(col, 1.0);
}
