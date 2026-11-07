package com.dan.lens;

/**
 * Farbe aus dem Spektrum: die Normspektralwerte der CIE 1931 (2°-Beobachter) als Summe stückweiser Gaußkurven nach
 * Wyman, Sloan und Shirley (2013, „Simple Analytic Approximations to the CIE XYZ Color Matching Functions“, mittlere
 * Abweichung unter 1 % des Maximums), dann XYZ → lineares sRGB (IEC 61966-2-1). Und die drei Wellenlängen, mit denen das
 * Objektiv seine Farbfehler rechnet (Schwerpunkte der sRGB-Kanäle [G]).
 */
public final class Spectrum {
    private Spectrum() { }

    /** Wellenlängen (nm) für Rot, Grün, Blau. */
    public static final double[] RGB_NM = {610, 550, 465};

    private static double g(double l, double mu, double s1, double s2) {
        double t = (l - mu) / (l < mu ? s1 : s2);
        return Math.exp(-0.5 * t * t);
    }

    /** x̄, ȳ, z̄ bei λ (nm). */
    public static double[] xyz(double l) {
        double x = 1.056 * g(l, 599.8, 37.9, 31.0) + 0.362 * g(l, 442.0, 16.0, 26.7) - 0.065 * g(l, 501.1, 20.4, 26.2);
        double y = 0.821 * g(l, 568.8, 46.9, 40.5) + 0.286 * g(l, 530.9, 16.3, 31.1);
        double z = 1.217 * g(l, 437.0, 11.8, 36.0) + 0.681 * g(l, 459.0, 26.0, 13.8);
        return new double[]{x, y, z};
    }

    /** Lineares sRGB einer Spektrallinie der Leistung 1 bei λ (nm), ungeklemmt (kann negativ sein). */
    public static double[] rgb(double l) {
        double[] c = xyz(l);
        return new double[]{
                3.2406 * c[0] - 1.5372 * c[1] - 0.4986 * c[2],
                -0.9689 * c[0] + 1.8758 * c[1] + 0.0415 * c[2],
                0.0557 * c[0] - 0.2040 * c[1] + 1.0570 * c[2]};
    }

    /**
     * Gewichte für n Wellenlängen von 400 bis 700 nm: je Kanal so, dass ein Spektrum gleicher Energie Weiß (1, 1, 1)
     * ergibt; negative Anteile auf 0 geklemmt. Ergebnis [n][4]: λ, wR, wG, wB.
     */
    public static double[][] weights(int n) {
        double[][] w = new double[n][4];
        double[] sum = new double[3];
        for (int i = 0; i < n; i++) {
            double l = 400 + 300.0 * (i + 0.5) / n;
            double[] c = rgb(l);
            w[i][0] = l;
            for (int k = 0; k < 3; k++) { w[i][k + 1] = Math.max(0, c[k]); sum[k] += w[i][k + 1]; }
        }
        for (double[] r : w) for (int k = 0; k < 3; k++) r[k + 1] /= Math.max(1e-12, sum[k]);
        return w;
    }
}
