package com.dan.core.gitter;

import java.util.Locale;

/**
 * Ein regelmäßiges Raster in Länge und Breite, wie es Quellen liefern (EOT20 zu 1/8°, ERA5 zu 1/4°; Werkbuch
 * HalligHooge Teil A, 12.6, P5b): Punkte, keine Zellen. {@code laenge0}, {@code breite0} ist der Punkt im Südwesten,
 * Index {@code j · nx + i}, i nach Osten, j nach Norden.
 * <p>
 * Werte zwischen den Punkten bilinear, und zwar nur über gültige Punkte (nicht NaN): Liegt ein Ort an der Küste, zählen
 * nur die Punkte im Wasser, ihre Gewichte werden auf 1 gebracht. Ist keiner der vier gültig, kann der nächste gültige
 * Punkt in einem Umkreis gelten ({@link #gewichte}).
 */
public record Gradraster(double laenge0, double breite0, double dLaenge, double dBreite, int nx, int ny) {

    /** Vier (oder ein) Punkte und ihre Gewichte; die Gewichte summieren sich zu 1. */
    public record Gewichte(int[] index, double[] gewicht, boolean naechster) {
        /** Der gewichtete Wert aus einem Feld, ab {@code versatz}, mit Schritt {@code schritt} je Punkt. */
        public double wert(float[] f, int versatz, int schritt) {
            double s = 0;
            for (int k = 0; k < index.length; k++) s += gewicht[k] * f[versatz + schritt * index[k]];
            return s;
        }
    }

    public Gradraster {
        if (nx < 1 || ny < 1 || !(dLaenge > 0) || !(dBreite > 0)) throw new IllegalArgumentException("Gradraster: " + nx + " × " + ny + ", " + dLaenge + "° × " + dBreite + "°");
    }

    public int punkte() { return nx * ny; }

    public double laenge(int i) { return laenge0 + i * dLaenge; }

    public double breite(int j) { return breite0 + j * dBreite; }

    /** Westlichster, südlichster, östlichster, nördlichster Punkt (Grad). */
    public double[] grenzen() { return new double[] {laenge0, breite0, laenge(nx - 1), breite(ny - 1)}; }

    /** Liegt der Ort zwischen den äußeren Punkten? */
    public boolean enthaelt(double laenge, double breite) {
        double fi = (laenge - laenge0) / dLaenge, fj = (breite - breite0) / dBreite;
        return fi >= -1e-9 && fj >= -1e-9 && fi <= nx - 1 + 1e-9 && fj <= ny - 1 + 1e-9;
    }

    /**
     * Gewichte für einen Ort: bilinear über die gültigen der vier umgebenden Punkte; gültig heißt
     * {@code gueltig[versatz + schritt · index]} ist nicht NaN ({@code gueltig} null: alle). Ist keiner gültig, der
     * nächste gültige Punkt (Abstand mit cos φ in der Länge) höchstens {@code umkreis} Punkte entfernt; sonst null. Null
     * auch außerhalb des Rasters.
     */
    public Gewichte gewichte(double laenge, double breite, float[] gueltig, int versatz, int schritt, int umkreis) {
        if (!enthaelt(laenge, breite)) return null;
        double fi = (laenge - laenge0) / dLaenge, fj = (breite - breite0) / dBreite;
        int i0 = Math.max(0, Math.min(nx - 2, (int) Math.floor(fi))), j0 = Math.max(0, Math.min(ny - 2, (int) Math.floor(fj)));
        if (nx == 1) i0 = 0;
        if (ny == 1) j0 = 0;
        double a = nx == 1 ? 0 : fi - i0, b = ny == 1 ? 0 : fj - j0;
        int[] idx = new int[4];
        double[] w = new double[4];
        int n = 0;
        double sw = 0;
        for (int dj = 0; dj < 2; dj++)
            for (int di = 0; di < 2; di++) {
                int i = Math.min(nx - 1, i0 + di), j = Math.min(ny - 1, j0 + dj);
                double g = (di == 0 ? 1 - a : a) * (dj == 0 ? 1 - b : b);
                int k = j * nx + i;
                if (g <= 0 || (gueltig != null && Float.isNaN(gueltig[versatz + schritt * k]))) continue;
                idx[n] = k;
                w[n++] = g;
                sw += g;
            }
        if (n > 0 && sw > 0) {
            int[] ri = new int[n];
            double[] rw = new double[n];
            for (int k = 0; k < n; k++) {
                ri[k] = idx[k];
                rw[k] = w[k] / sw;
            }
            return new Gewichte(ri, rw, false);
        }
        if (umkreis <= 0 || gueltig == null) return null;
        double c = Math.cos(Math.toRadians(breite)), best = Double.POSITIVE_INFINITY;
        int bk = -1, ic = (int) Math.round(fi), jc = (int) Math.round(fj);
        for (int j = Math.max(0, jc - umkreis); j <= Math.min(ny - 1, jc + umkreis); j++)
            for (int i = Math.max(0, ic - umkreis); i <= Math.min(nx - 1, ic + umkreis); i++) {
                int k = j * nx + i;
                if (Float.isNaN(gueltig[versatz + schritt * k])) continue;
                double d = Math.hypot((laenge(i) - laenge) * c, breite(j) - breite);
                if (d < best) {
                    best = d;
                    bk = k;
                }
            }
        return bk < 0 ? null : new Gewichte(new int[] {bk}, new double[] {1}, true);
    }

    /** Die Zeile {@code raster <Länge0> <Breite0> <dLänge> <dBreite> <nx> <ny>}. */
    public String zeile() { return String.format(Locale.ROOT, "raster %.6f %.6f %.6f %.6f %d %d", laenge0, breite0, dLaenge, dBreite, nx, ny); }

    /** Aus einer Zeile von {@link #zeile()} (ohne das erste Wort oder mit). */
    public static Gradraster aus(String zeile) {
        String[] t = zeile.strip().split("\\s+");
        int o = t[0].equals("raster") ? 1 : 0;
        return new Gradraster(Double.parseDouble(t[o]), Double.parseDouble(t[o + 1]), Double.parseDouble(t[o + 2]), Double.parseDouble(t[o + 3]),
                Integer.parseInt(t[o + 4]), Integer.parseInt(t[o + 5]));
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%d × %d Punkte zu %.4f° × %.4f°, %.3f–%.3f° O, %.3f–%.3f° N", nx, ny, dLaenge, dBreite, laenge0, laenge(nx - 1), breite0, breite(ny - 1));
    }
}
