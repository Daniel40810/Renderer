// brandung.glsl – render.brandung (seit P13c, Werkbuch HalligHooge Teil A, 12.14; F160 A, F162 A): Brecher, Auflauf und
// Gischt an den Böschungen. Raum wie render.bau: relativ zum Auge, x Ost, y oben, z Süd, mit Erdkrümmung (Senkung r²/2R').
// Scheitel und Bündel aus core.szene.Brandung.
//
// Bindungen (gleich in allen Shadern des Moduls): 0 Scheitel (je zwei vec4: Ort und Schaum, Normale und Art), 1 Kopf,
// 2 Himmel/Werte, 3 Himmel/Durchlaessigkeit, 4 Himmelslicht (9 Glieder), 5 Himmel/Ansicht, 6 Bündel der Gischt (je vier vec4).
#include "/com/dan/core/gpu/luft.glsl"

layout(set = 0, binding = 0, std430) readonly buffer Scheitel { vec4 sch[]; };
layout(set = 0, binding = 1, std430) readonly buffer Kopf {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung)
    vec4 augeHi;        // Auge im Raum der Brandung (x, y = Höhe m NHN, z) als float, w 1/(2R')
    vec4 augeLo;        // Rest des Auges (double − float), w Augenhöhe (m NHN)
    vec4 werte;         // Albedo des Schaums, Leuchtdichtefaktor des trüben Wassers (Albedo des Körpers), Tropfen je Bündel, rechts.x
    vec4 rechtsV;       // Bild: rechts (Einheitsvektor), w Bildhöhe in Bildpunkten
    vec4 obenV;         // Bild: oben (Einheitsvektor), w Winkel eines Bildpunkts (rad)
};
layout(set = 0, binding = 2, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 3) uniform sampler2D durch;

// Lage relativ zum Auge mit Erdkrümmung
vec3 lageRel(vec3 p) {
    vec3 r = (p - augeHi.xyz) - augeLo.xyz;
    r.y -= dot(r.xz, r.xz) * augeHi.w;
    return r;
}

vec3 sonneAm(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}
