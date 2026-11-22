// sediment.glsl — gemeinsamer Teil der Kernel des Sediments (Werkbuch HalligHooge Teil A, 12.10, P9a): die Werte des
// Schritts und die Formeln, wie in com.dan.physics.sediment.Sediment, in float.
layout(std430, binding = 0) readonly buffer Werte {
    int nx, ny, schalter, pad0;                 // schalter: 1 Geschiebe, 2 Schwebstoff, 4 Schlick, 8 Rippel, 16 Diffusion, 32 zweite Ordnung, 64 Rand Sand gesetzt, 128 Rand Schlick gesetzt
    float dx, dy, rho, rhoS;
    float nu, poros, hMin, epsHang;
    float deltaAkt, mErosion, tauE0, tauE1;     // deltaAkt: Dicke der Mischschicht (m)
    float tKons, wsSchlick, tauD, rhoSchlick;
    float hangSand, hangSchlick, rutschAnteil, rippelSchlick;
    float courant, randSand, randSchlick, pad1;
    float faktor, deckel, deckelMin, intervall;   // seit P9c: morphologischer Faktor; Deckel (Anteil der Tiefe, Mindestwert m) je Schritt der Länge intervall (s; 0: je Teilschritt)
    float tauA, mA1, mA0, anteilTief;             // lockere Auflage: Schwelle, Rate erster Ordnung, Höchstrate, Anteil der Ablagerung in die Mischschicht
    float eSpirale, pad2, pad3, pad4;
} W;

const float G = 9.81, KAPPA = 0.40;
const int GESCHIEBE = 1, SCHWEB = 2, SCHLICK = 4, RIPPEL = 8, DIFFUSION = 16, ZWEITE = 32, RAND_SAND = 64, RAND_SCHLICK = 128, STROM = 256, AUFLAGE = 512;

bool an(int bit) { return (W.schalter & bit) != 0; }
int zelle(int i, int j) { return i + W.nx * j; }
int kx(int i, int j) { return i + (W.nx + 1) * j; }
int ky(int i, int j) { return i + W.nx * j; }

float dStern(float d, float s) { return d * pow(G * (s - 1.0) / (W.nu * W.nu), 1.0 / 3.0); }

// Soulsby (1997); umgestellt, damit die Differenz bei kleinem Korn nicht auslöscht
float sink(float d, float ds) {
    float x = 1.049 * ds * ds * ds;
    return W.nu / d * x / (sqrt(10.36 * 10.36 + x) + 10.36);
}

float thetaKrit(float ds) { return 0.30 / (1.0 + 1.2 * ds) + 0.055 * (1.0 - exp(-0.020 * ds)); }

float cdStrom(float h, float z0) {
    float l = log(max(h / z0, 20.0)) - 1.0;
    return KAPPA * KAPPA / (l * l);
}

float fwRau(float a, float z0) { return min(0.3, 1.39 * pow(a / z0, -0.52)); }

float tauMittel(float tc, float tw) {
    float sum = tc + tw;
    return sum <= 0.0 ? 0.0 : tc * (1.0 + 1.2 * pow(tw / sum, 3.2));
}

float tauGroesste(float tm, float tw, float cosPhi, float sinPhi) {
    float a = tm + tw * cosPhi, b = tw * sinPhi;
    return sqrt(a * a + b * b);
}

float geschiebe(float d, float ds, float tm, float tx, float tcr) {
    return tx <= tcr ? 0.0 : 0.5 * W.rhoS * d * pow(ds, -0.3) * sqrt(tm / W.rho) * (tx - tcr) / tcr;
}

float bezug(float d, float ds, float a, float t) {
    return t <= 0.0 ? 0.0 : min(0.05 * W.rhoS, 0.015 * W.rhoS * d / a * pow(t, 1.5) * pow(ds, -0.3));
}

float formfaktor(float ah, float z) {
    float f;
    if (abs(z - 1.2) < 1e-3) f = -pow(ah, 1.2) * log(ah) / pow(1.0 - ah, 1.2);
    else f = (pow(ah, z) - pow(ah, 1.2)) / (pow(1.0 - ah, z) * (1.2 - z));
    return min(1.0, max(ah, f));
}

// Galappatti (1983), wie bei Lesser (2009, Gl. 2.38)
float anpassung(float ur, float w) {
    ur = min(0.15, max(0.02, ur));
    w = min(1.0, max(0.005, w));
    float t = w * exp((1.547 - 20.12 * ur) * w * w * w + (326.832 * pow(ur, 2.2047) - 0.2) * w * w + (0.1385 * log(ur) - 6.4061) * w + (0.5467 * ur + 2.1963));
    return min(1.0, max(0.01, t));
}

// Soulsby und Whitehouse (2005): (Höhe, Länge) im Gleichgewicht
vec2 rippelGl(float d, float ds, float theta) {
    if (ds < 1.2 || ds > 16.0) return vec2(0.0);
    float hMax = d * 202.0 * pow(ds, -0.554), lMax = d * (500.0 + 1881.0 * pow(ds, -1.5));
    float two = ds <= 1.58 ? 0.916 : 1.66 * pow(ds, -1.3), tsf = ds <= 1.58 ? 2.26 : 4.09 * pow(ds, -1.3);
    if (theta <= two) return vec2(hMax, lMax);
    if (theta < tsf) return vec2(hMax * (tsf - theta) / (tsf - two), lMax);
    return vec2(0.0, lMax);
}

// Summe mit Ausgleich (Kahan): s Summe, c Ausgleich, x Zuwachs
void dazu(inout float s, inout float c, float x) {
    precise float y = x - c;
    precise float t = s + y;
    precise float neu = (t - s) - y;
    c = neu;
    s = t;
}
