package com.dan.sky;

/**
 * Der CIE-Normhimmel (seit LituyaBay1958 W8b): die 15 Typen der relativen Leuchtdichteverteilung nach CIE S 011/E:2003
 * und ISO 15469:2004 (Kittler, Darula und Perez 1998; Darula und Kittler 2002), von „bedeckt, steiler Verlauf“ bis
 * „wolkenlos, trüb, weite Sonnenkorona“.
 * <p>
 * Leuchtdichte eines Himmelselements im Zenitabstand Z, Winkelabstand χ zur Sonne (Sonne im Zenitabstand Z<sub>s</sub>),
 * bezogen auf den Zenit:
 * <pre>
 *   L / L<sub>z</sub> = f(χ) · φ(Z) / ( f(Z<sub>s</sub>) · φ(0) )
 *   φ(Z) = 1 + a · exp(b / cos Z)                 Abstufung (Gradation), φ(90°) = 1
 *   f(χ) = 1 + c · (exp(d · χ) − exp(d · π/2)) + e · cos² χ    Streuindikatrix
 * </pre>
 * Typ 1 ist der klassische bedeckte Himmel: an der Kimm ein Drittel der Zenitleuchtdichte, wie Moon und Spencer (1942)
 * ihn mit (1 + 2 sin γ) / 3 beschrieben (hier 0,335). Für eine dicke Wolkendecke über dem Beobachter gibt
 * {@link #normalizedGradation} die Abstufung so, dass die Bestrahlungsstärke der Decke gleich bleibt: nur die Verteilung
 * über den Himmel ändert sich (heller im Zenit, dunkler zur Kimm), nicht das Licht am Boden. Nur Java 21.
 */
public final class CieSky {
    private CieSky() { }

    /** a, b, c, d, e je Typ (1 … 15, Index 0 … 14). */
    public static final double[][] TYPES = {
            {4.0, -0.70, 0, -1.0, 0.00},
            {4.0, -0.70, 2, -1.5, 0.15},
            {1.1, -0.80, 0, -1.0, 0.00},
            {1.1, -0.80, 2, -1.5, 0.15},
            {0.0, -1.00, 0, -1.0, 0.00},
            {0.0, -1.00, 2, -1.5, 0.15},
            {0.0, -1.00, 5, -2.5, 0.30},
            {0.0, -1.00, 10, -3.0, 0.45},
            {-1.0, -0.55, 2, -1.5, 0.15},
            {-1.0, -0.55, 5, -2.5, 0.30},
            {-1.0, -0.55, 10, -3.0, 0.45},
            {-1.0, -0.32, 10, -3.0, 0.45},
            {-1.0, -0.32, 16, -3.0, 0.30},
            {-1.0, -0.15, 16, -3.0, 0.30},
            {-1.0, -0.15, 24, -2.8, 0.15}};

    public static final String[] NAME = {
            "bedeckt, steiler Verlauf, ringsum gleich",
            "bedeckt, steiler Verlauf, zur Sonne leicht heller",
            "bedeckt, mäßiger Verlauf, ringsum gleich",
            "bedeckt, mäßiger Verlauf, zur Sonne leicht heller",
            "gleichmäßig hell",
            "teils bedeckt, ohne Verlauf, zur Sonne leicht heller",
            "teils bedeckt, ohne Verlauf, hellere Sonnenkorona",
            "teils bedeckt, ohne Verlauf, deutliche Sonnenkorona",
            "teils bewölkt, Sonne verdeckt",
            "teils bewölkt, hellere Sonnenkorona",
            "weiß-blau, deutliche Sonnenkorona",
            "klar, geringe Trübung",
            "klar, verschmutzte Luft",
            "wolkenlos, trüb, weite Sonnenkorona",
            "weiß-blau, trüb, weite Sonnenkorona"};

    static double[] p(int type) { return TYPES[Math.max(1, Math.min(15, type)) - 1]; }

    /** Abstufung φ(Z) für cos Z (über der Kimm; an und unter der Kimm 1). */
    public static double gradation(int type, double cosZ) {
        double[] t = p(type);
        return cosZ <= 1e-6 ? 1 : 1 + t[0] * Math.exp(t[1] / cosZ);
    }

    /** Streuindikatrix f(χ) für den Winkelabstand χ zur Sonne (rad). */
    public static double indicatrix(int type, double chi) {
        double[] t = p(type);
        return 1 + t[2] * (Math.exp(t[3] * chi) - Math.exp(t[3] * Math.PI / 2)) + t[4] * Math.cos(chi) * Math.cos(chi);
    }

    /** L / L<sub>z</sub> für Zenitabstand z, Zenitabstand der Sonne zs und Winkelabstand chi (alles rad). */
    public static double relative(int type, double z, double zs, double chi) {
        return indicatrix(type, chi) * gradation(type, Math.cos(z)) / (indicatrix(type, zs) * gradation(type, 1));
    }

    /** Wie {@link #relative} für Richtungen (Einheitsvektoren, y nach oben): Blick und Sonne. */
    public static double relative(int type, double[] view, double[] sun) {
        double z = Math.acos(Math.max(-1, Math.min(1, view[1]))), zs = Math.acos(Math.max(-1, Math.min(1, sun[1])));
        double c = view[0] * sun[0] + view[1] * sun[1] + view[2] * sun[2];
        return relative(type, Math.min(z, Math.PI / 2), zs, Math.acos(Math.max(-1, Math.min(1, c))));
    }

    /** Mittel von φ über die Halbkugel, gewichtet mit cos Z (wie die Bestrahlungsstärke): (1/π) ∫ φ cos Z dω. */
    public static double meanGradation(int type) {
        int n = 2000;
        double s = 0;
        for (int i = 0; i < n; i++) {
            double cz = (i + 0.5) / n;            // dω cos Z = 2π cos Z d(cos Z)
            s += gradation(type, cz) * cz;
        }
        return 2 * s / n;                          // (1/π) · 2π ∫ φ μ dμ
    }

    /** φ(Z) / Mittel: Abstufung einer Decke bei gleicher Bestrahlungsstärke (Mittel 1). */
    public static double normalizedGradation(int type, double cosZ) { return gradation(type, cosZ) / meanGradation(type); }

    /**
     * Typ für eine Wolkendecke [G]: geschlossene, dicke Decke mit Niederschlag 1, dünnere geschlossene 3, aufgelockert 5,
     * darunter die Typen der Bewölkung bis zum klaren Himmel (12).
     */
    public static int forCover(double oktas, boolean precipitating) {
        if (oktas >= 7.5) return precipitating ? 1 : 3;
        if (oktas >= 6) return precipitating ? 3 : 5;
        if (oktas >= 4) return 9;
        if (oktas >= 2) return 11;
        return 12;
    }
}
