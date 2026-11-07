#version 450
// craft.vert — Netze des Bootsbauers (com.dan.vessel.Shipwright, seit LituyaBay1958 W8a): 14 float je Ecke.
layout(location = 0) in vec3 aP;
layout(location = 1) in vec3 aN;
layout(location = 2) in vec3 aC;
layout(location = 3) in vec2 aUV;
layout(location = 4) in float aM;
layout(location = 5) in float aAO;
layout(location = 6) in float aEdge;
uniform mat4 uVP, uModel;
uniform vec4 uClip;          // Ebene im Weltraum (n, d): gezeichnet wird, wo n·p + d ≥ 0; uClip.w = 1e30: aus
out vec3 vW, vN, vC, vL;
out vec2 vUV;
flat out int vM;
out float vAO, vEdge;
void main() {
    vec4 w = uModel * vec4(aP, 1.0);
    vW = w.xyz;
    vN = mat3(uModel) * aN;
    vC = aC;
    vL = aP;
    vUV = aUV;
    vM = int(aM + 0.5);
    vAO = aAO;
    vEdge = aEdge;
    gl_ClipDistance[0] = dot(uClip.xyz, w.xyz) + uClip.w;
    gl_Position = uVP * w;
}
