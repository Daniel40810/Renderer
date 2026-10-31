package com.dan.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Wegenetze: ein Graph mit Knoten und Kanten (Länge, Steigung, Art, Breite, Einbahn), kürzeste Wege, Umkreise nach
 * Kosten und die Geschwindigkeit von Fußgängern nach der Dichte (Werkbuch Timmendorf, Abschnitte 6, 7 und 8). <b>Hülle
 * seit Timmendorf Phase 1</b>, nach {@code muster}: Graph, Dijkstra, Umkreis, Weidmann 1993 und die Abfragen ohne
 * Grafikkarte. Phase 10 bringt mehrere Netze, Ströme mit Kapazität und Stau, Ziele und Zeiten.
 * <p>
 * Das Package kennt keine Datenbank: die App füllt den Graphen aus dem Oracle Network Data Model oder aus einer Datei
 * (Werkbuch, F20 A). Raum der Szenen: x Ost, y oben, z Süd, Meter.
 * <p>
 * Der Kern kennt nur Java 21.
 */
public final class Route {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): Zahl der Gänger in der Nähe ab Phase 10. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Arten der Kanten (aus OSM highway, man_made=pier, Fahrwasser). */
    public enum Kind { FOOTWAY, PROMENADE, PIER, STEPS, BEACH, ROAD, BUS, FAIRWAY }

    /** Ein Knoten: Nummer (aus der Quelle), Lage (m). */
    public record Node(long id, double x, double y, double z) { }

    /**
     * Eine Kante zwischen zwei Knoten (Index im Graphen): Länge (m), Art, Breite (m), Einbahn (nur a → b); seit
     * Timmendorf Phase 10 die erlaubte Geschwindigkeit (m/s, 0 = keine Angabe; Straßen).
     */
    public record Edge(int a, int b, double length, Kind kind, double width, boolean oneway, double speed) {
        /** Steigung von a nach b im Graphen g (Anteil). */
        public double slope(Route g) { return (g.node(b).y() - g.node(a).y()) / Math.max(1e-6, length); }
    }

    /** Kosten einer Kante in Richtung a → b (s oder m); Standard: Länge durch Gehgeschwindigkeit. */
    @FunctionalInterface
    public interface Cost { double cost(Route g, Edge e, boolean forward); }

    /** Ein gefundener Weg: Knoten in Reihenfolge, seit Phase 10 die Kanten dazwischen (Index), Länge (m), Kosten. */
    public record Path(int[] nodes, int[] edges, double length, double cost) {
        public boolean found() { return nodes.length > 0; }
    }

    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Freie Gehgeschwindigkeit (m/s) nach Weidmann 1993. */
    public double v0 = 1.34;
    private final List<Node> nodes = new ArrayList<>();
    private final Map<Long, Integer> index = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<List<Integer>> adj = new ArrayList<>();

    // ------------------------------------------------------------------ Aufbau

    /** Fügt einen Knoten hinzu oder gibt den vorhandenen mit derselben Nummer zurück (Index). */
    public int node(long id, double x, double y, double z) {
        Integer i = index.get(id);
        if (i != null) return i;
        nodes.add(new Node(id, x, y, z));
        cells = null;
        adj.add(new ArrayList<>());
        index.put(id, nodes.size() - 1);
        return nodes.size() - 1;
    }

    /** Kante zwischen zwei Knoten-Indizes; die Länge ist der Abstand im Raum. */
    public Edge edge(int a, int b, Kind kind, double width, boolean oneway) { return edge(a, b, kind, width, oneway, 0); }

    /** Seit Timmendorf Phase 10: Kante mit erlaubter Geschwindigkeit (m/s, 0 = keine Angabe). */
    public Edge edge(int a, int b, Kind kind, double width, boolean oneway, double speed) {
        Node p = nodes.get(a), q = nodes.get(b);
        Edge e = new Edge(a, b, Math.sqrt(Math.pow(q.x() - p.x(), 2) + Math.pow(q.y() - p.y(), 2) + Math.pow(q.z() - p.z(), 2)), kind, width, oneway, speed);
        edges.add(e);
        adj.get(a).add(edges.size() - 1);
        adj.get(b).add(edges.size() - 1);
        return e;
    }

    public Node node(int i) { return nodes.get(i); }
    public int indexOf(long id) { Integer i = index.get(id); return i == null ? -1 : i; }
    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return edges.size(); }
    public List<Edge> edges() { return List.copyOf(edges); }
    /** Seit Phase 10: eine Kante nach Index, die Kanten an einem Knoten (Indizes). */
    public Edge edge(int i) { return edges.get(i); }
    public List<Integer> edgesAt(int node) { return java.util.Collections.unmodifiableList(adj.get(node)); }

    /** Gesamtlänge aller Kanten (m). */
    public double totalLength() { return edges.stream().mapToDouble(Edge::length).sum(); }

    /** Knoten mit nur einer Kante (lose Enden; bei einem Netz aus GeoJSON ein Hinweis auf fehlende Verknüpfung). */
    public int danglingCount() {
        int n = 0;
        for (List<Integer> a : adj) if (a.size() == 1) n++;
        return n;
    }

    /** Der Knoten am nächsten zu (x, z) in der Ebene; −1 bei leerem Graphen. Seit Phase 10 über ein Raster. */
    public int nearest(double x, double z) { return nearest(x, z, null); }

    /**
     * Seit Phase 10: der nächste Knoten, an dem eine Kante einer der Arten liegt (null: jede Art), gesucht in Ringen
     * eines Rasters zu {@link #CELL} m; −1, wenn es keinen gibt.
     */
    public int nearest(double x, double z, java.util.Set<Kind> kinds) {
        if (nodes.isEmpty()) return -1;
        Map<Long, List<Integer>> c = cells();
        int cx = (int) Math.floor(x / CELL), cz = (int) Math.floor(z / CELL);
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int r = 0; r <= maxRing; r++) {
            for (int j = -r; j <= r; j++)
                for (int i = -r; i <= r; i++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != r) continue;
                    List<Integer> l = c.get(key(cx + i, cz + j));
                    if (l == null) continue;
                    for (int n : l) {
                        if (kinds != null && !hasKind(n, kinds)) continue;
                        double d = Math.hypot(nodes.get(n).x() - x, nodes.get(n).z() - z);
                        if (d < bd) { bd = d; best = n; }
                    }
                }
            // alles in Ring r + 1 ist mindestens r · CELL entfernt
            if (best >= 0 && bd <= r * CELL) break;
        }
        return best;
    }

    private boolean hasKind(int n, java.util.Set<Kind> kinds) {
        for (int e : adj.get(n)) if (kinds.contains(edges.get(e).kind())) return true;
        return false;
    }

    /** Kante des Rasters für {@link #nearest} (m). */
    public static final double CELL = 50;
    private Map<Long, List<Integer>> cells;
    private int maxRing;

    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    private synchronized Map<Long, List<Integer>> cells() {
        if (cells != null) return cells;
        Map<Long, List<Integer>> c = new HashMap<>();
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            c.computeIfAbsent(key((int) Math.floor(n.x() / CELL), (int) Math.floor(n.z() / CELL)), k -> new ArrayList<>()).add(i);
            x0 = Math.min(x0, n.x()); x1 = Math.max(x1, n.x()); z0 = Math.min(z0, n.z()); z1 = Math.max(z1, n.z());
        }
        maxRing = (int) Math.ceil(Math.max(x1 - x0, z1 - z0) / CELL) + 2;
        cells = c;
        return c;
    }

    // ------------------------------------------------------------------ Wege

    /** Standardkosten: Gehzeit in s mit freier Geschwindigkeit, Treppen halb so schnell [G]. */
    public Cost walking() { return (g, e, f) -> e.length() / (v0 * (e.kind() == Kind.STEPS ? 0.5 : 1)); }

    /** Kosten als Länge (m). */
    public static Cost distance() { return (g, e, f) -> e.length(); }

    /** Seit Phase 10: Fahrzeit (s) mit der erlaubten Geschwindigkeit, ohne Angabe 50 km/h; Fußwege und See gesperrt. */
    public static Cost driving() {
        return (g, e, f) -> e.kind() == Kind.ROAD || e.kind() == Kind.BUS ? e.length() / (e.speed() > 0 ? e.speed() : 13.9) : Double.POSITIVE_INFINITY;
    }

    /** Seit Phase 10: Kapazität einer Kante für Fußgänger (Personen/s): Breite · größter Fluss nach Weidmann. */
    public double capacity(Edge e) { return Math.max(0.5, e.width()) * maxFlow(); }

    /** Größter Fluss je Meter Breite (Personen/(m·s)) nach Weidmann. */
    public double maxFlow() {
        double best = 0;
        for (double r = 0.05; r < RHO_MAX; r += 0.01) best = Math.max(best, flow(r));
        return best;
    }

    /** Kürzester Weg von a nach b (Dijkstra) mit den Kosten c; Einbahnkanten nur vorwärts. */
    public Path shortest(int a, int b, Cost c) { return search(a, b, c, 0, Double.MAX_VALUE).path(this, a, b); }

    /**
     * Seit Phase 10: kürzester Weg mit A*; {@code perMetre} sind die kleinsten Kosten je Meter Luftlinie (für die Gehzeit
     * 1/v₀, für die Länge 1). Gleiches Ergebnis wie {@link #shortest}, aber nur ein Bruchteil des Netzes wird besucht.
     */
    public Path astar(int a, int b, Cost c, double perMetre) { return search(a, b, c, perMetre, Double.MAX_VALUE).path(this, a, b); }

    /** Alle Knoten, die von a aus mit Kosten bis {@code max} erreichbar sind (Index → Kosten). */
    public Map<Integer, Double> within(int a, Cost c, double max) {
        double[] dist = search(a, -1, c, 0, max).dist;
        Map<Integer, Double> m = new HashMap<>();
        for (int i = 0; i < dist.length; i++) if (dist[i] <= max) m.put(i, dist[i]);
        return m;
    }

    /** Zahl der Knoten, die die letzte Suche besucht hat (Prüfung von A*). */
    public volatile int lastVisited;

    private record Search(double[] dist, int[] prev) {
        Path path(Route g, int a, int b) {
            if (b < 0 || b >= dist.length || Double.isInfinite(dist[b])) return new Path(new int[0], new int[0], 0, Double.POSITIVE_INFINITY);
            List<Integer> rev = new ArrayList<>(), revE = new ArrayList<>();
            double len = 0;
            for (int v = b; v != a; ) {
                rev.add(v);
                int e = prev[v];
                revE.add(e);
                Edge ed = g.edges.get(e);
                len += ed.length();
                v = ed.a() == v ? ed.b() : ed.a();
            }
            rev.add(a);
            int[] p = new int[rev.size()], pe = new int[revE.size()];
            for (int i = 0; i < p.length; i++) p[i] = rev.get(p.length - 1 - i);
            for (int i = 0; i < pe.length; i++) pe[i] = revE.get(pe.length - 1 - i);
            return new Path(p, pe, len, dist[b]);
        }
    }

    private Search search(int a, int target, Cost c, double perMetre, double max) {
        int n = nodes.size();
        double[] dist = new double[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        int[] prev = new int[n];
        Arrays.fill(prev, -1);
        int visited = 0;
        if (a < 0 || a >= n) return new Search(dist, prev);
        Node t = target >= 0 && target < n ? nodes.get(target) : null;
        dist[a] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((p, r) -> Double.compare(p[0], r[0]));
        q.add(new double[]{h(a, t, perMetre), a, 0});
        while (!q.isEmpty()) {
            double[] top = q.poll();
            int v = (int) top[1];
            if (top[2] > dist[v]) continue;
            visited++;
            if (v == target || top[2] > max) break;
            for (int ei : adj.get(v)) {
                Edge e = edges.get(ei);
                boolean fwd = e.a() == v;
                if (!fwd && e.oneway()) continue;
                int w = fwd ? e.b() : e.a();
                double d = dist[v] + c.cost(this, e, fwd);
                if (d < dist[w]) {
                    dist[w] = d;
                    prev[w] = ei;
                    q.add(new double[]{d + h(w, t, perMetre), w, d});
                }
            }
        }
        lastVisited = visited;
        return new Search(dist, prev);
    }

    private double h(int v, Node t, double perMetre) {
        if (t == null || perMetre <= 0) return 0;
        Node p = nodes.get(v);
        return Math.hypot(p.x() - t.x(), p.z() - t.z()) * perMetre;
    }

    // ------------------------------------------------------------------ Fußgänger

    /** Dichte, bei der nichts mehr geht (Personen/m²), und Form der Kurve (m⁻²), nach Weidmann 1993. */
    public static final double RHO_MAX = 5.4, GAMMA = 1.913;

    /** Gehgeschwindigkeit bei der Dichte ρ (Personen/m²): v₀·(1 − exp(−γ·(1/ρ − 1/ρ_max))). */
    public double weidmann(double rho) {
        if (rho <= 0) return v0;
        if (rho >= RHO_MAX) return 0;
        return v0 * (1 - Math.exp(-GAMMA * (1 / rho - 1 / RHO_MAX)));
    }

    /** Fluss je Meter Breite (Personen/(m·s)) bei der Dichte ρ: ρ·v(ρ). */
    public double flow(double rho) { return rho * weidmann(rho); }

    /** Wie viel los ist (0..1), für das Platzhalterbild in {@code gl/}. */
    public double strength() { return Math.min(1, edges.size() / 50.0); }
}
