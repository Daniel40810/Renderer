package com.dan.herd;

import java.util.Arrays;

/**
 * Das Netz einer Tierart aus Ellipsoiden und Röhren, in Körperkoordinaten (x rechts, y oben vom Boden, z nach hinten;
 * Kopf bei −z), rein in Java. Ecken: Ort (3), Normale (3), Farbe (3, linear), Teil (1), Tönung (1: wie stark die Farbe
 * des Tiers wirkt, 0 für Schnabel, Kamm, Hufe, Hörner). Teile: 0 Rumpf, 1 Kopf mit Hals (dreht um {@link #neck}),
 * 2 bis 5 Beine vorn links, vorn rechts, hinten links, hinten rechts (drehen um {@link #hipFront}, {@link #hipBack}),
 * 6 Schwanz (dreht um {@link #tail}). Hühner haben nur die Beine 2 und 3.
 * <p>
 * Die Formen sind schlicht, aber die Maße stimmen [G]: ein Haushuhn knapp 40 cm hoch, eine Ziege 70 cm Schulterhöhe mit
 * nach hinten gebogenen Hörnern und Bart, ein Schwein der Wikingerzeit klein, borstig und dunkel mit langem Rüssel,
 * ein spitzartiger Hund mit Stehohren und Ringelschwanz.
 */
public final class AnimalMesh {
    public static final int FLOATS = 11;
    public final Herd.Kind kind;
    /** Drehpunkte (y, z): Hals, Hüfte vorn, Hüfte hinten, Schwanz; Beinlänge (m). */
    public final double[] neck = new double[2], hipFront = new double[2], hipBack = new double[2], tail = new double[2];
    public double legLength;
    private float[] v = new float[FLOATS * 2048];
    private int n;

    private AnimalMesh(Herd.Kind k) { kind = k; }

    public float[] vertices() { return Arrays.copyOf(v, n * FLOATS); }
    public int count() { return n; }

    private void put(double x, double y, double z, double[] nn, double[] c, int part, double tint) {
        if ((n + 1) * FLOATS > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = n * FLOATS;
        v[o] = (float) x; v[o + 1] = (float) y; v[o + 2] = (float) z;
        v[o + 3] = (float) nn[0]; v[o + 4] = (float) nn[1]; v[o + 5] = (float) nn[2];
        v[o + 6] = (float) c[0]; v[o + 7] = (float) c[1]; v[o + 8] = (float) c[2];
        v[o + 9] = part;
        v[o + 10] = (float) tint;
        n++;
    }

    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] c(double r, double g, double b) { return new double[]{r, g, b}; }

    /** Ellipsoid (Halbachsen rx, ry, rz) um (cx, cy, cz), um die x-Achse gekippt (pitch, rad, positiv: Vorderende hoch). */
    private void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, double pitch, double[] col, int part, double tint, int seg) {
        int rings = Math.max(3, seg / 2);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < seg; i++) {
                double[][] q = new double[4][];
                int[][] ij = {{j, i}, {j + 1, i}, {j + 1, i + 1}, {j, i + 1}};
                for (int k = 0; k < 4; k++) {
                    double t = Math.PI * ij[k][0] / rings, a = 2 * Math.PI * ij[k][1] / seg;
                    q[k] = new double[]{Math.sin(t) * Math.sin(a), Math.cos(t), Math.sin(t) * Math.cos(a)};
                }
                int[][] tris = {{0, 1, 2}, {0, 2, 3}};
                for (int[] tr : tris)
                    for (int k : tr) {
                        double[] d = q[k];
                        double x = d[0] * rx, y = d[1] * ry, z = d[2] * rz;
                        double[] nn = norm(new double[]{d[0] / rx, d[1] / ry, d[2] / rz});
                        // kippen um x: Vorderende (−z) hoch bei positivem pitch
                        double y2 = y * cp - z * sp, z2 = y * sp + z * cp;
                        double ny = nn[1] * cp - nn[2] * sp, nz = nn[1] * sp + nn[2] * cp;
                        put(cx + x, cy + y2, cz + z2, new double[]{nn[0], ny, nz}, col, part, tint);
                    }
            }
    }

    /** Röhre von a nach b mit Halbmessern ra, rb (8 Seiten). */
    private void tube(double[] a, double[] b, double ra, double rb, double[] col, int part, double tint) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double[] w = {dx / l, dy / l, dz / l};
        double[] u = Math.abs(w[1]) < 0.9 ? norm(cross(w, new double[]{0, 1, 0})) : norm(cross(w, new double[]{1, 0, 0}));
        double[] vv = cross(w, u);
        int seg = 8;
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            double[] n0 = add(mul(u, Math.cos(a0)), mul(vv, Math.sin(a0))), n1 = add(mul(u, Math.cos(a1)), mul(vv, Math.sin(a1)));
            double[] p00 = add(a, mul(n0, ra)), p01 = add(a, mul(n1, ra)), p10 = add(b, mul(n0, rb)), p11 = add(b, mul(n1, rb));
            put3(p00, n0, col, part, tint); put3(p10, n0, col, part, tint); put3(p11, n1, col, part, tint);
            put3(p00, n0, col, part, tint); put3(p11, n1, col, part, tint); put3(p01, n1, col, part, tint);
        }
    }

    private void put3(double[] p, double[] nn, double[] col, int part, double tint) { put(p[0], p[1], p[2], nn, col, part, tint); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }

    /** Die Farbe eines Tiers aus Art und Ton (0..1): Fell oder Gefieder, linear. */
    public static double[] coat(Herd.Kind k, double tone) {
        return switch (k) {
            // Hühner: rotbraun, weiß, schwarz, gesprenkelt grau
            case HUHN -> tone < 0.45 ? c(0.30, 0.13, 0.05) : tone < 0.7 ? c(0.62, 0.58, 0.50) : tone < 0.85 ? c(0.03, 0.03, 0.035) : c(0.25, 0.24, 0.22);
            // Ziegen: weiß, braun, schwarz, gescheckt grau
            case ZIEGE -> tone < 0.3 ? c(0.55, 0.52, 0.45) : tone < 0.6 ? c(0.22, 0.13, 0.07) : tone < 0.8 ? c(0.04, 0.035, 0.03) : c(0.30, 0.27, 0.22);
            // Schweine um 950: dunkel, borstig, wildschweinähnlich [G]
            case SCHWEIN -> tone < 0.5 ? c(0.07, 0.05, 0.035) : tone < 0.8 ? c(0.13, 0.08, 0.05) : c(0.20, 0.15, 0.11);
            // Hund: grau-braun mit hellem Bauch
            case HUND -> tone < 0.5 ? c(0.25, 0.20, 0.14) : c(0.32, 0.30, 0.26);
        };
    }

    /** Das Netz der Art; die Farbe im Netz ist weiß (mal Tönung) bzw. die feste Farbe der untönten Teile. */
    public static AnimalMesh build(Herd.Kind k) {
        AnimalMesh m = new AnimalMesh(k);
        double[] W = c(1, 1, 1), BELLY = c(1.15, 1.12, 1.08);
        switch (k) {
            case HUHN -> {
                m.legLength = 0.14;
                m.hipFront[0] = m.hipBack[0] = 0.15; m.hipFront[1] = m.hipBack[1] = 0.02;
                m.neck[0] = 0.27; m.neck[1] = -0.08;
                m.tail[0] = 0.27; m.tail[1] = 0.13;
                m.ellipsoid(0, 0.24, 0.02, 0.10, 0.10, 0.15, 0.15, W, 0, 1, 12);
                // Schwanzfedern: hoch und nach hinten
                m.ellipsoid(0, 0.33, 0.15, 0.03, 0.09, 0.06, -0.6, W, 6, 1, 8);
                m.ellipsoid(0, 0.32, -0.09, 0.05, 0.06, 0.05, 0, W, 1, 1, 8);
                m.ellipsoid(0, 0.385, -0.13, 0.04, 0.042, 0.045, 0, W, 1, 1, 10);
                double[] red = c(0.55, 0.04, 0.03), beak = c(0.6, 0.45, 0.12);
                m.ellipsoid(0, 0.43, -0.13, 0.008, 0.025, 0.035, 0, red, 1, 0, 6);         // Kamm
                m.ellipsoid(0, 0.355, -0.165, 0.008, 0.02, 0.01, 0, red, 1, 0, 6);         // Kehllappen
                m.tube(new double[]{0, 0.39, -0.165}, new double[]{0, 0.38, -0.20}, 0.012, 0.002, beak, 1, 0);
                for (int s = -1; s <= 1; s += 2) {
                    int part = s < 0 ? 2 : 3;
                    double x = s * 0.04;
                    m.tube(new double[]{x, 0.15, 0.02}, new double[]{x, 0.01, 0.02}, 0.008, 0.006, beak, part, 0);
                    m.tube(new double[]{x, 0.008, 0.03}, new double[]{x, 0.008, -0.05}, 0.006, 0.004, beak, part, 0);
                }
            }
            case ZIEGE -> {
                m.legLength = 0.45;
                m.hipFront[0] = 0.46; m.hipFront[1] = -0.28; m.hipBack[0] = 0.48; m.hipBack[1] = 0.30;
                m.neck[0] = 0.66; m.neck[1] = -0.36;
                m.tail[0] = 0.68; m.tail[1] = 0.40;
                m.ellipsoid(0, 0.60, 0, 0.16, 0.19, 0.42, 0.03, W, 0, 1, 14);
                m.ellipsoid(0, 0.53, 0, 0.13, 0.12, 0.30, 0, BELLY, 0, 1, 10);
                m.tube(new double[]{0, 0.66, -0.34}, new double[]{0, 0.80, -0.52}, 0.08, 0.06, W, 1, 1);
                m.ellipsoid(0, 0.80, -0.62, 0.065, 0.075, 0.13, -0.5, W, 1, 1, 10);
                m.ellipsoid(0, 0.70, -0.70, 0.02, 0.06, 0.02, 0, W, 1, 1, 6);              // Bart
                double[] horn = c(0.18, 0.16, 0.12);
                for (int s = -1; s <= 1; s += 2) {
                    m.tube(new double[]{s * 0.035, 0.87, -0.60}, new double[]{s * 0.05, 0.96, -0.55}, 0.016, 0.012, horn, 1, 0);
                    m.tube(new double[]{s * 0.05, 0.96, -0.55}, new double[]{s * 0.065, 0.97, -0.46}, 0.012, 0.004, horn, 1, 0);
                    m.ellipsoid(s * 0.09, 0.83, -0.57, 0.06, 0.018, 0.025, 0, W, 1, 1, 6);    // Ohren
                }
                m.ellipsoid(0, 0.73, 0.43, 0.03, 0.06, 0.03, 0.7, W, 6, 1, 6);
                legs(m, 0.09, 0.024, c(0.12, 0.10, 0.08));
            }
            case SCHWEIN -> {
                m.legLength = 0.26;
                m.hipFront[0] = 0.28; m.hipFront[1] = -0.30; m.hipBack[0] = 0.30; m.hipBack[1] = 0.32;
                m.neck[0] = 0.42; m.neck[1] = -0.42;
                m.tail[0] = 0.44; m.tail[1] = 0.48;
                m.ellipsoid(0, 0.43, 0, 0.19, 0.20, 0.50, 0.04, W, 0, 1, 14);
                m.ellipsoid(0, 0.55, -0.20, 0.10, 0.08, 0.25, 0.1, W, 0, 1, 8);              // Borstenkamm
                m.ellipsoid(0, 0.42, -0.55, 0.12, 0.13, 0.17, -0.2, W, 1, 1, 10);
                m.tube(new double[]{0, 0.38, -0.66}, new double[]{0, 0.33, -0.83}, 0.07, 0.05, W, 1, 1);
                m.ellipsoid(0, 0.33, -0.835, 0.05, 0.045, 0.01, 0, c(0.18, 0.10, 0.08), 1, 0, 8);
                for (int s = -1; s <= 1; s += 2) m.ellipsoid(s * 0.08, 0.52, -0.55, 0.04, 0.05, 0.025, 0.3, W, 1, 1, 6);
                m.tube(new double[]{0, 0.48, 0.48}, new double[]{0, 0.40, 0.56}, 0.012, 0.008, W, 6, 1);
                legs(m, 0.10, 0.03, c(0.05, 0.04, 0.03));
            }
            case HUND -> {
                m.legLength = 0.40;
                m.hipFront[0] = 0.42; m.hipFront[1] = -0.26; m.hipBack[0] = 0.42; m.hipBack[1] = 0.28;
                m.neck[0] = 0.56; m.neck[1] = -0.33;
                m.tail[0] = 0.56; m.tail[1] = 0.36;
                m.ellipsoid(0, 0.50, 0, 0.13, 0.15, 0.36, 0.05, W, 0, 1, 14);
                m.ellipsoid(0, 0.45, -0.05, 0.10, 0.08, 0.26, 0, BELLY, 0, 1, 10);
                m.tube(new double[]{0, 0.56, -0.30}, new double[]{0, 0.68, -0.44}, 0.08, 0.065, W, 1, 1);
                m.ellipsoid(0, 0.70, -0.50, 0.08, 0.08, 0.10, 0, W, 1, 1, 10);
                m.tube(new double[]{0, 0.67, -0.57}, new double[]{0, 0.65, -0.68}, 0.04, 0.028, W, 1, 1);
                m.ellipsoid(0, 0.66, -0.685, 0.015, 0.012, 0.01, 0, c(0.02, 0.02, 0.02), 1, 0, 6);
                for (int s = -1; s <= 1; s += 2) m.tube(new double[]{s * 0.045, 0.76, -0.49}, new double[]{s * 0.05, 0.85, -0.50}, 0.03, 0.004, W, 1, 1);
                // Ringelschwanz: über den Rücken gebogen
                m.tube(new double[]{0, 0.56, 0.35}, new double[]{0, 0.68, 0.40}, 0.03, 0.028, W, 6, 1);
                m.tube(new double[]{0, 0.68, 0.40}, new double[]{0, 0.72, 0.30}, 0.028, 0.015, W, 6, 1);
                legs(m, 0.075, 0.022, c(0.15, 0.12, 0.09));
            }
        }
        return m;
    }

    /** Vier Beine an den Hüften: Röhre bis zum Boden, darunter ein Huf in fester Farbe. */
    private static void legs(AnimalMesh m, double x, double r, double[] hoof) {
        for (int i = 0; i < 4; i++) {
            boolean front = i < 2;
            double s = i % 2 == 0 ? -1 : 1;
            double[] hip = front ? m.hipFront : m.hipBack;
            int part = 2 + i;
            m.tube(new double[]{s * x, hip[0] + 0.05, hip[1]}, new double[]{s * x, 0.05, hip[1]}, r * 1.4, r, c(1, 1, 1), part, 1);
            m.tube(new double[]{s * x, 0.05, hip[1]}, new double[]{s * x, 0.0, hip[1] - 0.01}, r, r * 1.1, hoof, part, 0);
        }
    }
}
