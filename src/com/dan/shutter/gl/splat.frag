// splat.frag — Kino-Stufe (com.dan.shutter), seit W9c Runde 2: der Unschärfekreis eines Lichts. Für den Bildpunkt im Abstand
// o von der Mitte: welche Orte der Pupille landen hier? Entzerrt (o.y / Stauchung) ist |X(ρ)| = |o|, die Richtung von p die
// von ±o (Vorzeichen von X). X stückweise linear über 16 Zonen, beide Vorzeichen; jeder Ort, der in der Blende (Lamellen,
// Rundung, Drehung) und im Katzenauge liegt, trägt E · ρ / (π q |X| |X'|) bei (Fluss der Pupille, Fläche π q, auf die Fläche
// im Bild). Ohne Aberration ist das E / (π q S²) im Kreis, mit ihr der Ring vor oder hinter der Schärfe und die Kaustik.
// Gewicht des Zeitfensters an diesem Bildpunkt (window.glsl); Gewicht a = 0 (das Teilbild hat hier schon gezählt).
in vec2 vOff;
flat in vec3 vCol;
flat in float vSc;
flat in vec2 vCenter;
out vec4 frag;
uniform float uTA[17];
uniform float uRhoTop;
uniform float uSqueezeP;
uniform int uBlades;
uniform float uRound, uRot, uScaleP;   // Rundung, Drehung (rad), Umkreis → Fläche π

float apRadius(float phi) {
    if (uBlades < 3) return 1.0;
    float seg = 6.2831853 / float(uBlades);
    float a = phi - uRot;
    a = a - seg * floor(a / seg);
    float rn = cos(3.14159265 / float(uBlades)) / cos(a - 3.14159265 / float(uBlades));
    return rn + uRound * (1.0 - rn);
}

bool catEyeP(vec2 n, vec2 q) {   // q: Pupille ohne Stauchung
    if (uCat.x >= 50.0 && uCat.z >= 50.0) return true;
    vec2 d = n * uSize;
    float r = length(d);
    vec2 er = r > 0.5 ? d / r : vec2(1.0, 0.0);
    float th = atan(r * uTanPerPx);
    if (uCat.x < 50.0 && length(q - uCat.y * th * er) > uCat.x) return false;
    if (uCat.z < 50.0 && length(q - uCat.w * th * er) > uCat.z) return false;
    return true;
}

void main() {
    vec2 px = vCenter + vOff;
    vec2 n = px / uSize - 0.5;
    float w = windowWeight(n);
    if (w <= 0.0) discard;
    float q = max(uSqueezeP, 1e-3);
    vec2 o = vec2(vOff.x, vOff.y / q);
    float R = length(o);
    vec2 dir = R > 1e-4 ? o / R : vec2(1.0, 0.0);
    vec2 nc = vCenter / uSize - 0.5;
    float dr = uRhoTop / 16.0, I = 0.0;
    for (int k = 0; k < 16; k++) {
        float r0 = float(k) * dr, r1 = r0 + dr;
        float x0 = vSc * r0 - uTA[k], x1 = vSc * r1 - uTA[k + 1];
        float dx = x1 - x0;
        for (int sg = 0; sg < 2; sg++) {
            float t = sg == 0 ? R : -R;
            if ((t - x0) * (t - x1) > 0.0 || abs(dx) < 1e-6) continue;
            float rho = r0 + (t - x0) / dx * dr;
            vec2 pd = sg == 0 ? dir : -dir;   // Richtung des Pupillenorts (entzerrt)
            float phi = atan(pd.y, pd.x);
            if (rho > uScaleP * apRadius(phi) + 1e-4) continue;
            if (!catEyeP(nc, pd * rho)) continue;
            float slope = max(abs(dx / dr), 0.02 * abs(vSc) + 0.05);
            I += (R > 1e-3 ? rho / R : dr / max(abs(dx), 1e-6)) / slope;
        }
    }
    if (I <= 0.0) discard;
    frag = vec4(vCol * (w * I / (3.14159265 * q)), 0.0);
}
