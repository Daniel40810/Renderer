// boden.vert — der Boden als ein großes Viereck in y = 0 (das Licht rechnet boden.frag je Bildpunkt).
uniform mat4 uVP;
uniform vec4 uBodenRect;   // x0, z0, x1, z1
out vec3 vP;

void main() {
    vec2 q = vec2((gl_VertexID & 1) == 0 ? uBodenRect.x : uBodenRect.z, (gl_VertexID & 2) == 0 ? uBodenRect.y : uBodenRect.w);
    vP = vec3(q.x, 0.0, q.y);
    gl_Position = uVP * vec4(vP, 1.0);
}
