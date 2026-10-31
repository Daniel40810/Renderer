// wg_height.frag — Höhe einer Kugelkappe (Kontaktwinkel 60°) in mm, wie Wetglass.Snapshot.capMm; die Karte nimmt
// mit GL_MAX den höchsten Tropfen.
in vec2 vPos;
flat in vec4 vA;
flat in vec4 vB;
out vec4 frag;
const float WG_S = 0.86602540378, WG_C = 0.5;   // sin, cos 60°
void main() {
    vec2 d = vPos - vA.xy;
    float along = dot(d, vB.xy), across = -d.x * vB.y + d.y * vB.x;
    along = (along + vA.z * vA.w * 0.5) / (1.0 + vA.w);
    float d2 = (along * along + across * across) / (vA.z * vA.z);
    if (d2 >= 1.0) discard;
    float h = (sqrt(max(0.0, 1.0 / (WG_S * WG_S) - d2)) - WG_C / WG_S) * vA.z * 1000.0;
    frag = vec4(h, 0.0, 0.0, 1.0);
}
