package com.dan.quake;

/**
 * Eine Störung als Linienzug an der Oberfläche (x Ost, z Süd, m), senkrecht bis {@link #bottom} (m) in die Tiefe, mit
 * Herd (Hypozentrum) und dem Teil, der bricht. Länge und größter Versatz aus der Magnitude nach WELLS94 für
 * Blattverschiebungen (Wells und Coppersmith 1994): log10 L = −3,55 + 0,74 M (Bruch an der Oberfläche, km),
 * log10 D = −7,03 + 1,03 M (größter Versatz, m). Der Bruch läuft vom Herd mit {@link #vr} (m/s) nach beiden Seiten, im
 * Verhältnis {@link #forward} nach vorn (wachsendes Maß s); er endet, wo die Länge erreicht ist oder die Linie aufhört.
 */
public final class Fault {
    public final double[] x, z, cum;
    /** Unterkante der Bruchfläche (m Tiefe). */
    public double bottom = 15_000;
    /** Herd: Lage (m) und Tiefe (m). */
    public double hx, hz, hdepth = 10_000;
    /** Anteil der Bruchlänge vor dem Herd (in Richtung wachsender s); 0,5 = beidseitig gleich. */
    public double forward = 0.5;
    /** Geschwindigkeit des Bruchs (m/s); Werkbuch 9.1: 0,8 · vS. */
    public double vr = 2800;
    public Bssa14.Mechanism mechanism = Bssa14.Mechanism.STRIKE_SLIP;
    /** Länge des Bruchs (m); NaN = aus der Magnitude nach WELLS94. */
    public double ruptureLength = Double.NaN;

    public Fault(double[] xz) {
        int n = xz.length / 2;
        if (n < 2) throw new IllegalArgumentException("Störung: mindestens zwei Punkte");
        x = new double[n]; z = new double[n]; cum = new double[n];
        for (int i = 0; i < n; i++) { x[i] = xz[2 * i]; z[i] = xz[2 * i + 1]; }
        for (int i = 1; i < n; i++) cum[i] = cum[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
        hx = x[0]; hz = z[0];
    }

    public double length() { return cum[cum.length - 1]; }

    /** WELLS94, Blattverschiebung: Länge des Bruchs an der Oberfläche (m). */
    public static double wellsLength(double m) { return 1000 * Math.pow(10, -3.55 + 0.74 * m); }

    /** WELLS94, Blattverschiebung: größter Versatz (m). */
    public static double wellsDisplacement(double m) { return Math.pow(10, -7.03 + 1.03 * m); }

    /** Punkt beim Maß s (geklemmt): x, z. */
    public double[] point(double s) {
        s = Math.max(0, Math.min(length(), s));
        int i = 1;
        while (i < cum.length - 1 && cum[i] < s) i++;
        double t = (s - cum[i - 1]) / Math.max(1e-9, cum[i] - cum[i - 1]);
        return new double[]{x[i - 1] + (x[i] - x[i - 1]) * t, z[i - 1] + (z[i] - z[i - 1]) * t};
    }

    /** Maß s und Abstand zur Linie des Punkts (px, pz). */
    public double[] project(double px, double pz) {
        double best = Double.MAX_VALUE, bs = 0;
        for (int i = 0; i + 1 < x.length; i++) {
            double ux = x[i + 1] - x[i], uz = z[i + 1] - z[i], l2 = ux * ux + uz * uz;
            double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - x[i]) * ux + (pz - z[i]) * uz) / l2));
            double d = Math.hypot(px - x[i] - ux * t, pz - z[i] - uz * t);
            if (d < best) { best = d; bs = cum[i] + t * Math.sqrt(l2); }
        }
        return new double[]{bs, best};
    }

    /** Maß des Herds auf der Linie. */
    public double hypoS() { return project(hx, hz)[0]; }

    /** Anfang und Ende des Bruchs (Maß s) für die Magnitude m. */
    public double[] rupture(double m) {
        double l = Double.isNaN(ruptureLength) ? wellsLength(m) : ruptureLength, h = hypoS();
        double a = h - l * (1 - forward), b = h + l * forward;
        // was über ein Ende hinaus ginge, kommt auf die andere Seite (die Länge bleibt, solange die Linie reicht)
        if (a < 0) { b = Math.min(length(), b - a); a = 0; }
        if (b > length()) { a = Math.max(0, a - (b - length())); b = length(); }
        return new double[]{a, b};
    }

    /** Wann der Bruch das Maß s erreicht (s nach dem ersten Stoß); +∞ außerhalb des Bruchs. */
    public double ruptureTime(double s, double m) {
        double[] r = rupture(m);
        if (s < r[0] - 1e-6 || s > r[1] + 1e-6) return Double.POSITIVE_INFINITY;
        return Math.abs(s - hypoS()) / vr;
    }

    /** Joyner-Boore-Abstand (m): kürzester waagerechter Abstand zur Oberfläche des gebrochenen Teils. */
    public double rjb(double px, double pz, double m) {
        double[] r = rupture(m);
        double best = Double.MAX_VALUE;
        int steps = Math.max(2, (int) ((r[1] - r[0]) / 200));
        double[] prev = point(r[0]);
        for (int k = 1; k <= steps; k++) {
            double[] p = point(r[0] + (r[1] - r[0]) * k / steps);
            double ux = p[0] - prev[0], uz = p[1] - prev[1], l2 = ux * ux + uz * uz;
            double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - prev[0]) * ux + (pz - prev[1]) * uz) / l2));
            best = Math.min(best, Math.hypot(px - prev[0] - ux * t, pz - prev[1] - uz * t));
            prev = p;
        }
        return best;
    }

    /** Abstand zum Herd (m), mit Tiefe. */
    public double hypoDistance(double px, double pz) { return Math.sqrt((px - hx) * (px - hx) + (pz - hz) * (pz - hz) + hdepth * hdepth); }
}
