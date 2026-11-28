#version 460
// schwimmer.vert – render.schwimmer (S7b-3): ein Dreieck der Körper je drei Scheitel aus dem Puffer.
#include "/com/dan/render/schwimmer/schwimmer.glsl"
layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vN;
layout(location = 2) out vec3 vUv;
layout(location = 3) flat out int vMat;
void main() {
    int i = gl_VertexIndex;
    vec3 n;
    vRel = rel(i, n);
    vN = n;
    vUv = sch[3 * i + 2].xyz;
    vMat = int(sch[3 * i].w + 0.5);
    gl_Position = vp * vec4(vRel, 1.0);
}
