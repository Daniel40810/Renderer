#version 460
// netz.vert – render.water, seit S3c (Seegang Werkbuch): das Netz der Clipmap des Seegangs. Je Stufe (Instanz) ein Gitter
// aus (n − 1)² Vierecken zu zwei Dreiecken; die Stufen außer der letzten reichen über die Stellen 0 … n − 2 (eine gerade
// Spanne, deren Rand auf der gröberen liegt), die gröbere spart die Vierecke unter der feineren aus, bis auf deren
// äußersten Ring (gegen Lücken an der T-Naht). Ausgesparte Dreiecke
// fallen auf einen Punkt außerhalb des Bilds. Projektion wie wasser.frag (bildpunkt), Tiefe umgekehrt: vorn.w / z.
#include "/com/dan/render/water/wasser.glsl"
#include "/com/dan/render/water/netz.glsl"
layout(set = 0, binding = 18, std430) readonly buffer Scheitel { vec4 scheitel[]; };
layout(location = 0) out vec3 vP;
layout(location = 1) out vec2 vX0;
layout(location = 2) out float vEta;
layout(location = 3) out float vWelle;
layout(location = 4) flat out int vL;
layout(location = 5) out float vNah;     // seit S3c-5 (B47): 1 nass, 0,5 nah am Wasser, 0 weit davon (netz.comp)
layout(location = 6) flat out uint vId;  // seit S3c-6 (N6): Nummer des Dreiecks über alle Stufen, ab 1 (Sieger im Vorlauf)

void main() {
    int L = gl_InstanceIndex, n = snN.y, st = snN.x;
    int m = L == st - 1 ? n - 1 : n - 2;
    int q = gl_VertexIndex / 6, e = gl_VertexIndex - 6 * q;
    int qi = q % (n - 1), qj = q / (n - 1);
    bool weg = snN.z != 1 || qi >= m || qj >= m;
    if (!weg && L > 0) {
        vec4 lf = snLage[L - 1], la = snLage[L];
        int h0 = int(round((lf.x - la.x) / la.z)), h1 = int(round((la.y - lf.y) / la.z)), hb = (n - 2) / 2;
        // die gröbere spart nur das Innere aus: ihr äußerster Ring liegt unter dem Rand der feineren (dort sind beide
        // gleich) und deckt die Bildpunkte, die an der T-Naht durch Rundung zwischen den Dreiecken fallen [G]
        weg = qi > h0 && qi < h0 + hb - 1 && qj > h1 && qj < h1 + hb - 1;
    }
    vL = L;
    vId = uint((L * (n - 1) * (n - 1) + q) * 2 + e / 3 + 1);
    if (weg) {
        gl_Position = vec4(-2.0, -2.0, 0.5, 1.0);
        vP = vec3(0.0); vX0 = vec2(0.0); vEta = 0.0; vWelle = 0.0; vNah = 0.0;
        return;
    }
    // (0,0) (1,0) (1,1) | (0,0) (1,1) (0,1)
    int dx = (e == 1 || e == 2 || e == 4) ? 1 : 0, dy = (e == 2 || e == 4 || e == 5) ? 1 : 0;
    int o = 2 * ((L * n + qj + dy) * n + qi + dx);
    vec4 s0 = scheitel[o], s1 = scheitel[o + 1];
    vec3 Q = s0.xyz;
    vP = Q;
    vX0 = s1.xy;
    vEta = s0.w;
    vWelle = s1.z;
    vNah = s1.w;
    float z = dot(Q, vorn.xyz);
    gl_Position = vec4(dot(Q, rechts.xyz) / dot(rechts.xyz, rechts.xyz), -dot(Q, oben.xyz) / dot(oben.xyz, oben.xyz), vorn.w, z);
}
