#version 460
// spiegel_misch.frag – render.water, seit S9c-1 (Seegang Werkbuch 108, SF175 A): die Spiegelung des Geländes aus halber
// Auflösung (spiegel_halb.comp, je 2 × 2 Bildpunkte) in das Bild des Netzes. Das Netz hat mit dem Himmel allein gezeichnet;
// das Bild ist affin in Lh mit dem Faktor M, also kommt w · (M · L_Gelände − M · L_Himmel) dazu (Mischen: Quelle + Ziel).
layout(set = 0, binding = 0) uniform sampler2D spA;                       // M · L_Himmel, M
layout(set = 0, binding = 1) uniform sampler2D spB;                       // Strahl, t (0: nicht vom Netz)
layout(set = 0, binding = 2, std430) readonly buffer SpiegelHalb { vec4 sh[]; };
layout(push_constant) uniform Schub { ivec4 p; };                         // p.x: Breite in halber Auflösung
layout(location = 0) out vec4 aus;
void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    if (texelFetch(spB, px, 0).w <= 0.0) discard;
    vec4 s = sh[(px.y >> 1) * p.x + (px.x >> 1)];
    if (s.w <= 0.0) discard;
    vec4 a = texelFetch(spA, px, 0);
    aus = vec4(s.w * (a.w * s.rgb - a.rgb), 0.0);
}
