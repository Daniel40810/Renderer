package com.dan.beacon;

/**
 * Wie viel Licht die Luft am Ort streut: Streukoeffizient in 1/m (Regen, Gischt, Nebel; ohne den gleichmäßigen Dunst,
 * der als eigener Wert {@link Beacon#hazePerM} kommt). Eigene Kopie der Schnittstelle {@code ScatterField} der App,
 * damit das Paket allein steht. Muss schnell sein: Das Gitter fragt einige zehntausend Orte je Aktualisierung.
 */
@FunctionalInterface
public interface Scatter {
    double sigma(double x, double y, double z);

    /** Klare Luft. */
    Scatter NONE = (x, y, z) -> 0;

    /** Überall gleich (z. B. Landregen ohne Vorhänge). */
    static Scatter uniform(double sigma) { return (x, y, z) -> sigma; }
}
