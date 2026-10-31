package com.dan.fracture.demo;

import com.dan.fracture.Fracture;
import com.dan.fracture.Material;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Shape;
import com.dan.fracture.Solid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Phase 6: Zellen, Säule, Balken und Kragplatte gegen die Handrechnung, Fall, Reibung, Auftrieb,
 * seit Sylt Phase 11 Lockergestein (Kohäsion, Feuchte, Steilwand, Hohlkehle, Zerfall, Glacier unverändert), seit Sylt
 * Phase 12 der Bestand ({@code Solid.stabilize}),
 * Stapel ohne Durchdringung, 1 000 Stücke im Budget, gleicher Seed – gleicher Bruch.
 */
public final class FractureCheck {
    private static int bad, count;
    static final double DT = 1.0 / 120;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        section("Hülle");
        Fracture f0 = Fracture.ice();
        check(Math.abs(f0.freeFall(50) - 3.19) < 0.01, fmt("freier Fall aus 50 m: %.2f s", f0.freeFall(50)));
        check(Fracture.rock().material.density > Fracture.concrete().material.density && Fracture.concrete().material.density > f0.material.density,
                "Presets: Fels schwerer als Beton schwerer als Eis");
        check(f0.onCrack == null && f0.onBreak == null && f0.onImpact == null && f0.water == null && f0.ground == null, "ohne Zuhörer, Wasser und Boden startklar");
        check(Fracture.STAGE == 6 && f0.describe().contains("Engine"), "Stand: Engine (Phase 6)");
        Material ice = Material.ice();
        double c = ice.cohesion(), p = Math.toRadians(ice.frictionAngle);
        check(Math.abs(2 * c * Math.cos(p) / (1 - Math.sin(p)) - ice.compressive) < 1, fmt("Mohr-Coulomb: Kohäsion %.2f MPa gibt σc = %.1f MPa", c / 1e6, ice.compressive / 1e6));

        section("Zellen");
        double[] m = Poly.box(0, 0, 0, 2, 3, 4, -1).mass();
        check(Math.abs(m[0] - 24) < 1e-9 && Math.abs(m[4] - 50) < 1e-9 && Math.abs(m[6] - 26) < 1e-9, "Quader 2 × 3 × 4: Volumen 24, Trägheit 50 / 40 / 26 (Handrechnung)");
        Fracture fv = Fracture.ice();
        Solid sv = fv.addSolid(Shape.box(0, 0, 0, 3, 7, 2), 300);
        double vol = 0;
        for (int i = 0; i < sv.cells(); i++) vol += sv.volume(i);
        check(Math.abs(vol - 42) < 1e-6, fmt("%d Zellen füllen den Quader ohne Lücke: %.6f von 42 m³", sv.cells(), vol));
        int sym = 0;
        for (int b = 0; b < sv.bonds(); b++) if (sv.bondArea(b) > 0 && sv.bondB(b) > sv.bondA(b)) sym++;
        check(sym == sv.bonds() && sv.bonds() > 3 * sv.cells(), fmt("%,d Nachbarflächen, je Zelle %.1f, alle mit Fläche", sv.bonds(), 2.0 * sv.bonds() / sv.cells()));
        Solid sv2 = Fracture.ice().addSolid(Shape.box(0, 0, 0, 3, 7, 2), 300);
        Fracture fs = Fracture.ice();
        fs.seed = 2;
        Solid sv3 = fs.addSolid(Shape.box(0, 0, 0, 3, 7, 2), 300);
        check(java.util.Arrays.equals(sv.seedOf(17), sv2.seedOf(17)) && !java.util.Arrays.equals(sv.seedOf(17), sv3.seedOf(17)), "gleicher Seed, gleiche Zellen; anderer Seed, andere");
        Solid dense = Fracture.ice().addSolid(Shape.box(0, 0, 0, 10, 2, 2), 300, (x, y, z) -> x > 7 ? 1 : 0.15, null);
        int fine = 0;
        for (int i = 0; i < dense.cells(); i++) if (dense.center(i)[0] > 7) fine++;
        check(fine > dense.cells() * 0.5, fmt("Dichte nach Ort: %d von %d Zellen im feinen Drittel", fine, dense.cells()));

        section("Spannung");
        // Säule: Bruchlast gegen σc · A − Eigengewicht (Werkbuch: auf 10 %)
        double worst = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Fracture f = Fracture.ice();
            f.seed = seed;
            f.material.scatter = 0;
            Solid s = f.addSolid(Shape.box(0, 0, 0, 2, 10, 2), 300);
            s.anchorBelow(0);
            double lo = 0, hi = 60e6;
            for (int it = 0; it < 24; it++) {
                double F = (lo + hi) / 2;
                s.pressure(q -> q.ny > 0.99 && q.p[1] > 9.999, F / 4);
                if (s.solve() > 1) hi = F; else lo = F;
            }
            double hand = 5e6 * 4 - 917 * 9.81 * 40;
            worst = Math.max(worst, Math.abs(lo / hand - 1));
            if (seed == 1) {
                // Gleichgewicht im Schnitt y = 5: die Kraft, die der untere Teil auf den oberen ausübt
                s.pressure(q -> q.ny > 0.99 && q.p[1] > 9.999, 1e6 / 4);
                s.solve();
                double Fy = 0;
                for (int b = 0; b < s.bonds(); b++) {
                    double[] a = s.seedOf(s.bondA(b)), bb = s.seedOf(s.bondB(b));
                    if ((a[1] - 5) * (bb[1] - 5) >= 0) continue;
                    double fy = s.forceOf(b)[1];
                    Fy += bb[1] > a[1] ? fy : -fy;
                }
                double want = 1e6 + 917 * 9.81 * 4 * 5;
                check(Math.abs(Fy / want - 1) < 0.01, fmt("Säule: Kraft im Schnitt %.4g N, Handrechnung %.4g N", Fy, want));
            }
        }
        check(worst < 0.10, fmt("Säule 2 × 2 × 10 m Eis: Bruchlast auf %.1f %% an σc·A − G (Werkbuch: 10 %%)", worst * 100));
        // Balken: Kragarm aus der Wand, Eigengewicht
        {
            Fracture f = Fracture.ice();
            f.material.scatter = 0;
            double L = 10, h = 2, b = 2;
            Solid s = f.addSolid(Shape.box(0, 0, 0, L, h, b), 800);
            s.anchor(q -> q.nx < -0.99 && Math.abs(q.p[0]) < 1e-6);
            s.solve();
            double M = 0, xc = 3;
            for (int k = 0; k < s.bonds(); k++) {
                double[] a = s.seedOf(s.bondA(k)), bb = s.seedOf(s.bondB(k));
                if ((a[0] - xc) * (bb[0] - xc) >= 0) continue;
                double[] fv2 = s.forceOf(k), mv = s.momentOf(k), cc = s.bondCenter(k);
                double sg = bb[0] > a[0] ? 1 : -1;
                M += sg * ((cc[0] - xc) * fv2[1] - (cc[1] - h / 2) * fv2[0] + mv[2]);
            }
            double Mhand = 917 * 9.81 * b * h * (L - xc) * (L - xc) / 2;
            check(Math.abs(M / Mhand - 1) < 0.01, fmt("Balken: Moment im Schnitt %.4g N·m, Handrechnung ρ·g·b·h·a²/2 = %.4g N·m", M, Mhand));
            double maxT = 0;
            for (int k = 0; k < s.bonds(); k++) { double[] st = s.stressOf(k); maxT = Math.max(maxT, st[0] + st[2]); }
            double hand = 3 * 917 * 9.81 * L * L / h;
            check(Math.abs(maxT / hand - 1) < 0.15, fmt("Balken: größte Zugspannung %.2f MPa, Handrechnung 3ρgL²/h = %.2f MPa (auf 15 %%)", maxT / 1e6, hand / 1e6));
        }
        // Kragplatte: bricht erst ab der Länge der Handrechnung, und der Riss meldet sich vorher
        {
            double h = 4, ft = 2e5;
            double Lc = Math.sqrt(ft * h / (3 * 900 * 9.81));
            double[] held = new double[2];
            double[] tCrack = {Double.NaN}, tBreak = {Double.NaN};
            for (int r = 0; r < 2; r++) {
                double L = r == 0 ? 0.75 * Lc : 1.3 * Lc;
                Fracture f = Fracture.glacier();
                f.material.tensile = ft;
                f.material.scatter = 0;
                f.ground = (x, z) -> x < 0 ? 0.0 : -30.0;
                Solid s = f.addSolid(Shape.box(-12, 0, -2, L, h, 2), 500, (x, y, z) -> x > -4 ? 1 : 0.3, null);
                s.anchor(q -> q.ny < -0.99 && q.p[1] < 1e-6 && q.centroid()[0] < 0, 3);
                s.support(q -> q[0] < -10);
                final int rr = r;
                f.onCrack = (pth, e, br) -> { if (rr == 1 && Double.isNaN(tCrack[0])) tCrack[0] = f.time(); };
                f.onBreak = (id, v, mm) -> { if (rr == 1 && Double.isNaN(tBreak[0])) tBreak[0] = f.time(); };
                for (int i = 0; i < 120 * 4; i++) f.step(DT);
                double free = 0;
                for (Piece q : f.pieces()) free += q.volume();
                held[r] = free;
            }
            check(held[0] == 0, fmt("Kragplatte h = 4 m, Zug 200 kPa: hält mit 0,75 · L_c (L_c = √(f_t h / 3ρg) = %.1f m)", Lc));
            check(held[1] > 0.5 * 1.3 * Lc * h * 4, fmt("Kragplatte: bricht mit 1,3 · L_c ab (%.0f m³ frei, Überhang %.0f m³)", held[1], 1.3 * Lc * h * 4));
            check(tCrack[0] < tBreak[0], fmt("der Riss meldet sich vor dem Bruch: Riss %.2f s, frei %.2f s", tCrack[0], tBreak[0]));
        }

        section("Starrkörper");
        {
            Fracture f = Fracture.ice();
            f.ground = (x, z) -> 0.0;
            Piece p0 = f.addPiece(Shape.box(-1, 0, -1, 1, 2, 1), 1);
            p0.place(0, 51, 0);
            double t = 0;
            double deepest = 0;
            while (p0.position()[1] - 1 > 0.02 && t < 10) { f.step(DT); t += DT; }
            for (int i = 0; i < 30; i++) { f.step(DT); deepest = Math.max(deepest, -lowest(p0)); }
            check(Math.abs(t / f.freeFall(50) - 1) < 0.02, fmt("Fall aus 50 m bis zum Boden: %.2f s, Handrechnung √(2h/g) = %.2f s", t, f.freeFall(50)));
            check(deepest < 0.03, fmt("schlägt mit 31 m/s auf und dringt höchstens %.3f m in den Boden", deepest));
            for (int i = 0; i < 600; i++) f.step(DT);
            check(Math.abs(p0.position()[1] - 1) < 0.02 && p0.asleep(), fmt("liegt danach auf dem Boden (Mitte %.3f m, 1 m erwartet) und schläft", p0.position()[1]));
        }
        for (double mu : new double[]{0.5, 0.2}) {
            Fracture f = Fracture.ice();
            f.material.friction = mu;
            double a = Math.toRadians(20);
            f.ground = (x, z) -> -Math.tan(a) * x;
            Piece q = f.addPiece(Shape.box(-1, 0, -1, 1, 1, 1), 1);
            q.turn(0, 0, 1, -a);
            q.place(0, 0.5 / Math.cos(a) + 0.01, 0);
            for (int i = 0; i < 240; i++) f.step(DT);
            double x = q.position()[0], hand = Math.max(0, 0.5 * 9.81 * (Math.sin(a) - mu * Math.cos(a)) * 4 * Math.cos(a));
            check(Math.abs(x - hand) < Math.max(0.05, 0.05 * hand), fmt("Reibung μ = %.1f am 20°-Hang: %.2f m in 2 s, Handrechnung %.2f m", mu, x, hand));
        }
        {
            Fracture f = Fracture.ice();
            f.water = (x, z, t) -> 0.0;
            List<double[]> hits = new ArrayList<>();
            f.onImpact = (x, y, z, v, s, w) -> { if (w) hits.add(new double[]{v, s}); };
            Piece q = f.addPiece(Shape.box(-1, -1, -1, 1, 1, 1), 1);
            q.place(0, 4, 0);
            for (int i = 0; i < 120 * 25; i++) f.step(DT);
            check(Math.abs(q.submergedShare() - 0.917) < 0.03, fmt("Auftrieb: Eiswürfel schwimmt mit %.3f unter Wasser (917/1000 = 0,917)", q.submergedShare()));
            double dv = 0;
            for (double[] h : hits) dv += h[0];
            check(!hits.isEmpty() && dv > 0.5 * 8 * 0.917 && dv < 1.6 * 8, fmt("Einschlag ins Wasser gemeldet: %d Schritte, %.1f m³ verdrängt", hits.size(), dv));
        }
        {
            Fracture f = Fracture.ice();
            f.ground = (x, z) -> 0.0;
            f.seed = 3;
            Random r = new Random(1);
            for (int i = 0; i < 200; i++) {
                Piece q = f.addPiece(Shape.box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5), 1);
                q.place((i % 5) * 1.8 - 3.6 + r.nextGaussian() * 0.2, 1 + (i / 25) * 1.8, ((i / 5) % 5) * 1.8 - 3.6 + r.nextGaussian() * 0.2);
                q.turn(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble() * 3);
            }
            double E0 = energy(f), Emax = E0, minY = 1e9, late = 0;
            for (int i = 0; i < 120 * 30; i++) {
                f.step(DT);
                if (i % 12 == 0) {
                    Emax = Math.max(Emax, energy(f));
                    for (Piece q : f.pieces()) minY = Math.min(minY, lowest(q));
                }
                if (i > 120 * 25) late = Math.max(late, f.maxPenetration());
            }
            check(minY > -0.03, fmt("200 Würfel fallen aus bis zu 14 m auf einen Haufen: keine Ecke tiefer als %.3f m im Boden", -minY));
            check(late < 0.05, fmt("im ruhenden Haufen dringt nichts tiefer als %.3f m ein", late));
            check(Emax < E0 * 1.01, fmt("keine Energie aus dem Nichts: höchstens %.2f %% der Anfangsenergie", Emax / E0 * 100));
            check(f.awake() < 20, fmt("nach 30 s ruhen %d von 200", 200 - f.awake()));
        }
        {
            Fracture f = Fracture.ice();
            f.ground = (x, z) -> 0.0;
            Solid s = f.addSolid(Shape.box(-0.6, 0, -0.6, 0.6, 6, 0.6), 200);
            s.anchorBelow(0, 2);
            f.material = Material.rock();
            Piece blk = f.addPiece(Shape.box(-1.5, 0, -1.5, 1.5, 1.5, 1.5), 1);
            blk.place(0, 6.76, 0);
            for (int i = 0; i < 600; i++) f.step(DT);
            double Fy = 0;
            for (int b = s.bonds(); b < s.bonds() + s.anchors(); b++) Fy += s.forceOf(b)[1];
            double want = -(blk.mass() + 917 * 1.44 * 6) * 9.81;
            check(Math.abs(Fy / want - 1) < 0.03 && blk.asleep(), fmt("ein ruhender Block lastet auf dem Pfeiler: Anker tragen %.0f kN, Handrechnung %.0f kN", -Fy / 1e3, -want / 1e3));
        }
        {
            int[] parts = new int[2];
            for (int r = 0; r < 2; r++) {
                Fracture f = Fracture.rock();
                f.material.tensile = 4e5;
                f.material.compressive = 6e6;
                f.ground = (x, z) -> 0.0;
                Piece q = f.addPiece(Shape.box(-1.2, -1, -1, 1.2, 1, 1), 14);
                q.place(0, 1.02, 0);
                q.push(0, r == 0 ? -1 : -12, 0);
                for (int i = 0; i < 240; i++) f.step(DT);
                parts[r] = f.pieces().size();
            }
            check(parts[0] == 1 && parts[1] >= 2, fmt("Fels (Zug 400 kPa) schlägt auf: mit 1 m/s bleibt er ganz, mit 12 m/s bricht er in %d Stücke", parts[1]));
        }

        section("Lockergestein (seit Sylt Phase 11)");
        lockergestein();

        section("Wiederholbarkeit und Leistung");
        {
            double[] h = new double[2];
            for (int r = 0; r < 2; r++) {
                FractureScene sc = new FractureScene();
                sc.load(FractureScene.Case.FELS);
                sc.trigger();
                sc.step(6);
                double acc = 0;
                for (Piece q : sc.world.pieces()) { double[] ps = q.position(); acc += q.id * 31.7 + ps[0] * 1.3 + ps[1] * 2.9 + ps[2] * 0.7; }
                h[r] = acc + sc.world.pieces().size() * 1000;
            }
            check(h[0] == h[1], "gleicher Seed, gleicher Sturz: zwei Läufe des Felssturzes enden Stück für Stück gleich");
        }
        {
            Fracture f = Fracture.ice();
            f.water = (x, z, t) -> 0.0;
            f.ground = (x, z) -> -30 + 0.1 * x;
            f.seed = 5;
            Random r = new Random(2);
            for (int i = 0; i < 1000; i++) {
                double s = 0.5 + r.nextDouble() * 1.5;
                Piece q = f.addPiece(Shape.box(-s, -s * 0.7, -s, s, s * 0.7, s), 1);
                q.place(r.nextDouble() * 120 - 60, 2 + r.nextDouble() * 25, r.nextDouble() * 120 - 60);
                q.turn(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble() * 3);
            }
            for (int i = 0; i < 120; i++) f.step(DT);
            long a = System.nanoTime();
            for (int i = 0; i < 240; i++) f.step(DT);
            double ms = (System.nanoTime() - a) / 1e6 / 240;
            check(ms < 3 && f.awake() == 1000, fmt("1 000 wache Stücke fallen in einen See: %.2f ms je Schritt auf dem Prozessor (Werkbuch: unter 3 ms)", ms));
        }
        // Seit Glacier Phase 10: Boden aus Kacheln
        {
            int[] calls = {0};
            Fracture.Ground src = (x, z) -> { calls[0]++; return 3 * Math.sin(x * 0.05) + 2 * Math.cos(z * 0.07); };
            com.dan.fracture.GroundCache gc = new com.dan.fracture.GroundCache(src, 1.0);
            double err = 0;
            java.util.Random rq = new java.util.Random(3);
            for (int i = 0; i < 20000; i++) { double x = rq.nextDouble() * 120 - 60, z = rq.nextDouble() * 120 - 60; err = Math.max(err, Math.abs(gc.heightAt(x, z) - src.heightAt(x, z))); }
            check(err < 0.01 && gc.tiles() <= 9 && gc.hitRate() > 0.99, String.format(java.util.Locale.GERMAN,
                    "Boden aus Kacheln (GroundCache, 1 m): größter Fehler %.4f m, %d Kacheln, %.1f %% Treffer", err, gc.tiles(), 100 * gc.hitRate()));
        }
        System.out.println(fmt("%d Prüfungen, %.1f s", count, (System.nanoTime() - t0) / 1e9));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Spannung einer Säule 1 × 1 × H aus dem Stoff bei der Sättigung S (NaN: Bezug), ohne Streuung. */
    static double column(Material mat, double h, double sat, long seed) {
        Fracture f = new Fracture();
        f.material = mat.copy();
        f.material.scatter = 0;
        f.seed = seed;
        Solid s = f.addSolid(Shape.box(0, 0, 0, 1, h, 1), 200);
        s.anchorBelow(0);
        if (!Double.isNaN(sat)) s.soak((x, y, z) -> sat);
        return s.solve();
    }

    /** Größte Auslastung im oberen Meter eines Kliffs (8 × 6 × 2 m Lehm) mit einer Kehle L tief, 1,5 m hoch. */
    static double overhangTop(double L, long seed) {
        Fracture f = Fracture.geschiebelehm();
        f.material.scatter = 0;
        f.seed = seed;
        Solid s = f.addSolid(Shape.box(0, 0, 0, 8, 6, 2), 900);
        s.anchorBelow(0, 5);
        s.erode(i -> s.center(i)[0] > 8 - L && s.center(i)[1] < 1.5);
        s.solve();
        double top = 0;
        for (int b = 0; b < s.bonds(); b++) if (s.bondCenter(b)[1] > 5) top = Math.max(top, s.utilization(b));
        return top;
    }

    /** Ein Würfel 1 m³ aus dem Stoff fällt aus der Höhe h auf festen Boden: {zerfallenes Volumen, Zahl der Stücke, Höhe, Halbmesser}. */
    static double[] drop(Material mat, double h) {
        Fracture f = new Fracture();
        f.material = mat.copy();
        f.ground = (x, z) -> 0;
        double[] ev = new double[4];
        f.onDisintegrate = (v, x, y, z, r, id) -> { ev[0] += v; ev[2] = y; ev[3] = r; };
        f.addPiece(Shape.box(0, 0, 0, 1, 1, 1), 8).place(0.5, h + 0.5, 0.5);
        for (int i = 0; i < 480; i++) f.step(DT);
        ev[1] = f.pieces().size();
        check(Math.abs(ev[0] - f.disintegratedVolume()) < 1e-9, "Ereignis und Zähler des Zerfalls stimmen überein");
        return ev;
    }

    static void lockergestein() {
        Material lehm = Material.geschiebelehm(), sand = Material.kaolinsand(), flug = Material.flugsand();
        double pl = Math.toRadians(lehm.frictionAngle);
        check(Math.abs(lehm.cohesion() - 1.2e5) < 1 && Math.abs(lehm.compressive - 2 * 1.2e5 * Math.cos(pl) / (1 - Math.sin(pl))) < 1
                        && Math.abs(sand.cohesion() - 6e3) < 1 && Math.abs(flug.cohesion() - 1.5e3) < 1,
                fmt("Mohr-Coulomb mit c: Geschiebelehm c %.0f kPa, φ %.0f°, σc %.0f kPa · Kaolinsand c %.1f kPa · Flugsand c %.1f kPa",
                        lehm.cohesion() / 1e3, lehm.frictionAngle, lehm.compressive / 1e3, sand.cohesion() / 1e3, flug.cohesion() / 1e3));
        boolean fest = true;
        for (Material m : new Material[]{Material.ice(), Material.glacierIce(), Material.rock(), Material.concrete(), Material.klei()})
            fest &= !m.moist() && m.crumble == 0 && m.wetFactor(1) == 1 && Double.isInfinite(m.crumbleSpeed());
        check(fest && Fracture.geschiebelehm().material.moist() && Fracture.kaolinsand().material.crumble > 0,
                "Eis, Fels, Beton und Klei ohne Feuchte und ohne Zerfall (Glacier, Rungholt unverändert); Lehm und Sand mit");
        Material mg = Fracture.brodtenerUfer().material;
        check(mg.moist() && mg.cohesion() < lehm.cohesion() && mg.compressive < lehm.compressive && mg.wetFactor(1) < lehm.wetFactor(1) && mg.crumble > 0,
                fmt("Brodtener Ufer (seit Timmendorf Phase 9): Geschiebemergel c %.0f kPa, σc %.0f kPa, nass ×%.2f – weicher als der Lehm am Roten Kliff (×%.2f)",
                        mg.cohesion() / 1e3, mg.compressive / 1e3, mg.wetFactor(1), lehm.wetFactor(1)));
        double[] c = new double[4];
        double[] sv = {0, 0.3, 0.5, 1};
        for (int i = 0; i < 4; i++) c[i] = sand.strengthAt(sv[i])[2];
        check(c[0] < c[1] && c[1] < c[2] && c[3] < c[0] && Math.abs(c[1] - 6e3) < 1,
                fmt("Feuchte im Sand: c trocken %.1f, erdfeucht %.1f, halb %.1f, nass %.2f kPa (Menisken, 4S(1 − S))", c[0] / 1e3, c[1] / 1e3, c[2] / 1e3, c[3] / 1e3));
        double kl = lehm.wetFactor(1);
        check(kl > 0.5 && kl < 0.7 && lehm.wetFactor(0) > 1, fmt("Feuchte im Lehm: nass %.0f %% der Festigkeit, trocken %.0f %%", kl * 100, lehm.wetFactor(0) * 100));
        // Säule aus Kaolinsand: σc/(ρg) ≈ 1,24 m erdfeucht
        double hcS = sand.compressive / (sand.density * 9.81);
        double u3 = column(sand, 1.0, Double.NaN, 1), u0 = column(sand, 1.0, 0, 1), u1 = column(sand, 1.0, 1, 1);
        check(u3 < 1 && u0 > 1 && u1 > 1, fmt("Sandsäule 1 m (steht bis %.2f m): erdfeucht Auslastung %.2f, trocken %.1f, nass %.1f – feuchter Sand steht, trockener und nasser nicht", hcS, u3, u0, u1));
        // Steilwand aus Lehm: σc/(ρg)
        double hc = lehm.compressive / (lehm.density * 9.81);
        double a = column(lehm, 0.7 * hc, Double.NaN, 1), b = column(lehm, 1.3 * hc, Double.NaN, 1), w = column(lehm, 0.7 * hc, 1, 1);
        check(a < 1 && b > 1 && w > 1 && Math.abs(a / 0.7 - b / 1.3) < 0.15 * b / 1.3,
                fmt("Steilwand aus Lehm (Hc = σc/ρg = %.1f m): 0,7·Hc Auslastung %.2f, 1,3·Hc %.2f; durchnässt bricht schon 0,7·Hc (%.2f)", hc, a, b, w));
        // seit Sylt Phase 12: Bestand (Solid.stabilize) – eine Säule über Hc steht, wie sie ist; durchnässt bricht sie wieder
        {
            Fracture f = new Fracture();
            f.material = lehm.copy();
            f.material.scatter = 0;
            f.seed = 1;
            Solid s = f.addSolid(Shape.box(0, 0, 0, 1, 1.3 * hc, 1), 200);
            s.anchorBelow(0);
            double vor = s.solve();
            int k = s.stabilize(0.6);
            double nach = s.solve();
            s.soak((x, y, z) -> 1);
            double nass = s.solve();
            check(vor > 1 && k > 0 && Math.abs(nach - 0.6) < 0.02 && nass > nach && nass < 1.2,
                    fmt("Bestand (Solid.stabilize, Sylt Phase 12): Säule 1,3·Hc Auslastung %.2f, %d Flächen angehoben → %.2f; durchnässt %.2f", vor, k, nach, nass));
        }
        // Unterspülung: Überhang reißt oben hinter der Kante ab L_c = √(f_t·h / 3ρg)
        double lc = Math.sqrt(lehm.tensile * 4.5 / (3 * lehm.density * 9.81));
        double[] lo = {overhangTop(0.75 * lc, 1), overhangTop(0.75 * lc, 2)}, hi = {overhangTop(1.3 * lc, 1), overhangTop(1.3 * lc, 2)};
        check(lo[0] < 1 && lo[1] < 1 && hi[0] > 1 && hi[1] > 1,
                fmt("Hohlkehle unter 4,5 m Lehm (L_c = %.2f m): oben hinter der Kante mit 0,75·L_c Auslastung %.2f / %.2f, mit 1,3·L_c %.2f / %.2f", lc, lo[0], lo[1], hi[0], hi[1]));
        {
            Fracture f = Fracture.geschiebelehm();
            f.seed = 1;
            f.ground = (x, z) -> 0;
            double[] ev = {0};
            f.onDisintegrate = (v, x, y, z, r, id) -> { if (id < 0) ev[0] += v; };
            double[] big = {0, 0};
            f.onBreak = (id, v, mm) -> { if (v > big[0]) { big[0] = v; big[1] = id; } };
            Solid s = f.addSolid(Shape.box(0, 0, 0, 8, 6, 2), 900);
            s.anchorBelow(0, 5);
            double cut = s.undercut(8, 1, 1, 0, 0, 2.2, 1.7 * lc), cells = 0;
            for (int i = 0; i < s.cells(); i++) if (!s.attached(i)) cells += s.volume(i);
            check(cut > 1 && Math.abs(cut - ev[0]) < 1e-9 && Math.abs(cut - f.erodedVolume()) < 1e-9 && Math.abs(cut - cells) < 1e-9,
                    fmt("Hohlkehle %.2f m tief, 2,2 m hoch ausgespült: %.2f m³, gemeldet %.2f m³ (Sediment für den Strand)", 1.7 * lc, cut, ev[0]));
            double t = 0;
            while (big[0] < 5 && t < 40) { f.step(DT); t += DT; }
            check(big[0] > 5 && t < 40,
                    fmt("mit 1,7·L_c reißt der Überhang hinter der Kante nach %.1f s als Block ab (%.1f m³)", t, big[0]));
        }
        // Zerfall beim Aufprall
        double[] l1 = drop(lehm, 1), l8 = drop(lehm, 8), s03 = drop(sand, 0.3), e8 = drop(Material.ice(), 8);
        check(l1[0] == 0 && l1[1] >= 1 && Math.abs(l8[0] - 1) < 1e-9 && l8[1] == 0 && l8[2] < 1.2 && Math.abs(l8[3] - Math.cbrt(lehm.bulking) * 1.2) < 1e-9,
                fmt("Lehmwürfel 1 m³: aus 1 m bleibt er ganz (%.0f Stück), aus 8 m zerfällt er ganz (%.3f m³ bei %.2f m Höhe, Kegel %.2f m)", l1[1], l8[0], l8[2], l8[3]));
        check(Math.abs(s03[0] - 1) < 1e-9 && e8[0] == 0 && e8[1] >= 1,
                fmt("Kaolinsand zerfällt schon aus 0,3 m (ab %.1f m/s), Eis aus 8 m nie (%.0f Stücke)", sand.crumbleSpeed(), e8[1]));
        // Glacier unverändert: die Front aus der Vorführung bricht wie vor Sylt Phase 11
        FractureScene sc = new FractureScene();
        sc.load(FractureScene.Case.UEBERHANG);
        sc.trigger();
        for (int i = 0; i < 120 * 25; i++) sc.step(DT);
        double hsh = 0;
        for (Piece q : sc.world.pieces()) { double[] pp = q.position(); hsh += pp[0] * 1.1 + pp[1] * 2.3 + pp[2] * 3.7 + q.volume(); }
        check(sc.world.pieces().size() == 2 && sc.world.breaks() == 104 && Math.abs(hsh - 1264.2463) < 1,
                fmt("Gletscherfront der Vorführung wie vor Phase 11: %d Stücke, %d Flächen gebrochen, Lagen %.4f (vorher 2, 104, 1264,2463)",
                        sc.world.pieces().size(), sc.world.breaks(), hsh));
    }

    static double energy(Fracture f) {
        double e = 0;
        for (Piece q : f.pieces()) e += q.kinetic() + q.mass() * f.gravity * (q.position()[1] + 100);
        return e;
    }

    static double lowest(Piece q) {
        double y = 1e9;
        for (int i = 0; i < q.cellCount(); i++) {
            Poly c = q.worldCell(i);
            y = Math.min(y, -c.maxAbove(0, -1, 0, 0));
        }
        return y;
    }

    static String fmt(String f, Object... a) { return String.format(Locale.GERMAN, f, a); }

    static void section(String s) { System.out.println(s); }

    static void check(boolean c, String what) {
        count++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private FractureCheck() { }
}
