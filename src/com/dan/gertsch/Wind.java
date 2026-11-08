package com.dan.gertsch;

/**
 * Wind im Dickicht, in Java und GLSL gleich ({@code gl/gertsch.glsl}):
 * <ul>
 * <li><b>Zweige</b> neigen sich mit dem Staudruck (Auslenkung ∝ U² [G]: 4 cm bei 5 m/s, 16 cm bei 10 m/s), oben mehr
 * als unten, schwingen mit 0,6 bis 1 Hz um diese Lage, und Böen laufen als Wellen von 18 m Länge mit 0,7 · U über das
 * Dickicht.</li>
 * <li><b>Blätter</b> flattern: Sie drehen sich um ihre Längsachse, bis 26° bei 6 m/s und mehr, mit 2,5 bis 4 Hz [G].
 * Das ändert ihr Licht (die Neigung zur Sonne) und ihre Form im Bild.</li>
 * </ul>
 * Die Laubstreu am Boden bewegt sich nicht.
 */
public final class Wind {
    /** Windgeschwindigkeit (m/s), Richtung, aus der er kommt (Grad, 0 = von +z, 90 = von +x), Böigkeit 0..1. */
    public double staerke = 0, richtung = 250, boeen = 0.5;

    /** Auslenkung (m) bei 1 m/s im Staudruck [G]. */
    public static final double AUSLENKUNG = 0.0016, BOE_LAENGE = 18, FLATTERN = 0.45;

    /** Waagerechte Auslenkung eines Zweigs bei (x, y, z) mit Phase 0..1 zur Zeit t (s) in {@code o[0]} (x) und {@code o[1]} (z). */
    public void versatz(double x, double y, double z, double phase, double t, double[] o) {
        double r = Math.toRadians(richtung + 180), dx = Math.sin(r), dz = Math.cos(r), u = staerke;
        double a = AUSLENKUNG * u * u, hoehe = 0.35 + 0.65 * Math.min(1, Math.max(0, y) / 8);
        double k = 2 * Math.PI / BOE_LAENGE, boe = 1 + boeen * 0.5 * (1 + Math.sin(k * (x * dx + z * dz) - k * 0.7 * u * t));
        double schw = Math.sin(2 * Math.PI * ((0.6 + 0.4 * phase) * t + phase));
        double s = a * hoehe * boe * (0.6 + 0.4 * schw);
        o[0] = dx * s;
        o[1] = dz * s;
    }

    /** Drehwinkel (rad) eines Blatts um seine Längsachse mit Phase 0..1 zur Zeit t. */
    public double flattern(double phase, double t) {
        return FLATTERN * Math.min(1, staerke / 6) * Math.sin(2 * Math.PI * ((2.5 + 1.5 * phase) * t + phase));
    }
}
