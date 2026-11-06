// floe.vert — ein Stück Eis aus com.dan.buoyancy.Floes (seit LituyaBay1958 W6 Runde 3): Netz in Einheiten von
// Durchmesser (x, z) und Höhe über Wasser (y), je Instanz Ort, Drehung, Durchmesser, Höhe, Neigung, Tönung, Phase.
// uShadow = 1: Deckel und Kante entlang der Sonne auf den Wasserspiegel gelegt (Schatten).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in float aPart;
layout(location = 3) in vec4 iA;   // x, y (Wasser), z, Drehung
layout(location = 4) in vec4 iB;   // Durchmesser, Höhe, Neigung a, b
layout(location = 5) in vec4 iC;   // Tönung, Form, Phase, 0
uniform mat4 uVP;
uniform vec3 uSunDir;
uniform float uTime;
uniform int uShadow;
out vec3 vWorld, vNrm;
out float vPart, vTint, vLocalY;

mat3 rotY(float a) { float c = cos(a), s = sin(a); return mat3(c, 0, -s, 0, 1, 0, s, 0, c); }
mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1, 0, 0, 0, c, s, 0, -s, c); }
mat3 rotZ(float a) { float c = cos(a), s = sin(a); return mat3(c, s, 0, -s, c, 0, 0, 0, 1); }

void main() {
    float d = iB.x, h = iB.y;
    mat3 R = rotY(iA.w) * rotX(iB.z) * rotZ(iB.w);
    vec3 p = vec3(aPos.x * d, aPos.y * h, aPos.z * d);
    vec3 n = normalize(vec3(aNrm.x / d, aNrm.y / max(h, 0.05), aNrm.z / d));
    // leichtes Schaukeln (Dünung im Inlet) [G]
    float bob = 0.035 * h * sin(uTime * 0.9 + iC.z);
    vec3 w = vec3(iA.x, iA.y + bob, iA.z) + R * p;
    if (uShadow == 1) {
        float sy = max(uSunDir.y, 0.07);
        float lift = w.y - iA.y;
        w = w - uSunDir * (lift / sy);
        w.y = iA.y + 0.12;
    }
    vWorld = w;
    vNrm = R * n;
    vPart = aPart;
    vTint = iC.x;
    vLocalY = aPos.y;
    gl_Position = uVP * vec4(w, 1.0);
}
