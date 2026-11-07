#version 450
// lamp.vert — Lichthof einer Lampe (Ankerlicht, Positionslichter) als Fläche zur Kamera, additiv.
layout(location = 0) in vec3 aP;
layout(location = 1) in vec3 aRad;
layout(location = 2) in float aR;
uniform mat4 uVP, uModel;
uniform vec3 uCam;
uniform float uPixel;
uniform vec4 uClip;
out vec2 vQ;
out vec3 vRad;
out float vSize;
void main() {
    int id = gl_VertexID;
    vec2 q = vec2((id & 1) == 0 ? -1.0 : 1.0, (id & 2) == 0 ? -1.0 : 1.0);
    vec3 p = (uModel * vec4(aP, 1.0)).xyz;
    vec3 toCam = uCam - p;
    float d = length(toCam);
    vec3 f = toCam / max(d, 1e-4);
    vec3 r = normalize(cross(vec3(0.0, 1.0, 0.0), f)), u = cross(f, r);
    float size = max(aR * 4.0, uPixel * d * 4.0);    // Hof: viermal die Linse, mindestens vier Bildpunkte
    vec3 w = p + (r * q.x + u * q.y) * size + f * 0.2;
    vQ = q; vRad = aRad; vSize = aR / size;
    gl_ClipDistance[0] = dot(uClip.xyz, w) + uClip.w;
    gl_Position = uVP * vec4(w, 1.0);
}
