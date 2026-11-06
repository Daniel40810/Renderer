package com.dan.gputime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Benannte Abschnitte der Grafikkarten-Zeit innerhalb eines Bilds (seit LituyaBay1958 W6 Runde 6), mit Zeitstempeln
 * (GL_TIMESTAMP), ohne im Bildlauf zu warten: je Abschnitt ein Paar Stempel in einem Ring von {@link #RING} Bildern; gelesen
 * wird erst, wenn der Treiber das Ergebnis meldet, sonst bleibt das alte Mittel.
 * <p>
 * Gebrauch je Bild: {@code begin(name)} … {@code end(name)} (auch verschachtelt und mehrfach je Bild; dann zählt die
 * Summe), am Ende des Bilds {@link #endFrame()}. {@link #toString()} gibt „Name 1,23 · Name 0,45“ (ms, gleitendes Mittel).
 * Abschnitte, die in einem Bild fehlen, zählen dort als 0 (das Mittel sinkt).
 * <p>
 * Die Stempel stellt ein {@link Clock}; {@code com.dan.gputime.gl.GlClock} für JOGL, die Prüfung nimmt eine erfundene Uhr.
 */
public final class GpuSpans {

    /** Quelle der Zeitstempel. */
    public interface Clock {
        /** Ein neuer Stempel (Kennung). */
        int create();
        /** Den Stempel jetzt in den Befehlsstrom setzen. */
        void stamp(int id);
        /** Liegt das Ergebnis vor? */
        boolean ready(int id);
        /** Zeit in ns (nur nach {@link #ready}). */
        long nanos(int id);
        /** Alle Stempel freigeben. */
        default void delete(int[] ids) { }
    }

    /** Bilder im Ring: so alt ist ein Ergebnis, wenn es gelesen wird (mindestens). */
    public static final int RING = 4;
    /** Gewicht des neuen Werts im Mittel (0,1: rund 10 Bilder). */
    public double weight = 0.1;
    /** Aus: begin/end/endFrame tun nichts (kein Stempel). */
    public boolean enabled = true;

    private final Clock clock;
    private final Map<String, Integer> index = new LinkedHashMap<>();
    private final List<String> names = new ArrayList<>();
    /** je Ring-Platz, je Abschnitt: Paare von Stempeln (wachsend, bei mehrfachem Gebrauch im Bild). */
    private final List<List<int[]>>[] slots;
    private final int[][] used;
    private final int[][] open;
    private double[] mean = new double[0];
    private boolean[] seen = new boolean[0];
    private int frame;
    private long lost;

    @SuppressWarnings("unchecked")
    public GpuSpans(Clock clock) {
        this.clock = clock;
        slots = new List[RING];
        for (int r = 0; r < RING; r++) slots[r] = new ArrayList<>();
        used = new int[RING][];
        open = new int[RING][];
        for (int r = 0; r < RING; r++) { used[r] = new int[0]; open[r] = new int[0]; }
    }

    private int id(String name) {
        Integer i = index.get(name);
        if (i != null) return i;
        int k = names.size();
        index.put(name, k);
        names.add(name);
        mean = java.util.Arrays.copyOf(mean, k + 1);
        seen = java.util.Arrays.copyOf(seen, k + 1);
        for (int r = 0; r < RING; r++) {
            slots[r].add(new ArrayList<>());
            used[r] = java.util.Arrays.copyOf(used[r], k + 1);
            open[r] = java.util.Arrays.copyOf(open[r], k + 1);
        }
        return k;
    }

    public void begin(String name) {
        if (!enabled) return;
        int r = frame % RING, k = id(name);
        List<int[]> l = slots[r].get(k);
        int u = used[r][k];
        if (u == l.size()) l.add(new int[]{clock.create(), clock.create()});
        clock.stamp(l.get(u)[0]);
        open[r][k] = u + 1;   // offen: Paar u (+1, damit 0 „keins“ heißt)
    }

    public void end(String name) {
        if (!enabled) return;
        int r = frame % RING;
        Integer k = index.get(name);
        if (k == null || open[r][k] == 0) return;
        int u = open[r][k] - 1;
        clock.stamp(slots[r].get(k).get(u)[1]);
        open[r][k] = 0;
        used[r][k] = u + 1;
    }

    /** Am Ende des Bilds: das älteste Bild im Ring lesen (falls fertig) und den Platz für das nächste freimachen. */
    public void endFrame() {
        if (!enabled) return;
        frame++;
        int r = frame % RING;   // der Platz des nächsten Bilds: das älteste im Ring
        if (frame >= RING) {
            int n = names.size();
            boolean all = true;
            for (int k = 0; k < n && all; k++)
                for (int u = 0; u < used[r][k] && all; u++) { int[] p = slots[r].get(k).get(u); all = clock.ready(p[0]) && clock.ready(p[1]); }
            if (all) {
                for (int k = 0; k < n; k++) {
                    double ms = 0;
                    for (int u = 0; u < used[r][k]; u++) { int[] p = slots[r].get(k).get(u); ms += Math.max(0, clock.nanos(p[1]) - clock.nanos(p[0])) / 1e6; }
                    mean[k] = seen[k] ? mean[k] + (ms - mean[k]) * weight : ms;
                    seen[k] = true;
                }
            } else lost++;
        }
        java.util.Arrays.fill(used[r], 0);
        java.util.Arrays.fill(open[r], 0);
    }

    /** Mittel in ms (NaN, solange nichts gelesen ist oder der Name fehlt). */
    public double ms(String name) {
        Integer k = index.get(name);
        return k == null || !seen[k] ? Double.NaN : mean[k];
    }

    /** Bilder, deren Stempel beim Lesen noch nicht fertig waren (die Grafikkarte lag mehr als {@link #RING} Bilder zurück). */
    public long lost() { return lost; }

    public List<String> names() { return java.util.Collections.unmodifiableList(names); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < names.size(); k++) {
            if (!seen[k]) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(names.get(k)).append(' ').append(String.format(Locale.GERMAN, "%.2f", mean[k]));
        }
        return sb.toString();
    }

    /** Alle Stempel freigeben. */
    public void dispose() {
        List<Integer> ids = new ArrayList<>();
        for (int r = 0; r < RING; r++) for (List<int[]> l : slots[r]) for (int[] p : l) { ids.add(p[0]); ids.add(p[1]); }
        int[] a = new int[ids.size()];
        for (int i = 0; i < a.length; i++) a[i] = ids.get(i);
        clock.delete(a);
    }
}
