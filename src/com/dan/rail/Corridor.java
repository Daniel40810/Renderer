package com.dan.rail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Seit Brocken Phase 12: die Achse eines Gleises aus den Flächen seiner Trasse (etwa der Nutzung „Bahnverkehr“ eines
 * Katasters, wo keine Linien vorliegen). Die Flächen werden auf ein dünnes Raster gelegt (nur die belegten Kacheln),
 * nach Zhang und Suen (1984) auf eine Linie von einem Bildpunkt Breite verdünnt, darauf der kürzeste Weg zwischen zwei
 * Punkten gesucht (Bahnhofsgleise und Stummel bleiben liegen) und dieser Weg geglättet und gleichmäßig nachgetastet.
 * <p>
 * Koordinaten in der Ebene (x, z) der App; Ringe als {x0, z0, x1, z1, …}, mehrere Ringe einer Fläche nach der
 * Gerade-ungerade-Regel (Löcher bleiben frei).
 */
public final class Corridor {
    /** Zellgröße des Rasters (m). */
    public final double cell;
    /** Größte Lücke (m), die {@link #path} zwischen zwei Stücken der Linie überbrückt. */
    public double gap = 60;
    private final Set<Long> pix = new HashSet<>();
    private int skeleton;
    private long millis;

    private Corridor(double cell) { this.cell = cell; }

    /** Die Flächen (je Fläche ihre Ringe) auf das Raster legen und verdünnen. */
    public static Corridor of(List<double[][]> polygons, double cell) {
        long t0 = System.currentTimeMillis();
        Corridor c = new Corridor(cell);
        for (double[][] rings : polygons) c.fill(rings);
        c.thin();
        c.skeleton = c.pix.size();
        c.millis = System.currentTimeMillis() - t0;
        return c;
    }

    static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }
    static int ki(long k) { return (int) (k >> 32); }
    static int kj(long k) { return (int) k; }

    /** Gerade-ungerade-Füllung je Zeile über das Rechteck der Fläche (Mittelpunkte der Zellen). */
    private void fill(double[][] rings) {
        double zMin = Double.MAX_VALUE, zMax = -Double.MAX_VALUE;
        for (double[] r : rings) for (int k = 1; k < r.length; k += 2) { zMin = Math.min(zMin, r[k]); zMax = Math.max(zMax, r[k]); }
        int j0 = (int) Math.floor(zMin / cell), j1 = (int) Math.ceil(zMax / cell);
        List<Double> xs = new ArrayList<>();
        for (int j = j0; j <= j1; j++) {
            double z = (j + 0.5) * cell;
            xs.clear();
            for (double[] r : rings) {
                int n = r.length / 2;
                for (int a = 0, b = n - 1; a < n; b = a++) {
                    double za = r[a * 2 + 1], zb = r[b * 2 + 1];
                    if ((za > z) != (zb > z)) xs.add(r[a * 2] + (z - za) / (zb - za) * (r[b * 2] - r[a * 2]));
                }
            }
            xs.sort(null);
            for (int k = 0; k + 1 < xs.size(); k += 2) {
                int i0 = (int) Math.ceil(xs.get(k) / cell - 0.5), i1 = (int) Math.floor(xs.get(k + 1) / cell - 0.5);
                for (int i = i0; i <= i1; i++) pix.add(key(i, j));
            }
        }
    }

    private boolean on(int i, int j) { return pix.contains(key(i, j)); }

    /** Verdünnen nach Zhang und Suen: in zwei Teilschritten Randpunkte abtragen, bis sich nichts mehr ändert. */
    private void thin() {
        boolean changed = true;
        List<Long> del = new ArrayList<>();
        while (changed) {
            changed = false;
            for (int step = 0; step < 2; step++) {
                del.clear();
                for (long k : pix) {
                    int i = ki(k), j = kj(k);
                    // Nachbarn P2..P9 im Uhrzeigersinn ab Norden (j-1 ist Norden)
                    boolean p2 = on(i, j - 1), p3 = on(i + 1, j - 1), p4 = on(i + 1, j), p5 = on(i + 1, j + 1), p6 = on(i, j + 1),
                            p7 = on(i - 1, j + 1), p8 = on(i - 1, j), p9 = on(i - 1, j - 1);
                    int b = (p2 ? 1 : 0) + (p3 ? 1 : 0) + (p4 ? 1 : 0) + (p5 ? 1 : 0) + (p6 ? 1 : 0) + (p7 ? 1 : 0) + (p8 ? 1 : 0) + (p9 ? 1 : 0);
                    if (b < 2 || b > 6) continue;
                    boolean[] s = {p2, p3, p4, p5, p6, p7, p8, p9, p2};
                    int a = 0;
                    for (int q = 0; q < 8; q++) if (!s[q] && s[q + 1]) a++;
                    if (a != 1) continue;
                    if (step == 0 ? (p2 && p4 && p6) || (p4 && p6 && p8) : (p2 && p4 && p8) || (p2 && p6 && p8)) continue;
                    del.add(k);
                }
                if (!del.isEmpty()) { pix.removeAll(del); changed = true; }
            }
        }
    }

    /** Zahl der Punkte der verdünnten Linie. */
    public int skeletonPoints() { return skeleton; }

    /** Der Punkt der Linie, der (x, z) am nächsten liegt (Mitte der Zelle). */
    public double[] nearest(double x, double z) {
        long k = nearestKey(x, z);
        return new double[]{(ki(k) + 0.5) * cell, (kj(k) + 0.5) * cell};
    }

    private long nearestKey(double x, double z) {
        long best = 0;
        double bd = Double.MAX_VALUE;
        for (long k : pix) {
            double dx = (ki(k) + 0.5) * cell - x, dz = (kj(k) + 0.5) * cell - z, d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = k; }
        }
        return best;
    }

    /**
     * Der kürzeste Weg auf der Linie vom Punkt nächst (x0, z0) zum Punkt nächst (x1, z1) (Dijkstra über 8 Nachbarn),
     * geglättet über smoothM m (gleitendes Mittel, Enden fest) und alle step m nachgetastet. Rückgabe {x, z}…; leer,
     * wenn die Punkte nicht verbunden sind.
     */
    public double[][] path(double x0, double z0, double x1, double z1, double smoothM, double step) {
        long a = nearestKey(x0, z0), b = nearestKey(x1, z1);
        // Lücken (Bahnübergänge, Brücken, Grenzen zwischen Flurstücken) überbrücken: von jedem Ende der Linie zu jedem
        // Punkt bis gap m entfernt, zum dreifachen Preis
        int gr = (int) Math.ceil(gap / cell);
        Map<Long, List<Long>> grid = new HashMap<>();
        int gs = Math.max(4, gr);
        for (long k : pix) grid.computeIfAbsent(key(Math.floorDiv(ki(k), gs), Math.floorDiv(kj(k), gs)), u -> new ArrayList<>()).add(k);
        Map<Long, Double> dist = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();
        PriorityQueue<double[]> q = new PriorityQueue<>((u, v) -> Double.compare(u[0], v[0]));
        dist.put(a, 0.0);
        q.add(new double[]{0, Double.longBitsToDouble(a)});
        while (!q.isEmpty()) {
            double[] e = q.poll();
            long k = Double.doubleToRawLongBits(e[1]);
            if (e[0] > dist.getOrDefault(k, Double.MAX_VALUE)) continue;
            if (k == b) break;
            int i = ki(k), j = kj(k);
            int deg = 0;
            for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) if ((di != 0 || dj != 0) && pix.contains(key(i + di, j + dj))) deg++;
            if (deg <= 1 && gap > 0) {
                int gi = Math.floorDiv(i, gs), gj = Math.floorDiv(j, gs);
                for (int bj = gj - 1; bj <= gj + 1; bj++)
                    for (int bi = gi - 1; bi <= gi + 1; bi++)
                        for (long n : grid.getOrDefault(key(bi, bj), List.of())) {
                            double d = Math.hypot(ki(n) - i, kj(n) - j);
                            if (d <= 3 || d > gr) continue;
                            double nd = e[0] + 3 * d;
                            if (nd < dist.getOrDefault(n, Double.MAX_VALUE)) {
                                dist.put(n, nd);
                                prev.put(n, k);
                                q.add(new double[]{nd, Double.longBitsToDouble(n)});
                            }
                        }
            }
            for (int dj = -1; dj <= 1; dj++)
                for (int di = -1; di <= 1; di++) {
                    if (di == 0 && dj == 0) continue;
                    long n = key(i + di, j + dj);
                    if (!pix.contains(n)) continue;
                    double nd = e[0] + (di != 0 && dj != 0 ? Math.sqrt(2) : 1);
                    if (nd < dist.getOrDefault(n, Double.MAX_VALUE)) {
                        dist.put(n, nd);
                        prev.put(n, k);
                        q.add(new double[]{nd, Double.longBitsToDouble(n)});
                    }
                }
        }
        if (!dist.containsKey(b)) return new double[0][];
        ArrayDeque<double[]> raw = new ArrayDeque<>();
        for (long k = b; ; k = prev.get(k)) {
            raw.addFirst(new double[]{(ki(k) + 0.5) * cell, (kj(k) + 0.5) * cell});
            if (k == a) break;
        }
        return resample(smooth(resample(raw.toArray(new double[0][]), cell), smoothM), step);
    }

    /** Gleitendes Mittel über win m (Enden fest, das Fenster schrumpft zu den Enden hin). */
    public static double[][] smooth(double[][] p, double win) {
        if (p.length < 3 || win <= 0) return p;
        double[] cum = cumulative(p);
        double[][] o = new double[p.length][2];
        int lo = 0, hi = 0;
        for (int i = 0; i < p.length; i++) {
            double h = Math.min(win / 2, Math.min(cum[i], cum[p.length - 1] - cum[i]));
            while (cum[lo] < cum[i] - h) lo++;
            while (hi < p.length - 1 && cum[hi + 1] <= cum[i] + h) hi++;
            double sx = 0, sz = 0;
            for (int k = lo; k <= hi; k++) { sx += p[k][0]; sz += p[k][1]; }
            o[i][0] = sx / (hi - lo + 1);
            o[i][1] = sz / (hi - lo + 1);
        }
        return o;
    }

    /** Gleichmäßig nachtasten (Abstand step m in der Ebene). */
    public static double[][] resample(double[][] p, double step) {
        double[] cum = cumulative(p);
        double len = cum[cum.length - 1];
        int n = Math.max(2, (int) Math.ceil(len / step) + 1);
        double[][] o = new double[n][2];
        int j = 0;
        for (int i = 0; i < n; i++) {
            double s = len * i / (n - 1);
            while (j < p.length - 2 && cum[j + 1] < s) j++;
            double t = Math.max(0, Math.min(1, (s - cum[j]) / Math.max(1e-9, cum[j + 1] - cum[j])));
            o[i][0] = p[j][0] + (p[j + 1][0] - p[j][0]) * t;
            o[i][1] = p[j][1] + (p[j + 1][1] - p[j][1]) * t;
        }
        return o;
    }

    static double[] cumulative(double[][] p) {
        double[] c = new double[p.length];
        for (int i = 1; i < p.length; i++) c[i] = c[i - 1] + Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
        return c;
    }

    /** „Trasse zu 2 m: 4 210 Punkte der Linie (0,6 s)“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Trasse zu %.1f m: %,d Punkte der Mittellinie (%.1f s)", cell, skeleton, millis / 1000.0);
    }
}
