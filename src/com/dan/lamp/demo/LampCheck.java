package com.dan.lamp.demo;

import com.dan.lamp.Lamp;
import com.dan.lamp.Lamps;
import com.dan.lamp.Spectra;
import com.dan.lamp.Wet;
import com.dan.lamp.Windows;
import com.dan.lamp.gl.LampGl;

import java.util.Locale;

/** Prüfung von {@code com.dan.lamp} ohne Fenster (Paketvertrag Punkt 6); endet mit „Alles in Ordnung.“. */
public final class LampCheck {
    static int bad;

    public static void main(String[] args) {
        System.out.println("com.dan.lamp – Prüfung");
        lichtstrom();
        verteilung();
        strasse();
        spektrum();
        schaltung();
        fenster();
        nass();
        darstellung();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        if (bad > 0) System.exit(1);
    }

    /** ∫ I dΩ = Lichtstrom · Betriebswirkungsgrad, für jede Verteilung und Richtung zur Straße. */
    static void lichtstrom() {
        System.out.println("Lichtstrom");
        for (Lamp.Distribution d : Lamp.Distribution.values()) {
            Lamp l = new Lamp(0, 6, 0, Lamp.Type.LED_3000K, d, 5000).facing(37);
            l.step(0, 0, 20);
            double sum = 0;
            int nt = 400, np = 400;
            for (int i = 0; i < nt; i++) {
                double th = Math.PI * (i + 0.5) / nt, st = Math.sin(th), ct = Math.cos(th);
                for (int j = 0; j < np; j++) {
                    double ph = 2 * Math.PI * (j + 0.5) / np;
                    sum += l.candela(st * Math.cos(ph), -ct, st * Math.sin(ph)) * st;
                }
            }
            sum *= (Math.PI / nt) * (2 * Math.PI / np);
            check(Math.abs(sum / 4000 - 1) < 0.01, f("%-6s 5 000 lm · 0,8 → %,.0f lm abgestrahlt, Spitze %,.0f cd", d, sum, l.peakCandela()));
        }
    }

    static void verteilung() {
        System.out.println("Verteilung");
        Lamp s = new Lamp(0, 8, 0, Lamp.Type.LED_4000K, Lamp.Distribution.STREET, 8000).facing(90);   // Straße im Osten, Achse Nord–Süd
        s.step(0, 0, 20);
        double up = 0;
        for (int k = 0; k < 360; k += 5) up = Math.max(up, s.candela(Math.cos(Math.toRadians(k)) * 0.999, 0.04, Math.sin(Math.toRadians(k)) * 0.999));
        check(up == 0, "STREET voll abgeschirmt: über der Waagerechten 0 cd");
        double th = Math.toRadians(65), along = s.candela(0, -Math.cos(th), -Math.sin(th)), across = s.candela(Math.sin(th), -Math.cos(th), 0), back = s.candela(-Math.sin(th), -Math.cos(th), 0);
        check(along > 1.5 * across && across > back, f("STREET wirft längs der Straße: bei 65° längs %,.0f cd, zur Straße %,.0f, zum Haus %,.0f", along, across, back));
        double best = 0, bestTh = 0;
        for (int i = 0; i <= 90; i++) {
            double t = Math.toRadians(i), v = s.candela(0, -Math.cos(t), -Math.sin(t));
            if (v > best) { best = v; bestTh = i; }
        }
        check(bestTh >= 55 && bestTh <= 75, f("Höchste Lichtstärke längs der Straße bei %.0f° vom Nadir (Typ II: 55–75°)", bestTh));
        Lamp r = new Lamp(0, 4, 0, Lamp.Type.GAS, Lamp.Distribution.ROUND, 1200);
        r.step(0, 0, 20);
        double upFlux = 0, all = 0;
        for (int i = 0; i < 180; i++) {
            double t = Math.PI * (i + 0.5) / 180, v = r.candela(Math.sin(t), -Math.cos(t), 0) * Math.sin(t);
            all += v;
            if (t > Math.PI / 2) upFlux += v;
        }
        check(upFlux / all > 0.03 && upFlux / all < 0.12, f("ROUND (Laterne): %.0f %% des Lichts nach oben", 100 * upFlux / all));
    }

    /** Eine Wohnstraße nach DIN EN 13201 (Klasse P3: Em ≥ 7,5 lx, Emin ≥ 1,5 lx) [G: Leuchte geschätzt]. */
    static void strasse() {
        System.out.println("Straße");
        Lamps ls = new Lamps();
        int n = ls.row(0, 0, 0, -350, 0, 35, 6, Lamp.Type.LED_3000K, Lamp.Distribution.STREET, 6000, 1);
        ls.step(0, 5, 21);
        double sum = 0, min = 1e9, brute = 0, grid = 0;
        int k = 0;
        for (double z = -140; z >= -175; z -= 1) for (double x = 0.5; x <= 7.5; x += 1) {
            double e = ls.groundLux(x, 0, z);
            sum += e; min = Math.min(min, e); k++;
        }
        for (Lamp l : ls.all()) brute += l.illuminance(3, 0, -160, 0, 1, 0);
        grid = ls.groundLux(3, 0, -160);
        double em = sum / k;
        check(em >= 7.5 && em < 25 && min >= 1.5, f("%d Leuchten 6 000 lm in 6 m, alle 35 m: Em %.1f lx, Emin %.1f lx, Gleichmäßigkeit %.2f", n, em, min, min / em));
        check(Math.abs(grid / brute - 1) < 0.01, f("Gitter findet alles Wesentliche: %.2f lx statt %.2f lx", grid, brute));
    }

    static void spektrum() {
        System.out.println("Spektrum");
        double c3 = Spectra.cct(Spectra.xyz(Lamp.Type.LED_3000K)), c4 = Spectra.cct(Spectra.xyz(Lamp.Type.LED_4000K)), ch = Spectra.cct(Spectra.xyz(Lamp.Type.HALOGEN));
        check(Math.abs(c3 - 3000) < 300 && Math.abs(c4 - 4000) < 400 && Math.abs(ch - 2900) < 100, f("Farbtemperatur LED %.0f K und %.0f K, Halogen %.0f K", c3, c4, ch));
        double[] lps = Spectra.chromaticity(Spectra.xyz(Lamp.Type.LPS)), hps = Spectra.chromaticity(Spectra.xyz(Lamp.Type.HPS));
        check(Math.abs(lps[0] - 0.575) < 0.01 && Math.abs(lps[1] - 0.424) < 0.01, f("Natrium-Niederdruck einfarbig: x %.3f y %.3f (589 nm: 0,575 0,424)", lps[0], lps[1]));
        double hc = Spectra.cct(Spectra.xyz(Lamp.Type.HPS));
        check(hps[0] > 0.5 && hc > 1800 && hc < 2300, f("Natrium-Hochdruck orange: x %.3f y %.3f, %.0f K (Datenblatt um 2 000 K)", hps[0], hps[1], hc));
        double[] g = Lamp.Type.GAS.rgb(), i = Lamp.Type.INCANDESCENT.rgb();
        check(g[1] / g[0] > i[1] / i[0], "Gasglühlicht grünlicher als die Glühlampe (Auerstrumpf)");
        for (Lamp.Type t : Lamp.Type.values()) System.out.println("        " + Spectra.describe(t));
    }

    static void schaltung() {
        System.out.println("Schaltung");
        Lamp l = new Lamp(0, 8, 0, Lamp.Type.HPS, Lamp.Distribution.STREET, 10000);
        l.step(1, 200, 18);
        boolean a = !l.on();
        l.step(1, 50, 18);
        boolean b = !l.on();           // Hysterese: zwischen 35 und 70 lx bleibt sie aus
        l.step(1, 30, 18);
        boolean c = l.on();
        l.step(1, 50, 18);
        boolean d = l.on();            // und bleibt an
        check(a && b && c && d, "Dämmerungsschalter mit Hysterese: an unter 35 lx, aus über 70 lx");
        Lamp h = new Lamp(0, 8, 0, Lamp.Type.HPS, Lamp.Distribution.STREET, 10000);
        h.step(0, 0, 18);
        h = new Lamp(0, 8, 0, Lamp.Type.HPS, Lamp.Distribution.STREET, 10000);
        for (int i = 0; i < 90; i++) h.step(1, 0, 18);
        check(Math.abs(h.level() - (1 - Math.exp(-1))) < 0.03, f("Natriumdampf läuft an: nach 90 s %.0f %%", 100 * h.level()));
        Lamp n = new Lamp(0, 8, 0, Lamp.Type.LED_3000K, Lamp.Distribution.STREET, 4000);
        n.halfLevel = 0.5;
        n.step(0, 0, 22.5);
        double before = n.level();
        n.step(0, 0, 23.5);
        check(before == 1 && n.level() == 0.5, "Halbnacht: ab 23 Uhr halbe Leistung");
    }

    static void fenster() {
        System.out.println("Fenster");
        int n = 2000;
        int abends = Windows.count(Windows.Use.HOME, 7, n, 20, 1), nachts = Windows.count(Windows.Use.HOME, 7, n, 3, 1), ferien = Windows.count(Windows.Use.HOLIDAY, 7, n, 23, 1);
        check(abends > 0.5 * n && nachts < 0.07 * n, f("Wohnhaus: um 20 Uhr %d von %d Fenstern hell, um 3 Uhr %d", abends, n, nachts));
        check(ferien > Windows.count(Windows.Use.HOME, 7, n, 23, 1), f("Ferienwohnungen bleiben länger wach: um 23 Uhr %d", ferien));
        boolean same = true;
        for (int i = 0; i < 50; i++) same &= Windows.lit(Windows.Use.HOME, 3, i, 21.3, 5) == Windows.lit(Windows.Use.HOME, 3, i, 21.3, 5);
        java.util.Set<Integer> minutes = new java.util.HashSet<>();
        boolean[] prev = new boolean[400];
        for (int i = 0; i < 400; i++) prev[i] = Windows.lit(Windows.Use.HOME, 9, i, 21, 1);
        for (int m = 1; m <= 180; m++) {
            for (int i = 0; i < 400; i++) {
                boolean now = Windows.lit(Windows.Use.HOME, 9, i, 21 + m / 60.0, 1);
                if (now != prev[i]) minutes.add(m);
                prev[i] = now;
            }
        }
        check(same && minutes.size() > 60, f("Fest je Fenster; zwischen 21 und 24 Uhr wird in %d verschiedenen Minuten geschaltet", minutes.size()));
    }

    static void nass() {
        System.out.println("Nässe und Spiegelung");
        Lamp l = new Lamp(0, 5, -40, Lamp.Type.LED_3000K, Lamp.Distribution.ROUND, 3000);
        l.step(0, 0, 21);
        double[] eye = {0, 1.7, 0};
        double[] wet = streak(l, eye, Wet.Surface.ASPHALT, 1.0, -1), dry = streak(l, eye, Wet.Surface.ASPHALT, 0.0, -1);
        check(wet[0] / wet[1] > 3, f("nasser Asphalt: Lichtstreifen %.1f° hoch, %.1f° breit (%.1f : 1)", wet[0], wet[1], wet[0] / wet[1]));
        check(wet[2] > 5 * dry[2], f("nass glänzt es: Spitze %.2f cd/m² statt %.2f cd/m² trocken", wet[2], dry[2]));
        check(Wet.Surface.COBBLE.albedo(1) < 0.6 * Wet.Surface.COBBLE.albedo(0), "Pflaster wird nass dunkler (Albedo 0,25 → 0,13)");
        double[] calm = streak(l, eye, null, 0, 1), windy = streak(l, eye, null, 0, 8);
        check(windy[1] > calm[1] && calm[2] > windy[2], f("Wasser: bei 1 m/s %.1f° × %.1f°, bei 8 m/s %.1f° × %.1f° (Cox und Munk)", calm[0], calm[1], windy[0], windy[1]));
    }

    /** Lichtstreifen im Blick: Höhe und Breite (°) über der halben Spitze und die Spitze (cd/m²). */
    static double[] streak(Lamp l, double[] eye, Wet.Surface s, double wetness, double wind) {
        int nb = 400, na = 400;
        double[][] L = new double[nb][na];
        double best = 0;
        int bi = 0, bj = 0;
        for (int i = 0; i < nb; i++) {
            double beta = Math.toRadians(0.1 + i * 0.05);                 // unter der Waagerechten
            for (int j = 0; j < na; j++) {
                double alpha = Math.toRadians((j - na / 2) * 0.05);
                double dy = -Math.sin(beta), c = Math.cos(beta), dx = c * Math.sin(alpha), dz = -c * Math.cos(alpha);
                double t = -eye[1] / dy;
                double[] p = {eye[0] + dx * t, 0, eye[2] + dz * t};
                double v = s == null ? Wet.waterLuminance(l, p, eye, wind) : Wet.luminance(l, p, eye, s, wetness);
                L[i][j] = v;
                if (v > best) { best = v; bi = i; bj = j; }
            }
        }
        int up = 0, side = 0;
        for (int i = 0; i < nb; i++) if (L[i][bj] > best / 2) up++;
        for (int j = 0; j < na; j++) if (L[bi][j] > best / 2) side++;
        return new double[]{up * 0.05, side * 0.05, best};
    }

    static void darstellung() {
        System.out.println("Darstellung (gl/LampGl, ohne Grafikkarte)");
        Lamps ls = new Lamps();
        ls.quality = Lamps.Quality.LOW;
        ls.row(0, 0, 0, -2000, 0, 25, 6, Lamp.Type.LED_3000K, Lamp.Distribution.STREET, 4500, 1);
        ls.step(0, 0, 21);
        LampGl gl = new LampGl();
        gl.prepare(ls, new double[]{0, 1.7, -10});
        float[] d = gl.data();
        check(gl.count() == Lamps.Quality.LOW.gpuLamps && Math.abs(d[2]) < 30, f("%d Leuchten von %d an die Karte, die nächste zuerst (z %.0f m)", gl.count(), ls.size(), d[2]));
        boolean all = true;
        for (String file : LampGl.files()) all &= LampGl.class.getResource(file) != null;
        check(all && LampGl.header().contains("lampSpecular"), "Shader liegen neben der Klasse; lamp.glsl gibt lampIrradiance und lampSpecular");
        boolean einmal = true;
        for (String src : LampGl.sources()) einmal &= src.startsWith("#version") && src.lines().filter(z -> z.trim().startsWith("#version")).count() == 1;
        check(einmal, "die Programme der Lichtpunkte beginnen mit genau einem #version (Befund Phase 12)");
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }
}
