// see_kiel_spur.glsl — Seegang Werkbuch, S7d6 (Datei 94, SF133 A): die Quellen des Kielwassers, das Muster eines
// Druckpunkts und die Suche über die Spur, aus see_kiel.comp herausgelöst. see_kiel.comp rechnet damit die Kacheln fürs
// Bild, see_koerper das Kielwasser direkt an den Punkten der Körper: genau, vom Auge unabhängig, ohne die eigene Quelle
// (wie core.szene.Kielwasser.am in double).
// Vor dem Einbinden KIEL_QUELLEN setzen (Bindung des Puffers der Quellen, Aufbau wie Kielwasser.werte: je Quelle KS_JE
// Werte; Kopf: Bug x, y; Mitte x, y; Länge, halbe Breite, Amplitude, α_max; θ₀, θ₁, K_c, K_ref; Schaum f₀, τ, Breite,
// Reichweite; Ast quer, Punkte der Spur, Fahrt; dann die Tafeln und die Spur (x, y, Weg, Alter)).
const int KS_NT = 256, KS_NA = 256, KS_SPUR = 96, KS_KOPF = 24, KS_JE = KS_KOPF + 4 * KS_NT + 2 * KS_NA + 4 * KS_SPUR;

layout(std430, binding = KIEL_QUELLEN) readonly buffer KielQuellen { int zahl; int r0; int r1; int r2; float q[]; } E;

const float KS_PI = 3.14159265358979, KS_VIERTEL_PI = 0.785398163397448;

float kielTafel(int b, int komp, float th, float th0, float th1) {
    float u = (th - th0) / (th1 - th0) * float(KS_NT - 1);
    if (!(u > 0.0)) return E.q[b + KS_KOPF + komp];
    if (u >= float(KS_NT - 1)) return E.q[b + KS_KOPF + 4 * (KS_NT - 1) + komp];
    int i = int(u);
    float f = u - float(i);
    float a = E.q[b + KS_KOPF + 4 * i + komp], c = E.q[b + KS_KOPF + 4 * (i + 1) + komp];
    return a + f * (c - a);
}

float kielAst(int b, int welcher, float al, float aMax) {
    int o = b + KS_KOPF + 4 * KS_NT + welcher * KS_NA;
    float u = al / aMax * float(KS_NA - 1);
    if (!(u > 0.0)) return E.q[o];
    if (u >= float(KS_NA - 1)) return E.q[o + KS_NA - 1];
    int i = int(u);
    float f = u - float(i);
    return E.q[o + i] + f * (E.q[o + i + 1] - E.q[o + i]);
}

// Muster eines Druckpunkts (Einheitsstärke) x hinter und y neben ihm: Höhe, Σ K g, Σ g (wie Kielwasser.punkt)
vec3 kielPunkt(int b, float x, float y, float bh, float kMax) {
    vec3 r3 = vec3(0.0);
    if (x <= 1e-6) return r3;
    float aMax = E.q[b + 7], th0 = E.q[b + 8], th1 = E.q[b + 9], kc = E.q[b + 10], kref = E.q[b + 11];
    bool quer = E.q[b + 16] > 0.5;
    float ay = abs(y), r = sqrt(x * x + ay * ay), al = atan(ay, x), aus = 1.0;
    if (al > aMax) {
        aus = exp(-3.0 * (al - aMax) * pow(kc * r, 2.0 / 3.0));
        if (aus < 1e-4) return r3;
        al = aMax;
    }
    for (int a = 0; a < 2; a++) {
        if (a == 0 && !quer) continue;
        float th = kielAst(b, a, al, aMax);
        float k = kielTafel(b, 0, th, th0, th1);
        if (!(k > 0.0) || k > 1.6 * kMax) continue;
        float k1 = kielTafel(b, 1, th, th0, th1), k2 = kielTafel(b, 2, th, th0, th1), k3 = kielTafel(b, 3, th, th0, th1);
        float c = cos(th + al), s = sin(th + al);
        float phi = r * k * c;
        float p2 = r * (k2 * c - 2.0 * k1 * s - k * c), p3 = r * (k3 * c - 3.0 * k2 * s - 3.0 * k1 * c + k * s);
        float amp = min(sqrt(2.0 * KS_PI / (abs(p2) + 1e-12)), 1.547 * pow(abs(p3) / 6.0 + 1e-12, -1.0 / 3.0));
        float w = exp(-(k * bh) * (k * bh) / 4.0);
        if (k > kMax) w *= max(0.0, 1.0 - (k - kMax) / (0.6 * kMax));
        float sS = k / kref, g = sS * sS * w * amp * aus;
        r3.x += g * cos(phi + (p2 > 0.0 ? KS_VIERTEL_PI : -KS_VIERTEL_PI));
        r3.y += g * k;
        r3.z += g;
    }
    return r3;
}

float kielGlatt(float a, float b, float x) { float t = clamp((x - a) / (b - a), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }

/** Ob der Ort p im Bereich der Quelle b liegt (Reichweite plus zwei Längen um ihre Mitte, wie Kielwasser.am). */
bool kielInReichweite(int b, vec2 p) { return length(p - vec2(E.q[b + 2], E.q[b + 3])) <= E.q[b + 15] + 2.0 * E.q[b + 4]; }

/**
 * Über die Stücke k = l, l + s, … der Spur der Quelle b das nächste mit Lot im Stück (0 ≤ u ≤ 1), wie spur in
 * see_kiel.comp ohne Vorwahl: best (d²), bestK (Nummer) und o (Weg hinter dem Bug, Abstand quer, Alter) laufen mit; bei
 * gleichem Abstand bleibt das frühere Stück (wie in der Reihe).
 */
void kielSpurTeil(int b, vec2 p, int l, int s, inout float best, inout int bestK, inout vec3 o) {
    int m = int(E.q[b + 17]);
    int s0 = b + KS_KOPF + 4 * KS_NT + 2 * KS_NA;
    for (int k = l; k + 1 < m; k += s) {
        int ia = s0 + 4 * k;
        vec2 a = vec2(E.q[ia], E.q[ia + 1]), c = vec2(E.q[ia + 4], E.q[ia + 5]);
        vec2 sv = c - a;
        float l2 = dot(sv, sv);
        float u = l2 < 1e-12 ? 0.0 : dot(p - a, sv) / l2;
        if (u < 0.0 || u > 1.0) continue;
        vec2 qv = a + sv * u - p;
        float d2 = dot(qv, qv);
        if (d2 < best) {
            best = d2;
            bestK = k;
            o = vec3(E.q[ia + 2] + (E.q[ia + 6] - E.q[ia + 2]) * u, sqrt(d2), E.q[ia + 3] + (E.q[ia + 7] - E.q[ia + 3]) * u);
        }
    }
}

/**
 * Der Beitrag der Quelle b am Ort mit den Spurkoordinaten o: (Höhe, Schaum, Σ K g, Σ g); welle falsch: nur der Schaum
 * (seit S7d5 die weite Kachel). Wie Kielwasser.am je Quelle.
 */
vec4 kielBeitrag(int b, vec3 o, float kMax, bool welle) {
    vec4 eigen = vec4(0.0);
    float lang = E.q[b + 4];
    if (welle) {
        float a = E.q[b + 6], bh = E.q[b + 5];
        vec3 pb = kielPunkt(b, o.x, o.y, bh, kMax), ph = kielPunkt(b, o.x - lang, o.y, bh, kMax);
        eigen.x = a * (pb.x - ph.x);
        eigen.z = a * (pb.y + ph.y);
        eigen.w = a * (pb.z + ph.z);
    }
    // Schaum hinter dem Heck (Kielwasser.schaum)
    float f0 = E.q[b + 12], tau = E.q[b + 13], breite = E.q[b + 14];
    float weg = o.x - lang * 0.8;
    if (weg >= -0.2 * lang && f0 > 0.0) {
        float w = 0.55 * breite + 0.12 * sqrt(max(0.0, weg) * breite);
        eigen.y = min(1.0, f0 * exp(-max(0.0, o.z) / tau) * (1.0 - kielGlatt(0.5 * w, w, o.y)));
    }
    return eigen;
}
