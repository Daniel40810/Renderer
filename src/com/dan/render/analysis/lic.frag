#version 460
// lic.frag – render.analysis (P11c2): die LIC als Schicht auf der Wasserfläche. Aus der Tiefe der Szene der Ort des
// Bildpunkts (wie core.raum.Blick.bildpunkt umgekehrt): rel = d · (f + nx · a/t · r − ny / t · u), d = nah / Tiefe. Auf
// dem Wasser liegt der Punkt, wenn die Zelle nass ist und seine Höhe nahe am Wasserstand (Toleranz mit dem Abstand).
// η, h, LIC und der Betrag der Geschwindigkeit kommen aus dem Bild von lic.comp (linear gefiltert).
// Farbe nach dem Betrag der Geschwindigkeit, hell und dunkel nach der LIC; vormultipliziert.
layout(set = 0, binding = 0) uniform sampler2D tiefe;
layout(set = 0, binding = 1) uniform sampler2D licbild;
layout(push_constant) uniform Q {
    vec4 r;       // rechts (xyz), a/t
    vec4 u;       // oben (xyz), 1/t
    vec4 f;       // vorn (xyz), nah
    vec4 off;     // Ursprung − Auge (Ost, Nord), Höhe des Auges, dx
    ivec4 n;      // nx, ny, Breite, Höhe des Bilds
    vec4 p;       // Deckung, –, Geschwindigkeit für das Ende der Leiter (m/s), hMin
} q;
layout(location = 0) out vec4 aus;

vec3 leiter(float s) {
    s = clamp(s, 0.0, 1.0);
    const vec3 c0 = vec3(0.15, 0.20, 0.75), c1 = vec3(0.10, 0.65, 0.95), c2 = vec3(0.20, 0.85, 0.55), c3 = vec3(0.85, 0.90, 0.20), c4 = vec3(1.00, 0.60, 0.15);
    if (s < 0.25) return mix(c0, c1, s / 0.25);
    if (s < 0.5) return mix(c1, c2, (s - 0.25) / 0.25);
    if (s < 0.75) return mix(c2, c3, (s - 0.5) / 0.25);
    return mix(c3, c4, (s - 0.75) / 0.25);
}

void main() {
    float z = texelFetch(tiefe, ivec2(gl_FragCoord.xy), 0).r;
    if (z <= 0.0) discard;
    float d = q.f.w / z;
    float nxc = gl_FragCoord.x / float(q.n.z) * 2.0 - 1.0, nyc = gl_FragCoord.y / float(q.n.w) * 2.0 - 1.0;
    vec3 rel = d * (q.f.xyz + nxc * q.r.w * q.r.xyz - nyc * q.u.w * q.u.xyz);
    vec2 c = vec2(rel.x - q.off.x, -rel.z - q.off.y) / q.off.w;          // in Zellen
    if (c.x < 0.0 || c.y < 0.0 || c.x > float(q.n.x) || c.y > float(q.n.y)) discard;
    vec4 s = texture(licbild, c / vec2(q.n.xy));
    float h = s.y;
    if (h <= q.p.w) discard;
    if (abs(rel.y + q.off.z - s.x) > 0.3 + 0.002 * d) discard;           // nicht auf dem Wasser [G]
    vec3 farbe = leiter(s.w / q.p.z) * (0.25 + 1.1 * s.z);
    float a = q.p.x * min(1.0, h / 0.3);                                  // im ganz Flachen ausblenden [G]
    aus = vec4(farbe * a, a);
}
