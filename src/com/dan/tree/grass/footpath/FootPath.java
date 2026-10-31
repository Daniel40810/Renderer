package com.dan.tree.grass.footpath;

import com.dan.tree.grass.GrassField;
import com.dan.tree.TreeAsset;
import com.dan.tree.TreeSpecies;

/**
 * Fußweg im Gras („Dusty foot path way in grass garden“): Gelände aus Fotogrammetrie, 33 m × 42 m mit 3 m
 * Höhenunterschied und 120 000 Dreiecken, eine Farbtextur (2048², verkleinert aus 8K). Statisch, bewegt sich im
 * Wind nicht. {@link #ground()} liefert die Höhe am Ort, etwa um eine {@link GrassField}-Wiese darauf zu streuen.
 */
public final class FootPath {

    private FootPath() {}

    public static final String[] VARIANTS = {"footpath.dtree"};

    public static TreeSpecies species() {
        TreeSpecies s = new TreeSpecies("Fußweg im Gras", "Gelände (Fotogrammetrie)", FootPath.class, VARIANTS);
        s.variantNames = new String[]{"Gelände"};
        s.form = TreeSpecies.Form.GROUND;
        s.flexTrunk = 0;
        s.flexBranch = 0;
        s.flutter = 0;
        s.season.evergreen = true;
        // Stufen nach Entfernung in halben Durchmessern
        s.lodDistance = new float[]{1.5f, 4, 10};
        return s;
    }

    private static float[] heights;
    private static float x0, z0, step;
    private static int nx, nz;

    /** Höhe des Geländes am Ort (Raster 0,25 m, höchste Fläche je Zelle); außerhalb die Höhe am Rand. */
    public static synchronized GrassField.Ground ground() {
        if (heights == null) build(species().load(0), 0.25f);
        return (x, z) -> {
            int i = Math.max(0, Math.min(nx - 1, (int) ((x - x0) / step)));
            int k = Math.max(0, Math.min(nz - 1, (int) ((z - z0) / step)));
            return heights[k * nx + i];
        };
    }

    private static void build(TreeAsset a, float cell) {
        float[] p = a.trunk.pos;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < p.length; i += 3) {
            minX = Math.min(minX, p[i]);
            maxX = Math.max(maxX, p[i]);
            minZ = Math.min(minZ, p[i + 2]);
            maxZ = Math.max(maxZ, p[i + 2]);
        }
        x0 = minX;
        z0 = minZ;
        step = cell;
        nx = (int) Math.ceil((maxX - minX) / cell) + 1;
        nz = (int) Math.ceil((maxZ - minZ) / cell) + 1;
        heights = new float[nx * nz];
        java.util.Arrays.fill(heights, Float.NaN);
        int[] t = a.trunk.tri;
        for (int k = 0; k < t.length; k += 3) {
            int ia = t[k] * 3, ib = t[k + 1] * 3, ic = t[k + 2] * 3;
            float ax = p[ia], az = p[ia + 2], bx = p[ib], bz = p[ib + 2], cx = p[ic], cz = p[ic + 2];
            float area = (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
            if (Math.abs(area) < 1e-9f) continue;
            int i0 = (int) Math.floor((Math.min(ax, Math.min(bx, cx)) - x0) / cell), i1 = (int) Math.ceil((Math.max(ax, Math.max(bx, cx)) - x0) / cell);
            int k0 = (int) Math.floor((Math.min(az, Math.min(bz, cz)) - z0) / cell), k1 = (int) Math.ceil((Math.max(az, Math.max(bz, cz)) - z0) / cell);
            for (int kk = Math.max(0, k0); kk <= Math.min(nz - 1, k1); kk++)
                for (int ii = Math.max(0, i0); ii <= Math.min(nx - 1, i1); ii++) {
                    float px = x0 + ii * cell, pz = z0 + kk * cell;
                    float w0 = ((bx - px) * (cz - pz) - (bz - pz) * (cx - px)) / area;
                    float w1 = ((cx - px) * (az - pz) - (cz - pz) * (ax - px)) / area;
                    float w2 = 1 - w0 - w1;
                    if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) continue;
                    float y = w0 * p[ia + 1] + w1 * p[ib + 1] + w2 * p[ic + 1];
                    int idx = kk * nx + ii;
                    if (Float.isNaN(heights[idx]) || y > heights[idx]) heights[idx] = y;
                }
        }
        // Lücken (Rand, Löcher) mit dem nächsten bekannten Wert der Zeile füllen
        for (int kk = 0; kk < nz; kk++) {
            float last = Float.NaN;
            for (int ii = 0; ii < nx; ii++) {
                int idx = kk * nx + ii;
                if (!Float.isNaN(heights[idx])) last = heights[idx];
                else if (!Float.isNaN(last)) heights[idx] = last;
            }
            for (int ii = nx - 1; ii >= 0; ii--) {
                int idx = kk * nx + ii;
                if (!Float.isNaN(heights[idx])) last = heights[idx];
                else heights[idx] = Float.isNaN(last) ? 0 : last;
            }
        }
    }
}
