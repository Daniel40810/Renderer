// nah_gemein.glsl — gemeinsamer Teil der Kernel des Nahfelds (Seegang Werkbuch, S2b; com.dan.physics.waves.nah.Nahfeld
// in float). Bindungen 0–3 sind in allen Kerneln gleich; 4–13 legt jeder Kernel mit seinem Zugriff selbst an:
//   4 Bin     vec4 je Stützstelle: k (−1 blockiert oder trocken), σ, c_g, c_θ der Refraktion
//   5 Tempo   vec4 je Stützstelle: C_x, C_y, C_θ, Δt im Anlauf
//   6 Beug    vec4 je Zelle und Band: √E, mittleres k, mittleres c c_g, s = √(1 + δ)
//   7 Glatt   float 2 × Zelle und Band (Glätten von √E, hin und her)
//   8 Alt     float je Stützstelle: Wirkung vor dem Teilschritt
//   9 Neu     float je Stützstelle: Wirkung danach (Quellen und Bindung an Ort und Stelle)
//  10 Rand    float je Geisterzelle, Band und Richtung: Varianz (m²), Folge wie Nahfeld.randZellen
//  11 Aus     vec4 je Zelle: dwc, dbr, dbf, dveg (W/m²)
//  12 Rate    uint: größte Courant-Rate (Bits eines positiven float)
//  13 Neue    float: Zellen, die beim Verschieben neu ins Gitter kamen (Index, dann nb · nd Varianzen)
//  14 Ziel    float je Stützstelle: Spektrum des Ziels der Bindung (Varianz, m²), wenn W.spektral = 1
//  15 NachQ   float je Stützstelle: Wirkung nach den Quellen der Runde davor (nur im Anlauf, B22)

layout(std430, binding = 0) readonly buffer Werte {
    int nx, ny, nb, nd, periodischY, mitBeugung, mitRefraktion, mitReflexion;
    int mitWind, mitSchaum, mitBrechen, mitReibung, mitPflanzen, kappung, abstandMax, bindungBlock;
    float dx, dTheta, drehGrenze, courant, minTiefe, stencil, deltaMin, deltaMax;
    float cds, steilheitPM, cReibung, alphaBJ, gammaBJ, bindungZeit, spektral, pad1;   // spektral: 1 = Bindung an das Spektrum (B20)
} W;
// je Zelle fünf: (nass, Tiefe, Grund, Wand), (U, V, Wind x, Wind y), (u*, C_D b_v N_v, Halm, frei von Bindung),
// (∂h/∂x, ∂h/∂y, ∂U/∂x, ∂U/∂y), (∂V/∂x, ∂V/∂y, m₀ des Ziels der Bindung, –)
layout(std430, binding = 1) readonly buffer Zelle { vec4 zelle[]; };
layout(std430, binding = 2) readonly buffer Band { vec4 band[]; };      // ω, f, Δf, –
layout(std430, binding = 3) readonly buffer Winkel { vec2 winkel[]; };  // cos, sin je Richtung

// h: Schritt (s); schalter: Bit 0 Anlauf (Δt je Stützstelle aus Tempo.w), Bit 1 Raten aus Alt (sonst aus Neu);
// pass: Durchgang des Glättens; dtMax: größter Schritt im Anlauf. Beim Verschieben: schalter = di, pass = dj.
layout(push_constant) uniform Schritt { float h; int schalter; int pass; float dtMax; } P;

const float G = 9.81, RHO = 1025.0, RHO_A = 1.28, ZWEI_PI = 6.28318530718, PI = 3.14159265359;
const int ND_MAX = 72;

/**
 * Nummer des Fadens bei zweidimensionaler Abgabe (Befund B39: ein Faden je Stützstelle; x höchstens 65 535 Gruppen).
 * Nur in Kerneln, die layout(local_size_x = …) vor dem #include setzen.
 */
int fadenNummer() { return int(gl_GlobalInvocationID.x + gl_NumWorkGroups.x * gl_WorkGroupSize.x * gl_GlobalInvocationID.y); }
int gruppenNummer() { return int(gl_WorkGroupID.x + gl_NumWorkGroups.x * gl_WorkGroupID.y); }

bool nass(int c) { return zelle[5 * c].x > 0.5; }
float tiefe(int c) { return zelle[5 * c].y; }
int ix(int c, int b, int q) { return (c * W.nb + b) * W.nd + q; }
int nachbarS(int c) { int j = c / W.nx; return j > 0 ? c - W.nx : (W.periodischY != 0 ? c + (W.ny - 1) * W.nx : -1); }
int nachbarN(int c) { int j = c / W.nx; return j < W.ny - 1 ? c + W.nx : (W.periodischY != 0 ? c - (W.ny - 1) * W.nx : -1); }

/** Zelle (i, j) mit der Zeile j, periodisch oder −1 außerhalb. */
int zeileVon(int j, int i) {
    if (j < 0 || j >= W.ny) {
        if (W.periodischY == 0) return -1;
        j = ((j % W.ny) + W.ny) % W.ny;
    }
    return j * W.nx + i;
}

/**
 * tanh mit Klemme (Befund B37): Der Treiber der 680M rechnet tanh über exp(2x) und gibt ab x ≈ 44 NaN; in float ist
 * tanh(x) ab x ≈ 9 ohnehin 1. Jeder Kernel nimmt th statt tanh.
 */
float th(float x) { return tanh(clamp(x, -15.0, 15.0)); }

float sigmaK(float k, float h) { return sqrt(G * k * th(k * h)); }

float gruppe(float s, float k, float h) {
    float kd = k * h;
    float n = kd > 30.0 ? 0.5 : 0.5 * (1.0 + 2.0 * kd / sinh(2.0 * kd));
    return n * s / k;
}

/** Fluss in θ wie Waves: aufwind, Lax–Wendroff, begrenzt nach van Leer. */
float fluss(float v, float ell, float el, float er, float err, float hd) {
    float auf = v > 0.0 ? el : er;
    float ab = v > 0.0 ? er : el, dahinter = v > 0.0 ? ell : err, d = ab - auf;
    if (d == 0.0) return v * auf;
    float x = auf - dahinter;
    if (x == 0.0 || (x > 0.0) != (d > 0.0)) return v * auf;
    float phi = 2.0 * x / (x + d);
    return v * (auf + 0.5 * phi * max(0.0, 1.0 - abs(v) * hd) * d);
}
