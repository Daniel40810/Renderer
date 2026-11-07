// lens_common.glsl — gemeinsame Teile der Shader von com.dan.lens: Tiefe, Blende, Katzenauge, Tabellen.
// Längen auf dem Sensor in mm, Bildpunkte als Vielfaches von uPitch (mm je Bildpunkt im vollen Bild).

uniform int uReversed;          // 1: umgekehrte Tiefe, unendlich fern (Tiefe = near / z)
uniform float uNear, uFar;      // m
uniform vec2 uFull;             // Größe des vollen Bilds (Bildpunkte)
uniform float uPitch;           // mm je Bildpunkt (voll)
uniform float uEfl, uPupil, uFocusFallback; // Brennweite, Pupille (mm), Fokus (mm), falls kein Zustand
uniform float uAreaRoot;        // 1 / √(Fläche/π) der Blende: Umkreis gleicher Fläche
uniform int uBlades;
uniform float uRound, uRot;     // Rundung 0..1, Drehung (rad)
uniform float uSqueeze;         // anamorphotisch (1 = sphärisch)
uniform vec4 uCat;              // Katzenauge: (ρ1, σ1, ρ2, σ2), Pupillenmaß, σ je rad Feldwinkel
uniform float uSAProf[256];     // Helligkeit im Unschärfekreis: 16 Zeilen (8 Unschärfen vor, 8 hinter dem Fokus) × 16 Ringe
uniform float uSAScale;         // größte Querabweichung der Randstrahlen (mm); 0 = aus
uniform vec2 uDz;               // Längsfarbfehler Rot, Blau (mm Schnittweite gegen Grün)
uniform float uFNumber;
uniform float uField;           // Stauchung des Feldwinkels für Weitwinkel (Lens.fieldScale), 1 bis ±26°

const float PI = 3.14159265;

// Tiefe aus dem Tiefenpuffer → Abstand längs der Achse (m)
float viewDepth(float d) {
    if (uReversed == 1) return uNear / max(d, 1e-12);
    float ndc = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / ((uFar + uNear) - ndc * (uFar - uNear));
}

// Unschärfekreis (Durchmesser, mm, + hinter dem Fokus) für den Abstand z (m) bei Fokus s (mm)
float cocMm(float zM, float s) {
    float d = zM * 1000.0, f = uEfl;
    if (s <= f * 1.01) return 0.0;
    d = max(d, f * 1.01);
    return uPupil * f * (d - s) / (d * (s - f));
}

// Rand der Blende in Richtung φ (Umkreis 1)
float apRadius(float phi) {
    if (uBlades < 3) return 1.0;
    float n = float(uBlades), seg = 2.0 * PI / n;
    float a = phi - uRot;
    a = a - seg * floor(a / seg);
    float rn = cos(PI / n) / cos(a - PI / n);
    return rn + uRound * (1.0 - rn);
}

// Deckung (0..1) der Pupille am Punkt q (Pupillenmaß: Kreis gleicher Fläche hat den Halbmesser 1), weicher Rand w;
// mit den Kreisen der Fassungen für den Feldwinkel th in Richtung er (Katzenauge)
float pupil(vec2 q, float w, float th, vec2 er) {
    q.y /= uSqueeze;
    vec2 p = q / uAreaRoot;                 // Umkreis 1
    float r = length(p);
    float m = clamp((apRadius(atan(p.y, p.x)) - r) / (w / uAreaRoot) + 0.5, 0.0, 1.0);
    if (uCat.x < 50.0) m *= clamp((uCat.x - length(q - uCat.y * th * er)) / w + 0.5, 0.0, 1.0);
    if (uCat.z < 50.0) m *= clamp((uCat.z - length(q - uCat.w * th * er)) / w + 0.5, 0.0, 1.0);
    return m;
}

// Helligkeit im Unschärfekreis aus der sphärischen Aberration bei s = r/R (0..1) für den Kreis c (mm, mit Vorzeichen):
// aus der Tabelle von Lens.optics (Landepunkte L(ρ) = αρ + TA(ρ) echter Randstrahlen, α = −c/2), Zeile m ↔ |α| = 2^(m−2)·TA_max;
// jenseits des 32-Fachen gleichmäßig (wie Lens.Optics.saWeight)
float saWeight(float s, float cMm) {
    if (uSAScale <= 0.0) return 1.0;
    float m = log2(max(0.5 * abs(cMm) / uSAScale, 1e-4)) + 2.0;
    float far = 1.0 - smoothstep(7.0, 8.0, m);
    m = clamp(m, 0.0, 7.0);
    int base = cMm < 0.0 ? 0 : 8;
    int m0 = int(floor(m)), m1 = min(m0 + 1, 7);
    float fm = m - float(m0);
    float x = clamp(s, 0.0, 1.0) * 16.0 - 0.5;
    int b0 = int(clamp(floor(x), 0.0, 15.0)), b1 = min(b0 + 1, 15);
    float fb = clamp(x - float(b0), 0.0, 1.0);
    float v0 = mix(uSAProf[(base + m0) * 16 + b0], uSAProf[(base + m0) * 16 + b1], fb);
    float v1 = mix(uSAProf[(base + m1) * 16 + b0], uSAProf[(base + m1) * 16 + b1], fb);
    return mix(1.0, mix(v0, v1, fm), far);
}

// Tabelle mit 17 Werten über den Bildradius 0..1 (Halbdiagonale)
float table17(float t[17], float r) {
    float x = clamp(r, 0.0, 1.0) * 16.0;
    int i = int(min(floor(x), 15.0));
    return mix(t[i], t[i + 1], x - float(i));
}
