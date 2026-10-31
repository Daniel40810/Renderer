package com.dan.cliff;

/**
 * Ein Überhang als Zusatzgeometrie: eine Felsnase, die aus der Wand ragt, oben am weitesten, unten mit einer hohlen
 * Unterseite (wie die Abrisskante eines Falls, über die das Wasser frei fällt). Ein Höhenfeld kann das nicht; die
 * Nase wird als eigenes Netz gezeichnet, mit demselben Material wie die Wand ({@code gl/cliff.glsl}). Die Rückseite
 * steckt {@link #embed} m in der Wand, damit an der Fuge keine Lücke bleibt.
 * <p>
 * Lage: Mitte auf der Wand (x, y, z), Blick aus der Wand heraus (Grad von Nord), Breite, Höhe, größte Ausladung (m).
 */
public final class Overhang {
    public final double x, y, z, headingDeg, width, height, depth;
    public double embed = 4;
    public final int seed;

    public Overhang(double x, double y, double z, double headingDeg, double width, double height, double depth, int seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.headingDeg = headingDeg;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.seed = seed;
    }

    /** Netz mit {@code res} × {@code res} Ecken: Ecken (x, y, z, nx, ny, nz) und Dreiecke (Indizes). */
    public static final class Mesh {
        public final float[] vertices;
        public final int[] indices;
        Mesh(float[] v, int[] i) { vertices = v; indices = i; }
        public int vertexCount() { return vertices.length / 6; }
    }

    /** Ausladung (m) an der Stelle u, v ∈ [−1, 1] (quer, hoch) vor der Wand, mit Rauschen. */
    public double protrusion(double u, double v) {
        double across = Math.pow(Math.max(0, 1 - u * u), 0.55), up = Math.pow(Math.max(0, 1 - v * v), 0.45);
        double top = 0.35 + 0.65 * (v + 1) / 2;   // oben am weitesten: die Unterseite ist hohl
        double n = CNoise.value(u * 2.3 + 11, v * 2.3, seed * 0.37, seed) - 0.5;
        return Math.max(0, depth * across * up * top * (1 + 0.35 * n)) - embed * (1 - across * up);
    }

    /** Das Netz in Weltlage. */
    public Mesh mesh(int res) {
        double h = Math.toRadians(headingDeg);
        double ox = Math.sin(h), oz = -Math.cos(h);          // aus der Wand heraus
        double rx = Math.cos(h), rz = Math.sin(h);           // quer, rechts
        float[] p = new float[res * res * 3];
        for (int j = 0; j < res; j++)
            for (int i = 0; i < res; i++) {
                double u = -1 + 2.0 * i / (res - 1), v = -1 + 2.0 * j / (res - 1);
                double o = protrusion(u, v) - embed * 0.5;
                int k = (j * res + i) * 3;
                p[k] = (float) (x + rx * u * width / 2 + ox * o);
                p[k + 1] = (float) (y + v * height / 2);
                p[k + 2] = (float) (z + rz * u * width / 2 + oz * o);
            }
        float[] v = new float[res * res * 6];
        for (int j = 0; j < res; j++)
            for (int i = 0; i < res; i++) {
                int a = Math.max(0, i - 1), b = Math.min(res - 1, i + 1), c = Math.max(0, j - 1), d = Math.min(res - 1, j + 1);
                double[] du = sub(p, (j * res + b) * 3, (j * res + a) * 3), dv = sub(p, (d * res + i) * 3, (c * res + i) * 3);
                // Normale nach außen: du (rechts) × dv (oben) zeigt zum Betrachter vor der Wand
                double nx = dv[1] * du[2] - dv[2] * du[1], ny = dv[2] * du[0] - dv[0] * du[2], nz = dv[0] * du[1] - dv[1] * du[0];
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nx * ox + nz * oz < 0) l = -l;
                int k = (j * res + i);
                v[k * 6] = p[k * 3];
                v[k * 6 + 1] = p[k * 3 + 1];
                v[k * 6 + 2] = p[k * 3 + 2];
                v[k * 6 + 3] = (float) (nx / l);
                v[k * 6 + 4] = (float) (ny / l);
                v[k * 6 + 5] = (float) (nz / l);
            }
        int[] idx = new int[(res - 1) * (res - 1) * 6];
        int t = 0;
        for (int j = 0; j < res - 1; j++)
            for (int i = 0; i < res - 1; i++) {
                int a = j * res + i, b = a + 1, c = a + res, d = c + 1;
                idx[t++] = a; idx[t++] = b; idx[t++] = d;
                idx[t++] = a; idx[t++] = d; idx[t++] = c;
            }
        return new Mesh(v, idx);
    }

    private static double[] sub(float[] p, int a, int b) { return new double[]{p[a] - p[b], p[a + 1] - p[b + 1], p[a + 2] - p[b + 2]}; }

    /** Größte Ausladung vor der Wand (m), für Prüfung und HUD. */
    public double maxReach() {
        double m = 0;
        for (double u = -1; u <= 1; u += 0.05) for (double v = -1; v <= 1; v += 0.05) m = Math.max(m, protrusion(u, v));
        return m;
    }
}
