// tiles.frag — größter Unschärfekreis je Kachel (uMode 0: 16 × 16 halbe Bildpunkte → x: größter |c|, y: größter Kreis davor)
// und die Nachbarschaft (uMode 1: 3 × 3 Kacheln), damit ein Bildpunkt weiß, wie weit er sammeln muss (com.dan.lens).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uSrc;
uniform int uMode;
uniform int uTile;

void main() {
    ivec2 t = ivec2(gl_FragCoord.xy);
    ivec2 sz = textureSize(uSrc, 0) - 1;
    vec2 m = vec2(0.0);
    if (uMode == 0) {
        for (int j = 0; j < uTile; j++)
            for (int i = 0; i < uTile; i++) {
                float c = texelFetch(uSrc, min(t * uTile + ivec2(i, j), sz), 0).a;
                m.x = max(m.x, abs(c));
                if (c < 0.0) m.y = max(m.y, -c);
            }
    } else {
        for (int j = -1; j <= 1; j++)
            for (int i = -1; i <= 1; i++) m = max(m, texelFetch(uSrc, clamp(t + ivec2(i, j), ivec2(0), sz), 0).xy);
    }
    frag = vec4(m, 0.0, 1.0);
}
