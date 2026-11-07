package com.dan.precip;

/**
 * Licht in einem Regentropfen nach der Strahlenoptik einer Wasserkugel: Jeder Strahl, der den Tropfen im Abstand b
 * (0..1 vom Halbmesser) trifft, wird an der Vorderseite gespiegelt (Ordnung 0) oder gebrochen, läuft durch, wird innen
 * 0, 1 oder 2 Mal gespiegelt und tritt wieder aus (Ordnung 1, 2, 3). Fresnel für beide Schwingungsrichtungen, Snellius,
 * die Brechzahl von Wasser je Wellenlänge. Über alle b und das sichtbare Spektrum (CIE 1931, linear sRGB) ergibt das die
 * Phasenfunktion p(θ) je Raumwinkel in RGB: der Glanz nach vorn (Gegenlicht), der Hauptregenbogen bei 138° Streuwinkel
 * (42° um den Gegenpunkt der Sonne), Alexanders dunkles Band, der Nebenbogen bei 129° (51°) mit umgekehrten Farben.
 * <p>
 * Ohne Beugung: Die gleich große Menge Licht, die ein Tropfen in einen schmalen Kegel nach vorn beugt (das
 * Extinktionsparadoxon), gilt als nicht gestreut. Die Überzähligen Bögen kleiner Tropfen (Airy) kommen mit
 * {@code com.dan.rainbow} (W7c). Dieselbe Tabelle nimmt der Shader als 1D-Textur.
 */
public final class DropOptics {

    /** Winkelschritt der Tabelle (Grad) und Zahl der Stufen von 0° bis 180°. */
    public static final double STEP = 0.25;
    public static final int BINS = (int) Math.round(180 / STEP);
    /** Wellenlängen (nm) von 380 bis 730 in 10er-Schritten. */
    static final int L0 = 380, L1 = 730, LSTEP = 10;

    private final float[] rgb = new float[BINS * 3];
    private final double[] total = new double[4];

    /** Die Tabelle für Sonnenlicht (Scheibe 0,53°) mit 4 000 Strahlen je Wellenlänge. */
    public DropOptics() { this(0.53, 4000); }

    /**
     * @param sunDeg Durchmesser der Lichtquelle (Grad), über den die Kaustik der Bögen geglättet wird
     * @param rays   Strahlen je Wellenlänge (gleicher Fluss je Strahl)
     */
    public DropOptics(double sunDeg, int rays) {
        int nl = (L1 - L0) / LSTEP + 1;
        double[][] w = new double[nl][3];
        double[] norm = new double[3];
        for (int i = 0; i < nl; i++) {
            double[] c = cieToSrgb(L0 + i * LSTEP);
            for (int k = 0; k < 3; k++) { w[i][k] = c[k]; norm[k] += c[k]; }
        }
        double[][] acc = new double[BINS][3];
        for (int il = 0; il < nl; il++) {
            double lam = L0 + il * LSTEP, n = waterIndex(lam);
            double[] hist = new double[BINS];
            for (int r = 0; r < rays; r++) {
                double b = Math.sqrt((r + 0.5) / rays);       // gleicher Fluss je Strahl
                double ti = Math.asin(b), tt = Math.asin(b / n);
                double ci = Math.cos(ti), ct = Math.cos(tt);
                double rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
                double Rs = rs * rs, Rp = rp * rp, Ts = 1 - Rs, Tp = 1 - Rp;
                for (int order = 0; order <= 3; order++) {
                    double inten = order == 0 ? 0.5 * (Rs + Rp)
                            : 0.5 * (Ts * Ts * Math.pow(Rs, order - 1) + Tp * Tp * Math.pow(Rp, order - 1));
                    double dev = order == 0 ? Math.PI - 2 * ti : 2 * (ti - tt) + (order - 1) * (Math.PI - 2 * tt);
                    double th = Math.acos(Math.cos(dev));        // Streuwinkel 0..π
                    int bin = Math.min(BINS - 1, (int) (Math.toDegrees(th) / STEP));
                    hist[bin] += inten / rays;
                    if (il == nl / 2) total[order] += inten / rays;
                }
            }
            // je Raumwinkel: Anteil der Stufe / 2π (cos θa − cos θb)
            for (int bin = 0; bin < BINS; bin++) {
                double a = Math.toRadians(bin * STEP), bb = Math.toRadians((bin + 1) * STEP);
                double sr = 2 * Math.PI * (Math.cos(a) - Math.cos(bb));
                double p = hist[bin] / sr;
                for (int k = 0; k < 3; k++) acc[bin][k] += p * w[il][k];
            }
        }
        // über die Scheibe der Quelle glätten (Kaustik der Bögen), dann je Kanal auf Weiß bezogen
        int half = Math.max(0, (int) Math.round(sunDeg / 2 / STEP));
        for (int bin = 0; bin < BINS; bin++)
            for (int k = 0; k < 3; k++) {
                double s = 0, ws = 0;
                for (int j = -half; j <= half; j++) {
                    int q = bin + j;
                    if (q < 0 || q >= BINS) continue;
                    double wj = Math.sqrt(Math.max(0, 1 - (j / (half + 0.5)) * (j / (half + 0.5))));   // Scheibe
                    s += acc[q][k] * wj;
                    ws += wj;
                }
                rgb[bin * 3 + k] = (float) Math.max(0, s / ws / norm[k]);
            }
    }

    /** Phasenfunktion (1/sr) bei θ Grad, Kanal c (0 R, 1 G, 2 B), linear zwischen den Stufen. */
    public double phase(double thetaDeg, int c) {
        double x = Math.max(0, Math.min(180, thetaDeg)) / STEP - 0.5;
        int i = (int) Math.floor(x);
        double t = x - i;
        int a = Math.max(0, Math.min(BINS - 1, i)), b = Math.max(0, Math.min(BINS - 1, i + 1));
        return rgb[a * 3 + c] * (1 - t) + rgb[b * 3 + c] * t;
    }

    /** Die Tabelle: BINS Stufen zu je R, G, B (1/sr), Mitte der Stufe i bei (i + 0,5) · STEP Grad. */
    public float[] table() { return rgb.clone(); }

    /** Anteil des Lichts (bei 550 nm) in Ordnung 0 (Spiegelung), 1 (durch), 2 (Hauptbogen), 3 (Nebenbogen). */
    public double fraction(int order) { return total[order]; }

    // ------------------------------------------------------------------ Optik einer Kugel

    /**
     * Brechzahl von reinem Wasser bei λ nm und 20 °C nach Quan und Fry (1995):
     * n = 1,31405 − 2,02·10⁻⁶ T² + (15,868 − 0,00423 T)/λ − 4382/λ² + 1,1455·10⁶/λ³ (Salzgehalt 0) –
     * 1,3432 bei 400 nm, 1,3330 bei 589 nm, 1,3302 bei 700 nm.
     */
    public static double waterIndex(double lam) {
        double t = 20;
        return 1.31405 - 2.02e-6 * t * t + (15.868 - 0.00423 * t) / lam - 4382 / (lam * lam) + 1.1455e6 / (lam * lam * lam);
    }

    /**
     * Winkel (Grad) des Bogens der Ordnung k (2 Hauptbogen, 3 Nebenbogen) um den Gegenpunkt der Sonne aus der
     * kleinsten Ablenkung: cos² i = (n² − 1) / (m² − 1) mit m = k − 1 inneren Spiegelungen plus 1.
     */
    public static double bowRadius(double n, int k) {
        int m = k;   // m = Zahl der inneren Spiegelungen + 1
        double ci = Math.sqrt((n * n - 1) / (m * m - 1)), i = Math.acos(ci), r = Math.asin(Math.sin(i) / n);
        double dev = 2 * (i - r) + (k - 1) * (Math.PI - 2 * r);
        double th = Math.acos(Math.cos(dev));
        return 180 - Math.toDegrees(th);
    }

    /**
     * Für das Weitwinkelbild im Tropfen: Ablenkung (Grad) des durchgehenden Strahls (Ordnung 1) und sein Gewicht
     * (Fluss mal Durchlass) in rings gleich breiten Ringen von b. Ein Strahl, der den Tropfen oben trifft, verlässt ihn
     * nach unten: Der Tropfen zeigt die Welt hinter sich auf dem Kopf, bis 82,8° vom Blick weg. Ergebnis {Winkel…,
     * Gewicht…}; die Gewichte summieren sich zum Durchlass (rund 0,9).
     */
    public static double[][] refractionRings(int rings, double n) {
        double[] ang = new double[rings], wt = new double[rings];
        int sub = 64;
        for (int r = 0; r < rings; r++) {
            double sa = 0, sw = 0;
            for (int s = 0; s < sub; s++) {
                double u = (r + (s + 0.5) / sub) / rings, b = Math.sqrt(u);
                double ti = Math.asin(b), tt = Math.asin(b / n), ci = Math.cos(ti), ct = Math.cos(tt);
                double rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
                double T = 0.5 * ((1 - rs * rs) * (1 - rs * rs) + (1 - rp * rp) * (1 - rp * rp));
                sa += Math.toDegrees(2 * (ti - tt)) * T;
                sw += T;
            }
            ang[r] = sa / Math.max(1e-12, sw);
            wt[r] = sw / sub / rings;
        }
        return new double[][]{ang, wt};
    }

    /** Spiegelung der Vorderseite über den ganzen Querschnitt gemittelt (rund 0,066 für Wasser). */
    public static double reflectance(double n) {
        double s = 0;
        int rays = 2000;
        for (int i = 0; i < rays; i++) {
            double b = Math.sqrt((i + 0.5) / rays), ti = Math.asin(b), tt = Math.asin(b / n), ci = Math.cos(ti), ct = Math.cos(tt);
            double rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
            s += 0.5 * (rs * rs + rp * rp);
        }
        return s / rays;
    }

    // ------------------------------------------------------------------ Farbe

    /** CIE 1931 (Wyman, Sloan, Shirley 2013) für λ nm, nach linear sRGB (D65-Matrix). */
    static double[] cieToSrgb(double lam) {
        double x = 1.056 * g(lam, 599.8, 37.9, 31.0) + 0.362 * g(lam, 442.0, 16.0, 26.7) - 0.065 * g(lam, 501.1, 20.4, 26.2);
        double y = 0.821 * g(lam, 568.8, 46.9, 40.5) + 0.286 * g(lam, 530.9, 16.3, 31.1);
        double z = 1.217 * g(lam, 437.0, 11.8, 36.0) + 0.681 * g(lam, 459.0, 26.0, 13.8);
        return new double[]{3.2406 * x - 1.5372 * y - 0.4986 * z, -0.9689 * x + 1.8758 * y + 0.0415 * z, 0.0557 * x - 0.2040 * y + 1.0570 * z};
    }

    private static double g(double l, double mu, double s1, double s2) {
        double t = (l - mu) / (l < mu ? s1 : s2);
        return Math.exp(-0.5 * t * t);
    }
}
