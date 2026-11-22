package com.dan.core.takt;

import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.zeit.Uhr;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Takt (Werkbuch HalligHooge Teil A, 5.6): Jedes Modul hat seinen eigenen Schritt und ein Kopplungsintervall. Der
 * Takt ruft die Module so, dass jedes die Felder der anderen zu seiner Startzeit vorfindet.
 * <ul>
 * <li><b>Reihenfolge.</b> Es rechnet immer das Modul, das am weitesten zurückliegt; bei Gleichstand das früher
 * angemeldete. Ein Modul liest die anderen zur Startzeit seines Intervalls; die liegt dann immer zwischen deren
 * beiden jüngsten Ständen.</li>
 * <li><b>Antrieb im Intervall (seit P5c).</b> Liest ein Modul ein Feld über sein ganzes Intervall
 * ({@code Anmeldung.liestImIntervall}: Randwerte, Wind), rechnet dessen Schreiber vorher bis zum Ende dieses
 * Intervalls, auch wenn er dann nicht am weitesten zurückliegt. Die Reihenfolge hängt weiter nur von den
 * Kopplungsintervallen ab.</li>
 * <li><b>Reproduzierbar.</b> Die Reihenfolge hängt nur von den Kopplungsintervallen ab, nicht von der Rechenzeit:
 * Gleicher {@code seed} und gleiche Anmeldung geben auf demselben Gerät dasselbe Ergebnis, ob im eigenen Faden oder
 * nicht ({@link #protokoll}).</li>
 * <li><b>Rechenzeit ist nicht Bildzeit.</b> {@link #laufen} rechnet in einem eigenen Faden der Uhr voraus: Ein Modul
 * beginnt sein nächstes Intervall, sobald die Uhr in sein jüngstes eingetreten ist. So liegt die Uhr immer in den
 * drei Ständen, die der Bus hält; das Bild führt sie mit {@link #fuehre} und liest zwischen zwei Ständen.</li>
 * <li><b>Zeitraffer.</b> Das Ziel steht in {@link #ziel}. {@link #fuehre} hält die Uhr auf dem Ziel, solange die
 * Physik es schafft; sonst sinkt der Zeitraffer auf das Erreichbare ({@link #erreichbar}) und steigt wieder, wenn die
 * Physik aufholt. Die Uhr läuft der Physik nie davon. Schneller geht es nur aus dem Archiv.</li>
 * </ul>
 * Die Arbeit auf der Karte und in weiteren Fäden ist Sache der Module; der Takt ruft sie nacheinander.
 */
public final class Takt {
    private final Bus bus;
    private final List<Modul> module;
    private final double[] zeit, kopplung;
    /** Je Modul die Rechenzeit seiner Schritte (s), seit P8b für den Vergleich der Module. */
    private final double[] rechenzeit;
    /** Je Modul die Module (Indizes), deren Felder es im Intervall liest. */
    private final int[][] antrieb;
    /** Wer wann von wo bis wo gerechnet hat; nur gefüllt, wenn {@link #mitschreiben} gesetzt ist. */
    public final List<String> protokoll = new ArrayList<>();
    public volatile boolean mitschreiben;
    /** Der gewünschte Zeitraffer: Szenensekunden je Laufzeitsekunde. */
    public volatile double ziel = 1;
    /** Anteil des gemessenen Zeitraffers, auf den {@link #fuehre} senkt [G]. */
    public double reserve = 0.9;
    private volatile double stand = Double.NaN, erreichbar = Double.NaN;
    private volatile boolean laeuft;
    private volatile Throwable fehler;
    private Thread faden;
    private volatile long schritte;
    private final java.util.concurrent.atomic.AtomicLong angehalten = new java.util.concurrent.atomic.AtomicLong();

    public Takt(Bus bus) {
        this.bus = bus;
        module = new ArrayList<>();
        for (Modul m : bus.module()) if (!Double.isInfinite(bus.kopplung(m))) module.add(m);        // wer nur liest, wird nicht getaktet
        if (module.isEmpty()) throw new IllegalArgumentException("Takt: kein Modul mit Kopplungsintervall");
        zeit = new double[module.size()];
        kopplung = new double[module.size()];
        rechenzeit = new double[module.size()];
        for (int i = 0; i < kopplung.length; i++) kopplung[i] = bus.kopplung(module.get(i));
        antrieb = new int[module.size()][];
        for (int i = 0; i < antrieb.length; i++) {
            List<Modul> a = bus.antriebe(module.get(i));
            antrieb[i] = new int[a.size()];
            for (int k = 0; k < a.size(); k++) {
                antrieb[i][k] = module.indexOf(a.get(k));
                if (antrieb[i][k] < 0) throw new IllegalArgumentException("Takt: " + module.get(i).name() + " liest im Intervall von " + a.get(k).name() + ", das nicht getaktet wird");
            }
        }
    }

    /**
     * Setzt alle Module auf die Zeit t0 und lässt sie ihren Anfangsstand ablegen: in der Folge der Anmeldung, ein Antrieb
     * aber vor dem Modul, das ihn im Intervall liest.
     */
    public synchronized void beginn(double t0) {
        boolean[] fertig = new boolean[zeit.length];
        for (int i = 0; i < zeit.length; i++) beginne(i, t0, fertig);
        stand = t0;
        schritte = 0;
    }

    private void beginne(int i, double t0, boolean[] fertig) {
        if (fertig[i]) return;
        fertig[i] = true;
        for (int a : antrieb[i]) beginne(a, t0, fertig);
        zeit[i] = t0;
        module.get(i).beginn(t0, bus);
    }

    /** Rechenzeit (s) aller Schritte des Moduls mit diesem Namen seit dem Beginn; NaN, wenn es nicht getaktet wird. */
    public synchronized double rechenzeit(String name) {
        for (int i = 0; i < module.size(); i++) if (module.get(i).name().equals(name)) return rechenzeit[i];
        return Double.NaN;
    }

    /** Rechenzeit aller Module zusammen (s), seit P13d (A33): zum Vergleich mit der Wanduhr im selben Fenster. */
    public synchronized double rechenzeitAlle() {
        double s = 0;
        for (double r : rechenzeit) s += r;
        return s;
    }

    /** Die Zeit, bis zu der alle Module gerechnet haben (s). */
    public double zeit() { return stand; }

    /**
     * Rechnet einen Schritt des Moduls, das am weitesten zurückliegt; liest es im Intervall von einem anderen, das noch
     * nicht bis zum Ende dieses Intervalls gerechnet hat, rechnet erst jenes.
     */
    public synchronized void schritt() {
        int m = naechster();
        double t0 = zeit[m], t1 = t0 + kopplung[m];
        long n0 = System.nanoTime();
        module.get(m).schritt(t0, t1, bus);
        long n1 = System.nanoTime();
        zeit[m] = t1;
        double min = zeit[0];
        for (double z : zeit) min = Math.min(min, z);
        double vor = min - stand, dauer = (n1 - n0) * 1e-9;
        rechenzeit[m] += dauer;
        stand = min;
        schritte++;
        if (mitschreiben) protokoll.add(module.get(m).name() + " " + t0 + " " + t1);
        // gemessener Zeitraffer: Modellsekunden je Rechensekunde; ältere Schritte verblassen mit der Laufzeit
        double alt = letzter == 0 ? 0 : Math.exp(-(n1 - letzter) * 1e-9 / fenster);
        letzter = n1;
        rechen = rechen * alt + dauer;
        modell = modell * alt + vor;
        if (rechen > 0 && modell > 0) erreichbar = modell / rechen;
    }

    private double rechen, modell;
    private long letzter;
    /** Laufzeit (s), nach der ein Schritt nur noch mit 1/e in den erreichbaren Zeitraffer eingeht [G]. */
    public double fenster = 1;

    /** Rechnet, bis alle Module mindestens die Zeit tZiel erreicht haben. */
    public void bis(double tZiel) {
        while (stand < tZiel) schritt();
    }

    /** Zahl der Modulschritte seit {@link #beginn}. */
    public long schritte() { return schritte; }

    /** Gemessener Zeitraffer der Physik: Modellsekunden je Rechensekunde (NaN vor dem ersten Schritt). */
    public double erreichbar() { return erreichbar; }

    /** Wie oft {@link #fuehre} die Uhr am Stand der Physik anhalten musste. */
    public long angehalten() { return angehalten.get(); }

    // ------------------------------------------------------------------ eigener Faden

    /**
     * Startet einen Faden, der der Uhr vorausrechnet: Das Modul, das am weitesten zurückliegt, rechnet sein nächstes
     * Intervall, sobald die Uhr in sein jüngstes eingetreten ist. {@link #beginn} muss vorher gerufen sein.
     */
    public synchronized void laufen(Uhr uhr) {
        if (laeuft) return;
        laeuft = true;
        fehler = null;
        faden = new Thread(() -> {
            try {
                while (laeuft) {
                    if (faellig(uhr.sekunden())) schritt();
                    else Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                fehler = e;
                laeuft = false;
            }
        }, "takt");
        faden.setDaemon(true);
        faden.start();
    }

    /** Ist das Modul, das am weitesten zurückliegt, höchstens ein Kopplungsintervall vor der Zeit t? */
    private synchronized boolean faellig(double t) {
        int m = 0;
        for (int i = 1; i < zeit.length; i++) if (zeit[i] < zeit[m]) m = i;
        return zeit[m] - kopplung[m] <= t;
    }

    /** Das Modul, das als nächstes rechnet: das am weitesten zurückliegende, oder ein Antrieb, den es noch braucht. */
    private int naechster() {
        int m = 0;
        for (int i = 1; i < zeit.length; i++) if (zeit[i] < zeit[m]) m = i;
        for (int tiefe = 0; tiefe <= zeit.length; tiefe++) {
            double ende = zeit[m] + kopplung[m];
            int w = -1;
            for (int a : antrieb[m]) if (zeit[a] < ende - 1e-9 * Math.max(1, Math.abs(ende)) && (w < 0 || zeit[a] < zeit[w])) w = a;
            if (w < 0) return m;
            m = w;
        }
        throw new IllegalStateException("Takt: Antrieb im Kreis");
    }

    /** Hält den Faden an und wartet auf sein Ende. Ein Fehler aus einem Modul wird hier weitergegeben. */
    public void anhalten() {
        Thread f;
        synchronized (this) {
            laeuft = false;
            f = faden;
            faden = null;
        }
        if (f != null) {
            try {
                f.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (fehler != null) throw new IllegalStateException("Fehler im Takt: " + fehler, fehler);
    }

    /** Läuft der Faden? Nach einem Fehler in einem Modul nicht mehr ({@link #anhalten} gibt ihn weiter). */
    public boolean laeuft() { return laeuft; }

    /**
     * Führt die Uhr um eine Laufzeit (s) weiter: mit dem Zeitraffer {@link #ziel}, höchstens aber mit
     * {@link #reserve} · {@link #erreichbar}, und nie über den Stand der Physik hinaus (dort bleibt sie stehen).
     * Der gehaltene Zeitraffer steht danach in der Uhr. Gibt die Szenenzeit zurück.
     */
    public double fuehre(Uhr uhr, double laufzeit) {
        double soll = ziel, e = erreichbar;
        if (soll > 0 && !Double.isNaN(e) && e * reserve < soll) soll = e * reserve;
        uhr.zeitraffer = soll;
        double jetzt = uhr.sekunden(), s = stand;
        if (soll <= 0 || jetzt + soll * laufzeit <= s) return uhr.vor(laufzeit);
        uhr.setze(Math.max(jetzt, s));
        angehalten.incrementAndGet();
        return uhr.sekunden();
    }
}
