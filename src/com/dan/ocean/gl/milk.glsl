// milk.glsl — com.dan.ocean (aus LituyaBay1958, Werkbuch Welle W2): Gletschermilch. Anteil 0..1 je Ort aus Grundton und
// Quellen (Gilbert Inlet, Zuflüsse) und das Streulicht des Gesteinsmehls im Wasser. Uniforms: MilkGl.bind. Gleiche
// Rechnung wie GlacialMilk im Kern.
uniform vec4 uMilkSrc[4];   // x, z, Reichweite (m), Stärke
uniform int uMilkN;
uniform float uMilkBase, uMilkK;
uniform vec3 uMilkColor;

float oceanMilk(vec2 xz) {
    float s = uMilkBase;
    for (int i = 0; i < 4; i++) {
        if (i >= uMilkN) break;
        vec4 q = uMilkSrc[i];
        s += q.w * exp(-dot(xz - q.xy, xz - q.xy) / (q.z * q.z));
    }
    return clamp(s, 0.0, 1.0);
}

// Streulicht im Wasser: eDown Bestrahlung von oben (Himmel und Sonne), F der Anteil der Spiegelung
vec3 oceanMilkLight(vec2 xz, vec3 eDown, float F) {
    return uMilkColor * eDown * uMilkK * oceanMilk(xz) * (1.0 - F);
}
