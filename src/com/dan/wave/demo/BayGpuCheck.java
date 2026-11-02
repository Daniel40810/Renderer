package com.dan.wave.demo;

import com.dan.wave.Bay;
import com.dan.wave.BayGrid;
import com.dan.wave.gl.BayGpu;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Prüfung von {@link BayGpu} gegen {@link Bay} auf dem Prozessor (seit LituyaBay1958 Phase 11b, Werkbuch 6.1, 15.2), ohne
 * Fenster. Je Fall rechnet der Prozessor (double) und hält seine Zeitschritte fest; die Grafikkarte (float) rechnet dieselbe
 * Bucht mit denselben Schritten, dann wird Zelle für Zelle verglichen. Spielraum (Werkbuch 6.1): glatte Fälle 1 mm,
 * Auflauf 1 %, Volumen 1e-6 des Anfangs (mit Einlass 1e-5 [G]: in 140 m Wasser ist ein float-Schritt der Tiefe 15 µm).
 * Danach die Rechenzeiten je Gitter (Tabelle 15.2). Endet mit „Alles in Ordnung.“
 */
public final class BayGpuCheck {
    static int bad;

    public static void main(String[] a) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, 64, 64);
        dr.display();
        int[] r = new int[1];
        dr.invoke(true, (GLAutoDrawable d) -> { r[0] = run(d.getGL().getGL4(), a.length > 0 && a[0].equals("--kurz")); return true; });
        dr.destroy();
        System.exit(r[0] == 0 ? 0 : 1);
    }

    /** Die Prüfung im GL-Faden; gibt die Zahl der Fehler zurück. kurz: ohne die langen Läufe (Laborfall, Zeiten). */
    public static int run(GL4 gl, boolean kurz) {
        bad = 0;
        System.out.println("Grafikkarte: " + BayGpu.renderer(gl));

        // 1 ruhender See über welligem Grund mit trockener Insel, zwei Gitter
        Supplier<Bay> ruhe = () -> {
            Bay s = new Bay(0, 0, 100, 80, 10);
            s.nest(30, 25, 30, 25, 4);
            return s.build((x, z) -> -20 + 15 * Math.sin(x / 40) * Math.cos(z / 55) + 30 * Math.exp(-((x - 500) * (x - 500) + (z - 400) * (z - 400)) / 8000.0), 0, null);
        };
        Pair p = pair(gl, ruhe, 100);
        double vmax = 0;
        for (BayGrid g : p.gpu.grids()) for (int j = 0; j < g.nz; j++) for (int i = 0; i < g.nx; i++) { double[] v = g.velocity(i, j); vmax = Math.max(vmax, Math.hypot(v[0], v[1])); }
        check(p.depth[0] < 1e-3 && p.dv < 1e-6 && vmax < 1e-3, String.format(Locale.GERMAN,
                "ruhender See mit Insel über die Gittergrenze (10 und 2,5 m), %d Schritte: Tiefe auf %.1e m wie der Prozessor, Volumen %.1e, höchstens %.1e m/s",
                p.steps, p.depth[0], p.dv, vmax));

        // 2 Dammbruch nach Stoker (Stoß), ein Gitter
        p = pair(gl, () -> {
            Bay s = new Bay(0, 0, 2000, 3, 1);
            s.manning = 0;
            return s.build((x, z) -> 0, 0, new Bay.Init() { public double eta(double x, double z) { return x < 1000 ? 10 : 2; } });
        }, 400);
        check(p.depth[0] < 1e-3 && p.dv < 1e-6, String.format(Locale.GERMAN, "Dammbruch nach Stoker 10 m auf 2 m, %d Schritte: Tiefe auf %.1e m, Durchfluss auf %.1e m²/s wie der Prozessor, Volumen %.1e",
                p.steps, p.depth[0], p.flow, p.dv));

        // 3 Fjordbucht, drei Gitter, Buckel 100 m im feinsten
        p = pair(gl, BayDemo::fjordBay, 200);
        double ru = p.cpu.runup(-1e9, -1e9, 1e9, 1e9), rg = p.gpu.runup(-1e9, -1e9, 1e9, 1e9);
        check(Math.abs(rg - ru) <= 0.01 * Math.abs(ru) && p.dv < 1e-6 && p.depth[1] < 0.01 && p.depth[2] < 0.1, String.format(Locale.GERMAN,
                "Fjordbucht (3 Gitter, Buckel 100 m), %d Schritte bis %.0f s: Auflauf %.2f m (Prozessor %.2f m), Volumen %.1e; Tiefe Median %.1e m, 99 %% %.1e m, größte %.1e m [G: am Rand des Nassen]",
                p.steps, p.cpu.time(), rg, ru, p.dv, p.depth[1], p.depth[2], p.depth[0]));

        if (!kurz) {
            // 4 Laborfall im Schnitt (Messwelle FRITZ01 als Einlass) bis 50 s
            p = pair(gl, BayDemo::laborfall, -50);
            ru = p.cpu.runup(1220, -1, 1e9, 1e9);
            rg = p.gpu.runup(1220, -1, 1e9, 1e9);
            check(Math.abs(rg - ru) <= 0.01 * Math.abs(ru) && p.dv < 1e-5, String.format(Locale.GERMAN,
                    "Laborfall (Messwelle als Einlass), %d Schritte bis %.0f s: Auflauf %.1f m (Prozessor %.1f m, Versuch 526 m), Zufluss %.0f m³ (%.0f m³), Volumen %.1e [G 1e-5]",
                    p.steps, p.cpu.time(), rg, ru, p.gpu.inflowVolume(), p.cpu.inflowVolume(), p.dv));
            // dieselbe Bucht mit eigenem Zeitschritt auf der Grafikkarte
            Bay own = BayDemo.laborfall();
            BayGpu g = new BayGpu(own);
            g.upload(gl);
            g.run(gl, 50);
            g.sync(gl);
            g.dispose(gl);
            double ro = own.runup(1220, -1, 1e9, 1e9);
            double go = own.gauges().get(0).max(), gc = p.cpu.gauges().get(0).max();
            check(Math.abs(ro - ru) <= 0.01 * Math.abs(ru) && Math.abs(go - gc) <= 0.01 * Math.abs(gc), String.format(Locale.GERMAN, "Laborfall mit eigenem Zeitschritt der Grafikkarte: %d Schritte, Auflauf %.1f m, Pegel 1 100 m %.0f m (Prozessor %.0f m)",
                    own.steps(), ro, go, gc));

            // Rechenzeiten (Tabelle 15.2)
            System.out.println("  Rechenzeit (Prozessor: Rechenzeit der Gitter; Grafikkarte: Abfragen je Gitter, ohne die feineren)");
            timing(gl, "512² zu 4 m bei 100 m Tiefe", () -> new Bay(0, 0, 512, 512, 4).build((x, z) -> -100, 0, new Bay.Init() {
                public double eta(double x, double z) { return 5 * Math.exp(-((x - 1024) * (x - 1024) + (z - 1024) * (z - 1024)) / (2 * 200 * 200.0)); }
            }), 30);
            timing(gl, "Fjordbucht, 3 Gitter", BayDemo::fjordBay, 30);
        }
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        return bad;
    }

    /** Prozessor und Grafikkarte über denselben Fall; n > 0: Schritte, n < 0: bis −n Sekunden. */
    static Pair pair(GL4 gl, Supplier<Bay> make, int n) {
        Pair p = new Pair();
        p.cpu = make.get();
        p.gpu = make.get();
        List<Double> dts = new ArrayList<>();
        if (n > 0) for (int i = 0; i < n; i++) dts.add(p.cpu.step());
        else while (p.cpu.time() < -n) dts.add(p.cpu.step());
        BayGpu g = new BayGpu(p.gpu);
        g.upload(gl);
        for (double dt : dts) g.step(gl, dt);
        g.sync(gl);
        g.dispose(gl);
        p.steps = dts.size();
        p.dv = Math.abs(p.gpu.volume() - p.cpu.volume()) / p.cpu.initialVolume();
        List<Double> d = new ArrayList<>();
        double flow = 0;
        for (int k = 0; k < p.cpu.grids().size(); k++) {
            BayGrid a = p.cpu.grids().get(k), b = p.gpu.grids().get(k);
            double[] ha = a.depthCells(), hb = b.depthCells(), ua = a.qxCells(), ub = b.qxCells(), va = a.qzCells(), vb = b.qzCells();
            for (int j = 0; j < a.nz; j++)
                for (int i = 0; i < a.nx; i++) {
                    int c = a.id(i, j);
                    if (Double.isNaN(a.bedCells()[c])) continue;
                    if (ha[c] <= 0.01 && hb[c] <= 0.01) continue;
                    d.add(Math.abs(ha[c] - hb[c]));
                    flow = Math.max(flow, Math.max(Math.abs(ua[c] - ub[c]), Math.abs(va[c] - vb[c])));
                }
        }
        double[] s = d.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        p.depth = s.length == 0 ? new double[3] : new double[]{s[s.length - 1], s[s.length / 2], s[(int) Math.min(s.length - 1, Math.floor(0.99 * s.length))]};
        p.flow = flow;
        return p;
    }

    static final class Pair {
        Bay cpu, gpu;
        int steps;
        double dv, flow;
        double[] depth; // größte, Median, 99 %
    }

    /** Rechenzeit je Gitter auf Prozessor und Grafikkarte über n Schritte des gröbsten Gitters. */
    static void timing(GL4 gl, String name, Supplier<Bay> make, int n) {
        Bay c = make.get(), q = make.get();
        c.step();
        double[] ms0 = new double[c.grids().size()];
        for (int k = 0; k < ms0.length; k++) ms0[k] = c.grids().get(k).busyMs();
        double t0 = c.time();
        long w0 = System.nanoTime();
        for (int i = 0; i < n; i++) c.step();
        double wallC = (System.nanoTime() - w0) / 1e9, simC = c.time() - t0;
        BayGpu g = new BayGpu(q);
        g.upload(gl);
        g.step(gl);
        g.sync(gl);
        double[] gs = new double[ms0.length];
        long[] gc = new long[ms0.length];
        for (int k = 0; k < gs.length; k++) { gs[k] = g.gpuMs(k); gc[k] = g.cellSteps(k); }
        double tg0 = g.time();
        w0 = System.nanoTime();
        for (int i = 0; i < n; i++) g.step(gl);
        g.sync(gl);
        double wallG = (System.nanoTime() - w0) / 1e9, simG = g.time() - tg0;
        StringBuilder b = new StringBuilder(String.format(Locale.GERMAN, "  HINWEIS %s: Prozessor %.2f s Welle je Sekunde Rechnung, Grafikkarte %.2f s (%.1f×);", name, simC / wallC, simG / wallG,
                (simG / wallG) / (simC / wallC)));
        for (int k = 0; k < ms0.length; k++) {
            BayGrid a = c.grids().get(k);
            long cells = (long) a.nx * a.nz;
            long stepsC = n;
            for (int m = 0; m < k; m++) stepsC *= c.placement(m)[4];
            double nsC = (a.busyMs() - ms0[k]) * 1e6 / (stepsC * cells);
            double nsG = (g.gpuMs(k) - gs[k]) * 1e6 / Math.max(1, g.cellSteps(k) - gc[k]);
            b.append(String.format(Locale.GERMAN, " Gitter %d (%d × %d zu %.1f m) %.0f ns je Zelle auf dem Prozessor, %s;", k, a.nx, a.nz, a.dx, nsC,
                    nsG > 0.01 ? String.format(Locale.GERMAN, "%.1f auf der Grafikkarte", nsG) : "die Grafikkarte misst keine Zeit (Abfrage leer)"));
        }
        System.out.println(b);
        g.dispose(gl);
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) bad++;
    }
}
