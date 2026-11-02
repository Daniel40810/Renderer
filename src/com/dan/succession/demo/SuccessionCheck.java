package com.dan.succession.demo;

import com.dan.succession.Art;
import com.dan.succession.Boden;
import com.dan.succession.Succession;
import com.dan.succession.Wellenwurf;

import java.util.List;
import java.util.Locale;

/**
 * Prüfung von {@link Succession} und {@link Wellenwurf} ohne Fenster (seit LituyaBay1958 Phase 14, Werkbuch 5.6, 9.7):
 * Wuchskurve und ihre Umkehrung, kritische Geschwindigkeit 9.7, Folgen mit wachsender Tiefe und am steilen Hang, dann
 * ein gerechneter Hang über einem Fjord (2,4 × 1,5 km, 15° im Westen, 35° im Osten, Spurlinie 40 bis 300 m) mit 68 Jahren:
 * scharfe Kante, Fels nur in tiefer schneller Strömung, die Roterle zuerst, die Nadelbäume im Unterstand und später,
 * Kronenhöhe im Band gegen außerhalb nach 54 Jahren, Fels wächst langsamer zu als Mineralboden, vom Rand her früher als
 * innen, Kastenfilter erhält die Menge, zwei Läufe gleich, Ausgabe je Platz. Endet mit „Alles in Ordnung.“
 */
public final class SuccessionCheck {
    static int bad, all;

    /** Der Hang: Wasser für z &gt; 0 (50 m tief), Land bis z = −1 500 mit 15° (x &lt; 0) oder 35°. */
    static final Succession.Gelaende HANG = (x, z) -> z > 0 ? -50 : -z * Math.tan(Math.toRadians(x < 0 ? 15 : 35));

    /** Spurlinie über x: 40 m bis 300 m [G]. */
    static double trim(double x) { return 40 + 260 * Math.max(0, Math.min(1, (x + 1000) / 2000)); }

    /** Höchster Stand: über dem Hang mit 35 % der Höhe bis zur Spurlinie (der Auflauf wird dünner), darüber trocken. */
    static final Succession.Wasser WASSER = (x, z) -> {
        double t = trim(x), g = HANG.height(x, z);
        return g < t ? g + 0.35 * (t - g) + 0.5 : Double.NaN;
    };

    /** Altbestand: Sitka 55 %, Hemlock 40 %, Roterle am Ufer 30 %, sonst 8 % [G]. */
    static final Succession.Bestand BESTAND = (x, z, s) -> s == 0 ? 0.55 : s == 1 ? 0.4 : (HANG.height(x, z) < 20 ? 0.3 : 0.08);

    static Succession hang(long seed) {
        Succession su = Succession.covering(-1200, -1500, 1200, 0, 6, List.of(Art.sitkaFichte(), Art.hemlock(), Art.roterle()), seed);
        su.stoeren(HANG, WASSER, BESTAND);
        su.rechnen(68);
        return su;
    }

    public static void main(String[] a) {
        long t0 = System.nanoTime();
        System.out.println("Wuchs und Kraft");
        {
            Art s = Art.sitkaFichte(), e = Art.roterle();
            double worst = 0;
            for (double h = 0.5; h < s.hMax * 0.98; h += 0.5) worst = Math.max(worst, Math.abs(s.hoehe(s.alterBei(h)) - h));
            check(worst < 1e-6, fmt("Chapman-Richards und Umkehrung: größte Abweichung %.1e m", worst));
            check(e.hoehe(10) > 9 && e.hoehe(10) < 13 && s.hoehe(54) > 28 && s.hoehe(54) < 40 && e.hoehe(10) > s.hoehe(10) * 1.8,
                    fmt("Roterle nach 10 Jahren %.1f m, Sitka-Fichte nach 10 %.1f m und nach 54 Jahren %.1f m (frei, [G])", e.hoehe(10), s.hoehe(10), s.hoehe(54)));
            double uk = Wellenwurf.uKrit(0.8, 100, 20);
            check(Math.abs(uk - 1.4) < 0.1, fmt("kritische Geschwindigkeit (d = 0,8 m, Krone 100 m², Hebel 20 m): %.2f m/s (Werkbuch 9.7: ≈ 1,4)", uk));
        }
        {
            Art s = Art.sitkaFichte();
            double[] t = {0.5, 3, 10, 30};
            Wellenwurf.Folge[] f = new Wellenwurf.Folge[t.length];
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < t.length; k++) {
                f[k] = Wellenwurf.wurf(t[k], Wellenwurf.geschwindigkeit(t[k], 1.5), 40, s, 10).folge();
                sb.append(k > 0 ? ", " : "").append(fmt("%.1f m %s", t[k], f[k]));
            }
            check(f[0] == Wellenwurf.Folge.STEHT && f[1] == Wellenwurf.Folge.ENTRINDET && f[2] == Wellenwurf.Folge.GEBROCHEN && f[3] == Wellenwurf.Folge.FORT,
                    "Sitka-Fichte 40 m am flachen Hang, Fr 1,5: " + sb);
            Wellenwurf.Schaden st = Wellenwurf.wurf(10, Wellenwurf.geschwindigkeit(10, 1.5), 40, s, 40);
            check(st.folge() == Wellenwurf.Folge.UMGELEGT && st.boden() == Boden.MINERAL, "am steilen Hang (40°, dünner Boden) entwurzelt statt gebrochen: " + st);
            double kr = Wellenwurf.moment(6.5, 5, 40, s) / Wellenwurf.moment(5.5, 5, 40, s);
            check(kr > 1.4, fmt("erreicht das Wasser die Krone (ab 6 m), springt das Moment: 6,5 m gegen 5,5 m Tiefe ×%.2f", kr));
        }
        System.out.println("Kastenfilter");
        {
            int gw = 40, gh = 30;
            float[] v = new float[gw * gh];
            v[15 * gw + 20] = 100;
            Succession.blurTest(v, gw, gh, 3);
            double sum = 0, mx = 0;
            for (float x : v) { sum += x; mx = Math.max(mx, x); }
            float[] c = new float[gw * gh];
            java.util.Arrays.fill(c, 2f);
            Succession.blurTest(c, gw, gh, 4);
            double dev = 0;
            for (float x : c) dev = Math.max(dev, Math.abs(x - 2));
            check(Math.abs(sum - 100) < 1e-3 && mx < 10 && dev < 1e-5, fmt("Punkt verteilt (Summe %.4f, Spitze %.2f), gleichmäßiges Feld bleibt (%.1e)", sum, mx, dev));
        }
        System.out.println("Ein Hang über dem Fjord, 68 Jahre");
        long t1 = System.nanoTime();
        Succession su = hang(1958);
        double sec = (System.nanoTime() - t1) / 1e9;
        Succession.Statistik s0 = su.statistik(0), s10 = su.statistik(10), s30 = su.statistik(30), s54 = su.statistik(54), s68 = su.statistik(68);
        {
            int[] fo = s0.folgen();
            check(su.band() > 15_000 && fo[Wellenwurf.Folge.FORT.ordinal()] + fo[Wellenwurf.Folge.GEBROCHEN.ordinal()] + fo[Wellenwurf.Folge.UMGELEGT.ordinal()] > 0.85 * su.band(),
                    fmt("Band %,d Plätze in %.2f s (68 Jahre); fort %,d, umgelegt %,d, gebrochen %,d, entrindet %,d, steht %,d", su.band(), sec,
                            fo[5], fo[4], fo[3], fo[2], fo[1]));
            // scharfe Kante: lebende Bäume nur dicht unter der Spurlinie
            int lebend = 0, weit = 0;
            double tiefMax = 0;
            for (int k = 0; k < su.band(); k++) {
                if (su.folge(k) != Wellenwurf.Folge.STEHT && su.folge(k) != Wellenwurf.Folge.ENTRINDET) continue;
                double[] l = su.lage(k);
                lebend++;
                tiefMax = Math.max(tiefMax, l[2]);
                double x = (l[0] + 0.5) * su.spacing, z = (l[1] + 0.5) * su.spacing;
                if (trim(x) - HANG.height(x, z) > 30) weit++;
            }
            check(tiefMax < 8 && weit == 0 && lebend < 0.15 * su.band(), fmt("scharfe Kante: %d Bäume überleben (stehend oder entrindet), alle in höchstens %.1f m Wasser (unter dem Kronenansatz) und keiner mehr als 30 m unter der Spurlinie", lebend, tiefMax));
            int fels = 0, felsFlach = 0;
            for (int k = 0; k < su.band(); k++) {
                if (su.bodenArt(k, 0) != Boden.FELS) continue;
                fels++;
                if (su.lage(k)[2] < Wellenwurf.abraumTiefe) felsFlach++;
            }
            check(fels > 0 && felsFlach == 0, fmt("bis zum Fels abgeräumt nur in tiefer schneller Strömung: %,d Plätze, keiner unter %.0f m Wasser", fels, Wellenwurf.abraumTiefe));
        }
        {
            check(s10.anteil()[2] > s10.anteil()[0] * 0.5 && s30.anteil()[2] > 0.45,
                    fmt("die Roterle früh: nach 10 Jahren %.0f %% der Bäume (Sitka %.0f %%), nach 30 Jahren %.0f %%", 100 * s10.anteil()[2], 100 * s10.anteil()[0], 100 * s30.anteil()[2]));
            double nadel68 = s68.anteil()[0] + s68.anteil()[1], nadel40 = su.statistik(40).anteil()[0] + su.statistik(40).anteil()[1];
            check(nadel68 > nadel40, fmt("die Nadelbäume holen auf: Anteil nach 40 Jahren %.0f %%, nach 68 Jahren %.0f %%", 100 * nadel40, 100 * nadel68));
            check(s10.deckung() < s30.deckung() && s30.deckung() < s54.deckung() && s68.deckung() > 0.7,
                    fmt("Deckung über 2 m wächst: 10 Jahre %.0f %%, 30 Jahre %.0f %%, 54 Jahre %.0f %%, 68 Jahre %.0f %%", 100 * s10.deckung(), 100 * s30.deckung(), 100 * s54.deckung(), 100 * s68.deckung()));
            double v = s54.verhaeltnis();
            check(v > 0.25 && v < 0.75, fmt("Kronenhöhe im Band nach 54 Jahren %.1f m gegen %.1f m außerhalb: %.2f (das Band bleibt Jahrzehnte sichtbar; Abgleich am DSM 2012 in der App, 14a)",
                    s54.hoeheBand(), s54.hoeheAussen(), v));
        }
        {
            // Fels gegen Mineralboden, Rand gegen innen
            int nf = 0, df = 0, nm = 0, dm = 0, nr = 0, dr = 0, ni = 0, di = 0;
            for (int k = 0; k < su.band(); k++) {
                double[] l = su.lage(k);
                double x = (l[0] + 0.5) * su.spacing, z = (l[1] + 0.5) * su.spacing, g = HANG.height(x, z);
                boolean dicht = su.hoehe(k, 15) >= 2;
                Boden b = su.bodenArt(k, 0);
                if (b == Boden.FELS) { nf++; if (dicht) df++; }
                if (b == Boden.MINERAL) { nm++; if (dicht) dm++; }
                double rand = (trim(x) - g) / Math.tan(Math.toRadians(x < 0 ? 15 : 35));   // Abstand zur Spurlinie hangauf (m)
                boolean dicht8 = su.hoehe(k, 8) >= 1;
                if (rand < 60) { nr++; if (dicht8) dr++; }
                if (rand > 300) { ni++; if (dicht8) di++; }
            }
            double pf = df / (double) Math.max(1, nf), pm = dm / (double) Math.max(1, nm), pr = dr / (double) Math.max(1, nr), pi = di / (double) Math.max(1, ni);
            check(pf < pm, fmt("nach 15 Jahren auf Fels %.0f %% bewachsen, auf Mineralboden %.0f %%", 100 * pf, 100 * pm));
            check(pr > 2 * pi, fmt("nach 8 Jahren am Rand (unter 60 m zur Spurlinie) %.0f %% mit Jungwuchs über 1 m, mitten im Band (über 300 m) %.0f %%", 100 * pr, 100 * pi));
        }
        {
            Succession su2 = hang(1958), su3 = hang(7);
            check(su.pruefsumme() == su2.pruefsumme() && su.pruefsumme() != su3.pruefsumme(), "zwei Läufe gleich, ein anderer Startwert anders");
            int[] z = new int[5];
            int[] lebend = {0};
            su.zustand(68, (i, j, st, fall, art, h) -> { z[st]++; if (art >= 0 && h > 0) lebend[0]++; });
            int[] z0 = new int[5];
            su.zustand(0, (i, j, st, fall, art, h) -> z0[st]++);
            check(z[0] == lebend[0] && z[0] + z[1] + z[2] + z[3] + z[4] == su.band() && z0[4] > 0 && z0[3] > 0 && z[0] > 0.7 * su.band(),
                    fmt("Ausgabe je Platz: Jahr 0 %,d fort, %,d umgelegt, %,d stehend; Jahr 68 %,d stehend (mit Art und Höhe), %,d Reste, %,d fort",
                            z0[4], z0[3], z0[0], z[0], z[1] + z[2] + z[3], z[4]));
        }
        for (int y : new int[]{0, 10, 30, 54, 68}) System.out.println("  " + su.zeile(y));
        System.out.printf(Locale.GERMAN, "%d Prüfungen in %.1f s%n", all, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static String fmt(String f, Object... a) { return String.format(Locale.GERMAN, f, a); }

    static void check(boolean ok, String what) {
        all++;
        if (!ok) bad++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
    }
}
