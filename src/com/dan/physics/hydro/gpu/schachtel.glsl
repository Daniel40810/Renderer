// schachtel.glsl — Lage des feinen Gitters im groben (Schachtel auf der Karte, Werkbuch 12.5, Stand P4c).
// Eingebunden von geist.comp (eigene Bindungen) und, nach hydro.glsl, von den Kernels, die das grobe Gitter schreiben
// (Bindung 20).
layout(std430, binding = SCHACHTEL_BINDUNG) readonly buffer SchachtelWerte {
    int nxC, nyC, nxF, nyF, r, i0, j0, ni, nj, nRandF, seiten, nAussen;   // seiten: Bit s, wenn die Seite s geschachtelt ist
} K;
