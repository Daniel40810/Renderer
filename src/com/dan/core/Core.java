package com.dan.core;

/**
 * Der Kern des Renderers (Werkbuch HalligHooge Teil A, Abschnitt 5): Raum, Zeit und Himmelsmechanik, Felder und
 * Feldkatalog, Gitter mit Subgrid, Kopplungsbus, Takt, Archiv, Bilanzen und Prüfrahmen. Der Kern importiert nur
 * {@code java.*}; {@code com.dan.core.gpu} (ab P2) setzt Vulkan darauf.
 * <p>
 * Schichtenvertrag (Abschnitt 4): Ein Modul importiert nur Schichten unter sich; Module derselben Schicht kennen
 * einander nicht und sprechen über Felder aus dem Katalog. {@code tools/PaketPruefung} prüft das im Bau.
 *
 * <pre>
 * com.dan.core.raum     Erde (GRS80, Kugel, Krümmung, Coriolis), Utm, Ebene der Szene
 * com.dan.core.zeit     Zeit (UTC, TT, julianisches Datum), Uhr (Szenenzeit, Zeitraffer)
 * com.dan.core.astro    Gestirne (Sonne, Mond, Gezeitenpotential, Sonnenstand), Partialtide (Doodson, Knotenfaktoren)
 * com.dan.core.feld     Feld, Katalog, Eintrag, Ort
 * com.dan.core.gitter   Gitter, Subgrid (V, A, K je Zelle und Kante), Umrechnung zwischen Gittern, Gelaende (Kachelstapel)
 * com.dan.core.bus      Bus, Modul, Anmeldung
 * com.dan.core.takt     Takt (Reihenfolge, eigener Faden, Zeitraffer)
 * com.dan.core.archiv   Archiv (Läufe als Dateien, abspielbar)
 * com.dan.core.bilanz   Bilanz (Rest über dem Grenzwert ist ein Fehler)
 * com.dan.core.pruef    Attest, Harmonik, Statistik, Bildvergleich
 * </pre>
 */
public final class Core {
    /** Version des Kerns für Archiv und Attest. */
    public static final String VERSION = "0.1";
    /** Stand: 1 = P1 (Kern ohne Karte). */
    public static final int STAGE = 1;

    private Core() { }
}
