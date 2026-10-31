// mist.vert — com.dan.fall: Nebel und Gischt am Fuß. Ballen steigen vom Aufprall, wachsen und ziehen mit dem Wind.
#include "fall.glsl"
layout(location = 0) in vec4 aM;    // Strähne, Phase, Winkel, Zufall
uniform mat4 uVP;
uniform float uTime, uMist;
uniform float uSizeGain;
uniform vec2 uNear;                 // seit Phase 8: größter Winkel eines Ballens (rad), Ausblenden unter … m
out vec2 vUV;
out float vA;
out vec3 vP;
out float vR;
void main() {
    int i = int(aM.x + 0.5);
    vec4 I = uStrandI[i];
    float age = fract(uTime * (1.0 - uIce) / 9.0 + aM.y);
    float rad = (3.0 + uStrandB[i].w + 25.0 * age) * (0.4 + 0.6 * aM.w);
    vec3 base = I.xyz + vec3(cos(aM.z), 0.0, sin(aM.z)) * rad + uFallWind * age * 9.0 * 0.6 + vec3(0.0, 2.0 + 22.0 * age * age, 0.0);
    float size = (10.0 + 40.0 * age) * (0.6 + 0.4 * uMist) * uSizeGain;
    float de = distance(uEye, base), fade = 1.0;
    if (uNear.x > 0.0) {
        size = min(size, uNear.x * de);
        fade = smoothstep(uNear.y * 0.5, uNear.y * 2.5, de);
    }
    vec2 c = vec2(float(gl_VertexID & 1) * 2.0 - 1.0, float(gl_VertexID >> 1) * 2.0 - 1.0);
    vec3 toEye = normalize(uEye - base);
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), toEye) + vec3(1e-5, 0.0, 0.0));
    vec3 up = cross(toEye, right);
    vec3 pos = base + (right * c.x + up * c.y) * size * 0.5;
    vUV = c;
    vA = uMist * smoothstep(0.0, 0.12, age) * (1.0 - age) * 1.4 * (1.0 - uIce) * fade;
    vP = pos;
    vR = aM.w;
    gl_Position = vA < 0.002 ? vec4(2.0, 2.0, 2.0, 1.0) : uVP * vec4(pos, 1.0);
}
