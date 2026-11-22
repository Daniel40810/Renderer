package com.dan.core.gpu;

/**
 * Eine Auflage über dem fertigen Bild (seit P11c2, Werkbuch HalligHooge Teil A, 12.12): Linien, Spuren, Texturen, die
 * ihre Farbe behalten sollen, wie hell die Szene auch belichtet ist. render.frame ruft sie nach der Ausgabe durch die
 * Filmkurve: erst alle {@link #vorbereiten} (vor den Teilen des Bilds), dann alle {@link #auflegen} in das Ziel der
 * Ausgabe. Die Tiefe der Szene ({@code z.tiefe}) darf eine Auflage testen oder lesen, aber nicht schreiben.
 */
public interface Auflage extends AutoCloseable {
    String name();

    /** Vor dem Bild: Felder hochladen, Kernels aufnehmen (etwa Partikel bewegen). */
    default void vorbereiten(Ablauf a, Bildziel z) { }

    /** Zeichnet in {@code aus} (Format beliebig, Größe wie das Bildziel), nach der Filmkurve. */
    void auflegen(Ablauf a, Bildziel z, Bild aus);

    @Override default void close() { }
}
