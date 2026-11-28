// schwimmer.glsl – render.schwimmer (seit S7b-3, Seegang Werkbuch 88): die schwimmenden Körper. Raum wie render.bau:
// relativ zum Auge, x Ost, y oben, z Süd, mit Erdkrümmung (Senkung r²/2R'). Die Netze liegen im Körper (x vorn,
// y Backbord, z oben ab der Unterkante; physics.auftrieb.Schiffbau); je Körper eine Matrix vom Körper in den Raum relativ
// zum Auge, je Bild auf dem Prozessor in double gerechnet.
//
// Bindungen (Bild und Schatten): 0 Scheitel, 1 Material, 2 Kopf, 3 Lagen, 4 Bau/Schattenwerte; nur im Bild: 5 Himmel/Werte,
// 6 Himmel/Durchlaessigkeit, 7 Himmelslicht (Kugelflächenfunktionen), 8 Himmel/Ansicht, 9 Bau/Schatten.
#include "/com/dan/core/gpu/luft.glsl"

struct Material {
    vec4 a;     // Albedo R G B (linear), Art (0 matt, 1 Lack, 2 Glas, 3 Fensterband, 4 Metall, 5 Holz)
    vec4 b;     // Glanz 0 … 1, Breite und Abstand der Fenster (m), 0
};

layout(set = 0, binding = 0, std430) readonly buffer Scheitel { vec4 sch[]; };   // je Scheitel: Lage + Material, Normale + Körper, u v w
layout(set = 0, binding = 1, std430) readonly buffer Materialien { Material mat[]; };
layout(set = 0, binding = 2, std430) readonly buffer Kopf {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung)
    vec4 augeHi;        // w 1/(2R')
    vec4 augeLo;        // w Augenhöhe (m NHN)
};
layout(set = 0, binding = 3, std430) readonly buffer Lagen { mat4 lage[]; };

// Lage des Scheitels i relativ zum Auge (mit Erdkrümmung); n: Normale im Raum.
vec3 rel(int i, out vec3 n) {
    vec4 a = sch[3 * i], b = sch[3 * i + 1];
    mat4 m = lage[int(b.w + 0.5)];
    vec3 r = (m * vec4(a.xyz, 1.0)).xyz;
    n = mat3(m) * b.xyz;
    r.y -= dot(r.xz, r.xz) * augeHi.w;
    return r;
}
