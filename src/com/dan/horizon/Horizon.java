package com.dan.horizon;

import java.util.function.DoubleBinaryOperator;
import java.util.stream.IntStream;

/**
 * Bergschatten aus Horizontkarten (LituyaBay1958, Werkbuch Welle W1, L2). Für jeden Bodenpunkt eines Gitters der höchste
 * Winkel des Geländes in Richtung des Sonnen-Azimuts, mit Erdkrümmung und Strahlenbrechung (wirksamer Erdradius
 * R / (1 − k), k = 0,13) und mindestens der Kimm des Meers aus der Höhe des Punkts. Die Karte gilt für jede Sonnenhöhe bei
 * diesem Azimut: ob ein Punkt Sonne hat, entscheidet erst der Shader aus der Sonnenhöhe. Darum steigt das Alpenglühen beim
 * Sinken der Sonne von selbst die Hänge hinauf, und neu gerechnet wird nur, wenn das Azimut um mehr als
 * {@link #azimuthStep} Grad wandert.
 * <p>
 * Gelände aus zwei Gittern: ein feines über den Kern der Szene und ein grobes über die ganze Szene (die fernen Gipfel, die
 * Schatten werfen); außerhalb liegt das Meer ({@link #seaLevel}). Gesucht wird auf dem Strahl mit wachsender Schrittweite
 * (erster Schritt 0,75 Zellen, dann 2,2 % der Entfernung), höchstens {@link #maxDistance} m und {@link #maxSteps} Schritte.
 * <p>
 * Der Kern rechnet ohne Grafikkarte genau wie {@code gl/horizon.comp} (Prüfung, Standbilder ohne GL, Werkzeuge);
 * {@code gl/HorizonGl} rechnet dieselbe Karte auf der Grafikkarte in rund 0,1 s.
 */
public final class Horizon {
    /** Qualitätsstufen: Zellen je Kante des feinen und des groben Gitters. */
    public enum Quality {
        LOW(512, 256), MEDIUM(1024, 512), HIGH(2048, 1024), ULTRA(4096, 2048);
        public final int fine, coarse;
        Quality(int fine, int coarse) { this.fine = fine; this.coarse = coarse; }
    }

    /** Ein quadratisches Gitter von Höhen: Ecke x0, z0 (kleinste x und z der Szene), Kante (m), n Zellen je Kante. */
    public static final class Grid {
        public final double x0, z0, edge;
        public final int n;
        /** Höhen (m), Zeile j bei z0 + (j + ½) · Zelle, Spalte i bei x0 + (i + ½) · Zelle. */
        public final float[] h;

        public Grid(double x0, double z0, double edge, int n, float[] h) {
            if (h.length != n * n) throw new IllegalArgumentException("horizon: " + h.length + " Höhen für " + n + "²");
            this.x0 = x0; this.z0 = z0; this.edge = edge; this.n = n; this.h = h;
        }

        public double cell() { return edge / n; }

        /** Mitte der Zelle (i, j): x, z. */
        public double x(int i) { return x0 + (i + 0.5) * cell(); }
        public double z(int j) { return z0 + (j + 0.5) * cell(); }

        /** Ob (x, z) im Gitter liegt (Rand eingeschlossen, wie im Shader). */
        public boolean contains(double x, double z) {
            double u = (x - x0) / edge, v = (z - z0) / edge;
            return u >= 0 && u <= 1 && v >= 0 && v <= 1;
        }

        /** Höhe an (x, z), bilinear zwischen den Mitten der Zellen, am Rand fest (wie GL_LINEAR, CLAMP_TO_EDGE). */
        public double at(double x, double z) {
            double fi = (x - x0) / edge * n - 0.5, fj = (z - z0) / edge * n - 0.5;
            int i = (int) Math.floor(fi), j = (int) Math.floor(fj);
            double a = fi - i, b = fj - j;
            int i0 = clamp(i), i1 = clamp(i + 1), j0 = clamp(j), j1 = clamp(j + 1);
            return (1 - b) * ((1 - a) * h[j0 * n + i0] + a * h[j0 * n + i1]) + b * ((1 - a) * h[j1 * n + i0] + a * h[j1 * n + i1]);
        }

        private int clamp(int i) { return i < 0 ? 0 : i >= n ? n - 1 : i; }

        /** Höhen aus einer Funktion (x, z) → m; was fehlt (NaN) oder unter dem Meer liegt, wird zum Meer. */
        public static Grid sample(DoubleBinaryOperator height, double x0, double z0, double edge, int n, double sea) {
            float[] v = new float[n * n];
            double c = edge / n;
            IntStream.range(0, n).parallel().forEach(j -> {
                for (int i = 0; i < n; i++) {
                    double hh = height.applyAsDouble(x0 + (i + 0.5) * c, z0 + (j + 0.5) * c);
                    v[j * n + i] = (float) (Double.isNaN(hh) ? sea : Math.max(sea, hh));
                }
            });
            return new Grid(x0, z0, edge, n, v);
        }

        /** Wie {@link #sample}, aber mit den Tiefen unter dem Meer (Grund für das Wasser); was fehlt, wird missing. */
        public static Grid sampleRaw(DoubleBinaryOperator height, double x0, double z0, double edge, int n, double missing) {
            float[] v = new float[n * n];
            double c = edge / n;
            IntStream.range(0, n).parallel().forEach(j -> {
                for (int i = 0; i < n; i++) {
                    double hh = height.applyAsDouble(x0 + (i + 0.5) * c, z0 + (j + 0.5) * c);
                    v[j * n + i] = (float) (Double.isNaN(hh) ? missing : hh);
                }
            });
            return new Grid(x0, z0, edge, n, v);
        }

        /** Ein Gitter der Kante edge um die Mitte (cx, cz). */
        public static double[] around(double cx, double cz, double edge) { return new double[]{cx - edge / 2, cz - edge / 2, edge}; }
    }

    /** Erdradius (m) und Brechungszahl der Luft (k, Anteil der Krümmung, den die Brechung aufhebt). */
    public double earthRadius = 6371000.0, refraction = 0.13;
    /** Höhe des Meers außerhalb der Gitter (m). */
    public double seaLevel = 0;
    /** Suche: größte Entfernung (m), größte Zahl der Schritte, erster Schritt in Zellen, Wachstum der Schritte. */
    public double maxDistance = 90_000, firstStep = 0.75, growth = 0.022;
    public int maxSteps = 420;
    /** Halber Winkel des Halbschattens der Sonnenscheibe (rad, 0,53°/2) und Entfernung des Hindernisses für den Hub über
     *  dem Boden (m): ein Punkt h m über dem Boden sieht um h/2000 rad mehr Himmel [G]. */
    public double penumbra = 0.0046, liftDistance = 2000;
    /** Neu rechnen, wenn das Azimut der Sonne um mehr als so viele Grad gewandert ist. */
    public double azimuthStep = 0.25;

    /** Wirksamer Erdradius mit Brechung (m). */
    public double effectiveRadius() { return earthRadius / (1 - refraction); }

    /** Winkel der Kimm des Meers unter der Waagerechten (rad, als Steigung genähert) aus der Höhe h über dem Meer. */
    public double seaDip(double h) { return Math.sqrt(2 * Math.max(h - seaLevel, 0) / effectiveRadius()); }

    /** Höhe (m) aus fein, sonst grob, sonst Meer (wie {@code hoehe} im Shader). */
    public double height(Grid fine, Grid coarse, double x, double z) {
        if (fine != null && fine.contains(x, z)) return fine.at(x, z);
        if (coarse != null && coarse.contains(x, z)) return coarse.at(x, z);
        return seaLevel;
    }

    /**
     * Horizontwinkel (rad über der Waagerechten) am Punkt (x, z) in Richtung des Azimuts az (Grad von Nord über Ost;
     * Szene: x nach Ost, z nach Süd). out[0] = Winkel, out[1] = Höhe des Bodens. Zellgröße c bestimmt den ersten Schritt.
     */
    public void angle(Grid fine, Grid coarse, double x, double z, double azDeg, double c, double[] out) {
        double a = Math.toRadians(azDeg), dx = Math.sin(a), dz = -Math.cos(a);
        double R = effectiveRadius();
        double h0 = height(fine, coarse, x, z);
        double best = -Math.sqrt(2.0 * Math.max(h0 - seaLevel, 0.0) / R);
        double d = c * firstStep;
        for (int k = 0; k < maxSteps && d < maxDistance; k++) {
            double hh = height(fine, coarse, x + dx * d, z + dz * d);
            best = Math.max(best, (hh - h0 - d * d / (2.0 * R)) / d);
            d += Math.max(c * firstStep, d * growth);
        }
        out[0] = Math.atan(best);
        out[1] = h0;
    }

    /** Die Karte über target (r = Winkel, g = Boden, je Zelle zwei Werte), auf allen Kernen. */
    public float[] map(Grid target, Grid fine, Grid coarse, double azDeg) {
        int n = target.n;
        float[] out = new float[n * n * 2];
        double c = target.cell();
        IntStream.range(0, n).parallel().forEach(j -> {
            double[] r = new double[2];
            for (int i = 0; i < n; i++) {
                angle(fine, coarse, target.x(i), target.z(j), azDeg, c, r);
                out[(j * n + i) * 2] = (float) r[0];
                out[(j * n + i) * 2 + 1] = (float) r[1];
            }
        });
        return out;
    }

    /**
     * Sonne ja/nein (0..1): Horizontwinkel angle und Boden ground an der Stelle, Punkt in der Höhe y, Sonnenhöhe mit Sinus
     * sinEl; weich über die Sonnenscheibe, ein Punkt über dem Boden sieht etwas mehr (wie {@code horizonSun} im Shader).
     */
    public double visibility(double angle, double ground, double y, double sinEl) {
        double el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
        double lift = Math.max(y - ground, 0) / liftDistance;
        return smooth(-penumbra, penumbra, el - angle + lift);
    }

    /** Ob die Sonne seit dem Azimut last (Grad, NaN: nie) so weit gewandert ist, dass neu gerechnet werden muss. */
    public boolean moved(double lastAz, double az) {
        return Double.isNaN(lastAz) || Math.abs(((az - lastAz) % 360 + 540) % 360 - 180) > azimuthStep;
    }

    /** Azimut (Grad von Nord über Ost) der Richtung (x Ost, z Süd). */
    public static double azimuth(double[] dir) { return Math.toDegrees(Math.atan2(dir[0], -dir[2])); }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
