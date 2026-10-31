// check.frag — Prüfung: liest das Rundumbild an 64 × 32 gleichmäßig verteilten Texelmitten zurück.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uPattern;
void main() {
    ivec2 ts = textureSize(uPattern, 0);
    ivec2 p = ivec2(gl_FragCoord.xy) * (ts / ivec2(64, 32)) + (ts / ivec2(64, 32)) / 2;
    frag = vec4(texelFetch(uPattern, p, 0).rgb, float(p.x * 1024 + p.y));
}
