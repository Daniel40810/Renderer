package com.dan.rail;

import java.util.Locale;

/**
 * Seit Brocken Phase 12: der Bahnkörper als Änderung des Geländes auf einem Raster – Damm, wo das Gleis über dem
 * Gelände liegt, Einschnitt, wo es darunter liegt. Das Planum liegt {@link #depth} m unter der Schienenoberkante und ist
 * {@link #formation} m breit; daneben steigen oder fallen die Böschungen im Verhältnis 1 : {@link #slope} bis zum
 * Gelände. Das Package kennt kein Gelände: die Höhe kommt über {@link Ground}, das Ergebnis ist je Zelle die Änderung
 * (m, + aufgeschüttet, − abgetragen), der Abstand zur Achse und ob dort Schotter liegt. Die App legt die Änderung auf
 * ihr Gelände (etwa als Textur im Shader und in ihrer Höhenabfrage).
 */
public final class Bed {
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    /** Breite des Planums (m), Tiefe unter der Schienenoberkante (m), Böschung 1 : slope, Schotterbreite oben (m). */
    public double formation = 4.0, depth = 0.55, slope = 1.5, ballast = 2.6;
    /** Weiter als so viele m von der Achse ändert sich nichts. */
    public double reach = 130;
    /**
     * Bis zu so viel m Höhe ist der Bahnkörper gebaut (Böschung 1 : slope); was darüber hinausgeht, gleicht das Gelände
     * flach im Verhältnis 1 : {@link #easing} an (für Höhenmodelle, die neben dem Gleis Bäume oder Unschärfe tragen
     * und darum zu hoch oder zu tief liegen) [G].
     */
    public double built = 3, easing = 6;

    public double x0, z0, cell;
    public int nx, nz;
    /** Änderung der Höhe (m), Abstand zur Achse (m; groß = weit weg), Höhe des Planums (m, NaN = keins in der Nähe). */
    public float[] delta, dist, planum;
    private double cutMax, fillMax, cutVol, fillVol;
    private long millis;

    /** Das Raster über dem Rechteck (x0, z0, nx · cell, nz · cell) für ein Gleis rechnen. */
    public Bed build(Track t, Ground g, double x0, double z0, double cell, int nx, int nz) {
        return build(java.util.List.of(t), g, x0, z0, cell, nx, nz);
    }

    /**
     * Seit Brocken Phase 13: das Raster für mehrere Gleise (etwa Hauptgleis und Ausweichgleise); je Zelle zählt das
     * nächste.
     */
    public Bed build(java.util.List<Track> tracks, Ground g, double x0, double z0, double cell, int nx, int nz) {
        long t0 = System.currentTimeMillis();
        this.x0 = x0; this.z0 = z0; this.cell = cell; this.nx = nx; this.nz = nz;
        delta = new float[nx * nz];
        dist = new float[nx * nz];
        planum = new float[nx * nz];
        float[] corr = new float[nx * nz];
        java.util.Arrays.fill(dist, Float.MAX_VALUE);
        java.util.Arrays.fill(planum, Float.NaN);
        double half = formation / 2, area = cell * cell, nearZone = half + built * slope;
        // je Stelle (alle cell m) die Zellen im Umkreis stempeln: nächste Stelle, ihr Planum und der Überschuss der
        // Abweichung an der Achse über das Gebaute (den gleicht das Gelände flach an)
        for (Track t : tracks)
        for (double s = 0; s <= t.length(); s += Math.max(0.5, cell)) {
            Track.Pose p = t.at(s);
            double f = p.y() - depth, d0 = f - g.height(p.x(), p.z());
            double c = Math.signum(d0) * Math.max(0, Math.abs(d0) - built);
            double rad = Math.min(reach, nearZone + Math.abs(c) * easing + 2 * cell);
            int r = (int) Math.ceil(rad / cell) + 1;
            int ci = (int) Math.floor((p.x() - x0) / cell), cj = (int) Math.floor((p.z() - z0) / cell);
            for (int j = cj - r; j <= cj + r; j++) {
                if (j < 0 || j >= nz) continue;
                for (int i = ci - r; i <= ci + r; i++) {
                    if (i < 0 || i >= nx) continue;
                    double x = x0 + (i + 0.5) * cell, z = z0 + (j + 0.5) * cell, d = Math.hypot(x - p.x(), z - p.z());
                    if (d > rad) continue;
                    int k = j * nx + i;
                    if (d < dist[k]) { dist[k] = (float) d; planum[k] = (float) f; corr[k] = (float) c; }
                }
            }
        }
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                if (Float.isNaN(planum[k])) continue;
                double x = x0 + (i + 0.5) * cell, z = z0 + (j + 0.5) * cell, h = g.height(x, z), f = planum[k], d = dist[k];
                // erst angleichen (verschieben, zur Seite flach auslaufend), dann den Bahnkörper bauen
                double c = corr[k], taper = d <= nearZone ? 1 : Math.max(0, 1 - (d - nearZone) / Math.max(1e-6, Math.abs(c) * easing));
                double hs = h + c * taper;
                double e = Math.max(0, d - half), off = e / slope;
                double nh = Math.max(f - off, Math.min(f + off, hs));
                delta[k] = (float) (nh - h);
                if (delta[k] < 0) { cutMax = Math.max(cutMax, -delta[k]); cutVol -= delta[k] * area; }
                else { fillMax = Math.max(fillMax, delta[k]); fillVol += delta[k] * area; }
            }
        millis = System.currentTimeMillis() - t0;
        return this;
    }

    private int idx(double x, double z) {
        int i = (int) Math.floor((x - x0) / cell), j = (int) Math.floor((z - z0) / cell);
        return i < 0 || j < 0 || i >= nx || j >= nz ? -1 : j * nx + i;
    }

    /** Änderung der Höhe am Ort (bilinear; außerhalb 0). */
    public double deltaAt(double x, double z) {
        double u = (x - x0) / cell - 0.5, v = (z - z0) / cell - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        if (i < 0 || j < 0 || i >= nx - 1 || j >= nz - 1) return 0;
        double fu = u - i, fv = v - j;
        return (delta[j * nx + i] * (1 - fu) + delta[j * nx + i + 1] * fu) * (1 - fv) + (delta[(j + 1) * nx + i] * (1 - fu) + delta[(j + 1) * nx + i + 1] * fu) * fv;
    }

    /** Abstand zur Achse (m, nächste Zelle; außerhalb sehr groß). */
    public double distAt(double x, double z) {
        int k = idx(x, z);
        return k < 0 ? Double.MAX_VALUE : dist[k];
    }

    /** Größter Abtrag und größte Schüttung (m), Mengen (m³). */
    public double[] earthwork() { return new double[]{cutMax, fillMax, cutVol, fillVol}; }

    /**
     * Die Karte als RGBA-Bytes: R Änderung (−32..+32 m auf 0..255, 128 = 0; in Stufen von 25 cm, für feinere die App
     * selbst aus {@link #delta}), G Schotter (0..255 nach dem Abstand zur
     * Achse), B Planum (Böschung, Planum, Schotter; 255 auf dem Planum), A 255.
     */
    public byte[] rgba() {
        byte[] b = new byte[nx * nz * 4];
        for (int k = 0; k < nx * nz; k++) {
            double d = dist[k];
            b[k * 4] = (byte) Math.round(Math.max(0, Math.min(255, 127.5 + delta[k] / 64 * 255)));
            b[k * 4 + 1] = (byte) Math.round(255 * Math.max(0, Math.min(1, (ballast / 2 + 0.6 - d) / 1.2)));
            b[k * 4 + 2] = (byte) Math.round(255 * Math.max(0, Math.min(1, (formation / 2 + 0.8 - d) / 1.6)));
            b[k * 4 + 3] = (byte) 255;
        }
        return b;
    }

    /** „Bahnkörper zu 2 m: Einschnitt bis 3,2 m, Damm bis 2,1 m · 12 000 m³ Abtrag, 9 000 m³ Auftrag (0,4 s)“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Bahnkörper zu %.0f m: Einschnitt bis %.1f m, Damm bis %.1f m · %,.0f m³ Abtrag, %,.0f m³ Auftrag (%.1f s)",
                cell, cutMax, fillMax, cutVol, fillVol, millis / 1000.0);
    }
}
