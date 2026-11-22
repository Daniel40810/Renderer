// bau.glsl – render.bau (seit P12a, Werkbuch HalligHooge Teil A, 12.13): Häuser als Körper. Raum wie das Gelände:
// relativ zum Auge, x Ost, y oben, z Süd, mit Erdkrümmung (Senkung r²/2R'). Scheitel aus core.szene.Bauten (12 float).
//
// Bindungen (gleich in allen Shadern des Moduls): 0 Scheitel, 1 Häuser, 2 Kopf, 3 Himmel/Werte, 4 Himmel/Durchlaessigkeit,
// 5 Himmelslicht (9 Glieder nach Kugelflächenfunktionen), 6 Himmel/Ansicht, 7 Bau/Schatten, 8 Bau/Schattenwerte.
#include "/com/dan/core/gpu/luft.glsl"

struct Haus {
    vec4 dach;      // Albedo R G B (linear), Dachart (0 Reet, 1 Ziegel, 2 flach)
    vec4 wand;      // Albedo R G B des Backsteins (Mittel mit Fugen), Boden (m NHN)
    vec4 info;      // Traufe, First (m NHN), Zufall 0 … 1, 0
};

layout(set = 0, binding = 0, std430) readonly buffer Scheitel { vec4 sch[]; };
layout(set = 0, binding = 1, std430) readonly buffer Haeuser { Haus haus[]; };
layout(set = 0, binding = 2, std430) readonly buffer Kopf {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung) – im Schattengang: die Karte
    vec4 augeHi;        // Auge im Raum der Bauten (x, y = Höhe m NHN, z) als float, w 1/(2R')
    vec4 augeLo;        // Rest des Auges (double − float), w Augenhöhe (m NHN)
    vec4 fenster;       // Anteil der Fenster mit Licht (0 … 1), Leuchtdichte (cd/m²), Höhe der Sonne (sin), 1 = Fenster an
};
layout(set = 0, binding = 3, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 4) uniform sampler2D durch;

// Lage relativ zum Auge mit Erdkrümmung; i: Nummer des Scheitels.
vec3 lage(int i) {
    vec3 p = sch[3 * i].xyz;
    vec3 r = (p - augeHi.xyz) - augeLo.xyz;
    r.y -= dot(r.xz, r.xz) * augeHi.w;
    return r;
}
