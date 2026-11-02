package com.dan.debris.demo;

import com.dan.debris.Carpet;
import com.dan.debris.Debris;

import java.util.Locale;

/**
 * Prüfung von {@link Debris} und {@link Carpet} ohne Fenster (seit LituyaBay1958 Phase 12, Werkbuch 5.5): Tiefgang aus
 * der Dichte, Drift mit der Strömung, Windanteil gegen die Handrechnung, Mitreißen durch eine Welle, Stranden am flachen
 * Ufer, Stoß ohne Durchdringen mit erhaltenem Impuls, Teppich im Wind, Vollsaugen und Sinken, das Feld (Fläche genau,
 * Drift, Stau am Ufer), zwei Läufe gleich. Endet mit „Alles in Ordnung.“
 */
public final class DebrisCheck {
    static int bad, all;

    /** Offenes Wasser mit fester Tiefe und Strömung. */
    static Debris.Water open(double depth, double u, double w) {
        return new Debris.Water() {
            public double depth(double x, double z) { return depth; }

            public double[] current(double x, double z) { return new double[]{u, w}; }
        };
    }

    public static void main(String[] a) {
        long t0 = System.nanoTime();
        System.out.println("Schwimmen");
        {
            Debris d = new Debris(open(50, 0, 0));
            Debris.Log l = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, 0);
            double t = l.draft(1000), r = 0.25, y = r - t;
            double seg = (r * r * Math.acos(y / r) - y * Math.sqrt(r * r - y * y)) / (Math.PI * r * r);
            check(Math.abs(seg - 0.45) < 1e-6, fmt("Stamm 20 m × 0,5 m, 450 kg/m³: Tiefgang %.3f m (%.0f %% des Durchmessers), eingetaucht %.4f des Querschnitts = ρ/ρw 0,45 (Werkbuch 5.5: 45 %%)", t, 100 * t / 0.5, seg));
            Debris.Log ice = d.add(Debris.Kind.ICE, 3, 3, 0, 50, 0);
            check(Math.abs(ice.draft(1000) / 3 - Debris.draft(0.917, 1)) < 1e-12 && ice.draft(1000) > 0.85 * 3, fmt("Eisbrocken 917 kg/m³: Tiefgang %.2f von 3 m", ice.draft(1000)));
        }
        {
            Debris d = new Debris(open(50, 1.0, 0.5));
            Debris.Log l = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, 0.7);
            d.run(1800);
            double al = d.windage(l), f = 1 / (1 + al);
            check(Math.abs(l.vx / f - 1) < 0.01 && Math.abs(l.vz / (0.5 * f) - 1) < 0.01 && Math.abs(l.spin) < 1e-6,
                    fmt("Strömung 1,0 / 0,5 m/s, ruhende Luft: nach 30 min %.3f / %.3f m/s (die Luft bremst: c/(1 + α) = %.3f / %.3f), Drehung %.1e rad/s", l.vx, l.vz, f, 0.5 * f, l.spin));
        }
        System.out.println("Wind");
        {
            Debris d = new Debris(open(50, 0, 0));
            d.wind = (x, z, t) -> new double[]{10, 0};
            Debris.Log quer = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, Math.PI / 2), laengs = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 100, 0), krone = d.add(Debris.Kind.CROWN, 20, 0.5, 0, 200, Math.PI / 2);
            d.run(600);
            double al = d.windage(quer), want = 10 * al / (1 + al), ak = d.windage(krone);
            check(Math.abs(quer.vx / want - 1) < 0.01 && Math.abs(laengs.vx / want - 1) < 0.01 && al > 0.02 && al < 0.045,
                    fmt("Westwind 10 m/s, stilles Wasser: quer %.3f m/s, längs %.3f m/s; Hand: α = √(ρa f / (ρw t)) = %.1f %% → %.3f m/s (Werkbuch: 2–3 %% des Windes)", quer.vx, laengs.vx, 100 * al, want));
            check(Math.abs(krone.vx / (10 * ak / (1 + ak)) - 1) < 0.01 && krone.vx > quer.vx, fmt("Stamm mit Krone: %.3f m/s (%.1f %% des Windes; die Äste fangen Wind [G])", krone.vx, 100 * krone.vx / 10));
        }
        {
            // Welle: 30 m/s für 4 s, dann Ruhe – der Stamm geht mit, ohne zu überschwingen
            double[] u = {0};
            Debris d = new Debris(new Debris.Water() {
                public double depth(double x, double z) { return 20; }

                public double[] current(double x, double z) { return new double[]{u[0], 0}; }
            });
            Debris.Log l = d.add(Debris.Kind.TRUNK, 25, 0.6, 0, 0, Math.PI / 2);
            double vmax = 0;
            u[0] = 30;
            for (int i = 0; i < 40; i++) { d.step(0.1); vmax = Math.max(vmax, l.vx); }
            double v4 = l.vx;
            u[0] = 0;
            d.run(60);
            double vs = 30 / (1 + d.windage(l));
            check(Math.abs(v4 / vs - 1) < 0.01 && vmax <= vs + 1e-6 && Math.abs(l.vx) < 0.5, fmt("Welle 30 m/s für 4 s: Stamm nach 4 s %.2f m/s (c/(1 + α) = %.2f, kein Überschwingen), 56 s nach der Welle %.2f m/s", v4, vs, l.vx));
        }
        System.out.println("Ufer und Stoß");
        {
            // Strand 1:20 aus 5 m Tiefe, Strömung 0,5 m/s auflandig
            Debris d = new Debris(new Debris.Water() {
                public double depth(double x, double z) { return 5 - 0.05 * x; }

                public double[] current(double x, double z) { return new double[]{0.5, 0}; }
            });
            Debris.Log l = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, Math.PI / 2);
            d.run(600);
            double depth = 5 - 0.05 * l.x, t = l.draft(1000);
            check(l.state == Debris.State.GROUNDED && depth > 0 && depth < t && Math.hypot(l.vx, l.vz) < 0.01,
                    fmt("auflandige Strömung 0,5 m/s, Strand 1:20: liegt nach 600 s auf Grund bei %.1f m (%.3f m/s), Wasser %.3f m unter dem Tiefgang %.3f m", l.x, Math.hypot(l.vx, l.vz), depth, t));
        }
        {
            Debris d = new Debris(open(50, 0, 0));
            d.cNormal = 0;
            d.cAxial = 0;
            Debris.Log p = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, Math.PI / 2), q = d.add(Debris.Kind.TRUNK, 15, 0.7, 6, 3, Math.PI / 2 + 0.3);
            p.vx = 2;
            q.vx = -1;
            double m0x = p.mass() * p.vx + q.mass() * q.vx, e0 = 0.5 * (p.mass() * p.vx * p.vx + q.mass() * q.vx * q.vx);
            double worst = 0;
            for (int i = 0; i < 100; i++) { d.step(0.05); worst = Math.max(worst, d.worstOverlap()); }
            double m1x = p.mass() * p.vx + q.mass() * q.vx, m1z = p.mass() * p.vz + q.mass() * q.vz;
            double e1 = 0.5 * (p.mass() * (p.vx * p.vx + p.vz * p.vz) + q.mass() * (q.vx * q.vx + q.vz * q.vz));
            check(worst < 0.1 && Math.abs(m1x - m0x) < 1e-6 * Math.abs(m0x) && Math.abs(m1z) < 1e-6 * Math.abs(m0x) && e1 < e0 && d.contacts() > 0,
                    fmt("zwei Stämme stoßen (ohne Wasser): größte Durchdringung %.1f %% des Durchmessers, Impuls %.9f des Anfangs, Bewegungsenergie %.0f %% (Drehung nicht gezählt)", 100 * worst, m1x / m0x, 100 * e1 / e0));
        }
        System.out.println("Teppich");
        {
            Debris d = raft(true), free = raft(false);
            double a0 = d.hullArea();
            d.run(600);
            free.run(600);
            double a1 = d.hullArea(), af = free.hullArea();
            double[] v = d.meanVelocity();
            check(a1 < 1.15 * a0 && d.worstOverlap() < 0.1 && d.contacts() > 1000, fmt("Teppich aus 150 Stämmen (dicht, 95 × 26 m) im Westwind 10 m/s: Fläche nach 600 s %.2f des Anfangs (ohne Stöße %.2f: die Windanteile liegen nah beieinander), treibt mit %.2f m/s, %d Berührungen, größte Durchdringung %.0f %%",
                    a1 / a0, af / a0, v[0], d.contacts(), 100 * d.worstOverlap()));
        }
        {
            Debris d = new Debris(open(50, 0, 0));
            d.maxSub = 3600;
            Debris.Log l = d.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, 0);
            double day = 86400, sunk = Double.NaN, at150 = 0;
            for (int i = 1; i <= 400 && Double.isNaN(sunk); i++) {
                d.step(day);
                if (i == 150) at150 = l.density;
                if (l.state == Debris.State.SUNK) sunk = i;
            }
            double want = 150 * Math.log((1100 - 450) / 100.0);
            check(Math.abs(sunk - want) <= 1.5 && Math.abs(at150 - (1100 - 650 / Math.E)) < 1, fmt("Vollsaugen [G 150 Tage, 1 100 kg/m³]: nach 150 Tagen %.0f kg/m³, sinkt nach %.0f Tagen (Hand: τ ln(650/100) = %.0f)", at150, sunk, want));
        }
        System.out.println("Feld");
        {
            Debris.Water w = new Debris.Water() {
                public double depth(double x, double z) { return x > 1800 ? Double.NaN : 30; }

                public double[] current(double x, double z) { return new double[]{0.2, 0}; }
            };
            Carpet c = new Carpet(0, 0, 100, 40, 20, w);
            c.wind = (x, z, t) -> new double[]{10, 0};
            Debris ref = new Debris(w);
            c.like(ref, ref.add(Debris.Kind.TRUNK, 20, 0.5, 0, 0, 0));
            c.fill(200, 300, 400, 500, 0.3);
            double A0 = c.area()[0];
            double[] p0 = c.centroid();
            c.run(600);
            double[] p1 = c.centroid();
            double v = (p1[0] - p0[0]) / 600, want = 0.2 + 10 * c.windage / (1 + c.windage);
            check(Math.abs(v / want - 1) < 0.02 && Math.abs(c.area()[0] / A0 - 1) < 1e-12, fmt("Feld: treibt mit %.3f m/s (Strömung 0,2 + %.1f %% von 10 m/s = %.3f m/s), Fläche %.12f", v, 100 * c.windage / (1 + c.windage), want, c.area()[0] / A0));
            c.run(20000);
            double[] ar = c.area();
            double band = (ar[0] + ar[1]) / (c.maxCover * 200) ;
            check(Math.abs(ar[0] + ar[1] - A0) < 1e-9 * A0 && c.maxCoverNow() <= c.maxCover + 1e-12 && ar[1] > 0,
                    fmt("Stau am Ostufer: Fläche %.12f, höchste Bedeckung %.3f (höchstens %.2f), gestrandet %.0f m², Band rund %.0f m breit", (ar[0] + ar[1]) / A0, c.maxCoverNow(), c.maxCover, ar[1], band));
        }
        System.out.println("Wiederholbar");
        {
            Debris x = raft(true), y = raft(true);
            x.run(120);
            y.run(120);
            boolean same = true;
            for (int i = 0; same && i < x.logs().size(); i++) same = x.logs().get(i).x == y.logs().get(i).x && x.logs().get(i).yaw == y.logs().get(i).yaw;
            check(same, fmt("zwei Läufe gleich (%d Teilschritte, %d Berührungen)", x.steps(), x.contacts()));
            System.out.println(fmt("  HINWEIS %s: %.0f s in %.1f s Rechnung (%.1f µs je Stamm und Teilschritt)", x.describe(), x.time(), x.busyMs() / 1000, x.busyMs() * 1000 / (x.steps() * (double) x.logs().size())));
        }
        System.out.println(fmt("%d Prüfungen, %.1f s", all, (System.nanoTime() - t0) / 1e9));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** 150 Stämme dicht in 100 × 60 m, Westwind 10 m/s; mit oder ohne Stöße. */
    static Debris raft(boolean contacts) {
        Debris d = new Debris(open(50, 0, 0));
        d.wind = (x, z, t) -> new double[]{10, 0};
        d.contactFriction = contacts ? 0.5 : 0;
        java.util.Random r = new java.util.Random(7);
        int n = 0;
        for (int j = 0; j < 30 && n < 150; j++)
            for (int i = 0; i < 5 && n < 150; i++, n++) {
                double len = 12 + 6 * r.nextDouble(), dia = 0.35 + 0.4 * r.nextDouble();
                d.add(Debris.Kind.TRUNK, len, dia, i * 19 + 10 + 0.5 * r.nextDouble(), j * 0.85 + 1, 0.04 * (r.nextDouble() - 0.5));
            }
        if (!contacts) d.maxSub = 0.05;
        return contacts ? d : noContacts(d);
    }

    static Debris noContacts(Debris d) { d.contactsOn = false; return d; }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }

    static void check(boolean ok, String what) {
        all++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) bad++;
    }
}
