package com.dan.core.szene;

import java.util.ArrayList;
import java.util.List;

/**
 * Ebene Ringe im Raum in Dreiecke zerlegen (seit P12a): Normale nach Newell, Ohrenschneiden in der Ebene der größten
 * Projektion. Für die Flächen der Gebäude (LoD2: Wände, Dächer; selten mehr als 20 Punkte).
 */
public final class Zerlegung {
    private Zerlegung() { }

    /** Normale eines Rings (x, y, z je Punkt) nach Newell, Einheitsvektor; null, wenn der Ring keine Fläche hat. */
    public static double[] normale(double[] r) {
        int n = r.length / 3;
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double x0 = r[3 * i], y0 = r[3 * i + 1], z0 = r[3 * i + 2], x1 = r[3 * j], y1 = r[3 * j + 1], z1 = r[3 * j + 2];
            nx += (y0 - y1) * (z0 + z1);
            ny += (z0 - z1) * (x0 + x1);
            nz += (x0 - x1) * (y0 + y1);
        }
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(l > 1e-9)) return null;
        return new double[] {nx / l, ny / l, nz / l};
    }

    /** Doppelte Fläche des Rings (m²·2) aus der Normale nach Newell. */
    public static double flaeche(double[] r) {
        int n = r.length / 3;
        double nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            nx += (r[3 * i + 1] - r[3 * j + 1]) * (r[3 * i + 2] + r[3 * j + 2]);
            ny += (r[3 * i + 2] - r[3 * j + 2]) * (r[3 * i] + r[3 * j]);
            nz += (r[3 * i] - r[3 * j]) * (r[3 * i + 1] + r[3 * j + 1]);
        }
        return 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    /**
     * Dreiecke (je drei Indizes der Punkte) eines ebenen Rings mit Normale {@code n}; null, wenn das Ohrenschneiden
     * scheitert (Ring schneidet sich). Die Dreiecke laufen um {@code n} gegen den Uhrzeigersinn.
     */
    public static int[] dreiecke(double[] r, double[] n) {
        int m = r.length / 3;
        // Projektion: die Achse mit dem größten Anteil der Normale weglassen
        int ax = Math.abs(n[0]) > Math.abs(n[1]) ? (Math.abs(n[0]) > Math.abs(n[2]) ? 0 : 2) : (Math.abs(n[1]) > Math.abs(n[2]) ? 1 : 2);
        int a = (ax + 1) % 3, b = (ax + 2) % 3;
        boolean umkehr = n[ax] < 0;
        double[] u = new double[m], v = new double[m];
        for (int i = 0; i < m; i++) {
            u[i] = r[3 * i + a];
            v[i] = r[3 * i + b];
        }
        // Lauf in der Projektion gegen den Uhrzeigersinn machen
        double fl = 0;
        for (int i = 0; i < m; i++) {
            int j = (i + 1) % m;
            fl += u[i] * v[j] - u[j] * v[i];
        }
        List<Integer> idx = new ArrayList<>(m);
        if (fl >= 0) for (int i = 0; i < m; i++) idx.add(i);
        else for (int i = m - 1; i >= 0; i--) idx.add(i);
        int[] out = new int[3 * (m - 2)];
        int k = 0, schutz = 0;
        while (idx.size() > 3) {
            boolean geschnitten = false;
            int s = idx.size();
            for (int q = 0; q < s; q++) {
                int i0 = idx.get((q + s - 1) % s), i1 = idx.get(q), i2 = idx.get((q + 1) % s);
                double cr = (u[i1] - u[i0]) * (v[i2] - v[i0]) - (v[i1] - v[i0]) * (u[i2] - u[i0]);
                if (cr <= 1e-12) continue;                                   // spitz nach innen oder entartet
                boolean leer = true;
                for (int p : idx) {
                    if (p == i0 || p == i1 || p == i2) continue;
                    if (innen(u[p], v[p], u[i0], v[i0], u[i1], v[i1], u[i2], v[i2])) { leer = false; break; }
                }
                if (!leer) continue;
                out[k++] = i0; out[k++] = i1; out[k++] = i2;
                idx.remove(q);
                geschnitten = true;
                break;
            }
            if (!geschnitten) {
                // entartete Punkte (in einer Linie) entfernen, sonst aufgeben
                boolean weg = false;
                for (int q = 0; q < s && !weg; q++) {
                    int i0 = idx.get((q + s - 1) % s), i1 = idx.get(q), i2 = idx.get((q + 1) % s);
                    double cr = (u[i1] - u[i0]) * (v[i2] - v[i0]) - (v[i1] - v[i0]) * (u[i2] - u[i0]);
                    if (Math.abs(cr) <= 1e-12) { idx.remove(q); weg = true; }
                }
                if (!weg || ++schutz > m) return null;
            }
        }
        int i0 = idx.get(0), i1 = idx.get(1), i2 = idx.get(2);
        double cr = (u[i1] - u[i0]) * (v[i2] - v[i0]) - (v[i1] - v[i0]) * (u[i2] - u[i0]);
        if (Math.abs(cr) > 1e-12) { out[k++] = i0; out[k++] = i1; out[k++] = i2; }
        int[] t = java.util.Arrays.copyOf(out, k);
        // In der Projektion gegen den Uhrzeigersinn; im Raum um n, wenn die weggelassene Achse positiv ist (sonst umdrehen)
        if (umkehr) for (int i = 0; i < t.length; i += 3) { int z = t[i + 1]; t[i + 1] = t[i + 2]; t[i + 2] = z; }
        return t;
    }

    static boolean innen(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
        double d2 = (cx - bx) * (py - by) - (cy - by) * (px - bx);
        double d3 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx);
        return d1 >= -1e-12 && d2 >= -1e-12 && d3 >= -1e-12;
    }
}
