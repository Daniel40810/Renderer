// tree_shadow.frag — seit LituyaBay1958 W6 Runde 5: nur Tiefe für eine Sonnenschattenkarte; Laub mit Textur schneidet
// an seiner Deckung aus wie in tree.frag, sonst wirft das ganze Blattviereck Schatten.
in vec2 vUv;
in float vTex;
uniform sampler2D uLeafTex;
void main() {
    if (vTex > 1.5 && texture(uLeafTex, vUv).a < 0.5) discard;
}
