#version 450
// craft_shadow.vert — Tiefe des Boots aus Sicht der Sonne (eigener Schatten).
layout(location = 0) in vec3 aP;
layout(location = 4) in float aM;
uniform mat4 uVP, uModel;
flat out int vM;
void main() {
    vM = int(aM + 0.5);
    gl_Position = uVP * uModel * vec4(aP, 1.0);
}
