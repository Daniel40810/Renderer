// surface.glsl — Baustein für den Pixelshader der App (com.dan.surface, Phase 5 von LituyaBay1958).
// Einsetzen als Include von /com/dan/surface/gl/surface.glsl (oder SurfaceGl.glsl()); Werte setzt SurfaceGl.bind().
// Die Materialien liegen als Schichten in drei Texturfeldern: uSurfA (Farbe sRGB → linear, Höhe im Alpha), uSurfN
// (Normale x, y), uSurfR (Rauheit, Verdeckung). Je Schicht in uSurfParam: 1/Kachel (1/m), Höhe der Prägung (m),
// drehen (0/1), dreiseitig (0/1); in uSurfMean: mittlere Farbe (linear) und Rauheit für die Ferne; in uSurfWet: wie sehr
// Nässe dunkelt.
// Kachelung: Sechseckgitter nach Mikkelsen 2022 (Practical Real-Time Hex-Tiling) mit ganzzahligem Hash, gleich wie
// HexTiling.java; Mip-Stufen über textureGrad mit den Ableitungen vor dem Versatz (keine Nähte an den Dreiecken).
// Licht macht die App: sie bekommt Farbe, Normale (Welt), Rauheit, Verdeckung, Höhe.
uniform highp sampler2DArray uSurfA;
uniform highp sampler2DArray uSurfN;
uniform highp sampler2DArray uSurfR;
uniform vec4 uSurfParam[16];
uniform vec4 uSurfMean[16];
uniform float uSurfWet[16];
uniform int uSurfCount;
uniform float uSurfFar;      // ab dieser Bildpunktgröße (Anteil der Kachel) nur das Mittel; Vorgabe 0,35

const float SURF_GAIN = 6.0;              // wie HexTiling.GAIN
const float SURF_CELLS = 1.7320508;       // wie HexTiling.CELLS (2√3 · 0,5)

struct Surf {
    vec3 albedo;    // linear
    vec3 normal;    // Welt
    float rough;
    float ao;
    float height;   // 0 … 1 (für das Mischen über die Höhe)
};

uint surfPcg(uint v) {
    uint state = v * 747796405u + 2891336453u;
    uint word = ((state >> ((state >> 28u) + 4u)) ^ state) * 277803737u;
    return (word >> 22u) ^ word;
}

vec2 surfHash(ivec2 p) {
    uint s = uint(p.x) * 1597334677u ^ uint(p.y) * 3812015801u;
    uint a = surfPcg(s), b = surfPcg(a);
    return vec2(float(a >> 8u), float(b >> 8u)) / 16777216.0;
}

// Eine Ebene, hex-gekachelt: Farbe+Höhe (A), Normale im Raum der Kachel (N, xy), Rauheit+Verdeckung (R).
// gx, gy: Ableitungen von uv nach dem Bildpunkt (vorher in einheitlichem Fluss gerechnet).
void surfHex(int L, vec2 uv, vec2 gx, vec2 gy, bool rot, out vec4 A, out vec2 N, out vec2 R) {
    vec2 st = uv * SURF_CELLS;
    vec2 g = vec2(st.x, -0.57735027 * st.x + 1.15470054 * st.y);
    ivec2 b = ivec2(floor(g));
    vec2 f = g - vec2(b);
    float fz = 1.0 - f.x - f.y;
    float s = fz < 0.0 ? 1.0 : 0.0, s2 = 2.0 * s - 1.0;
    vec3 w = vec3(-fz * s2, s - f.y * s2, s - f.x * s2);
    int si = int(s);
    ivec2 v[3] = ivec2[3](b + ivec2(si, si), b + ivec2(si, 1 - si), b + ivec2(1 - si, si));
    w = pow(max(w, vec3(0.0)), vec3(SURF_GAIN));
    w /= max(w.x + w.y + w.z, 1e-12);
    A = vec4(0.0); N = vec2(0.0); R = vec2(0.0);
    for (int k = 0; k < 3; k++) {
        vec2 h = surfHash(v[k]);
        mat2 m = mat2(1.0);
        if (rot) {
            float a = h.x * 6.28318530718;
            float c = cos(a), sn = sin(a);
            m = mat2(c, sn, -sn, c);   // (c·u − s·v, s·u + c·v)
        }
        vec3 p = vec3(m * uv + h * 17.0, float(L));
        vec2 dx = m * gx, dy = m * gy;
        A += w[k] * textureGrad(uSurfA, p, dx, dy);
        vec2 n = textureGrad(uSurfN, p, dx, dy).rg * 2.0 - 1.0;
        N += w[k] * (transpose(m) * n);   // zurückdrehen
        R += w[k] * textureGrad(uSurfR, p, dx, dy).rg;
    }
}

// Ein Material am Weltort p mit der Normale gN der Fläche; px: Bildpunktgröße in m. In einheitlichem Fluss aufrufen
// (die Ableitungen von p werden hier genommen).
Surf surfSampleD(int L, vec3 p, vec3 dpw, vec3 dpv, vec3 gN, float px) {
    vec4 P = uSurfParam[L];
    vec3 dpx = dpw * P.x, dpy = dpv * P.x;
    Surf o;
    float far = px * P.x;   // Bildpunkt in Kacheln
    float near = 1.0 - smoothstep(uSurfFar * 0.6, uSurfFar, far);
    o.albedo = uSurfMean[L].rgb;
    o.rough = uSurfMean[L].a;
    o.ao = 1.0;
    o.height = 0.5;
    o.normal = gN;
    if (near <= 0.0) return o;
    bool rot = P.z > 0.5;
    vec4 A; vec2 N; vec2 R;
    if (P.w > 0.5 && abs(gN.y) < 0.8) {
        // dreiseitig: Ebenen x (zy), y (xz), z (xy) nach |n|^4
        vec3 bw = pow(abs(gN), vec3(4.0));
        bw /= bw.x + bw.y + bw.z;
        vec4 Ax, Ay, Az; vec2 Nx, Ny, Nz, Rx, Ry, Rz;
        surfHex(L, p.zy * P.x, dpx.zy, dpy.zy, rot, Ax, Nx, Rx);
        surfHex(L, p.xz * P.x, dpx.xz, dpy.xz, rot, Ay, Ny, Ry);
        surfHex(L, p.xy * P.x, dpx.xy, dpy.xy, rot, Az, Nz, Rz);
        A = Ax * bw.x + Ay * bw.y + Az * bw.z;
        R = Rx * bw.x + Ry * bw.y + Rz * bw.z;
        // Normalen nach Whiteout je Ebene (Golus), in Weltrichtungen zurückgelegt
        vec3 nx = vec3(gN.x, Nx.y + gN.y, Nx.x + gN.z);
        vec3 ny = vec3(Ny.x + gN.x, gN.y, -Ny.y + gN.z);
        vec3 nz = vec3(Nz.x + gN.x, Nz.y + gN.y, gN.z);
        o.normal = normalize(nx * bw.x + ny * bw.y + nz * bw.z);
    } else {
        // eine Ebene, die der Fläche am nächsten liegt (ohne Verzerrung an Wänden auch ohne dreiseitig)
        vec3 an = abs(gN);
        if (an.y >= max(an.x, an.z)) {
            surfHex(L, p.xz * P.x, dpx.xz, dpy.xz, rot, A, N, R);
            o.normal = normalize(vec3(N.x + gN.x, gN.y, -N.y + gN.z));
        } else if (an.x > an.z) {
            surfHex(L, p.zy * P.x, dpx.zy, dpy.zy, rot, A, N, R);
            o.normal = normalize(vec3(gN.x, N.y + gN.y, N.x + gN.z));
        } else {
            surfHex(L, p.xy * P.x, dpx.xy, dpy.xy, rot, A, N, R);
            o.normal = normalize(vec3(N.x + gN.x, N.y + gN.y, gN.z));
        }
    }
    o.albedo = mix(o.albedo, A.rgb, near);
    o.height = mix(0.5, A.a, near);
    o.rough = mix(o.rough, R.r, near);
    o.ao = mix(1.0, R.g, near);
    o.normal = normalize(mix(gN, o.normal, near));
    return o;
}

Surf surfSample(int L, vec3 p, vec3 gN, float px) { return surfSampleD(L, p, dFdx(p), dFdy(p), gN, px); }

// Zwei oder drei Materialien über die Höhe gemischt (das höhere setzt sich am Übergang durch); w: Anteile, Summe 1.
Surf surfBlend(ivec3 L, vec3 w, vec3 p, vec3 gN, float px) {
    vec3 dpw = dFdx(p), dpv = dFdy(p);   // vor jeder Verzweigung
    Surf a = surfSampleD(L.x, p, dpw, dpv, gN, px);
    if (w.y <= 0.001 && w.z <= 0.001) return a;
    Surf b = surfSampleD(L.y, p, dpw, dpv, gN, px);
    Surf c = a;
    if (w.z > 0.001) c = surfSampleD(L.z, p, dpw, dpv, gN, px);
    const float depth = 0.2;
    vec3 h = vec3(a.height, b.height, c.height) + w;
    float hm = max(h.x, max(h.y, h.z)) - depth;
    vec3 k = max(h - vec3(hm), vec3(0.0)) * step(vec3(0.001), w);
    k /= max(k.x + k.y + k.z, 1e-6);
    Surf o;
    o.albedo = a.albedo * k.x + b.albedo * k.y + c.albedo * k.z;
    o.normal = normalize(a.normal * k.x + b.normal * k.y + c.normal * k.z);
    o.rough = a.rough * k.x + b.rough * k.y + c.rough * k.z;
    o.ao = a.ao * k.x + b.ao * k.y + c.ao * k.z;
    o.height = a.height * k.x + b.height * k.y + c.height * k.z;
    return o;
}

// Auflagen: Nässe (0 … 1; dunkler, glatter, Mulden zuerst), Schnee (auf Flächen, die nach oben schauen), Staub.
void surfWet(inout Surf s, int L, float wet, vec3 gN) {
    float k = clamp(wet * (1.3 - s.height * 0.6), 0.0, 1.0);
    s.albedo *= 1.0 - uSurfWet[L] * k;
    s.rough = mix(s.rough, 0.07, k);
    s.normal = normalize(mix(s.normal, gN, k * 0.7));
}

void surfSnow(inout Surf s, float snow, vec3 gN) {
    float k = clamp(snow * smoothstep(0.35, 0.8, gN.y) * (0.6 + 0.8 * s.height), 0.0, 1.0);
    s.albedo = mix(s.albedo, vec3(0.86, 0.89, 0.93), k);
    s.rough = mix(s.rough, 0.65, k);
    s.normal = normalize(mix(s.normal, gN, k * 0.8));
    s.ao = mix(s.ao, 1.0, k);
}

void surfDust(inout Surf s, float dust, vec3 col) {
    s.albedo = mix(s.albedo, col, clamp(dust, 0.0, 1.0));
    s.rough = mix(s.rough, 0.95, clamp(dust, 0.0, 1.0));
}
