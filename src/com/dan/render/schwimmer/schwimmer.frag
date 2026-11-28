#version 460
// schwimmer.frag – render.schwimmer (S7b-3): Licht auf den Körpern wie auf den Häusern und Gehölzen (Sonne und Mond mit der
// Durchlässigkeit der Luft und dem Schatten aus render.bau, Himmel und Boden aus den Kugelflächenfunktionen). Material je
// Dreieck [G]: matt (Anstrich unter Wasser, Deck), Lack (Glanz nach Schlick, n = 1,5, mit dem Himmel in der gespiegelten
// Richtung), Glas, Fensterband (Fenster mit Rahmen in einer Wand, Breite und Abstand aus dem Material), Metall, Holz.
// Ausgabe: Leuchtdichte (cd/m²) mal Vorbelichtung; die Luftperspektive legt der Himmel darüber.
#include "/com/dan/render/schwimmer/schwimmer.glsl"
#include "/com/dan/render/schwimmer/kugel.glsl"
layout(set = 0, binding = 5, std430) readonly buffer Werte { Himmelswerte hw; };
layout(set = 0, binding = 6) uniform sampler2D durch;
layout(set = 0, binding = 7, std430) readonly buffer Licht { vec4 sh[9]; };
layout(set = 0, binding = 8) uniform sampler2D ansichtT;
#define BAU_SCHATTEN_BILD 9
#define BAU_SCHATTEN_WERTE 4
#include "/com/dan/core/gpu/bauschatten.glsl"
layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vN;
layout(location = 2) in vec3 vUv;
layout(location = 3) flat in int vMat;
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

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

// Glätte eines Musters: 1, solange ein Bildpunkt (fuss, m) die Größe g auflöst, darunter 0.
float aufgeloest(float g, float fuss) { return clamp(g / (2.0 * max(fuss, 1e-5)) - 1.0, 0.0, 1.0); }

void main() {
    Material m = mat[vMat];
    vec3 n = normalize(vN), v = -normalize(vRel);
    if (dot(n, v) < 0.0) n = -n;
    float fuss = max(length(dFdx(vRel)), length(dFdy(vRel)));
    float h = max(0.5, vRel.y + augeLo.w);
    int art = int(m.a.w + 0.5);
    vec3 a = m.a.rgb;
    float glanz = m.b.x, glas = 0.0;
    if (art == 3) {
        // Fensterband: Scheiben mit Rahmen in der Wand (u längs, v die Höhe im Band) [G]
        float b = max(m.b.y, 0.2), ab = max(m.b.z, b + 0.1), u = vUv.x, hv = vUv.y;
        float fx = mod(u, ab), rahmen = 0.06;
        bool scheibe = fx > 0.5 * (ab - b) && fx < 0.5 * (ab + b) && hv > rahmen && hv < 1.0;
        float auf = aufgeloest(ab, fuss);
        if (scheibe && auf > 0.0) {
            a = mix(a, vec3(0.025), auf);
            glas = auf;
        } else a *= 1.0;
    } else if (art == 2) glas = 1.0;
    else if (art == 5) a *= 0.8 + 0.4 * hash(vec2(floor(vUv.x / 0.15), floor(vUv.y / 2.0)));      // Holz: Planken
    // Licht: Sonne und Mond (mit dem Schatten der Häuser und Körper), Himmel und Boden
    float bs = bauSchatten(vRel, n);
    float bsS = bauInfo.y < 0.5 ? bs : 1.0, bsM = bauInfo.y > 0.5 ? bs : 1.0;
    vec3 Es = sonne(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h) * bsS, Em = sonne(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h) * bsM;
    vec3 E = Es * max(0.0, dot(n, hw.sonne.xyz)) + Em * max(0.0, dot(n, hw.mond.xyz)) + himmelE(n);
    vec3 L = a / L_PI * E;
    // Glanz: Lack und Glas spiegeln den Himmel (Fresnel nach Schlick) und die Sonne (schmale Keule) [G]
    float c = clamp(dot(n, v), 0.0, 1.0);
    float F0 = glas > 0.0 ? 0.043 : 0.04;
    float F = (F0 + (1.0 - F0) * pow(1.0 - c, 5.0)) * max(glanz, glas);
    if (F > 0.0) {
        vec3 r = reflect(-v, n);
        L += F * ansicht(ansichtT, normalize(vec3(r.x, max(r.y, 0.01), r.z)), hw.sonne.w);
        float sp = pow(max(0.0, dot(r, hw.sonne.xyz)), 400.0) * 60.0;
        L += F * Es * sp / L_PI;
    }
    farbe = vec4(min(L * hw.sonneE.w, vec3(60000.0)), 1.0);
}
