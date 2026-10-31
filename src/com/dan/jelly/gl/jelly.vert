// Ohrenqualle als waagerechtes Viereck knapp über dem Spiegel (com.dan.jelly). Je Instanz:
// a0 = Ort x, y, z, Halbmesser (m) · a1 = Richtung, Zusammenziehen, Tiefe (m), Strandung (0 schwimmt, 1..2 trocknet)
// a2 = Farbe der Gonaden, Sichtanteil, Größe, frei
layout(location = 0) in vec4 a0;
layout(location = 1) in vec4 a1;
layout(location = 2) in vec4 a2;
uniform mat4 uViewProj;
out vec2 vUv;
out vec3 vWorld;
flat out vec4 vA1;
flat out vec4 vA2;

const vec2 C[6] = vec2[](vec2(-1, -1), vec2(1, -1), vec2(1, 1), vec2(-1, -1), vec2(1, 1), vec2(-1, 1));

void main() {
    vec2 c = C[gl_VertexID] * 1.12;          // Rand mit den Tentakeln
    float h = a1.x, s = sin(h), co = cos(h);
    vec2 r = vec2(c.x * co - c.y * s, c.x * s + c.y * co) * a0.w;
    vec3 p = vec3(a0.x + r.x, a0.y, a0.z - r.y);
    vUv = c;
    vWorld = p;
    vA1 = a1;
    vA2 = a2;
    gl_Position = uViewProj * vec4(p, 1.0);
}
