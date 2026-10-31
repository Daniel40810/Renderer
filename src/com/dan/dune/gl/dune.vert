#version 430 core
// dune.vert — Boden des Flugsands (seit Sylt Phase 8): Gitter in Windrichtung aus FluxField, je Ecke Fluss, Feuchte,
// Bewuchs, Windschatten und die Windrippel (λ, H, c) aus Ripples.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec4 aState;   // q / qRef, Feuchte (Massenanteil), Bewuchs λ, Windschatten 0/1
layout(location = 2) in vec4 aRipple;  // λ (m), H (m), Wandergeschwindigkeit c (m/s), Ablagerung (m/h)
uniform mat4 uViewProj;
out vec3 vPos;
out vec4 vState;
out vec4 vRipple;
void main() {
    vPos = aPos;
    vState = aState;
    vRipple = aRipple;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
