#version 460
// brecher.frag – render.brandung (P13c, F160 A): Licht auf Brecher und Wasserfilm. Wasser: Fresnel (n = 1,34) mal Himmel
// in der gespiegelten Richtung, dazu das Licht aus dem trüben Wasser (Lambert mit der Albedo des Körpers [G]) und, wo die
// Lippe dünn ist, durchscheinende Sonne [G]. Schaum: Lambert mit der Albedo des Schaums (wie Seegangsoptik.ALBEDO). Licht
// von Sonne und Mond durch die Luft, Himmel und Boden aus den Kugelflächenfunktionen (licht.comp).
// Ausgabe: Leuchtdichte (cd/m²) mal Vorbelichtung; die Luftperspektive legt der Himmel darüber.
#include "/com/dan/render/brandung/brandung.glsl"
#include "/com/dan/render/brandung/kugel.glsl"
layout(set = 0, binding = 4, std430) readonly buffer Licht { vec4 sh[9]; };
layout(set = 0, binding = 5) uniform sampler2D ansichtT;
layout(location = 0) in vec3 vRel;
layout(location = 1) in vec3 vN;
layout(location = 2) in vec2 vSchaum;
layout(location = 0) out vec4 farbe;

vec3 himmelE(vec3 n) {
    float y[9];
    kugelY(n, y);
    const float A[9] = float[](3.14159265, 2.0943951, 2.0943951, 2.0943951, 0.78539816, 0.78539816, 0.78539816, 0.78539816, 0.78539816);
    vec3 e = vec3(0.0);
    for (int k = 0; k < 9; k++) e += A[k] * sh[k].rgb * y[k];
    return max(e, vec3(0.0));
}

float fresnelW(float ci) {
    ci = clamp(ci, 0.0, 1.0);
    float n = 1.34, st2 = (1.0 - ci * ci) / (n * n), ct = sqrt(max(0.0, 1.0 - st2));
    float rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
    return 0.5 * (rs * rs + rp * rp);
}

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

void main() {
    vec3 n = normalize(vN), v = normalize(-vRel);
    if (dot(n, v) < 0.0) n = -n;
    float h = max(0.5, vRel.y + augeLo.w);
    vec3 Es = sonneAm(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h), Em = sonneAm(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h);
    vec3 E = Es * max(0.0, dot(n, hw.sonne.xyz)) + Em * max(0.0, dot(n, hw.mond.xyz)) + himmelE(n);
    // Schaum: Muster auf 0,3 m, im Mittel der Anteil [G]
    float schaum = clamp(vSchaum.x, 0.0, 1.0);
    vec3 w = vRel + augeHi.xyz;
    float fuss = max(length(dFdx(vRel)), length(dFdy(vRel)));
    float mu = clamp(0.3 / (2.0 * max(fuss, 1e-4)) - 1.0, 0.0, 1.0);
    schaum = clamp(mix(schaum, schaum * (0.6 + 0.8 * hash(floor(w.xz / 0.3) + floor(w.y / 0.3) * 7.0)), mu), 0.0, 1.0);
    vec3 Lf = werte.x / L_PI * E;
    // Wasser
    vec3 r = reflect(-v, n);
    r = normalize(vec3(r.x, max(r.y, 0.01), r.z));
    float F = fresnelW(dot(n, v));
    vec3 Lw = F * ansicht(ansichtT, r, hw.sonne.w) + (1.0 - F) * werte.y / L_PI * (Es * max(0.0, hw.sonne.y) + Em * max(0.0, hw.mond.y) + himmelE(vec3(0.0, 1.0, 0.0)));
    float duenn = vSchaum.y >= 2.0 ? vSchaum.y - 2.0 : 0.0;
    Lw += duenn * 0.25 * Es * pow(max(0.0, dot(hw.sonne.xyz, -v)), 4.0) * vec3(0.55, 0.62, 0.45) / L_PI;
    vec3 L = mix(Lw, Lf, schaum);
    farbe = vec4(min(L * hw.sonneE.w, vec3(60000.0)), 1.0);
}
