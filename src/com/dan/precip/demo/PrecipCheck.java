package com.dan.precip.demo;

import com.dan.precip.DropOptics;
import com.dan.precip.Drops;
import com.dan.precip.Precip;
import com.dan.precip.Ripples;
import com.dan.precip.Splash;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Pakets ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10), gegen Werte aus der Literatur. Endet
 * mit „Alles in Ordnung.“. Geprüft werden Verteilung und Regenmenge, Fallgeschwindigkeit (Gunn und Kinzer), dünne Luft,
 * Luftwiderstand und Böen, Form (Beard und Chuang), Schwingung (Rayleigh), Kennzahlen des Einschlags, die Wellen
 * (Lamb: langsamste Welle 23,1 cm/s bei 1,71 cm, kleinste Gruppengeschwindigkeit 17,7 cm/s), das Wellenfeld (genaue
 * Schwingung, ruhige Mitte des Rings), die Optik des Tropfens (Regenbögen bei 42° und 51°, Alexanders Band, Farbfolge,
 * Energie je Ordnung), Sicht, Reflektivität und der Lauf (Böen, Karte, Kasten, Reproduzierbarkeit).
 */
public final class PrecipCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        PrecipCheck c = new PrecipCheck();
        for (String l : c.run()) System.out.println(l);
        if (c.bad > 0) System.exit(1);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private void head(String h) { lines.add(h); }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    public List<String> run() {
        lines.clear();
        bad = 0;
        drops();
        fall();
        shape();
        impact();
        waves();
        field();
        optics();
        controller();
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        return lines;
    }

    // ------------------------------------------------------------------ Verteilung

    private void drops() {
        head("Verteilung und Regenmenge");
        double worst = 0;
        for (Drops.Dsd d : Drops.Dsd.values())
            for (double r : new double[]{0.2, 1, 5, 25, 80}) worst = Math.max(worst, Math.abs(Drops.rateIntegral(d.n0(r), d.lambda(r)) / r - 1));
        check(worst < 1e-9, f("abgeglichenes N0 ergibt die Regenmenge genau (größter Fehler %.1e)", worst));
        double lo = 1e9, hi = 0;
        for (double r : new double[]{1, 5, 25}) {
            double q = Drops.Dsd.LANDREGEN.n0(r) / Drops.Dsd.LANDREGEN.n0Literature;
            lo = Math.min(lo, q);
            hi = Math.max(hi, q);
        }
        check(lo > 0.75 && hi < 1.3, f("Marshall-Palmer: abgeglichenes N0 liegt bei %.2f bis %.2f des Werts 8000 (1 bis 25 mm/h)", lo, hi));
        Drops.Dsd mp = Drops.Dsd.LANDREGEN;
        double r5 = 5, n0 = mp.n0(r5), lam = mp.lambda(r5);
        double z = Drops.reflectivity(n0, lam), dbz = 10 * Math.log10(z), dbzMp = 10 * Math.log10(200 * Math.pow(r5, 1.6));
        check(Math.abs(dbz - dbzMp) < 1.5, f("Radar bei 5 mm/h: %.1f dBZ, Marshall-Palmer Z = 200 R^1,6: %.1f dBZ", dbz, dbzMp));
        double lwc = Drops.liquidWater(n0, lam), lwcLit = 0.072 * Math.pow(r5, 0.88);
        check(Math.abs(lwc / lwcLit - 1) < 0.2, f("Wasser in der Luft bei 5 mm/h: %.3f g/m³, Literatur 0,072 R^0,88: %.3f g/m³", lwc, lwcLit));
        double vis = 3.912 / Drops.extinction(n0, lam) / 1000, visLit = 11.6 * Math.pow(r5, -0.63);
        check(vis > visLit * 0.6 && vis < visLit * 1.6, f("Sicht bei 5 mm/h: %.1f km, Atlas (1953) 11,6 R^−0,63: %.1f km", vis, visLit));
        double nd = Drops.numberDensity(n0, lam, 0.5, 6), imp = Drops.impacts(n0, lam, 0.3, 6);
        check(nd > 300 && nd < 900, f("5 mm/h: %.0f Tropfen über 0,5 mm je m³, %.0f Einschläge je m² und s über 0,3 mm", nd, imp));
        // Ziehen aus der Verteilung
        java.util.SplittableRandom rng = new java.util.SplittableRandom(7);
        double d0 = 1.2, d1 = 6, sum = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) sum += Drops.sample(lam, d0, d1, rng.nextDouble()) - d0;
        double span = d1 - d0, e = Math.exp(-lam * span), mean = 1 / lam - span * e / (1 - e);
        check(Math.abs(sum / n - mean) < 0.005, f("gezogene Größen über 1,2 mm: mittlerer Überschuss %.4f mm, Formel %.4f mm", sum / n, mean));
        // Einschläge nach N v: mittlere Größe gegen das Integral ∫ D N v / ∫ N v
        double si = 0, num = 0, den = 0;
        for (int i = 0; i < n; i++) si += Drops.sampleImpact(lam, 0.3, 6, rng);
        for (double dd = 0.3 + 0.0025; dd < 6; dd += 0.005) { double w = Math.exp(-lam * dd) * Drops.terminal(dd); num += dd * w; den += w; }
        double dN = 0;
        for (int i = 0; i < n; i++) dN += Drops.sample(lam, 0.3, 6, rng.nextDouble());
        check(Math.abs(si / n - num / den) < 0.004 && si / n > dN / n + 0.1,
                f("Einschläge nach N·v: mittlere Größe %.3f mm (Integral %.3f mm), nach N allein wären es %.3f mm", si / n, num / den, dN / n));
        double vprev = 0;
        boolean mono = true;
        for (double dd = 0.05; dd <= 6.0; dd += 0.01) { double vv = Drops.terminal(dd); mono &= vv >= vprev - 1e-9; vprev = vv; }
        check(mono, "Fallgeschwindigkeit steigt mit der Größe bis 6 mm (Grundlage des Verwerfens nach v / v(dmax))");
        double dm = Drops.dminFor(n0, lam, 55.6, 6);
        check(Math.abs(Drops.numberDensity(n0, lam, dm, 6) - 55.6) < 0.01, f("kleinste Größe für 55,6 Tropfen je m³: %.2f mm", dm));
    }

    // ------------------------------------------------------------------ Fall

    private void fall() {
        head("Fall");
        double worst = 0, at = 0;
        for (double[] p : Drops.GUNN_KINZER) {
            double e = Math.abs(Drops.terminal(p[0]) / p[1] - 1);
            if (e > worst) { worst = e; at = p[0]; }
        }
        check(worst < 0.03, f("Fallgeschwindigkeit gegen Gunn und Kinzer (0,1 bis 5,6 mm): größte Abweichung %.1f %% bei %.1f mm", worst * 100, at));
        check(Drops.terminal(1) > 3.9 && Drops.terminal(2) > 6.4 && Drops.terminal(5.6) > 9.0,
                f("1/2/5,6 mm: %.2f/%.2f/%.2f m/s (gemessen 4,03/6,49/9,16)", Drops.terminal(1), Drops.terminal(2), Drops.terminal(5.6)));
        boolean mono = true;
        for (double d = 0.01; d < 6; d += 0.01) if (Drops.terminal(d + 0.01) < Drops.terminal(d) - 1e-12) mono = false;
        check(mono, "Fallgeschwindigkeit steigt stetig mit der Größe");
        double up = Drops.terminal(2, Drops.airDensity(1000)) / Drops.terminal(2) - 1;
        check(up > 0.03 && up < 0.07, f("in 1000 m Höhe fällt ein Tropfen %.1f %% schneller (Foote und du Toit: rund 5 %%)", up * 100));
        // Luftwiderstand: aus der Ruhe auf die Fallgeschwindigkeit, einer Böe folgen
        double vt = Drops.terminal(2), vy = 0, vx = 0, dt = 1e-4;
        double[] a = new double[3];
        double t63 = -1;
        for (double t = 0; t < 5; t += dt) {
            Drops.drag(vx, vy, 0, 5, 0, 0, vt, a);
            vx += a[0] * dt;
            vy += (a[1] - Drops.G) * dt;
            if (t63 < 0 && -vy > 0.632 * vt) t63 = t;
        }
        double vrel = Math.hypot(vx - 5, vy);
        check(Math.abs(vrel / vt - 1) < 0.005 && Math.abs(vx - 5) < 0.05,
                f("2 mm im Wind von 5 m/s: nach 5 s %.2f m/s seitlich, %.2f m/s gegen die Luft (Fallgeschwindigkeit %.2f)", vx, vrel, vt));
        check(t63 > 0.3 * Drops.relaxation(2) && t63 < 1.2 * Drops.relaxation(2),
                f("63 %% der Fallgeschwindigkeit nach %.2f s, Zeitmaß vt/g = %.2f s", t63, Drops.relaxation(2)));
        check(Drops.relaxation(0.5) < 0.25 && Drops.relaxation(4) > 0.85, f("kleine Tropfen folgen Böen schnell (0,5 mm: %.2f s), große träge (4 mm: %.2f s)",
                Drops.relaxation(0.5), Drops.relaxation(4)));
    }

    // ------------------------------------------------------------------ Form und Schwingung

    private void shape() {
        head("Form und Schwingung");
        double[][] bc = {{1, 0.983}, {2, 0.928}, {3, 0.853}, {4, 0.779}, {5, 0.708}, {6, 0.641}};
        double worst = 0;
        for (double[] p : bc) worst = Math.max(worst, Math.abs(Drops.axisRatio(p[0]) - p[1]));
        check(worst < 0.01, f("Achsenverhältnis nach Beard und Chuang (1 bis 6 mm): %.3f/%.3f/%.3f/%.3f/%.3f/%.3f",
                Drops.axisRatio(1), Drops.axisRatio(2), Drops.axisRatio(3), Drops.axisRatio(4), Drops.axisRatio(5), Drops.axisRatio(6)));
        check(Drops.axisRatio(0.5) > 0.99 && Drops.axisRatio(0.1) == 1, "kleine Tropfen sind rund");
        double f2 = Drops.oscillationHz(2);
        check(Math.abs(f2 - 121.6) < 1, f("Schwingung nach Rayleigh: 2 mm %.1f Hz (Literatur rund 120 Hz), 4 mm %.1f Hz, 1 mm %.0f Hz", f2,
                Drops.oscillationHz(4), Drops.oscillationHz(1)));
    }

    // ------------------------------------------------------------------ Einschlag

    private void impact() {
        head("Einschlag");
        double v2 = Drops.terminal(2);
        check(Math.abs(Drops.weber(2, v2) - 1162) < 30, f("Weberzahl 2 mm mit %.2f m/s: %.0f", v2, Drops.weber(2, v2)));
        double dcrit = 0;
        for (double d = 0.1; d < 3; d += 0.01)
            if (Splash.kind(d, Drops.terminal(d), Splash.Surface.GROUND, false) == Splash.Kind.SPLASH) { dcrit = d; break; }
        check(dcrit > 0.5 && dcrit < 1.0, f("trockener Grund: ab %.2f mm spritzt ein Tropfen (K = Oh Re^1,25 > 57,7, Mundo u. a.)", dcrit));
        double dwet = 0;
        for (double d = 0.1; d < 3; d += 0.01)
            if (Splash.kind(d, Drops.terminal(d), Splash.Surface.GROUND, true) == Splash.Kind.SPLASH) { dwet = d; break; }
        check(dwet < dcrit, f("nasser Grund spritzt früher: ab %.2f mm", dwet));
        Splash.Kind k05 = Splash.kind(0.5, Drops.terminal(0.5), Splash.Surface.WATER, false), k2 = Splash.kind(2, v2, Splash.Surface.WATER, false),
                k4 = Splash.kind(4, Drops.terminal(4), Splash.Surface.WATER, false);
        check(k05 == Splash.Kind.COALESCE && k2 == Splash.Kind.JET && k4 == Splash.Kind.CROWN,
                f("auf Wasser: 0,5 mm %s, 2 mm %s, 4 mm %s (fließt ein, Strahl, Krone)", k05, k2, k4));
        double jh = Splash.jetHeight(2, v2);
        check(jh > 0.004 && jh < 0.02, f("Worthington-Strahl eines 2-mm-Tropfens: %.1f mm hoch, Krone 4 mm: %.0f mm Halbmesser", jh * 1000, Splash.crownRadius(4) * 1000));
    }

    // ------------------------------------------------------------------ Wellen

    private void waves() {
        head("Wellen (Lamb)");
        double km = Drops.kMin(), lam = 2 * Math.PI / km, c = Drops.phaseSpeed(km);
        check(Math.abs(lam - 0.0171) < 0.0003 && Math.abs(c - 0.231) < 0.002, f("langsamste Welle: %.2f cm lang, %.1f cm/s", lam * 100, c * 100));
        double[] g = Ripples.groupMinimum();
        check(Math.abs(g[0] - 0.177) < 0.003, f("kleinste Gruppengeschwindigkeit %.1f cm/s bei %.1f cm Wellenlänge (Literatur 17,7 cm/s, 4,4 cm)", g[0] * 100,
                2 * Math.PI / g[1] * 100));
        check(Math.abs(Drops.groupSpeed(km) - c) < 1e-6, "bei der langsamsten Welle sind Phasen- und Gruppengeschwindigkeit gleich");
        check(Drops.viscousDecay(km) < 0.4, f("Zähigkeit allein dämpft eine 1,7-cm-Welle mit %.2f/s (Ringe leben lange)", Drops.viscousDecay(km)));
    }

    // ------------------------------------------------------------------ Wellenfeld

    private void field() {
        head("Wellenfeld (spektral)");
        // genaue Schwingung einer ebenen Welle
        Ripples rp = new Ripples(64, 0.004);
        Ripples.Field fl = rp.new Field();
        double kx = 2 * Math.PI / rp.size * 8;
        fl.setHeight((x, zz) -> 1e-3 * Math.cos(kx * x));
        double dt = 1 / 120.0;
        for (int i = 0; i < 37; i++) fl.step(dt);
        double t = 37 * dt, w = Drops.omega(kx), gm = Ripples.decay(kx), wd = Math.sqrt(w * w - gm * gm);
        double want = 1e-3 * Math.exp(-gm * t) * (Math.cos(wd * t) + gm / wd * Math.sin(wd * t)) * Math.cos(kx * 0.5 * rp.dx);
        double got = fl.surface()[0][0];
        check(Math.abs(got - want) < 1e-8, f("ebene Welle 3,2 cm: Höhe nach %.2f s %.4f mm, genau %.4f mm", t, got * 1000, want * 1000));
        // ein Einschlag: Mulde, Ringe, ruhige Mitte
        Ripples big = new Ripples(256, 0.003);
        Ripples.Field ring = big.new Field();
        double cx = big.size / 2, d = 2, v = Drops.terminal(2);
        ring.impact(cx, cx, d, v);
        double crater = 0;
        for (int i = 0; i < 2; i++) ring.step(0.004);
        crater = -ring.surface()[0][128 * 256 + 128];
        while (ring.time < 0.2 - 1e-9) ring.step(1 / 120.0);
        double early = 0;
        for (double[] q : new double[][]{ring.surface()[1], ring.surface()[2]}) for (double x : q) early = Math.max(early, Math.abs(x));
        while (ring.time < 0.6 - 1e-9) ring.step(1 / 120.0);
        double[][] s = ring.surface();
        double cg = Ripples.groupMinimum()[0], tt = ring.time, rIn = 0.6 * cg * tt, rOut0 = cg * tt, rOut1 = Math.min(1.5 * cg * tt, big.size / 2 - 0.01);
        double inS = 0, outS = 0, maxS = 0;
        int inN = 0, outN = 0;
        for (int j = 0; j < 256; j++)
            for (int i = 0; i < 256; i++) {
                double r = Math.hypot((i + 0.5) * big.dx - cx, (j + 0.5) * big.dx - cx), sl = s[1][j * 256 + i] * s[1][j * 256 + i] + s[2][j * 256 + i] * s[2][j * 256 + i];
                if (r < rIn) { inS += sl; inN++; }
                else if (r > rOut0 && r < rOut1) { outS += sl; outN++; }
                maxS = Math.max(maxS, Math.sqrt(sl));
            }
        double ratio = Math.sqrt((inS / Math.max(1, inN)) / Math.max(1e-30, outS / Math.max(1, outN)));
        check(ratio < 0.35, f("ruhige Mitte: nach %.1f s ist die Steigung innerhalb %.1f cm nur %.0f %% der im Ring bei %.1f bis %.1f cm", tt, rIn * 100, ratio * 100,
                rOut0 * 100, rOut1 * 100));
        check(crater > 0.0005 && crater < 0.02 && maxS > 0.005 && maxS < 0.5,
                f("2-mm-Tropfen: Mulde %.1f mm nach 8 ms, größte Steigung nach 0,2 s %.3f, nach %.1f s %.3f", crater * 1000, early, tt, maxS));
        // die Schwingung bleibt in der Energie: ohne Quelle nimmt sie nur ab
        double e0 = energy(s);
        ring.step(0.1);
        double e1 = energy(ring.surface());
        check(e1 < e0 && e1 > 0.3 * e0, f("ohne Quelle klingt das Feld ab: Steigungsenergie nach 0,1 s %.0f %%", e1 / e0 * 100));
        craeye();
        roughness();
    }

    /** Die Ringe gegen die Messung: Craeye, Sobieski, Bliven, Guissard (1999), Tropfen aus 5,5 m (93 % der Fallgeschwindigkeit). */
    private void craeye() {
        for (double[] m : new double[][]{{2.8, 0.10}, {2.2, 0.05}}) {
            double d = m[0];
            Ripples rp = new Ripples(256, 0.002);
            Ripples.Field fl = rp.new Field();
            double c = rp.size / 2 + 0.001;
            fl.impact(c, c, d, 0.93 * Drops.terminal(d));
            while (fl.time < 0.8 - 1e-9) fl.step(1 / 200.0);
            double[] h = fl.surface()[0];
            double best = 0, at = 0;
            for (int j = 0; j < 256; j++)
                for (int i = 0; i < 256; i++) {
                    double r = Math.hypot((i + 0.5) * rp.dx - c, (j + 0.5) * rp.dx - c), a = Math.abs(h[j * 256 + i]);
                    if (r > 0.03 && r < 0.24 && a > best) { best = a; at = r; }
                }
            check(best > 0.6 * m[1] * 1e-3 && best < 1.6 * m[1] * 1e-3 && at > 0.10 && at < 0.18,
                    f("Ring eines %.1f-mm-Tropfens nach 0,8 s: %.3f mm hoch bei %.1f cm (gemessen rund %.2f mm bei 13 cm, Craeye u. a. 1999)", d, best * 1000, at * 100, m[1]));
        }
    }

    /** Die mittlere Steigung des Regenfelds gegen die Formel; die Rauigkeit unter dem Raster und was sie bedeutet. */
    private void roughness() {
        Drops.Dsd mp = Drops.Dsd.LANDREGEN;
        double rate = 5, n0 = mp.n0(rate), lam = mp.lambda(rate);
        Ripples rp = new Ripples(64, 0.008);
        Ripples.Field fl = rp.new Field();
        java.util.SplittableRandom rng = new java.util.SplittableRandom(11);
        double per = Drops.impacts(n0, lam, 0.3, 6) * rp.size * rp.size, dt = 1 / 60.0, acc = 0;
        int cnt = 0;
        for (int k = 0; k < 480; k++) {
            int c = (int) Math.floor(per * dt + rng.nextDouble());
            for (int i = 0; i < c; i++) {
                double d = Drops.sampleImpact(lam, 0.3, 6, rng);
                fl.impact(rng.nextDouble() * rp.size, rng.nextDouble() * rp.size, d, Drops.terminal(d));
            }
            fl.step(dt);
            if (k >= 120 && k % 3 == 0) { acc += energy(fl.surface()) / (64.0 * 64.0); cnt++; }
        }
        double got = acc / cnt, want = Ripples.slopeVariance(n0, lam, 0.3, 6, 0.008);
        check(Math.abs(got / want - 1) < 0.3, f("Regen 5 mm/h auf 8-mm-Raster: mittlere Steigung² %.2e, Formel %.2e", got, want));
        double all = Ripples.slopeVariance(n0, lam, 0.3, 6, 0), sub = Ripples.subgridVariance(n0, lam, 0.3, 6, 0.008);
        double u = Math.max(0, (all - 0.003) / 0.00512);
        check(sub > 0 && sub < all && all > 1e-3 && all < 0.05,
                f("5 mm/h: Wasser rms-Steigung %.3f (so rau wie %.1f m/s Wind nach Cox und Munk 1954), davon unter 8 mm %.0f %%", Math.sqrt(all), u, 100 * sub / all));
        StringBuilder st = new StringBuilder();
        double prev = 0;
        boolean up = true;
        for (Precip.Quality q : Precip.Quality.values()) {
            double c = Ripples.slopeVariance(n0, lam, 0.3, 6, q.rippleCell) / all;
            up &= c >= prev;
            prev = c;
            st.append(st.length() == 0 ? "" : ", ").append(f("%s %.0f %% (%.0f mm, %.1f m)", q, 100 * c, q.rippleCell * 1000, q.rippleSize()));
        }
        check(up && prev > 0.5, "5 mm/h: das Wellenfeld trägt von der Steigung " + st);
        // wie fleckig: Anteil der Fläche, der die Steigung unter 16 mm Wellenlänge trägt, auf einem 1-mm-Raster
        Ripples fine = new Ripples(256, 0.001);
        Ripples.Field ff = fine.new Field(), hp = fine.new Field();
        double perF = Drops.impacts(n0, lam, 0.3, 6) * fine.size * fine.size, m1 = 0, m2 = 0, m4 = 0;
        long nn = 0;
        for (int k = 0; k < 240; k++) {
            int c = (int) Math.floor(perF * (1 / 120.0) + rng.nextDouble());
            for (int i = 0; i < c; i++) {
                double d = Drops.sampleImpact(lam, 0.3, 6, rng);
                ff.impact(rng.nextDouble() * fine.size, rng.nextDouble() * fine.size, d, Drops.terminal(d));
            }
            ff.step(1 / 120.0);
            if (k >= 120 && k % 12 == 0) {
                System.arraycopy(ff.spec, 0, hp.spec, 0, ff.spec.length);
                for (int j = 0; j < 256; j++)
                    for (int i = 0; i < 256; i++) if (Math.hypot(fine.kAxis(i), fine.kAxis(j)) < Math.PI / 0.008) java.util.Arrays.fill(hp.spec, (j * 256 + i) * 4, (j * 256 + i) * 4 + 4, 0);
                double[][] su = hp.surface();
                for (int ax = 1; ax <= 2; ax++) for (double x : su[ax]) { m1 += Math.abs(x); m2 += x * x; m4 += x * x * x * x; nn++; }
            }
        }
        m1 /= nn; m2 /= nn; m4 /= nn;
        double pMeas = 2 / Math.PI * m1 * m1 / m2, pFit = Ripples.intermittency(5)[0], kurt = m4 / (m2 * m2);
        check(pMeas > 0.65 * pFit && pMeas < 1.5 * pFit,
                f("5 mm/h unter 16 mm: %.0f %% der Fläche tragen die Steigung (Ansatz %.0f %%, Kurtosis %.0f) – zwischen den Einschlägen bleibt der Spiegel glatt", 100 * pMeas, 100 * pFit, kurt));
    }

    private static double energy(double[][] s) {
        double e = 0;
        for (int m = 0; m < s[1].length; m++) e += s[1][m] * s[1][m] + s[2][m] * s[2][m];
        return e;
    }

    // ------------------------------------------------------------------ Optik

    private void optics() {
        head("Optik des Tropfens");
        double n589 = DropOptics.waterIndex(589), n400 = DropOptics.waterIndex(400), n700 = DropOptics.waterIndex(700);
        check(Math.abs(n589 - 1.3330) < 0.0005 && Math.abs(n400 - 1.3434) < 0.001 && Math.abs(n700 - 1.3306) < 0.001,
                f("Brechzahl von Wasser 400/589/700 nm: %.4f/%.4f/%.4f (Daimon und Masumura 2007: 1,3434/1,3330/1,3306)", n400, n589, n700));
        double b1 = DropOptics.bowRadius(n589, 2), b2 = DropOptics.bowRadius(n589, 3);
        check(Math.abs(b1 - 42.0) < 0.3 && Math.abs(b2 - 50.9) < 0.4, f("Hauptbogen %.2f°, Nebenbogen %.2f° um den Gegenpunkt der Sonne (Literatur 42,0° und 50,9°)", b1, b2));
        double r1 = DropOptics.bowRadius(n700, 2), v1 = DropOptics.bowRadius(n400, 2), r2 = DropOptics.bowRadius(n700, 3), v2 = DropOptics.bowRadius(n400, 3);
        check(r1 > v1 && r2 < v2, f("Hauptbogen außen rot (%.1f°) innen violett (%.1f°), Nebenbogen umgekehrt (rot %.1f°, violett %.1f°)", r1, v1, r2, v2));
        DropOptics o = new DropOptics();
        double f0 = o.fraction(0), f1 = o.fraction(1), f2 = o.fraction(2), f3 = o.fraction(3);
        check(Math.abs(f0 - 0.066) < 0.006 && f1 > 0.84 && f1 < 0.90 && f2 > 0.035 && f2 < 0.06 && f3 > 0.005 && f3 < 0.015,
                f("Licht je Ordnung bei 550 nm: gespiegelt %.1f %%, durch %.1f %%, Hauptbogen %.1f %%, Nebenbogen %.1f %%", f0 * 100, f1 * 100, f2 * 100, f3 * 100));
        double integral = 0;
        for (int i = 0; i < DropOptics.BINS; i++) {
            double a = Math.toRadians(i * DropOptics.STEP), b = Math.toRadians((i + 1) * DropOptics.STEP);
            integral += o.phase((i + 0.5) * DropOptics.STEP, 1) * 2 * Math.PI * (Math.cos(a) - Math.cos(b));
        }
        check(Math.abs(integral - (f0 + f1 + f2 + f3)) < 0.03, f("Phasenfunktion über die Kugel: %.3f (Summe der Ordnungen %.3f)", integral, f0 + f1 + f2 + f3));
        // Bogen, Alexanders Band
        double band = avg(o, 132, 136), inside = avg(o, 145, 160), bow = peak(o, 135, 142, 1), sec = peak(o, 125, 131, 1);
        check(bow > 2 * inside && band < 0.5 * inside && sec > band, f("Streuung bei 138°: Bogen %.3f, innen %.3f, Alexanders Band %.4f, Nebenbogen %.3f (1/sr)",
                bow, inside, band, sec));
        double rPk = argmax(o, 134, 142, 0), bPk = argmax(o, 134, 142, 2);
        check(rPk < bPk, f("Hauptbogen: Rot liegt bei %.2f° Streuwinkel, Blau bei %.2f° (rot außen)", rPk, bPk));
        double fw = o.phase(5, 1), side = o.phase(90, 1);
        check(fw > 20 * side, f("Gegenlicht: nach vorn (5°) %.2f/sr, zur Seite (90°) %.3f/sr", fw, side));
        double[][] rings = DropOptics.refractionRings(64, n589);
        double wsum = 0;
        boolean up = true;
        for (int i = 0; i < 64; i++) { wsum += rings[1][i]; if (i > 0 && rings[0][i] <= rings[0][i - 1]) up = false; }
        double lim = 2 * (90 - Math.toDegrees(Math.asin(1 / n589)));
        check(up && rings[0][63] > 70 && rings[0][63] < lim && Math.abs(lim - 82.8) < 0.1 && wsum > 0.85 && wsum < 0.95,
                f("im Tropfen steht die Welt auf dem Kopf: außen %.1f° (Grenze %.1f°), Durchlass %.3f", rings[0][63], lim, wsum));
        check(Math.abs(DropOptics.reflectance(n589) - 0.066) < 0.004, f("Spiegelung der Vorderseite über den Querschnitt %.4f", DropOptics.reflectance(n589)));
    }

    private static double avg(DropOptics o, double a, double b) {
        double s = 0;
        int n = 0;
        for (double t = a; t <= b; t += 0.25) { s += o.phase(t, 1); n++; }
        return s / n;
    }

    private static double peak(DropOptics o, double a, double b, int c) {
        double m = 0;
        for (double t = a; t <= b; t += 0.25) m = Math.max(m, o.phase(t, c));
        return m;
    }

    private static double argmax(DropOptics o, double a, double b, int c) {
        double m = -1, at = 0;
        for (double t = a; t <= b; t += 0.05) if (o.phase(t, c) > m) { m = o.phase(t, c); at = t; }
        return at;
    }

    // ------------------------------------------------------------------ Lauf

    private void controller() {
        head("Lauf");
        Precip p = Precip.moderate();
        p.quality = Precip.Quality.MEDIUM;
        p.step(0);
        Precip.Snapshot s = p.snapshot();
        check(Math.abs(s.dmin() - 1.28) < 0.05, f("5 mm/h, Stufe MEDIUM: Tropfen ab %.2f mm in voller Dichte", s.dmin()));
        check(Math.abs(s.densityAboveDmin() * p.quality.volume() - p.quality.drops) < 1,
                f("%d Tropfen im Kasten von %.0f m³ entsprechen genau der echten Dichte (%.1f je m³)", p.quality.drops, p.quality.volume(), s.densityAboveDmin()));
        Precip h = Precip.shower();
        h.step(0);
        Precip dz = Precip.drizzle();
        dz.step(0);
        check(h.snapshot().dmin() > s.dmin() && dz.snapshot().dmin() < s.dmin(), f("Schauer ab %.2f mm, Niesel ab %.2f mm", h.snapshot().dmin(), dz.snapshot().dmin()));
        // Böen
        p.gust = 0.3;
        p.windSpeed = 8;
        double sum = 0, sum2 = 0;
        int n = 0;
        for (int i = 0; i < 40_000; i++) {
            p.step(0.05);
            double g = p.snapshot().gustX() / 0.9;
            sum += g;
            sum2 += g * g;
            n++;
        }
        double mean = sum / n, sd = Math.sqrt(sum2 / n - mean * mean);
        check(Math.abs(sd / (0.3 * 8) - 1) < 0.15 && Math.abs(mean) < 0.4, f("Böen: Mittel %.2f m/s, Streuung %.2f m/s (Soll %.2f)", mean, sd, 0.3 * 8));
        // Karte der Schauer
        Precip c = Precip.moderate();
        c.curtain = (x, zz) -> Math.max(0, 1 - Math.hypot(x - 2000, zz) / 3000);
        c.eyeX = 500;
        c.step(0);
        Precip.Snapshot cs = c.snapshot();
        double want = 1 - Math.hypot(500 - 2000, 0) / 3000;
        check(cs.mapN() == Precip.MAP_N && Math.abs(cs.curtainAt(500, 0) - want) < 0.02 && cs.curtainAt(6000, 0) == 0,
                f("Karte: am Auge %.3f (Soll %.3f), weit weg trocken", cs.curtainAt(500, 0), want));
        check(Math.abs(cs.liveFraction(c.rate) - 1) < 1e-9 && cs.liveFraction(c.rate / 2) < 0.75 && cs.liveFraction(0) == 0,
                f("am halben Regen leben %.0f %% der Tropfen über dmin", cs.liveFraction(c.rate / 2) * 100));
        c.eyeX = 5000;
        c.step(0.1);
        check(Math.abs(c.snapshot().mapX0() - (Math.floor(5000 / (Precip.MAP_SIZE / Precip.MAP_N)) * (Precip.MAP_SIZE / Precip.MAP_N) - Precip.MAP_SIZE / 2)) < 1e-9,
                "die Karte folgt dem Auge");
        // Unterstand
        c.shelter = new double[]{10, 20, 3, 4};
        c.step(0);
        check(c.snapshot().sheltered(11, 1, 20) && !c.snapshot().sheltered(11, 5, 20) && !c.snapshot().sheltered(20, 1, 20), "Unterstand: drinnen trocken, darüber und daneben nass");
        // gleich gesät, gleicher Lauf
        Precip a = Precip.heavy(), b = Precip.heavy();
        for (int i = 0; i < 100; i++) { a.step(0.02); b.step(0.02); }
        check(a.snapshot().gustX() == b.snapshot().gustX() && a.snapshot().gustZ() == b.snapshot().gustZ(), "gleiche Saat, gleiche Böen");
        check(Math.abs(p.snapshot().shutterSim() - 1 / 250.0) < 1e-12, "Belichtung 1/250 s voreingestellt");
        p.timeScale = 0.05;
        p.step(0);
        check(Math.abs(p.snapshot().shutterSim() - 0.05 / 250) < 1e-12, "Zeitlupe: Belichtung in der Zeit der Tropfen");
        lines.add("  " + Precip.moderate().describe());
    }
}
