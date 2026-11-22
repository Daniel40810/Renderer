// messblick.glsl – render.analysis (P11c2, Werkbuch HalligHooge Teil A, 12.12): gemeinsame Teile des Messblicks.
// Das Feld liegt in einem Puffer als acht Ebenen zu nx · ny Werten: u, v, η, h des Stands A, dann dieselben des Stands B
// (u, v in m/s nach Ost und Nord in der Zelle, η in m NHN, h in m; trocken h = 0). Lage in Zellen: Mitte der Zelle (i, j)
// bei (i + 0,5; j + 0,5), j nach Norden.

const int ART_U = 0, ART_V = 1, ART_ETA = 2, ART_H = 3;

#ifdef MIT_FELD
// bilinear zwischen den Mitten der Zellen, am Rand festgehalten; Ebene e des Stands s (0: A, 1: B)
float ebene(int s, int e, vec2 p, int nx, int ny) {
    vec2 f = p - 0.5;
    vec2 g = clamp(f, vec2(0.0), vec2(float(nx - 1), float(ny - 1)));
    ivec2 i0 = ivec2(floor(g));
    ivec2 i1 = min(i0 + 1, ivec2(nx - 1, ny - 1));
    vec2 t = g - vec2(i0);
    int b = (4 * s + e) * nx * ny;
    float a00 = FELD[b + i0.x + nx * i0.y], a10 = FELD[b + i1.x + nx * i0.y];
    float a01 = FELD[b + i0.x + nx * i1.y], a11 = FELD[b + i1.x + nx * i1.y];
    return mix(mix(a00, a10, t.x), mix(a01, a11, t.x), t.y);
}

// zwischen den Ständen A und B mit dem Gewicht w
float feld(int e, vec2 p, int nx, int ny, float w) {
    return mix(ebene(0, e, p, nx, ny), ebene(1, e, p, nx, ny), w);
}
#endif

#ifdef MIT_FELDBILD
// das Feld zum Gewicht w je Zelle (feldbild.comp, RGBA32F: u, v, η, h), bilinear zwischen den Mitten, am Rand
// festgehalten; vier Zugriffe statt sechzehn einzelner Werte
vec4 fb(vec2 p, int nx, int ny) {
    vec2 g = clamp(p - 0.5, vec2(0.0), vec2(float(nx - 1), float(ny - 1)));
    ivec2 i0 = ivec2(floor(g));
    ivec2 i1 = min(i0 + 1, ivec2(nx - 1, ny - 1));
    vec2 t = g - vec2(i0);
    vec4 a00 = texelFetch(FELDBILD, i0, 0), a10 = texelFetch(FELDBILD, ivec2(i1.x, i0.y), 0);
    vec4 a01 = texelFetch(FELDBILD, ivec2(i0.x, i1.y), 0), a11 = texelFetch(FELDBILD, i1, 0);
    return mix(mix(a00, a10, t.x), mix(a01, a11, t.x), t.y);
}
#endif

// Farbleiter von dunkelblau über türkis und grün nach gelb (s in 0 … 1) [G]
vec3 leiter(float s) {
    s = clamp(s, 0.0, 1.0);
    const vec3 c0 = vec3(0.15, 0.20, 0.75), c1 = vec3(0.10, 0.65, 0.95), c2 = vec3(0.20, 0.85, 0.55), c3 = vec3(0.85, 0.90, 0.20), c4 = vec3(1.00, 0.60, 0.15);
    if (s < 0.25) return mix(c0, c1, s / 0.25);
    if (s < 0.5) return mix(c1, c2, (s - 0.25) / 0.25);
    if (s < 0.75) return mix(c2, c3, (s - 0.5) / 0.25);
    return mix(c3, c4, (s - 0.75) / 0.25);
}

// Zufall aus zwei ganzen Zahlen (PCG nach Jarzynski und Olano 2020), in 0 … 1
uint pcg(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float zufall(uint a, uint b) { return float(pcg(a ^ pcg(b))) / 4294967296.0; }
