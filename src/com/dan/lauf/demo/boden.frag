#version 460
// boden.frag – lauf.demo (S3c, wie render.water.demo/boden.frag): Ersatz für das Gelände in SeegangNetzCheck:
// eine schiefe Ebene z = z0 + g · Ost (relativ zum Auge, mit Erdkrümmung), gleichmäßig hell. Push: z0 (m NHN), g, L
// (cd/m² mal Vorbelichtung), Lage einer Wand x = w (Ost relativ zum Auge, 0 keine; von z0 bis 5 m NHN, Leuchtdichte
// L / 20; ohne Erdkrümmung gerechnet). Dazu wasser.vert.
#include "/com/dan/render/water/wasser.glsl"
layout(push_constant) uniform Push { vec4 p; };
layout(location = 0) out vec4 farbe;
void main() {
    float nx = gl_FragCoord.x / bild.x * 2.0 - 1.0, ny = gl_FragCoord.y / bild.y * 2.0 - 1.0;
    vec3 d = normalize(vorn.xyz + rechts.xyz * nx - oben.xyz * ny);
    // y = z0 + g x − k (x² + z²) − h: feste Stelle in t (die Ebene ist flach geneigt)
    float t = -1.0;
    float z = p.x;
    for (int k = 0; k < 6; k++) {
        float tt = treffen(d, z);
        if (tt < 0.0) break;
        t = tt;
        z = p.x + p.y * d.x * tt;
    }
    if (p.w != 0.0 && d.x * p.w > 0.0) {
        float tw = p.w / d.x, y = auge.x + d.y * tw;
        if (y <= 5.0 && y >= p.x && (t <= 0.0 || tw < t)) {
            farbe = vec4(vec3(p.z / 20.0), 1.0);
            gl_FragDepth = vorn.w / (tw * dot(d, vorn.xyz));
            return;
        }
    }
    if (t <= 0.0) discard;
    farbe = vec4(p.z, p.z, p.z, 1.0);
    gl_FragDepth = vorn.w / (t * dot(d, vorn.xyz));
}
