package com.dan.core.gpu;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Das Speicherkonto eines Moduls auf der Karte (Werkbuch HalligHooge Teil A, Abschnitt 6: „Budget je Modul“). Jeder
 * Puffer und jedes Bild bucht auf ein Konto; eine Zuteilung über die Grenze wird abgelehnt, bevor sie die Karte
 * erreicht. So bleibt die Summe unter dem Ziel von 1,5 GB (Abschnitt 11), und es ist zu sehen, wer was belegt.
 */
public final class Konto {
    public final String modul;
    /** Grenze in Byte. */
    public final long grenze;
    private final AtomicLong belegt = new AtomicLong(), groesste = new AtomicLong();

    Konto(String modul, long grenze) {
        this.modul = modul;
        this.grenze = grenze;
    }

    void buche(long bytes, String wofuer) {
        long neu = belegt.addAndGet(bytes);
        if (neu > grenze) {
            belegt.addAndGet(-bytes);
            throw new IllegalStateException("Speicherkonto " + modul + ": " + wofuer + " (" + Geraet.mb(bytes) + ") geht über die Grenze von " + Geraet.mb(grenze) + " (belegt "
                    + Geraet.mb(neu - bytes) + ")");
        }
        groesste.accumulateAndGet(neu, Math::max);
    }

    void gib(long bytes) { belegt.addAndGet(-bytes); }

    /** Belegte Byte. */
    public long belegt() { return belegt.get(); }

    /** Größte Belegung seit dem Anlegen. */
    public long spitze() { return groesste.get(); }

    @Override public String toString() { return modul + " " + Geraet.mb(belegt()) + " von " + Geraet.mb(grenze); }
}
