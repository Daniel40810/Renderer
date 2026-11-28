package com.dan.core.szene;

import java.util.ArrayList;
import java.util.List;

/**
 * Ereignisse für den Ton (Seegang Werkbuch, Datei 104, S8c, SF159 A): was in der Szene einmal klingt – ein Brecher an einer
 * Kante, ein Überlauf über eine Krone, ein Bugschlag, das Typhon beim Ablegen. Die Module melden ({@link #melde}), die
 * Anwendung holt ({@link #seit}) und gibt sie dem Klang (im Steuerhaus aus {@code com.dan.soundscape}, mit Laufzeit und Pegel
 * nach dem Abstand). Jedes Ereignis hat eine laufende Nummer; ein Ring hält die letzten {@link #RING}.
 * <p>
 * Ort in UTM (Ost, Nord) und Höhe (m NHN), Stärke 0 … 1, Zeit in s der Szene (wann es klingt). Synchronisiert: melden und
 * holen dürfen verschiedene Fäden.
 */
public final class Tonereignisse {
    public static final String VERSION = "0.1";
    /** Arten. */
    public static final String BRECHER = "Brecher", UEBERLAUF = "Überlauf", BUGSCHLAG = "Bugschlag", TYPHON = "Typhon";
    /** So viele behält der Ring. */
    public static final int RING = 1024;

    /** Ein Ereignis. */
    public record Ereignis(long nr, String art, double ost, double nord, double hoehe, double staerke, double t) { }

    private final Ereignis[] ring = new Ereignis[RING];
    private long naechste = 1;
    /** Zahl je Art seit dem Anfang (für Prüfungen). */
    private final java.util.Map<String, Long> zahl = new java.util.TreeMap<>();

    /** Meldet ein Ereignis; gibt seine Nummer zurück. */
    public synchronized long melde(String art, double ost, double nord, double hoehe, double staerke, double t) {
        long nr = naechste++;
        ring[(int) (nr % RING)] = new Ereignis(nr, art, ost, nord, hoehe, Math.max(0, Math.min(1, staerke)), t);
        zahl.merge(art, 1L, Long::sum);
        return nr;
    }

    /** Die Ereignisse mit Nummer über nr, der Reihe nach (höchstens die letzten {@link #RING}). */
    public synchronized List<Ereignis> seit(long nr) {
        List<Ereignis> l = new ArrayList<>();
        for (long k = Math.max(nr + 1, naechste - RING); k < naechste; k++) {
            Ereignis e = ring[(int) (k % RING)];
            if (e != null && e.nr() == k) l.add(e);
        }
        return l;
    }

    /** Die Nummer des letzten Ereignisses (0: keins). */
    public synchronized long letzte() { return naechste - 1; }

    /** Zahl der Ereignisse einer Art seit dem Anfang. */
    public synchronized long zahl(String art) { return zahl.getOrDefault(art, 0L); }
}
