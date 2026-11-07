package com.dan.exposure;

/**
 * Vorbelichtung einer Szene mit Teilanpassung (aus LituyaBay1958, Werkbuch Welle W1 „Licht“, dazu W6 und W7). Alle
 * Helligkeiten der Szene werden schon beim Zeichnen mit {@link #value()} multipliziert, damit auch eine Nacht im Halbfloat
 * bleibt; die Nachbearbeitung belichtet danach mit 1.
 * <p>
 * <b>Messung.</b> Leuchtdichte der Anpassung a = √(A · V): A die des Himmels (etwa {@code Sky.State.adaptation}), V die
 * im Blickfeld (etwa {@code Sky.State.viewLuminance}); unter Wolken mal dem Helligkeitsfaktor des Himmels (mindestens 0,05).
 * <br><b>Ziel.</b> E = key / L<sub>ref</sub> · (a / L<sub>ref</sub>)<sup>−p</sup> · 2<sup>bias + Tag</sup>, mit dem
 * Exponenten der Anpassung p ({@link #adaptation}, 1 volle, 0 keine): mit 0,8 bleibt die Dämmerung dunkel, wie sie ist
 * [G]. Am Tag bei hoher Sonne zusätzlich {@link #dayStops} Blenden (Lituya W6: −1, sonst brannten Schnee, Wiesen und
 * Dunst aus), Übergang zwischen {@link #dayFrom} und {@link #dayTo} Grad Sonnenhöhe.
 * <br><b>Zeit.</b> In log geglättet mit der Rate {@link #rate} (1/s); im Standbild sofort.
 * <p>
 * Die Formel des Ziels ist dieselbe wie {@code Sky.State.exposureFor} in {@code com.dan.sky} (Bezug 0,105, Schlüssel 0,35).
 */
public final class PreExposure {
    /** Bezugsleuchtdichte und Schlüssel (wie com.dan.sky). */
    public double reference = 0.105, key = 0.35;
    /** Exponent der Anpassung (1 volle, 0 keine). */
    public double adaptation = 0.8;
    /** Blenden am Tag und der Übergang nach der Sonnenhöhe (Grad) [G]. */
    public double dayStops = -1.0, dayFrom = 5, dayTo = 25;
    /** Glättung in log (1/s). */
    public double rate = 1.5;
    /** Untere Grenze des Helligkeitsfaktors unter Wolken. */
    public double minBrightness = 0.05;

    private double pre = 1, target = 1;

    /** Vorbelichtung jetzt (alle Helligkeiten der Szene damit multiplizieren). */
    public double value() { return pre; }
    /** Ziel ohne Glättung. */
    public double target() { return target; }

    /** Belichtung für die Leuchtdichte der Anpassung a mit dem Exponenten p (wie {@code Sky.State.exposureFor}). */
    public double exposureFor(double a, double p) {
        double aa = Math.max(1e-12, a);
        return key / reference * Math.pow(aa / reference, -p);
    }

    /**
     * Ein Bild: Leuchtdichte der Anpassung des Himmels und im Blickfeld, Helligkeitsfaktor des Himmels (Wolken, ohne 1),
     * Sonnenhöhe (Grad), Korrektur in Blenden (Regler), Zeitschritt (s), Standbild (sofort). Gibt die Vorbelichtung zurück.
     */
    public double update(double skyAdaptation, double viewLuminance, double brightness, double sunElevDeg, double biasStops,
                         double dt, boolean still) {
        double a = Math.sqrt(Math.max(1e-14, skyAdaptation) * Math.max(1e-14, viewLuminance));
        a *= Math.max(minBrightness, Math.min(1, brightness));
        double tag = dayStops * Math.max(0, Math.min(1, (sunElevDeg - dayFrom) / (dayTo - dayFrom)));
        target = exposureFor(a, adaptation) * Math.pow(2, biasStops + tag);
        pre = still || !(pre > 0) ? target : Math.exp(Math.log(pre) + (Math.log(target) - Math.log(pre)) * Math.min(1, dt * rate));
        return pre;
    }

    /** Sofort auf das Ziel (Schnitt, Standbild). */
    public void snap() { pre = target; }
}
