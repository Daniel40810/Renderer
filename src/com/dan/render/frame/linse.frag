#version 460
// linse.frag – render.frame (S8a): das Bild durch die Tropfen auf der Linse. Wo Wasser auf der Scheibe liegt, zeigt der
// Bildpunkt die Szene in der Richtung, in die der Tropfen den Strahl bricht (aus der Kopie des Bilds, am Rand geklemmt);
// mit Totalreflexion ist er dunkel [G: 3 % des Bilds dahinter]. Mehr nicht: Unschärfe und Streulicht fehlen (eine Kamera
// mit kleiner Blende, scharf bis nah [G]).
#include "/com/dan/render/frame/linse.glsl"
layout(set = 0, binding = 3) uniform sampler2D kopie;
layout(location = 0) out vec4 aus;
void main() {
    vec2 uv = gl_FragCoord.xy / mehr.zw;
    vec2 uo;
    float t;
    // seit S9b-3: der Bildpunkt selbst genau (texelFetch); texture an der Mitte mischte auf manchem Gerät die Nachbarn in den
    // letzten Stellen bei, auch wo kein Tropfen liegt (lavapipe: L7b, LinseRechteckCheck)
    vec3 c = texelFetch(kopie, ivec2(gl_FragCoord.xy), 0).rgb;
    if (lDurch(uv, uo, t)) {
        vec3 hinten = texture(kopie, clamp(uo, vec2(0.0), vec2(1.0))).rgb;
        c = t > 0.0 ? hinten * t : c * 0.03;
    }
    aus = vec4(c, 1.0);
}
