#version 460
// brecher.vert – render.brandung (P13c): ein Dreieck je drei Scheitel aus dem Puffer (core.szene.Brandung, Netz).
#include "/com/dan/render/brandung/brandung.glsl"
layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vN;
layout(location = 2) out vec2 vSchaum;    // Schaum, Art (0 Brecher, 1 Film, 2 + dünn)
void main() {
    int i = gl_VertexIndex;
    vec4 a = sch[2 * i], b = sch[2 * i + 1];
    vRel = lageRel(a.xyz);
    vN = b.xyz;
    vSchaum = vec2(a.w, b.w);
    gl_Position = vp * vec4(vRel, 1.0);
}
