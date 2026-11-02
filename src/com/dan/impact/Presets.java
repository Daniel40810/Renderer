package com.dan.impact;

/** Fertige Fälle für {@link Impact}. */
public final class Presets {
    private Presets() { }

    /** Wassertiefe im Gilbert Inlet (m), Abstand Uferlinie bis Uferlinie (m) [G wie landslide.gilbertInlet1958Terrain]. */
    public static final double DEPTH = 122, WIDTH = 1342, SLOPE = 45, COUNTER = 45;

    /** Der Laborfall nach FRITZ01 in Naturmaßen: Granulat und Profil des Versuchs. */
    public static Impact gilbertInlet1958(Impact.Quality q) { return gilbertInlet1958(q, Inflow.fritz01(), Granulate.fritz01()); }

    /**
     * Der Schnitt durch den Gilbert Inlet wie im Versuch FRITZ01 (Rinne, Modell 1:675, hier in Naturmaßen): Hang 45° vom
     * Ufer bis zur Sohle in 122 m, ebener Grund, Gegenhang 45° mit der Uferlinie 1 342 m vom Aufprall; Zulauf an der
     * Uferlinie. Pegel bei 600, 885 und 1 100 m, Auflauf am Gegenhang.
     */
    public static Impact gilbertInlet1958(Impact.Quality q, Inflow in, Granulate g) {
        Impact s = new Impact(q.dx);
        s.name = "Gilbert Inlet 1958 im Schnitt";
        s.source = "FRITZ01, Modell 1:675 auf Natur; Gelände wie im Versuch [G]";
        s.grain = g;
        double a = Math.toRadians(SLOPE), up = Impact.rampLength(in) + 6 * q.dx;
        double xl = -up * Math.cos(a), yl = up * Math.sin(a);
        double footL = DEPTH / Math.tan(a), footR = WIDTH - DEPTH / Math.tan(Math.toRadians(COUNTER)), top = 650;
        s.bed(xl, yl, footL, -DEPTH, footR, -DEPTH, WIDTH + top / Math.tan(Math.toRadians(COUNTER)), top);
        s.fill(0);
        s.feed(in, 0, 0, SLOPE);
        s.gauge("600 m", 600);
        s.gauge("885 m", 885);
        s.gauge("1 100 m", 1100);
        s.runupFrom(footR);
        s.boxX0 = xl - 200;
        s.boxX1 = WIDTH + 900;
        s.boxY0 = -DEPTH - 200;
        s.boxY1 = 1500;
        s.cavernX0 = 0;
        s.cavernX1 = 800;
        s.energyFrom = 450;
        return s;
    }
}
