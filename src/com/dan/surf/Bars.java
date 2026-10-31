package com.dan.surf;

import java.util.Locale;
import java.util.Random;

/**
 * Riffe und Rinnen (seit Sylt Phase 5, F10 A): Sandbänke vor einem Strand, die das grobe Raster des Grunds (BAW, 10 m)
 * nicht kennt, als Höhe, die zum Grund dazukommt [G nach dem typischen Bild an der Westküste von Sylt: ein inneres Riff
 * rund 150 m und ein äußeres rund 450 m vor der Wasserlinie, in Bögen (Riffbögen), alle paar hundert Meter von Rinnen
 * durchbrochen, durch die das Wasser zurückströmt].
 * <ul>
 * <li>Je Riff ein Rücken mit Höhe und Breite; landseitig eine Senke (Trog), etwa halb so tief wie der Rücken hoch.</li>
 * <li>Die Lage des Kamms schwingt längs der Küste um {@link #crescent} m (Riffbögen), die Hörner zum Strand.</li>
 * <li>Rinnen alle {@link #ripSpacing} m (mit Zufall), {@link #ripWidth} m breit: dort fehlt das Riff, und der Grund
 * liegt {@link #ripDepth} m tiefer.</li>
 * <li>{@link #storm} verlagert Bögen und Rinnen (nach Stürmen, F10 A), gleicher Startwert, gleiche Lage.</li>
 * </ul>
 * Die Bänke liegen in Küstenkoordinaten: Abstand von der Wasserlinie seewärts (u) und Lage längs der Küste (v), die
 * {@link Coast} aus der Welt rechnet.
 */
public final class Bars {
    /** Küstenkoordinaten am Ort: Abstand seewärts von der Wasserlinie (m) und Lage längs der Küste (m). */
    public interface Coast {
        double offshore(double x, double z);
        double along(double x, double z);

        /** Gerade Küste: Wasserlinie durch (x0, z0), Blick zur See unter seawardDeg (Grad von Nord). */
        static Coast straight(double x0, double z0, double seawardDeg) {
            double a = Math.toRadians(seawardDeg), sx = Math.sin(a), sz = -Math.cos(a);
            return new Coast() {
                public double offshore(double x, double z) { return (x - x0) * sx + (z - z0) * sz; }
                public double along(double x, double z) { return (x - x0) * (-sz) + (z - z0) * sx; }
            };
        }
    }

    /** Kammlage seewärts (m), Höhe über dem Grund ohne Riff (m), Breite (m, eine Standardabweichung) je Riff. */
    public double[] crest = {150, 450}, height = {1.0, 1.4}, width = {35, 70};
    /** Riffbögen: Ausschlag der Kammlage (m); Rinnen: Abstand (m), Streuung des Abstands (Anteil), Breite (m), Tiefe (m). */
    public double crescent = 25, ripSpacing = 380, ripJitter = 0.3, ripWidth = 40, ripDepth = 1.0;
    /** Tiefe des Trogs landseits jedes Riffs (Anteil der Riffhöhe). */
    public double trough = 0.45;
    public long seed = 1;
    private double[] ripAt = new double[0];
    private double phase;
    /** Wie oft die Bänke nach Stürmen verlagert wurden. */
    public int storms;

    public Bars() { layout(); }

    /** Riffe und Rinnen vor dem Sylter Weststrand [G]. */
    public static Bars sylt() { return new Bars(); }

    /** Lage der Rinnen neu aus dem Startwert (längs der Küste von −20 km bis +20 km). */
    private void layout() {
        Random r = new Random(seed * 7919 + storms);
        phase = r.nextDouble() * ripSpacing;
        int n = (int) Math.ceil(40_000 / ripSpacing) + 2;
        ripAt = new double[n];
        double v = -20_000 + phase;
        for (int i = 0; i < n; i++) {
            ripAt[i] = v;
            v += ripSpacing * (1 + ripJitter * (r.nextDouble() * 2 - 1));
        }
    }

    /** Nach einem Sturm: Bögen und Rinnen verlagern sich (neue Lage aus Startwert und Zahl der Stürme). */
    public void storm() {
        storms++;
        layout();
    }

    /** Abstand von v zur nächsten Rinne (m). */
    public double ripDistance(double v) {
        int lo = 0, hi = ripAt.length - 1;
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (ripAt[m] < v) lo = m + 1; else hi = m;
        }
        double d = Double.MAX_VALUE;
        for (int k = Math.max(0, lo - 1); k <= Math.min(ripAt.length - 1, lo); k++) d = Math.min(d, Math.abs(v - ripAt[k]));
        return d;
    }

    /** Wie stark am Ort v eine Rinne das Riff unterbricht, 0..1. */
    public double ripAt(double v) {
        int lo = 0, hi = ripAt.length - 1;
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (ripAt[m] < v) lo = m + 1; else hi = m;
        }
        double d = Double.MAX_VALUE;
        for (int k = Math.max(0, lo - 1); k <= Math.min(ripAt.length - 1, lo); k++) d = Math.min(d, Math.abs(v - ripAt[k]));
        return Math.exp(-0.5 * (d / (ripWidth * 0.5)) * (d / (ripWidth * 0.5)));
    }

    /** Höhe (m), die zum Grund dazukommt, in Küstenkoordinaten u (seewärts), v (längs). */
    public double offset(double u, double v) {
        if (u < -20) return 0;
        double rip = ripAt(v), dz = 0;
        for (int k = 0; k < crest.length; k++) {
            // Riffbögen: an den Rinnen liegt der Kamm am weitesten draußen, dazwischen biegen die Hörner zum Strand
            double c = crest[k] + crescent * Math.cos(Math.PI * Math.min(1, ripDistance(v) / (0.5 * ripSpacing))) * (k == 0 ? 1 : 1.6);
            double w = width[k], a = (u - c) / w, t = (u - (c - 1.6 * w)) / (0.8 * w);
            dz += height[k] * Math.exp(-0.5 * a * a) - trough * height[k] * Math.exp(-0.5 * t * t);
        }
        // in der Rinne fehlt das Riff, und der Grund liegt tiefer (zwischen Strand und äußerem Riff)
        double span = smooth(10, 60, u) * (1 - smooth(crest[crest.length - 1] + width[width.length - 1], crest[crest.length - 1] + 3 * width[width.length - 1], u));
        return dz * (1 - rip) - ripDepth * rip * span;
    }

    /** Ein Grund mit Riffen und Rinnen: base plus {@link #offset} in den Küstenkoordinaten von coast. */
    public Surf.Bed apply(Surf.Bed base, Coast coast) {
        return (x, z) -> base.height(x, z) + offset(coast.offshore(x, z), coast.along(x, z));
    }

    /** Lage der Rinnen zwischen v0 und v1 (für Prüfung und HUD). */
    public double[] ripsBetween(double v0, double v1) {
        return java.util.Arrays.stream(ripAt).filter(v -> v >= v0 && v <= v1).toArray();
    }

    /** „Riffe bei 150 und 450 m (1,0 und 1,4 m hoch) · Rinnen alle 380 m, 40 m breit, 1,0 m tief · 0 Stürme“ */
    public String describe() {
        StringBuilder c = new StringBuilder(), h = new StringBuilder();
        for (int k = 0; k < crest.length; k++) {
            if (k > 0) { c.append(" und "); h.append(" und "); }
            c.append(String.format(Locale.GERMAN, "%.0f", crest[k]));
            h.append(String.format(Locale.GERMAN, "%.1f", height[k]));
        }
        return String.format(Locale.GERMAN, "Riffe bei %s m (%s m hoch) · Rinnen alle %.0f m, %.0f m breit, %.1f m tief · %d Stürme",
                c, h, ripSpacing, ripWidth, ripDepth, storms);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
