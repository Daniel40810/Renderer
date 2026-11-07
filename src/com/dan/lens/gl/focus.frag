// focus.frag — der Schärfezieher auf der Grafikkarte (com.dan.lens), ein Bildpunkt: (Dioptrien, Dioptrien/s, Ziel, Messung).
// Autofokus: 5 × 5 Messpunkte in der Bildmitte (±uWin), Tiefe → Dioptrien (Himmel 0), das 65. Perzentil (nahe Dinge
// gewinnen, wie bei einer Kamera mit mittenbetontem Feld) [G]. Dann eine kritisch gedämpfte Feder genau gelöst
// (wie FocusPuller.java): x(t) = x_Ziel + (c1 + c2·t)·e^(−ωt), ω = 5,8 / Zeit, höchstens uMaxRate Dioptrien je s.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uDepth;
uniform sampler2D uState;
uniform int uAuto, uSnap;
uniform float uTargetD, uDt, uTimeS, uMaxRate, uWin, uMinD, uMaxD;
uniform vec2 uCenter;           // Messfeld (uv), Vorgabe Bildmitte

void main() {
    vec4 st = texelFetch(uState, ivec2(0), 0);
    float target = uTargetD, meas = 0.0;
    if (uAuto == 1) {
        float v[25];
        for (int j = 0; j < 5; j++)
            for (int i = 0; i < 5; i++) {
                vec2 uv = uCenter + (vec2(float(i), float(j)) / 4.0 - 0.5) * 2.0 * uWin * vec2(1.0, uFull.x / uFull.y);
                float d = texture(uDepth, uv).r;
                float z = viewDepth(d);
                v[j * 5 + i] = (uReversed == 1 && d <= 0.0) || z > uFar * 0.98 ? 0.0 : 1.0 / max(z, 0.05);
            }
        for (int a = 1; a < 25; a++) {          // Einfügen sortieren (aufsteigend: fern → nah)
            float x = v[a];
            int b = a - 1;
            while (b >= 0 && v[b] > x) { v[b + 1] = v[b]; b--; }
            v[b + 1] = x;
        }
        meas = v[16];
        target = meas;
    }
    target = clamp(target, uMinD, uMaxD);
    if (uSnap == 1 || uTimeS <= 0.0 || !(st.x > 0.0) || isnan(st.y) || isinf(st.y)) { frag = vec4(target, 0.0, target, meas); return; }
    float w = 5.8 / uTimeS, dt = uDt;
    float c1 = st.x - target, c2 = st.y + w * c1, e = exp(-w * dt);
    float nx = target + (c1 + c2 * dt) * e;
    float nv = (c2 - w * (c1 + c2 * dt)) * e;
    if (uMaxRate > 0.0 && abs(nv) > uMaxRate) {
        nv = sign(nv) * uMaxRate;
        nx = st.x + nv * dt;
        if ((st.x - target) * (nx - target) < 0.0) { nx = target; nv = 0.0; }
    }
    frag = vec4(clamp(nx, uMinD, uMaxD), nv, target, meas);
}
