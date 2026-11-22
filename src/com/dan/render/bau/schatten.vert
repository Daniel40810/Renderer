#version 460
// schatten.vert – render.bau (P12a): Häuser in die Karte des Schattens (nur Tiefe). Push: Nummer der Karte.
#include "/com/dan/render/bau/bau.glsl"
layout(push_constant) uniform Push { mat4 m; };
void main() {
    gl_Position = m * vec4(lage(gl_VertexIndex), 1.0);
}
