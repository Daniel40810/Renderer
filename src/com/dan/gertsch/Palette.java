package com.dan.gertsch;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Die Tonstufen eines Bilds: wenige Farben in Lab, aufsteigend nach L* (Stufe 0 ist der dunkle Grund), je mit dem
 * Anteil der Bildfläche. Gertsch mischt für ein Bild eine enge Palette; {@link #kmeans} findet sie in einer Vorlage,
 * {@link #naechste} ordnet später jedem Fleck seine Stufe zu.
 */
public final class Palette {
    /** Je Stufe {L*, a*, b*}, aufsteigend nach L*. */
    public final double[][] lab;
    /** Anteil der Bildfläche je Stufe, Summe 1. */
    public final double[] anteil;

    public Palette(double[][] lab, double[] anteil) {
        if (lab.length != anteil.length) throw new IllegalArgumentException("Stufen und Anteile ungleich lang");
        this.lab = lab;
        this.anteil = anteil;
    }

    public int size() { return lab.length; }

    /** Stufe mit dem kleinsten ΔE*ab zur Farbe. */
    public int naechste(double l, double a, double b) { return naechster(lab, l, a, b); }

    /** sRGB der Stufe (0xRRGGBB). */
    public int rgb(int i) { return Lab.zuRgb(lab[i][0], lab[i][1], lab[i][2]); }

    /** Jeder Bildpunkt in der Farbe seiner Stufe (sRGB), etwa zum Zeigen. */
    public int[] stufen(Bild bild) {
        int[] rgb = new int[size()], o = new int[bild.w * bild.h];
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb(i);
        for (int i = 0; i < o.length; i++) o[i] = rgb[naechste(bild.L[i], bild.a[i], bild.b[i])];
        return o;
    }

    /**
     * k-means in Lab (Lloyd) mit Start nach k-means++ (Arthur und Vassilvitskii 2007): auf einer Stichprobe von höchstens
     * {@code stichprobe} Bildpunkten bis zur Ruhe, danach zwei Schritte mit allen Bildpunkten. Gleicher seed, gleiche
     * Palette.
     */
    public static Palette kmeans(Bild bild, int k, long seed, int stichprobe) {
        int n = bild.w * bild.h;
        k = Math.max(1, Math.min(k, n));
        SplittableRandom rnd = new SplittableRandom(seed);
        int m = Math.min(n, Math.max(k, stichprobe));
        float[] sl = new float[m], sa = new float[m], sb = new float[m];
        for (int i = 0; i < m; i++) {
            int j = m == n ? i : rnd.nextInt(n);
            sl[i] = bild.L[j];
            sa[i] = bild.a[j];
            sb[i] = bild.b[j];
        }
        double[][] c = start(sl, sa, sb, k, rnd);
        int[] zu = new int[m];
        for (int it = 0; it < 60; it++)
            if (schritt(c, sl, sa, sb, zu) < 1e-4) break;
        // zwei Schritte mit allen Bildpunkten, damit die Stufen nicht an der Stichprobe hängen
        int[] zuAlle = new int[n];
        for (int it = 0; it < 2; it++) schritt(c, bild.L, bild.a, bild.b, zuAlle);
        double[] anteil = new double[k];
        for (int i = 0; i < n; i++) anteil[naechster(c, bild.L[i], bild.a[i], bild.b[i])] += 1.0 / n;
        Integer[] ord = new Integer[k];
        for (int i = 0; i < k; i++) ord[i] = i;
        Arrays.sort(ord, (x, y) -> Double.compare(c[x][0], c[y][0]));
        double[][] lab = new double[k][];
        double[] an = new double[k];
        for (int i = 0; i < k; i++) {
            lab[i] = c[ord[i]].clone();
            an[i] = anteil[ord[i]];
        }
        return new Palette(lab, an);
    }

    static double[][] start(float[] l, float[] a, float[] b, int k, SplittableRandom rnd) {
        int m = l.length;
        double[][] c = new double[k][];
        int first = rnd.nextInt(m);
        c[0] = new double[]{l[first], a[first], b[first]};
        double[] d2 = new double[m];
        Arrays.fill(d2, Double.MAX_VALUE);
        for (int j = 1; j < k; j++) {
            double sum = 0;
            for (int i = 0; i < m; i++) {
                double dl = l[i] - c[j - 1][0], da = a[i] - c[j - 1][1], db = b[i] - c[j - 1][2];
                d2[i] = Math.min(d2[i], dl * dl + da * da + db * db);
                sum += d2[i];
            }
            int pick = 0;
            if (sum > 0) {
                double t = rnd.nextDouble() * sum;
                for (pick = 0; pick < m - 1; pick++) if ((t -= d2[pick]) <= 0) break;
            } else pick = rnd.nextInt(m);
            c[j] = new double[]{l[pick], a[pick], b[pick]};
        }
        return c;
    }

    static int naechster(double[][] c, double l, double a, double b) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < c.length; i++) {
            double dl = l - c[i][0], da = a - c[i][1], db = b - c[i][2], d = dl * dl + da * da + db * db;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /**
     * Ein Lloyd-Schritt: zuordnen, dann Mittel je Gruppe; eine leere Gruppe bekommt den Punkt, der am weitesten von
     * seiner Mitte liegt. Gibt die größte Verschiebung (ΔE) zurück.
     */
    static double schritt(double[][] c, float[] l, float[] a, float[] b, int[] zu) {
        int k = c.length, m = l.length;
        double[][] s = new double[k][3];
        int[] n = new int[k];
        for (int i = 0; i < m; i++) {
            int j = zu[i] = naechster(c, l[i], a[i], b[i]);
            s[j][0] += l[i];
            s[j][1] += a[i];
            s[j][2] += b[i];
            n[j]++;
        }
        double shift = 0;
        for (int j = 0; j < k; j++) {
            double[] neu;
            if (n[j] > 0) neu = new double[]{s[j][0] / n[j], s[j][1] / n[j], s[j][2] / n[j]};
            else {
                int far = 0;
                double fd = -1;
                for (int i = 0; i < m; i++) {
                    double[] q = c[zu[i]];
                    double dl = l[i] - q[0], da = a[i] - q[1], db = b[i] - q[2], d = dl * dl + da * da + db * db;
                    if (d > fd) { fd = d; far = i; }
                }
                neu = new double[]{l[far], a[far], b[far]};
            }
            shift = Math.max(shift, Lab.deltaE(neu, c[j]));
            c[j] = neu;
        }
        return shift;
    }
}
