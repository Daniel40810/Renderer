package com.dan.lauf.demo;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Stoppuhr;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.dan.core.pruef.Attest.f;

/**
 * Eichlast der Karte (Seegang Werkbuch 108, S9c-2, SF180 A): eine feste Arbeit, an der sich ablesen lässt, ob die Karte
 * gerade so schnell ist wie sonst. Die Abnahme misst sie vor und nach jeder Stufe; liegt sie mehr als {@link #GRENZE}
 * über dem besten Wert des Laufs, gilt die Stufe als gestört.
 * <ul>
 * <li><b>Rechnen</b> ({@code eichlast.comp}, modus 0): {@link #GRUPPEN} × 64 Fäden, je {@link #SCHRITTE} Runden aus vier
 * vec4-FMA, ohne Speicher; folgt dem Takt der Karte.</li>
 * <li><b>Speicher</b> (modus 1): {@link #N} vec4 (32 MB) von einem Puffer in den anderen; folgt dem Speicher.</li>
 * </ul>
 * Gemessen mit Zeitstempeln auf der Warteschlange zum Rechnen, nachdem die Karte leer ist ({@link Geraet#ruhe}); einmal zum
 * Anlaufen, dann {@link #MAL}-mal, je Teil der Median.
 * <p>
 * Seit S9d-1 (Seegang Werkbuch 109, SF184 A): vor jeder Messung die <b>Vorlast</b>, rund {@link #VORLAST_MS} ms derselbe
 * Rechenkern ungemessen, in Aufträgen zu {@link #VORLAST_JE} Durchgängen. L7k zeigte: Nach einer leichten Stufe taktet die
 * Karte niedriger, und die kurze Eichlast (rund 4 ms) maß diesen Takt mit (Mittel und Niedrig ×1,1–1,9 bei gleicher
 * Bildzeit). Mit Vorlast misst sie das Budget der Karte und Arbeit daneben, nicht die Last davor [S].
 */
final class Eichlast implements AutoCloseable {
    static final String PFAD = "/com/dan/lauf/demo/eichlast.comp";
    static final int SCHRITTE = 1024, GRUPPEN = 4096, N = 1 << 21, MAL = 3;
    /** Ab diesem Verhältnis zum besten Wert des Laufs gilt eine Messung als gestört (SF180 A: mehr als 10 %). */
    static final double GRENZE = 1.10;
    static final String RECHNEN = "Eichlast, Rechnen", SPEICHER = "Eichlast, Speicher";
    /** Seit S9d-1 (SF184 A): Dauer der Vorlast (ms, Wanduhr) und Durchgänge des Rechenkerns je Auftrag. */
    static final int VORLAST_MS = 150, VORLAST_JE = 8;

    /** Ein Wert der Eichlast: Rechnen und Speicher in ms, dazu die Uhrzeit. */
    record Wert(double rechnen, double speicher, long uhr) {
        double summe() { return rechnen + speicher; }
        @Override public String toString() { return f("%.2f ms (Rechnen %.2f, Speicher %.2f)", summe(), rechnen, speicher); }
    }

    private final Geraet g;
    private final Kernel k;
    private final Puffer a, b;
    private final Stoppuhr uhr;
    private final long satz;
    /** Bester Wert (Summe) im Lauf bisher. */
    private double bester = Double.POSITIVE_INFINITY;
    private int messungen;
    /** Seit S9d-1: die letzte Vorlast – Aufträge und Dauer (ms). */
    private int vorlastAuftraege;
    private double vorlastMs;

    Eichlast(Geraet g) {
        this.g = g;
        var konto = g.konto("Eichlast", 96L << 20);
        k = Kernel.lade(g, PFAD, 16, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
        a = Puffer.speicher(g, konto, "Eichlast a", 16L * N);
        b = Puffer.speicher(g, konto, "Eichlast b", 16L * N);
        a.nullen();
        uhr = new Stoppuhr(g, g.rechnen, 2);
        satz = k.satz(a, b);
    }

    /** False, wenn die Warteschlange keine Zeitstempel hat; dann misst {@link #miss} nichts. */
    boolean verfuegbar() { return uhr.verfuegbar; }

    double bester() { return bester; }

    int messungen() { return messungen; }

    /** Seit S9d-1: die letzte Vorlast als Text. */
    String vorlast() { return f("%.0f ms in %d Aufträgen zu %d Durchgängen", vorlastMs, vorlastAuftraege, VORLAST_JE); }

    /** Misst die Eichlast (Median aus {@link #MAL}) und merkt sich den besten Wert. */
    Wert miss() {
        g.ruhe();
        vorlaufen();
        double[] r = new double[MAL], s = new double[MAL];
        for (int j = -1; j < MAL; j++) {
            einmal();
            if (j < 0) continue;
            r[j] = uhr.letzte(RECHNEN);
            s[j] = uhr.letzte(SPEICHER);
        }
        Arrays.sort(r);
        Arrays.sort(s);
        Wert w = new Wert(r[MAL / 2], s[MAL / 2], System.currentTimeMillis());
        if (verfuegbar() && w.summe() > 0) bester = Math.min(bester, w.summe());
        messungen++;
        return w;
    }

    /** Verhältnis eines Werts zum besten des Laufs (1 = so schnell wie am schnellsten). */
    double verhaeltnis(Wert w) { return bester > 0 && Double.isFinite(bester) ? w.summe() / bester : 1; }

    /** True, wenn der Wert mehr als {@link #GRENZE} über dem besten des Laufs liegt. */
    boolean gestoert(Wert w) { return verhaeltnis(w) > GRENZE; }

    /** Seit S9d-1 (SF184 A): rund {@link #VORLAST_MS} ms der Rechenkern ungemessen, Auftrag an Auftrag, damit der Takt oben ist. */
    private void vorlaufen() {
        ByteBuffer pr = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).putInt(0, 0).putInt(4, SCHRITTE).putInt(8, N).putInt(12, 0);
        long t0 = System.nanoTime(), ende = t0 + VORLAST_MS * 1_000_000L;
        int n = 0;
        do {
            Ablauf ab = new Ablauf(g);
            for (int i = 0; i < VORLAST_JE; i++) ab.rechne("Eichlast, Vorlast", k, satz, pr, GRUPPEN, 1, 1, new Mittel[0], new Mittel[] {b});
            g.rechnen.einmal(c -> ab.aufnehmen(c, null));
            n++;
        } while (System.nanoTime() < ende);
        vorlastAuftraege = n;
        vorlastMs = (System.nanoTime() - t0) * 1e-6;
    }

    private void einmal() {
        // Ablauf.rechne kopiert die Werte; die Schicht lauf nimmt kein LWJGL (Schichtenvertrag), also ein Puffer im Heap
        ByteBuffer pr = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).putInt(0, 0).putInt(4, SCHRITTE).putInt(8, N).putInt(12, 0);
        ByteBuffer ps = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).putInt(0, 1).putInt(4, 0).putInt(8, N).putInt(12, 0);
        Ablauf ab = new Ablauf(g)
                .rechne(RECHNEN, k, satz, pr, GRUPPEN, 1, 1, new Mittel[0], new Mittel[] {b})
                .rechne(SPEICHER, k, satz, ps, N / 64, 1, 1, new Mittel[] {a}, new Mittel[] {b});
        g.rechnen.einmal(c -> {
            uhr.runde(c);
            ab.aufnehmen(c, uhr);
        });
        uhr.sammle(true);
    }

    @Override public void close() {
        g.ruhe();
        uhr.close();
        b.close();
        a.close();
        k.close();
    }
}
