#version 460
// haus.vert – render.bau (P12a): ein Dreieck der Häuser je drei Scheitel aus dem Puffer (core.szene.Bauten).
#include "/com/dan/render/bau/bau.glsl"
layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vN;
layout(location = 2) out vec3 vUv;
layout(location = 3) flat out ivec2 vArt;     // Art (0 Wand, 1 Dach), Haus
void main() {
    int i = gl_VertexIndex;
    vec4 a = sch[3 * i], b = sch[3 * i + 1], c = sch[3 * i + 2];
    vRel = lage(i);
    vN = b.xyz;
    vUv = c.xyz;
    vArt = ivec2(int(a.w + 0.5), int(b.w + 0.5));
    gl_Position = vp * vec4(vRel, 1.0);
}
