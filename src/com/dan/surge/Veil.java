package com.dan.surge;

/**
 * Schleier am Auflauf (aus LituyaBay1958, Werkbuch Welle W5, E11 „524 m“) [G]: wo die Zunge am Hang ihre höchste Linie
 * erreicht (nasse Zellen am Rand zum Trockenen, Grund über {@link #minBed}), wirft sie Wasser hangauf und in die Luft; es
 * fliegt mit Luftwiderstand im Bogen, bremst, fällt und zieht dabei Fäden. Je Randpunkt (alle {@link #stride} Zellen)
 * eine Garbe aus {@link #perPoint} Fäden mit ähnlicher Richtung: so stehen Vorhänge statt Gesprenkel in der Luft.
 * <p>
 * Ort, Zeit und Richtung aus der Rechnung (Ankunft, höchster Stand, Gefälle der Ankunft; wo trocken, hangauf nach dem
 * Grund); Wurf (0,35–0,8 des Tempos der Front, 4–45 m/s), Streuung und Steilheit gestaltet. Ergebnis je Faden
 * {@link #FL} Werte: Abwurf x, y, z, Zeit (s) · Geschwindigkeit x, y, z, Kennung; die Bahn rechnet der Eckenformer
 * ({@code gl/veil.vert}: linearer Luftwiderstand k = 0,55/s und Schwere). Eine Garbe am selben Samen ist dieselbe.
 */
public final class Veil {
    /** Werte je Faden: Abwurf x y z, Zeit · Geschwindigkeit x y z, Kennung. */
    public static final int FL = 8;
    /** Fäden je Randpunkt, Abstand der Randpunkte (Zellen), Grund (m), ab dem der Rand als Auflauf am Hang zählt, größte
     *  Tiefe der Zunge (m), Samen. */
    public int perPoint = 40, stride = 2;
    public double minBed = 60, maxDepth = 25;
    public long seed = 524;
    /** Luftwiderstand (1/s) und Schwere (m/s²) des Eckenformers (für Prüfungen und Bahnen auf dem Prozessor). */
    public static final double K = 0.55, G = 9.81;

    /** Mitte der Zelle (i, j) in der Szene (x, z); Zeilen laufen nach Norden. */
    @FunctionalInterface public interface Centre { double[] at(int i, int j); }

    /** Mitte der Zellen eines Gitters mit Westkante x0 und Südkante zSouth. */
    public static Centre grid(double x0, double zSouth, double cell) {
        return (i, j) -> new double[]{x0 + (i + 0.5) * cell, zSouth - (j + 0.5) * cell};
    }

    /**
     * Die Fäden aus dem höchsten Stand, der Ankunft und dem Grund eines Gitters (nx × nz, Zelle cell, NaN: trocken bzw.
     * nie); Ergebnis {@link #FL} Werte je Faden.
     */
    public float[] threads(float[] mx, float[] an, float[] bd, int nx, int nz, double c, Centre centre) {
        java.util.Random rnd = new java.util.Random(seed);
        float[] out = new float[FL * perPoint * 8192];
        int m = 0, d = 3;
        for (int j = d; j < nz - d; j += stride)
            for (int i = d; i < nx - d; i += stride) {
                int q = j * nx + i;
                if (Float.isNaN(mx[q]) || Float.isNaN(an[q]) || bd[q] < minBed) continue;
                // Rand zum Trockenen: ein Nachbar in d Zellen trocken
                boolean rand = Float.isNaN(mx[q + d]) || Float.isNaN(mx[q - d]) || Float.isNaN(mx[q + d * nx]) || Float.isNaN(mx[q - d * nx]);
                if (!rand) continue;
                double tief = mx[q] - bd[q];
                if (tief > maxDepth) continue;
                // Laufrichtung: Gefälle der Ankunft (wo trocken: hangauf nach dem Grund)
                double ge = 0, gn = 0;
                float ae = an[q + 1], aw = an[q - 1], anN = an[q + nx], as = an[q - nx];
                if (!Float.isNaN(ae) && !Float.isNaN(aw)) ge = (ae - aw) / (2 * c);
                else ge = (bd[q + 1] - bd[q - 1]) / (2 * c) * 0.02;
                if (!Float.isNaN(anN) && !Float.isNaN(as)) gn = (anN - as) / (2 * c);
                else gn = (bd[q + nx] - bd[q - nx]) / (2 * c) * 0.02;
                double gl = Math.hypot(ge, gn);
                if (gl < 1e-6) continue;
                double tempo = Math.max(4, Math.min(45, 1 / Math.max(gl, 1e-3)));   // Front (m/s)
                double ux = ge / gl, uz = -gn / gl;   // Szene: x Ost, z Süd
                double[] s = centre.at(i, j);
                // je Randpunkt eine Garbe: gemeinsame Richtung, Streuung je Faden
                double wurf = (0.35 + 0.45 * rnd.nextDouble()) * tempo, steil = 0.7 + 0.8 * rnd.nextDouble();
                for (int f = 0; f < perPoint; f++) {
                    if (m + FL > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                    double a = (rnd.nextDouble() - 0.5) * 0.5, v = wurf * (0.55 + 0.6 * rnd.nextDouble());
                    double cx = ux * Math.cos(a) - uz * Math.sin(a), cz = ux * Math.sin(a) + uz * Math.cos(a);
                    out[m++] = (float) (s[0] + (rnd.nextDouble() - 0.5) * 2 * c * stride);
                    out[m++] = (float) (mx[q] + rnd.nextDouble() * Math.min(tief, 6));
                    out[m++] = (float) (s[1] + (rnd.nextDouble() - 0.5) * 2 * c * stride);
                    out[m++] = (float) (an[q] + 0.2 + rnd.nextDouble() * 1.6);
                    out[m++] = (float) (cx * v);
                    out[m++] = (float) (v * steil * (0.6 + 0.6 * rnd.nextDouble()) + 4);
                    out[m++] = (float) (cz * v);
                    out[m++] = rnd.nextFloat();
                }
            }
        return java.util.Arrays.copyOf(out, m);
    }

    /** Ort eines Fadens τ s nach dem Abwurf (wie veil.vert): Abwurf p0, Geschwindigkeit v0. */
    public static double[] position(double[] p0, double[] v0, double tau) {
        double e = (1 - Math.exp(-K * tau)) / K;
        return new double[]{p0[0] + v0[0] * e, p0[1] + v0[1] * e - G / K * (tau - e), p0[2] + v0[2] * e};
    }

    /** Lebensdauer eines Fadens mit Kennung k (s, wie veil.vert). */
    public static double life(double k) { return 4.5 + 4.0 * k; }
}
