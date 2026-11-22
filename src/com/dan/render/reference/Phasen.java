package com.dan.render.reference;

import java.util.SplittableRandom;

/**
 * Phasenfunktionen der Referenz (P7b). Das Argument ist μ = cos des Winkels zwischen der Richtung, in der das Licht
 * ankommt, und der, in der es weiterläuft (μ = 1: geradeaus). Im Pfad, der vom Auge zurück läuft, ist das der Kosinus
 * zwischen alter und neuer Richtung des Pfads.
 * <ul>
 * <li><b>Rayleigh:</b> 3/(16π) (1 + μ²), wie das Echtzeitbild; gezogen genau (kubische Gleichung).</li>
 * <li><b>Mie (Dunst):</b> Cornette und Shanks mit g = 0,85 wie {@code Scattering}; gezogen über Henyey-Greenstein mit
 * demselben g und dem Gewicht p / q (höchstens 3 (1 + g²) / (2 + g²) / … ≤ 1,2).</li>
 * <li><b>Schwebstoff:</b> Fournier und Forand (1994) in der Form von Mobley u. a. (2002), n = 1,10 und μ_FF so, dass
 * b_b / b = {@link Wellen#BB_ZU_B} (F54 A); als Tabelle (8 192 Stufen in θ, nach vorn verdichtet, je Stufe
 * gleichverteilt in cos θ), aus der gezogen und ausgewertet wird.</li>
 * </ul>
 */
public final class Phasen {
    private Phasen() { }

    public static final double INV4PI = 1 / (4 * Math.PI);

    public static double rayleigh(double mu) { return 3.0 / (16 * Math.PI) * (1 + mu * mu); }

    /** μ nach Rayleigh: Lösung von (3μ + μ³ + 4) / 8 = u. */
    public static double rayleighZiehe(double u) {
        double z = 4 * u - 2, w = Math.cbrt(z + Math.sqrt(z * z + 1));
        return w - 1 / w;
    }

    public static double cornetteShanks(double mu, double g) {
        double g2 = g * g;
        return 3.0 / (8 * Math.PI) * (1 - g2) * (1 + mu * mu) / ((2 + g2) * Math.pow(Math.max(1e-6, 1 + g2 - 2 * g * mu), 1.5));
    }

    public static double hg(double mu, double g) {
        double g2 = g * g;
        return INV4PI * (1 - g2) / Math.pow(1 + g2 - 2 * g * mu, 1.5);
    }

    public static double hgZiehe(double u, double g) {
        if (Math.abs(g) < 1e-6) return 1 - 2 * u;
        double s = (1 - g * g) / (1 - g + 2 * g * u);
        return Math.max(-1, Math.min(1, (1 + g * g - s * s) / (2 * g)));
    }

    // ------------------------------------------------------------------ Fournier-Forand

    /** n und μ_FF der Teilchen (μ_FF aus b_b / b). */
    public static final double FF_N = 1.10, FF_MU;
    private static final int FF_STUFEN = 8192;
    /** Stützstellen in θ (rad), Werte p(θ) (1/sr), Verteilung F(θ) = ∫₀^θ p 2π sin. */
    private static final double[] FF_THETA = new double[FF_STUFEN + 1], FF_P = new double[FF_STUFEN + 1], FF_F = new double[FF_STUFEN + 1];
    /** Rückstreuanteil der Tabelle (zur Prüfung). */
    public static final double FF_BB;

    static {
        double lo = 3.0, hi = 5.0;
        for (int i = 0; i < 200; i++) {
            double m = 0.5 * (lo + hi);
            if (ffRueck(FF_N, m) < Wellen.BB_ZU_B) lo = m; else hi = m;
        }
        FF_MU = 0.5 * (lo + hi);
        // θ nach vorn verdichtet: θ = π s^3
        for (int i = 0; i <= FF_STUFEN; i++) {
            double s = (double) i / FF_STUFEN;
            FF_THETA[i] = Math.PI * s * s * s;
            FF_P[i] = i == 0 ? Double.POSITIVE_INFINITY : ffFormel(FF_THETA[i]);
        }
        // F mit dem Mittelpunkt je Stufe (die Spitze bei θ = 0 ist integrierbar: p ~ θ^(-μ+1)… hier über die Stufe genähert)
        FF_F[0] = 0;
        for (int i = 1; i <= FF_STUFEN; i++) {
            double a = FF_THETA[i - 1], b = FF_THETA[i], m = 0.5 * (a + b);
            FF_F[i] = FF_F[i - 1] + ffFormel(m) * 2 * Math.PI * (Math.cos(a) - Math.cos(b));
        }
        double tot = FF_F[FF_STUFEN];
        for (int i = 0; i <= FF_STUFEN; i++) FF_F[i] /= tot;
        for (int i = 1; i <= FF_STUFEN; i++) FF_P[i] /= tot;
        int j = 0;
        while (FF_THETA[j + 1] < Math.PI / 2) j++;
        double fj = FF_F[j] + (FF_F[j + 1] - FF_F[j]) * (Math.PI / 2 - FF_THETA[j]) / (FF_THETA[j + 1] - FF_THETA[j]);
        FF_BB = 1 - fj;
    }

    /** Seit P11a: die Tafel für Stufe 2 (ReferenzKarte), Kopien von {θ_i, F(θ_i)}, i = 0 … 8 192. */
    public static double[][] ffTafel() { return new double[][] {FF_THETA.clone(), FF_F.clone()}; }

    /** b_b / b der Formel nach Mobley u. a. (2002), Gl. 4. */
    static double ffRueck(double n, double mu) {
        double nu = (3 - mu) / 2, d90 = 4 / (3 * (n - 1) * (n - 1)) * Math.pow(Math.sin(Math.PI / 4), 2);
        return 1 - (1 - Math.pow(d90, nu + 1) - 0.5 * (1 - Math.pow(d90, nu))) / ((1 - d90) * Math.pow(d90, nu));
    }

    /** p(θ) nach Fournier und Forand (normiert auf 1 über die Kugel, Mobley u. a. 2002, Gl. 2). */
    static double ffFormel(double theta) {
        double n = FF_N, nu = (3 - FF_MU) / 2, s2 = Math.pow(Math.sin(theta / 2), 2);
        double d = 4 / (3 * (n - 1) * (n - 1)) * s2, d180 = 4 / (3 * (n - 1) * (n - 1));
        double dn = Math.pow(d, nu), d180n = Math.pow(d180, nu);
        double c = Math.cos(theta);
        return 1 / (4 * Math.PI * (1 - d) * (1 - d) * dn) * (nu * (1 - d) - (1 - dn) + (d * (1 - dn) - nu * (1 - d)) / s2)
                + (1 - d180n) / (16 * Math.PI * (d180 - 1) * d180n) * (3 * c * c - 1);
    }

    /**
     * p(μ) nach Fournier und Forand, wie die Referenz sie rechnet: die Tabelle, in jeder Stufe gleichverteilt in cos θ
     * ({@link #ffDichte}). Ziehen und Auswerten sind damit dieselbe Verteilung (Gewicht 1), normiert auf 1.
     */
    public static double ff(double mu) { return ffDichte(mu); }

    /** μ nach Fournier und Forand (Tabelle invertiert, in jeder Stufe gleichverteilt in cos θ). */
    public static double ffZiehe(double u) {
        int lo = 0, hi = FF_STUFEN;
        while (hi - lo > 1) {
            int m = (lo + hi) >>> 1;
            if (FF_F[m] <= u) lo = m; else hi = m;
        }
        double f = (u - FF_F[lo]) / Math.max(1e-300, FF_F[lo + 1] - FF_F[lo]);
        double ca = Math.cos(FF_THETA[lo]), cb = Math.cos(FF_THETA[lo + 1]);
        return ca + f * (cb - ca);
    }

    /** Dichte, mit der {@link #ffZiehe} μ zieht (1/sr): in jeder Stufe gleichverteilt in cos θ. */
    public static double ffDichte(double mu) {
        double th = Math.acos(Math.max(-1, Math.min(1, mu)));
        int i = Math.min(FF_STUFEN - 1, (int) (Math.cbrt(th / Math.PI) * FF_STUFEN));
        while (i > 0 && FF_THETA[i] > th) i--;
        while (i < FF_STUFEN - 1 && FF_THETA[i + 1] < th) i++;
        // cos a − cos b ohne Auslöschung (in der ersten Stufe ist θ ~ 10⁻¹¹)
        double dc = 2 * Math.sin(0.5 * (FF_THETA[i] + FF_THETA[i + 1])) * Math.sin(0.5 * (FF_THETA[i + 1] - FF_THETA[i]));
        return (FF_F[i + 1] - FF_F[i]) / (2 * Math.PI * dc);
    }

    /** Eine Richtung um die Achse a mit cos μ (φ gleichverteilt). */
    public static double[] umAchse(double[] a, double mu, SplittableRandom r) {
        double phi = 2 * Math.PI * r.nextDouble(), s = Math.sqrt(Math.max(0, 1 - mu * mu));
        double[][] b = Vek.basis(a);
        double cx = s * Math.cos(phi), cy = s * Math.sin(phi);
        return new double[] {b[0][0] * cx + b[1][0] * cy + a[0] * mu, b[0][1] * cx + b[1][1] * cy + a[1] * mu, b[0][2] * cx + b[1][2] * cy + a[2] * mu};
    }
}
