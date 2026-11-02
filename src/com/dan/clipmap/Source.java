package com.dan.clipmap;

/**
 * Woher die Clipmap ihre Höhen nimmt. Raum: x nach Osten, z nach Süden, Höhen in m; welcher Bezug (NAVD88, NHN …) gilt,
 * weiß nur die App. Unbekannt ist {@link Float#NaN}.
 * <p>
 * {@link #fill} wird aus dem Ladefaden der Clipmap gerufen, nie aus dem Bildlauf; eine Quelle darf dort Dateien lesen.
 */
public interface Source {
    /** Die Höhe am Ort (x, z) für eine Abtastung mit Zellweite {@code cell} (m), oder NaN. */
    float height(double x, double z, double cell);

    /**
     * Füllt {@code out[j * w + i]} mit der Höhe bei (x0 + i·cell, z0 + j·cell). Die Vorgabe ruft {@link #height} je Punkt;
     * Quellen mit Kacheln können schneller sein.
     */
    default void fill(double x0, double z0, double cell, int w, int h, float[] out) {
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) out[j * w + i] = height(x0 + i * cell, z0 + j * cell, cell);
    }

    /** Die feinste Zellweite, die die Quelle hat (m). */
    double finestCell();

    /** Eine Zeile für Statusanzeigen. */
    default String describe() { return getClass().getSimpleName() + ", Zelle " + finestCell() + " m"; }
}
