#version 430 core
// sail.frag — beidseitig beleuchtet; helles Tuch scheint im Gegenlicht durch; unter Wasser dunkler; Dunst.
in vec3 vWorld;
in vec3 vNormal;
in vec3 vColor;
uniform vec3 uCam, uSunDir, uSunColor, uSkyColor, uGroundColor;
uniform vec4 uFog;
uniform float uWater, uExposure;
uniform int uTonemap;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vNormal);
    vec3 v = normalize(uCam - vWorld);
    if (dot(n, v) < 0.0) n = -n;
    float ndl = dot(n, uSunDir);
    float cloth = step(0.7, vColor.r + vColor.g * 0.2) * 0.35;      // helle Segel lassen Licht durch
    vec3 c = vColor * (uSunColor * (max(ndl, 0.0) + cloth * max(-ndl, 0.0)) + mix(uGroundColor, uSkyColor, 0.5 + 0.5 * n.y));
    if (vWorld.y < uWater) c *= vec3(0.25, 0.45, 0.55);
    c = mix(c, uFog.rgb, 1.0 - exp(-length(uCam - vWorld) / max(uFog.w, 1.0)));
    if (uTonemap == 1) {
        c *= uExposure;
        c = c / (1.0 + c * 0.25);
        c = pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
    }
    fragColor = vec4(c, 1.0);
}
