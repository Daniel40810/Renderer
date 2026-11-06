package com.dan.shore;

import java.util.Arrays;
import java.util.Locale;

/**
 * Böschung an der Naht zwischen Land und Grund. Wo ein Höhenmodell aus einem Landmodell (Lidar, IfSAR) und einem Modell
 * des Grunds (Lotungen, BlueTopo) gemischt ist, liegt an der Wasserlinie oft eine Stufe: Das Landmodell endet an der
 * Wasserfläche (hydroflattened), das Grundmodell ist dort grob und tiefer, und die Zone dazwischen fehlt in beiden. Im Bild
 * steht dann eine senkrechte Wand von einigen Metern mit gerader Oberkante (LituyaBay1958 W6 Runde 8: 3,0 m Land neben
 * −2,2 m Grund, über 54 km Ufer im Kern).
 * <p>
 * Die Rechnung hebt den Grund vor der Stufe auf eine Böschung, die an der Landzelle beginnt: erst flach wie ein Strand
 * ({@link #beachSlope} bis {@link #beachFoot} m), dann steiler wie ein Unterwasserhang ({@link #foreSlope}), bis sie den
 * alten Grund trifft. Unter {@link #floor} m bleibt alles, wie es ist. Quellen sind Landzellen bis {@link #topMax} m mit
 * einem Nachbarn im Grund, der mehr als {@link #stepMin} m tiefer liegt; hohe Felsufer bekommen also keinen Strand.
 * Land wird nie verändert, Grund nur gehoben.
 * <p>
 * Der Abstand ist ein Chamfer-Abstand mit 5 × 5-Maske (1, √2, √5; höchstens 2 % von der Luftlinie), nur durch Grund
 * hindurch. Für Kacheln: mit einem Rand von {@link #reach()} m rechnen, dann ist das Ergebnis in der Mitte dasselbe wie im
 * ganzen Raster. Ein zweiter Durchgang ändert nichts mehr (keine Stufe übrig).
 */
public final class ShoreRamp {
    /** Klassen je Zelle: Land, Grund; alles andere (etwa −1) nimmt nicht teil. */
    public static final byte LAND = 0, BED = 1;

    /** Kleinste Stufe, die eine Böschung bekommt (m). */
    public double stepMin = 1.0;
    /** Nur Land bis zu dieser Höhe (m) ist Quelle; darüber Felsufer ohne Strand. */
    public double topMax = 4.0;
    /** Neigung des Strands (Höhe je Meter) bis zum Fuß. */
    public double beachSlope = 0.10;
    /** Fuß des Strands (m, im Bezug des Rasters). */
    public double beachFoot = -2.0;
    /** Neigung unter dem Fuß. */
    public double foreSlope = 0.5;
    /** Darunter wird nicht mehr gehoben (m). */
    public double floor = -40;

    /** Ergebnis: Quellen (Landzellen an einer Stufe), gehobene Zellen, größte Hebung (m), Durchgänge. */
    public record Result(int sources, int raised, double maxRaise, int passes) {
        @Override public String toString() {
            return String.format(Locale.GERMAN, "%d Quellen, %d Zellen gehoben (bis %.2f m), %d Durchgänge", sources, raised, maxRaise, passes);
        }
    }

    /** Größte Länge der Böschung (m): von topMax bis floor. Mindestens so breit muss der Rand einer Kachel sein. */
    public double reach() {
        return Math.max(0, topMax - beachFoot) / beachSlope + Math.max(0, beachFoot - floor) / foreSlope;
    }

    /** Höhe der Böschung nach d Metern, wenn sie bei v beginnt. */
    public double fall(double v, double d) {
        if (v > beachFoot) {
            double d1 = (v - beachFoot) / beachSlope;
            if (d <= d1) return v - beachSlope * d;
            d -= d1;
            v = beachFoot;
        }
        return v - foreSlope * d;
    }

    // 5 × 5-Chamfer: vorwärts (Zeile darüber, links daneben), rückwärts gespiegelt
    private static final int[][] VOR = {{-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {-2, -1}, {-1, -2}, {1, -2}, {2, -1}};

    /**
     * Hebt den Grund vor jeder Stufe. h: Höhen (Zeilen nacheinander, w je Zeile, hh Zeilen), cls: {@link #LAND},
     * {@link #BED} oder anderes je Zelle, cell: Zellweite (m). h wird überschrieben.
     */
    public Result apply(float[] h, byte[] cls, int w, int hh, double cell) {
        int n = w * hh;
        if (h.length < n || cls.length < n) throw new IllegalArgumentException("Raster kleiner als " + w + " × " + hh);
        float[] p = new float[n];
        boolean[] src = new boolean[n];
        int sources = 0;
        for (int j = 0; j < hh; j++)
            for (int i = 0; i < w; i++) {
                int k = j * w + i;
                if (cls[k] != LAND || Float.isNaN(h[k]) || h[k] > topMax) continue;
                float t = h[k];
                boolean s = false;
                if (i > 0) s |= stufe(h, cls, k - 1, t);
                if (i < w - 1) s |= stufe(h, cls, k + 1, t);
                if (j > 0) s |= stufe(h, cls, k - w, t);
                if (j < hh - 1) s |= stufe(h, cls, k + w, t);
                if (s) { src[k] = true; sources++; }
            }
        if (sources == 0) return new Result(0, 0, 0, 0);
        Arrays.fill(p, Float.NEGATIVE_INFINITY);
        for (int k = 0; k < n; k++) if (src[k]) p[k] = h[k];
        double[] wv = new double[VOR.length];
        for (int m = 0; m < VOR.length; m++) wv[m] = Math.hypot(VOR[m][0], VOR[m][1]) * cell;
        float lo = (float) floor;
        int passes = 0;
        boolean changed = true;
        while (changed && passes < 12) {
            changed = false;
            passes++;
            for (int j = 0; j < hh; j++)
                for (int i = 0; i < w; i++) changed |= relax(h, cls, p, src, w, hh, i, j, 1, wv, lo);
            for (int j = hh - 1; j >= 0; j--)
                for (int i = w - 1; i >= 0; i--) changed |= relax(h, cls, p, src, w, hh, i, j, -1, wv, lo);
        }
        int raised = 0;
        double max = 0;
        for (int k = 0; k < n; k++) {
            if (cls[k] != BED || Float.isNaN(h[k]) || p[k] < lo || p[k] <= h[k]) continue;
            max = Math.max(max, p[k] - h[k]);
            h[k] = p[k];
            raised++;
        }
        return new Result(sources, raised, max, passes);
    }

    private boolean stufe(float[] h, byte[] cls, int k, float t) {
        return cls[k] == BED && !Float.isNaN(h[k]) && t - h[k] > stepMin;
    }

    private boolean relax(float[] h, byte[] cls, float[] p, boolean[] src, int w, int hh, int i, int j, int dir, double[] wv, float lo) {
        int k = j * w + i;
        if (cls[k] != BED || Float.isNaN(h[k])) return false;
        float best = p[k];
        for (int m = 0; m < VOR.length; m++) {
            int x = i + dir * VOR[m][0], y = j + dir * VOR[m][1];
            if (x < 0 || y < 0 || x >= w || y >= hh) continue;
            int q = y * w + x;
            if (p[q] == Float.NEGATIVE_INFINITY || !(src[q] || cls[q] == BED)) continue;
            float c = (float) fall(p[q], wv[m]);
            if (c > best) best = c;
        }
        if (best > p[k] && best >= lo - 1e-3f) { p[k] = best; return true; }
        return false;
    }
}
