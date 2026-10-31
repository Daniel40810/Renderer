// herd.vert — ein Tier je Instanz. Beine schwingen um die Hüften (Trab: diagonale Paare im Gleichtakt), der Kopf
// senkt sich um den Hals zum Fressen, der Schwanz wedelt; beim Liegen klappen die Beine ein und der Körper sinkt, beim
// Sitzen (Hund) kippt er um die hintere Hüfte. Dann Drehung um die Hochachse und Ort.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in float aPart;
layout(location = 4) in float aTint;
layout(location = 5) in vec4 iPos;      // Ort (Boden), Richtung (rad, von Nord im Uhrzeigersinn)
layout(location = 6) in vec4 iPose;     // Phase des Gangs, Schwung der Beine (rad), Kopf unten 0..1, Liegen 0..1
layout(location = 7) in vec4 iCoat;     // Farbe des Fells (linear), Sitzen 0..1
layout(location = 8) in vec4 iMisc;     // Schwanz (rad), Größe, frei, frei
uniform mat4 uVP;
uniform vec2 uNeck, uHipF, uHipB, uTail;  // Drehpunkte (y, z)
uniform float uLeg, uHeadDown;            // Beinlänge (m), Senken des Kopfs beim Fressen (rad)
out vec3 vWorld, vNrm, vCol;

mat3 rotX(float a) { float c = cos(a), s = sin(a); return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c); }

void main() {
    int part = int(aPart + 0.5);
    vec3 p = aPos, n = aNrm;
    float lie = iPose.w;
    if (part == 1) {
        // Kopf: nach unten um den Hals (vorn ist −z: positive Drehung um x senkt die Schnauze)
        mat3 R = rotX(-iPose.z * uHeadDown);
        vec3 pv = vec3(0.0, uNeck.x, uNeck.y);
        p = pv + R * (p - pv);
        n = R * n;
    } else if (part >= 2 && part <= 5) {
        bool front = part <= 3;
        vec2 hip = front ? uHipF : uHipB;
        // Trab: vorn links mit hinten rechts
        float ph = (part == 2 || part == 5) ? 0.0 : 3.14159;
        float a = iPose.y * sin(iPose.x + ph);
        // Sitzen: Vorderbeine bleiben senkrecht (gegen die Drehung des Körpers), Hinterbeine falten sich ein
        float sitA = 0.45 * iCoat.w;
        if (front) a -= sitA;
        mat3 R = rotX(a);
        vec3 pv = vec3(0.0, hip.x, hip.y);
        p = pv + R * (p - pv);
        n = R * n;
        // Liegen: die Beine liegen eingeklappt unter dem Körper (gestaucht zur Hüfte hin)
        float fold = lie * 0.88 + (front ? 0.0 : iCoat.w * 0.75);
        p.y = hip.x + (p.y - hip.x) * (1.0 - fold);
    } else if (part == 6) {
        // Schwanz: seitlich wedeln (um die Hochachse am Ansatz)
        float a = iMisc.x;
        float c = cos(a), s = sin(a);
        vec3 pv = vec3(0.0, uTail.x, uTail.y);
        vec3 d = p - pv;
        p = pv + vec3(c * d.x + s * d.z, d.y, -s * d.x + c * d.z);
        n = vec3(c * n.x + s * n.z, n.y, -s * n.x + c * n.z);
    }
    // Liegen: der ganze Körper sinkt um fast die Beinlänge
    p.y -= lie * uLeg * 0.85;
    // Sitzen: um die Vorderfüße kippen, das Hinterteil sinkt (Vorderende hoch)
    float sit = iCoat.w;
    if (sit > 0.0) {
        mat3 R = rotX(0.45 * sit);
        vec3 pv = vec3(0.0, 0.0, uHipF.y);
        p = pv + R * (p - pv);
        n = R * n;
    }
    // Wippen im Gang
    p.y += 0.015 * iPose.y * abs(sin(iPose.x * 2.0)) * uLeg;
    p *= iMisc.y;
    float h = iPos.w, c = cos(h), s = sin(h);
    // Körper: −z ist vorn; Richtung h von Nord im Uhrzeigersinn: vorn = (sin h, 0, −cos h), rechts = (cos h, 0, sin h)
    vec3 right = vec3(c, 0.0, s), fwd = vec3(s, 0.0, -c);
    vec3 w = iPos.xyz + right * p.x + vec3(0.0, p.y, 0.0) - fwd * p.z;
    vWorld = w;
    vNrm = right * n.x + vec3(0.0, n.y, 0.0) - fwd * n.z;
    vCol = mix(aCol, aCol * iCoat.rgb, aTint);
    gl_Position = uVP * vec4(w, 1.0);
}
