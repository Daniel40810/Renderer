#version 460
// netz_vorlauf.frag – render.water, seit S3c-6 (Seegang Werkbuch 22, N6): Vorlauf der Tiefe für das Netz der Clipmap. Je
// Bildpunkt nur der Fuß (netz_fuss.glsl: η_Bild, Wegfallen, B47); er schreibt die Tiefe und als Farbe die Nummer seines
// Dreiecks. Was die Prüfung der Tiefe zuletzt durchlässt, ist derselbe Bildpunkt, der ohne Vorlauf die Farbe gesetzt
// hätte; netz.frag schattiert danach nur ihn (Schalter Wasserbild.vorlauf).
#define SWASH_BINDUNG 22    // seit S6c-2: das Bild des Auflaufs (wasser.glsl), wie netz.frag
#include "/com/dan/render/water/wasser.glsl"
#include "/com/dan/render/water/netz_fuss.glsl"
layout(location = 0) in vec3 vP;
layout(location = 1) in vec2 vX0;
layout(location = 2) in float vEta;
layout(location = 3) in float vWelle;
layout(location = 4) flat in int vL;
layout(location = 5) in float vNah;
layout(location = 6) flat in uint vId;
layout(location = 0) out uint sieger;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    zaehle(px, 0u);
    vec3 P, d;
    float t, eta, tiefe;
    bool b47;
    if (!netzFuss(vP, vEta, vWelle, vNah, gl_FragCoord.z, P, d, t, eta, tiefe, b47)) discard;
    gl_FragDepth = tiefe;
    sieger = vId;
}
