// katzen.glsl – render.water (Seegang Werkbuch, Datei 104, S8b, SF156 A): Katzenpfoten, Zeile für Zeile wie
// core.szene.Katzenpfoten. Der Wind am Ort ist der Wind der Szene mal einem log-normalen Böenfeld (drei Oktaven
// Wertrauschen in Ort und Zeit, Gitter periodisch mit 256 Zellen); unter 1,5 m/s am Ort keine Kräusel (Kahma und Donelan
// 1988, seit L6a), bis 4 m/s wachsen sie zu Cox und Munk beim Wind am Ort. Liest boe und boeMehr aus dem Kopf (wasser.glsl):
// boe = Gitterkoordinaten des Auges (qx, qy, τ; mod 256) und σ (ln; 0 aus), boeMehr = Wind der Szene U (m/s), 1 / Fleck
// (1/m), Gewicht des Felds (1 bis 4 m/s Wind der Szene, 0 ab 6 m/s), 0.
const float K_MITTEL = 0.43753, K_STREUUNG = 0.10597, K_U_EIN = 1.5, K_U_VOLL = 4.0;

float kHash(ivec3 q, int o) {
    uint v = uint(q.x) * 1597334677u ^ uint(q.y) * 3812015801u ^ uint(q.z) * 2654435761u ^ uint(o + 1) * 0x27d4eb2du;
    v ^= v >> 16;
    v *= 0x7feb352du;
    v ^= v >> 15;
    v *= 0x846ca68bu;
    v ^= v >> 16;
    return float(v >> 8u) / 16777216.0;
}

// Wertrauschen in [0, 1), dreilinear mit weicher Kurve; Gitter periodisch mit p (Zweierpotenz), Oktave o im Hash
float kRauschen(vec3 x, int p, int o) {
    vec3 f = floor(x);
    ivec3 i = ivec3(f);
    vec3 u = x - f;
    u = u * u * (3.0 - 2.0 * u);
    float r = 0.0;
    for (int c = 0; c < 8; c++) {
        ivec3 d = ivec3(c & 1, (c >> 1) & 1, (c >> 2) & 1);
        vec3 w = mix(1.0 - u, u, vec3(d));
        r += w.x * w.y * w.z * kHash((i + d) & ivec3(p - 1), o);
    }
    return r;
}

// Wind am Ort p (x Ost, z Süd relativ zum Auge, m)
float katzenWind(vec2 p) {
    if (boe.w <= 0.0) return boeMehr.x;
    vec3 q = vec3(boe.x + p.x * boeMehr.y, boe.y - p.y * boeMehr.y, boe.z);
    float s = 0.0, a = 0.5;
    int f = 1;
    for (int o = 0; o < 3; o++) {
        s += a * kRauschen(q * float(f), 256 * f, o);
        a *= 0.5;
        f *= 2;
    }
    float z = (s - K_MITTEL) / K_STREUUNG;
    return boeMehr.x * exp(boe.w * z - 0.5 * boe.w * boe.w);
}

// Wirksame Neigungsvarianz der Kräusel am Ort p; uOrt: der Wind am Ort
float katzenSigma2(vec2 p, out float uOrt) {
    uOrt = katzenWind(p);
    float cmU = 0.003 + 5.12e-3 * boeMehr.x;
    float ort = smoothstep(K_U_EIN, K_U_VOLL, uOrt) * (0.003 + 5.12e-3 * uOrt);
    return mix(cmU, ort, boeMehr.z);
}
