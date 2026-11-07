#version 450
// wire.vert — Drähte und Leinen als Bänder zur Kamera, mindestens so breit wie ein Bildpunkt; was dünner ist, deckt
// anteilig (Alpha), damit Stagen in der Ferne weder verschwinden noch treppen (seit LituyaBay1958 W8a).
layout(location = 0) in vec3 aA;
layout(location = 1) in vec3 aB;
layout(location = 2) in vec2 aRM;     // Halbmesser, Material
layout(location = 3) in vec3 aC;
uniform mat4 uVP, uModel;
uniform vec3 uCam;
uniform float uPixel;                 // Winkel eines Bildpunkts (rad): 2 tan(φ/2) / Bildhöhe
uniform vec4 uClip;
out vec3 vW, vC, vT;
out float vAcross, vCover;
flat out int vM;
void main() {
    int id = gl_VertexID;              // 0..3 als Streifen
    vec3 a = (uModel * vec4(aA, 1.0)).xyz, b = (uModel * vec4(aB, 1.0)).xyz;
    vec3 p = (id & 1) == 0 ? a : b;
    float side = (id & 2) == 0 ? -1.0 : 1.0;
    vec3 t = normalize(b - a);
    vec3 toCam = uCam - p;
    float d = length(toCam);
    vec3 off = normalize(cross(t, toCam / max(d, 1e-4)));
    float rPix = 0.6 * uPixel * d;     // halber Mindestwert: gut ein Bildpunkt breit
    float r = max(aRM.x, rPix);
    vCover = clamp(aRM.x / rPix, 0.02, 1.0);
    vec3 w = p + off * r * side;
    vW = w; vC = aC; vT = t; vAcross = side; vM = int(aRM.y + 0.5);
    gl_ClipDistance[0] = dot(uClip.xyz, w) + uClip.w;
    gl_Position = uVP * vec4(w, 1.0);
}
