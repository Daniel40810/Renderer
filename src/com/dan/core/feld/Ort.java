package com.dan.core.feld;

/**
 * Ort der Werte eines Felds im Gitter (Werkbuch HalligHooge Teil A, 5.3). Das Gitter ist versetzt: Wasserstand in
 * der Zelle, Durchfluss auf der Kante.
 */
public enum Ort {
    /** Mitte der Zelle: nx · ny Werte. */
    ZELLE,
    /**
     * Kanten: zuerst die (nx + 1) · ny Kanten quer zu x (zwischen Zelle i − 1 und i), dann die nx · (ny + 1) Kanten
     * quer zu y (zwischen Zelle j − 1 und j).
     */
    KANTE,
    /** Ecken: (nx + 1) · (ny + 1) Werte. */
    ECKE,
    /** Säule über der Zelle: nx · ny · Schichten. */
    SAEULE,
    /** Rand des Gitters: 2 · (nx + ny) Zellen, im Süden beginnend gegen den Uhrzeigersinn (Süd, Ost, Nord, West). */
    RAND,
    /** Ein Wert für die ganze Szene. */
    GLOBAL,
    /** Tabellen je Zelle und Kante (Subgrid); liegen nicht in einem {@link Feld}, sondern in {@code gitter.Subgrid}. */
    TABELLE;

    /** Zahl der Orte in einem Gitter mit nx · ny Zellen. */
    public int anzahl(int nx, int ny) {
        return switch (this) {
            case ZELLE, SAEULE, TABELLE -> nx * ny;
            case KANTE -> (nx + 1) * ny + nx * (ny + 1);
            case ECKE -> (nx + 1) * (ny + 1);
            case RAND -> 2 * (nx + ny);
            case GLOBAL -> 1;
        };
    }
}
