package com.dan.horizon;

/**
 * Luftperspektive in zwei Schichten (LituyaBay1958, Werkbuch Welle W1, L3): Luft mit 8 km Skalenhöhe und rund 150 km
 * Sicht, Dunst mit 1,2 km Skalenhöhe und der Sicht des Tages. Gezählt wird die Strecke in der mittleren Höhe des Wegs
 * (Auge plus halbe Höhenänderung); so stehen die Gipfel klar über dem Dunst eines Tals oder einer Bucht, und der Blick
 * von oben verschwindet nicht im Dunst.
 * <p>
 * Der Dunst ist mit {@link #hazeWeight} 0,45 gewichtet: der Himmel aus {@code com.dan.sky} rechnet die Auslöschung am
 * Boden (Licht und Streuung); sichtbar wird davon rund die Hälfte als Schleier (erste Probe in Lituya: der Mittelgrund war
 * grau verwaschen) [G].
 * <p>
 * Dazu für Dinge, die ihren Dunst als eine Entfernung nehmen (Wald aus {@code com.dan.forest}, Fälle, Boote):
 * {@link #fogDistanceAt} gibt die gleichwertige Entfernung in einer Höhe (Werkbuch Welle W5, K16: vorher nahm der Wald die
 * volle Bodenextinktion ohne Höhe, dreimal so viel Dunst wie das Gelände, und die Kronen standen orange über dem Hang).
 */
public final class Aerial {
    /** Skalenhöhe (m) und Sicht (m, bis e⁻¹) der klaren Luft. */
    public double airScale = 8000, airLength = 150_000;
    /** Skalenhöhe des Dunsts (m) und sein Gewicht [G]. */
    public double hazeScale = 1200, hazeWeight = 0.45;

    /** Optische Dicke einer Strecke dist (m) in der mittleren Höhe hm (m), Dunst mit der Länge fogDist (m, am Boden). */
    public double opticalDepth(double dist, double hm, double fogDist) {
        return dist * (Math.exp(-hm / airScale) / airLength + hazeWeight * Math.exp(-hm / hazeScale) / fogDist);
    }

    /** Mittlere Höhe des Wegs von der Augenhöhe camY in Richtung mit dem Anteil dirY nach oben über dist. */
    public static double midHeight(double camY, double dirY, double dist) { return Math.max(0, camY + dirY * dist * 0.5); }

    /** Anteil des Dunsts (0..1) über der Strecke. */
    public double fraction(double dist, double camY, double dirY, double fogDist) {
        return 1 - Math.exp(-opticalDepth(dist, midHeight(camY, dirY, dist), fogDist));
    }

    /**
     * Gleichwertige Entfernung des Dunsts (m) für ein Ding in der Höhe h, wenn die Luft am Boden die Auslöschung
     * groundExtinction (1/m, etwa {@code Sky.State.groundExtinction}) hat: 1 / (β · Gewicht · e^(−h/1200) + e^(−h/8000) / 150 km).
     */
    public double fogDistanceAt(double h, double groundExtinction) {
        double hh = Math.max(0, h);
        return 1 / Math.max(1e-9, groundExtinction * hazeWeight * Math.exp(-hh / hazeScale) + Math.exp(-hh / airScale) / airLength);
    }
}
