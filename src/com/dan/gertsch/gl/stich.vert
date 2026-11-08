// stich.vert — ein Dreieck über das ganze Ziel (ohne Puffer), für Helligkeit, Druck und Zeigen.
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    gl_Position = vec4(2.0 * p - 1.0, 0.0, 1.0);
}
