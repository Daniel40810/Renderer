// Referenz Stufe 2 (P11a): die Szene Ebenen (Stufe 1: Ebenen) – waagerechter Boden und Wasser, unendlich, oder mit
// W_KUGEL beide als Kugel um die Mitte der Luft. Höhen relativ zum Auge. Für die Kugel ist |o|² − r² ohne Auslöschung
// gerechnet (die Mitte liegt 6 360 km unter dem Auge; float hätte dort 0,5 m Stellen).

float HA;           // Höhe des Auges (m NHN), gesetzt in main

/** |p − Mitte|² − r² für eine Kugel um die Mitte der Luft in der Höhe hk relativ zum Auge: x² + z² + (y − hk)(y + hk + 2 (R + h_Auge)). */
float kugelC(vec3 p, float hk) { return p.x * p.x + p.z * p.z + (p.y - hk) * (p.y + hk + 2.0 * (W[W_R] + HA)); }

float kugelB(vec3 p, vec3 d) { return dot(p, d) + (W[W_R] + HA) * d.y; }

vec3 kugelNormale(vec3 p, vec3 d, float t) {
    vec3 q = vec3(p.x + d.x * t, p.y + d.y * t + (W[W_R] + HA), p.z + d.z * t);
    return normalize(q);
}

bool schneide(vec3 p, vec3 d, float tMax, out Treffer aus) {
    float best = tMax;
    int art = 0;
    vec3 nk = vec3(0.0, 1.0, 0.0);
    bool kugel = W[W_KUGEL] > 0.5;
    if (W[W_HAT_BODEN] > 0.5) {
        float hb = W[W_BODEN];
        if (kugel) {
            float b = kugelB(p, d), cc = kugelC(p, hb), disc = b * b - cc;
            if (disc >= 0.0 && b < 0.0) {
                float t = cc / (-b + sqrt(disc));
                if (t > 1e-9 && t < best) { best = t; art = BODEN; nk = kugelNormale(p, d, t); }
            }
        } else if (d.y != 0.0) {
            float t = (hb - p.y) / d.y;
            if (t > 1e-9 && t < best) { best = t; art = BODEN; nk = vec3(0.0, 1.0, 0.0); }
        }
    }
    if (W[W_HAT_WASSER] > 0.5) {
        float hw = W[W_WASSER];
        if (kugel) {
            float b = kugelB(p, d), cc = kugelC(p, hw), disc = b * b - cc;
            if (disc >= 0.0) {
                float sq = sqrt(disc);
                float t1 = b < 0.0 ? cc / (-b + sq) : -b - sq, t2 = b < 0.0 ? -b + sq : cc / (-b - sq);
                float t = t1 > 1e-9 ? t1 : t2;
                if (t > 1e-9 && t < best) { best = t; art = WASSER; nk = kugelNormale(p, d, t); }
            }
        } else if (d.y != 0.0) {
            float t = (hw - p.y) / d.y;
            if (t > 1e-9 && t < best) { best = t; art = WASSER; nk = vec3(0.0, 1.0, 0.0); }
        }
    }
    if (art == 0) return false;
    aus.t = best;
    aus.art = art;
    aus.n = nk;
    aus.albedo = vec3(W[W_ALB], W[W_ALB + 1], W[W_ALB + 2]);
    aus.sigma2 = W[W_SZ_SIGMA2];
    return true;
}

/** Trübung am Eintrittspunkt (S8); negativ: die der ganzen Szene. */
float szeneTruebung(vec3 p) { return -1.0; }
