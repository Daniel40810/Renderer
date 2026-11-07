// mb_velocity.frag — Bewegungsunschärfe (com.dan.shutter, McGuire 2012): je Bildpunkt die halbe Bewegung während der
// Belichtung V (Bildpunkte), aus der Tiefe und dem Blick des vorigen Bilds (die Kamera bewegt sich), mal T / Δt; Länge
// höchstens die Kachel k (so reicht die Nachbarschaft der Kacheln). Dazu der Abstand (m) für den Tiefenvergleich.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDepth;
uniform mat4 uReproj;           // Bildraum jetzt -> Clip des vorigen Bilds
uniform int uReversed;
uniform float uNear, uFar;
uniform float uScale;           // T / Δt
uniform float uK;
uniform vec2 uSize;
float viewDepth(float d) {
    if (uReversed == 1) return uNear / max(d, 1e-12);
    float ndc = d * 2.0 - 1.0;
    return 2.0 * uNear * uFar / ((uFar + uNear) - ndc * (uFar - uNear));
}
void main() {
    float d = texture(uDepth, vUV).r;
    vec2 ndc = vUV * 2.0 - 1.0;
    float z = uReversed == 1 ? d : d * 2.0 - 1.0;
    vec4 pc = uReproj * vec4(ndc, z, 1.0);
    vec2 prev = abs(pc.w) > 1e-12 ? pc.xy / pc.w : ndc;
    vec2 v = (ndc - prev) * 0.5 * uSize * uScale;     // Weg während der Belichtung (Bildpunkte)
    vec2 hv = 0.5 * v;
    float l = length(hv);
    if (l > uK) hv *= uK / l;
    if (l < 0.05) hv = vec2(0.0);
    frag = vec4(hv, viewDepth(d), 0.0);
}
