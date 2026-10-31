#version 430 core
// cloth.vert — Tuch aus den Puffern (vec4 je Punkt): Lage und Normale aus SSBO (Grafikkarte oder Prozessor),
// u, v im Netz und Nummer des Tuchs als Eckattribut.
layout(std430, binding = 0) readonly buffer Pos { vec4 POS[]; };
layout(std430, binding = 1) readonly buffer Nrm { vec4 NRM[]; };
layout(location = 0) in vec4 aUv;          // u, v (0..1), Tuch, -
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
flat out int vCloth;
void main() {
    vec3 p = POS[gl_VertexID].xyz;
    vWorld = p;
    vNormal = NRM[gl_VertexID].xyz;
    vUv = aUv.xy;
    vCloth = int(aUv.z + 0.5);
    gl_Position = uVP * vec4(p, 1.0);
}
