// sprite.vert — ein Licht aus highlights.comp als Viereck um seinen Unschärfekreis (halbe Auflösung, com.dan.lens).
layout(std430, binding = 3) buffer Spots { vec4 spot[]; } sp;
uniform vec2 uHalfSize;
out vec2 vOff;          // Abstand zum Licht (halbe Bildpunkte)
flat out vec4 vSpot;    // x, y, c, –
flat out vec3 vRgb;
void main() {
    int k = gl_InstanceID;
    vec4 a = sp.spot[2 * k], b = sp.spot[2 * k + 1];
    vec2 corner = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID >> 1) == 0 ? -1.0 : 1.0);
    float r = 0.5 * abs(a.z) * uAreaRoot * max(1.0, uSqueeze) * 1.15 + 2.0;
    vOff = corner * r;
    vSpot = a;
    vRgb = b.rgb;
    vec2 pos = a.xy + vOff;
    gl_Position = vec4(pos / uHalfSize * 2.0 - 1.0, 0.0, 1.0);
}
