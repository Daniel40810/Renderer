// forest_shadow.frag — nur Tiefe für die Sonnenschattenkarte; seit W6 Runde 7 schneiden Karten (Bartflechten) nach ihrer
// Deckung aus (gewürfelt wie in forest.frag).
in vec3 vCard;
// seit W6 Runde 7: Karten der Bartflechten. Mit Textur (uCardOn): Bild der Ebene, Alpha = Deckung, gewürfelt (die
// Kantenglättung über die Zeit mittelt); ohne: Fäden aus Rauschen, oben schmal, unten breiter und spitz auslaufend.
uniform sampler2DArray uCardTex;
uniform int uCardOn, uCardFrame;
vec4 cardSample(vec3 c) {
    if (uCardOn == 1) return texture(uCardTex, c);
    float u = c.x - 0.5, v = c.y;
    float breite = 0.5 * (0.2 + 0.8 * min(1.0, v * 2.5));
    float faden = fract(c.x * 23.0 + 0.6 * sin(v * 9.0 + c.x * 31.0) + 0.3 * sin(v * 23.0));
    float a = step(abs(u), breite) * smoothstep(0.75, 0.9, faden) * (1.0 - smoothstep(0.75, 1.0, v));
    return vec4(0.30, 0.30, 0.17, a);
}
float cardDither() {
    vec2 q = gl_FragCoord.xy + vec2(5.588, 3.177) * float(uCardFrame & 63);
    return fract(52.9829189 * fract(dot(q, vec2(0.06711056, 0.00583715))));
}
void main() {
    if (vCard.z >= 0.0 && cardSample(vCard).a < max(cardDither(), 0.02)) discard;
}
