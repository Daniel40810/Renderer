#version 430 core
// shadow.vert — Schatten der Körbe auf dem Sand: je Korb ein Vieleck (Umriss mit der Sonne auf den Boden geworfen, auf
// der Prozessorseite gerechnet), knapp über dem Grund; Erdkrümmung wie beachchair.vert.
layout(location = 0) in vec3 aPos;
layout(location = 1) in float aFade;   // 1 am Korb … 0 an der Grenze der Schatten
uniform mat4 uViewProj;
uniform vec3 uEye;
uniform float uEarthR;
out float vFade;
out float vDist;
void main() {
    vec2 dh = aPos.xz - uEye.xz;
    float drop = uEarthR > 0.0 ? dot(dh, dh) / (2.0 * uEarthR) : 0.0;
    vFade = aFade;
    vDist = length(aPos - uEye);
    gl_Position = uViewProj * vec4(aPos.x, aPos.y - drop, aPos.z, 1.0);
}
