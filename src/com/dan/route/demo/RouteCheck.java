package com.dan.route.demo;

import com.dan.route.Crowd;
import com.dan.route.Line;
import com.dan.route.Route;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10) für die Hülle von {@code route}. Endet mit „Alles in Ordnung.“ oder mit
 * den Verstößen (Rückgabewert 1).
 */
public final class RouteCheck {
    private static int bad;

    public static void main(String[] args) {
        Route g = grid(5);
        check(g.nodeCount() == 25 && g.edgeCount() == 40, "Gitter 5 × 5: 25 Knoten, 40 Kanten");
        Route.Path p = g.shortest(0, 24, Route.distance());
        check(p.found() && Math.abs(p.length() - 800) < 1e-9, String.format("Ecke zu Ecke über das Gitter: %.0f m", p.length()));
        check(p.nodes()[0] == 0 && p.nodes()[p.nodes().length - 1] == 24, "Weg beginnt und endet richtig");
        Route h = new Route();
        int a = h.node(1, 0, 0, 0), b = h.node(2, 100, 0, 0), c = h.node(3, 100, 0, 100);
        h.edge(a, b, Route.Kind.ROAD, 6, true);
        h.edge(b, c, Route.Kind.ROAD, 6, false);
        check(h.shortest(a, c, Route.distance()).found(), "Einbahn vorwärts: Weg gefunden");
        check(!h.shortest(c, a, Route.distance()).found(), "Einbahn rückwärts: kein Weg");
        check(h.node(2, 0, 0, 0) == b, "gleiche Knotennummer, derselbe Knoten (Netz aus Oracle oder Datei)");
        check(h.danglingCount() == 2, "lose Enden werden gezählt");
        Route w = new Route();
        check(Math.abs(w.weidmann(0) - 1.34) < 1e-12, "freie Gehgeschwindigkeit 1,34 m/s (Weidmann)");
        check(w.weidmann(Route.RHO_MAX) == 0, "bei 5,4 Personen/m² steht alles");
        check(w.weidmann(1) < w.weidmann(0.5) && w.weidmann(0.5) < w.weidmann(0.1), "je dichter, desto langsamer");
        double best = 0, rhoBest = 0;
        for (double r = 0.1; r < 5.4; r += 0.01) if (w.flow(r) > best) { best = w.flow(r); rhoBest = r; }
        check(rhoBest > 1.2 && rhoBest < 2.5 && best > 1.0 && best < 1.5, String.format("größter Fluss %.2f P/(m·s) bei %.2f P/m²", best, rhoBest));
        java.util.Map<Integer, Double> in = g.within(12, Route.distance(), 100);
        check(in.size() == 5, "Umkreis 100 m um die Mitte: fünf Knoten");
        check(g.nearest(101, 99) == 6, "nächster Knoten");
        phase10();
        phase12();
        lituya();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Seit Timmendorf Phase 10: A*, Raster, Ströme, Stau, Fahrzeuge, Fahrplan. */
    static void phase10() {
        System.out.println("Seit Phase 10");
        // A* gegen Dijkstra auf einem Gitter mit krummen Kanten
        java.util.Random r = new java.util.Random(7);
        Route g = new Route();
        int n = 40;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) g.node(j * n + i, i * 25 + r.nextDouble() * 10, 0, j * 25 + r.nextDouble() * 10);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                if (i + 1 < n && r.nextDouble() < 0.9) g.edge(j * n + i, j * n + i + 1, Route.Kind.FOOTWAY, 3, false);
                if (j + 1 < n && r.nextDouble() < 0.9) g.edge(j * n + i, (j + 1) * n + i, Route.Kind.FOOTWAY, 3, false);
            }
        boolean same = true;
        int vd = 0, va = 0;
        for (int k = 0; k < 30; k++) {
            int a = r.nextInt(n * n), b = r.nextInt(n * n);
            Route.Path d = g.shortest(a, b, g.walking());
            vd += g.lastVisited;
            Route.Path s = g.astar(a, b, g.walking(), 1 / g.v0);
            va += g.lastVisited;
            same &= d.found() == s.found() && (!d.found() || Math.abs(d.cost() - s.cost()) < 1e-6) && s.edges().length == s.nodes().length - 1 + (s.found() ? 0 : 1) - (s.found() ? 0 : 1);
        }
        check(same && va < vd * 0.7, String.format("A* findet dieselben Wege wie Dijkstra (30 Paare) und besucht %.0f %% der Knoten", 100.0 * va / vd));
        boolean near = true;
        for (int k = 0; k < 200; k++) {
            double x = r.nextDouble() * 1200 - 100, z = r.nextDouble() * 1200 - 100;
            int lin = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < g.nodeCount(); i++) { double d = Math.hypot(g.node(i).x() - x, g.node(i).z() - z); if (d < bd) { bd = d; lin = i; } }
            near &= g.nearest(x, z) == lin;
        }
        check(near, "nächster Knoten über das Raster = Suche über alle (200 Punkte, auch außerhalb)");
        Route k = grid(5);
        check(k.nearest(10, 410, java.util.Set.of(Route.Kind.PROMENADE)) >= 0 && k.nearest(10, 10, java.util.Set.of(Route.Kind.PIER)) == -1, "nächster Knoten einer Art (Promenade ja, Seebrücke nein)");
        check(Math.abs(k.capacity(k.edge(0)) - 3 * k.maxFlow()) < 1e-9 && k.maxFlow() > 1.2, String.format("Kapazität: 3 m Weg tragen %.1f Personen/s", k.capacity(k.edge(0))));

        // Fundamentaldiagramm: ein Rundweg von 400 m, 2 m breit, verschieden voll
        double[] rho = {0.1, 0.5, 1.0, 2.0}, meas = new double[4], expect = new double[4];
        for (int t = 0; t < rho.length; t++) {
            Route ring = new Route();
            int m = 80;
            for (int i = 0; i < m; i++) ring.node(i, 200 / Math.PI * Math.cos(2 * Math.PI * i / m), 0, 200 / Math.PI * Math.sin(2 * Math.PI * i / m));
            for (int i = 0; i < m; i++) ring.edge(i, (i + 1) % m, Route.Kind.PROMENADE, 2, true);
            java.util.List<Crowd.Goal> gs = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) gs.add(new Crowd.Goal(i * m / 4, 1, 0, 0, "Tor " + i));
            Crowd c = new Crowd(ring, Crowd.Mode.WALK, gs, 3);
            c.reach = 1e9;
            c.target((int) Math.round(rho[t] * 400 * 2));
            double sum = 0, cnt = 0, v0 = 0;
            for (Crowd.Agent a : c.agents) v0 += a.v0 / c.agents.size();
            for (int i = 0; i < 3000; i++) {
                c.step(0.1);
                if (i > 1000) for (Crowd.Agent a : c.agents) if (a.moving) { sum += a.speed; cnt++; }
            }
            meas[t] = sum / Math.max(1, cnt);
            expect[t] = ring.weidmann(rho[t]) * v0 / ring.v0;
        }
        boolean fd = true;
        for (int t = 0; t < 4; t++) fd &= Math.abs(meas[t] - expect[t]) < 0.25 * expect[t] + 0.03;
        check(fd && meas[0] > meas[3], String.format("Rundweg nach Weidmann: bei 0,1 / 0,5 / 1 / 2 P/m² gehen sie %.2f / %.2f / %.2f / %.2f m/s (erwartet %.2f / %.2f / %.2f / %.2f)",
                meas[0], meas[1], meas[2], meas[3], expect[0], expect[1], expect[2], expect[3]));

        // Engstelle: 300 Menschen wollen über einen 0,6 m schmalen Steg; ohne Engstelle kein Stau
        int[] jam = new int[2];
        for (int t = 0; t < 2; t++) {
            Route cor = new Route();
            for (int i = 0; i <= 30; i++) cor.node(i, i * 10, 0, 0);
            for (int i = 0; i < 30; i++) cor.edge(i, i + 1, i >= 14 && i < 16 && t == 0 ? Route.Kind.PIER : Route.Kind.PROMENADE, i >= 14 && i < 16 && t == 0 ? 0.6 : 6, false);
            Crowd c = new Crowd(cor, Crowd.Mode.WALK, java.util.List.of(new Crowd.Goal(0, 1, 0, 5, "West"), new Crowd.Goal(30, 1, 0, 5, "Ost")), 5);
            c.minWidth = 0.6;
            c.target(300);
            int worst = 0;
            for (int i = 0; i < 2400; i++) { c.step(0.1); worst = Math.max(worst, c.moving()[1]); }
            jam[t] = worst;
        }
        check(jam[0] > 20 && jam[1] < jam[0] / 3, String.format("Stau vor der Engstelle: bis %d im Stau, ohne Engstelle %d", jam[0], jam[1]));

        // Ziele, Verweilen, Blick, Wiederholbarkeit
        Route gg = grid(6);
        java.util.List<Crowd.Goal> goals = java.util.List.of(new Crowd.Goal(0, 1, 20, 40, "Promenade"), new Crowd.Goal(35, 2, 30, 60, 1000, 250, "Seebrücke"), new Crowd.Goal(5, 1, 10, 20, "Hafen"));
        Crowd c1 = new Crowd(gg, Crowd.Mode.WALK, goals, 11), c2 = new Crowd(gg, Crowd.Mode.WALK, goals, 11);
        c1.target(50);
        c2.target(50);
        for (int i = 0; i < 6000; i++) { c1.step(0.1); c2.step(0.1); }
        boolean rep = true, look = true;
        for (int i = 0; i < 50; i++) {
            Crowd.Agent a = c1.agents.get(i), b = c2.agents.get(i);
            rep &= a.x == b.x && a.z == b.z;
            if (!a.moving && a.goal == 1 && a.dwellLeft < 20) look &= Math.abs(Math.IEEEremainder(a.heading - Math.atan2(1000 - a.x, -(250 - a.z)), 2 * Math.PI)) < 0.1;
        }
        check(c1.arrivals() > 30 && rep, String.format("50 Gänger mit drei Zielen: %d Ankünfte in 10 min, gleicher Startwert, gleicher Lauf", c1.arrivals()));
        check(look, "wer an der Seebrücke verweilt, schaut zum Blickpunkt (Zuschauer)");
        c1.target(20);
        for (int i = 0; i < 12000 && c1.count() > 20; i++) c1.step(0.1);
        check(c1.count() == 20, "weniger werden: wer zu viel ist, geht am nächsten Ziel (" + c1.count() + ")");

        // Fahrzeuge: frei mit der erlaubten Geschwindigkeit, im dichten Verkehr langsamer, rechts
        Route road = new Route();
        for (int i = 0; i <= 50; i++) road.node(i, i * 20, 0, 0);
        for (int i = 0; i < 50; i++) road.edge(i, i + 1, Route.Kind.ROAD, 7, false, 50 / 3.6);
        java.util.List<Crowd.Goal> ends = java.util.List.of(new Crowd.Goal(0, 1, 0, 0, "A"), new Crowd.Goal(50, 1, 0, 0, "B"));
        Crowd few = new Crowd(road, Crowd.Mode.DRIVE, ends, 2), many = new Crowd(road, Crowd.Mode.DRIVE, ends, 2);
        few.target(3);
        many.target(200);
        double vf = 0, vm = 0, side = 0;
        int nf = 0, nm = 0;
        for (int i = 0; i < 2000; i++) {
            few.step(0.1);
            many.step(0.1);
            if (i < 300) continue;
            for (Crowd.Agent a : few.agents) if (a.moving) { vf += a.speed; nf++; side += Math.signum(a.z) * Math.signum(Math.sin(a.heading)); }
            for (Crowd.Agent a : many.agents) if (a.moving) { vm += a.speed; nm++; }
        }
        vf /= Math.max(1, nf);
        vm /= Math.max(1, nm);
        check(vf > 11 && vf < 15.5 && vm < vf * 0.6 && side > 0, String.format("Fahrzeuge: frei %.0f km/h (erlaubt 50), mit 200 Wagen auf 1 km %.0f km/h, Rechtsverkehr", vf * 3.6, vm * 3.6));

        // Fahrplan: eine Buslinie über drei Halte
        Line l = new Line("Linie 1", road, new int[]{0, 25, 50}, 50 / 3.6, 20, 1800, 6 * 3600, 22 * 3600, Route.driving());
        double leg = l.travel(500);
        check(l.ok() && Math.abs(l.duration() - (2 * leg + 20)) < 1e-6 && Math.abs(l.atStop(1) - leg) < 1e-6,
                String.format("Fahrplan: 1 km über drei Halte in %.0f s (2 × %.0f s Fahrt mit Anfahren und Bremsen, 20 s Halt)", l.duration(), leg));
        java.util.List<Line.Vehicle> v = l.vehicles(6 * 3600 + leg + 5);
        check(v.size() == 1 && v.get(0).atStop() && Math.abs(v.get(0).x() - 500) < 1e-6, "zur Zeit steht der Bus am mittleren Halt");
        check(Math.abs(l.nextDeparture(0, 6 * 3600 + 60) - (6 * 3600 + 1800)) < 1e-6 && Double.isNaN(l.nextDeparture(0, 22 * 3600 + 60)) && l.vehicles(3 * 3600).isEmpty(),
                "nächste Abfahrt im Takt, nach der letzten keine mehr, nachts kein Bus · " + l.describe());
    }

    /** Gitter n × n mit 100 m Abstand. */
    public static Route grid(int n) {
        Route g = new Route();
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) g.node(j * n + i, i * 100, 0, j * 100);
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                if (i + 1 < n) g.edge(j * n + i, j * n + i + 1, Route.Kind.FOOTWAY, 3, false);
                if (j + 1 < n) g.edge(j * n + i, (j + 1) * n + i, Route.Kind.PROMENADE, 6, false);
            }
        return g;
    }

    /** Seit Timmendorf Phase 12: Ziele mit veränderlichem Gewicht (Zuschauer kommen zum Feuerwerk) und Aufbruch danach. */
    static void phase12() {
        Route gg = grid(6);
        java.util.List<Crowd.Goal> goals = java.util.List.of(new Crowd.Goal(0, 1, 20, 40, "Promenade"), new Crowd.Goal(5, 1, 20, 40, "Hafen"),
                new Crowd.Goal(35, 0, 1500, 2000, 1000, 250, "Feuerwerk"));
        Crowd c = new Crowd(gg, Crowd.Mode.WALK, goals, 3);
        c.target(80);
        for (int i = 0; i < 3000; i++) c.step(0.1);
        int vorher = c.dwelling(g -> g == 2);
        c.weight(2, 40);
        for (int i = 0; i < 9000; i++) c.step(0.1);
        int dort = c.dwelling(g -> g == 2);
        check(vorher == 0 && dort > 50, String.format("Ziel mit Gewicht 0 bleibt leer; mit Gewicht 40 stehen nach 15 min %d von 80 dort", dort));
        c.weight(2, 0);
        int los = c.hurry(g -> g == 2, 120);
        for (int i = 0; i < 1800; i++) c.step(0.1);
        int danach = c.dwelling(g -> g == 2);
        check(los > 40 && danach < 5, String.format("nach dem Ende brechen %d auf, nach 3 min stehen noch %d dort", los, danach));
    }

    /** Seit LituyaBay1958 Phase 7: Laufzeitgitter (Eikonal als kürzester Weg), Isochronen, Strahlen, Messstrecke. */
    static void lituya() {
        System.out.println("Seit LituyaBay1958 Phase 7");
        java.util.Locale de = java.util.Locale.GERMAN;
        int[] sizes = {4, 8, 16, 32};
        boolean st = true;
        for (int k = 0; k < 4; k++) st &= com.dan.route.TravelGrid.Stencil.values()[k].size() == sizes[k];
        double e16 = com.dan.route.TravelGrid.Stencil.N16.error(), e32 = com.dan.route.TravelGrid.Stencil.N32.error();
        check(st && Math.abs(e16 - 0.0275) < 0.0002 && Math.abs(e32 - 0.0076) < 0.0002, String.format(de,
                "Sterne mit 4, 8, 16, 32 Nachbarn; größte Lücke 16: %.1f°, 32: %.1f°; Fehler 1/cos(θ/2) − 1: %.2f %% und %.2f %% (Werkbuch 9.4)",
                com.dan.route.TravelGrid.Stencil.N16.maxGapDeg(), com.dan.route.TravelGrid.Stencil.N32.maxGapDeg(), e16 * 100, e32 * 100));
        // konstanter Grund: h = 100 m, c = √(g h)
        double c = com.dan.route.TravelGrid.waveSpeed(100, 0);
        for (com.dan.route.TravelGrid.Stencil s : com.dan.route.TravelGrid.Stencil.values()) {
            com.dan.route.TravelGrid g = flat(401, 20, c);
            g.stencil = s;
            long t0 = System.nanoTime();
            g.solve(java.util.List.of(new com.dan.route.TravelGrid.Source(200, 200, 0)));
            double ms = (System.nanoTime() - t0) / 1e6;
            double cx = g.cx(200), cz = g.cz(200), worst = 0, low = 1;
            for (int a = 0; a < 720; a++) {
                double ang = Math.PI * a / 360, L = 3000;
                double T = g.arrivalAt(cx + L * Math.cos(ang), cz + L * Math.sin(ang)), rel = T / (L / c) - 1;
                worst = Math.max(worst, rel);
                low = Math.min(low, rel);
            }
            double axis = g.arrivalAt(cx + 3000, cz) / (3000 / c) - 1;
            boolean ok = worst <= s.error() + 0.002 && worst >= 0.8 * s.error() && low > -0.002 && Math.abs(axis) < 0.001;
            check(ok, String.format(de, "konstanter Grund, h = 100 m (c = %.2f m/s), 3 km um die Quelle, %d Nachbarn: Laufzeit gegen L/√(gh) %+.2f %% bis %+.2f %% (Grenze %.2f %%), auf der Achse %+.3f %%; %,d Zellen in %.0f ms",
                    c, s.size(), low * 100, worst * 100, s.error() * 100, axis * 100, g.visited, ms));
        }
        // Isochrone T = 60 s auf konstantem Grund ist ein Kreis mit r = 60 c (16 Nachbarn: höchstens 2,75 % größer)
        com.dan.route.TravelGrid g = flat(401, 20, c);
        g.solve(java.util.List.of(new com.dan.route.TravelGrid.Source(200, 200, 0)));
        java.util.List<float[]> iso = g.isochrones(60);
        double r0 = 60 * c, rmin = 1e9, rmax = 0;
        for (float[] l : iso) for (int k = 0; k < l.length; k += 2) { double r = Math.hypot(l[k] - g.cx(200), l[k + 1] - g.cz(200)); rmin = Math.min(rmin, r); rmax = Math.max(rmax, r); }
        float[] l0 = iso.isEmpty() ? new float[2] : iso.get(0);
        boolean closed = iso.size() == 1 && Math.hypot(l0[0] - l0[l0.length - 2], l0[1] - l0[l0.length - 1]) < 1e-3;
        check(closed && rmin > r0 / (1 + com.dan.route.TravelGrid.Stencil.N16.error()) * 0.998 && rmax < r0 * 1.002, String.format(de, "Isochrone 60 s (16 Nachbarn): ein geschlossener Zug, Radius %.0f bis %.0f m; Kreis 60 · c = %.0f m, mit 2,75 %% Gitterfehler bis %.0f m innen", rmin, rmax, r0, r0 / (1 + com.dan.route.TravelGrid.Stencil.N16.error())));
        // zwei Quellen mit Startzeit; Strahl
        g = flat(201, 20, c);
        g.solve(java.util.List.of(new com.dan.route.TravelGrid.Source(50, 100, 0), new com.dan.route.TravelGrid.Source(150, 100, 20)));
        double px = g.cx(120), pz = g.cz(100), want = Math.min(Math.hypot(px - g.cx(50), pz - g.cz(100)) / c, 20 + Math.hypot(px - g.cx(150), pz - g.cz(100)) / c);
        float[] ray = g.ray(180, 30);
        boolean mono = true;
        for (int k = 5; k < ray.length; k += 3) mono &= ray[k] >= ray[k - 3];
        check(Math.abs(g.arrivalAt(px, pz) - want) < 0.01 && Math.abs(ray[0] - g.cx(150)) < 1e-3 && ray[2] == 20 && mono,
                String.format(de, "zwei Quellen (Start 0 s und 20 s): bei x = 120 %.2f s = %.2f s; der Strahl von (180, 30) beginnt an der späteren Quelle, Zeiten steigen", g.arrivalAt(px, pz), want));
        // Insel: die Front läuft herum
        g = flat(201, 20, c);
        for (int j = 80; j < 120; j++) for (int i = 90; i < 110; i++) g.speed[j * 201 + i] = 0;
        g.solve(java.util.List.of(new com.dan.route.TravelGrid.Source(40, 100, 0)));
        double behind = g.arrival()[100 * 201 + 160], direct = (160 - 40) * 20 / c;
        double around = (Math.hypot(50 - 0.5, 20.5) + Math.hypot(50 + 0.5, 20.5) + 20) * 20 / c;
        check(Float.isNaN(g.arrival()[100 * 201 + 100]) && behind > direct * 1.05 && behind < around * 1.03,
                String.format(de, "Insel 400 × 800 m: dahinter %.1f s statt %.1f s geradeaus, um die Ecken %.1f s", behind, direct, around));
        // Kanten für ein Netz
        com.dan.route.TravelGrid sm = flat(10, 20, c);
        long[] n = new long[1];
        double[] axisCost = {0};
        long edges = sm.edges((a, b, len, cost) -> { n[0]++; if (b == a + 1 && a % 10 == 0) axisCost[0] = cost; });
        long want16 = 0;
        for (int[] m : new int[][]{{1, 0}, {0, 1}, {1, 1}, {1, -1}, {2, 1}, {1, 2}, {2, -1}, {1, -2}}) want16 += (long) (10 - Math.abs(m[0])) * (10 - Math.abs(m[1]));
        check(edges == want16 && n[0] == edges && Math.abs(axisCost[0] - 20 / c) < 1e-6,
                String.format(de, "Kanten des Sterns für ein Netz (10 × 10, 16 Nachbarn): %d, jede einmal; Kosten einer Achskante %.4f s = 20 m / c", edges, axisCost[0]));
        // Messstrecke
        com.dan.route.Measure ms = new com.dan.route.Measure(new float[]{0, 0, 300, 0, 300, 400});
        ms.m0 = 1000;
        double[] p = ms.locate(1500);
        com.dan.route.Measure.Projection pr = ms.project(320, 200), pl = ms.project(280, 200);
        ms.segments.add(new com.dan.route.Measure.Segment("Spurlinie", 1100, 1600, new double[]{1100, 1300, 1600}, new double[]{30, 50, 20}));
        float[] cl = ms.clip(1200, 1400);
        double clen = 0;
        for (int k = 2; k < cl.length; k += 2) clen += Math.hypot(cl[k] - cl[k - 2], cl[k + 1] - cl[k - 1]);
        check(Math.abs(p[0] - 300) < 1e-9 && Math.abs(p[1] - 200) < 1e-9 && Math.abs(pr.m() - 1500) < 1e-9 && Math.abs(Math.abs(pr.offset()) - 20) < 1e-9
                        && Math.signum(pr.offset()) == -Math.signum(pl.offset()) && Math.abs(clen - 200) < 1e-6 && Math.abs(ms.valueAt(1200) - 40) < 1e-9
                        && Double.isNaN(ms.valueAt(1700)) && Math.abs(ms.length() - 700) < 1e-9,
                String.format(de, "Messstrecke (700 m, ab m = 1000): Maß 1500 liegt bei (%.0f, %.0f), zurück %.0f; Abstand %+.0f / %+.0f m je Seite; Stück 1200–1400 %.0f m lang; Spurlinie bei 1200: %.0f m",
                        p[0], p[1], pr.m(), pr.offset(), pl.offset(), clen, ms.valueAt(1200)));
    }

    static com.dan.route.TravelGrid flat(int n, double cell, double c) {
        com.dan.route.TravelGrid g = new com.dan.route.TravelGrid(n, n, 0, 0, cell);
        java.util.Arrays.fill(g.speed, (float) c);
        return g;
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private RouteCheck() { }
}
