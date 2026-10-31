// cliff.glsl — com.dan.cliff: Fels als Material und Feinform, Zeile für Zeile wie Cliff.java und CNoise.java.
// Einbinden über eine Include-Zeile mit dem absoluten Pfad /com/dan/cliff/gl/cliff.glsl im Klassenpfad; die Uniforms setzt
// CliffGl.bind. Alles ist 3D-Rauschen am Ort (keine Projektion auf Ebenen): senkrechte Wände ziehen keine Streifen.
//   cliffRelief(p, footprint)                 Relief in m um 0 (Feinform), für den Scheitelshader und die Normale
//   cliffDisplace(p, n, footprint, range)     verschobener Ort: entlang der Normale um das Relief, nur an der Wand
//   cliffMaterial(p, n, rainWet, snow, fp)    Farbe, Normale, Anteil Fels, Nässe, Schnee (nur im Bildpunkt: dFdx)
// Das Rauschen kommt aus der 3D-Textur uCliffNoise (64³, CNoise.latticeTexture), die CliffGl.bind auf Einheit 15 legt.
uniform int uCliffSeed;
uniform vec4 uCliffSlope;      // Fels ab (1 − cos), Wand von, Wand bis (1 − cos), Oktaven des Reliefs
uniform vec3 uCliffFol;        // Normale der Schieferung
uniform vec4 uCliffJoint;      // Winkel der Scharen A und B (rad, schon + 90°), Abstand, Breite (m)
uniform vec2 uCliffRelief;     // Höhe (m), größter Maßstab (m)
uniform vec3 uCliffLight, uCliffDark, uCliffFeld, uCliffLichen, uCliffMoss, uCliffTalus, uCliffSnow;
uniform vec4 uCliffAmount;     // Flechten, Moos, Sickerstreifen, Feldspat
uniform float uCliffFallWet;   // Stärke der nassen Streifen unter den Fällen 0..1 (Cliff.fallWet, nach dem Abfluss)
uniform sampler2D uCliffMap;   // Wand, Geröll, Nässe, Absatz (CliffMap)
uniform vec4 uCliffMapRect;    // x0, z0, Kantenlänge (m), an
uniform int uCliffRock;        // seit Brocken Phase 8: 0 Gneis, 1 Granit (Cliff.Rock)
uniform vec2 uCliffSheet;      // Granit: Abstand der Bankung (m), Rundung der Blöcke (Anteil des Abstands)
uniform float uCliffForce;     // 0..1: Fels und Wand unabhängig von der Neigung (Netze: Felsburgen, Blöcke)
uniform float uCliffTopMoss;   // seit Brocken Phase 8: Moos auf allem, was nach oben schaut (Cliff.topMoss)
uniform float uCliffPillow;    // Granit: Wollsäcke als Feinform 1 (Wand, Gelände), 0 auf Netzen aus Blöcken (die Blöcke sind die Wollsäcke)
// seit Sylt Phase 4: Lockergestein (uCliffRock 2): Schichtkarte, Deckschichten, Farben, Runsen, Hohlkehle (Cliff.rotesKliff)
uniform sampler2D uCliffStrata; // Oberkante, untere Grenze, Decke, Hohlkehle (m, RGBA16F, StrataMap)
uniform vec4 uCliffStrataRect;  // x0, z0, Breite, Tiefe (m)
uniform int uCliffStrataOn;
uniform vec4 uCliffStrataDef;   // ohne Karte: Oberkante, untere Grenze, Decke, Hohlkehle
uniform vec4 uCliffThick;       // Grasnarbe, Bleichsand, Ortstein, Steinsohle (m)
uniform vec4 uCliffThick2;      // Eisenschwarte (m), Unterkante und Höhe der Hohlkehle (m), Länge der Sickerstreifen (m)
uniform vec4 uCliffGully;       // Runsen: Abstand, Tiefe, Anteil; Rieselsand
uniform vec2 uCliffLoose;       // Trockenrisse, Findlinge
uniform vec3 uCliffLehm, uCliffLehmDark, uCliffKaolin, uCliffKaolinDark, uCliffIron, uCliffSand, uCliffBleach, uCliffPodsol, uCliffStone, uCliffSod, uCliffGrass;

uint cliffHash(int x, int y, int z) {
    uvec3 v = uvec3(uint(x), uint(y), uint(z)) * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}

float cliffUnit(uint h) { return float(h >> 8u) * (1.0 / 16777216.0); }
float cliffFade(float t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

uniform sampler3D uCliffNoise;  // Gitterwerte 64³ (R16, wiederholt), CNoise.latticeTexture

// Wertrauschen: ein Abruf an der geglätteten Stelle; die Grafikkarte mischt trilinear mit den quintischen Gewichten
float cliffValue(vec3 p, int seed) {
    p += vec3(float((seed * 37) & 63), float((seed * 59) & 63), float((seed * 1013) & 63));
    p -= 64.0 * floor(p / 64.0);
    vec3 i = floor(p), t = p - i;
    vec3 f = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    return textureLod(uCliffNoise, (i + f + 0.5) / 64.0, 0.0).r;
}

// Seit Geiranger Nacharbeit 11: dieselbe Mischung genau gerechnet (acht Gitterwerte). Die Grafikkarte mischt trilinear
// nur mit 8 Bit Gewicht (256 Stufen je Zelle); ganz nah, wo eine Zelle viele Bildpunkte deckt, zeichneten diese Stufen
// über die Ableitungen der Normale ein Raster aus Strichen in die Wand (Status_Phase11a). cliffRelief nimmt sie dort, wo
// eine Stufe breiter ist als ein Bildpunkt.
float cliffValueExact(vec3 p, int seed) {
    p += vec3(float((seed * 37) & 63), float((seed * 59) & 63), float((seed * 1013) & 63));
    p -= 64.0 * floor(p / 64.0);
    vec3 i = floor(p), t = p - i;
    vec3 f = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    ivec3 a = ivec3(i) & 63, b = (a + 1) & 63;
    float c000 = texelFetch(uCliffNoise, ivec3(a.x, a.y, a.z), 0).r, c100 = texelFetch(uCliffNoise, ivec3(b.x, a.y, a.z), 0).r;
    float c010 = texelFetch(uCliffNoise, ivec3(a.x, b.y, a.z), 0).r, c110 = texelFetch(uCliffNoise, ivec3(b.x, b.y, a.z), 0).r;
    float c001 = texelFetch(uCliffNoise, ivec3(a.x, a.y, b.z), 0).r, c101 = texelFetch(uCliffNoise, ivec3(b.x, a.y, b.z), 0).r;
    float c011 = texelFetch(uCliffNoise, ivec3(a.x, b.y, b.z), 0).r, c111 = texelFetch(uCliffNoise, ivec3(b.x, b.y, b.z), 0).r;
    return mix(mix(mix(c000, c100, f.x), mix(c010, c110, f.x), f.y), mix(mix(c001, c101, f.x), mix(c011, c111, f.x), f.y), f.z);
}

float cliffValue1(float x, int seed) {
    float f = floor(x);
    int ix = int(f);
    return mix(cliffUnit(cliffHash(ix, seed, 7)), cliffUnit(cliffHash(ix + 1, seed, 7)), cliffFade(x - f));
}

float cliffBand(float lambda, float footprint) { return 1.0 - smoothstep(0.5 * lambda, 1.5 * lambda, 2.0 * footprint); }

// eine feste Drehung gegen die Kästchen des Gitters (wie Cliff.ROT, Zeilen von ROT als Spalten: q = p * CLIFF_ROT = ROT · p)
const mat3 CLIFF_ROT = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);

const float CLIFF_PILL_MEAN = 0.79;

// Granit (seit Brocken Phase 8): 0 an der Kluft, 1 in der Mitte eines Blocks, aus zwei senkrechten Scharen und der
// waagerechten Bankung, gerundet; in der Ferne das Mittel 0,79. Wie Cliff.granitBlock.
float cliffGranitBlock(vec3 p, float footprint) {
    float pill = 1.0;
    for (int k = 0; k < 3; k++) {
        float sp, u, open;
        if (k < 2) {
            float a = k == 0 ? uCliffJoint.x : uCliffJoint.y;
            sp = uCliffJoint.z;
            u = (p.x * sin(a) - p.z * cos(a)) / sp + (cliffValue(p / 25.0, uCliffSeed + 8 + k) - 0.5) * 0.8
                + (cliffValue(p / 6.0, uCliffSeed + 14 + k) - 0.5) * 0.35;
            open = smoothstep(0.38, 0.55, cliffValue(p / vec3(9.0, 4.0, 9.0), uCliffSeed + 40 + k));
        } else {
            sp = uCliffSheet.x;
            u = p.y / sp + (cliffValue(p / 8.0, uCliffSeed + 30) - 0.5) * 1.6 + (cliffValue(p / 5.0, uCliffSeed + 31) - 0.5) * 0.3;
            open = smoothstep(0.48, 0.62, cliffValue(p / vec3(7.0, 30.0, 7.0), uCliffSeed + 42));
        }
        float toJoint = (0.5 - abs(u - floor(u) - 0.5)) * sp;
        pill *= 1.0 - open * (1.0 - smoothstep(0.0, uCliffSheet.y * sp, toJoint));
    }
    return CLIFF_PILL_MEAN + (pill - CLIFF_PILL_MEAN) * cliffBand(uCliffSheet.y * uCliffSheet.x * 2.0, footprint) * uCliffPillow;
}

// Lockergestein: Runse 0..1, senkrecht gestreckt (die Rinnen folgen der Falllinie). Wie Cliff.gully.
float cliffGully(vec3 p) {
    float g = cliffValue(p / vec3(uCliffGully.x, uCliffGully.x * 9.0, uCliffGully.x), uCliffSeed + 55);
    return smoothstep(0.55, 0.85, g) * uCliffGully.z;
}

// Lockergestein: Form der Hohlkehle über der Höhe (1 am tiefsten). Wie Cliff.notchShape.
float cliffNotchShape(float y) {
    float t = (y - uCliffThick2.y) / uCliffThick2.z;
    if (t <= 0.0 || t >= 1.0) return 0.0;
    return sin(3.14159265 * pow(t, 0.7));
}

// Lockergestein: Schichtkarte am Ort (außerhalb oder ohne Karte die Vorgaben). Wie Cliff.strataAt.
vec4 cliffStrataAt(vec2 xz) {
    if (uCliffStrataOn == 1) {
        vec2 uv = (xz - uCliffStrataRect.xy) / uCliffStrataRect.zw;
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) return textureLod(uCliffStrata, uv, 0.0);
    }
    return uCliffStrataDef;
}

float cliffRelief(vec3 p, float footprint) {
    float s = uCliffRelief.y, a = uCliffRelief.x, r = 0.0;
    if (uCliffRock == 1) {
        r = uCliffRelief.x * (cliffGranitBlock(p, footprint) - CLIFF_PILL_MEAN);
        a *= 0.35;
    } else if (uCliffRock == 2) {
        r = -uCliffGully.y * cliffGully(p) * cliffBand(uCliffGully.x, footprint);
    }
    for (int o = 0; o < 4; o++) {
        if (float(o) >= uCliffSlope.w) break;
        p = p * CLIFF_ROT;
        float w = cliffBand(s, footprint);
        if (w <= 0.0) break;   // die feineren Oktaven sind noch kleiner (gleiches Ergebnis, weniger Abrufe in der Ferne)
        // nah (die 256 Stufen einer Zelle liegen weiter als ein Bildpunkt auseinander) genau gemischt, sonst ein Abruf
        float n = footprint * 256.0 < s ? cliffValueExact(p / s, uCliffSeed + 11 + o) : cliffValue(p / s, uCliffSeed + 11 + o);
        r += a * w * (0.5 - abs(2.0 * n - 1.0));
        s /= 3.0;
        a /= 2.6;
    }
    return r;
}

// Wandkarte am Ort; ohne Karte aus der Neigung (Wand), sonst 0
vec4 cliffMapAt(vec2 xz, float slope) {
    if (uCliffMapRect.w > 0.0) {
        vec2 uv = (xz - uCliffMapRect.xy) / uCliffMapRect.z;
        if (all(greaterThanEqual(uv, vec2(0.0))) && all(lessThanEqual(uv, vec2(1.0)))) return textureLod(uCliffMap, uv, 0.0);
    }
    return vec4(smoothstep(uCliffSlope.y, uCliffSlope.z, slope), 0.0, 0.0, 0.0);
}

// Lockergestein: die Hohlkehle am Ort (m, so weit weicht die Wand zurück)
float cliffNotch(vec3 p) {
    if (uCliffRock != 2) return 0.0;
    float nd = cliffStrataAt(p.xz).w;
    return nd > 0.0 ? nd * cliffNotchShape(p.y) : 0.0;
}

// Feinform: der Ort, entlang der Normale um das Relief verschoben, nur an der Wand und bis range m vom Auge; die
// Hohlkehle (Lockergestein) in jeder Entfernung
vec3 cliffDisplace(vec3 p, vec3 n, float footprint, float dist, float range) {
    float wall = cliffMapAt(p.xz, 1.0 - n.y).x;
    // in der Ferne aus, und dicht vor dem Auge auch (sonst schöbe sich die Wand über ein Auge, das 2 m über dem Höhenfeld steht)
    float fade = (1.0 - smoothstep(range * 0.7, range, dist)) * smoothstep(4.0, 40.0, dist);
    float notch = cliffNotch(p) * wall;
    if (wall * fade <= 0.0 && notch <= 0.0) return p;
    return p + n * (cliffRelief(p, footprint) * wall * fade - notch);
}

// Material nur im Bildpunkt (dFdx); Scheitelshader setzen vor der Include-Zeile #define CLIFF_VERTEX
#ifndef CLIFF_VERTEX
struct CliffSample { vec3 albedo; vec3 normal; float rock; float wet; float snow; };

CliffSample cliffMaterial(vec3 p, vec3 n, float rainWet, float snowAmt, float footprint) {
    CliffSample s;
    float slope = 1.0 - n.y;
    vec4 m = cliffMapAt(p.xz, slope);
    float wall = max(m.x, uCliffForce), tal = m.y, wetMap = m.z, ledge = m.w;
    s.rock = max(max(max(smoothstep(uCliffSlope.x, uCliffSlope.x + 0.12, slope), wall), tal * 0.45), uCliffForce);
    int sd = uCliffSeed;

    // Normale aus dem Relief über die Ableitungen im Bild (Oberflächengradient): ein Reliefwert statt vier; die
    // Ableitungen stehen außerhalb jeder Verzweigung, ohne Fels ist das Relief 0
    float r0 = s.rock > 0.003 ? cliffRelief(p, footprint) - cliffNotch(p) : 0.0;
    vec3 dpx = dFdx(p), dpy = dFdy(p);
    float drx = dFdx(r0), dry = dFdy(r0);
    vec3 r1 = cross(dpy, n), r2 = cross(n, dpx);
    float det = dot(dpx, r1);
    vec3 sg = sign(det) * (drx * r1 + dry * r2);
    s.normal = abs(det) > 1e-8 ? normalize(abs(det) * n - sg * s.rock) : n;
    s.wet = 0.0;
    s.snow = 0.0;
    s.albedo = uCliffTalus;
    if (s.rock <= 0.003 && snowAmt <= 0.0) return s;

    vec3 col;
    vec3 q = p * CLIFF_ROT;
    float lehmShare = 0.0, lehmTop = 0.0;
    if (uCliffRock == 2) {
        // Lockergestein (seit Sylt Phase 4), wie Cliff.material
        vec4 st = cliffStrataAt(p.xz);
        float b25 = cliffBand(2.5, footprint);
        float wv = (cliffValue(p / 9.0, sd + 51) - 0.5) * 0.8 + (b25 > 0.0 ? (cliffValue(q / 2.5, sd + 52) - 0.5) * 0.3 * b25 : 0.0);
        float d = st.x - p.y + wv, aa = max(footprint, 0.04);
        float zN = uCliffThick.x, zF = zN + st.z, zB = zF + uCliffThick.y, zP = zB + uCliffThick.z, zS = zP + uCliffThick.w;
        float mot = 0.6 * cliffValue(q / 4.0, sd + 58) + 0.4 * cliffValue(q / 1.1, sd + 59);
        vec3 lc = mix(uCliffLehmDark, uCliffLehm, smoothstep(0.25, 0.75, mot));
        float b03 = cliffBand(0.3, footprint);
        if (b03 > 0.0) lc *= 1.0 + (cliffValue(q / 0.3, sd + 60) - 0.5) * 0.15 * b03;
        float b05 = cliffBand(0.5, footprint);
        float er = b05 > 0.0 ? smoothstep(0.84, 0.87, cliffValue(q / 0.5, sd + 53)) * uCliffLoose.y * b05 : 0.0;
        if (er > 0.0) lc = mix(lc, uCliffStone * (0.7 + 0.6 * cliffValue(q / 0.2, sd + 61)), er);
        float b12 = cliffBand(0.12, footprint);
        vec3 sc = uCliffStone * (0.6 + 0.8 * (b12 > 0.0 ? cliffValue(q / 0.12, sd + 62) : 0.0) * b12 + 0.4 * (1.0 - b12));
        float b35 = cliffBand(0.35, footprint);
        float lam = (b35 > 0.0 ? cliffValue(p / vec3(8.0, 0.35, 8.0), sd + 63) : 0.0) * b35 + 0.5 * (1.0 - b35);
        vec3 kc = mix(uCliffKaolinDark, uCliffKaolin, smoothstep(0.2, 0.8, lam));
        col = uCliffSod;
        col = mix(col, uCliffSand, smoothstep(-aa, aa, d - zN));
        col = mix(col, uCliffBleach, smoothstep(-aa, aa, d - zF));
        col = mix(col, uCliffPodsol, smoothstep(-aa, aa, d - zB));
        col = mix(col, sc, smoothstep(-aa, aa, d - zP));
        float inLehm = smoothstep(-aa, aa, d - zS);
        col = mix(col, lc, inLehm);
        float yl = p.y - st.y + wv * 0.5;
        float below = 1.0 - smoothstep(-aa, aa, yl);
        col = mix(col, uCliffIron, (1.0 - smoothstep(-aa, aa, yl - uCliffThick2.x)) * (1.0 - below) * inLehm);
        col = mix(col, kc, below);
        lehmShare = inLehm * (1.0 - below);
        lehmTop = d - zS;
        float gu = cliffGully(p) * wall;
        float tongue = smoothstep(0.7, 0.85, cliffValue(p / vec3(1.1, 25.0, 1.1), sd + 56));
        float tr = uCliffGully.w * max(gu * 0.9, tongue * 0.6) * lehmShare;
        col *= 1.0 - 0.18 * gu * lehmShare;
        col = mix(col, uCliffSand * 0.95, tr);
        float gr = smoothstep(0.55, 0.8, n.y) * (1.0 - smoothstep(zN, zN + 0.4, d));
        if (gr > 0.0) col = mix(col, uCliffGrass * (0.8 + 0.4 * cliffValue(q / 0.4, sd + 64)), gr);
    } else if (uCliffRock == 1) {
        // Granit: Flecken in zwei Maßstäben, rötlicher Feldspat und dunkler Glimmer als Korn, Wollsäcke an den Klüften
        float mo = 0.6 * cliffValue(q / 3.0, sd + 3) + 0.4 * cliffValue(q / 0.9, sd + 4);
        col = mix(uCliffDark, uCliffLight, smoothstep(0.2, 0.8, mo));
        float bf = cliffBand(0.035, footprint), bb = cliffBand(0.02, footprint);   // Korn: Feldspat 3,5 cm, Biotit 2 cm
        float fel = (bf > 0.0 ? smoothstep(0.58, 0.88, cliffValue(q / 0.035, sd + 6)) : 0.0) * uCliffAmount.w;
        col = mix(col, uCliffFeld, fel * bf + uCliffAmount.w * 0.25 * (1.0 - bf));
        float bio = bb > 0.0 ? smoothstep(0.66, 0.92, cliffValue(q / 0.02 + vec3(17.0, 0.0, 0.0), sd + 5)) : 0.0;
        col *= 1.0 - 0.6 * bio * bb - 0.06 * (1.0 - bb);
        float pill = cliffGranitBlock(p, footprint);
        col *= 1.0 - 0.45 * (1.0 - smoothstep(0.0, 0.35, pill)) * wall;
    } else {
        float b6 = cliffBand(6.0, footprint);
        float warp = (cliffValue(p / 40.0, sd + 1) - 0.5) * 14.0 + (b6 > 0.0 ? (cliffValue(p / 6.0, sd + 2) - 0.5) * 1.6 * b6 : 0.0);
        float f = dot(p, uCliffFol) + warp;
        float b = 0.45 * cliffValue1(f / 33.0, sd + 3) + 0.35 * cliffValue1(f / 7.0, sd + 4)
                + 0.20 * (cliffValue1(f / 1.6, sd + 5) - 0.5) * cliffBand(1.6, footprint) + 0.1;
        b = smoothstep(0.25, 0.75, b);
        col = mix(uCliffDark, uCliffLight, b);
        float fel = smoothstep(0.78, 0.9, cliffValue1(f / 4.5, sd + 6)) * uCliffAmount.w;
        col = mix(col, uCliffFeld, fel);
        float b07 = cliffBand(0.7, footprint);
        if (b07 > 0.0) col *= 1.0 + (cliffValue(q / 0.7, sd + 7) - 0.5) * 0.18 * b07;

        float bj = cliffBand(uCliffJoint.w * 4.0, footprint) * wall;
        if (bj > 0.0) {
            float joint = 0.0;
            for (int k = 0; k < 2; k++) {
                float a = k == 0 ? uCliffJoint.x : uCliffJoint.y;
                float u = (p.x * sin(a) - p.z * cos(a)) / uCliffJoint.z + (cliffValue(p / 25.0, sd + 8 + k) - 0.5) * 0.8;
                float toJoint = (0.5 - abs(u - floor(u) - 0.5)) * uCliffJoint.z;
                float open = smoothstep(0.35, 0.65, cliffValue(p / vec3(12.0, 30.0, 12.0), sd + 20 + k));
                joint = max(joint, (1.0 - smoothstep(uCliffJoint.w * 0.5, uCliffJoint.w * 1.5, toJoint)) * open);
            }
            col *= 1.0 - 0.55 * joint * bj;
        }
    }

    float seep = 0.0;
    if (uCliffRock == 2) seep = wall > 0.0 ? smoothstep(0.62, 0.76, cliffValue(p / vec3(2.2, 45.0, 2.2), sd + 57)) * uCliffAmount.z * wall
            * smoothstep(-0.3, 0.2, lehmTop) * (1.0 - smoothstep(1.0, uCliffThick2.w, lehmTop)) : 0.0;
    else seep = wall > 0.0 ? smoothstep(0.66, 0.78, cliffValue(p / vec3(3.5, 70.0, 3.5), sd + 9)) * uCliffAmount.z * wall : 0.0;
    s.wet = clamp(max(max(wetMap * uCliffFallWet, seep), rainWet * s.rock), 0.0, 1.0);
    if (uCliffRock == 2) {
        float bc = cliffBand(0.15, footprint) * uCliffLoose.x * lehmShare * (1.0 - s.wet);
        if (bc > 0.0) col *= 1.0 - 0.5 * (1.0 - smoothstep(0.015, 0.045, abs(cliffValue(q / 0.45, sd + 54) - 0.5))) * bc;
        col *= vec3(1.0, 1.0 - 0.15 * s.wet * lehmShare, 1.0 - 0.25 * s.wet * lehmShare);
    }
    float li = smoothstep(0.5, 0.72, cliffValue(q / 2.2, sd + 10)) * uCliffAmount.x * (1.0 - s.wet) * s.rock;
    col = mix(col, uCliffLichen, li * 0.7);
    float mw = clamp((ledge * 0.9 + s.wet * 0.45) * uCliffAmount.y * 2.0 + uCliffTopMoss * smoothstep(0.45, 0.85, n.y), 0.0, 1.0);
    if (mw > 0.0) col = mix(col, uCliffMoss, mw * smoothstep(0.4, 0.62, cliffValue(q / 3.5, sd + 12)));
    float tw = tal * (1.0 - wall);
    if (tw > 0.0) {
        float blk = 0.5 + (smoothstep(0.3, 0.7, cliffValue(q / 1.3, sd + 13)) - 0.5) * cliffBand(1.3, footprint);
        col = mix(col, uCliffTalus * (uCliffRock == 2 ? 0.92 + 0.16 * blk : 0.75 + 0.5 * blk), tw);
    }
    col *= 1.0 - 0.45 * s.wet;
    float flatv = max(ledge, 1.0 - smoothstep(0.05, 0.15, slope));
    if (snowAmt > 0.0) s.snow = clamp(snowAmt * flatv * smoothstep(0.38, 0.55, cliffValue(p / 5.0, sd + 14) + snowAmt * 0.3), 0.0, 1.0);
    col = mix(col, uCliffSnow, s.snow);
    s.albedo = col;
    return s;
}
#endif
