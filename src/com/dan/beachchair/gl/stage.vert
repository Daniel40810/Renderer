#version 430 core
// stage.vert — Bühne für die Vorführung (nicht für Szenen): Sand bis zur Wasserlinie, dahinter die See, ein Himmel.
layout(location = 0) in vec3 aPos;
uniform mat4 uViewProj;
out vec3 vWorld;
void main() {
    vWorld = aPos;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
