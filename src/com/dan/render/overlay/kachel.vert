#version 460
// kachel.vert – render.overlay (W2): eine Kachel als zwei Dreiecke (6 Scheitel), ohne Scheitel-Eingänge. Das Rechteck
// steht in Bildpunkten (links oben, y nach unten), gedreht um seine Mitte. Hat die Kachel einen Anker in der Welt
// (anker.z > 0: Abstand längs der Achse), liest der Shader die Tiefe der Szene an fünf Stellen um den Anker; wo die
// Szene näher ist als der Anker (mit einer Toleranz von 5 m oder 2 % [G]), deckt die Kachel anteilig weniger.
// Tiefe umgekehrt: z = nah / Abstand, 0 ist der Himmel.
layout(set = 0, binding = 0) uniform sampler2D tiefe;
layout(push_constant) uniform Q {
    vec4 rect;    // links, oben, Breite, Höhe (Bildpunkte)
    vec4 ziel;    // Breite und Höhe des Ziels, Drehung (Bogenmaß, im Uhrzeigersinn), Deckung
    vec4 anker;   // Bildpunkt x, y, Abstand längs der Achse (m; ≤ 0: ohne Verdecken), nah
} q;
layout(location = 0) out vec2 uv;
layout(location = 1) out float deckung;

const vec2 ECKEN[6] = vec2[](vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0));
const vec2 PROBEN[5] = vec2[](vec2(0.0, 0.0), vec2(-3.0, 0.0), vec2(3.0, 0.0), vec2(0.0, -3.0), vec2(0.0, 3.0));

void main() {
    vec2 e = ECKEN[gl_VertexIndex];
    uv = e;
    vec2 m = q.rect.xy + 0.5 * q.rect.zw;
    vec2 p = (e - 0.5) * q.rect.zw;
    float c = cos(q.ziel.z), s = sin(q.ziel.z);
    p = vec2(c * p.x - s * p.y, s * p.x + c * p.y) + m;
    gl_Position = vec4(p / q.ziel.xy * 2.0 - 1.0, 0.0, 1.0);
    float a = q.ziel.w;
    if (q.anker.z > 0.0) {
        ivec2 gr = textureSize(tiefe, 0);
        float frei = 0.0;
        float tol = max(5.0, 0.02 * q.anker.z);
        for (int i = 0; i < 5; i++) {
            ivec2 k = clamp(ivec2(floor(q.anker.xy + PROBEN[i])), ivec2(0), gr - 1);
            float z = texelFetch(tiefe, k, 0).r;
            float d = z > 0.0 ? q.anker.w / z : 1.0e30;
            if (d >= q.anker.z - tol) frei += 0.2;
        }
        a *= frei;
    }
    deckung = a;
}
