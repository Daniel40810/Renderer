// splat.vert — Kino-Stufe (com.dan.shutter), seit W9c Runde 2: ein Licht aus lights.comp als ganzer Unschärfekreis, je
// Farbe eine Instanz (3 je Licht). Der Kreis ist das Bild der Pupille durch dieses Licht: der Pupillenort p landet bei
// Mitte + X(ρ)/ρ · p, X(ρ) = S_c ρ − TA_best(ρ) (Bildpunkte; S_c mit dem Längsfarbfehler der Farbe c), so wie accumulate.frag
// die Teilbilder verschiebt. Das Viereck deckt den größten Halbmesser. splat.frag kehrt die Abbildung um.
layout(std430, binding = 2) readonly buffer Lights { vec4 L[]; };
uniform int uCap;
uniform float uTA[17];          // TA_best(ρ) an ρ = k/16 · uRhoTop (Bildpunkte)
uniform float uRhoTop;          // größter Halbmesser der Pupille (Umkreis, Fläche π)
uniform vec3 uKc;               // Längsfarbfehler: Bildpunkte je Einheit der Pupille, rot, 0, blau
uniform float uSqueezeP;        // Stauchung der Pupille
out vec2 vOff;
flat out vec3 vCol;
flat out float vSc;
flat out vec2 vCenter;

void main() {
    int id = gl_InstanceID, i = id / 3, ch = id - 3 * (id / 3);
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vOff = vec2(0.0); vCol = vec3(0.0); vSc = 0.0; vCenter = vec2(0.0);
    if (i >= uCap) return;
    vec4 a = L[2 * i], b = L[2 * i + 1];
    float S = a.z - (ch == 0 ? uKc.x : (ch == 2 ? uKc.z : 0.0));
    float rmax = 0.0;
    for (int k = 0; k <= 16; k++) rmax = max(rmax, abs(S * (float(k) / 16.0 * uRhoTop) - uTA[k]));
    float e = ch == 0 ? b.r : (ch == 1 ? b.g : b.b);
    if (rmax < 0.5 || e <= 0.0) return;
    vCol = (ch == 0 ? vec3(1.0, 0.0, 0.0) : (ch == 1 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0))) * e;
    vSc = S;
    vCenter = a.xy;
    vec2 corner = vec2(float(gl_VertexID & 1), float(gl_VertexID >> 1)) * 2.0 - 1.0;
    vec2 R = vec2(rmax, rmax * max(uSqueezeP, 1.0)) + 1.5;
    vOff = corner * R;
    gl_Position = vec4((a.xy + vOff) / uSize * 2.0 - 1.0, 0.0, 1.0);
}
