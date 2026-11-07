// splash.frag — Licht von Krone, Strahl und Bläschen. Eine dünne Wand aus Wasser spiegelt die Umgebung (Fresnel, am Rand
// streifend stark) und lässt den Rest durch: sie leuchtet vor dunklem Grund, glitzert in der Sonne. Die Krone ist an
// ihren Kanten (streifender Blick durch die Wand) und am Saum mit Spitzen am dichtesten; der Strahl ist eine Säule mit
// Kopf, das Bläschen ein Ring. Ausgabe vormultipliziert.
#include "precip_common.glsl"
in vec2 vQ;
flat in vec4 vInfo;
flat in vec2 vSize;
in vec3 vWorld;
out vec4 frag;
uniform vec3 uEye;
uniform float uStrength;
uniform float uPSunVis;

void main() {
    float kind = vInfo.x, t = vInfo.y;
    vec2 q = vQ;
    float a;
    if (kind < 2.5) {
        // Krone: Wand bei |x| = 1 dichter (1/√(1 − x²)), Saum oben mit 10–16 Spitzen, am Ende in Tröpfchen zerfallen
        float x = clamp(abs(q.x), 0.0, 0.999);
        if (abs(q.x) > 1.0 || q.y > 1.15) discard;
        float spikes = 10.0 + 6.0 * vInfo.z;
        float phi = asin(clamp(q.x, -1.0, 1.0));
        float top = 0.85 + 0.25 * pow(abs(sin(phi * spikes * 0.5)), 3.0);
        a = clamp(0.12 / sqrt(1.0 - x * x), 0.0, 0.8) * smoothstep(top, top - 0.08, q.y) * (1.0 - smoothstep(0.7, 1.0, t));
    } else if (kind < 3.5) {
        // Strahl: Säule mit dickerem Kopf
        float r = abs(q.x);
        float head = smoothstep(0.75, 0.95, q.y);
        float rad = mix(0.7, 1.0, head);
        a = smoothstep(rad, rad * 0.6, r) * smoothstep(1.02, 0.96, q.y) * 0.85;
    } else {
        // Bläschen: heller Ring um die Kuppe
        float r = length(vec2(q.x, q.y * 2.0 - 0.0));
        if (r > 1.0 || q.y < 0.0) discard;
        a = 0.25 + 0.6 * smoothstep(0.65, 0.95, r);
        a *= smoothstep(1.0, 0.92, r) * (1.0 - smoothstep(0.8, 1.0, t));
    }
    a *= vInfo.w * uStrength;
    if (a < 1e-4) discard;
    vec3 V = normalize(vWorld - uEye);
    vec3 col = pDropRadiance(V, uPSunVis);
    // Glanz: die Wand ist zum Teil Spiegel (streifend bis rund 30 %)
    vec3 R = reflect(V, normalize(vec3(-V.x, 0.15, -V.z)));
    col += uPSunE * uPSunVis * 0.08 * pow(max(0.0, dot(R, uPSunDir)), 64.0) * 10.0;
    frag = vec4(col * a, a);
}
