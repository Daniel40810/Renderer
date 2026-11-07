// lip.vert — com.dan.surge (aus LituyaBay1958, Werkbuch Welle W4b „Die Röhre“): die Lippe der Front als eigenes Netz
// (Lip). Ecken in Szenenmetern, Erdkrümmung als Absenkung r² · uCurvature; die Ecke rückt um 0,2 % des Abstands zur
// Kamera, damit die Lippe dort gewinnt, wo sie die Wellenfläche berührt.
#include "light.glsl"
layout(location = 0) in vec3 aP;
layout(location = 1) in vec3 aN;
layout(location = 2) in float aU;     // 0 Rücken … 1 Ende der Rolle in der Röhre
layout(location = 3) in float aH;     // Höhe der Welle über dem Pegel (m)
layout(location = 4) in vec2 aT;      // Laufrichtung (Szene x, z)
layout(location = 5) in float aW;     // Gewicht am Rand der Lippe (0 … 1)
layout(location = 6) in float aZu;    // 0 offen (die Lippe fliegt), 1 zu (aufgeschlagen, Vorhang)
layout(location = 7) in vec2 aB;      // Runde 4: der Punkt der Linie unter dem Querschnitt (Szene x, z), für das Rauschen
uniform float uCurvature;
out vec3 vW;
out vec3 vN;
out float vU;
out float vH;
out vec2 vT;
out float vRand;
out float vZu;
out vec2 vB;

void main() {
    vec2 r = aP.xz - uCam.xz;
    vW = vec3(aP.x, aP.y - dot(r, r) * uCurvature, aP.z);
    vN = aN;
    vU = aU;
    vH = aH;
    vT = aT;
    vRand = aW;
    vZu = aZu;
    vB = aB;
    gl_Position = uVP * vec4(mix(vW, uCam, 0.002), 1.0);
}
