// forestgpu_bake.vert — backt eine Ansicht eines Oktaeder-Impostors (seit LituyaBay1958 Phase 6): das Netz eines Baums
// (Ecken wie forest.vert) parallel projiziert aus der Richtung uDir, Mitte uC, Hüllradius uR, Achsen wie
// com.dan.forest.Octahedral.frameBasis. Der Bildausschnitt (glViewport) ist das Feld der Ansicht im Atlas.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec2 aAux;
uniform vec3 uC, uRight, uUp, uDir;
uniform float uR;
out vec3 vN;
out vec3 vCol;
out float vLeafy;
void main() {
    vec3 q = aPos - uC;
    gl_Position = vec4(dot(q, uRight) / uR, dot(q, uUp) / uR, -dot(q, uDir) / uR * 0.5, 1.0);
    vN = aNrm;
    vCol = aCol;
    vLeafy = aAux.y;
}
