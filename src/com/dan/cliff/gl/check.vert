// check.vert — com.dan.cliff: Selbsttest. Je Punkt (Lage, Normale) ein Bildpunkt in einer Zeile; dort rechnet
// check.frag Relief und Material, CliffGl.selfCheck vergleicht mit dem Prozessor.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform float uCount;
flat out vec3 vP;
flat out vec3 vN;
void main() {
    vP = aPos;
    vN = aNormal;
    gl_Position = vec4((float(gl_VertexID) + 0.5) / uCount * 2.0 - 1.0, 0.0, 0.0, 1.0);
    gl_PointSize = 1.0;
}
