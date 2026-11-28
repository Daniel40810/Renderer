#version 460
// voll.vert – render.water (S8d, wie render.frame): ein Dreieck über das ganze Bild, ohne Scheitel-Eingänge (für Unterwasser).
void main() {
    vec2 p = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
