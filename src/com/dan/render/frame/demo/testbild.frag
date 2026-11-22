#version 460
// testbild.frag – FrameCheck (P6a): legt ein hochgeladenes Bild Bildpunkt für Bildpunkt in das HDR-Bild.
layout(set = 0, binding = 0) uniform sampler2D bild;
layout(location = 0) out vec4 aus;
void main() { aus = texelFetch(bild, ivec2(gl_FragCoord.xy), 0); }
