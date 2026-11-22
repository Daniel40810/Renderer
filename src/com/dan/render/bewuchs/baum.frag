#version 460
// baum.frag – render.bewuchs (P12c): Licht auf den Gehölzen wie auf den Häusern (Sonne und Mond mit der Durchlässigkeit
// der Luft und dem Schatten aus render.bau, Himmel und Boden aus den Kugelflächenfunktionen). Laub ist zweiseitig und
// lässt Licht durch (Anteil 0,35 der Albedo [G]); Rinde nach Lambert. Farbe je Scheitel (linear, nach dem Tag gefärbt).
#include "/com/dan/render/bewuchs/bewuchs.glsl"
#include "/com/dan/render/bewuchs/kugel.glsl"
layout(set = 0, binding = 5, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 6) uniform sampler2D durch;
layout(set = 0, binding = 7, std430) readonly buffer Licht { vec4 sh[9]; };
layout(set = 0, binding = 8) uniform sampler2D ansichtT;
#define BAU_SCHATTEN_BILD 9
#define BAU_SCHATTEN_WERTE 4
#include "/com/dan/core/gpu/bauschatten.glsl"
layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vN;
layout(location = 2) in vec3 vCol;
layout(location = 3) flat in float vTeil;
layout(location = 0) out vec4 farbe;

vec3 sonne(vec3 l, vec3 e, float h) {
    float r = R_BOTTOM + h, q = qAus(h);
    if (trifftBoden(r, q, l.y)) return vec3(0.0);
    return e * durchlaessigkeit(durch, r, q, l.y);
}

vec3 himmelE(vec3 n) {
    float y[9];
    kugelY(n, y);
    const float A[9] = float[](3.14159265, 2.0943951, 2.0943951, 2.0943951, 0.78539816, 0.78539816, 0.78539816, 0.78539816, 0.78539816);
    vec3 e = vec3(0.0);
    for (int k = 0; k < 9; k++) e += A[k] * sh[k].rgb * y[k];
    return max(e, vec3(0.0));
}

void main() {
    vec3 v = -normalize(vRel), n = normalize(vN);
    bool laub = vTeil > 0.5;
    if (dot(n, v) < 0.0) n = -n;
    float h = max(0.5, vRel.y + augeLo.w);
    float bs = bauSchatten(vRel, n);
    float bsS = bauInfo.y < 0.5 ? bs : 1.0, bsM = bauInfo.y > 0.5 ? bs : 1.0;
    vec3 Es = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * bsS, Em = sonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h) * bsM;
    float durchL = laub ? 0.35 : 0.0;
    vec3 E = Es * (max(0.0, dot(n, hw.sonne.xyz)) + durchL * max(0.0, -dot(n, hw.sonne.xyz)))
           + Em * (max(0.0, dot(n, hw.mond.xyz)) + durchL * max(0.0, -dot(n, hw.mond.xyz)))
           + himmelE(n) + durchL * himmelE(-n);
    vec3 L = vCol / L_PI * E;
    farbe = vec4(min(L * hw.sonneE.w, vec3(60000.0)), 1.0);
}
