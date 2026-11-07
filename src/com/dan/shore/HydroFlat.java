package com.dan.shore;

/**
 * Erkennt „hydroflattened“ Wasser in einem Landmodell. Lidar- und IfSAR-Höhenmodelle legen Wasserflächen auf eine
 * einzige, genau gleiche Höhe (in LituyaBay1958: Lidar 2019 überall 0,800 m über NAVD88). Ein Strand dagegen fällt,
 * auch ein flacher (0,05 bedeutet 2,5 cm je halbem Meter), und das Lidar rauscht um Zentimeter. Eine Zelle gilt deshalb
 * als glattes Wasser, wenn mindestens {@code minEqual} ihrer acht Nachbarn bis auf {@code tol} dieselbe Höhe haben.
 * <p>
 * Die Regel kennt nur das Raster; welche Höhe das Wasser hat, muss sie nicht wissen. Am Rand des Rasters zählen nur die
 * vorhandenen Nachbarn (darum mit einem Rand rechnen, wenn es auf die äußersten Zellen ankommt). NaN ist nie Wasser.
 */
public final class HydroFlat {
    private HydroFlat() { }

    /** Vorgaben: 2 mm, fünf von acht Nachbarn. */
    public static final float TOL = 0.002f;
    public static final int MIN_EQUAL = 5;

    /** Maske glatter Flächen (Zeilen nacheinander, w Zellen je Zeile, h Zeilen). */
    public static boolean[] mask(float[] v, int w, int h, float tol, int minEqual) { return mask(v, null, null, w, h, tol, minEqual); }

    public static boolean[] mask(float[] v, int w, int h) { return mask(v, null, null, w, h, TOL, MIN_EQUAL); }

    /**
     * Wie oben, aber nur Nachbarn derselben Quelle zählen (group je Zelle, etwa 0 = Lidar, 1 = IfSAR; null: alle eine).
     * LituyaBay1958 W6 Runde 8: Wo das Lidar endet und das IfSAR beginnt, liegen zwei glatte Wasserflächen in
     * verschiedener Höhe nebeneinander (0,80 m und eine andere). Ohne Quellen sah die Grenze nicht glatt aus, wurde Land
     * und bekam eine Böschung: ein Rücken mitten im Wasser. Mit Quellen gilt: glatt, wenn mindestens
     * min(minEqual, gleiche Quelle − 1) Nachbarn derselben Quelle gleich hoch sind und es mindestens drei davon gibt.
     */
    public static boolean[] mask(float[] v, int[] group, int w, int h, float tol, int minEqual) { return mask(v, group, null, w, h, tol, minEqual); }

    /**
     * Wie oben, die Quellen als ein Byte je Zelle (seit LituyaBay1958 W9c Runde 2b: ein Viertel des Speichers; eine Kachel
     * des Kerns mit Rand hat 6,5 Mio. Zellen, das sind 26 MB als int, 6,5 MB als byte). Gleiches Ergebnis wie mit int.
     */
    public static boolean[] mask(float[] v, byte[] group, int w, int h, float tol, int minEqual) { return mask(v, null, group, w, h, tol, minEqual); }

    private static boolean[] mask(float[] v, int[] gi, byte[] gb, int w, int h, float tol, int minEqual) {
        if (v.length < (long) w * h) throw new IllegalArgumentException("Raster kleiner als " + w + " × " + h);
        boolean grouped = gi != null || gb != null;
        boolean[] m = new boolean[w * h];
        for (int j = 0; j < h; j++) {
            int j0 = Math.max(0, j - 1), j1 = Math.min(h - 1, j + 1);
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                float c = v[k];
                if (Float.isNaN(c)) continue;
                int g = gi != null ? gi[k] : gb != null ? gb[k] : 0;
                int i0 = Math.max(0, i - 1), i1 = Math.min(w - 1, i + 1), eq = 0, tot = 0;
                for (int y = j0; y <= j1; y++)
                    for (int x = i0; x <= i1; x++) {
                        if (x == i && y == j) continue;
                        int q = y * w + x;
                        if (grouped && (gi != null ? gi[q] : gb[q]) != g) continue;
                        float n = v[q];
                        if (Float.isNaN(n)) continue;
                        tot++;
                        if (Math.abs(n - c) <= tol) eq++;
                    }
                m[k] = !grouped ? eq >= minEqual : tot >= 3 && eq >= Math.min(minEqual, tot - 1);
            }
        }
        return m;
    }

    /**
     * Welche Kandidaten mit einem Anker zusammenhängen (8 Nachbarn, über Kandidaten und Anker hinweg). LituyaBay1958 W6
     * Runde 8: Strand (Land unter 3 m, nicht glatt) bleibt nur, wo er am Land über 3 m hängt; einzelne unruhige Zellen
     * mitten im Wasser (Rauschen, Ränder der Messung) werden so nicht zu Inseln.
     * <p>
     * Seit W9c Runde 2b sparsam: Die Anker werden der Reihe nach angefangen statt alle auf einmal auf den Stapel gelegt, der
     * Stapel wächst nur mit der Front der Suche (vorher ein int je Zelle, 26 MB für eine Kachel des Kerns mit Rand), und das
     * Ergebnis steht im Feld der besuchten Zellen. Gleiches Ergebnis wie vorher.
     */
    public static boolean[] attached(boolean[] candidate, boolean[] anchor, int w, int h) {
        int n = w * h;
        boolean[] seen = new boolean[n];
        int[] stack = new int[1024];
        for (int a = 0; a < n; a++) {
            if (!anchor[a] || seen[a]) continue;
            seen[a] = true;
            int sp = 0;
            stack[sp++] = a;
            while (sp > 0) {
                int k = stack[--sp], i = k % w, j = k / w;
                for (int y = Math.max(0, j - 1); y <= Math.min(h - 1, j + 1); y++)
                    for (int x = Math.max(0, i - 1); x <= Math.min(w - 1, i + 1); x++) {
                        int q = y * w + x;
                        if (seen[q] || !candidate[q]) continue;
                        seen[q] = true;
                        if (sp == stack.length) stack = java.util.Arrays.copyOf(stack, Math.min(n, stack.length * 2));
                        stack[sp++] = q;
                    }
            }
        }
        for (int k = 0; k < n; k++) seen[k] &= candidate[k];
        return seen;
    }

    /** Anteil glatter Zellen unter den Zellen mit Wert. */
    public static double share(boolean[] m, float[] v) {
        long n = 0, f = 0;
        for (int i = 0; i < m.length; i++) if (!Float.isNaN(v[i])) { n++; if (m[i]) f++; }
        return n == 0 ? 0 : f / (double) n;
    }
}
