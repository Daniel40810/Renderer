package com.dan.core.zeit;

import java.time.Instant;

/**
 * Die Hauptuhr einer Szene (Werkbuch HalligHooge Teil A, 5.2): Szenenzeit = Startzeit + Zeitraffer · Laufzeit.
 * Die Modellzeit {@code t} aller Module, des Busses und des Archivs sind die Sekunden seit dem Start
 * ({@link #sekunden()}); {@link #jetzt()} gibt sie als UTC.
 * <p>
 * Der Zeitraffer darf sich im Lauf ändern (auch null oder negativ: Standbild, rückwärts aus dem Archiv); die
 * Szenenzeit springt dabei nicht, weil sie aufsummiert wird. Fäden dürfen lesen, während einer die Uhr führt.
 */
public final class Uhr {
    public final Instant start;
    /** Szenensekunden je Laufzeitsekunde. */
    public volatile double zeitraffer = 1;
    private double sekunden;

    public Uhr(Instant start) { this.start = start; }

    /** Führt die Uhr um eine Laufzeit (s) weiter; gibt die neue Szenenzeit zurück. */
    public synchronized double vor(double laufzeit) {
        sekunden += zeitraffer * laufzeit;
        return sekunden;
    }

    /** Setzt die Szenenzeit (s seit Start). */
    public synchronized void setze(double szenensekunden) { sekunden = szenensekunden; }

    /** Szenenzeit in Sekunden seit dem Start – die Modellzeit t. */
    public synchronized double sekunden() { return sekunden; }

    /** Szenenzeit als UTC. */
    public Instant jetzt() { return bei(sekunden()); }

    /** UTC zu einer Modellzeit t (s seit Start). */
    public Instant bei(double t) { return Zeit.ausSekunden(start.getEpochSecond() + start.getNano() * 1e-9 + t); }

    /** Modellzeit t zu einem UTC-Zeitpunkt. */
    public double t(Instant zeit) { return zeit.getEpochSecond() - start.getEpochSecond() + (zeit.getNano() - start.getNano()) * 1e-9; }
}
