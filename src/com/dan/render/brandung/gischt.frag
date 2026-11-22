#version 460
// gischt.frag – render.brandung (P13c): ein Tropfen oder ein Fetzen Fahne; Streulicht von Sonne und Mond nach vorn
// betont (Henyey-Greenstein, g = 0,6 [G]), Himmel aus den Kugelflächenfunktionen; gemischt mit vormultipliziertem Alpha.
#include "/com/dan/render/brandung/brandung.glsl"
layout(set = 0, binding = 4, std430) readonly buffer Licht { vec4 sh[9]; };
layout(location = 0) in vec2 vUv;
layout(location = 1) in float vAlpha;
layout(location = 2) in vec3 vRel;
layout(location = 0) out vec4 farbe;
void main() {
    float r2 = dot(vUv, vUv);
    if (r2 > 1.0 || vAlpha <= 0.0) discard;
    float a = vAlpha * (1.0 - r2);
    vec3 v = normalize(-vRel);
    float h = max(0.5, vRel.y + augeLo.w);
    vec3 Es = sonneAm(hw.sonne.xyz, hw.sonneE.rgb * hw.farbe.rgb, h), Em = sonneAm(hw.mond.xyz, hw.mondE.rgb * hw.farbeM.rgb, h);
    const float g = 0.6;
    float cs = dot(hw.sonne.xyz, -v), cm = dot(hw.mond.xyz, -v);
    float ps = (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cs, 1.5) / (4.0 * L_PI), pm = (1.0 - g * g) / pow(1.0 + g * g - 2.0 * g * cm, 1.5) / (4.0 * L_PI);
    // Himmel: gestreut mit dem Mittel der Leuchtdichte über die Kugel (Glied 0 der Kugelflächenfunktionen mal Y₀)
    vec3 L = 0.9 * (Es * ps + Em * pm) + 0.9 * sh[0].rgb * 0.282095;
    farbe = vec4(min(L * hw.sonneE.w, vec3(60000.0)) * a, a);
}
