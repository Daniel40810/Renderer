package com.dan.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Eine Messstrecke (seit LituyaBay1958 Phase 7, Werkbuch 6.9 und 7.2): ein Linienzug mit dem Maß m (Meter entlang der
 * Linie ab ihrem Anfang plus {@link #m0}), wie ein LRS-Netz in Oracle. Punkt zu Maß ({@link #project}), Maß zu Punkt
 * ({@link #locate}), Stück zwischen zwei Maßen ({@link #clip}); Ereignisse auf der Strecke: Punkte mit Wert und
 * Abschnitte mit Werten an Stützstellen, linear dazwischen ({@link #valueAt}), etwa die Höhe der Spurlinie je Ufermeter.
 * Gleiche Rechnung wie {@code SDO_LRS.LOCATE_PT}, {@code PROJECT_PT} und {@code CLIP_GEOM_SEGMENT} (2D, x und z).
 */
public final class Measure {
    /** Ein Abschnitt: von m1 bis m2, Werte an den Stützstellen ms (aufsteigend, in [m1, m2]). */
    public record Segment(String name, double m1, double m2, double[] ms, double[] values) {
        public double value(double m) {
            if (ms.length == 0 || m < m1 || m > m2) return Double.NaN;
            if (m <= ms[0]) return values[0];
            for (int i = 1; i < ms.length; i++)
                if (m <= ms[i]) {
                    double t = (m - ms[i - 1]) / Math.max(1e-12, ms[i] - ms[i - 1]);
                    return values[i - 1] + (values[i] - values[i - 1]) * t;
                }
            return values[values.length - 1];
        }
    }

    /** Ein Punktereignis: Maß, Wert, Name. */
    public record Event(String name, double m, double value) { }

    /** Ergebnis einer Projektion: Maß, Abstand zur Linie (links +, rechts −, in Laufrichtung), Fußpunkt. */
    public record Projection(double m, double offset, double x, double z) { }

    public final double[] x, z, cum;
    public double m0;
    public final List<Segment> segments = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();

    /** Aus einem Linienzug x, z abwechselnd (mindestens zwei Punkte). */
    public Measure(float[] xz) {
        int n = xz.length / 2;
        if (n < 2) throw new IllegalArgumentException("Messstrecke: mindestens zwei Punkte");
        x = new double[n]; z = new double[n]; cum = new double[n];
        for (int i = 0; i < n; i++) { x[i] = xz[2 * i]; z[i] = xz[2 * i + 1]; }
        for (int i = 1; i < n; i++) cum[i] = cum[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
    }

    public double length() { return cum[cum.length - 1]; }

    /** Punkt beim Maß m (geklemmt): x, z, Richtung dx, dz. */
    public double[] locate(double m) {
        double d = Math.max(0, Math.min(length(), m - m0));
        int i = Arrays.binarySearch(cum, d);
        if (i < 0) i = -i - 2;
        i = Math.max(0, Math.min(cum.length - 2, i));
        double seg = Math.max(1e-12, cum[i + 1] - cum[i]), t = (d - cum[i]) / seg;
        double dx = (x[i + 1] - x[i]) / seg, dz = (z[i + 1] - z[i]) / seg;
        return new double[]{x[i] + (x[i + 1] - x[i]) * t, z[i] + (z[i + 1] - z[i]) * t, dx, dz};
    }

    /** Nächster Punkt der Linie zu (px, pz): Maß, Abstand mit Vorzeichen, Fußpunkt. */
    public Projection project(double px, double pz) {
        double best = Double.MAX_VALUE, bm = 0, bx = x[0], bz = z[0], side = 0;
        for (int i = 0; i + 1 < x.length; i++) {
            double ux = x[i + 1] - x[i], uz = z[i + 1] - z[i], l2 = ux * ux + uz * uz;
            double t = l2 < 1e-18 ? 0 : Math.max(0, Math.min(1, ((px - x[i]) * ux + (pz - z[i]) * uz) / l2));
            double fx = x[i] + ux * t, fz = z[i] + uz * t, d = Math.hypot(px - fx, pz - fz);
            if (d < best) {
                best = d; bx = fx; bz = fz;
                bm = cum[i] + t * Math.sqrt(l2);
                side = Math.signum(ux * (pz - z[i]) - uz * (px - x[i]));
            }
        }
        // links in Laufrichtung (x Ost, z Süd, von oben gesehen): Kreuzprodukt negativ
        return new Projection(bm + m0, -side * best, bx, bz);
    }

    /** Das Stück der Linie zwischen den Maßen m1 und m2 (x, z abwechselnd). */
    public float[] clip(double m1, double m2) {
        double a = Math.min(m1, m2), b = Math.max(m1, m2);
        List<double[]> p = new ArrayList<>();
        double[] s = locate(a);
        p.add(new double[]{s[0], s[1]});
        for (int i = 0; i < x.length; i++) if (cum[i] + m0 > a && cum[i] + m0 < b) p.add(new double[]{x[i], z[i]});
        double[] e = locate(b);
        p.add(new double[]{e[0], e[1]});
        float[] out = new float[2 * p.size()];
        for (int i = 0; i < p.size(); i++) { out[2 * i] = (float) p.get(i)[0]; out[2 * i + 1] = (float) p.get(i)[1]; }
        return out;
    }

    /** Wert des ersten Abschnitts, der m enthält (NaN, wenn keiner). */
    public double valueAt(double m) {
        for (Segment s : segments) { double v = s.value(m); if (!Double.isNaN(v)) return v; }
        return Double.NaN;
    }

    /** Werte in gleichen Schritten (m) über die ganze Strecke: je Schritt Maß und Wert. */
    public double[][] sample(double step) {
        int n = (int) Math.floor(length() / step) + 1;
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) { double m = m0 + i * step; out[i] = new double[]{m, valueAt(m)}; }
        return out;
    }
}
