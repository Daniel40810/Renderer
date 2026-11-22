#version 460
// linie.vert – render.analysis (P11c2): Isolinien als Liste von Strecken. Je Scheitel vier Werte: Ost, Nord (m vom
// Ursprung des Gitters), Höhe (m NHN), s (0 … 1, Stelle in der Farbleiter). Lage relativ zum Auge wie core.raum.Blick
// (x nach Ost, y nach oben, z nach Süd); die Tiefe wird um einen Faktor zum Auge gezogen, damit die Linie auf dem
// Wasser nicht in dessen Tiefe versinkt.
layout(std430, set = 0, binding = 0) readonly buffer Punkte { vec4 P[]; };
layout(push_constant) uniform Q {
    mat4 vp;
    vec4 off;     // Ursprung − Auge (Ost, Nord, m), Höhe des Auges (m NHN), Faktor der Tiefe
    vec4 farbe;   // rgb, Deckung
    vec4 art;     // 0: Farbe aus der Leiter nach s, 1: feste Farbe; Hub (m); –; –
} q;
layout(location = 0) out vec4 farbe;
vec3 leiter(float s);
void main() {
    vec4 p = P[gl_VertexIndex];
    vec3 rel = vec3(p.x + q.off.x, p.z + q.art.y - q.off.z, -(p.y + q.off.y));
    gl_Position = q.vp * vec4(rel, 1.0);
    gl_Position.z *= q.off.w;
    vec3 c = q.art.x > 0.5 ? q.farbe.rgb : leiter(p.w);
    farbe = vec4(c, q.farbe.a);
}
vec3 leiter(float s) {
    s = clamp(s, 0.0, 1.0);
    const vec3 c0 = vec3(0.15, 0.20, 0.75), c1 = vec3(0.10, 0.65, 0.95), c2 = vec3(0.20, 0.85, 0.55), c3 = vec3(0.85, 0.90, 0.20), c4 = vec3(1.00, 0.60, 0.15);
    if (s < 0.25) return mix(c0, c1, s / 0.25);
    if (s < 0.5) return mix(c1, c2, (s - 0.25) / 0.25);
    if (s < 0.75) return mix(c2, c3, (s - 0.5) / 0.25);
    return mix(c3, c4, (s - 0.75) / 0.25);
}
