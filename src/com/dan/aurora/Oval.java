package com.dan.aurora;

/**
 * Das Polarlichtoval nach dem Kp-Index: Äquatorgrenze der diffusen Aurora um Mitternacht nach Gussenhoven, Hardy und
 * Heinemann (1983), 66,1° − 2,0°·Kp, gerundet; die diskreten Bögen stehen 2,5° polwärts davon, die Polgrenze bei
 * 6,5° + 0,8°·Kp. Zur Tagseite hin rücken alle Grenzen um bis zu 8° polwärts (Feldstein und Starkov 1967, Holzworth und
 * Meng 1975). Alle Breiten sind magnetische Breiten in Grad, auf beiden Halbkugeln als Betrag.
 */
public final class Oval {
    private Oval() { }

    /** Äquatorgrenze der diffusen Aurora. */
    public static double diffusAequator(double kp, double mlt) {
        return 66.1 - 2.0 * begrenzt(kp) + tagesgang(mlt);
    }

    /** Äquatorgrenze der diskreten Bögen. */
    public static double bogenAequator(double kp, double mlt) {
        return diffusAequator(kp, mlt) + 2.5;
    }

    /** Polgrenze des Ovals. */
    public static double bogenPol(double kp, double mlt) {
        return diffusAequator(kp, mlt) + 6.5 + 0.8 * begrenzt(kp);
    }

    /** Verschiebung polwärts zur Tagseite: 0 um Mitternacht, 8° um Mittag. */
    static double tagesgang(double mlt) {
        return 8 * (1 - Math.cos(Math.PI * mlt / 12)) / 2;
    }

    static double begrenzt(double kp) {
        return Math.max(0, Math.min(9, kp));
    }

    /** Kilometer je Grad magnetischer Breite auf der Erdoberfläche. */
    public static final double KM_JE_GRAD = Math.PI / 180 * Magnetfeld.ERDRADIUS;

    /**
     * Höhenwinkel in Grad, unter dem ein Punkt in {@code hoeheKm} über dem Boden in {@code entfernungKm} Bogenlänge vom
     * Beobachter erscheint, mit Erdkrümmung; negativ heißt unter der Kimm.
     */
    public static double hoehenwinkel(double entfernungKm, double hoeheKm) {
        double r = Magnetfeld.ERDRADIUS, a = Math.abs(entfernungKm) / r;
        double x = (r + hoeheKm) * Math.sin(a), y = (r + hoeheKm) * Math.cos(a) - r;
        return Math.toDegrees(Math.atan2(y, x));
    }
}
