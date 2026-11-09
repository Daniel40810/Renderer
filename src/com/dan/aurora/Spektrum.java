package com.dan.aurora;

/**
 * Von Rayleigh zu Licht: Die vier Linien werden mit den Normspektralwertfunktionen CIE 1931 (2°) zu XYZ und weiter zu
 * linearem sRGB (D65), dessen Y die Leuchtdichte in cd/m² ist. 1 kR in 557,7 nm sind 2,83·10⁻⁷ W/m²/sr und
 * 1,93·10⁻⁴ cd/m²: IBC III (100 kR) leuchtet wie eine Wolke im Mondlicht. Dazu die Nachtsicht: Stäbchen sehen mit der
 * skotopischen Kurve V′(λ), für die 630 nm fast unsichtbar ist, und die Kamera mit Belichtungszeit, ISO und Blende.
 */
public final class Spektrum {
    private Spektrum() { }

    /** CIE 1931 2° an den vier Wellenlängen (427,8 · 557,7 · 630,0 · 670 nm). */
    static final double[] X = {0.2535, 0.5570, 0.6424, 0.2187}, Y = {0.0097, 0.9975, 0.2650, 0.0816}, Z = {1.2330, 0.0047, 0.0000, 0.0000};
    /** Skotopische Empfindlichkeit V′(λ), Maximum 1 bei 507 nm. */
    static final double[] V_SKOTOPISCH = {0.177, 0.365, 0.0033, 0.0002};
    /** Maximale Lichtausbeuten: photopisch 683 lm/W bei 555 nm, skotopisch 1700 lm/W bei 507 nm. */
    public static final double KM = 683, KM_SKOTOPISCH = 1700;
    /** W/m²/sr je kR für jede Linie: 10⁹/(4π) Photonen je cm², s und sr · 10⁴ · hc/λ. */
    public static final double[] WATT_JE_KR = new double[Emission.LINIEN];

    static {
        double h = 6.62607e-34, c = 2.99792e8;
        for (int l = 0; l < Emission.LINIEN; l++) WATT_JE_KR[l] = 1e9 / (4 * Math.PI) * 1e4 * h * c / (Emission.WELLENLAENGE[l] * 1e-9);
    }

    /** Photopische Leuchtdichte in cd/m² aus kR je Linie. */
    public static double leuchtdichte(double[] kr) {
        double y = 0;
        for (int l = 0; l < Emission.LINIEN; l++) y += kr[l] * WATT_JE_KR[l] * Y[l];
        return KM * y;
    }

    /** Skotopische Leuchtdichte in cd/m² (Stäbchen) aus kR je Linie. */
    public static double leuchtdichteSkotopisch(double[] kr) {
        double y = 0;
        for (int l = 0; l < Emission.LINIEN; l++) y += kr[l] * WATT_JE_KR[l] * V_SKOTOPISCH[l];
        return KM_SKOTOPISCH * y;
    }

    /** XYZ in cd/m² aus kR je Linie. */
    public static void xyz(double[] kr, double[] out) {
        double x = 0, y = 0, z = 0;
        for (int l = 0; l < Emission.LINIEN; l++) {
            double w = kr[l] * WATT_JE_KR[l] * KM;
            x += w * X[l];
            y += w * Y[l];
            z += w * Z[l];
        }
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    /** XYZ → lineares sRGB (D65), dann in den Farbraum gezogen: negative Anteile mit Weiß aufgefüllt, Y bleibt. */
    public static void xyzZuRgb(double x, double y, double z, double[] rgb) {
        double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double b = 0.0557 * x - 0.2040 * y + 1.0570 * z;
        double min = Math.min(r, Math.min(g, b));
        if (min < 0) {
            r -= min;
            g -= min;
            b -= min;
            double y2 = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            if (y2 > 0) {
                double k = y / y2;
                r *= k;
                g *= k;
                b *= k;
            }
        }
        rgb[0] = r;
        rgb[1] = g;
        rgb[2] = b;
    }

    /** Lineares sRGB in cd/m² aus kR je Linie. */
    public static void rgb(double[] kr, double[] rgb) {
        double[] t = new double[3];
        xyz(kr, t);
        xyzZuRgb(t[0], t[1], t[2], rgb);
    }

    /** Farbe einer Linie allein, lineares sRGB mit Y = 1. */
    public static double[] farbe(int linie) {
        double[] rgb = new double[3];
        xyzZuRgb(X[linie] / Y[linie], 1, Z[linie] / Y[linie], rgb);
        return rgb;
    }

    /** Skotopische Leuchtdichte, grob aus linearem sRGB (cd/m²): Blau und Grün zählen für die Stäbchen, Rot kaum; Weiß hat S/P 2,5. */
    public static double skotopischAusRgb(double r, double g, double b) {
        return 0.07 * r + 1.1 * g + 1.33 * b;
    }

    /**
     * Kamera: lineares sRGB in cd/m² → Anzeige 0..255. Belichtung nach der Lichtwertformel: L·t·S/(12,5·N²) = 1 gibt
     * Mittelgrau (18 %); die Lichter laufen weich aus (1 − e^(−v)).
     */
    public static int kamera(double r, double g, double b, double zeitS, double iso, double blende) {
        double k = 0.18 * zeitS * iso / (12.5 * blende * blende);
        return pack(knie(r * k), knie(g * k), knie(b * k));
    }

    static double knie(double v) { return v <= 0 ? 0 : 1 - Math.exp(-v); }

    /**
     * Auge bei Nacht: Unter 10⁻³ cd/m² sehen nur die Stäbchen (grau, Rot verschwindet), bis 0,3 cd/m² kommt die Farbe
     * dazu. {@code anpassung} ist die Leuchtdichte in cd/m², die als Weiß erscheint (0,3 für das dunkeladaptierte Auge).
     */
    public static int auge(double r, double g, double b, double anpassung) {
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        if (y <= 0) return 0;
        double ys = skotopischAusRgb(r, g, b) / 2.5;
        double s = glatt((Math.log10(y) + 3) / 2.5);
        double ym = ys * (1 - s) + y * s;
        double k = ym / y;
        double rr = (ym * (1 - s) + r * k * s), gg = (ym * (1 - s) + g * k * s), bb = (ym * (1 - s) + b * k * s);
        // das dunkeladaptierte Auge antwortet flacher als linear; die sRGB-Kennlinie kommt in pack dazu
        double e = 0.6;
        return pack(Math.pow(Math.max(0, rr / anpassung), e), Math.pow(Math.max(0, gg / anpassung), e), Math.pow(Math.max(0, bb / anpassung), e));
    }

    static double glatt(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** sRGB-Kennlinie auf 0..1. */
    public static double gamma(double v) {
        v = Math.max(0, Math.min(1, v));
        return v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
    }

    /** Drei lineare Werte 0..1 → 0xRRGGBB mit sRGB-Kennlinie. */
    public static int pack(double r, double g, double b) {
        int ir = (int) Math.round(255 * gamma(r)), ig = (int) Math.round(255 * gamma(g)), ib = (int) Math.round(255 * gamma(b));
        return (ir << 16) | (ig << 8) | ib;
    }
}
