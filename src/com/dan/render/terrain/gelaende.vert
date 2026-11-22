#version 460
// gelaende.vert – render.terrain (P6b): ein Block einer Stufe der Clipmap. Scheitel aus gl_VertexIndex: je Viereck
// zwei Dreiecke, Vierecke je Block lc.y; Push: Stufe, Block x, Block y.
#include "/com/dan/render/terrain/gelaende.glsl"
layout(set = 0, binding = 8) uniform sampler2D bodenDz;   // seit P9c: bed.dz je Zelle des Sediments (m); 1 × 1 mit NaN: keine
layout(push_constant) uniform Push { ivec4 p; };
layout(location = 0) out vec3 vRel;
layout(location = 1) out vec3 vN;
layout(location = 2) out vec3 vAlb;
layout(location = 3) out float vWatt;
layout(location = 4) out float vSicht;
// Bodenänderung am Ort (P9c, F81 A): bilinear zwischen den Mitten der Zellen des Sediments, außerhalb des Gitters 0.
// xz: Ort relativ zum Auge (x Ost, z Süd).
float bodenDzOrt(vec2 xz) {
    if (bodLage.w < 0.5) return 0.0;
    vec2 u = vec2(xz.x - bodLage.x, -xz.y - bodLage.y) / bodLage.z - 0.5;
    ivec2 c0 = ivec2(floor(u));
    vec2 f = u - vec2(c0);
    float s = 0.0;
    for (int k = 0; k < 4; k++) {
        ivec2 c = c0 + ivec2(k & 1, k >> 1);
        if (c.x < 0 || c.y < 0 || c.x >= bodN.x || c.y >= bodN.y) continue;
        float v = texelFetch(bodenDz, c, 0).r;
        if (isnan(v)) continue;
        s += ((k & 1) == 0 ? 1.0 - f.x : f.x) * ((k >> 1) == 0 ? 1.0 - f.y : f.y) * v;
    }
    return s;
}

const ivec2 ECKE[6] = ivec2[](ivec2(0, 0), ivec2(1, 0), ivec2(0, 1), ivec2(1, 0), ivec2(1, 1), ivec2(0, 1));
void main() {
    int B = int(lc.y), q = gl_VertexIndex / 6, e = gl_VertexIndex % 6;
    int k = p.y * B + q % B + ECKE[e].x, l = p.z * B + q / B + ECKE[e].y;
    int L = p.x;
    Scheitel s = scheitel(L, k, l);
    s.rel.y += bodenDzOrt(s.rel.xz);                 // der Boden hebt und senkt sich mit bed.dz; Normale und Schatten bleiben (20 m je Zelle)
    vRel = s.rel;
    vN = s.n;
    vAlb = s.albedo;
    vWatt = s.watt;
    vSicht = L < int(lc.z) ? sicht[platz(0, L, st[L].idx.x + k, st[L].idx.y + l)] : 1.0;
    gl_Position = vp * vec4(s.rel, 1.0);
}
