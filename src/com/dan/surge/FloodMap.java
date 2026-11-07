package com.dan.surge;

/**
 * Die Flutkarte einer Riesenwelle: je Zelle die Ankunft der Front (s Ereigniszeit, NaN: nie) und der höchste Stand des
 * Wassers (m, NaN: trocken), auf einem Gitter mit Zeilen nach Norden (wie die Gitter eines Lösers in UTM). Aus ihr kommen
 * die Linie der Front zu jeder Zeit, ihre Laufrichtung (Gefälle der Ankunft) und ihr Tempo, die Höhe der Welle, die
 * Hüllen der Gischt; die Shader lesen sie als Textur RG32F (surge_flood.glsl).
 * <p>
 * Die Karte stammt aus einer Vorrechnung (LituyaBay1958: drei geschachtelte Gitter des HLL-Lösers aus
 * {@code com.dan.wave}, das feinste zuerst, zu 8 m zusammengelegt) oder aus einem Lauf, der Ankunft und höchsten Stand je
 * Zelle mitschreibt. Das Package kennt keinen Löser.
 */
public final class FloodMap {
    /** Zellen je Richtung, Zelle (m), x der Westkante, z der Südkante (Szene: x nach Ost, z nach Süd). */
    public final int nx, nz;
    public final double cell, x0, zSouth;
    /** Ankunft (s) und höchster Stand (m) je Zelle, Zeile j läuft nach Norden: Index j · nx + i. */
    public final float[] arrival, stage;

    private float[] smoothFrom, smoothed;
    private int blockSize;
    private float[] blockMin, blockMax, blockStage;

    public FloodMap(int nx, int nz, double cell, double x0, double zSouth, float[] arrival, float[] stage) {
        if (arrival.length != nx * nz || stage.length != nx * nz) throw new IllegalArgumentException("surge: Flutkarte " + nx + " × " + nz);
        this.nx = nx; this.nz = nz; this.cell = cell; this.x0 = x0; this.zSouth = zSouth;
        this.arrival = arrival; this.stage = stage;
    }

    /** Mitte der Zelle in Bruchteilen (fi, fj): Szene x, z. */
    public double x(double fi) { return x0 + (fi + 0.5) * cell; }
    public double z(double fj) { return zSouth - (fj + 0.5) * cell; }

    /** Rechteck für die Shader: x0, z der Südkante, Breite, Tiefe (m). */
    public double[] rect() { return new double[]{x0, zSouth, nx * cell, nz * cell}; }

    /**
     * Trockene Zellen bekommen in rounds Runden die früheste Ankunft ihrer nassen Nachbarn (der Stand bleibt trocken): so
     * reicht die Linie der Front bis an den Rand des Nassen, und das Gefälle der Ankunft ist am Ufer nicht abgeschnitten.
     */
    public FloodMap fillDry(int rounds) {
        for (int runde = 0; runde < rounds; runde++) {
            float[] B = arrival.clone();
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (!Float.isNaN(arrival[c])) continue;
                    float best = Float.NaN;
                    for (int dj = -1; dj <= 1; dj++)
                        for (int di = -1; di <= 1; di++) {
                            int ii = i + di, jj = j + dj;
                            if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                            float v = arrival[jj * nx + ii];
                            if (!Float.isNaN(v) && !(v >= best)) best = v;
                        }
                    B[c] = best;
                }
            System.arraycopy(B, 0, arrival, 0, arrival.length);
        }
        smoothFrom = null;
        blockMin = null;
        return this;
    }

    /** Die Werte für die Textur RG32F: Ankunft (1e6 für nie), Stand (−1000 für trocken). */
    public float[] textureData() {
        float[] b = new float[nx * nz * 2];
        for (int c = 0; c < nx * nz; c++) {
            b[2 * c] = Float.isNaN(arrival[c]) ? 1e6f : arrival[c];
            b[2 * c + 1] = Float.isNaN(stage[c]) ? -1000f : stage[c];
        }
        return b;
    }

    /**
     * Je Block zu size × size Zellen: früheste und späteste Ankunft und höchster Stand der Zellen, die über level + 2 m
     * standen (für Hüllen und das schnelle Verwerfen ganzer Blöcke). Felder mit (nx + size − 1)/size Spalten.
     */
    public void blocks(int size, double level) {
        int ftx = (nx + size - 1) / size, ftz = (nz + size - 1) / size;
        float[] fa = new float[ftx * ftz], fb = new float[ftx * ftz], fm = new float[ftx * ftz];
        java.util.Arrays.fill(fa, Float.MAX_VALUE);
        java.util.Arrays.fill(fb, -Float.MAX_VALUE);
        java.util.Arrays.fill(fm, -Float.MAX_VALUE);
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                float a = Float.isNaN(arrival[c]) ? 1e6f : arrival[c], m = Float.isNaN(stage[c]) ? -1000f : stage[c];
                if (a < 1e5f && m > level + 2) {
                    int f = (j / size) * ftx + i / size;
                    fa[f] = Math.min(fa[f], a);
                    fb[f] = Math.max(fb[f], a);
                    fm[f] = Math.max(fm[f], m);
                }
            }
        blockSize = size;
        blockMin = fa;
        blockMax = fb;
        blockStage = fm;
    }

    public int blockSize() { return blockSize; }
    public float[] blockMin() { return blockMin; }
    public float[] blockMax() { return blockMax; }
    public float[] blockStage() { return blockStage; }

    /**
     * Hülle der Gischt zur Zeit t: Blöcke, an denen die Front bis t + 0,5 s angekommen ist und die nicht länger als
     * afterglow s her ist; {x0, z Nord, x1, z Süd, y0, y1} oder null. Vorher {@link #blocks}.
     */
    public double[] sprayBox(double t, double level, double afterglow) {
        float[] fa = blockMin, fb = blockMax, fm = blockStage;
        if (fa == null) return null;
        int ftx = (nx + blockSize - 1) / blockSize, ftz = (nz + blockSize - 1) / blockSize;
        double x0b = 1e30, x1 = -1e30, zS = -1e30, zN = 1e30, top = -1e30;
        double fz = blockSize * cell;
        for (int j = 0; j < ftz; j++)
            for (int i = 0; i < ftx; i++) {
                int f = j * ftx + i;
                if (!(fa[f] <= t + 0.5) || !(fb[f] >= t - afterglow)) continue;
                double xa = x0 + i * fz, za = zSouth - j * fz;
                x0b = Math.min(x0b, xa); x1 = Math.max(x1, xa + fz);
                zS = Math.max(zS, za); zN = Math.min(zN, za - fz);
                top = Math.max(top, fm[f]);
            }
        if (x0b > x1) return null;
        return new double[]{x0b, zN, x1, zS, level - 10, top + 140};
    }

    /**
     * Die Ankunft geglättet (getrennt in x und z über ±r Zellen, nur über gültige Zellen; ungültig 1e6). Die Ankunft einer
     * Rechnung liegt in Stufen ihrer Bilder; ihre Linie wäre eine Treppe. Einmal gerechnet, dann gemerkt.
     */
    public float[] smoothedArrival(int r) {
        float[] A = arrival;
        if (smoothFrom == A && smoothed != null) return smoothed;
        float[] B = new float[A.length], C = new float[A.length];
        for (int pass = 0; pass < 2; pass++) {
            float[] src = pass == 0 ? A : B, dst = pass == 0 ? B : C;
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (A[c] > 1e5f || Float.isNaN(A[c])) { dst[c] = 1e6f; continue; }
                    double sum = 0; int n = 0;
                    for (int k = -r; k <= r; k++) {
                        int ii = pass == 0 ? i + k : i, jj = pass == 0 ? j : j + k;
                        if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                        float v = src[jj * nx + ii];
                        if (v > 1e5f || Float.isNaN(v)) continue;
                        sum += v; n++;
                    }
                    dst[c] = n > 0 ? (float) (sum / n) : 1e6f;
                }
        }
        smoothFrom = A;
        smoothed = C;
        return C;
    }

    /** Ankunft, Stand bilinear an der Stelle (fi, fj) in Zellen; NaN außerhalb. */
    public static double bilinear(float[] R, int nx, int nz, double fi, double fj) {
        int i = (int) Math.floor(fi), j = (int) Math.floor(fj);
        if (i < 0 || j < 0 || i + 1 >= nx || j + 1 >= nz) return Double.NaN;
        double a = fi - i, b = fj - j;
        float v00 = R[j * nx + i], v10 = R[j * nx + i + 1], v01 = R[(j + 1) * nx + i], v11 = R[(j + 1) * nx + i + 1];
        return (1 - b) * ((1 - a) * v00 + a * v10) + b * ((1 - a) * v01 + a * v11);
    }

    /**
     * Eine Karte aus einer Funktion: Ankunft und Stand je Szenenort (für Prüfungen, Vorführungen und Läufe, die beides
     * schon als Feld haben). f(x, z) gibt {Ankunft, Stand} oder null (nie erreicht).
     */
    public static FloodMap of(int nx, int nz, double cell, double x0, double zSouth, java.util.function.BiFunction<Double, Double, double[]> f) {
        float[] A = new float[nx * nz], M = new float[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                double[] v = f.apply(x0 + (i + 0.5) * cell, zSouth - (j + 0.5) * cell);
                A[j * nx + i] = v == null ? Float.NaN : (float) v[0];
                M[j * nx + i] = v == null ? Float.NaN : (float) v[1];
            }
        return new FloodMap(nx, nz, cell, x0, zSouth, A, M);
    }
}
