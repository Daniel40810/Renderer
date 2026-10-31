// check.vert — com.dan.fall: Selbsttest, Punkt i in Spalte i einer Zeile.
layout(location = 0) in vec4 aQ;    // Strähne, Flugzeit, Endgeschwindigkeit, seitlich
layout(location = 1) in vec2 aQ2;   // vorwärts, frei
uniform float uCount;
flat out vec4 vQ;
flat out float vF;
void main() {
    vQ = aQ;
    vF = aQ2.x;
    gl_Position = vec4((float(gl_VertexID) + 0.5) / uCount * 2.0 - 1.0, 0.0, 0.0, 1.0);
    gl_PointSize = 1.0;
}
