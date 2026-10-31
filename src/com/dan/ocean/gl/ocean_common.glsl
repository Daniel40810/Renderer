// ocean_common.glsl — gemeinsame Werte der Oberflächen-Shader: Kaskaden, Dünung, Ringwellen, Rauschen, Himmel.
// Formeln wie in com.dan.ocean (Swell, Ripples, Ocean.streaks/surfRing), damit Bild und Abfragen übereinstimmen.
uniform mat4 uVP;
uniform vec3 uCam;
uniform vec2 uCenter;          // Mittelpunkt des Ringgitters (auf 0,25 m gerastert)
uniform float uTime;           // Zeit der See modulo Wiederholzeit
uniform float uLevel;          // Wasserstand
uniform int uCascades;
uniform float uLength[4];
uniform sampler2DArray uDisp;  // Dx, Dy, Dz, Schaum
uniform sampler2DArray uDeriv; // ∂h/∂x, ∂h/∂z, ∂Dx/∂x, ∂Dz/∂z
uniform vec4 uSwellA[4];       // Richtung x, z, k, ω
uniform vec4 uSwellB[4];       // A, Q, φ₀, –
uniform int uReversed;
uniform sampler2D uBed;        // seit Haithabu: Höhe des Grunds, optional
uniform vec4 uBedRect;         // x, z der Nordwestecke, Kantenlänge (m), an (1) / aus (0)
uniform vec3 uBedMap;          // Höhe = Wert · x + y; z: Trübung je m
uniform vec2 uBedOutside;      // seit Rungholt: Grund außerhalb des Rechtecks (x, m), an (y = 1); aus: dort kein Wasser

// Tiefe des Wassers über dem Grund am Ort p (groß, wenn kein Grund gesetzt; −1, wenn außerhalb des Rechtecks)
float bedDepth(vec2 p) {
    if (uBedRect.w <= 0.0) return 1.0e4;
    vec2 uv = (p - uBedRect.xy) / uBedRect.z;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return uBedOutside.y > 0.5 ? uLevel - uBedOutside.x : -1.0;
    return uLevel - (textureLod(uBed, uv, 0.0).r * uBedMap.x + uBedMap.y);
}
const float EARTH_R = 6371000.0;

// seit Glacier Phase 8: eine lange Welle von außen (Flachwasser, zwei Gitter), optional. Je Texel der Wasserstand über
// dem Ruhespiegel; trockene Zellen tragen LONG_DRY und zählen beim Abtasten nicht mit.
uniform sampler2D uLongH0, uLongH1;
uniform vec4 uLongR0, uLongR1;   // x0, z0 der Ecke, Zelle (m), an (1) / aus (0)
const float LONG_DRY = -29.0;

// x: Höhe (m), y: Gewicht nasser Nachbarn (0 = trocken), z: 1 im Gitter, sonst 0
vec3 longLevel(sampler2D t, vec4 r, vec2 p) {
    ivec2 n = textureSize(t, 0);
    vec2 u = (p - r.xy) / r.z - 0.5;
    if (u.x < -0.5 || u.y < -0.5 || u.x > float(n.x) - 0.5 || u.y > float(n.y) - 0.5) return vec3(0.0);
    vec2 i = floor(u), f = u - i;
    float s = 0.0, w = 0.0;
    for (int b = 0; b <= 1; b++)
        for (int a = 0; a <= 1; a++) {
            ivec2 c = clamp(ivec2(i) + ivec2(a, b), ivec2(0), n - 1);
            float e = texelFetch(t, c, 0).r;
            if (e <= LONG_DRY) continue;
            float ww = (a == 0 ? 1.0 - f.x : f.x) * (b == 0 ? 1.0 - f.y : f.y);
            s += ww * e;
            w += ww;
        }
    return vec3(w > 1.0e-4 ? s / w : 0.0, w, 1.0);
}

vec3 longWave(vec2 p) {
    if (uLongR0.w > 0.0) {
        vec3 a = longLevel(uLongH0, uLongR0, p);
        if (a.z > 0.0) return a;
    }
    if (uLongR1.w > 0.0) return longLevel(uLongH1, uLongR1, p);
    return vec3(0.0);
}

// seit Rungholt Phase 6: Strömung als Textur (RG: m/s nach Osten und Süden) über {x0, z0, Zelle, an}; die Fläche
// kräuselt sich darin, Schaum zieht in Streifen mit, das Wasser wird trüb vom Schwebstoff (uFlowSilt: Farbe, Stärke)
uniform sampler2D uFlow;
uniform vec4 uFlowRect;
uniform vec4 uFlowSilt;
// seit Rungholt Phase 6: gegen das Kachelmuster (Befund Status_Phase4: Rauten auf dem Wasser im Watt) schwanken die
// beiden größten Kaskaden und die Dünung mit flachem Rauschen in der Stärke (0 = aus, 1 = voll)
uniform float uTileBreak;
// seit Rungholt Phase 6: weicher Rand der langen Welle (0 = wie bisher: halbe Zelle über die letzte nasse hinaus, dann 1 m
// unter den Grund)
uniform float uLongSoft;

vec2 flowAt(vec2 p) {
    if (uFlowRect.w <= 0.0) return vec2(0.0);
    vec2 uv = (p - uFlowRect.xy) / (uFlowRect.z * vec2(textureSize(uFlow, 0)));
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec2(0.0);
    return textureLod(uFlow, uv, 0.0).rg;
}

// Dünung am Ruhepunkt p: Auslenkung (xyz) und Steigungen dy/dx0, dy/dz0, Jacobi-Anteil
void swell(vec2 p, out vec3 d, out vec3 slope) {
    d = vec3(0.0);
    slope = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float a = uSwellB[i].x;
        if (a == 0.0) continue;
        vec2 dir = uSwellA[i].xy;
        float k = uSwellA[i].z, w = uSwellA[i].w, q = uSwellB[i].y;
        float ph = k * dot(dir, p) - w * uTime + uSwellB[i].z;
        float s = sin(ph), c = cos(ph);
        d += vec3(-q * a * dir.x * s, a * c, -q * a * dir.y * s);
        slope += vec3(-a * k * dir.x * s, -a * k * dir.y * s, -q * a * k * c);
    }
}

float hash21(vec2 p) { return fract(sin(p.x * 127.1 + p.y * 311.7) * 43758.5453); }

float vnoise(vec2 p) {
    vec2 i = floor(p), f = p - i;
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i), b = hash21(i + vec2(1.0, 0.0)), c = hash21(i + vec2(0.0, 1.0)), d = hash21(i + vec2(1.0, 1.0));
    return a + (b - a) * f.x + (c - a) * f.y + (a - b - c + d) * f.x * f.y;
}

// Stärke einer Kaskade am Ort (gegen das Kachelmuster): 1 ohne uTileBreak; sonst für die beiden größten 0,45 … 1,55
// nach Rauschen über gut dreieinhalb Kachellängen, im Mittel 1
float cascadeGain(vec2 p, int c) {
    if (uTileBreak <= 0.0 || c > 1) return 1.0;
    float n = vnoise(p / (uLength[c] * 3.7) + float(c) * 13.1) * 0.65 + vnoise(p / (uLength[c] * 1.3) + float(c) * 5.3) * 0.35;
    return mix(1.0, 0.45 + 1.1 * n, uTileBreak);
}
float swellGain(vec2 p) {
    if (uTileBreak <= 0.0) return 1.0;
    return mix(1.0, 0.5 + vnoise(p / 430.0 + 3.7), uTileBreak);
}

// seit Sylt Phase 7a: die Brandung von außen (etwa com.dan.surf), optional. RGBA32F über uSurfRect (x0, z0 der Ecke,
// Kantenlänge in m, an): R Anteil der Wellen, die hier brechen oder als Walze laufen, G Phase in Wellenlängen ab dem Rand zur See, B Schaumteppich,
// A Set-up (m). Die Walzen laufen mit der Phase zur Küste, eine je Periode uSurfPeriod (s).
uniform sampler2D uSurf;
uniform vec4 uSurfRect;
uniform float uSurfPeriod;
vec4 surfAt(vec2 p) {
    if (uSurfRect.w <= 0.0) return vec4(0.0);
    vec2 u = (p - uSurfRect.xy) / uSurfRect.z;
    if (any(lessThan(u, vec2(0.0))) || any(greaterThan(u, vec2(1.0)))) return vec4(0.0);
    vec4 v = textureLod(uSurf, u, 0.0);
    // zum Rand der Textur hin auslaufen lassen (50 m)
    vec2 e = min(u, 1.0 - u) * uSurfRect.z;
    float w = smoothstep(0.0, 50.0, min(e.x, e.y));
    return vec4(v.r * w, v.g, v.b * w, v.a * w);
}
// Lage innerhalb einer Walze: 0 an der Stirn, steigt nach hinten bis 1 (eine Wellenlänge)
float surfSaw(float phase) { return fract(uTime / max(uSurfPeriod, 1.0) - phase); }
// Bricht die Welle hier (oder läuft sie als Walze)? R ist der Anteil dieser Wellen: jede Welle (ganze Zahl der Phase) bekommt ihr eigenes
// Rauschen längs des Kamms, und sie ist weiß, wo es unter R liegt; so brechen bei kleinem R einzelne Wellen auf
// Stücken des Kamms, bei großem alle auf ganzer Länge. 0..1 mit weichem Rand.
float surfBreaksWave(vec2 p, vec4 sf, float wave) {
    if (sf.r <= 0.001) return 0.0;
    float n = vnoise(p / 38.0 + vec2(wave * 7.31, wave * 3.17)) * 0.7 + vnoise(p / 11.0 + vec2(wave * 1.9, -wave * 5.3)) * 0.3;
    float q = clamp(sf.r * 1.15, 0.0, 1.0);
    return smoothstep(n - 0.08, n + 0.08, q);
}
float surfBreaks(vec2 p, vec4 sf) { return surfBreaksWave(p, sf, floor(uTime / max(uSurfPeriod, 1.0) - sf.g)); }
// Seit Sylt Phase 9a: Relief der Walzen (m), stetig. Hinter der Stirn fällt es ab (exp(−7 s)), vor der Stirn steigt die
// Front der nächsten Welle über das letzte Fünftel der Wellenlänge an, mit deren eigenem Rauschen. Vorher sprang das Relief
// an der Stirn und zwischen den Wellen um bis 1 m, das gab auf dem Gitter schräge Tafeln. Seit Sylt Phase 10 flacher
// (0,3 m je m Tiefe, höchstens 0,75 m) und quer zum Kamm weicher (Rand ±0,25 statt ±0,08): die Tafeln im Statusbild
// Phase 9a kamen von glasigen Rampen mit scharfen Kanten längs des Kamms.
float surfReliefWave(vec2 p, vec4 sf, float wave) {
    float n = vnoise(p / 38.0 + vec2(wave * 7.31, wave * 3.17)) * 0.7 + vnoise(p / 11.0 + vec2(wave * 1.9, -wave * 5.3)) * 0.3;
    float q = clamp(sf.r * 1.15, 0.0, 1.0);
    return smoothstep(n - 0.25, n + 0.25, q);
}
float surfRelief(vec2 p, vec4 sf, float depth) {
    if (sf.r <= 0.001) return 0.0;
    float x = uTime / max(uSurfPeriod, 1.0) - sf.g, s = fract(x), w = floor(x);
    float cur = surfReliefWave(p, sf, w) * (exp(-s * 7.0) - 0.15);
    float nxt = surfReliefWave(p, sf, w + 1.0) * 0.85;
    return mix(cur, nxt, smoothstep(0.8, 1.0, s)) * 0.3 * clamp(depth, 0.0, 2.5);
}

// seit Timmendorf Phase 8: Kielwasser von außen (etwa com.dan.wake, das Paket weiß nicht woher), optional. Textur-Array mit
// zwei Schichten RG16F (R Höhe in m, G Schaum 0..1): Schicht 0 nah (uKielR0), Schicht 1 weit (uKielR1), je Ecke x, z,
// Kantenlänge (m), an (1). Zum Rand der nahen Schicht wird auf die weite übergeblendet, am Rand der weiten läuft es aus.
uniform sampler2DArray uKiel;
uniform vec4 uKielR0, uKielR1;
float kielEdge(vec2 u, float size, float band) {
    vec2 e = min(u, 1.0 - u) * size;
    return smoothstep(0.0, band, min(e.x, e.y));
}
vec2 kielAt(vec2 p) {
    vec2 r = vec2(0.0);
    float wNear = 0.0;
    if (uKielR0.w > 0.0) {
        vec2 u = (p - uKielR0.xy) / uKielR0.z;
        if (all(greaterThanEqual(u, vec2(0.0))) && all(lessThanEqual(u, vec2(1.0)))) {
            wNear = kielEdge(u, uKielR0.z, 0.12 * uKielR0.z);
            r = textureLod(uKiel, vec3(u, 0.0), 0.0).rg * wNear;
        }
    }
    if (uKielR1.w > 0.0 && wNear < 1.0) {
        vec2 u = (p - uKielR1.xy) / uKielR1.z;
        if (all(greaterThanEqual(u, vec2(0.0))) && all(lessThanEqual(u, vec2(1.0))))
            r += textureLod(uKiel, vec3(u, 1.0), 0.0).rg * kielEdge(u, uKielR1.z, 0.08 * uKielR1.z) * (1.0 - wNear);
    }
    return r;
}
// Neigung (dh/dx, dh/dz) des Kielwassers, mittlere Differenzen über eine Zelle der jeweiligen Schicht
vec2 kielSlope(vec2 p) {
    if (uKielR0.w <= 0.0 && uKielR1.w <= 0.0) return vec2(0.0);
    float n = float(textureSize(uKiel, 0).x);
    vec2 u0 = (p - uKielR0.xy) / uKielR0.z;
    bool near = uKielR0.w > 0.0 && all(greaterThanEqual(u0, vec2(0.0))) && all(lessThanEqual(u0, vec2(1.0)));
    float e = (near ? uKielR0.z : uKielR1.z) / n;
    return vec2(kielAt(p + vec2(e, 0.0)).x - kielAt(p - vec2(e, 0.0)).x, kielAt(p + vec2(0.0, e)).x - kielAt(p - vec2(0.0, e)).x) / (2.0 * e);
}
