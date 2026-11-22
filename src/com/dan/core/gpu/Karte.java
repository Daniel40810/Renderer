package com.dan.core.gpu;

import com.dan.core.feld.Feld;

import java.util.ArrayList;
import java.util.List;

/**
 * Felder auf die Karte bringen (Werkbuch HalligHooge Teil A, Abschnitt 6, „Felder“): {@link #an} gibt einem
 * {@link Feld} seine Seite auf der Karte, gebucht auf das Konto des Moduls. Danach gehen {@link Feld#hoch()} und
 * {@link Feld#herunter()}, und Kernels rechnen auf {@link KartenFeld#puffer}.
 * <p>
 * Die Felder am Bus wechseln mit jedem Stand ({@code Bus.schreibfeld} gibt nach {@code lege} ein anderes); ein Modul
 * ruft {@link #an} darum für jedes Schreibfeld – beim zweiten Mal kommt die vorhandene Seite zurück.
 */
public final class Karte implements AutoCloseable {
    private final Geraet g;
    private final Konto konto;
    private final List<KartenFeld> felder = new ArrayList<>();

    public Karte(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
    }

    /** Die Seite des Felds im Speicher der Karte; angelegt beim ersten Ruf. */
    public KartenFeld an(Feld feld) { return an(feld, Puffer.Art.KARTE); }

    /** Die Seite des Felds auf der Karte in der gewünschten Art; angelegt beim ersten Ruf. */
    public synchronized KartenFeld an(Feld feld, Puffer.Art art) {
        if (feld.karte instanceof KartenFeld k) return k;
        if (feld.karte != null) throw new IllegalStateException(feld + " hat schon eine andere Kartenseite");
        KartenFeld k = new KartenFeld(g, konto, feld, art);
        feld.karte = k;
        felder.add(k);
        return k;
    }

    /** Zahl der Felder auf der Karte. */
    public synchronized int felder() { return felder.size(); }

    /** Gibt alle Felder frei, die diese Karte angelegt hat. */
    @Override public synchronized void close() {
        for (KartenFeld k : felder) k.close();
        felder.clear();
    }
}
