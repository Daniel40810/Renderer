package com.dan.dune.demo;

import com.dan.dune.Dune;
import com.dan.dune.FluxField;
import com.dan.dune.Grains;
import com.dan.dune.Ripples;
import com.dan.dune.SandBed;
import com.dan.dune.TimeLapse;
import com.dan.dune.Werner;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10), Stand Sylt Phase 8 (dune I – Flugsand). Fertig, wenn sie den Fluss
 * gegen Bagnold und die Schwelle trocken und feucht trifft; dazu Sättigungslänge, Hang, Beschleunigung über dem Kamm,
 * Windschatten, Sandbilanz, Ereignisse, Saltation als Körner (Fluss, Höhe, Fahnen, Schweb) und Windrippel. Seit
 * Phase 9 (dune II): Dünenwanderung im Zeitraffer, Sand erhalten, Schüttwinkel, Strandhafer wächst durch, Ablagerung
 * im Bewuchs, Sandfangzäune, Faden. Endet mit
 * „Alles in Ordnung.“
 */
public final class DuneCheck {
    private static int bad, n;

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMANY);
        huelle();
        schwelle();
        fluss();
        gelaende();
        koerner();
        rippel();
        zeitraffer();
        strandhafer();
        System.out.println(n + " Prüfungen. " + (bad == 0 ? "Alles in Ordnung." : bad + " Verstöße."));
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Stand der Hülle (Phase 1). */
    static void huelle() {
        System.out.println("Hülle");
        Dune d = Dune.sylt();
        d.grain = 0.25e-3;
        double ut = d.thresholdDry();
        check(Math.abs(ut - 0.23) < 0.01, String.format("Schwelle trocken bei 0,25 mm: u*t = %.3f m/s (Bagnold ≈ 0,22–0,25)", ut));
        Dune grob = Dune.sylt();
        grob.grain = 0.5e-3;
        check(Math.abs(grob.thresholdDry() / ut - Math.sqrt(2)) < 1e-12, "Schwelle wächst mit √d");
        check(Dune.moistureFactor(0) == 1 && Dune.moistureFactor(0.0001) == 1, "trocken: kein Faktor");
        check(Math.abs(Dune.moistureFactor(0.01) - 1.8) < 1e-12 && Dune.moistureFactor(0.04) > Dune.moistureFactor(0.01),
                "Belly: 1 % ⇒ 1,8-fach, nasser ⇒ mehr");
        check(Math.abs(d.coverFactor(0) - 1) < 1e-12 && d.coverFactor(0.05) > 1.5,
                String.format("Raupach: kahl 1, λ = 0,05 ⇒ %.2f-fach", d.coverFactor(0.05)));
        check(d.flux(ut * 0.99, ut) == 0 && d.flux(ut, ut) == 0 && d.flux(ut * 1.01, ut) > 0, "unter der Schwelle fliegt nichts");
        double us = 2.0, bag = 1.8 * d.rhoAir / Dune.G * us * us * us;
        check(Math.abs(d.flux(us, ut) / bag - (1 - ut / us)) < 1e-12, "weit über der Schwelle geht der Fluss in Bagnolds u*³ über");
        double u10 = d.windFromUstar(ut);
        check(Math.abs(d.ustarFromWind(u10) - ut) < 1e-12 && u10 > 4 && u10 < 7,
                String.format("logarithmisches Profil: Schwelle bei %.1f m/s in 10 m", u10));
        Dune nass = Dune.beaufort(7);
        nass.moisture = (x, z) -> x < 50 ? 0.20 : 0.0005;
        check(nass.fluxAt(10, 0) == 0 && nass.fluxAt(80, 0) > 0, "Bft 7: nasser Strand fliegt nicht, trockener schon");
        Dune halm = Dune.beaufort(7);
        halm.cover = (x, z) -> x > 100 ? 0.08 : 0;
        check(halm.fluxAt(150, 0) < halm.fluxAt(50, 0), "im Strandhafer fliegt weniger");
        check(Dune.calm().windSpeed < Dune.breeze().windSpeed && Dune.breeze().windSpeed < Dune.gale().windSpeed
                && Dune.gale().windSpeed < Dune.storm().windSpeed, "Presets steigen von calm bis storm");
        int[] calls = new int[1];
        nass.onSaltation = (x, z, q) -> calls[0]++;
        int found = nass.scan(0, 0, 100, 20);
        check(found == calls[0] && found > 0 && found < 400, "scan meldet jeden Ort mit Flug über die eigene Schnittstelle");
    }

    /** Schwelle trocken und feucht gegen unabhängige Ansätze. */
    static void schwelle() {
        System.out.println("Schwelle trocken und feucht");
        Dune d = Dune.sylt();
        // Shao und Lu (2000): u*t = √(A_N·(σ·g·d + γ/(ρ·d))), A_N = 0,0123, γ = 3·10⁻⁴ kg/s²
        for (double dd : new double[]{0.25e-3, 0.3e-3, 0.5e-3}) {
            d.grain = dd;
            double shao = Math.sqrt(0.0123 * (d.rhoSand / d.rhoAir * Dune.G * dd + 3e-4 / (d.rhoAir * dd))), ut = d.thresholdDry();
            check(Math.abs(ut / shao - 1) < 0.2, String.format("trocken, %.1f mm: Bagnold %.3f m/s, Shao und Lu %.3f m/s (%+.0f %%)", dd * 1000, ut, shao, (ut / shao - 1) * 100));
        }
        d.grain = 0.3e-3;
        // Fécan u. a. (1999), Sand ohne Ton: f = √(1 + 1,21·W^0,68) mit W in %
        for (double w : new double[]{0.01, 0.02, 0.03, 0.05}) {
            double fecan = Math.sqrt(1 + 1.21 * Math.pow(w * 100, 0.68)), belly = Dune.moistureFactor(w);
            check(Math.abs(belly / fecan - 1) < 0.25, String.format("feucht, %.0f %%: Belly %.2f-fach, Fécan %.2f-fach (%+.0f %%)", w * 100, belly, fecan, (belly / fecan - 1) * 100));
        }
        Strand st = new Strand();
        Dune b = st.wire(Dune.beaufort(6));
        FluxField f = st.field(b, 0.5);
        double qNass = f.fluxAt(10, 0), utNass = f.thresholdAt(10, 0), utTrocken = f.thresholdAt(65, 0), qTrocken = f.fluxAt(65, 0);
        check(qNass == 0 && qTrocken > 0 && Math.abs(utTrocken / b.thresholdDry() - 1) < 0.06 && utNass > 2.4 * b.thresholdDry(),
                String.format("Bft 6 am Strand: Auflauf (20 %% Wasser) u*t %.2f m/s, fliegt nicht; oben trocken u*t %.2f m/s, q %.4f kg/(m·s)", utNass, utTrocken, qTrocken));
        st.rain = 0.03;
        FluxField r = st.field(b, 0.5);
        check(r.fluxAt(60, 0) == 0, String.format("nach Regen (3 %%) fliegt bei Bft 6 nichts mehr (u* %.2f < u*t %.2f m/s)", r.ustarAt(60, 0), r.thresholdAt(60, 0)));
        Dune g = Dune.storm();
        g.moisture = (x, z) -> 0.07;
        check(g.thresholdAt(0, 0) == Double.POSITIVE_INFINITY && g.fluxAt(0, 0) == 0,
                String.format("über %.0f %% Wasser ist der Sand gebunden: auch bei Bft 10 fliegt nichts", g.boundMoisture * 100));
        check(Math.abs(d.slopeFactor(0) - 1) < 1e-12 && d.slopeFactor(Math.tan(Math.toRadians(10))) > 1.1
                        && d.slopeFactor(-Math.tan(Math.toRadians(d.reposeDeg))) < 1e-6,
                String.format("Hang nach Dyer: eben 1, 10° bergauf %.2f-fach, bergab am Schüttwinkel 0", d.slopeFactor(Math.tan(Math.toRadians(10)))));
    }

    /** Fluss gegen Bagnold bei bekanntem u*, Sättigungslänge, Sandbilanz. */
    static void fluss() {
        System.out.println("Fluss gegen Bagnold");
        for (double us : new double[]{0.30, 0.40, 0.60}) {
            Dune d = Dune.sylt();
            d.windSpeed = d.windFromUstar(us);
            FluxField f = new FluxField(d, 0, 0, 60, 2, 0.25).compute();
            double ut = d.thresholdDry(), bag = d.bagnoldC * Math.sqrt(d.grain / 0.25e-3) * d.rhoAir / Dune.G * us * us * (us - ut);
            double end = f.fluxAt(28, 0);
            check(Math.abs(end / bag - 1) < 0.005,
                    String.format("u* %.2f m/s über 60 m trockenem Sand: q %.4f kg/(m·s), Bagnold mit Schwelle %.4f (%+.2f %%)", us, end, bag, (end / bag - 1) * 100));
        }
        Dune d = Dune.sylt();
        d.windSpeed = d.windFromUstar(0.5);
        double us = 0.5, ut = d.thresholdDry(), q = d.flux(us, ut);
        double kaw = 2.78 * d.rhoAir / Dune.G * (us - ut) * (us + ut) * (us + ut);
        double lettau = 4.2 * Math.sqrt(d.grain / 0.25e-3) * d.rhoAir / Dune.G * us * us * (us - ut);
        check(q > 0.3 * kaw && q < 1.2 * kaw && q < lettau,
                String.format("u* 0,5 m/s: q %.4f kg/(m·s) gegen Kawamura %.4f und Lettau %.4f (Bagnold liegt darunter)", q, kaw, lettau));
        // Sättigungslänge: vom nassen in den trockenen Sand
        d.moisture = (x, z) -> x < 0 ? 0.2 : 0;
        FluxField f = new FluxField(d, 10, 0, 40, 2, 0.05).compute();
        double ls = d.saturationLength(), qs = f.saturatedAt(15, 0), atL = f.fluxAt(ls, 0) / qs, at5 = f.fluxAt(5 * ls, 0) / qs;
        check(Math.abs(atL - (1 - Math.exp(-1))) < 0.03 && at5 > 0.99,
                String.format("Sättigungslänge ℓ = 2,2·(ρs/ρ)·d = %.2f m: nach ℓ %.0f %%, nach 5ℓ %.1f %% des Sättigungsflusses", ls, atL * 100, at5 * 100));
        check(f.massBalance() < 1e-4, String.format("Sandbilanz je Zeile: Σ Ablagerung = Fluss hinein − hinaus (Abweichung %.1e)", f.massBalance()));
        check(f.depositAt(ls, 0) < 0, String.format("wo der Fluss wächst, nimmt der Wind Sand auf (%.4f kg/(m²·s))", f.depositAt(ls, 0)));
    }

    /** Über dem Hügel: Beschleunigung, Windschatten, Ablagerung, Ereignisse. */
    static void gelaende() {
        System.out.println("Kamm und Windschatten");
        Dune d = Dune.sylt();
        d.windSpeed = d.windFromUstar(0.45);
        // Gaußhügel, H = 4 m, Halbwertsbreite 40 m ⇒ H/L ≈ 0,1 (L halbe Breite in halber Höhe)
        double hh = 4, sig = 40 / 2.3548 * 2;
        d.ground = (x, z) -> hh * Math.exp(-x * x / (2 * sig * sig));
        FluxField f = new FluxField(d, 0, 0, 400, 2, 0.5).compute();
        double u0 = f.ustarAt(-190, 0), crest = f.ustarAt(0, 0), best = 0, at = 0, foot = 1e9;
        for (double x = -100; x <= 100; x += 0.5) {
            double u = f.ustarAt(x, 0);
            if (u > best) { best = u; at = x; }
            if (x < 0) foot = Math.min(foot, u);
        }
        double jh = 1 + 1.6 * hh / 40;   // Jackson und Hunt (1975) für den Wind über einem flachen Hügel
        check(best / u0 > 1.05 && best / u0 < 1.5 && at < 0 && at > -20 && foot < u0,
                String.format("Hügel H/L = 0,1: u* am Kamm %.2f-fach (Jackson und Hunt ≈ %.2f), am höchsten %.1f m luvwärts, am Fuß gebremst (%.2f-fach)",
                        best / u0, jh, -at, foot / u0));
        // Steile Düne: Lee 30° im Windschatten
        Strand st = new Strand();
        Dune b = st.wire(Dune.beaufort(7));
        int[] dep = new int[1], sco = new int[1];
        b.onDeposit = (x, z, r) -> dep[0]++;
        b.onScour = (x, z, r) -> sco[0]++;
        FluxField g = st.field(b, 0.5);
        double c = st.crest(0);
        double laenge = 0;
        for (double x = c + 3; x < c + 80; x += 0.5) if (g.ustarAt(x, 0) == 0) laenge = x - (c + 1.5);
        double erwartet = (st.duneH + 0.8 + 0.15) / Math.tan(Math.toRadians(b.shadowDeg));
        check(g.shadowAt(c + 8, 0) && g.fluxAt(c + 30, 0) < 0.2 * g.fluxAt(c, 0) && Math.abs(laenge / erwartet - 1) < 0.25,
                String.format("Lee 30°: Windschatten %.0f m lang (Hülle unter %.0f° ⇒ %.0f m), dahinter fliegt kaum etwas", laenge, b.shadowDeg, erwartet));
        double ab = 0;
        for (double x = c + 1; x < c + 20; x += 0.5) ab = Math.max(ab, g.depositRateAt(x, 0));
        check(ab > 0 && g.depositAt(c - 10, 0) < 0, String.format("Luv trägt ab, im Lee fällt der Sand aus (bis %.0f mm/h)", ab * 3.6e6));
        check(dep[0] > 0 && sco[0] > 0, String.format("Ereignisse: onDeposit %d Zellen (Lee, Strandhafer), onScour %d Zellen (Luv)", dep[0], sco[0]));
        check(g.massBalance() < 1e-4, String.format("Sandbilanz über der Düne (Abweichung %.1e)", g.massBalance()));
    }

    /** Saltation als Körner. */
    static void koerner() {
        System.out.println("Saltation, Fahnen, Schweb");
        Dune d = Dune.sylt();
        d.windSpeed = d.windFromUstar(0.45);
        d.gustiness = 0;
        FluxField f = new FluxField(d, 0, 0, 120, 60, 0.5).compute();
        Grains g = new Grains(d);
        g.radius = 25;
        for (int i = 0; i < 240; i++) g.step(1 / 60.0, f, 0, 0);
        g.gateS = 0;
        g.gateHalf = 10;
        for (int i = 0; i < 600; i++) g.step(1 / 60.0, f, 0, 0);
        double gq = g.gateFlux(20), q = f.fluxAt(0, 0);
        check(Math.abs(gq / q - 1) < 0.2, String.format("Körner tragen den Fluss: durch eine Ebene %.4f kg/(m·s), Feld %.4f (%+.0f %%), %d Körner zu %.2g g",
                gq, q, (gq / q - 1) * 100, g.count, g.weight * 1000));
        double hm = g.gateHeight(), hop = g.hop(0.45)[0];
        check(hm > 0.01 && hm < 0.1 && hop > 0.05 && hop < 1,
                String.format("mittlere Höhe des Flusses %.0f mm (gemessen 20–60 mm), Sprungweite %.0f cm", hm * 1000, hop * 100));
        Grains g2 = new Grains(d), g3 = new Grains(d);
        g2.radius = g3.radius = 25;
        for (int i = 0; i < 120; i++) { g2.step(1 / 60.0, f, 0, 0); g3.step(1 / 60.0, f, 0, 0); }
        boolean gleich = g2.count == g3.count && g2.count > 0;
        for (int k = 0; gleich && k < g2.count; k++) gleich = g2.x[k] == g3.x[k] && g2.y[k] == g3.y[k];
        check(gleich, String.format("gleicher Samen, gleiche Körner (%d nach 2 s)", g2.count));
        // Fahnen: nahe der Schwelle fliegt Sand nur in Böen, weit darüber fast immer
        Dune s = Dune.sylt();
        double ut = s.thresholdDry();
        double[] frac = new double[2];
        double[] ratio = {0.95, 2.0};
        for (int k = 0; k < 2; k++) {
            s.windSpeed = s.windFromUstar(ut * ratio[k]);
            int on = 0, all = 0;
            for (double t = 0; t < 120; t += 0.25) {
                all++;
                if (s.fluxGusty(3, 1, t) > 0) on++;
            }
            frac[k] = on / (double) all;
        }
        check(frac[0] > 0.03 && frac[0] < 0.6 && frac[1] > 0.95,
                String.format("Sandfahnen: bei 0,95·u*t fliegt %.0f %% der Zeit Sand (nur in Böen), bei 2·u*t %.0f %%", frac[0] * 100, frac[1] * 100));
        Strand st = new Strand();
        Dune b = st.wire(Dune.beaufort(8));
        FluxField fd = st.field(b, 0.5);
        Grains gd = new Grains(b);
        gd.radius = 20;
        double zc = 28;   // im Windriss ohne Strandhafer
        for (int i = 0; i < 480; i++) gd.step(1 / 60.0, fd, st.crest(zc), zc);
        double top = gd.finesTop(), salt = 0;
        int sn = 0;
        double lee = 0;
        int ln = 0;
        for (int k = 0; k < gd.count; k++) {
            if (gd.kind[k] != 0) continue;
            double hy = gd.y[k] - b.ground.height(gd.x[k], gd.z[k]);
            if (gd.x[k] < st.crest(gd.z[k]) - 2) { salt += hy; sn++; }
            else if (gd.x[k] > st.crest(gd.z[k]) + 4) { lee = Math.max(lee, hy); ln++; }
        }
        check(top > 0.5 && sn > 0 && salt / sn < 0.15 && ln > 0 && lee > 1,
                String.format("Kammfahne bei Bft 8: im Luv springen die Körner im Mittel %.0f cm hoch, über dem Lee fliegen %d bis %.1f m über dem Boden, Schweb steigt bis %.1f m",
                        salt / sn * 100, ln, lee, top));
        // Staub (seit Sylt Phase 12, F15 A): ein Stoß Feinkorn steigt auf und treibt mit dem Wind leewärts
        Grains gs = new Grains(b);
        gs.radius = 60;
        int n0 = gs.burst(st.crest(zc) - 10, b.ground.height(st.crest(zc) - 10, zc) + 0.5, zc, 200, 2.5);
        double x0 = 0;
        for (int k = 0; k < gs.count; k++) x0 += gs.x[k];
        x0 /= Math.max(1, gs.count);
        double hoch = 0;
        for (int i = 0; i < 120; i++) {
            gs.step(1 / 60.0, fd, st.crest(zc), zc);
            for (int k = 0; k < gs.count; k++) if (gs.kind[k] == 1) hoch = Math.max(hoch, gs.y[k] - b.ground.height(gs.x[k], gs.z[k]));
        }
        double x1 = 0;
        int nf = 0;
        for (int k = 0; k < gs.count; k++) if (gs.kind[k] == 1) { x1 += gs.x[k]; nf++; }
        x1 /= Math.max(1, nf);
        check(n0 == 200 && hoch > 1 && nf > 0 && x1 > x0 + 2,
                String.format("Staub (Grains.burst): %d Körner, steigen bis %.1f m, treiben in 2 s %.1f m mit dem Wind", n0, hoch, x1 - x0));
    }

    /** Windrippel. */
    static void rippel() {
        System.out.println("Windrippel");
        Strand st = new Strand();
        Dune b = st.wire(Dune.beaufort(6));
        FluxField f = st.field(b, 0.5);
        Ripples r = new Ripples(f).settle();
        double[] trocken = r.at(60, 0), nass = r.at(10, 0);
        check(trocken[0] > 0.06 && trocken[0] < 0.25 && Math.abs(trocken[0] / trocken[1] - b.rippleIndex) < 2 && nass[1] == 0,
                String.format("Bft 6: λ %.0f cm, H %.1f mm (Index %.0f) auf trockenem Sand, keine im Auflauf", trocken[0] * 100, trocken[1] * 1000, trocken[0] / trocken[1]));
        Dune s = st.wire(Dune.beaufort(8));
        Ripples r8 = new Ripples(st.field(s, 0.5)).settle();
        double[] stark = r8.at(60, 0);
        double d0 = r.travelled;
        r.step(60);
        double weg = r.travelled - d0;
        check(stark[0] > trocken[0] && stark[2] > trocken[2] && weg > 0,
                String.format("mehr Wind: λ %.0f statt %.0f cm, wandern %.1f statt %.1f cm/min (am schnellsten in 60 s %.1f cm)",
                        stark[0] * 100, trocken[0] * 100, stark[2] * 6000, trocken[2] * 6000, weg * 100));
        double[] lee = r8.at(st.crest(0) + 10, 0);
        double lz = r8.drawLambda();
        check(lee[2] == 0 && lz > 0.06 && lz < 0.25, String.format("im Windschatten wandern keine Rippel; gezeichnet mit λ %.0f cm für das Feld", lz * 100));
    }

    /** Plattenmodell im Zeitraffer: Wandern, Bilanz, Schüttwinkel, Zäune, Faden. */
    static void zeitraffer() {
        System.out.println("Zeitraffer (Werner)");
        Dune d = Dune.sylt();
        d.windSpeed = 14;
        // eine Düne auf festem Grund, im Ring
        SandBed b = new SandBed(d, 0, 0, 240, 30, 1.0, 0.05, (x, z) -> 0, (x, z) -> 7 * Math.exp(-x * x / (2 * 14 * 14)));
        Werner w = new Werner(b);
        w.periodic = true;
        w.relax();
        long s0 = b.total();
        w.advanceYears(1);
        double c0 = centre(b), h0 = top(b);
        w.advanceYears(4);
        double c1 = centre(b), h1 = top(b), v = (c1 - c0) / 4;
        double expect = w.qRef() * w.duty / d.bulkDensity * 3.15576e7 / (0.5 * (h0 + h1));
        check(v > 0 && Math.abs(v / expect - 1) < 0.5,
                String.format("Düne wandert %.1f m/Jahr (q·Anteil/(ρ_b·H) = %.1f m/Jahr bei H %.1f m), Wind 14 m/s %.0f %% der Zeit", v, expect, 0.5 * (h0 + h1), w.duty * 100));
        check(b.balance(s0) == 0 && b.inflow == 0 && b.outflow == 0,
                String.format("Sand erhalten: %,d Platten vorher und nachher, %,d bewegt", s0, w.moves));
        double sl = b.maxSlopeDeg(), lee = leeSlope(b);
        check(sl <= d.reposeDeg + 0.5 && lee > 28,
                String.format("Schüttwinkel: steilster Hang %.1f° (≤ %.0f°), Rutschfläche im Lee %.0f°", sl, d.reposeDeg, lee));
        check(h1 > 0.6 * 7 && h1 < 1.6 * 7, String.format("die Düne bleibt beim Wandern %.1f m hoch (anfangs 7 m)", h1));
        // gleicher Samen, gleicher Sand
        SandBed a1 = new SandBed(d, 0, 0, 120, 10, 1.0, 0.05, (x, z) -> 0, (x, z) -> 4 * Math.exp(-x * x / 200)), a2 = new SandBed(d, 0, 0, 120, 10, 1.0, 0.05, (x, z) -> 0, (x, z) -> 4 * Math.exp(-x * x / 200));
        Werner w1 = new Werner(a1), w2 = new Werner(a2);
        w1.periodic = w2.periodic = true;
        w1.advanceYears(0.5);
        w2.advanceYears(0.5);
        check(java.util.Arrays.equals(a1.sand, a2.sand), "gleicher Samen, gleiche Dünen");
        // Sandfangzaun auf ebenem Sand
        SandBed z = new SandBed(d, 0, 0, 120, 10, 1.0, 0.05, (x, y) -> 0, (x, y) -> 1.0);
        z.fence(new SandBed.Fence(0, -6, 0, 6, 1.0, 0.5));
        Werner wz = new Werner(z);
        wz.tide = false;
        long z0 = z.total();
        double before = lee(z), hf0 = z.fenceHeight(z.nearest(0, 0));
        wz.advanceYears(1);
        double after = lee(z), hf1 = z.fenceHeight(z.nearest(0, 0));
        check(after - before > 0.3 && hf1 < hf0 && z.balance(z0) == 0,
                String.format("Sandfangzaun 1 m, halb durchlässig: in einem Jahr %.2f m Sand bis 10 m dahinter, Zaun ragt noch %.2f m heraus", after - before, hf1));
        // Faden
        SandBed t = new SandBed(d, 0, 0, 80, 10, 1.0, 0.05, (x, y) -> 0, (x, y) -> 3 * Math.exp(-x * x / 100));
        Werner wt = new Werner(t);
        wt.periodic = true;
        TimeLapse lapse = new TimeLapse(wt);
        lapse.yearsPerSecond = 2;
        long t0 = t.total();
        lapse.start();
        try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        lapse.stop();
        TimeLapse.Snapshot sn = lapse.snapshot();
        check(!lapse.running() && sn.years() > 0.1 && sn.sand() == t0 && Math.abs(lapse.ground().height(3.3, 1.2) - t.height(3.3, 1.2)) < 0.3,
                String.format("Zeitraffer im eigenen Faden: %.1f Jahre in 0,6 s, Kopie für die Darstellung passt (%s)", sn.years(), lapse.describe()));
    }

    /** Strandhafer: wächst durch die Übersandung, stirbt unter zu viel Sand, fängt Sand. */
    static void strandhafer() {
        System.out.println("Strandhafer");
        Dune d = Dune.sylt();
        int[] events = new int[1];
        d.onDeposit = (x, z, r) -> events[0]++;
        double[] ok = rate(d, 0.4, 5), viel = rate(d, 2.0, 3);
        check(ok[0] > 0.5 && ok[1] > 0.4,
                String.format("0,4 m Sand im Jahr: nach 5 Jahren 2 m übersandet, Strandhafer %.0f %% dicht und ragt %.0f %% heraus (wächst durch)", ok[0] * 100, ok[1] * 100));
        check(viel[0] < 0.1 && events[0] > 0,
                String.format("2 m Sand im Jahr: nach 3 Jahren nur noch %.0f %% (begraben), onDeposit %d-mal", viel[0] * 100, events[0]));
        double[] ohne = rate(d, 0, 8);
        check(ohne[0] < 0.8 && ohne[0] > 0.3, String.format("ohne Sand vergreist er langsam: nach 8 Jahren %.0f %%", ohne[0] * 100));
        // Vordüne der Bühne: der Bewuchs fängt den Sand
        Strand st = new Strand();
        Dune b = st.wire(Dune.sylt());
        b.windSpeed = 14;
        SandBed bed = st.bed(b, 1.0);
        b.ground = bed;
        b.cover = bed;
        Werner w = new Werner(bed);
        w.relax();
        int[] start = bed.sand.clone();
        float[] vegStart = bed.veg.clone();
        long s0 = bed.total();
        w.advanceYears(4);
        double gV = 0, gB = 0;
        int nV = 0, nB = 0;
        for (int k = 0; k < start.length; k++) {
            double[] p = bed.world(k % bed.nx, k / bed.nx);
            if (p[0] < st.crest(p[1]) - 12 || p[0] > st.crest(p[1]) + 2) continue;
            double dz = (bed.sand[k] - start[k]) * bed.slab;
            if (vegStart[k] > 0.5) { gV += dz; nV++; } else if (vegStart[k] == 0) { gB += dz; nB++; }
        }
        gV /= Math.max(1, nV);
        gB /= Math.max(1, nB);
        check(gV > 0.3 && gV > gB + 0.3 && bed.balance(s0) == 0,
                String.format("Vordüne in 4 Jahren: im Strandhafer %+.2f m, im Windriss ohne Halme %+.2f m; Sand erhalten (%,d herein, %,d hinaus)", gV, gB, bed.inflow, bed.outflow));
        double alive = 0, through = 0;
        int nAlive = 0;
        for (int k = 0; k < start.length; k++)
            if (vegStart[k] > 0.5 && bed.veg[k] > 0.3 && (bed.sand[k] - start[k]) * bed.slab > 1.0) { nAlive++; through += bed.exposed(k); }
        check(nAlive > 50 && through / nAlive > 0.3,
                String.format("%d Zellen mit über 1 m neuem Sand, in denen der Strandhafer lebt und im Mittel %.0f %% herausragt", nAlive, 100 * through / Math.max(1, nAlive)));
        check(bed.maxSlopeDeg() <= b.reposeDeg + 0.5, String.format("kein Hang steiler als %.0f° (%.1f°)", b.reposeDeg, bed.maxSlopeDeg()));
        FluxField f = new FluxField(b, 80, 0, 170, 120, 0.5).compute();
        check(f.fluxAt(st.crest(-28) - 4, -28) < f.fluxAt(st.crest(28) - 4, 28),
                "auf dem Sandbett: im Strandhafer fliegt weniger als im Windriss (Bewuchs λ aus dem Sandbett)");
    }

    /** Eine Zelle mit Strandhafer unter gleichmäßiger Übersandung: {Bedeckung, frei} nach so vielen Jahren. */
    static double[] rate(Dune d, double perYear, int years) {
        SandBed b = new SandBed(d, 0, 0, 3, 3, 1.0, 0.05, (x, z) -> 0, (x, z) -> 1);
        java.util.Arrays.fill(b.veg, 1f);
        double add = 0;
        for (int s = 0; s < years * 20; s++) {
            add += perYear / 20;
            int slabs = (int) Math.floor(add / b.slab);
            add -= slabs * b.slab;
            for (int k = 0; k < b.sand.length; k++) b.sand[k] += slabs;
            b.grow(1 / 20.0);
        }
        int k = b.nearest(0, 0);
        return new double[]{b.veg[k], b.exposed(k)};
    }

    static double centre(SandBed b) {
        // Schwerpunkt im Ring: über den Winkel (der Sand läuft über den Rand)
        double cs = 0, sn = 0;
        for (int k = 0; k < b.sand.length; k++) {
            double a = 2 * Math.PI * (k % b.nx) / b.nx;
            cs += b.sand[k] * Math.cos(a);
            sn += b.sand[k] * Math.sin(a);
        }
        return unwrap(Math.atan2(sn, cs) / (2 * Math.PI) * b.nx * b.cell, b);
    }

    private static final java.util.Map<SandBed, double[]> LAST = new java.util.HashMap<>();

    static double unwrap(double x, SandBed b) {
        double[] l = LAST.computeIfAbsent(b, k -> new double[]{x, 0});
        double len = b.nx * b.cell, d = x - l[0];
        while (d > len / 2) { d -= len; l[1] -= len; }
        while (d < -len / 2) { d += len; l[1] += len; }
        l[0] = x;
        return x + l[1];
    }

    static double top(SandBed b) {
        double m = 0;
        for (int k = 0; k < b.sand.length; k++) m = Math.max(m, b.surface(k));
        return m;
    }

    /** Steilster Abschnitt in Windrichtung abwärts (Grad), Mittel über die Zeilen. */
    static double leeSlope(SandBed b) {
        double sum = 0;
        for (int j = 0; j < b.ny; j++) {
            double m = 0;
            for (int i = 0; i < b.nx; i++) {
                int k = j * b.nx + i, q = j * b.nx + (i + 1) % b.nx;
                m = Math.max(m, b.surface(k) - b.surface(q));
            }
            sum += Math.toDegrees(Math.atan(m / b.cell));
        }
        return sum / b.ny;
    }

    /** Sand bis 10 m hinter dem Zaun bei x = 0 (m, Mittel). */
    static double lee(SandBed b) {
        double s = 0;
        int n = 0;
        for (double x = 1; x <= 10; x += 1)
            for (double z = -4; z <= 4; z += 1) { s += b.height(x, z); n++; }
        return s / n;
    }

    static void check(boolean c, String what) {
        n++;
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private DuneCheck() { }
}
