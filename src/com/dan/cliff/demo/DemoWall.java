package com.dan.cliff.demo;

import com.dan.cliff.CNoise;

/**
 * Die Wand der Vorführung als Höhenfeld (ohne Grafikkarte, auch für die Prüfung): Talboden auf 0 m im Süden (+z),
 * Wand nach Norden auf 300 m, ein Absatz bei rund 150 m, leichtes Rauschen.
 */
public final class DemoWall {
    private DemoWall() { }

    public static double height(double x, double z) {
        double s = -z + 30 * (CNoise.value(x / 160, 0, 0, 3) - 0.5) * 2 + 8 * (CNoise.value(x / 40, 0, 5, 3) - 0.5);
        double wall = 300 * smooth(0, 110, s);
        double ledge = 18 * smooth(48, 56, s) * (1 - smooth(56, 70, s));
        double floor = 6 * (CNoise.value(x / 90, z / 90, 1, 4) - 0.5);
        return wall - ledge * 0.5 + floor + 4 * (CNoise.value(x / 25, z / 25, 2, 5) - 0.5);
    }

    /**
     * Seit Sylt Phase 4: ein Kliff aus Lockergestein wie das Rote Kliff, Strand im Süden (+z) auf 1,5 m, Fuß bei
     * z ≈ 30 m mit Sturzkegeln, die Wand rund 26 m hoch und 55° bis 70° steil, oben Dünen mit Strandhafer.
     */
    public static double kliff(double x, double z) {
        double foot = 30 + 16 * (CNoise.value(x / 90, 0, 4, 7) - 0.5) + 6 * (CNoise.value(x / 25, 0, 8, 7) - 0.5);
        double s = foot - z;                                              // Meter nördlich des Fußes
        double top = 26 + 10 * (CNoise.value(x / 150, 0, 9, 7) - 0.5);
        double wall = top * smooth(0, 15, s);
        double beach = 1.5 + Math.min(0, s) * 0.025;
        double fans = 3.2 * smooth(-12, 0, s) * (1 - smooth(1, 7, s)) * smooth(0.35, 0.75, CNoise.value(x / 18, 0, 3, 8));
        double dunes = 2.5 * (CNoise.value(x / 35, z / 35, 2, 9) - 0.4) * smooth(16, 30, s);
        return beach + wall + fans + dunes + 0.4 * (CNoise.value(x / 6, z / 6, 1, 10) - 0.5);
    }

    /** z des Fußes (Strand → Wand) und der Oberkante bei x im Kliff. */
    public static double kliffFoot(double x) { return 30 + 16 * (CNoise.value(x / 90, 0, 4, 7) - 0.5) + 6 * (CNoise.value(x / 25, 0, 8, 7) - 0.5); }
    public static double kliffEdge(double x) { return kliffFoot(x) - 15; }

    /** z der Wand auf der Höhe y bei x (Suche von Süden nach Norden). */
    public static double zAt(double x, double y) {
        for (double z = 80; z > -200; z -= 0.5) if (height(x, z) >= y) return z;
        return -100;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
