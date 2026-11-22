package com.dan.core.pruef;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Bildvergleich für die Prüfstufe D (Werkbuch HalligHooge Teil A, 5.9): Echtzeitbild gegen die Referenz, als
 * Farbabstand ΔE₀₀ (CIEDE2000, Sharma u. a. 2005) Bildpunkt für Bildpunkt. Die Bilder sind sRGB (D65).
 * Für Leuchtdichten in cd/m² vergleicht man die Felder vor der Tonwertkurve mit {@link Statistik}.
 */
public final class Bildvergleich {
    /** Ergebnis: mittlerer, 95-%- und größter Farbabstand, Anteil der Bildpunkte über der Grenze. */
    public record Ergebnis(double mittel, double p95, double groesste, double anteilUeber, int bildpunkte) { }

    /** Vergleicht zwei gleich große Bilder; {@code grenze} ist der ΔE₀₀, ab dem ein Bildpunkt als abweichend zählt. */
    public static Ergebnis vergleiche(BufferedImage a, BufferedImage b, double grenze) {
        int w = a.getWidth(), h = a.getHeight();
        if (b.getWidth() != w || b.getHeight() != h) throw new IllegalArgumentException("Bilder verschieden groß: " + w + " × " + h + " gegen " + b.getWidth() + " × " + b.getHeight());
        double[] e = new double[w * h], la = new double[3], lb = new double[3];
        double s = 0;
        int ueber = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = a.getRGB(x, y), q = b.getRGB(x, y);
                double d = 0;
                if ((p & 0xFFFFFF) != (q & 0xFFFFFF)) {
                    lab(p, la);
                    lab(q, lb);
                    d = deltaE00(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
                }
                e[x + w * y] = d;
                s += d;
                if (d > grenze) ueber++;
            }
        Arrays.sort(e);
        return new Ergebnis(s / e.length, e[(int) Math.min(e.length - 1, Math.floor(0.95 * e.length))], e[e.length - 1], ueber / (double) e.length, e.length);
    }

    /** sRGB (0xRRGGBB) → CIE L*a*b* (D65). */
    public static void lab(int rgb, double[] ziel) {
        double r = linear((rgb >> 16 & 255) / 255.0), g = linear((rgb >> 8 & 255) / 255.0), b = linear((rgb & 255) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047, y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
                z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        ziel[0] = 116 * fy - 16;
        ziel[1] = 500 * (fx - fy);
        ziel[2] = 200 * (fy - fz);
    }

    private static double linear(double c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

    private static double f(double t) { return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116; }

    /** Farbabstand CIEDE2000 zwischen zwei Farben in L*a*b* (k_L = k_C = k_H = 1). */
    public static double deltaE00(double l1, double a1, double b1, double l2, double a2, double b2) {
        double c1 = Math.hypot(a1, b1), c2 = Math.hypot(a2, b2), cm = (c1 + c2) / 2, cm7 = Math.pow(cm, 7);
        double g = 0.5 * (1 - Math.sqrt(cm7 / (cm7 + 6103515625.0)));
        double ap1 = (1 + g) * a1, ap2 = (1 + g) * a2, cp1 = Math.hypot(ap1, b1), cp2 = Math.hypot(ap2, b2);
        double hp1 = winkel(b1, ap1), hp2 = winkel(b2, ap2);
        double dl = l2 - l1, dc = cp2 - cp1, dh;
        if (cp1 * cp2 == 0) dh = 0;
        else {
            dh = hp2 - hp1;
            if (dh > 180) dh -= 360; else if (dh < -180) dh += 360;
        }
        double dH = 2 * Math.sqrt(cp1 * cp2) * Math.sin(Math.toRadians(dh / 2));
        double lm = (l1 + l2) / 2, cpm = (cp1 + cp2) / 2, hm;
        if (cp1 * cp2 == 0) hm = hp1 + hp2;
        else if (Math.abs(hp1 - hp2) <= 180) hm = (hp1 + hp2) / 2;
        else hm = hp1 + hp2 < 360 ? (hp1 + hp2 + 360) / 2 : (hp1 + hp2 - 360) / 2;
        double t = 1 - 0.17 * Math.cos(Math.toRadians(hm - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hm)) + 0.32 * Math.cos(Math.toRadians(3 * hm + 6))
                - 0.20 * Math.cos(Math.toRadians(4 * hm - 63));
        double dTheta = 30 * Math.exp(-((hm - 275) / 25) * ((hm - 275) / 25)), cpm7 = Math.pow(cpm, 7);
        double rc = 2 * Math.sqrt(cpm7 / (cpm7 + 6103515625.0));
        double sl = 1 + 0.015 * (lm - 50) * (lm - 50) / Math.sqrt(20 + (lm - 50) * (lm - 50)), sc = 1 + 0.045 * cpm, sh = 1 + 0.015 * cpm * t;
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        double x = dl / sl, y = dc / sc, z = dH / sh;
        return Math.sqrt(x * x + y * y + z * z + rt * y * z);
    }

    private static double winkel(double b, double a) {
        if (a == 0 && b == 0) return 0;
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }

    private Bildvergleich() { }
}
