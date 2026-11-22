package com.dan.aufbereitung;

import java.util.Arrays;

/**
 * Flächen und Linien auf ein Fenster aus Pixeln bringen (für die Rauheit, Werkbuch HalligHooge Teil A, 10.4, Schritt
 * 6). Das Fenster beginnt mit seiner Nordwestecke bei ({@code x0}, {@code yN}); Zeilen laufen nach Süden. Ein Pixel
 * gehört zur Fläche, wenn seine Mitte darin liegt (gerade-ungerade über alle Ringe), zur Linie, wenn seine Mitte
 * höchstens {@code halb} von ihr entfernt ist.
 */
final class Raster {
    private Raster() { }

    /** Setzt {@code wert} in {@code ziel}, wo die Fläche liegt und {@code frei} (falls nicht null) den Wert 0 hat. */
    static int flaeche(Osm.Flaeche f, double x0, double yN, double zelle, int w, int h, byte[] ziel, byte wert, byte[] frei) {
        double[] g = f.grenzen();
        if (g[2] < x0 || g[0] > x0 + w * zelle || g[3] < yN - h * zelle || g[1] > yN) return 0;
        int r0 = Math.max(0, (int) Math.floor((yN - g[3]) / zelle - 0.5)), r1 = Math.min(h - 1, (int) Math.ceil((yN - g[1]) / zelle - 0.5));
        double[] xs = new double[64];
        int gesetzt = 0;
        for (int r = r0; r <= r1; r++) {
            double y = yN - (r + 0.5) * zelle;
            int n = 0;
            for (double[][] ring : f.ringe()) {
                double[] rx = ring[0], ry = ring[1];
                for (int i = 0, j = rx.length - 1; i < rx.length; j = i++) {
                    if ((ry[i] > y) != (ry[j] > y)) {
                        if (n == xs.length) xs = Arrays.copyOf(xs, 2 * n);
                        xs[n++] = rx[i] + (y - ry[i]) * (rx[j] - rx[i]) / (ry[j] - ry[i]);
                    }
                }
            }
            if (n < 2) continue;
            Arrays.sort(xs, 0, n);
            for (int k = 0; k + 1 < n; k += 2) {
                int c0 = Math.max(0, (int) Math.ceil((xs[k] - x0) / zelle - 0.5)), c1 = Math.min(w - 1, (int) Math.floor((xs[k + 1] - x0) / zelle - 0.5));
                for (int c = c0; c <= c1; c++) {
                    int p = r * w + c;
                    if (frei == null || frei[p] == 0) {
                        ziel[p] = wert;
                        gesetzt++;
                    }
                }
            }
        }
        return gesetzt;
    }

    /** Setzt {@code wert}, wo die Linie höchstens {@code halb} entfernt ist und {@code darf} (falls nicht null) wahr ist. */
    static int linie(double[] lx, double[] ly, double halb, double x0, double yN, double zelle, int w, int h, byte[] ziel, byte wert, java.util.function.IntPredicate darf) {
        int gesetzt = 0;
        for (int s = 0; s + 1 < lx.length; s++) {
            double ax = lx[s], ay = ly[s], bx = lx[s + 1], by = ly[s + 1];
            double mx = Math.min(ax, bx) - halb, Mx = Math.max(ax, bx) + halb, my = Math.min(ay, by) - halb, My = Math.max(ay, by) + halb;
            if (Mx < x0 || mx > x0 + w * zelle || My < yN - h * zelle || my > yN) continue;
            int c0 = Math.max(0, (int) Math.floor((mx - x0) / zelle)), c1 = Math.min(w - 1, (int) Math.floor((Mx - x0) / zelle));
            int r0 = Math.max(0, (int) Math.floor((yN - My) / zelle)), r1 = Math.min(h - 1, (int) Math.floor((yN - my) / zelle));
            for (int r = r0; r <= r1; r++)
                for (int c = c0; c <= c1; c++) {
                    double x = x0 + (c + 0.5) * zelle, y = yN - (r + 0.5) * zelle;
                    if (Bauwerke.abstandStrecke(x, y, ax, ay, bx, by) > halb) continue;
                    int p = r * w + c;
                    if (ziel[p] == wert || darf != null && !darf.test(p)) continue;
                    ziel[p] = wert;
                    gesetzt++;
                }
        }
        return gesetzt;
    }
}
