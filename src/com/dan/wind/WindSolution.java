package com.dan.wind;

import java.util.Locale;

/**
 * Ein gelöstes Windfeld (seit Brocken Phase 4): das massenkonsistente Feld von {@link WindField#solve} auf einem
 * geländefolgenden Gitter mit fester Obergrenze (σ-Koordinaten: die Schichten folgen unten dem Gelände und werden zur
 * Obergrenze hin flach). Unveränderlich; Abfragen sind von jedem Faden aus erlaubt.
 * <p>
 * Raum wie {@link WindField}: x Ost, y oben, z Süd, Höhen über dem Bezug des Geländes. Zellen in Spalten (i Ost, j Süd)
 * und Schichten k von unten; die Werte stehen in der Mitte jeder Zelle.
 */
public final class WindSolution {
    /** Ecke (x0, z0) des Gitters, Zelle waagerecht (m), Zellen in x und z, Schichten. */
    public final double x0, z0, cell;
    public final int nx, nz, n;
    /** Obergrenze des Gitters (m über dem Bezug): höchster Grund plus {@link WindField#top}. */
    public final double ztop;
    /** σ der Schichtgrenzen (n + 1, von 0 am Grund bis 1 oben) und der Mitten (n). */
    final double[] sigE, sigC;
    /** Grund je Spalte (m), Lee-Winkel je Spalte (rad), Tiefe des Kaltluftsees je Spalte (m). */
    final float[] zg, shelter, cold;
    /** Wind in der Mitte jeder Zelle (m/s), Index (k · nz + j) · nx + i. */
    final float[] u, v, w;
    /** Diagnose: Schritte des Lösers, verbleibender Rest (relativ), größter und mittlerer Fehler der Masse je Zelle, Rechenzeit. */
    public final int iterations;
    public final double residual, massMax, massMean, millis;
    /** Was gerechnet wurde: Wind der Lage, Richtung, Schichtung, Verhältnis senkrecht zu waagerecht, Hangwind. */
    public final double speed, fromDeg, ratio, slopeFlow;
    public final WindField.Stability stability;

    WindSolution(double x0, double z0, double cell, int nx, int nz, int n, double ztop, double[] sigE, double[] sigC, float[] zg, float[] shelter,
                 float[] cold, float[] u, float[] v, float[] w, int iterations, double residual, double massMax, double massMean, double millis,
                 double speed, double fromDeg, double ratio, double slopeFlow, WindField.Stability stability) {
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.nx = nx;
        this.nz = nz;
        this.n = n;
        this.ztop = ztop;
        this.sigE = sigE;
        this.sigC = sigC;
        this.zg = zg;
        this.shelter = shelter;
        this.cold = cold;
        this.u = u;
        this.v = v;
        this.w = w;
        this.iterations = iterations;
        this.residual = residual;
        this.massMax = massMax;
        this.massMean = massMean;
        this.millis = millis;
        this.speed = speed;
        this.fromDeg = fromDeg;
        this.ratio = ratio;
        this.slopeFlow = slopeFlow;
        this.stability = stability;
    }

    /** Liegt der Ort (x, z) im Gitter? */
    public boolean contains(double x, double z) {
        return x >= x0 && z >= z0 && x <= x0 + nx * cell && z <= z0 + nz * cell;
    }

    /** Zahl der Zellen. */
    public int cells() { return nx * nz * n; }

    // ------------------------------------------------------------------ Spalten (bilinear zwischen den Mitten)

    private double col(float[] a, double x, double z) {
        double fx = (x - x0) / cell - 0.5, fz = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        int i0 = clamp(i, 0, nx - 1), i1 = clamp(i + 1, 0, nx - 1), j0 = clamp(j, 0, nz - 1), j1 = clamp(j + 1, 0, nz - 1);
        double a0 = a[j0 * nx + i0] + (a[j0 * nx + i1] - a[j0 * nx + i0]) * tx, a1 = a[j1 * nx + i0] + (a[j1 * nx + i1] - a[j1 * nx + i0]) * tx;
        return a0 + (a1 - a0) * tz;
    }

    /** Grund des Gitters am Ort (bilinear zwischen den Spalten, m). */
    public double groundAt(double x, double z) { return col(zg, x, z); }

    /** Lee-Winkel am Ort (rad, 0 frei, positiv im Windschatten einer Kante luvwärts). */
    public double shelterAt(double x, double z) { return col(shelter, x, z); }

    /** Tiefe des Kaltluftsees am Ort (m, 0 ohne). */
    public double coldAt(double x, double z) { return col(cold, x, z); }

    /** Schicht (mit Bruchteil, auf die Mitten bezogen) für σ. */
    double layerOf(double s) {
        if (s <= sigC[0]) return 0;
        if (s >= sigC[n - 1]) return n - 1;
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (sigC[m] <= s) lo = m; else hi = m; }
        return lo + (s - sigC[lo]) / (sigC[hi] - sigC[lo]);
    }

    /**
     * Wind (m/s) am Ort (x, y, z) als {u Ost, v oben, w Süd}, trilinear zwischen den Mitten; unter dem Grund 0, unter
     * der Mitte der ersten Schicht mit dem Logarithmus zum Grund hin (z0 aus dem Verhältnis der beiden untersten
     * Schichten geschätzt), über der Obergrenze der Wert der obersten Schicht.
     */
    public double[] at(double x, double y, double z) {
        double g = groundAt(x, z), d = Math.max(1, ztop - g), h = y - g;
        if (h <= 0) return new double[3];
        double s = h / d;
        double fx = (x - x0) / cell - 0.5, fz = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        int i0 = clamp(i, 0, nx - 1), i1 = clamp(i + 1, 0, nx - 1), j0 = clamp(j, 0, nz - 1), j1 = clamp(j + 1, 0, nz - 1);
        double kk = layerOf(s);
        int k0 = (int) Math.floor(kk), k1 = Math.min(n - 1, k0 + 1);
        double tk = kk - k0;
        double[] out = new double[3];
        float[][] f = {u, v, w};
        for (int c = 0; c < 3; c++) {
            float[] a = f[c];
            double l0 = bil(a, k0, i0, i1, j0, j1, tx, tz), l1 = bil(a, k1, i0, i1, j0, j1, tx, tz);
            out[c] = l0 + (l1 - l0) * tk;
        }
        double h0 = sigC[0] * d;
        if (h < h0) {
            // unter der ersten Mitte: logarithmisch zum Grund (z0 klein gegen die Schicht: 0,1 m [G])
            double k = Math.log(h / 0.1 + 1) / Math.log(h0 / 0.1 + 1);
            for (int c = 0; c < 3; c++) out[c] *= k;
        }
        return out;
    }

    private double bil(float[] a, int k, int i0, int i1, int j0, int j1, double tx, double tz) {
        int b = k * nz;
        double a0 = a[(b + j0) * nx + i0] + (a[(b + j0) * nx + i1] - a[(b + j0) * nx + i0]) * tx;
        double a1 = a[(b + j1) * nx + i0] + (a[(b + j1) * nx + i1] - a[(b + j1) * nx + i0]) * tx;
        return a0 + (a1 - a0) * tz;
    }

    /** Waagerechte Geschwindigkeit (m/s) in h m über dem Grund am Ort. */
    public double speedAt(double x, double h, double z) {
        double[] q = at(x, groundAt(x, z) + h, z);
        return Math.hypot(q[0], q[2]);
    }

    static int clamp(int v, int a, int b) { return v < a ? a : Math.min(v, b); }

    /**
     * Dasselbe Feld mit dem Faktor k (etwa um die Stärke an eine Messung anzupassen, ohne neu zu rechnen; Massenerhaltung
     * bleibt, weil sie linear ist).
     */
    public WindSolution scaled(double k) {
        float[] a = u.clone(), b = v.clone(), c = w.clone();
        float f = (float) k;
        for (int i = 0; i < a.length; i++) { a[i] *= f; b[i] *= f; c[i] *= f; }
        return new WindSolution(x0, z0, cell, nx, nz, n, ztop, sigE, sigC, zg, shelter, cold, a, b, c, iterations, residual, massMax, massMean, millis,
                speed * k, fromDeg, ratio, slopeFlow * k, stability);
    }

    /** „254 × 238 × 30 Zellen zu 50 m bis 2 641 m · 143 Schritte, Rest 1e-5 · Masse ± 0,03 % (höchstens 0,4 %) · 1,8 s“ */
    public String describe() {
        return String.format(Locale.GERMAN, "%d × %d × %d Zellen zu %.0f m bis %.0f m · %d Schritte, Rest %.0e · Masse ± %.3f %% (höchstens %.2f %%) · %.1f s",
                nx, nz, n, cell, ztop, iterations, residual, massMean * 100, massMax * 100, millis / 1000);
    }
}
