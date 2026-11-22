package com.dan.core.szene;

/**
 * Die wirksame Wasserfläche (P7g, F63 A, Werkbuch HalligHooge Teil A, 12.8): was die Neigungen, die ein Bildpunkt nicht
 * auflöst, im Mittel über die sichtbaren Flanken tun. Neigungen nach Beckmann mit der Varianz σ² (Cox und Munk),
 * Schattung des gespiegelten Strahls nach Smith (G1 nach Walter u. a. 2007, Gl. 27) – dieselben Formeln wie
 * {@code render.reference.Facette}. Je Blick (cos μ zur mittleren Normalen) und σ² acht Werte:
 * <ol>
 * <li>die wirksame Spiegelung (Fresnel über die sichtbaren Flanken, nur Strahlen, die frei zum Himmel gehen),</li>
 * <li>der Sinus der mittleren Höhe des Spiegelbilds über der mittleren Ebene,</li>
 * <li>die Breite des Spiegelbilds in der Höhe (Standardabweichung, rad),</li>
 * <li>der Durchlass (1 − Fresnel über die sichtbaren Flanken) für das Licht aus dem Wasser,</li>
 * <li>seit P11d (A26, Werkbuch HalligHooge Datei 60) der Sinus der Höhe des Spiegelbilds an den Quantilen 1/8, 3/8, 5/8,
 * 7/8 seiner Verteilung (gewichtet wie die Spiegelung). Die Verteilung ist an der Kimm abgeschnitten und schief; drei Höhen
 * nach Mittel und Breite (Gauß) gewichten den hellen Himmel nahe der Kimm zu schwach (Spiegelung weit weg 5–16 % zu
 * dunkel), vier Höhen an den Quantilen zu je ¼ treffen sie auf 1–3 %.</li>
 * </ol>
 * Ohne Neigungen ist das Fresnel an der Normalen, der Spiegel und 1 − Fresnel. Die Tafel hat NMU Stufen in μ,
 * μ_i = (i/(NMU−1))² (dicht am flachen Blick), und NS Stufen in σ², σ²_j = S2_MAX (j/(NS−1))² (dicht an der glatten Fläche); gelesen wird bilinear in diesen Lagen,
 * in Java ({@link #lies}) wie in {@code wasser.frag}.
 */
public final class Flaeche {
    public static final int NMU = 32, NS = 12, JE = 8;
    /** Lage der Werte: Spiegelung, Höhe, Breite, Durchlass, erstes der vier Quantile der Höhe (seit P11d). */
    public static final int SPIEGEL = 0, HOEHE = 1, BREITE = 2, DURCH = 3, QUANTIL = 4, QUANTILE = 4;
    /** Größte Varianz der Tafel (Cox und Munk bei 23 m/s) [G]; darüber gilt der Rand. */
    public static final double S2_MAX = 0.12;
    /** Stützstellen der Quadratur je Achse der Neigung und ihre Weite in Standardabweichungen je Achse [G]. */
    static final int Q = 161;
    static final double WEITE = 6.5;

    private Flaeche() { }

    public static double mu(int i) {
        double a = i / (NMU - 1.0);
        return a * a;
    }

    public static double s2(int j) {
        double a = j / (NS - 1.0);
        return S2_MAX * a * a;
    }

    /** Smith G1 für Beckmann (Walter u. a. 2007, Gl. 27) für cos des Winkels zur mittleren Normalen und α = √σ². */
    public static double g1(double cos, double alpha) {
        if (cos <= 0) return 0;
        double t = Math.sqrt(Math.max(0, 1 - cos * cos)) / cos;
        if (t == 0) return 1;
        double a = 1 / (alpha * t);
        return a < 1.6 ? (3.535 * a + 2.181 * a * a) / (1 + 2.276 * a + 2.577 * a * a) : 1;
    }

    /**
     * {Spiegelung, sin der Höhe des Spiegelbilds, Breite (rad), Durchlass, sin der Höhe an den Quantilen 1/8, 3/8, 5/8, 7/8}
     * für den Blick μ, die Varianz s2 und die Brechzahl n.
     */
    public static double[] rechne(double mu, double s2, double n) {
        mu = Math.max(0, Math.min(1, mu));
        double f0 = Wasseroptik.fresnel(mu, n);
        if (s2 <= 0) return new double[] {f0, mu, 0, 1 - f0, mu, mu, mu, mu};
        double alpha = Math.sqrt(s2), so = Math.sqrt(1 - mu * mu), g = WEITE * alpha / Math.sqrt(2), h = 2 * g / Q;
        double sw = 0, sr = 0, se = 0, se2 = 0, st = 0;
        double[] ee = new double[Q * Q], ww = new double[Q * Q];
        int z = 0;
        for (int a = 0; a < Q; a++) {
            double sx = -g + (a + 0.5) * h;
            double zaehler = mu - sx * so;            // (o·m) mal der Länge der Normalen (−sx, 1, −sy)
            if (zaehler <= 0) continue;
            for (int b = 0; b < Q; b++) {
                double sy = -g + (b + 0.5) * h, len = Math.sqrt(1 + sx * sx + sy * sy), om = zaehler / len;
                double w = Math.exp(-(sx * sx + sy * sy) / s2) * zaehler;   // Dichte der Neigung mal sichtbarer Fläche
                double f = Wasseroptik.fresnel(om, n), iy = 2 * om / len - mu;
                double wr = iy > 0 ? w * f * g1(iy, alpha) : 0, e = Math.asin(Math.max(-1, Math.min(1, iy)));
                sw += w;
                sr += wr;
                se += wr * e;
                se2 += wr * e * e;
                st += w * (1 - f);
                if (wr > 0) {
                    ee[z] = e;
                    ww[z++] = wr;
                }
            }
        }
        if (sw <= 0 || sr <= 0) return new double[] {f0, mu, 0, 1 - f0, mu, mu, mu, mu};
        double em = se / sr;
        double[] q = quantile(ee, ww, z, sr);
        return new double[] {sr / sw, Math.sin(em), Math.sqrt(Math.max(0, se2 / sr - em * em)), st / sw, Math.sin(q[0]), Math.sin(q[1]), Math.sin(q[2]), Math.sin(q[3])};
    }

    /** Die Höhen an den Quantilen (k + ½)/4 der Verteilung (Gewichte w, Summe s), linear zwischen den Stützstellen. */
    static double[] quantile(double[] e, double[] w, int n, double s) {
        Integer[] o = new Integer[n];
        for (int i = 0; i < n; i++) o[i] = i;
        java.util.Arrays.sort(o, (x, y) -> Double.compare(e[x], e[y]));
        double[] q = new double[QUANTILE];
        double acc = 0;
        int k = 0;
        for (int i = 0; i < n && k < QUANTILE; i++) {
            double w0 = w[o[i]], ziel = (k + 0.5) / QUANTILE * s;
            while (k < QUANTILE && acc + w0 >= ziel) {
                double f = (ziel - acc) / w0;
                double lo = i > 0 ? 0.5 * (e[o[i - 1]] + e[o[i]]) : e[o[i]], hi = i < n - 1 ? 0.5 * (e[o[i]] + e[o[i + 1]]) : e[o[i]];
                q[k++] = lo + f * (hi - lo);
                ziel = (k + 0.5) / QUANTILE * s;
            }
            acc += w0;
        }
        while (k < QUANTILE) q[k++] = e[o[n - 1]];
        return q;
    }

    private static float[] tafel;
    private static double tafelN = Double.NaN;

    /** Die Tafel [μ][σ²][4] für die Brechzahl n (einmal gerechnet). */
    public static synchronized float[] tafel(double n) {
        if (tafel != null && tafelN == n) return tafel;
        float[] t = new float[NMU * NS * JE];
        java.util.stream.IntStream.range(0, NMU * NS).parallel().forEach(q -> {
            double[] v = rechne(mu(q / NS), s2(q % NS), n);
            for (int k = 0; k < JE; k++) t[q * JE + k] = (float) v[k];
        });
        tafel = t;
        tafelN = n;
        return t;
    }

    /** Liest die Tafel bilinear wie {@code wasser.frag}: Lage (NMU−1) √μ und (NS−1) √(σ²/S2_MAX). */
    public static double[] lies(float[] t, double mu, double s2) {
        double a = (NMU - 1) * Math.sqrt(Math.max(0, Math.min(1, mu))), b = (NS - 1) * Math.sqrt(Math.max(0, Math.min(1, s2 / S2_MAX)));
        int i = Math.min(NMU - 2, (int) a), j = Math.min(NS - 2, (int) b);
        double fa = a - i, fb = b - j;
        double[] v = new double[JE];
        for (int k = 0; k < JE; k++) {
            double q0 = t[(i * NS + j) * JE + k] * (1 - fb) + t[(i * NS + j + 1) * JE + k] * fb;
            double q1 = t[((i + 1) * NS + j) * JE + k] * (1 - fb) + t[((i + 1) * NS + j + 1) * JE + k] * fb;
            v[k] = q0 * (1 - fa) + q1 * fa;
        }
        return v;
    }
}
