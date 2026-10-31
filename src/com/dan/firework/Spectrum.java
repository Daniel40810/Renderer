package com.dan.firework;

/**
 * Farben als Spektrum (seit Timmendorf Phase 11, F16 A): die Emission der farbgebenden Salze als Banden und
 * Glühlicht, über die Normspektralwerte CIE 1931 (2°) zu XYZ, dann zu linearem sRGB (D65). Die Normspektralwerte sind
 * die analytische Näherung mit mehreren Gauß-Keulen von Wyman, Sloan und Shirley (2013, JCGT 2(2)), auf rund 1 %
 * genau. Glühen nach Planck.
 * <p>
 * Banden [Lage nach Kosanke und Kosanke (2004), Pyrotechnic Chemistry, und Shimizu (1981), Fireworks – The Art,
 * Science and Technique; Breiten und Gewichte gestaltet G]: Strontium – SrCl 636, 661, 674 nm und SrOH 606 nm (rot);
 * Calcium – CaCl 593, 606, 618 nm (orange); Natrium – die D-Linien bei 589 nm, durch Druck und Selbstabsorption
 * verbreitert, mit Glühen (gelb); Barium – BaCl 513, 524, 532 nm (grün); Kupfer – CuCl 428 bis 452 nm und 478 bis
 * 526 nm (blau); Violett aus Strontium und Kupfer; Weiß aus glühendem Magnesiumoxid und Aluminium (3 300 K); Gold aus
 * glühender Holzkohle (1 900 K).
 */
public final class Spectrum {
    private Spectrum() { }

    /** CIE 1931 x̄, ȳ, z̄ bei λ (nm), Näherung nach Wyman, Sloan und Shirley (2013). */
    public static double[] cmf(double l) {
        double x = 1.056 * g(l, 599.8, 37.9, 31.0) + 0.362 * g(l, 442.0, 16.0, 26.7) - 0.065 * g(l, 501.1, 20.4, 26.2);
        double y = 0.821 * g(l, 568.8, 46.9, 40.5) + 0.286 * g(l, 530.9, 16.3, 31.1);
        double z = 1.217 * g(l, 437.0, 11.8, 36.0) + 0.681 * g(l, 459.0, 26.0, 13.8);
        return new double[]{x, y, z};
    }

    private static double g(double l, double mu, double s1, double s2) {
        double t = (l - mu) / (l < mu ? s1 : s2);
        return Math.exp(-0.5 * t * t);
    }

    /** Planck: spektrale Strahldichte (relativ) bei λ (nm) und T (K). */
    public static double planck(double nm, double T) {
        double l = nm * 1e-9;
        return 1 / (Math.pow(l, 5) * (Math.exp(1.4388e-2 / (l * T)) - 1));
    }

    /** Banden je Salz: {Lage nm, Breite σ nm, Gewicht} …; dazu Glühen (Temperatur K, Anteil an der Strahlung). */
    static double[][] bands(Firework.Salt s) {
        return switch (s) {
            case STRONTIUM -> new double[][]{{636, 7, 1.0}, {661, 6, 0.7}, {674, 5, 0.55}, {606, 6, 0.35}};
            case CALCIUM -> new double[][]{{593, 5, 1.0}, {606, 5, 0.8}, {618, 5, 0.45}, {554, 6, 0.15}};
            case SODIUM -> new double[][]{{589.3, 3.5, 1.0}};
            case BARIUM -> new double[][]{{524, 4, 1.0}, {513, 4, 0.8}, {532, 4, 0.6}, {507, 4, 0.3}};
            case COPPER -> new double[][]{{452, 5, 1.0}, {443, 5, 0.9}, {435, 5, 0.8}, {428, 5, 0.6}, {478, 5, 0.45}, {488, 5, 0.35}, {498, 5, 0.25}, {526, 6, 0.2}};
            case VIOLET -> new double[][]{{636, 7, 0.55}, {661, 6, 0.38}, {674, 5, 0.3}, {452, 5, 1.0}, {443, 5, 0.9}, {435, 5, 0.8}, {428, 5, 0.6}};
            default -> new double[0][];
        };
    }

    /** Glühen: Temperatur (K) und Anteil der Strahlung (0..1); farbige Sterne glühen schwach mit [G]. */
    static double[] glow(Firework.Salt s) {
        return switch (s) {
            case WHITE -> new double[]{3300, 1.0};
            case GOLD -> new double[]{1900, 1.0};
            case SODIUM -> new double[]{2100, 0.25};
            default -> new double[]{2400, 0.08};
        };
    }

    /** Spektrale Leistung (relativ) eines Salzes bei λ: Banden plus Glühen, beide auf gleiche Fläche normiert. */
    public static double power(Firework.Salt s, double nm) {
        double p = 0, sum = 0;
        for (double[] b : bands(s)) sum += b[2] * b[1] * Math.sqrt(2 * Math.PI);
        for (double[] b : bands(s)) { double t = (nm - b[0]) / b[1]; p += b[2] * Math.exp(-0.5 * t * t); }
        double[] gl = glow(s);
        double band = sum > 0 ? p / sum : 0;
        double pl = planck(nm, gl[0]) / planckArea(gl[0]);
        return (1 - gl[1]) * band + gl[1] * pl;
    }

    private static double planckArea(double T) {
        double a = 0;
        for (double nm = 380; nm <= 780; nm += 2) a += planck(nm, T) * 2;
        return a;
    }

    /** CIE XYZ eines Salzes (Y = 1). */
    public static double[] xyz(Firework.Salt s) {
        double X = 0, Y = 0, Z = 0;
        for (double nm = 380; nm <= 780; nm += 1) {
            double p = power(s, nm);
            double[] c = cmf(nm);
            X += p * c[0];
            Y += p * c[1];
            Z += p * c[2];
        }
        return new double[]{X / Y, 1, Z / Y};
    }

    /** Normfarbwertanteile x, y. */
    public static double[] chromaticity(Firework.Salt s) {
        double[] c = xyz(s);
        double sum = c[0] + c[1] + c[2];
        return new double[]{c[0] / sum, c[1] / sum};
    }

    /**
     * Lineares sRGB (D65) mit Leuchtdichte 1; was außerhalb des sRGB-Raums liegt (die gesättigten Banden), wird zum
     * Weißpunkt hin verschoben, bis keine Komponente negativ ist (Farbton bleibt, die Sättigung sinkt).
     */
    public static double[] rgb(Firework.Salt s) {
        double[] c = xyz(s);
        double r = 3.2406 * c[0] - 1.5372 * c[1] - 0.4986 * c[2];
        double g = -0.9689 * c[0] + 1.8758 * c[1] + 0.0415 * c[2];
        double b = 0.0557 * c[0] - 0.2040 * c[1] + 1.0570 * c[2];
        double m = Math.min(r, Math.min(g, b));
        if (m < 0) {
            // Mischung mit Weiß gleicher Leuchtdichte (1, 1, 1), bis das Kleinste 0 ist
            double t = -m / (1 - m);
            r = r * (1 - t) + t;
            g = g * (1 - t) + t;
            b = b * (1 - t) + t;
        }
        r = Math.max(0, r);
        g = Math.max(0, g);
        b = Math.max(0, b);
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return new double[]{r / y, g / y, b / y};
    }

    /** Farbtafel aller Salze (lineares sRGB, Leuchtdichte 1), einmal gerechnet. */
    private static final double[][] TABLE = new double[Firework.Salt.values().length][];

    public static double[] rgbCached(Firework.Salt s) {
        double[] t = TABLE[s.ordinal()];
        if (t == null) TABLE[s.ordinal()] = t = rgb(s);
        return t;
    }

    /** Lichtausbeute der Sterne (lm/W der sichtbaren Strahlung) ist hier nicht gerechnet; Lichtstärken nach Literatur. */
    public static String describe(Firework.Salt s) {
        double[] c = chromaticity(s);
        return String.format(java.util.Locale.GERMAN, "%s x %.3f y %.3f", s, c[0], c[1]);
    }
}
