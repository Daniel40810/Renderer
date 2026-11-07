package com.dan.film;

/**
 * Eine Schwärzungskurve (Hurter und Driffield 1890): Dichte D über dem Logarithmus der Belichtung x = log₁₀ H. Durchhang
 * (Fuß), gerades Stück mit der Steigung γ und Schulter, glatt verbunden:
 * <pre>
 *   D(x) = D_min + γ · ( s_t · ln(1 + e^((x − x_t)/s_t)) − s_s · ln(1 + e^((x − x_s)/s_s)) )
 * </pre>
 * Weit unter x_t bleibt D_min (Träger und Schleier, beim Negativ mit der orangen Maske), dazwischen steigt D mit γ, weit
 * über x_s ist D_max = D_min + γ · (x_s − x_t) erreicht. Das gerade Stück ist der Belichtungsspielraum: x_s − x_t in
 * Zehnerlogarithmen, durch 0,301 in Blenden. x = 0 ist eine Graukarte von 18 %.
 */
public record Curve(double dmin, double gamma, double xt, double st, double xs, double ss) {

    static double softplus(double z) { return z > 30 ? z : z < -30 ? Math.exp(z) : Math.log1p(Math.exp(z)); }

    static double sigmoid(double z) { return 1 / (1 + Math.exp(-z)); }

    public double density(double x) { return dmin + gamma * (st * softplus((x - xt) / st) - ss * softplus((x - xs) / ss)); }

    /** Steigung dD/dx. */
    public double slope(double x) { return gamma * (sigmoid((x - xt) / st) - sigmoid((x - xs) / ss)); }

    public double dmax() { return dmin + gamma * (xs - xt); }

    /** Spielraum in Blenden (gerades Stück). */
    public double latitudeStops() { return (xs - xt) / Math.log10(2); }

    /** x zu einer Dichte zwischen D_min und D_max (Bisektion). */
    public double inverse(double d) {
        double lo = xt - 10 * st - 2, hi = xs + 10 * ss + 2;
        for (int i = 0; i < 100; i++) {
            double m = 0.5 * (lo + hi);
            if (density(m) < d) lo = m; else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    /** Als sechs Zahlen für die Grafik: D_min, γ, x_t, s_t, x_s, s_s. */
    public float[] toArray() { return new float[]{(float) dmin, (float) gamma, (float) xt, (float) st, (float) xs, (float) ss}; }
}
