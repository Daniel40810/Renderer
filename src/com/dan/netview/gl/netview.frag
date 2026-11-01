// netview.frag — Farbe des Stücks; Punkte rund, Ränder weich (die Ebene wird mit Mischung gezeichnet).
in vec4 vCol;
in vec2 vLocal;
flat in int vKind;
out vec4 frag;
void main() {
    float a = vCol.a;
    if (vKind == 2) {
        float r = length(vLocal);
        if (r > 1.0) discard;
        a *= smoothstep(1.0, 0.75, r);
    } else if (vKind != 1) {
        a *= smoothstep(1.0, 0.6, abs(vLocal.y));
    }
    frag = vec4(vCol.rgb, a);
}
