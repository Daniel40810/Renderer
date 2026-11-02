// forestgpu_imp.vert — ein ferner Baum als Oktaeder-Impostor (seit LituyaBay1958 Phase 6): ein Viereck zum Auge, Größe
// aus dem Hüllradius des Typs. Aus der Blickrichtung im Modellraum (gegen die Drehung der Instanz) folgen die vier
// nächsten Ansichten des Halb-Oktaeders (N × N) und ihre bilinearen Gewichte; jede Ecke wird in die Bildachsen jeder
// Ansicht projiziert (wie com.dan.forest.Octahedral), so dass der Atlas ohne Versatz getroffen wird.
layout(location = 0) in vec2 aCorner;
layout(location = 4) in vec4 iA;        // x, y, z, Drehung
layout(location = 5) in vec4 iB;        // Maßstab, Tönung, Typ, Verbreiterung
layout(std430, binding = 3) readonly buffer Types { vec4 tinfo[]; };
uniform mat4 uVP;
uniform vec3 uCam;
uniform float uFrames;
out vec2 vUV0, vUV1, vUV2, vUV3;
flat out vec4 vW;
flat out vec2 vF;
flat out float vLayer;
flat out vec2 vRot;
flat out float vTint;
out vec3 vWorld;

vec2 octEnc(vec3 d) { d.y = max(d.y, 0.0); float l = abs(d.x) + d.y + abs(d.z); vec2 p = d.xz / l; return vec2(p.x + p.y, p.x - p.y); }
vec3 octDec(vec2 o) { vec2 p = vec2(o.x + o.y, o.x - o.y) * 0.5; float y = 1.0 - abs(p.x) - abs(p.y); return normalize(vec3(p.x, y, p.y)); }
vec2 frameUV(vec2 f, vec3 q, float R) {
    vec3 d = octDec((f + 0.5) / uFrames * 2.0 - 1.0);
    vec3 r = vec3(d.z, 0.0, -d.x);
    float l = length(r);
    r = l < 1e-4 ? vec3(1.0, 0.0, 0.0) : r / l;
    vec3 u = normalize(vec3(d.y * r.z, d.z * r.x - d.x * r.z, -d.y * r.x));
    vec2 loc = vec2(dot(q, r), dot(q, u)) / R * 0.5 + 0.5;
    return (f + loc) / uFrames;
}

void main() {
    float c = cos(iA.w), s = sin(iA.w);
    int type = int(iB.z + 0.5);
    vec4 T = tinfo[type];
    float scale = iB.x, widen = max(1.0, iB.w), R = T.y;
    vec3 C = iA.xyz + vec3(0.0, T.z * scale, 0.0);
    vec3 Vw = normalize(uCam - C);
    vec3 Vm = vec3(c * Vw.x + s * Vw.z, max(Vw.y, 0.0), -s * Vw.x + c * Vw.z);
    vec2 g = (octEnc(normalize(Vm + vec3(0.0, 1e-4, 0.0))) * 0.5 + 0.5) * uFrames - 0.5;
    vec2 f0 = clamp(floor(g), vec2(0.0), vec2(uFrames - 2.0));
    vec2 t = clamp(g - f0, 0.0, 1.0);
    vW = vec4((1.0 - t.x) * (1.0 - t.y), t.x * (1.0 - t.y), (1.0 - t.x) * t.y, t.x * t.y);
    vF = f0;
    vec3 right = cross(vec3(0.0, 1.0, 0.0), Vw);
    right = length(right) < 1e-4 ? vec3(1.0, 0.0, 0.0) : normalize(right);
    vec3 up = cross(Vw, right);
    float Rw = R * scale * widen;
    vec3 P = C + (right * aCorner.x + up * aCorner.y) * Rw;
    vec3 q = P - C;
    q = vec3(c * q.x + s * q.z, q.y, -s * q.x + c * q.z) / scale;
    q.xz /= widen;
    vUV0 = frameUV(f0, q, R);
    vUV1 = frameUV(f0 + vec2(1.0, 0.0), q, R);
    vUV2 = frameUV(f0 + vec2(0.0, 1.0), q, R);
    vUV3 = frameUV(f0 + vec2(1.0, 1.0), q, R);
    vLayer = float(type);
    vRot = vec2(c, s);
    vTint = iB.y;
    vWorld = P;
    gl_Position = uVP * vec4(P, 1.0);
}
