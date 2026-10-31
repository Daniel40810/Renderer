package com.dan.soundscape;

/**
 * Seit Brocken Phase 15: eine Kante oder ein Spalt, an dem der Sturm heult: Lamellen, Fugen, Gitter, Hausecken. Hinter
 * einer Kante der Breite w löst sich die Strömung im Takt f = 0,2 · U / w ab wie hinter einem Stab, aber breiter
 * (Güte 4 statt 22) und unruhiger; liegt f unter 90 Hz (breite Kanten, Hausecken), heult nur das Band um
 * 150 + 12 · U Hz (Güte 1,3). Pegel in 1 m: 56 + 60 · lg(U / 10) + 10 · lg(Länge / 1 m) dB [G], erst ab 5 m/s. Ort in
 * m, Breite und Länge der Kante in m. Teil „Äolstöne“; die nächsten acht in 300 m klingen.
 */
public record Edge(String name, double x, double y, double z, double width, double length) {
    /** Mitte des Heulens (Hz) bei der Anströmung u (m/s). */
    public double frequency(double u) {
        double f = Soundscape.STROUHAL * u / Math.max(1e-3, width);
        return f < 90 ? 150 + 12 * u : f;
    }

    /** Güte des Bands. */
    public double q(double u) { return Soundscape.STROUHAL * u / Math.max(1e-3, width) < 90 ? 1.3 : 4; }

    /** Pegel in 1 m (dB) bei u (m/s). */
    public double levelDb(double u) { return 56 + 60 * Math.log10(Math.max(0.1, u) / 10) + 10 * Math.log10(Math.max(0.05, length)); }
}
