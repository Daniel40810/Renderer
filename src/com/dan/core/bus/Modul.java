package com.dan.core.bus;

/**
 * Ein Modul am Kopplungsbus (Werkbuch HalligHooge Teil A, 4 und 5.5): Physik, Antrieb oder Bild. Es kennt den Kern
 * und kein anderes Modul; was es von anderen braucht, liest es als Feld aus dem Katalog.
 */
public interface Modul {
    /** Name wie im Katalog, etwa {@code hydro}, {@code tide}, {@code antrieb}, {@code render.water}. */
    String name();

    /** Version für das Archiv und das Attest. */
    default String version() { return "0"; }

    /** Meldet an, was das Modul liest, was es schreibt und wie oft es koppelt. */
    void anmelden(Anmeldung a);

    /** Legt den Anfangsstand jedes geschriebenen Felds zur Zeit t0 ab ({@link Bus#lege}). */
    void beginn(double t0, Bus bus);

    /**
     * Rechnet ein Kopplungsintervall von t0 bis t1 (Modellzeit in s) mit eigenen Schritten und legt am Ende den Stand
     * zur Zeit t1 ab. Andere Felder liest es zur Zeit t0: Der {@code takt.Takt} sorgt dafür, dass sie dort vorliegen.
     */
    void schritt(double t0, double t1, Bus bus);
}
