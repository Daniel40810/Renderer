package com.dan.render.reference;

import com.dan.core.szene.Bauten;

import java.util.List;

/**
 * Häuser und Körper der Gestalt für die Referenz (seit P12c3, Werkbuch HalligHooge Teil A, 12.13, Datei 81; F138 A,
 * F141 A, F143 A): die Dreiecke aus {@link Bauten} in einem {@link Huellbaum}, relativ zum Auge (x Ost, y oben, z Süd)
 * und je Scheitel gesenkt um d² / 2 R_eff wie im Echtzeitbild ({@code bau.glsl}). Schnitt in double nach Möller und
 * Trumbore, beide Seiten.
 * <p>
 * <b>Material</b> wie {@code haus.frag}, im Mittel der Muster (F143 A): Wand mit der Albedo des Hauses (Mittel mit
 * Fugen), Sockel unter 0,25 m mal 0,7; Fenster und Tür am selben Ort wie im Bild (Rahmen weiß, Glas als Spiegel mit
 * f0 = 0,043 und Licht in der Nacht nach dem Anteil des Bilds, F141 A; Tür dunkelgrün); Reet mit Moos nach Norden
 * (18 %) und dunklem First (0,35 m); Ziegel und flache Dächer mit der Albedo des Dachs; Körper nach Art (Holz, Stein,
 * Beton, Buschwerk), unter 1,4 m NHN nass, unter 0,9 m mit Algen.
 */
public final class Dreiecksbaum {
    /** Glas: Fresnel f0 (n = 1,52), Facetten α² [G] (fast glatt), Albedo des Raums dahinter wie {@code haus.frag}. */
    public static final double GLAS_F0 = 0.043, GLAS_ALPHA2 = 1e-4, GLAS_ALBEDO = 0.025;

    final Bauten bauten;
    final int n;
    /** Scheitel je Dreieck (9 double), Normale (3), Fläche (u, v, w je Scheitel: 9 float), Art, Gebäude. */
    final double[] p, nrm;
    final float[] uvw;
    final int[] art, haus;
    final Huellbaum baum;
    /** Auge (UTM, m NHN), Kehrwert von 2 R_eff. */
    public final double ost, nord, hAuge, k;
    /** Fenster: Anteil mit Licht (0 … 1), Leuchtdichte (cd/m²), an (wie der Kopf von {@code bau.glsl}). */
    public double fensterAnteil, fensterL = 40;
    final float[] zufall;

    /**
     * @param bereiche Bereiche der Scheitel {erster, Zahl} (die im Echtzeitbild gezeichnet werden)
     * @param zufall   je Gebäude die Zahl 0 … 1 des Bilds ({@code Hausbild.zufall})
     */
    public Dreiecksbaum(Bauten b, List<int[]> bereiche, float[] zufall, double ost, double nord, double hAuge, double k) {
        this.bauten = b;
        this.ost = ost;
        this.nord = nord;
        this.hAuge = hAuge;
        this.k = k;
        this.zufall = zufall;
        int z = 0;
        for (int[] r : bereiche) z += r[1] / 3;
        n = z;
        p = new double[9 * n];
        nrm = new double[3 * n];
        uvw = new float[9 * n];
        art = new int[n];
        haus = new int[n];
        double[] lo = new double[3 * n], hi = new double[3 * n];
        float[] s = b.scheitel;
        int F = Bauten.FLOATS, t = 0;
        double dx = b.e0 - ost, dz = (nord - b.n0);         // x_rel = x + e0 − ost; z_rel = z − (n0 − nord) = z + nord − n0
        for (int[] r : bereiche)
            for (int v = r[0]; v + 2 < r[0] + r[1]; v += 3, t++) {
                for (int e = 0; e < 3; e++) {
                    int o = F * (v + e);
                    double x = s[o] + dx, zz = s[o + 2] + dz, y = s[o + 1] - hAuge - k * (x * x + zz * zz);
                    p[9 * t + 3 * e] = x;
                    p[9 * t + 3 * e + 1] = y;
                    p[9 * t + 3 * e + 2] = zz;
                    uvw[9 * t + 3 * e] = s[o + 8];
                    uvw[9 * t + 3 * e + 1] = s[o + 9];
                    uvw[9 * t + 3 * e + 2] = s[o + 10];
                }
                int o = F * v;
                art[t] = (int) s[o + 3];
                haus[t] = (int) s[o + 7];
                double nx = s[o + 4], ny = s[o + 5], nz = s[o + 6], l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (!(l > 0)) {
                    double[] a = {p[9 * t + 3] - p[9 * t], p[9 * t + 4] - p[9 * t + 1], p[9 * t + 5] - p[9 * t + 2]};
                    double[] c = {p[9 * t + 6] - p[9 * t], p[9 * t + 7] - p[9 * t + 1], p[9 * t + 8] - p[9 * t + 2]};
                    nx = a[1] * c[2] - a[2] * c[1];
                    ny = a[2] * c[0] - a[0] * c[2];
                    nz = a[0] * c[1] - a[1] * c[0];
                    l = Math.max(1e-30, Math.sqrt(nx * nx + ny * ny + nz * nz));
                }
                nrm[3 * t] = nx / l;
                nrm[3 * t + 1] = ny / l;
                nrm[3 * t + 2] = nz / l;
                for (int a = 0; a < 3; a++) {
                    double m0 = Math.min(p[9 * t + a], Math.min(p[9 * t + 3 + a], p[9 * t + 6 + a]));
                    double m1 = Math.max(p[9 * t + a], Math.max(p[9 * t + 3 + a], p[9 * t + 6 + a]));
                    lo[3 * t + a] = m0 - 1e-6;
                    hi[3 * t + a] = m1 + 1e-6;
                }
            }
        baum = new Huellbaum(lo, hi);
    }

    public int dreiecke() { return n; }

    /** Mitte des Dreiecks i (relativ zum Auge), für Prüfungen. */
    public double[] mitte(int i) {
        int q = 9 * i;
        return new double[] {(p[q] + p[q + 3] + p[q + 6]) / 3, (p[q + 1] + p[q + 4] + p[q + 7]) / 3, (p[q + 2] + p[q + 5] + p[q + 8]) / 3};
    }

    /** Schnitt mit Dreieck i: Weg oder +∞ (Möller und Trumbore, double, beide Seiten). */
    double dreieck(int i, double[] o, double[] d, double tMax, double[] bary) {
        int q = 9 * i;
        double e1x = p[q + 3] - p[q], e1y = p[q + 4] - p[q + 1], e1z = p[q + 5] - p[q + 2];
        double e2x = p[q + 6] - p[q], e2y = p[q + 7] - p[q + 1], e2z = p[q + 8] - p[q + 2];
        double hx = d[1] * e2z - d[2] * e2y, hy = d[2] * e2x - d[0] * e2z, hz = d[0] * e2y - d[1] * e2x;
        double a = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(a) < 1e-14) return Double.POSITIVE_INFINITY;
        double f = 1 / a;
        double sx = o[0] - p[q], sy = o[1] - p[q + 1], sz = o[2] - p[q + 2];
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0 || u > 1) return Double.POSITIVE_INFINITY;
        double qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
        double v = f * (d[0] * qx + d[1] * qy + d[2] * qz);
        if (v < 0 || u + v > 1) return Double.POSITIVE_INFINITY;
        double t = f * (e2x * qx + e2y * qy + e2z * qz);
        if (!(t > 0) || !(t < tMax)) return Double.POSITIVE_INFINITY;
        if (bary != null) {
            bary[0] = u;
            bary[1] = v;
        }
        return t;
    }

    /** Nächster Treffer bis tMax; füllt aus (Material wie im Bild) und gibt true. */
    public boolean schneide(double[] o, double[] d, double tMax, Szene.Treffer aus) {
        int[] welches = {-1};
        double[] bary = new double[2], b = new double[2];
        double t = baum.schneide(o, d, tMax, (i, tm) -> {
            double ti = dreieck(i, o, d, tm, b);
            if (ti < tm) {
                welches[0] = i;
                bary[0] = b[0];
                bary[1] = b[1];
            }
            return ti;
        });
        if (welches[0] < 0 || !(t < tMax)) return false;
        material(welches[0], o, d, t, bary, aus);
        return true;
    }

    /** Nur der Weg (ohne Material), für die Prüfung gegen alle Dreiecke. */
    public double weg(double[] o, double[] d, double tMax) {
        return baum.schneide(o, d, tMax, (i, tm) -> dreieck(i, o, d, tm, null));
    }

    /** Weg gegen jedes Dreieck (zur Prüfung des Baums). */
    public double wegAlle(double[] o, double[] d, double tMax) {
        double best = tMax;
        boolean g = false;
        for (int i = 0; i < n; i++) {
            double t = dreieck(i, o, d, best, null);
            if (t < best) {
                best = t;
                g = true;
            }
        }
        return g ? best : Double.POSITIVE_INFINITY;
    }

    // ------------------------------------------------------------------ Material

    private void material(int i, double[] o, double[] d, double t, double[] bary, Szene.Treffer aus) {
        aus.t = t;
        aus.art = Szene.BODEN;
        aus.sigma2 = 0;
        aus.spiegel = 0;
        aus.leuchtet = false;
        aus.aus = Szene.AUS_BAUTEN;
        double nx = nrm[3 * i], ny = nrm[3 * i + 1], nz = nrm[3 * i + 2];
        // wie haus.frag: die Normale zum Auge des Strahls (Rückseite)
        if (nx * d[0] + ny * d[1] + nz * d[2] > 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        aus.n[0] = nx;
        aus.n[1] = ny;
        aus.n[2] = nz;
        double w0 = 1 - bary[0] - bary[1];
        int q = 9 * i;
        double u = w0 * uvw[q] + bary[0] * uvw[q + 3] + bary[1] * uvw[q + 6];
        double v = w0 * uvw[q + 1] + bary[0] * uvw[q + 4] + bary[1] * uvw[q + 7];
        double w = w0 * uvw[q + 2] + bary[0] * uvw[q + 5] + bary[1] * uvw[q + 8];
        double x = o[0] + t * d[0], y = o[1] + t * d[1], z = o[2] + t * d[2];
        double yW = y + hAuge + k * (x * x + z * z);                 // Höhe (m NHN) ohne die Senkung
        double[] a = aus.albedo;
        int ar = art[i];
        Bauten.Gebaeude g = haus[i] >= 0 && haus[i] < bauten.gebaeude.size() ? bauten.gebaeude.get(haus[i]) : null;
        if (ar == Bauten.WAND && g != null) {
            for (int c = 0; c < 3; c++) a[c] = g.wandAlbedo[c];
            if (v < 0.25) for (int c = 0; c < 3; c++) a[c] *= 0.7;
            float z0 = haus[i] < zufall.length ? zufall[haus[i]] : 0;
            float[] oe = oeffnung((float) u, (float) v, (float) w, g.traufe - g.boden, g.first - g.boden, z0, (float) nx, (float) nz);
            if (oe[0] > 0.5f) {
                if (oe[0] < 1.5f) set(a, 0.78, 0.78, 0.76);
                else if (oe[0] < 2.5f) {
                    aus.art = Szene.SPIEGEL;
                    aus.f0 = GLAS_F0;
                    aus.alpha2 = GLAS_ALPHA2;
                    set(a, GLAS_ALBEDO, GLAS_ALBEDO, GLAS_ALBEDO);
                    float an = hash(oe[1] * 7.31f + z0 * 113.0f, (float) Math.floor(nx * 5.0f) + 3.0f * (float) Math.floor(nz * 5.0f));
                    if (fensterAnteil > 0 && an < fensterAnteil) {
                        double s = fensterL * (0.6 + 0.8 * hash(an, 3.7f));
                        aus.leuchtet = true;
                        aus.leucht[0] = s;
                        aus.leucht[1] = 0.72 * s;
                        aus.leucht[2] = 0.42 * s;
                    }
                } else set(a, 0.045, 0.085, 0.06);
            }
        } else if (ar >= Bauten.HOLZ) {
            switch (ar) {
                case Bauten.HOLZ -> set(a, 0.10, 0.085, 0.065);
                case Bauten.STEIN -> set(a, 0.17 * 0.92, 0.16 * 0.92, 0.15 * 0.92);
                case Bauten.BETON -> set(a, 0.30, 0.29, 0.27);
                default -> set(a, 0.065, 0.055, 0.04);
            }
            if (yW < 1.4) for (int c = 0; c < 3; c++) a[c] *= 0.55;
            if (yW < 0.9) {
                a[0] = 0.5 * (a[0] + 0.03);
                a[1] = 0.5 * (a[1] + 0.05);
                a[2] = 0.5 * (a[2] + 0.02);
            }
        } else if (g != null) {
            for (int c = 0; c < 3; c++) a[c] = g.dachAlbedo[c];
            if (g.dach == Bauten.REET) {
                double h = Math.hypot(nx, nz);
                double nordS = Math.max(0, Math.min(1, -nz / Math.max(h, 1e-3))) * Math.max(0, Math.min(1, 1 - ny));
                double m = 0.18 * nordS;
                a[0] = a[0] * (1 - m) + 0.055 * m;
                a[1] = a[1] * (1 - m) + 0.075 * m;
                a[2] = a[2] * (1 - m) + 0.035 * m;
                if (g.first - yW < 0.35) set(a, 0.055, 0.055, 0.05);
            }
        } else set(a, 0.2, 0.2, 0.2);
    }

    private static void set(double[] a, double r, double g, double b) {
        a[0] = r;
        a[1] = g;
        a[2] = b;
    }

    // ------------------------------------------------------------------ wie haus.frag (float)

    static float fract(float x) { return x - (float) Math.floor(x); }

    /** hash(vec2) aus haus.frag in float. */
    static float hash(float px, float py) {
        float qx = fract(px * 0.1031f), qy = fract(py * 0.1031f), qz = fract(px * 0.1031f);
        float dd = qx * (qy + 33.33f) + qy * (qz + 33.33f) + qz * (qx + 33.33f);
        qx += dd;
        qy += dd;
        qz += dd;
        return fract((qx + qy) * qz);
    }

    /**
     * Fenster und Tür auf einer Wand wie {@code oeffnung()} in haus.frag (ohne Ausblenden der Sprossen): {Art, Nummer};
     * Art 0 Wand, 1 Rahmen, 2 Glas, 3 Tür. traufe, first: Höhe über dem Boden des Hauses; n: Normale (x, z) zum Auge.
     */
    static float[] oeffnung(float u, float v, float w, float traufe, float first, float zuf, float nx, float nz) {
        if (w < 2.6f) return new float[] {0, 0};
        float hoch = traufe;
        float wb = 0.95f, wh = 1.20f, br = 0.85f, rahmen = 0.07f, sprosse = 0.035f;
        int anzahl = (int) Math.floor((w - 1.0f) / 2.4f) + 1;
        float teil = w / anzahl;
        float kk = (float) Math.floor((u + 0.5f * w) / teil);
        float mitte = (kk + 0.5f) * teil - 0.5f * w, du = u - mitte;
        boolean tuerWand = w >= 6.0f && (nz > 0.6f || nx > 0.6f);
        if (tuerWand && Math.abs(u) < 0.55f && v < 2.0f && v > -0.2f) {
            if (Math.abs(u) > 0.48f || v > 1.93f) return new float[] {1, kk};
            return new float[] {3, kk};
        }
        if (!(tuerWand && Math.abs(mitte) < 1.1f) && hoch >= 2.2f && Math.abs(du) < 0.5f * wb && v > br && v < br + wh) {
            float x = Math.abs(du), y = v - br;
            if (x > 0.5f * wb - rahmen || y < rahmen || y > wh - rahmen) return new float[] {1, kk};
            float s = sprosse;
            if (x < 0.5f * s || Math.abs(y - 0.5f * wh) < 0.5f * s || Math.abs(y - 0.75f * wh) < 0.5f * s) return new float[] {1, kk};
            return new float[] {2, kk};
        }
        if (first - traufe > 3.2f && Math.abs(u) < 0.42f && v > traufe + 0.7f && v < traufe + 1.7f) {
            float x = Math.abs(u), y = v - traufe - 0.7f;
            if (x > 0.42f - rahmen || y < rahmen || y > 1.0f - rahmen || x < 0.5f * sprosse || Math.abs(y - 0.5f) < 0.5f * sprosse) return new float[] {1, 100};
            return new float[] {2, 100};
        }
        return new float[] {0, 0};
    }
}
