package com.dan.surge;

/**
 * Kachelbares Wertrauschen n³ für den Strahlengang ({@code gl/surge_spray.glsl}, {@code uSgNoise}): Zufall mit einem Samen, einmal
 * mit einem 3er-Kasten geglättet (kachelbar, damit die Lagen nicht körnig werden), auf 0..255 gestreckt. Mit n = 64 und
 * Samen 1958 dasselbe wie in LituyaBay1958 seit Werkbuch Welle W3 (mit Sinus-Hashes kostete der Strahl dort über 100 ms).
 */
public final class Noise {
    private Noise() { }

    /** Die Bytes (x schnell, dann y, dann z), n eine Zweierpotenz. */
    public static byte[] tile(int n, long seed) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("surge: n muss eine Zweierpotenz sein: " + n);
        float[] v = new float[n * n * n];
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < v.length; i++) v[i] = r.nextFloat();
        float[] w = new float[v.length];
        int m = n - 1;
        for (int z = 0; z < n; z++)
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    float sum = 0;
                    for (int dz = -1; dz <= 1; dz++)
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++)
                                sum += v[(((z + dz) & m) * n + ((y + dy) & m)) * n + ((x + dx) & m)];
                    w[(z * n + y) * n + x] = sum / 27;
                }
        float lo = 1, hi = 0;
        for (float f : w) { lo = Math.min(lo, f); hi = Math.max(hi, f); }
        byte[] out = new byte[w.length];
        for (int i = 0; i < w.length; i++) out[i] = (byte) Math.round((w[i] - lo) / (hi - lo) * 255);
        return out;
    }
}
