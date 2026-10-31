// shadow.frag — Wolkenschatten von oben: je Texel ein Punkt auf der Fläche der Karte (uRect.w, seit Brocken Phase 3;
// sonst Meereshöhe), 16 Schritte zum Gestirn durch die Schicht, dazu die hohe Schicht; Ergebnis der Anteil direkten
// Lichts. Kanal R wie Clouds.shadow (Java), Kanal G nur hohe Schicht und Linsen (Clouds.upperShadow) für Punkte über der
// unteren Schicht.
#include "cloud_common.glsl"
in vec2 vUV;
out vec4 frag;
uniform vec3 uLightDir;
uniform vec4 uRect;   // x, z der Ecke, Kantenlänge in m, Höhe der Fläche in m
void main() {
    float x = uRect.x + vUV.x * uRect.z, z = uRect.y + vUV.y * uRect.z, y = uRect.w;
    vec3 d = normalize(vec3(uLightDir.x, max(0.05, uLightDir.y), uLightDir.z));
    vec2 seg = shell(vec3(x, y, z), d, uType1.x, uType1.x + uType1.z);
    float tau = 0.0, up = 0.0, dummy;
    if (seg.x >= 0.0) {
        float t0 = seg.x, t1 = min(seg.y, seg.x + 40000.0), dt = (t1 - t0) / 16.0;
        for (int k = 0; k < 16; k++) {
            float t = t0 + (float(k) + 0.5) * dt;
            tau += density(vec3(x, y, z) + d * t, false, dummy) * dt;
        }
    }
    float th = max(0.0, HIGH_HEIGHT - y) / d.y;
    up += highTau(vec2(x, z) + d.xz * th) / d.y;
    for (int i = 0; i < LENS_SLICES; i++) {
        float tl = max(0.0, LENS_HEIGHT + float(i) * LENS_STEP - y) / d.y;
        up += lensTau(vec2(x, z) + d.xz * tl, i) / d.y;
    }
    frag = vec4(exp(-(tau + up) * SHADOW_SOFT), exp(-up * SHADOW_SOFT), 0.0, 1.0);
}
