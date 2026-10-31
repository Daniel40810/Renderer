// breaker.vert — com.dan.surf: das Netz der Brandung (BreakerMesh) und der Sand darunter. Ecken in der Welt (x zur
// Küste, y nach oben, z längs des Kamms) mit Normale und Merkmalen: Schaum, dünn (Lippe), Wassertiefe, Grund.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aAttr;
uniform mat4 uVP;
out vec3 vWorld;
out vec3 vNormal;
out vec4 vAttr;
void main() {
    vWorld = aPos;
    vNormal = aNormal;
    vAttr = aAttr;
    gl_Position = uVP * vec4(aPos, 1.0);
}
