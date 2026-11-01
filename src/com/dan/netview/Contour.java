package com.dan.netview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolinien eines Rasterfelds (seit LituyaBay1958 Phase 7; dieselbe Rechnung wie in com.dan.route, eigene Kopie nach Paketvertrag): Marching Squares über die Werte an den Punkten
 * (ox + i·step, oz + j·step); Quadrate mit einem nicht endlichen Wert (gesperrt, unerreicht) bleiben leer. Die Stücke
 * werden über gemeinsame Kantenpunkte zu Linienzügen verbunden (offen am Rand, geschlossen um Inseln).
 */
public final class Contour {
    private Contour() { }

    /** Linienzüge der Höhe level; je Zug x, z abwechselnd. */
    public static List<float[]> lines(float[] f, int w, int h, double ox, double oz, double step, double level) {
        // Stücke: Endpunkte als Schlüssel der Rasterkante (waagerecht oder senkrecht), damit Nachbarn denselben Punkt finden
        Map<Long, List<long[]>> at = new HashMap<>();
        Map<Long, float[]> pos = new HashMap<>();
        List<long[]> segs = new ArrayList<>();
        for (int j = 0; j + 1 < h; j++)
            for (int i = 0; i + 1 < w; i++) {
                float a = f[j * w + i], b = f[j * w + i + 1], c = f[(j + 1) * w + i + 1], d = f[(j + 1) * w + i];
                if (!Float.isFinite(a) || !Float.isFinite(b) || !Float.isFinite(c) || !Float.isFinite(d)) continue;
                int idx = (a >= level ? 1 : 0) | (b >= level ? 2 : 0) | (c >= level ? 4 : 0) | (d >= level ? 8 : 0);
                if (idx == 0 || idx == 15) continue;
                // Kanten: 0 unten (a-b), 1 rechts (b-c), 2 oben (d-c), 3 links (a-d)
                long[] e = new long[4];
                e[0] = key(i, j, 0); e[1] = key(i + 1, j, 1); e[2] = key(i, j + 1, 0); e[3] = key(i, j, 1);
                float[][] p = {
                        lerp(ox + i * step, oz + j * step, ox + (i + 1) * step, oz + j * step, a, b, level),
                        lerp(ox + (i + 1) * step, oz + j * step, ox + (i + 1) * step, oz + (j + 1) * step, b, c, level),
                        lerp(ox + i * step, oz + (j + 1) * step, ox + (i + 1) * step, oz + (j + 1) * step, d, c, level),
                        lerp(ox + i * step, oz + j * step, ox + i * step, oz + (j + 1) * step, a, d, level)};
                int[][] pairs = switch (idx) {
                    case 1, 14 -> new int[][]{{0, 3}};
                    case 2, 13 -> new int[][]{{0, 1}};
                    case 3, 12 -> new int[][]{{1, 3}};
                    case 4, 11 -> new int[][]{{1, 2}};
                    case 6, 9 -> new int[][]{{0, 2}};
                    case 7, 8 -> new int[][]{{2, 3}};
                    case 5 -> (a + b + c + d) / 4 >= level ? new int[][]{{0, 1}, {2, 3}} : new int[][]{{0, 3}, {1, 2}};
                    case 10 -> (a + b + c + d) / 4 >= level ? new int[][]{{0, 3}, {1, 2}} : new int[][]{{0, 1}, {2, 3}};
                    default -> new int[0][];
                };
                for (int[] q : pairs) {
                    long[] s = {e[q[0]], e[q[1]], 0};
                    segs.add(s);
                    pos.put(e[q[0]], p[q[0]]);
                    pos.put(e[q[1]], p[q[1]]);
                    at.computeIfAbsent(s[0], k -> new ArrayList<>()).add(s);
                    at.computeIfAbsent(s[1], k -> new ArrayList<>()).add(s);
                }
            }
        List<float[]> out = new ArrayList<>();
        for (long[] s : segs) {
            if (s[2] != 0) continue;
            s[2] = 1;
            java.util.ArrayDeque<Long> line = new java.util.ArrayDeque<>();
            line.add(s[0]);
            line.add(s[1]);
            for (int dir = 0; dir < 2; dir++) {
                while (true) {
                    long end = dir == 0 ? line.peekLast() : line.peekFirst();
                    long[] next = null;
                    for (long[] t : at.getOrDefault(end, List.of())) if (t[2] == 0) { next = t; break; }
                    if (next == null) break;
                    next[2] = 1;
                    long o = next[0] == end ? next[1] : next[0];
                    if (dir == 0) line.addLast(o); else line.addFirst(o);
                }
            }
            float[] pts = new float[2 * line.size()];
            int k = 0;
            for (long id : line) { float[] p = pos.get(id); pts[k++] = p[0]; pts[k++] = p[1]; }
            out.add(pts);
        }
        return out;
    }

    private static long key(int i, int j, int vertical) { return (((long) i) << 33) ^ (((long) j) << 1) ^ vertical; }

    private static float[] lerp(double x0, double z0, double x1, double z1, float a, float b, double level) {
        double t = Math.abs(b - a) < 1e-12 ? 0.5 : (level - a) / (b - a);
        return new float[]{(float) (x0 + (x1 - x0) * t), (float) (z0 + (z1 - z0) * t)};
    }
}
