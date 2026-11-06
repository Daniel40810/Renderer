package com.dan.gputime.demo;

import com.dan.gputime.GpuSpans;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Prüfung von {@link GpuSpans} mit einer erfundenen Uhr (ohne Grafikkarte; seit LituyaBay1958 W6 Runde 6): Dauer je
 * Abschnitt, Summe bei mehrfachem Gebrauch, Verschachtelung, fehlender Abschnitt, gleitendes Mittel, Warten auf
 * Ergebnisse ohne Blockieren, aus.
 */
public final class GpuSpansCheck {
    static int all, bad;

    /** Uhr: jeder Stempel bekommt die Zeit now; fertig erst, wenn delay Bilder vergangen sind. */
    static final class Fake implements GpuSpans.Clock {
        long now;
        int next = 1, frame, delay = 1;
        final Map<Integer, long[]> st = new HashMap<>();   // id → {Zeit, Bild}
        int stamps;
        @Override public int create() { return next++; }
        @Override public void stamp(int id) { st.put(id, new long[]{now, frame}); stamps++; }
        @Override public boolean ready(int id) { long[] s = st.get(id); return s != null && frame - s[1] >= delay; }
        @Override public long nanos(int id) { return st.get(id)[0]; }
    }

    public static void main(String[] a) {
        Fake c = new Fake();
        GpuSpans g = new GpuSpans(c);
        g.weight = 1;   // ohne Mittel: der letzte gelesene Wert
        for (int f = 0; f < 10; f++) {
            g.begin("A"); c.now += 2_000_000;
            g.begin("B"); c.now += 500_000; g.end("B");
            c.now += 1_000_000; g.end("A");
            g.begin("B"); c.now += 250_000; g.end("B");        // zweites Mal im Bild: Summe
            if (f < 5) { g.begin("C"); c.now += 3_000_000; g.end("C"); }
            c.frame++;
            g.endFrame();
        }
        check(Math.abs(g.ms("A") - 3.5) < 1e-9, String.format(Locale.GERMAN, "verschachtelt: A %.2f ms (mit B darin)", g.ms("A")));
        check(Math.abs(g.ms("B") - 0.75) < 1e-9, String.format(Locale.GERMAN, "zweimal im Bild: B %.2f ms (Summe)", g.ms("B")));
        check(g.ms("C") == 0, String.format(Locale.GERMAN, "fehlt im Bild: C %.2f ms", g.ms("C")));
        check(Double.isNaN(g.ms("X")), "unbekannt: NaN");
        check(g.toString().startsWith("A 3,50 · B 0,75 · C 0,00"), "Text: " + g);
        // gleitendes Mittel
        Fake c2 = new Fake();
        GpuSpans m = new GpuSpans(c2);
        for (int f = 0; f < 200; f++) {
            m.begin("A"); c2.now += (f % 2 == 0 ? 1_000_000 : 3_000_000); m.end("A");
            c2.frame++;
            m.endFrame();
        }
        check(Math.abs(m.ms("A") - 2) < 0.15, String.format(Locale.GERMAN, "gleitendes Mittel aus 1 und 3 ms: %.2f ms", m.ms("A")));
        // die Grafikkarte liegt weit zurück: nichts gelesen, nicht gewartet, gezählt
        Fake c3 = new Fake();
        c3.delay = 100;
        GpuSpans w = new GpuSpans(c3);
        for (int f = 0; f < 20; f++) { w.begin("A"); c3.now += 1_000_000; w.end("A"); c3.frame++; w.endFrame(); }
        check(Double.isNaN(w.ms("A")) && w.lost() == 20 - GpuSpans.RING + 1, String.format(Locale.GERMAN, "nicht fertig: kein Wert, %d Bilder übersprungen", w.lost()));
        // Stempel werden wiederverwendet (kein Wachsen je Bild)
        check(c.next - 1 == GpuSpans.RING * 2 * 4, String.format(Locale.GERMAN, "Stempel angelegt: %d (Ring %d × je Abschnitt und Gebrauch 2)", c.next - 1, GpuSpans.RING));
        // aus
        Fake c4 = new Fake();
        GpuSpans o = new GpuSpans(c4);
        o.enabled = false;
        o.begin("A"); o.end("A"); o.endFrame();
        check(c4.stamps == 0 && o.toString().isEmpty(), "aus: kein Stempel");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        if (bad > 0) System.exit(1);
    }

    static void check(boolean ok, String what) {
        all++;
        if (!ok) bad++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
    }
}
