package com.dan.physics.auftrieb;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Form eines schwimmenden Körpers (Seegang Werkbuch, S7a): ob ein Punkt innen liegt und wie weit er trägt. Daraus
 * baut {@link Proben} die Würfel des Körpers und {@link Hydrostatik} die Werte der Handrechnung; in S7b baut
 * `core.szene.Schiffbau` das Netz aus derselben Form, damit Bild und Körper übereinstimmen.
 * <p>
 * <b>Achsen des Körpers:</b> x nach vorn (0 mittschiffs), y nach Backbord, z nach oben ab der Unterkante (Kiel), m.
 */
public interface Form {
    /** Ob (x, y, z) im Körper liegt. */
    boolean innen(double x, double y, double z);

    /** Anteil, mit dem ein Punkt im Körper trägt, wenn er eintaucht (1 dicht; Aufbauten weniger [G]). */
    default double traegt(double x, double y, double z) { return 1; }

    /** Umgrenzung {x0, x1, y0, y1, z0, z1}. */
    double[] kasten();

    String name();

    // ------------------------------------------------------------------ Formen

    /**
     * Ein Rumpf (Einrumpf) aus wenigen Maßen, nach dem Vorbild von `vessel.Hull`, aber mit Kimmradius und Aufkimmung, so
     * dass Prahm (Fähre), Rundspant (Kutter) und Knickspant (schnelles Fahrgastschiff) aus denselben Zahlen kommen.
     * <ul>
     * <li><b>Wasserlinienriss:</b> paralleles Mittelschiff von −mitteAchtern bis mitteVorn (Anteile der halben Länge),
     * vorn und achtern Superellipsen mit den Exponenten bugVoll, heckVoll bis auf die Breite des Bugs bzw. Spiegels;</li>
     * <li><b>Spant:</b> Boden mit Aufkimmung β, Kimm mit Radius r, Seite senkrecht;</li>
     * <li><b>Boden</b> zu den Enden aufgeholt (Vorfuß, Prahmenden);</li>
     * <li><b>Kiel</b> unter dem Rumpf (Höhe, Breite);</li>
     * <li><b>Deck</b> mit Sprung; Aufbauten als Kästen, die beim Eintauchen zu einem Teil tragen [G].</li>
     * </ul>
     */
    final class Rumpf implements Form {
        public final String name;
        /** Länge über alles, Breite, Seitenhöhe (Unterkante Rumpf bis Deck mittschiffs), Konstruktionstiefgang ab Kiel (m). */
        public final double laenge, breite, seitenhoehe, tiefgang;
        /** Paralleles Mittelschiff: vorn und achtern als Anteil der halben Länge. */
        public double mitteVorn = 0.2, mitteAchtern = 0.3;
        /** Völligkeit der Enden (Exponent der Superellipse; 2 Ellipse, größer voller), Breite an Bug und Spiegel (Anteil). */
        public double bugVoll = 1.6, heckVoll = 2.5, bugBreite = 0, spiegel = 0.6;
        /** Aufkimmung (Grad) und Kimmradius (m). */
        public double aufkimmung = 0, kimmradius = 0.5;
        /** Kiel unter dem Rumpf: Höhe und Breite (m). */
        public double kielHoehe = 0, kielBreite = 0.3;
        /** Aufholen des Bodens vorn und achtern (m an den Enden), ab diesem Anteil der halben Länge. */
        public double aufholVorn = 0, aufholVornAb = 0.6, aufholAchtern = 0, aufholAchternAb = 0.7;
        /** Sprung des Decks vorn und achtern (m an den Enden). */
        public double sprungVorn = 0, sprungAchtern = 0;
        /** Aufbauten: je {x0, x1, halbe Breite, Unterkante über Deck mittschiffs, Höhe, trägt}. */
        public final List<double[]> aufbauten = new ArrayList<>();

        public Rumpf(String name, double laenge, double breite, double seitenhoehe, double tiefgang) {
            this.name = name;
            this.laenge = laenge;
            this.breite = breite;
            this.seitenhoehe = seitenhoehe;
            this.tiefgang = tiefgang;
        }

        @Override public String name() { return name; }

        /** Ein Aufbau von x0 bis x1, halbe Breite, Unterkante über dem Deck mittschiffs, Höhe, trägt [G]. */
        public Rumpf aufbau(double x0, double x1, double halbeBreite, double ueberDeck, double hoehe, double traegt) {
            aufbauten.add(new double[] {x0, x1, halbeBreite, ueberDeck, hoehe, traegt});
            return this;
        }

        /** Anteil der halben Breite im Riss bei x (0 … 1). */
        public double riss(double x) {
            double u = 2 * x / laenge;
            if (u > 1 || u < -1) return 0;
            if (u > mitteVorn) {
                double t = (u - mitteVorn) / (1 - mitteVorn);
                return bugBreite + (1 - bugBreite) * Math.pow(Math.max(0, 1 - Math.pow(t, bugVoll)), 1 / bugVoll);
            }
            if (u < -mitteAchtern) {
                double t = (-u - mitteAchtern) / (1 - mitteAchtern);
                return spiegel + (1 - spiegel) * Math.pow(Math.max(0, 1 - Math.pow(t, heckVoll)), 1 / heckVoll);
            }
            return 1;
        }

        /** Unterkante des Rumpfs (ohne Kiel) bei x, über der Unterkante des Kiels (m). */
        public double boden(double x) {
            double u = 2 * x / laenge, r = 0;
            if (u > aufholVornAb && aufholVorn > 0) { double t = (u - aufholVornAb) / (1 - aufholVornAb); r = aufholVorn * t * t; }
            if (u < -aufholAchternAb && aufholAchtern > 0) { double t = (-u - aufholAchternAb) / (1 - aufholAchternAb); r = aufholAchtern * t * t; }
            return kielHoehe + r;
        }

        /** Höhe des Decks bei x (m über der Unterkante des Kiels). */
        public double deck(double x) {
            double u = 2 * x / laenge;
            return kielHoehe + seitenhoehe + sprungVorn * Math.max(0, u) * Math.max(0, u) + sprungAchtern * Math.max(0, -u) * Math.max(0, -u);
        }

        /** Halbe Breite des Rumpfs (ohne Kiel und Aufbauten) bei x in der Höhe z (m); 0 außerhalb. */
        public double halbeBreite(double x, double z) {
            double b = 0.5 * breite * riss(x);
            if (b <= 0) return 0;
            double z0 = boden(x);
            if (z < z0 || z > deck(x)) return 0;
            double zz = z - z0, r = Math.min(kimmradius, 0.999 * b), be = Math.toRadians(aufkimmung);
            double tb = Math.tan(be), cb = Math.cos(be), sb = Math.sin(be);
            double zc = (b - r) * tb + r / cb;
            if (zz >= zc) return b;
            double zt = zc - r * cb;
            if (zz >= zt) return (b - r) + Math.sqrt(Math.max(0, r * r - (zz - zc) * (zz - zc)));
            return tb > 1e-9 ? Math.min(b, zz / tb) : 0;
        }

        private boolean imKiel(double x, double y, double z) {
            return kielHoehe > 0 && Math.abs(y) <= kielBreite / 2 && z >= 0 && z <= boden(x) + 1e-9 && Math.abs(2 * x / laenge) <= 0.96 && riss(x) > 0;
        }

        private double[] aufbauAn(double x, double y, double z) {
            double d0 = deck(0);
            for (double[] a : aufbauten)
                if (x >= a[0] && x <= a[1] && Math.abs(y) <= a[2] && z >= d0 + a[3] && z <= d0 + a[3] + a[4]) return a;
            return null;
        }

        @Override public boolean innen(double x, double y, double z) {
            return Math.abs(y) < halbeBreite(x, z) || imKiel(x, y, z) || aufbauAn(x, y, z) != null;
        }

        @Override public double traegt(double x, double y, double z) {
            if (Math.abs(y) < halbeBreite(x, z) || imKiel(x, y, z)) return 1;
            double[] a = aufbauAn(x, y, z);
            return a != null ? a[5] : 0;
        }

        @Override public double[] kasten() {
            double top = Math.max(deck(laenge / 2), deck(-laenge / 2));
            top = Math.max(top, deck(0));
            for (double[] a : aufbauten) top = Math.max(top, deck(0) + a[3] + a[4]);
            return new double[] {-laenge / 2, laenge / 2, -breite / 2, breite / 2, 0, top};
        }
    }

    /**
     * Ein Drehkörper um die senkrechte Achse (Tonnen): Halbmesser r(z) als Polygonzug über z, ab z = 0 (Unterkante).
     */
    final class Drehkoerper implements Form {
        public final String name;
        final double[] zs, rs;

        public Drehkoerper(String name, double[] zs, double[] rs) {
            this.name = name;
            this.zs = zs.clone();
            this.rs = rs.clone();
        }

        @Override public String name() { return name; }

        /** Halbmesser in der Höhe z (m). */
        public double halbmesser(double z) {
            if (z < zs[0] || z > zs[zs.length - 1]) return 0;
            for (int i = 1; i < zs.length; i++)
                if (z <= zs[i]) {
                    double t = zs[i] > zs[i - 1] ? (z - zs[i - 1]) / (zs[i] - zs[i - 1]) : 1;
                    return rs[i - 1] + (rs[i] - rs[i - 1]) * t;
                }
            return 0;
        }

        @Override public boolean innen(double x, double y, double z) {
            double r = halbmesser(z);
            return x * x + y * y < r * r;
        }

        @Override public double[] kasten() {
            double r = 0;
            for (double v : rs) r = Math.max(r, v);
            return new double[] {-r, r, -r, r, zs[0], zs[zs.length - 1]};
        }
    }

    /** Ein Ellipsoid mit den Halbachsen a (x), b (y), c (z), auf der Unterkante z = 0 (Ente) [G]. */
    final class Ellipsoid implements Form {
        public final String name;
        public final double a, b, c;

        public Ellipsoid(String name, double a, double b, double c) { this.name = name; this.a = a; this.b = b; this.c = c; }

        @Override public String name() { return name; }

        @Override public boolean innen(double x, double y, double z) {
            double u = x / a, v = y / b, w = (z - c) / c;
            return u * u + v * v + w * w < 1;
        }

        @Override public double[] kasten() { return new double[] {-a, a, -b, b, 0, 2 * c}; }
    }

    /** Ein Quader l × b × h (Kiste, Balken), auf der Unterkante z = 0. */
    final class Quader implements Form {
        public final String name;
        public final double l, b, h;

        public Quader(String name, double l, double b, double h) { this.name = name; this.l = l; this.b = b; this.h = h; }

        @Override public String name() { return name; }

        @Override public boolean innen(double x, double y, double z) { return Math.abs(x) < l / 2 && Math.abs(y) < b / 2 && z > 0 && z < h; }

        @Override public double[] kasten() { return new double[] {-l / 2, l / 2, -b / 2, b / 2, 0, h}; }
    }

    /** Ein liegender Zylinder längs x (Stamm), Halbmesser r, Länge l, auf der Unterkante z = 0. */
    final class Zylinder implements Form {
        public final String name;
        public final double r, l;

        public Zylinder(String name, double r, double l) { this.name = name; this.r = r; this.l = l; }

        @Override public String name() { return name; }

        @Override public boolean innen(double x, double y, double z) { double w = z - r; return Math.abs(x) < l / 2 && y * y + w * w < r * r; }

        @Override public double[] kasten() { return new double[] {-l / 2, l / 2, -r, r, 0, 2 * r}; }
    }
}
