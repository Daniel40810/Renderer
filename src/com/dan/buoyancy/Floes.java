package com.dan.buoyancy;

import java.util.Arrays;

/**
 * Eisschollen und Brocken als einzelne Körper um das Auge (seit LituyaBay1958 W6 Runde 3): aus einem Feld der
 * Bedeckung (0..1, etwa {@link BrashField} oder eine Formel der App) würfelt ein festes Gitter je Zelle bis zu
 * {@link #slots} Stücke. Ein Stück steht, wenn sein Wurf unter der Bedeckung an seinem Ort liegt; Größe, Höhe über dem
 * Wasser, Drehung und Neigung kommen aus einem Hash über Zelle und Startwert. Gleicher Startwert, gleiches Eis; ein
 * Stück bleibt an seinem Platz, wenn das Auge wandert. Zum Zeichnen {@code gl/FloeGl}.
 * <p>
 * Ab {@link #fadeFrom} · {@link #radius} wird gewürfelt ausgedünnt; dahinter zeichnet die App das Eis flach (etwa im
 * Shader des Wassers) und blendet es mit {@link #nearWeight} innen aus, so dass beides zusammen gleich viel deckt.
 */
public final class Floes {

    /** Bedeckung (0..1) am Ort; 0 über Land. */
    public interface Cover { double at(double x, double z); }

    /** Höhe des Wasserspiegels am Ort (m). */
    public interface Water { double height(double x, double z); }

    /** Kante einer Zelle (m), Stücke je Zelle, Reichweite (m), Beginn des Ausdünnens (Anteil). */
    public double cell = 12, radius = 260, fadeFrom = 0.7;
    public int slots = 6;
    /** Durchmesser von bis (m), Verteilung: d = min + (max − min) · u^shape (viele kleine, wenige große) [G]. */
    public double sizeMin = 1.2, sizeMax = 9, sizeShape = 2.5;
    /** Höhe über Wasser als Anteil des Durchmessers, von bis [G] (Brocken aus Gletschereis liegen flach, über Wasser ein Sechzehntel bis ein Sechstel; W6 Runde 3: vorher bis 0,28, sie standen wie Dosen). */
    public double freeboardMin = 0.06, freeboardMax = 0.16;
    /** Größte Neigung (Grad). */
    public double tiltMax = 9;
    public long seed = 1958;
    /** Anzahl der Formen ({@code gl/FloeGl} baut so viele Netze). */
    public static final int VARIANTS = 4;
    /** Werte je Stück in {@link #data}: x, y, z, Drehung, Durchmesser, Höhe, Neigung a, b, Tönung, Form, Phase, 0. */
    public static final int STRIDE = 12;

    /** Stücke nach {@link #fill}, nach Form sortiert; {@link #start} und {@link #count} je Form. */
    public float[] data = new float[0];
    public final int[] start = new int[VARIANTS], count = new int[VARIANTS];
    public int total;

    /** Anteil des flachen Eises der App bei waagerechtem Abstand dist (m) vom Auge: 0 innen, 1 ab der Reichweite. */
    public double nearWeight(double dist) {
        double a = radius * fadeFrom;
        double t = Math.max(0, Math.min(1, (dist - a) / Math.max(1e-6, radius - a)));
        return t * t * (3 - 2 * t);
    }

    static int pcg(int v) {
        int s = v * 747796405 + (int) 2891336453L;
        int w = ((s >>> ((s >>> 28) + 4)) ^ s) * 277803737;
        return (w >>> 22) ^ w;
    }

    static double u01(int h, int k) { return (pcg(h + k * (int) 0x9E3779B9L) >>> 8) * 0x1.0p-24; }

    /** Stücke um das Auge (x, z) aus der Bedeckung neu würfeln; Rückgabe: Anzahl. */
    public int fill(double ex, double ez, Cover cover, Water water) {
        int r = (int) Math.ceil(radius / cell) + 1;
        int ci = (int) Math.floor(ex / cell), cj = (int) Math.floor(ez / cell);
        int sd = (int) (seed ^ (seed >>> 32));
        int[] n = new int[VARIANTS];
        float[][] per = new float[VARIANTS][];
        for (int v = 0; v < VARIANTS; v++) per[v] = new float[STRIDE * 256];
        double a0 = radius * fadeFrom;
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                double mx = (i + 0.5) * cell - ex, mz = (j + 0.5) * cell - ez;
                if (mx * mx + mz * mz > (radius + cell) * (radius + cell)) continue;
                int hc = pcg(i * 1597334677 ^ j * (int) 3812015801L ^ sd);
                for (int k = 0; k < slots; k++) {
                    int h = pcg(hc + k * 7919);
                    double x = (i + u01(h, 1)) * cell, z = (j + u01(h, 2)) * cell;
                    double dx = x - ex, dz = z - ez, dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius) continue;
                    if (dist > a0 && u01(h, 3) < nearWeight(dist)) continue;
                    double c = cover.at(x, z);
                    if (!(c > 0) || u01(h, 4) >= c) continue;
                    double d = sizeMin + (sizeMax - sizeMin) * Math.pow(u01(h, 5), sizeShape);
                    double fb = d * (freeboardMin + (freeboardMax - freeboardMin) * u01(h, 6));
                    int v = Math.min(VARIANTS - 1, (int) (u01(h, 7) * VARIANTS));
                    if (STRIDE * (n[v] + 1) > per[v].length) per[v] = Arrays.copyOf(per[v], per[v].length * 2);
                    float[] o = per[v];
                    int q = STRIDE * n[v]++;
                    o[q] = (float) x;
                    o[q + 1] = (float) water.height(x, z);
                    o[q + 2] = (float) z;
                    o[q + 3] = (float) (u01(h, 8) * 2 * Math.PI);
                    o[q + 4] = (float) d;
                    o[q + 5] = (float) fb;
                    o[q + 6] = (float) Math.toRadians(tiltMax * (2 * u01(h, 9) - 1));
                    o[q + 7] = (float) Math.toRadians(tiltMax * (2 * u01(h, 10) - 1));
                    o[q + 8] = (float) u01(h, 11);
                    o[q + 9] = v;
                    o[q + 10] = (float) (u01(h, 12) * 2 * Math.PI);
                    o[q + 11] = 0;
                }
            }
        int tot = 0;
        for (int v = 0; v < VARIANTS; v++) tot += n[v];
        if (data.length < STRIDE * tot) data = new float[STRIDE * Math.max(tot, 64)];
        int off = 0;
        for (int v = 0; v < VARIANTS; v++) {
            start[v] = off;
            count[v] = n[v];
            System.arraycopy(per[v], 0, data, STRIDE * off, STRIDE * n[v]);
            off += n[v];
        }
        total = tot;
        return tot;
    }

    /**
     * Umriss der Form v (Einheitsdurchmesser, Mitte 0): k Punkte x, z gegen den Uhrzeigersinn von oben, kantig wie
     * gebrochenes Eis (Radius 0,26 bis 0,5 je Ecke, Winkel gestört) [G].
     */
    public static float[] outline(int v, int k) {
        float[] o = new float[2 * k];
        int h = pcg(0x51CE + v * 104729);
        for (int i = 0; i < k; i++) {
            double a = 2 * Math.PI * (i + 0.6 * (u01(h, 2 * i) - 0.5)) / k;
            double rr = 0.26 + 0.24 * u01(h, 2 * i + 1);
            o[2 * i] = (float) (Math.cos(a) * rr);
            o[2 * i + 1] = (float) (-Math.sin(a) * rr);
        }
        return o;
    }
}
