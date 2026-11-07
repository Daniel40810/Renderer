package com.dan.sky.demo;

import com.dan.sky.CieSky;

import java.util.Locale;

/** Prüfung des CIE-Normhimmels (seit LituyaBay1958 W8b): Werte gegen Moon und Spencer und gegen die Definition. */
public final class CieCheck {
    static int fails, checks;

    static void check(String what, boolean ok, String detail) {
        checks++;
        if (!ok) fails++;
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what + " – " + detail);
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("CieCheck – com.dan.sky.CieSky (ISO 15469, CIE S 011)");
        double h1 = CieSky.relative(1, Math.PI / 2 - 1e-6, Math.toRadians(60), Math.toRadians(80));
        check("Typ 1: Kimm zu Zenit wie Moon und Spencer (1/3)", Math.abs(h1 - 1.0 / 3) < 0.01, String.format("%.3f", h1));
        double m30 = CieSky.relative(1, Math.toRadians(60), 0.5, 1.0), ms = (1 + 2 * Math.sin(Math.toRadians(30))) / 3;
        check("Typ 1: 30° über der Kimm wie (1 + 2 sin γ)/3", Math.abs(m30 - ms) < 0.05, String.format("%.3f gegen %.3f", m30, ms));
        for (int t : new int[]{1, 3, 5, 12}) {
            double s = 0;
            int n = 4000;
            for (int i = 0; i < n; i++) { double cz = (i + 0.5) / n; s += CieSky.normalizedGradation(t, cz) * cz; }
            check("Typ " + t + ": Abstufung bei gleicher Bestrahlung (Mittel 1)", Math.abs(2 * s / n - 1) < 1e-3, String.format("%.4f", 2 * s / n));
        }
        check("Typ 5 gleichmäßig", Math.abs(CieSky.relative(5, 1.2, 0.7, 1.9) - 1) < 1e-9, String.format("%.4f", CieSky.relative(5, 1.2, 0.7, 1.9)));
        double z = CieSky.relative(12, 0, 1.0, 1.0);
        check("Zenit ist 1 (Definition)", Math.abs(z - 1) < 1e-9, String.format("%.4f", z));
        double near = CieSky.relative(12, 1.0, 1.0, 0.05), away = CieSky.relative(12, 1.0, 1.0, 2.5);
        check("Typ 12: um die Sonne heller als gegenüber", near > 3 * away, String.format("%.2f gegen %.2f", near, away));
        double zen = CieSky.normalizedGradation(1, 1), hor = CieSky.normalizedGradation(1, 0);
        check("Typ 1: Decke im Zenit heller, an der Kimm dunkler als gleichmäßig", zen > 1.2 && hor < 0.5, String.format("Zenit %.2f, Kimm %.2f", zen, hor));
        for (int t = 1; t <= 15; t++) System.out.printf("  Typ %2d  Kimm/Zenit %.3f  Mittel %.3f  %s%n", t, 1 / CieSky.gradation(t, 1), CieSky.meanGradation(t), CieSky.NAME[t - 1]);
        System.out.println(fails == 0 ? "Alles in Ordnung (" + checks + " Prüfungen)." : fails + " von " + checks + " Prüfungen FEHLER.");
        if (fails > 0) System.exit(1);
    }
}
