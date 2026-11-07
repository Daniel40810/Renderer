package com.dan.film.demo;

import com.dan.film.Curve;
import com.dan.film.Film;
import com.dan.film.Format;
import com.dan.film.Grade;
import com.dan.film.Stock;

import java.util.Locale;

/**
 * Selbsttest von com.dan.film ohne Grafikkarte: Graukarte neutral, Kopierlicht in Punkten, Systemgamma, Spielraum,
 * orange Maske, Selwyn, Halationsring, Bildstand, Flackern, Kennlinie stetig. Endet mit „Alles in Ordnung.“
 */
public final class FilmCheck {
    private static int ok, bad;

    public static void main(String[] a) {
        Locale.setDefault(Locale.GERMAN);
        System.out.println("com.dan.film – Prüfung");
        Film v3 = new Film();
        Film ec = new Film();
        ec.negative = Stock.eastmanColor5248();
        ec.print = Stock.print5382();
        ec.format = Format.CINEMASCOPE;
        for (Film f : new Film[]{v3, ec}) {
            String n = f.negative.name();
            double[] dp = f.printDensity(f.negativeDensity(f.layerExposure(0.18, 0.18, 0.18)));
            double[] dm = {f.print.curves()[0].dmin(), f.print.curves()[1].dmin(), f.print.curves()[2].dmin()};
            check(n + ": Graukarte in der Kopie auf dieselbe Nettodichte in allen Farben (Grün D = 1,0)", Math.abs(dp[1] - 1) < 1e-6
                    && Math.abs(dp[0] - dm[0] - (dp[1] - dm[1])) < 1e-6 && Math.abs(dp[2] - dm[2] - (dp[1] - dm[1])) < 1e-6, "%.4f / %.4f / %.4f", dp[0], dp[1], dp[2]);
            double[] g = f.develop(0.18, 0.18, 0.18);
            check(n + ": Graukarte auf der Leinwand neutral (Nebenabsorption zeilenweise auf 1)", Math.abs(g[0] - g[2]) / g[1] < 1e-6 && Math.abs(g[0] - g[1]) / g[1] < 1e-6, "%.4f %.4f %.4f", g[0], g[1], g[2]);
            Curve c = f.negative.curves()[0];
            check(n + ": orange Maske (D_min rot < grün < blau)", f.negative.curves()[0].dmin() < f.negative.curves()[1].dmin() && f.negative.curves()[1].dmin() < f.negative.curves()[2].dmin(),
                    "%.2f / %.2f / %.2f", f.negative.curves()[0].dmin(), f.negative.curves()[1].dmin(), f.negative.curves()[2].dmin());
            double e = 0.05, y1 = f.develop(0.18 * Math.pow(10, -e), 0.18 * Math.pow(10, -e), 0.18 * Math.pow(10, -e))[1],
                    y2 = f.develop(0.18 * Math.pow(10, e), 0.18 * Math.pow(10, e), 0.18 * Math.pow(10, e))[1];
            double gam = Math.log10(y2 / y1) / (2 * e);
            check(n + ": Systemgamma an der Graukarte 1,25 bis 1,7 (Negativ γ · Kopie γ, im weichen Fuß der Kopie etwas weniger)", gam > 1.25 && gam < 1.7, "%.2f (Negativ %.2f · Kopie %.2f)", gam, c.gamma(), f.print.curves()[0].gamma());
            boolean mono = true;
            double last = -1;
            for (double st = -12; st <= 12; st += 0.25) {
                double v = 0.18 * Math.pow(2, st), y = f.develop(v, v, v)[1];
                if (y < last - 1e-12) mono = false;
                last = y;
            }
            check(n + ": heller in der Szene ist heller auf der Leinwand (−12 bis +12 Blenden), höchstens 1", mono && last <= 1, "bei +12: %.4f", last);
        }
        double l3 = v3.negative.curves()[1].latitudeStops(), l5 = ec.negative.curves()[1].latitudeStops();
        check("Spielraum: Vision3 rund 14 Blenden, 5248 rund 9", l3 > 13 && l3 < 16 && l5 > 8 && l5 < 10.5, "%.1f und %.1f Blenden", l3, l5);
        // Kopierlicht: ein Punkt mehr Rot ändert die Dichte der Kopie in Rot um γ_Kopie · 0,025
        Film pl = new Film();
        double d0 = pl.printDensity(pl.negativeDensity(pl.layerExposure(0.18, 0.18, 0.18)))[0];
        pl.grade = new Grade("R+1", 26, 25, 25, 0);
        double d1 = pl.printDensity(pl.negativeDensity(pl.layerExposure(0.18, 0.18, 0.18)))[0];
        double want = pl.print.curves()[0].slope(pl.print.curves()[0].inverse(1.0)) * Grade.POINT;
        check("Kopierlicht: +1 Punkt Rot → Dichte Rot der Kopie + γ · 0,025 (mehr Cyan, weniger Rot)", Math.abs((d1 - d0) - want) / want < 0.05, "%.4f (erwartet %.4f)", d1 - d0, want);
        Film warm = new Film();
        warm.grade = Grade.ABEND_1958;
        double[] w = warm.develop(0.18, 0.18, 0.18);
        check("Kopierlicht „Abend 1958“ macht Grau warm", w[0] > w[1] && w[1] > w[2], "%.3f %.3f %.3f", w[0], w[1], w[2]);
        // Selwyn
        double s1 = v3.grainSigma(v3.negative, 1, 1, 100), s4 = v3.grainSigma(v3.negative, 1, 1, 400);
        check("Korn nach Selwyn: vierfache Fläche, halbe Streuung", Math.abs(s1 / s4 - 2) < 1e-9, "%.4f gegen %.4f", s1, s4);
        double a3 = v3.format.pixelAreaUm2(1920), a5 = ec.format.pixelAreaUm2(1920);
        double g3 = v3.grainSigma(v3.negative, 1, 1, a3), g5 = ec.grainSigma(ec.negative, 1, 1, a5);
        check("Korn bei 1920 Bildpunkten: 5248 auf CinemaScope gröber als Vision3 auf Super 35", g5 > g3, "σ_D %.4f gegen %.4f (Fläche je Bildpunkt %.0f und %.0f µm²)", g5, g3, a5, a3);
        // Halation
        double r = v3.negative.halationRadiusMm(), th = Math.asin(1 / 1.48), rr = 2 * 0.127 * Math.tan(th);
        check("Halation: Ring bei 2 · t · tan(arcsin 1/n)", Math.abs(r - rr) < 1e-9 && r > 0.2 && r < 0.3, "%.3f mm (θ_c = %.1f°)", r, Math.toDegrees(th));
        Stock clear = new Stock("klar", "", 0, 0, false, ec.negative.curves(), ec.negative.matrix(), ec.negative.dye(), 0, ec.negative.layerGrain(),
                ec.negative.baseMm(), 1.48, 0, false, 0.02, ec.negative.halationRgb(), new double[3]);
        double f0 = Film.halationProfile(clear, 0), fres = Math.pow(0.48 / 2.48, 2);
        check("Halation: senkrecht nur die Fresnel-Reflexion ((n − 1)/(n + 1))², ab θ_c Totalreflexion", Math.abs(f0 - fres) < 1e-9
                && Math.abs(Film.halationProfile(clear, 1.0001) / Math.pow(Math.cos(Math.asin(1 / 1.48)), 4) - 1) < 1e-3, "R(0) = %.4f", f0);
        float[] tc = Film.halationTable(clear), t5 = Film.halationTable(ec.negative), tv = Film.halationTable(v3.negative);
        int pk5 = 0;
        for (int i = 1; i < t5.length; i++) if (t5[i] > t5[pk5]) pk5 = i;
        double rho5 = Film.HALATION_REACH * pk5 / (t5.length - 1);
        boolean mono = true;
        for (int i = 1; i < tv.length; i++) mono &= tv[i] <= tv[i - 1] + 1e-6;
        check("Halation: 5248 (Luft hinten) mit Ring bei r_c, Vision3 (Rückschicht) ohne Ring", rho5 > 0.95 && rho5 < 1.25 && mono && t5[4] < 0.4,
                "Gipfel bei %.2f r_c, innen %.2f", rho5, t5[4]);
        double outC = 0, allC = 0, out5 = 0, all5 = 0;
        for (int i = 0; i < tc.length; i++) {
            double rho = Film.HALATION_REACH * i / (tc.length - 1);   // Fläche ∝ ρ dρ
            allC += tc[i] * rho; all5 += t5[i] * rho;
            if (rho > 2) { outC += tc[i] * rho; out5 += t5[i] * rho; }
        }
        check("Halation: der graue Träger dämpft die langen, schrägen Wege", out5 / all5 < 0.5 * (outC / allC), "jenseits 2 r_c: %.1f %% gegen %.1f %% (klar)", 100 * out5 / all5, 100 * outC / allC);
        // ein Licht, ein Bildpunkt: Energie bleibt, Ring am rechten Ort (anamorphotisch eine Ellipse)
        int pw = 400, hgt = 300;
        float[] src = new float[pw * hgt];
        src[150 * pw + 200] = 1;
        double[] rr2 = ec.halationRadiusPx2(1920);
        float[] hl = Film.halate(src, pw, hgt, rr2[0], rr2[1], t5);
        double e = 0;
        for (float x : hl) e += x;
        int bx = 0;
        for (int x = 200; x < pw; x++) if (hl[150 * pw + x] > hl[150 * pw + bx]) bx = x;
        int by = 0;
        for (int y = 150; y < hgt; y++) if (hl[y * pw + 200] > hl[by * pw + 200]) by = y;
        double rx = bx - 200, ry = by - 150;
        check("Halation: ein Licht von einem Bildpunkt – Energie erhalten, Ring bei r_c (CinemaScope: Ellipse 2 : 1)", Math.abs(e - 1) < 0.03
                && Math.abs(rx / rr2[0] - 1.1) < 0.3 && Math.abs(ry / rr2[1] - 1.1) < 0.35, "Summe %.3f, Ring %.0f × %.0f px (r_c %.1f × %.1f)", e, rx, ry, rr2[0], rr2[1]);
        check("Halation: Vision3 (Rückschicht) schwächer als 5248", v3.negative.halation() < ec.negative.halation(), "%.3f gegen %.3f", v3.negative.halation(), ec.negative.halation());
        Grade gp = Grade.parse("24-25-26 −1"), gq = Grade.parse("27 25 22 +1"), gm = Grade.mix(Grade.NEUTRAL, Grade.NACHT, 0.5);
        check("Kopierlicht aus Text („24-25-26 −1“, „27 25 22 +1“) und dazwischen", gp.red() == 24 && gp.blue() == 26 && gp.density() == -1 && gq.equals(new Grade(gq.name(), 27, 25, 22, 1))
                && gm.red() == 26 && gm.blue() == 23.5 && gm.density() == 0.5, "%s · %s · Mitte %s %.1f-%.1f-%.1f", gp.name(), gq.name(), gm.name(), gm.red(), gm.green(), gm.blue());
        // Alter der Kopie
        Film alt = new Film();
        alt.negative = Stock.eastmanColor5248();
        alt.print = Stock.print5382();
        alt.printAgeYears = 68;
        double[] ga = alt.develop(0.18, 0.18, 0.18), gw = alt.develop(0.9, 0.9, 0.9);
        Film neu = new Film();
        neu.printAgeYears = 68;
        double[] gn = neu.develop(0.18, 0.18, 0.18);
        check("Kopie von 1958 im Jahr 2026: Gelb und Cyan verblasst, Grau wird purpur; 2383 hält fast", ga[0] > ga[1] && ga[2] > ga[1] && gw[0] > gw[1]
                && Math.abs(gn[0] - gn[1]) / gn[1] < 0.1 && (ga[0] - ga[1]) / ga[1] > 5 * (gn[0] - gn[1]) / gn[1], "5248/5382 %.3f %.3f %.3f, 2383 %.3f %.3f %.3f", ga[0], ga[1], ga[2], gn[0], gn[1], gn[2]);
        // Bildstand und Flackern
        double sx = 0, sy = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) { double[] wv = v3.weaveMm(i / 24.0); sx += wv[0] * wv[0]; sy += wv[1] * wv[1]; }
        sx = Math.sqrt(sx / n);
        sy = Math.sqrt(sy / n);
        check("Bildstand: Streuung wie eingestellt (±30 %)", Math.abs(sx / v3.weaveXmm - 1) < 0.3 && Math.abs(sy / v3.weaveYmm - 1) < 0.3, "%.4f / %.4f mm (Vorgabe %.3f / %.3f)", sx, sy, v3.weaveXmm, v3.weaveYmm);
        double[] w1 = v3.weaveMm(3.21), w2 = new Film().weaveMm(3.21);
        check("Bildstand: wiederholbar mit gleichem seed", w1[0] == w2[0] && w1[1] == w2[1], "");
        double fs = 0;
        for (int i = 0; i < n; i++) { double f = v3.flicker(i) - 1; fs += f * f; }
        check("Flackern: Streuung wie eingestellt (±10 %)", Math.abs(Math.sqrt(fs / n) / v3.flickerSigma - 1) < 0.1, "%.4f", Math.sqrt(fs / n));
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen.");
        System.exit(bad == 0 ? 0 : 1);
    }

    private static void check(String what, boolean pass, String fmt, Object... v) {
        if (pass) ok++; else bad++;
        System.out.println((pass ? "  ok    " : "  FEHLER ") + what + (fmt.isEmpty() ? "" : ": " + String.format(Locale.GERMAN, fmt, v)));
    }

    private FilmCheck() { }
}
