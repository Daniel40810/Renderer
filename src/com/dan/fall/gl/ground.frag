// ground.frag — com.dan.fall: Fels nach Neigung mit etwas Rauschen und Wasser (nur für die Vorführung).
#include "fall.glsl"
in vec3 vWorld;
in vec3 vNormal;
out vec4 frag;
uniform float uWater;    // 1: Wasserspiegel
uniform vec3 uSky;
void main() {
    vec3 n = normalize(vNormal);
    vec3 col;
    if (uWater > 0.5) {
        vec3 v = normalize(uEye - vWorld);
        float fr = 0.02 + 0.98 * pow(1.0 - max(v.y, 0.0), 5.0);
        vec3 body = vec3(0.02, 0.07, 0.07) * (uSunColor * max(uSunDir.y, 0.0) * 0.3 + uAmbient);
        col = mix(body, uSky, fr);
    } else {
        float k = fallNoise2(vWorld.xy * 0.12 + vWorld.zz * 0.07, 5u) * 0.6 + fallNoise2(vWorld.xz * 0.5 + vWorld.yy * 0.3, 6u) * 0.4;
        float slope = 1.0 - n.y;
        vec3 rock = mix(vec3(0.07, 0.07, 0.075), vec3(0.17, 0.165, 0.16), k);   // nasser Gneis, dunkel
        vec3 grass = mix(vec3(0.08, 0.12, 0.05), vec3(0.15, 0.18, 0.08), k);
        vec3 alb = mix(grass, rock, smoothstep(0.2, 0.35, slope));
        col = alb * (uSunColor * max(dot(n, uSunDir), 0.0) / 3.14159265 + uAmbient * (0.6 + 0.4 * n.y));
    }
    vec4 o = fallFinish(vWorld, col, 1.0);
    frag = vec4(o.rgb, 1.0);
}
