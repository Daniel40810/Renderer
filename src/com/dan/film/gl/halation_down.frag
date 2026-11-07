// halation_down.frag — Halation, Schritt 1 (com.dan.film): die Belichtung der rotempfindlichen Schicht H_R (relativ zur
// Graukarte) auf den kleinen Puffer gemittelt, in dem der Ring rund Film.HALATION_TEXELS Bildpunkte misst. Ein Bildpunkt
// des Puffers mittelt die Fläche, die er im HDR-Bild deckt (Gitter aus bilinearen Proben, je 2 × 2 Bildpunkte, höchstens
// 8 × 8). So bleibt die Energie eines Lichts erhalten, auch wenn es nur einen Bildpunkt groß ist.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform float uExposure;        // Belichtung der App · Flackern · 2^Blenden / 0,18
uniform mat3 uMatrix;           // sRGB → Schichten (Zeilen)
uniform vec2 uBuf;              // Größe des Puffers
void main() {
    vec2 sz = vec2(textureSize(uHdr, 0));
    vec2 foot = sz / uBuf;
    ivec2 n = ivec2(clamp(ceil(foot * 0.5), vec2(1.0), vec2(8.0)));
    vec2 c = vUV * sz;
    float acc = 0.0;
    for (int j = 0; j < n.y; j++)
        for (int i = 0; i < n.x; i++) {
            vec2 o = (vec2(i, j) + 0.5) / vec2(n) - 0.5;
            vec3 rgb = max(textureLod(uHdr, (c + o * foot) / sz, 0.0).rgb, vec3(0.0));
            acc += (uMatrix * rgb).r;
        }
    frag = vec4(acc / float(n.x * n.y) * uExposure, 0.0, 0.0, 1.0);
}
