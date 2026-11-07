// horizon.glsl — com.dan.horizon (LituyaBay1958, Werkbuch Welle W1): Bausteine für die Shader eines Wirts.
//   horizonAirmass, horizonTransmittance: Sonne am Punkt (Luftmasse nach Kasten und Young, unter der Waagerechten
//     streifend nach Chapman; Rayleigh 8 km, Dunst 1,2 km) — wie Airmass im Kern.
//   horizonSun: Bergschatten aus den beiden Horizontkarten (HorizonGl.bind setzt die Uniforms).
//   horizonAerialDepth: optische Dicke der Luftperspektive in zwei Schichten — wie Aerial im Kern.
//   horizonPanoUV, horizonHaze, horizonSkyLight: Dunstfarbe und Himmelslicht aus einem Rundumbild des Himmels
//     (gleichwinklig, Azimut über u, Höhe über v, mit Mip-Stufen; etwa aus com.dan.sky drawPanorama).
// Kein Uniform und keine Funktion des Wirts wird hier vorausgesetzt.
uniform int uHzOn;
uniform sampler2D uHzFine, uHzCoarse;
uniform vec4 uHzFineRect, uHzCoarseRect;   // x0, z0, Kante
// halber Winkel der Sonnenscheibe (rad) und Entfernung des Hindernisses für den Hub über dem Boden (m); ein Wirt kann sie
// vor dem Einbinden anders setzen (dann auch Horizon.penumbra, liftDistance)
#ifndef HZ_PENUMBRA
#define HZ_PENUMBRA 0.0046
#endif
#ifndef HZ_LIFT
#define HZ_LIFT 2000.0
#endif

// Luftmasse einer Schicht mit Skalenhöhe H an der Höhe h bei der Sonnenhöhe mit Sinus sinEl.
float horizonAirmass(float h, float sinEl, float H) {
    float el = asin(clamp(sinEl, -1.0, 1.0));
    float R = 6371000.0;
    if (el >= 0.0) {
        float e = degrees(el);
        return exp(-h / H) / (sin(el) + 0.50572 * pow(e + 6.07995, -1.6364));
    }
    float ht = (R + h) * cos(el) - R;                       // Tangentenhöhe
    float voll = sqrt(6.2831853 * R / H);
    return max(voll * exp(-max(ht, -200.0) / H) - 0.5 * voll * exp(-h / H), 0.5 * voll * exp(-h / H));
}

// Durchlässigkeit der Luft für die Sonne an einem Punkt der Höhe h (m): auf 4 000 m und 1° unter der Kimm rot, rund ein
// Zehntel der Sonne.
vec3 horizonTransmittance(float h, float sinEl) {
    vec3 tauR = vec3(0.046, 0.108, 0.265);
    float tauA = 0.10;
    vec3 od = tauR * horizonAirmass(h, sinEl, 8000.0) + vec3(tauA * horizonAirmass(h, sinEl, 1200.0));
    return exp(-od);
}

// Sonne ja/nein (0..1) am Punkt w aus den Horizontkarten: Horizontwinkel gegen Sonnenhöhe, Halbschatten der Scheibe;
// ein Punkt über dem Boden (Welle, Staub, Baumkrone) sieht etwas mehr. Außerhalb beider Karten: 1.
float horizonSun(vec3 w, float sinEl) {
    if (uHzOn == 0) return 1.0;
    vec2 hz;
    vec2 uf = (w.xz - uHzFineRect.xy) / uHzFineRect.z;
    if (all(greaterThan(uf, vec2(0.0))) && all(lessThan(uf, vec2(1.0)))) hz = textureLod(uHzFine, uf, 0.0).rg;
    else {
        vec2 ug = (w.xz - uHzCoarseRect.xy) / uHzCoarseRect.z;
        if (all(greaterThan(ug, vec2(0.0))) && all(lessThan(ug, vec2(1.0)))) hz = textureLod(uHzCoarse, ug, 0.0).rg;
        else return 1.0;
    }
    float el = asin(clamp(sinEl, -1.0, 1.0));
    float lift = max(w.y - hz.y, 0.0) / HZ_LIFT;
    return smoothstep(-HZ_PENUMBRA, HZ_PENUMBRA, el - hz.x + lift);
}

// Optische Dicke einer Strecke dist (m) in der mittleren Höhe hm (m): Luft 8 km / 150 km, Dunst 1,2 km mit 0,45 und der
// Länge fogDist (m, am Boden).
float horizonAerialDepth(float dist, float hm, float fogDist) {
    return dist * (exp(-hm / 8000.0) / 150000.0 + 0.45 * exp(-hm / 1200.0) / fogDist);
}

// Ort im Rundumbild für die Richtung d (Szene: x Ost, y oben, z Süd; Azimut von Nord).
vec2 horizonPanoUV(vec3 d) {
    float az = atan(d.x, -d.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(d.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
    return vec2(az, el);
}

// Farbe des Dunsts in Richtung dir: das Rundumbild 3 % über der Kimm in Mip-Stufe 4 (rund 11° je Texel; feiner zeichneten
// Wolken an der Kimm senkrechte Streifen ins Gelände), in der Helligkeit auf das Dreifache des Himmelslichts skyAmb
// begrenzt (die Sonnenscheibe fällt nicht als heller Fleck aufs Gelände).
vec3 horizonHaze(sampler2D pano, vec3 dir, vec3 skyAmb) {
    vec2 h = normalize(dir.xz + vec2(1e-5));
    float az = atan(h.x, -h.y) / 6.28318530718 + 0.5;
    float el = asin(0.03) / 3.14159265359 + 0.5;
    vec3 c = textureLod(pano, vec2(az, el), 4.0).rgb;
    float cap = 3.0 * dot(skyAmb, vec3(0.2126, 0.7152, 0.0722)) + 1e-6, l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return l > cap ? c * (cap / l) : c;
}

// Himmelslicht auf eine Fläche mit Normale n (W3c in Lituya): das Rundumbild stark gemittelt (Mip-Stufe 6, rund 45°) in
// Richtung der Normale (etwas angehoben). Hänge zum hellen Himmel bekommen das warme Licht der Kimm, Hänge zur
// Erdschattenseite das kühle; dazu Licht vom Boden zurück [G]. zenith: Himmel im Zenit, skyAmb: Himmelslicht (Bestrahlung/π).
vec3 horizonSkyLight(sampler2D pano, vec3 n, vec3 zenith, vec3 skyAmb) {
    float sicht = 0.55 + 0.45 * n.y;
    vec3 dd = normalize(vec3(n.x, max(n.y, 0.0) + 0.35, n.z));
    float az = atan(dd.x, -dd.z) / 6.28318530718 + 0.5;
    float el = asin(clamp(dd.y, -1.0, 1.0)) / 3.14159265359 + 0.5;
    vec3 c = textureLod(pano, vec2(az, el), 6.0).rgb;
    // in der Helligkeit an das Mittel gebunden (die Szene bleibt gleich hell, nur die Farbe folgt der Richtung)
    float lz = dot(zenith, vec3(0.2126, 0.7152, 0.0722)) + 1e-6, lc = dot(c, vec3(0.2126, 0.7152, 0.0722)) + 1e-6;
    c *= clamp(lz / lc, 0.6, 1.6);
    vec3 boden = skyAmb * vec3(1.05, 0.95, 0.8) * 0.35;   // Licht vom Boden (warm, schwach)
    return mix(zenith, c, 0.75) * sicht + boden * (1.0 - sicht);
}
