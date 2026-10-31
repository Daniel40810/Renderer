package com.dan.beacon;

/**
 * Die Farben der Seezeichen-Notation. Jede Farbe steht als Farbort (x, y nach CIE 1931) und als lineares RGB mit der
 * Leuchtdichte 1 (0,2126 R + 0,7152 G + 0,0722 B = 1): Multipliziert mit einer Lichtstärke in Candela ergibt sich die
 * Lichtstärke je Kanal. Farborte aus der Mitte der IALA-Bereiche für Signalfarben (E-200-1), Weiß als Glühlicht um 3 000 K;
 * was außerhalb von sRGB liegt (Rot, Grün), wird an den Rand gezogen.
 */
public enum LightColour {
    W("weiß", 0.437, 0.404), R("rot", 0.690, 0.305), G("grün", 0.200, 0.550), Y("gelb", 0.560, 0.430), BU("blau", 0.150, 0.100);

    public final String german;
    public final double x, y;
    private final double[] rgb;

    LightColour(String german, double x, double y) {
        this.german = german;
        this.x = x;
        this.y = y;
        this.rgb = toRgb(x, y);
    }

    /** Lineares RGB mit Leuchtdichte 1 (Kopie). */
    public double[] rgb() { return rgb.clone(); }

    /** Kürzel in der Notation: W, R, G, Y, Bu. */
    public String code() { return this == BU ? "Bu" : name(); }

    static double[] toRgb(double x, double y) {
        double X = x / y, Y = 1, Z = (1 - x - y) / y;
        double r = 3.2406 * X - 1.5372 * Y - 0.4986 * Z;
        double g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z;
        double b = 0.0557 * X - 0.2040 * Y + 1.0570 * Z;
        r = Math.max(0, r);
        g = Math.max(0, g);
        b = Math.max(0, b);
        double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return new double[]{r / l, g / l, b / l};
    }

    /** Leuchtdichte eines linearen RGB. */
    public static double luminance(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    /** „W“ → W, „Bu“ → BU; null, wenn unbekannt. */
    public static LightColour of(String code) {
        for (LightColour c : values()) if (c.code().equalsIgnoreCase(code)) return c;
        return null;
    }
}
