#version 460
// gischt.vert – render.schwimmer (S8a): je Tropfen eine Tafel aus sechs Ecken, ohne Eckpuffer (Instanz = Tropfen).
// Spritzwasser als Streifen längs der Bewegung über die Belichtungszeit (Bewegungsunschärfe), mindestens einen Bildpunkt
// breit; Nebel als weicher Ball, der wächst. Wie spray/gl/spray.vert, ohne Wände (die Gischt fliegt vom Rumpf weg).
#include "/com/dan/render/schwimmer/gischt.glsl"
layout(location = 0) out vec2 vUv;
layout(location = 1) out float vAlpha;
layout(location = 2) out vec3 vRel;
layout(location = 3) flat out int vArt;
layout(location = 4) flat out float vZufall;
layout(location = 5) flat out float vAlter;
layout(location = 6) flat out vec3 vL;          // seit L6a: Leuchtdichte mal Vorbelichtung mal Durchlässigkeit der Luft bis zum Tropfen

void aus() { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vUv = vec2(0.0); vAlpha = 0.0; vRel = vec3(0.0); vArt = 0; vZufall = 0.0; vAlter = 0.0; vL = vec3(0.0); }

void main() {
    int per = int(werte.y + 0.5);
    int k = gl_InstanceIndex / per, i = gl_InstanceIndex % per;
    Buendel P = bu[k];
    int kind = int(P.f.w + 0.5);
    if (kind == 1 && i >= int(werte.z + 0.5)) { aus(); return; }
    vec3 pos, vel;
    float size;
    bool alive;
    gTropfen(P, i, pos, vel, size, alive);
    if (!alive) { aus(); return; }
    int c = gl_VertexIndex % 6;
    const vec2 E[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));
    vec2 q = E[c];
    float d = max(length(pos), 0.05);
    vec3 zumAuge = -pos / d;
    float age = max(0.0, P.a.w), life = P.b.w;
    float pix = mehr.w * d;                                                  // ein Bildpunkt in dieser Entfernung (m)
    vec3 rechts, oben;
    if (kind == 1) {
        rechts = normalize(cross(zumAuge, vec3(0.0, 1.0, 0.0)) + vec3(1e-5, 0.0, 0.0));
        oben = cross(rechts, zumAuge);
        rechts *= size;
        oben *= size;
        vAlpha = smoothstep(0.0, 1.2, age) * (1.0 - smoothstep(life * 0.5, life, age));
    } else {
        vec3 v = vel * werte.x;
        float len = length(v);
        vec3 achse = len > 1e-4 ? v / len : vec3(0.0, 1.0, 0.0);
        float b = max(size, pix);
        rechts = normalize(cross(achse, zumAuge) + vec3(0.0, 1e-5, 0.0)) * b * 0.5;
        oben = achse * (len * 0.5 + b * 0.5);
        pos += v * 0.5;
        // kleiner als ein Bildpunkt: die Deckkraft im Verhältnis der Fläche (die Tafel bleibt einen Bildpunkt breit)
        vAlpha = 0.75 * (1.0 - smoothstep(life * 0.7, life, age)) * clamp(size / max(b, len * 0.3), 0.05, 1.0) * min(1.0, size / b);
    }
    vec3 w = pos + rechts * q.x + oben * q.y;
    vRel = w;
    vUv = q;
    vArt = kind;
    vZufall = gRnd(floatBitsToUint(P.d.x), i, 10);
    vAlter = age;
    vL = min(gLicht(pos / d, kind) * hw.sonneE.w * exp(-werte.w * d), vec3(60000.0));
    gl_Position = vp * vec4(gKrumm(w), 1.0);
}
