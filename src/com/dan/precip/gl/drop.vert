// drop.vert — ein Tropfen (oder ein Spritztröpfchen) als die Spur, die er in der Belichtungszeit zieht: von seinem Ort
// vor T Sekunden bis jetzt, so breit wie er ist (abgeplattet nach Beard und Chuang, schwingend), mindestens anderthalb
// Bildpunkte. Wie viel Licht er wo abgibt, rechnet drop.frag; hier nur die Lage. Ohne Eckpuffer: sechs Ecken je Instanz.
#include "precip_common.glsl"
layout(std430, binding = 0) readonly buffer Src { vec4 src[]; };   // je Instanz drei vec4
uniform int uSource;        // 0 Tropfen (Kasten), 1 Spritztröpfchen
uniform mat4 uVP;
uniform vec3 uEye;
uniform vec3 uFwd;
uniform float uPixel;       // Breite eines Bildpunkts in m auf 1 m Abstand
uniform float uNow;         // Zeit (s) für das Alter der Tröpfchen
out vec2 vQ;                // quer (m), längs ab dem Anfang der Spur (m)
flat out vec4 vGeo;         // halbe Breite a_h, halbe Höhe a_v (m), Spurlänge L (m), Fußabdruck am Ort (Bildpunkt und Unschärfe, m)
flat out vec3 vAxis, vRight, vStart;
flat out float vFade;
out vec3 vWorld;

void hide() { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vFade = 0.0; vQ = vec2(0.0); vGeo = vec4(0.0); vAxis = vRight = vStart = vWorld = vec3(0.0); }

void main() {
    int b = gl_InstanceID * 3;
    vec4 p = src[b], v = src[b + 1], m = src[b + 2];
    float d = p.w, fade = 1.0, ratio = 1.0;
    vec3 vel = v.xyz, symm = vec3(0.0, 1.0, 0.0);
    if (uSource == 0) {
        if (m.w < 0.5) { hide(); return; }
        // zum Rand des Kastens hin ausblenden (die Schichten übernehmen), ganz nah am Auge auch (unter 8 cm)
        vec3 r = abs(p.xyz - uPA.xyz);
        float edge = max(r.x, r.z) / uPA.w;
        fade = (1.0 - smoothstep(0.8, 0.98, edge)) * (1.0 - smoothstep(0.85, 0.99, r.y / uPB.x)) * smoothstep(0.03, 0.08, length(p.xyz - uEye));
        // Form: Achse entlang der Bewegung gegen die Luft; Achsenverhältnis mit Schwingung
        vec3 w = pWind(p.xyz, uPB.y);
        symm = normalize(w - vel + vec3(0.0, 1e-5, 0.0));
        ratio = clamp(pAxisRatio(d) + m.x * sin(v.w), 0.45, 1.08);
    } else {
        float age = uNow - v.w;
        if (m.x < 0.5 || m.x > 1.5 || age < 0.0 || age > m.y) { hide(); return; }
        fade = 1.0 - smoothstep(0.8, 1.0, age / m.y);
    }
    if (fade <= 0.0) { hide(); return; }
    float rr = d * 0.5e-3;
    float av = rr * pow(ratio, 2.0 / 3.0), ah = rr * pow(ratio, -1.0 / 3.0);
    vec3 toEye = uEye - p.xyz;
    float dist = length(toEye);
    toEye /= max(dist, 1e-6);
    vec3 seg = vel * uPB.w;
    vec3 segP = seg - toEye * dot(seg, toEye);
    float L = length(segP);
    vec3 axis = L > 1e-6 ? segP / L : normalize(symm - toEye * dot(symm, toEye) + vec3(1e-6, 0.0, 0.0));
    vec3 right = normalize(cross(axis, toEye));
    float pw = pFootprint(uPixel * dist, dist);
    float wh = max(ah, 0.75 * pw), lh = max(av, 0.75 * pw);
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, 0) : c == 1 ? vec2(1, 0) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, 0) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    vec3 start = p.xyz - seg;
    float along = mix(-lh, L + lh, q.y);
    vec3 wp = start + right * (q.x * wh) + axis * along;
    vQ = vec2(q.x * wh, along);
    vGeo = vec4(ah, av, L, pw);
    vAxis = axis;
    vRight = right;
    vStart = start;
    vFade = fade;
    vWorld = wp;
    gl_Position = uVP * vec4(wp, 1.0);
}
