package com.dan.gertsch;

/**
 * Tonwerte nach der Vorlage, für Prozessor und Grafikkarte ({@code gl/gertsch.glsl}):
 * <ul>
 * <li>{@link #anteile}: wie viel Fläche jede Tonstufe bekommt, wie in der Vorlage. Stufe 0 bekommt den Anteil des
 * dunklen Grunds, wenn nur sie unter L* 20 liegt (gemalt ist jede Stufe flach; bei Gertsch reicht auch ein Teil der
 * zweiten Stufe unter 20).</li>
 * <li>{@link #grenzen}: die Grenzen zwischen den Bändern in log2 der Helligkeit Y aus einem Histogramm des Bilds, so
 * dass jedes Band seinen Anteil der Bildpunkte bekommt.</li>
 * <li>{@link #band}: das Band einer Helligkeit, mit <b>Hysterese</b>: Ein Fleck behält sein bisheriges Band, solange
 * seine Helligkeit nicht weiter als {@code h} Blenden über dessen Grenzen hinaus geht. Im Wind wandert sonst jedes
 * Blatt an einer Grenze bei jedem Flattern zwischen zwei Stufen hin und her.</li>
 * </ul>
 */
public final class Stufen {
    private Stufen() { }

    /** Flächenanteile je Tonstufe der Vorlage (aufsteigend nach L*), Summe 1. */
    public static double[] anteile(Vorlage v) {
        Palette p = v.palette;
        double[] a = p.anteil.clone();
        if (p.size() > 1 && p.lab[0][0] < 20 && p.lab[1][0] >= 20 && v.grund > a[0]) {
            double rest = 1 - a[0], skala = (1 - v.grund) / Math.max(1e-9, rest);
            a[0] = v.grund;
            for (int i = 1; i < a.length; i++) a[i] *= skala;
        }
        return a;
    }

    /**
     * Grenzen in log2 Y (Länge k − 1) aus einem Histogramm: {@code nullen} Bildpunkte ohne Licht (Grund), dann
     * {@code fach.length} Fächer gleich breit in log2 Y von {@code lo} bis {@code hi}. Innerhalb eines Fachs linear.
     */
    public static double[] grenzen(long[] fach, long nullen, double lo, double hi, double[] anteile) {
        int k = anteile.length, n = fach.length;
        long summe = nullen;
        for (long f : fach) summe += f;
        double[] g = new double[Math.max(0, k - 1)];
        if (summe == 0) {
            for (int j = 0; j < g.length; j++) g[j] = lo + (hi - lo) * (j + 1) / k;
            return g;
        }
        double ziel = 0, breite = (hi - lo) / n;
        int fi = 0;
        double unter = nullen;   // Bildpunkte unter dem Anfang von Fach fi
        for (int j = 0; j < k - 1; j++) {
            ziel += anteile[j] * summe;
            if (ziel <= nullen) { g[j] = lo; continue; }
            while (fi < n && unter + fach[fi] < ziel) unter += fach[fi++];
            if (fi >= n) { g[j] = hi; continue; }
            double t = fach[fi] == 0 ? 0 : (ziel - unter) / fach[fi];
            g[j] = lo + breite * (fi + t);
        }
        return g;
    }

    /** Band 0..k−1 der Helligkeit (log2 Y) ohne Hysterese. */
    public static int band(double log2Y, double[] grenzen) {
        int b = 0;
        for (double g : grenzen) if (log2Y >= g) b++;
        return b;
    }

    /** Band mit Hysterese: das bisherige ({@code vorher}, −1 = keins), solange log2 Y höchstens h Blenden außerhalb liegt. */
    public static int band(double log2Y, double[] grenzen, int vorher, double h) {
        int roh = band(log2Y, grenzen);
        if (vorher < 0 || vorher > grenzen.length) return roh;
        double lo = vorher == 0 ? Double.NEGATIVE_INFINITY : grenzen[vorher - 1] - h;
        double hi = vorher == grenzen.length ? Double.POSITIVE_INFINITY : grenzen[vorher] + h;
        return log2Y >= lo && log2Y < hi ? vorher : roh;
    }
}
