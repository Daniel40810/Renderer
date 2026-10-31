// pattern.frag — die Lichtverteilung des Feuers als Rundumbild (RGB cd, ohne Vorbelichtung). Jedes Texel rechnet
// Beacon.Snapshot.intensityAzEl in seiner Mitte: Sektortabelle bei der Peilung von See, Höhenprofil der Linse, bei der
// Drehlinse die Summe der Strahlen, Helligkeit und Farbe des Augenblicks.
in vec2 vUV;
out vec4 frag;
uniform sampler1D uLut;        // RGB cd je 0,5° Peilung
uniform sampler1D uProfile;    // Höhenprofil −90..90° in 0,05°
uniform float uPanels[64];
uniform int uPanelCount;
uniform vec4 uState;           // Optik, Drehwinkel, Verstärkung, Helligkeit
uniform vec3 uTint;

float profileAt(float e) {
    float n = float(textureSize(uProfile, 0));
    return textureLod(uProfile, ((e + 90.0) / 0.05 + 0.5) / n, 0.0).r;
}

float wrap180(float a) { return a - 360.0 * floor((a + 180.0) / 360.0); }

void main() {
    vec2 ts = vec2(textureSize(uLut, 0), 1.0);
    float az = vUV.x * 360.0;
    float s = vUV.y * 2.0 - 1.0;
    float el = sign(s) * s * s * 90.0;
    vec3 c = textureLod(uLut, fract((az + 180.0) / 360.0), 0.0).rgb;
    float v = profileAt(el) * uState.w;
    if (uState.x > 0.5) {
        float h = 0.0;
        for (int i = 0; i < 64; i++) {
            if (i >= uPanelCount) break;
            h += profileAt(wrap180(az - uPanels[i] - uState.y));
        }
        v *= uState.z * h;
    }
    frag = vec4(c * v * uTint, 1.0);
}
