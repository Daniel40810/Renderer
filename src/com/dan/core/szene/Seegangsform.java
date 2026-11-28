package com.dan.core.szene;

/**
 * Die Form der Wellen im Flachen (Seegang Werkbuch, S3; Plan 6.3): Schiefe und Vorneigung nach der Ursell-Zahl, die
 * Verteilung der Höhen im Flachen und die gebundene lange Welle. Werte und Formeln für {@link Oberflaeche} und die Shader.
 * <p>
 * <b>Schiefe und Vorneigung</b> nach Ruessink, Ramaekers und van Rijn (2012), Coastal Engineering 65, 56–63, als
 * Parametrisierung über die Ursell-Zahl (mit den Konstanten wie in XBeach, „ruessink_vanrijn“; geprüft gegen die
 * geschlossene Form bei CoastalWiki: (S² + A²)^−½ = 1,17 (1 + 0,205 Ur^−3,37)):
 * <pre>
 *   Ur = 3/8 · Hs k / (k h)³
 *   B  = p1 + (p2 − p1) / (1 + exp((p3 − ln Ur) / p4)),   p1 = 0, p2 = 0,857, p3 = −0,471, p4 = 0,297
 *   ψ  = −90° + 90° tanh(p5 / Ur^p6),                     p5 = 0,815, p6 = 0,672
 *   Sk = B cos ψ,  As = B sin ψ
 * </pre>
 * Die Parametrisierung gilt für die Geschwindigkeit am Grund; für die Höhe der Fläche ist sie übernommen [G]. As &lt; 0
 * heißt nach vorn geneigt (die Front steil).
 * <p>
 * <b>Form zweiter Ordnung im schmalen Band</b> (nach Tayfun 1980, erweitert um die Vorneigung [G]): mit dem analytischen
 * Signal Z der linearen Fläche (η₁ = Re Z) ist η = Re Z + Re(c Z²). Für ein Gaußsches Z gilt in erster Ordnung
 * Sk = 6 Re(c) σ und As = 6 Im(c) σ (mit dem Vorzeichen der Phase φ = k x − ω t, As &lt; 0: Front steil). Die
 * Verschiebung nach Gerstner bringt einen Teil der Schiefe schon mit ({@link #schiefeVerschiebung}); c gibt nur den
 * Rest dazu, nie weniger als 0. Wo Ruessink weniger Schiefe gibt als die Verschiebung (kleine Ursell-Zahl, Stokes im
 * Mitteltiefen), bleibt die der Verschiebung.
 * <p>
 * <b>Höhen im Flachen</b> nach Battjes und Groenendijk (2000), Coastal Engineering 40, 161–182: zusammengesetzte
 * Weibull-Verteilung, k₁ = 2 unter H_tr und k₂ = 3,6 darüber, H_tr = (0,35 + 5,8 tan α) h, H_rms = (0,6725 + 0,2025 Hm0/h) Hm0.
 * Die Hülle |Z| der linearen Fläche wird so abgebildet, dass 2|Z| statt der Rayleigh-Verteilung diese Verteilung
 * hat ({@link #huelle}); E[H²] bleibt, also die Varianz.
 * <p>
 * <b>Gebundene lange Welle</b> nach Longuet-Higgins und Stewart (1962): η_lw = −S_xx / (ρ (g h − c_g²)) aus der Hülle,
 * S_xx = ½ ρ g |Z|² (2n − ½); der Nenner ist auf mindestens {@link #LW_NENNER} g h begrenzt [G], weil die Theorie im
 * Flachen gegen die Resonanz geht.
 */
public final class Seegangsform {
    public static final double P1 = 0, P2 = 0.857, P3 = -0.471, P4 = 0.297, P5 = 0.815, P6 = 0.672;
    public static final double K1 = 2, K2 = 3.6;
    public static final double LW_NENNER = 0.5;
    /** Kleinste normierte Übergangshöhe H̃_tr [G]: darunter (fast trocken, Fernfeld über Land) gilt sie selbst. */
    public static final double HTR_MIN = 0.05;

    private Seegangsform() { }

    public static double ursell(double hs, double k, double h) {
        double kh = k * h;
        return 0.375 * hs * k / (kh * kh * kh);
    }

    /** {B, ψ (rad), Sk, As} nach Ruessink u. a. (2012). */
    public static double[] ruessink(double ur) {
        if (!(ur > 0)) return new double[] {0, 0, 0, 0};
        double b = P1 + (P2 - P1) / (1 + Math.exp((P3 - Math.log(ur)) / P4));
        double psi = Math.toRadians(-90 + 90 * Math.tanh(P5 / Math.pow(ur, P6)));
        return new double[] {b, psi, b * Math.cos(psi), b * Math.sin(psi)};
    }

    /**
     * Anteil der Schiefe aus der Verschiebung, gemessen wie bei Ruessink (ohne Anteile unter fp/2), gegen 3 λ k coth(kh) σ
     * im schmalen Band [G, S3a Teil 6: 0,66 bei kh 0,44, 0,84 bei kh 1,5]. Die Verschiebung bringt Summen- und
     * Differenzfrequenzen; über alles gemittelt heben sich ihre Schiefen bei einem Gaußschen Seegang auf (Befund B27),
     * ohne die Differenzen bleibt dieser Anteil.
     */
    public static final double LAGRANGE = 2.0 / 3;

    /** Schiefe, die die waagrechte Verschiebung λ a coth(kh) allein bringt: {@link #LAGRANGE} · 3 λ k coth(kh) σ. */
    public static double schiefeVerschiebung(double lambda, double k, double h, double sigma) {
        return LAGRANGE * 3 * lambda * k / Math.tanh(Math.max(1e-6, k * h)) * sigma;
    }

    /**
     * Der Beiwert der zweiten Ordnung und der Maßstab der linearen Fläche: Ziel {sk, as} (gesamt), σ (Streuung der
     * Höhe), skLag (Schiefe aus der Verschiebung). Zurück {Re c, Im c, s}: η = s Re Z + Re(c (s Z)²) hat die Varianz σ²
     * (in zweiter Ordnung) und Sk, As in erster Ordnung wie das Ziel. Die Schiefe der zweiten Ordnung wird nicht negativ.
     * Feste Punktiteration für s³ (die Schiefe fällt mit s³).
     */
    public static double[] beiwert(double sk, double as, double sigma, double skLag) {
        if (!(sigma > 0)) return new double[] {0, 0, 1};
        double zr = Math.max(0, sk - skLag), zi = as;
        double tr = zr, ti = zi, s = 1;
        for (int it = 0; it < 20; it++) {
            s = 1 / Math.sqrt(1 + (tr * tr + ti * ti) / 9);
            double s3 = s * s * s;
            tr = zr / s3; ti = zi / s3;
        }
        s = 1 / Math.sqrt(1 + (tr * tr + ti * ti) / 9);
        double sl = s * sigma;
        return new double[] {tr / (6 * sl), ti / (6 * sl), s};
    }

    // ------------------------------------------------------------------ Battjes und Groenendijk (2000)

    public static double hTr(double h, double tanA) { return (0.35 + 5.8 * Math.max(0, tanA)) * h; }

    public static double hRms(double hm0, double h) { return (0.6725 + 0.2025 * hm0 / Math.max(1e-6, h)) * hm0; }

    /** {x = (H_tr/H₁)², H̃₁, H̃₂} zur normierten Übergangshöhe H̃_tr = H_tr / H_rms; Normierung E[H̃²] = 1. */
    public static double[] bg(double htr) {
        if (!(htr > 0)) return new double[] {0, 0, 0};
        if (htr > 4) { double x = htr * htr; return new double[] {x, 1, htr / Math.pow(x, 1 / K2)}; }
        double lo = 1e-8, hi = 60;
        for (int it = 0; it < 200; it++) {
            double m = 0.5 * (lo + hi);
            if (norm(htr, m) > 1) lo = m; else hi = m;
        }
        double x = 0.5 * (lo + hi);
        return new double[] {x, htr / Math.sqrt(x), htr / Math.pow(x, 1 / K2)};
    }

    /** E[H̃²] für H̃_tr und x; fällt mit x. */
    static double norm(double htr, double x) {
        double g2 = 1 - (1 + x) * Math.exp(-x);
        double a = 1 + 2 / K2;
        return htr * htr * (g2 / x + gammaOben(a, x) / Math.pow(x, 2 / K2));
    }

    /** Normierte Höhe zur Unterschreitung p (Quantil). */
    public static double quantil(double[] p3, double p) {
        double l = -Math.log(1 - p);
        return l < p3[0] ? p3[1] * Math.sqrt(l) : p3[2] * Math.pow(l, 1 / K2);
    }

    /** {H1/3, H1/10, H2 %} der Verteilung, normiert mit H_rms. */
    public static double[] kennwerte(double[] p3) {
        int n = 200000;
        double s3 = 0, s10 = 0;
        int n3 = 0, n10 = 0;
        for (int i = 0; i < n; i++) {
            double p = (i + 0.5) / n, hq = quantil(p3, p);
            if (p >= 2.0 / 3) { s3 += hq; n3++; }
            if (p >= 0.9) { s10 += hq; n10++; }
        }
        return new double[] {s3 / n3, s10 / n10, quantil(p3, 0.98)};
    }

    /**
     * Faktor der Hülle: 2|Z| hat lokal die Rayleigh-Verteilung mit H_rms,R = √(8 m0); zurück f mit 2|Z| f nach Battjes
     * und Groenendijk. hm0 = 4 √m0, h die Tiefe, tanA das Gefälle des Grunds. 1, wo H̃_tr groß ist (tiefes Wasser).
     */
    public static double huelle(double z, double m0, double h, double tanA) {
        if (!(m0 > 0) || !(z > 0)) return 1;
        double hm0 = 4 * Math.sqrt(m0);
        double htr = Math.max(HTR_MIN, hTr(h, tanA) / hRms(hm0, h));
        if (htr > 4) return 1;
        double[] p3 = bg(htr);
        double l = z * z / (2 * m0);
        return l < p3[0] ? p3[1] : p3[2] * Math.pow(l, 1 / K2 - 0.5);
    }

    /** κ der gebundenen langen Welle: η_lw = −κ (|Z|² − 2 m0). k, h der tragenden Wellen. */
    public static double langeWelle(double k, double h) {
        double hh = Math.max(Phasenfeld.H_MIN, h), n = Dispersion.n(k, hh), sg = Dispersion.sigma(k, hh), cg = n * sg / k;
        double nenner = Math.max(Dispersion.G * hh - cg * cg, LW_NENNER * Dispersion.G * hh);
        return Dispersion.G * (2 * n - 0.5) / (2 * nenner);
    }

    // ------------------------------------------------------------------ Hilfen

    /** Obere unvollständige Gammafunktion Γ(a, x). */
    static double gammaOben(double a, double x) {
        if (x <= 0) return Math.exp(lnGamma(a));
        double gln = lnGamma(a);
        if (x < a + 1) {                                                  // Reihe für P(a, x)
            double ap = a, sum = 1 / a, del = sum;
            for (int n = 0; n < 500; n++) { ap += 1; del *= x / ap; sum += del; if (Math.abs(del) < Math.abs(sum) * 1e-15) break; }
            double p = sum * Math.exp(-x + a * Math.log(x) - gln);
            return (1 - p) * Math.exp(gln);
        }
        double b = x + 1 - a, c = 1 / 1e-300, d = 1 / b, hh = d;          // Kettenbruch für Q(a, x)
        for (int i = 1; i < 500; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b; if (Math.abs(d) < 1e-300) d = 1e-300;
            c = b + an / c; if (Math.abs(c) < 1e-300) c = 1e-300;
            d = 1 / d;
            double del = d * c;
            hh *= del;
            if (Math.abs(del - 1) < 1e-15) break;
        }
        return Math.exp(-x + a * Math.log(x) - gln) * hh * Math.exp(gln);
    }

    static double lnGamma(double xx) {
        double[] cof = {76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};
        double x = xx, y = xx, tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (double c : cof) ser += c / ++y;
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
