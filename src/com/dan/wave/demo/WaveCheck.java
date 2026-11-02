package com.dan.wave.demo;

import com.dan.wave.Bay;
import com.dan.wave.BayGrid;
import com.dan.wave.Wave;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Seit Phase 8 gegen Handrechnungen: Tempo gegen √(g·h), Erhaltung des Volumens, Auflaufhöhe einer
 * Einzelwelle am flachen Strand gegen Synolakis (1987), Reflexion an einer Wand, Dämpfung am Rand und der Übergang
 * zwischen feinem und grobem Gitter. Seit LituyaBay1958 Phase 11 dazu der Löser {@link Bay} (HLL, geschachtelte Gitter):
 * ruhender See über steilem Grund mit trockener Insel über die Gittergrenze, Dammbruch nach Stoker, Auflauf nach Synolakis,
 * Volumen über drei Gitter, Welle über die Gittergrenze gegen ein durchgehend feines Gitter, Einlass (am Rand und seit
 * Phase 13 über eine Linie im Inneren), Laborfall Lituya
 * (Messwelle FRITZ01, Auflauf 526 ± 50 m), zwei gleiche Läufe. Die alten Zeilen bleiben, wie sie waren.
 */
public final class WaveCheck {
    private static int bad, all;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        System.out.println("Grundwerte");
        Wave w0 = Wave.lake();
        double c = w0.celerity(100);
        check(Math.abs(c - 31.32) < 0.01, String.format(Locale.GERMAN, "lange Welle bei 100 m Tiefe: %.1f m/s (Werkbuch: gut 30 m/s)", c));
        double dt = w0.cflStep(2, 170);
        check(dt > 0.02 && dt < 0.03, String.format(Locale.GERMAN, "Schritt auf 2 m bei 170 m Tiefe: %.3f s (Werkbuch: rund 0,03 s)", dt));
        check(Wave.STAGE == 8, "wave ist ein Löser (Stufe 8), keine Hülle mehr");

        System.out.println("Tempo");
        celerity();
        System.out.println("Erhaltung");
        volume();
        System.out.println("Auflaufen und Reflexion");
        runup();
        wall();
        System.out.println("Zwei Gitter");
        nested();
        System.out.println("Deichbruch mit gehaltenem Meer (seit Rungholt Phase 12)");
        dambreak();
        System.out.println("Bucht: HLL auf geschachtelten Gittern (seit LituyaBay1958 Phase 11)");
        bay();

        System.out.printf(Locale.GERMAN, "%d Prüfungen, %.1f s%n", all, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Ein Hügel in einem Becken von 10 m Tiefe: der Scheitel kommt nach Weg / √(g·h) am Pegel an. */
    static void celerity() {
        Wave w = Wave.basin();
        w.place(0, 0, 0, 0).build((x, z) -> 10);
        Wave.Gauge g = w.gauge("80 m", 80, 0);
        w.addVolume(0, 0, 60, 3);
        double peak = 0, tPeak = 0;
        for (int i = 0; i < 400; i++) {
            w.step(0.05);
            double e = w.levelAt(80, 0);
            if (e > peak) { peak = e; tPeak = w.time(); }
        }
        double hand = 80 / w.celerity(10);
        check(Math.abs(tPeak / hand - 1) < 0.06, String.format(Locale.GERMAN, "Scheitel nach 80 m bei 10 m Tiefe in %.2f s, √(g·h): %.2f s", tPeak, hand));
        check(g.max() > 0 && g.max() < 0.2, String.format(Locale.GERMAN, "Pegel zeichnet auf: größte Höhe %.3f m", g.max()));
    }

    /** Ein Einschlag in ein Becken mit Ufer: das Volumen bleibt, auch wenn Wasser auf- und abläuft. */
    static void volume() {
        Wave w = Wave.basin();
        // Schüssel: in der Mitte 12 m tief, nach außen Land bis 3 m hoch
        w.place(0, 0, 0, 0).build((x, z) -> 12 - 15 * (x * x + z * z) / (120.0 * 120.0));
        w.addVolume(-30, 10, 1500);
        w.step(0.1);
        double v0 = w.volume();
        for (int i = 0; i < 600; i++) w.step(0.1);
        double v1 = w.volume();
        check(Math.abs(v0 - 1500) < 1 && Math.abs(v1 / v0 - 1) < 1e-3,
                String.format(Locale.GERMAN, "Volumen nach 60 s mit Auflaufen: %.1f m³ von %.1f m³ (eingesetzt 1 500 m³)", v1, v0));
        check(w.runupAt(-118, 0) > 0 || w.runupAt(0, 117) > 0 || maxRunup(w) > 0, "Wasser ist an Land gelaufen (Auflaufen)");
    }

    static double maxRunup(Wave w) {
        double m = Double.NaN;
        for (int i = -128; i < 128; i++)
            for (int j = -128; j < 128; j += 4) {
                double r = w.runupAt(i, j);
                if (!Double.isNaN(r)) m = Double.isNaN(m) ? r : Math.max(m, r);
            }
        return m;
    }

    /**
     * Einzelwelle H/d = 0,02 auf einen Strand 1:20 (Rinne 1 m breit gerechnet): Auflaufhöhe nach Synolakis
     * R/d = 2,831·√(cot β)·(H/d)^1,25 für nicht brechende Wellen.
     */
    static void runup() {
        double d = 5, H = 0.1, slope = 1 / 20.0, toe = 300;
        Wave w = Wave.basin();
        w.fineN = 520;
        w.fineNz = 6;
        w.manning = 0;
        w.place(260, 0, 0, 0).build((x, z) -> x < toe ? d : d - (x - toe) * slope);
        double k = Math.sqrt(3 * H / (4 * d * d * d)), x0 = 120, cw = Math.sqrt(9.81 * d);
        w.initial(new Wave.Init() {
            @Override public double eta(double x, double z) { double s = 1 / Math.cosh(k * (x - x0)); return H * s * s; }
            @Override public double qx(double x, double z) { double s = 1 / Math.cosh(k * (x - x0)); double e = H * s * s; return e * cw * (1 + e / (2 * d)); }
        });
        double r = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 1200; i++) {
            w.step(0.05);
            for (double x = toe + d / slope - 10; x < toe + d / slope + 40; x += 1) {
                double ru = w.runupAt(x, 0);
                if (!Double.isNaN(ru)) r = Math.max(r, ru);
            }
        }
        double hand = d * 2.831 * Math.sqrt(1 / slope) * Math.pow(H / d, 1.25);
        check(Math.abs(r / hand - 1) < 0.25, String.format(Locale.GERMAN, "Auflaufen einer Einzelwelle 0,1 m auf 5 m, Strand 1:20: %.2f m, Synolakis: %.2f m", r, hand));
    }

    /** Eine Welle läuft gegen eine senkrechte Wand (Gletscherfront): an der Wand doppelt so hoch, dann zurück. */
    static void wall() {
        double d = 20, H = 0.05;
        Wave w = Wave.basin();
        w.fineN = 400;
        w.fineNz = 6;
        w.manning = 0;
        w.place(200, 0, 0, 0).build((x, z) -> x < 380 ? d : Double.NaN);
        double cw = Math.sqrt(9.81 * d);
        w.initial(new Wave.Init() {
            @Override public double eta(double x, double z) { return H * Math.exp(-Math.pow((x - 150) / 15, 2)); }
            @Override public double qx(double x, double z) { return eta(x, z) * cw; }
        });
        Wave.Gauge atWall = w.gauge("Wand", 379.5, 0), back = w.gauge("zurück", 150, 0);
        for (int i = 0; i < 800; i++) w.step(0.05);
        check(Math.abs(atWall.max() / (2 * H) - 1) < 0.12, String.format(Locale.GERMAN, "an der Wand %.3f m, doppelte Höhe %.3f m", atWall.max(), 2 * H));
        float[][] s = back.series(40);
        double late = 0;
        for (int q = 0; q < s[0].length; q++) if (s[0][q] > 2 * 230 / cw - 2 && !Float.isNaN(s[1][q])) late = Math.max(late, s[1][q]);
        check(late > 0.8 * H, String.format(Locale.GERMAN, "die Welle kommt zurück: %.3f m (ausgesandt %.3f m)", late, H));
    }

    /** Feines Gitter im groben: die Welle läuft hinüber, ohne viel zurückzuwerfen, und der Rand dämpft sie. */
    static void nested() {
        Wave w = Wave.lake();
        w.fineN = 128;
        w.coarseN = 256;
        w.place(0, 0, 0, 0).build((x, z) -> 50);
        Wave.Gauge mid = w.gauge("Mitte", 0, 0), out = w.gauge("draußen", 600, 0);
        w.addVolume(0, 0, 4000, 20);
        double first = 0, rest = 0, tPeak = 0, pk = 0, v10 = 0;
        for (int i = 0; i < 800; i++) {
            w.step(0.05);
            double t = w.time(), e60 = Math.abs(w.levelAt(60, 0)), e600 = w.levelAt(600, 0);
            if (t < 4) first = Math.max(first, e60);
            if (t > 8 && t < 14) rest = Math.max(rest, e60);
            if (e600 > pk) { pk = e600; tPeak = t; }
            if (v10 == 0 && t >= 10) v10 = w.volume();
        }
        double hand = 600 / w.celerity(50);
        check(Math.abs(tPeak / hand - 1) < 0.08, String.format(Locale.GERMAN, "aus dem feinen ins grobe Gitter: Scheitel nach 600 m in %.1f s, √(g·h): %.1f s", tPeak, hand));
        check(rest < 0.1 * first, String.format(Locale.GERMAN, "zurückgeworfen am Übergang: %.4f m gegen %.4f m im ersten Durchgang", rest, first));
        check(Math.abs(v10 / 4000 - 1) < 1e-3, String.format(Locale.GERMAN, "Volumen über beide Gitter nach 10 s: %.1f m³ von 4 000 m³", v10));
        for (int i = 0; i < 1000; i++) w.step(0.05);
        check(w.maxHeight() < 0.05 * first || !w.active(), String.format(Locale.GERMAN, "der Saum am Rand dämpft: nach 90 s %.5f m (anfangs %.4f m)%s", w.maxHeight(), first, w.active() ? "" : ", See ruhig"));
        // Rechenzeit im See (Werkbuch Abschnitt 10)
        Wave big = Wave.lake();
        big.place(0, 0, 0, 0).build((x, z) -> 100);
        big.addVolume(0, 0, 20000);
        long t0 = System.nanoTime();
        for (int i = 0; i < 100; i++) big.step(0.05);
        double ms = (System.nanoTime() - t0) / 1e6 / 5;
        check(ms < 400, String.format(Locale.GERMAN, "512² und 1 024² bei 100 m Tiefe: %.0f ms Rechnung je Sekunde Welle (eigener Faden)", ms));
    }

    /**
     * Dammbruch nach Ritter: links ein Meer 2 m tief, festgehalten (Hold), rechts trockener Grund auf Höhe der Sohle.
     * Durchfluss am Bruch q = (8/27)·√(g h0³) = 2,62 m²/s, Stirn mit 2√(g h0) = 8,9 m/s (ohne Reibung).
     */
    static void dambreak() {
        Wave w = new Wave();
        w.coarseN = 0;
        w.fineN = 200;
        w.fineNz = 40;
        w.fineCell = 1;
        w.spongeCells = 0;
        w.manning = 0;
        w.edgeDamping = 0;
        double h0 = 2;
        w.place(100, 20, 0, 0).build((x, z) -> h0);   // Sohle überall 2 m unter dem Ruhespiegel
        w.initial((x, z) -> x < 60 ? 0 : -h0);         // rechts von x = 60 trocken
        w.hold(new Wave.Hold() {
            public boolean held(double x, double z) { return x < 20; }
            public double eta(double t) { return 0; }
        });
        double v0 = 0, v1 = 0, front = 0;
        for (int k = 0; k < 120; k++) {
            w.step(0.05);
            if (k == 59) v0 = wetVolume(w, 60);
        }
        v1 = wetVolume(w, 60);
        for (double x = 60; x < 200; x += 0.5) if (water(w, x, 20, h0) > 0.01) front = x;
        double q = (v1 - v0) / 3 / 40, ritter = 8.0 / 27 * Math.sqrt(9.81 * h0 * h0 * h0);
        double hb = water(w, 10, 20, h0);
        check(q / ritter > 0.8 && q / ritter < 1.5 && front > 60 + 15 && Math.abs(hb - h0) < 0.05 && w.heldCount() == 20 * 40,
                String.format(Locale.GERMAN, "Durchfluss am Bruch %.2f m²/s (Ritter %.2f), Stirn (über 1 cm) nach 6 s bei %.0f m (Ritter, bis zur Spitze: %.0f m), das gehaltene Meer bleibt %.2f m tief",
                        q, ritter, front - 60, 6 * 2 * Math.sqrt(9.81 * h0), hb));
    }

    /** Wassertiefe über einer Sohle h0 unter dem Ruhespiegel (levelAt ist NaN, wo es trocken ist). */
    static double water(Wave w, double x, double z, double h0) { double l = w.levelAt(x, z); return Double.isNaN(l) ? 0 : Math.max(0, l + h0); }

    /** Wasser rechts von x0 (m³) im feinen Gitter (1 m Zellen, 40 m breit). */
    static double wetVolume(Wave w, double x0) {
        double v = 0;
        for (double x = x0 + 0.5; x < 200; x += 1) for (double z = 0.5; z < 40; z += 1) v += water(w, x, z, 2);
        return v;
    }


    // ------------------------------------------------------------------ Bay (seit LituyaBay1958 Phase 11)

    static void bay() {
        // ruhender See über steilem, welligem Grund mit trockener Insel, zwei Gitter
        {
            Bay s = new Bay(0, 0, 100, 80, 10);
            s.nest(30, 25, 30, 25, 4);
            s.build((x, z) -> -20 + 15 * Math.sin(x / 40) * Math.cos(z / 55) + 30 * Math.exp(-((x - 500) * (x - 500) + (z - 400) * (z - 400)) / 8000.0), 0, null);
            for (int i = 0; i < 200; i++) s.step();
            double vmax = 0;
            for (BayGrid g : s.grids()) for (int j = 0; j < g.nz; j++) for (int i = 0; i < g.nx; i++) { double[] v = g.velocity(i, j); vmax = Math.max(vmax, Math.hypot(v[0], v[1])); }
            check(vmax < 1e-10 && Math.abs(s.volume() / s.initialVolume() - 1) < 1e-12,
                    String.format(Locale.GERMAN, "ruhender See über welligem Grund (±15 m) mit trockener Insel über die Gittergrenze (10 und 2,5 m): nach %.0f s höchstens %.1e m/s, Volumen genau", s.time(), vmax));
        }
        // Dammbruch nach Stoker
        {
            Bay d = new Bay(0, 0, 2000, 3, 1);
            d.manning = 0;
            d.build((x, z) -> 0, 0, new Bay.Init() { public double eta(double x, double z) { return x < 1000 ? 10 : 2; } });
            d.run(40);
            double hl = 10, hr = 2, g = 9.81, hm = 4;
            for (int it = 0; it < 100; it++) {
                double f = stoker(hm, hl, hr, g), f1 = stoker(hm + 1e-6, hl, hr, g);
                hm -= f / ((f1 - f) / 1e-6);
            }
            double num = d.grids().get(0).depth(1250, 1);
            check(Math.abs(num / hm - 1) < 0.01 && Math.abs(d.volume() / d.initialVolume() - 1) < 1e-12,
                    String.format(Locale.GERMAN, "Dammbruch 10 auf 2 m (Stoker): Tiefe zwischen Schwall und Verdünnung %.3f m, genau %.3f m; Volumen genau", num, hm));
        }
        // Auflauf nach Synolakis
        {
            double d = 10, hh = 0.019 * d, k = Math.sqrt(3 * hh / (4 * d * d * d)), c = Math.sqrt(9.81 * (d + hh)), xb = 600, x0 = xb - 5 / k, xs = x0 - 6 / k, dx = 0.5;
            int nx = (int) Math.ceil((xb + 2.5 * d * 19.85 - xs) / dx);
            Bay s = new Bay(xs, 0, nx, 3, dx);
            s.manning = 0;
            s.edge(Bay.W, BayGrid.Edge.OPEN);
            s.build((x, z) -> x < xb ? -d : -d + (x - xb) / 19.85, 0, new Bay.Init() {
                public double eta(double x, double z) { double ch = 1 / Math.cosh(k * (x - x0)); return hh * ch * ch; }
                public double[] q(double x, double z) { return new double[]{c * eta(x, z), 0}; }
            });
            s.run(100);
            double r = s.runup(xb, -1, 1e9, 1e9) / d, hand = 2.831 * Math.sqrt(19.85) * Math.pow(0.019, 1.25);
            check(Math.abs(r / hand - 1) < 0.06, String.format(Locale.GERMAN, "Einzelwelle H/d = 0,019 auf einem Strand 1:19,85: Auflauf R/d %.4f, Synolakis 2,831 √cot β (H/d)^1,25 = %.4f", r, hand));
        }
        // drei Gitter: Volumen; Welle über die Grenze gegen ein durchgehend feines Gitter
        {
            Bay.Init hump = new Bay.Init() { public double eta(double x, double z) { return 3 * Math.exp(-((x - 300) * (x - 300) + (z - 500) * (z - 500)) / (2 * 40 * 40)); } };
            Bay n = new Bay(0, 0, 125, 125, 8);
            n.manning = 0;
            n.nest(50, 40, 40, 45, 4).nest(40, 40, 60, 60, 2);
            n.build((x, z) -> -30 + 25 * Math.max(0, (x - 700) / 300.0), 0, hump);
            Bay f = new Bay(0, 0, 500, 500, 2);
            f.manning = 0;
            f.build((x, z) -> -30 + 25 * Math.max(0, (x - 700) / 300.0), 0, hump);
            Bay.Gauge gn = n.gauge("520", 520, 500), gf = f.gauge("520", 520, 500), gn2 = n.gauge("650", 650, 500), gf2 = f.gauge("650", 650, 500);
            n.run(30);
            f.run(30);
            check(Math.abs(n.volume() / n.initialVolume() - 1) < 1e-12 && n.clipped() < 1e-9,
                    String.format(Locale.GERMAN, "drei Gitter (8, 2, 1 m) mit Unterschritten und Abgleich der Flüsse: Volumen nach 30 s %.12f, nichts abgeschnitten", n.volume() / n.initialVolume()));
            check(Math.abs(gn.max() / gf.max() - 1) < 0.08 && Math.abs(gn2.max() / gf2.max() - 1) < 0.08,
                    String.format(Locale.GERMAN, "Welle über die Grenze ins feine Gitter: bei 520 m %.3f m (durchgehend 2 m: %.3f), bei 650 m %.3f m (%.3f); der Rest ist der Weg über das 8-m-Gitter",
                            gn.max(), gf.max(), gn2.max(), gf2.max()));
        }
        // Fjordbucht der Demo: drei Gitter, Trockenfronten über die Gittergrenzen, 50 s
        {
            Bay f = BayDemo.fjordBay();
            f.run(50);
            double dv = (f.volume() - f.initialVolume()) / f.initialVolume();
            Bay.Gauge arm = f.gauges().get(0), stamm = f.gauges().get(1);
            check(Math.abs(dv) < 5e-5 && f.steps() < 800 && arm.max() > 10 && arm.max() < 60 && stamm.max() > 1,
                    String.format(Locale.GERMAN, "Fjordbucht der Demo (20, 5, 2,5 m), Hügel 100 m im Arm, 50 s: Pegel Arm %.1f m nach %.1f s, Stamm %.1f m nach %.1f s; %d Schritte (stabil), Volumen %+.1e",
                            arm.max(), arm.timeOfMax(), stamm.max(), stamm.timeOfMax(), f.steps(), dv));
            if (Math.abs(dv) > 1e-12)
                System.out.println(String.format(Locale.GERMAN, "  HINWEIS Volumen %+.1e (%.0f m³): an Trockenfronten über Gittergrenzen zieht das feine Gitter mehr, als die grobe Zelle davor hat; abgeschnitten und gezählt (Rückgabe an die feinen Randzellen machte die Bucht instabil)", dv, f.clipped()));
        }
        // Einlass und Laborfall Lituya
        {
            double h = 122, hh = 152, c = Math.sqrt(9.81 * (h + hh)), k = Math.sqrt(3 * hh / (4 * h * h * h)), dx = 2, xa = 885, foot = 1220, top = 650;
            int nx = (int) Math.ceil((1342 + top + 50 - xa) / dx);
            Bay s = new Bay(xa, 0, nx, 3, dx);
            s.inlet(Bay.W, (t, x, z) -> { double ch = 1 / Math.cosh(k * c * (t - 16)); double e = hh * ch * ch; return new double[]{e, c * e}; });
            s.build((x, z) -> x < foot ? -h : Math.min(top, -h + (x - foot)), 0, null);
            s.run(25);
            double dv = s.volume() - s.initialVolume();
            check(Math.abs(dv / s.inflowVolume() - 1) < 1e-9 && s.inflowVolume() > 30000,
                    String.format(Locale.GERMAN, "Einlass: %.0f m³ je 2 m Breite herein, im Gitter %.0f m³ mehr (genau)", s.inflowVolume(), dv));
            s.run(50);
            double r = s.runup(foot, -1, 1e9, 1e9);
            check(Math.abs(r - 526) <= 50, String.format(Locale.GERMAN, "Laborfall Lituya (FRITZ01): Einzelwelle 152 m auf 122 m bei 885 m nach 16 s als Einlass, Gegenhang 45° ab 1 220 m: Auflauf %.0f m (FRITZ01 526 ± 50 m, Hall-Watts 524 m)", r));
            // dieselbe Welle über eine Linie im Inneren (seit Phase 13): davor 40 m Fels, die Linie bei 886 m
            Bay li = new Bay(xa - 40, 0, nx + 20, 3, dx);
            li.line(xa + 1, -10, xa + 1, 20, 1, 0, (t, x, z) -> { double ch = 1 / Math.cosh(k * c * (t - 16)); double e = hh * ch * ch; return new double[]{e, c * e}; });
            li.build((x, z) -> x < xa ? Double.NaN : x < foot ? -h : Math.min(top, -h + (x - foot)), 0, null);
            li.run(50);
            double rl = li.runup(foot, -1, 1e9, 1e9);
            check(Math.abs(rl - r) <= 0.02 * r && li.forcedCells(0).length == 3, String.format(Locale.GERMAN, "dieselbe Welle über eine Linie im Inneren (Bay.line, %d Zellen, Fels davor): Auflauf %.0f m gegen %.0f m über den Rand", li.forcedCells(0).length, rl, r));
            Bay s2 = new Bay(xa, 0, nx, 3, dx);
            s2.inlet(Bay.W, (t, x, z) -> { double ch = 1 / Math.cosh(k * c * (t - 16)); double e = hh * ch * ch; return new double[]{e, c * e}; });
            s2.build((x, z) -> x < foot ? -h : Math.min(top, -h + (x - foot)), 0, null);
            s2.run(10);
            Bay s3 = new Bay(xa, 0, nx, 3, dx);
            s3.inlet(Bay.W, (t, x, z) -> { double ch = 1 / Math.cosh(k * c * (t - 16)); double e = hh * ch * ch; return new double[]{e, c * e}; });
            s3.build((x, z) -> x < foot ? -h : Math.min(top, -h + (x - foot)), 0, null);
            s3.run(10);
            boolean same = s2.steps() == s3.steps();
            BayGrid g2 = s2.grids().get(0), g3 = s3.grids().get(0);
            for (int i = 0; same && i < g2.nx; i++) same = g2.depth(i, 1) == g3.depth(i, 1);
            check(same, String.format(Locale.GERMAN, "zwei Läufe gleich (%d Schritte, Zelle für Zelle)", s2.steps()));
        }
        // Rechenzeit
        {
            Bay big = new Bay(0, 0, 512, 512, 4);
            big.build((x, z) -> -100, 0, new Bay.Init() { public double eta(double x, double z) { return 5 * Math.exp(-((x - 1024) * (x - 1024) + (z - 1024) * (z - 1024)) / (2 * 200 * 200.0)); } });
            big.step();
            long t0 = System.nanoTime();
            for (int i = 0; i < 10; i++) big.step();
            double ms = (System.nanoTime() - t0) / 1e6 / 10;
            System.out.println(String.format(Locale.GERMAN, "  HINWEIS Rechenzeit auf dem Prozessor: 512² zu 4 m bei 100 m Tiefe %.0f ms je Schritt (%.0f ns je Zelle), %.2f s Welle je Sekunde Rechnung; die Grafikkarte: BayGpuCheck (seit Phase 11b)",
                    ms, ms * 1e6 / (512 * 512), big.time() / big.steps() / (ms / 1000)));
        }
    }

    static double stoker(double hm, double hl, double hr, double g) {
        double um = 2 * (Math.sqrt(g * hl) - Math.sqrt(g * hm)), s = Math.sqrt(g * hm * (hm + hr) / (2 * hr));
        return um - s * (1 - hr / hm);
    }

    static void check(boolean c, String what) {
        all++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private WaveCheck() { }
}
