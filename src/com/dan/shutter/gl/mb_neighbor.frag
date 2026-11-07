// mb_neighbor.frag — Bewegungsunschärfe (com.dan.shutter): je Kachel die längste Bewegung der 3 × 3 Nachbarn (was von
// nebenan über die Kachel streichen kann). Diagonale Nachbarn nur, wenn ihre Bewegung auf diese Kachel zeigt (McGuire 2013).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uTileMax;
void main() {
    ivec2 t = ivec2(gl_FragCoord.xy), sz = textureSize(uTileMax, 0);
    vec2 best = texelFetch(uTileMax, t, 0).xy;
    float bl = dot(best, best);
    for (int j = -1; j <= 1; j++)
        for (int i = -1; i <= 1; i++) {
            if (i == 0 && j == 0) continue;
            ivec2 p = clamp(t + ivec2(i, j), ivec2(0), sz - 1);
            vec2 v = texelFetch(uTileMax, p, 0).xy;
            float l = dot(v, v);
            if (i != 0 && j != 0 && l > 0.0 && dot(normalize(v), -normalize(vec2(i, j))) < 0.7) continue;
            if (l > bl) { bl = l; best = v; }
        }
    frag = vec4(best, 0.0, 0.0);
}
