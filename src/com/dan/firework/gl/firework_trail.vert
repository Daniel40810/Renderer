#version 430 core
// firework_trail.vert — Funkenschweife und Spuren der Langzeitbelichtung als Linien, 1 Bildpunkt breit (seit Timmendorf Phase 11).
// aP = Ort, Länge des Stücks (m); aC = Lichtstärke je Farbkanal (cd) des Stücks.
layout(location = 0) in vec4 aP;
layout(location = 1) in vec3 aC;
uniform mat4 uViewProj;
uniform vec3 uEye;
uniform int uMirror;
uniform float uWaterY;
uniform float uPxPerRad;
out vec3 vColor;
out float gl_ClipDistance[1];
void main() {
    vec3 p = aP.xyz;
    gl_ClipDistance[0] = uMirror == 1 ? p.y - uWaterY : 1.0;
    if (uMirror == 1) p.y = 2.0 * uWaterY - p.y;
    float d = max(length(p - uEye), 0.1);
    // Beleuchtungsstärke I/d² verteilt auf (Länge/d) × (1 Bildpunkt) Raumwinkel, mindestens ein Bildpunkt lang
    float along = max(aP.w / d, 1.0 / uPxPerRad), across = 1.0 / uPxPerRad;
    vColor = aC / (d * d) / (along * across);
    gl_Position = uViewProj * vec4(p, 1.0);
}
