#version 460
// spur.vert – render.analysis (P11c2): die Spuren der Partikel als Strecken (aus partikel.comp). Je Partikel K − 1
// Strecken von spur[j] nach spur[j + 1]; die Deckung fällt längs der Spur und am Anfang und Ende des Lebens. Eine Strecke
// mit einem ungültigen Ende liegt außerhalb des Bilds.
#define K 8
struct Partikel { vec4 kopf; vec4 info; vec4 spur[K]; };
layout(std430, set = 0, binding = 0) readonly buffer Teile { Partikel P[]; };
layout(push_constant) uniform Q {
    mat4 vp;
    vec4 off;     // Ursprung − Auge (Ost, Nord), Höhe des Auges, Faktor der Tiefe
    vec4 farbe;   // rgb, Deckung
    vec4 art;     // –; Hub (m); Geschwindigkeit für das Ende der Leiter (m/s); 1: Farbe nach der Geschwindigkeit
} q;
layout(location = 0) out vec4 farbe;
vec3 leiter(float s) {
    s = clamp(s, 0.0, 1.0);
    const vec3 c0 = vec3(0.15, 0.20, 0.75), c1 = vec3(0.10, 0.65, 0.95), c2 = vec3(0.20, 0.85, 0.55), c3 = vec3(0.85, 0.90, 0.20), c4 = vec3(1.00, 0.60, 0.15);
    if (s < 0.25) return mix(c0, c1, s / 0.25);
    if (s < 0.5) return mix(c1, c2, (s - 0.25) / 0.25);
    if (s < 0.75) return mix(c2, c3, (s - 0.5) / 0.25);
    return mix(c3, c4, (s - 0.75) / 0.25);
}
void main() {
    int strecke = gl_VertexIndex / 2, ende = gl_VertexIndex % 2;
    int i = strecke / (K - 1), j = strecke % (K - 1);
    vec4 a = P[i].spur[j], b = P[i].spur[j + 1];
    if (a.w < 0.5 || b.w < 0.5) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        farbe = vec4(0.0);
        return;
    }
    vec4 p = ende == 0 ? a : b;
    vec3 rel = vec3(p.x + q.off.x, p.z + q.art.y - q.off.z, -(p.y + q.off.y));
    gl_Position = q.vp * vec4(rel, 1.0);
    gl_Position.z *= q.off.w;
    vec4 kopf = P[i].kopf;
    float leben = kopf.w > 0.0 ? clamp(kopf.z / kopf.w, 0.0, 1.0) : 1.0;
    float rand = min(1.0, min(leben, 1.0 - leben) * 10.0);         // je ein Zehntel des Lebens ein- und ausblenden [G]
    float laengs = 1.0 - float(j + ende) / float(K - 1);
    vec3 c = q.art.w > 0.5 ? leiter(P[i].info.x / q.art.z) : q.farbe.rgb;
    farbe = vec4(c, q.farbe.a * laengs * rand);
}
