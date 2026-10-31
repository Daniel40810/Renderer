// longship.vert — ein Teil eines Schiffs: Schiffskoordinaten (x Steuerbord, y oben, z achteraus) mit der Modellmatrix
// des Schiffs (Kurs, Stampfen, Rollen, Ort) in die Welt.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUV;
layout(location = 3) in float aMat;
uniform mat4 uVP;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vLocal;
out vec2 vUV;
flat out int vMat;

void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNrm;
    vLocal = aPos;
    vUV = aUV;
    vMat = int(aMat + 0.5);
    gl_Position = uVP * w;
}
