package com.dan.aurora;

/**
 * Die obere Atmosphäre von 80 bis 400 km bei Nacht: Massendichte, Teilchendichten von N₂, O₂ und O und die Temperatur,
 * tabelliert nach der US-Standardatmosphäre 1976 und MSIS (oberhalb 150 km auf die kühlere Nacht gedrückt), dazwischen
 * logarithmisch interpoliert. Aus der Dichte folgt die Massensäule über jeder Höhe, an der die Elektronen ihre Energie
 * abgeben.
 */
public final class Atmosphaere {
    private Atmosphaere() { }

    /** Stützhöhen in km. */
    static final double[] H = {80, 85, 90, 95, 100, 105, 110, 115, 120, 130, 140, 150, 160, 180, 200, 225, 250, 275, 300, 350, 400};
    /** Massendichte in g/cm³. */
    static final double[] RHO = {1.85e-8, 8.2e-9, 3.4e-9, 1.4e-9, 5.6e-10, 2.3e-10, 9.7e-11, 4.5e-11, 2.2e-11, 8.2e-12, 3.8e-12,
            2.1e-12, 1.2e-12, 5.0e-13, 2.0e-13, 9.0e-14, 4.6e-14, 2.5e-14, 1.5e-14, 5.5e-15, 2.2e-15};
    /** Teilchendichten in 1/cm³. */
    static final double[] N2 = {3.0e14, 1.3e14, 5.5e13, 2.3e13, 9.2e12, 3.8e12, 1.6e12, 7.5e11, 3.7e11, 1.4e11, 6.5e10,
            3.1e10, 1.7e10, 6.5e9, 3.0e9, 1.2e9, 5.5e8, 2.5e8, 1.1e8, 2.8e7, 9.0e6};
    static final double[] O2 = {8.0e13, 3.5e13, 1.5e13, 6.0e12, 2.2e12, 8.0e11, 3.0e11, 1.3e11, 5.5e10, 1.8e10, 7.5e9,
            2.8e9, 1.4e9, 5.0e8, 2.0e8, 7.0e7, 2.8e7, 1.1e7, 4.5e6, 8.0e5, 2.5e5};
    static final double[] O = {1.0e11, 2.0e11, 3.5e11, 4.5e11, 4.3e11, 3.5e11, 2.5e11, 1.6e11, 1.0e11, 5.5e10, 3.3e10,
            1.8e10, 1.2e10, 6.5e9, 4.0e9, 2.3e9, 1.5e9, 9.0e8, 5.5e8, 2.2e8, 1.1e8};
    /** Temperatur in K (Nacht, mittlere Sonnenaktivität). */
    static final double[] T = {200, 190, 185, 185, 195, 215, 250, 300, 360, 470, 560, 630, 690, 770, 820, 860, 880, 895, 905, 915, 920};
    /** Massensäule über jeder Stützhöhe in g/cm². */
    static final double[] SAEULE = new double[H.length];

    static {
        // Schwant: oberhalb 400 km fällt die Dichte weiter exponentiell mit der Skalenhöhe der letzten beiden Stützen
        int n = H.length - 1;
        double hs = (H[n] - H[n - 1]) / Math.log(RHO[n - 1] / RHO[n]);
        SAEULE[n] = RHO[n] * hs * 1e5;
        for (int i = n - 1; i >= 0; i--) {
            double dh = (H[i + 1] - H[i]) * 1e5;
            SAEULE[i] = SAEULE[i + 1] + (RHO[i] - RHO[i + 1]) * dh / Math.log(RHO[i] / RHO[i + 1]);
        }
    }

    /** Massendichte in g/cm³. */
    public static double dichte(double km) { return log(km, RHO); }

    /** N₂ in 1/cm³. */
    public static double n2(double km) { return log(km, N2); }

    /** O₂ in 1/cm³. */
    public static double o2(double km) { return log(km, O2); }

    /** Atomarer Sauerstoff in 1/cm³. */
    public static double o(double km) { return log(km, O); }

    /** Temperatur in K. */
    public static double temperatur(double km) { return linear(km, T); }

    /** Massensäule über der Höhe in g/cm². */
    public static double saeule(double km) { return log(km, SAEULE); }

    /** Logarithmische Interpolation in der Tabelle; außerhalb exponentiell fortgesetzt. */
    static double log(double km, double[] w) {
        int n = H.length - 1;
        if (km <= H[0]) return w[0] * Math.exp((H[0] - km) / ((H[1] - H[0]) / Math.log(w[0] / w[1])));
        if (km >= H[n]) return w[n] * Math.exp(-(km - H[n]) / ((H[n] - H[n - 1]) / Math.log(w[n - 1] / w[n])));
        int i = index(km);
        double f = (km - H[i]) / (H[i + 1] - H[i]);
        return Math.exp(Math.log(w[i]) + f * (Math.log(w[i + 1]) - Math.log(w[i])));
    }

    static double linear(double km, double[] w) {
        int n = H.length - 1;
        if (km <= H[0]) return w[0];
        if (km >= H[n]) return w[n];
        int i = index(km);
        double f = (km - H[i]) / (H[i + 1] - H[i]);
        return w[i] + f * (w[i + 1] - w[i]);
    }

    static int index(double km) {
        int lo = 0, hi = H.length - 2;
        while (lo < hi) {
            int m = (lo + hi + 1) >>> 1;
            if (H[m] <= km) lo = m;
            else hi = m - 1;
        }
        return lo;
    }
}
