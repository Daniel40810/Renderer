// wg_normal.frag — die fertige Karte: Höhe (mm), Film (0..1), Steigung dh/dx, dh/dy aus Mittendifferenzen (auf einer
// Kachel über den Rand hinweg).
in vec2 vUv;
out vec4 frag;
uniform sampler2D uHeight;   // R16F, Höhe in mm
uniform sampler2D uFilm;     // RGBA32F grob: Film (R), Feintropfen (G), Beschlag (B), Salz (A)
uniform vec2 uTexelMm;       // Kantenlänge eines Texels in mm (x, y)
uniform int uPeriodic;
ivec2 at(ivec2 p, ivec2 n) { return uPeriodic == 1 ? (p + n) % n : clamp(p, ivec2(0), n - 1); }
void main() {
    ivec2 n = textureSize(uHeight, 0);
    ivec2 p = ivec2(gl_FragCoord.xy);
    float h = texelFetch(uHeight, p, 0).r;
    float l = texelFetch(uHeight, at(p - ivec2(1, 0), n), 0).r, r = texelFetch(uHeight, at(p + ivec2(1, 0), n), 0).r;
    float d = texelFetch(uHeight, at(p - ivec2(0, 1), n), 0).r, u = texelFetch(uHeight, at(p + ivec2(0, 1), n), 0).r;
    frag = vec4(h, texture(uFilm, vUv).r, (r - l) / (2.0 * uTexelMm.x), (u - d) / (2.0 * uTexelMm.y));
}
