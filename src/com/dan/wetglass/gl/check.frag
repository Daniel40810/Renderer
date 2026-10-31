// check.frag — Selbsttest: Höhe aus der Karte an Texel p (wird zurückgelesen und mit Snapshot.heightMm verglichen).
out vec4 frag;
uniform sampler2D uMap;
uniform ivec2 uOrigin;
uniform int uStep;
void main() {
    ivec2 p = uOrigin + ivec2(gl_FragCoord.xy) * uStep;
    frag = vec4(texelFetch(uMap, p, 0).r, float(p.x), float(p.y), 1.0);
}
