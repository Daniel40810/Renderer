package com.dan.film;

/**
 * Ein Filmmaterial: drei Schichten (rot-, grün-, blauempfindlich; im Negativ und in der Kopie bilden sie Cyan, Purpur und
 * Gelb), je eine Schwärzungskurve; die Empfindlichkeit der Schichten gegen lineares sRGB als Matrix (wie viel jedes
 * Primärlicht jede Schicht belichtet, Zeilen auf 1); die Nebenabsorption der Farbstoffe (nur die Kopie: die Maske des
 * Negativs gleicht seine aus); die Körnigkeit als RMS-Körnigkeit (σ_D · 1000 bei D = 1,0 über eine Blende von 48 µm, so
 * gibt Kodak sie an) mit dem Anteil je Schicht; der Träger (Dicke, Brechzahl, Dichte eines grau eingefärbten Trägers je
 * Durchgang, ob hinten eine Rückschicht aus Ruß liegt) und die Halation (wie viel Licht vom Träger in die Schichten
 * zurückkommt, je Schicht; die Form des Kerns folgt aus dem Träger, {@link Film#halationProfile}); bei einer Kopie, wie
 * schnell ihre Farbstoffe verblassen (Anteil je Jahrzehnt für Cyan, Purpur, Gelb, bei Zimmerwärme).
 * <p>Die Werte sind nach den Datenblättern und Beschreibungen geformt [G, zu prüfen gegen die Kurven der Datenblätter]:
 * Kennzeichnend sind die Steigung (Negativ γ ≈ 0,6, Kopie γ ≈ 2,6–2,8), der Spielraum (Vision3 rund 14 Blenden, das
 * Farbnegativ der 1950er rund 9), die orange Maske (D_min rot &lt; grün &lt; blau), das Korn und die Halation (Vision3 mit
 * Rückschicht aus Ruß, „remjet“, kaum; die 1950er mit grau eingefärbtem Träger deutlich mehr).
 */
public record Stock(String name, String note, int year, double iso, boolean print, Curve[] curves, double[] matrix, double[] dye,
                    double rms, double[] layerGrain, double baseMm, double baseN, double baseDensity, boolean remjet,
                    double halation, double[] halationRgb, double[] fade) {

    static double[] rows(double... m) {
        double[] r = m.clone();
        for (int i = 0; i < 3; i++) {
            double s = r[3 * i] + r[3 * i + 1] + r[3 * i + 2];
            for (int j = 0; j < 3; j++) r[3 * i + j] /= s;
        }
        return r;
    }

    static final double[] ID = {1, 0, 0, 0, 1, 0, 0, 0, 1}, NO_FADE = {0, 0, 0};

    /**
     * Kodak Vision3 250D 5207 (2007): Tageslicht, ISO 250, Spielraum rund 14 Blenden, feines Korn; hinten die Rückschicht
     * aus Ruß („remjet“, im Labor abgewaschen): sie schluckt, was durch den Träger kommt, es gibt keine Totalreflexion und
     * keinen Ring, nur einen schwachen, breiten Schein.
     */
    public static Stock vision3_250D() {
        return new Stock("Kodak Vision3 250D", "Farbnegativ 5207, Tageslicht", 2007, 250, false,
                new Curve[]{new Curve(0.22, 0.58, -2.35, 0.25, 2.15, 0.35), new Curve(0.60, 0.60, -2.30, 0.25, 2.10, 0.35), new Curve(0.82, 0.62, -2.25, 0.25, 2.05, 0.35)},
                rows(0.92, 0.07, 0.01, 0.05, 0.88, 0.07, 0.01, 0.08, 0.91), ID, 7, new double[]{0.85, 1.0, 1.25}, 0.127, 1.48, 0, true, 0.004, new double[]{1, 0.30, 0.05}, NO_FADE);
    }

    /** Kodak Vision3 500T 5219 (2007): Kunstlicht, ISO 500, für Abend und Nacht, etwas gröber. */
    public static Stock vision3_500T() {
        return new Stock("Kodak Vision3 500T", "Farbnegativ 5219, Kunstlicht (mit Filter 85 am Tag)", 2007, 500, false,
                new Curve[]{new Curve(0.25, 0.62, -2.25, 0.25, 2.00, 0.35), new Curve(0.68, 0.63, -2.20, 0.25, 1.95, 0.35), new Curve(0.92, 0.65, -2.15, 0.25, 1.90, 0.35)},
                rows(0.91, 0.08, 0.01, 0.06, 0.86, 0.08, 0.01, 0.09, 0.90), ID, 9, new double[]{0.85, 1.0, 1.25}, 0.127, 1.48, 0, true, 0.005, new double[]{1, 0.30, 0.05}, NO_FADE);
    }

    /**
     * Eastman Color Negative 5248 (1953–1959): das Farbnegativ, auf dem Hollywood die CinemaScope-Filme jener Jahre drehte;
     * ASA 25 bei Kunstlicht. Kürzerer Spielraum (rund 9 Blenden), weichere Schulter, mehr Überlappung der Schichten
     * (weniger reine Farben), deutlich gröberes Korn; gegen die Halation ein grau eingefärbter Träger (Dichte 0,25 je
     * Durchgang [G, zu prüfen: ob 5248 schon eine Rückschicht hatte]), der den Ring dämpft, aber nicht löscht.
     */
    public static Stock eastmanColor5248() {
        return new Stock("Eastman Color Negative 5248", "Farbnegativ der 1950er, ASA 25 Kunstlicht", 1953, 25, false,
                new Curve[]{new Curve(0.20, 0.65, -1.65, 0.32, 1.25, 0.45), new Curve(0.55, 0.66, -1.60, 0.32, 1.20, 0.45), new Curve(0.78, 0.68, -1.55, 0.32, 1.15, 0.45)},
                rows(0.84, 0.14, 0.02, 0.10, 0.80, 0.10, 0.03, 0.15, 0.82), ID, 14, new double[]{0.90, 1.0, 1.30}, 0.14, 1.48, 0.25, false, 0.018, new double[]{1, 0.40, 0.12}, NO_FADE);
    }

    /** Kodak Vision Color Print 2383: die Kopie für das Kino heute, γ um 2,7, D_max rund 4; ihre Farbstoffe halten lange [G]. */
    public static Stock print2383() {
        return new Stock("Kodak Vision Print 2383", "Positiv für die Projektion", 1998, 0, true,
                new Curve[]{new Curve(0.06, 2.79, -0.90, 0.18, 0.60, 0.10), new Curve(0.06, 2.70, -0.90, 0.18, 0.54, 0.10), new Curve(0.07, 2.61, -0.90, 0.18, 0.49, 0.10)},
                ID, new double[]{1, 0.06, 0.02, 0.07, 1, 0.05, 0.02, 0.10, 1}, 3, new double[]{1, 1, 1}, 0.12, 1.65, 0, false, 0, new double[]{0, 0, 0}, new double[]{0.010, 0.004, 0.010});
    }

    /**
     * Eastman Color Print 5382 (1950er): weicher, D_max um 3,3, Farbstoffe mit mehr Nebenabsorption [G]. Ihr Gelb und ihr
     * Cyan verblassen schnell (die Kopien jener Jahre sind heute rosa); je Jahrzehnt 12 % Gelb, 10 % Cyan, 3 % Purpur [G,
     * nach den Messungen der Archive geformt, zu prüfen].
     */
    public static Stock print5382() {
        return new Stock("Eastman Color Print 5382", "Positiv der 1950er", 1953, 0, true,
                new Curve[]{new Curve(0.08, 2.52, -0.88, 0.20, 0.43, 0.15), new Curve(0.08, 2.50, -0.88, 0.20, 0.41, 0.15), new Curve(0.10, 2.47, -0.88, 0.20, 0.36, 0.15)},
                ID, new double[]{1, 0.12, 0.04, 0.12, 1, 0.10, 0.04, 0.16, 1}, 4, new double[]{1, 1, 1}, 0.14, 1.48, 0, false, 0, new double[]{0, 0, 0}, new double[]{0.10, 0.03, 0.12});
    }

    /** Halbmesser des Halationsrings (mm): Licht, das im Träger über den Grenzwinkel läuft, kommt nach 2·t·tan θ_c zurück. */
    public double halationRadiusMm() { return 2 * baseMm * Math.tan(Math.asin(1 / baseN)); }

    @Override public String toString() { return name; }
}
