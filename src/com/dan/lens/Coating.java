package com.dan.lens;

/**
 * Vergütung einer Glas-Luft-Fläche: eine dünne Schicht der Brechzahl nFilm und Dicke d. Der Reflexionsgrad folgt der
 * Interferenz der beiden Teilwellen an den Grenzen Luft|Schicht und Schicht|Glas (Airy-Formel, senkrechter Einfall):
 * <pre>
 *   r = (r01 + r12 · e^(−2iδ)) / (1 + r01 · r12 · e^(−2iδ)),   δ = 2π · nFilm · d / λ,   R = |r|²
 * </pre>
 * Ohne Schicht bleibt Fresnel, ((n1 − n2) / (n1 + n2))². Eine λ/4-Schicht aus Magnesiumfluorid (MgF₂, n = 1,38) auf
 * Kronglas (1,52) senkt R bei ihrer Wellenlänge von 4,3 % auf 1,3 % und lässt Rot und Blau stärker zurück: Darum
 * sind die Geisterbilder vergüteter Objektive purpur oder grün. Einfach vergütet ist der Stand der 1950er Jahre (Zeiss,
 * Smakula 1935); heutige Objektive haben Mehrschichten mit R unter 0,5 % ({@link #multi()} [G], als flache Kurve).
 */
public record Coating(String name, double nFilm, double thicknessNm, double flatR) {

    /** Unvergütet: Fresnel. */
    public static final Coating NONE = new Coating("unvergütet", 1.0, 0, -1);

    /** λ/4-Schicht MgF₂ für die Wellenlänge lambda0 (nm). */
    public static Coating mgf2(double lambda0Nm) {
        return new Coating("MgF₂ λ/4 bei " + Math.round(lambda0Nm) + " nm", 1.38, lambda0Nm / (4 * 1.38), -1);
    }

    /** Mehrschicht heute [G]: R ≈ 0,3 % mit leichtem Anstieg zu den Rändern des Spektrums. */
    public static Coating multi() { return new Coating("Mehrschicht", 1.0, 0, 0.003); }

    /** Reflexionsgrad zwischen den Medien n1 und n2 (eine Seite Luft) bei λ (nm), senkrechter Einfall. */
    public double reflectance(double n1, double n2, double lambdaNm) {
        boolean glasGlas = n1 > 1.0001 && n2 > 1.0001;
        if (glasGlas || this == NONE || (thicknessNm <= 0 && flatR < 0)) {
            double r = (n1 - n2) / (n1 + n2);
            return r * r;
        }
        if (flatR >= 0) {
            double x = (lambdaNm - 550) / 130;
            return flatR * (1 + 1.6 * x * x);
        }
        // Luft auf der Seite mit der kleineren Brechzahl
        double na = Math.min(n1, n2), ng = Math.max(n1, n2), nf = nFilm;
        double r01 = (na - nf) / (na + nf), r12 = (nf - ng) / (nf + ng);
        double delta = 2 * Math.PI * nf * thicknessNm / lambdaNm;
        double c = Math.cos(2 * delta), s = -Math.sin(2 * delta);
        // Zähler r01 + r12·e, Nenner 1 + r01·r12·e, e = c + i·s
        double zr = r01 + r12 * c, zi = r12 * s;
        double nr = 1 + r01 * r12 * c, ni = r01 * r12 * s;
        return (zr * zr + zi * zi) / (nr * nr + ni * ni);
    }

    @Override public String toString() { return name; }
}
