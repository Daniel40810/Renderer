package com.dan.horizon;

/**
 * Sonne am Punkt: wie viel vom Sonnenlicht außerhalb der Luft an einem Punkt der Höhe h ankommt, bei der Sonnenhöhe el
 * (LituyaBay1958, Werkbuch Welle W1). Zwei Schichten mit eigener Skalenhöhe: Luft (Rayleigh, 8 km, optische Dicke im
 * Zenit 0,046 / 0,108 / 0,265 für Rot, Grün, Blau) und Dunst (Aerosol, 1,2 km, 0,10, grau) [G].
 * <p>
 * <b>Luftmasse über der Waagerechten</b> nach Kasten und Young (1989): m = e^(−h/H) / (sin el + 0,50572 (el° + 6,07995)^−1,6364).
 * <br><b>Unter der Waagerechten</b> streift der Strahl die Erde (Chapman): die Masse hängt an der Tangentenhöhe
 * h<sub>t</sub> = (R + h) cos el − R; voll durch die Luft wäre √(2πR/H) e^(−h<sub>t</sub>/H), davon zählt die Hälfte vor dem
 * Berührpunkt nicht mehr. Nach unten ist die Masse durch die halbe Masse am Ort begrenzt.
 * <p>
 * So glüht ein Gipfel auf 4 000 m rot, wenn die Sonne für das Tal schon 1° unter der Kimm steht: das Alpenglühen, ohne
 * es zu malen. Die Rechnung ist gleich der in {@code gl/horizon.glsl} (horizonAirmass, horizonTransmittance).
 */
public final class Airmass {
    /** Erdradius (m). */
    public static final double R = 6371000.0;
    /** Optische Dicke der Luft im Zenit für Rot, Grün, Blau (Rayleigh) und Skalenhöhe (m). */
    public double[] tauRayleigh = {0.046, 0.108, 0.265};
    public double scaleRayleigh = 8000;
    /** Optische Dicke des Dunsts im Zenit (grau) und seine Skalenhöhe (m) [G]. */
    public double tauAerosol = 0.10;
    public double scaleAerosol = 1200;

    /** Luftmasse (relativ zur senkrechten Säule vom Meer aus) einer Schicht mit Skalenhöhe H an der Höhe h. */
    public static double relative(double h, double sinEl, double H) {
        double el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
        if (el >= 0) return Math.exp(-h / H) / (Math.sin(el) + 0.50572 * Math.pow(Math.toDegrees(el) + 6.07995, -1.6364));
        double ht = (R + h) * Math.cos(el) - R, voll = Math.sqrt(2 * Math.PI * R / H);
        return Math.max(voll * Math.exp(-Math.max(ht, -200) / H) - 0.5 * voll * Math.exp(-h / H), 0.5 * voll * Math.exp(-h / H));
    }

    /** Durchlass (Rot, Grün, Blau, je 0..1) für die Sonne an der Höhe h (m) bei der Sonnenhöhe mit Sinus sinEl. */
    public double[] transmittance(double h, double sinEl) {
        double mr = relative(h, sinEl, scaleRayleigh), ma = relative(h, sinEl, scaleAerosol);
        double[] t = new double[3];
        for (int c = 0; c < 3; c++) t[c] = Math.exp(-(tauRayleigh[c] * mr + tauAerosol * ma));
        return t;
    }

    /** Wie {@link #transmittance} mit den Werten des Shaders (Vorgabe). */
    public static double[] standard(double h, double sinEl) { return new Airmass().transmittance(h, sinEl); }

    /** Tangentenhöhe (m) eines Strahls von der Höhe h unter der Sonnenhöhe el (negativ: unter der Waagerechten). */
    public static double tangentHeight(double h, double sinEl) {
        double el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
        return el >= 0 ? h : (R + h) * Math.cos(el) - R;
    }
}
