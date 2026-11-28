package com.dan.physics.auftrieb;

/**
 * Das Wasser, in dem ein Körper schwimmt (Seegang Werkbuch, S7a; Datei 86, 3.2): Pegel, Welle in der Tiefe, Strömung,
 * Wind und Grund. Das Package kennt kein Archiv und keine Karte; die Anwendung reicht ihr Wasser herein (in S7b
 * `lauf.Flotte` aus der Karte, in den Prüfungen {@link Welle}).
 * <p>
 * <b>Achsen der Welt:</b> x nach Ost, y nach Nord, z nach oben (m). Zeiten in s.
 * <p>
 * <b>Druckhöhe:</b> Die Welle gibt in der Tiefe z unter dem Pegel ψ(z) = p<sub>dyn</sub> / (ρ g), in linearer Theorie
 * Σ a cosh(k (z + h)) / cosh(k h) cos φ. An der Oberfläche (z = 0) ist ψ die Auslenkung η. Eine Probe in der Tiefe z
 * „sieht“ den Spiegel pegel + ψ(z): Darüber trägt sie nicht, darunter ganz (Froude–Krylov, SF105 A).
 */
public interface Wasser {
    double G = 9.81;

    /** Ruhewasserspiegel am Ort (m über dem Bezug, etwa NHN). */
    double pegel(double x, double y, double t);

    /**
     * Die Welle am Ort (x, y) in der Tiefe z unter dem Pegel (z ≤ 0; darüber gilt z = 0) zur Zeit t. Schreibt in out:
     * {ψ, ∂ψ/∂x, ∂ψ/∂y, u, v, w}: Druckhöhe (m), ihr Gefälle, Geschwindigkeit des Wassers aus der Welle (m/s).
     */
    void welle(double x, double y, double z, double t, double[] out);

    /**
     * Seit S7b-1 (Seegang Werkbuch 88): wie {@link #welle(double, double, double, double, double[])} für eine Probe der Säule
     * saeule ihres Körpers ({@link Proben#saeule}); Wasser von der Karte kennt je Säule eine Stelle. Vorgabe: ohne Säule.
     */
    default void welle(int saeule, double x, double y, double z, double t, double[] out) { welle(x, y, z, t, out); }

    /** Seit S7b-1: wie {@link #grund(double, double)} für eine Probe der Säule saeule. Vorgabe: ohne Säule. */
    default double grund(int saeule, double x, double y) { return grund(x, y); }

    /** Strömung über die Tiefe gemittelt {u nach Ost, v nach Nord} (m/s). */
    default void strom(double x, double y, double t, double[] out) { out[0] = 0; out[1] = 0; }

    /** Wind in 10 m Höhe {u nach Ost, v nach Nord} (m/s). */
    default void wind(double x, double y, double t, double[] out) { out[0] = 0; out[1] = 0; }

    /** Höhe des Grunds am Ort (m über dem Bezug); ohne Grund −∞. */
    default double grund(double x, double y) { return Double.NEGATIVE_INFINITY; }

    /** Dichte des Wassers (kg/m³). */
    default double dichte() { return 1025; }

    /** Dichte der Luft (kg/m³). */
    default double luftdichte() { return 1.25; }

    /** Ruhiges Wasser auf dem Pegel level, ohne Grund. */
    static Wasser ruhig(double level) { return ruhig(level, Double.NEGATIVE_INFINITY); }

    /** Ruhiges Wasser auf dem Pegel level über einem ebenen Grund in der Höhe grund. */
    static Wasser ruhig(double level, double grund) {
        return new Wasser() {
            @Override public double pegel(double x, double y, double t) { return level; }

            @Override public void welle(double x, double y, double z, double t, double[] out) { for (int i = 0; i < 6; i++) out[i] = 0; }

            @Override public double grund(double x, double y) { return grund; }
        };
    }
}
