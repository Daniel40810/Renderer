// block.vert — com.dan.cliff (seit Brocken Phase 8): Blöcke eines Blockmeers, instanziert. Das Netz ist ein Gitter
// über Breite u (−π/2…π/2) und Länge w (−π…π) in aPos.xy; je Block (iA: Mitte, Drehung um die Senkrechte in rad;
// iB: halbe Ausdehnung, Exponent der Rundung) wird daraus ein Superellipsoid wie Tor.superellipsoid. Material wie der
// Fels (rock.frag, als Fels erzwungen).
#define CLIFF_VERTEX
#include "cliff.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec4 iA;
layout(location = 3) in vec4 iB;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;

float spw(float v, float p) { return sign(v) * pow(abs(v), p); }

void main() {
    float u = aPos.x, w = aPos.y, e = iB.w;
    float cu = cos(u), su = sin(u), cw = cos(w), sw = sin(w);
    vec3 q = vec3(iB.x * spw(cu, e) * spw(cw, e), iB.y * spw(su, e), iB.z * spw(cu, e) * spw(sw, e));
    vec3 n = vec3(spw(cu, 2.0 - e) * spw(cw, 2.0 - e) / iB.x, spw(su, 2.0 - e) / iB.y, spw(cu, 2.0 - e) * spw(sw, 2.0 - e) / iB.z);
    n = length(n) < 1e-6 ? vec3(0.0, sign(su), 0.0) : normalize(n);
    float c = cos(iA.w), s = sin(iA.w);
    vec3 p = iA.xyz + vec3(q.x * c - q.z * s, q.y, q.x * s + q.z * c);
    vNormal = vec3(n.x * c - n.z * s, n.y, n.x * s + n.z * c);
    vWorld = p;
    gl_Position = uVP * vec4(p, 1.0);
}
