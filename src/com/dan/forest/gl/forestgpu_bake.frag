// forestgpu_bake.frag — Farbe (linear, in eine sRGB-Textur) mit Deckung, Normale im Modellraum (0..1) mit Laubanteil.
in vec3 vN;
in vec3 vCol;
in float vLeafy;
uniform vec3 uDir;
layout(location = 0) out vec4 oAlb;
layout(location = 1) out vec4 oNrm;
void main() {
    vec3 n = normalize(vN);
    if (vLeafy > 0.25) { if (dot(n, uDir) < 0.0) n = normalize(n - 1.3 * dot(n, uDir) * uDir); }
    else if (!gl_FrontFacing) n = -n;
    oAlb = vec4(vCol, 1.0);
    oNrm = vec4(n * 0.5 + 0.5, max(vLeafy, 0.0));   // Holz mit Rindenmuster trägt negative Werte (W6 Runde 5)
}
