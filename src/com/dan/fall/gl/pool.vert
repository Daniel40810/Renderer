// pool.vert — com.dan.fall: Schaum des Tosbeckens als Scheibe auf dem Wasser (oder Gischt auf dem Fels), je Strähne.
#include "fall.glsl"
uniform mat4 uVP;
out vec2 vUV;
out vec3 vP;
flat out int vI;
void main() {
    int i = gl_InstanceID;
    vec4 I = uStrandI[i];
    float R = 5.0 + uStrandB[i].w * 1.6 + I.w * 0.25;
    vec2 c = vec2(float(gl_VertexID & 1) * 2.0 - 1.0, float(gl_VertexID >> 1) * 2.0 - 1.0);
    vec3 pos = I.xyz + vec3(c.x * R, 0.06, c.y * R) + uFallWind * 0.8;
    vUV = c;
    vP = pos;
    vI = i;
    gl_Position = uVP * vec4(pos, 1.0);
}
