// glare.vert — ein Lichtpunkt je Feuer als Quadrat um seinen Bildort. Verdeckt die Szene den Ort, fällt er weg.
uniform mat4 uVP;
uniform vec4 uGlarePos[8];     // xyz, Kernanteil (0 = Linse schon als Fläche sichtbar)
uniform vec4 uGlareE[8];       // Beleuchtungsstärke am Auge RGB (lx, vorbelichtet), Prüfabstand vor dem Licht (m)
uniform vec3 uCam;
uniform sampler2D uDepth;
uniform int uReversed;
uniform vec2 uViewport;
uniform float uSize;           // halbe Kantenlänge in Bildpunkten
out vec2 vPix;
flat out vec4 vE;
flat out float vCore;
void main() {
    int id = gl_InstanceID;
    vec2 corner = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID & 2) == 0 ? -1.0 : 1.0);
    vec4 c = uVP * vec4(uGlarePos[id].xyz, 1.0);
    // verdeckt prüfen ein Stück vor dem Licht (die eigene Laterne und ihr Glas verdecken es nicht)
    vec3 toCam = uCam - uGlarePos[id].xyz;
    vec4 ct = uVP * vec4(uGlarePos[id].xyz + toCam / max(length(toCam), 1e-3) * min(uGlareE[id].w, 0.5 * length(toCam)), 1.0);
    vE = uGlareE[id];
    vCore = uGlarePos[id].w;
    if (c.w <= 0.0) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vPix = vec2(0.0); return; }
    vec3 n = c.xyz / c.w;
    vec2 uv = n.xy * 0.5 + 0.5;
    vec3 nt = ct.xyz / max(ct.w, 1e-6);
    float lightZ = uReversed == 1 ? nt.z : nt.z * 0.5 + 0.5;
    float sceneZ = texelFetch(uDepth, ivec2(clamp(uv, vec2(0.0), vec2(0.9999)) * uViewport), 0).r;
    bool hidden = uReversed == 1 ? sceneZ > lightZ * 1.0001 + 1e-7 : sceneZ < lightZ - 1e-5;
    if (hidden || any(lessThan(uv, vec2(-0.2))) || any(greaterThan(uv, vec2(1.2)))) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vPix = vec2(0.0); return; }
    vPix = corner * uSize;
    gl_Position = vec4(n.xy + corner * uSize * 2.0 / uViewport, uReversed == 1 ? 1.0 : -1.0, 1.0);
}
