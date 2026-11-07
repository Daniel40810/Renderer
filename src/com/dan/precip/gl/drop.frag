// drop.frag — Licht eines fallenden Tropfens, ehrlich belichtet. Ein Punkt der Spur ist nur so lange bedeckt, wie der
// Tropfen über ihn zieht: Deckung = (Überlapp von [x − c, x + c] mit [0, L]) / L mit der halben Sehne c an der Stelle
// quer zur Spur; über die ganze Spur ergibt das genau die Fläche des Tropfens, gleich bei welcher Belichtung. Kleiner
// als ein Bildpunkt verteilt er sich auf ihn (dieselbe Fläche).
// Farbe: Ein Tropfen ist eine Linse. Fern (kleiner als ein paar Bildpunkte) zeigt er im Mittel ∫ L p dΩ (Umgebung durch
// die Phasenfunktion aus DropOptics, dazu Sonne und Mond); nah sieht man das Bild in ihm: die Welt dahinter auf dem
// Kopf (oben der dunkle Grund, unten der Himmel), am Rand die Spiegelung der Umgebung vor ihm, die Glanzpunkte der Sonne.
// Ausgabe vormultipliziert (ONE, ONE_MINUS_SRC_ALPHA), HDR, vorbelichtet.
#include "precip_common.glsl"
in vec2 vQ;
flat in vec4 vGeo;
flat in vec3 vAxis, vRight, vStart;
flat in float vFade;
in vec3 vWorld;
out vec4 frag;
uniform vec3 uEye;
uniform float uStrength;
uniform sampler2D uPPano;       // Rundumbild (wie pPanoUv), optional
uniform int uPHasPano;
uniform vec3 uPSkyTop, uPSkyHorizon, uPGroundL;   // ohne Rundumbild: Himmel oben, am Horizont; Grund (Strahldichte)
uniform float uPSunVis;         // Sonne am Auge durch die Wolken (0..1)
uniform float uPFogDist;
uniform vec3 uPFogColor;

const float N_WATER = 1.333;

vec3 envAt(vec3 d) {
    if (d.y < 0.0) return mix(uPGroundL, uPSkyHorizon, exp(d.y * 12.0) * 0.35);
    if (uPHasPano == 1) return textureLod(uPPano, pPanoUv(d), 1.0).rgb;
    return mix(uPSkyHorizon, uPSkyTop, sqrt(clamp(d.y, 0.0, 1.0)));
}

float overlap(float a0, float a1, float b0, float b1) { return max(0.0, min(a1, b1) - max(a0, b0)); }

// Bild in der Linse am Scheibenpunkt (eta quer, xi längs, je in Halbachsen), Blick V, Achsen der Scheibe
vec3 lens(float eta, float xi, vec3 V) {
    float rho2 = eta * eta + xi * xi;
    if (rho2 >= 1.0) return vec3(0.0);
    float rho = sqrt(rho2);
    vec3 o = rho > 1e-5 ? normalize(vRight * eta + vAxis * xi) : vRight;
    float ti = asin(rho), tt = asin(rho / N_WATER), ci = cos(ti), ct = cos(tt);
    float rs = (ci - N_WATER * ct) / (ci + N_WATER * ct), rp = (N_WATER * ci - ct) / (N_WATER * ci + ct);
    float R = 0.5 * (rs * rs + rp * rp), T = 0.5 * ((1.0 - rs * rs) * (1.0 - rs * rs) + (1.0 - rp * rp) * (1.0 - rp * rp));
    float dev = 2.0 * (ti - tt);
    vec3 outD = normalize(V * cos(dev) - o * sin(dev));
    vec3 nrm = normalize(o * rho - V * sqrt(1.0 - rho2));
    vec3 refl = reflect(V, nrm);
    vec3 c = T * envAt(outD) + R * envAt(refl);
    // Glanzpunkte der Sonne (Spiegelung vorn, Durchgang von hinten), auf 1,5° verschmiert [G]
    float s2 = 2.0 * 0.026 * 0.026;
    float gr = 1.0 - dot(refl, uPSunDir), gt = 1.0 - dot(outD, uPSunDir);
    c += uPSunE * uPSunVis * (R * exp(-2.0 * gr / s2) + T * exp(-2.0 * gt / s2)) / (3.14159 * s2);
    return c;
}

void main() {
    float ah = vGeo.x, av = vGeo.y, L = vGeo.z, pw = vGeo.w, pwh = 0.5 * pw;
    float y = vQ.x, x = vQ.y;
    float c, lat;
    if (ah > pwh) {
        float e = clamp(abs(y) / ah, 0.0, 1.0);
        c = av * sqrt(max(0.0, 1.0 - e * e));
        lat = clamp((ah + pwh * 0.5 - abs(y)) / pwh, 0.0, 1.0);
    } else {
        float wh = max(ah, 0.75 * pw);
        c = 0.785398 * av;
        lat = ah / wh * 2.0 * max(0.0, 1.0 - abs(y) / wh);
    }
    float ce = max(c, pwh), Le = max(L, 1e-6);
    float f = overlap(x - ce, x + ce, 0.0, Le) / Le * (c / ce);
    float a = f * lat * vFade * uStrength;
    if (a < 1e-4) discard;
    vec3 V = normalize(vWorld - uEye);
    vec3 col = pDropRadiance(V, uPSunVis);
    float res = smoothstep(1.0, 3.0, ah / max(pw, 1e-6));
    if (res > 0.0) {
        // die Linse: über die Lagen der Scheibe gemittelt, die diesen Punkt in der Belichtung bedecken
        float x0 = max(x - c, 0.0), x1 = min(x + c, L);
        vec3 lc = vec3(0.0);
        for (int k = 0; k < 3; k++) {
            float cen = L > 1e-6 ? mix(x0, x1, (float(k) + 0.5) / 3.0) : 0.0;
            float xi = (x - cen) / av, eta = y / ah;
            lc += lens(eta, xi, V);
        }
        col = mix(col, lc / 3.0, res);
    }
    float fd = 1.0 - exp(-length(vWorld - uEye) / max(1.0, uPFogDist));
    col = mix(col, uPFogColor, fd);
    frag = vec4(col * a, a);
}
