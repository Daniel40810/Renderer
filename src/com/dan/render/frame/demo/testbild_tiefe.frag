#version 460
// testbild_tiefe.frag – FrameCheck (F49 A): legt ein hochgeladenes Bild und eine Tiefe (R32F, 0 = Himmel) Bildpunkt
// für Bildpunkt in das HDR-Bild und die Tiefe des Bildziels.
layout(set = 0, binding = 0) uniform sampler2D bild;
layout(set = 0, binding = 1) uniform sampler2D tiefe;
layout(location = 0) out vec4 aus;
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    aus = texelFetch(bild, p, 0);
    gl_FragDepth = texelFetch(tiefe, p, 0).r;
}
