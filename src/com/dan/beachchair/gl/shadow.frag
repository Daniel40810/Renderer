#version 430 core
// shadow.frag — der Schatten nimmt dem Boden das Sonnenlicht: Faktor = Himmel / (Sonne + Himmel) je Farbe (auf der
// Prozessorseite gerechnet), multiplikativ gemischt (Ziel × Quelle); in der Ferne und zum Rand hin heller.
in float vFade;
in float vDist;
out vec4 frag;
uniform vec3 uFactor;
uniform float uFar;
void main() {
    float k = vFade * (1.0 - smoothstep(uFar * 0.7, uFar, vDist));
    frag = vec4(mix(vec3(1.0), uFactor, k), 1.0);
}
