// gruppe.glsl – render.air (F62 A, Werkbuch HalligHooge Teil A, 12.8): eine der fünf Gruppen der Himmelsansicht, drei
// Wellenlängen der Sonne je Einheit Bestrahlung (256 × 128, RGBA32F), ein Strahlengang mit 64 Schritten wie zuvor in
// ansicht.comp. gruppe0–4.comp setzen GRUPPE; ansicht.comp liest die fünf Bilder und bildet nach sRGB ab. So lässt
// sich die Ansicht über fünf Bilder verteilen (Himmel.verteilt).
#include "/com/dan/core/gpu/luft.glsl"
layout(local_size_x = 8, local_size_y = 8) in;
layout(set = 0, binding = 0, rgba32f) uniform writeonly image2D ziel;
layout(set = 0, binding = 1) uniform sampler2D dg;
layout(set = 0, binding = 2) uniform sampler2D jg;
layout(set = 0, binding = 3, std430) readonly buffer Werte { Himmelswerte hw; };
const int SP_NM = 471;
layout(set = 0, binding = 4, std430) readonly buffer Spektrum { vec4 sRay[5]; vec4 sOz[5]; vec4 sTafel[SP_NM]; };
const int SCHRITTE = 64;   // wie Scattering.VIEW_STEPS

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= ANSICHT_W || p.y >= ANSICHT_H) return;
    float h = hw.sonne.w;
    vec3 d = ansichtRichtung((vec2(p) + 0.5) / vec2(ANSICHT_W, ANSICHT_H), h);
    vec3 s = hw.sonne.xyz, Lg = vec3(0.0), tp;
    if (hw.sonne.y > -0.45)
        strahl(h, d, s, vec3(1.0), s, vec3(0.0), SCHRITTE, true, -1.0, hw.atm, sRay[GRUPPE].rgb, sOz[GRUPPE].rgb, dg, jg, false, Lg, tp);
    imageStore(ziel, p, vec4(Lg, 1.0));
}
