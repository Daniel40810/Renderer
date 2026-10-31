package com.dan.sky;

/**
 * Streuung an einer Kugel nach Lorenz und Mie (seit Brocken Phase 7), Rechenweg nach Bohren und Huffman, „Absorption
 * and Scattering of Light by Small Particles“ (1983), Anhang A (BHMIE): logarithmische Ableitung abwärts, Riccati-Bessel
 * aufwärts, die Winkelfunktionen π und τ je Ordnung. Für Wassertröpfchen ohne Absorption (reeller Brechungsindex), was
 * im sichtbaren Licht auf besser als 10⁻⁸ stimmt.
 * <p>
 * Nur Java. Die Tabellen für Glorie und Nebelbogen baut {@link Droplets} daraus.
 */
public final class Mie {
    private Mie() { }

    /** Ergebnis für eine Kugel: Wirkungsgrade und die Streufunktion (|S1|² + |S2|²) / 2 je Winkel. */
    public record Result(double qExt, double qSca, double g, double[] s11) { }

    /**
     * Streuung einer Kugel mit dem Größenparameter {@code x = 2πr/λ} und dem reellen Brechungsindex {@code m} relativ zur
     * Luft, an den Winkeln mit den Kosinussen {@code mu} (Streuwinkel 0 = vorwärts, π = zurück).
     */
    public static Result scatter(double x, double m, double[] mu) {
        int nAng = mu.length;
        int nStop = (int) Math.round(x + 4.05 * Math.cbrt(x) + 2.0);
        double y = m * x;
        int nmx = (int) Math.round(Math.max(nStop, Math.abs(y))) + 15;
        double[] d = new double[nmx + 1];
        for (int n = nmx; n >= 1; n--) d[n - 1] = n / y - 1.0 / (d[n] + n / y);
        double[] pi0 = new double[nAng], pi1 = new double[nAng];
        java.util.Arrays.fill(pi1, 1.0);
        double[] s1r = new double[nAng], s1i = new double[nAng], s2r = new double[nAng], s2i = new double[nAng];
        double psi0 = Math.cos(x), psi1 = Math.sin(x), chi0 = -Math.sin(x), chi1 = Math.cos(x);
        double qsca = 0, qext = 0, gsum = 0;
        double anPr = 0, anPi = 0, bnPr = 0, bnPi = 0;
        for (int n = 1; n <= nStop; n++) {
            double en = n, fn = (2 * en + 1) / (en * (en + 1));
            double psi = (2 * en - 1) * psi1 / x - psi0;
            double chi = (2 * en - 1) * chi1 / x - chi0;
            // ξ = ψ − iχ
            double xiR = psi, xiI = -chi, xi1R = psi1, xi1I = -chi1;
            double da = d[n] / m + en / x, db = m * d[n] + en / x;
            // a_n = (da ψ − ψ1) / (da ξ − ξ1), b_n entsprechend
            double[] an = div(da * psi - psi1, 0, da * xiR - xi1R, da * xiI - xi1I);
            double[] bn = div(db * psi - psi1, 0, db * xiR - xi1R, db * xiI - xi1I);
            double ar = an[0], ai = an[1], br = bn[0], bi = bn[1];
            qsca += (2 * en + 1) * (ar * ar + ai * ai + br * br + bi * bi);
            qext += (2 * en + 1) * (ar + br);
            // Asymmetrie nach Bohren und Huffman: Re(a_n b_n*) und Re(a_(n−1) a_n* + b_(n−1) b_n*)
            gsum += (2 * en + 1) / (en * (en + 1)) * (ar * br + ai * bi);
            if (n > 1) gsum += (en - 1) * (en + 1) / en * (anPr * ar + anPi * ai + bnPr * br + bnPi * bi);
            for (int j = 0; j < nAng; j++) {
                double p = pi1[j], t = en * mu[j] * p - (en + 1) * pi0[j];
                s1r[j] += fn * (ar * p + br * t);
                s1i[j] += fn * (ai * p + bi * t);
                s2r[j] += fn * (ar * t + br * p);
                s2i[j] += fn * (ai * t + bi * p);
                double pn = ((2 * en + 1) * mu[j] * p - (en + 1) * pi0[j]) / en;
                pi0[j] = p;
                pi1[j] = pn;
            }
            anPr = ar; anPi = ai; bnPr = br; bnPi = bi;
            psi0 = psi1; psi1 = psi;
            chi0 = chi1; chi1 = chi;
        }
        double k = 2 / (x * x);
        qsca *= k;
        qext *= k;
        double g = 4 * gsum / (x * x * Math.max(qsca, 1e-30));
        double[] s11 = new double[nAng];
        for (int j = 0; j < nAng; j++) s11[j] = 0.5 * (s1r[j] * s1r[j] + s1i[j] * s1i[j] + s2r[j] * s2r[j] + s2i[j] * s2i[j]);
        return new Result(qext, qsca, g, s11);
    }

    private static double[] div(double ar, double ai, double br, double bi) {
        double q = br * br + bi * bi;
        return new double[]{(ar * br + ai * bi) / q, (ai * br - ar * bi) / q};
    }

    /**
     * Brechungsindex von Wasser bei 10 °C im Sichtbaren (λ in µm), nach Cauchy an die Werte von Hale und Querry (1973)
     * angepasst: 1,343 bei 400 nm, 1,334 bei 550 nm, 1,330 bei 700 nm.
     */
    public static double waterIndex(double lambdaUm) { return 1.3243 + 3.03e-3 / (lambdaUm * lambdaUm); }

    /** Brechungsindex von Eis (λ in µm): 1,317 bei 450 nm, 1,307 bei 656 nm (für die Halos). */
    public static double iceIndex(double lambdaUm) { return 1.2996 + 3.4e-3 / (lambdaUm * lambdaUm); }
}
