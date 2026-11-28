#version 460
// schatten.vert – render.schwimmer (S7b-3): die Körper in die Karte des Schattens von render.bau (nur Tiefe). Die
// Abbildung der Karte k aus Bau/Schattenwerte; Push: k.
#include "/com/dan/render/schwimmer/schwimmer.glsl"
layout(set = 0, binding = 4, std430) readonly buffer BauW { mat4 bauM[3]; vec4 bauInfo; };
layout(push_constant) uniform Push { int k; };
void main() {
    vec3 n;
    gl_Position = bauM[k] * vec4(rel(gl_VertexIndex, n), 1.0);
}
