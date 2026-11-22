package com.dan.core.raum;

/**
 * Der Blick einer Kamera (seit P6a, Werkbuch HalligHooge Teil A, 8.1 und 12.7): Auge in UTM und Höhe in double,
 * Richtung, Neigung, Bildwinkel und Bildgröße. Er steht im Kern, weil jedes Bildmodul ihn liest und die Module einander
 * nicht kennen (Schichtenvertrag, 4).
 * <ul>
 * <li><b>Lage relativ zum Auge:</b> Auf der Karte rechnet alles in float relativ zum Auge (x nach Ost, y nach oben,
 * z nach Süd – wie die Ebene der Szene, 5.1). Die Umrechnung geschieht in double ({@link #relativ}); so bleibt ein Punkt
 * in 30 km auf 1 mm genau, auch wenn das Auge in UTM bei 6 Mio. m steht.</li>
 * <li><b>Projektion umgekehrt, Hinterebene unendlich fern:</b> Tiefe = {@link #nah} / d mit d dem Abstand längs der
 * Blickachse; die vordere Ebene hat Tiefe 1, die Unendlichkeit 0. float hat bei Tiefen nahe 0 die feinsten Schritte, also
 * in der Ferne die beste Auflösung.</li>
 * <li><b>Bild in Vulkan:</b> y im Bild nach unten, Bildpunkt (0, 0) oben links, Tiefe 0..1.</li>
 * </ul>
 * Matrizen sind spaltenweise (wie GLSL, {@code mat4}).
 */
public final class Blick {
    /** Auge: UTM Ost und Nord (m), Höhe (m NHN). */
    public double ost, nord, hoehe;
    /** Blickrichtung als Azimut (Grad von Nord über Ost) und Neigung (Grad, über dem Horizont positiv). */
    public double richtung, neigung;
    /** Senkrechter Bildwinkel (Grad) und vordere Ebene (m). */
    public double bildwinkel = 50, nah = 0.1;
    /** Bildgröße in Bildpunkten. */
    public int breite = 1920, hoehe_px = 1080;

    public Blick() { }

    public Blick(double ost, double nord, double hoehe, double richtung, double neigung, double bildwinkel) {
        this.ost = ost;
        this.nord = nord;
        this.hoehe = hoehe;
        this.richtung = richtung;
        this.neigung = neigung;
        this.bildwinkel = bildwinkel;
    }

    public Blick kopie() {
        Blick b = new Blick(ost, nord, hoehe, richtung, neigung, bildwinkel);
        b.nah = nah;
        b.breite = breite;
        b.hoehe_px = hoehe_px;
        return b;
    }

    public Blick groesse(int breite, int hoehe) {
        this.breite = breite;
        this.hoehe_px = hoehe;
        return this;
    }

    /** Blickachse (Einheitsvektor) relativ zum Auge: x Ost, y oben, z Süd. */
    public double[] achse() {
        double a = Math.toRadians(richtung), e = Math.toRadians(neigung);
        return new double[] {Math.sin(a) * Math.cos(e), Math.sin(e), -Math.cos(a) * Math.cos(e)};
    }

    /** Rechts und oben im Bild (Einheitsvektoren). */
    public double[][] basis() {
        double[] f = achse();
        double[] r = {-f[2], 0, f[0]};                                   // f × (0, 1, 0)
        double n = Math.hypot(r[0], r[2]);
        if (n < 1e-12) {                                                 // senkrecht: rechts nach der Richtung
            double a = Math.toRadians(richtung);
            r = new double[] {Math.cos(a), 0, Math.sin(a)};
        } else {
            r[0] /= n;
            r[2] /= n;
        }
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        return new double[][] {r, u, f};
    }

    /** Lage eines Punkts relativ zum Auge in m (x Ost, y oben, z Süd), in double. */
    public double[] relativ(double ost, double nord, double h) { return new double[] {ost - this.ost, h - this.hoehe, this.nord - nord}; }

    /** Ansicht (nur Drehung; die Lage ist schon relativ zum Auge), spaltenweise. */
    public float[] ansicht() {
        double[][] b = basis();
        double[] r = b[0], u = b[1], f = b[2];
        return new float[] {
                (float) r[0], (float) u[0], (float) -f[0], 0,
                (float) r[1], (float) u[1], (float) -f[1], 0,
                (float) r[2], (float) u[2], (float) -f[2], 0,
                0, 0, 0, 1};
    }

    /** Projektion mit umgekehrter Tiefe und unendlich ferner Hinterebene, für Vulkan (y nach unten), spaltenweise. */
    public float[] projektion() {
        double t = 1 / Math.tan(Math.toRadians(bildwinkel) / 2), a = (double) breite / hoehe_px;
        return new float[] {
                (float) (t / a), 0, 0, 0,
                0, (float) -t, 0, 0,
                0, 0, 0, -1,
                0, 0, (float) nah, 0};
    }

    /** Projektion mal Ansicht, spaltenweise. */
    public float[] ansichtProjektion() { return mal(projektion(), ansicht()); }

    /** Bildpunkt (x, y mit Mitte bei ,5) und Tiefe eines Punkts relativ zum Auge, in double; null hinter dem Auge. */
    public double[] bildpunkt(double[] rel) {
        double[][] b = basis();
        double x = dot(b[0], rel), y = dot(b[1], rel), d = dot(b[2], rel);
        if (d <= 0) return null;
        double t = 1 / Math.tan(Math.toRadians(bildwinkel) / 2), a = (double) breite / hoehe_px;
        double nx = x * t / a / d, ny = -y * t / d;
        return new double[] {(nx + 1) / 2 * breite, (ny + 1) / 2 * hoehe_px, nah / d};
    }

    /** Richtung des Strahls durch einen Bildpunkt (x, y im Bild), relativ zum Auge, Einheitsvektor. */
    public double[] strahl(double px, double py) {
        double t = 1 / Math.tan(Math.toRadians(bildwinkel) / 2), a = (double) breite / hoehe_px;
        double nx = px / breite * 2 - 1, ny = py / hoehe_px * 2 - 1;
        double[][] b = basis();
        double[] v = new double[3];
        for (int i = 0; i < 3; i++) v[i] = b[0][i] * nx * a / t - b[1][i] * ny / t + b[2][i];
        double n = Math.sqrt(dot(v, v));
        for (int i = 0; i < 3; i++) v[i] /= n;
        return v;
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    /** a · b für spaltenweise 4 × 4-Matrizen. */
    public static float[] mal(float[] a, float[] b) {
        float[] c = new float[16];
        for (int j = 0; j < 4; j++)
            for (int i = 0; i < 4; i++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += (double) a[k * 4 + i] * b[j * 4 + k];
                c[j * 4 + i] = (float) s;
            }
        return c;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "Auge O %.1f, N %.1f, %.1f m NHN, Richtung %.1f°, Neigung %.1f°, Bildwinkel %.1f°, %d × %d", ost, nord, hoehe, richtung, neigung,
                bildwinkel, breite, hoehe_px);
    }
}
