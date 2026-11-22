package com.dan.core.gpu;

import com.dan.core.feld.Feld;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Die Seite eines Felds auf der Karte (Werkbuch HalligHooge Teil A, 5.3 und Abschnitt 6): ein Speicherpuffer mit einem
 * float je Wert, in derselben Reihenfolge wie im {@link Feld}. Auf der Karte rechnet der Betrieb in float; ein Feld
 * der Referenz (double) wird beim Hochladen gekürzt und beim Herunterladen aufgeweitet.
 * <p>
 * Der Abgleich ist ausdrücklich: {@link Feld#hoch()} und {@link Feld#herunter()}. Hat ein Kernel das Feld geschrieben,
 * meldet das Modul es mit {@link #geschrieben}; {@code herunter} wartet dann, bis die Karte so weit ist.
 */
public final class KartenFeld implements Feld.Kartenseite, AutoCloseable {
    public final Puffer puffer;
    private volatile Zeitleiste leiste;
    private volatile long wert;

    KartenFeld(Geraet g, Konto konto, Feld feld, Puffer.Art art) {
        String n = feld.eintrag.name() + (feld.gitter == null ? "" : " " + feld.gitter.name);
        puffer = art == Puffer.Art.GETEILT ? Puffer.geteilt(g, konto, n, 4L * feld.laenge) : Puffer.speicher(g, konto, n, 4L * feld.laenge);
    }

    /**
     * Ein Kernel schreibt dieses Feld; fertig ist es, wenn die Zeitleiste den Wert erreicht hat. Danach hat die Karte
     * den jüngeren Stand.
     */
    public void geschrieben(Feld feld, Zeitleiste leiste, long wert) {
        this.leiste = leiste;
        this.wert = wert;
        feld.karteNeuer = true;
        feld.prozessorNeuer = false;
    }

    /** Ist die Arbeit fertig, die das Feld zuletzt geschrieben hat? */
    public boolean fertig() {
        Zeitleiste z = leiste;
        return z == null || z.stand() >= wert;
    }

    /** Wartet, bis die Arbeit fertig ist, die das Feld zuletzt geschrieben hat. */
    public void warte() {
        Zeitleiste z = leiste;
        if (z != null) z.warte(wert, Long.MAX_VALUE);
    }

    @Override public void hoch(Feld feld) {
        warte();
        ByteBuffer b = memAlloc(4 * feld.laenge);
        try {
            FloatBuffer f = b.asFloatBuffer();
            if (feld.f != null) f.put(feld.f);
            else for (int i = 0; i < feld.laenge; i++) f.put(i, (float) feld.d[i]);
            puffer.schreibe(b, 0);
        } finally {
            memFree(b);
        }
    }

    @Override public void herunter(Feld feld) {
        warte();
        ByteBuffer b = memAlloc(4 * feld.laenge);
        try {
            puffer.lies(b, 0);
            FloatBuffer f = b.asFloatBuffer();
            if (feld.f != null) f.get(feld.f);
            else for (int i = 0; i < feld.laenge; i++) feld.d[i] = f.get(i);
        } finally {
            memFree(b);
        }
    }

    @Override public void close() { puffer.close(); }
}
