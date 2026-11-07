// mb_tilemax.frag — Bewegungsunschärfe (com.dan.shutter): je Kachel k × k die längste halbe Bewegung.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uVel;
uniform int uK;
void main() {
    ivec2 t = ivec2(gl_FragCoord.xy), sz = textureSize(uVel, 0);
    vec2 best = vec2(0.0);
    float bl = 0.0;
    for (int j = 0; j < uK; j++)
        for (int i = 0; i < uK; i++) {
            ivec2 p = t * uK + ivec2(i, j);
            if (p.x >= sz.x || p.y >= sz.y) continue;
            vec2 v = texelFetch(uVel, p, 0).xy;
            float l = dot(v, v);
            if (l > bl) { bl = l; best = v; }
        }
    frag = vec4(best, 0.0, 0.0);
}
