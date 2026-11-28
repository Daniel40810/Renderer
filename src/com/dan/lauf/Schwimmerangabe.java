package com.dan.lauf;

import java.util.Arrays;

/**
 * Ein Schwimmer aus der Szenendatei (Seegang Werkbuch, S7b-2, Datei 88; SF106 A, SF107 A): welcher Körper
 * ({@code physics.auftrieb.Vorgaben}), wo er liegt und wie er fest ist. Gelesen von {@link Szenen}:
 * <pre>
 * schwimmer.hilligenlei.art  = faehre                          # Vorgabe: faehre, adlerExpress, haukeHaien, krabbenkutter,
 *                                                              # spierentonne, spitztonne, leuchttonne, balken, …
 * schwimmer.hilligenlei.ort  = 471325 6048035                  # UTM 32 (m)
 * schwimmer.hilligenlei.kurs = 205                             # Grad von Nord
 * schwimmer.hilligenlei.fest = anleger 471317 6048017 25       # anleger Ost Nord Richtung | anker Ost Nord Kette | kette Länge | frei
 * schwimmer.hilligenlei.route    = Hooge.route_schluettsiel  # seit S7d: Weg ab dem Anleger (je Zeile Ost Nord)
 * schwimmer.hilligenlei.fahrplan = 10:30 14:00               # seit S7d: Ankunft und Abfahrt am Anleger (UTC, täglich)
 * schwimmer.hilligenlei.fahrplan = tide -2:00 +1:30          # seit S7d2: dasselbe gegen jedes Hochwasser am Pegel (bei Tag)
 * schwimmer.adler.fahrplan   = 13:20 18:30 Europe/Berlin       # seit S7e2: in der Ortszeit
 * schwimmer.adler.route_ab   = Hooge.route_adler_nordstrand   # seit S7e2: eigener Weg der Abfahrt
 * schwimmer.adler.lotse      = fahrt 12.3 plan 11 anlegen 200 hebel 0.55 pegel -9 sturm weg   # seit S7e2: je Schiff
 * schwimmer.tonnen.datei     = Hooge.tonnen                    # viele aus einer Datei: je Zeile art Ost Nord Farbe Name …
 *                                                              # seit S7e2 auch „art Ost Nord Farbe frei Name“ (Treibgut)
 *                                                              # und „trupp Ost Nord Anzahl Halbmesser Name“ (Eiderenten)
 * szenario.seegang.schwimmer = hilligenlei kutter tonnen
 * </pre>
 */
public final class Schwimmerangabe {
    public final String name, art;
    public final double ost, nord, kurs;
    /** Wie er fest ist: das erste Wort (anleger, anker, kette, frei), dann Zahlen. */
    public final String fest;
    public final double[] festZahlen;
    /** Farbe einer Tonne (rot, gruen, gelb; sonst null), Kennung des Feuers (OSM, sonst null), Titel. */
    public String farbe, kennung, titel;
    /**
     * Seit S7d: der Weg ab dem Anleger (je Punkt Ost, Nord; null: keiner) und Ankunft, Abfahrt (s des Tags, UTC); seit S7d2
     * mit nachTide: Ankunft und Abfahrt in s gegen das Hochwasser (vorher negativ).
     */
    public double[][] route;
    public double[] fahrplan;
    public boolean nachTide;
    /**
     * Seit S7e2 (Seegang Werkbuch 101): der Weg der Abfahrt (null: derselbe wie der Ankunft), die Zeitzone des Fahrplans
     * (null: UTC), Werte des Lotsen ({@code fahrt}, {@code plan}, {@code anlegen}, {@code hebel}, {@code pegel}; sonst wie die
     * Fähre) und ob das Schiff bei Sturm nicht da ist (sonst liegt es am Anleger).
     */
    public double[][] routeAb;
    public java.time.ZoneId zone;
    public final java.util.Map<String, Double> lotse = new java.util.HashMap<>();
    public boolean sturmWeg;

    public Schwimmerangabe(String name, String art, double ost, double nord, double kurs, String fest, double[] festZahlen) {
        this.name = name;
        this.art = art;
        this.ost = ost;
        this.nord = nord;
        this.kurs = kurs;
        this.fest = fest;
        this.festZahlen = festZahlen;
    }

    @Override public String toString() { return name + " (" + art + ", " + fest + " " + Arrays.toString(festZahlen) + ")"; }
}
