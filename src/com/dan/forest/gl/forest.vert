// forest.vert — ein Baum je Instanz: Netz der Art, Variante und Stufe (Fuß bei 0, örtlich), gedreht, skaliert, die
// Krone in der Ferne verbreitert (ausgedünnter Bestand), im Wind gebogen und an seinen Ort gesetzt. Mit DEPTH_ONLY
// nur die Lage für die Sonnenschattenkarte.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in vec2 aAux;      // Höhe im Baum 0..1, Laub (1 Blatt, 0,5 Kronenhülle, 0 Holz)
layout(location = 4) in vec4 iA;        // x, y, z, Drehung (rad)
layout(location = 5) in vec4 iB;        // Maßstab, Tönung, Phase, Verbreiterung
uniform mat4 uVP;
uniform vec3 uCam;
uniform float uTime;
uniform vec4 uWind;                     // Richtung (wohin) x, z, Geschwindigkeit (m/s), Böigkeit 0..1
uniform vec4 uRime;                     // Eis: Dicke (m), Art (0 keins, 1 Raueis, 2 Raureif, 3 Klareis), woher x, z
uniform vec2 uRimeBand;                 // darunter kein Eis, darüber die volle Dicke (m über NN)
#ifndef DEPTH_ONLY
out vec3 vWorld;
out vec3 vNormal;
out vec3 vColor;
out float vLeafy;
out float vH;
out float vRime;                        // Dicke des Eises an dieser Ecke (m)
#endif

void main() {
    float c = cos(iA.w), s = sin(iA.w);
    vec3 p = aPos * iB.x;
    p.xz *= mix(1.0, iB.w, smoothstep(0.15, 0.4, aAux.x));
    p = vec3(c * p.x - s * p.z, p.y, s * p.x + c * p.z);
    // Wind: stetige Neigung mit U², Böen laufen über die Fläche, der Baum schwingt in seiner Phase
    float U = uWind.z;
    float wave = sin(uTime * 0.7 - dot(iA.xz, uWind.xy) * 0.045 + iB.z * 0.3);
    float gust = 1.0 + uWind.w * 0.6 * wave;
    float lean = clamp(U * U * 0.0007, 0.0, 0.07) * gust;
    float swing = sin(uTime * (0.9 + 0.25 * fract(iB.z)) + iB.z) * 0.0025 * U;
    float h2 = aAux.x * aAux.x;
    vec2 dir = uWind.xy;
    p.xz += (dir * lean + vec2(swing, swing * 0.6)) * p.y * h2;
    // Laub zittert (klein, nur nah sichtbar)
    float fl = step(0.75, aAux.y) * 0.012 * min(U, 14.0) * sin(uTime * 6.3 + dot(aPos, vec3(3.1, 1.7, 2.3)) + iB.z);
    p += vec3(fl, fl * 0.5, -fl * 0.7);
    // Eis: Raueis wächst auf der Luvseite der Krone als Fahne gegen den Wind (an Nadeln und dürren Zweigen, Holz der
    // Stämme kaum), die Last drückt die Äste nach unten; Raureif und Klareis legen sich nur um
    float dicke = uRime.x * smoothstep(uRimeBand.x, uRimeBand.y, iA.y + p.y);
    if (dicke > 0.0) {
        vec3 von = vec3(uRime.z, 0.0, uRime.w);
        vec3 nw = vec3(c * aNrm.x - s * aNrm.z, aNrm.y, s * aNrm.x + c * aNrm.z);
        float fein = max(step(0.25, aAux.y), smoothstep(0.3, 0.6, aAux.x));   // Krone und obere Zweige
        if (uRime.y < 1.5) {
            float luv = smoothstep(-0.3, 0.7, dot(nw, von));
            p += (von * dicke * (0.4 + 0.6 * luv) + nw * dicke * 0.2 * luv) * fein;
            p.y -= min(dicke * 0.8, 0.25) * h2 * fein * (1.0 - aAux.x * 0.5);
        } else p += nw * dicke * 0.33 * fein;
    }
    vec3 w = iA.xyz + p;
    gl_Position = uVP * vec4(w, 1.0);
#ifndef DEPTH_ONLY
    vNormal = vec3(c * aNrm.x - s * aNrm.z, aNrm.y, s * aNrm.x + c * aNrm.z);
    vColor = aCol * mix(vec3(0.84, 0.88, 0.86), vec3(1.14, 1.08, 0.96), iB.y);
    vWorld = w;
    vLeafy = aAux.y;
    vH = aAux.x;
    vRime = dicke;
#endif
}
