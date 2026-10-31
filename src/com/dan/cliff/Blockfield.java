package com.dan.cliff;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Ein Blockmeer (seit Brocken Phase 8): gerundete Granitblöcke, wie sie die Kaltzeit auf den Hängen hinterlassen hat,
 * halb im Boden, dicht an dicht. Das Package kennt das Gelände nicht: wo Blöcke liegen ({@link Density}, 0..1 Anteil der
 * Fläche) und wie hoch der Boden ist ({@link Cliff.Heights}), sagt die App.
 * <p>
 * Gestreut wird in Kacheln zu {@link #TILE} m aus einem Startwert je Kachel (immer gleich); {@link #instances} gibt die
 * Blöcke im Umkreis {@link #radius} um das Auge, in der Ferne nur die großen (ein Block bleibt, solange er im Bild größer
 * als rund {@link #minPixels} Bildpunkte ist). Je Block acht Zahlen: Mitte x, y, z, Drehung (rad), halbe Ausdehnung x, y,
 * z, Rundung (Exponent). Gezeichnet wird mit {@code gl/CliffGl.drawBlocks} instanziert, Material wie der Fels.
 */
public final class Blockfield {
    /** Kantenlänge einer Kachel (m). */
    public static final double TILE = 32;
    /** Werte je Block in {@link #instances}. */
    public static final int STRIDE = 8;

    /** Anteil der Fläche mit Blöcken am Ort, 0..1 (Blockmeer 0,6 bis 1, Blockschutt im Wald 0,1). */
    @FunctionalInterface
    public interface Density {
        double at(double x, double z);
    }

    public volatile Density density = (x, z) -> 0;
    public volatile Cliff.Heights ground = (x, z) -> 0;
    public volatile int seed = 1;
    /** Reichweite um das Auge (m), Blöcke je m² bei voller Dichte, mittlerer Durchmesser (m) und seine Streuung (σ von ln d). */
    public volatile double radius = 450, perM2 = 0.32, sizeMedian = 1.1, sizeSpread = 0.45;
    /** Ein Block bleibt im Bild, solange sein Durchmesser mehr als so viele Bildpunkte deckt (bei einer Bildpunktöffnung von 1 mrad). */
    public volatile double minPixels = 3;
    /** Wie tief die Blöcke im Boden stecken (Anteil ihrer Höhe). */
    public volatile double sink = 0.25;

    private final Map<Long, float[]> tiles = new HashMap<>();
    private int version;
    private float[] last = new float[0];
    private int lastCount;
    private double lex = Double.NaN, lez, lpa;

    /** Wird erhöht, wenn {@link #instances} etwas anderes liefert (für das Hochladen). */
    public int version() { return version; }

    /** Zahl der Blöcke beim letzten Aufruf von {@link #instances}. */
    public int count() { return lastCount; }

    /** Die Kacheln neu streuen (nach einer Änderung von Dichte, Boden oder Größen). */
    public synchronized void invalidate() { tiles.clear(); lex = Double.NaN; version++; }

    /**
     * Die Blöcke um das Auge (ex, ez), Öffnung eines Bildpunkts pixelAngle (rad); das Feld hat {@link #count()} × {@link
     * #STRIDE} gültige Werte. Ist das Auge seit dem letzten Mal weniger als 8 m gewandert, kommt dasselbe Feld zurück
     * (gleiche {@link #version()}).
     */
    public synchronized float[] instances(double ex, double ez, double pixelAngle) {
        if (!Double.isNaN(lex) && Math.hypot(ex - lex, ez - lez) < 8 && Math.abs(pixelAngle - lpa) < 1e-9) return last;
        lex = ex;
        lez = ez;
        lpa = pixelAngle;
        double r = radius;
        int i0 = (int) Math.floor((ex - r) / TILE), i1 = (int) Math.floor((ex + r) / TILE);
        int j0 = (int) Math.floor((ez - r) / TILE), j1 = (int) Math.floor((ez + r) / TILE);
        tiles.keySet().removeIf(k -> { int ti = (int) (k >> 32), tj = (int) (long) k; return ti < i0 - 2 || ti > i1 + 2 || tj < j0 - 2 || tj > j1 + 2; });
        float[] out = last.length > 0 ? last : new float[4096 * STRIDE];
        int n = 0;
        double minSize = Math.max(1e-6, pixelAngle) * minPixels;
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                double nx = Math.max(i * TILE, Math.min(ex, (i + 1) * TILE)), nz = Math.max(j * TILE, Math.min(ez, (j + 1) * TILE));
                if ((nx - ex) * (nx - ex) + (nz - ez) * (nz - ez) > r * r) continue;
                float[] t = tile(i, j);
                for (int k = 0; k + STRIDE <= t.length; k += STRIDE) {
                    double dx = t[k] - ex, dz = t[k + 2] - ez, d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r) continue;
                    double size = 2 * Math.max(t[k + 4], t[k + 6]);
                    if (size < d * minSize) continue;
                    if ((n + 1) * STRIDE > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
                    System.arraycopy(t, k, out, n * STRIDE, STRIDE);
                    n++;
                }
            }
        version++;
        last = out;
        lastCount = n;
        return out;
    }

    /** Eine Kachel streuen (zwischengespeichert). */
    float[] tile(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xffffffffL);
        float[] t = tiles.get(key);
        if (t != null) return t;
        Random rnd = new Random(((long) i * 73856093L) ^ ((long) j * 19349663L) ^ (seed * 83492791L));
        double expect = perM2 * TILE * TILE;
        int tries = (int) expect + (rnd.nextDouble() < expect - (int) expect ? 1 : 0);
        float[] buf = new float[Math.max(1, tries) * STRIDE];
        int n = 0;
        for (int k = 0; k < tries; k++) {
            double x = (i + rnd.nextDouble()) * TILE, z = (j + rnd.nextDouble()) * TILE;
            double keep = rnd.nextDouble(), g = rnd.nextGaussian();
            double dens = density.at(x, z);
            if (keep >= dens) continue;
            double d = sizeMedian * Math.exp(g * sizeSpread);
            double ax = d / 2 * (0.8 + 0.4 * rnd.nextDouble()), az = d / 2 * (0.7 + 0.4 * rnd.nextDouble()), ay = d / 2 * (0.45 + 0.3 * rnd.nextDouble());
            double y = ground.height(x, z) + ay * (1 - 2 * sink);
            double yaw = rnd.nextDouble() * Math.PI * 2, e = 0.5 + 0.3 * rnd.nextDouble();
            int o = n * STRIDE;
            buf[o] = (float) x; buf[o + 1] = (float) y; buf[o + 2] = (float) z; buf[o + 3] = (float) yaw;
            buf[o + 4] = (float) ax; buf[o + 5] = (float) ay; buf[o + 6] = (float) az; buf[o + 7] = (float) e;
            n++;
        }
        t = java.util.Arrays.copyOf(buf, n * STRIDE);
        tiles.put(key, t);
        return t;
    }

    /** Erwartete Blöcke je Hektar bei voller Dichte (für Prüfung und HUD). */
    public double perHectare() { return perM2 * 10_000; }
}
