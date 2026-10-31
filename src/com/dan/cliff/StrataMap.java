package com.dan.cliff;

import java.util.Locale;

/**
 * Die Schichtkarte für ein Kliff aus Lockergestein (seit Sylt Phase 4): je Zelle eines Rasters über dem Höhenfeld vier
 * Werte, als RGBA16F auf die Grafikkarte:
 * <ol>
 * <li><b>Oberkante</b> (m): die höchste Stelle des Geländes im Umkreis von {@link #reach} m, also die Kante über der
 * Wand; von ihr aus zählen die Deckschichten (Grasnarbe, Flugsand, Boden, Steinsohle) nach unten;</li>
 * <li><b>Untere Grenze</b> (m): Oberfläche der unteren Schicht (am Roten Kliff der Kaolinsand), absolut; darunter
 * {@link Cliff#kaolin}, darüber {@link Cliff#lehm}. Weit unten (−1000), wo es sie nicht gibt;</li>
 * <li><b>Decke</b> (m): Mächtigkeit des Flugsands unter der Grasnarbe;</li>
 * <li><b>Hohlkehle</b> (m): wie tief die Brandung den Fuß ausgewaschen hat ({@link #paintNotch}); kommt von außen
 * (in Sylt aus {@code surf} in Phase 12), die Wand weicht im Band {@link Cliff#notchBase} bis
 * {@code notchBase + notchHeight} um so viel zurück.</li>
 * </ol>
 * Die Werte liegen als halbe Gleitkommazahlen vor (16 Bit, wie auf der Grafikkarte); {@link #valuesAt} liest sie
 * bilinear wie die Grafikkarte. Das Raster darf rechteckig sein ({@link #nx} × {@link #nz}).
 */
public final class StrataMap {
    /** Ein Feld über der Fläche (m), etwa die untere Grenze oder die Decke je Ort. */
    public interface Field {
        double at(double x, double z);
    }

    public final int nx, nz;
    public final double x0, z0, cell;
    /** Je Zelle vier halbe Gleitkommazahlen (Float.floatToFloat16), Zeile für Zeile von z0 an: Oberkante, untere Grenze, Decke, Hohlkehle. */
    public final short[] rgba;
    /** Umkreis, in dem die Oberkante gesucht wird (m). */
    public final double reach;
    /** Kennwerte nach dem Bau: höchste Oberkante, Spanne der unteren Grenze, mittlere Decke, tiefste Hohlkehle. */
    public double topMax, lowerMin = Double.NaN, lowerMax = Double.NaN, coverMean, notchMax;
    /** Zähler: geändert seit dem letzten Hochladen (CliffGl lädt neu, wenn sich die Zahl ändert). */
    public volatile int version;

    private StrataMap(int nx, int nz, double x0, double z0, double cell, double reach) {
        this.nx = nx;
        this.nz = nz;
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.reach = reach;
        this.rgba = new short[nx * nz * 4];
    }

    public double cx(int i) { return x0 + (i + 0.5) * cell; }
    public double cz(int j) { return z0 + (j + 0.5) * cell; }

    /**
     * Baut die Karte über nx × nz Zellen ab (x0, z0) mit Zellgröße cell: Oberkante als höchste Höhe im Umkreis reach,
     * untere Grenze und Decke aus den Feldern (null: keine untere Schicht, Decke {@code coverDefault}).
     */
    public static StrataMap build(Cliff.Heights h, double x0, double z0, double cell, int nx, int nz, double reach,
                                  Field lower, Field cover, double coverDefault) {
        StrataMap m = new StrataMap(nx, nz, x0, z0, cell, reach);
        float[] hh = new float[nx * nz];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) hh[j * nx + i] = (float) h.height(m.cx(i), m.cz(j));
        int r = Math.max(1, (int) Math.round(reach / cell));
        float[] top = maxFilter(hh, nx, nz, r);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, cs = 0, tm = -1e9;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                double x = m.cx(i), z = m.cz(j);
                double lw = lower != null ? lower.at(x, z) : -1000, cv = cover != null ? cover.at(x, z) : coverDefault;
                m.set(k, 0, top[k]);
                m.set(k, 1, lw);
                m.set(k, 2, Math.max(0, cv));
                m.set(k, 3, 0);
                tm = Math.max(tm, top[k]);
                if (lw > -999) { lo = Math.min(lo, lw); hi = Math.max(hi, lw); }
                cs += cv;
            }
        m.topMax = tm;
        if (lo <= hi) { m.lowerMin = lo; m.lowerMax = hi; }
        m.coverMean = cs / (nx * nz);
        return m;
    }

    private void set(int k, int c, double v) { rgba[k * 4 + c] = Float.floatToFloat16((float) v); }

    private double get(int i, int j, int c) {
        i = Math.max(0, Math.min(nx - 1, i));
        j = Math.max(0, Math.min(nz - 1, j));
        return Float.float16ToFloat(rgba[(j * nx + i) * 4 + c]);
    }

    /**
     * Hohlkehle eintragen: ein runder Fleck um (x, z) mit Breite width m, Tiefe depth m (die tiefste gewinnt), mit
     * weichem Rand. Für den Fuß einer Strecke Kliff mehrmals entlang der Linie aufrufen.
     */
    public void paintNotch(double x, double z, double width, double depth) {
        int rr = (int) Math.ceil(width / cell) + 1;
        int ci = (int) Math.floor((x - x0) / cell), cj = (int) Math.floor((z - z0) / cell);
        for (int j = cj - rr; j <= cj + rr; j++)
            for (int i = ci - rr; i <= ci + rr; i++) {
                if (i < 0 || j < 0 || i >= nx || j >= nz) continue;
                double d = Math.hypot(cx(i) - x, cz(j) - z);
                double w = depth * (1 - Cliff.smooth(width * 0.4, width, d));
                int k = j * nx + i;
                if (w > Float.float16ToFloat(rgba[k * 4 + 3])) set(k, 3, w);
            }
        notchMax = Math.max(notchMax, depth);
        version++;
    }

    /**
     * Seit Sylt Phase 12: die Hohlkehle in einem Rechteck löschen (x0..x1, z0..z1), etwa wo das Kliff abgebrochen ist und
     * die neue Wand frisch steht.
     */
    public void clearNotch(double ax, double az, double bx, double bz) {
        int i0 = Math.max(0, (int) Math.floor((Math.min(ax, bx) - x0) / cell)), i1 = Math.min(nx - 1, (int) Math.floor((Math.max(ax, bx) - x0) / cell));
        int j0 = Math.max(0, (int) Math.floor((Math.min(az, bz) - z0) / cell)), j1 = Math.min(nz - 1, (int) Math.floor((Math.max(az, bz) - z0) / cell));
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) rgba[(j * nx + i) * 4 + 3] = 0;
        version++;
    }

    /** Alle Hohlkehlen löschen. */
    public void clearNotch() {
        for (int k = 0; k < nx * nz; k++) rgba[k * 4 + 3] = 0;
        notchMax = 0;
        version++;
    }

    /** Die vier Werte am Ort (bilinear über die Zellmitten, wie die Grafikkarte); außerhalb der Rand. */
    public double[] valuesAt(double x, double z) {
        double u = (x - x0) / cell - 0.5, v = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double[] r = new double[4];
        for (int c = 0; c < 4; c++)
            r[c] = (get(i, j, c) * (1 - fx) + get(i + 1, j, c) * fx) * (1 - fz) + (get(i, j + 1, c) * (1 - fx) + get(i + 1, j + 1, c) * fx) * fz;
        return r;
    }

    /** Ob (x, z) auf der Karte liegt. */
    public boolean covers(double x, double z) { return x >= x0 && z >= z0 && x <= x0 + nx * cell && z <= z0 + nz * cell; }

    /** „Schichtkarte 600 × 3700 zu 1 m · Oberkante bis 31,2 m · Kaolinsand oben 2,0 bis 9,5 m · Decke 1,6 m · Hohlkehle bis 0,0 m“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Schichtkarte %d × %d zu %.1f m · Oberkante bis %.1f m · %s · Decke im Mittel %.1f m · Hohlkehle bis %.1f m",
                nx, nz, cell, topMax, Double.isNaN(lowerMin) ? "ohne untere Schicht" : String.format(Locale.GERMAN, "untere Grenze %.1f bis %.1f m", lowerMin, lowerMax),
                coverMean, notchMax);
    }

    /** Größter Wert im Rechteck (2r+1)², getrennt nach Zeilen und Spalten. */
    static float[] maxFilter(float[] a, int nx, int nz, int r) {
        float[] tmp = new float[nx * nz], out = new float[nx * nz];
        float[] line = new float[Math.max(nx, nz)], res = new float[Math.max(nx, nz)];
        int[] q = new int[Math.max(nx, nz)];
        for (int j = 0; j < nz; j++) {
            System.arraycopy(a, j * nx, line, 0, nx);
            slide(line, res, nx, r, q);
            System.arraycopy(res, 0, tmp, j * nx, nx);
        }
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) line[j] = tmp[j * nx + i];
            slide(line, res, nz, r, q);
            for (int j = 0; j < nz; j++) out[j * nx + i] = res[j];
        }
        return out;
    }

    private static void slide(float[] a, float[] out, int n, int r, int[] q) {
        int head = 0, tail = 0, next = 0;
        for (int i = 0; i < n; i++) {
            int hi = Math.min(n - 1, i + r);
            while (next <= hi) {
                while (tail > head && a[q[tail - 1]] <= a[next]) tail--;
                q[tail++] = next++;
            }
            while (q[head] < i - r) head++;
            out[i] = a[q[head]];
        }
    }
}
