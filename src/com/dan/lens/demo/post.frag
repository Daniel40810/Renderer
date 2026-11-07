// post.frag — Belichtung und Filmkurve (ACES nach Narkowicz) für die Demo von com.dan.lens.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform float uExposure;
vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
void main() { frag = vec4(pow(aces(texture(uHdr, vUV).rgb * uExposure), vec3(1.0 / 2.2)), 1.0); }
