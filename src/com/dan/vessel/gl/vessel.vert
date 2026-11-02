#version 450
// vessel.vert — Netze von com.dan.vessel (Rumpf, Steuerhaus, Kette, in der Vorführung auch Wasser und Gelände).
layout(location = 0) in vec3 aP;
layout(location = 1) in vec3 aN;
layout(location = 2) in vec3 aC;
uniform mat4 uVP, uModel;
out vec3 vW, vN, vC;
void main() {
    vec4 w = uModel * vec4(aP, 1.0);
    vW = w.xyz;
    vN = mat3(uModel) * aN;
    vC = aC;
    gl_Position = uVP * w;
}
