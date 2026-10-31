package com.dan.lamp;

import java.util.Locale;

/**
 * Lichtfarben der Lampen aus ihrem Spektrum (seit Timmendorf Phase 11, F18 A): Glühen nach Planck, Linien und Banden der
 * Entladungslampen, Leuchtstoff der LED; über die Normspektralwerte CIE 1931 (2°, Näherung nach Wyman, Sloan und Shirley
 * 2013, JCGT 2(2)) zu XYZ und linearem sRGB (D65) mit Leuchtdichte 1. Eigene Kopie der Formeln, damit das Package für
 * sich steht (Paketvertrag Punkt 1).
 * <p>
 * Spektren [Lage der Linien nach den Datenblättern der Lampen und dem NIST-Atomspektrenverzeichnis; Breiten und Gewichte
 * gestaltet G]: Natriumdampf-Hochdruck – die D-Linie selbst absorbiert, Flügel bei 568 und 615 nm; Natriumdampf-Niederdruck
 * – 589 nm allein; Quecksilberdampf – 405, 436, 546, 578 nm und roter Leuchtstoff; LED – blaue Pumpe bei 450 nm und
 * breiter Leuchtstoff; Gasglühlicht – Glühstrumpf (Auer) mit Überhöhung im Grünen; Halogen und Glühlampe nach Planck.
 */
public final class Spectra {
    private Spectra() { }

    /** CIE 1931 x̄, ȳ, z̄ bei λ (nm). */
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

    /** Planck (relativ) bei λ (nm) und T (K). */
    public static double planck(double nm, double T) {
        double l = nm * 1e-9;
        return 1 / (Math.pow(l, 5) * (Math.exp(1.4388e-2 / (l * T)) - 1));
    }

    /** Spektrale Leistung (relativ, beliebiger Maßstab) einer Lampenart bei λ (nm). */
    public static double power(Lamp.Type t, double nm) {
        return switch (t) {
            case LED_3000K -> led(nm, 3000);
            case LED_4000K -> led(nm, 4000);
            case AMBER_LED -> band(nm, 592, 9, 1.0) + 0.05 * band(nm, 610, 30, 1);
            case HPS -> band(nm, 568, 7, 0.95) + band(nm, 545, 8, 0.15) + band(nm, 598, 14, 0.7) + band(nm, 615, 8, 0.45) - band(nm, 589, 3.5, 0.6)
                    + band(nm, 498, 4, 0.08) + band(nm, 466, 4, 0.05) + band(nm, 819, 6, 0.3) + 0.03 * planck(nm, 2400) / planck(700, 2400);
            case LPS -> band(nm, 589.3, 1.2, 1.0);
            case MERCURY -> band(nm, 405, 2, 0.5) + band(nm, 436, 2, 1.0) + band(nm, 546, 2, 1.1) + band(nm, 578, 2, 0.5) + band(nm, 620, 25, 0.1);
            case HALOGEN -> planck(nm, 2900) / planck(560, 2900);
            case INCANDESCENT -> planck(nm, 2700) / planck(560, 2700);
            case GAS -> planck(nm, 2600) / planck(560, 2600) + band(nm, 535, 35, 0.35);
        };
    }

    /** Weiße LED: blaue Pumpe und Leuchtstoff; die Farbtemperatur verschiebt den Leuchtstoff und den Blauanteil [G]. */
    private static double led(double nm, double cct) {
        double blue = cct >= 4000 ? 0.62 : 0.40, peak = cct >= 4000 ? 570 : 590, w = cct >= 4000 ? 60 : 58;
        return band(nm, 450, 10, blue) + band(nm, peak, w, 1.0);
    }

    private static double band(double nm, double mu, double s, double w) {
        double t = (nm - mu) / s;
        return w * Math.exp(-0.5 * t * t);
    }

    /** CIE XYZ einer Lampenart (Y = 1). */
    public static double[] xyz(Lamp.Type t) {
        return xyz(nm -> Math.max(0, power(t, nm)));
    }

    /** CIE XYZ eines Planckschen Strahlers (Y = 1). */
    public static double[] xyzPlanck(double T) {
        return xyz(nm -> planck(nm, T));
    }

    static double[] xyz(java.util.function.DoubleUnaryOperator p) {
        double X = 0, Y = 0, Z = 0;
        for (double nm = 380; nm <= 780; nm += 1) {
            double v = p.applyAsDouble(nm);
            double[] c = cmf(nm);
            X += v * c[0]; Y += v * c[1]; Z += v * c[2];
        }
        return new double[]{X / Y, 1, Z / Y};
    }

    /** Normfarbwertanteile x, y. */
    public static double[] chromaticity(double[] xyz) {
        double s = xyz[0] + xyz[1] + xyz[2];
        return new double[]{xyz[0] / s, xyz[1] / s};
    }

    /** Ähnlichste Farbtemperatur nach McCamy (1992), gut für 2 000 bis 12 500 K. */
    public static double cct(double[] xyz) {
        double[] c = chromaticity(xyz);
        double n = (c[0] - 0.3320) / (0.1858 - c[1]);
        return 449 * n * n * n + 3525 * n * n + 6823.3 * n + 5520.33;
    }

    /** Lineares sRGB mit Leuchtdichte 1; außerhalb des Farbraums zum Weiß hin geschoben (Farbton bleibt). */
    public static double[] rgb(double[] c) {
        double r = 3.2406 * c[0] - 1.5372 * c[1] - 0.4986 * c[2];
        double g = -0.9689 * c[0] + 1.8758 * c[1] + 0.0415 * c[2];
        double b = 0.0557 * c[0] - 0.2040 * c[1] + 1.0570 * c[2];
        double m = Math.min(r, Math.min(g, b));
        if (m < 0) {
            double t = -m / (1 - m);
            r = r * (1 - t) + t; g = g * (1 - t) + t; b = b * (1 - t) + t;
        }
        r = Math.max(0, r); g = Math.max(0, g); b = Math.max(0, b);
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return new double[]{r / y, g / y, b / y};
    }

    private static final double[][] TABLE = new double[Lamp.Type.values().length][];

    /** Farbe einer Lampenart, einmal gerechnet. */
    public static double[] rgb(Lamp.Type t) {
        double[] v = TABLE[t.ordinal()];
        if (v == null) TABLE[t.ordinal()] = v = rgb(xyz(t));
        return v;
    }

    private static final java.util.Map<Integer, double[]> PLANCK = new java.util.concurrent.ConcurrentHashMap<>();

    /** Farbe eines Planckschen Strahlers (auf 50 K gerundet, zwischengespeichert). */
    public static double[] rgbPlanck(double T) {
        return PLANCK.computeIfAbsent((int) Math.round(T / 50) * 50, k -> rgb(xyzPlanck(k)));
    }

    public static String describe(Lamp.Type t) {
        double[] c = xyz(t), xy = chromaticity(c);
        return String.format(Locale.GERMAN, "%s x %.3f y %.3f, %.0f K", t, xy[0], xy[1], cct(c));
    }
}
