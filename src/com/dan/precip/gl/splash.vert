// splash.vert — Kronen, Strahlen und Bläschen als Tafeln zum Auge hin (Tröpfchen zeichnet drop.vert). Krone: dünne
// Wand aus Wasser, die in ihrer Lebenszeit auf 3 D aufgeht und mit 2 D Höhe wieder einsinkt; Strahl: Säule, die steigt
// und fällt; Bläschen: Halbkugel auf dem Wasser. Kleiner als ein Bildpunkt: auf ihn verteilt, gleiche Fläche.
#include "precip_common.glsl"
struct Part { vec4 p; vec4 v; vec4 m; };
layout(std430, binding = 0) readonly buffer Parts { Part parts[]; };
uniform mat4 uVP;
uniform vec3 uEye;
uniform float uPixel;
out vec2 vQ;
flat out vec4 vInfo;        // Art, Fortschritt 0..1, Zufall, Deckungsfaktor (Fläche / gezeichnete Fläche)
flat out vec2 vSize;        // Breite, Höhe (m), echt
out vec3 vWorld;

void hide() { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vQ = vec2(0.0); vInfo = vec4(0.0); vSize = vec2(0.0); vWorld = vec3(0.0); }

void main() {
    Part q = parts[gl_InstanceID];
    float kind = q.m.x, age = uPB.y - q.v.w, life = q.m.y;
    if (kind < 1.5 || age < 0.0 || age > life) { hide(); return; }
    float t = age / life;
    float w, h;
    if (kind < 2.5) {            // Krone
        float r = q.m.w * sqrt(t);
        w = 2.0 * r + q.p.w * 1e-3;
        h = q.p.w * 1e-3 * 2.0 * sin(3.14159 * min(1.0, t * 1.2));
    } else if (kind < 3.5) {     // Strahl
        w = q.p.w * 1e-3 * 0.55;
        h = q.m.w * sin(3.14159 * t);
    } else {                     // Bläschen
        w = 2.0 * q.m.w;
        h = q.m.w;
    }
    if (h <= 1e-5) { hide(); return; }
    vec3 base = q.p.xyz;
    vec3 toEye = uEye - base;
    float dist = length(toEye);
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), toEye / max(dist, 1e-6)) + vec3(1e-6, 0.0, 0.0));
    float pw = pFootprint(uPixel * dist, dist);
    float W = max(w, 1.5 * pw), H = max(h, 1.5 * pw);
    int c = gl_VertexID % 6;
    vec2 qq = c == 0 ? vec2(-1, 0) : c == 1 ? vec2(1, 0) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, 0) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    vec3 wp = base + side * (qq.x * 0.5 * W) + vec3(0.0, qq.y * H, 0.0);
    vQ = vec2(qq.x * 0.5 * W / max(0.5 * w, 1e-7), qq.y * H / max(h, 1e-7));
    vInfo = vec4(kind, t, q.m.z, (w * h) / (W * H));
    vSize = vec2(w, h);
    vWorld = wp;
    gl_Position = uVP * vec4(wp, 1.0);
}
