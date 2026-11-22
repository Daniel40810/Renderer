package com.dan.physics.hydro;

import com.dan.core.gitter.Gebiet;

/**
 * Der Rand eines Gitters aus der Vorgabe der Tide ({@link Gebiet.Vorgabe}, Werkbuch HalligHooge Teil A, 12.5, Plan P4d),
 * bis P5 den Rand als {@code tide.bc} rechnet. Je Randkante
 * <pre>
 * η(t) = η₀ + r(t) · (Mittel − η₀ + A cos(ω (t + versatz) − φ)),   r(t) = ½ (1 − cos(π t / Anlauf)) bis zum Anlauf, dann 1
 * </pre>
 * mit η₀, dem Mittel des Mittelwassers über die Kanten mit Wert. Am Anfang steht damit überall ein ruhender See bei η₀,
 * und der Rand fährt ohne Knick an. Eine Seite ganz ohne Werte wird Wand.
 */
public final class Vorgaberand {
    private Vorgaberand() { }

    /** Anteil der Tide zur Zeit t beim Anlauf über {@code anlauf} Sekunden. */
    public static double anteil(double t, double anlauf) { return anlauf <= 0 || t >= anlauf ? 1 : t <= 0 ? 0 : 0.5 * (1 - Math.cos(Math.PI * t / anlauf)); }

    /** η₀: das Mittel des Mittelwassers über die Randkanten mit Wert. */
    public static double eta0(Gebiet.Vorgabe v) {
        double s = 0;
        int n = 0;
        for (int k = 0; k < v.kanten(); k++)
            if (!Double.isNaN(v.mittel()[k]) && !Double.isNaN(v.amplitude()[k])) {
                s += v.mittel()[k];
                n++;
            }
        return n > 0 ? s / n : 0;
    }

    /**
     * Setzt die Ränder des Gitters nach der Vorgabe (die Gitter müssen gleich sein). Gibt η₀ zurück; die Zellen füllt
     * der Aufrufer ({@link Hydro#fuellen}).
     */
    public static double setze(Hydro h, Gebiet.Vorgabe v, double anlauf, double versatz) { return setze(h, v, anlauf, versatz, 0); }

    /**
     * Wie {@link #setze(Hydro, Gebiet.Vorgabe, double, double)}, alle Werte um {@code anheben} (m) höher (für Prüfungen in
     * überflutetem Gebiet). Gibt η₀ + anheben zurück.
     */
    public static double setze(Hydro h, Gebiet.Vorgabe v, double anlauf, double versatz, double anheben) {
        if (!h.gitter.name.equals(v.gitter())) throw new IllegalArgumentException("Vorgabe für " + v.gitter() + ", nicht für " + h.gitter.name);
        if (v.kanten() != 2 * h.nx + 2 * h.ny) throw new IllegalArgumentException("Vorgabe mit " + v.kanten() + " Randkanten für " + h.gitter);
        double e0 = eta0(v) + anheben;
        int[] anfang = {0, h.ny, 2 * h.ny, 2 * h.ny + h.nx}, laenge = {h.ny, h.ny, h.nx, h.nx};
        for (int s = 0; s < 4; s++) {
            boolean da = false;
            for (int k = 0; k < laenge[s]; k++) da |= !Double.isNaN(v.amplitude()[anfang[s] + k]);
            if (!da) {
                h.rand[s] = Hydro.Rand.WAND;
                h.randwert[s] = null;
                continue;
            }
            final int a0 = anfang[s], n = laenge[s];
            final boolean quer = s == Hydro.WEST || s == Hydro.OST;
            h.rand[s] = Hydro.Rand.WASSERSTAND;
            h.randwert[s] = (t, x, y) -> {
                int k = quer ? (int) Math.floor((y - h.gitter.y0) / h.gitter.dy) : (int) Math.floor((x - h.gitter.x0) / h.gitter.dx);
                k = a0 + Math.max(0, Math.min(n - 1, k));
                double w = v.eta(k, t + versatz) + anheben;
                return Double.isNaN(w) ? e0 : e0 + anteil(t, anlauf) * (w - e0);
            };
        }
        return e0;
    }
}
