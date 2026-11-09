// aurora.frag — der Strahlengang des Polarlichts auf der Grafikkarte, Schritt für Schritt wie Aurora.strahlung in
// Java: in der Höhe durch die Schale marschieren (fein bis uHFein, darüber das glatte Rot in vierfachen Schritten),
// jeden Punkt entlang der Feldlinie auf seine Fußlinie in 100 km zurückrechnen (u entlang des Bogens, v polwärts),
// dort die Dichte der Vorhänge aus den Tabellen (Mitte, Mitte des Rots, Strahlen, Helligkeit; Stufen über die
// Mip-Stufen) und das Höhenprofil je Linie aus der Profiltextur lesen, aufsummieren, am Ende die Extinktion der Luft.
// Heraus kommt lineares sRGB in cd/m² mal uScale (Vorbelichtung durch Einheit des Wirts).
// Mit PANORAMA: dasselbe als Rundumbild (Azimut von Nord, Höhe), wie com.dan.sky es für Spiegelungen legt.
in vec2 vUV;
out vec4 frag;

uniform sampler1DArray uTab;   // je Vorhang 4 Schichten über u (−U..U): 0 Mitte v, 1 Mitte des Rots, 2 Strahlfaktor, 3 Helligkeit
uniform sampler2D uProf;       // je Vorhang 9 Zeilen über die Höhe (80..400 km): glatt 4 Linien, Strahlen 4 Linien, Rot
uniform int uN;                // Zahl der Vorhänge
uniform float uBreite[8], uSigmaRot[8], uStrahlen[8], uHUnten[8], uHOben[8], uVMin[8], uVMax[8];
uniform vec2 uE, uP;           // horizontale Einheitsvektoren (Ost, Nord): entlang der Bögen und polwärts
uniform float uCotI, uHMin, uHMax, uHFein, uDh, uU, uSchritt, uZeilen, uHoehen, uMaxLod;
uniform vec4 uTau;             // optische Dicke im Zenit je Linie
uniform vec3 uFarbe[4];        // lineares sRGB in cd/m² je kR je Linie
uniform float uScale;
uniform mat4 uInvVP;
uniform int uReversed;
uniform vec3 uOst, uNord, uOben;   // die Richtungen Ost, Nord und oben in den Koordinaten des Wirts

const float R = 6371.2;
const int ROT = 2;

vec3 viewRay(vec2 uv) {
    vec2 ndc = uv * 2.0 - 1.0;
    float zn = uReversed == 1 ? 1.0 : -1.0, zm = uReversed == 1 ? 0.5 : 0.0;
    vec4 a = uInvVP * vec4(ndc, zn, 1.0);
    vec4 b = uInvVP * vec4(ndc, zm, 1.0);
    return normalize(b.xyz / b.w - a.xyz / a.w);
}

float weg(float dz, float h) { return -R * dz + sqrt(R * R * dz * dz + 2.0 * R * h + h * h); }
float gauss(float x) { return exp(-0.5 * x * x); }
float luftmasse(float hoeheGrad) {
    float h = max(0.0, hoeheGrad);
    return 1.0 / (sin(radians(h)) + 0.50572 * pow(h + 6.07995, -1.6364));
}

vec4 strahlung(vec3 e) {
    vec4 kr = vec4(0.0);
    if (e.z <= 0.0005 || uN == 0) return kr;
    float dU = dot(e.xy, uE), dP = dot(e.xy, uP);
    int nFein = int(ceil((uHFein - uHMin) / uDh)), n = nFein + int(ceil((uHMax - uHFein) / (4.0 * uDh)));
    float tVor = weg(e.z, uHMin);
    float uVor = tVor * dU, vVor = tVor * dP + (uHMin - 100.0) * uCotI;
    for (int i = 0; i < n; i++) {
        bool fein = i < nFein;
        float dh = fein ? uDh : 4.0 * uDh;
        float h0 = fein ? uHMin + float(i) * uDh : uHFein + float(i - nFein) * dh;
        float h1 = min(fein ? uHFein : uHMax, h0 + dh), hm = 0.5 * (h0 + h1);
        if (h1 <= h0) continue;
        float t1 = weg(e.z, h1), tm = weg(e.z, hm);
        float u = tm * dU, v = tm * dP + (hm - 100.0) * uCotI;
        float u1 = t1 * dU, v1 = t1 * dP + (h1 - 100.0) * uCotI;
        float du = abs(u1 - uVor), dv = abs(v1 - vVor);
        uVor = u1;
        vVor = v1;
        float dl = (t1 - tVor) * 1e5;
        tVor = t1;
        float x = (u + uU) / (2.0 * uU);
        if (x < 0.0 || x > 1.0) continue;
        float hx = (hm - 80.0 + 0.5) / uHoehen;
        float lod = clamp(log2(max(du, uSchritt) / uSchritt), 0.0, uMaxLod);
        for (int k = 0; k < uN; k++) {
            if (hm < uHUnten[k] || hm > uHOben[k] || v < uVMin[k] || v > uVMax[k]) continue;
            float L = float(k * 4);
            float vc = textureLod(uTab, vec2(x, L), lod).r;
            float vr = textureLod(uTab, vec2(x, L + 1.0), lod).r;
            float ray = textureLod(uTab, vec2(x, L + 2.0), lod).r;
            float he = textureLod(uTab, vec2(x, L + 3.0), lod).r;
            float b = uBreite[k], s = sqrt(b * b + dv * dv / 12.0);
            float g = b / s * gauss((v - vc) / s) * he;
            float d0 = (1.0 - uStrahlen[k]) * g, d1 = uStrahlen[k] * ray * g;
            float sr0 = uSigmaRot[k], sr = sqrt(sr0 * sr0 + dv * dv / 12.0);
            float d2 = sr0 / sr * gauss((v - vr) / sr) * he;
            if (d0 + d1 + d2 <= 0.0) continue;
            float Z = float(k * 9) + 0.5;
            for (int l = 0; l < 4; l++) {
                float el;
                if (l == ROT) el = d2 * texture(uProf, vec2(hx, (Z + 8.0) / uZeilen)).r;
                else el = d0 * texture(uProf, vec2(hx, (Z + float(l)) / uZeilen)).r + d1 * texture(uProf, vec2(hx, (Z + 4.0 + float(l)) / uZeilen)).r;
                kr[l] += el * dl;
            }
        }
    }
    float luft = luftmasse(degrees(asin(min(1.0, e.z))));
    return kr / 1e9 * exp(-uTau * luft);
}

void main() {
#ifdef PANORAMA
    float az = (vUV.x - 0.5) * 2.0 * 3.14159265, el = (vUV.y - 0.5) * 3.14159265;
    vec3 d = vec3(sin(az) * cos(el), sin(el), -cos(az) * cos(el));
#else
    vec3 d = viewRay(vUV);
#endif
    vec3 e = vec3(dot(d, uOst), dot(d, uNord), dot(d, uOben));
    vec4 kr = strahlung(e);
    vec3 rgb = kr.x * uFarbe[0] + kr.y * uFarbe[1] + kr.z * uFarbe[2] + kr.w * uFarbe[3];
    frag = vec4(rgb * uScale, 0.0);
}
