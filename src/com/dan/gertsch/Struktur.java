package com.dan.gertsch;

/**
 * Strukturtensor (Förstner und Gülch 1987, Bigün und Granlund 1987): je Bildpunkt die über eine Umgebung gemittelte
 * Matrix J = [gx² gx·gy; gx·gy gy²] der Helligkeitsgradienten. Ihre Eigenwerte λ1 ≥ λ2 sagen, ob dort Linien in einer
 * Richtung laufen (λ1 ≫ λ2: Halme, Zweige, Stämme) oder Flecken ohne Richtung liegen (λ1 ≈ λ2: Laub). Die Linie läuft
 * quer zum Gradienten.
 */
public final class Struktur {
    private Struktur() { }

    /**
     * Rechnet den Tensor auf {@code f} (w × h): erst mit σ_D geglättet, Gradienten aus zentralen Differenzen, die
     * Produkte mit σ_I gemittelt. Ergebnis: {Winkel der Linien in Grad 0..180 (0 waagerecht, 90 senkrecht, 45 von links
     * unten nach rechts oben), λ1 − λ2 (gerichtete Energie), λ1 + λ2 (ganze Energie)}.
     */
    public static float[][] tensor(float[] f, int w, int h, double sigmaD, double sigmaI) {
        float[] s = gauss(f, w, h, sigmaD);
        int n = w * h;
        float[] xx = new float[n], yy = new float[n], xy = new float[n];
        for (int y = 0; y < h; y++) {
            int ym = Math.max(0, y - 1), yp = Math.min(h - 1, y + 1);
            for (int x = 0; x < w; x++) {
                int xm = Math.max(0, x - 1), xp = Math.min(w - 1, x + 1), i = y * w + x;
                float gx = (s[y * w + xp] - s[y * w + xm]) / Math.max(1, xp - xm);
                float gy = (s[yp * w + x] - s[ym * w + x]) / Math.max(1, yp - ym);
                xx[i] = gx * gx;
                yy[i] = gy * gy;
                xy[i] = gx * gy;
            }
        }
        xx = gauss(xx, w, h, sigmaI);
        yy = gauss(yy, w, h, sigmaI);
        xy = gauss(xy, w, h, sigmaI);
        float[] winkel = new float[n], gerichtet = new float[n], ganz = new float[n];
        for (int i = 0; i < n; i++) {
            double d = xx[i] - yy[i], diff = Math.sqrt(d * d + 4.0 * xy[i] * xy[i]);
            // Richtung des Gradienten im Bild (y nach unten): φ = ½·atan2(2 Jxy, Jxx − Jyy); die Linie steht senkrecht
            // dazu. Mit y nach oben gezählt (wie in der Mathematik) ist ihr Winkel 90° − φ.
            double phi = 0.5 * Math.toDegrees(Math.atan2(2.0 * xy[i], d)), a = 90 - phi;
            a %= 180;
            if (a < 0) a += 180;
            winkel[i] = (float) a;
            gerichtet[i] = (float) diff;
            ganz[i] = xx[i] + yy[i];
        }
        return new float[][]{winkel, gerichtet, ganz};
    }

    /** Gaußsche Glättung, getrennt in x und y, Kern bis 3σ, am Rand gespiegelt. σ ≤ 0: Kopie. */
    public static float[] gauss(float[] f, int w, int h, double sigma) {
        if (sigma <= 0) return f.clone();
        int r = Math.max(1, (int) Math.ceil(3 * sigma));
        float[] k = new float[2 * r + 1];
        double sum = 0;
        for (int i = -r; i <= r; i++) sum += k[i + r] = (float) Math.exp(-0.5 * i * i / (sigma * sigma));
        for (int i = 0; i < k.length; i++) k[i] /= (float) sum;
        float[] t = new float[w * h], o = new float[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float s = 0;
                for (int i = -r; i <= r; i++) s += k[i + r] * f[y * w + spiegel(x + i, w)];
                t[y * w + x] = s;
            }
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float s = 0;
                for (int i = -r; i <= r; i++) s += k[i + r] * t[spiegel(y + i, h) * w + x];
                o[y * w + x] = s;
            }
        return o;
    }

    static int spiegel(int i, int n) {
        if (n == 1) return 0;
        while (i < 0 || i >= n) i = i < 0 ? -i : 2 * n - 2 - i;
        return i;
    }
}
