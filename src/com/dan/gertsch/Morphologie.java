package com.dan.gertsch;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Graustufen-Morphologie mit einer Scheibe als Strukturelement, für die Granulometrie der Flecken (Matheron 1975,
 * Serra 1982): Eine Öffnung mit Radius r nimmt alle hellen Gebilde weg, in die keine Scheibe mit Radius r passt; eine
 * Schließung füllt alle dunklen Lücken, die schmaler sind. Was zwischen zwei Radien verschwindet, sind die Flecken
 * dieser Größe.
 * <p>
 * Die Scheibe wird in Zeilen zerlegt: in Zeile dy ist sie ⌊√((r + ½)² − dy²)⌋ Bildpunkte nach jeder Seite breit.
 * Minimum und Maximum über eine Zeile rechnet der Algorithmus von van Herk (1992) und Gil und Werman (1993) mit drei
 * Vergleichen je Bildpunkt, gleich wie breit die Zeile ist. Außerhalb des Bilds zählt nichts (Rand ohne Einfluss).
 */
public final class Morphologie {
    private Morphologie() { }

    /** Halbe Breite der Scheibe mit Radius r in der Zeile dy (0 ≤ dy ≤ r). */
    public static int halb(int r, int dy) { return (int) Math.floor(Math.sqrt((r + 0.5) * (r + 0.5) - (double) dy * dy)); }

    /** Erosion (Minimum über die Scheibe). */
    public static float[] erodieren(float[] f, int w, int h, int r) { return scheibe(f, w, h, r, true); }

    /** Dilatation (Maximum über die Scheibe). */
    public static float[] dilatieren(float[] f, int w, int h, int r) { return scheibe(f, w, h, r, false); }

    /** Öffnung: erst Erosion, dann Dilatation. Helle Flecken mit einem Inkreis unter r verschwinden. */
    public static float[] oeffnen(float[] f, int w, int h, int r) { return dilatieren(erodieren(f, w, h, r), w, h, r); }

    /** Schließung: erst Dilatation, dann Erosion. Dunkle Lücken mit einem Inkreis unter r werden gefüllt. */
    public static float[] schliessen(float[] f, int w, int h, int r) { return erodieren(dilatieren(f, w, h, r), w, h, r); }

    /**
     * Summe des geöffneten ({@code hell}) oder geschlossenen Bilds für jeden Radius der Leiter, parallel gerechnet.
     * Daraus folgt die Größenverteilung der hellen Flecken bzw. dunklen Lücken.
     */
    public static double[] summen(float[] f, int w, int h, int[] radien, boolean hell) {
        return IntStream.range(0, radien.length).parallel().mapToDouble(i -> {
            float[] g = hell ? oeffnen(f, w, h, radien[i]) : schliessen(f, w, h, radien[i]);
            double s = 0;
            for (float v : g) s += v;
            return s;
        }).toArray();
    }

    static float[] scheibe(float[] f, int w, int h, int r, boolean min) {
        if (r <= 0) return f.clone();
        float[] out = new float[w * h], zeile = new float[w * h];
        Arrays.fill(out, min ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY);
        float[] g = new float[w + 2 * r + 1], k = new float[w + 2 * r + 1];
        int last = -1;
        for (int dy = 0; dy <= r; dy++) {
            int hw = halb(r, dy);
            if (hw != last) {
                linie(f, zeile, w, h, hw, min, g, k);
                last = hw;
            }
            mischen(out, zeile, w, h, dy, min);
            if (dy != 0) mischen(out, zeile, w, h, -dy, min);
        }
        return out;
    }

    /** Minimum (oder Maximum) über ein Fenster von 2·hw + 1 Bildpunkten in jeder Zeile (van Herk / Gil–Werman). */
    static void linie(float[] f, float[] o, int w, int h, int hw, boolean min, float[] g, float[] k) {
        if (hw == 0) {
            System.arraycopy(f, 0, o, 0, w * h);
            return;
        }
        float pad = min ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        int n = w + 2 * hw, len = 2 * hw + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int i = 0; i < n; i++) {
                float p = i < hw || i >= hw + w ? pad : f[row + i - hw];
                g[i] = i % len == 0 ? p : (min ? Math.min(g[i - 1], p) : Math.max(g[i - 1], p));
            }
            for (int i = n - 1; i >= 0; i--) {
                float p = i < hw || i >= hw + w ? pad : f[row + i - hw];
                k[i] = i == n - 1 || (i + 1) % len == 0 ? p : (min ? Math.min(k[i + 1], p) : Math.max(k[i + 1], p));
            }
            for (int x = 0; x < w; x++) o[row + x] = min ? Math.min(k[x], g[x + 2 * hw]) : Math.max(k[x], g[x + 2 * hw]);
        }
    }

    /** Zeile um dy verschoben in das Ergebnis mischen (Zeilen außerhalb des Bilds zählen nicht). */
    static void mischen(float[] out, float[] zeile, int w, int h, int dy, boolean min) {
        int y0 = Math.max(0, -dy), y1 = Math.min(h, h - dy);
        for (int y = y0; y < y1; y++) {
            int o = y * w, s = (y + dy) * w;
            if (min) for (int x = 0; x < w; x++) out[o + x] = Math.min(out[o + x], zeile[s + x]);
            else for (int x = 0; x < w; x++) out[o + x] = Math.max(out[o + x], zeile[s + x]);
        }
    }
}
