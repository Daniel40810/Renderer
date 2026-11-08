// stich_hell.frag — lineare Helligkeit Y einer Quelle in sRGB (wie Stich.helligkeit), je Bildpunkt der Quelle.
uniform sampler2D uQuelle;
layout(location = 0) out float oY;

float linear(float c) { return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4); }

void main() {
    vec3 c = texelFetch(uQuelle, ivec2(gl_FragCoord.xy), 0).rgb;
    c = floor(c * 255.0 + 0.5) / 255.0;   // auf die 8 Bit der Quelle, wie Lab.linear
    oY = 0.2126 * linear(c.r) + 0.7152 * linear(c.g) + 0.0722 * linear(c.b);
}
