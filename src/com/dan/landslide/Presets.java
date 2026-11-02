package com.dan.landslide;

/**
 * Fertige Ströme auf gerechnetem Gelände (Werkbuch 6.2). Das echte Gelände kommt aus der App (für LituyaBay1958 mit
 * {@code rueckbau1958}); hier steht jeweils ein Schnitt, der die Größen der Quellen trägt. Achsen: x hangabwärts, z quer,
 * Höhe in m über dem Wasserspiegel.
 */
public final class Presets {
    private Presets() { }

    /** Lituya Bay, 9. Juli 1958: Volumen (m³) nach FRITZ01 / MILLER60. */
    public static final double LITUYA_VOLUME = 30.6e6;
    /** Platte: Breite quer 823 m, Länge längs des Hangs 970 m, größte Mächtigkeit 92 m, Schwerpunkt 610 m ü. M. */
    public static final double LITUYA_WIDTH = 823, LITUYA_LENGTH = 970, LITUYA_HMAX = 92, LITUYA_CENTROID = 610;
    public static final double LITUYA_SLOPE = 40, LITUYA_DEPTH = 122, LITUYA_OPPOSITE = 1342;
    /** Aufprall nach FRITZ01: 96–110 m/s; Mächtigkeit am Ufer bis 134 m, Länge 748 m, Dauer 6,8 s. */
    public static final double[] LITUYA_IMPACT = {96, 110};

    /** Form der Platte: Exponent, mit dem (p/(p+1))² · 92 m die mittlere Mächtigkeit 38,3 m trägt. */
    public static double plateExponent(double meanOverMax) { double q = Math.sqrt(meanOverMax); return q / (1 - q); }

    /**
     * Gilbert Inlet 1958 auf gerechnetem Gelände: Hang 40° vom Grat bis zur Sohle 122 m unter dem Wasser, flacher Grund,
     * Gegenhang 45° mit der Uferlinie 1 342 m vom Fuß des Abbruchs (FRITZ01). Die Platte 823 × 970 m, bis 92 m mächtig,
     * Schwerpunkt der Gleitfläche 610 m ü. M., auf 30,6 Mio. m³ gestellt. Stoff: Fels mit μ 0,06, ξ 8 000 m/s² [G] –
     * gerechnet auf die 96–110 m/s des Aufpralls nach FRITZ01 (kurzer Weg, grober Fels auf Fels).
     */
    public static Landslide gilbertInlet1958(Landslide.Quality q) {
        Landslide s = gilbertInlet1958Terrain(q);
        double ta = Math.tan(Math.toRadians(LITUYA_SLOPE));
        double p = plateExponent(LITUYA_VOLUME / (LITUYA_WIDTH * LITUYA_LENGTH) / LITUYA_HMAX);
        s.addPlate(-LITUYA_CENTROID / ta, 0, 1, 0, LITUYA_LENGTH, LITUYA_WIDTH, LITUYA_HMAX, p);
        s.scaleTo(LITUYA_VOLUME);
        s.crown();
        return s;
    }

    /** Das Gelände des Gilbert Inlet ohne die Platte (für die Übergabe aus einem Bruch). */
    public static Landslide gilbertInlet1958Terrain(Landslide.Quality q) {
        double ta = Math.tan(Math.toRadians(LITUYA_SLOPE)), toe = LITUYA_DEPTH / ta, rise = LITUYA_OPPOSITE - LITUYA_DEPTH;
        Landslide s = Landslide.over(-1150, -800, LITUYA_OPPOSITE + 80, 800, q.dx);
        s.name = "Gilbert Inlet 1958";
        s.source = "FRITZ01, MILLER60; Gelände gerechnet [G]";
        // die Wand ist eine Rinne: quer jenseits von ±480 m steigt sie mit 30° an (die Flanken der Abrissnische), unter
        // Wasser weitet sie sich auf ±620 m (der Inlet) [G]
        double side = Math.tan(Math.toRadians(30));
        s.bed((x, z) -> {
            double w = 480 + 140 * Math.max(0, Math.min(1, x / 150));
            return (x <= toe ? -x * ta : x < rise ? -LITUYA_DEPTH : -LITUYA_DEPTH + (x - rise)) + Math.max(0, Math.abs(z) - w) * side;
        });
        s.material = new Material("Fels (Lituya)", 2700, 0.06, 8000, 35);
        s.water(0);
        return s;
    }

    /**
     * Vajont, 9. Oktober 1963: 270 Mio. m³ glitten auf einer Tonlage (Sessel: oben 35°, unten flach) 300–400 m über das
     * Tal und in den Stausee (Spiegel 700 m ü. M.; hier 0), mit 20–30 m/s (GENEVOIS05) [G für den Schnitt]. Stoff: μ 0,12,
     * ξ 1 000 m/s².
     */
    public static Landslide vajont1963(Landslide.Quality q) {
        double dx = Math.max(q.dx, 10), t35 = Math.tan(Math.toRadians(35));
        Landslide s = Landslide.over(-1400, -1300, 900, 1300, dx);
        s.name = "Vajont 1963";
        s.source = "GENEVOIS05, SEMENZA65; Gelände gerechnet [G]";
        s.material = new Material("Fels auf Ton (Vajont)", 2400, 0.12, 1000, 30);
        // Sessel: 35° bis x = 0 (Höhe 0 = Talboden 50 m unter dem Spiegel), dann flach bis 250 m, Gegenhang 40°
        s.bed((x, z) -> x <= 0 ? -60 - x * t35 : x < 250 ? -60 : -60 + (x - 250) * Math.tan(Math.toRadians(40)));
        s.water(0);
        s.addPlate(-700, 0, 1, 0, 1300, 1900, 250, 1.6);
        s.scaleTo(270e6);
        s.crown();
        return s;
    }

    /**
     * Mount St. Helens, 18. Mai 1980: der Teil der Schuttlawine (rund 0,43 km³), der in den Spirit Lake ging (Spiegel 975 m
     * ü. M.; hier 0) – 50–70 m/s (VOIGHT83) [G für den Schnitt]. Hang 25° vom Gipfel herab, See bis 60 m tief. Stoff:
     * Schutt μ 0,10, ξ 1 500 m/s².
     */
    public static Landslide spiritLake1980(Landslide.Quality q) {
        double dx = Math.max(q.dx, 20), t25 = Math.tan(Math.toRadians(25));
        Landslide s = Landslide.over(-4400, -1600, 3000, 1600, dx);
        s.name = "Spirit Lake 1980";
        s.source = "VOIGHT83; Gelände gerechnet [G]";
        s.material = new Material("Schuttlawine (St. Helens)", 2000, 0.10, 1500, 30);
        s.bed((x, z) -> x <= 0 ? -x * t25 : x < 2000 ? -60 * Math.sin(Math.PI * x / 2000) : (x - 2000) * Math.tan(Math.toRadians(15)));
        s.water(0);
        s.addPlate(-2600, 0, 1, 0, 2400, 1500, 230, 1.5);
        s.scaleTo(0.43e9);
        s.crown();
        return s;
    }
}
