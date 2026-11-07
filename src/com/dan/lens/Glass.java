package com.dan.lens;

/**
 * Ein optisches Glas: Brechzahl n_d bei der Helium-d-Linie (587,56 nm) und Abbe-Zahl V_d = (n_d − 1) / (n_F − n_C).
 * Dazwischen und daneben gilt die Dispersion nach Cauchy, n(λ) = A + B / λ², mit A und B so, dass n_d und
 * n_F − n_C genau stimmen (F: 486,13 nm, C: 656,27 nm). Zwischen 450 und 680 nm liegt Cauchy für die üblichen
 * Kron- und Flintgläser um wenige 10⁻⁴ neben Sellmeier; für die Farbfehler eines Objektivs genügt das.
 * <p>Die Gläser unten sind Katalogwerte (Schott); ein Linsenschnitt nennt sie mit ihrer Brechzahl.
 */
public record Glass(String name, double nd, double vd) {

    /** Fraunhofer-Linien (nm): d (Helium), F (Wasserstoff, blau), C (Wasserstoff, rot). */
    public static final double LAMBDA_D = 587.56, LAMBDA_F = 486.13, LAMBDA_C = 656.27;

    public static final Glass AIR = new Glass("Luft", 1.0, Double.POSITIVE_INFINITY);

    /** Bariumflint N-BAF10 (n_d 1,67003, V_d 47,11). */
    public static final Glass BAF10 = new Glass("N-BAF10", 1.67003, 47.11);
    /** Schwerflint SF15 (1,69895, 30,07). */
    public static final Glass SF15 = new Glass("SF15", 1.69895, 30.07);
    /** Flint F5 (1,60342, 38,03). */
    public static final Glass F5 = new Glass("F5", 1.60342, 38.03);
    /** Schwerkron N-SSK5 (1,65844, 50,88). */
    public static final Glass SSK5 = new Glass("N-SSK5", 1.65844, 50.88);
    /** Lanthanflint N-LAF3 (1,71736, 48,02). */
    public static final Glass LAF3 = new Glass("N-LAF3", 1.71736, 48.02);
    /** Borkron N-BK7 (1,51680, 64,17), zum Vergleich und für die Prüfung. */
    public static final Glass BK7 = new Glass("N-BK7", 1.51680, 64.17);

    /** Brechzahl bei der Wellenlänge λ (nm). */
    public double n(double lambdaNm) {
        if (Double.isInfinite(vd) || nd == 1.0) return nd;
        double l = lambdaNm * 1e-3, ld = LAMBDA_D * 1e-3, lf = LAMBDA_F * 1e-3, lc = LAMBDA_C * 1e-3;
        double dn = (nd - 1) / vd;
        double b = dn / (1 / (lf * lf) - 1 / (lc * lc));
        double a = nd - b / (ld * ld);
        return a + b / (l * l);
    }

    public boolean isAir() { return nd == 1.0; }

    @Override public String toString() { return name; }
}
