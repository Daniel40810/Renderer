package com.dan.aurora;

/**
 * Der Niederschlag der Elektronen: ein Maxwell-Spektrum mit der charakteristischen Energie {@link #energie} (keV) und
 * dem Energiefluss {@link #fluss} (mW/m² = erg/cm²/s), dazu wahlweise ein weicher Anteil. Jede Energie dringt bis zu
 * ihrer Reichweite in Massensäule ein (Rees 1989: R = 4,30·10⁻⁷ + 5,36·10⁻⁶·E^1,67 g/cm²) und gibt ihre Energie
 * entlang des Wegs nach der Funktion λ(s) für isotropen Einfall ab (Rees 1989, Abb. 3.3, tabelliert). Je 35 eV
 * entsteht ein Ionenpaar. Ergebnis ist die Ionisationsrate über der Höhe auf dem Gitter {@link #HOEHEN}.
 */
public final class Niederschlag {
    /** Höhengitter der Profile in km: 80 bis 400 km in Schritten von 1 km. */
    public static final int H0 = 80, H1 = 400;
    public static final double[] HOEHEN = new double[H1 - H0 + 1];

    static {
        for (int i = 0; i < HOEHEN.length; i++) HOEHEN[i] = H0 + i;
    }

    /** Charakteristische Energie E₀ in keV; die mittlere Energie des Maxwell-Spektrums ist 2·E₀. */
    public double energie = 3;
    /** Energiefluss Q in mW/m² (erg/cm²/s): 1 ist ein schwacher Bogen (IBC I–II), 10 ein heller (IBC II–III), 50 bis 100 der Aufbruch (IBC III–IV). */
    public double fluss = 5;
    /** Anteil des Flusses in einem weichen Maxwell-Spektrum (Energie {@link #weichEnergie}); er macht das Rot oben. */
    public double weich = 0.15, weichEnergie = 0.2;
    /** Energie je Ionenpaar in eV. */
    public static final double EV_JE_IONENPAAR = 35;

    public Niederschlag() { }

    public Niederschlag(double energie, double fluss) {
        this.energie = energie;
        this.fluss = fluss;
    }

    /** Reichweite in g/cm² nach Rees (1989). */
    public static double reichweite(double keV) {
        return 4.30e-7 + 5.36e-6 * Math.pow(keV, 1.67);
    }

    /** λ(s) für isotropen Einfall, s = Massensäule / Reichweite, auf ∫ λ ds = 1 normiert. */
    static final double[] LAMBDA = {1.00, 1.25, 1.42, 1.48, 1.42, 1.25, 1.00, 0.70, 0.42, 0.17, 0.0};
    static final double LAMBDA_NORM;

    static {
        double s = 0;
        for (int i = 0; i < LAMBDA.length - 1; i++) s += 0.5 * (LAMBDA[i] + LAMBDA[i + 1]) * 0.1;
        LAMBDA_NORM = 1 / s;
    }

    public static double lambda(double s) {
        if (s <= 0 || s >= 1) return s <= 0 ? LAMBDA[0] * LAMBDA_NORM : 0;
        double x = s * 10;
        int i = (int) x;
        double f = x - i;
        return (LAMBDA[i] + f * (LAMBDA[i + 1] - LAMBDA[i])) * LAMBDA_NORM;
    }

    /** Ionisationsrate in Ionenpaaren je cm³ und s auf {@link #HOEHEN}, für beide Anteile des Spektrums. */
    public double[] ionisation() {
        double[] q = new double[HOEHEN.length];
        if (weich > 0 && weich < 1) {
            maxwell(q, weichEnergie, fluss * weich);
            maxwell(q, energie, fluss * (1 - weich));
        } else maxwell(q, weich >= 1 ? weichEnergie : energie, fluss);
        return q;
    }

    /** Addiert die Ionisation eines Maxwell-Spektrums (E₀ keV, Q erg/cm²/s), 60 Energiestufen von 0,02·E₀ bis 15·E₀. */
    static void maxwell(double[] q, double e0, double qFluss) {
        if (qFluss <= 0) return;
        int n = 60;
        double lo = Math.log(0.02 * e0), hi = Math.log(15 * e0), dl = (hi - lo) / n;
        double[] e = new double[n], w = new double[n];
        double norm = 0;
        for (int k = 0; k < n; k++) {
            e[k] = Math.exp(lo + (k + 0.5) * dl);
            // Energiefluss je Stufe: E²·exp(−E/E₀) dE, dE = E·dl (logarithmisch)
            w[k] = e[k] * e[k] * Math.exp(-e[k] / e0) * e[k] * dl;
            norm += w[k];
        }
        double evJeErg = 6.2415e11;
        for (int k = 0; k < n; k++) {
            double dq = qFluss * w[k] / norm, r = reichweite(e[k]);
            for (int i = 0; i < HOEHEN.length; i++) {
                double s = Atmosphaere.saeule(HOEHEN[i]) / r;
                if (s >= 1) continue;
                q[i] += dq * Atmosphaere.dichte(HOEHEN[i]) / r * lambda(s) * evJeErg / EV_JE_IONENPAAR;
            }
        }
    }

    /** Höhe in km, in der ein Profil sein Maximum hat. */
    public static double hoeheMaximum(double[] profil) {
        int best = 0;
        for (int i = 1; i < profil.length; i++) if (profil[i] > profil[best]) best = i;
        return HOEHEN[best];
    }

    /** Senkrechte Säule eines Profils: ∫ über die Höhe, je cm² und s (Höhe in km → cm). */
    public static double saeule(double[] profil) {
        double s = 0;
        for (int i = 0; i < profil.length - 1; i++) s += 0.5 * (profil[i] + profil[i + 1]) * (HOEHEN[i + 1] - HOEHEN[i]) * 1e5;
        return s;
    }
}
