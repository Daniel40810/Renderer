#version 450
// wire.frag — Draht (Stahl) und Leine (Tauwerk) als Zylinder geschattet, Deckung vormultipliziert.
in vec3 vW, vC, vT;
in float vAcross, vCover;
flat in int vM;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uGroundColor, uFogColor;
uniform float uFogDist, uSunVis, uWet;
out vec4 frag;
void main() {
    vec3 v = normalize(uCam - vW);
    vec3 side = normalize(cross(vT, v));
    float x = clamp(vAcross, -1.0, 1.0);
    vec3 n = normalize(side * x + v * sqrt(max(0.0, 1.0 - x * x)));
    vec3 l = normalize(uSunDir);
    float nl = max(dot(n, l), 0.0);
    vec3 hemi = mix(uGroundColor, uSkyColor, 0.5 + 0.5 * n.y);
    bool steel = vM == 4;
    vec3 base = vC * (steel ? 1.0 : 0.9 + 0.2 * sin(dot(vW, vT) * 140.0));
    vec3 col = base * (uSunColor * nl * uSunVis + hemi * 0.85);
    // Glanz längs des Drahts (Kajiya-Kay)
    vec3 hv = normalize(l + v);
    float th = sqrt(max(0.0, 1.0 - pow(dot(vT, hv), 2.0)));
    col += uSunColor * uSunVis * pow(th, steel ? 80.0 : 12.0) * (steel ? 0.6 : 0.05) * (0.3 + 0.7 * uWet + (steel ? 0.7 : 0.0));
    float edge = 1.0 - smoothstep(0.75, 1.0, abs(vAcross));
    float a = vCover * edge;
    float dist = length(uCam - vW);
    col = mix(col, uFogColor, 1.0 - exp(-dist / uFogDist));
    frag = vec4(col * a, a);
}
