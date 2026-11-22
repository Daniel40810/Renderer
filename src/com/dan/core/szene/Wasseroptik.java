package com.dan.core.szene;

/**
 * Die Optik des Wassers (P6d, Werkbuch HalligHooge Teil A, 12.7, F42; seit P7a, F52 A, in der gemeinsamen Szene): Fresnel, Absorption reinen
 * Wassers, Schwebstoff, Kräuselung nach Cox und Munk. Werte und Formeln für den Prozessor; {@code wasser.glsl} rechnet
 * dieselben auf der Karte. Die drei Kanäle stehen für 680, 550 und 440 nm [G], wie die Rayleigh-Streuung von render.air.
 * <ul>
 * <li><b>Reines Wasser:</b> Absorption nach Pope und Fry (1997), Tabelle in 2,5-nm-Schritten [S]: 680 nm 0,00465,
 * 550 nm 0,000565, 440 nm 0,0000635 je cm.</li>
 * <li><b>Schwebstoff</b> (mineralisch) mit C in g/m³ = mg/l (30 [G] bis sediment, P9): Absorption a*(443) = 0,04 m²/g
 * [S, untere Spanne nach Babin und Stramski 2004] mit der Neigung 0,0123 je nm [S, Babin u. a. 2003]; Rückstreuung
 * b_b* = 0,012 m²/g [S, Neukermans u. a. 2012, mineralisch], ohne Gang mit der Wellenlänge [G].</li>
 * <li><b>Dämpfung</b> auf den Wegen von Sonne und Grund: a + b_b [G]. Das Licht, das Schwebstoff nach vorn streut,
 * bleibt im Strahl (der Schwebstoff streut zu über 98 % nach vorn); die Unschärfe des Grunds fehlt.</li>
 * <li><b>Einfachstreuung</b> der Sonne und des Monds mit β = b_b / 2π je sr nach hinten [G]: über die Tiefe integriert
 * L = E_w · β · (1 − e^(−K s k)) / (K k), k = 1 + μ_v / μ_s, s der Weg des Blicks bis zum Grund.</li>
 * <li><b>Kräuselung</b> (F42): Neigungsvarianz σ² = 0,003 + 5,12 · 10⁻³ U₁₀ (Cox und Munk 1954), verteilt auf
 * Wellen von 5 mm bis 1 m mit gleicher Varianz je Oktave (Sättigungsbereich) [G]. Was ein Bildpunkt auflöst, ist
 * Geometrie (48 Wellen); der Rest ist Statistik für das Glitzern der Sonne.</li>
 * </ul>
 */
public final class Wasseroptik {
    /** Wellenlängen der Kanäle R, G, B (nm) [G]. */
    public static final double[] LAMBDA = {680, 550, 440};
    /** Pope und Fry (1997), 1/m. */
    public static final double[] POPE_FRY = {0.465, 0.0565, 0.00635};
    public static final double A_STERN_443 = 0.04, S_NAP = 0.0123, BB_STERN = 0.012;
    /** Kurze Wellen: kürzeste und längste Wellenlänge (m) und Zahl der Bänder, je zwei Wellen [G]. */
    public static final double WELLE_MIN = 0.005, WELLE_MAX = 1.0;
    public static final int BAENDER = 12;

    /** Brechzahl [G, Werkbuch 12.7]. */
    public double n = 1.34;
    /** Schwebstoff (g/m³) [G]. */
    public double schwebstoff = 30;

    /** Absorption des Schwebstoffs je Kanal (1/m). */
    public double[] aSchweb() {
        double[] a = new double[3];
        for (int c = 0; c < 3; c++) a[c] = A_STERN_443 * schwebstoff * Math.exp(-S_NAP * (LAMBDA[c] - 443));
        return a;
    }

    /** Rückstreuung je Kanal (1/m). */
    public double[] bb() { return new double[] {BB_STERN * schwebstoff, BB_STERN * schwebstoff, BB_STERN * schwebstoff}; }

    /** Dämpfung K = a_w + a_p + b_b je Kanal (1/m). */
    public double[] daempfung() {
        double[] a = aSchweb(), b = bb(), k = new double[3];
        for (int c = 0; c < 3; c++) k[c] = POPE_FRY[c] + a[c] + b[c];
        return k;
    }

    /** Reflexion für unpolarisiertes Licht aus Luft bei cos θ des Einfalls. */
    public static double fresnel(double ci, double n) {
        ci = Math.max(0, Math.min(1, ci));
        double st2 = (1 - ci * ci) / (n * n), ct = Math.sqrt(Math.max(0, 1 - st2));
        double rs = (ci - n * ct) / (ci + n * ct), rp = (n * ci - ct) / (n * ci + ct);
        return 0.5 * (rs * rs + rp * rp);
    }

    /** Neigungsvarianz nach Cox und Munk aus der Windgeschwindigkeit in 10 m (m/s). */
    public static double coxMunk(double u10) { return 0.003 + 5.12e-3 * u10; }

    /** Kreisfrequenz einer kurzen Welle (rad/s): ω² = g k + (σ/ρ) k³ mit Oberflächenspannung 0,074 N/m, ρ = 1025 kg/m³. */
    public static double omega(double k) { return Math.sqrt(9.81 * k + 0.074 / 1025 * k * k * k); }

    /** Wellen je Band [G]. */
    public static final int JE_BAND = 4;

    /**
     * Die Wellen der Kräuselung: je Welle {Wellenzahl Ost, Nord (rad/m), Phase am Ort (0, 0) zur Zeit 0, Neigungsamplitude}.
     * Vier Wellen je Band; ihre Richtungen folgen der Verteilung cos²(θ) um den Wind (θ bis ±90°) [G], Richtungen und
     * Phasen fest aus Folgen mit dem goldenen Schnitt (kein Zufall: jedes Bild gleich). Die Neigungsvarianz aller Wellen
     * ist σ² (jede Welle s² / 2).
     */
    public static double[][] wellen(double u10, double v10) {
        double s2 = coxMunk(Math.hypot(u10, v10));
        double wind = Math.hypot(u10, v10) > 1e-6 ? Math.atan2(v10, u10) : 0;
        int n = JE_BAND * BAENDER;
        double[][] w = new double[n][];
        double je = s2 / n, s = Math.sqrt(2 * je);
        for (int b = 0; b < BAENDER; b++) {
            double lam = WELLE_MAX * Math.pow(WELLE_MIN / WELLE_MAX, (b + 0.5) / BAENDER), k = 2 * Math.PI / lam;
            for (int q = 0; q < JE_BAND; q++) {
                int m = b * JE_BAND + q;
                double u = ((m + 0.5) * 0.6180339887498949) % 1.0;
                double winkel = wind + cos2Quantil(u);
                double phase = 2 * Math.PI * ((m * 0.7548776662466927) % 1.0);
                w[m] = new double[] {k * Math.cos(winkel), k * Math.sin(winkel), phase, s};
            }
        }
        return w;
    }

    /** Winkel θ in (−π/2, π/2) mit P(Θ ≤ θ) = u für die Dichte (2/π) cos²θ (Newton auf θ + sin θ cos θ = π (u − 1/2)). */
    static double cos2Quantil(double u) {
        double z = Math.PI * (u - 0.5), th = z / 2;
        for (int i = 0; i < 30; i++) {
            double f = th + Math.sin(th) * Math.cos(th) - z, d = 1 + Math.cos(2 * th);
            if (d < 1e-12) break;
            th -= f / d;
            th = Math.max(-Math.PI / 2, Math.min(Math.PI / 2, th));
        }
        return th;
    }
}
