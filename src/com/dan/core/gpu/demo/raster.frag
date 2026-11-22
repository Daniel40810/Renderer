#version 460
// raster.frag – GpuCheck Teil 10 (P6a): schreibt die Nummer des Dreiecks.
layout(location = 0) flat in float nummer;
layout(location = 0) out float farbe;
void main() { farbe = nummer; }
