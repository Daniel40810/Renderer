package com.dan.shutter;

/**
 * Die Eintrittspupille als Fläche, über die die Kino-Stufe Teilbilder verteilt: Jedes Teilbild sieht die Szene von einem
 * anderen Punkt der Pupille, alle mit derselben Schärfenebene. Was in dieser Ebene liegt, fällt in allen Teilbildern auf
 * denselben Bildpunkt; was davor oder dahinter liegt, wandert und wird zum Bild der Blende – so entsteht die Tiefenschärfe
 * für alles, auch für Gischt, Nebel, Staub und Regen, die keine eigene Tiefe haben.
 * <p>Form wie eine Irisblende: n Lamellen (unter 3: Kreis), Rundung 0 (Vieleck) bis 1 (Kreis), Drehung; dazu die Stauchung
 * eines anamorphotischen Objektivs (die Pupille wird im entzerrten Bild so viel höher, das Bokeh oval). Maß: Die Fläche ist
 * π (ein Kreis gleicher Fläche hat den Halbmesser 1, so belichtet jede Form wie ein Kreis derselben Blendenzahl); mit der
 * Stauchung wächst sie mit. Der Ort in Metern ist u · D/2, D = f/N.
 * <p>Gleichmäßig über die Fläche: der Winkel nach ∫ r(φ)² dφ (tabelliert), der Halbmesser r(φ) · √u (Fläche ∝ r²).
 */
public final class Pupil {
    public final int blades;
    public final double roundness, rotationDeg, squeeze;
    private final double scale;          // Umkreis → Fläche π
    private final double[] cdf, phis;    // ∫ r² dφ, normiert

    public Pupil(int blades, double roundness, double rotationDeg, double squeeze) {
        this.blades = blades;
        this.roundness = Math.max(0, Math.min(1, roundness));
        this.rotationDeg = rotationDeg;
        this.squeeze = Math.max(1e-3, squeeze);
        int n = 1024;
        cdf = new double[n + 1];
        phis = new double[n + 1];
        double s = 0;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n, r = radius((a0 + a1) / 2);
            phis[i] = a0;
            cdf[i] = s;
            s += r * r * (a1 - a0) / 2;
        }
        phis[n] = 2 * Math.PI;
        cdf[n] = s;
        for (int i = 0; i <= n; i++) cdf[i] /= s;
        scale = Math.sqrt(Math.PI / s);  // Fläche s (Umkreis 1) → π
    }

    /** Kreisrund, ohne Stauchung. */
    public static Pupil round() { return new Pupil(0, 1, 0, 1); }

    /** Rand in Richtung φ (rad), Umkreis 1 (vor der Stauchung). */
    public double radius(double phi) {
        if (blades < 3) return 1;
        double seg = 2 * Math.PI / blades;
        double a = phi - Math.toRadians(rotationDeg);
        a = a - seg * Math.floor(a / seg);
        double rn = Math.cos(Math.PI / blades) / Math.cos(a - Math.PI / blades);
        return rn + roundness * (1 - rn);
    }

    /** Fläche durch π vor dem Maßstab (Kreis 1, gerades Sechseck 0,827). */
    public double areaFactor() { return Math.PI / (scale * scale) / Math.PI; }

    /** Maßstab: Umkreis 1 → Fläche π. */
    public double scale() { return scale; }

    /** Ein Punkt der Pupille aus zwei gleichverteilten Zahlen u1, u2 ∈ [0, 1): {x, y} (Fläche π, y mit der Stauchung). */
    public double[] sample(double u1, double u2) {
        int lo = 0, hi = cdf.length - 1;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (cdf[m] <= u1) lo = m; else hi = m; }
        double f = (u1 - cdf[lo]) / Math.max(1e-12, cdf[hi] - cdf[lo]);
        double phi = phis[lo] + f * (phis[hi] - phis[lo]);
        double r = radius(phi) * Math.sqrt(u2) * scale;
        return new double[]{r * Math.cos(phi), r * Math.sin(phi) * squeeze};
    }

    /** Liegt (x, y) (Maß wie {@link #sample}) in der Pupille? */
    public boolean inside(double x, double y) {
        double yy = y / squeeze;
        double r = Math.hypot(x, yy) / scale;
        return r <= radius(Math.atan2(yy, x)) + 1e-9;
    }
}
