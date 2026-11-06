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

    /**
     * Seit LituyaBay1958 W6 Runde 4: Stücke überlappen nicht. Zwei Stücke stören sich, wenn ihre Mitten näher liegen als
     * {@link #gap} mal die Summe der Halbmesser; dann bleibt das größere (bei gleicher Größe entscheidet der Hash). Die
     * Regel sieht nur Nachbarn an, die selbst stehen (Bedeckung und Ausdünnen), und hängt nicht von der Reihenfolge ab:
     * gleicher Startwert, gleiches Eis. 0 = Überlappen erlaubt (wie in Runde 3).
     */
    public double gap = 0.85;
    /** Nach {@link #fill}: so viele Stücke fielen weg, weil ein größeres Nachbarstück an ihrer Stelle lag. */
    public int removedOverlap;

    /** Stücke um das Auge (x, z) aus der Bedeckung neu würfeln; Rückgabe: Anzahl. */
    public int fill(double ex, double ez, Cover cover, Water water) {
        int r = (int) Math.ceil(radius / cell) + 2;   // ein Ring mehr für die Nachbarn
        int ci = (int) Math.floor(ex / cell), cj = (int) Math.floor(ez / cell);
        int sd = (int) (seed ^ (seed >>> 32));
        int w = 2 * r + 1, S = slots;
        // Durchgang 1: je Platz Lage, Durchmesser, Hash und ob er steht
        double[] px = new double[w * w * S], pz = new double[w * w * S], pd = new double[w * w * S];
        int[] ph = new int[w * w * S];
        boolean[] on = new boolean[w * w * S];
        double a0 = radius * fadeFrom;
        for (int jj = 0; jj < w; jj++)
            for (int ii = 0; ii < w; ii++) {
                int i = ci - r + ii, j = cj - r + jj;
                double mx = (i + 0.5) * cell - ex, mz = (j + 0.5) * cell - ez;
                if (mx * mx + mz * mz > (radius + 2 * cell) * (radius + 2 * cell)) continue;
                int hc = pcg(i * 1597334677 ^ j * (int) 3812015801L ^ sd);
                for (int k = 0; k < S; k++) {
                    int h = pcg(hc + k * 7919), q = (jj * w + ii) * S + k;
                    double x = (i + u01(h, 1)) * cell, z = (j + u01(h, 2)) * cell;
                    double dx = x - ex, dz = z - ez, dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist > radius) continue;
                    if (dist > a0 && u01(h, 3) < nearWeight(dist)) continue;
                    double c = cover.at(x, z);
                    if (!(c > 0) || u01(h, 4) >= c) continue;
                    px[q] = x; pz[q] = z; ph[q] = h; on[q] = true;
                    pd[q] = sizeMin + (sizeMax - sizeMin) * Math.pow(u01(h, 5), sizeShape);
                }
            }
        // Durchgang 2: weg, wenn ein stehendes, größeres Stück in den Nachbarzellen zu nah liegt
        int[] n = new int[VARIANTS];
        float[][] per = new float[VARIANTS][];
        for (int v = 0; v < VARIANTS; v++) per[v] = new float[STRIDE * 256];
        removedOverlap = 0;
        for (int jj = 1; jj < w - 1; jj++)
            for (int ii = 1; ii < w - 1; ii++)
                for (int k = 0; k < S; k++) {
                    int q = (jj * w + ii) * S + k;
                    if (!on[q]) continue;
                    double x = px[q], z = pz[q], d = pd[q];
                    boolean weg = false;
                    if (gap > 0)
                        for (int nj = jj - 1; nj <= jj + 1 && !weg; nj++)
                            for (int ni = ii - 1; ni <= ii + 1 && !weg; ni++)
                                for (int kk = 0; kk < S; kk++) {
                                    int o = (nj * w + ni) * S + kk;
                                    if (o == q || !on[o]) continue;
                                    double lim = gap * 0.5 * (d + pd[o]);
                                    double ddx = px[o] - x, ddz = pz[o] - z;
                                    if (ddx * ddx + ddz * ddz >= lim * lim) continue;
                                    if (pd[o] > d || pd[o] == d && ph[o] > ph[q]) { weg = true; break; }
                                }
                    if (weg) { removedOverlap++; continue; }
                    int h = ph[q];
                    double fb = d * (freeboardMin + (freeboardMax - freeboardMin) * u01(h, 6));
                    int v = Math.min(VARIANTS - 1, (int) (u01(h, 7) * VARIANTS));
                    if (STRIDE * (n[v] + 1) > per[v].length) per[v] = Arrays.copyOf(per[v], per[v].length * 2);
                    float[] o = per[v];
                    int b = STRIDE * n[v]++;
                    o[b] = (float) x;
                    o[b + 1] = (float) water.height(x, z);
                    o[b + 2] = (float) z;
                    o[b + 3] = (float) (u01(h, 8) * 2 * Math.PI);
                    o[b + 4] = (float) d;
                    o[b + 5] = (float) fb;
                    o[b + 6] = (float) Math.toRadians(tiltMax * (2 * u01(h, 9) - 1));
                    o[b + 7] = (float) Math.toRadians(tiltMax * (2 * u01(h, 10) - 1));
                    o[b + 8] = (float) u01(h, 11);
                    o[b + 9] = v;
                    o[b + 10] = (float) (u01(h, 12) * 2 * Math.PI);
                    o[b + 11] = 0;
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
