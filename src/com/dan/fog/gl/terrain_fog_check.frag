// terrain_fog_check.frag — Selbsttest: die Dichte an 64 Orten (uP[i]) wie TerrainFog.density, je Bildpunkt einer;
// seit Brocken Phase 7 in A der Schatten der Schattenwerfer wie TerrainFog.sunShadow.
#include "terrain_fog_common.glsl"
uniform vec4 uP[64];
out vec4 frag;
void main() {
    int i = int(gl_FragCoord.x);
    vec3 p = uP[i].xyz;
    vec4 m = tfMap(p.xz);
    frag = vec4(tfDensityM(p, m) * 1000.0, m.x, m.y, tfSunShadow(p));
}
