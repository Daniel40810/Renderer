// understory.vert — eine Pflanze je Instanz (Netz aus com.dan.understory.PlantMesh, Fuß bei 0), gedreht, skaliert, im
// Wind gewiegt (Wedel und Zweige mit dem Quadrat der Höhe im Netz, Blätter zittern; Bartflechten pendeln zur Spitze hin)
// und an ihren Ort gesetzt. Mit DEPTH_ONLY nur die Lage für eine Schattenkarte.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec2 aAux;      // Höhe im Netz 0..1, Laub 0/1
layout(location = 4) in vec4 iA;        // x, y, z, Drehung
layout(location = 5) in vec4 iB;        // Maßstab, Tönung, Phase, Ausblenden
uniform mat4 uVP;
uniform float uTime, uSway;             // Biegsamkeit der Art
uniform vec4 uWind;                     // wohin x, z, Geschwindigkeit, Böigkeit
#ifndef DEPTH_ONLY
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vLeafy;
out float vFade;
out float vH;
#endif
void main() {
    float c = cos(iA.w), s = sin(iA.w);
    vec3 p = aPos * iB.x;
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    float U = uWind.z;
    float gust = 1.0 + uWind.w * 0.6 * sin(uTime * 0.9 - dot(iA.xz, uWind.xy) * 0.07 + iB.z);
    float h2 = aAux.x * aAux.x;
    float sway = uSway * h2 * (min(U, 18.0) * 0.012 * gust + 0.01 * sin(uTime * (1.3 + 0.3 * fract(iB.z)) + iB.z) * min(U, 10.0) * 0.2);
    p.xz += uWind.xy * sway * max(0.3, iB.x) + vec2(cos(iB.z), sin(iB.z)) * sway * 0.3 * sin(uTime * 2.1 + iB.z);
    p += aNrm * (aAux.y * 0.006 * min(U, 12.0) * sin(uTime * 7.0 + dot(aPos, vec3(5.1, 3.7, 4.3))));
    vec3 w = iA.xyz + p;
    gl_Position = uVP * vec4(w, 1.0);
#ifndef DEPTH_ONLY
    vNormal = vec3(c * aNrm.x - s * aNrm.z, aNrm.y, s * aNrm.x + c * aNrm.z);
    vColor = aCol * mix(vec3(0.85, 0.9, 0.88), vec3(1.15, 1.08, 0.95), iB.y);
    vWorld = w;
    vLeafy = aAux.y;
    vFade = iB.w;
    vH = aAux.x;
#endif
}
