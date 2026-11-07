// resolve.frag — Kino-Stufe (com.dan.shutter): die Summe der Teilbilder durch ihr Gewicht. Wo kein Teilbild gezählt hat
// (ganz am Rand bei starkem Katzenauge, oder zu wenige Teilbilder für das Fenster), das letzte Teilbild.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uSum;
uniform sampler2D uLast;
void main() {
    vec4 s = texture(uSum, vUV);
    vec3 c = s.a > 1e-6 ? s.rgb / s.a : max(texture(uLast, vUV).rgb, vec3(0.0));
    frag = vec4(c, 1.0);
}
