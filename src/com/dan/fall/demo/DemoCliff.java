package com.dan.fall.demo;

import com.dan.fall.Fall;

/**
 * Die Wand der Vorführung als Höhenfeld (ohne Grafikkarte, auch für die Prüfung): eine Hochfläche auf 250 m, die bei
 * z = 0 senkrecht in einen See (Spiegel 0 m, Grund −15 m) abbricht; seitlich steigen die Talflanken an. Die Kante des
 * Falls liegt 1 m vor dem Abbruch, der Strahl fällt frei.
 * <p>
 * Seit Phase 7 dazu die <b>Stufenwand</b> ({@link #stufen}) für den Lauf über Fels: von der Kante senkrecht auf 130 m,
 * ein Absatz mit 25° bis 109 m, dann mit 63° ans Wasser.
 */
public final class DemoCliff {
    private DemoCliff() { }

    /** Höhe der Kante (m über dem Spiegel). */
    public static final double LIP = 250;

    public static double height(double x, double z) {
        double plateau = LIP + 10 * smooth(0, -300, z) + 3 * Math.sin(x * 0.05) * Math.cos(z * 0.07);
        double h = z < 0 ? plateau : -15;
        // Talflanken links und rechts, höher als die Kante
        double side = Math.abs(x) - 260 - 0.25 * Math.max(0, z);
        h = Math.max(h, 320 * smooth(0, 260, side) - 15);
        return h;
    }

    /** Höhe des Absatzes unter der senkrechten Wand der Stufenwand (m). */
    public static final double ABSATZ = 130;

    /** Profil der Stufenwand (Höhe über z ≥ 0): senkrecht auf 130 m, Absatz mit 25° über 45 m, 63° bis ans Wasser, dann der Grund. */
    public static double stufenProfil(double z) {
        if (z < 45) return ABSATZ - z * Math.tan(Math.toRadians(25));
        double b = ABSATZ - 45 * Math.tan(Math.toRadians(25));
        return Math.max(-15, b - (z - 45) * Math.tan(Math.toRadians(63)));
    }

    /** Die Stufenwand: wie {@link #height}, aber die Wand fällt in Stufen (für den Lauf über Fels). */
    public static double stufen(double x, double z) {
        double plateau = LIP + 10 * smooth(0, -300, z) + 3 * Math.sin(x * 0.05) * Math.cos(z * 0.07);
        double h = z < 0 ? plateau : stufenProfil(z);
        double side = Math.abs(x) - 260 - 0.25 * Math.max(0, z);
        return Math.max(h, 320 * smooth(0, 260, side) - 15);
    }

    /** Der Fall der Vorführung im Lauf über Fels an der Stufenwand (Kante 1 m vor dem Abbruch, wie der freie Fall). */
    public static Fall stufenFall() {
        Fall f = new Fall();
        f.lipA = new double[]{-20, LIP, 1};
        f.lipB = new double[]{20, LIP, 1};
        f.headingDeg = 180;
        f.waterLevel = 0;
        f.ground = DemoCliff::stufen;
        f.overRock = true;
        f.update();
        return f;
    }

    /** Der Fall der Vorführung: Kante 40 m breit auf 250 m, fließt nach Süden (+z) in den See. */
    public static Fall fall() {
        Fall f = new Fall();
        f.lipA = new double[]{-20, LIP, 1};
        f.lipB = new double[]{20, LIP, 1};
        f.headingDeg = 180;
        f.waterLevel = 0;
        f.ground = DemoCliff::height;
        f.update();
        return f;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
