package com.dan.clipmap.demo;

import com.dan.clipmap.Clipmap;
import com.dan.clipmap.Source;
import com.dan.clipmap.TileFolder;
import com.dan.clipmap.TileStack;

import java.io.File;
import java.util.Locale;
import java.util.Random;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10), endet mit „Alles in Ordnung.“ oder den Verstößen (Rückgabewert 1).
 * Auf einem gerechneten Gelände ({@link Synth}): Zahl und Reichweite der Stufen, Schachtelung, Nachladen in Streifen
 * gleich vollem Laden, Übergang 0 innen und 1 am Rand, <b>Naht</b>: am Rand jeder Stufe liegen alle Scheitel auf den
 * Kanten der gröberen Stufe. Mit {@code --daten <ordner> <ost> <nord>} (Kachelordner mit {@code STUFE.txt}, Ursprung in
 * m) zusätzlich: <b>Höhe an 1 000 Stichproben gleich der Kachel</b> und die Zeit je Runde des Ladefadens. Mit
 * {@code --gpu} rechnet der Baustein {@code clipmap.glsl} alle Scheitel auf der Grafikkarte, verglichen mit Java
 * ({@link ClipmapGpuCheck}).
 */
public final class ClipmapCheck {
    private static int bad;

    public static void main(String[] a) throws Exception {
        Synth s = new Synth(1958);
        for (Clipmap.Quality q : Clipmap.Quality.values()) {
            Clipmap c = new Clipmap(s, q, 0.5, 0.25, 0.25, 140_000);
            double reach = c.half() * c.cell(c.levels() - 1);
            check(reach >= 140_000 && reach < 300_000, String.format(Locale.GERMAN, "%s: %d Stufen à %d², 0,5 m bis %s m, reicht %.0f km",
                    q, c.levels(), c.grid, fmt(c.cell(c.levels() - 1)), reach / 1000));
        }
        Clipmap c = new Clipmap(s, Clipmap.Quality.LOW, 0.5, 0.25, 0.25, 40_000);
        check(c.morph(c.half(), c.half()) == 0 && c.morph(0, c.half()) == 1 && c.morph(c.grid - 1, 7) == 1
                && c.morph(c.half() - c.morphWidth() - 1, c.half()) == 0, "Übergang: 0 innen, 1 am ganzen Rand, Band " + c.morphWidth() + " Zellen");

        // Fahrt: kleine Schritte, Sprünge, wieder zurück; nach jeder Runde alles gegen die Quelle und die Naht
        Random r = new Random(7);
        double x = 1234.6, z = -876.2;
        int rounds = 0, wrong = 0, nest = 0, seam = 0;
        double worstSeam = 0;
        for (int step = 0; step < 60; step++) {
            if (step % 15 == 14) { x += (r.nextDouble() - 0.5) * 30_000; z += (r.nextDouble() - 0.5) * 30_000; }
            else { x += (r.nextDouble() - 0.4) * 9; z += (r.nextDouble() - 0.6) * 7; }
            c.update(x, z);
            c.refreshNow();
            rounds++;
            Clipmap.Window[] w = c.windows();
            for (int L = 0; L < w.length; L++) {
                // jeder Gitterpunkt im Fenster gleich der Quelle
                int h = c.half();
                for (int t = 0; t < 400; t++) {
                    int k = r.nextInt(c.grid), l = r.nextInt(c.grid);
                    int i = w[L].ci() - h + k, j = w[L].cj() - h + l;
                    double px = c.originX + i * w[L].cell(), pz = c.originZ + j * w[L].cell();
                    if (c.gridHeight(L, i, j) != s.height(px, pz, w[L].cell())) wrong++;
                }
                if (L + 1 < w.length) {
                    double[] f = w[L].rect(c), g = w[L + 1].rect(c);
                    double m = 3 * w[L + 1].cell();
                    if (!(f[0] >= g[0] + m && f[1] >= g[1] + m && f[2] <= g[2] - m && f[3] <= g[3] - m)) nest++;
                    double e = seam(c, L);
                    worstSeam = Math.max(worstSeam, e);
                    if (e > 1e-3) seam++;
                }
            }
        }
        check(wrong == 0, String.format(Locale.GERMAN, "%d Runden mit Schritten und Sprüngen: Stichproben in allen Stufen gleich der Quelle (%d falsch)", rounds, wrong));
        check(nest == 0, "jede Stufe liegt mit mindestens 3 Zellen Rand in der gröberen");
        check(seam == 0, String.format(Locale.GERMAN, "Naht: alle Randscheitel liegen auf den Kanten der gröberen Stufe (größte Abweichung %.1e m)", worstSeam));
        float hq = c.height(x + 0.3, z - 0.7);
        check(Math.abs(hq - s.height(x + 0.3, z - 0.7, 0.5)) < 0.05, String.format(Locale.GERMAN, "Höhe am Auge %.2f m (Quelle %.2f m)", hq, s.height(x + 0.3, z - 0.7, 0.5)));
        check(Math.abs(c.drop(10_000) - 7.85 / (1 / 0.87)) < 0.1 && Math.abs(c.drop(100_000) - 683) < 3,
                String.format(Locale.GERMAN, "Erdkrümmung mit Refraktion: %.1f m auf 10 km, %.0f m auf 100 km", c.drop(10_000), c.drop(100_000)));

        if (java.util.Arrays.asList(a).contains("--gpu")) {
            double[] g = ClipmapGpuCheck.run(c);
            check(g[0] < 2e-3 && g[1] < 1e-5, ClipmapGpuCheck.line(g));
        } else System.out.println("  (ohne --gpu: der Baustein wird nicht auf der Grafikkarte gegen Java geprüft)");

        // Faden
        Clipmap ct = new Clipmap(s, Clipmap.Quality.HIGH, 0.5, 0.25, 0.25, 140_000);
        ct.start();
        ct.update(0, 0);
        long t0 = System.nanoTime();
        boolean ok = ct.await(30_000);
        double full = (System.nanoTime() - t0) / 1e6;
        ct.update(3.2, -1.7);
        t0 = System.nanoTime();
        ok &= ct.await(10_000);
        double small = (System.nanoTime() - t0) / 1e6;
        ct.stop();
        check(ok, String.format(Locale.GERMAN, "Ladefaden (Hoch): alles in %.0f ms, ein Schritt in %.1f ms", full, small));

        kachelordner(s);

        int ai = java.util.Arrays.asList(a).indexOf("--daten");
        if (ai >= 0 && a.length > ai + 3) daten(new File(a[ai + 1]), Double.parseDouble(a[ai + 2]), Double.parseDouble(a[ai + 3]));
        else System.out.println("  (ohne --daten <ordner> <ost> <nord>: keine Stichproben an Kacheln)");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /**
     * Größte Abweichung am Rand der Stufe L: jeder Randscheitel (mit Übergang) gegen die gerade Linie zwischen den beiden
     * Scheiteln der gröberen Stufe, auf deren Kante er liegt.
     */
    static double seam(Clipmap c, int L) {
        int m = c.grid, h = c.half();
        int[] f = c.center(L), g = c.center(L + 1);
        double worst = 0;
        for (int side = 0; side < 4; side++)
            for (int t = 0; t < m; t++) {
                int k = side == 0 ? t : side == 1 ? t : side == 2 ? 0 : m - 1;
                int l = side == 0 ? 0 : side == 1 ? m - 1 : t;
                float v = c.vertexHeight(L, k, l);
                int i = f[0] - h + k, j = f[1] - h + l;
                // Gitterpunkt der groben Stufe (Index halbiert); ungerade: Mitte einer Kante
                int ci = Math.floorDiv(i, 2), cj = Math.floorDiv(j, 2);
                int gk = ci - (g[0] - h), gl = cj - (g[1] - h);
                double e;
                if ((i & 1) == 0 && (j & 1) == 0) e = c.vertexHeight(L + 1, gk, gl);
                else if ((i & 1) != 0) e = 0.5 * (c.vertexHeight(L + 1, gk, gl) + c.vertexHeight(L + 1, gk + 1, gl));
                else e = 0.5 * (c.vertexHeight(L + 1, gk, gl) + c.vertexHeight(L + 1, gk, gl + 1));
                worst = Math.max(worst, Math.abs(v - e));
            }
        return worst;
    }

    /**
     * Ein kleiner Kachelordner zur Probe (3 × 2 Kacheln zu 64² Zellen à 0,5 m, aus {@link Synth} geschrieben, im
     * Temp-Ordner): Format, Lage der Zellmitten, Nähte zwischen Kacheln und die Clipmap darüber gleich der Datei.
     */
    static void kachelordner(Synth s) throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("clipmap").toFile();
        int n = 64, tx = 3, ty = 2;
        double cell = 0.5, swE = 352_000 - 48, swN = 6_503_000 - 32, e0 = 352_000, n0 = 6_503_000;
        java.nio.file.Files.writeString(new File(dir, "STUFE.txt").toPath(), String.format(Locale.ROOT,
                "# Probe%nzelle=%s%nkachel_zellen=%d%nkacheln_ost=%d%nkacheln_nord=%d%nsuedwest_ost=%.0f%nsuedwest_nord=%.0f%n", cell, n, tx, ty, swE, swN));
        for (int b = 0; b < ty; b++)
            for (int t = 0; t < tx; t++) {
                double e = swE + t * n * cell, no = swN + b * n * cell;
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(n * n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        double E = e + (i + 0.5) * cell, N = no + n * cell - (j + 0.5) * cell;
                        bb.putFloat(s.height(E - e0, n0 - N, cell));
                    }
                java.nio.file.Files.write(new File(dir, String.format(Locale.ROOT, "E%d_N%d.r32", Math.round(e), Math.round(no))).toPath(), bb.array());
            }
        TileFolder f = new TileFolder(dir, e0, n0);
        int wrong = 0;
        Random r = new Random(11);
        for (int k = 0; k < 1000; k++) {
            int col = r.nextInt(n * tx), row = r.nextInt(n * ty);
            double x = f.xOfCol(col), z = f.zOfRow(row);
            if (Math.abs(f.cellValue(col, row) - s.height(x, z, cell)) > 1e-3 || Math.abs(f.bilinear(x, z) - s.height(x, z, cell)) > 1e-3) wrong++;
        }
        check(wrong == 0, "Kachelordner (Probe 3 × 2 Kacheln): 1 000 Zellmitten an der richtigen Stelle, bilinear dort genau");
        Clipmap c = new Clipmap(new TileStack(f), Clipmap.Quality.LOW, cell, f.xOfCol(0), f.zOfRow(0), 500);
        c.noData = -7;
        c.update(f.xOfCol(90), f.zOfRow(70));
        c.refreshNow();
        int bad0 = 0, m = 0;
        for (int k = 0; k < 1000; k++) {
            int col = 60 + r.nextInt(60), row = 40 + r.nextInt(50);
            float v = c.height(f.xOfCol(col), f.zOfRow(row));
            m++;
            if (Math.abs(v - f.cellValue(col, row)) > 1e-4) bad0++;
        }
        float out = c.height(f.xOfCol(90) + 120, f.zOfRow(70) - 60);
        check(bad0 == 0 && out == -7, String.format(Locale.GERMAN, "Clipmap über dem Kachelordner: %d Stichproben genau der Datei (%d falsch), außerhalb der Ersatzwert (%.1f)", m, bad0, out));
        for (File x : dir.listFiles()) x.delete();
        dir.delete();
    }

    /** 1 000 Stichproben an Zellmitten der feinsten Kachel: die Clipmap muss genau den Wert der Datei haben. */
    static void daten(File dir, double e0, double n0) throws Exception {
        TileStack st = TileStack.folders(dir, e0, n0);
        TileFolder fine = (TileFolder) st.sources()[0];
        double[] ex = fine.extent();
        double c0 = fine.cell;
        // Ursprung des feinsten Gitters auf eine Zellmitte
        double ox = fine.xOfCol(0), oz = fine.zOfRow(0);
        Clipmap c = new Clipmap(st, Clipmap.Quality.HIGH, c0, ox, oz, 140_000);
        c.noData = -100;
        c.start();
        Random r = new Random(4671);
        int n = 0, wrong = 0, nan = 0;
        double worst = 0, ms = 0;
        for (int cam = 0; cam < 20; cam++) {
            double x = ex[0] + 300 + r.nextDouble() * (ex[2] - ex[0] - 600), z = ex[1] + 300 + r.nextDouble() * (ex[3] - ex[1] - 600);
            long t0 = System.nanoTime();
            c.update(x, z);
            if (!c.await(120_000)) { check(false, "Ladefaden kommt nicht nach"); return; }
            ms += (System.nanoTime() - t0) / 1e6;
            for (int k = 0; k < 50; k++) {
                int col = (int) Math.round(fine.col(x + (r.nextDouble() - 0.5) * 100)), row = (int) Math.round(fine.row(z + (r.nextDouble() - 0.5) * 100));
                float want = fine.cellValue(col, row);
                if (Float.isNaN(want)) { nan++; continue; }
                float got = c.height(fine.xOfCol(col), fine.zOfRow(row));
                n++;
                double d = Math.abs(got - want);
                worst = Math.max(worst, d);
                if (d > 1e-3) wrong++;
            }
        }
        c.stop();
        check(n >= 900 && wrong == 0, String.format(Locale.GERMAN, "%s: %d Stichproben an Zellmitten gleich der Kachel (größte Abweichung %.1e m, %d ohne Wert übersprungen)",
                fine.name, n, worst, nan));
        System.out.printf(Locale.GERMAN, "  20 Orte geladen in %.0f ms je Ort (mit dem Lesen der Kacheln); %s%n", ms / 20, st.describe());
        System.out.println("  " + c.describe());
    }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    /** Gerechnetes Gelände für Prüfung und Demo: Kette, Täler und Fjord aus Wellen und Rauschen, reproduzierbar über seed. */
    public static final class Synth implements Source {
        final long seed;
        final double p1, p2;

        public Synth(long seed) {
            this.seed = seed;
            Random r = new Random(seed);
            p1 = r.nextDouble() * 6.28;
            p2 = r.nextDouble() * 6.28;
        }

        @Override public float height(double x, double z, double cell) {
            double ridge = 1 - Math.abs(Math.sin(x / 9000 + p1) * Math.cos(z / 7000 + p2));
            double h = 1800 * ridge * ridge - 400 + 120 * Math.sin(x / 1300.0 + z / 2100.0) + 25 * Math.sin(x / 97.0 - z / 131.0)
                    + 2 * Math.sin(x / 7.3) * Math.cos(z / 5.9);
            double fj = Math.exp(-Math.pow((z - 300 * Math.sin(x / 2500)) / 900, 2));
            return (float) (h * (1 - 0.8 * fj) - 250 * fj);
        }

        @Override public double finestCell() { return 0.5; }
    }
}
