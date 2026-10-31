// check.frag — com.dan.cliff: Selbsttest, Relief (m) und Farbe am Punkt.
#include "cliff.glsl"
flat in vec3 vP;
flat in vec3 vN;
uniform float uFootprint;
out vec4 frag;
void main() {
    CliffSample s = cliffMaterial(vP, normalize(vN), 0.0, 0.0, uFootprint);
    frag = vec4(s.albedo, cliffRelief(vP, uFootprint));
}
