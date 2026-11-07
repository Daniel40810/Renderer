package com.dan.horizon.demo;

import com.dan.horizon.Aerial;
import com.dan.horizon.Airmass;
import com.dan.horizon.Horizon;

import java.util.Locale;

/**
 * Prüfung von {@code com.dan.horizon} ohne Grafikkarte: Kimm, Bergschatten an einem Grat, Erdkrümmung, Halbschatten,
 * Luftmasse, Alpenglühen, Luftperspektive, Wiederholbarkeit. Endet mit „Alles in Ordnung.“
 */
public final class HorizonCheck {
    private static int fehler, pruef;

    public static void main(String[] args) {
        Locale.setDefault(Locale.GERMANY);
        System.out.println("com.dan.horizon – Prüfung");
        Horizon hz = new Horizon();

        // 1 Kimm des Meers aus 4 000 m mit k = 0,13 (Lituya W1: 1,89°)
        double dip = Math.toDegrees(hz.seaDip(4000));
        check("Kimm aus 4 000 m", dip, 1.88, 1.91, "°");
        check("Kimm aus 0 m", Math.toDegrees(hz.seaDip(0)), 0, 1e-12, "°");

        // 2 flaches Land auf Meereshöhe: Horizontwinkel = Kimm (leicht unter null wegen der Krümmung)
        Horizon.Grid flat = flat(0, 4000, 64);
        double[] r = new double[2];
        hz.angle(flat, null, 2000, 2000, 270, flat.cell(), r);
        check("flach: Winkel", Math.toDegrees(r[0]), -0.6, 0.0, "°");

        // 3 ein Grat von 500 m, 5 km westlich, die nahe Kante oben in 4 850 m: Winkel atan((500 − d²/2R)/d)
        Horizon.Grid ridge = ridge(500, 5000);
        hz.angle(ridge, null, 8000, 6000, 270, ridge.cell(), r);
        double R = hz.effectiveRadius();
        double soll = Math.toDegrees(Math.atan((500 - 4850.0 * 4850 / (2 * R)) / 4850));
        check("Grat 500 m in 5 km", Math.toDegrees(r[0]), soll - 0.12, soll + 0.01, "°");   // Schritte rund 110 m: der Grat hat oben 300 m Breite
        hz.angle(ridge, null, 8000, 6000, 90, ridge.cell(), r);
        check("Grat im Rücken: kein Schatten", Math.toDegrees(r[0]), -0.6, 0.05, "°");

        // 4 Schattenlänge: bei 3° Sonne liegt Schatten bis rund 500/tan(3°) ≈ 9,5 km hinter dem Grat
        double inside = hz.visibility(angleAt(hz, ridge, 3000 + 7000, 6000), 0, 0, Math.sin(Math.toRadians(3)));
        double outside = hz.visibility(angleAt(hz, ridge, 3000 + 11000, 6000), 0, 0, Math.sin(Math.toRadians(3)));
        check("Schatten 7 km hinter dem Grat", inside, 0, 0.01, "");
        check("Sonne 11 km hinter dem Grat", outside, 0.99, 1, "");

        // 5 Erdkrümmung: in 60 km fällt die Kimm um 60²/2R
        double drop = 60000.0 * 60000 / (2 * R);
        check("Krümmung in 60 km", drop, 240, 250, "m");

        // 6 Halbschatten: die Sonne verschwindet über ±0,26°
        check("Halbschatten halb", hz.visibility(0, 0, 0, 0), 0.49, 0.51, "");
        check("Halbschatten oben", hz.visibility(0, 0, 0, Math.sin(0.0047)), 0.999, 1, "");
        check("im Schatten am Boden", hz.visibility(0.02, 0, 0, Math.sin(0.01)), 0.0, 0.001, "");
        check("Hub über dem Boden (30 m)", hz.visibility(0.02, 0, 30, Math.sin(0.01)), 0.999, 1, "");

        // 7 Luftmasse: im Zenit 1, bei 60° rund 1,154, an der Kimm rund 38; stetig über die Waagerechte
        check("Luftmasse Zenit", Airmass.relative(0, 1, 8000), 0.999, 1.001, "");
        check("Luftmasse 30° Höhe", Airmass.relative(0, Math.sin(Math.toRadians(30)), 8000), 1.99, 2.01, "");
        check("Luftmasse Kimm", Airmass.relative(0, 0, 8000), 37, 39, "");
        double a = Airmass.relative(4000, Math.sin(1e-4), 8000), b = Airmass.relative(4000, Math.sin(-1e-4), 8000);
        check("stetig an der Kimm (4 000 m)", Math.abs(a - b) / a, 0, 0.6, "");

        // 8 Alpenglühen: auf 4 000 m bei 0,5° unter der Kimm rot, rund ein Fünftel im Rot (Lituya W1)
        double[] t = Airmass.standard(4000, Math.sin(Math.toRadians(-0.5)));
        check("Alpenglühen Rot", t[0], 0.10, 0.35, "");
        check("Alpenglühen rot vor blau", t[0] / Math.max(t[2], 1e-12), 20, 1e9, "");
        double[] mittag = Airmass.standard(0, Math.sin(Math.toRadians(54)));
        check("Mittag Grün", mittag[1], 0.75, 0.9, "");

        // 9 Luftperspektive: auf 20 km in 300 m Höhe bei 60 km Dunst; Kamm in 3 km Höhe klarer; Wald (K16)
        Aerial ae = new Aerial();
        double tal = ae.fraction(20000, 300, 0, 60000), kamm = ae.fraction(20000, 3000, 0.1, 60000);
        check("Dunst im Tal (20 km)", tal, 0.1, 0.25, "");
        check("Kamm klarer als Tal", kamm / tal, 0, 0.6, "");
        double fd = ae.fogDistanceAt(0, 1 / 60000.0);
        check("Dunst des Walds am Meer", fd, 1 / (0.45 / 60000 + 1 / 150000.0) - 1, 1 / (0.45 / 60000 + 1 / 150000.0) + 1, "m");

        // 10 Karte: gleich zweimal, und gleich der Rechnung je Punkt
        float[] m1 = hz.map(flat(0, 4000, 32), ridge, null, 270), m2 = hz.map(flat(0, 4000, 32), ridge, null, 270);
        check("Karte wiederholbar", java.util.Arrays.equals(m1, m2) ? 1 : 0, 1, 1, "");
        check("Azimut gewandert", hz.moved(270, 270.3) ? 1 : 0, 1, 1, "");
        check("Azimut nicht gewandert", hz.moved(359.9, 0.1) ? 1 : 0, 0, 0, "");

        System.out.println();
        System.out.println(fehler == 0 ? "Alles in Ordnung. (" + pruef + " Prüfungen)" : fehler + " von " + pruef + " Prüfungen FEHLER");
        if (fehler > 0) System.exit(1);
    }

    private static double angleAt(Horizon hz, Horizon.Grid g, double x, double z) {
        double[] r = new double[2];
        hz.angle(g, null, x, z, 270, g.cell(), r);
        return r[0];
    }

    /** Ebene auf Höhe h, Kante e, n Zellen. */
    static Horizon.Grid flat(double h, double e, int n) {
        float[] v = new float[n * n];
        java.util.Arrays.fill(v, (float) h);
        return new Horizon.Grid(0, 0, e, n, v);
    }

    /** Ein Grat der Höhe H bei x = 3 000 m (Nord–Süd, oben 300 m breit, Flanken 400 m), sonst Meer; 20 × 20 km zu 25 m. */
    static Horizon.Grid ridge(double H, double unused) {
        int n = 800;
        double e = 20000, c = e / n;
        float[] v = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = (i + 0.5) * c;
                v[j * n + i] = (float) (H * Math.max(0, Math.min(1, 1 - (Math.abs(x - 3000) - 150) / 400)));
            }
        return new Horizon.Grid(0, 0, e, n, v);
    }

    private static void check(String name, double v, double lo, double hi, String unit) {
        pruef++;
        boolean ok = v >= lo && v <= hi;
        if (!ok) fehler++;
        System.out.printf("  %-34s %12.4f %-2s  [%s … %s]  %s%n", name, v, unit, fmt(lo), fmt(hi), ok ? "ok" : "FEHLER");
    }

    private static String fmt(double v) { return Math.abs(v) >= 1e8 ? "∞" : String.format(Locale.GERMANY, "%.4g", v); }
}
