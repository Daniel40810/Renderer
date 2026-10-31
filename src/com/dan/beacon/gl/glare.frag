// glare.frag — Lichtpunkt: ein scharfer Kern (trägt 90 % des Lichts, Strahldichte = Beleuchtungsstärke durch
// Raumwinkel eines Bildpunkts) und der Schleier aus Streulicht im Auge (Stiles-Holladay). Additiv.
in vec2 vPix;
flat in vec4 vE;
flat in float vCore;
out vec4 frag;
uniform float uPixelSr;        // Raumwinkel eines Bildpunkts
uniform float uSize;           // halbe Kantenlänge des Quadrats in Bildpunkten
uniform float uDegPerPx;       // Grad je Bildpunkt
void main() {
    float r = length(vPix);
    const float TAU = 6.28318530718;
    // Kern: 90 % in einem Bildpunkt (Gauß, σ 0,7 px)
    float core = vCore * 0.90 * exp(-r * r / (2.0 * 0.7 * 0.7)) / (TAU * 0.7 * 0.7) / uPixelSr;
    // Streulicht nach Stiles-Holladay (Auge: L = 10 · E / θ², θ in Grad); für ein Kamerabild ein Drittel davon, unter 0,15°
    // begrenzt, zum Rand weich auf null
    float th = r * uDegPerPx;
    float edge = 1.0 - smoothstep(0.6, 1.0, r / uSize);
    float veil = 3.0 / (th * th + 0.15 * 0.15) * edge;
    vec3 L = vE.rgb * (core + veil);
    frag = vec4(min(L, vec3(60000.0)), 0.0);
}
