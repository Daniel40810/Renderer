// herd.frag — Fell und Gefieder matt (Albedo/π), Licht wie die Flächen der App (Sonne und Mond als Bestrahlung,
// Himmel als Bestrahlung/π, vorbelichtet), Sonnenschatten aus der Wolkenkarte, ein wenig Licht von unten (Boden), Dunst.
in vec3 vWorld, vNrm, vCol;
out vec4 frag;
uniform vec3 uCam, uSunDir, uSunCol, uMoonDir, uMoonCol, uSky, uFogColor;
uniform float uFogDist;
uniform sampler2D uCloudShadow;
uniform vec4 uCloudRect;
const float PI = 3.14159265;
void main() {
    vec3 N = normalize(vNrm), V = normalize(uCam - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    float shade = 1.0;
    if (uCloudRect.w > 0.0) shade = textureLod(uCloudShadow, (vWorld.xz - uCloudRect.xy) / uCloudRect.z, 0.0).r;
    float ns = max(dot(N, uSunDir), 0.0) * smoothstep(-0.02, 0.04, uSunDir.y), nm = max(dot(N, uMoonDir), 0.0) * smoothstep(-0.02, 0.04, uMoonDir.y);
    float up = N.y * 0.5 + 0.5;
    vec3 col = vCol / PI * (uSunCol * ns * shade + uMoonCol * nm + uSky * (0.35 + 0.65 * up));
    float d = length(vWorld - uCam);
    col = mix(col, uFogColor, 1.0 - exp(-d / max(1.0, uFogDist)));
    frag = vec4(col, 1.0);
}
