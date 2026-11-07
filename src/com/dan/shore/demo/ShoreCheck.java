package com.dan.shore.demo;

import com.dan.shore.HydroFlat;
import com.dan.shore.ShoreRamp;

import java.util.Locale;
import java.util.Random;

/**
 * Prüfung von {@code com.dan.shore} ohne Grafikkarte (seit LituyaBay1958 W6 Runde 8): Böschung an einer geraden Stufe
 * (Verlauf wie {@link ShoreRamp#fall}), Land unverändert, zweiter Durchgang ohne Wirkung, Felsufer und kleine Stufen ohne
 * Strand, schräge Ufer gleich breit, Kacheln mit Rand gleich dem ganzen Raster, Grund unter {@code floor} unverändert,
 * glattes Wasser gegen Strand mit Rauschen; seit W9c Runde 2b die Quellen als byte und der sparsame Zusammenhang gegen die
 * Rechnung vorher.
 */
public final class ShoreCheck {
    static int all, bad;

    public static void main(String[] a) {
        ShoreRamp r = new ShoreRamp();
        double c = 0.5;
        // 1 gerade Stufe: Land 3 m (links), Grund −10 m (rechts), Naht bei i = 100
        int w = 400, h = 40;
        float[] z = new float[w * h];
        byte[] k = new byte[w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                boolean land = i < 100;
                z[j * w + i] = land ? 3f : -10f;
                k[j * w + i] = land ? ShoreRamp.LAND : ShoreRamp.BED;
            }
        float[] z0 = z.clone();
        long t0 = System.nanoTime();
        ShoreRamp.Result e = r.apply(z, k, w, h, c);
        double ms = (System.nanoTime() - t0) / 1e6;
        double err = 0;
        for (int i = 100; i < w; i++) {
            double d = (i - 99) * c;
            double soll = Math.max(-10, r.fall(3, d));
            err = Math.max(err, Math.abs(z[20 * w + i] - soll));
        }
        check(err < 0.06, String.format(Locale.GERMAN, "gerade Stufe: Böschung wie fall() (größte Abweichung %.3f m; %s; %.0f ms)", err, e, ms));
        boolean landGleich = true;
        for (int j = 0; j < h; j++) for (int i = 0; i < 100; i++) landGleich &= z[j * w + i] == z0[j * w + i];
        check(landGleich, "Land bleibt, wie es ist");
        // Strand bis −2 m über 50 m, dann 0,5 bis −10 m: 16 m; zusammen 66 m = 132 Zellen
        int ende = 100;
        while (ende < w && z[20 * w + ende] > -10f + 1e-4f) ende++;
        check(Math.abs((ende - 99) * c - 66) <= 1.0, String.format(Locale.GERMAN, "gerade Stufe: Böschung %.1f m breit (soll 66 m)", (ende - 99) * c));
        ShoreRamp.Result e2 = r.apply(z, k, w, h, c);
        check(e2.raised() == 0 && e2.sources() == 0, "zweiter Durchgang ändert nichts (" + e2 + ")");

        // 2 Felsufer 30 m und kleine Stufe 0,5 m: kein Strand
        float[] f = z0.clone();
        for (int j = 0; j < h; j++) for (int i = 0; i < 100; i++) f[j * w + i] = 30f;
        check(r.apply(f, k, w, h, c).raised() == 0, "Felsufer (30 m) bekommt keinen Strand");
        float[] s = new float[w * h];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) s[j * w + i] = i < 100 ? 0.5f : 0f;
        check(r.apply(s, k, w, h, c).raised() == 0, "Stufe von 0,5 m bleibt (stepMin 1 m)");

        // 3 schräges Ufer (30°): Breite quer zur Naht wie bei der geraden
        int n = 600;
        float[] q = new float[n * n];
        byte[] qk = new byte[n * n];
        double ang = Math.toRadians(30), nx = Math.cos(ang), ny = Math.sin(ang);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double d = (i - n / 2.0) * nx + (j - n / 2.0) * ny;   // Zellen quer zur Naht
                boolean land = d < 0;
                q[j * n + i] = land ? 3f : -10f;
                qk[j * n + i] = land ? ShoreRamp.LAND : ShoreRamp.BED;
            }
        r.apply(q, qk, n, n, c);
        double sum = 0;
        int cnt = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double d = ((i - n / 2.0) * nx + (j - n / 2.0) * ny) * c;
                if (d < 4 || d > 40 || Math.abs((i - n / 2.0) * -ny + (j - n / 2.0) * nx) > 150) continue;
                sum += Math.abs(q[j * n + i] - r.fall(3, d));
                cnt++;
            }
        double mittel = sum / cnt;
        check(mittel < 0.12, String.format(Locale.GERMAN, "Ufer schräg zu 30°: Böschung im Mittel %.3f m neben der geraden (Chamfer 5 × 5)", mittel));

        // 4 Kacheln mit Rand: gleich dem ganzen Raster
        Random rnd = new Random(7);
        int g = 800;
        float[] all0 = new float[g * g];
        byte[] ak = new byte[g * g];
        for (int j = 0; j < g; j++)
            for (int i = 0; i < g; i++) {
                double u = i + 40 * Math.sin(j / 37.0) + 25 * Math.cos(j / 11.0);
                boolean land = u < g / 2.0;
                all0[j * g + i] = land ? (float) (3 + 0.01 * rnd.nextGaussian()) : (float) (-5 - 0.05 * (u - g / 2.0));
                ak[j * g + i] = land ? ShoreRamp.LAND : ShoreRamp.BED;
            }
        float[] ganz = all0.clone();
        r.apply(ganz, ak, g, g, c);
        int rand = (int) Math.ceil(r.reach() / c) + 2, half = g / 2;
        boolean gleich = true;
        for (int teil = 0; teil < 2; teil++) {
            int y0 = teil == 0 ? 0 : half - rand, y1 = teil == 0 ? half + rand : g;
            int hh = y1 - y0;
            float[] t = new float[g * hh];
            byte[] tk = new byte[g * hh];
            System.arraycopy(all0, y0 * g, t, 0, g * hh);
            System.arraycopy(ak, y0 * g, tk, 0, g * hh);
            r.apply(t, tk, g, hh, c);
            int m0 = teil == 0 ? 0 : half, m1 = teil == 0 ? half : g;
            for (int j = m0; j < m1; j++) for (int i = 0; i < g; i++) gleich &= t[(j - y0) * g + i] == ganz[j * g + i];
        }
        check(gleich, String.format(Locale.GERMAN, "zwei Kacheln mit Rand %d Zellen (%.0f m) gleich dem ganzen Raster", rand, r.reach()));

        // 5 unter floor bleibt
        float[] tief = new float[w * h];
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) tief[j * w + i] = i < 100 ? 1f : -100f;
        r.apply(tief, k, w, h, c);
        float min = Float.MAX_VALUE;
        int unver = 0;
        for (int i = 100; i < w; i++) { min = Math.min(min, tief[20 * w + i]); if (tief[20 * w + i] == -100f) unver++; }
        check(unver > 0 && min == -100f, String.format(Locale.GERMAN, "Grund unter %.0f m bleibt (%d Zellen der Zeile unverändert)", r.floor, unver));
        boolean ueber = true;
        for (int i = 100; i < w; i++) { float v = tief[20 * w + i]; ueber &= v == -100f || v >= r.floor - 1e-3; }
        check(ueber, "gehoben wird nur bis floor");

        // 6 glattes Wasser gegen Strand
        int m = 200;
        float[] lid = new float[m * m];
        Random rr = new Random(3);
        for (int j = 0; j < m; j++)
            for (int i = 0; i < m; i++)
                lid[j * m + i] = i < 100 ? (float) (0.8 + 0.05 * (100 - i) * 0.5 + 0.01 * rr.nextGaussian()) : 0.8f;
        boolean[] fl = HydroFlat.mask(lid, m, m);
        int strandFlach = 0, wasserNicht = 0;
        for (int j = 1; j < m - 1; j++)
            for (int i = 1; i < m - 1; i++) {
                if (i < 99 && fl[j * m + i]) strandFlach++;
                if (i > 101 && !fl[j * m + i]) wasserNicht++;
            }
        check(strandFlach == 0 && wasserNicht == 0, String.format(Locale.GERMAN, "glattes Wasser erkannt, Strand mit Rauschen nicht (%d / %d falsch; Wasser %.0f %%)",
                strandFlach, wasserNicht, 100 * HydroFlat.share(fl, lid)));
        float[] eben = new float[m * m];
        for (int j = 0; j < m; j++) for (int i = 0; i < m; i++) eben[j * m + i] = (float) (0.05 * i * 0.5);
        boolean[] fe = HydroFlat.mask(eben, m, m);
        int fehl = 0;
        for (boolean b : fe) if (b) fehl++;
        check(fehl == 0, "Strand ohne Rauschen (Neigung 0,05) ist kein glattes Wasser (" + fehl + " Zellen)");

        // 7 Grenze zweier Quellen: links Wasser 0,80 m (Lidar), rechts 0,20 m (IfSAR); beide glatt
        float[] zw = new float[m * m];
        int[] gr = new int[m * m];
        for (int j = 0; j < m; j++) for (int i = 0; i < m; i++) { zw[j * m + i] = i < 100 ? 0.8f : 0.2f; gr[j * m + i] = i < 100 ? 0 : 1; }
        boolean[] ohne = HydroFlat.mask(zw, m, m), mit = HydroFlat.mask(zw, gr, m, m, HydroFlat.TOL, HydroFlat.MIN_EQUAL);
        int nOhne = 0, nMit = 0;
        for (int k2 = 0; k2 < m * m; k2++) { if (!ohne[k2]) nOhne++; if (!mit[k2]) nMit++; }
        check(nOhne > 0 && nMit == 0, String.format(Locale.GERMAN, "Grenze zweier glatter Quellen: ohne Quellen %d Zellen unruhig, mit Quellen %d", nOhne, nMit));
        // 8 Zusammenhang: ein Strand am Land bleibt, eine Insel im Wasser nicht
        boolean[] kand = new boolean[m * m], anker = new boolean[m * m];
        for (int j = 0; j < m; j++) for (int i = 0; i < m; i++) { anker[j * m + i] = i < 20; kand[j * m + i] = i >= 20 && i < 40; }
        for (int j = 100; j < 104; j++) for (int i = 150; i < 154; i++) kand[j * m + i] = true;
        boolean[] dran = HydroFlat.attached(kand, anker, m, m);
        check(dran[50 * m + 39] && !dran[101 * m + 151], "Strand am Land bleibt, Insel im Wasser nicht");

        // 9 W9c Runde 2b: Quellen als byte wie als int; Zusammenhang sparsam wie vorher (zufällige Felder gegen die alte Rechnung)
        Random rz = new Random(11);
        int p = 300;
        float[] fz = new float[p * p];
        int[] gi = new int[p * p];
        byte[] gb = new byte[p * p];
        boolean[] ka = new boolean[p * p], an = new boolean[p * p];
        boolean gleichMaske = true, gleichDran = true;
        long nDran = 0;
        for (int lauf = 0; lauf < 6; lauf++) {
            for (int z9 = 0; z9 < p * p; z9++) {
                int i = z9 % p, j = z9 / p;
                int quelle = (i / 70 + j / 90 + lauf) % 3;
                gi[z9] = quelle;
                gb[z9] = (byte) quelle;
                // Wasser in Flecken (gleich hoch je Quelle), dazwischen Land mit Rauschen, ab und zu NaN
                boolean wasser = Math.sin(i / 17.0 + lauf) * Math.cos(j / 23.0) > 0.2;
                fz[z9] = rz.nextDouble() < 0.01 ? Float.NaN : wasser ? 0.8f + 0.1f * quelle : (float) (1 + 0.02 * rz.nextGaussian());
                double u = rz.nextDouble();
                an[z9] = u < 0.002 * (lauf + 1);
                ka[z9] = !an[z9] && u < 0.45 + 0.03 * lauf;
            }
            boolean[] mi = HydroFlat.mask(fz, gi, p, p, HydroFlat.TOL, HydroFlat.MIN_EQUAL), mb = HydroFlat.mask(fz, gb, p, p, HydroFlat.TOL, HydroFlat.MIN_EQUAL);
            gleichMaske &= java.util.Arrays.equals(mi, mb);
            boolean[] neu = HydroFlat.attached(ka, an, p, p), alt = attachedAlt(ka, an, p, p);
            gleichDran &= java.util.Arrays.equals(neu, alt);
            for (boolean b : neu) if (b) nDran++;
        }
        check(gleichMaske, "Quellen als byte: dieselbe Maske wie als int (6 zufällige Felder 300 × 300, drei Quellen, NaN)");
        check(gleichDran, String.format(Locale.GERMAN, "Zusammenhang sparsam (Anker der Reihe nach, Stapel wächst mit der Front): wie vorher (6 Felder, %,d Zellen dran)", nDran));

        System.out.printf(Locale.GERMAN, "ShoreCheck: %d von %d Prüfungen bestanden%n", all - bad, all);
        if (bad > 0) System.exit(1);
    }

    /** Die Rechnung bis W9c Runde 2 (alle Anker auf einmal, Stapel ein int je Zelle): Maßstab für die sparsame. */
    static boolean[] attachedAlt(boolean[] candidate, boolean[] anchor, int w, int h) {
        int n = w * h;
        boolean[] seen = new boolean[n];
        int[] stack = new int[n];
        int sp = 0;
        for (int k = 0; k < n; k++) if (anchor[k]) { seen[k] = true; stack[sp++] = k; }
        while (sp > 0) {
            int k = stack[--sp], i = k % w, j = k / w;
            for (int y = Math.max(0, j - 1); y <= Math.min(h - 1, j + 1); y++)
                for (int x = Math.max(0, i - 1); x <= Math.min(w - 1, i + 1); x++) {
                    int q = y * w + x;
                    if (!seen[q] && candidate[q]) { seen[q] = true; stack[sp++] = q; }
                }
        }
        boolean[] r = new boolean[n];
        for (int k = 0; k < n; k++) r[k] = candidate[k] && seen[k];
        return r;
    }

    static void check(boolean ok, String s) {
        all++;
        if (!ok) bad++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + s);
    }
}
