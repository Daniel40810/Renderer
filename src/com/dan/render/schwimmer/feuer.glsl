// feuer.glsl – render.schwimmer (seit S7e1, Seegang Werkbuch 99; SF148 A): Lichter der Schwimmer (Positionslichter nach
// KVR, Tonnenfeuer nach Kennung, Decksbeleuchtung) als Lichtpunkte mit Hof, dazu ihr Spiegelbild im Wasser als
// senkrechter Streif. Raum wie schwimmer.glsl: relativ zum Auge, x Ost, y oben, z Süd, mit Erdkrümmung.
//
// Bindungen: 0 Kopf, 1 Lichter (je Licht zwei vec4), 2 Himmel/Werte.
#include "/com/dan/core/gpu/luft.glsl"

layout(set = 0, binding = 0, std430) readonly buffer Kopf {
    mat4 vp;            // Projektion mal Ansicht (nur Drehung)
    vec4 augeHi;        // w 1/(2R')
    vec4 rechtsV;       // Bild: rechts (Einheitsvektor)
    vec4 obenV;         // Bild: oben (Einheitsvektor); w Winkel eines Bildpunkts (rad)
    vec4 werte;         // x Auslöschung der Luft β (1/m), y Anteil des Hofs, z Länge des Streifs (Vielfaches), w Abstand zum Wasser (Anteil)
};
// je Licht: a = Lage relativ zum Auge (ohne Krümmung), Lichtstärke zum Auge (cd, mit Sektor und Kennung);
//           b = Farbe (linear, größter Kanal 1), w Höhe des Wassers am Licht relativ zum Auge (m; +1e9: keins)
layout(set = 0, binding = 1, std430) readonly buffer Lichter { vec4 li[]; };
layout(set = 0, binding = 2, std430) readonly buffer Werte { Himmelswerte hw; };

// Kern und Hof [G]: der Kern ist eine Gauß-Glocke mit σ = 0,75 Bildpunkte (ein Punkt, den das Auge nicht auflöst), der Hof
// (Streulicht in Luft und Linse) fällt wie 1/(1 + r²/w²)² mit w = 6 Bildpunkten ab; beide auf eins normiert.
const float KERN = 0.75, HOF = 6.0, HOF_RAND = 4.5;

// Seit L5e1 (Befund 1): Hof und Streif laufen zum Rand ihres Vierecks auf null aus. Die Farbe überstrahlter Lichter hält
// render.frame (ausgabe.frag, Belichtung.farbtreu): ein Kappen hier senkte die Leuchtdichte unter die Schwelle der Stäbchen.

// Nacht: Lichter an, wenn die Sonne unter 2° steht, ganz ab −2° [G] (Lichtschalter der Tonnen, KVR: von Sonnenuntergang an).
float nacht() { return smoothstep(0.0349, -0.0349, hw.sonne.y); }

vec3 krumm(vec3 r) { r.y -= dot(r.xz, r.xz) * augeHi.w; return r; }
