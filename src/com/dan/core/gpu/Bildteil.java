package com.dan.core.gpu;

/**
 * Ein Teil des Bilds (seit P6a): Gelände, Wasser, Himmel. render.frame führt die Teile je Bild der Reihe nach aus,
 * erst alle {@link #vorbereiten}, dann alle {@link #zeichnen}. Ein Teil nimmt seine Durchgänge in den {@link Ablauf}
 * der Grafik-Warteschlange auf; die Sperren rechnet der Ablauf.
 */
public interface Bildteil extends AutoCloseable {
    String name();

    /** Vor dem Zeichnen: Tabellen rechnen, Felder hochladen, geteilte Bilder ablegen. */
    default void vorbereiten(Ablauf a, Bildziel z) { }

    /** Zeichnet in {@code z.hdr} und {@code z.tiefe}. */
    void zeichnen(Ablauf a, Bildziel z);

    @Override default void close() { }
}
