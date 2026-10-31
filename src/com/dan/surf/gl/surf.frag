#version 430 core
// surf.frag — Querschnitt durch einen ebenen Hang mit flacher Welle (Hülle, Sylt Phase 1).
// Seeseite links, Strand rechts; Höhe der Welle nach Green (h^-1/4), begrenzt durch gamma * h; weiß, wo sie bricht.
in vec2 vUV;
out vec4 frag;
uniform float uHs, uTp, uSlope, uGamma, uTime;
void main() {
    float x = vUV.x * 400.0;                 // m, 0 = Seeseite
    float bed = -10.0 + x * uSlope;          // Grund, m
    float h = max(0.0, -bed);
    float hShoal = uHs * pow(max(h, 0.05) / 10.0, -0.25);
    float H = min(hShoal, uGamma * h);
    float k = 6.2831853 / (9.81 * uTp * uTp / 6.2831853) * max(1.0, sqrt(10.0 / max(h, 0.3)));
    float eta = 0.5 * H * sin(k * x - 6.2831853 / uTp * uTime);
    float y = mix(-12.0, 4.0, vUV.y);
    vec3 col = vec3(0.55, 0.70, 0.78);                               // Luft
    if (y < eta && y > bed) col = mix(vec3(0.05, 0.25, 0.27), vec3(0.20, 0.55, 0.50), (y - bed) / max(h, 0.1));
    if (y < eta && y > eta - 0.25 && hShoal >= uGamma * h && h > 0.0) col = vec3(0.95);   // bricht
    if (y <= bed) col = vec3(0.85, 0.76, 0.56);                       // Sand
    frag = vec4(col, 1.0);
}
