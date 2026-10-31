#version 430 core
// train.vert — Fahrzeuge (seit Brocken Phase 13): ein Modell je Lok oder Wagen in eigenen Koordinaten (x rechts,
// y oben ab Schiene, z vorn), mit uModel auf das Gleis gesetzt. Stoff: 0 matt, 1 Lack, 2 Glas, 3 Lampe.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aMat;
uniform mat4 uVP;
uniform mat4 uModel;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vMat;
out float vFront;                       // + vorn an der Lok (z > 0), − hinten

void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNrm;
    vColor = aCol;
    vMat = aMat;
    vFront = sign(aPos.z);
    gl_Position = uVP * w;
}
