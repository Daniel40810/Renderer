package com.dan.precip;

/**
 * Was ein Tropfen beim Aufschlag tut. Auf festem Grund spritzt er, wenn K = Oh · Re^1,25 über 57,7 liegt (Mundo u. a.
 * 1995), sonst breitet er sich nur aus; nasser Grund spritzt leichter. Auf tiefem Wasser hängt es an Größe und
 * Weberzahl (Engel 1966, Rein 1993, Pumphrey und Elmore 1990): kleine Tropfen fließen ein (nur Kapillarwellen), mittlere
 * schlagen einen Krater, aus dem ein Worthington-Strahl steigt, der oben ein Tröpfchen abschnüren kann, große werfen eine
 * Krone mit Spitzen, deren Tröpfchen wegfliegen, und schließen dabei Luft ein (Bläschen). Die Zahlen für Höhe, Zahl und
 * Geschwindigkeit der Tröpfchen sind an Fotos und Messungen angelehnt und als gestaltet markiert [G]; die Grenzen
 * zwischen den Arten sind die der Literatur. Dieselbe Regel steht in {@code gl/splash.comp}.
 */
public final class Splash {
    private Splash() {}

    /** Worauf ein Tropfen fällt. */
    public enum Surface { WATER, GROUND, CANOPY }

    /** Art des Einschlags. */
    public enum Kind {
        /** fließt ein, nur Wellen (Wasser) oder ein nasser Fleck (Grund) */
        COALESCE,
        /** Krater und Worthington-Strahl (Wasser) */
        JET,
        /** Krater, Krone mit Spitzen, Strahl, Bläschen (Wasser) */
        CROWN,
        /** breitet sich aus und haftet (Grund, K unter 57,7) */
        DEPOSIT,
        /** spritzt in einer Krone aus Tröpfchen (Grund) */
        SPLASH
    }

    /** Art des Einschlags für D mm mit v m/s auf der Fläche s; nass: Grund mit Wasserfilm (spritzt leichter). */
    public static Kind kind(double d, double v, Surface s, boolean wet) {
        if (s == Surface.WATER) {
            double we = Drops.weber(d, v);
            if (d < 0.6 || we < 40) return Kind.COALESCE;
            if (d < 2.6 || we < 900) return Kind.JET;
            return Kind.CROWN;
        }
        double k = Drops.splashK(d, v) * (wet ? 1.6 : 1.0);
        return k > Drops.SPLASH_K ? Kind.SPLASH : Kind.DEPOSIT;
    }

    /** Höhe (m) des Worthington-Strahls [G]: rund 4 D bei 2 mm, wächst mit der Weberzahl, höchstens 4 cm. */
    public static double jetHeight(double d, double v) {
        double we = Drops.weber(d, v);
        return Math.min(0.04, d * 1e-3 * 4 * Math.sqrt(we / 1100));
    }

    /** Endhalbmesser (m) der Krone [G]: rund 3 D. */
    public static double crownRadius(double d) { return d * 1e-3 * 3; }

    /** Höhe (m) der Krone [G]: rund 2 D. */
    public static double crownHeight(double d, double v) { return d * 1e-3 * (1.4 + 0.25 * v); }

    /** Lebensdauer (s) von Krone und Strahl [G]: Krone 15–25 ms, Strahl 40–80 ms. */
    public static double crownLife(double d) { return 0.012 + 0.003 * d; }

    public static double jetLife(double d) { return 0.03 + 0.01 * d; }

    /** Zahl der Spritztröpfchen [G]: Krone auf Wasser 6–16, Krone auf Grund 4–12, Strahl 0–1 (abgeschnürt). */
    public static int droplets(Kind k, double d) {
        return switch (k) {
            case CROWN -> (int) Math.round(4 + 2.5 * d);
            case SPLASH -> (int) Math.round(2 + 2 * d);
            case JET -> d > 1.6 ? 1 : 0;
            default -> 0;
        };
    }

    /** Größe der Spritztröpfchen (mm) [G]: ein Zehntel bis ein Viertel des Tropfens. */
    public static double dropletSize(double d, double u) { return d * (0.1 + 0.15 * u); }

    /** Geschwindigkeit (m/s) der Spritztröpfchen [G]: 15–45 % der Aufschlaggeschwindigkeit. */
    public static double dropletSpeed(double v, double u) { return v * (0.15 + 0.3 * u); }

    /** Bläschen, die ein Einschlag auf Wasser hinterlässt [G]: große Tropfen schließen unregelmäßig Luft ein. */
    public static double bubbleChance(double d) { return d < 2.8 ? 0 : Math.min(0.7, (d - 2.8) * 0.35); }

    /** Lebensdauer (s) eines Bläschens [G]: 0,3 bis 2 s. */
    public static double bubbleLife(double u) { return 0.3 + 1.7 * u; }
}
