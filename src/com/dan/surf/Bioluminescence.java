package com.dan.surf;

/**
 * Seit Sylt Phase 13: Meeresleuchten. In warmen Sommernächten blüht in der Nordsee der Dinoflagellat
 * <i>Noctiluca scintillans</i>; jede Zelle blitzt blaugrün (um 475 nm) auf, wenn das Wasser sie schert. Darum leuchtet
 * die See dort, wo sie aufgewühlt wird: im stürzenden Kamm, im frischen Schaum der Walze, im Schwall und in den Blasen
 * am Spülsaum, die Fußtritte im nassen Sand. Der Schaum klingt mit der Zeit ab (die Zellen erschöpfen sich), alter
 * Schaum leuchtet nicht mehr.
 * <p>
 * Das Modell gibt nur Zahlen: wie stark die Blüte nach Monat und Wassertemperatur ist, und die Leuchtdichte des frisch
 * aufgewühlten Wassers. Die Shader von {@code surf} (Netz der Brecher) und {@code ocean} (Brandung von außen) nehmen sie
 * als Farbe, vorbelichtet. Werte gerundet nach Beobachtungen an Nord- und Ostsee [G]: kräftiges Meeresleuchten ist mit
 * dunkeladaptiertem Auge deutlich sichtbar, also um 10⁻³ cd/m², Spitzen um 10⁻² cd/m².
 */
public final class Bioluminescence {
    private Bioluminescence() { }

    /** Leuchtdichte frisch aufgewühlten Wassers bei voller Blüte (cd/m²) [G]. */
    public static final double PEAK = 6e-3;
    /** Farbe (linear RGB, Leuchtdichte 1) bei 475 nm, an den Rand von sRGB gezogen. */
    public static final double[] COLOUR = rgb475();

    /**
     * Stärke der Blüte 0..1 nach Monat (1..12) und Wassertemperatur (°C) [G]: Noctiluca blüht in der Deutschen Bucht von
     * Juni bis September, am stärksten im August, und erst über etwa 14 °C.
     */
    public static double bloom(int month, double waterC) {
        double[] m = {0, 0, 0, 0, 0.05, 0.35, 0.8, 1, 0.6, 0.15, 0, 0};
        double season = m[Math.max(1, Math.min(12, month)) - 1];
        double warm = Math.max(0, Math.min(1, (waterC - 13) / 5));
        return season * warm;
    }

    /** Mittlere Wassertemperatur der Nordsee vor Sylt je Monat (°C) [G, gerundet nach BSH-Klimatologie]. */
    public static double northSeaTemperature(int month) {
        double[] t = {5.5, 4.5, 5, 7, 10.5, 14, 16.5, 17.5, 16, 13, 10, 7.5};
        return t[Math.max(1, Math.min(12, month)) - 1];
    }

    /**
     * Leuchtdichte (cd/m²) je Kanal für Wasser, das gerade so stark aufgewühlt ist ({@code agitation} 0..1: 1 der
     * stürzende Kamm, 0,3 der Schwall), bei Blüte {@code bloom} und Verstärkung {@code gain} (1 wie gemessen).
     */
    public static double[] luminance(double bloom, double agitation, double gain) {
        double l = PEAK * Math.max(0, bloom) * Math.max(0, Math.min(1, agitation)) * Math.max(0, gain);
        return new double[]{COLOUR[0] * l, COLOUR[1] * l, COLOUR[2] * l};
    }

    /** Lineares RGB mit Leuchtdichte 1 für 475 nm (CIE 1931: x 0,1096, y 0,0868), außerhalb sRGB abgeschnitten. */
    static double[] rgb475() {
        double x = 0.1096, y = 0.0868, X = x / y, Y = 1, Z = (1 - x - y) / y;
        double r = Math.max(0, 3.2406 * X - 1.5372 * Y - 0.4986 * Z), g = Math.max(0, -0.9689 * X + 1.8758 * Y + 0.0415 * Z),
                b = Math.max(0, 0.0557 * X - 0.2040 * Y + 1.0570 * Z);
        // reines Spektralblau liegt weit außerhalb sRGB; mit etwas Grün mischen, wie es das Auge im Dunkeln sieht
        g = Math.max(g, 0.35 * b);
        double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return new double[]{r / l, g / l, b / l};
    }
}
