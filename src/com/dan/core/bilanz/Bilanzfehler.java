package com.dan.core.bilanz;

/** Eine Bilanz geht nicht auf: ein Fehler, keine Warnung (Werkbuch HalligHooge Teil A, 5.8). */
public final class Bilanzfehler extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public Bilanzfehler(String meldung) { super(meldung); }
}
