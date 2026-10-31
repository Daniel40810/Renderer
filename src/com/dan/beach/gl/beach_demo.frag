#version 430 core
// beach_demo.frag — Vorführung von beach.glsl: heller Ostseesand, nass am Wasser (dunkler, glatt, spiegelt den Himmel),
// zertreten in einem Streifen landwärts, dahinter die See. Licht: Sonne (Lambert), Himmel nach der Neigung, Dunst.
#include "beach.glsl"
in vec3 vWorld;
out vec4 frag;
uniform vec3 uEye, uSunDir, uSunColor, uSkyTop, uSkyHorizon;
uniform float uFogDist, uDisplay;
uniform vec3 uZones;   // Wasserlinie (z = −x), zertreten von, bis (m landwärts der Wasserlinie)
const float PI = 3.14159265;
void main() {
    vec3 toEye = uEye - vWorld;
    float dist = length(toEye);
    vec3 V = toEye / dist;
    float pxm = max(max(length(dFdx(vWorld)), length(dFdy(vWorld))), 1e-4);
    float land = vWorld.z + uZones.x;   // m landwärts der Wasserlinie
    vec3 col;
    if (land < 0.0) {
        float F = 0.02 + 0.98 * pow(1.0 - max(V.y, 0.0), 5.0);
        vec3 deep = vec3(0.012, 0.05, 0.085) * (uSkyTop * 2.0 + uSunColor * 0.25);
        col = mix(deep, uSkyHorizon, F);
    } else {
        float trocken = smoothstep(0.5, 4.0, land);
        float zertreten = smoothstep(uZones.y - 3.0, uZones.y, land) * (1.0 - smoothstep(uZones.z, uZones.z + 4.0, land));
        BeachSample s = beachSurface(vWorld.xz, vec3(0.0, 1.0, 0.0), pxm, dist, trocken, zertreten, uSunDir);
        vec3 alb = mix(vec3(0.30, 0.25, 0.18), vec3(0.52, 0.46, 0.34), trocken) * s.albedo;
        vec3 n = s.normal;
        float ndl = max(dot(n, uSunDir), 0.0) * s.sun;
        float up = 0.5 + 0.5 * n.y;
        col = alb * (uSunColor * ndl / PI + mix(uSkyHorizon, uSkyTop, up));
        // nasser Sand spiegelt den Himmel ein wenig
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        col = mix(col, uSkyHorizon, F * (1.0 - trocken) * 0.6);
    }
    col = mix(uSkyHorizon, col, exp(-dist / uFogDist));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
