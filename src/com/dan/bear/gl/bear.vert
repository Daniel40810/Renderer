// bear.vert — com.dan.bear (Phase 18a von LituyaBay1958): ein Braunbär je Instanz. Die Wirbelsäule biegt sich nach der
// Haltung (Kopf heben, Kopf drehen, Buckel, Neigung um die Hüfte), Querschnitt und Ohren hängen im Rahmen ihrer Stelle
// s, der Unterkiefer dreht um sein Scharnier, die Beine aus Oberarm/Oberschenkel, Unterarm/Unterschenkel und Pfote um
// ihre Gelenke (Schwung und Beugung je Bein; Schulterblatt und Becken gleiten mit). Dann dreht die Rolle den Körper um
// seine Achse, die Richtung stellt ihn in die Welt. Dieselbe Rechnung wie BearBody.deform in Java.
// Körper: x rechts, y oben, z vorn, Länge 1, Hüfte im Ursprung; Welt: x Ost, y oben, z Süd.
layout(location = 0) in vec3 aOff;      // Versatz im Rahmen (Beine: im Rahmen des Glieds ab dem oberen Gelenk)
layout(location = 1) in vec3 aNrm;
layout(location = 2) in float aS;       // Stelle entlang der Wirbelsäule (0 Nase … 1 Schwanz)
layout(location = 3) in float aPart;    // 0 Rumpf, 1 Ohr, 10 + 3·Bein + Glied
layout(location = 4) in float aAng;     // Winkel um die Achse (0 oben); an der Pfote 1 = Kralle
layout(location = 5) in vec4 iA;        // x, y, z (Hüfte), Länge
layout(location = 6) in vec4 iB;        // Richtung (rad), Nässe, Fell (+2 beim Jungen), Takt
layout(location = 7) in vec4 iC;        // Kopf heben, Kopf drehen, Buckel, Neigung
layout(location = 8) in vec4 iD;        // Rolle, Kiefer, –, –
layout(location = 9) in vec4 iE;        // Schwung LF, RF, LH, RH
layout(location = 10) in vec4 iF;       // Beugung LF, RF, LH, RH
uniform mat4 uVP;
out vec3 vWorld, vNrm;
out vec4 vBody;                         // s, Winkel, Teil, Seite
out vec4 vInst;                         // Nässe, Fell, Junges, Takt

const float ANCHOR = 0.80;
const float LEG_OUT = 0.66;
// je Bein: s, Seite, Ansatz y, Ruhe oben, Ruhe unten, Länge oben, Länge unten, Gleiten
const float LS[4] = float[](0.33, 0.33, 0.80, 0.80);
const float LSIDE[4] = float[](-1.0, 1.0, -1.0, 1.0);
const float LY[4] = float[](-0.04, -0.04, -0.04, -0.04);
const float LR1[4] = float[](0.0, 0.0, 0.30, 0.30);
const float LR2[4] = float[](0.0, 0.0, -0.36, -0.36);
const float LL1[4] = float[](0.160, 0.160, 0.170, 0.170);
const float LL2[4] = float[](0.160, 0.160, 0.164, 0.164);
const float LSL[4] = float[](0.10, 0.10, 0.05, 0.05);

float smoothS(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }
float pitchAt(float s) { return iC.w + iC.x * (1.0 - smoothS(0.12, 0.34, s)) + iC.z * clamp((s - 0.56) * 2.6, -1.0, 1.0); }
float yawAt(float s) { return iC.y * (1.0 - smoothS(0.10, 0.36, s)); }

vec3 spine(float s) {
    float ds = (ANCHOR - s) / 10.0;
    vec3 p = vec3(0.0);
    for (int k = 0; k < 10; k++) {
        float m = s + (float(k) + 0.5) * ds, ph = pitchAt(m), yw = yawAt(m);
        p += vec3(sin(yw) * cos(ph), sin(ph), cos(yw) * cos(ph)) * ds;
    }
    return p;
}

// Halbe Breite des Querschnitts an der Stelle s (wie BearBody.PROFILE, nur die Breite)
const float PS[16] = float[](0.000, 0.020, 0.055, 0.095, 0.135, 0.175, 0.225, 0.290, 0.345, 0.420, 0.540, 0.660, 0.780, 0.880, 0.950, 1.000);
const float PW[16] = float[](0.020, 0.040, 0.054, 0.078, 0.100, 0.098, 0.112, 0.142, 0.158, 0.162, 0.164, 0.164, 0.154, 0.128, 0.080, 0.024);
float halfWidth(float s) {
    for (int i = 1; i < 16; i++)
        if (s <= PS[i]) { float t = (s - PS[i - 1]) / (PS[i] - PS[i - 1]); t = t * t * (3.0 - 2.0 * t); return mix(PW[i - 1], PW[i], t); }
    return PW[15];
}

// Drehung um x: + kippt „unten“ nach vorn. (y, z)
vec2 rotX(vec2 yz, float a) { float c = cos(a), s = sin(a); return vec2(yz.x * c + yz.y * s, -yz.x * s + yz.y * c); }

void main() {
    vec3 o = aOff, n = aNrm;
    float s = aS;
    int part = int(aPart + 0.5);
    if (part >= 10) {
        int leg = (part - 10) / 3, seg = (part - 10) - 3 * leg;
        s = LS[leg];
        float sw = iE[leg], b = iF[leg];
        float a1 = LR1[leg] + sw, a2 = a1 + LR2[leg] - b;
        bool fore = leg < 2;
        float ap = fore ? -a2 * 0.75 - b * 0.55 : -a2 * 0.85 - b * 0.35;
        float a3 = a2 + ap;
        vec2 j0 = vec2(LY[leg], LSL[leg] * sin(sw));
        vec2 j1 = j0 + rotX(vec2(-1.0, 0.0), a1) * LL1[leg];
        vec2 j2 = j1 + rotX(vec2(-1.0, 0.0), a2) * LL2[leg];
        float a = seg == 0 ? a1 : seg == 1 ? a2 : a3;
        vec2 jj = seg == 0 ? j0 : seg == 1 ? j1 : j2;
        vec2 q = rotX(o.yz, a), qn = rotX(n.yz, a);
        o = vec3(LSIDE[leg] * halfWidth(s) * LEG_OUT + o.x, jj + q);
        n = vec3(n.x, qn);
    } else if (part == 0 && s < 0.125 && cos(aAng) < -0.15) {
        float k = smoothS(-0.15, -0.55, cos(aAng));
        vec2 q = rotX(vec2(o.y, 0.125 - s), -iD.y * k);
        o.y = q.x;
        o.z += q.y - (0.125 - s);
    }
    float ph = pitchAt(s), yw = yawAt(s);
    vec3 f = vec3(sin(yw) * cos(ph), sin(ph), cos(yw) * cos(ph));
    vec3 r = vec3(cos(yw), 0.0, -sin(yw));
    vec3 u = cross(f, r);
    vec3 p = spine(s) + r * o.x + u * o.y + f * o.z;
    vec3 qq = r * n.x + u * n.y + f * n.z;
    float cr = cos(iD.x), sr = sin(iD.x);
    p = vec3(p.x * cr - p.y * sr, p.x * sr + p.y * cr, p.z);
    qq = vec3(qq.x * cr - qq.y * sr, qq.x * sr + qq.y * cr, qq.z);
    float h = iB.x;
    vec3 R = vec3(cos(h), 0.0, sin(h)), F = vec3(sin(h), 0.0, -cos(h));
    vec3 w = iA.xyz + (R * p.x + vec3(0.0, 1.0, 0.0) * p.y + F * p.z) * iA.w;
    vWorld = w;
    vNrm = R * qq.x + vec3(0.0, 1.0, 0.0) * qq.y + F * qq.z;
    vBody = vec4(aS, aAng, aPart, aOff.x < 0.0 ? -1.0 : 1.0);
    vInst = vec4(iB.y, fract(iB.z), iB.z >= 2.0 ? 1.0 : 0.0, iB.w);
    gl_Position = uVP * vec4(w, 1.0);
}
