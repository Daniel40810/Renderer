#version 460
// kachel.frag – render.overlay (W2): die Kachel ist schon linear vormultipliziert (RGBA16F); mit der Deckung gewichtet.
// Mischen: Quelle + (1 − α) · Ziel.
layout(set = 0, binding = 1) uniform sampler2D kachel;
layout(location = 0) in vec2 uv;
layout(location = 1) in float deckung;
layout(location = 0) out vec4 aus;
void main() {
    vec4 c = texture(kachel, uv) * deckung;
    if (c.a <= 0.0) discard;
    aus = c;
}
