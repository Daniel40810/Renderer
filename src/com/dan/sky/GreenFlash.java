package com.dan.sky;

/**
 * Seit Sylt Phase 13: das grüne Leuchten (grüner Blitz) beim Auf- und Untergang der Sonne über einer freien Kimm. Die
 * Luft bricht kurzwelliges Licht etwas stärker als langwelliges (Brechzahl der Luft bei 15 °C: (n − 1) · 10⁶ = 275,9 bei
 * 650 nm, 277,7 bei 550 nm, 280,6 bei 450 nm); das grüne Bild der Sonne steht darum ein wenig über dem roten, an der Kimm
 * bei 34′ Brechung rund 0,22′. Blau ginge noch höher, verlöscht aber auf dem langen Weg durch die Luft. Geht die Sonne
 * unter, verschwindet das rote Bild zuerst, und der letzte Rand ist grün. Mit bloßem Auge sieht man das nur, wenn eine
 * Luftspiegelung (warmes Wasser unter kalter Luft) das Bild streckt: {@code magnification} 1 ist die reine Brechung
 * (der grüne Rand 1 bis 2 s, im Fernglas), 2 bis 4 eine Spiegelung mit einem grünen Blitz von einigen Sekunden [G]. Der Shader zeichnet je Farbkanal eine eigene Scheibe.
 */
public final class GreenFlash {
    private GreenFlash() { }

    /** Brechung je Kanal (R, G, B) relativ zu Grün, aus der Brechzahl der Luft. */
    public static final double[] RELATIVE = {275.9 / 277.7, 1.0, 280.6 / 277.7};

    /** Faktoren auf die Brechung je Kanal bei einer Vergrößerung m (0 oder 1 ohne Spiegelung; 0: aus, alle 1). */
    public static double[] factors(double m) {
        if (m <= 0) return new double[]{1, 1, 1};
        return new double[]{1 + (RELATIVE[0] - 1) * m, 1, 1 + (RELATIVE[2] - 1) * m};
    }

    /** Wie weit (Bogenminuten) das Bild eines Kanals über dem grünen steht, bei scheinbarer Höhe h (Grad). */
    public static double offsetArcmin(double appAltDeg, double m, int channel) {
        return Celestial.refractionApparent(appAltDeg) * 60 * (factors(m)[channel] - 1);
    }

    /**
     * Sichtbarer Anteil der Scheibe je Kanal über einer Kimm (Grad, negativ unter dem mathematischen Horizont), wenn die
     * Mitte der grünen Scheibe scheinbar bei h steht: {R, G, B}, jeweils 0..1 der Fläche.
     */
    public static double[] visible(double appAltDeg, double radiusDeg, double horizonDeg, double m) {
        double[] out = new double[3];
        for (int c = 0; c < 3; c++) {
            double center = appAltDeg + offsetArcmin(appAltDeg, m, c) / 60;
            double x = (center - horizonDeg) / radiusDeg;   // −1: ganz unten, 1: ganz oben
            out[c] = x <= -1 ? 0 : x >= 1 ? 1 : segment(x);
        }
        return out;
    }

    /** Anteil der Fläche eines Kreises über der Sehne in der Höhe −x (x von −1 bis 1). */
    static double segment(double x) {
        double y = -x, a = Math.acos(Math.max(-1, Math.min(1, y)));
        return (a - y * Math.sqrt(Math.max(0, 1 - y * y))) / Math.PI;
    }
}
