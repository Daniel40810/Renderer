package com.dan.beacon;

/**
 * Tragweiten nach IALA (Empfehlung E-200-2): Lichtstärke und Sichtweite ergeben die Tragweite, die Höhen von Feuer und
 * Auge die Kimm. Alle Entfernungen in Seemeilen (1 sm = 1 852 m), Lichtstärken in Candela, Höhen in Metern.
 * <ul>
 * <li><b>Nenntragweite:</b> Tragweite bei 10 sm meteorologischer Sicht (Durchlässigkeit 0,74 je sm), nachts mit der
 * Schwelle 0,2 µlx am Auge: I = 3,43·10⁶ · E · d² · 0,74⁻ᵈ.</li>
 * <li><b>Lichttragweite</b> bei anderer Sicht V: Durchlässigkeit 0,05^(1/V) je sm (Koschmieder mit 5 %).</li>
 * <li><b>Geografische Tragweite:</b> 2,075 · (√h_Feuer + √h_Auge), mit normaler Lichtbrechung.</li>
 * <li><b>Wirksame Lichtstärke</b> eines Blitzes der Dauer t (Blondel-Rey): I · t / (0,2 + t).</li>
 * </ul>
 */
public final class Range {
    private Range() { }

    public static final double NM = 1852;
    /** Schwelle der Beleuchtungsstärke am Auge nachts in lx (IALA für Nenntragweite). */
    public static final double E_NIGHT = 2e-7;
    /** Durchlässigkeit je sm bei 10 sm Sicht. */
    public static final double T_NOMINAL = 0.74;

    /** Lichtstärke in cd für eine Nenntragweite in sm. */
    public static double intensityForNominal(double nm) {
        if (nm <= 0) return 0;
        return 3.43e6 * E_NIGHT * nm * nm * Math.pow(T_NOMINAL, -nm);
    }

    /** Nenntragweite in sm für eine Lichtstärke in cd. */
    public static double nominalRange(double cd) { return luminousRange(cd, T_NOMINAL); }

    /** Durchlässigkeit je sm bei meteorologischer Sicht V in sm. */
    public static double transmissivity(double visibilityNm) { return Math.pow(0.05, 1 / Math.max(1e-3, visibilityNm)); }

    /** Lichttragweite in sm bei der Sicht visibilityNm (sm). */
    public static double luminousRangeAtVisibility(double cd, double visibilityNm) { return luminousRange(cd, transmissivity(visibilityNm)); }

    /** Lichttragweite in sm bei der Durchlässigkeit T je sm (Halbierung, auf 0,001 sm genau). */
    public static double luminousRange(double cd, double t) {
        if (cd <= 0) return 0;
        double lo = 1e-4, hi = 200;
        for (int i = 0; i < 60; i++) {
            double m = 0.5 * (lo + hi);
            double e = cd * Math.pow(t, m) / (3.43e6 * m * m);
            if (e > E_NIGHT) lo = m; else hi = m;
        }
        return 0.5 * (lo + hi);
    }

    /** Geografische Tragweite in sm zwischen Feuerhöhe und Augenhöhe (m). */
    public static double geographicRange(double lightHeightM, double eyeHeightM) {
        return 2.075 * (Math.sqrt(Math.max(0, lightHeightM)) + Math.sqrt(Math.max(0, eyeHeightM)));
    }

    /** Wirksame Lichtstärke eines Blitzes der Dauer t s (Blondel-Rey, a = 0,2 s). */
    public static double effective(double cd, double flashSeconds) { return cd * flashSeconds / (0.2 + flashSeconds); }

    /** Spitzenstärke, die ein Blitz der Dauer t s braucht, um wirksam cd zu erreichen. */
    public static double peakForEffective(double cd, double flashSeconds) { return cd * (0.2 + flashSeconds) / Math.max(1e-3, flashSeconds); }

    /** Meteorologische Sicht in km aus einem Auslöschungskoeffizienten in 1/m (Koschmieder, 5 %: 3/σ). */
    public static double visibilityKm(double sigmaPerM) { return sigmaPerM <= 0 ? Double.POSITIVE_INFINITY : 2.996 / sigmaPerM / 1000; }
}
