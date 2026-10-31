package com.dan.cliff;

import java.util.Locale;

/**
 * Die Wandkarte: je Zelle eines Rasters über dem Höhenfeld vier Werte 0..1 (als RGBA8 auf die Grafikkarte):
 * <ol>
 * <li><b>Wand</b>: weich um {@link Cliff#wallSlopeDeg};</li>
 * <li><b>Geröll</b>: flachere Zellen (5° bis 40°) unterhalb einer Wand, die mindestens {@link #talusDrop} m über ihnen
 * steht, im Umkreis von {@link #talusReach} m;</li>
 * <li><b>Nässe</b>: Streifen unter Fällen ({@link #addFall}): vom Fuß der Wand bergauf entlang der Falllinie bis zur
 * Abrisskante, so breit wie der Fall, unten breiter (Gischt);</li>
 * <li><b>Absatz</b>: flache Stellen in der Wand (weniger als 35° Neigung, Wand im Umkreis von zwei Zellen), wo Moos,
 * Gras und Schnee liegen bleiben.</li>
 * </ol>
 * Gerechnet auf dem Prozessor, einmal je Höhenfeld (für 2048² rund eine Sekunde); die Grafikkarte liest sie bilinear.
 */
public final class CliffMap {
    public final int n;
    public final double x0, z0, cell;
    /** Werte je Zelle, Zeile für Zeile von Nord (z0) nach Süd: Wand, Geröll, Nässe, Absatz. */
    public final byte[] rgba;
    /** Geröll: Mindesthöhe der Wand über der Zelle (m) und Umkreis (m). */
    public double talusDrop = 25, talusReach = 110;
    private final float[] h;
    private final Cliff cliff;
    /** Kennwerte nach dem Bau: Anteile Wand, Geröll, Absatz; Zahl der Fälle. */
    public double wallShare, talusShare, ledgeShare;
    public int falls;

    private CliffMap(Cliff c, Cliff.Heights hs, double x0, double z0, double cell, int n) {
        this.cliff = c;
        this.n = n;
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.rgba = new byte[n * n * 4];
        this.h = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) h[j * n + i] = (float) hs.height(cx(i), cz(j));
    }

    public double cx(int i) { return x0 + (i + 0.5) * cell; }
    public double cz(int j) { return z0 + (j + 0.5) * cell; }

    /** Baut die Karte über n × n Zellen ab (x0, z0) mit Zellgröße cell. */
    public static CliffMap build(Cliff c, Cliff.Heights hs, double x0, double z0, double cell, int n) {
        CliffMap m = new CliffMap(c, hs, x0, z0, cell, n);
        m.compute();
        return m;
    }

    /**
     * Wie oben, mit eigenem Geröll (seit Sylt Phase 4): Mindesthöhe der Wand (m) und Umkreis (m); unter einem Kliff aus
     * Lockergestein liegen Sturzkegel schon unter Wänden von wenigen Metern, dicht am Fuß.
     */
    public static CliffMap build(Cliff c, Cliff.Heights hs, double x0, double z0, double cell, int n, double talusDrop, double talusReach) {
        CliffMap m = new CliffMap(c, hs, x0, z0, cell, n);
        m.talusDrop = talusDrop;
        m.talusReach = talusReach;
        m.compute();
        return m;
    }

    private double at(int i, int j) {
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        return h[j * n + i];
    }

    double slopeDeg(int i, int j) {
        double gx = (at(i + 1, j) - at(i - 1, j)) / (2 * cell), gz = (at(i, j + 1) - at(i, j - 1)) / (2 * cell);
        return Math.toDegrees(Math.atan(Math.hypot(gx, gz)));
    }

    private void compute() {
        int nn = n * n;
        float[] slope = new float[nn], wall = new float[nn];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int k = j * n + i;
                slope[k] = (float) slopeDeg(i, j);
                wall[k] = (float) Cliff.smooth(cliff.wallSlopeDeg - 5, cliff.wallSlopeDeg + 5, slope[k]);
            }
        // Wand im Umkreis von zwei Zellen (für Absätze) und höchste Wand im Umkreis (für Geröll)
        float[] wallNear = maxFilter(wall, n, 2);
        float[] wallTop = new float[nn];
        for (int k = 0; k < nn; k++) wallTop[k] = wall[k] > 0.5f ? h[k] : -1e9f;
        int r = Math.max(1, (int) Math.round(talusReach / cell));
        wallTop = maxFilter(wallTop, n, r);
        double ws = 0, ts = 0, ls = 0;
        for (int k = 0; k < nn; k++) {
            double s = slope[k];
            double talus = Cliff.smooth(talusDrop, talusDrop * 2, wallTop[k] - h[k]) * Cliff.smooth(2, 8, s) * (1 - Cliff.smooth(38, 44, s)) * (1 - wall[k]);
            double ledge = wallNear[k] * (1 - Cliff.smooth(30, 40, s)) * (wall[k] < 0.5 ? 0.6 : 1);
            rgba[k * 4] = (byte) Math.round(wall[k] * 255);
            rgba[k * 4 + 1] = (byte) Math.round(talus * 255);
            rgba[k * 4 + 3] = (byte) Math.round(ledge * 255);
            ws += wall[k];
            ts += talus;
            ls += ledge;
        }
        wallShare = ws / nn;
        talusShare = ts / nn;
        ledgeShare = ls / nn;
    }

    /**
     * Ein Fall: vom Fuß (x, z) bergauf entlang der Falllinie, bis die Wand {@code height} m über dem Fuß ist; Breite
     * width m, am Fuß doppelt (Gischt). Nässe 0..1 wird mit {@code strength} eingetragen (Maximum).
     */
    public void addFall(double x, double z, double height, double width, double strength) {
        double y0 = sample(x, z), px = x, pz = z, best = y0, dx = 0, dz = 0;
        double step = cell * 0.5, e = Math.max(2 * cell, 30);
        int still = 0;
        for (int s = 0; s < 8000 && still < 400; s++) {
            double y = sample(px, pz);
            double up = (y - y0) / Math.max(1, height);
            if (up >= 1) break;
            if (y > best + 0.5) { best = y; still = 0; } else still++;
            double wdt = width * (1 + Math.max(0, 1 - up * 4));
            paint(px, pz, wdt, strength * (0.7 + 0.3 * Math.min(1, up * 3)));
            // bergauf: die höchste von 16 Richtungen in e m (auf Böden mit kleinen Buckeln die Wand, nicht der Buckel)
            double gx = 0, gz = 0, top = -1e9;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8, hx = Math.cos(a), hz = Math.sin(a), v = sample(px + hx * e, pz + hz * e);
                if (v > top) { top = v; gx = hx; gz = hz; }
            }
            dx = 0.6 * dx + 0.4 * gx;
            dz = 0.6 * dz + 0.4 * gz;
            double dl = Math.hypot(dx, dz);
            px += dx / dl * step;
            pz += dz / dl * step;
        }
        falls++;
    }

    /**
     * Nässe an einer Stelle malen (seit Geiranger Phase 7): ein runder Fleck mit weichem Rand, Breite in m, Stärke
     * 0..1; die stärkste Nässe gewinnt. Für die nasse Linie, die {@code com.dan.fall} liefert (die Wandkarte kennt den
     * Fall nicht, nur die Punkte).
     */
    public void paintWet(double x, double z, double width, double strength) { paint(x, z, Math.max(cell, width), strength); }

    private void paint(double x, double z, double width, double v) {
        int r = (int) Math.ceil(width / cell) + 1;
        int ci = (int) Math.floor((x - x0) / cell), cj = (int) Math.floor((z - z0) / cell);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                if (i < 0 || j < 0 || i >= n || j >= n) continue;
                double d = Math.hypot(cx(i) - x, cz(j) - z);
                double w = v * (1 - Cliff.smooth(width * 0.4, width, d));
                int k = (j * n + i) * 4 + 2;
                int old = rgba[k] & 255, nv = (int) Math.round(Math.min(1, w) * 255);
                if (nv > old) rgba[k] = (byte) nv;
            }
    }

    /** Höhe bilinear aus den gespeicherten Werten. */
    public double sample(double x, double z) {
        double u = (x - x0) / cell - 0.5, v = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        return (at(i, j) * (1 - fx) + at(i + 1, j) * fx) * (1 - fz) + (at(i, j + 1) * (1 - fx) + at(i + 1, j + 1) * fx) * fz;
    }

    /** Die vier Werte 0..1 am Ort (bilinear, wie die Grafikkarte). */
    public double[] valuesAt(double x, double z) {
        double u = (x - x0) / cell - 0.5, v = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double[] r = new double[4];
        for (int c = 0; c < 4; c++)
            r[c] = (val(i, j, c) * (1 - fx) + val(i + 1, j, c) * fx) * (1 - fz) + (val(i, j + 1, c) * (1 - fx) + val(i + 1, j + 1, c) * fx) * fz;
        return r;
    }

    private double val(int i, int j, int c) {
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        return (rgba[(j * n + i) * 4 + c] & 255) / 255.0;
    }

    /** „2048² zu 12 m · Wand 4,1 %, Geröll 2,3 %, Absatz 1,8 % · 3 Fälle“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Wandkarte %d² zu %.0f m · Wand %.1f %%, Geröll %.1f %%, Absatz %.1f %% · %d Fälle", n, cell,
                wallShare * 100, talusShare * 100, ledgeShare * 100, falls);
    }

    /** Größter Wert im Quadrat (2r+1)², getrennt nach Zeilen und Spalten (gleitendes Maximum). */
    static float[] maxFilter(float[] a, int n, int r) {
        float[] tmp = new float[n * n], out = new float[n * n], line = new float[n], res = new float[n];
        int[] q = new int[n];
        for (int j = 0; j < n; j++) {
            System.arraycopy(a, j * n, line, 0, n);
            slide(line, res, n, r, q);
            System.arraycopy(res, 0, tmp, j * n, n);
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) line[j] = tmp[j * n + i];
            slide(line, res, n, r, q);
            for (int j = 0; j < n; j++) out[j * n + i] = res[j];
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
