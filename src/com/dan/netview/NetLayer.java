package com.dan.netview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Eine Ebene eines Netzblicks (seit LituyaBay1958 Phase 7, Werkbuch 5.12 und 7.10): was gezeichnet werden soll, ohne
 * Datenbank und ohne Netzbibliothek. Linien, Pfeile, Punkte, Isolinien mit Wert (laufen mit der Zeit), Strahlen,
 * Messstrecken mit Werten (als Farbband), Graphen aus Knoten und Kanten. Lage in Weltkoordinaten (x, y, z in m), Breite
 * und Größe in Bildpunkten; Farben als 0xRRGGBB oder über eine {@link Ramp} aus dem Wert.
 * <p>
 * Jedes Stück (Item) bekommt beim Einfügen drei Stufen nach Douglas-Peucker ({@link #lodTolerance}): in der Nähe alle
 * Punkte, weiter weg weniger; {@code gl.NetviewGl} wählt je Bild nach dem Abstand. Der Kern kennt nur Java 21.
 */
public final class NetLayer {
    /** Arten der Stücke. */
    public enum Kind { LINE, ARROW, POINTS, ISOLINE, RAY, BAND, GRAPH }

    /** Ein Stück: Art, Punkte (x, y, z), Werte je Punkt (oder null), Farbe, Breite (px), Stufen (Indizes der behaltenen Punkte). */
    public static final class Item {
        public final Kind kind;
        public final float[] xyz;
        public final float[] values;
        public final int rgb;
        public final float width;
        /** Für {@link Kind#GRAPH}: Kanten als Paare von Punktindizes. */
        public final int[] edges;
        /** Behaltene Punkte je Stufe (0 alle). */
        public final int[][] lod = new int[3][];
        /** Mitte und Radius der Hülle (für die Stufenwahl). */
        public final float cx, cy, cz, radius;

        Item(Kind kind, float[] xyz, float[] values, int rgb, float width, int[] edges, double[] tol) {
            this.kind = kind; this.xyz = xyz; this.values = values; this.rgb = rgb; this.width = width; this.edges = edges;
            int n = xyz.length / 3;
            float lx = Float.MAX_VALUE, ly = lx, lz = lx, hx = -lx, hy = hx, hz = hx;
            for (int i = 0; i < n; i++) {
                lx = Math.min(lx, xyz[3 * i]); hx = Math.max(hx, xyz[3 * i]);
                ly = Math.min(ly, xyz[3 * i + 1]); hy = Math.max(hy, xyz[3 * i + 1]);
                lz = Math.min(lz, xyz[3 * i + 2]); hz = Math.max(hz, xyz[3 * i + 2]);
            }
            cx = (lx + hx) / 2; cy = (ly + hy) / 2; cz = (lz + hz) / 2;
            radius = (float) Math.sqrt((hx - lx) * (hx - lx) + (hy - ly) * (hy - ly) + (hz - lz) * (hz - lz)) / 2;
            boolean simplifiable = kind != Kind.POINTS && kind != Kind.GRAPH;
            for (int l = 0; l < 3; l++) lod[l] = simplifiable && l > 0 ? Simplify.keep(xyz, tol[l]) : identity(n);
        }

        public int points() { return xyz.length / 3; }

        /** Zahl der Strecken in Stufe l. */
        public int segments(int l) {
            return switch (kind) {
                case POINTS -> 0;
                case GRAPH -> edges.length / 2;
                default -> Math.max(0, lod[l].length - 1);
            };
        }

        private static int[] identity(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }
    }

    public final String name;
    public final List<Item> items = new ArrayList<>();
    /** Toleranz (m) der Stufen 1 und 2 nach Douglas-Peucker; Stufe 0 behält alles. */
    public double[] lodTolerance = {0, 4, 25};
    /** Ab welcher Entfernung (m) Stufe 1 und 2 gelten. */
    public double[] lodDistance = {800, 4000};
    /** Werte für die Farbe (wenn ein Stück Werte hat) und die Zeit, bis zu der Isolinien sichtbar sind. */
    public Ramp ramp = Ramp.TIME;
    public boolean visible = true;
    private int version;

    public NetLayer(String name) { this.name = name; }

    public int version() { return version; }

    /** Leert die Ebene. */
    public void clear() { items.clear(); version++; }

    private Item add(Kind k, float[] xyz, float[] values, int rgb, float width, int[] edges) {
        if (xyz.length < 3 || xyz.length % 3 != 0) throw new IllegalArgumentException("netview: Punkte als x, y, z");
        if (values != null && values.length != xyz.length / 3) throw new IllegalArgumentException("netview: ein Wert je Punkt");
        Item it = new Item(k, xyz.clone(), values == null ? null : values.clone(), rgb, width, edges, lodTolerance);
        items.add(it);
        version++;
        return it;
    }

    /** Linienzug in einer Farbe. */
    public Item line(float[] xyz, int rgb, float widthPx) { return add(Kind.LINE, xyz, null, rgb, widthPx, null); }

    /** Linienzug mit Pfeilspitze am Ende. */
    public Item arrow(float[] xyz, int rgb, float widthPx) { return add(Kind.ARROW, xyz, null, rgb, widthPx, null); }

    /** Punkte (Kreise) mit Durchmesser in Bildpunkten; mit Werten über die Rampe gefärbt (rgb &lt; 0). */
    public Item points(float[] xyz, float[] values, int rgb, float sizePx) { return add(Kind.POINTS, xyz, values, rgb, sizePx, null); }

    /** Isolinie zum Wert value (etwa eine Isochrone in s): sichtbar, sobald die Zeit des Blicks den Wert erreicht. */
    public Item isoline(float[] xyz, float value, int rgb, float widthPx) {
        float[] v = new float[xyz.length / 3];
        Arrays.fill(v, value);
        return add(Kind.ISOLINE, xyz, v, rgb, widthPx, null);
    }

    /** Strahl mit Zeit je Punkt: wächst mit der Zeit des Blicks von der Quelle her. */
    public Item ray(float[] xyz, float[] times, int rgb, float widthPx) { return add(Kind.RAY, xyz, times, rgb, widthPx, null); }

    /** Messstrecke mit Wert je Punkt, als Farbband (Rampe) in der Breite widthPx. */
    public Item band(float[] xyz, float[] values, float widthPx) { return add(Kind.BAND, xyz, values, -1, widthPx, null); }

    /** Graph: Knoten (x, y, z), Kanten als Paare von Knotenindizes. */
    public Item graph(float[] nodes, int[] edges, int rgb, float widthPx) {
        if (edges.length % 2 != 0) throw new IllegalArgumentException("netview: Kanten paarweise");
        for (int e : edges) if (e < 0 || e >= nodes.length / 3) throw new IllegalArgumentException("netview: Kante zeigt auf Knoten " + e);
        return add(Kind.GRAPH, nodes, null, rgb, widthPx, edges.clone());
    }

    /**
     * Isolinien eines Rasterfelds (Marching Squares, {@link Contour}) zu den Werten levels, auf der Höhe y; das Feld liegt
     * an den Punkten (ox + i·step, oz + j·step).
     */
    public int isolines(float[] field, int w, int h, double ox, double oz, double step, float y, double[] levels, int rgb, float widthPx) {
        int n = 0;
        for (double lv : levels)
            for (float[] l : Contour.lines(field, w, h, ox, oz, step, lv)) {
                float[] xyz = new float[l.length / 2 * 3];
                for (int k = 0; k < l.length / 2; k++) { xyz[3 * k] = l[2 * k]; xyz[3 * k + 1] = y; xyz[3 * k + 2] = l[2 * k + 1]; }
                if (xyz.length >= 6) { isoline(xyz, (float) lv, rgb, widthPx); n++; }
            }
        return n;
    }

    /** Stufe eines Stücks bei Abstand d (m) zum Auge. */
    public int lodFor(Item it, double d) {
        double e = Math.max(0, d - it.radius);
        return e < lodDistance[0] ? 0 : e < lodDistance[1] ? 1 : 2;
    }

    /** Strecken in allen Stücken, Stufe 0. */
    public long segments() {
        long n = 0;
        for (Item it : items) n += it.segments(0);
        return n;
    }
}
