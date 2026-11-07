// glare_down.frag — eine Stufe kleiner, mit dem 13-Punkte-Filter nach Jimenez (2014): ohne Kästen und ohne Flimmern
// (com.dan.lens, Schleierlicht).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uSrc;
uniform vec2 uTexel;            // Bildpunkt der Quelle (uv)
vec3 s(vec2 o) { return texture(uSrc, vUV + o * uTexel).rgb; }
void main() {
    vec3 a = s(vec2(-2, -2)), b = s(vec2(0, -2)), c = s(vec2(2, -2)), d = s(vec2(-1, -1)), e = s(vec2(1, -1)),
         f = s(vec2(-2, 0)), g = s(vec2(0, 0)), h = s(vec2(2, 0)), i = s(vec2(-1, 1)), j = s(vec2(1, 1)),
         k = s(vec2(-2, 2)), l = s(vec2(0, 2)), m = s(vec2(2, 2));
    vec3 o = (d + e + i + j) * 0.125 + (a + c + k + m) * 0.03125 + (b + f + h + l) * 0.0625 + g * 0.125;
    frag = vec4(o, 1.0);
}
