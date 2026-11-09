// zusammen.frag — das Zwischenbild des Polarlichts (halbe Auflösung) additiv in das Ziel (Mischung GL_ONE, GL_ONE).
in vec2 vUV;
out vec4 frag;
uniform sampler2D uBild;
void main() {
    frag = vec4(texture(uBild, vUV).rgb, 0.0);
}
