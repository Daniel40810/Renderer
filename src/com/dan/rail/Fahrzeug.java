package com.dan.rail;

/**
 * Seit Brocken Phase 13: die Lage eines Fahrzeugs auf dem Gleis, ohne Grafik. Ein Fahrzeug steht zwischen seinen beiden
 * Drehpunkten (je 35 % der Länge von der Mitte): Richtung und Neigung aus diesen Punkten, Kippen mit der Überhöhung,
 * auf Wunsch seitlich versetzt (Ausweichgleis). Die Lok schaut immer zu wachsendem s (den Schornstein bergauf; bergab
 * fährt sie mit dem Kohlenkasten voran) [G]. Koordinaten im Fahrzeug: x rechts, y oben ab Schienenoberkante, z vorn.
 */
public final class Fahrzeug {
    /** Lage des Schornsteins (vor der Mitte der Lok, über der Schiene; m) und des Führerstands zum Hinauslehnen [G]. */
    public static final double SCHLOT_VOR = 4.25, SCHLOT_HOCH = 3.72, STAND_VOR = -2.6, STAND_HOCH = 3.05, STAND_SEITE = 1.75;

    private Fahrzeug() { }

    /**
     * Matrix (Spalten) eines Fahrzeugs mit der Mitte bei s und der Länge len: zwischen den Drehpunkten aufs Gleis
     * gesetzt, vorn zu wachsendem s, mit der Überhöhung gekippt.
     */
    public static double[] lage(Track t, double s, double len) { return lage(t, s, len, null); }

    /** Wie {@link #lage(Track, double, double)} auf einem um versatz(s) m seitlich versetzten Gleis (null: keins). */
    public static double[] lage(Track t, double s, double len, java.util.function.DoubleUnaryOperator versatz) {
        double a = 0.35 * len;
        double[] p0 = auf(t, s - a, versatz), p1 = auf(t, s + a, versatz);
        double fx = p1[0] - p0[0], fy = p1[1] - p0[1], fz = p1[2] - p0[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-6) { Track.Pose p = t.at(s); double h = Math.toRadians(p.heading()); fx = Math.sin(h); fy = 0; fz = -Math.cos(h); fl = 1; }
        fx /= fl; fy /= fl; fz /= fl;
        double c = Math.atan2(t.cant(s), t.gauge + 0.067);
        // rechts waagerecht (wie im Gleis: vorn (sin h, 0, −cos h), rechts (cos h, 0, sin h)), oben = rechts × vorn,
        // dann beide um die Fahrtrichtung mit der Überhöhung gekippt
        double rx = -fz, rz = fx, rl = Math.hypot(rx, rz);
        rx /= rl; rz /= rl;
        double upx = -rz * fy, upy = rz * fx - rx * fz, upz = rx * fy;
        double ul = Math.sqrt(upx * upx + upy * upy + upz * upz);
        upx /= ul; upy /= ul; upz /= ul;
        double cr = Math.cos(c), sr = Math.sin(c);
        double Rx = rx * cr - upx * sr, Ry = -upy * sr, Rz = rz * cr - upz * sr;
        double Ux = rx * sr + upx * cr, Uy = upy * cr, Uz = rz * sr + upz * cr;
        double mx = (p0[0] + p1[0]) / 2, my = (p0[1] + p1[1]) / 2, mz = (p0[2] + p1[2]) / 2;
        return new double[]{Rx, Ry, Rz, 0, Ux, Uy, Uz, 0, fx, fy, fz, 0, mx, my, mz, 1};
    }

    /** Ein Punkt auf dem Gleis an der Stelle s, seitlich versetzt. */
    static double[] auf(Track t, double s, java.util.function.DoubleUnaryOperator versatz) {
        Track.Pose p = t.at(s);
        double o = versatz == null ? 0 : versatz.applyAsDouble(s), h = Math.toRadians(p.heading());
        return new double[]{p.x() + Math.cos(h) * o, p.y(), p.z() + Math.sin(h) * o};
    }

    /** Ein Punkt im Fahrzeug (x rechts, y oben, z vorn) in der Szene. */
    public static double[] punkt(double[] m, double x, double y, double z) {
        return new double[]{m[12] + m[0] * x + m[4] * y + m[8] * z, m[13] + m[1] * x + m[5] * y + m[9] * z, m[14] + m[2] * x + m[6] * y + m[10] * z};
    }

    /** Oberkante des Schornsteins der Lok eines Zuges (Szene). */
    public static double[] schlot(Track t, Train z) {
        double[] f = z.fahrzeuge()[0];
        return punkt(lage(t, f[0], f[1], z.versatz), 0, SCHLOT_HOCH, SCHLOT_VOR);
    }

    /** Der Lokführer lehnt sich rechts aus dem Führerhaus: Auge (Szene) und Blickrichtung (Grad von Nord) in Fahrtrichtung. */
    public static double[] fuehrerstand(Track t, Train z) {
        double[] f = z.fahrzeuge()[0], m = lage(t, f[0], f[1], z.versatz);
        double side = z.richtung > 0 ? STAND_SEITE : -STAND_SEITE;
        double[] e = punkt(m, side, STAND_HOCH, STAND_VOR);
        double fx = m[8] * z.richtung, fz = m[10] * z.richtung;
        double h = Math.toDegrees(Math.atan2(fx, -fz));
        return new double[]{e[0], e[1], e[2], h < 0 ? h + 360 : h};
    }

    /** Stampfen (Grad, + Nase hoch in Fahrtrichtung) und Rollen (Grad) der Lok eines Zuges, für eine Kamera. */
    public static double[] neigung(Track t, Train z) {
        double[] f = z.fahrzeuge()[0], m = lage(t, f[0], f[1], z.versatz);
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, m[9]))) ) * z.richtung;
        double roll = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, m[1])))) * z.richtung;
        return new double[]{pitch, roll};
    }
}
