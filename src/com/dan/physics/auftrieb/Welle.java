package com.dan.physics.auftrieb;

import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * Lineare Wellen über ebenem Grund (Airy, endliche Tiefe), als {@link Wasser} für Prüfungen und als Bezug in double
 * (Seegang Werkbuch, S7a). Jede Teilwelle hat Amplitude a, Kreisfrequenz ω, Wellenzahl k aus ω² = g k tanh(k h),
 * Laufrichtung und Phase:
 * <pre>
 *   ψ(z) = Σ a · cosh(k (z + h)) / cosh(k h) · cos φ,          φ = k (x sin θ + y cos θ) − ω t + φ₀
 *   u    = Σ a ω · cosh(k (z + h)) / sinh(k h) · cos φ · (sin θ, cos θ)
 *   w    = Σ a ω · sinh(k (z + h)) / sinh(k h) · sin φ
 * </pre>
 * θ ist die Laufrichtung in Grad von Nord im Uhrzeigersinn (wohin die Welle läuft). Jede Teilwelle gehört zu einem
 * Frequenzband (wie im Nahfeld), damit {@link Druckprofil} gegen sie geprüft werden kann.
 * <p>
 * Pegel, Strömung, Wind und Grund sind einfach gehalten: Pegel als Funktion der Zeit, Grund eben (Tiefe h für die
 * Dispersion aus Pegel und Grund zur Zeit 0), Strömung und Wind fest.
 */
public final class Welle implements Wasser {
    public final int n;
    public final double h;
    final double[] a, om, k, kx, ky, ph;
    final int[] band;
    /** Pegel über der Zeit (m); Vorgabe fest. */
    public DoubleUnaryOperator pegel;
    /** Höhe des Grunds (m). */
    public double grund;
    /** Strömung und Wind {Ost, Nord} (m/s). */
    public double stromU, stromV, windU, windV;
    /** Anstieg der Welle über so viele Sekunden ab t = 0 (Rampe sin²; 0: sofort voll) [G]. */
    public double anlauf;

    private Welle(double pegel0, double h, double[] a, double[] om, double[] dirDeg, double[] ph, int[] band) {
        this.n = a.length;
        this.h = h;
        this.a = a;
        this.om = om;
        this.ph = ph;
        this.band = band;
        this.k = new double[n];
        this.kx = new double[n];
        this.ky = new double[n];
        for (int i = 0; i < n; i++) {
            k[i] = wellenzahl(om[i], h);
            double th = Math.toRadians(dirDeg[i]);
            kx[i] = k[i] * Math.sin(th);
            ky[i] = k[i] * Math.cos(th);
        }
        this.pegel = t -> pegel0;
        this.grund = pegel0 - h;
    }

    /** Eine einzelne Welle: Amplitude a (m), Periode T (s), Laufrichtung (Grad von Nord), Tiefe h (m), Pegel 0. */
    public static Welle regelmaessig(double a, double periode, double laufrichtung, double h) {
        return new Welle(0, h, new double[] {a}, new double[] {2 * Math.PI / periode}, new double[] {laufrichtung}, new double[] {0}, new int[] {0});
    }

    /** Ruhiges Wasser der Tiefe h über ebenem Grund (keine Teilwelle), Pegel 0. */
    public static Welle ruhig(double h) {
        return new Welle(0, h, new double[0], new double[0], new double[0], new double[0], new int[0]);
    }

    /**
     * Ein Seegang nach JONSWAP (γ 3,3) mit cos²ˢ der Richtung: signifikante Höhe hs (m), Spitzenperiode tp (s),
     * mittlere Laufrichtung (Grad von Nord), Breite s, Tiefe h. Frequenzen in {@code baender} logarithmischen Bändern
     * von fMin bis fMax (wie das Nahfeld: 0,08 … 0,6 Hz, 8 Bänder), je Band nf Teilwellen mit zufälliger Frequenz im Band,
     * nd Richtungen, Phasen zufällig aus seed. Die Varianz ist genau hs² / 16.
     */
    public static Welle jonswap(double hs, double tp, double laufrichtung, double s, double h, int baender, int nf, int nd, double fMin, double fMax, long seed) {
        Random r = new Random(seed);
        int m = baender * nf * nd;
        double[] a = new double[m], om = new double[m], dir = new double[m], ph = new double[m];
        int[] band = new int[m];
        double fp = 1 / tp, q = Math.pow(fMax / fMin, 1.0 / baender);
        // Richtungsverteilung cos^2s((θ−θ0)/2) auf nd Stützen über ±90°
        double[] dw = new double[nd], dd = new double[nd];
        double sw = 0;
        for (int j = 0; j < nd; j++) {
            double d = nd == 1 ? 0 : -90 + 180.0 * (j + 0.5) / nd;
            dd[j] = d;
            dw[j] = Math.pow(Math.cos(Math.toRadians(d) / 2), 2 * s);
            sw += dw[j];
        }
        int i = 0;
        double var = 0;
        for (int b = 0; b < baender; b++) {
            double f0 = fMin * Math.pow(q, b), f1 = f0 * q;
            for (int l = 0; l < nf; l++) {
                double u0 = f0 + (f1 - f0) * l / nf, u1 = f0 + (f1 - f0) * (l + 1) / nf;
                double f = u0 + (u1 - u0) * r.nextDouble();
                double e = jonswapS(f, fp) * (u1 - u0);
                for (int j = 0; j < nd; j++) {
                    double ej = e * dw[j] / sw;
                    a[i] = Math.sqrt(2 * ej);
                    om[i] = 2 * Math.PI * f;
                    dir[i] = laufrichtung + dd[j];
                    ph[i] = 2 * Math.PI * r.nextDouble();
                    band[i] = b;
                    var += 0.5 * a[i] * a[i];
                    i++;
                }
            }
        }
        double sc = Math.sqrt(hs * hs / 16 / Math.max(1e-30, var));
        for (int j = 0; j < m; j++) a[j] *= sc;
        return new Welle(0, h, a, om, dir, ph, band);
    }

    /** Form des JONSWAP-Spektrums (ohne Maßstab), γ 3,3. */
    static double jonswapS(double f, double fp) {
        double sig = f <= fp ? 0.07 : 0.09;
        double r = Math.exp(-(f - fp) * (f - fp) / (2 * sig * sig * fp * fp));
        return Math.pow(f, -5) * Math.exp(-1.25 * Math.pow(fp / f, 4)) * Math.pow(3.3, r);
    }

    /** Wellenzahl k zu ω bei der Tiefe h (Newton auf ω² = g k tanh(k h)). */
    public static double wellenzahl(double om, double h) {
        if (om <= 0) return 0;
        double k0 = om * om / G;
        double kk = h > 0 ? Math.max(k0, om / Math.sqrt(G * h)) : k0;
        for (int it = 0; it < 60; it++) {
            double th = Math.tanh(kk * h), f = G * kk * th - om * om, df = G * th + G * kk * h * (1 - th * th);
            double d = f / df;
            kk -= d;
            if (Math.abs(d) < 1e-14 * kk) break;
        }
        return kk;
    }

    /** cosh(k (z + h)) / cosh(k h) für −h ≤ z ≤ 0, ohne Überlauf. */
    public static double abklingen(double kk, double z, double h) {
        double e1 = Math.exp(-2 * kk * (z + h)), e2 = Math.exp(-2 * kk * h);
        return Math.exp(kk * z) * (1 + e1) / (1 + e2);
    }

    /** cosh(k (z + h)) / sinh(k h) und sinh(k (z + h)) / sinh(k h) für −h ≤ z ≤ 0. */
    static void orbital(double kk, double z, double h, double[] o) {
        double e1 = Math.exp(-2 * kk * (z + h)), e2 = Math.exp(-2 * kk * h), ez = Math.exp(kk * z);
        o[0] = ez * (1 + e1) / (1 - e2);
        o[1] = ez * (1 - e1) / (1 - e2);
    }

    private double rampe(double t) {
        if (anlauf <= 0 || t >= anlauf) return 1;
        if (t <= 0) return 0;
        double s = Math.sin(0.5 * Math.PI * t / anlauf);
        return s * s;
    }

    @Override public double pegel(double x, double y, double t) { return pegel.applyAsDouble(t); }

    @Override public void welle(double x, double y, double z, double t, double[] out) {
        double zz = Math.max(-h, Math.min(0, z)), ra = rampe(t);
        double p = 0, px = 0, py = 0, u = 0, v = 0, w = 0;
        double[] o = new double[2];
        for (int i = 0; i < n; i++) {
            double phi = kx[i] * x + ky[i] * y - om[i] * t + ph[i], c = Math.cos(phi), s = Math.sin(phi);
            double at = abklingen(k[i], zz, h), ai = a[i] * ra;
            p += ai * at * c;
            px -= ai * at * kx[i] * s;
            py -= ai * at * ky[i] * s;
            orbital(k[i], zz, h, o);
            double uh = ai * om[i] * o[0] * c / Math.max(1e-30, k[i]);
            u += uh * kx[i];
            v += uh * ky[i];
            w += ai * om[i] * o[1] * s;
        }
        out[0] = p; out[1] = px; out[2] = py; out[3] = u; out[4] = v; out[5] = w;
    }

    @Override public void strom(double x, double y, double t, double[] out) { out[0] = stromU; out[1] = stromV; }

    @Override public void wind(double x, double y, double t, double[] out) { out[0] = windU; out[1] = windV; }

    @Override public double grund(double x, double y) { return grund; }

    /** Varianz der Fläche m₀ (m²). */
    public double m0() {
        double v = 0;
        for (int i = 0; i < n; i++) v += 0.5 * a[i] * a[i];
        return v;
    }

    /** Zahl der Bänder (größter Index + 1). */
    public int baender() {
        int m = 0;
        for (int i = 0; i < n; i++) m = Math.max(m, band[i] + 1);
        return m;
    }

    /**
     * Was die Karte je Punkt und Band liefert (S7b): an der Oberfläche S0_b = Σ a cos φ und S1_b = Σ a (k − k_b) cos φ
     * über die Teilwellen des Bands, mit k_b der mittleren Wellenzahl des Bands nach der Energie. Daraus rechnet
     * {@link Druckprofil} ψ in jeder Tiefe.
     */
    public void baender(double x, double y, double t, double[] s0, double[] s1, double[] kb) {
        int nb = baender();
        double[] e = new double[nb], ek = new double[nb];
        for (int i = 0; i < n; i++) { e[band[i]] += a[i] * a[i]; ek[band[i]] += a[i] * a[i] * k[i]; }
        for (int b = 0; b < nb; b++) { kb[b] = e[b] > 0 ? ek[b] / e[b] : 0; s0[b] = 0; s1[b] = 0; }
        double ra = rampe(t);
        for (int i = 0; i < n; i++) {
            double c = Math.cos(kx[i] * x + ky[i] * y - om[i] * t + ph[i]) * a[i] * ra;
            s0[band[i]] += c;
            s1[band[i]] += c * (k[i] - kb[band[i]]);
        }
    }
}
