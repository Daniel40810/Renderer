#version 460
// feuer.frag – render.schwimmer (S7e1): Leuchtdichte eines Lichts im Bild. Die Beleuchtungsstärke am Auge E (lx) verteilt
// sich über den Raumwinkel: L = E · p(r) mit ∫ p dΩ = 1 (Kern als Gauß-Glocke, Hof nach 1/(1 + r²/w²)², Anteil werte.y; das
// Spiegelbild als gestreckte Gauß-Glocke ohne Hof). Ausgabe Leuchtdichte (cd/m²) mal Vorbelichtung, dazugezählt
// (vormultipliziert, α = 0).
#include "/com/dan/render/schwimmer/feuer.glsl"
layout(location = 0) in vec2 vUv;
layout(location = 1) in vec3 vE;
layout(location = 2) flat in vec2 vArt;
layout(location = 3) flat in float vOben;
layout(location = 0) out vec4 farbe;
void main() {
    float alpha = obenV.w, pre = hw.sonneE.w;
    vec3 v;
    if (vArt.x < 0.5) {
        float r2 = dot(vUv, vUv) * alpha * alpha;                           // rad²
        float s = KERN * alpha, w = HOF * alpha, h = werte.y;
        float kern = exp(-0.5 * r2 / (s * s)) / (2.0 * L_PI * s * s);
        float q = 1.0 + r2 / (w * w);
        float hof = 1.0 / (L_PI * w * w * q * q);
        // seit L5e1: der Hof läuft am Rand des Vierecks auf null aus (vorher war das Viereck bei Nacht als Kante zu sehen)
        float f = clamp(1.0 - dot(vUv, vUv) / (HOF_RAND * HOF * HOF_RAND * HOF), 0.0, 1.0);
        v = vE * ((1.0 - h) * kern + h * hof * f * f) * pre;
    } else {
        float sx = 1.5 * KERN * alpha, sy = sx * vArt.y;
        vec2 r = vUv * alpha;
        float p = exp(-0.5 * (r.x * r.x / (sx * sx) + r.y * r.y / (sy * sy))) / (2.0 * L_PI * sx * sy);
        p *= clamp(vOben - vUv.y + 0.5, 0.0, 1.0);                           // seit L5e1 (Befund 4): nicht über das Wasser unter dem Licht
        vec2 qn = vUv / vec2(6.0 * KERN, 6.0 * KERN * vArt.y);                // −1 … 1 im Viereck
        float f = clamp(1.0 - qn.x * qn.x, 0.0, 1.0) * clamp(1.0 - qn.y * qn.y, 0.0, 1.0);
        v = vE * p * f * f * pre;
    }
    farbe = vec4(min(v, vec3(60000.0)), 0.0);
}
