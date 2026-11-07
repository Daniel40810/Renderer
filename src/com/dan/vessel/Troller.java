package com.dan.vessel;

/**
 * Bauplan eines hölzernen Trollers aus Südostalaska (1940er/50er) für den Bootsbauer {@link Shipwright}: Rumpf (aus
 * {@link Hull}, dieselbe Form wie für die Physik), Anstrich, Aufbauten, Rigg, Fanggeschirr und Abnutzung. Seit
 * LituyaBay1958 W8a.
 * <p>
 * <b>Vorbild</b> (gestaltet [G] nach Fotos von Trollern aus Pelican, Sitka und Juneau, dazu den Vorlagen
 * {@code templates/Fischkutter*.jpg} in LituyaBay1958): ein Kraweel-Rumpf mit kräftigem Sprung und senkrechtem,
 * leicht gerundetem Steven; weißes Freibord über schwarzem Wasserpass und kupferrotem Unterwasserschiff, farbiger
 * Schergang unter dem Scheuerleisten; ein Steuerhaus mittschiffs mit drei Frontscheiben und einem niedrigen Aufbau
 * davor (Bullaugen in Messing); dahinter Fischraumluke und hinten die Trollgrube mit den Trollwinden (Gurdies);
 * ein Mast vor dem Steuerhaus mit Saling, Ladebaum, Topp- und Ankerlicht; zwei lange Trollstangen am Mastfuß, vor
 * Anker abgesenkt mit Schlingerdämpfern („flopper stoppers“) im Wasser; achtern ein kurzer Besan mit Stützsegel
 * („riding sail“), das das Boot vor Anker in den Wind stellt. Ankerwinde und Bugrolle vorn, Name an Bug und Spiegel.
 * <p>
 * Nur Zahlen und Farben, keine Grafik: Der Kern braucht Java 21. Farben linear (nicht sRGB).
 */
public final class Troller {
    /** Name am Bug und am Spiegel (Großbuchstaben), Heimathafen am Spiegel. */
    public String name, homeport = "PELICAN, ALASKA";
    /** Rumpf (dieselbe Form wie für den Starrkörper). */
    public final Hull hull;

    // ------------------------------------------------------------------ Anstrich (linear)
    /** Freibord, Schergang (Streifen unter der Scheuerleiste), Wasserpass, Unterwasserschiff. */
    public double[] topsides = {0.70, 0.70, 0.66}, sheerStrake = {0.045, 0.16, 0.10}, bootTop = {0.025, 0.025, 0.024}, bottom = {0.30, 0.075, 0.040};
    /** Steuerhaus und Aufbau, ihre Kanten und Fensterrahmen, Dach, Deck (geölt, vergraut). */
    public double[] house = {0.72, 0.72, 0.68}, houseTrim = {0.045, 0.16, 0.10}, roof = {0.42, 0.40, 0.33}, deck = {0.26, 0.22, 0.17};
    /** Lackiertes Holz (Schandeck, Luken, Stangen), Mast (gestrichen). */
    public double[] varnish = {0.32, 0.17, 0.07}, mast = {0.60, 0.56, 0.45};
    /** Höhe des Schergangs (m), des Wasserpasses über und unter der Konstruktionswasserlinie (m). */
    public double sheerStrakeHeight = 0.24, bootTopAbove = 0.13, bootTopBelow = 0.05;

    // ------------------------------------------------------------------ Zustand
    /** Abnutzung 0 … 1: abgeplatzte Farbe an Kanten und am Wasserpass, Scheuerstellen. */
    public double wear = 0.45;
    /** Rostfahnen unter Beschlägen und Speigatten 0 … 1. */
    public double rust = 0.35;
    /** Bewuchs am Wasserpass 0 … 1 (grüner Saum, Schleim). */
    public double growth = 0.4;
    /** Zufall für die Abnutzung (gleicher Wert, gleiches Boot). */
    public long seed = 1958;

    // ------------------------------------------------------------------ Aufbauten
    /** Niedriger Aufbau vor dem Steuerhaus (Logis) bis s = this · loa, Höhe über Deck (m). */
    public double trunkFore = 0.36, trunkHeight = 0.62;
    /** Neigung der Frontscheiben nach hinten (Grad), Einzug der Seitenwände (Grad). */
    public double windshieldRake = 12, houseTumblehome = 4;
    /** Höhe der Verschanzung über Deck (m). */
    public double bulwark = 0.30;
    /** Fischraumluke: Mitte (Anteil von loa), Kantenlänge (m). */
    public double hatchS = -0.12, hatchSize = 1.15;

    // ------------------------------------------------------------------ Rigg und Fanggeschirr
    /** Trollstangen: Winkel aus der Senkrechten (Grad, 0 = am Mast aufgeholt, 50 … 70 = zum Fischen ausgelegt). */
    public double poleAngle = 58;
    /** Schlingerdämpfer an den Stangen (vor Anker): Tiefe unter Wasser (m); 0 = ohne. */
    public double flopperDepth = 3.2;
    /** Besan mit Stützsegel. */
    public boolean ridingSail = true;
    /** Ankerlicht an (vor Anker in der Dämmerung) und Positionslichter (in Fahrt). */
    public boolean anchorLight = true, runningLights = false;
    /** Wie hell die Lichter sind (Strahldichte der Linse, linear, vor der Belichtung). */
    public double lampRadiance = 40;

    public Troller(String name, Hull hull) {
        this.name = name;
        this.hull = hull;
    }

    /** Konstruktionswasserlinie (m über der Kielunterkante). */
    public double waterline() { return hull.draft; }

    private static double[] c(double r, double g, double b) { return new double[]{r, g, b}; }

    /** Edrie (Howard Ulrich): weiß mit grünem Schergang, vergraut, viel gefahren [G]. */
    public static Troller edrie(Hull h) {
        Troller t = new Troller("EDRIE", h);
        t.seed = 1;
        return t;
    }

    /** Badger (Bill und Vivian Swanson): weiß mit dunkelblauem Schergang und rotem Dach [G]. */
    public static Troller badger(Hull h) {
        Troller t = new Troller("BADGER", h);
        t.sheerStrake = c(0.02, 0.05, 0.16);
        t.houseTrim = c(0.02, 0.05, 0.16);
        t.roof = c(0.30, 0.07, 0.05);
        t.wear = 0.30;
        t.rust = 0.25;
        t.seed = 2;
        t.homeport = "JUNEAU, ALASKA";
        return t;
    }

    /** Sunmore (Orville und Mickey Wagner): hellgrau mit rotbraunem Schergang, älter [G]. */
    public static Troller sunmore(Hull h) {
        Troller t = new Troller("SUNMORE", h);
        t.topsides = c(0.52, 0.54, 0.53);
        t.house = c(0.62, 0.62, 0.58);
        t.sheerStrake = c(0.20, 0.05, 0.03);
        t.houseTrim = c(0.20, 0.05, 0.03);
        t.wear = 0.6;
        t.rust = 0.5;
        t.growth = 0.55;
        t.seed = 3;
        t.homeport = "SITKA, ALASKA";
        return t;
    }

    /** Der Bauplan zu einem der drei Boote von 1958 (nach dem Namen des Rumpfs), sonst die Edrie-Vorgabe. */
    public static Troller fuer(Hull h) {
        String n = h.name == null ? "" : h.name.toLowerCase(java.util.Locale.ROOT);
        Troller t = n.startsWith("badger") ? badger(h) : n.startsWith("sunmore") ? sunmore(h) : edrie(h);
        if (!n.startsWith("edrie") && !n.startsWith("badger") && !n.startsWith("sunmore") && h.name != null) t.name = h.name.toUpperCase(java.util.Locale.ROOT);
        return t;
    }
}
