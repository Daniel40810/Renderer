package com.dan.fog.demo;

import com.dan.fog.Fog;
import com.dan.fog.Signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“. Geprüft werden Sicht
 * nach Koschmieder, die Eichung der Bedeckung, Höhe und Obergrenze, das Ziehen mit dem Wind, Presets nach Beaufort,
 * Reproduzierbarkeit, weiche Ränder, die Kennungen der Nebelsignale und dass sie nur im Nebel tönen; seit Brocken Phase 6
 * der Nebel über Gelände, seit Phase 7 Schatten von Körpern im Nebel und die Auflösung je Stufe.
 */
public final class FogCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new FogCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Sicht und Dichte
        Fog d = Fog.dense();
        d.wisps = 0;
        d.step(0);
        double vis = d.visibilityAt(100, 3, -200);
        double tr = d.transmittance(0, 3, 0, vis, 3, 0);
        check(Math.abs(vis - d.visibilityM) < 0.5 && Math.abs(tr - 0.05) < 0.005,
                f("Koschmieder: geschlossener Nebel %.0f m → Sicht %.1f m, Durchlässigkeit über die Sicht %.3f (5 %%)", d.visibilityM, vis, tr));
        double topHere = d.topAt(100, -200);
        double hi = topHere * (1 + d.softness * 0.5) + 0.5;
        check(d.density(100, hi, -200) == 0 && d.density(100, 1, -200) > 0 && Math.abs(topHere / d.top - 1) <= 0.25 + 1e-9,
                f("Obergrenze am Ort %.0f m (Mittel %.0f m, ±25 %%): darüber klare Luft, darunter Nebel", topHere, d.top));
        double tmin = 1e9, tmax = 0;
        for (int i = 0; i < 400; i++) { double t = d.topAt(i * 137.0, i * -91.0); tmin = Math.min(tmin, t); tmax = Math.max(tmax, t); }
        check(tmin >= d.top * 0.75 - 1e-6 && tmax <= d.top * 1.25 + 1e-6 && tmax - tmin > d.top * 0.3,
                f("Obergrenze wellt sich: %.0f bis %.0f m an 400 Orten", tmin, tmax));

        // ------------------------------------------------ Eichung der Bedeckung
        StringBuilder cs = new StringBuilder();
        boolean calOk = true;
        for (double c : new double[]{0.2, 0.45, 0.7}) {
            Fog b = Fog.banks();
            b.coverage = c;
            b.seed = 3;
            b.step(0);
            double m = b.measuredCoverage();
            calOk &= Math.abs(m - c) < 0.07;
            cs.append(cs.length() > 0 ? ", " : "").append(f("%.0f → %.0f %%", c * 100, m * 100));
        }
        check(calOk, "Bedeckung nach Eichtabelle (gewollt → gemessen über 24 km): " + cs);

        // ------------------------------------------------ weiche Ränder
        Fog b = Fog.banks();
        b.wisps = 0;
        b.step(0);
        double maxJump = 0;
        for (int i = 0; i < 4000; i++) {
            double x = i * 5.0 - 10_000;
            maxJump = Math.max(maxJump, Math.abs(b.bankAt(x + 5, 300) - b.bankAt(x, 300)));
        }
        check(maxJump < 0.35, f("Ränder der Bänke weich: größter Sprung der Bank auf 5 m %.2f", maxJump));

        // ------------------------------------------------ Ziehen mit dem Wind
        Fog w = Fog.banks();
        w.windSpeed = 5;
        w.windFromDeg = 270;     // Westwind: zieht nach Osten (+x)
        w.wisps = 0;
        w.step(0);
        double x0 = 1234, z0 = -567;
        double before = w.bankAt(x0, z0);
        for (int i = 0; i < 100; i++) w.step(1);
        double moved = 100 * 5 * Fog.DRIFT;
        double after = w.bankAt(x0 + moved, z0);
        check(Math.abs(after - before) < 1e-6, f("Bänke ziehen mit 80 %% des Winds: nach 100 s bei 5 m/s aus West %.0f m weiter östlich dieselbe Bank (%.3f / %.3f)", moved, before, after));

        // ------------------------------------------------ Presets, seed
        Fog p1 = Fog.beaufort(2), p2 = Fog.beaufort(2), p3 = Fog.beaufort(2);
        p3.seed = 99;
        p1.step(0); p2.step(0); p3.step(0);
        boolean same = true, diff = false;
        for (int i = 0; i < 50; i++) {
            double x = i * 311.0, z = i * -173.0;
            same &= p1.density(x, 5, z) == p2.density(x, 5, z);
            diff |= p1.density(x, 5, z) != p3.density(x, 5, z);
        }
        check(same && diff, "gleicher seed, gleiche Bänke; anderer seed, andere");
        check(Fog.beaufort(2).amount == 1 && Fog.beaufort(5).amount < 1 && Fog.beaufort(6).amount < 0.2 && Fog.beaufort(8).amount == 0 && Fog.calm().amount == 1 && Fog.storm().amount == 0,
                "Presets nach Beaufort: Nebel bis Bft 3, Fetzen bis 5, ab 7 reißt ihn der Wind auf");
        Fog sh = Fog.shallow();
        sh.coverage = 1;
        sh.wisps = 0;
        sh.step(0);
        check(sh.density(0, 3, 0) > 0 && sh.density(0, 28.3, 0) == 0,
                f("flacher Seenebel bis %.0f m: am Fuß eines Turms dicht (Sicht %.0f m), an der Laterne in 28 m klar", sh.topAt(0, 0), sh.visibilityAt(0, 3, 0)));
        // Untergrenze (seit Haithabu Phase 3): der Nebel hebt ab, unten wird es klar, oben bleibt die Schicht
        Fog lift = Fog.dense();
        lift.coverage = 1;
        lift.wisps = 0;
        lift.step(0);
        double dense0 = lift.density(0, 1, 0), mid0 = lift.density(0, 40, 0);
        lift.base = 30;
        lift.step(0);
        check(dense0 > 0 && lift.density(0, 1, 0) == 0 && Math.abs(lift.density(0, 40, 0) - mid0) < 1e-12 && lift.density(0, 25, 0) > 0
                        && lift.density(0, 25, 0) < mid0,
                f("Untergrenze 30 m: am Wasser klar (vorher Sicht %.0f m), in 40 m unverändert, weicher Übergang darunter", lift.visibilityAt(0, 40, 0)));

        // ------------------------------------------------ Nebelsignale
        Signal r = new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0);
        Signal g2 = new Signal("Feuerschiff", "Horn(2) 20s", 0, 0, 0);
        Signal ty = new Signal("Schiff", "Tyfon 120s", 0, 0, 0);
        check(r.blasts.size() == 3 && r.blasts.get(0).seconds() == 1 && r.blasts.get(1).seconds() == 3 && r.blasts.get(2).seconds() == 1 && r.onSeconds() == 5 && r.period == 30,
                "Kennung " + r.describe());
        check(g2.blasts.size() == 2 && g2.onSeconds() == 4 && ty.blasts.size() == 1 && ty.onSeconds() == 5 && ty.kind == Signal.Kind.TYFON,
                g2.describe() + "; " + ty.describe());
        String e1 = "", e2 = "";
        try { new Signal("x", "Horn Mo(SOS) 10s", 0, 0, 0); } catch (IllegalArgumentException ex) { e1 = ex.getMessage(); }
        try { new Signal("x", "Glocke rund", 0, 0, 0); } catch (IllegalArgumentException ex) { e2 = ex.getMessage(); }
        check(e1.contains("passen nicht") && e2.contains("nicht lesbar"), "falsche Kennungen werden gemeldet („" + e1 + "“)");

        Fog clear = Fog.banks();
        clear.amount = 0;
        Signal r2 = new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0);
        clear.signals.add(r2);
        int[] heard = new int[1];
        clear.onHorn = (x, y, z, s, sec, sig) -> heard[0]++;
        for (int i = 0; i < 120; i++) clear.step(1);
        int clearHeard = heard[0];
        Fog fogged = Fog.dense();
        fogged.top = 120;
        Signal r3 = new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0);
        fogged.signals.add(r3);
        heard[0] = 0;
        double[] dur = new double[1];
        fogged.onHorn = (x, y, z, s, sec, sig) -> { heard[0]++; dur[0] += sec; };
        for (int i = 0; i < 1200; i++) fogged.step(0.1);
        check(clearHeard == 0 && heard[0] == 12 && Math.abs(dur[0] - 20) < 1e-9 && r3.active(),
                f("Nebelhorn tönt nur im Nebel: klar 0 Töne, in 120 s dichtem Nebel %d Töne mit zusammen %.0f s (4 × kurz-lang-kurz)", heard[0], dur[0]));
        check(r3.soundingAt(0.5) && !r3.soundingAt(1.5) && r3.soundingAt(2.5) && !r3.soundingAt(9),
                "Takt: Ton bei 0,5 s und 2,5 s, Pause bei 1,5 s und 9 s");

        // seit Brocken Phase 5: Wind von außen
        Fog fw = Fog.breeze();
        fw.windFromDeg = 270;
        double[] d0 = fw.drift();
        fw.wind = (x, y, z) -> new double[]{0, 0, x > 100 ? 5 : -5};
        fw.windAnchor = new double[]{200, 10, 0};
        double[] d1 = fw.drift();
        double oz0 = fw.snapshot().offZ();
        fw.step(10);
        double dz = fw.snapshot().offZ() - oz0, per = Fog.PERIOD * Math.max(1, fw.bankSize);
        dz = ((dz % per) + per * 1.5) % per - per / 2;
        check(d0[0] > 0 && Math.abs(d1[0]) < 1e-9 && Math.abs(d1[1] - 4) < 1e-9 && Math.abs(dz + 40) < 1e-6,
                String.format(Locale.GERMAN, "mit Wind von außen ziehen die Bänke mit dem Wind am Anker (%.1f m/s nach Süden statt %.1f nach Osten)", d1[1], d0[0]));
        terrain();
        light();
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Prüfungen fehlgeschlagen.");
        return lines;
    }

    public int failures() { return bad; }

    /** Seit Brocken Phase 6: Nebel über Gelände (Nebelmeer, Auflösen von unten, Talnebel, Stau am Luvhang). */
    void terrain() {
        com.dan.fog.TerrainFog f = TerrainFogDemo.lage(0);
        f.wisps = 0;
        f.step(0);
        double vor = -6000, gv = TerrainFogDemo.ground(vor, 0);
        double inSea = f.density(vor, 650, 0), over = f.density(vor, 950, 0), top = f.density(0, TerrainFogDemo.ground(0, 0) + 2, 0);
        check(Math.abs(inSea - com.dan.fog.Fog.SIGMA_PER_VIS / f.seaVisibility) < 1e-6 && over == 0 && top == 0,
                f("Nebelmeer 450–850 m: in 650 m Sicht %.0f m, in 950 m klar, die Kuppe (1 140 m) ragt heraus (Vorland %.0f m)", com.dan.fog.Fog.SIGMA_PER_VIS / inSea, gv));
        // Auflösen von unten: zwei und vier Stunden Sonne
        com.dan.fog.TerrainFog z = TerrainFogDemo.lage(1), v = TerrainFogDemo.lage(2);
        z.wisps = 0;
        z.step(0);
        double b2 = z.seaBase(), t2 = z.seaTop();
        check(b2 > 650 && t2 > 800 && z.density(vor, 560, 0) == 0 && z.density(vor, 800, 0) > 0.5 * com.dan.fog.Fog.SIGMA_PER_VIS / z.seaVisibility,
                f("nach zwei Stunden Sonne (Erwärmung 0,4): Untergrenze %.0f m, Obergrenze %.0f m – unten klar, oben noch Nebel", b2, t2));
        check(v.seaAmount() < 0.05, f("nach vier Stunden ist das Meer fort (%.0f %%)", v.seaAmount() * 100));
        com.dan.fog.TerrainFog nacht = TerrainFogDemo.lage(1);
        nacht.heating = -0.6;
        for (int i = 0; i < 8 * 60; i++) nacht.step(60);
        check(Math.abs(nacht.seaBase() - 450) < 20 && nacht.seaAmount() > 0.95, f("in der Nacht sinkt die Untergrenze zurück (%.0f m) und das Meer schließt sich", nacht.seaBase()));
        // Talnebel
        com.dan.fog.TerrainFog t = TerrainFogDemo.lage(3);
        t.wisps = 0;
        double tx = 3400, tg = TerrainFogDemo.ground(tx, 0), cd = TerrainFogDemo.cold(tx, 0);
        check(t.density(tx, tg + 10, 0) > 0.01 && t.density(0, TerrainFogDemo.ground(0, 0) + 10, 0) == 0 && t.density(tx, tg + cd + 60, 0) == 0,
                f("Talnebel: im Tal (Kaltluft %.0f m) 10 m über Grund Nebel, darüber und auf der Kuppe klar", cd));
        // Stau: Gürtel am Luvhang (Westen), nicht im Lee (Osten)
        com.dan.fog.TerrainFog c = TerrainFogDemo.lage(4);
        c.wisps = 0;
        double lx = -1200, ex = 1200, y = 900;
        check(c.density(lx, y, 0) > 0.005 && c.density(ex, y, 0) == 0 && c.density(lx, 1200, 0) == 0,
                f("Stau ab 980 m bei Westwind: am Luvhang in 900 m Nebel (Gürtel), am Leehang in 900 m klar, über der Decke nichts (die zeichnen die Wolken)"));
        float[] m = c.buildMap(32);
        int o = 32 * 32 * com.dan.fog.TerrainFog.MAP_STRIDE;
        check(m[o] < 320 && m[o + 1] > 1100 && m[o + 3] > 100, f("Karte: Boden %.0f–%.0f m, Gürtel bis %.0f m", m[o], m[o + 1], m[o + 3]));
        com.dan.fog.TerrainFog leer = com.dan.fog.TerrainFog.nebelmeer();
        check(leer.density(0, 600, 0) > 0 && leer.density(0, 300, 0) == 0, "ohne Gelände liegt das Nebelmeer trotzdem (Karte auf 0 m)");
    }

    /** Seit Brocken Phase 7: Schatten von Körpern im Sonnenlicht (das Brockengespenst) und die Stufen der Auflösung. */
    void light() {
        double[] sun = {0.6, 0.25, 0.76};
        double l = Math.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        for (int k = 0; k < 3; k++) sun[k] /= l;
        double rad = Math.toRadians(0.2665);
        double[] p = com.dan.fog.TerrainFog.person(0, 1000, 0, 1.75);
        // Punkte auf der Achse des Körpers, von der Sonne weg
        double head = 1000 + 1.75 * 0.935, body = 1000 + 1.0;
        double s5 = com.dan.fog.TerrainFog.sunShadow(p, sun, rad, -sun[0] * 5, head - sun[1] * 5, -sun[2] * 5);
        double s30 = com.dan.fog.TerrainFog.sunShadow(p, sun, rad, -sun[0] * 30, body - sun[1] * 30, -sun[2] * 30);
        double s200 = com.dan.fog.TerrainFog.sunShadow(p, sun, rad, -sun[0] * 200, body - sun[1] * 200, -sun[2] * 200);
        double vorn = com.dan.fog.TerrainFog.sunShadow(p, sun, rad, sun[0] * 5, body + sun[1] * 5, sun[2] * 5);
        double side = com.dan.fog.TerrainFog.sunShadow(p, sun, rad, -sun[0] * 5 - sun[2] * 1.0, body - sun[1] * 5, -sun[2] * 5 + sun[0] * 1.0);
        double umbra = 0.25 / Math.tan(rad);
        check(s5 < 1e-6 && s30 < 0.05 && s200 > 0.8 && vorn == 1 && side == 1,
                f("Schatten eines Menschen im Sonnenlicht: 5 m dahinter am Kopf %.2f, 30 m dahinter %.2f, 200 m dahinter %.2f (Kernschatten %.0f m), vor ihm %.0f, 1 m neben der Achse %.0f",
                        s5, s30, s200, umbra, vorn, side));
        check(com.dan.fog.TerrainFog.sunShadow(null, sun, rad, 0, 0, 0) == 1 && com.dan.fog.TerrainFog.person(0, 0, 0, 1.75).length == 2 * com.dan.fog.TerrainFog.CASTER_STRIDE,
                "ohne Schattenwerfer volles Licht; ein Mensch sind zwei Körper (Rumpf und Kopf)");
        com.dan.fog.TerrainFog.Quality[] q = com.dan.fog.TerrainFog.Quality.values();
        check(q[0].scale >= q[1].scale && q[1].scale >= q[2].scale && q[2].scale >= q[3].scale && q[3].scale == 1 && q[1].scale == 3,
                f("Auflösung je Stufe: %d, %d, %d, %d (Mittel in drittel, Ultra in voller Auflösung)", q[0].scale, q[1].scale, q[2].scale, q[3].scale));
    }
}
