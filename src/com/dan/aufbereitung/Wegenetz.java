package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Das Netz der Wege aus OSM für Rundgänge (seit X3, Werkbuch HalligHooge 148): Knoten sind die Punkte der Wege
 * ({@code highway=*}), zwei Wege hängen zusammen, wo sie einen Punkt teilen (OSM: derselbe Knoten, also dieselbe Lage).
 * {@link #weg} sucht den kürzesten Weg zwischen zwei Orten (Dijkstra); vom Ort zum nächsten Knoten und vom letzten
 * Knoten zum Ziel geht es gerade. Liegt kein Knoten in {@link #ANSCHLUSS} m oder hängen die beiden Teile des Netzes nicht
 * zusammen, ist der Weg die Gerade ({@link Strecke#verbunden} = false).
 * <p>
 * {@link #profil} tastet eine Strecke in gleichen Schritten ab und liest die Höhe des Bodens aus dem Gelände.
 */
public final class Wegenetz {
    /** So weit (m) darf der nächste Knoten von einem Ort entfernt sein [G]. */
    public static final double ANSCHLUSS = 250;
    /** Wege, die es (noch) nicht gibt oder die kein Mensch geht. */
    static final Set<String> OHNE = Set.of("proposed", "construction", "abandoned", "razed", "motorway", "trunk");

    private final double[] ost, nord;
    private final int[][] nach;
    private final double[][] laenge;
    /** Das Gelände für {@link #profil(Strecke, double, double)} (null: ohne, der Boden ist dann NaN). */
    private Gelaende gelaende;
    /** Zahl der Wege und der Knoten im Netz. */
    public final int wege, knoten;

    /** Eine Strecke: Punkte in UTM, Länge (m), davon auf Wegen (m); verbunden: ganz über das Netz. */
    public record Strecke(double[] ost, double[] nord, double laenge, double aufWegen, boolean verbunden) {
        /** Die Luftlinie vom ersten zum letzten Punkt (m). */
        public double luftlinie() {
            int n = ost.length - 1;
            return Math.hypot(ost[n] - ost[0], nord[n] - nord[0]);
        }
    }

    /** Ein abgetastetes Profil: je Schritt Ost, Nord, Weg seit dem Anfang (m) und Höhe des Bodens (m NHN; NaN: unbekannt). */
    public record Profil(double[] ost, double[] nord, double[] s, double[] boden) {
        public int n() { return ost.length; }

        public double laenge() { return s[s.length - 1]; }
    }

    private Wegenetz(double[] ost, double[] nord, int[][] nach, double[][] laenge, int wege) {
        this.ost = ost;
        this.nord = nord;
        this.nach = nach;
        this.laenge = laenge;
        this.wege = wege;
        this.knoten = ost.length;
    }

    /** Die Wege ({@code highway}) aus OSM, die das Rechteck {Ost, Nord, Ost, Nord} berühren. */
    public static Wegenetz aus(Osm osm, double[] rechteck) {
        Map<Long, Integer> index = new HashMap<>();
        List<double[]> punkte = new ArrayList<>();
        List<List<int[]>> kanten = new ArrayList<>();
        int wege = 0;
        for (Osm.Weg w : osm.wege) {
            String h = w.tags().get("highway");
            if (h == null || OHNE.contains(h) || w.ost().length < 2) continue;
            if (!beruehrt(w, rechteck)) continue;
            wege++;
            int vorher = -1;
            for (int i = 0; i < w.ost().length; i++) {
                long k = schluessel(w.ost()[i], w.nord()[i]);
                Integer j = index.get(k);
                if (j == null) {
                    j = punkte.size();
                    index.put(k, j);
                    punkte.add(new double[] {w.ost()[i], w.nord()[i]});
                    kanten.add(new ArrayList<>());
                }
                if (vorher >= 0 && vorher != j) {
                    kanten.get(vorher).add(new int[] {j});
                    kanten.get(j).add(new int[] {vorher});
                }
                vorher = j;
            }
        }
        int n = punkte.size();
        double[] o = new double[n], no = new double[n];
        for (int i = 0; i < n; i++) {
            o[i] = punkte.get(i)[0];
            no[i] = punkte.get(i)[1];
        }
        int[][] na = new int[n][];
        double[][] la = new double[n][];
        for (int i = 0; i < n; i++) {
            List<int[]> l = kanten.get(i);
            na[i] = new int[l.size()];
            la[i] = new double[l.size()];
            for (int k = 0; k < l.size(); k++) {
                int j = l.get(k)[0];
                na[i][k] = j;
                la[i][k] = Math.hypot(o[j] - o[i], no[j] - no[i]);
            }
        }
        return new Wegenetz(o, no, na, la, wege);
    }

    /** Wie {@link #aus(Osm, double[])}, dazu das Gelände aus {@code <gebiet>/gelaende} für die Profile. */
    public static Wegenetz aus(Osm osm, double[] rechteck, java.io.File gebiet) throws java.io.IOException {
        Wegenetz w = aus(osm, rechteck);
        java.io.File g = new java.io.File(gebiet, "gelaende");
        if (g.isDirectory()) w.gelaende = Gelaende.ordner(g);
        return w;
    }

    /** Hat das Netz ein Gelände (für die Profile)? */
    public boolean mitGelaende() { return gelaende != null; }

    /** {@link #profil(Strecke, Gelaende, double, double)} mit dem Gelände des Netzes. */
    public Profil profil(Strecke st, double schritt, double glaetten) { return profil(st, gelaende, schritt, glaetten); }

    private static long schluessel(double ost, double nord) {
        return Math.round(ost * 100) * 2_000_000_000L + Math.round(nord * 100) % 2_000_000_000L;
    }

    private static boolean beruehrt(Osm.Weg w, double[] r) {
        for (int i = 0; i < w.ost().length; i++)
            if (w.ost()[i] >= r[0] && w.ost()[i] <= r[2] && w.nord()[i] >= r[1] && w.nord()[i] <= r[3]) return true;
        return false;
    }

    /** Der Knoten am nächsten bei (ost, nord); -1: keiner in {@link #ANSCHLUSS} m. */
    public int naechster(double o, double n) {
        int best = -1;
        double bd = ANSCHLUSS;
        for (int i = 0; i < ost.length; i++) {
            double d = Math.hypot(ost[i] - o, nord[i] - n);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Der kürzeste Weg von (o1, n1) nach (o2, n2) über das Netz; ohne Anschluss die Gerade. */
    public Strecke weg(double o1, double n1, double o2, double n2) {
        int a = naechster(o1, n1), b = naechster(o2, n2);
        if (a < 0 || b < 0) return gerade(o1, n1, o2, n2);
        double[] dist = new double[knoten];
        int[] vor = new int[knoten];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(vor, -1);
        dist[a] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        q.add(new double[] {0, a});
        while (!q.isEmpty()) {
            double[] e = q.poll();
            int i = (int) e[1];
            if (e[0] > dist[i]) continue;
            if (i == b) break;
            for (int k = 0; k < nach[i].length; k++) {
                int j = nach[i][k];
                double d = dist[i] + laenge[i][k];
                if (d < dist[j]) {
                    dist[j] = d;
                    vor[j] = i;
                    q.add(new double[] {d, j});
                }
            }
        }
        if (Double.isInfinite(dist[b])) return gerade(o1, n1, o2, n2);
        List<Integer> kette = new ArrayList<>();
        for (int i = b; i >= 0; i = vor[i]) kette.add(0, i);
        List<double[]> p = new ArrayList<>();
        p.add(new double[] {o1, n1});
        for (int i : kette) p.add(new double[] {ost[i], nord[i]});
        p.add(new double[] {o2, n2});
        // doppelte Punkte (Ort liegt auf dem Knoten) weg
        List<double[]> q2 = new ArrayList<>();
        for (double[] x : p) if (q2.isEmpty() || Math.hypot(x[0] - q2.get(q2.size() - 1)[0], x[1] - q2.get(q2.size() - 1)[1]) > 0.05) q2.add(x);
        double[] so = new double[q2.size()], sn = new double[q2.size()];
        for (int i = 0; i < so.length; i++) {
            so[i] = q2.get(i)[0];
            sn[i] = q2.get(i)[1];
        }
        double gesamt = 0;
        for (int i = 1; i < so.length; i++) gesamt += Math.hypot(so[i] - so[i - 1], sn[i] - sn[i - 1]);
        return new Strecke(so, sn, gesamt, dist[b], true);
    }

    static Strecke gerade(double o1, double n1, double o2, double n2) {
        double l = Math.hypot(o2 - o1, n2 - n1);
        return new Strecke(new double[] {o1, o2}, new double[] {n1, n2}, l, 0, false);
    }

    /**
     * Tastet eine Strecke alle {@code schritt} m ab (der letzte Punkt ist das Ziel) und liest den Boden aus dem Gelände
     * ({@code gel} null: NaN). Der Boden wird über ±{@code glaetten} m gemittelt, damit ein Graben neben dem Weg die
     * Augenhöhe nicht springen lässt.
     */
    public static Profil profil(Strecke st, Gelaende gel, double schritt, double glaetten) {
        double[] so = st.ost(), sn = st.nord();
        double[] cum = new double[so.length];
        for (int i = 1; i < so.length; i++) cum[i] = cum[i - 1] + Math.hypot(so[i] - so[i - 1], sn[i] - sn[i - 1]);
        double L = cum[cum.length - 1];
        int n = Math.max(2, (int) Math.ceil(L / schritt) + 1);
        double[] o = new double[n], no = new double[n], s = new double[n], roh = new double[n];
        Gelaende.Abfrage q = gel == null ? null : gel.abfrage();
        int seg = 0;
        for (int k = 0; k < n; k++) {
            double d = k == n - 1 ? L : Math.min(L, k * L / (n - 1));
            while (seg + 2 < cum.length && cum[seg + 1] < d) seg++;
            double sl = cum[seg + 1] - cum[seg], f = sl > 0 ? (d - cum[seg]) / sl : 0;
            o[k] = so[seg] + f * (so[seg + 1] - so[seg]);
            no[k] = sn[seg] + f * (sn[seg + 1] - sn[seg]);
            s[k] = d;
            roh[k] = q == null ? Double.NaN : q.hoehe(o[k], no[k], 1.0);
        }
        double[] boden = new double[n];
        int r = Math.max(0, (int) Math.round(glaetten / Math.max(1e-6, L / (n - 1))));
        for (int k = 0; k < n; k++) {
            double sum = 0;
            int z = 0;
            for (int j = Math.max(0, k - r); j <= Math.min(n - 1, k + r); j++)
                if (!Double.isNaN(roh[j])) {
                    sum += roh[j];
                    z++;
                }
            boden[k] = z > 0 ? sum / z : Double.NaN;
        }
        return new Profil(o, no, s, boden);
    }

    /** Wie viel der Strecke auf Wegen liegt (0 … 1). */
    public static double anteilAufWegen(Strecke s) { return s.laenge() > 0 ? Math.min(1, s.aufWegen() / s.laenge()) : 0; }
}
