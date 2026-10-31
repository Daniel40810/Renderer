package com.dan.wetglass.demo;

import com.dan.wetglass.Wetglass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“.
 * Geprüft werden die Kappe (Volumen, Höhe), Haften und Laufen, Neigung und Wind, Verschmelzen, Resttröpfchen und
 * Film, Feintropfen, Einschläge aus dem Regen, Wischen, die Kachel, Beschlag, Salz, Überspülung, Zylinder liegend und
 * stehend, die Grenzen und die Reproduzierbarkeit.
 */
public final class WetglassCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new WetglassCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    /** Eine trockene Scheibe ohne Kachel, ohne Regen und ohne Verdunstung. */
    static Wetglass pane(double w, double h) {
        Wetglass g = new Wetglass();
        g.periodic = false;
        g.width = w;
        g.height = h;
        g.evaporation = 0;
        g.reset();
        return g;
    }

    static double[] run(Wetglass g, double sec) {
        for (int i = 0; i < Math.round(sec * 60); i++) g.step(1 / 60.0);
        return g.drop(0);
    }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // Kappe
        double v = 3.3e-9;
        double a2 = Wetglass.contactRadius(2);
        check(Math.abs(Wetglass.volume(Wetglass.radiusForVolume(v)) - v) < 1e-18 && a2 > 1.4e-3 && a2 < 1.8e-3,
                f("Kugelkappe mit 60°: ein Tropfen von 2 mm sitzt mit %.2f mm Halbmesser auf dem Glas, Volumen bleibt", a2 * 1000));
        Wetglass c = pane(0.1, 0.1);
        c.addDrop(0.05, 0.05, 1e-3);
        c.step(0);
        double top = c.snapshot().heightMm(0.05, 0.05);
        check(Math.abs(top - Wetglass.capHeight(0)) < 1e-4 && c.snapshot().heightMm(0.0515, 0.05) == 0,
                f("Höhe in der Mitte %.3f mm bei 1 mm Halbmesser (Kappe 60°: 0,577), daneben trocken", top));
        // Haften und Laufen
        Wetglass s = pane(0.3, 0.5);
        s.addDrop(0.15, 0.4, 0.8e-3);
        double[] d = run(s, 5);
        check(Math.hypot(d[0] - 0.15, d[1] - 0.4) < 1e-4, f("ein Tropfen mit 0,8 mm Halbmesser haftet (nach 5 s %.2f mm bewegt)", Math.hypot(d[0] - 0.15, d[1] - 0.4) * 1000));
        double[] fall = new double[2];
        for (int k = 0; k < 2; k++) {
            Wetglass r = pane(0.3, 0.5);
            r.addDrop(0.15, 0.45, (3 + k) * 1e-3);
            r.step(0);
            for (int i = 0; i < 60; i++) r.step(1 / 60.0);
            double low = 1;
            for (int i = 0; i < r.count(); i++) if (r.drop(i)[2] > 1.5e-3) low = Math.min(low, r.drop(i)[1]);
            fall[k] = 0.45 - low;
        }
        check(fall[0] > 0.03 && fall[1] > fall[0], f("große Tropfen laufen: 3 mm in 1 s %.0f mm, 4 mm %.0f mm weit", fall[0] * 1000, fall[1] * 1000));
        Wetglass tl = pane(0.3, 0.5);
        tl.slopeY = -Math.sin(Math.toRadians(15));
        tl.addDrop(0.15, 0.45, 3e-3);
        double[] dt = run(tl, 1);
        check(0.45 - dt[1] < fall[0] * 0.5, f("auf einer flach geneigten Scheibe (15°) läuft er langsamer: %.0f mm in 1 s", (0.45 - dt[1]) * 1000));
        Wetglass wd = pane(0.3, 0.5);
        wd.windX = 15;
        wd.addDrop(0.15, 0.45, 3e-3);
        double[] dw = run(wd, 1);
        check(dw[0] - 0.15 > 0.005, f("Wind von links schiebt ihn nach rechts: %.0f mm zur Seite in 1 s", (dw[0] - 0.15) * 1000));
        // Verschmelzen
        Wetglass m = pane(0.1, 0.1);
        m.addDrop(0.05, 0.05, 1.0e-3);
        m.addDrop(0.0515, 0.05, 0.8e-3);
        double before = m.waterMm3();
        m.step(1 / 60.0);
        check(m.count() == 1 && Math.abs(m.waterMm3() - before) < 1e-9 && m.totals()[1] == 1,
                f("zwei Tropfen, die sich berühren, verschmelzen; Wasser vorher %.3f mm³, nachher %.3f mm³", before, m.waterMm3()));
        // Resttröpfchen und Film, Wasser bleibt
        Wetglass t = pane(0.3, 1.0);
        t.addDrop(0.15, 0.95, 3.5e-3);
        double w0 = t.waterMm3();
        run(t, 1.5);
        double w1 = t.waterMm3();
        long res = t.totals()[2];
        double trailFilm = 0;
        for (double xx = 0.10; xx <= 0.20; xx += 0.001) trailFilm = Math.max(trailFilm, t.filmAt(xx, 0.7));
        check(res > 3 && Math.abs(w1 - w0) < 1e-6 && trailFilm > 0.5 && t.filmAt(0.02, 0.7) == 0,
                f("ein Läufer lässt %d Resttröpfchen zurück und zieht einen nassen Film; das Wasser bleibt (%.2f → %.2f mm³)", res, w0, w1));
        // Einschläge aus dem Regen, Feintropfen
        Wetglass rn = pane(0.5, 0.5);
        rn.rain(5, 10, 1);
        double expected = rn.hitRate * 0.25 * 10;
        for (int i = 0; i < 600; i++) rn.step(1 / 60.0);
        long hits = rn.totals()[0];
        Wetglass flat = pane(0.5, 0.5);
        flat.slopeY = 0;
        flat.rain(5, 10, 1);
        check(Math.abs(hits - expected) < expected * 0.01 + 2 && flat.hitRate > rn.hitRate,
                f("5 mm/h mit 10 m/s gegen die Scheibe: %,d Einschläge in 10 s (erwartet %,.0f); eine liegende Scheibe fängt %.0f statt %.0f je m²s", hits, expected, flat.hitRate, rn.hitRate));
        check(rn.fineMean() > 0.05, f("kleine Tropfen bleiben als Feintropfen: Bedeckung %.0f %% nach 10 s", rn.fineMean() * 100));
        // Wischen
        rn.wipe(0.25, 0.25, 0.08);
        int inside = 0;
        for (int i = 0; i < rn.count(); i++) {
            double[] q = rn.drop(i);
            if (Math.hypot(q[0] - 0.25, q[1] - 0.25) < 0.08 - q[2]) inside++;
        }
        check(inside == 0 && rn.fineAt(0.25, 0.25) == 0 && rn.filmAt(0.25, 0.25) >= 0.5, "Wischen: im Kreis keine Tropfen und Feintropfen mehr, nur ein Film");
        // Kachel
        Wetglass k1 = new Wetglass();
        k1.evaporation = 0;
        k1.drainLength = 1e9;
        k1.reset();
        k1.addDrop(0.1, 0.02, 4e-3);
        double wk = k1.waterMm3();
        for (int i = 0; i < 120; i++) k1.step(1 / 60.0);
        boolean wrapped = false;
        for (int i = 0; i < k1.count(); i++) if (k1.drop(i)[2] > 2e-3 && k1.drop(i)[1] > 0.2) wrapped = true;
        Wetglass k2 = new Wetglass();
        k2.evaporation = 0;
        k2.drainLength = k2.height;
        k2.reset();
        k2.addDrop(0.1, 0.02, 4e-3);
        for (int i = 0; i < 120; i++) k2.step(1 / 60.0);
        boolean gone = true;
        for (int i = 0; i < k2.count(); i++) if (k2.drop(i)[2] > 2e-3) gone = false;
        check(wrapped && Math.abs(k1.waterMm3() - wk) < 1e-6 && gone,
                "Kachel: ein Läufer, der unten hinausläuft, kommt oben wieder herein (Wasser bleibt); ist die Scheibe nicht länger als die Kachel, läuft er ab");
        // Beschlag innen
        Wetglass fg = pane(0.2, 0.2);
        fg.condensation = 1e-8;
        for (int i = 0; i < 60 * 60; i++) fg.step(1 / 60.0);
        double fog1 = fg.fogMean();
        fg.wipeInside(0.1, 0.1, 0.04);
        double wiped = fg.fogAt(0.1, 0.1);
        for (int i = 0; i < 60 * 40; i++) fg.step(1 / 60.0);
        double back = fg.fogAt(0.1, 0.1), beside = fg.fogAt(0.17, 0.03);
        fg.condensation = -2e-8;
        for (int i = 0; i < 60 * 60; i++) fg.step(1 / 60.0);
        check(Math.abs(fog1 - 0.6) < 0.01 && wiped == 0 && back > 0.05 && back < beside * 0.75 && fg.fogMean() == 0,
                f("Beschlag: 1e-8 m/s machen in 60 s %.0f %%; gewischt 0, nach 40 s in der Spur %.0f %% (daneben %.0f %%); bei Trocknung weg", fog1 * 100, back * 100, beside * 100));
        // Salz
        Wetglass sl = pane(0.3, 0.3);
        sl.saltRate = 0.02;
        for (int i = 0; i < 60 * 30; i++) sl.step(1 / 60.0);
        double s1 = sl.saltMean();
        sl.saltRate = 0;
        double sp0 = sl.saltAt(0.05, 0.05);
        for (int i = 0; i < 20; i++) sl.spray(0.05, 0.05, 1.5);
        double sp1 = sl.saltAt(0.05, 0.05);
        sl.addDrop(0.2, 0.28, 4e-3);
        for (int i = 0; i < 60; i++) sl.step(1 / 60.0);
        double track = 9;
        for (double yy = 0.12; yy < 0.24; yy += 0.01) track = Math.min(track, minSalt(sl, 0.12, 0.28, yy));
        double saltBefore = sl.saltAt(0.1, 0.25);
        sl.rain(5, 10, 1);
        for (int i = 0; i < 60 * 60; i++) sl.step(1 / 60.0);
        check(s1 > 0.5 && sp1 > sp0 + 0.3 && track < s1 * 0.5 && sl.saltAt(0.1, 0.25) < saltBefore * 0.7,
                f("Salz: Sprühnebel legt in 30 s %.1f g/m² ab, Gischt mehr; ein Läufer löst eine Spur (%.1f g/m²), eine Minute Regen spült ab (%.1f → %.1f g/m²)",
                        s1 * Wetglass.SALT_FULL, track * Wetglass.SALT_FULL, saltBefore * Wetglass.SALT_FULL, sl.saltAt(0.1, 0.25) * Wetglass.SALT_FULL));
        // Überspülung
        Wetglass ov = Wetglass.gale();
        ov.evaporation = 0;
        for (int i = 0; i < 60 * 20; i++) ov.step(1 / 60.0);
        ov.hitRate = 0;
        double w0f = ov.waterMm3();
        int c0 = ov.count();
        ov.flood(1.0, true);
        double w1f = ov.waterMm3();
        boolean absorbed = ov.count() == 0 && ov.sheetMm() > 1.0 && Math.abs(w1f - (w0f + 1e-3 * ov.width * ov.height * 1e9)) < 1;
        double t0 = ov.time();
        while (ov.sheetMm() > 0 && ov.time() - t0 < 60) ov.step(1 / 60.0);
        double tBreak = ov.time() - t0;
        check(absorbed && ov.sheetMm() == 0 && tBreak > 2 && tBreak < 30 && ov.count() > c0 / 4 && ov.saltMean() > 0.2 && ov.totals()[5] == 1,
                f("Überspülung: die Haut von 1 mm nimmt alle %,d Tropfen auf (Wasser bleibt), läuft ab und zerreißt nach %.0f s in %,d Tropfen; Seewasser lässt %.1f g/m² Salz",
                        c0, tBreak, ov.count(), ov.saltMean() * Wetglass.SALT_FULL));
        // Zylinder liegend: oben liegen, an den Flanken laufen, unten abfallen
        Wetglass.Cylinder rail = new Wetglass.Cylinder(0.03, true);
        Wetglass hz = new Wetglass();
        hz.surface = rail;
        hz.width = rail.circumference();
        hz.height = 0.2;
        hz.evaporation = 0;
        hz.reset();
        hz.addDrop(0.0005, 0.05, 2.2e-3);
        hz.addDrop(rail.radius * Math.PI / 2, 0.10, 2.6e-3);
        for (int i = 0; i < 60 * 3; i++) hz.step(1 / 60.0);
        double topX = -1, flankX = -1;
        for (int i = 0; i < hz.count(); i++) {
            double[] q = hz.drop(i);
            if (Math.abs(q[1] - 0.05) < 0.01 && q[2] > 2e-3) topX = q[0];
            if (Math.abs(q[1] - 0.10) < 0.01 && q[0] < rail.radius * Math.PI * 1.2) flankX = Math.max(flankX, q[0]);
        }
        hz.rainAcross(30, 0);
        for (int i = 0; i < 60 * 120; i++) hz.step(1 / 60.0);
        double fTop = hz.fineAt(0.002, 0.1), fBottom = hz.fineAt(rail.radius * Math.PI, 0.1);
        check(topX >= 0 && topX < 0.002 && flankX > rail.radius * Math.PI / 2 + 0.01 && hz.totals()[4] > 0 && fTop > 0.05 && fBottom == 0,
                f("liegender Zylinder (Handlauf): oben bleibt ein Tropfen liegen, an der Flanke läuft einer %.0f mm um ihn herum nach unten; unten fallen %d ab; der Regen trifft nur oben", (flankX - rail.radius * Math.PI / 2) * 1000, hz.totals()[4]));
        // Zylinder stehend im Wind: vorn nass, hinten trocken, der Wind schiebt zur Seite
        Wetglass.Cylinder post = new Wetglass.Cylinder(0.05, false);
        post.wind = 12;
        Wetglass vt = new Wetglass();
        vt.surface = post;
        vt.width = post.circumference();
        vt.height = 0.2;
        vt.drainLength = 1e9;
        vt.evaporation = 0;
        vt.reset();
        double x45 = post.radius * Math.PI / 4;
        vt.addDrop(x45, 0.15, 1.2e-3);
        for (int i = 0; i < 60 * 2; i++) vt.step(1 / 60.0);
        double side = vt.drop(0)[0] - x45;
        vt.rain(5, 12, 1);
        for (int i = 0; i < 60 * 20; i++) vt.step(1 / 60.0);
        double front = vt.fineAt(0.002, 0.1), rear = vt.fineAt(post.radius * Math.PI, 0.1);
        check(side > 0.005 && front > 0.01 && rear == 0,
                f("stehender Zylinder im Wind 12 m/s: an der Flanke schiebt die Umströmung einen kleinen Tropfen %.0f mm zur Seite; vorn %.0f %% Feintropfen, hinten trocken", side * 1000, front * 100));
        // Grenzen, Stufen, Voreinstellungen, Reproduzierbarkeit
        Wetglass big = Wetglass.beaufort(10);
        big.quality = Wetglass.Quality.LOW;
        for (int i = 0; i < 60 * 30; i++) big.step(1 / 60.0);
        check(big.count() <= Wetglass.Quality.LOW.maxDrops, f("Grenze: höchstens %,d Tropfen auf „LOW“ (%,d nach 30 s Sturm)", Wetglass.Quality.LOW.maxDrops, big.count()));
        boolean q = true;
        Wetglass.Quality[] qs = Wetglass.Quality.values();
        for (int i = 1; i < qs.length; i++) if (qs[i].maxDrops <= qs[i - 1].maxDrops || qs[i].mapRes < qs[i - 1].mapRes) q = false;
        check(q, f("Stufen: %,d bis %,d Tropfen, Karte %d bis %d Texel", qs[0].maxDrops, qs[qs.length - 1].maxDrops, qs[0].mapRes, qs[qs.length - 1].mapRes));
        check(Wetglass.calm().hitRate < Wetglass.breeze().hitRate && Wetglass.breeze().hitRate < Wetglass.gale().hitRate
                && Wetglass.gale().hitRate < Wetglass.storm().hitRate && Wetglass.beaufort(-2).hitRate == Wetglass.beaufort(0).hitRate,
                "Voreinstellungen steigen von calm bis storm, beaufort(n) begrenzt auf 0..12");
        double[] r1 = sim(1885), r2 = sim(1885), r3 = sim(7);
        check(r1[0] == r2[0] && r1[1] == r2[1] && (r1[0] != r3[0] || r1[1] != r3[1]), "gleicher seed, gleiche Tropfen; anderer seed, andere");
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        return new ArrayList<>(lines);
    }

    static double minSalt(Wetglass g, double x0, double x1, double y) {
        double m = 9;
        for (double xx = x0; xx <= x1; xx += 0.002) m = Math.min(m, g.saltAt(xx, y));
        return m;
    }

    static double[] sim(int seed) {
        Wetglass g = Wetglass.gale();
        g.seed = seed;
        g.reset();
        for (int i = 0; i < 300; i++) g.step(1 / 60.0);
        return new double[]{g.count(), g.waterMm3()};
    }
}
