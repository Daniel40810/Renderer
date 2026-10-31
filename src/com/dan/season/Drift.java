package com.dan.season;

import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Seit Brocken Phase 11: Verwehungen und Wechten über einem Gelände, als Karte auf einem Raster. Der Wind nimmt den
 * Schnee von Kuppen und Luvhängen und legt ihn in Mulden, hinter Kanten und in Leehänge; an einer Kante, hinter der
 * das Gelände steil abfällt, wächst eine Wechte über den Abbruch hinaus.
 * <p>
 * Maß ist der Schutz nach Winstral u. a. (2002): der größte Winkel zum Gelände luvwärts bis {@link #reach} m (Sx;
 * positiv = geschützt, negativ = frei). Der Faktor auf die Schneehöhe der Fläche ist 1 + {@link #gain} · Sx (Grad),
 * begrenzt auf {@link #min}..{@link #max} [G], auf Wunsch zusätzlich nach dem Wind am Ort ({@link #wind}: wo es stärker
 * bläst, bleibt weniger liegen). Danach wird der Faktor so geteilt, dass er im Mittel 1 ist: der Wind verteilt den
 * Schnee, er nimmt keinen weg (Sublimation im Treiben vernachlässigt).
 * <p>
 * Wechte: wo luvwärts bis 60 m freie Fläche liegt (Sx unter {@link #corniceSx}°), und leewärts innerhalb von
 * {@link #leeReach} m das Gelände um mehr als {@link #corniceDrop}° abfällt.
 */
public final class Drift {
    /** Höhe des Geländes am Ort (m). */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Woher der Wind kommt, der den Schnee verlagert (Grad; im Mittel des Winters). */
    public double fromDeg = 250;
    /** Reichweite des Schutzes luvwärts (m) und Schrittweite (m). */
    public double reach = 300, stepM = 12.5;
    /** Faktor je Grad Schutz, Grenzen. */
    public double gain = 0.08, min = 0.08, max = 3.5;
    /** Wind von außen (optional, null = nur Gelände), Höhe über Grund (m), Gewicht (0..1). */
    public Wind wind;
    public double windHeight = 10, windWeight = 0.5;
    /** Wechten: freie Fläche luvwärts (Sx unter so viel Grad), Abbruch leewärts (Grad) innerhalb von leeReach m. */
    public double corniceSx = 1.5, corniceDrop = 22, leeReach = 30;

    /** Das Raster: Ecke, Zellgröße, Zahl der Zellen. */
    public double x0, z0, cell;
    public int nx, nz;
    /** Faktor auf die Schneehöhe der Fläche (Mittel 1) und Stärke der Wechte 0..1 je Zelle (Zeile für Zeile in z). */
    public float[] factor, cornice;
    private long millis;
    private double meanRaw;

    /** Die Karte über dem Raster rechnen (parallel). */
    public Drift build(Ground g, double x0, double z0, double cell, int nx, int nz) {
        long t0 = System.currentTimeMillis();
        this.x0 = x0; this.z0 = z0; this.cell = cell; this.nx = nx; this.nz = nz;
        float[] f = new float[nx * nz], c = new float[nx * nz], ws = new float[nx * nz];
        double a = Math.toRadians(fromDeg), ux = Math.sin(a), uz = -Math.cos(a);   // luvwärts (dorthin, woher der Wind kommt)
        Wind w = wind;
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                double x = x0 + (i + 0.5) * cell, z = z0 + (j + 0.5) * cell, y = g.height(x, z);
                double sx = -90, near = -90;
                for (double d = stepM; d <= reach; d += stepM) {
                    double ang = Math.toDegrees(Math.atan2(g.height(x + ux * d, z + uz * d) - y, d));
                    sx = Math.max(sx, ang);
                    if (d <= 60) near = Math.max(near, ang);
                }
                f[k] = (float) Math.max(min, Math.min(max, 1 + gain * sx));
                if (w != null) { double[] v = w.at(x, y + windHeight, z); ws[k] = (float) Math.hypot(v[0], v[2]); }
                // leewärts der stärkste Abfall bis leeReach
                double drop = 0;
                for (double d = stepM * 0.5; d <= leeReach; d += stepM * 0.5)
                    drop = Math.max(drop, Math.toDegrees(Math.atan2(y - g.height(x - ux * d, z - uz * d), d)));
                c[k] = (float) (smooth(corniceDrop * 0.7, corniceDrop * 1.4, drop) * smooth(corniceSx + 2, corniceSx - 2, near));
            }
        });
        if (w != null) {
            double mean = 0;
            for (float v : ws) mean += v;
            mean /= ws.length;
            for (int k = 0; k < f.length; k++) {
                double e = mean > 1e-6 ? ws[k] / mean : 1;
                double wf = Math.max(0.3, Math.min(1.6, 1.5 - 0.5 * e));   // wo es stärker bläst, bleibt weniger liegen [G]
                f[k] = (float) Math.max(min, Math.min(max, f[k] * (1 - windWeight + windWeight * wf)));
            }
        }
        double sum = 0;
        for (float v : f) sum += v;
        meanRaw = sum / f.length;
        for (int k = 0; k < f.length; k++) f[k] = (float) (f[k] / meanRaw);
        factor = f;
        cornice = c;
        millis = System.currentTimeMillis() - t0;
        return this;
    }

    /** Faktor am Ort (bilinear; außerhalb 1). */
    public double factorAt(double x, double z) { return sample(factor, x, z, 1); }

    /** Wechte am Ort (bilinear; außerhalb 0). */
    public double corniceAt(double x, double z) { return sample(cornice, x, z, 0); }

    private double sample(float[] m, double x, double z, double outside) {
        if (m == null) return outside;
        double u = (x - x0) / cell - 0.5, v = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i >= nx - 1 || j >= nz - 1) return outside;
        double fu = u - i, fv = v - j;
        return (m[j * nx + i] * (1 - fu) + m[j * nx + i + 1] * fu) * (1 - fv) + (m[(j + 1) * nx + i] * (1 - fu) + m[(j + 1) * nx + i + 1] * fu) * fv;
    }

    /** Anteile: Fläche mit weniger als halb so viel Schnee, mit mehr als doppelt so viel, mit Wechte. */
    public double[] shares() {
        int lo = 0, hi = 0, co = 0;
        for (int k = 0; k < factor.length; k++) {
            if (factor[k] < 0.5) lo++;
            if (factor[k] > 2) hi++;
            if (cornice[k] > 0.5) co++;
        }
        double n = factor.length;
        return new double[]{lo / n, hi / n, co / n};
    }

    /** „Verwehungen 12,7 km zu 25 m aus 250°: 18 % abgeblasen, 9 % mehr als doppelt, Wechten 0,4 % (1,2 s)“ */
    public String describe() {
        double[] s = shares();
        return String.format(Locale.GERMAN, "Verwehungen %.1f km zu %.0f m aus %03.0f°: %.0f %% abgeblasen (unter halb), %.0f %% mehr als doppelt, Wechten %.1f %% (%.1f s)",
                Math.max(nx, nz) * cell / 1000, cell, fromDeg, 100 * s[0], 100 * s[1], 100 * s[2], millis / 1000.0);
    }

    /** Die Karte als Bytes (Faktor/4 und Wechte, je 0..255) für eine Textur RG8. */
    public byte[] rg() {
        byte[] b = new byte[nx * nz * 2];
        for (int k = 0; k < nx * nz; k++) {
            b[k * 2] = (byte) Math.round(Math.max(0, Math.min(1, factor[k] / 4)) * 255);
            b[k * 2 + 1] = (byte) Math.round(Math.max(0, Math.min(1, cornice[k])) * 255);
        }
        return b;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
