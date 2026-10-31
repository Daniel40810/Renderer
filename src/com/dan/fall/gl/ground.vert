// ground.vert — com.dan.fall: Boden und Wand der Vorführung als Netz (Ort, Normale), dazu der Wasserspiegel.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
void main() {
    vWorld = aPos;
    vNormal = aNormal;
    gl_Position = uVP * vec4(aPos, 1.0);
}
