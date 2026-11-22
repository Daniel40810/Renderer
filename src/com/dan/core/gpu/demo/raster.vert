#version 460
// raster.vert – GpuCheck Teil 10 (P6a): Scheitel aus einem Speicherpuffer über gl_VertexIndex, ohne Scheitel-Eingänge.
// Je Scheitel vec4: x, y im Bildraum von Vulkan (−1..1, y nach unten), Tiefe, Nummer des Dreiecks.
layout(std430, set = 0, binding = 0) readonly buffer Scheitel { vec4 s[]; };
layout(location = 0) flat out float nummer;
void main() {
    vec4 v = s[gl_VertexIndex];
    nummer = v.w;
    gl_Position = vec4(v.xy, v.z, 1.0);
}
