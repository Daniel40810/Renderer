package com.dan.bear.demo;

import com.dan.bear.Sleuth;

/**
 * Eine gerechnete Bucht für Demo und Prüfung: Meer im Süden (z &gt; 60 m), ein flacher Strand bis z = 0, dahinter der
 * Hang mit 14 % Steigung nach Norden und ein Hügel; von Norden ein Bach (x = 40 m), 6 m breit, 0,35 m tief, mit
 * Fischplätzen. Der Wasserspiegel des Meers ist {@link #tide} (m).
 */
public final class DemoBay implements Sleuth.World {
    public double tide = 0;

    public double ground(double x, double z) {
        double g;
        if (z > 60) g = -0.04 * (z - 60) - 0.3;
        else if (z > 0) g = -0.3 + (60 - z) * 0.025;
        else g = 1.2 + (-z) * 0.14;
        g += 18 * Math.exp(-(Math.pow((x + 160) / 70, 2) + Math.pow((z + 180) / 70, 2)));
        // Bach: Rinne
        double c = Math.abs(x - 40 - 6 * Math.sin(z / 40));
        if (z < 70 && c < 4) g -= 0.45 * (1 - c / 4);
        return g;
    }

    public double water(double x, double z) {
        double g = ground(x, z);
        double c = Math.abs(x - 40 - 6 * Math.sin(z / 40));
        if (z < 62 && c < 3.2) return Math.max(g + 0.30 * (1 - c / 3.2), tide);
        return g < tide ? tide : Double.NaN;
    }

    /** Der Bach mit Plätzen alle 25 m. */
    public static Sleuth.Stream creek() {
        double[][] s = new double[8][];
        for (int i = 0; i < s.length; i++) { double z = -160 + i * 25; s[i] = new double[]{40 + 6 * Math.sin(z / 40), z}; }
        return new Sleuth.Stream("Bach", s);
    }

    public static Sleuth.Beach beach() { return new Sleuth.Beach(-40, 35, 30); }

    public static Sleuth.Range range() { return new Sleuth.Range(0, -60, 220); }
}
