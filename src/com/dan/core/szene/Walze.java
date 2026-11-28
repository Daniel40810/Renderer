package com.dan.core.szene;

/**
 * Brechen in der Tiefe und die Walze am Ort, ohne Zustand (Seegang Werkbuch, S5a; Plan 6.5, SF46 A, Datei 39/40).
 * Referenz in double; {@code see_gemein.glsl} ({@code walze}) rechnet dasselbe in float.
 * <p>
 * Aus der Hülle Z' = Re + i Im der Fläche nach Höhenabbildung und Maßstab ({@link Oberflaeche}), der Tiefe h und k_p:
 * <ul>
 * <li><b>Brechgrad</b> q = glatt({@link #Q_VON}, {@link #Q_BIS}, H / H_m) mit H = 2 |Z'| und H_m = γ h, γ = 0,73 wie im
 * Nahfeld (Battjes und Janssen 1978; derselbe Ansatz, aus dem dbr kommt). Seit S5a-2 (Befund L2u) bricht ein Kamm ab
 * H / H_m = 0,81 (q = 0,5): so trifft der Anteil der brechenden Kämme Q_b im Sturm und bei Xaver (ab 0,9 in L2u: 0,58
 * und 0,26 von Q_b; ab 0,82 in L2v: 0,88 und 0,74; ab 0,80: 0,93 und 1,41 – bei Xaver brechen nur rund 1,7 % der Kämme,
 * dort ist die Verteilung steil und die Stichprobe klein).</li>
 * <li><b>Lage</b> aus der Phase θ = arg Z': der Kamm liegt bei θ = 0, die Front davor bei θ &gt; 0 (die Welle läuft in
 * Richtung ∇Φ, Gerstner wie in der Synthese).</li>
 * <li><b>Walze</b> auf der Front als Keil mit der Dicke {@link #DICKE} H am Kamm und der Länge L_r = 2 A / (DICKE H),
 * in der Phase θ_r = k_p L_r höchstens π/2 (dann dicker, die Fläche bleibt) [G]. Fläche seit S5a-2 aus der Bilanz der
 * Walze: Je brechender Welle gibt sie ab, was die Welle verliert, β_r ρ g c A / L = (α/4) ρ g f H² (Bore wie bei
 * Battjes und Janssen, α = 1 wie im Nahfeld), also A = α H² / (4 β_r) = 2,5 H² mit β_r = 0,1. Mit A = 0,9 H² (Svendsen
 * 1984) gaben die Walzen in L2u nur rund ein Drittel von dbr ab. Weißwasser w deckt die Walze und {@link #HINTEN} rad
 * hinter dem Kamm [G].</li>
 * </ul>
 * Dissipation der Walzen (Stive und De Vriend 1994; Nairn u. a. 1990): D_r = 2 g β_r E_r / c mit E_r = ρ c² A / (2 L),
 * also D_r = β_r ρ g c A / L = β_r ρ g c ⟨t⟩, ⟨t⟩ das Mittel der Dicke über die Fläche; β_r {@link #BETA_VON} bis
 * {@link #BETA_BIS} [G].
 */
public final class Walze {
    public static final String VERSION = "0.3";
    /** γ und α wie im Nahfeld, Brechen von bis (H / H_m), Dicke am Kamm (in H), Weißwasser hinter dem Kamm (rad). */
    public static final double GAMMA = 0.73, ALPHA = 1.0, Q_VON = 0.71, Q_BIS = 0.91, DICKE = 0.5, HINTEN = 0.15;
    /**
     * Seit S5b-1 (SF47 A): Anteil der Dicke, um den die Walze die Fläche hebt. 0: Die Form der Synthese trägt die Walze
     * schon. Höhen nach Battjes–Groenendijk, Schiefe und Vorneigung nach Ruessink u. a. (2012) sind an gemessenen Wellen
     * der Brandung gewonnen, Walze eingeschlossen. Mit 0,3 (L2x) lag Sk im Prüfstein S3.4 bis 0,10 über Ruessink
     * (h = 2 m, Hs = 0,9 m: 0,737 gegen 0,633; Befund B67); die Hebung zählte die Walze doppelt.
     */
    public static final double HEBUNG = 0.0;
    /** Neigung der Walze β_r (Stive und De Vriend 1994: 0,05–0,1) [G]; Fläche A / H² aus der Bilanz; Svendsen (1984) zum Vergleich. */
    public static final double BETA = 0.1, FLAECHE = ALPHA / (4 * BETA), SVENDSEN = 0.9;

    private Walze() { }

    /**
     * {Brechgrad q, Weißwasser w, Dicke t (m), Phase θ (rad)} aus Z' = zr + i zi (nach Hülle und Maßstab, m), der Tiefe
     * hh (m, schon mit der kleinsten Tiefe) und k_p (1/m).
     */
    public static double[] walze(double zr, double zi, double hh, double kp) {
        double hoehe = 2 * Math.hypot(zr, zi), hm = GAMMA * hh;
        double q = hm > 0 ? glatt(Q_VON, Q_BIS, hoehe / hm) : 0;
        if (!(q > 0) || !(kp > 0) || !(hoehe > 0)) return new double[] {q > 0 ? q : 0, 0, 0, 0};
        double a = FLAECHE * hoehe * hoehe, th = Math.atan2(zi, zr), thr = Math.min(kp * 2 * a / (DICKE * hoehe), 0.5 * Math.PI);
        double t0 = 2 * a * kp / thr;                                          // Dicke am Kamm, damit die Fläche bleibt
        double wv = glatt(-HINTEN - 0.1, -HINTEN, th) * (1 - glatt(0.8 * thr, thr, th));
        double t = th >= 0 ? q * t0 * Math.max(0, 1 - th / thr) : 0;
        return new double[] {q, q * wv, t, th};
    }

    /** Phasengeschwindigkeit ω / k (m/s) bei k und h, linear. */
    public static double c(double k, double h) {
        return k > 0 ? Math.sqrt(9.81 / k * Math.tanh(k * h)) : 0;
    }

    /** Dissipation der Walzen D_r = β_r ρ g c ⟨t⟩ (W/m²). */
    public static double dissipation(double beta, double c, double tMittel) {
        return beta * 1025 * 9.81 * c * tMittel;
    }

    /** smoothstep wie in GLSL. */
    static double glatt(double a, double b, double x) {
        double u = Math.min(1, Math.max(0, (x - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }
}
