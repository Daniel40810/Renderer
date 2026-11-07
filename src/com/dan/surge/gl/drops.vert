// drops.vert — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W4b, Runde 4): Tropfen und Spritzer, die von der Spitze
// der Lippe und vom Fuß des Vorhangs fliegen (Lip) [G]. Punkte mit Größe in Metern; auf dem Bild so groß, wie sie sind
// (aus Brennweite und Abstand: uPxPerM Bildpunkte je Meter in 1 m), mindestens 1,5 Bildpunkte; kleinere werden statt
// kleiner durchsichtiger (die Kantenglättung des Wirts mittelt die gestreute Durchsicht).
#include "light.glsl"
layout(location = 0) in vec3 aP;
layout(location = 1) in float aS;     // Durchmesser (m)
layout(location = 2) in float aA;     // Deckung 0 … 1
layout(location = 3) in float aK;     // Kennung für das Rauschen
uniform float uCurvature;
uniform float uPxPerM;                // Bildpunkte je Meter in 1 m Abstand
out vec3 vW;
out float vA;
out float vK;

void main() {
    vec2 r = aP.xz - uCam.xz;
    vW = vec3(aP.x, aP.y - dot(r, r) * uCurvature, aP.z);
    float dist = max(length(vW - uCam), 1.0);
    float px = aS * uPxPerM / dist;
    float klein = clamp(px / 1.5, 0.0, 1.0);
    vA = aA * klein * klein;
    vK = aK;
    gl_PointSize = clamp(px * 1.4, 1.5, 9.0);   // weicher Fleck: etwas größer als der Tropfen, die Deckung fällt nach außen
    // Lippe und Welle rücken um 0,2 % des Abstands zur Kamera vor (K10); die Tropfen um 0,4 %, sonst verschwanden die,
    // die eben erst von der Spitze oder vom Fuß des Vorhangs abgehen, in der Fläche, von der sie kommen (Probe Runde 4)
    gl_Position = uVP * vec4(mix(vW, uCam, 0.004), 1.0);
}
