package com.dan.core.szene;

import java.util.Random;

/**
 * Die Hüllen der Stützstellen für die Synthese der Oberfläche (Seegang Werkbuch, S3; SF1 A nach Jeschke u. a. 2018,
 * „Water Surface Wavelets“, mit einer eigenen Erweiterung [G]).
 * <p>
 * Eine Stützstelle (Band b, Richtung q) des Nahfelds wird nicht als eine Welle gezeichnet, sondern als viele Wellen mit
 * den Frequenzen ω_b + Δ_m im Band und zufälligen Phasen. Ihre Wellenzahl ist am Ort k(ω_b + Δ) ≈ k_b + Δ ∂k/∂ω, und
 * ∂k/∂ω ist der Kehrwert der Gruppengeschwindigkeit. Längs eines Strahls ist die Phase der Welle m daher
 * <pre>
 *   Φ_b(x) + Δ_m T_b(x) − (ω_b + Δ_m) t + φ_m = [Φ_b(x) − ω_b t] + Δ_m [T_b(x) − t] + φ_m
 * </pre>
 * mit Φ_b der Phase der Mitte und T_b = ∂Φ/∂ω der Laufzeit der Gruppe ({@link Phasenfeld}). Die Summe über m hängt
 * also nur von τ = T_b(x) − t ab: η_bq = Re{ A · G_bq(τ) · e^{i(Φ_bq − ω_b t)} } mit der komplexen Hülle
 * <pre>
 *   G_bq(τ) = Σ_m c_m e^{i Δ_m τ},   Σ |c_m|² = 1
 * </pre>
 * Der Träger läuft mit der Phasengeschwindigkeit ω/k(h), die Gruppen mit c_g(h) – auch im Flachen, wo der Profilpuffer
 * nach Jeschke (eine Funktion von Φ allein) die Gruppen mit der Geschwindigkeit des tiefen Wassers liefe. Vernachlässigt
 * ist die Krümmung ∂²k/∂ω² über die Breite des Bands.
 * <p>
 * <b>Festlegungen [G]:</b> Jede Stützstelle hat ihre eigene Hülle (eine gemeinsame, nur verschoben gelesen, gab
 * Echos zwischen den Richtungen, Befund B30). Energie gleich verteilt in ω über das Band: |c_m|² = 1 / Zahl der
 * Teilwellen, die Phasen zufällig (fester Keim); mit zufälligen Beträgen verschob sich der Schwerpunkt eines Bands je
 * nach Wurf, und die Neigung der Fläche lag bis 14 % neben der Buchhaltung (Befund B29). Abgelegt an {@link #N}
 * Stellen je Periode, acht Stellen auf die kürzeste Teilwelle (Δt_b = 2π / (8 · max|Δ|)), dazwischen kubisch nach
 * Hermite aus G und G' = dG/dτ. Die Periode einer Hülle ist damit rund 4 h im Band der Spitze; je Richtung um bis zu
 * 10 % verschieden, damit die Stützstellen nicht im Gleichschritt wiederkehren. Gerechnet mit einer FFT der Länge N.
 */
public final class Profilpuffer {
    /** Stellen je Periode [G]. */
    public static final int N = 4096;

    public final int nb, nd;
    /** Mitte und Grenzen der Bänder (rad/s). */
    public final double[] omega, unten, oben;
    /** Je Stützstelle bq = b · nd + q: Periode (s), Zahl der Teilwellen, die erste m, Momente Σ|c|² Δ und Σ|c|² Δ². */
    public final double[] periode, mittelD, quadD;
    public final int[] teile, mVon;
    /** Je Stützstelle N × {Re G, Im G, Re G', Im G'}. */
    public final double[][] g;
    private final double[][] cRe, cIm;

    /**
     * Hüllen für Bänder mit den Mitten ω_b und den Grenzen (rad/s), nd Richtungen; {@code einzeln}: nur die Mitte
     * (G ≡ 1), zum Prüfen der Phase.
     */
    public Profilpuffer(double[] omega, double[] unten, double[] oben, int nd, long keim, boolean einzeln) {
        nb = omega.length;
        this.nd = nd;
        this.omega = omega.clone(); this.unten = unten.clone(); this.oben = oben.clone();
        int n = nb * nd;
        periode = new double[n]; mittelD = new double[n]; quadD = new double[n];
        teile = new int[n]; mVon = new int[n];
        g = new double[n][];
        cRe = new double[n][]; cIm = new double[n][];
        Random r = new Random(keim);
        for (int b = 0; b < nb; b++) {
            double dmax = Math.max(omega[b] - unten[b], oben[b] - omega[b]);
            for (int q = 0; q < nd; q++) {
                int bq = b * nd + q;
                double tp = N * 2 * Math.PI / (8 * dmax) * (1 + 0.1 * frac(0.6180339887 * (bq + 1)));
                periode[bq] = tp;
                double dw = 2 * Math.PI / tp;
                int mLo = (int) Math.ceil((unten[b] - omega[b]) / dw), mHi = (int) Math.floor((oben[b] - omega[b]) / dw);
                if (einzeln) { mLo = 0; mHi = 0; }
                int nm = mHi - mLo + 1;
                teile[bq] = nm; mVon[bq] = mLo;
                double[] cr = new double[nm], ci = new double[nm];
                double f = 1 / Math.sqrt(nm);
                double[] re = new double[N], im = new double[N], dre = new double[N], dim = new double[N];
                for (int m = 0; m < nm; m++) {
                    double ph = einzeln ? 0 : 2 * Math.PI * r.nextDouble();
                    cr[m] = f * Math.cos(ph); ci[m] = f * Math.sin(ph);
                    int mm = mLo + m, k = Math.floorMod(mm, N);
                    double d = mm * dw;
                    re[k] = cr[m]; im[k] = ci[m];
                    dre[k] = -d * ci[m]; dim[k] = d * cr[m];                     // i Δ c
                    mittelD[bq] += f * f * d; quadD[bq] += f * f * d * d;
                }
                cRe[bq] = cr; cIm[bq] = ci;
                fft(re, im);
                fft(dre, dim);
                double[] gb = new double[4 * N];
                for (int j = 0; j < N; j++) { gb[4 * j] = re[j]; gb[4 * j + 1] = im[j]; gb[4 * j + 2] = dre[j]; gb[4 * j + 3] = dim[j]; }
                g[bq] = gb;
            }
        }
    }

    static double frac(double x) { return x - Math.floor(x); }

    /** Rückwärts-FFT ohne Faktor: x_j = Σ_k X_k e^{+2πi jk/N}, an Ort und Stelle. */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len;
            for (int i = 0; i < n; i += len)
                for (int k = 0; k < len / 2; k++) {
                    double wr = Math.cos(ang * k), wi = Math.sin(ang * k);
                    int a = i + k, b = i + k + len / 2;
                    double vr = re[b] * wr - im[b] * wi, vi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - vr; im[b] = im[a] - vi; re[a] += vr; im[a] += vi;
                }
        }
    }

    /** G_bq(τ) und dG/dτ, kubisch nach Hermite zwischen den Stellen: {Re G, Im G, Re G', Im G'} in o. */
    public void wert(int bq, double tau, double[] o) {
        double tp = periode[bq], dt = tp / N;
        double x = (tau % tp + tp) % tp / dt;
        int j = (int) x;
        if (j >= N) j = N - 1;
        double u = x - j;
        int j1 = j + 1 == N ? 0 : j + 1;
        double[] gb = g[bq];
        double u2 = u * u, u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        double d00 = (6 * u2 - 6 * u) / dt, d10 = 3 * u2 - 4 * u + 1, d01 = (-6 * u2 + 6 * u) / dt, d11 = 3 * u2 - 2 * u;
        int a = 4 * j, c = 4 * j1;
        for (int k = 0; k < 2; k++) {
            double p0 = gb[a + k], m0 = gb[a + 2 + k], p1 = gb[c + k], m1 = gb[c + 2 + k];
            o[k] = h00 * p0 + h10 * dt * m0 + h01 * p1 + h11 * dt * m1;
            o[2 + k] = d00 * p0 + d10 * m0 + d01 * p1 + d11 * m1;
        }
    }

    /** G_bq(τ) und dG/dτ exakt als Summe der Teilwellen (für die Prüfung der Hermite-Näherung). */
    public void exakt(int bq, double tau, double[] o) {
        double dw = 2 * Math.PI / periode[bq], re = 0, im = 0, dre = 0, dim = 0;
        for (int m = 0; m < teile[bq]; m++) {
            double d = (mVon[bq] + m) * dw, co = Math.cos(d * tau), si = Math.sin(d * tau);
            double rr = cRe[bq][m] * co - cIm[bq][m] * si, ii = cRe[bq][m] * si + cIm[bq][m] * co;
            re += rr; im += ii; dre -= d * ii; dim += d * rr;
        }
        o[0] = re; o[1] = im; o[2] = dre; o[3] = dim;
    }

    /**
     * Seit S5b-6 (Seegang Werkbuch, Reflexion): die Stützstelle {@code nach} liest dieselbe Hülle wie {@code von} – dieselben
     * Teilwellen, dieselbe Periode. Für die reflektierte Welle, die dieselbe Gruppe ist wie die einlaufende.
     */
    public void teilen(int von, int nach) {
        g[nach] = g[von];
        periode[nach] = periode[von];
        mittelD[nach] = mittelD[von];
        quadD[nach] = quadD[von];
        teile[nach] = teile[von];
        mVon[nach] = mVon[von];
        cRe[nach] = cRe[von];
        cIm[nach] = cIm[von];
    }

    /** Speicher auf der Karte in float (Byte). */
    public long bytes() { return 4L * 4 * N * nb * nd; }
}
