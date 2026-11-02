package com.dan.vessel.demo;

import com.dan.vessel.Water;

/**
 * Eine gerechnete Bucht für Prüfung und Vorführung (LituyaBay1958 Phase 15): Querschnitt in x (nach Osten zum Ufer),
 * längs z gleich. Draußen 30 m tief, dann ansteigend auf den Ankerplatz in 9,1 m (5 Faden wie bei der Edrie), ab 40 m
 * das Ufer mit 1 : 4,5 hinauf in den Wald. Eine Einzelwelle läuft von See heran (η = H sech²((x − x₀ − c t) / L),
 * c = √(g (h + H)), Strömung u = c η / (h + η)) und wird am Ufer mit {@link #reflexion} zurückgeworfen. Über dem Ufer
 * steht das Wasser waagerecht auf der Höhe des Auflaufs R(t) = {@link #auflauf} · η(Uferlinie, t); die Strömung dort folgt
 * der Uferlinie (u = Ṙ / Neigung), beim Fallen als Rückstrom hinaus – er zieht das Boot zurück in die Bucht. Gestaltet [G]; die Höhe der Front an der Edrie war nach MILLER60 15 bis 23 m (50 bis 75 Fuß); hier 15 m, am Ufer mit der Reflexion fast doppelt so hoch.
 */
public final class Bucht implements Water {
    public static final double TIEFE = 5 * 1.8288;
    /** Höhe der Welle (m), Breite L (m), Anfang der Front (x₀, m), Anteil der Reflexion, Zeit (s). */
    public double hoehe = 15, breite = 55, x0 = -700, reflexion = 0.9, auflauf = 1.0, t;
    /** Neigung des Ufers (1 : 4,5). */
    public static final double NEIGUNG = 1 / 4.5;
    /** Wasserstand in Ruhe (m). */
    public double pegel = 0;
    public boolean welle = true;

    public double bed(double x, double z) {
        if (x < -400) return pegel - 30;
        if (x < -60) return pegel - 30 + (x + 400) / 340 * (30 - TIEFE);
        if (x < 40) return pegel - TIEFE;
        return pegel - TIEFE + (x - 40) / 4.5;
    }

    /** Uferlinie in Ruhe (x, m). */
    public double ufer() { return 40 + TIEFE * 4.5; }

    double c() { return Math.sqrt(9.81 * (TIEFE + hoehe)); }

    static double sech2(double v) { double c = Math.cosh(Math.max(-30, Math.min(30, v))); return 1 / (c * c); }

    /** Anstieg über Ruhe von der hinlaufenden und der zurückgeworfenen Welle. */
    double[] eta(double x) {
        if (!welle) return new double[2];
        double c = c(), xs = ufer();
        double ei = hoehe * sech2((x - x0 - c * t) / breite);
        double ts = (xs - x0) / c, er = 0;
        if (t > ts - 3 * breite / c) er = reflexion * hoehe * sech2((x - xs + c * (t - ts)) / breite);
        return new double[]{ei, er};
    }

    /** Auflauf über dem Ufer: Höhe R(t) über Ruhe (m). */
    public double auflaufHoehe(double tt) {
        double t0 = t;
        t = tt;
        double[] e = eta(ufer());
        t = t0;
        return auflauf * (e[0] + e[1]);
    }

    public double height(double x, double z) {
        double b = bed(x, z);
        if (x >= ufer()) return Math.max(b, pegel + auflaufHoehe(t));
        double[] e = eta(x);
        return Math.max(pegel + uebergang(x) * (e[0] + e[1]), b);
    }

    public double[] velocity(double x, double z) {
        double c = c();
        if (x >= ufer()) {
            double r = (auflaufHoehe(t + 0.25) - auflaufHoehe(t - 0.25)) / 0.5 / NEIGUNG;
            double xs = ufer() + auflaufHoehe(t) / NEIGUNG;
            double k = Math.max(0, 1 - (x - ufer()) / Math.max(1, xs - ufer()));   // an der Spitze des Auflaufs steht das Wasser
            return new double[]{Math.max(-0.6 * c, Math.min(0.6 * c, r * (0.4 + 0.6 * k))), 0};
        }
        double[] e = eta(x);
        double d = Math.max(0.5, pegel - bed(x, z)), k = uebergang(x);
        return new double[]{c * k * e[0] / (d + k * e[0]) - c * k * e[1] / (d + k * e[1]), 0};
    }

    /** Vor dem Ufer wächst die Welle auf 80 m stetig auf den Auflauf (kein Sprung an der Uferlinie). */
    double uebergang(double x) {
        double t = Math.max(0, Math.min(1, (x - (ufer() - 80)) / 80));
        return 1 + (auflauf - 1) * t * t * (3 - 2 * t);
    }

    /** Wann die Front (Scheitel) den Ort x erreicht (s). */
    public double ankunft(double x) { return (x - x0) / c(); }
}
