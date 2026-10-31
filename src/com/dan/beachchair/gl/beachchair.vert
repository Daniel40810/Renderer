#version 430 core
// beachchair.vert — viele Strandkörbe auf einmal (je Detailstufe ein Aufruf). Je Ecke: Ort, Normale, Musterkoordinaten,
// Teil, Gruppe, innen; je Korb: Ort und Blickrichtung, Farbe der Streifen, Stellung. Die Gruppe 1 (Oberteil) kippt um das
// Gelenk hinten auf dem Unterkasten, die Gruppe 2 (Fußstützen) gleitet nach vorn, die Gruppe 3 (Verschluss) gibt es nur
// bei verschlossenen Körben. Erdkrümmung wie das Gelände der Szene (uEarthR, 0 = flach).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec3 aInfo;     // Teil, Gruppe, innen
layout(location = 4) in vec4 iPos;      // x, y, z, Blickrichtung (Bogenmaß von Nord, im Uhrzeigersinn)
layout(location = 5) in vec4 iCol;      // dunkler Streifen (linear), Kennzahl 0..1
layout(location = 6) in vec4 iPose;     // Lehne (Bogenmaß), Fußstützen (m), verschlossen 0/1, frei

uniform mat4 uViewProj;
uniform vec3 uEye;
uniform vec2 uHinge;                    // Gelenk der Lehne: z, y am Korb
uniform float uEarthR;
uniform vec3 uSunDir;

out vec3 vWorld;
out vec3 vNormal;
out vec3 vLocal;
out vec2 vUv;
out vec3 vSunL;
flat out vec3 vInfo;
flat out vec4 vCol;

void main() {
    vec3 p = aPos, n = aNrm;
    float g = aInfo.y;
    bool closed = iPose.z > 0.5;
    float rec = closed ? 0.0 : iPose.x, foot = closed ? 0.0 : iPose.y;
    if (g > 0.5 && g < 1.5) {
        float c = cos(rec), s = sin(rec);
        vec2 d = vec2(p.z, p.y) - uHinge;
        p.z = uHinge.x + d.x * c - d.y * s;
        p.y = uHinge.y + d.x * s + d.y * c;
        n = vec3(n.x, n.z * s + n.y * c, n.z * c - n.y * s);
    } else if (g > 1.5 && g < 2.5) {
        p.z += foot;
    } else if (g > 2.5 && !closed) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);   // außerhalb: Verschluss nur abends
        vWorld = vec3(0.0); vNormal = vec3(0.0, 1.0, 0.0); vLocal = vec3(0.0); vUv = vec2(0.0); vSunL = vec3(0.0, 1.0, 0.0);
        vInfo = aInfo; vCol = iCol;
        return;
    }
    float h = iPos.w, ch = cos(h), sh = sin(h);
    // am Korb: x rechts, z vorn; in der Welt: vorn = (sin h, 0, −cos h), rechts = (cos h, 0, sin h)
    vec3 w = iPos.xyz + vec3(p.x * ch + p.z * sh, p.y, p.x * sh - p.z * ch);
    vWorld = w;
    vNormal = vec3(n.x * ch + n.z * sh, n.y, n.x * sh - n.z * ch);
    vLocal = p;
    vUv = aUv;
    vSunL = vec3(uSunDir.x * ch + uSunDir.z * sh, uSunDir.y, uSunDir.x * sh - uSunDir.z * ch);
    vInfo = aInfo;
    vCol = iCol;
    vec2 dh = w.xz - uEye.xz;
    float drop = uEarthR > 0.0 ? dot(dh, dh) / (2.0 * uEarthR) : 0.0;
    gl_Position = uViewProj * vec4(w.x, w.y - drop, w.z, 1.0);
}
