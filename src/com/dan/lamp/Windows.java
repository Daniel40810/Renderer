package com.dan.lamp;

/**
 * Fensterlicht nach Uhrzeit (seit Timmendorf Phase 11, F18 A): welcher Anteil der Fenster eines Hauses brennt, je nach
 * Nutzung und Stunde; welches Fenster (fest je Haus und Fenster, damit nichts springt) und in welcher Farbe und
 * Leuchtdichte.
 * <p>
 * Die Tagesgänge sind gestaltet [G], geformt nach der Zeitverwendungserhebung des Statistischen Bundesamts 2012/13
 * (die meisten gehen zwischen 22 und 24 Uhr schlafen, stehen zwischen 6 und 7 Uhr auf). Ferienwohnungen brennen später
 * und morgens weniger, Läden bis Ladenschluss (Schaufenster danach gedimmt), Gastronomie bis Mitternacht. Leuchtdichten
 * hinter Gardinen 5 bis 40 cd/m², Schaufenster 100 bis 300 cd/m² [G].
 */
public final class Windows {
    private Windows() { }

    public enum Use { HOME, HOLIDAY, HOTEL, SHOP, GASTRO, OFFICE }

    /** Anteil brennender Fenster je volle Stunde 0..23 (bei Dunkelheit). */
    private static final double[][] SHARE = {
            // 0     1     2     3     4     5     6     7     8     9    10    11    12    13    14    15    16    17    18    19    20    21    22    23
            {0.12, 0.06, 0.04, 0.03, 0.03, 0.06, 0.22, 0.30, 0.15, 0.08, 0.06, 0.06, 0.08, 0.08, 0.08, 0.10, 0.18, 0.35, 0.50, 0.58, 0.60, 0.55, 0.40, 0.22},  // HOME
            {0.22, 0.12, 0.06, 0.04, 0.03, 0.03, 0.06, 0.15, 0.20, 0.12, 0.06, 0.04, 0.05, 0.05, 0.05, 0.06, 0.10, 0.25, 0.40, 0.45, 0.52, 0.55, 0.50, 0.35},  // HOLIDAY
            {0.25, 0.15, 0.08, 0.06, 0.06, 0.08, 0.20, 0.28, 0.20, 0.10, 0.08, 0.08, 0.08, 0.10, 0.10, 0.12, 0.18, 0.30, 0.42, 0.50, 0.55, 0.55, 0.48, 0.35},  // HOTEL
            {0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.25, 0.30, 0.60, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.80, 0.40, 0.30, 0.25, 0.25},  // SHOP
            {0.25, 0.10, 0.05, 0.05, 0.05, 0.05, 0.05, 0.10, 0.30, 0.50, 0.60, 0.80, 0.90, 0.90, 0.80, 0.80, 0.85, 0.90, 0.95, 0.95, 0.95, 0.90, 0.75, 0.50},  // GASTRO
            {0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.08, 0.40, 0.80, 0.85, 0.85, 0.85, 0.80, 0.85, 0.85, 0.85, 0.75, 0.50, 0.25, 0.12, 0.08, 0.05, 0.04, 0.03},  // OFFICE
    };

    /** Anteil brennender Fenster um hour (h, stetig zwischen den vollen Stunden). */
    public static double share(Use u, double hour) {
        double h = ((hour % 24) + 24) % 24;
        int a = (int) Math.floor(h), b = (a + 1) % 24;
        double t = h - a;
        return SHARE[u.ordinal()][a] * (1 - t) + SHARE[u.ordinal()][b] * t;
    }

    /** Fester Zufall 0..1 je Haus, Fenster und Salz. */
    static double hash(long house, int window, int salt, long seed) {
        long z = seed * 0x9E3779B97F4A7C15L + house * 0xBF58476D1CE4E5B9L + window * 0x94D049BB133111EBL + salt * 0x2545F4914F6CDD1DL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    /**
     * Brennt Fenster window von house um hour? Jedes Fenster hat eine feste Schwelle und eine eigene Verschiebung von
     * bis zu ±40 min – so gehen die Lichter nacheinander an und aus, nicht alle zur vollen Stunde.
     */
    public static boolean lit(Use u, long house, int window, double hour, long seed) {
        double shift = (hash(house, window, 1, seed) - 0.5) * 1.33;
        return hash(house, window, 0, seed) < share(u, hour + shift);
    }

    /** Leuchtdichte (cd/m², lineares RGB) eines brennenden Fensters: warmes Glühlicht, LED, kaltweiß, flimmernder Bildschirm. */
    public static double[] radiance(Use u, long house, int window, long seed) {
        double k = hash(house, window, 2, seed), l = hash(house, window, 3, seed);
        double T = k < 0.55 ? 2700 : k < 0.85 ? 3000 : 4000;
        double L = u == Use.SHOP ? 100 + 200 * l : u == Use.GASTRO ? 20 + 40 * l : 5 + 35 * l * l;
        double[] c = Spectra.rgbPlanck(T);
        return new double[]{c[0] * L, c[1] * L, c[2] * L};
    }

    /** Wie viele von n Fenstern brennen (für Prüfungen und das HUD). */
    public static int count(Use u, long house, int n, double hour, long seed) {
        int c = 0;
        for (int i = 0; i < n; i++) if (lit(u, house, i, hour, seed)) c++;
        return c;
    }
}
