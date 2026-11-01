package com.dan.quake;

/**
 * Abminderungsgesetz nach Boore, Stewart, Seyhan und Atkinson 2014 (BSSA14, Earthquake Spectra 30(3), NGA-West2) für
 * flache Beben in der Kruste: Median von PGA (g), PGV (cm/s) und Spektralbeschleunigung bei 0,2 s und 1,0 s (g, 5 %
 * Dämpfung) aus Magnitude Mw, Joyner-Boore-Abstand Rjb (km), Vs30 (m/s) und Herdmechanismus; dazu die Streuung
 * (σ in ln). Koeffizienten aus der Veröffentlichung (wie in OpenQuake {@code boore_2014.py}); ohne Beckenterm (Z1 nicht
 * bekannt) und mit Δc3 = 0 (global).
 * <p>
 * ln Y = F_E(M, Mech) + F_P(Rjb, M) + F_S(Vs30, PGAr), mit
 * F_E = e_Mech + e4 (M − Mh) + e5 (M − Mh)² für M ≤ Mh, sonst e_Mech + e6 (M − Mh);
 * F_P = [c1 + c2 (M − Mref)] ln(R / Rref) + (c3 + Δc3)(R − Rref), R = √(Rjb² + h²);
 * F_S = c ln(min(Vs30, Vc) / Vref) + f1 + f2 ln((PGAr + f3) / f3), f2 = f4 [e^{f5 (min(Vs30, 760) − 360)} − e^{f5 (760 − 360)}].
 */
public final class Bssa14 {
    private Bssa14() { }

    /** Herdmechanismus. */
    public enum Mechanism { UNSPECIFIED, STRIKE_SLIP, NORMAL, REVERSE }

    /** Größen. */
    public enum Imt { PGA, PGV, SA02, SA10 }

    static final double MREF = 4.5, RREF = 1.0, VREF = 760.0, F1 = 0.0, F3 = 0.1, V1 = 225.0, V2 = 300.0;

    // e0 e1 e2 e3 e4 e5 e6 Mh | c1 c2 c3 h Dc3 | c Vc f4 f5 | R1 R2 DfR DfV phi1 phi2 tau1 tau2
    static final double[][] K = {
            {0.4473, 0.4856, 0.2459, 0.4539, 1.431, 0.05053, -0.1662, 5.5, -1.134, 0.1917, -0.008088, 4.5, 0, -0.6, 1500, -0.15, -0.00701, 110, 270, 0.1, 0.07, 0.695, 0.495, 0.398, 0.348},
            {5.037, 5.078, 4.849, 5.033, 1.073, -0.1536, 0.2252, 6.2, -1.243, 0.1489, -0.00344, 5.3, 0, -0.84, 1300, -0.1, -0.00844, 105, 272, 0.082, 0.08, 0.644, 0.552, 0.401, 0.346},
            {1.3255, 1.359, 1.122, 1.3414, 1.1349, -0.11096, -0.15852, 5.92, -1.0607, 0.14489, -0.007717, 4.61, 0, -0.68762, 1392.61, -0.24658, -0.00614, 90.91, 270, 0.136, 0.045, 0.711, 0.539, 0.344, 0.309},
            {0.3932, 0.4218, 0.207, 0.4124, 1.5004, -0.18983, 0.17895, 6.2, -1.193, 0.10248, -0.00121, 5.74, 0, -1.05, 1109.95, -0.10521, -0.00844, 116.39, 270, 0.098, 0.02, 0.553, 0.625, 0.498, 0.298},
    };

    /** ln des Medians (Y in g, PGV in cm/s) auf Fels mit Vs30 = 760 (ohne Bodenterm). */
    static double lnRock(double[] k, double m, double rjb, Mechanism mech) {
        double e = k[mech.ordinal()];
        double dm = m - k[7];
        double fe = m <= k[7] ? e + k[4] * dm + k[5] * dm * dm : e + k[6] * dm;
        double r = Math.sqrt(rjb * rjb + k[11] * k[11]);
        double fp = (k[8] + k[9] * (m - MREF)) * Math.log(r / RREF) + (k[10] + k[12]) * (r - RREF);
        return fe + fp;
    }

    /** ln des Medians (Y in g, PGV in cm/s). */
    public static double ln(Imt imt, double m, double rjbKm, double vs30, Mechanism mech) {
        double[] k = K[imt.ordinal()];
        double pgar = Math.exp(lnRock(K[0], m, rjbKm, mech));
        double flin = k[13] * Math.log(Math.min(vs30, k[14]) / VREF);
        double f2 = k[15] * (Math.exp(k[16] * (Math.min(vs30, 760) - 360)) - Math.exp(k[16] * (760 - 360)));
        double fnl = F1 + f2 * Math.log((pgar + F3) / F3);
        return lnRock(k, m, rjbKm, mech) + flin + fnl;
    }

    /** Median (g, PGV in cm/s). */
    public static double median(Imt imt, double m, double rjbKm, double vs30, Mechanism mech) { return Math.exp(ln(imt, m, rjbKm, vs30, mech)); }

    /** Gesamtstreuung σ (ln), aus τ(M) und φ(M, Rjb, Vs30). */
    public static double sigma(Imt imt, double m, double rjbKm, double vs30) {
        double[] k = K[imt.ordinal()];
        double tau = m <= 4.5 ? k[23] : m >= 5.5 ? k[24] : k[23] + (k[24] - k[23]) * (m - 4.5);
        double phi = m <= 4.5 ? k[21] : m >= 5.5 ? k[22] : k[21] + (k[22] - k[21]) * (m - 4.5);
        if (rjbKm > k[17] && rjbKm <= k[18]) phi += k[19] * Math.log(rjbKm / k[17]) / Math.log(k[18] / k[17]);
        else if (rjbKm > k[18]) phi += k[19];
        if (vs30 <= V1) phi -= k[20];
        else if (vs30 < V2) phi -= k[20] * Math.log(V2 / vs30) / Math.log(V2 / V1);
        return Math.sqrt(tau * tau + phi * phi);
    }
}
