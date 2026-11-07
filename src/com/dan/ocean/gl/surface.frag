// surface.frag — Optik der See: Normale aus den Ableitungen aller Kaskaden und der Dünung, Fresnel mit Spiegelung des
// Himmels (Panorama-Textur), Streulicht im Wasser in Nordsee-Grün, Licht im Wellenkamm (durchscheinend gegen die
// Sonne), Sonnen- und Mondglitzer, Schaum aus Überschlag (mit Nachleuchten), Schaumstraßen ab Bft 7, Brandungsringe
// um Hindernisse, Ringwellen nach Einschlägen, Dunst zur Kimm, Wolkenschatten, Schatten eines Körpers und eine feine Störung wie Regenringe (alle
// optional). Ausgabe: HDR-Farbe, linear.
#include "ocean_common.glsl"
in vec2 vRest;
in vec3 vWorld;
in float vHeight;
in float vDist;
in float vDepth;
uniform vec3 uBedColor;
out vec4 frag;
uniform sampler2D uSky;        // Himmel als Panorama (Azimut von Nord, Höhe), linear
uniform float uSkyGain;
uniform vec3 uSunDir, uSunColor, uMoonDir, uMoonColor;
uniform vec3 uWaterColor, uSssColor;
uniform float uHs;
uniform float uFogDist;
uniform float uWindTo;
uniform float uWindSpeed;
uniform vec4 uObst[8];         // x, z, Radius, Ring (1; seit Lighthouse Phase 17 0 = ohne Brandungsring)
uniform int uObstCount;
uniform sampler2D uObstField;    // seit Haithabu Phase 8: Abstand zum nächsten Körper (m), viele Pfähle
uniform vec4 uObstFieldRect;     // Ecke x, z, Breite, Tiefe (m)
uniform int uObstFieldOn;
uniform vec2 uRingHs;            // Brandungsring: Beginn und volle Stärke über H_s (m)
uniform vec4 uHullA[8];          // seit Haithabu Phase 9: Rumpf x, z, sin und cos des Kurses
uniform vec4 uHullB[8];          // halbe Länge, halbe Breite in der Wasserlinie (m), Völligkeit
uniform int uHullCount;
uniform vec4 uRipple[32];      // x, z, Alter, Stärke
uniform int uRippleCount;
uniform float uFoamGain;
uniform vec3 uGlow;   // seit Sylt Phase 13: Meeresleuchten (vorbelichtet), 0 = aus
uniform sampler2D uCloudShadow;  // Anteil direkten Lichts von oben (Wolken), optional
uniform vec4 uShadowRect;       // x, z der Ecke, Kantenlänge in m, an (1) / aus (0)
uniform sampler2D uBodyShadow;   // Schatten eines Körpers (Turm, Schiff) auf dem Wasser, optional
uniform vec4 uBodyRect;         // wie uShadowRect
uniform sampler2D uDetail;      // feine Störung (Regenringe): Steigung in RG, kachelbar, optional
uniform vec4 uDetailP;          // Kachel in m, Stärke, Reichweite in m, an (1) / aus (0)
uniform vec4 uDetailQ;          // seit LituyaBay1958 W7b: Mitte x, z des Fensters, Art (0 Kachel, 1 Feld fest in der Welt), —
uniform vec4 uDetailR;          // seit W7b Runde 5: mittlere quadratische Steigung unter dem Raster der Störung, die sie trägt (0, 0 = aus), Anteile der Fläche dazu
uniform int uDetailN;           // Bildzähler für die Rauigkeit (zeitliche Glättung mittelt)
uniform sampler2D uPointTex;    // Punktlicht: Lichtstärke cd je Richtung vom Licht (Rundumbild), optional
uniform vec4 uPointPos;         // Ort des Punktlichts, Faktor (0 = aus)
uniform float uPointSigma;      // Auslöschung der Luft auf dem Weg zum Wasser (1/m)
uniform int uFlashN;            // einfache Punktlichter (Blitze): Zahl
uniform vec4 uFlashPos[8];      // Ort, Größe der Quelle (m)
uniform vec4 uFlashRgb[8];      // Lichtstärke RGB in cd · Vorbelichtung
uniform sampler2D uBrash;       // seit Glacier Phase 9: Treibeis, Anteil der bedeckten Fläche 0..1, optional
uniform vec4 uBrashRect;        // Ecke x, z, Breite, Tiefe (m)
uniform int uBrashOn;
uniform sampler2D uMask;        // seit Glacier Phase 13: ein gestauter Seearm (1) als eigener Spiegel, optional
uniform vec3 uMaskRect;         // Ecke x, z, Kantenlänge (m)
uniform int uMaskMode;          // 1: nur innen zeichnen, −1: nur außen, 0: aus
uniform int uUnder;             // seit Rungholt Phase 6: das Auge liegt unter dem Spiegel
uniform vec4 uHoleA;            // seit Sylt Phase 7a: Loch für ein anderes Wasser (Netz der Brecher): Mitte x, z, Richtung u (x, z)
uniform vec4 uHoleB;            // halbe Länge längs u, halbe Breite quer, weicher Rand (m), an (1)
// seit W8b Runde 2: feines zweites Feld (Kaskade), Maske des Regens
uniform sampler2D uDetail2, uDetailMask;
uniform vec4 uDetail2P;         // Kante (m), Stärke, Rauigkeit unter seinem Raster, an (1)
uniform vec2 uDetail2Q;         // Mitte
uniform vec4 uDetailMaskR;      // x0, z0, Kante (m), an (1)
// seit LituyaBay1958 W8b: Spiegelruhe (Schwelle des Winds für Kräuselwellen, Böenfeld mit treibenden Flecken)
uniform vec4 uCalm;             // Einsetzen, voll (m/s), Streuung (ln), Fleckgröße (m); x = 0: aus
uniform vec2 uCalmW;            // Wind (m/s), Zeit (s)
float calmN(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453), b = fract(sin(dot(i + vec2(1, 0), vec2(127.1, 311.7))) * 43758.5453);
    float c = fract(sin(dot(i + vec2(0, 1), vec2(127.1, 311.7))) * 43758.5453), d = fract(sin(dot(i + vec2(1, 1), vec2(127.1, 311.7))) * 43758.5453);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
// Anteil der Kräusel am Ort: Wind am Ort = Wind · exp(σ · z) mit z aus vier Oktaven Rauschen (Mittel 0, Streuung ≈ 1),
// treibend mit 0,8 des Winds in Windrichtung und langsam gewandelt; glatt unter dem Einsetzen, voll ab der vollen Stärke
float calmRipple(vec2 p) {
    if (uCalm.x <= 0.0) return 1.0;
    vec2 wd = vec2(sin(uWindTo), -cos(uWindTo));
    vec2 q = (p - wd * uCalmW.x * 0.8 * uCalmW.y) / uCalm.w;
    float t = uCalmW.y / max(uCalm.w / max(uCalmW.x, 0.3), 5.0) * 0.15;
    float n = 0.5 * calmN(q + vec2(t, 0.0)) + 0.25 * calmN(q * 2.03 + vec2(5.2, -t)) + 0.125 * calmN(q * 4.1 + 9.1) + 0.0625 * calmN(q * 8.3 + 2.7);
    float z = (n - 0.469) / 0.135;
    float u = uCalmW.x * exp(uCalm.z * z);
    return smoothstep(uCalm.x, uCalm.y, u);
}
// seit LituyaBay1958 W8a: planare Spiegelung (Bild der gespiegelten Szene samt Himmel, seine Tiefe, seine Kamera)
uniform sampler2D uPlanar, uPlanarDepth;
uniform int uPlanarOn;
uniform mat4 uPlanarVP;
uniform vec4 uPlanarP;          // Spiegelebene, Nahebene, Fernebene, tan(halbes Blickfeld)
uniform vec4 uPlanarE;          // Auge beim Spiegelbild, umgekehrte Tiefe (1)
uniform vec3 uPlanarF;          // Blickrichtung beim Spiegelbild
uniform vec3 uPlanarR;          // Anteil, verborgene Rauigkeit, ihr Zuwachs je m

// Der gespiegelte Strahl r von P aus, im Spiegelbild: Das Bild eines Punkts X in der gespiegelten Szene liegt dort, wo die
// Kamera den gespiegelten Punkt X' = P' + d'·D sieht (P' = P an der Ebene gespiegelt, d' = r mit umgekehrtem y). D folgt
// aus der Tiefe des Spiegelbilds (zwei Schritte: erst die Richtung, dann der Punkt in dieser Tiefe, dann noch einmal);
// ohne Tiefe (Himmel) gilt die Richtung. Rückgabe: Farbe, Deckung (Alpha des Bilds mal Rand).
vec4 planarLookup(vec3 P, vec3 r, float dist) {
    vec3 Pm = vec3(P.x, 2.0 * uPlanarP.x - P.y, P.z);
    vec3 ds = vec3(r.x, -r.y, r.z);
    vec4 c = uPlanarVP * vec4(ds, 0.0);
    if (c.w <= 1e-6) return vec4(0.0);
    vec2 uv = c.xy / c.w * 0.5 + 0.5;
    float den = dot(ds, uPlanarF);
    for (int i = 0; i < 2; i++) {
        if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) break;
        float z = textureLod(uPlanarDepth, uv, 0.0).r;
        float vz;
        if (uPlanarE.w > 0.5) vz = z > 0.0 ? uPlanarP.y / z : -1.0;
        else { float zn = z * 2.0 - 1.0; vz = z < 1.0 ? 2.0 * uPlanarP.z * uPlanarP.y / ((uPlanarP.z + uPlanarP.y) - zn * (uPlanarP.z - uPlanarP.y)) : -1.0; }
        if (vz <= 0.0 || den <= 1e-4) break;
        float D = (vz - dot(Pm - uPlanarE.xyz, uPlanarF)) / den;
        if (D <= 0.0) break;
        vec4 b = uPlanarVP * vec4(Pm + ds * D, 1.0);
        if (b.w <= 1e-4) break;
        uv = b.xy / b.w * 0.5 + 0.5;
    }
    // seit W8b Runde 2: knapp außerhalb (Wellen am Bildrand) am Rand greifen statt ausblenden – sonst ein heller Streifen
    vec2 e = min(uv, 1.0 - uv);
    float edge = smoothstep(-0.03, -0.005, min(e.x, e.y));
    if (edge <= 0.0) return vec4(0.0);
    uv = clamp(uv, vec2(0.0005), vec2(0.9995));
    // Mip-Stufe: was die Normale nicht trägt (Kräusel unter dem Gitter, in der Ferne gefiltert), streut den Strahl um
    // rund 2σ; im Bild sind das 2σ / (2 tan(φ/2)) der Höhe
    float sig = min(0.08, uPlanarR.y + uPlanarR.z * dist);
    float px = 2.0 * sig * float(textureSize(uPlanar, 0).y) / max(2.0 * uPlanarP.w, 1e-3);
    vec4 R = textureLod(uPlanar, uv, log2(max(px, 1.0)));
    return vec4(R.rgb, clamp(R.a, 0.0, 1.0) * edge);
}

// Lichtstärke des Punktlichts in Richtung w (vom Licht weg), mal Faktor
vec3 pointIntensity(vec3 w) {
    float az = atan(w.x, -w.z) / 6.28318530718;
    float el = degrees(asin(clamp(w.y, -1.0, 1.0)));
    return textureLod(uPointTex, vec2(fract(az + 1.0), 0.5 + 0.5 * sign(el) * sqrt(abs(el) / 90.0)), 0.0).rgb * uPointPos.w;
}

vec3 sky(vec3 d) {
    float az = atan(d.x, -d.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
    return textureLod(uSky, vec2(az, el), 0.0).rgb * uSkyGain;
}

float smoothr(float a, float b, float x) { return smoothstep(a, b, x); }

// Ringwellen: Steigung (dh/dx, dh/dz) aller Einschläge, Formel wie Ripples.ring()
vec2 rippleSlope(vec2 p) {
    vec2 g = vec2(0.0);
    for (int i = 0; i < 32; i++) {
        if (i >= uRippleCount) break;
        vec4 r = uRipple[i];
        vec2 dv = p - r.xy;
        float rr = length(dv), tau = r.z;
        if (rr > 0.9 * tau + 1.0 || rr < 1e-4) continue;
        float front = rr - 0.9 * tau, w = 0.15 + 0.12 * tau;
        float env = exp(-front * front / (w * w)) * exp(-tau / 1.4) / (1.0 + rr * 1.5);
        float k = 6.28318530718 / 0.35;
        float dh = r.w * 0.06 * env * (-k * sin(k * front) - 2.0 * front / (w * w) * cos(k * front));
        g += dh * dv / rr;
    }
    return g;
}

void main() {
    // seit Haithabu: kein Wasser über dem Grund oder außerhalb seines Rechtecks
    if (vDepth < -0.02) discard;
    // seit Sylt Phase 7a: im Loch zeichnet ein anderer; am Rand verdünnt (Raster nach Bildpunkt)
    if (uHoleB.w > 0.0) {
        vec2 hd = vRest - uHoleA.xy;
        vec2 hu = uHoleA.zw;
        float pu = abs(dot(hd, hu)) - uHoleB.x, pv = abs(dot(hd, vec2(-hu.y, hu.x))) - uHoleB.y;
        float inside = -max(pu, pv);                    // > 0 innen
        if (inside > 0.0) {
            float keepIt = 1.0 - clamp(inside / max(uHoleB.z, 0.01), 0.0, 1.0);
            if (hash21(floor(gl_FragCoord.xy)) >= keepIt) discard;
        }
    }
    if (uMaskMode != 0) {
        vec2 mu = (vRest - uMaskRect.xy) / uMaskRect.z;
        float m = all(greaterThan(mu, vec2(0.0))) && all(lessThan(mu, vec2(1.0))) ? textureLod(uMask, mu, 0.0).r : 0.0;
        if (uMaskMode == 1 && m < 0.5 || uMaskMode == -1 && m >= 0.5) discard;
    }
    // Wolkenschatten (aus com.dan.cloud über die App): Anteil direkten Lichts an dieser Stelle
    float shade = 1.0;
    if (uShadowRect.w > 0.0) shade = textureLod(uCloudShadow, (vWorld.xz - uShadowRect.xy) / uShadowRect.z, 0.0).r;
    if (uBodyRect.w > 0.0) {
        vec2 bu = (vWorld.xz - uBodyRect.xy) / uBodyRect.z;
        if (all(greaterThan(bu, vec2(0.0))) && all(lessThan(bu, vec2(1.0)))) shade *= textureLod(uBodyShadow, bu, 0.0).r;
    }
    vec3 sunC = uSunColor * shade, moonC = uMoonColor * shade;
    // Hindernisse: innen kein Wasser, außen ein Brandungsring
    float ring = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= uObstCount) break;
        float ds = length(vWorld.xz - uObst[i].xy) - uObst[i].z;
        if (ds < 0.0) discard;
        float width = 0.5 + 0.8 * uHs;
        float n = vnoise(vec2(vWorld.x * 0.8 + uTime * 0.35, vWorld.z * 0.8 - uTime * 0.2));
        ring = max(ring, uObst[i].w * smoothr(uRingHs.x, uRingHs.y, uHs) * exp(-ds / width) * (0.45 + 0.55 * n));
    }
    // Rümpfe: innen kein Wasser (offene Boote), außen ein schmaler Saum aus Schaum
    for (int i = 0; i < 8; i++) {
        if (i >= uHullCount) break;
        vec2 d = vWorld.xz - uHullA[i].xy;
        vec2 f = vec2(uHullA[i].z, -uHullA[i].w);
        float s = dot(d, f), q = d.x * -f.y + d.y * f.x;
        float u = s / uHullB[i].x;
        float ds;
        if (abs(u) >= 1.0) ds = length(vec2(abs(s) - uHullB[i].x, q));
        else ds = abs(q) - uHullB[i].y * pow(max(1.0 - pow(abs(u), uHullB[i].z), 0.0), 0.8);
        if (ds < 0.0) discard;
        float n = vnoise(vec2(vWorld.x * 1.3 + uTime * 0.5, vWorld.z * 1.3 - uTime * 0.3));
        ring = max(ring, 0.35 * exp(-ds / 0.35) * (0.4 + 0.6 * n));
    }
    if (uObstFieldOn == 1) {
        vec2 fu = (vWorld.xz - uObstFieldRect.xy) / uObstFieldRect.zw;
        if (all(greaterThan(fu, vec2(0.0))) && all(lessThan(fu, vec2(1.0)))) {
            float ds = textureLod(uObstField, fu, 0.0).r;
            float width = 0.5 + 0.8 * uHs;
            float n = vnoise(vec2(vWorld.x * 0.8 + uTime * 0.35, vWorld.z * 0.8 - uTime * 0.2));
            ring = max(ring, smoothr(uRingHs.x, uRingHs.y, uHs) * exp(-ds / width) * (0.45 + 0.55 * n));
        }
    }
    // Ableitungen und Schaum aller Kaskaden
    float sx = 0.0, sz = 0.0, jx = 0.0, jz = 0.0, keep = 1.0;
    for (int c = 0; c < 4; c++) {
        if (c >= uCascades) break;
        vec3 uv = vec3(vRest / uLength[c], float(c));
        float g = cascadeGain(vRest, c);
        vec4 dv = texture(uDeriv, uv) * g;
        sx += dv.x; sz += dv.y; jx += dv.z; jz += dv.w;
        keep *= 1.0 - clamp(texture(uDisp, uv).w * g, 0.0, 1.0);
    }
    // seit W8b: Spiegelruhe – ohne Wind am Ort keine Kräusel (die Dünung bleibt)
    float rip = calmRipple(vWorld.xz);
    float calmK = mix(0.03, 1.0, rip);
    sx *= calmK; sz *= calmK; jx *= calmK; jz *= calmK;
    vec3 swd, sws;
    swell(vRest, swd, sws);
    sws *= swellGain(vRest);
    sx += sws.x; sz += sws.y;
    vec2 rs = rippleSlope(vWorld.xz);
    // seit Glacier Phase 8: Neigung der langen Welle; an ihrer steilen Stirn bricht sie weiß
    float longFoam = 0.0;
    if (uLongR0.w > 0.0 || uLongR1.w > 0.0) {
        vec3 l0 = longWave(vRest);
        if (l0.z > 0.0 && l0.y > 0.0) {
            float e = uLongR0.w > 0.0 && longLevel(uLongH0, uLongR0, vRest).z > 0.0 ? uLongR0.z : uLongR1.z;
            vec2 ls = vec2(longWave(vRest + vec2(e, 0.0)).x - longWave(vRest - vec2(e, 0.0)).x,
                           longWave(vRest + vec2(0.0, e)).x - longWave(vRest - vec2(0.0, e)).x) / (2.0 * e);
            rs += ls;
            longFoam = smoothstep(0.04, 0.15, length(ls)) * smoothstep(0.15, 0.8, l0.x);
        }
    }
    float detailShown = 0.0;   // seit W7b Runde 5: welcher Anteil der getragenen Steigung hier zu sehen ist (Amplitude)
    vec2 rsDet = vec2(0.0);
    float win2 = 0.0, rainMask = 1.0;
    if (uDetailP.w > 0.0) {
        if (uDetailQ.z < 0.5) {
            detailShown = exp(-vDist / uDetailP.z);
            rsDet = texture(uDetail, vWorld.xz / uDetailP.x).rg * uDetailP.y * detailShown;
        } else {
            // Feld fest in der Welt: im Fenster echt, draußen zwei gedrehte Abfragen (Steigung zurückgedreht), gemittelt
            vec2 rel = abs(vWorld.xz - uDetailQ.xy) / uDetailP.x;
            float win = 1.0 - smoothstep(0.30, 0.47, max(rel.x, rel.y));
            vec2 near = texture(uDetail, vWorld.xz / uDetailP.x).rg;
            const mat2 R1 = mat2(0.8, 0.6, -0.6, 0.8), R2 = mat2(-0.28, 0.96, -0.96, -0.28);
            vec2 a = texture(uDetail, (R1 * vWorld.xz) / uDetailP.x + 0.37).rg, b = texture(uDetail, (R2 * vWorld.xz) / uDetailP.x + 0.71).rg;
            vec2 far = (transpose(R1) * a + transpose(R2) * b) * 0.7071;
            detailShown = win + (1.0 - win) * exp(-vDist / uDetailP.z);
            rsDet = (near * win + far * (1.0 - win) * exp(-vDist / uDetailP.z)) * uDetailP.y;
            // seit W8b Runde 2: das feine Feld nah am Auge trägt die Wellen unter dem groben Raster (andere Wellenzahlen,
            // also einfach dazu); in seinem Fenster liegt unter dem Raster nur noch, was auch es nicht trägt
            if (uDetail2P.w > 0.5) {
                vec2 rel2 = abs(vWorld.xz - uDetail2Q) / uDetail2P.x;
                win2 = 1.0 - smoothstep(0.30, 0.47, max(rel2.x, rel2.y));
                rsDet += texture(uDetail2, vWorld.xz / uDetail2P.x).rg * uDetail2P.y * win2;
            }
        }
        // seit W8b Runde 2: nur wo ein Schauer fällt (die Karte der Schauer); ferne Ringe nicht unter trockenem Himmel
        if (uDetailMaskR.w > 0.5) {
            vec2 mu = (vWorld.xz - uDetailMaskR.xy) / uDetailMaskR.z;
            rainMask = all(greaterThanEqual(mu, vec2(0.0))) && all(lessThanEqual(mu, vec2(1.0))) ? clamp(textureLod(uDetailMask, mu, 0.0).r, 0.0, 1.0) : 0.0;
        }
        rsDet *= rainMask;
        rs += rsDet;
    }
    // seit W7b Runde 5: Varianz erhalten. Was die Störung hier nicht als Steigung zeigt – unter ihrem Raster, in der
    // Mip-Stufe weggemittelt, in der Ferne ausgeblendet –, ist trotzdem da: als Neigung einzelner Facetten. Nur ein
    // Anteil p der Fläche trägt sie (Regen: unter dem Raster p ≈ 0,2 um die jungen Einschläge, die Ringe p ≈ 0,34), dafür
    // mit Varianz/p – so bleibt der Spiegel zwischen den Einschlägen glatt. Die zeitliche Glättung mittelt die Facetten
    // eines Bildpunkts, wie sie in seinem Fußabdruck liegen.
    vec2 rsJ = vec2(0.0);
    bool detailJ = false;
    if (uDetailP.w > 0.0 && uDetailR.x + uDetailR.y > 0.0) {
        float lod = max(textureQueryLod(uDetail, vWorld.xz / uDetailP.x).y, 0.0);
        float shown = detailShown * detailShown * exp2(-lod);   // im Kapillarband trägt jede Oktave etwa gleich viel [G]
        // unter dem Raster erst, wo ein Bildpunkt viele Einschläge deckt (Fußabdruck ab rund 1 cm); näher wäre es Korn [G]
        float s2 = uDetailP.y * uDetailP.y, hid = uDetailR.y * max(0.0, 1.0 - shown), fSub = smoothstep(0.004, 0.03, length(fwidth(vWorld.xz)));
        uint hs = uint(gl_FragCoord.x) * 1973u ^ uint(gl_FragCoord.y) * 9277u ^ uint(uDetailN) * 26699u;
        uvec3 h3;
        for (int qq = 0; qq < 3; qq++) { hs = (hs ^ (hs >> 16u)) * 0x45d9f3bu; hs = (hs ^ (hs >> 16u)) * 0x45d9f3bu; hs ^= hs >> 16u; h3[qq] = hs; hs += 0x9e3779b9u; }
        vec2 ua = (vec2(h3.x & 0xffffu, h3.x >> 16u) + 0.5) / 65536.0, ub = (vec2(h3.y & 0xffffu, h3.y >> 16u) + 0.5) / 65536.0;
        vec2 sel = vec2(h3.z & 0xffffu, h3.z >> 16u) / 65536.0;
        vec2 g1 = sqrt(-2.0 * log(ua.x)) * vec2(cos(6.2831853 * ua.y), sin(6.2831853 * ua.y));
        vec2 g2 = sqrt(-2.0 * log(ub.x)) * vec2(cos(6.2831853 * ub.y), sin(6.2831853 * ub.y));
        float pS = clamp(uDetailR.z, 1e-3, 1.0), pR = clamp(uDetailR.w, 1e-3, 1.0);
        // seit W8b Runde 2: die Facetten sitzen, wo das Feld gerade stark ist – an den jungen Einschlägen und ihren inneren
        // Ringen – statt gleich verteilt: Wahrscheinlichkeit mal (0,25 + 0,75 · |∇h|² / Mittel), im Mittel dieselbe
        float eLoc = dot(rsDet, rsDet) / max(1e-12, s2 * max(uDetailR.y, 1e-8) * max(rainMask, 1e-3) * max(rainMask, 1e-3));
        // (die Stärke je Facette bleibt, so sammelt sich die Varianz selbst dort; wo das Feld ausgeblendet ist, gleich verteilt)
        float clus = mix(1.0, 0.25 + 0.75 * min(eLoc / max(detailShown * detailShown, 0.05), 4.0), clamp(detailShown, 0.0, 1.0));
        float pSl = clamp(pS * clus, 1e-3, 1.0), pRl = clamp(pR * clus, 1e-3, 1.0);
        float sub = mix(uDetailR.x, uDetail2P.z, win2 * uDetail2P.w) * rainMask, hidM = hid * rainMask;
        if (sel.x < pSl) rsJ += sqrt(0.5 * fSub * sub * s2 / pS) * g1;
        if (sel.y < pRl) rsJ += sqrt(0.5 * hidM * s2 / pR) * g2;
        detailJ = true;
    }
    // seit Timmendorf Phase 8: Neigung und Schaum des Kielwassers von außen
    float kielFoam = 0.0;
    if (uKielR0.w > 0.0 || uKielR1.w > 0.0) {
        rs += kielSlope(vRest) * smoothstep(0.0, 1.0, vDepth);
        kielFoam = kielAt(vRest).y * smoothstep(0.0, 0.5, vDepth);
    }
    // seit Rungholt Phase 6: Strömung. Kräuselung als Rauschen, das mit dem Wasser zieht (zwei Phasen, überblendet,
    // damit es sich nicht endlos dehnt); Stärke mit der Geschwindigkeit, nur nah
    vec2 fv = flowAt(vRest);
    float fsp = length(fv);
    if (fsp > 0.04 && vDist < 900.0) {
        float k = 1.0 / 1.7, amp = 0.22 * smoothstep(0.05, 1.0, fsp) * (1.0 - smoothstep(300.0, 900.0, vDist));
        vec2 g = vec2(0.0);
        for (int ph = 0; ph < 2; ph++) {
            float t = fract(uTime / 4.0 + 0.5 * float(ph));
            vec2 q = (vRest - fv * t * 4.0) * k + float(ph) * 7.7;
            float e = 0.15;
            vec2 gg = vec2(vnoise(q + vec2(e, 0.0)) - vnoise(q - vec2(e, 0.0)), vnoise(q + vec2(0.0, e)) - vnoise(q - vec2(0.0, e))) / (2.0 * e) * k;
            g += gg * (1.0 - abs(2.0 * t - 1.0));
        }
        rs += g * amp;
    }
    vec3 n = normalize(vec3(-(sx / max(0.2, 1.0 + jx + 0.5 * sws.z) + rs.x), 1.0, -(sz / max(0.2, 1.0 + jz + 0.5 * sws.z) + rs.y)));
    if (detailJ) n = normalize(n + vec3(-rsJ.x, 0.0, -rsJ.y));

    vec3 V = normalize(uCam - vWorld);
    // seit Rungholt Phase 6: von unten. Im Snellschen Fenster (bis 48,6° vom Lot) der gebrochene Himmel, außerhalb die
    // Totalreflexion des trüben Wassers; das Wasser zwischen Auge und Fläche legt danach drawUnderwater darüber
    if (uUnder == 1 && uCam.y < vWorld.y) {
        vec3 up = sky(vec3(0.0, 1.0, 0.0));
        vec3 eD = up * 3.5 + sunC * max(uSunDir.y, 0.0);
        vec3 deep = uWaterColor * eD * 0.11 * 0.6;
        vec3 t = refract(-V, -n, 1.33);
        vec3 c = deep;
        if (dot(t, t) > 0.0) {
            float Fu = 0.02 + 0.98 * pow(1.0 - clamp(dot(V, -n), 0.0, 1.0), 5.0);
            c = mix(sky(normalize(t)) * 0.85 + sunC * pow(max(dot(normalize(t), uSunDir), 0.0), 400.0) * 2.0, deep, Fu);
        }
        frag = vec4(c, 1.0);
        return;
    }
    if (dot(n, V) < 0.02) n = normalize(n + V * (0.02 - dot(n, V)));
    float ndv = clamp(dot(n, V), 0.0, 1.0);
    float F = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
    vec3 r = reflect(-V, n);
    r.y = abs(r.y);
    vec3 refl = sky(r);
    if (uPlanarOn == 1) {
        vec4 pl = planarLookup(vWorld, r, vDist * mix(0.3, 1.0, rip));
        refl = mix(refl, pl.rgb, pl.a * uPlanarR.x);
    }

    // Wasserkörper: Streulicht aus Himmel und Sonne, im Kamm heller und türkis. Maßstab (Phase 3, physikalisches
    // Licht): Bestrahlung von oben E = Sonne · sin h + Himmel (≈ 3,5 · Zenit-Strahldichte); das Wasser gibt davon
    // rund 1 % je Steradiant zurück (Nordsee, grün), uWaterColor · 0,11.
    vec3 skyUp = sky(vec3(0.0, 1.0, 0.0));
    float sunUp = max(uSunDir.y, 0.0);
    vec3 eDown = skyUp * 3.5 + sunC * sunUp + moonC * max(uMoonDir.y, 0.0);
    vec3 body = uWaterColor * eDown * 0.11;
    // Trübung: schnelles, flaches Wasser wirbelt Schwebstoff auf (Farbe uFlowSilt.rgb, Stärke .a)
    float silt = uFlowRect.w > 0.0 ? clamp(uFlowSilt.a * (smoothstep(0.1, 1.0, fsp) * 0.7 + 0.3 * (1.0 - smoothstep(0.3, 3.0, vDepth))), 0.0, 0.85) : 0.0;
    body = mix(body, uFlowSilt.rgb * eDown * 0.11, silt);
    float crest = smoothstep(0.25, 1.0, vHeight / max(0.4 * uHs, 0.05));
    vec3 Lh = normalize(vec3(uSunDir.x, 0.0, uSunDir.z) + vec3(0.0, 1e-4, 0.0));
    vec3 Vh = normalize(vec3(V.x, 0.0, V.z) + vec3(0.0, 1e-4, 0.0));
    float back = pow(clamp(dot(-Vh, Lh) * 0.5 + 0.5, 0.0, 1.0), 4.0);
    float grazing = pow(1.0 - clamp(V.y, 0.0, 1.0), 2.0);
    vec3 sss = uSssColor * crest * grazing * (sunC * back * sunUp * 0.9 + skyUp * 0.35) * 0.15;
    // seit Haithabu: im Flachwasser scheint der Grund durch (Weg hin und zurück, schräg länger), bei uBedRect.w = 0 nie
    if (uBedRect.w > 0.0 && vDepth < 8.0) {
        float path = max(vDepth, 0.0) * (1.0 + 1.0 / max(V.y, 0.15));
        float T = exp(-uBedMap.z * (1.0 + 2.0 * silt) * path);
        vec3 bedLit = uBedColor / 3.14159265 * (sunC * sunUp * exp(-uBedMap.z * max(vDepth, 0.0) / max(sunUp, 0.15)) + skyUp * 3.5);
        body = mix(body, bedLit, T);
    }
    vec3 col = mix(body + sss, refl, F);

    // Glitzer: Sonne und Mond, rauer mit der Entfernung (die kleinen Wellen sind dort weggefiltert)
    float rough = clamp(0.012 + vDist * 0.00002, 0.012, 0.12) * mix(0.3, 1.0, rip);   // W8b: auf glattem Wasser ist der Glanz eine Scheibe
    float a2 = rough * rough;
    vec3 Hs = normalize(uSunDir + V);
    float nh = max(dot(n, Hs), 0.0);
    float D = a2 / (3.14159265 * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0));
    col += sunC * D * F * 0.25 * smoothstep(-0.02, 0.02, uSunDir.y);
    vec3 Hm = normalize(uMoonDir + V);
    float nm = max(dot(n, Hm), 0.0);
    float Dm = a2 / (3.14159265 * pow(nm * nm * (a2 - 1.0) + 1.0, 2.0));
    col += moonC * Dm * F * 0.25 * smoothstep(-0.02, 0.02, uMoonDir.y);
    // Punktlicht (Leuchtfeuer): Bestrahlung I/d² aus seiner Richtung, Glitzer wie Sonne und Mond
    vec3 pointE = vec3(0.0);
    vec3 Lp = vec3(0.0, 1.0, 0.0);
    if (uPointPos.w > 0.0) {
        vec3 tp = uPointPos.xyz - vWorld;
        float dp = max(length(tp), 0.5);
        Lp = tp / dp;
        pointE = pointIntensity(-Lp) / (dp * dp) * exp(-uPointSigma * dp);
        vec3 Hp = normalize(Lp + V);
        float np = max(dot(n, Hp), 0.0);
        float Dp = a2 / (3.14159265 * pow(np * np * (a2 - 1.0) + 1.0, 2.0));
        col += pointE * Dp * F * 0.25 + uWaterColor * pointE * max(Lp.y, 0.0) * 0.11;
    }
    // einfache Punktlichter (Blitze): Licht im Wasserkörper und auf dem Schaum; den Glanz zeigt die Spiegelung
    vec3 flashE = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= uFlashN) break;
        vec3 tf = uFlashPos[i].xyz - vWorld;
        float d2 = dot(tf, tf) + uFlashPos[i].w * uFlashPos[i].w;
        flashE += uFlashRgb[i].rgb * max(dot(n, normalize(tf)), 0.0) / d2 * exp(-uPointSigma * sqrt(dot(tf, tf)));
    }
    col += uWaterColor * flashE * 0.11;

    // Schaum: Überschlag aller Kaskaden, Straßen in Windrichtung, Brandungsringe; feine Struktur aus Rauschen
    float foam = (1.0 - keep) * uFoamGain;
    vec2 wd = vec2(sin(uWindTo), -cos(uWindTo));
    float along = dot(vRest, wd), across = dot(vRest, vec2(-wd.y, wd.x));
    float warp = vnoise(vec2(along / 23.0, across / 3.1)) * 1.6;
    float streak = smoothr(13.0, 17.5, uWindSpeed) * smoothr(0.58, 0.82, vnoise(vec2(along / 34.0 + warp, across / 0.9)) * 0.6 + vnoise(vec2(along / 9.0 - warp, across / 0.35)) * 0.4) * 0.75;
    // seit Haithabu: Schaumsaum am Ufer, wenn Wind die kleinen Wellen auflaufen lässt
    if (uBedRect.w > 0.0) {
        float shore = (1.0 - smoothstep(0.02, 0.22, vDepth)) * smoothr(2.5, 9.0, uWindSpeed);
        ring = max(ring, shore * (0.35 + 0.65 * vnoise(vec2(vWorld.x * 1.3 - uTime * 0.4, vWorld.z * 1.3 + uTime * 0.3))) * 0.6);
    }
    // Schaum in Streifen, die mit der Strömung ziehen (ab 0,4 m/s), dichter im Flachen
    float flowFoam = 0.0;
    if (fsp > 0.3) {
        vec2 fd = fv / fsp;
        float al = dot(vRest, fd), ac = dot(vRest, vec2(-fd.y, fd.x));
        float sn = vnoise(vec2(al / 7.0 - uTime * fsp / 7.0, ac / 0.9)) * 0.6 + vnoise(vec2(al / 2.3 - uTime * fsp / 2.3, ac / 0.4)) * 0.4;
        flowFoam = smoothstep(0.6, 0.85, sn) * smoothstep(0.35, 1.1, fsp) * (0.35 + 0.4 * (1.0 - smoothstep(0.3, 2.0, vDepth)));
    }
    // seit Sylt Phase 7a: die Brandung von außen. Die Walze vorn weiß und dicht, dahinter der zerreißende Schaum, dazu
    // der Schaumteppich zwischen den Brechern; das Relief der Walzen neigt die Fläche
    float surfFoam = 0.0;
    if (uSurfRect.w > 0.0) {
        vec4 sf = surfAt(vRest);
        if (sf.r > 0.002 || sf.b > 0.002) {
            float s = surfSaw(sf.g);
            float rag = vnoise(vec2(dot(vRest, vec2(0.37, 0.93)) / 6.0, dot(vRest, vec2(0.93, -0.37)) / 1.4 + uTime * 0.1));
            float roller = exp(-s / 0.06) + 0.55 * exp(-s / 0.22) * smoothstep(0.35, 0.75, rag + 0.25);
            float brk = surfBreaks(vRest, sf);
            surfFoam = brk * roller;
            // seit Sylt Phase 10: die steigende Front der nächsten Walze ist schon weiß (keine glasige Rampe)
            float wNext = floor(uTime / max(uSurfPeriod, 1.0) - sf.g) + 1.0;
            surfFoam = max(surfFoam, surfBreaksWave(vRest, sf, wNext) * smoothstep(0.8, 1.0, s) * 0.7);
            float carpet = sf.b * smoothstep(0.45, 0.8, vnoise(vRest / 3.3 + vec2(uTime * 0.03, 0.0)) * 0.6 + vnoise(vRest / 1.1) * 0.4 + 0.2);
            surfFoam = max(surfFoam, carpet);
            // Neigung aus dem stetigen Relief (seit Phase 9a), über ±1 m abgeleitet
            float e = 1.0;
            vec2 rx = vec2(e, 0.0), rz = vec2(0.0, e);
            vec2 bs = vec2(surfRelief(vRest + rx, surfAt(vRest + rx), vDepth) - surfRelief(vRest - rx, surfAt(vRest - rx), vDepth),
                           surfRelief(vRest + rz, surfAt(vRest + rz), vDepth) - surfRelief(vRest - rz, surfAt(vRest - rz), vDepth)) / (2.0 * e);
            n = normalize(n + vec3(-bs.x, 0.0, -bs.y));
        }
    }
    foam = clamp(max(max(max(max(max(max(foam, streak), ring), longFoam), flowFoam), surfFoam), kielFoam), 0.0, 1.0);
    // Struktur: in Windrichtung gestrecktes Rauschen; wenig Schaum zeigt nur die Spitzen (zerrissene Schaumköpfe),
    // viel Schaum deckt alles. In der Ferne wird die Struktur weich, damit nichts flimmert.
    vec2 fp = vec2(along * 0.55, across);
    float detail = vnoise(fp * 0.9) * 0.45 + vnoise(fp * 2.7 + 7.3) * 0.33 + vnoise(fp * 8.1 + 3.1) * 0.22;
    float soft = clamp(vDist / 400.0, 0.0, 1.0);
    float edge = mix(0.22, 0.6, soft);
    float level = 1.0 - min(foam, 0.8) * 0.9;
    float foamVis = mix(smoothstep(level, level + edge, detail), foam, soft * 0.7) * (0.45 + 0.45 * clamp(foam * 1.5, 0.0, 1.0));
    // Schaum als matte Fläche mit Albedo 0,6: Strahldichte = 0,6/π · Bestrahlung
    vec3 foamCol = vec3(0.93, 0.97, 1.0) * 0.6 / 3.14159265 * (sunC * max(dot(n, uSunDir), 0.0) + moonC * max(dot(n, uMoonDir), 0.0) + skyUp * 3.5 + pointE * max(dot(n, Lp), 0.0) + flashE);
    col = mix(col, foamCol, foamVis);
    // seit Sylt Phase 13: Meeresleuchten – Walzen und ihr Schaum leuchten stark, Schaumköpfe draußen schwach
    if (uGlow.g > 0.0) col += uGlow * foamVis * clamp(surfFoam * 1.2 + (foam - surfFoam) * 0.25, 0.0, 1.0) * (0.6 + 0.4 * vnoise(fp * 3.1 + uTime * 1.9));

    // seit Glacier Phase 9: Treibeis. Die Brocken sind Zellen aus Rauschen (rund 1,7 m); es liegen so viele, wie das
    // Feld Fläche bedeckt. In der Ferne nur noch die Bedeckung als weißer Schleier.
    if (uBrashOn == 1) {
        vec2 bu = (vWorld.xz - uBrashRect.xy) / uBrashRect.zw;
        if (all(greaterThanEqual(bu, vec2(0.0))) && all(lessThanEqual(bu, vec2(1.0)))) {
            float cover = textureLod(uBrash, bu, 0.0).r;
            if (cover > 0.003) {
                vec2 g = vWorld.xz / 1.7, gi = floor(g);
                float best = 9.0, second = 9.0, hc = 1.0;
                for (int y = -1; y <= 1; y++)
                    for (int x = -1; x <= 1; x++) {
                        vec2 c = gi + vec2(float(x), float(y));
                        vec2 pnt = c + vec2(hash21(c), hash21(c + 17.3));
                        float d = length(g - pnt);
                        if (d < best) { second = best; best = d; hc = hash21(c + 5.1); }
                        else if (d < second) second = d;
                    }
                float piece = step(hc, cover * 1.15) * smoothstep(0.05, 0.16, second - best);
                float far = smoothstep(250.0, 1200.0, vDist);
                float amount = mix(piece, cover * 0.85, far);
                vec3 iceCol = vec3(0.80, 0.88, 0.94) * 0.75 / 3.14159265 * (sunC * max(uSunDir.y, 0.0) + moonC * max(uMoonDir.y, 0.0) + skyUp * 3.5);
                col = mix(col, iceCol * (0.85 + 0.3 * hc), amount);
            }
        }
    }

    // Dunst zur Kimm
    float f = 1.0 - exp(-vDist / uFogDist);
    vec3 fogCol = sky(normalize(vec3(-V.x, 0.0, -V.z) + vec3(0.0, 0.002, 0.0)));
    col = mix(col, fogCol, f);
    frag = vec4(col, 1.0);
}
