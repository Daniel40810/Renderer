#version 460
// linse_kachel.vert – render.frame, seit S9b-3 (Seegang Werkbuch 107): je Kachel der Scheibe, in der ein Tropfen brechen kann,
// zwei Dreiecke über ihr Rechteck im Bild (linse.frag zeichnet nur dort; anderswo bliebe der Bildpunkt, wie er ist). Die
// Scheibe füllt das Bild; ihr Ursprung liegt links unten, der des Bilds links oben.
layout(set = 0, binding = 0, std430) readonly buffer Kopf { vec4 linse; vec4 bild; vec4 mehr; vec4 meniskus; vec4 kachel; };
layout(set = 0, binding = 4, std430) readonly buffer Kacheln { int kl[]; };
const ivec2 ECKE[6] = ivec2[](ivec2(0, 0), ivec2(1, 0), ivec2(0, 1), ivec2(1, 0), ivec2(1, 1), ivec2(0, 1));
void main() {
    int tx = int(kachel.y), ty = int(kachel.z);
    int t = kl[tx * ty + 1 + gl_VertexIndex / 6];
    vec2 c = vec2(ivec2(t % tx, t / tx) + ECKE[gl_VertexIndex % 6]) / vec2(tx, ty);
    gl_Position = vec4(c.x * 2.0 - 1.0, (1.0 - c.y) * 2.0 - 1.0, 0.0, 1.0);
}
