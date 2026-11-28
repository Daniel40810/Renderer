package com.dan.core.szene;

/**
 * Lineare Dispersion der Schwerewellen mit Strömung (Seegang Werkbuch, S3; SF3 A): eine Kopie der Formeln aus
 * {@code physics.waves.Waves} und {@code physics.waves.nah.Nahfeld} im Kern, damit die Oberfläche ({@link Oberflaeche},
 * {@link Phasenfeld}) und ihre Shader sie ohne die Physik kennen. Rechnet bitgleich wie die Vorlagen.
 * <pre>
 * σ² = g k tanh(k h),   ω = σ + k U_θ,   c_g = n σ / k,  n = ½ (1 + 2kh / sinh 2kh)
 * </pre>
 */
public final class Dispersion {
    public static final double G = 9.81;

    private Dispersion() { }

    /** Wellenzahl (rad/m) zur relativen Kreisfrequenz σ in der Tiefe d (Newton auf y = kd), wie {@code Waves.wellenzahl}. */
    public static double wellenzahl(double sigma, double d) {
        double k0 = sigma * sigma / G, x = k0 * d;
        if (x > 30) return k0;
        double y = x < 1 ? Math.sqrt(x) : x;
        for (int it = 0; it < 50; it++) {
            double t = Math.tanh(y), fy = y * t - x, dy = t + y * (1 - t * t), s = fy / dy;
            y -= s;
            if (Math.abs(s) < 1e-14 * y) break;
        }
        return y / d;
    }

    /** σ(k, h). */
    public static double sigma(double k, double h) { return Math.sqrt(G * k * Math.tanh(k * h)); }

    /** Gruppengeschwindigkeit relativ zum Wasser (m/s), wie {@code Waves.gruppe}. */
    public static double gruppe(double sigma, double k, double d) {
        double kd = k * d;
        double n = kd > 30 ? 0.5 : 0.5 * (1 + 2 * kd / Math.sinh(2 * kd));
        return n * sigma / k;
    }

    /** n = c_g / c. */
    public static double n(double k, double d) {
        double kd = k * d;
        return kd > 30 ? 0.5 : 0.5 * (1 + 2 * kd / Math.sinh(2 * kd));
    }

    /**
     * Wellenzahl zur absoluten Kreisfrequenz ω in der Tiefe h bei der Strömung U_θ in Laufrichtung (m/s), der Zweig mit
     * c_g + U_θ > 0; NaN, wenn keine Lösung besteht (blockiert). Wie {@code Nahfeld.wellenzahl}.
     */
    public static double wellenzahl(double omega, double h, double ut) {
        if (ut == 0) return wellenzahl(omega, h);
        if (ut > 0) {
            double lo = 0, hi = wellenzahl(omega, h);
            for (int it = 0; it < 200; it++) {
                double m = 0.5 * (lo + hi);
                if (sigma(m, h) + m * ut > omega) hi = m; else lo = m;
                if (hi - lo < 1e-13 * hi) break;
            }
            return 0.5 * (lo + hi);
        }
        double kmax = wellenzahl(omega, h) * 4;
        for (int t = 0; t < 60 && gruppe(sigma(kmax, h), kmax, h) + ut >= 0 && sigma(kmax, h) + kmax * ut < omega; t++) kmax *= 2;
        double kb = kmax;
        if (gruppe(sigma(kmax, h), kmax, h) + ut < 0) {
            double lo = 0, hi = kmax;
            for (int it = 0; it < 200; it++) {
                double m = 0.5 * (lo + hi);
                if (gruppe(sigma(m, h), m, h) + ut > 0) lo = m; else hi = m;
                if (hi - lo < 1e-13 * hi) break;
            }
            kb = lo;
        }
        if (sigma(kb, h) + kb * ut < omega) return Double.NaN;
        double lo = 0, hi = kb;
        for (int it = 0; it < 200; it++) {
            double m = 0.5 * (lo + hi);
            if (sigma(m, h) + m * ut > omega) hi = m; else lo = m;
            if (hi - lo < 1e-13 * hi) break;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Wellenzahl am Blockierpunkt (c_g + U_θ = 0) für Gegenstrom U_θ &lt; 0: dort endet der Zweig; das {@link Phasenfeld}
     * nimmt sie für blockierte Stützstellen, deren Amplitude ohnehin null ist [G].
     */
    public static double blockiert(double omega, double h, double ut) {
        double hi = Math.max(1e-6, wellenzahl(omega, h)) * 4;
        for (int t = 0; t < 60 && gruppe(sigma(hi, h), hi, h) + ut > 0; t++) hi *= 2;
        double lo = 0;
        for (int it = 0; it < 200; it++) {
            double m = 0.5 * (lo + hi);
            if (gruppe(sigma(m, h), m, h) + ut > 0) lo = m; else hi = m;
            if (hi - lo < 1e-13 * hi) break;
        }
        return lo;
    }
}
