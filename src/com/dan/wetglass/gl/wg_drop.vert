// wg_drop.vert — ein Tropfen als Rechteck in der Karte, ohne Eckpuffer: Instanz = Tropfen × 4 Abbilder, sechs Ecken.
// Laufende Tropfen sind in Laufrichtung gestreckt und nach hinten verschoben (wie Wetglass.Snapshot.capMm). Auf einer
// Kachel liegt ein Tropfen am Rand auch auf der gegenüberliegenden Seite: Abbild 1 links/rechts, 2 oben/unten, 3 Ecke.
layout(std430, binding = 0) readonly buffer WgDrops { vec4 wgDrops[]; };   // je Tropfen: (x, y, a, Streckung), (ux, uy, 0, 0)
uniform vec2 uSize;        // Fläche in m
uniform int uPeriodic;
out vec2 vPos;
flat out vec4 vA;
flat out vec4 vB;
void main() {
    int i = gl_InstanceID / 4, k = gl_InstanceID % 4;
    vec4 A = wgDrops[i * 2], B = wgDrops[i * 2 + 1];
    float ar = A.z, st = A.w;
    float ext = ar * (1.0 + st) * 1.6;
    vec2 off = vec2(0.0);
    bool nearX = A.x < ext || A.x > uSize.x - ext, nearY = A.y < ext || A.y > uSize.y - ext;
    if (k > 0 && (uPeriodic == 0 || (k == 1 && !nearX) || (k == 2 && !nearY) || (k == 3 && !(nearX && nearY)))) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        vPos = vec2(0.0); vA = A; vB = B;
        return;
    }
    if (k == 1 || k == 3) off.x = A.x < uSize.x * 0.5 ? uSize.x : -uSize.x;
    if (k == 2 || k == 3) off.y = A.y < uSize.y * 0.5 ? uSize.y : -uSize.y;
    A.xy += off;
    vA = A;
    vB = B;
    int c = gl_VertexID % 6;
    vec2 q = c == 0 ? vec2(-1, -1) : c == 1 ? vec2(1, -1) : c == 2 ? vec2(1, 1) : c == 3 ? vec2(-1, -1) : c == 4 ? vec2(1, 1) : vec2(-1, 1);
    float lo = -ar * (1.0 + st) - ar * st * 0.5, hi = ar * (1.0 + st) - ar * st * 0.5;
    float along = mix(lo, hi, q.x * 0.5 + 0.5) + sign(q.x) * ar * 0.05, across = q.y * ar * 1.05;
    vec2 u = B.xy, v = vec2(-u.y, u.x);
    vPos = A.xy + u * along + v * across;
    gl_Position = vec4(vPos / uSize * 2.0 - 1.0, 0.0, 1.0);
}
