package com.dan.core.feld;

import com.dan.core.gitter.Gitter;

import java.util.Arrays;

/**
 * Ein Feld (Werkbuch HalligHooge Teil A, 5.3): Katalogeintrag, Gitter, Zahlentyp und die Werte. Die Referenz rechnet
 * in double, der Betrieb in float; ein Feld trägt genau eines von beiden ({@link #d} oder {@link #f}).
 * <p>
 * <b>Ablage:</b> ein Wert je Ort, Komponente und Schicht in der Reihenfolge Ort, dann Schicht, dann Komponente:
 * {@code index = (ort · schichten + schicht) · komponenten + komponente}. Zellen zählen von Südwest zeilenweise nach
 * Nordost ({@code ort = i + nx · j}); Kanten, Ecken und Rand wie in {@link Ort}.
 * <p>
 * <b>Prozessor und Karte:</b> Die Werte liegen auf dem Prozessor, auf der Karte oder an beiden Stellen. Der Abgleich
 * ist ausdrücklich: {@link #hoch()} und {@link #herunter()} rufen die {@link Kartenseite}, die {@code core.gpu} stellt
 * (ab P2). Der Kern selbst kennt keine Karte.
 */
public final class Feld {
    /** Zahlentyp der Ablage. */
    public enum Zahl { DOUBLE, FLOAT }

    /** Die Seite eines Felds auf der Grafikkarte; {@code core.gpu} stellt sie. */
    public interface Kartenseite {
        /** Überträgt die Werte des Prozessors auf die Karte. */
        void hoch(Feld feld);
        /** Holt die Werte der Karte auf den Prozessor. */
        void herunter(Feld feld);
    }

    public final Eintrag eintrag;
    /** Das Gitter; null bei einem Feld ohne Gitter ({@link Ort#GLOBAL}). */
    public final Gitter gitter;
    public final Zahl zahl;
    public final int komponenten, schichten;
    /** Zahl der Orte und der Werte. */
    public final int orte, laenge;
    /** Die Werte: genau eines der beiden ist nicht null. */
    public final double[] d;
    public final float[] f;
    /** Modellzeit des Stands (s seit Start der Szene); NaN, solange keiner abgelegt ist. */
    public volatile double zeit = Double.NaN;
    /** Die Seite auf der Karte oder null. */
    public Kartenseite karte;
    /** Welche Seite den jüngeren Stand hat. */
    public volatile boolean prozessorNeuer = true, karteNeuer;

    public Feld(Eintrag eintrag, Gitter gitter, Zahl zahl) { this(eintrag, gitter, zahl, eintrag.schichten()); }

    /** Feld mit eigener Zahl von Schichten (für Einträge, die sie dem Modul überlassen, etwa {@code waves.spec}). */
    public Feld(Eintrag eintrag, Gitter gitter, Zahl zahl, int schichten) {
        if (eintrag.ort() == Ort.TABELLE) throw new IllegalArgumentException(eintrag.name() + " ist eine Tabelle (Subgrid), kein Feld");
        if (gitter == null && eintrag.ort() != Ort.GLOBAL) throw new IllegalArgumentException(eintrag.name() + " braucht ein Gitter");
        if (schichten < 1) throw new IllegalArgumentException(eintrag.name() + ": Zahl der Schichten fehlt");
        if (eintrag.schichten() > 0 && schichten != eintrag.schichten())
            throw new IllegalArgumentException(eintrag.name() + ": der Katalog legt " + eintrag.schichten() + " Schichten fest");
        this.eintrag = eintrag;
        this.gitter = gitter;
        this.zahl = zahl;
        this.komponenten = eintrag.komponenten();
        this.schichten = schichten;
        orte = eintrag.ort().anzahl(gitter == null ? 1 : gitter.nx, gitter == null ? 1 : gitter.ny);
        long n = (long) orte * schichten * komponenten;
        if (n > Integer.MAX_VALUE - 8) throw new IllegalArgumentException(eintrag.name() + ": " + n + " Werte passen nicht in ein Stück; in Stücken führen");
        laenge = (int) n;
        d = zahl == Zahl.DOUBLE ? new double[laenge] : null;
        f = zahl == Zahl.FLOAT ? new float[laenge] : null;
    }

    /** Ein leeres Feld gleicher Art. */
    public Feld gleiches() { return new Feld(eintrag, gitter, zahl, schichten); }

    public double hol(int index) { return d != null ? d[index] : f[index]; }

    public void setz(int index, double wert) {
        if (d != null) d[index] = wert; else f[index] = (float) wert;
    }

    /** Index des Werts in der Zelle (i, j), Komponente 0, Schicht 0. */
    public int zelle(int i, int j) { return (i + gitter.nx * j) * schichten * komponenten; }

    /** Index der Kante quer zu x zwischen den Zellen (i − 1, j) und (i, j); i = 0..nx. */
    public int kanteX(int i, int j) { return (i + (gitter.nx + 1) * j) * schichten * komponenten; }

    /** Index der Kante quer zu y zwischen den Zellen (i, j − 1) und (i, j); j = 0..ny. */
    public int kanteY(int i, int j) { return ((gitter.nx + 1) * gitter.ny + i + gitter.nx * j) * schichten * komponenten; }

    public void fuellen(double wert) {
        if (d != null) Arrays.fill(d, wert); else Arrays.fill(f, (float) wert);
    }

    /** Übernimmt Werte und Zeit eines Felds gleicher Länge (double nach float wird gekürzt). */
    public void kopiere(Feld von) {
        if (von.laenge != laenge) throw new IllegalArgumentException(eintrag.name() + ": Länge " + von.laenge + " statt " + laenge);
        if (d != null && von.d != null) System.arraycopy(von.d, 0, d, 0, laenge);
        else if (f != null && von.f != null) System.arraycopy(von.f, 0, f, 0, laenge);
        else for (int i = 0; i < laenge; i++) setz(i, von.hol(i));
        zeit = von.zeit;
        prozessorNeuer = true;
    }

    /** Summe aller Werte (Neumaier), etwa für Bilanzen. */
    public double summe() {
        double s = 0, c = 0;
        for (int i = 0; i < laenge; i++) {
            double v = hol(i), t = s + v;
            c += Math.abs(s) >= Math.abs(v) ? (s - t) + v : (v - t) + s;
            s = t;
        }
        return s + c;
    }

    /** Überträgt den Stand des Prozessors auf die Karte (nichts, wenn keine Karte da ist). */
    public void hoch() {
        if (karte == null) return;
        karte.hoch(this);
        prozessorNeuer = false;
        karteNeuer = false;
    }

    /** Holt den Stand der Karte auf den Prozessor (nichts, wenn keine Karte da ist). */
    public void herunter() {
        if (karte == null) return;
        karte.herunter(this);
        prozessorNeuer = false;
        karteNeuer = false;
    }

    @Override public String toString() {
        return eintrag.name() + " [" + eintrag.einheit() + "] " + (gitter == null ? "global" : gitter.name) + " " + laenge + " × " + zahl;
    }
}
