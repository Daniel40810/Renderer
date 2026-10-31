#version 430 core
// grains.frag — helle Körner im Sonnenlicht, Schweif blasser; ferne Körner verblassen (sie wären kleiner als ein Bildpunkt).
// Seit Phase 9 auch die Stecken der Sandfangzäune (Art 2).
in float vHead;
in float vKind;
in float vDist;
out vec4 frag;
uniform vec3 uSunColor, uSkyTop, uFogColor;
uniform float uFogDist, uDisplay, uNear, uFar;
void main() {
    vec3 c = vec3(0.86, 0.77, 0.60) * (uSunColor * 0.7 + uSkyTop * 0.8);
    if (vKind > 0.5) c = mix(c, vec3(0.80, 0.76, 0.68) * (uSunColor * 0.5 + uSkyTop), 0.5);
    // seit Phase 9: Art 2 sind die Stecken der Sandfangzäune aus Busch, dunkel und undurchsichtig
    if (vKind > 1.5) c = vec3(0.10, 0.075, 0.05) * (uSunColor * 0.6 + uSkyTop);
    float a = mix(0.15, 0.85, vHead) * (1.0 - smoothstep(uNear, uFar, vDist));
    if (vKind > 0.5 && vKind < 1.5) a *= 0.45;
    if (vKind > 1.5) a = 1.0 - smoothstep(uNear * 4.0, uFar * 4.0, vDist);
    c = mix(uFogColor, c, exp(-vDist / uFogDist));
    if (uDisplay > 0.0) c = pow(1.0 - exp(-c * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(c, a);
}
