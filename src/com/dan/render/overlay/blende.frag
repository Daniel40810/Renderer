#version 460
// blende.frag – render.overlay (W3): das gemerkte Bild vor einem Schnitt über dem neuen, mit der Deckung d.
// Das gemerkte Bild hat das Format der Ausgabe (sRGB); gelesen wird es linear, gemischt vormultipliziert.
layout(set = 0, binding = 0) uniform sampler2D gemerkt;
layout(push_constant) uniform Q { vec4 p; } q;     // Deckung, –, –, –
layout(location = 0) out vec4 aus;
void main() {
    vec3 c = texelFetch(gemerkt, ivec2(gl_FragCoord.xy), 0).rgb;
    float d = clamp(q.p.x, 0.0, 1.0);
    aus = vec4(c * d, d);
}
