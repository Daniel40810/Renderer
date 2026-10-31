package com.dan.firework.demo;

import com.dan.firework.Firework;
import com.dan.firework.Spectrum;

import java.util.Locale;

/**
 * Prüfung ohne Fenster (Paketvertrag Punkt 10) für {@code firework}: Schall, Zündplan, Bahnen und Zerlegungshöhen,
 * seit Timmendorf Phase 11 die Sterne (Größe des Bilds, Weide hängt, Crossette teilt sich, Ring liegt in einer Ebene,
 * Fächer vom Boden), das Spektrum der Salze, das Licht je Bombe, die Spuren und der Rauch im Wind. Endet mit „Alles in
 * Ordnung.“ oder mit den Verstößen (Rückgabewert 1).
 */
public final class FireworkCheck {
    private static int bad;

    public static void main(String[] args) {
        check(Math.abs(Firework.soundSpeed(20) - 343.2) < 0.2, f("Schall bei 20 °C: %.1f m/s", Firework.soundSpeed(20)));
        double d = Firework.soundDelay(700, 20);
        check(d > 2.0 && d < 2.1, f("Knall aus 700 m kommt nach %.2f s (Werkbuch: gut 2 s)", d));
        Firework f = Firework.demo(0, 0);
        check(f.shells().size() == 12, "Zündplan der Vorführung: zwölf Bomben");
        double a75 = f.apex(75), a150 = f.apex(150);
        check(a75 < a150, f("größeres Kaliber steigt höher (75 mm %.0f m, 150 mm %.0f m)", a75, a150));
        check(a75 < Firework.muzzleSpeed(75) * Firework.muzzleSpeed(75) / (2 * 9.81), "Luftwiderstand: Scheitel unter dem Wert im leeren Raum");
        double b75 = f.burstHeight(75), b150 = f.burstHeight(150), b300 = f.burstHeight(300);
        check(b75 > 0.8 * a75 && b75 <= a75 + 1e-6, f("Zerlegung nahe dem Scheitel (%.0f von %.0f m)", b75, a75));
        check(Math.abs(b75 / 90 - 1) < 0.2 && Math.abs(b150 / 180 - 1) < 0.2 && Math.abs(b300 / 360 - 1) < 0.25,
                f("Zerlegungshöhe nach der Praxisregel (1,2 m je mm): 75 mm %.0f m, 150 mm %.0f m, 300 mm %.0f m", b75, b150, b300));
        int[] launch = new int[1], burst = new int[1];
        f.events = new Firework.Events() {
            @Override public void onLaunch(Firework.Shell s, double t) { launch[0]++; }
            @Override public void onBurst(Firework.Shell s, double x, double y, double z, double st, double t) { burst[0]++; }
        };
        long t0 = System.nanoTime();
        int peak = 0;
        for (int i = 0; i < 40 * 60; i++) { f.step(1 / 60.0); peak = Math.max(peak, f.stars().size()); }
        double ms = (System.nanoTime() - t0) / 1e6 / 2400;
        check(launch[0] == 12 && burst[0] == 12, "alle Bomben abgeschossen und zerlegt (Ereignisse)");
        check(ms < 4, f("Vorführung: bis %,d Sterne zugleich, %.2f ms je Bild (1/60 s)", peak, ms));
        try {
            f.load(0, 0, 0, "1 75 PEONY\n");
            check(false, "Fehler im Zündplan wird gemeldet");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains("Zeile 1"), "Fehler im Zündplan nennt die Zeile");
        }
        Firework g = Firework.demo(0, 0);
        g.wind = (x, y, z, t, o) -> { o[0] = 8; o[1] = 0; o[2] = 0; };
        for (int i = 0; i < 4 * 60; i++) g.step(1 / 60.0);
        check(g.shells().get(0).x > 0, "Wind versetzt die Bombe");
        check(Firework.Salt.STRONTIUM.nm > Firework.Salt.BARIUM.nm && Firework.Salt.BARIUM.nm > Firework.Salt.COPPER.nm, "rot vor grün vor blau");
        sterne();
        spektrum();
        licht();
        rauch();
        darstellung();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Eine Bombe allein, senkrecht, ohne Wind; Rückgabe nach dem Erlöschen. */
    static Firework one(String line) {
        Firework f = new Firework();
        f.load(0, 0, 0, line);
        return f;
    }

    static void sterne() {
        System.out.println("Sterne seit Phase 11");
        for (int mm : new int[]{75, 150}) {
            Firework f = one("0 " + mm + " PEONY STRONTIUM");
            double[] c = null;
            double r = 0;
            int n = 0;
            for (int i = 0; i < 20 * 120; i++) {
                f.step(1 / 120.0);
                Firework.Shell s = f.shells().get(0);
                if (s.burst && c == null) c = new double[]{s.x, s.y, s.z};
                if (c != null && !f.stars().isEmpty()) {
                    boolean anyAlive = false;
                    for (Firework.Star st : f.stars()) anyAlive |= st.alive;
                    if (!anyAlive) break;
                    r = 0;
                    n = 0;
                    for (Firework.Star st : f.stars()) if (st.alive && st.age > st.burn * 0.85) { r += Math.hypot(st.x - c[0], st.z - c[2]); n++; }
                }
            }
            double rr = n > 0 ? r / n : 0, want = Firework.burstRadius(mm);
            // waagerechter Abstand der Sterne am Äquator ist am größten: Mittel über die Kugel × 4/π
            double radius = rr * 4 / Math.PI;
            check(Math.abs(radius / want - 1) < 0.25, f("Bild einer Päonie %d mm: Halbmesser beim Erlöschen %.0f m (Praxisregel 0,42 m je mm: %.0f m)", mm, radius, want));
        }
        Firework w = one("0 125 WILLOW GOLD"), p = one("0 125 PEONY BARIUM");
        double wy = 0, py = 0, by = 0;
        int wn = 0, pn = 0;
        double wBurn = 0, pBurn = 0;
        for (int i = 0; i < 25 * 60; i++) {
            w.step(1 / 60.0);
            p.step(1 / 60.0);
            if (w.shells().get(0).burst && by == 0) by = w.shells().get(0).y;
            for (Firework.Star st : w.stars()) if (st.alive && st.age > st.burn * 0.8 && st.age < st.burn * 0.82) { wy += st.y; wn++; wBurn = st.burn; }
            for (Firework.Star st : p.stars()) if (st.alive && st.age > st.burn * 0.8 && st.age < st.burn * 0.82) { py += st.y; pn++; pBurn = st.burn; }
        }
        wy /= Math.max(1, wn);
        py /= Math.max(1, pn);
        check(wBurn > 2 * pBurn && by - wy > 20, f("Weide: brennt %.1f s statt %.1f s und hängt %.0f m unter der Zerlegung durch", wBurn, pBurn, by - wy));
        Firework cr = one("0 100 CROSSETTE STRONTIUM");
        int max = 0, before = 0;
        for (int i = 0; i < 12 * 60; i++) {
            cr.step(1 / 60.0);
            int alive = 0;
            for (Firework.Star st : cr.stars()) if (st.alive) alive++;
            if (before == 0 && alive > 0 && cr.shells().get(0).burst) before = alive;
            max = Math.max(max, alive);
        }
        check(before > 0 && max >= 3 * before, f("Crossette: %d Sterne teilen sich, dann %d", before, max));
        Firework ring = one("0 100 RING COPPER");
        double flat = 1;
        for (int i = 0; i < 6 * 120; i++) {
            ring.step(1 / 120.0);
            Firework.Shell s = ring.shells().get(0);
            if (s.burst && ring.time() - s.burstT > 0.4 && flat == 1) flat = planar(ring, s);
        }
        check(flat < 0.12, f("Ring: die Sterne liegen in einer Ebene (Abweichung %.0f %% des Halbmessers)", flat * 100));
        Firework fan = one("0 100 FAN GOLD");
        double xmin = 0, xmax = 0, ymax = 0;
        for (int i = 0; i < 3 * 60; i++) {
            fan.step(1 / 60.0);
            for (Firework.Star st : fan.stars()) { xmin = Math.min(xmin, st.x); xmax = Math.max(xmax, st.x); ymax = Math.max(ymax, st.y); }
        }
        check(fan.stars().size() == 7 && xmax - xmin > 60 && ymax > 40, f("Fächer: sieben Kometen vom Boden, %.0f m breit, bis %.0f m hoch", xmax - xmin, ymax));
        int[] counts = new int[4];
        for (Firework.Quality q : Firework.Quality.values()) {
            Firework k = one("0 100 PEONY WHITE");
            k.quality = q;
            for (int i = 0; i < 5 * 30; i++) k.step(1 / 30.0);
            counts[q.ordinal()] = k.starsMade();
        }
        check(counts[0] < counts[1] && counts[1] < counts[2] && counts[2] < counts[3] && counts[2] == 150,
                f("Stufen: %d / %d / %d / %d Sterne je Bombe von 100 mm", counts[0], counts[1], counts[2], counts[3]));
        Firework a = Firework.demo(0, 0), b = Firework.demo(0, 0);
        for (int i = 0; i < 20 * 30; i++) { a.step(1 / 30.0); b.step(1 / 30.0); }
        boolean same = a.stars().size() == b.stars().size();
        for (int i = 0; same && i < a.stars().size(); i++) same = a.stars().get(i).x == b.stars().get(i).x;
        check(same, "gleicher Startwert, gleiches Feuerwerk");
        Firework tw = Firework.travemuenderWoche(0, 0);
        tw.quality = Firework.Quality.MEDIUM;
        long t0 = System.nanoTime();
        int pk = 0;
        for (double t = 0; t < tw.duration(); t += 1 / 30.0) { tw.step(1 / 30.0); pk = Math.max(pk, tw.stars().size()); }
        double s = (System.nanoTime() - t0) / 1e9;
        check(tw.shells().size() > 80 && tw.duration() > 300 && s < tw.duration() * 0.2,
                f("Travemünder Woche: %d Bomben in %.0f s, bis %,d Sterne zugleich, gerechnet in %.1f s", tw.shells().size(), tw.duration(), pk, s));
    }

    /** Größte Abweichung der Sterne von ihrer Ebene durch die Mitte, im Verhältnis zum mittleren Abstand. */
    static double planar(Firework f, Firework.Shell s) {
        java.util.List<double[]> p = new java.util.ArrayList<>();
        double cx = 0, cy = 0, cz = 0;
        for (Firework.Star st : f.stars()) if (st.alive) { p.add(new double[]{st.x, st.y, st.z}); cx += st.x; cy += st.y; cz += st.z; }
        int n = p.size();
        cx /= n; cy /= n; cz /= n;
        // Normale als Kreuzprodukt zweier Sterne, die etwa einen Viertelkreis auseinander liegen
        double[] a = p.get(0), b = p.get(n / 4);
        double[] u = {a[0] - cx, a[1] - cy, a[2] - cz}, v = {b[0] - cx, b[1] - cy, b[2] - cz};
        double[] nn = {u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
        double l = Math.sqrt(nn[0] * nn[0] + nn[1] * nn[1] + nn[2] * nn[2]);
        double dev = 0, rad = 0;
        for (double[] q : p) {
            double dx = q[0] - cx, dy = q[1] - cy, dz = q[2] - cz;
            dev = Math.max(dev, Math.abs((dx * nn[0] + dy * nn[1] + dz * nn[2]) / l));
            rad += Math.sqrt(dx * dx + dy * dy + dz * dz) / n;
        }
        return dev / rad;
    }

    static void spektrum() {
        System.out.println("Spektrum");
        double yInt = 0, xPeak = 0, peakAt = 0;
        for (double l = 360; l <= 830; l += 1) {
            double[] c = Spectrum.cmf(l);
            yInt += c[1];
            if (c[0] > xPeak) { xPeak = c[0]; peakAt = l; }
        }
        check(Math.abs(yInt / 106.86 - 1) < 0.02 && Math.abs(peakAt - 599) < 4, f("Normspektralwerte: ∫ȳ dλ = %.1f nm (CIE 106,9), x̄ am größten bei %.0f nm", yInt, peakAt));
        double[] sr = Spectrum.chromaticity(Firework.Salt.STRONTIUM), ba = Spectrum.chromaticity(Firework.Salt.BARIUM), cu = Spectrum.chromaticity(Firework.Salt.COPPER),
                na = Spectrum.chromaticity(Firework.Salt.SODIUM), ca = Spectrum.chromaticity(Firework.Salt.CALCIUM), wh = Spectrum.chromaticity(Firework.Salt.WHITE),
                au = Spectrum.chromaticity(Firework.Salt.GOLD), vi = Spectrum.chromaticity(Firework.Salt.VIOLET);
        check(sr[0] > 0.62 && ba[1] > 0.6 && ba[0] < 0.3 && cu[0] < 0.2 && cu[1] < 0.15 && na[0] > 0.52 && na[0] < 0.6 && ca[0] > na[0] && ca[0] < sr[0],
                f("Farbörter: Strontium x %.3f (rot), Calcium x %.3f, Natrium x %.3f (gelb), Barium x %.3f y %.3f (grün), Kupfer x %.3f y %.3f (blau)",
                        sr[0], ca[0], na[0], ba[0], ba[1], cu[0], cu[1]));
        check(Math.abs(wh[0] - 0.42) < 0.02 && Math.abs(au[0] - 0.53) < 0.025 && vi[0] > cu[0] && vi[1] < 0.2,
                f("Glühen: Weiß (3 300 K) x %.3f y %.3f, Gold (1 900 K) x %.3f y %.3f; Violett x %.3f y %.3f", wh[0], wh[1], au[0], au[1], vi[0], vi[1]));
        boolean ok = true;
        String[] best = new String[Firework.Salt.values().length];
        for (Firework.Salt s : Firework.Salt.values()) {
            double[] c = s.rgb();
            double y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
            ok &= c[0] >= 0 && c[1] >= 0 && c[2] >= 0 && Math.abs(y - 1) < 1e-9;
            best[s.ordinal()] = c[0] >= c[1] && c[0] >= c[2] ? "R" : c[1] >= c[2] ? "G" : "B";
        }
        double[] r = Firework.Salt.STRONTIUM.rgb(), gr = Firework.Salt.BARIUM.rgb(), bl = Firework.Salt.COPPER.rgb();
        check(ok && best[0].equals("R") && best[3].equals("G") && best[4].equals("B") && r[0] > 4 * r[1] && bl[2] > 3 * bl[1],
                f("lineares sRGB mit Leuchtdichte 1: Strontium (%.2f, %.2f, %.2f), Barium (%.2f, %.2f, %.2f), Kupfer (%.2f, %.2f, %.2f)",
                        r[0], r[1], r[2], gr[0], gr[1], gr[2], bl[0], bl[1], bl[2]));
    }

    static void licht() {
        System.out.println("Licht");
        Firework f = one("0 150 PEONY BARIUM");
        double peakCd = 0, flash = 0;
        double[] at = null, bp = null;
        for (int i = 0; i < 10 * 120; i++) {
            f.step(1 / 120.0);
            Firework.Shell s = f.shells().get(0);
            if (s.burst && bp == null) bp = new double[]{s.x, s.y, s.z};
            for (Firework.Light l : f.lights()) {
                if (l.r() == l.g()) { flash = Math.max(flash, l.cd()); continue; }   // der Blitz ist weiß
                if (l.cd() > peakCd) { peakCd = l.cd(); at = new double[]{l.x(), l.y(), l.z(), l.g() / l.cd()}; }
            }
        }
        int stars = f.starsMade();
        check(bp != null && at != null && peakCd > stars * 1000 && Math.hypot(at[0] - bp[0], at[2] - bp[2]) < 15 && at[3] > 1.2 && flash > 1e6,
                f("Licht je Bombe: %d grüne Sterne zusammen bis %,.0f cd, Schwerpunkt %.0f m neben der Zerlegung, Grünanteil %.2f; Blitz %,.0f cd",
                        stars, peakCd, at == null ? -1 : Math.hypot(at[0] - bp[0], at[2] - bp[2]), at == null ? 0 : at[3], flash));
        double lux = peakCd / (700 * 700 + 180 * 180);
        check(lux > 0.1 && lux < 5, f("am Ufer in 700 m: %.2f lx (Vollmond 0,25 lx)", lux));
        Firework t = one("0 100 PEONY STRONTIUM");
        t.trailSeconds = 4;
        int full = 0, cap = 0;
        for (int i = 0; i < 9 * 60; i++) t.step(1 / 60.0);
        for (Firework.Star st : t.stars()) { full = Math.max(full, st.trailCount); cap = st.trail.length / 4; }
        check(cap >= 120 && full == cap, f("Langzeitbelichtung: Spuren über 4 s (%d Punkte je Stern)", full));
    }

    static void rauch() {
        System.out.println("Rauch");
        Firework f = one("0 150 PEONY WHITE");
        f.wind = (x, y, z, t, o) -> { o[0] = 4; o[1] = 0; o[2] = 0; };
        double x0 = 0, r0 = 0;
        int n0 = 0;
        for (int i = 0; i < 40 * 30; i++) {
            f.step(1 / 30.0);
            if (Math.abs(f.time() - 8) < 1e-6 + 1 / 60.0 && n0 == 0) {
                for (Firework.Smoke s : f.smoke()) { x0 += s.x; r0 += s.radius; n0++; }
                x0 /= n0; r0 /= n0;
            }
        }
        double x1 = 0, r1 = 0;
        int n1 = 0;
        for (Firework.Smoke s : f.smoke()) { x1 += s.x; r1 += s.radius; n1++; }
        x1 /= Math.max(1, n1);
        r1 /= Math.max(1, n1);
        check(n0 > 0 && n1 > 0 && Math.abs((x1 - x0) / 128 - 1) < 0.15 && r1 > r0, f("Rauch zieht mit dem Wind (%.0f m in 32 s bei 4 m/s) und wächst (%.1f → %.1f m)", x1 - x0, r0, r1));
    }

    /** Die Felder für die Grafikkarte (ohne GL gefüllt): Licht bleibt erhalten, Rauch hinten nach vorn, Shader vorhanden. */
    static void darstellung() {
        System.out.println("Darstellung (gl/FireworkGl, ohne Grafikkarte)");
        Firework f = Firework.demo(0, 0);
        for (int i = 0; i < 31 * 60; i++) f.step(1 / 60.0);
        com.dan.firework.gl.FireworkGl gl = new com.dan.firework.gl.FireworkGl();
        double[] eye = {0, 3, 600};
        gl.prepare(f, eye);
        double cd = 0, up = 0;
        for (Firework.Star s : f.stars()) cd += s.candela();
        float[] st = gl.starData();
        for (int i = 0; i < gl.stars(); i++) up += st[i * 8 + 3];
        check(gl.stars() > 0 && Math.abs(up / cd - 1) < 1e-4, f("%d Sterne mit zusammen %,.0f cd hochgeladen (%,.0f cd im Feuerwerk)", gl.stars(), up, cd));
        float[] sm = gl.smokeData();
        boolean sorted = true;
        for (int i = 1; i < gl.smokes(); i++) {
            double a = dd(sm, (i - 1) * 8, eye), b = dd(sm, i * 8, eye);
            if (b > a + 1e-3) { sorted = false; break; }
        }
        check(gl.smokes() > 0 && sorted, f("%,d Rauchwolken, hinten nach vorn", gl.smokes()));
        check(gl.trailVertices() % 2 == 0 && gl.segments() > 0, f("%,d Stücke Funkenschweif", gl.segments()));
        boolean all = true;
        for (String file : com.dan.firework.gl.FireworkGl.files()) all &= com.dan.firework.gl.FireworkGl.class.getResource(file) != null;
        check(all, "Shader liegen neben der Klasse: " + String.join(", ", com.dan.firework.gl.FireworkGl.files()));
    }

    static double dd(float[] a, int o, double[] e) {
        double x = a[o] - e[0], y = a[o + 1] - e[1], z = a[o + 2] - e[2];
        return x * x + y * y + z * z;
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMAN, s, a); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private FireworkCheck() { }
}
