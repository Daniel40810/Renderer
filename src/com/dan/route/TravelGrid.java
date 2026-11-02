package com.dan.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Laufzeit auf einem Gitter (seit LituyaBay1958 Phase 7, Werkbuch 6.9 und 9.4): die Ankunftszeit einer Front nach Fermat,
 * T(x) = min ∫ ds / c, als kürzester Weg über die Zellmitten eines Rasters. Jede Zelle hat eine Geschwindigkeit c (m/s;
 * ≤ 0 gesperrt, etwa Land für eine Welle). Von jeder Zelle gehen Kanten zu den Nachbarn eines Sterns ({@link Stencil}: 4,
 * 8, 16 oder 32 Nachbarn); die Kosten einer Kante sind ihre Länge mal die mittlere Langsamkeit 1/c der Zellen, die sie
 * überquert, und keine dieser Zellen darf gesperrt sein. Der Fehler gegen die Eikonalgleichung ist der Winkelfehler des
 * Sterns: 1/cos(θ/2) − 1 mit θ der größten Lücke zwischen zwei Richtungen ({@link Stencil#error}).
 * <p>
 * Mehrere Quellen mit eigener Startzeit ({@link #solve}); Ergebnis: Ankunftszeit und Vorgänger je Zelle (Laufzeitbaum),
 * daraus Strahlen ({@link #ray}) und Isochronen als Linien ({@link #isochrones}, Marching Squares). {@link #edges} gibt
 * dieselben Knoten und Kanten mit Länge und Kosten aus, etwa für ein Netz in Oracle (LB_NET_WELLE), damit beide Rechnungen
 * dieselben Zahlen haben.
 * <p>
 * Raster: Zelle (i, j) hat die Mitte (x0 + (i + ½)·zelle, z0 + (j + ½)·zelle); Zeilen nach z (Süden). Der Kern kennt nur
 * Java 21.
 */
public final class TravelGrid {
    /**
     * Nachbarsterne: Züge (di, dj) im ersten Achtel, gespiegelt auf alle Richtungen. N16: Königs- und Springerzüge
     * (größte Lücke 26,6°, Fehler 2,75 %); N32 dazu (4, 1) und (3, 2) (größte Lücke 14,0°, Fehler 0,76 %, Werkbuch 9.4).
     */
    public enum Stencil {
        N4(new int[][]{{1, 0}}), N8(new int[][]{{1, 0}, {1, 1}}), N16(new int[][]{{1, 0}, {1, 1}, {2, 1}}), N32(new int[][]{{1, 0}, {1, 1}, {2, 1}, {4, 1}, {3, 2}});
        final int[][] moves;

        Stencil(int[][] base) {
            List<int[]> m = new ArrayList<>();
            for (int[] b : base)
                for (int s = 0; s < 8; s++) {
                    int a = b[0], c = b[1];
                    int x = (s & 1) == 0 ? a : c, y = (s & 1) == 0 ? c : a;   // vertauscht
                    if ((s & 2) != 0) x = -x;
                    if ((s & 4) != 0) y = -y;
                    boolean dup = false;
                    for (int[] q : m) dup |= q[0] == x && q[1] == y;
                    if (!dup) m.add(new int[]{x, y});
                }
            moves = m.toArray(new int[0][]);
        }

        public int size() { return moves.length; }

        /** Größte Lücke zwischen zwei Richtungen (Grad). */
        public double maxGapDeg() {
            double[] a = new double[moves.length];
            for (int k = 0; k < a.length; k++) a[k] = Math.toDegrees(Math.atan2(moves[k][1], moves[k][0]));
            Arrays.sort(a);
            double g = 360 - (a[a.length - 1] - a[0]);
            for (int k = 1; k < a.length; k++) g = Math.max(g, a[k] - a[k - 1]);
            return g;
        }

        /** Größter Fehler der Laufzeit gegen die Eikonalgleichung auf konstantem Grund: 1/cos(θ/2) − 1. */
        public double error() { return 1 / Math.cos(Math.toRadians(maxGapDeg() / 2)) - 1; }
    }

    /** Eine Quelle: Zelle und Startzeit (s). */
    public record Source(int i, int j, double t0) { }

    public final int w, h;
    public final double x0, z0, cell;
    /** Geschwindigkeit je Zelle (m/s); ≤ 0 oder NaN gesperrt. */
    public final float[] speed;
    public Stencil stencil = Stencil.N16;
    private float[] arrival;
    private int[] parent;
    public int visited;

    public TravelGrid(int w, int h, double x0, double z0, double cell) {
        this.w = w; this.h = h; this.x0 = x0; this.z0 = z0; this.cell = cell;
        speed = new float[w * h];
    }

    /** Lange Welle im Flachwasser: c = √(g·(h + a)) mit Tiefe h und Amplitude a (m); 0 bei h ≤ 0. */
    public static double waveSpeed(double depth, double amplitude) { return depth <= 0 ? 0 : Math.sqrt(9.81 * Math.max(0, depth + amplitude)); }

    public boolean open(int i, int j) { if (i < 0 || j < 0 || i >= w || j >= h) return false; float c = speed[j * w + i]; return c > 0; }
    public double cx(int i) { return x0 + (i + 0.5) * cell; }
    public double cz(int j) { return z0 + (j + 0.5) * cell; }
    public int cellI(double x) { return (int) Math.floor((x - x0) / cell); }
    public int cellJ(double z) { return (int) Math.floor((z - z0) / cell); }

    /** Kosten der Kante von (i, j) um den Zug (di, dj) (s); unendlich, wenn eine überquerte Zelle gesperrt ist. */
    public double cost(int i, int j, int di, int dj) {
        int n = Math.max(Math.abs(di), Math.abs(dj));
        double len = Math.hypot(di, dj) * cell, slow = 0;
        // Abtastung entlang der Strecke: Endpunkte halb, dazwischen 2n − 1 Punkte
        int steps = 2 * n;
        for (int k = 0; k <= steps; k++) {
            double t = k / (double) steps;
            int ii = (int) Math.floor(i + 0.5 + di * t), jj = (int) Math.floor(j + 0.5 + dj * t);
            if (!open(ii, jj)) return Double.POSITIVE_INFINITY;
            double s = 1 / speed[jj * w + ii];
            slow += (k == 0 || k == steps) ? s * 0.5 : s;
        }
        return len * slow / steps;
    }

    /** Ankunftszeiten von den Quellen aus (Dijkstra über den Stern); gesperrte Zellen NaN, nicht erreichte +∞. */
    public float[] solve(List<Source> sources) {
        int n = w * h;
        double[] t = new double[n];
        Arrays.fill(t, Double.POSITIVE_INFINITY);
        parent = new int[n];
        Arrays.fill(parent, -1);
        Heap q = new Heap(n);
        for (Source s : sources)
            if (open(s.i(), s.j())) {
                int k = s.j() * w + s.i();
                if (s.t0() < t[k]) { t[k] = s.t0(); q.push(k, s.t0()); }
            }
        int[][] mv = stencil.moves;
        visited = 0;
        while (q.size > 0) {
            int k = q.pop();
            if (q.lastKey > t[k]) continue;   // veralteter Eintrag
            visited++;
            int i = k % w, j = k / w;
            for (int[] m : mv) {
                int ii = i + m[0], jj = j + m[1];
                if (!open(ii, jj)) continue;
                int kk = jj * w + ii;
                double d = t[k] + cost(i, j, m[0], m[1]);
                if (d < t[kk]) { t[kk] = d; parent[kk] = k; q.push(kk, d); }
            }
        }
        arrival = new float[n];
        for (int k = 0; k < n; k++) arrival[k] = speed[k] > 0 ? (float) t[k] : Float.NaN;
        return arrival;
    }

    public float[] arrival() { return arrival; }
    public int[] parent() { return parent; }

    /** Ankunftszeit bei (x, z), bilinear zwischen den Zellmitten (NaN, wo gesperrt oder unerreicht in der Nähe). */
    public double arrivalAt(double x, double z) {
        double fx = (x - x0) / cell - 0.5, fz = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double u = fx - i, v = fz - j, s = 0, ws = 0;
        for (int b = 0; b <= 1; b++)
            for (int a = 0; a <= 1; a++) {
                int ii = i + a, jj = j + b;
                if (ii < 0 || jj < 0 || ii >= w || jj >= h) continue;
                float tv = arrival[jj * w + ii];
                if (!Float.isFinite(tv)) continue;
                double wt = (a == 0 ? 1 - u : u) * (b == 0 ? 1 - v : v);
                s += tv * wt; ws += wt;
            }
        return ws > 1e-9 ? s / ws : Double.NaN;
    }

    /** Strahl von der Zelle zurück zur Quelle (Mitten der Zellen, x, z, Zeit je Punkt; von der Quelle her). */
    public float[] ray(int i, int j) {
        List<float[]> pts = new ArrayList<>();
        for (int k = j * w + i; k >= 0; k = parent[k]) pts.add(new float[]{(float) cx(k % w), (float) cz(k / w), arrival[k]});
        float[] out = new float[3 * pts.size()];
        for (int p = 0; p < pts.size(); p++) System.arraycopy(pts.get(pts.size() - 1 - p), 0, out, 3 * p, 3);
        return out;
    }

    /**
     * Isochronen T = level als Linienzüge (Marching Squares über die Zellmitten, nur Quadrate mit vier erreichten
     * Ecken); je Linienzug x, z abwechselnd. Die Stücke werden zu Zügen verbunden.
     */
    public List<float[]> isochrones(double level) { return Contour.lines(arrival, w, h, x0 + cell / 2, z0 + cell / 2, cell, level); }

    /** Senke für {@link #edges}: Knoten a → b, Länge (m), Kosten (s); Knoten = j·w + i. */
    @FunctionalInterface
    public interface EdgeSink { void edge(int a, int b, double length, double cost); }

    /** Alle Kanten des Sterns zwischen offenen Zellen, jede einmal (a &lt; b), mit Länge und Kosten. */
    public long edges(EdgeSink sink) {
        long n = 0;
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                if (!open(i, j)) continue;
                int a = j * w + i;
                for (int[] m : stencil.moves) {
                    int ii = i + m[0], jj = j + m[1];
                    int b = jj * w + ii;
                    if (!open(ii, jj) || b <= a) continue;
                    double c = cost(i, j, m[0], m[1]);
                    if (Double.isInfinite(c)) continue;
                    sink.edge(a, b, Math.hypot(m[0], m[1]) * cell, c);
                    n++;
                }
            }
        return n;
    }

    /** Zahl der offenen Zellen. */
    public int openCount() {
        int n = 0;
        for (float c : speed) if (c > 0) n++;
        return n;
    }

    /** Binärer Haufen mit Schlüsseln je Knoten (Mehrfacheinträge, alte werden beim Ziehen übersprungen). */
    private static final class Heap {
        int[] node;
        double[] key;
        int size;

        Heap(int cap) { node = new int[Math.max(16, cap / 4)]; key = new double[node.length]; }

        void push(int n, double k) {
            if (size == node.length) { node = Arrays.copyOf(node, size * 2); key = Arrays.copyOf(key, size * 2); }
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (key[p] <= k) break;
                node[i] = node[p]; key[i] = key[p]; i = p;
            }
            node[i] = n; key[i] = k;
        }

        double lastKey;

        int pop() {
            int top = node[0];
            lastKey = key[0];
            int n = node[--size];
            double k = key[size];
            int i = 0;
            while (true) {
                int c = 2 * i + 1;
                if (c >= size) break;
                if (c + 1 < size && key[c + 1] < key[c]) c++;
                if (key[c] >= k) break;
                node[i] = node[c]; key[i] = key[c]; i = c;
            }
            node[i] = n; key[i] = k;
            return top;
        }
    }
}
