package com.dan.bear.demo;

import com.dan.bear.Bear;
import com.dan.bear.BearBody;
import com.dan.bear.Sleuth;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Seit Phase 18a von LituyaBay1958: Regeln und Maße, der Körper (Länge der Wirbelsäule, Pfoten auf
 * dem Grund, Schulterhöhe, aufgerichtet), die Gangarten (Fußfolge, Frequenz, Pfoten im Stand ohne Rutschen und auf dem
 * Grund), das Verhalten in {@link DemoBay} (Fischen nur in den Lachsmonaten, Graben am trockenen Strand, nie im tiefen
 * Wasser), die Flucht vor einem Menschen und vor dem Beben (bergauf), das Junge bei der Mutter und die Wiederholbarkeit.
 */
public final class BearCheck {
    private static int bad;

    public static void main(String[] args) {
        regeln();
        koerper();
        gang();
        verhalten();
        flucht();
        beben();
        wiederholbar();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static void regeln() {
        System.out.println("Regeln");
        Bear b = Bear.coastalBrown();
        check(b.salmon(7) && b.salmon(9) && !b.salmon(6) && !b.salmon(10), "Lachse von Juli bis September");
        check(!Bear.grizzly().salmon(8), "Grizzly im Inland: ohne Lachsbach");
        check(b.gait(0, 2.2) == Bear.Gait.STAND && b.gait(1.2, 2.2) == Bear.Gait.WALK && b.gait(3, 2.2) == Bear.Gait.TROT && b.gait(10, 2.2) == Bear.Gait.GALLOP,
                "Gangarten nach der Geschwindigkeit: 0 steht, 1,2 geht, 3 trabt, 10 m/s galoppiert");
        check(b.gait(1.6, 1.0) == Bear.Gait.TROT, "ein Junges (1 m) trabt schon bei 1,6 m/s");
        check(b.animalsFor(Bear.Quality.LOW) < b.animalsFor(Bear.Quality.HIGH) && b.animalsFor(Bear.Quality.ULTRA) == b.maxAnimals,
                f("Tiere je Stufe: %d, %d, %d, %d", b.animalsFor(Bear.Quality.LOW), b.animalsFor(Bear.Quality.MEDIUM), b.animalsFor(Bear.Quality.HIGH), b.animalsFor(Bear.Quality.ULTRA)));
        check(b.describe().startsWith("bear: Stufe 18"), b.describe());
    }

    static float[] stand() {
        float[] p = new float[BearBody.N];
        Bear.legs(Bear.Gait.STAND, 0, 0, 2.4, p);
        return p;
    }

    static void koerper() {
        System.out.println("Körper");
        float[][] ps = BearDemo.poses();
        double worst = 0;
        for (float[] p : ps) worst = Math.max(worst, Math.abs(BearBody.spineLength(p) - BearBody.END));
        check(worst < 0.01, f("Wirbelsäule behält ihre Länge in allen %d Haltungen (größte Abweichung %.1f %%)", ps.length, worst * 100));
        int tri = BearBody.triangles();
        check(tri > 2500 && tri < 8000, tri + " Dreiecke je Tier (8 Tiere: " + tri * 8 + ")");
        float[] p = stand();
        double lvl = BearBody.soleLevel(p), top = BearBody.top(p), L = 2.4;
        double spread = 0;
        for (int l = 0; l < 4; l++) spread = Math.max(spread, BearBody.sole(l, p)[1] - lvl);
        check(spread < 0.003, f("im Stand alle vier Sohlen auf einer Ebene (größter Abstand %.1f mm bei 2,4 m)", spread * L * 1000));
        double sh = (top - lvl) * L;
        check(sh > 1.15 && sh < 1.55, f("Schulterhöhe %.2f m (Männchen 2,4 m; ADF&G: rund 1,3 m)", sh));
        double[] n = BearBody.nose(p);
        check(n[2] > 0.7 && n[1] < 0.05, f("Nase vorn (z %+.2f) und tief, unter dem Buckel", n[2]));
        float[] q = p.clone();
        q[BearBody.HEAD_LIFT] = 0.4f;
        check(BearBody.nose(q)[1] > n[1] + 0.08, f("Kopf heben hebt die Nase um %.0f cm", (BearBody.nose(q)[1] - n[1]) * L * 100));
        float[] r = p.clone();
        r[BearBody.PITCH] = 1.12f;
        r[BearBody.HEAD_LIFT] = -0.75f;
        double up = (BearBody.top(r) - lvl) * L;
        check(up > 2.2 && up < 3.1, f("aufgerichtet %.2f m hoch (große Männchen bis rund 2,7 m)", up));
        // Normalen auf dem Rücken nach oben
        float[] m = BearBody.mesh();
        int ok = 0, cnt = 0;
        for (int i = 0; i < m.length; i += BearBody.FLOATS)
            if (m[i + 7] == 0 && m[i + 6] > 0.45 && m[i + 6] < 0.7 && Math.abs(m[i]) < 0.03 && m[i + 1] > 0.08) { cnt++; if (m[i + 4] > 0.8) ok++; }
        check(cnt > 0 && ok == cnt, "Normalen auf dem Rücken zeigen nach oben (" + ok + " von " + cnt + ")");
        // Kiefer öffnet sich nach unten
        float[] j = p.clone();
        j[BearBody.JAW] = 0.5f;
        double[] lo = BearBody.deform(0, -0.03, 0, 0, -1, 0, 0.03, BearBody.BODY, Math.PI, p), lj = BearBody.deform(0, -0.03, 0, 0, -1, 0, 0.03, BearBody.BODY, Math.PI, j);
        check(lj[1] < lo[1] - 0.02, f("Kiefer auf: das Kinn sinkt um %.0f cm", (lo[1] - lj[1]) * L * 100));
    }

    static void gang() {
        System.out.println("Gangarten");
        Bear b = Bear.coastalBrown();
        double L = 2.4;
        check(Bear.Gait.WALK.phase[BearBody.LH] < Bear.Gait.WALK.phase[BearBody.LF] && Bear.Gait.WALK.phase[BearBody.LF] < Bear.Gait.WALK.phase[BearBody.RH]
                && Bear.Gait.WALK.phase[BearBody.RH] < Bear.Gait.WALK.phase[BearBody.RF], "Gehen: Fußfolge hinten links, vorn links, hinten rechts, vorn rechts");
        check(Bear.Gait.TROT.phase[BearBody.LF] == Bear.Gait.TROT.phase[BearBody.RH] && Bear.Gait.TROT.phase[BearBody.RF] == Bear.Gait.TROT.phase[BearBody.LH],
                "Traben: diagonale Paare im Gleichtakt");
        for (double v : new double[]{1.25, 3.2, 8.0}) {
            Bear.Gait g = b.gait(v, L);
            double fq = Bear.frequency(g, v, L);
            float[] p = new float[BearBody.N];
            double t = 0, z = 0, dt = 0.004, slip = 0, path = 0, above = 0, below = 0;
            double[] last = new double[4];
            boolean[] on = new boolean[4];
            for (int i = 0; i < (int) (3 / fq / dt); i++) {
                t += fq * dt;
                z += v * dt;
                double ph = t - Math.floor(t);
                double lvl = Bear.legs(g, ph, v, L, p);
                for (int l = 0; l < 4; l++) {
                    double u = ph + g.phase[l];
                    u -= Math.floor(u);
                    double[] s = BearBody.sole(l, p);
                    double wz = z + s[2] * L, wy = (s[1] - lvl) * L;
                    double m = g == Bear.Gait.GALLOP ? 0.08 : 0.04;
                    boolean st = u > m && u < g.duty - m;
                    if (st) {
                        if (on[l]) { slip += Math.abs(wz - last[l]); path += v * dt; }
                        above = Math.max(above, wy);
                        below = Math.min(below, wy);
                    }
                    on[l] = st;
                    last[l] = wz;
                }
            }
            check(slip / path < (g == Bear.Gait.GALLOP ? 0.12 : 0.05) && above < (g == Bear.Gait.GALLOP ? 0.06 : 0.02) && below > -0.02,
                    f("%s bei %.1f m/s: %.2f Doppelschritte/s, Schritt %.2f m; Pfote im Stand rutscht %.1f %%, %+.0f/%+.0f mm über dem Grund%s", g, v, fq, v / fq, slip / path * 100, below * 1000, above * 1000,
                            g == Bear.Gait.GALLOP ? " (Galopp: bis 60 mm erlaubt, er rollt über die Pfote ab)" : ""));
        }
    }

    static Sleuth sleuth(DemoBay w, int adults, int cubs, int month, long seed) {
        Bear b = Bear.coastalBrown();
        b.seed = seed;
        Sleuth s = new Sleuth(b);
        s.world = w;
        s.streams.add(DemoBay.creek());
        s.beaches.add(DemoBay.beach());
        s.ranges.add(DemoBay.range());
        s.count = adults;
        s.cubs = cubs;
        s.month = month;
        s.sunAltDeg = 40;
        s.reset();
        return s;
    }

    static void verhalten() {
        System.out.println("Verhalten (DemoBay, zwei Stunden)");
        DemoBay w = new DemoBay();
        w.tide = -0.6;
        Sleuth s = sleuth(w, 3, 2, 8, 7);
        int deep = 0, n = 0, fish = 0, dig = 0, lie = 0, sniff = 0;
        double maxCub = 0, maxDev = 0;
        int[] tracks = {0};
        s.onTrack = (x, z, dx, dz, leg, len) -> tracks[0]++;
        for (int i = 0; i < 7200; i++) {
            s.step(1);
            for (Sleuth.Animal a : s.animals()) {
                n++;
                if (s.depth(a.x, a.z) > 0.95) deep++;
                maxDev = Math.max(maxDev, Math.abs(a.y - (w.ground(a.x, a.z) - a.level * a.length)));
                switch (a.act) { case FISCHEN -> fish++; case GRABEN -> dig++; case LIEGEN -> lie++; case SCHNUEFFELN -> sniff++; default -> { } }
                if (a.cub) {
                    Sleuth.Animal m = s.animals().get(0);
                    maxCub = Math.max(maxCub, Math.hypot(m.x - a.x, m.z - a.z));
                }
            }
        }
        check(deep == 0, f("nie tiefer als 0,95 m im Wasser (%d von %d)", deep, n));
        check(fish > 0 && s.catches > 0 && s.pounces >= s.catches, f("August: fischen (%d s), %d Sprünge, %d Lachse", fish, s.pounces, s.catches));
        check(dig > 0 && lie > 0 && sniff > 0, f("graben am trockenen Strand (%d s), liegen (%d s), schnüffeln (%d s)", dig, lie, sniff));
        check(maxCub < 30, f("die Jungen bleiben bei der Mutter (höchstens %.0f m)", maxCub));
        check(tracks[0] > 100 && s.tracks == tracks[0], f("%d Trittsiegel auf trockenem Grund", tracks[0]));
        check(maxDev < 1e-6, "die Hüfte steht über dem Grund, wie der Gang es will");
        Sleuth m = sleuth(w, 3, 0, 5, 7);
        for (int i = 0; i < 3600; i++) m.step(1);
        check(m.catches == 0 && m.pounces == 0, "Mai: kein Lachs, kein Sprung");
        w.tide = 0.8;
        Sleuth hi = sleuth(w, 3, 0, 5, 9);
        int digHi = 0;
        for (int i = 0; i < 3600; i++) { hi.step(1); for (Sleuth.Animal a : hi.animals()) if (a.act == Sleuth.Activity.GRABEN) digHi++; }
        check(digHi == 0, "bei Hochwasser (0,8 m) kein Graben am Strand");
    }

    static void flucht() {
        System.out.println("Flucht vor einem Menschen");
        DemoBay w = new DemoBay();
        w.tide = -0.6;
        // 1. ein Wanderer kommt aus 400 m mit 1,4 m/s auf sie zu: erst wachsam, dann weicht sie aus
        Sleuth s = sleuth(w, 1, 2, 8, 3);
        for (int i = 0; i < 120; i++) s.step(1);
        Sleuth.Animal a = s.animals().get(0);
        double hx = a.x + 400, hz = a.z, rose = Double.NaN, minD = Double.MAX_VALUE;
        for (int i = 0; i < 3000; i++) {
            double dx = a.x - hx, dz = a.z - hz, l = Math.hypot(dx, dz);
            hx += dx / l * 0.28;
            hz += dz / l * 0.28;
            s.disturbers.clear();
            s.disturbers.add(new Sleuth.Disturber(hx, hz, 1, false));
            s.step(0.2);
            double d = Math.hypot(hx - a.x, hz - a.z);
            if (a.act == Sleuth.Activity.AUFRICHTEN && Double.isNaN(rose)) rose = d;
            if (!Double.isNaN(rose)) minD = Math.min(minD, d);
        }
        check(!Double.isNaN(rose) && rose < s.bear.alertDistance + 2, f("erst wachsam: richtet sich auf bei %.0f m (Warnweite %.0f m)", rose, s.bear.alertDistance));
        check(minD > s.bear.flightDistance * 0.9, f("dann weicht sie dem Wanderer (1,4 m/s) im Trab aus: er kommt nicht näher als %.0f m", minD));
        // 2. überrascht auf 60 m: Flucht im Galopp, die Jungen mit
        Sleuth u = sleuth(w, 1, 2, 8, 4);
        for (int i = 0; i < 120; i++) u.step(1);
        Sleuth.Animal b = u.animals().get(0);
        double px = b.x + 60, pz = b.z, sx = b.x, sz = b.z, maxV = 0, cubMax = 0;
        boolean fled = false;
        u.disturbers.add(new Sleuth.Disturber(px, pz, 1, false));
        for (int i = 0; i < 300; i++) {
            u.step(0.2);
            fled |= b.act == Sleuth.Activity.FLUCHT;
            maxV = Math.max(maxV, b.speed);
            for (Sleuth.Animal c : u.animals()) if (c.cub) cubMax = Math.max(cubMax, Math.hypot(c.x - b.x, c.z - b.z));
        }
        double end = Math.hypot(px - b.x, pz - b.z);
        check(fled && maxV > 7, f("überrascht auf 60 m: Flucht im Galopp bis %.1f m/s (Bärin mit Jungen: drei Viertel)", maxV));
        check(end > 250, f("nach 60 s %.0f m vom Menschen, %.0f m von ihrem Platz", end, Math.hypot(b.x - sx, b.z - sz)));
        check(cubMax < 25, f("die Jungen fliehen mit (höchstens %.0f m von der Mutter)", cubMax));
        check(u.flights >= 1, u.flights + " Flucht(en) gezählt");
    }

    static void beben() {
        System.out.println("Beben");
        DemoBay w = new DemoBay();
        w.tide = -0.6;
        Sleuth s = sleuth(w, 4, 0, 7, 5);
        for (int i = 0; i < 60; i++) s.step(1);
        double[] g0 = new double[s.animals().size()];
        for (int i = 0; i < g0.length; i++) g0[i] = w.ground(s.animals().get(i).x, s.animals().get(i).z);
        s.quake(1);
        int gallop = 0;
        for (int i = 0; i < 300; i++) {
            s.step(0.2);
            if (i == 25) for (Sleuth.Animal a : s.animals()) if (a.gait == Bear.Gait.GALLOP) gallop++;
        }
        double minRise = Double.MAX_VALUE;
        for (int i = 0; i < g0.length; i++) minRise = Math.min(minRise, w.ground(s.animals().get(i).x, s.animals().get(i).z) - g0[i]);
        check(gallop == g0.length && s.quakeFlights == g0.length, f("alle %d galoppieren 5 s nach dem Stoß", gallop));
        check(minRise > 10, f("nach 60 s alle höher als vorher (mindestens %.0f m)", minRise));
    }

    static void wiederholbar() {
        System.out.println("Wiederholbar");
        DemoBay w = new DemoBay();
        Sleuth a = sleuth(w, 3, 2, 8, 11), b = sleuth(w, 3, 2, 8, 11);
        for (int i = 0; i < 1800; i++) { a.step(1); b.step(1); }
        double d = 0;
        for (int i = 0; i < a.animals().size(); i++) d = Math.max(d, Math.hypot(a.animals().get(i).x - b.animals().get(i).x, a.animals().get(i).z - b.animals().get(i).z));
        check(d == 0, "gleicher Startwert, gleiche Wege nach 30 min");
        float[] inst = new float[Sleuth.INST * 8];
        check(a.instances(inst) == 5 && a.shadows(new float[40]) <= 5, "Instanzen für die Grafik: 5 Tiere");
        check(a.describe().startsWith("Bären 3 (2 Junge)"), a.describe());
    }
}
