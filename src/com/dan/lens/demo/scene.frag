// scene.frag — die Prüfszene der Demo (com.dan.lens): Himmel mit Sonne, Kai mit Schachbrett, eine Lichterkette von 3 bis 120 m,
// ein Kasten im Fokus, ein Pfahl ganz vorn. Schreibt HDR-Farbe (Einheiten der Szene) und die Tiefe (umgekehrt: near / z).
in vec2 vUV;
out vec4 frag;
uniform vec2 uSize;
uniform float uFocal;           // mm, Bildhöhe 24 mm
uniform vec2 uSunPx;            // Sonne im Bild (Bildpunkte, unten links)
uniform float uSunE;
uniform float uNearS;
void main() {
    vec2 px = gl_FragCoord.xy;
    float pitch = 24.0 / uSize.y;
    vec2 t = (px - 0.5 * uSize) * pitch / uFocal;          // Richtung (tan)
    float z = 1e9;
    float ty = clamp(t.y, 0.0, 1.0);
    vec3 c = mix(vec3(0.55, 0.62, 0.75), vec3(0.18, 0.28, 0.5), ty);
    float rs = uFocal * 0.004652 / pitch;
    if (length(px - uSunPx) < rs) c = vec3(uSunE / (3.14159265 * 0.004652 * 0.004652));
    float hc = 1.6;
    if (t.y < -1e-4) {
        float zg = hc / -t.y;
        if (zg < 3000.0) {
            float xw = t.x * zg;
            float chk = mod(floor(xw) + floor(zg), 2.0);
            float g = 0.06 + 0.10 * chk;
            c = vec3(g * 0.9, g, g * 0.8);
            z = zg;
        }
    }
    float zb = 8.0;
    if (abs(t.x * zb + 0.8) < 0.6 && t.y * zb > -hc && t.y * zb < -hc + 1.0) {
        c = vec3(0.5 + 0.3 * mod(floor(t.x * zb * 10.0), 2.0), 0.3, 0.2);
        z = zb;
    }
    for (int i = 0; i < 10; i++) {
        float d = float[](3.0, 5.0, 8.0, 12.0, 18.0, 27.0, 40.0, 60.0, 90.0, 120.0)[i];
        vec2 lp = 0.5 * uSize + vec2(2.5 / d, (2.5 - hc) / d) * uFocal / pitch;
        float rr = max(1.2, 0.06 * uFocal / pitch / d);
        if (length(px - lp) < rr && d < z) { c = vec3(300.0, 210.0, 120.0); z = d; }
    }
    if (abs(t.x * 1.2 + 0.12) < 0.04 && t.y * 1.2 < 0.5 && 1.2 < z) { c = vec3(0.03, 0.025, 0.02); z = 1.2; }
    frag = vec4(c, 1.0);
    gl_FragDepth = z > 1e8 ? 0.0 : uNearS / z;
}
