#version 460
// voll.vert – render.overlay (W3, wie render.analysis): ein Dreieck über das ganze Bild, ohne Scheitel-Eingänge (wie in render.frame).
void main() {
    vec2 p = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
