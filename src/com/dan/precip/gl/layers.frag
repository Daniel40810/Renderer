// layers.frag — der Regen zwischen dem Kasten und 60 m (und die kleinen Tropfen unter dmin auch im Kasten): Schalen um
// das Auge, auf jeder ein Gitter aus Zellen, in jeder Zelle ein Tropfen mit Größe aus der Verteilung, der mit seiner
// Fallgeschwindigkeit fällt und mit dem Wind zieht. Die Zellen sind so groß, dass auf eine Zelle im Mittel genau ein
// Tropfen der Schicht (Dichte · Dicke) kommt; jeder Tropfen deckt nach derselben Regel wie drop.frag (Fläche gleich bei
// jeder Belichtung). Wo auf einen Bildpunkt viele Tropfen kommen, wird daraus ihr Mittel (Auslöschung n⟨πD²/4⟩).
// Je Schale ein Gitter mit der ganzen Verteilung (die Spur jedes Tropfens nach seiner Größe). Unter dem Kronendach:
// der freie Durchfall und die großen Tropfen vom Dach.
// Ausgabe vormultipliziert über die Szene.
#include "precip_common.glsl"
in vec2 vUv;
out vec4 frag;
uniform mat4 uInvVP;
uniform vec3 uEye;
uniform sampler2D uSceneDepth;
uniform int uReversed, uHasDepth;
uniform float uPixel;
uniform int uLayers;
uniform float uStart, uEnd;      // m: erste und letzte Schale
uniform float uStrength;
uniform float uPSunVis;
uniform float uPFogDist;
uniform vec3 uPFogColor;

float overlap(float a0, float a1, float b0, float b1) { return max(0.0, min(a1, b1) - max(a0, b0)); }

// Deckung eines Tropfens D (mm), der in (du, dv) vom Bildpunkt liegt und die Spur L (m) nach oben zieht (mit Neigung k), pw Bildpunkt
float cover(vec2 rel, float d, float L, float k, float pw) {
    float r = d * 0.5e-3, pwh = 0.5 * pw;
    // in die Spur drehen: sie zieht sich gegen die Bewegung (−k, 1) nach oben
    vec2 ax = normalize(vec2(-k, 1.0));
    float along = dot(rel, ax), lat = dot(rel, vec2(ax.y, -ax.x));
    float wh = max(r, 0.75 * pw);
    float latW = r > pwh ? clamp((r + 0.25 * pw - abs(lat)) / pwh, 0.0, 1.0) : r / wh * 2.0 * max(0.0, 1.0 - abs(lat) / wh);
    float c = r > pwh ? r * sqrt(max(0.0, 1.0 - (lat / r) * (lat / r))) : 0.785398 * r;
    float ce = max(c, pwh), Le = max(L, 1e-6);
    return overlap(along - ce, along + ce, 0.0, Le) / Le * (c / ce) * latW;
}

float sceneDist(vec3 dir) {
    if (uHasDepth == 0) return 1e9;
    float d = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    if ((uReversed == 1 && d <= 0.0) || (uReversed == 0 && d >= 1.0)) return 1e9;
    vec4 w = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? d : d * 2.0 - 1.0, 1.0);
    return length(w.xyz / w.w - uEye);
}

// Ein Gitter der Schale: n Tropfen je m³ (Größen d0..d1, mittlere Fallgeschwindigkeit vt, mittleres D² m2), Schale
// von sa (Dicke ds, sichtbar bis tEnd). Die Zellen sind aus der vollen Dicke so groß, dass je Zelle ein Tropfen kommt;
// jeder Tropfen liegt irgendwo in der Dicke (hinter der Szene: verdeckt). Das Gitter fällt mit vt; den Wind quer zum
// Blick zeigt die Neigung jeder Spur (ein seitlich ziehendes Gitter wäre in benachbarten Bildpunkten verschieden).
struct Lat { float n, d0, d1, vt, m2; };

float lattice(vec2 uv, float sa, float ds, float tEnd, Lat q, float lam, uint salt, float wPerp, float wMax, float pw) {
    if (q.n <= 0.0 || q.d1 <= q.d0) return 0.0;
    float vis = clamp((tEnd - sa) / ds, 0.0, 1.0);
    float mean = q.n * ds * vis * 0.785398 * q.m2 * 1e-6;
    float many = q.n * ds * pw * pw;   // Tropfen je Bildpunkt
    float wD = 1.0 - smoothstep(0.4, 3.0, many);
    if (wD <= 0.0) return mean;
    float T = uPB.w;
    float area = 1.0 / max(1e-9, q.n * ds);
    float cv = max(sqrt(area), length(vec2(wMax, q.vt)) * T * 1.2 + 0.01), cu = area / cv;
    vec2 g = vec2(uv.x, uv.y + q.vt * mod(uPB.y, 600.0)) / vec2(cu, cv);
    vec2 cell = floor(g);
    float c = 0.0;
    for (int j = 0; j <= 1; j++) {
        vec2 cc = cell - vec2(0.0, float(j));
        uvec2 h = uvec2(ivec2(cc) + 1048576);
        uint id = pHash(h.x * 73856093u ^ h.y * 19349663u ^ salt);
        float ru = float(id >> 8u) / 16777216.0;
        uint id2 = pHash(id);
        float rv = float(id2 >> 8u) / 16777216.0, rd = float(id2 & 0xffffu) / 65536.0, rz = float((id >> 4u) & 0xfffu) / 4096.0;
        if (sa + rz * ds > tEnd) continue;   // hinter der Szene
        float d = pSample(lam, q.d0, q.d1, rd);
        float vt = q.vt * sqrt(d / max(0.3, 0.5 * (q.d0 + q.d1)));   // eigene Spurlänge (grob nach der Größe)
        float L = length(vec2(wPerp, vt)) * T, k = wPerp / max(0.3, vt);
        vec2 drop = (cc + vec2(ru, rv)) * vec2(cu, cv);
        vec2 rel = vec2(g.x * cu - drop.x, g.y * cv - drop.y);
        c += cover(rel, d, L, k, pw);
    }
    return c * wD + mean * (1.0 - wD);
}

// mittleres D² (mm²) der Verteilung zwischen d0 und d1
float meanD2(float lam, float d0, float d1) {
    float e0 = exp(-lam * d0), e1 = exp(-lam * d1);
    return ((d0 * d0 + 2.0 * d0 / lam + 2.0 / (lam * lam)) * e0 - (d1 * d1 + 2.0 * d1 / lam + 2.0 / (lam * lam)) * e1) / max(1e-12, e0 - e1);
}

Lat makeLat(float n, float lam, float d0, float d1) {
    Lat q;
    q.n = n; q.d0 = d0; q.d1 = d1;
    q.vt = pTerminal(pSample(lam, d0, d1, 0.5));
    q.m2 = meanD2(lam, d0, d1);
    return q;
}

void main() {
    if (uPD.x <= 0.0) discard;
    vec4 p0 = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? 1.0 : -1.0, 1.0);
    vec4 p1 = uInvVP * vec4(vUv * 2.0 - 1.0, uReversed == 1 ? 0.0001 : 1.0, 1.0);
    vec3 V = normalize(p1.xyz / p1.w - p0.xyz / p0.w);
    float horiz = length(V.xz);
    if (horiz < 0.15) discard;   // steil nach oben oder unten: Schalen um das Auge passen nicht [G]
    float tEnd = min(sceneDist(V), uEnd);
    if (V.y > 1e-4) tEnd = min(tEnd, (uPE.w - uEye.y) / V.y);
    if (tEnd <= uStart) discard;
    // Regen am Ort einmal je Bildpunkt (die Schauer ändern sich über Kilometer, nicht über 60 m)
    float loc = uPD.x * pCurtain(uEye.xz + V.xz * min(tEnd, 20.0));
    if (loc <= 0.001) discard;
    float lam = pLambda(loc), n0 = pN0(loc);
    // zwei Gitter: im Kasten nur die kleinen unter dmin, draußen alle
    Lat boxL = makeLat(pNumber(n0, lam, 0.3, uPD.w), lam, 0.3, uPD.w), allL = makeLat(pNumber(n0, lam, 0.3, uPE.x), lam, 0.3, uPE.x);
    vec3 L = pDropRadiance(V, uPSunVis);
    float az = atan(V.x, -V.z);
    float ratio = uEnd / uStart;
    float T = 1.0;
    vec3 acc = vec3(0.0);
    vec2 wTot = uPC.xy + uPC.zw;
    float wPerpBase = dot(wTot, vec2(-V.z, V.x) / horiz), wMax = length(wTot) + uPE.y;   // Wind quer zum Blick
    for (int i = 0; i < 16; i++) {
        if (i >= uLayers) break;
        float s = uStart * pow(ratio, (float(i) + 0.5) / float(uLayers));
        float sa = uStart * pow(ratio, float(i) / float(uLayers)), sb = uStart * pow(ratio, float(i + 1) / float(uLayers));
        if (sa >= tEnd) break;
        float ds = sb - sa;
        vec3 P = uEye + V * s;
        if (P.y > uPE.w || pSheltered(P)) continue;
        vec2 uv = vec2(az * s * horiz, P.y);
        float pw = pFootprint(uPixel * s, s);
        // im Kasten zeichnet drops die großen; hier nur die kleinen
        vec3 rb = abs(P - uPA.xyz);
        bool inBox = max(rb.x, rb.z) < uPA.w * 0.9 && rb.y < uPB.x * 0.9;
        vec4 gr = pGround(P.xz);
        bool roof = gr.b > P.y;
        float cov = 0.0;
        uint sA = uPSeed ^ uint(i * 2 + 1);
        if (roof) {
            // unter dem Kronendach: freier Durchfall (Anteil) und große Tropfen vom Dach, kaum Wind
            Lat a = inBox ? boxL : allL;
            a.n *= uPG.x;
            cov += lattice(uv, sa, ds, tEnd, a, lam, sA, wPerpBase * 0.2, wMax * 0.2, pw);
            if (!inBox) {
                Lat dr;
                dr.n = pDrip(loc) * uPBox.x / uPBox.y; dr.d0 = uPG.z; dr.d1 = uPG.w;
                dr.vt = pTerminal(0.5 * (uPG.z + uPG.w)); dr.m2 = 0.5 * (uPG.z * uPG.z + uPG.w * uPG.w);
                cov += lattice(uv, sa, ds, tEnd, dr, 0.01, sA ^ 0x51u, wPerpBase * 0.2, wMax * 0.2, pw);
            }
        } else {
            cov += lattice(uv, sa, ds, tEnd, inBox ? boxL : allL, lam, sA, wPerpBase, wMax, pw);
        }
        float a = clamp(cov, 0.0, 1.0) * uStrength;
        float fd = 1.0 - exp(-s / max(1.0, uPFogDist));
        acc += T * a * mix(L, uPFogColor, fd);
        T *= 1.0 - a;
        if (T < 0.01) break;
    }
    float alpha = 1.0 - T;
    if (alpha < 1e-5) discard;
    frag = vec4(acc, alpha);
}
