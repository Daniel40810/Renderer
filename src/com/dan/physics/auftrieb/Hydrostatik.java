package com.dan.physics.auftrieb;

import java.util.Locale;

/**
 * Die Werte der Handrechnung aus der Form, fein abgetastet und unabhängig von den Proben (Seegang Werkbuch, S7a): Volumen,
 * Formschwerpunkt, Wasserlinie und ihre Trägheitsmomente bei aufrechter Lage auf dem Tiefgang T (ab der Unterkante).
 * Daraus GM = KB + I_T / V − KG und die Eigenperioden der Prüfsteine.
 */
public final class Hydrostatik {
    public final double tiefgang;
    /** Volumen unter der Wasserlinie (m³, tragend), Formschwerpunkt (x, z). */
    public final double volumen, lcb, kb;
    /** Fläche der Wasserlinie (m²), ihr Schwerpunkt in x, Trägheitsmomente quer (um x) und längs (um y, durch LCF) (m⁴). */
    public final double awl, lcf, it, il;

    /** Tastet die Form mit nx × ny Säulen und nz Stufen bis T ab. */
    public Hydrostatik(Form f, double tiefgang, int nx, int ny, int nz) {
        this.tiefgang = tiefgang;
        double[] k = f.kasten();
        double dx = (k[1] - k[0]) / nx, dy = (k[3] - k[2]) / ny, dz = (tiefgang - k[4]) / nz;
        double v = 0, mx = 0, mz = 0, a = 0, ax = 0;
        for (int i = 0; i < nx; i++) {
            double x = k[0] + (i + 0.5) * dx;
            for (int j = 0; j < ny; j++) {
                double y = k[2] + (j + 0.5) * dy;
                for (int l = 0; l < nz; l++) {
                    double z = k[4] + (l + 0.5) * dz;
                    if (!f.innen(x, y, z)) continue;
                    double dv = dx * dy * dz * f.traegt(x, y, z);
                    v += dv; mx += dv * x; mz += dv * z;
                }
                if (f.innen(x, y, tiefgang) && f.traegt(x, y, tiefgang) >= 1) { a += dx * dy; ax += dx * dy * x; }
            }
        }
        volumen = v;
        lcb = v > 0 ? mx / v : 0;
        kb = v > 0 ? mz / v : 0;
        awl = a;
        lcf = a > 0 ? ax / a : 0;
        double iT = 0, iL = 0;
        for (int i = 0; i < nx; i++) {
            double x = k[0] + (i + 0.5) * dx;
            for (int j = 0; j < ny; j++) {
                double y = k[2] + (j + 0.5) * dy;
                if (f.innen(x, y, tiefgang) && f.traegt(x, y, tiefgang) >= 1) {
                    iT += y * y * dx * dy + dx * dy * dy * dy / 12;
                    iL += (x - lcf) * (x - lcf) * dx * dy + dy * dx * dx * dx / 12;
                }
            }
        }
        it = iT;
        il = iL;
    }

    /** Fein genug für die Prüfsteine: 400 × 160 Säulen, 160 Stufen. */
    public Hydrostatik(Form f, double tiefgang) { this(f, tiefgang, 400, 160, 160); }

    /** Querstabilität GM (m) bei Schwerpunkthöhe kg über der Unterkante. */
    public double gm(double kg) { return kb + it / volumen - kg; }

    /** Längsstabilität GM_L (m). */
    public double gmL(double kg) { return kb + il / volumen - kg; }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "T %.3f m: V %.3f m³, LCB %.3f m, KB %.3f m, A_W %.2f m², BM %.3f m, BM_L %.2f m", tiefgang, volumen, lcb, kb, awl, it / volumen, il / volumen);
    }
}
