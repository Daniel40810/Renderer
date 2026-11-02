#version 450
// vessel.frag — Licht wie forest/buoyancy: Sonne (lambertsch, beidseitig), Himmel von oben, Dunst; linear oder mit ACES.
in vec3 vW, vN, vC;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uFogColor;
uniform float uFogDist, uAlpha, uExposure, uGlanz;
uniform int uTonemap;
out vec4 frag;
void main() {
    vec3 n = normalize(vN), v = normalize(uCam - vW);
    if (dot(n, v) < 0.0) n = -n;
    float d = max(dot(n, normalize(uSunDir)), 0.0);
    vec3 col = vC * (uSunColor * d + uSkyColor * (0.55 + 0.45 * n.y));
    if (uGlanz > 0.0) {
        vec3 h = normalize(normalize(uSunDir) + v);
        float f = 0.02 + 0.98 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        col = mix(col, uSkyColor * 1.4, f * uGlanz) + uSunColor * pow(max(dot(n, h), 0.0), 120.0) * uGlanz;
    }
    // Kanten vom Licht selbst (Topplicht): Farben über 1 leuchten
    if (max(vC.r, max(vC.g, vC.b)) > 1.5) col = vC;
    float dist = length(uCam - vW);
    col = mix(col, uFogColor, 1.0 - exp(-dist / uFogDist));
    if (uTonemap == 1) {
        vec3 x = col * uExposure;
        col = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
    }
    frag = vec4(col, uAlpha);
}
