#version 460
// baum.vert – render.bewuchs (P12c): ein Gehölz aus seinem Netz (Instanz aus der Liste).
#include "/com/dan/render/bewuchs/bewuchs.glsl"
layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vN;
layout(location = 2) out vec3 vCol;
layout(location = 3) flat out float vTeil;
void main() {
    Pflanze q = pfl[liste[gl_InstanceIndex]];
    vec3 n;
    vec3 w = welt(gl_VertexIndex, q, n);
    vRel = rel(w);
    vN = n;
    vCol = sch[3 * gl_VertexIndex + 2].rgb;
    vTeil = sch[3 * gl_VertexIndex + 1].w;
    gl_Position = vp * vec4(vRel, 1.0);
}
