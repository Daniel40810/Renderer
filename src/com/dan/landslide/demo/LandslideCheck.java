package com.dan.landslide.demo;

import com.dan.landslide.Landslide;
import com.dan.landslide.Material;
import com.dan.landslide.Presets;

import java.util.Locale;

/**
 * Prüfung von {@code com.dan.landslide} ohne Fenster (LituyaBay1958 Phase 9, Werkbuch 6.2, Tabelle 14 Zeile 9): Block
 * auf der schiefen Ebene gegen v = √(2 g L (sin α − μ cos α)), Coulomb hält eine Schicht auf flachem Hang, Erddruck nach
 * Savage und Hutter, Fahrböschung gegen HEIM32 / SCHEIDEGGER73, Volumen bleibt, Übergabe mit {@code inject}, das Preset
 * des Gilbert Inlet trifft die 96–110 m/s des Aufpralls (FRITZ01), Vajont und Spirit Lake laufen, gleicher Lauf gleiches
 * Ergebnis. Endet mit „Alles in Ordnung.“ oder der Zahl der Verstöße.
 */
public final class LandslideCheck {
    private static int bad, count;

    public static void main(String[] a) {
        long t0 = System.nanoTime();
        section("Stoff");
        Material m = new Material("φ = δ", 2500, Math.tan(Math.toRadians(30)), 1000, 30);
        double[] k = m.earthPressure();
        double hand = (1 + Math.pow(Math.sin(Math.toRadians(30)), 2)) / Math.pow(Math.cos(Math.toRadians(30)), 2);
        check(Math.abs(k[0] - hand) < 1e-9 && Math.abs(k[1] - hand) < 1e-9, fmt("Erddruck bei φ = δ = 30°: k_akt = k_pass = (1 + sin²φ)/cos²φ = %.4f", hand));
        double[] kl = Presets.gilbertInlet1958(Landslide.Quality.LOW).material.earthPressure();
        check(kl[0] < 1 && kl[1] > 1 && kl[0] > 0, fmt("Erddruck Lituya (φ 35°, δ %.1f°): aktiv %.3f, passiv %.3f", Math.toDegrees(Math.atan(0.06)), kl[0], kl[1]));
        m.phi = 20;
        check(m.earthPressure()[0] == 1 && m.earthPressure()[1] == 1, "φ < δ: isotrop (k = 1)");
        check(Material.rock().mu > Material.ice().mu && Material.snow().density < Material.ice().density, "Presets: Fels, Eis, Schnee, Schutt");

        section("Block auf der schiefen Ebene");
        for (double[] c : new double[][]{{30, 0.2}, {40, 0.1}}) {
            double al = Math.toRadians(c[0]), mu = c[1];
            Landslide s = Landslide.over(-800, -150, 600, 150, 4);
            s.material = new Material("Block", 2500, mu, 1e12, Double.NaN);
            s.earthPressure = false;
            s.pressure = 0;
            s.bed((x, z) -> -x * Math.tan(al));
            s.addPlate(-500, 0, 1, 0, 120, 100, 10, 8);
            double[] c0 = s.centroid();
            s.run(6);
            double[] c1 = s.centroid();
            double l = Math.hypot(c1[0] - c0[0], c1[2] - c0[2]), v = s.meanSpeed();
            double vh = Math.sqrt(2 * 9.81 * l * (Math.sin(al) - mu * Math.cos(al))), vt = 9.81 * (Math.sin(al) - mu * Math.cos(al)) * s.time();
            check(Math.abs(v / vh - 1) < 0.01 && Math.abs(v / vt - 1) < 0.01,
                    fmt("starrer Block (ohne inneren Druck), %.0f°, μ %.1f: nach %.1f s und %.0f m %.2f m/s, Handrechnung √(2gL(sin α − μ cos α)) = %.2f m/s, g(sin α − μ cos α)t = %.2f m/s", c[0], mu, s.time(), l, v, vh, vt));
        }
        {
            Landslide s = Landslide.over(-200, -100, 200, 100, 5);
            s.material = new Material("Schicht", 2500, 0.25, 1000, Double.NaN);
            s.earthPressure = false;
            double a10 = Math.toRadians(10);
            s.bed((x, z) -> -x * Math.tan(a10));
            for (int c = 0; c < s.nx * s.nz; c++) s.inject(s.cx(c % s.nx), s.cz(c / s.nx), 2 * s.dx * s.dx, 0, 0);
            for (int i = 0; i < 400; i++) s.step(0.02);
            double v10 = s.maxSpeedNow();
            Landslide r = Landslide.over(-200, -100, 200, 100, 5);
            r.material = s.material;
            r.earthPressure = false;
            double a20 = Math.toRadians(20);
            r.bed((x, z) -> -x * Math.tan(a20));
            for (int c = 0; c < r.nx * r.nz; c++) r.inject(r.cx(c % r.nx), r.cz(c / r.nx), 2 * r.dx * r.dx, 0, 0);
            for (int i = 0; i < 50; i++) r.step(0.02);
            double ex = 9.81 * (Math.sin(a20) - 0.25 * Math.cos(a20)) * r.time();
            double mid = r.speed(r.index(r.nx / 2, r.nz / 2));
            check(v10 == 0 && Math.abs(mid / ex - 1) < 0.02, fmt("Coulomb: Schicht 2 m, δ 14°: am 10°-Hang %.3f m/s nach 8 s; am 20°-Hang in der Mitte %.2f m/s nach 1 s (Handrechnung %.2f)", v10, mid, ex));
        }

        section("Übergabe (inject)");
        {
            Landslide s = Landslide.over(-300, -100, 300, 100, 4);
            s.material = Material.rock();
            s.bed((x, z) -> -x * Math.tan(Math.toRadians(35)));
            double vol = 0, px = 0;
            for (int i = 0; i < 200; i++) {
                double v = 5 + i % 7;
                s.inject(-200 + (i % 20) * 4, -20 + (i / 20) * 4, v, 12, 0);
                vol += v; px += v * 12;
            }
            boolean out = s.inject(1e6, 0, 10, 0, 0);
            double q = 0;
            for (int c = 0; c < s.nx * s.nz; c++) q += s.thickness(c) * s.velocity(c)[0];
            q *= s.dx * s.dx;
            double v0 = s.volume();
            s.run(4);
            check(Math.abs(v0 / vol - 1) < 1e-12 && Math.abs(q / px - 1) < 1e-9 && !out && Math.abs(s.injected() - vol) < 1e-9 && s.centroid()[0] > -150,
                    fmt("200 Brocken (%.0f m³, Impuls %.0f m⁴/s) kommen als Volumen und Impuls an, außerhalb nichts; nach 4 s %.1f m/s am Hang", vol, px, s.meanSpeed()));
        }

        {
            Landslide s = Presets.gilbertInlet1958Terrain(Landslide.Quality.MEDIUM);
            double got = s.inject(-700, 0, 5e4, 10, 0, 25);
            int wet = 0;
            double hm = 0;
            for (int c = 0; c < s.nx * s.nz; c++) if (s.thickness(c) > 0) { wet++; hm = Math.max(hm, s.thickness(c)); }
            check(Math.abs(s.volume() - 5e4) < 1e-6 && got == 5e4 && wet > 100 && hm < 30,
                    fmt("ein Brocken aus dem Bruch (50 000 m³) auf einen Kreis von 25 m: %d Zellen, bis %.1f m statt einer Säule von %.0f m", wet, hm, 5e4 / (s.dx * s.dx)));
        }

        section("Fahrböschung (HEIM32)");
        {
            double ta = Math.tan(Math.toRadians(35));
            Landslide s = Landslide.over(-1200, -1600, 3400, 1600, 10);
            s.material = Material.rock();
            s.name = "trocken";
            s.bed((x, z) -> x < 0 ? -x * ta : 0);
            s.addPlate(-600 / ta, 0, 1, 0, Presets.LITUYA_LENGTH, Presets.LITUYA_WIDTH, Presets.LITUYA_HMAX, Presets.plateExponent(38.3 / 92));
            s.scaleTo(Presets.LITUYA_VOLUME);
            s.crown();
            s.runToRest(300);
            double[] f = s.fahrboeschung(0.5);
            double sch = Landslide.scheidegger(Presets.LITUYA_VOLUME);
            check(Math.abs(f[0] - sch) < 3 && f[0] > 12 && f[0] < 22, fmt("Platte der Lituya (30,6 Mio. m³) trocken über 35° in die Ebene, %s: Fahrböschung %.1f° (H %.0f m, L %.0f m, nach %.0f s); SCHEIDEGGER73 %.1f°, HEIM32 15–20° [G: ±3°]",
                    s.material, f[0], f[1], f[2], s.time(), sch));
        }

        section("Gilbert Inlet 1958 (FRITZ01)");
        Landslide g = Presets.gilbertInlet1958(Landslide.Quality.MEDIUM);
        double v0 = g.volume();
        long tg = System.nanoTime();
        g.run(34);
        double ms = (System.nanoTime() - tg) / 1e6 / g.steps();
        double[] e = g.entrySummary();
        double minH = Double.MAX_VALUE;
        for (int c = 0; c < g.nx * g.nz; c++) minH = Math.min(minH, g.thickness(c));
        check(Math.abs(v0 - Presets.LITUYA_VOLUME) < 1 && Math.abs((g.volume() + g.lost()) / v0 - 1) < 1e-9 && minH >= 0 && g.clipped() == 0,
                fmt("Volumen bleibt: %.6e m³ im Gitter + %.0f m³ über den Rand = %.6e m³; kleinste Mächtigkeit %.1e m", g.volume(), g.lost(), g.volume() + g.lost(), minH));
        double[] imp = Presets.LITUYA_IMPACT;
        check(e[3] >= imp[0] && e[3] <= imp[1], fmt("Aufprall: %.1f m/s im Mittel nach Rate über die Uferlinie (FRITZ01: %.0f–%.0f m/s), höchstens %.1f m/s", e[3], imp[0], imp[1], e[4]));
        check(e[8] > 0.95 * v0, fmt("über die Uferlinie ins Wasser: %.3e m³ (%.1f %%) bis %.0f s", e[8], 100 * e[8] / v0, g.time()));
        hint(fmt("Zufluss gegen FRITZ01: 90 %% in %.1f s (6,8 s), Mächtigkeit am Ufer bis %.0f m senkrecht / %.0f m normal (134 m), Breite bis %.0f m; die Platte läuft als Strom länger und dünner ein als der Block im Versuch",
                e[2], e[5], e[6], e[7]));
        hint(fmt("Leistung: %d × %d Zellen zu %.0f m, %d Schritte, %.1f ms je Schritt auf dem Prozessor", g.nx, g.nz, g.dx, g.steps(), ms));

        section("Vajont 1963, Spirit Lake 1980");
        for (Landslide s : new Landslide[]{Presets.vajont1963(Landslide.Quality.MEDIUM), Presets.spiritLake1980(Landslide.Quality.MEDIUM)}) {
            double vs = s.volume(), peak = 0;
            while (s.time() < 120 && !s.settled()) { s.step(); peak = Math.max(peak, s.meanSpeed()); }
            double[] es = s.entrySummary();
            check(Math.abs((s.volume() + s.lost()) / vs - 1) < 1e-9 && peak > 5,
                    fmt("%s: %.2e m³ laufen, mittlere Geschwindigkeit bis %.0f m/s, ins Wasser %.2e m³ mit %.0f m/s; ruht nach %.0f s (%s)", s.name, vs, peak, es[8], es[3], s.time(), s.source));
            hint(s.name.startsWith("Vajont")
                    ? fmt("Vajont gegen GENEVOIS05 (20–30 m/s): %.0f m/s – eine 250 m dicke Scholle, die als Ganzes glitt, fällt im tiefengemittelten Strom zusammen wie ein Damm", peak)
                    : fmt("Spirit Lake gegen VOIGHT83 (50–70 m/s): %.0f m/s – Schnitt und Reibung gerechnet [G]", peak));
        }

        section("Wiederholbarkeit");
        Landslide r1 = Presets.gilbertInlet1958(Landslide.Quality.LOW), r2 = Presets.gilbertInlet1958(Landslide.Quality.LOW);
        r1.run(20);
        r2.run(20);
        boolean same = r1.steps() == r2.steps();
        for (int c = 0; same && c < r1.nx * r1.nz; c++) same = r1.thickness(c) == r2.thickness(c) && r1.speed(c) == r2.speed(c);
        check(same, fmt("zwei Läufe (8 m, 20 s, %d Schritte) gleichen sich Zelle für Zelle", r1.steps()));

        System.out.println(fmt("%d Prüfungen, %.1f s", count, (System.nanoTime() - t0) / 1e9));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        if (bad > 0) System.exit(1);
    }

    static void section(String s) { System.out.println(s); }

    static void check(boolean c, String what) {
        count++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    static void hint(String what) { System.out.println("  HINWEIS " + what); }

    static String fmt(String f, Object... o) { return String.format(Locale.GERMAN, f, o); }
}
