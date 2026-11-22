// bauschatten.glsl – core.gpu (seit P12a, Werkbuch HalligHooge Teil A, 12.13): der Schatten der Körper (Häuser) für
// alle, die ihn empfangen (Gelände, Wasser, Häuser). render.bau legt ihn unter „Bau/Schatten“ (Tiefe, Atlas aus
// KASKADEN Karten nebeneinander) und „Bau/Schattenwerte“ ab; die Module kennen einander nicht, darum steht das Lesen hier.
// Vor dem Einbinden setzen: BAU_SCHATTEN_BILD und BAU_SCHATTEN_WERTE (Bindungen in Satz 0).
//
// Werte: m[k] bildet eine Lage relativ zum Auge (x Ost, y oben, z Süd, mit Erdkrümmung wie das Bild) auf den Raum der
// Karte k ab: xy in −1 … 1, z Tiefe 0 (bei der Quelle) … 1. info: x Zahl der Karten (0: kein Schatten, dann ist der
// Faktor genau 1), y Quelle (0 Sonne, 1 Mond), z Versatz längs der Normale (Texel), w Grundabstand der Tiefe (Texel);
// texel: Kantenlänge eines Texels (m) je Karte, w Texel je Karte (N); tiefe: Tiefe je m je Karte; licht: Richtung zur Quelle.
#define BAU_KASKADEN 3
layout(set = 0, binding = BAU_SCHATTEN_BILD) uniform sampler2D bauSchattenT;
layout(set = 0, binding = BAU_SCHATTEN_WERTE, std430) readonly buffer BauSchattenWerte {
    mat4 bauM[BAU_KASKADEN];
    vec4 bauInfo;
    vec4 bauTexel;
    vec4 bauTiefe;      // Tiefe je m je Karte (1 / (2 D))
    vec4 bauLicht;      // Richtung zur Quelle (x Ost, y oben, z Süd), 0
};

// Licht der Quelle am Ort (0 … 1), 3 × 3 Proben; außerhalb aller Karten 1. n: Normale der Fläche (für den Versatz).
float bauSchatten(vec3 rel, vec3 n) {
    if (bauInfo.x < 0.5) return 1.0;
    int N = int(bauTexel.w);
    for (int k = 0; k < BAU_KASKADEN; k++) {
        if (float(k) >= bauInfo.x) break;
        float tx = bauTexel[k];
        vec4 p = bauM[k] * vec4(rel + n * (bauInfo.z * tx), 1.0);
        vec2 uv = p.xy * 0.5 + 0.5;
        float rand = 2.0 / float(N);
        if (uv.x < rand || uv.y < rand || uv.x > 1.0 - rand || uv.y > 1.0 - rand || p.z < 0.0 || p.z > 1.0) continue;
        vec2 f = uv * float(N) - 0.5;
        ivec2 b = ivec2(floor(f));
        vec2 w = f - vec2(b);
        // Abstand der Tiefe nach der Neigung zur Quelle: Grundabstand plus 2 · tan θ Texel [G], höchstens 20 Texel
        float c = clamp(dot(n, bauLicht.xyz), 0.05, 1.0);
        float bias = tx * (bauInfo.w + min(2.0 * sqrt(1.0 - c * c) / c, 20.0)) * bauTiefe[k];
        float s = 0.0;
        // 4 × 4 Texel, bilinear gewichtet (weicher Rand über 2 Texel)
        for (int j = -1; j <= 2; j++)
            for (int i = -1; i <= 2; i++) {
                ivec2 q = clamp(b + ivec2(i, j), ivec2(0), ivec2(N - 1));
                float d = texelFetch(bauSchattenT, ivec2(q.x + k * N, q.y), 0).r;
                float lit = p.z - bias <= d ? 1.0 : 0.0;
                float wx = i == -1 ? 1.0 - w.x : i == 2 ? w.x : 1.0;
                float wy = j == -1 ? 1.0 - w.y : j == 2 ? w.y : 1.0;
                s += lit * wx * wy;
            }
        return s / 9.0;
    }
    return 1.0;
}
