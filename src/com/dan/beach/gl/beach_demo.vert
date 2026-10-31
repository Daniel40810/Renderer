#version 430 core
// beach_demo.vert — ein ebenes Stück Strand für die Vorführung (nicht für Szenen).
layout(location = 0) in vec3 aPos;
uniform mat4 uViewProj;
out vec3 vWorld;
void main() {
    vWorld = aPos;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
