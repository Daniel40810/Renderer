package com.dan.lichen.demo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Misst Fotos von Bartflechten (seit LituyaBay1958 W6 Runde 7; dasselbe Verfahren wie com.dan.moss.demo.MossVorlage,
 * hier ohne Grünmaske, dafür die helleren 80 % der scharfen Bildpunkte): nur scharfe Bildpunkte (Laplace-Energie über dem 55. Perzentil,
 * so fällt der unscharfe Hintergrund weg); Farbe linear (sRGB entfernt). Ausgabe je
 * Foto: Anteil, Perzentile der Leuchtdichte 5/25/50/75/95 und die Farbart (R, G, B je Leuchtdichte) im dunklen, mittleren
 * und hellen Drittel. Daraus stammen die Farbarten in {@link com.dan.lichen.LichenLook} und die Grenzen in {@link LichenCheck}.
 * <pre>java com.dan.lichen.demo.LichenVorlage templates/Bartflechten1.png …</pre>
 */
public final class LichenVorlage {
    public static void main(String[] a) throws Exception {
        for (String f : a) {
            BufferedImage im = ImageIO.read(new File(f));
            if (im == null) { System.out.println(new File(f).getName() + ": nicht lesbar (WebP oder anderes Format, das ImageIO nicht kennt: als PNG speichern)"); continue; }
            System.out.println(new File(f).getName() + ": " + measure(im, false));
        }
    }

    /** Ergebnis einer Messung. */
    public static final class Result {
        public float share;
        public float[] percentiles = new float[5];
        /** [Drittel][R, G, B] je Leuchtdichte. */
        public float[][] chroma = new float[3][3];

        @Override public String toString() {
            return String.format(Locale.GERMAN, "Anteil %.2f, Leuchtdichte %s, Farbart dunkel %s, mittel %s, hell %s", share, fmt(percentiles), fmt(chroma[0]), fmt(chroma[1]), fmt(chroma[2]));
        }
    }

    static String fmt(float[] v) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < v.length; i++) sb.append(i > 0 ? " " : "").append(String.format(Locale.GERMAN, "%.3f", v[i]));
        return sb.append(')').toString();
    }

    /** Misst ein Bild; green: nur grüne Bildpunkte (Moos), sonst die helleren 80 % der scharfen (Flechten). */
    public static Result measure(BufferedImage im, boolean green) {
        int w = im.getWidth(), h = im.getHeight();
        float[] lr = new float[w * h], lg = new float[w * h], lb = new float[w * h], gray = new float[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = im.getRGB(x, y), i = y * w + x;
                int R = p >> 16 & 255, G = p >> 8 & 255, B = p & 255;
                lr[i] = lin(R); lg[i] = lin(G); lb[i] = lin(B);
                gray[i] = (R + G + B) / 3f;
            }
        float[] g1 = box(gray, w, h, 1);
        float[] lap = new float[w * h];
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float l = g1[i - 1] + g1[i + 1] + g1[i - w] + g1[i + w] - 4 * g1[i];
                lap[i] = l * l;
            }
        float[] sharp = box(box(lap, w, h, 4), w, h, 4);
        float thr = pct(sharp.clone(), 55);
        boolean[] m = new boolean[w * h];
        int n = 0;
        float[] Y = new float[w * h];
        for (int i = 0; i < w * h; i++) {
            Y[i] = 0.2126f * lr[i] + 0.7152f * lg[i] + 0.0722f * lb[i];
            m[i] = sharp[i] > thr && (!green || (lg[i] > lr[i] * 1.05f && lg[i] > lb[i] * 1.3f));
        }
        if (!green) {
            float[] ys = collect(Y, m);
            float y20 = pct(ys, 20);
            for (int i = 0; i < w * h; i++) m[i] &= Y[i] > y20;
        }
        for (boolean b : m) if (b) n++;
        Result r = new Result();
        r.share = n / (float) (w * h);
        float[] ys = collect(Y, m);
        int[] P = {5, 25, 50, 75, 95};
        for (int k = 0; k < 5; k++) r.percentiles[k] = pct(ys.clone(), P[k]);
        float a = pct(ys.clone(), 33.3f), b = pct(ys.clone(), 66.7f);
        double[][] s = new double[3][4];
        for (int i = 0; i < w * h; i++) {
            if (!m[i]) continue;
            int t = Y[i] < a ? 0 : Y[i] < b ? 1 : 2;
            s[t][0] += lr[i]; s[t][1] += lg[i]; s[t][2] += lb[i]; s[t][3] += Y[i];
        }
        for (int t = 0; t < 3; t++) for (int k = 0; k < 3; k++) r.chroma[t][k] = (float) (s[t][k] / Math.max(1e-12, s[t][3]));
        return r;
    }

    static float lin(int v) { double c = v / 255.0; return (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)); }

    static float[] collect(float[] v, boolean[] m) {
        int n = 0;
        for (boolean b : m) if (b) n++;
        float[] o = new float[n];
        int k = 0;
        for (int i = 0; i < v.length; i++) if (m[i]) o[k++] = v[i];
        return o;
    }

    static float pct(float[] v, float p) {
        if (v.length == 0) return Float.NaN;
        Arrays.sort(v);
        return v[Math.min(v.length - 1, Math.max(0, Math.round(p / 100f * (v.length - 1))))];
    }

    static float[] box(float[] a, int w, int h, int r) {
        float[] t = new float[w * h], o = new float[w * h];
        for (int y = 0; y < h; y++) {
            float s = 0;
            int n = 0;
            for (int x = -r; x < w + r; x++) {
                if (x + r < w) { s += a[y * w + x + r]; n++; }
                if (x - r - 1 >= 0) { s -= a[y * w + x - r - 1]; n--; }
                if (x >= 0 && x < w) t[y * w + x] = s / n;
            }
        }
        for (int x = 0; x < w; x++) {
            float s = 0;
            int n = 0;
            for (int y = -r; y < h + r; y++) {
                if (y + r < h) { s += t[(y + r) * w + x]; n++; }
                if (y - r - 1 >= 0) { s -= t[(y - r - 1) * w + x]; n--; }
                if (y >= 0 && y < h) o[y * w + x] = s / n;
            }
        }
        return o;
    }
}
