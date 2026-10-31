#version 430 core
// stage.frag — Sand mit feinem Korn und Rippeln, nasser Streifen am Wasser, die See als Spiegel des Himmels mit
// Glitzern; Licht wie beachchair.frag.
in vec3 vWorld;
out vec4 frag;
uniform vec3 uEye;
uniform vec3 uSunDir, uSunColor, uSkyTop, uSkyHorizon, uFogColor;
uniform float uFogDist, uDisplay, uShore;
const float PI = 3.14159265;
float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}
void main() {
    vec3 toEye = uEye - vWorld;
    float dist = length(toEye);
    vec3 V = toEye / dist;
    float pxm = max(length(fwidth(vWorld)), 1e-4);
    vec3 col;
    if (vWorld.y < -0.01) {
        // See: Fresnel zum Himmel, Glitzern der Sonne
        vec3 n = normalize(vec3(0.03 * (vnoise(vWorld.xz * 0.7) - 0.5), 1.0, 0.03 * (vnoise(vWorld.xz * 0.9 + 3.0) - 0.5)));
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        vec3 r = reflect(-V, n);
        vec3 sky = mix(uSkyHorizon, uSkyTop, clamp(r.y * 3.0, 0.0, 1.0));
        vec3 deep = vec3(0.012, 0.05, 0.085) * (uSkyTop * 2.0 + uSunColor * 0.25);
        col = mix(deep, sky, F) + uSunColor * pow(max(dot(r, uSunDir), 0.0), 900.0) * 3.0;
    } else {
        float korn = mix(0.5, vnoise(vWorld.xz * 180.0), 1.0 - smoothstep(0.002, 0.01, pxm));
        float rippel = mix(0.5, 0.5 + 0.5 * sin(vWorld.x * 55.0 + vnoise(vWorld.xz * 2.0) * 6.0), 1.0 - smoothstep(0.01, 0.04, pxm));
        vec3 alb = vec3(0.62, 0.55, 0.43) * (0.9 + 0.12 * korn + 0.05 * rippel) * (0.95 + 0.1 * vnoise(vWorld.xz * 0.6));
        // nasser Streifen am Wasser (die See liegt bei z < −uShore)
        float nass = 1.0 - smoothstep(-uShore + 0.3, -uShore + 3.5, vWorld.z);
        alb *= mix(1.0, 0.6, nass);
        col = alb * (uSunColor * max(uSunDir.y, 0.0) / PI + uSkyTop);
    }
    col = mix(uFogColor, col, exp(-dist / uFogDist));
    if (uDisplay > 0.0) col = pow(1.0 - exp(-col * uDisplay), vec3(1.0 / 2.2));
    frag = vec4(col, 1.0);
}
