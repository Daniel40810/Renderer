// accumulate.frag — Kino-Stufe (com.dan.shutter): ein Teilbild in die Summe. Gewicht je Bildpunkt aus dem Zeitfenster
// (Öffnungsgang) und dem Katzenauge (window.glsl, davor gesetzt). Seit W9c Runde 2 je Farbe um uShift verschoben: die
// sphärische Aberration und der Längsfarbfehler dieses Orts der Pupille (com.dan.shutter.Aberration); eine andere
// Schärfenebene für ein Teilbild verschiebt das ganze Bild, für jede Tiefe gleich. uHdr ist das Teilbild, mit Lichtern
// ohne ihren Überschuss, der als Fleck kommt (lights.comp, splat.vert).
// Ergebnis (additiv gemischt): rgb · w, a = w. resolve.frag teilt.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
uniform vec2 uShiftR, uShiftG, uShiftB;   // uv

void main() {
    vec2 n = vUV - 0.5;
    float w = windowWeight(n);
    if (w <= 0.0 || !catEyeOk(n)) discard;
    vec3 c = vec3(texture(uHdr, vUV - uShiftR).r, texture(uHdr, vUV - uShiftG).g, texture(uHdr, vUV - uShiftB).b);
    frag = vec4(max(c, vec3(0.0)) * w, w);
}
