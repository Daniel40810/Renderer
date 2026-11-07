package com.dan.dustcloud;

/**
 * Staubfahne über einem Abbruch (aus LituyaBay1958, Werkbuch Welle W3c, Frage WF15 B) [G]: eine Säule feinen Staubs, die
 * über der abgerissenen Platte aufsteigt, oben pilzförmig aufquillt, sich mit der Höhe in den Wind neigt und nach einer
 * Viertelstunde verweht. Für die Darstellung ({@code gl/dust.glsl}, {@code dustPlumeDensity}); der Schwerestrom am Boden ist
 * {@link DustCloud}.
 * <p>
 * <b>Kopf.</b> z<sub>top</sub>(t) = H (1 − e<sup>−t·w/H</sup>): anfangs mit w (38 m/s), dann gegen H (1 200 m) [G, nach dem
 * Auftrieb einer warmen Staubsäule; Zeugen 1958 sahen über dem Abbruch keinen Staub über die Kette, gestaltet].
 * <br><b>Säule.</b> Halbmesser R = R<sub>0</sub> (0,55 + 0,6 h<sub>rel</sub>) (0,6 + 0,4 s(t/40 s)), am Kopf (ab 0,72 der
 * Höhe) bis 1,7-fach; die Achse neigt sich mit 0,10 je Meter Höhe in die Richtung, in die der Wind weht, sobald die
 * Säule steht (90 s), zum Kopf hin stärker.
 * <br><b>Leben.</b> Dichte 0,0045/m (unten 1,3-fach, oben 0,55-fach), steigt in 6 s auf, verweht zwischen 200 und 900 s.
 */
public final class Plume {
    /** Fuß der Achse (Szene x, z), Pegel (m), Halbmesser der Platte (m). */
    public double x, z, level, radius = 300;
    /** Höhe des Kopfs über dem Pegel am Ende (m), anfängliche Steiggeschwindigkeit (m/s), Lebensdauer (s). */
    public double height = 1_200, rise = 38, life = 900;
    /** Richtung, in die der Wind weht (Szene x, z, Einheitsvektor). */
    public double[] windTo = {0.8, 0.6};

    public Plume() { }

    public Plume(double x, double z, double radius) { this.x = x; this.z = z; this.radius = radius; }

    /** Oberkante des Kopfs über dem Pegel nach age Sekunden (m). */
    public double top(double age) { return height * (1 - Math.exp(-Math.max(age, 0) * rise / height)); }

    /** Ob die Fahne nach age Sekunden zu sehen ist. */
    public boolean active(double age) { return age > 0 && age < life; }

    /**
     * Die Werte für den Shader: {x, z, Oberkante (m über NAVD/Bezug, mindestens Pegel + 30), Alter} und
     * {Halbmesser, Wind x, Wind z}.
     */
    public float[][] uniforms(double age) {
        double t = level + Math.max(top(age), 30);
        return new float[][]{{(float) x, (float) z, (float) t, (float) age}, {(float) radius, (float) windTo[0], (float) windTo[1]}};
    }

    /**
     * Hülle der Dichte ohne Rauschen (0..1) wie im Shader mit Rauschen 0,5: Säule, Kopf, Fuß. Für Prüfungen und grobe
     * Rechnungen (Sicht, Schatten).
     */
    public double envelope(double px, double py, double pz, double age) {
        double top = level + Math.max(top(age), 30);
        double y = py - level;
        if (y < -5 || py > top + 220) return 0;
        double hrel = Math.max(0, Math.min(1.2, y / Math.max(top - level, 30)));
        double lean = y * 0.10 * smooth(0, 90, age) * (0.6 + 0.8 * hrel);
        double ax = x + windTo[0] * lean, az = z + windTo[1] * lean;
        double R = radius * (0.55 + 0.6 * hrel) * (0.6 + 0.4 * smooth(0, 40, age));
        R *= 1 + 0.7 * smooth(0.72, 1.0, hrel);
        double r = Math.hypot(px - ax, pz - az);
        double form = 1 - smooth(0.45 * R, R, r);
        double kopf = 1 - smooth(top - 0.18 * (top - level), top + 60, py);
        double fuss = smooth(-5, 60, y);
        return form * kopf * fuss;
    }

    static double smooth(double a, double b, double v) { double t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }
}
