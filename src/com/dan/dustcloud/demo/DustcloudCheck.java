package com.dan.dustcloud.demo;

import com.dan.dustcloud.DustCloud;

import java.util.Locale;

/**
 * Prüfung von {@link DustCloud} ohne Fenster (seit LituyaBay1958 Phase 12, Werkbuch 5.4, 9.2): Sinken nach Stokes und
 * Ferguson-Church, Absetzen einer ruhenden Schicht gegen e<sup>−wt/h</sup>, Schleuse gegen das Kastenmodell nach Huppert
 * und Simpson, Hang ins Wasser, Wind, Sichtweite, Bilanz des Staubs, zwei Läufe gleich. Endet mit „Alles in Ordnung.“
 */
public final class DustcloudCheck {
    static int bad, all;

    public static void main(String[] a) {
        long t0 = System.nanoTime();
        DustCloud ref = new DustCloud(0, 0, 1, 1, 1, new DustCloud.Grain(1e-6, 1));

        System.out.println("Sinken");
        double w50 = ref.settling(50e-6), s50 = ref.stokes(50e-6), w5 = ref.settling(5e-6), s5 = ref.stokes(5e-6), w200 = ref.settling(200e-6), s200 = ref.stokes(200e-6);
        check(Math.abs(s50 - 0.2) < 0.02 && Math.abs(s5 - 0.002) < 0.0002, fmt("Stokes: 50 µm %.3f m/s, 5 µm %.2f mm/s (Werkbuch 9.2: rund 0,2 m/s und 2 mm/s)", s50, s5 * 1000));
        check(Math.abs(w5 / s5 - 1) < 0.01 && w50 < s50 && w50 > 0.85 * s50 && w200 < 0.6 * s200,
                fmt("Ferguson-Church: 5 µm %.2f mm/s (Stokes genau), 50 µm %.3f m/s (%.0f %% von Stokes), 200 µm %.2f m/s (Stokes %.2f: große Körner bremst die Trägheit)",
                        w5 * 1000, w50, 100 * w50 / s50, w200, s200));

        // ruhende Schicht über ebenem Grund: q = q₀ e^(−w t / h)
        {
            DustCloud d = new DustCloud(0, 0, 20, 20, 50, new DustCloud.Grain(50e-6, 1));
            d.build((x, z) -> 0);
            d.put(500, 500, 2000, 100, 1e-4, 0, 0);
            double q0 = d.budget()[0];
            d.run(300);
            double q1 = d.budget()[0], want = q0 * Math.exp(-d.settling(50e-6) * d.time() / 100);
            double[] bu = d.budget();
            check(Math.abs(q1 / want - 1) < 0.01 && Math.abs(bu[0] + bu[1] - q0) < 1e-9 * q0,
                    fmt("ruhende Schicht 100 m, 50 µm: nach %.0f s %.4f des Staubs in der Luft, e^(−wt/h) = %.4f; abgesetzt + in der Luft = eingebracht", d.time(), q1 / q0, want / q0));
        }

        System.out.println("Schwerestrom");
        // Schleuse: 500 m lang, 100 m hoch, c = 1e-3 (g′ 21,7 m/s²), ohne Einmischung; Kastenmodell (Huppert und Simpson 1980)
        {
            double h0 = 100, c = 1e-3, L = 500, dx = 10;
            DustCloud d = lock(dx, h0, c, L, false);
            double gp = 9.81 * (2650 - 1.2) / 1.2 * c, A = L * h0;
            d.run(120);
            double f = d.front(0, 0, 1, 0, 0.01 * h0);
            double lo = box(1.19, gp, A, L, 120), hi = box(Math.sqrt(2), gp, A, L, 120);
            check(f > 0.95 * lo && f < 1.05 * hi, fmt("Schleuse 500 × 100 m, g′ %.1f m/s²: Nase (1 m) nach 120 s bei %.0f m; Kastenmodell x = (3/2 Fr √(g′A) t + L^1,5)^(2/3) mit Fr 1,19 bis √2: %.0f bis %.0f m", gp, f, lo, hi));
            DustCloud e = lock(dx, h0, c, L, true);
            e.run(120);
            double fe = e.front(0, 0, 1, 0, 1);
            double[] ex = e.extent(1), ex0 = d.extent(1);
            check(fe < d.front(0, 0, 1, 0, 1) && ex[0] > 1.05 * ex0[0], fmt("mit Einmischung (Parker): Front %.0f m statt %.0f m, Wolke %.0f statt %.0f m³ je Meter Breite (verdünnt)", fe, d.front(0, 0, 1, 0, 1), ex[0] / 30, ex0[0] / 30));
        }

        // Hang 17° ins Wasser: die Wolke fließt hangab, über das Wasser, setzt sich dort ab
        DustCloud hang = slope();
        hang.run(240);
        {
            double fd = hang.front(500, 1000, 1, 0, 1), fu = -hang.front(500, 1000, -1, 0, 1);
            double[] bu = hang.budget();
            double sum = bu[0] + bu[1] + bu[2] + bu[3] + bu[4];
            check(fd > 1700 && fu > -150, fmt("Hang 600 m auf 2 km ins Wasser, Quelle auf 450 m: nach 240 s Front hangab %.0f m vor der Quelle (Wasser ab 1 500 m), hangauf %.0f m", fd, fu));
            check(bu[2] > 0 && bu[1] > 0, fmt("abgesetzt: an Land %.0f m³, im Wasser %.0f m³, noch in der Luft %.0f m³, über den Rand %.0f m³ (Feststoff)", bu[1], bu[2], bu[0], bu[3]));
            check(Math.abs(sum - bu[5]) < 1e-9 * bu[5] && bu[4] < 1e-6 * bu[5], fmt("Bilanz: Luft + Land + Wasser + Rand = %.6f des Eingebrachten (%.0f m³), abgeschnitten %.1e", sum / bu[5], bu[5], bu[4] / bu[5]));
        }

        System.out.println("Wind und Licht");
        {
            DustCloud d = new DustCloud(0, 0, 200, 60, 20, new DustCloud.Grain(5e-6, 1));
            d.wind = (x, z, t) -> new double[]{10, 0};
            d.build((x, z) -> 0);
            d.put(600, 600, 150, 50, 1e-7, 0, 0);
            d.run(60);
            double d60 = d.centroid()[0] - 600;
            d.run(150);
            double[] c1 = d.centroid();
            d.run(200);
            double[] c2 = d.centroid();
            double v = (c2[0] - c1[0]) / 50;
            check(Math.abs(v / 10 - 1) < 0.05 && Math.abs(c2[1] - 600) < 20, fmt("verdünnte Wolke (c 1e-7) im Westwind 10 m/s: zieht nach 150 s mit %.2f m/s (τ₀ %.0f s), quer %.0f m", v, d.windTime, c2[1] - 600));
            DustCloud dense = new DustCloud(0, 0, 200, 60, 20, new DustCloud.Grain(5e-6, 1));
            dense.wind = d.wind;
            dense.build((x, z) -> 0);
            dense.put(2000, 600, 200, 50, 1e-3, 0, 0);
            dense.run(60);
            double[] cd = dense.centroid();
            double up = dense.front(2000, 600, -1, 0, 1) - 200;
            check(up > 300 && cd[0] - 2000 < d60, fmt("dichte Wolke (c 1e-3, 50 m) im selben Wind: läuft %.0f m gegen den Wind (Schwerestrom), Schwerpunkt nach 60 s %.0f m versetzt (verdünnt %.0f m)", up, cd[0] - 2000, d60));
            DustCloud s = new DustCloud(0, 0, 5, 5, 10, new DustCloud.Grain(10e-6, 1));
            s.build((x, z) -> 0);
            s.put(25, 25, 100, 20, 1e-6, 0, 0);
            check(Math.abs(s.visibility(25, 25) - 3.912 / 0.3) < 1e-6 && Math.abs(s.opticalDepth(25, 25) - 6) < 1e-9,
                    fmt("Sicht in einer Wolke mit c 1e-6, 10 µm: %.1f m (Koschmieder 3,912/β, β = 3c/d = 0,3 /m); optische Dicke von 20 m %.1f", s.visibility(25, 25), s.opticalDepth(25, 25)));
        }

        System.out.println("Wiederholbar");
        {
            DustCloud x = slope(), y = slope();
            x.run(60);
            y.run(60);
            boolean same = true;
            float[][] sx = x.snapshot(), sy = y.snapshot();
            for (int i = 0; same && i < sx[0].length; i++) same = sx[0][i] == sy[0][i] && sx[1][i] == sy[1][i];
            check(same && x.steps() == y.steps(), fmt("zwei Läufe gleich (%d Schritte, Zelle für Zelle)", x.steps()));
        }
        System.out.println(fmt("  HINWEIS %s: %d Schritte für %.0f s in %.1f s Rechnung (%.0f ns je Zelle und Schritt)", hang.describe(), hang.steps(), hang.time(), hang.busyMs() / 1000,
                hang.busyMs() * 1e6 / (hang.steps() * (double) hang.nx * hang.nz)));
        System.out.println(fmt("%d Prüfungen, %.1f s", all, (System.nanoTime() - t0) / 1e9));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Schleuse in einem Kanal von 3 Zellen Breite (Wände außen), 6 km lang. */
    static DustCloud lock(double dx, double h0, double c, double L, boolean entrain) {
        int nx = (int) (6000 / dx);
        DustCloud d = new DustCloud(0, 0, nx, 3, dx, new DustCloud.Grain(1e-6, 1));
        d.entrain = entrain;
        d.build((x, z) -> x < 0 || z < 0 || z > 3 * dx ? Double.NaN : 0);
        for (int i = 0; i * dx < L; i++) for (int j = 0; j < 3; j++) d.put((i + 0.5) * dx, (j + 0.5) * dx, 0.45 * dx, h0, c, 0, 0);
        return d;
    }

    /** Kastenmodell, Ebene: dx/dt = Fr √(g′ A / x). */
    static double box(double fr, double gp, double A, double L, double t) { return Math.pow(1.5 * fr * Math.sqrt(gp * A) * t + Math.pow(L, 1.5), 2.0 / 3); }

    /** Hang: 600 m bei x = 0 bis 0 m bei 1 500 m (22°), dann Wasser bis 4 km; Quelle bei 500 m über 30 s. */
    static DustCloud slope() {
        DustCloud d = new DustCloud(0, 0, 100, 50, 40, DustCloud.rockDust());
        d.name = "Hang ins Wasser";
        d.wind = (x, z, t) -> new double[]{2, 1};
        d.build(new DustCloud.Ground() {
            public double height(double x, double z) { return x < 1500 ? 600 * (1 - x / 1500) : 0; }

            public boolean water(double x, double z) { return x >= 1500; }
        });
        d.source(new DustCloud.Source(500, 1000, 100, 0, 30, 50, 5e5, 20, 0));
        return d;
    }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    static void check(boolean ok, String what) {
        all++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) bad++;
    }
}
