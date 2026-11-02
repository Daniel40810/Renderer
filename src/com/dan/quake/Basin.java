package com.dan.quake;

/**
 * Eine Bucht als Becken für die Seiche (Merian, Werkbuch 9.1): Länge {@link #length} (m), mittlere Tiefe {@link #depth}
 * (m), geschlossen (Vorgabe wie Werkbuch 9.1; der Eingang der Lituya Bay ist 300 m breit und 10 m tief) oder offen
 * ({@link #open}: Eigenform mit Knoten am Eingang). Eigenperioden
 * T_n = 2L / (n √(g h)) geschlossen, 4L / ((2n − 1) √(g h)) offen. Die erste Eigenform wird wie ein gedämpfter
 * Schwinger von der Bodenbeschleunigung längs der Achse angeregt: X'' + 2ζω X' + ω² X = −a(t); X ist die mittlere
 * waagerechte Verschiebung des Wassers, der Ausschlag am Kopf der Bucht η = π h X / L (geschlossen) bzw. π h X / (2 L)
 * (offen) [gerechnet aus der Massenbilanz der Eigenform, Dämpfung ζ [G]].
 */
public final class Basin {
    public double length = 12_000, depth = 130, damping = 0.02;
    /** Achse der Bucht (Grad von Nord im Uhrzeigersinn). */
    public double axisAzimuth = 45;
    public boolean open = false;

    /** Eigenperiode der Form n (s). */
    public double period(int n) {
        double c = Math.sqrt(9.81 * depth);
        return open ? 4 * length / ((2 * n - 1) * c) : 2 * length / (n * c);
    }

    /** Ausschlag am Kopf der Bucht (m) über der Zeit der Zeitreihe. */
    public double[] response(Quake.Record r) {
        double w = 2 * Math.PI / period(1), dt = r.dt, x = 0, v = 0;
        double az = Math.toRadians(axisAzimuth), ex = Math.sin(az), sz = -Math.cos(az);
        double k = Math.PI * depth / (open ? 2 * length : length);
        double[] eta = new double[r.length()];
        for (int i = 0; i < r.length(); i++) {
            double a = r.east[i] * ex + r.south[i] * sz;
            double acc = -a - 2 * damping * w * v - w * w * x;
            v += acc * dt;   // halbimplizit (symplektisch), stabil für dt ≪ T
            x += v * dt;
            eta[i] = k * x;
        }
        return eta;
    }

    /** Größter Ausschlag (m). */
    public double peak(Quake.Record r) {
        double m = 0;
        for (double e : response(r)) m = Math.max(m, Math.abs(e));
        return m;
    }
}
