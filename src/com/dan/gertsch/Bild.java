package com.dan.gertsch;

/**
 * Ein Bild in Lab: drei Ebenen L*, a*, b* als {@code float}, zeilenweise, Breite {@link #w}, Höhe {@link #h}.
 * {@link #aus} verkleinert dabei auf eine Arbeitsbreite (Mittel im linearen Licht, wie es eine Kamera mit größeren
 * Bildpunkten sähe), damit die Messung für große Vorlagen nicht zu lange braucht und Größen in Anteilen der Bildbreite
 * vergleichbar bleiben.
 */
public final class Bild {
    public final int w, h;
    public final float[] L, a, b;

    public Bild(int w, int h) {
        this.w = w;
        this.h = h;
        L = new float[w * h];
        a = new float[w * h];
        b = new float[w * h];
    }

    /**
     * Aus ARGB-Bildpunkten (zeilenweise, w × h). Ist das Bild breiter als {@code zielbreite}, wird es auf diese Breite
     * verkleinert (Kastenmittel im linearen RGB, Seitenverhältnis bleibt); schmalere Bilder bleiben, wie sie sind.
     */
    public static Bild aus(int[] argb, int w, int h, int zielbreite) {
        int bw = Math.min(w, Math.max(1, zielbreite));
        int bh = bw == w ? h : Math.max(1, (int) Math.round(h * (double) bw / w));
        double[] sum = new double[bw * bh * 3];
        int[] n = new int[bw * bh];
        for (int y = 0; y < h; y++) {
            int ty = (int) ((long) y * bh / h);
            for (int x = 0; x < w; x++) {
                int tx = (int) ((long) x * bw / w), t = ty * bw + tx, p = argb[y * w + x];
                sum[3 * t] += Lab.linear(p >> 16);
                sum[3 * t + 1] += Lab.linear(p >> 8);
                sum[3 * t + 2] += Lab.linear(p);
                n[t]++;
            }
        }
        Bild bild = new Bild(bw, bh);
        double[] o = new double[3];
        for (int t = 0; t < bw * bh; t++) {
            double k = 1.0 / Math.max(1, n[t]);
            Lab.vonLinear(sum[3 * t] * k, sum[3 * t + 1] * k, sum[3 * t + 2] * k, o);
            bild.L[t] = (float) o[0];
            bild.a[t] = (float) o[1];
            bild.b[t] = (float) o[2];
        }
        return bild;
    }

    /** Zurück nach sRGB (0xRRGGBB je Bildpunkt), etwa zum Zeigen. */
    public int[] rgb() {
        int[] o = new int[w * h];
        for (int i = 0; i < o.length; i++) o[i] = Lab.zuRgb(L[i], a[i], b[i]);
        return o;
    }
}
