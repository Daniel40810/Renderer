#version 460
// linie.frag – render.analysis (P11c2): Farbe mit Deckung, vormultipliziert (Mischen: Quelle + (1 − α) · Ziel).
layout(location = 0) in vec4 farbe;
layout(location = 0) out vec4 aus;
void main() { aus = vec4(farbe.rgb * farbe.a, farbe.a); }
