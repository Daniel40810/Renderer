#version 430 core
// rail.vert — Gleis als Netz (seit Brocken Phase 12): Schotter, Schienen, Bahnübergänge und Bahnsteige als fertige
// Dreiecke in der Szene (Ort, Normale, Farbe); mit uInst = 1 die Schwellen als Exemplare eines Einheitsquaders
// (Ort, Richtung, Überhöhung, Ton je Schwelle).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec3 iPos;
layout(location = 4) in vec3 iRot;      // Richtung (rad), Überhöhung (rad), Ton 0..1
uniform mat4 uVP;
uniform int uInst;
uniform vec3 uSchwelle;                 // Länge, Höhe, Breite (m)
uniform int uBeton;
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vKind;                        // 0 Netz, 1 Schwelle

void main() {
    vec3 p = aPos, n = aNrm, c = aCol;
    vKind = 0.0;
    if (uInst == 1) {
        float h = iRot.x, k = iRot.y;
        vec3 r = vec3(cos(h), 0.0, sin(h)), f = vec3(sin(h), 0.0, -cos(h)), u = vec3(0.0, 1.0, 0.0);
        vec3 rr = r * cos(k) - u * sin(k), uu = r * sin(k) + u * cos(k);
        vec3 l = aPos * uSchwelle;
        p = iPos + rr * l.x + uu * l.y + f * l.z;
        n = normalize(rr * aNrm.x + uu * aNrm.y + f * aNrm.z);
        // Holz dunkel und verwittert, Beton hellgrau; jede Schwelle ein wenig anders
        c = uBeton == 1 ? vec3(0.30, 0.29, 0.27) * (0.9 + 0.2 * iRot.z) : mix(vec3(0.07, 0.05, 0.035), vec3(0.13, 0.11, 0.09), iRot.z);
        vKind = 1.0;
    }
    vWorld = p;
    vNormal = n;
    vColor = c;
    gl_Position = uVP * vec4(p, 1.0);
}
