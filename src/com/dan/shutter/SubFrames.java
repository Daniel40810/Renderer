package com.dan.shutter;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Die Teilbilder eines Filmbilds in der Kino-Stufe: n Augenblicke über die Zeit, in der der Verschluss irgendwo offen ist,
 * je mit einem Ort in der Pupille und einem Versatz im Bildpunkt. Zusammen sind es fünf Maße (Zeit, Pupille x und y, Bildpunkt
 * x und y); sie kommen aus einer Kronecker-Folge in fünf Dimensionen (Roberts 2018, „R_d“: α_j = 1/g^j, g die Wurzel von
 * x^6 = x + 1), so liegen sie in jedem Maß und in jedem Paar gleichmäßig, auch in jedem zusammenhängenden Stück der Zeit (das
 * braucht der Öffnungsgang: Ein Bildpunkt sieht nur die Teilbilder, die in sein Fenster fallen). Die Orte in der Pupille kommen
 * aus einer eigenen Folge in zwei Maßen (R2, g die Wurzel von x³ = x + 1) über das umschreibende Rechteck, behalten wird, was in
 * der Blende liegt: so bleiben sie im Bild gleichmäßig (eine Abbildung über Winkel und Halbmesser legte Spiralen in die
 * Kreise). Je Filmbild sind sie um
 * einen festen Betrag verschoben (Cranley-Patterson), so wechselt das Restrauschen von Bild zu Bild wie Korn; ein Standbild
 * ist wiederholbar. Der Versatz im Bildpunkt folgt einem Zelt über ±1 Bildpunkt (Rekonstruktion mit dem Zeltfilter).
 * Nach der Zeit sortiert: Die Szene läuft vorwärts von Teilbild zu Teilbild.
 */
public final class SubFrames {
    /** Ein Teilbild: Zeit (s, auf der Uhr von {@link Shutter#openAt}), Ort in der Pupille (Fläche π), Versatz (Bildpunkte). */
    public record Sample(int index, double tauS, double u, double v, double jx, double jy) { }

    /** g für fünf Maße (x^6 = x + 1). */
    static final double G5 = gRoot(5);

    static double gRoot(int d) {
        double x = 2;
        for (int i = 0; i < 60; i++) x = Math.pow(1 + x, 1.0 / (d + 1));
        return x;
    }

    /**
     * Die n Teilbilder für das Filmbild frame. Ohne Pupille (pupil null) alle in der Mitte; ohne Versatz alle auf dem
     * Bildpunkt. Zeiten von openRange()[0] bis openRange()[1] + T.
     */
    public static Sample[] plan(Shutter sh, Pupil pupil, int n, long frame, boolean jitter) {
        n = Math.max(1, n);
        double[] a = new double[5];
        for (int j = 0; j < 5; j++) a[j] = 1 / Math.pow(G5, j + 1);
        double[] cp = new double[5];
        for (int j = 0; j < 5; j++) cp[j] = frame == 0 ? 0.5 : hash01(frame * 0x9E3779B97F4A7C15L + j * 0xBF58476D1CE4E5B9L);
        double[] r = sh.openRange();
        double t0 = r[0], w = r[1] - r[0] + sh.openS();
        Sample[] s = new Sample[n];
        double g2 = gRoot(2), b1 = 1 / g2, b2 = 1 / (g2 * g2);
        double rx = pupil == null ? 0 : pupil.scale(), ry = pupil == null ? 0 : pupil.scale() * pupil.squeeze;
        long k = 0;
        for (int i = 0; i < n; i++) {
            double[] x = new double[5];
            for (int j = 0; j < 5; j++) x[j] = frac(cp[j] + (i + 1) * a[j]);
            // die Zeit geschichtet: je Teilbild ein Streifen der Breite W/n, darin der Ort aus der Folge
            double tau = t0 + w * (i + x[0]) / n;
            double[] p = {0, 0};
            if (pupil != null) {
                for (int tries = 0; tries < 64; tries++) {
                    k++;
                    double px = (2 * frac(cp[1] + k * b1) - 1) * rx, py = (2 * frac(cp[2] + k * b2) - 1) * ry;
                    if (pupil.inside(px, py)) { p = new double[]{px, py}; break; }
                }
            }
            double jx = jitter ? tent(x[3]) : 0, jy = jitter ? tent(x[4]) : 0;
            s[i] = new Sample(i, tau, p[0], p[1], jx, jy);
        }
        Arrays.sort(s, Comparator.comparingDouble(Sample::tauS));
        return s;
    }

    /** Zelt über (−1, 1) aus u ∈ [0, 1). */
    static double tent(double u) { return u < 0.5 ? Math.sqrt(2 * u) - 1 : 1 - Math.sqrt(2 - 2 * u); }

    static double frac(double x) { return x - Math.floor(x); }

    static double hash01(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ((z >>> 11) + 0.5) / (1L << 53);
    }

    // ------------------------------------------------------------------ Kamera je Teilbild (Spalten-Matrizen wie OpenGL)

    /**
     * Die Blickmatrix vom Ort (ex, ey) der Pupille aus (m, im Kameraraum: x rechts, y oben): T(−e) · view. Die Richtung bleibt,
     * die Kamera rückt um e.
     */
    public static float[] offsetView(float[] view, double ex, double ey) {
        float[] v = view.clone();
        v[12] -= (float) ex;
        v[13] -= (float) ey;
        return v;
    }

    /**
     * Die Projektion geschert, so dass die Schärfenebene im Abstand focusM (m) trotz des Versatzes e an ihrem Ort im Bild
     * bleibt: P02 −= P00 · ex / s, P12 −= P11 · ey / s. Mit dem Versatz (jx, jy) Bildpunkten im Bild w × h.
     */
    public static float[] shearProjection(float[] proj, double ex, double ey, double focusM, double jx, double jy, int w, int h) {
        float[] p = proj.clone();
        double s = Math.max(1e-3, focusM);
        p[8] += (float) (-p[0] * ex / s + jx * 2.0 / w);
        p[9] += (float) (-p[5] * ey / s + jy * 2.0 / h);
        return p;
    }

    /**
     * Wohin ein Punkt im Abstand d (m) auf der Achse wandert, wenn die Kamera um e (m) in der Pupille rückt und die Schärfe auf
     * s (m) liegt: Verschiebung im Bild in Einheiten der Brennweite, e · (1/s − 1/d). Der Kreis aller Orte hat den Durchmesser
     * D · |1/s − 1/d| · f – die dünne Linse für d, s ≫ f.
     */
    public static double imageShift(double e, double s, double d) { return e * (1 / s - 1 / d); }

    private SubFrames() { }
}
