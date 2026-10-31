#version 430 core
// grains.vert — Körner als kurze Striche in Flugrichtung (seit Sylt Phase 8): Kopf und Schweif je Korn.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aInfo;   // Kopf 1 / Schweif 0, Art (0 Saltation, 1 Schweb)
uniform mat4 uViewProj;
uniform vec3 uEye;
out float vHead;
out float vKind;
out float vDist;
void main() {
    vHead = aInfo.x;
    vKind = aInfo.y;
    vDist = length(aPos - uEye);
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
