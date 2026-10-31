package com.dan.flock.demo;

import com.dan.flock.WaderMesh;
import com.dan.flock.WaderSpecies;
import com.dan.flock.Waders;

import java.util.Locale;

/**
 * Prüfung der Wattvögel ohne Fenster (seit Rungholt Phase 8, Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“
 * oder mit den Verstößen (Rückgabewert 1): die Arten als Datensätze, ihre Netze, eine Tide auf {@link DemoWatt}
 * (fressen bei Ebbe in ihrem Abstand zur Kante, stochern, vor der Flut auffliegen, rasten, zurück mit der Ebbe), ein
 * Störer, der Schwarm der Alpenstrandläufer mit dem Umschlag, die Wiederholbarkeit und die Zeit.
 */
public final class WaderCheck {
    private static int bad;

    public static void main(String[] args) {
        arten();
        netze();
        tide();
        schwarm();
        wiederholbar();
        zug();
        spuelsaum();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static void arten() {
        System.out.println("Arten");
        check(WaderSpecies.ALL.size() == 8 && WaderSpecies.byKey("Austernfischer") == WaderSpecies.AUSTERNFISCHER && WaderSpecies.ALL.get(7) == WaderSpecies.SANDERLING,
                "8 Arten als Datensätze (seit Sylt Phase 13 mit dem Sanderling am Ende): " + WaderSpecies.ALL);
        check(WaderSpecies.RINGELGANS.presence(1) == 1 && WaderSpecies.RINGELGANS.presence(7) == 0 && WaderSpecies.SAEBELSCHNAEBLER.presence(1) == 0,
                "Ringelgans nur im Winterhalbjahr, Säbelschnäbler nur im Sommerhalbjahr");
        check(WaderSpecies.LACHMOEWE.headIn(6) != WaderSpecies.LACHMOEWE.headIn(12) && WaderSpecies.ALPENSTRANDLAEUFER.summerPatch(6) && !WaderSpecies.ALPENSTRANDLAEUFER.summerPatch(11),
                "Lachmöwe mit brauner Kappe im Sommer, Alpenstrandläufer mit schwarzem Bauchfleck im Brutkleid");
        check(WaderSpecies.ALPENSTRANDLAEUFER.murmur() && WaderSpecies.ALPENSTRANDLAEUFER.groupMax() >= 2000 && WaderSpecies.RINGELGANS.graze(),
                "Alpenstrandläufer wendet im Schwarm (bis " + WaderSpecies.ALPENSTRANDLAEUFER.groupMax() + "), die Ringelgans weidet");
    }

    static void netze() {
        System.out.println("Netze");
        int worst = 0;
        boolean ok = true;
        for (WaderSpecies s : WaderSpecies.ALL) {
            WaderMesh n = WaderMesh.build(s, 0, false), f = WaderMesh.build(s, 1, true);
            worst = Math.max(worst, n.count() / 3);
            float[] v = n.vertices();
            boolean head = false;
            double maxX = 0;
            for (int i = 0; i < v.length; i += WaderMesh.FLOATS) { if (v[i + 9] == 4) head = true; maxX = Math.max(maxX, Math.abs(v[i])); }
            ok &= head && f.count() < n.count() * 0.6 && n.footY < -s.legLength() && Math.abs(maxX * 2 - s.span()) < s.span() * 0.2;
        }
        check(ok, "jede Art: Kopf zum Stochern (Teil 4), Füße unter dem Bauch, Spannweite stimmt, das ferne Netz mit weniger als 60 % der Dreiecke");
        check(worst < 2500, "höchstens " + worst + " Dreiecke je Vogel (nah)");
    }

    /** Eine Tide von Hochwasser zu Hochwasser. */
    static void tide() {
        System.out.println("Eine Tide");
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 9, 3);
        w.t = 0;
        v.settle();
        int birds = v.birds();
        check(birds > 800 && v.groups().size() >= 6, String.format(Locale.GERMAN, "September: %,d Vögel in %d Gruppen", birds, v.groups().size()));
        int[] c0 = v.census();
        check(c0[Waders.Mode.RASTEN.ordinal()] >= birds * 0.9, "Hochwasser: " + c0[Waders.Mode.RASTEN.ordinal()] + " rasten (" + v.describe() + ")");
        double maxFeed = 0, probing = 0, edgeDun = 0, edgeOys = 0;
        int nDun = 0, nOys = 0, wetViol = 0, samples = 0, tFeed = -1;
        boolean flewBefore = false;
        long t0 = System.nanoTime();
        int steps = 0;
        double hwNext = DemoWatt.PERIOD;
        int feedBeforeHw = -1, restAtHw = -1;
        for (double t = 0; t < DemoWatt.PERIOD; t += 5) {
            w.t = t;
            v.step(5);
            steps++;
            int[] c = v.census();
            maxFeed = Math.max(maxFeed, c[Waders.Mode.FRESSEN.ordinal()]);
            if (tFeed < 0 && c[Waders.Mode.FRESSEN.ordinal()] > birds / 2) tFeed = (int) t;
            if (((int) t) % 300 == 0) {
                for (Waders.Group g : v.groups()) {
                    if (g.mode != Waders.Mode.FRESSEN) continue;
                    for (Waders.Bird b : g.birds) {
                        if (b.air) continue;
                        samples++;
                        if (w.depth(b.x, b.z) > g.species.wade() + 0.05) wetViol++;
                        if (b.probe > 0.2) probing++;
                    }
                    double e = v.edgeDistance(g.cx, g.cz, 600);
                    if (g.species == WaderSpecies.ALPENSTRANDLAEUFER && !Double.isInfinite(e)) { edgeDun += e; nDun++; }
                    if (g.species == WaderSpecies.AUSTERNFISCHER && !Double.isInfinite(e)) { edgeOys += e; nOys++; }
                }
            }
            if (Math.abs(t - (hwNext - 1.2 * 3600)) < 3) feedBeforeHw = c[Waders.Mode.FRESSEN.ordinal()];
            if (Math.abs(t - (hwNext - 0.3 * 3600)) < 3) restAtHw = c[Waders.Mode.RASTEN.ordinal()] + c[Waders.Mode.FLIEGEN.ordinal()];
        }
        double ms = (System.nanoTime() - t0) / 1e6 / steps;
        check(tFeed > 0 && tFeed < 5 * 3600, String.format(Locale.GERMAN, "die Hälfte frisst %.1f h nach Hochwasser, höchstens %,.0f zugleich", tFeed / 3600.0, maxFeed));
        check(samples > 1000 && wetViol < samples * 0.03, String.format(Locale.GERMAN, "wer frisst, steht nicht tiefer, als er waten kann (%d von %d)", wetViol, samples));
        check(probing > samples * 0.15, String.format(Locale.GERMAN, "%.0f %% stochern gerade", probing * 100 / Math.max(1, samples)));
        check(nDun > 0 && nOys > 0 && edgeDun / nDun < 40 && edgeOys / nOys > edgeDun / nDun, String.format(Locale.GERMAN,
                "Alpenstrandläufer am Saum (im Mittel %.0f m von der Kante), Austernfischer weiter oben (%.0f m)", edgeDun / Math.max(1, nDun), edgeOys / Math.max(1, nOys)));
        check(restAtHw > birds * 0.85 && v.takeoffs >= v.groups().size(), String.format(Locale.GERMAN,
                "vor der Flut fliegen alle auf: 20 min vor Hochwasser %,d von %,d rasten oder fliegen (eine Stunde vorher fressen noch %,d), %d Abflüge",
                restAtHw, birds, Math.max(0, feedBeforeHw), v.takeoffs));
        check(ms < 6, String.format(Locale.GERMAN, "5 s Szenenzeit für %,d Vögel: %.2f ms", birds, ms));
    }

    /** Ein Störer scheucht die Strandläufer auf; der Schwarm wendet mit dem Umschlag. */
    static void schwarm() {
        System.out.println("Schwarm und Störer");
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 10, 5);
        w.t = DemoWatt.PERIOD / 2;
        v.settle();
        Waders.Group dun = null;
        for (Waders.Group g : v.groups()) if (g.species == WaderSpecies.ALPENSTRANDLAEUFER && g.mode == Waders.Mode.FRESSEN && (dun == null || g.size() > dun.size())) dun = g;
        check(dun != null && dun.size() >= 200, "ein Schwarm Alpenstrandläufer frisst bei Niedrigwasser (" + (dun == null ? 0 : dun.size()) + ")");
        if (dun == null) return;
        v.disturbers.add(new double[]{dun.cx - 40, dun.cz, 1});
        v.step(0.2);
        check(dun.mode == Waders.Mode.SCHWARM && v.alarms > 0, "ein Störer in 40 m: der Schwarm fliegt auf und wendet (" + dun.mode.label + ")");
        v.disturbers.clear();
        // Umschlag: Anteil der Vögel, die gleichsinnig stark geneigt sind (dann zeigt der Schwarm Bäuche oder Rücken)
        int flips = 0, strong = 0, frames = 0;
        double lastSign = 0, maxSpread = 0;
        for (int k = 0; k < 600 && dun.mode == Waders.Mode.SCHWARM; k++) {
            v.step(0.2);
            frames++;
            int pos = 0, neg = 0;
            double minX = 1e9, maxX = -1e9;
            for (Waders.Bird b : dun.birds) { if (b.bank > 0.35) pos++; else if (b.bank < -0.35) neg++; minX = Math.min(minX, b.x); maxX = Math.max(maxX, b.x); }
            maxSpread = Math.max(maxSpread, maxX - minX);
            int n = dun.size();
            double sign = pos > n * 0.6 ? 1 : neg > n * 0.6 ? -1 : 0;
            if (sign != 0) { strong++; if (lastSign != 0 && sign != lastSign) flips++; lastSign = sign; }
        }
        check(strong > frames * 0.1 && flips >= 2, String.format(Locale.GERMAN, "Umschlag: in %d von %d Bildern neigt sich der Schwarm gemeinsam, %d mal von einer Seite zur anderen", strong, frames, flips));
        check(maxSpread > 15 && maxSpread < 400, String.format(Locale.GERMAN, "der Schwarm bleibt zusammen (größte Ausdehnung %.0f m)", maxSpread));
        for (int k = 0; k < 1200 && dun.mode != Waders.Mode.FRESSEN && dun.mode != Waders.Mode.RASTEN; k++) v.step(0.5);
        boolean ground = dun.mode == Waders.Mode.FRESSEN || dun.mode == Waders.Mode.RASTEN;
        for (int k = 0; k < 60; k++) v.step(1);
        int landed = 0;
        for (Waders.Bird b : dun.birds) if (!b.air) landed++;
        check(ground && landed > dun.size() * 0.95 && v.murmurations > 0, "danach landet er wieder (" + dun.mode.label + ", " + landed + " am Boden)");
    }

    static void wiederholbar() {
        System.out.println("Wiederholbar");
        double[] r = new double[2];
        for (int k = 0; k < 2; k++) {
            DemoWatt w = new DemoWatt();
            Waders v = DemoWatt.waders(w, 9, 42);
            w.t = 3 * 3600;
            v.settle();
            for (int i = 0; i < 200; i++) { w.t += 6; v.step(6); }
            double s = 0;
            for (Waders.Group g : v.groups()) for (Waders.Bird b : g.birds) s += b.x * 1.1 + b.z * 0.7 + b.y;
            r[k] = s;
        }
        check(r[0] == r[1], "gleicher Startwert, gleiche Vögel nach 20 min");
    }

    /** Seit Rungholt Phase 9: Ringelgänse ziehen im Oktober in Keilen über das Watt, im Juli nicht. */
    static void zug() {
        System.out.println("Vogelzug");
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 10, 8);
        v.settle();
        int birdsBefore = v.birds();
        double minY = 1e9, maxSpan = 0;
        boolean leaderAhead = true;
        for (int i = 0; i < 6 * 360; i++) {
            w.t += 10; v.step(10);
            for (Waders.Group g : v.skeins()) {
                Waders.Bird lead = g.birds.get(0), last = g.birds.get(g.birds.size() - 1);
                double ahead = (lead.x - last.x) * g.dx + (lead.z - last.z) * g.dz;
                if (g.birds.size() > 4 && ahead < 2) leaderAhead = false;
                for (Waders.Bird b : g.birds) minY = Math.min(minY, b.y - w.ground(b.x, b.z));
                maxSpan = Math.max(maxSpan, Math.hypot(lead.x - last.x, lead.z - last.z));
            }
        }
        check(v.skeinsSeen >= 6 && v.birds() == birdsBefore, String.format(Locale.GERMAN, "Oktober: in 6 h %d Keile Ringelgänse (die Gruppen am Boden bleiben %d Vögel)", v.skeinsSeen, birdsBefore));
        check(minY > 50 && leaderAhead && maxSpan > 10 && maxSpan < 150, String.format(Locale.GERMAN, "im Keil: der Führende vorn, mindestens %.0f m hoch, Keil bis %.0f m lang", minY, maxSpan));
        Waders j = DemoWatt.waders(new DemoWatt(), 7, 8);
        j.settle();
        for (int i = 0; i < 360; i++) j.step(10);
        check(j.skeinsSeen == 0, "Juli: kein Zug");
    }

    /**
     * Seit Sylt Phase 13: ein Strand 1:30 mit einem Schwall, der alle 10 s hinaufläuft (schnell) und zurückfällt; die
     * Vögel am Spülsaum ({@link com.dan.flock.Shorebirds}) bleiben vor dem Wasser, rennen und hüpfen, hinterlassen Tritte,
     * fliehen vor dem Störer, sind bei Sturm und ohne Strand fort.
     */
    static void spuelsaum() {
        System.out.println("Spülsaum (seit Sylt Phase 13)");
        TestStrand st = new TestStrand();
        com.dan.flock.Shorebirds sb = st.birds(1);
        sb.populate();
        int[] c = sb.census();
        check(c[0] >= 20 && c[1] >= 2 && c[2] >= 2, String.format(Locale.GERMAN, "September: %d Sanderlinge, %d Austernfischer, %d Silbermöwen", c[0], c[1], c[2]));
        double maxRun = 0, gapMin = 1e9, gapMax = -1e9, gapSum = 0;
        int gapN = 0, stepsBefore = sb.steps;
        java.util.Map<Waders.Bird, double[]> last = new java.util.IdentityHashMap<>();
        for (int i = 0; i < 60 * 30; i++) {
            st.t += 1 / 30.0;
            sb.step(1 / 30.0);
            for (Waders.Group g : sb.groups()) {
                if (g.species != WaderSpecies.SANDERLING) continue;
                for (Waders.Bird b : g.birds) {
                    double[] p = last.get(b);
                    if (p != null && !b.air && p[2] == 0) maxRun = Math.max(maxRun, Math.hypot(b.x - p[0], b.z - p[1]) * 30);
                    last.put(b, new double[]{b.x, b.z, b.air ? 1 : 0});
                }
            }
            double gap = sb.meanGap();
            if (i > 300 && !Double.isNaN(gap)) { gapMin = Math.min(gapMin, gap); gapMax = Math.max(gapMax, gap); gapSum += gap; gapN++; }
        }
        double gapMean = gapSum / Math.max(1, gapN);
        check(sb.soaked <= 2 && gapMin > -0.6 && gapMean > 0.2 && gapMean < 2.5, String.format(Locale.GERMAN,
                "Sanderlinge vor dem Wasser: im Mittel %.2f m landwärts der Kante (%.2f bis %.2f m; bis 0,6 m dahinter läuft das Wasser über die Zehen), %d mal tiefer im Wasser als 6 cm über der Watschtiefe",
                gapMean, gapMin, gapMax, sb.soaked));
        check(maxRun > 1.5 && maxRun <= com.dan.flock.Shorebirds.RUN + 0.05, String.format(Locale.GERMAN, "rennen vor dem Schwall bis %.1f m/s (höchstens %.1f)", maxRun, com.dan.flock.Shorebirds.RUN));
        check(sb.hops > 0, String.format(Locale.GERMAN, "der Schwall läuft schneller als sie (3,5 m/s): %d Hüpfer", sb.hops));
        int steps = sb.steps - stepsBefore;
        check(steps > 2000, String.format(Locale.GERMAN, "Tritte im Sand: %,d in einer Minute", steps));
        // Störer: 8 m vor einem Trupp Sanderlinge
        Waders.Group sg = null;
        for (Waders.Group g : sb.groups()) if (g.species == WaderSpecies.SANDERLING) { sg = g; break; }
        double cv = sb.local(sg.cx, sg.cz)[1];
        double[] d = sb.world(st.edge(cv) + 8, cv);
        sb.disturbers.add(new double[]{d[0], d[1], 1});
        int fl = sb.flights;
        for (int i = 0; i < 30; i++) { st.t += 1 / 30.0; sb.step(1 / 30.0); }
        boolean up = sg.mode == Waders.Mode.FLIEGEN;
        sb.disturbers.clear();
        for (int i = 0; i < 40 * 30; i++) { st.t += 1 / 30.0; sb.step(1 / 30.0); }
        double nv = sb.local(sg.cx, sg.cz)[1];
        check(sb.flights > fl && up && sg.mode == Waders.Mode.FRESSEN && Math.abs(nv - cv) > 15 && nv > sb.v0 && nv < sb.v1,
                String.format(Locale.GERMAN, "aufgescheucht aus 8 m: fliegen %.0f m die Küste entlang und landen wieder an der Kante", Math.abs(nv - cv)));
        // Sturmflut: das Wasser bis an den Fuß (kein Strand)
        st.flut = true;
        for (int i = 0; i < 30; i++) { st.t += 1 / 30.0; sb.step(1 / 30.0); }
        boolean fort = sb.away();
        st.flut = false;
        for (int i = 0; i < 30; i++) { st.t += 1 / 30.0; sb.step(1 / 30.0); }
        check(fort && !sb.away() && sb.birds() > 0, "kein Strand (Wasser bis an den Fuß): alle fort, mit dem Strand wieder da");
        sb.windSpeed = 24;
        sb.step(0.1);
        boolean sturm = sb.away();
        sb.windSpeed = 6;
        sb.step(0.1);
        check(sturm && !sb.away(), "Bft 9 und mehr: fort");
        // das Auge springt (neue Ansicht): der Rahmen liegt 400 m weiter, die Trupps stehen sofort im neuen Stück
        TestStrand sp = new TestStrand();
        com.dan.flock.Shorebirds jb = sp.birds(3);
        jb.populate();
        for (int i = 0; i < 300; i++) { sp.t += 1 / 30.0; jb.step(1 / 30.0); }
        sp.oz = 400;   // derselbe Strand (im Rahmen gemessen), 400 m weiter
        jb.frame(0, 400, 1, 0, 0, 1, -70, 70);
        double jg = 0;
        int jn = 0;
        boolean drin = true;
        for (int i = 0; i < 60 * 30; i++) {
            sp.t += 1 / 30.0;
            jb.step(1 / 30.0);
            double g = jb.meanGap();
            if (i > 300 && !Double.isNaN(g)) { jg += g; jn++; }
        }
        for (Waders.Group g : jb.groups()) for (Waders.Bird b : g.birds) { double v = jb.local(b.x, b.z)[1]; if (v < -80 || v > 80) drin = false; }
        jg /= Math.max(1, jn);
        check(drin && jg > 0.2 && jg < 2.5, String.format(Locale.GERMAN, "Sprung des Auges um 400 m: alle im neuen Stück, Sanderlinge im Mittel %.2f m vor der Kante", jg));
        // Juni: wenige Sanderlinge; gleicher seed, gleiche Vögel
        TestStrand s2 = new TestStrand();
        com.dan.flock.Shorebirds jun = s2.birds(1);
        jun.month = 6;
        jun.populate();
        check(jun.census()[0] < c[0] / 2, String.format(Locale.GERMAN, "Juni: %d Sanderlinge (September %d)", jun.census()[0], c[0]));
        TestStrand a1 = new TestStrand(), a2 = new TestStrand();
        com.dan.flock.Shorebirds b1 = a1.birds(5), b2 = a2.birds(5);
        b1.populate(); b2.populate();
        for (int i = 0; i < 600; i++) { a1.t += 1 / 30.0; a2.t += 1 / 30.0; b1.step(1 / 30.0); b2.step(1 / 30.0); }
        Waders.Bird x1 = b1.groups().get(0).birds.get(0), x2 = b2.groups().get(0).birds.get(0);
        check(x1.x == x2.x && x1.z == x2.z && b1.steps == b2.steps, "gleicher seed, gleiche Vögel");
    }

    /** Ein gerader Strand 1:30 (landwärts +x), Pegel 0, ein Schwall alle 10 s: hinauf in 2,5 s mit 3,5 m/s, zurück in 7,5 s. */
    static final class TestStrand {
        double t, oz;
        boolean flut;
        /** Lage der Kante (m landwärts der Linie des Pegels). */
        double edge(double v) {
            if (flut) return 39;
            double p = (t + v * 0.01) % 10;
            return p < 2.5 ? -2 + 3.5 * p : 6.75 - 8.75 * (p - 2.5) / 7.5;
        }
        public double ground(double x, double z) { return x / 30.0; }
        public double depth(double x, double z) { double e = edge(z - oz); return x < e ? Math.min(0.5, (e - x) * 0.012 + 0.005) : 0; }
        public double edgeAt(double v) { return edge(v); }
        public double top(double v) { return 40; }
        com.dan.flock.Shorebirds birds(long seed) {
            com.dan.flock.Shorebirds sb = new com.dan.flock.Shorebirds();
            sb.seed = seed;
            sb.shore = new com.dan.flock.Shorebirds.Shore() {
                public double ground(double x, double z) { return TestStrand.this.ground(x, z); }
                public double depth(double x, double z) { return TestStrand.this.depth(x, z); }
                public double edge(double v) { return TestStrand.this.edge(v); }
                public double top(double v) { return TestStrand.this.top(v); }
            };
            sb.frame(0, 0, 1, 0, 0, 1, -70, 70);
            sb.month = 9;
            return sb;
        }
    }

    private WaderCheck() { }
}
