package com.dan.render.reference;

import com.dan.core.gitter.Gelaende;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Schranke des Geländes für den Schnitt der Referenz (P7c, Werkbuch HalligHooge Teil A, 12.8): je Zelle das Maximum
 * aller Stufen, die dort Werte haben, mit einem Pixel Rand (die Höhe ist bilinear zwischen Pixelmitten, also nie höher
 * als ein Pixel der Zelle oder ihres Rands), und die Mindesthöhe der Bauwerke. Außerhalb aller Stufen gilt der
 * Meeresgrund {@link #KEIN_GRUND} wie im Echtzeitbild.
 * <ul>
 * <li><b>Basis:</b> {@link #N} × {@link #N} Zellen zu {@link #BASIS} m um eine Mitte (gut 262 km, weiter als jede Kimm der
 * Ansichten mit 60 m [G] hohem Land dahinter).</li>
 * <li><b>Ziegel:</b> wo eine Stufe von 10 m oder feiner Kacheln hat, teilt ein Ziegel die Basiszelle in 8 × 8 Zellen zu
 * {@link #FEIN} m [G].</li>
 * <li><b>Schritt</b> je Zelle: ein Viertel [G] der feinsten Pixel, die sie berühren (ohne Stufe ein Viertel der Zelle).</li>
 * </ul>
 * Einmal je Gelände gebaut, für alle Ansichten; das Wasser legt {@link Gelaendeszene} je Stand darüber.
 */
public final class Schranke {
    public static final double BASIS = 256, FEIN = 32, KEIN_GRUND = -30, VIERTEL = 0.25;
    public static final int N = 1024, ZIEGEL = 8, Z2 = ZIEGEL * ZIEGEL;
    /** Stufen, deren Kacheln einen Ziegel bekommen (Zelle bis 10 m). */
    public static final double ZIEGEL_BIS = 10.0001;

    public final Gelaende gelaende;
    /** Südwestecke der Basis (UTM, m). */
    public final double ost0, nord0;
    /** Je Basiszelle (i von West, j von Süd, i + N j): Schranke (m NHN), Schritt (m), Ziegel (−1: keiner). */
    final float[] hb, sb;
    final int[] ziegel;
    /** Je Ziegel 64 Zellen (i + 8 j, von Südwest): Schranke und Schritt. */
    final float[] hz, sz;
    public final int ziegelZahl;
    /** Gelesene Pixel und Zeit des Baus (s). */
    public long pixel;
    public double sekunden;

    /** Für das Gelände um die Mitte (UTM). */
    public Schranke(Gelaende g, double ostMitte, double nordMitte) {
        long t0 = System.nanoTime();
        gelaende = g;
        ost0 = Math.floor((ostMitte - N * BASIS / 2) / BASIS) * BASIS;
        nord0 = Math.floor((nordMitte - N * BASIS / 2) / BASIS) * BASIS;
        hb = new float[N * N];
        sb = new float[N * N];
        ziegel = new int[N * N];
        java.util.Arrays.fill(hb, (float) KEIN_GRUND);
        java.util.Arrays.fill(sb, (float) (VIERTEL * BASIS));
        java.util.Arrays.fill(ziegel, -1);
        List<Gelaende.Stufe> alle = new ArrayList<>(List.of(g.stufen()));
        if (g.mindesthoehe() != null) alle.add(g.mindesthoehe());
        // Ziegel: Basiszellen unter Kacheln feiner Stufen
        int nz = 0;
        for (Gelaende.Stufe s : alle) {
            if (s.zelle > ZIEGEL_BIS) continue;
            for (int ty = 0; ty < s.kachelnNord; ty++)
                for (int tx = 0; tx < s.kachelnOst; tx++) {
                    if (!s.hat(tx, ty)) continue;
                    double w = s.suedwestOst + tx * s.kachel(), n = s.suedwestNord + (s.kachelnNord - ty) * s.kachel(), r = 1.5 * s.zelle;
                    int i0 = bi(w - r), i1 = bi(w + s.kachel() + r), j0 = bj(n - s.kachel() - r), j1 = bj(n + r);
                    for (int j = Math.max(0, j0); j <= Math.min(N - 1, j1); j++)
                        for (int i = Math.max(0, i0); i <= Math.min(N - 1, i1); i++)
                            if (ziegel[i + N * j] < 0) ziegel[i + N * j] = nz++;
                }
        }
        ziegelZahl = nz;
        hz = new float[nz * Z2];
        sz = new float[nz * Z2];
        java.util.Arrays.fill(hz, (float) KEIN_GRUND);
        java.util.Arrays.fill(sz, (float) (VIERTEL * FEIN));
        // Werte aller Stufen
        long px = 0;
        for (Gelaende.Stufe s : alle) {
            double z = s.zelle, r = 1.5 * z, schritt = VIERTEL * z;
            int kz = s.kachelZellen;
            for (int ty = 0; ty < s.kachelnNord; ty++)
                for (int tx = 0; tx < s.kachelnOst; tx++) {
                    double w = s.suedwestOst + tx * s.kachel(), n = s.suedwestNord + (s.kachelnNord - ty) * s.kachel();
                    if (w + s.kachel() + r < ost0 || w - r > ost0 + N * BASIS || n + r < nord0 || n - s.kachel() - r > nord0 + N * BASIS) continue;
                    if (!s.hat(tx, ty)) continue;
                    float[] t = s.kachel(tx, ty);
                    if (t.length == 0) continue;
                    for (int b = 0; b < kz; b++) {
                        double pn = n - (b + 0.5) * z;
                        for (int a = 0; a < kz; a++) {
                            float v = t[b * kz + a];
                            if (Float.isNaN(v)) continue;
                            px++;
                            double pe = w + (a + 0.5) * z;
                            setze(pe - r, pe + r, pn - r, pn + r, v, (float) schritt);
                        }
                    }
                    s.vergiss();
                }
        }
        pixel = px;
        sekunden = (System.nanoTime() - t0) / 1e9;
    }

    int bi(double ost) { return (int) Math.floor((ost - ost0) / BASIS); }

    int bj(double nord) { return (int) Math.floor((nord - nord0) / BASIS); }

    /** Ein Wert v für das Rechteck [e0, e1] × [n0, n1] (UTM). */
    private void setze(double e0, double e1, double n0, double n1, float v, float schritt) {
        int i0 = Math.max(0, bi(e0)), i1 = Math.min(N - 1, bi(e1)), j0 = Math.max(0, bj(n0)), j1 = Math.min(N - 1, bj(n1));
        for (int j = j0; j <= j1; j++)
            for (int i = i0; i <= i1; i++) {
                int c = i + N * j;
                if (v > hb[c]) hb[c] = v;
                if (schritt < sb[c]) sb[c] = schritt;
                int zi = ziegel[c];
                if (zi < 0) continue;
                double we = ost0 + i * BASIS, sn = nord0 + j * BASIS;
                int a0 = Math.max(0, (int) Math.floor((e0 - we) / FEIN)), a1 = Math.min(ZIEGEL - 1, (int) Math.floor((e1 - we) / FEIN));
                int b0 = Math.max(0, (int) Math.floor((n0 - sn) / FEIN)), b1 = Math.min(ZIEGEL - 1, (int) Math.floor((n1 - sn) / FEIN));
                for (int b = b0; b <= b1; b++)
                    for (int a = a0; a <= a1; a++) {
                        int k = zi * Z2 + a + ZIEGEL * b;
                        if (v > hz[k]) hz[k] = v;
                        if (schritt < sz[k]) sz[k] = schritt;
                    }
            }
    }

    /** Höchste Schranke (m NHN). */
    public double hoechste() {
        float m = (float) KEIN_GRUND;
        for (float v : hb) m = Math.max(m, v);
        return m;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "Schranke: %d × %d Zellen zu %.0f m ab O %.0f, N %.0f, %d Ziegel zu %d × %d Zellen zu %.0f m, %d Pixel, %.1f s",
                N, N, BASIS, ost0, nord0, ziegelZahl, ZIEGEL, ZIEGEL, FEIN, pixel, sekunden);
    }
}
