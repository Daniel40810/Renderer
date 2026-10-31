#version 430 core
// sail.vert — Boote aus BoatShape: je Ecke Lage, Normale, Farbe (Welt, linear).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
void main() {
    vWorld = aPos;
    vNormal = aNrm;
    vColor = aCol;
    gl_Position = uVP * vec4(aPos, 1.0);
}
