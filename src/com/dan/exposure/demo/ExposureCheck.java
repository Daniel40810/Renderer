package com.dan.exposure.demo;

import com.dan.exposure.EyeAdaptation;

import java.util.Locale;

/**
 * Prüfung von {@link EyeAdaptation} ohne Grafikkarte (seit LituyaBay1958 W6 Runde 6): Mittel aus dem Histogramm,
 * Perzentile, Ziel mit Stärke und Grenzen, Zeitkonstanten in beide Richtungen, Sprung, Wirkung.
 */
public final class ExposureCheck {
    static int all, bad;

    public static void main(String[] a) {
        EyeAdaptation e = new EyeAdaptation();
        double d = (e.logMax - e.logMin) / e.bins;
        // ein Fach: das Mittel ist die Mitte des Fachs
        long[] h = new long[e.bins];
        int i0 = 30;
        h[i0] = 1000;
        double m = e.meanFromHistogram(h), soll = Math.pow(2, e.logMin + (i0 + 0.5) * d);
        check(Math.abs(m / soll - 1) < 1e-9, String.format(Locale.GERMAN, "Histogramm mit einem Fach: Mittel %.4g = Mitte des Fachs %.4g", m, soll));
        // Perzentile: 30 % sehr dunkel, 63 % mittel, 7 % sehr hell (Himmel) → nur das Mittlere zählt
        h = new long[e.bins];
        h[2] = 300; h[i0] = 630; h[62] = 70;
        m = e.meanFromHistogram(h);
        check(Math.abs(m / soll - 1) < 1e-9, String.format(Locale.GERMAN, "Perzentile %.0f–%.0f %%: dunkelste 30 %% und hellste 7 %% zählen nicht (Mittel %.4g)", e.lowPercent, e.highPercent, m));
        check(Double.isNaN(e.meanFromHistogram(new long[e.bins])), "leeres Histogramm: kein Mittel");
        // Ziel
        check(Math.abs(e.targetFor(e.key)) < 1e-12, "beim Bezug keine Korrektur");
        double t3 = e.targetFor(e.key / 8);   // 3 Blenden zu dunkel
        check(Math.abs(t3 - e.strength * 3) < 1e-9, String.format(Locale.GERMAN, "3 Blenden zu dunkel: Ziel %+.2f (Stärke %.1f)", t3, e.strength));
        check(e.targetFor(e.key / 1e4) == e.maxUp && e.targetFor(e.key * 1e4) == -e.maxDown, String.format(Locale.GERMAN, "Grenzen +%.1f / −%.1f Blenden", e.maxUp, e.maxDown));
        // Zeit: nach einer Zeitkonstante 63 % des Wegs, ins Dunkle langsamer als ins Helle
        EyeAdaptation z = new EyeAdaptation();
        long[] dunkel = new long[z.bins], hell = new long[z.bins];
        dunkel[20] = 1000;
        hell[50] = 1000;
        z.update(hell, 0.1);                       // Sprung: sofort
        double ev0 = z.ev();
        check(Math.abs(ev0 - z.target()) < 1e-12, String.format(Locale.GERMAN, "erste Messung gilt sofort (%+.2f Blenden)", ev0));
        z.update(dunkel, 0);
        double ziel = z.target();
        for (int k = 0; k < 30; k++) z.step(z.tauDark / 30);
        double anteil = (z.ev() - ev0) / (ziel - ev0);
        check(Math.abs(anteil - (1 - Math.exp(-1))) < 1e-6, String.format(Locale.GERMAN, "ins Dunkle: nach %.1f s %.0f %% des Wegs", z.tauDark, anteil * 100));
        double evD = z.ev();
        z.update(hell, 0);
        double zielH = z.target();
        for (int k = 0; k < 30; k++) z.step(z.tauBright / 30);
        double anteilH = (z.ev() - evD) / (zielH - evD);
        check(Math.abs(anteilH - (1 - Math.exp(-1))) < 1e-6 && z.tauBright < z.tauDark,
                String.format(Locale.GERMAN, "ins Helle: nach %.1f s %.0f %% des Wegs (schneller als ins Dunkle)", z.tauBright, anteilH * 100));
        z.weight = 0;
        check(z.ev() == 0 && z.factor() == 1, "Wirkung 0: Belichtung unverändert");
        z.weight = 1;
        z.snap();
        z.update(dunkel, 0.016);
        check(Math.abs(z.ev() - z.target()) < 1e-12, "nach snap() gilt die nächste Messung sofort (Schnitt, Standbild)");
        System.out.println(e);
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Prüfungen fehlgeschlagen.");
        if (bad > 0) System.exit(1);
    }

    static void check(boolean ok, String what) {
        all++;
        if (!ok) bad++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
    }

    private ExposureCheck() { }
}
