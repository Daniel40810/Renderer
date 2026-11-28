package com.dan.physics.auftrieb;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Proben eines Körpers (Seegang Werkbuch, S7a): Würfel eines Rasters der Kante d über die {@link Form}. Jede Zelle
 * wird mit 8 × 8 × 8 Stellen abgetastet (dünne Körper feiner); die Stellen im Körper geben Volumen, Schwerpunkt, untere und obere Kante und den
 * Anteil, mit dem die Probe trägt. Aus denselben Stellen kommen die freien Flächen je Richtung (±x, ±y, ±z): eine
 * Teilsäule von Stellen ist an der Seite frei, wenn die Nachbarzelle auf ihr keine Stelle im Körper hat. So ist die
 * angeströmte Fläche eines Körpers aus Würfeln seine projizierte Fläche (ohne Treppen in der Breite).
 * <p>
 * Achsen des Körpers wie {@link Form}. Das Raster liegt mittig in x und y und beginnt in z an der Unterkante.
 */
public final class Proben {
    /** Abtastung je Kante einer Zelle (Vorgabe 8; dünne Körper mehr). */
    public final int S;
    private final int W;
    public final Form form;
    public final double d;
    public final int n;
    /** Schwerpunkt je Probe (Körper), Volumen (m³), Anteil, der trägt. */
    public final double[] x, y, z, vol, traegt;
    /** Untere Kante (Abstand unter dem Schwerpunkt) und Höhe der Probe (m). */
    public final double[] unten, hoehe;
    /** Freie Fläche je Probe und Richtung: Index 6p + {0: −x, 1: +x, 2: −y, 3: +y, 4: −z, 5: +z} (m²). */
    public final double[] frei;
    /**
     * Seit S7b-1 (Seegang Werkbuch 88): Säulen für das Wasser von der Karte – Blöcke von Zellen in x und y, höchstens
     * {@link #SAEULE} breit; je Probe die Nummer ihrer Säule, je Säule die Mitte ihrer Proben (Körper, nach Volumen).
     */
    public final int[] saeule;
    public final double[] saeuleX, saeuleY;
    public final int saeulen;
    /** Breite einer Säule höchstens (m) [G]: eine Stelle der Karte je Säule, dazwischen verschiebt der Körper die Phase. */
    public static final double SAEULE = 0.8;

    public Proben(Form form, double d) { this(form, d, 8); }

    /** Proben der Kante d mit s × s × s Stellen je Zelle. */
    public Proben(Form form, double d, int s) {
        this.form = form;
        this.d = d;
        this.S = s;
        this.W = (s * s * s + 63) / 64;
        double[] k = form.kasten();
        int nx = (int) Math.ceil((k[1] - k[0]) / d - 1e-9), ny = (int) Math.ceil((k[3] - k[2]) / d - 1e-9), nz = (int) Math.ceil((k[5] - k[4]) / d - 1e-9);
        double x0 = 0.5 * (k[0] + k[1]) - nx * d / 2, y0 = 0.5 * (k[2] + k[3]) - ny * d / 2, z0 = k[4];
        int nc = nx * ny * nz;
        long[] bits = new long[nc * W];
        double h = d / S;
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < ny; j++)
                for (int l = 0; l < nz; l++) {
                    int c0 = ((i * ny + j) * nz + l) * W;
                    for (int a = 0; a < S; a++)
                        for (int c = 0; c < S; c++)
                            for (int e = 0; e < S; e++)
                                if (form.innen(x0 + i * d + (a + 0.5) * h, y0 + j * d + (c + 0.5) * h, z0 + l * d + (e + 0.5) * h)) {
                                    int b = bit(a, c, e);
                                    bits[c0 + (b >>> 6)] |= 1L << (b & 63);
                                }
                }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < ny; j++)
                for (int l = 0; l < nz; l++) {
                    int c0 = ((i * ny + j) * nz + l) * W;
                    if (leer(bits, c0)) continue;
                    double sx = 0, sy = 0, sz = 0, st = 0, zmin = 1e9, zmax = -1e9;
                    int cnt = 0;
                    for (int a = 0; a < S; a++)
                        for (int c = 0; c < S; c++)
                            for (int e = 0; e < S; e++) {
                                if (!an(bits, c0, bit(a, c, e))) continue;
                                double px = x0 + i * d + (a + 0.5) * h, py = y0 + j * d + (c + 0.5) * h, pz = z0 + l * d + (e + 0.5) * h;
                                sx += px; sy += py; sz += pz; st += form.traegt(px, py, pz);
                                zmin = Math.min(zmin, pz - h / 2); zmax = Math.max(zmax, pz + h / 2);
                                cnt++;
                            }
                    double[] r = new double[15];
                    r[13] = i; r[14] = j;
                    r[0] = sx / cnt; r[1] = sy / cnt; r[2] = sz / cnt;
                    r[3] = d * d * d * cnt / (S * S * S);
                    r[4] = st / cnt;
                    r[5] = r[2] - zmin;
                    r[6] = zmax - zmin;
                    // freie Flächen: Teilsäulen, die in dieser Zelle eine Stelle haben und in der Nachbarzelle keine
                    int nxm = i > 0 ? (((i - 1) * ny + j) * nz + l) * W : -1, nxp = i < nx - 1 ? (((i + 1) * ny + j) * nz + l) * W : -1;
                    int nym = j > 0 ? ((i * ny + j - 1) * nz + l) * W : -1, nyp = j < ny - 1 ? ((i * ny + j + 1) * nz + l) * W : -1;
                    int nzm = l > 0 ? ((i * ny + j) * nz + l - 1) * W : -1, nzp = l < nz - 1 ? ((i * ny + j) * nz + l + 1) * W : -1;
                    int fxm = 0, fxp = 0, fym = 0, fyp = 0, fzm = 0, fzp = 0;
                    for (int p = 0; p < S; p++)
                        for (int q = 0; q < S; q++) {
                            boolean sx0 = spalte(bits, c0, 0, p, q), sy0 = spalte(bits, c0, 1, p, q), sz0 = spalte(bits, c0, 2, p, q);
                            if (sx0 && !spalte(bits, nxm, 0, p, q)) fxm++;
                            if (sx0 && !spalte(bits, nxp, 0, p, q)) fxp++;
                            if (sy0 && !spalte(bits, nym, 1, p, q)) fym++;
                            if (sy0 && !spalte(bits, nyp, 1, p, q)) fyp++;
                            if (sz0 && !spalte(bits, nzm, 2, p, q)) fzm++;
                            if (sz0 && !spalte(bits, nzp, 2, p, q)) fzp++;
                        }
                    double fa = h * h;
                    r[7] = fxm * fa; r[8] = fxp * fa; r[9] = fym * fa; r[10] = fyp * fa; r[11] = fzm * fa; r[12] = fzp * fa;
                    out.add(r);
                }
        n = out.size();
        x = new double[n]; y = new double[n]; z = new double[n]; vol = new double[n]; traegt = new double[n];
        unten = new double[n]; hoehe = new double[n]; frei = new double[6 * n];
        for (int p = 0; p < n; p++) {
            double[] r = out.get(p);
            x[p] = r[0]; y[p] = r[1]; z[p] = r[2]; vol[p] = r[3]; traegt[p] = r[4]; unten[p] = r[5]; hoehe[p] = r[6];
            for (int f = 0; f < 6; f++) frei[6 * p + f] = r[7 + f];
        }
        // seit S7b-1: Säulen aus Blöcken von sb × sb Zellen
        int sb = Math.max(1, (int) Math.floor(SAEULE / d + 1e-9));
        java.util.Map<Long, Integer> nr = new java.util.HashMap<>();
        saeule = new int[n];
        for (int p = 0; p < n; p++) {
            double[] r = out.get(p);
            long key = ((long) ((int) r[13] / sb) << 32) | ((int) r[14] / sb & 0xffffffffL);
            Integer c = nr.get(key);
            if (c == null) { c = nr.size(); nr.put(key, c); }
            saeule[p] = c;
        }
        saeulen = nr.size();
        saeuleX = new double[saeulen];
        saeuleY = new double[saeulen];
        double[] sv = new double[saeulen];
        for (int p = 0; p < n; p++) {
            int c = saeule[p];
            saeuleX[c] += vol[p] * x[p];
            saeuleY[c] += vol[p] * y[p];
            sv[c] += vol[p];
        }
        for (int c = 0; c < saeulen; c++) { saeuleX[c] /= sv[c]; saeuleY[c] /= sv[c]; }
    }

    int bit(int a, int c, int e) { return (a * S + c) * S + e; }

    static boolean an(long[] bits, int c0, int b) { return (bits[c0 + (b >>> 6)] >>> (b & 63) & 1) != 0; }

    boolean leer(long[] bits, int c0) {
        for (int i = 0; i < W; i++) if (bits[c0 + i] != 0) return false;
        return true;
    }

    /** Ob die Teilsäule (p, q) längs der Achse ax (0 x, 1 y, 2 z) in der Zelle c0 eine Stelle im Körper hat (c0 &lt; 0: keine Zelle). */
    boolean spalte(long[] bits, int c0, int ax, int p, int q) {
        if (c0 < 0) return false;
        for (int t = 0; t < S; t++) {
            int i = switch (ax) { case 0 -> bit(t, p, q); case 1 -> bit(p, t, q); default -> bit(p, q, t); };
            if (an(bits, c0, i)) return true;
        }
        return false;
    }

    /** Volumen aller Proben (m³), gewichtet mit dem Anteil, der trägt. */
    public double volumenTragend() {
        double v = 0;
        for (int p = 0; p < n; p++) v += vol[p] * traegt[p];
        return v;
    }

    /** Volumen der Proben unter der Höhe zw (aufrecht, m³), mit dem Anteil, der trägt. */
    public double volumenUnter(double zw) {
        double v = 0;
        for (int p = 0; p < n; p++) v += vol[p] * traegt[p] * anteil(zw - (z[p] - unten[p]), hoehe[p]);
        return v;
    }

    static double anteil(double s, double hz) { return s <= 0 ? 0 : s >= hz ? 1 : s / hz; }

    /** Angeströmte Fläche des ganzen Körpers in Richtung −x, −y, −z (m², alle Proben). */
    public double[] flaeche() {
        double[] a = new double[3];
        for (int p = 0; p < n; p++) { a[0] += frei[6 * p]; a[1] += frei[6 * p + 2]; a[2] += frei[6 * p + 4]; }
        return a;
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s: %d Proben zu %.2f m, %.2f m³", form.name(), n, d, volumenTragend());
    }
}
