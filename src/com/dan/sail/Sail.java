package com.dan.sail;

import java.util.List;
import java.util.Locale;

/**
 * Segel und Segelboote (Werkbuch Timmendorf, Abschnitte 6 und 7; Hülle seit Phase 1, Physik seit Phase 7). Ein
 * Geschwindigkeitsvorhersageprogramm (VPP) mit drei Gleichgewichten und dieselbe Physik als Bewegung über die Zeit:
 * <ul>
 * <li><b>Aerodynamik:</b> Segel als Tragflügel im scheinbaren Wind. Auftrieb und Formwiderstand über dem scheinbaren
 * Windwinkel β aus Tabellen für Groß mit Fock und für Gennaker oder Spinnaker [G nach dem Verlauf der Beiwerte von
 * Hazen 1980 und ORC], induzierter Widerstand C_L²/(π·AR) mit dem Seitenverhältnis über dem Spiegelbild der
 * Wasseroberfläche. Krängung dreht den Wind in die Segelebene (β_eff = atan(tan β · cos φ), V² · (cos²β + sin²β · cos²φ)).
 * Entlasten über die Flachheit (flat, 0,2 bis 1): weniger Auftrieb, quadratisch weniger induzierter Widerstand.</li>
 * <li><b>Hydrodynamik:</b> Reibung nach ITTC 1957 mit Formfaktor, Wellenwiderstand als Anteil des Gewichts über der
 * Froude-Zahl (x = Fn / 0,4: w · x⁶ / (1 + p · x⁶), p > 0 lässt Jollen und Kats gleiten) [G, je Klasse an
 * Literaturwerte angepasst], Kiel oder Schwert als Flügel (Abdrift aus der nötigen Querkraft, C_Lα nach Helmbold,
 * induzierter Widerstand), Zuschlag für Krängung.</li>
 * <li><b>Gleichgewicht:</b> Vortrieb = Widerstand, Querkraft = Seitenkraft von Kiel und Schwert, krängendes Moment =
 * aufrichtendes Moment aus Rumpf (W · GM · sin φ, beim Kat höchstens W · B/2) und Crew auf der Kante. Wo das Moment
 * nicht reicht, wird entlastet; über der größten Krängung der Klasse fährt das Boot nicht.</li>
 * <li><b>Polaren:</b> {@link #polar} sucht je wahrem Windwinkel die schnellste Kombination aus Flachheit und
 * Vorwindsegel, {@link #vmg} die besten Kreuz- und Vorwindwinkel.</li>
 * <li><b>Bewegung:</b> {@link #step} rechnet Fahrt (Masse mit mitbewegtem Wasser), Krängung (Trägheit, Dämpfung),
 * Abdrift, Kurs (Rudergänger mit Drehrate) und Trimm (alle 0,5 s nachgeführt) über die Zeit.</li>
 * <li><b>Seit Phase 8:</b> Segelpläne als Datensatz ({@link Plan}: Hochtakelung, Gaffel, Rahsegel, Kite) mit eigenen
 * Beiwerten; die Flachheit wird stetig gesucht (statt in Stufen); die Krängung hat ein Vorzeichen und läuft bei der Wende
 * durch null; der Baum schwingt bei der Halse über (Ereignis {@code onGybe}, mit Stoß in die Krängung); Jollen und
 * Kats kentern über ihrem Grenzwinkel und werden nach einer Weile aufgerichtet ({@code onCapsize}, {@code onRight});
 * Schoten fieren ({@link Boat#ease}) und Können der Crew ({@link Boat#skill}). Die Regatta steht in {@link Regatta}.</li>
 * </ul>
 * Winkel in Grad, Richtungen als Kompasskurs (0 Nord, 90 Ost); der Wind wird angegeben, woher er kommt. Raum der
 * Szenen: x Ost, y oben, z Süd. Der Kern kennt nur Java 21; Wind, Wasser und Tuchform kommen über {@link Wind},
 * {@link Water} und {@link Cloth}.
 */
public final class Sail {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7): Feinheit der Polaren und Unterschritte. */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Wind am Ort: Stärke (m/s) und Richtung, aus der er kommt (Grad). */
    public interface Wind {
        double speed(double x, double z, double t);
        double fromDeg(double x, double z, double t);
    }

    /** Wasser am Ort: Höhe der Oberfläche (m) und Strömung (m/s je Achse). */
    public interface Water {
        double height(double x, double z, double t);
        default void current(double x, double z, double t, double[] out) { out[0] = 0; out[1] = 0; }
    }

    /** Form des Segels aus {@code cloth} (F10 A): Profiltiefe (Anteil der Sehne) und Verwindung (Grad). */
    public interface Cloth {
        double camber();
        double twistDeg();
    }

    /** Ereignisse nach außen (Klang, Regie). */
    public interface Events {
        default void onTack(Boat b) { }
        default void onLuff(Boat b, double strength) { }
        default void onCapsize(Boat b) { }
        default void onStart(double t) { }
        /** Seit Phase 8: der Baum ist übergegangen; Heftigkeit 0..1 (1: Patenthalse bei viel Wind). */
        default void onGybe(Boat b, double violence) { }
        /** Seit Phase 8: das gekenterte Boot ist wieder aufgerichtet. */
        default void onRight(Boat b) { }
    }

    /**
     * Seit Phase 8: der Segelplan. Hochtakelung (Groß und Fock, Gennaker), Gaffel (Schoner), Rahsegel (Passat, mit
     * Stagsegeln und Besan), Kite (Schirm an Leinen, der Fahrer hält ihn am Trapezhaken).
     */
    public enum Plan { BERMUDA, GAFF, SQUARE, KITE }

    /** Bauart des Rumpfs. */
    public enum Kind { DINGHY, KEELBOAT, CATAMARAN, SHIP, KITE }

    /** Knoten in m/s. */
    public static final double KN = 1852.0 / 3600.0;
    public static final double G = 9.81;

    /**
     * Eine Takelage als Datensatz (Klassenmaße; Formbeiwerte [G], an Literaturwerte angepasst, siehe SailCheck):
     * <ul>
     * <li>loa, lwl, beam: Länge über alles, Wasserlinie, Breite (m); mass: Boot ohne Crew (kg); crew, crewMass: Personen
     * und Masse je Person (kg);</li>
     * <li>mainArea, jibArea, downwindArea: Groß, Fock, Gennaker oder Spinnaker (m²); ceHeight: Segeldruckpunkt über
     * Wasser (m); mastHeight: Masttopp über Wasser (m);</li>
     * <li>finArea, finDepth: Fläche und Tiefe von Kiel oder Schwert mit Ruder (m², m); wetted: benetzte Fläche (m²);
     * gm: metazentrische Höhe beladen (m); hikeArm: Hebel der Crew auf der Kante oder im Trapez (m);
     * maxHeelDeg: größte gesegelte Krängung;</li>
     * <li>wave, planing: Wellenwiderstand (Anteil des Gewichts bei Fn 0,4) und Gleiten; formFactor: Zuschlag zur Reibung;
     * turnRate: Drehrate beim Wenden (Grad/s).</li>
     * </ul>
     */
    public record Rig(String name, Kind kind, double loa, double lwl, double beam, double mass, int crew, double crewMass,
                      double mainArea, double jibArea, double downwindArea, double ceHeight, double mastHeight,
                      double finArea, double finDepth, double wetted, double gm, double hikeArm, double maxHeelDeg,
                      double wave, double planing, double formFactor, double turnRate, Plan plan) {
        /** Wie bisher, mit Hochtakelung (Phase 7). */
        public Rig(String name, Kind kind, double loa, double lwl, double beam, double mass, int crew, double crewMass,
                   double mainArea, double jibArea, double downwindArea, double ceHeight, double mastHeight,
                   double finArea, double finDepth, double wetted, double gm, double hikeArm, double maxHeelDeg,
                   double wave, double planing, double formFactor, double turnRate) {
            this(name, kind, loa, lwl, beam, mass, crew, crewMass, mainArea, jibArea, downwindArea, ceHeight, mastHeight, finArea, finDepth,
                    wetted, gm, hikeArm, maxHeelDeg, wave, planing, formFactor, turnRate, Plan.BERMUDA);
        }
        /** Segelfläche am Wind (m²). */
        public double sailArea() { return mainArea + jibArea; }
        /** Masse mit Crew (kg). */
        public double total() { return mass + crew * crewMass; }
        /** Hat einen Gennaker oder Spinnaker. */
        public boolean downwindSail() { return downwindArea > 0; }
        /** Seit Phase 8: kann kentern (Jollen und Kats); Kielboote und Schiffe legen sich nur auf die Seite. */
        public boolean canCapsize() { return kind == Kind.DINGHY || kind == Kind.CATAMARAN; }
        /** Seit Phase 8: Krängung, ab der das Boot kentert (Grad) [G]: Jolle 55°, Kat 35°. */
        public double capsizeDeg() { return kind == Kind.CATAMARAN ? 35 : kind == Kind.DINGHY ? 55 : 90; }
        /** Seit Phase 8: Zeit, bis die Crew ein gekentertes Boot aufgerichtet hat (s) [G]. */
        public double rightingTime() { return kind == Kind.CATAMARAN ? 75 : 12 + 6 * crew + 2 * loa; }
    }

    // Klassenmaße nach den Klassenvorschriften und Herstellerangaben; Formbeiwerte [G] (SailCheck vergleicht die Polaren)
    public static final Rig OPTIMIST = new Rig("Optimist", Kind.DINGHY, 2.31, 2.0, 1.13, 35, 1, 42, 3.3, 0, 0, 1.45, 2.6,
            0.16, 0.75, 2.1, 0.25, 0.42, 14, 0.1455, 1.0, 0.25, 40);
    public static final Rig ILCA = new Rig("ILCA 7", Kind.DINGHY, 4.23, 3.8, 1.37, 59, 1, 80, 7.06, 0, 0, 2.2, 6.4,
            0.32, 1.0, 3.5, 0.2, 0.95, 14, 0.1164, 1.0, 0.12, 35);
    public static final Rig DINGHY_420 = new Rig("420er", Kind.DINGHY, 4.20, 4.0, 1.71, 80, 2, 60, 7.45, 2.8, 9, 2.35, 6.3,
            0.35, 1.05, 4.2, 0.25, 1.25, 14, 0.0931, 0.5, 0.12, 35);
    public static final Rig HOBIE = new Rig("Hobie 16", Kind.CATAMARAN, 5.04, 4.9, 2.41, 145, 2, 75, 15.0, 6.0, 0, 3.0, 8.4,
            0.45, 0.6, 3.6, 12, 1.4, 6, 0.0244, 0.25, 0.25, 25);
    public static final Rig J70 = new Rig("J/70", Kind.KEELBOAT, 6.93, 6.2, 2.25, 794, 4, 80, 13.1, 9.6, 56, 3.9, 9.5,
            0.95, 1.45, 10.5, 0.85, 0.95, 24, 0.0299, 0.1, 0.1, 18);
    public static final Rig CRUISER = new Rig("Fahrtenyacht 34 ft", Kind.KEELBOAT, 10.4, 9.0, 3.4, 5200, 4, 80, 30, 25, 0, 6.5, 14.5,
            2.4, 1.9, 26, 1.1, 1.2, 26, 0.0195, 0.0, 0.15, 10);
    /** Gaffelschoner (Traditionssegler) [G nach Schonern der Travemünder Woche, zu prüfen]: Gaffelgroß, Schonersegel, Vorsegel. */
    public static final Rig SCHOONER = new Rig("Gaffelschoner", Kind.SHIP, 30, 24, 6.5, 120_000, 12, 80, 210, 90, 0, 10, 28,
            30, 3.2, 210, 1.2, 0, 22, 0.03, 0.0, 0.2, 4, Plan.GAFF);
    /**
     * Viermastbark Passat (Blohm &amp; Voss 1911): Länge über alles 115,5 m, Rumpf 96 m in der Wasserlinie, Breite 14,4 m,
     * Wasserverdrängung rund 4 700 t beladen [zu prüfen], Segelfläche rund 4 100 m² (Rahsegel an drei Masten, Besan mit
     * Gaffel), Masttopp 56 m über Wasser. Rahsegel tragen nicht höher als etwa 60° am wahren Wind.
     */
    public static final Rig PASSAT = new Rig("Viermastbark Passat", Kind.SHIP, 115.5, 96, 14.4, 4_700_000, 30, 80, 4100, 0, 0, 22, 56,
            700, 8.5, 3500, 1.4, 0, 15, 0.03, 0.0, 0.2, 1.5, Plan.SQUARE);
    /**
     * Kite 12 m² mit Board (Twintip 1,4 m, Finnen): der Schirm fliegt an 22 m Leinen; die Kraft greift am Trapezhaken an,
     * der Fahrer hält sie mit seinem Gewicht (Hebel {@code hikeArm}) [G].
     */
    public static final Rig KITE = new Rig("Kite 12 m²", Kind.KITE, 1.4, 1.3, 0.42, 8, 1, 80, 12, 0, 0, 1.0, 22,
            0.25, 0.30, 0.6, 0.1, 1.15, 40, 0.10, 1.0, 1.0, 60, Plan.KITE);
    /**
     * Seit Timmendorf Phase 18 (Rückfluss nach Haithabu): Langschiff nach Haithabu 1 (um 985) mit einem Rahsegel 11 × 8,6 m,
     * 30,9 × 2,7 m, Tiefgang 0,85 m, rund 26 t mit 60 Mann. Kiel und Seitenruder geben wenig Lateralfläche, die Crew sitzt
     * in Luv. Maße wie in {@code com.dan.longship.ShipType}, Beiwerte [G, zu prüfen: Nachbauten Havhingsten und Saga Oseberg
     * laufen raumschots 10 bis 12 kn und kommen nicht höher als rund 65° an den wahren Wind].
     */
    public static final Rig LONGSHIP = new Rig("Langschiff (Haithabu 1)", Kind.SHIP, 30.9, 28.5, 2.7, 21_200, 60, 80, 94.6, 0, 0, 7.2, 12.5,
            9.0, 0.85, 80, 0.55, 0.3, 15, 0.02, 0.0, 0.15, 3, Plan.SQUARE);
    /**
     * Seit Timmendorf Phase 18: Knorr nach Haithabu 3 (um 1023), Handelsschiff 22 × 6 m, Rahsegel 12 × 10,5 m, rund 60 t
     * beladen mit 8 Mann [G, zu prüfen: Nachbauten wie Ottar laufen rund 8 kn raumschots].
     */
    public static final Rig KNARR = new Rig("Knorr (Haithabu 3)", Kind.SHIP, 22.0, 20.0, 6.0, 59_360, 8, 80, 126, 0, 0, 8.25, 14.5,
            6.5, 1.45, 108, 1.2, 0, 15, 0.03, 0.0, 0.2, 2, Plan.SQUARE);
    public static final List<Rig> RIGS = List.of(OPTIMIST, ILCA, DINGHY_420, HOBIE, J70, CRUISER, SCHOONER, PASSAT, KITE);
    /** Seit Timmendorf Phase 18: die Rahsegler der Wikingerzeit (nicht in {@link #RIGS}, damit Listen der Klassen bleiben). */
    public static final List<Rig> VIKING = List.of(LONGSHIP, KNARR);

    /** Ein Boot: Takelage, Lage (x, z), Kurs, Fahrt, Krängung, Abdrift, Trimm; der Rudergänger hält {@link #targetHeadingDeg}. */
    public static final class Boat {
        public final Rig rig;
        /** Krängung als Betrag (Grad, nach Lee; wie Phase 7) und Schotwinkel (Grad, Betrag). */
        public double x, z, headingDeg, speed, heelDeg, sheetDeg = 20;
        /** Kurs, den der Rudergänger steuert (NaN = Kurs halten). */
        public double targetHeadingDeg = Double.NaN;
        /** Abdrift (Grad, nach Lee), Flachheit des Segels, Vorwindsegel gesetzt. */
        public double leewayDeg, flat = 1;
        public boolean downwind;
        /** Seit Timmendorf Phase 9: darf die Crew den Gennaker setzen (die Regatta verbietet ihn vor dem Start)? */
        public boolean downwindAllowed = true;
        /**
         * Seit Phase 8: Krängung mit Vorzeichen (Grad, + Steuerbord unten) und ihre Geschwindigkeit (Grad/s); Baum mit
         * Vorzeichen (Grad, + nach Steuerbord) und seine Drehgeschwindigkeit (Grad/s).
         */
        public double rollDeg, rollRate, boomDeg = -20, boomRate;
        /** Seit Phase 8: Schoten gefiert (0 dicht … 1 los, das Segel killt), Können der Crew (0,5 … 1). */
        public double ease, skill = 1;
        /** Seit Phase 8: wie weit die Crew gerade aus Reflex fiert (0..1), wenn das Boot zu weit krängt. */
        public double reflex;
        /** Seit Phase 8: gekentert (liegt auf der Seite), wird aufgerichtet; Restzeit (s). */
        public boolean capsized, righting;
        public double capsizeTimer;
        /** Seit Phase 8: Segel festgemacht (Schiff am Kai, Boot vor Anker): kein Vortrieb, das Rigg zeigt gepackte Segel. */
        public boolean furled;
        /** Seit Phase 8: Farben von Rumpf und Segel (linear, null = Vorgabe von {@link BoatShape}) und Segelnummer. */
        public float[] hullColor, sailColor;
        public String label = "";
        /** Tuchform aus {@code cloth} (F10 A, nahe der Kamera), sonst null. */
        public Cloth cloth;
        /** Letzte Kräfte: Vortrieb, Querkraft (N), krängendes Moment (Nm), scheinbarer Wind (m/s), β (Grad). */
        public final double[] last = new double[5];
        double trimTimer, lastTackSide, lastBoomSide = -1;
        boolean boomSet;
        public Boat(Rig rig) { this.rig = rig; }
        /** Seite, von der der Wind kommt: +1 Steuerbord, −1 Backbord (aus dem letzten Schritt). */
        public double windSide() { return lastTackSide == 0 ? 1 : Math.signum(lastTackSide); }
        /** Zustand in Worten (HUD). */
        public String state() { return capsized ? (righting ? "wird aufgerichtet" : "gekentert") : furled ? "Segel fest" : ease > 0.6 ? "Schoten los" : ""; }
    }

    /** Startwert des Zufalls (Punkt 6). */
    public long seed = 1;
    public Quality quality = Quality.HIGH;
    /** Luftdichte (kg/m³), Dichte und Zähigkeit des Brackwassers der Lübecker Bucht (kg/m³, m²/s) [G, zu prüfen]. */
    public double airDensity = 1.225, waterDensity = 1012, viscosity = 1.19e-6;
    /** Wind als offener Wert, bis die App {@link #wind} setzt. */
    public double windSpeed = 6, windFromDeg = 270;
    public Wind wind;
    public Water water;
    public Events events = new Events() { };
    private double time;

    // ------------------------------------------------------------------ Presets (Punkt 5)

    public static Sail calm() { Sail s = new Sail(); s.windSpeed = 2; return s; }
    public static Sail breeze() { Sail s = new Sail(); s.windSpeed = 6; return s; }
    public static Sail fresh() { Sail s = new Sail(); s.windSpeed = 10; return s; }
    /** Sommertag zur Travemünder Woche: Seewind aus Nordost [G]. */
    public static Sail travemuenderWoche() { Sail s = new Sail(); s.windSpeed = 5.5; s.windFromDeg = 45; return s; }

    double windSpeed(double x, double z, double t) { return wind != null ? wind.speed(x, z, t) : windSpeed; }
    double windFrom(double x, double z, double t) { return wind != null ? wind.fromDeg(x, z, t) : windFromDeg; }

    // ------------------------------------------------------------------ Wind und Beiwerte

    /**
     * Scheinbarer Wind: V_A = V_W − V_B. Gibt {Stärke m/s, Winkel β_A zum Bug in Grad (0 von vorn, 180 von achtern),
     * Seite +1 Steuerbord / −1 Backbord}.
     */
    public static double[] apparentWind(double trueSpeed, double trueFromDeg, double boatSpeed, double headingDeg) {
        double tw = Math.toRadians(trueFromDeg), hd = Math.toRadians(headingDeg);
        double wx = -Math.sin(tw) * trueSpeed, wy = -Math.cos(tw) * trueSpeed;
        double bx = Math.sin(hd) * boatSpeed, by = Math.cos(hd) * boatSpeed;
        double ax = wx - bx, ay = wy - by, s = Math.hypot(ax, ay);
        if (s < 1e-9) return new double[]{0, 0, 1};
        double from = Math.toDegrees(Math.atan2(-ax, -ay));
        double rel = ((from - headingDeg) % 360 + 540) % 360 - 180;
        return new double[]{s, Math.abs(rel), rel >= 0 ? 1 : -1};
    }

    /** Beiwerte für Groß mit Fock (oder nur Groß) über β (Grad) [G nach dem Verlauf bei Hazen 1980]. */
    static final double[] UP_B = {0, 15, 22, 28, 40, 60, 90, 120, 150, 180},
            UP_CL = {0, 0.55, 1.15, 1.38, 1.48, 1.42, 1.22, 0.90, 0.45, 0},
            UP_CD = {0.05, 0.03, 0.04, 0.06, 0.12, 0.30, 0.62, 0.92, 1.12, 1.20};
    /** Beiwerte für Gennaker oder Spinnaker über β [G]. */
    static final double[] DN_B = {40, 55, 75, 90, 120, 150, 180},
            DN_CL = {0.3, 1.1, 1.45, 1.5, 1.2, 0.6, 0},
            DN_CD = {0.20, 0.25, 0.35, 0.50, 0.85, 1.05, 1.15};
    /**
     * Seit Phase 8: Gaffelsegel (Schoner): weniger Auftrieb hoch am Wind, die Gaffel verwindet [G nach Marchaj 1979,
     * Gaffel gegen Hochtakelung].
     */
    static final double[] GAFF_B = {0, 20, 27, 34, 45, 60, 90, 120, 150, 180},
            GAFF_CL = {0, 0.45, 0.95, 1.22, 1.36, 1.36, 1.20, 0.92, 0.48, 0},
            GAFF_CD = {0.06, 0.05, 0.07, 0.10, 0.16, 0.32, 0.64, 0.95, 1.14, 1.22};
    /**
     * Seit Phase 8: Rahsegel mit Stagsegeln und Besan (Viermastbark): die Rahen lassen sich nur bis etwa 30° an die
     * Längsachse brassen, darum trägt das Rigg erst ab β ≈ 35° und am besten von achterlich-quer [G nach Marchaj 1979 und
     * den Reiseberichten der Flying P-Liner: am Wind nicht höher als 6 Strich (67°)].
     */
    static final double[] SQ_B = {0, 25, 35, 45, 60, 75, 90, 110, 130, 150, 180},
            SQ_CL = {0, 0.05, 0.30, 0.62, 0.92, 1.08, 1.12, 1.04, 0.80, 0.45, 0},
            SQ_CD = {0.10, 0.12, 0.16, 0.22, 0.34, 0.52, 0.75, 0.98, 1.12, 1.22, 1.28};
    /**
     * Seit Phase 8: Kite (Tubekite 12 m²) im Flug: Gleitzahl rund 5, der Schirm fliegt im Windfenster quer und holt sich
     * so mehr Wind, als am Fahrer weht (Faktor {@link #KITE_POWER} auf den Staudruck) [G nach Loyd 1980 und Dadd et al. 2011].
     * Tief raum geht ihm die Querfahrt aus.
     */
    static final double[] KITE_B = {0, 12, 22, 32, 60, 90, 120, 140, 160, 180},
            KITE_CL = {0, 0.45, 0.85, 0.95, 0.95, 0.92, 0.75, 0.55, 0.38, 0.30},
            KITE_CD = {0.30, 0.15, 0.16, 0.17, 0.18, 0.19, 0.24, 0.32, 0.40, 0.45};
    /**
     * Kraft eines fliegenden Kites gegen den geparkten (Staudruck) [G]: quer zum Wind holt sich der Schirm im Flug den
     * Faktor, achterlich kaum noch (dann zieht er wie ein Spinnaker).
     */
    public static final double KITE_POWER = 1.35;

    /** Faktor auf den Staudruck des Kites über β (Grad). */
    public static double kitePower(double betaDeg) {
        double f = Math.max(0, Math.min(1, (165 - Math.abs(betaDeg)) / 75));
        return 1 + (KITE_POWER - 1) * f;
    }

    static double interp(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) return ys[0];
        for (int i = 1; i < xs.length; i++)
            if (x <= xs[i]) {
                double f = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + f * (ys[i] - ys[i - 1]);
            }
        return ys[ys.length - 1];
    }

    /**
     * Auftriebs- und Formwiderstandsbeiwert des Riggs am Wind über dem scheinbaren Windwinkel β (Grad, 0..180).
     * (In der Hülle von Phase 1 über dem Anstellwinkel; seit Phase 7 über β wie in VPPs üblich.)
     */
    public static double[] coefficients(double betaDeg) { return coefficients(Plan.BERMUDA, betaDeg); }

    /** Seit Phase 8: Beiwerte je Segelplan über β (Grad). */
    public static double[] coefficients(Plan p, double betaDeg) {
        double b = Math.min(180, Math.abs(betaDeg));
        return switch (p) {
            case BERMUDA -> new double[]{interp(UP_B, UP_CL, b), interp(UP_B, UP_CD, b)};
            case GAFF -> new double[]{interp(GAFF_B, GAFF_CL, b), interp(GAFF_B, GAFF_CD, b)};
            case SQUARE -> new double[]{interp(SQ_B, SQ_CL, b), interp(SQ_B, SQ_CD, b)};
            case KITE -> new double[]{interp(KITE_B, KITE_CL, b), interp(KITE_B, KITE_CD, b)};
        };
    }

    /** Beiwerte des Vorwindsegels über β (0 vor 40°). */
    public static double[] downwindCoefficients(double betaDeg) {
        double b = Math.min(180, Math.abs(betaDeg));
        if (b < DN_B[0]) return new double[]{0, 0.3};
        return new double[]{interp(DN_B, DN_CL, b), interp(DN_B, DN_CD, b)};
    }

    // ------------------------------------------------------------------ Kräfte

    /**
     * Aerodynamik im scheinbaren Wind V (m/s) unter β (Grad) bei Krängung φ (Grad), Flachheit und Segelwahl:
     * {Vortrieb N, Querkraft in Segelebene N, krängendes Moment Nm}.
     */
    public double[] aero(Rig r, double v, double betaDeg, double heelDeg, double flat, boolean dn) { return aero(r, v, betaDeg, heelDeg, flat, dn, 0); }

    /**
     * Seit Phase 8 mit gefierten Schoten ({@code ease} 0..1): das Tuch verliert Auftrieb und schlägt (wenig Widerstand).
     * Beim Kite greift die Kraft am Trapezhaken an ({@code ceHeight}); das Seitenverhältnis ist das des Schirms (5).
     */
    public double[] aero(Rig r, double v, double betaDeg, double heelDeg, double flat, boolean dn, double ease) {
        double bt = Math.toRadians(betaDeg), ph = Math.toRadians(heelDeg);
        boolean kite = r.plan() == Plan.KITE;
        if (kite) ph = 0;                         // der Schirm fliegt unabhängig von der Lage des Fahrers
        double be = Math.atan2(Math.sin(bt) * Math.cos(ph), Math.cos(bt));
        double ve2 = v * v * (Math.cos(bt) * Math.cos(bt) + Math.sin(bt) * Math.sin(bt) * Math.cos(ph) * Math.cos(ph));
        double bed = Math.toDegrees(be);
        double area, cl, cdp;
        double[] up = coefficients(r.plan(), bed);
        if (dn && r.downwindSail()) {
            double[] d = downwindCoefficients(bed);
            area = r.mainArea() + r.downwindArea();
            cl = (up[0] * r.mainArea() + d[0] * r.downwindArea()) / area;
            cdp = (up[1] * r.mainArea() + d[1] * r.downwindArea()) / area;
        } else {
            area = r.sailArea();
            cl = up[0];
            cdp = up[1];
        }
        cl *= flat;
        double e = Math.max(0, Math.min(1, ease));
        cl *= 1 - e;
        cdp = cdp * (1 - e) + 0.08 * e;
        double ar;
        if (kite) ar = 5;
        else {
            double h = r.mastHeight() * 1.05;
            ar = h * h / area * 1.1;                 // Spiegelbild an der Wasseroberfläche [G]
        }
        double cd = cdp + cl * cl / (Math.PI * ar);
        double q = 0.5 * airDensity * ve2 * area * (kite ? kitePower(bed) : 1);
        double L = q * cl, D = q * cd + 0.5 * airDensity * v * v * windage(r);
        double fx = L * Math.sin(be) - D * Math.cos(be), fy = L * Math.cos(be) + D * Math.sin(be);
        double clr = r.finDepth() * 0.45;
        return new double[]{fx, fy, fy * (r.ceHeight() + clr)};
    }

    /**
     * Seit Phase 8: Luftwiderstand von Crew, Rumpf und Rigg als Fläche mal c_w (m²) [G]: zählt bei schnellen Booten mit
     * viel scheinbarem Wind (Kats, Kites), bremst die J/70 kaum. Für Schiffe der Rumpf über Wasser.
     */
    public static double windage(Rig r) {
        if (r.kind() == Kind.SHIP) return 0.35 * r.loa() * 2.5 * 0.3;
        double crew = r.crew() * (r.kind() == Kind.CATAMARAN || r.kind() == Kind.KITE ? 0.45 : 0.15);
        double rig = r.kind() == Kind.CATAMARAN ? 0.025 * r.loa() * r.mastHeight() : 0.004 * r.loa() * r.mastHeight();
        return crew + rig;
    }

    /**
     * Aufrichtendes Moment (Nm) bei Krängung φ (Grad): Rumpf und Crew auf der Kante. Seit Phase 8: der Kat höchstens
     * W · (B/2 · cos φ − z_G · sin φ) (fliegt ein Rumpf, nimmt das Moment ab), die Jolle verliert ihre Formstabilität
     * gegen 70°, die Crew hängt nur bis 60° wirksam aus [G].
     */
    public static double rightingMoment(Rig r, double heelDeg) {
        double ph = Math.toRadians(Math.abs(heelDeg)), w = r.total() * G;
        double hull = w * r.gm() * Math.sin(ph);
        if (r.kind() == Kind.CATAMARAN) hull = Math.min(hull, w * (r.beam() / 2 * Math.cos(ph) - zG(r) * Math.sin(ph)));
        else if (r.kind() == Kind.DINGHY) hull *= Math.max(0, 1 - Math.pow(Math.abs(heelDeg) / 70, 2));
        return hull + crewMoment(r, heelDeg);
    }

    /** Hebel der Crew (Nm) bei Krängung φ, voll ausgehängt. */
    public static double crewMoment(Rig r, double heelDeg) {
        double ph = Math.toRadians(Math.min(90, Math.abs(heelDeg)));
        double fade = r.kind() == Kind.KITE ? 1 : Math.max(0, 1 - Math.pow(Math.max(0, Math.abs(heelDeg) - 30) / 30, 2));
        return r.crew() * r.crewMass() * G * r.hikeArm() * Math.cos(ph) * fade;
    }

    /** Schwerpunkt des Kats über der Wasserlinie (m) [G]. */
    static double zG(Rig r) { return 0.12 * r.mastHeight(); }

    /** Reibung nach ITTC 1957 (Beiwert) bei V (m/s) über der Länge l. */
    public double ittc(double v, double l) {
        double re = Math.max(1e4, v * 0.7 * l / viscosity);
        double lg = Math.log10(re) - 2;
        return 0.075 / (lg * lg);
    }

    /**
     * Widerstand des Rumpfs ohne Kiel (N) bei V und Krängung φ: Reibung mit Formfaktor, Wellenwiderstand über der
     * Froude-Zahl (gleitende Rümpfe tragen sich ab Fn 0,5 und verlieren benetzte Fläche) [G], Zuschlag für Krängung.
     */
    public double hullResistance(Rig r, double v, double heelDeg) {
        double fn = v / Math.sqrt(G * r.lwl()), x = fn / 0.4, x6 = Math.pow(x, 6);
        double lift = r.planing() > 0 ? 1 / (1 + 0.25 * r.planing() * Math.max(0, fn - 0.45) * Math.max(0, fn - 0.45) * 10) : 1;
        double rf = 0.5 * waterDensity * v * v * r.wetted() * lift * ittc(v, r.lwl()) * (1 + r.formFactor());
        double rw = r.total() * G * r.wave() * x6 / (1 + r.planing() * x6);
        double hs = r.kind() == Kind.CATAMARAN || r.kind() == Kind.KITE ? 0 : Math.pow(Math.sin(Math.toRadians(Math.min(60, Math.abs(heelDeg)))), 2);
        return (rf + rw) * (1 + 0.6 * hs);
    }

    /**
     * Kiel oder Schwert als Flügel: Abdrift (Grad) und induzierter Widerstand plus Reibung (N), die eine Querkraft
     * {@code side} (N) bei V verlangt. C_Lα = 2π · AR / (AR + 2) (Helmbold), AR über dem Spiegelbild; über 10° reißt die
     * Strömung ab, dann trägt der Flügel nicht mehr und der Widerstand steigt steil.
     */
    public double[] appendage(Rig r, double v, double side) {
        double a = r.finArea(), t = r.finDepth(), ar = 2 * t * t / Math.max(1e-6, a);
        double cla = 2 * Math.PI * ar / (ar + 2), q = 0.5 * waterDensity * Math.max(0.05, v) * Math.max(0.05, v) * a;
        double cl = side / q, stall = cla * Math.toRadians(10);
        double over = Math.max(0, Math.abs(cl) - stall);
        cl = Math.max(-stall, Math.min(stall, cl));
        double lee = Math.toDegrees(cl / cla) + 25 * over;
        double cd = 0.012 + cl * cl / (Math.PI * ar) + 1.2 * over;
        // abgerissen treibt das Boot quer: höchstens ein Viertel der Querkraft als Widerstand [G], damit es aus dem
        // Stand anläuft (im Gleichgewicht der Polaren tritt das nicht auf)
        double d = Math.min(q * cd, q * (0.012 + cl * cl / (Math.PI * ar)) + 0.25 * Math.abs(side));
        return new double[]{Math.min(lee, 45), d};
    }

    /**
     * Gleichgewicht bei Fahrt V (m/s), wahrem Wind (m/s) unter TWA (Grad), Flachheit und Segelwahl: {Überschuss an
     * Vortrieb N, Krängung Grad, Abdrift Grad, β Grad, V_A m/s, möglich 1/0}. Die Krängung folgt aus Moment = aufrichtendes
     * Moment (bis zur größten Krängung der Klasse); reicht das nicht, ist die Lage nicht möglich.
     */
    public double[] balance(Rig r, double v, double tws, double twa, double flat, boolean dn) {
        double[] aw = apparentWind(tws, 0, v, twa);
        double beta = aw[1], vaw = aw[0];
        double heel = 0;
        double[] f = null;
        boolean ok = true;
        for (int it = 0; it < 8; it++) {
            f = aero(r, vaw, beta, heel, flat, dn);
            double lo = 0, hi = r.maxHeelDeg();
            if (rightingMoment(r, hi) < f[2] * Math.cos(Math.toRadians(hi))) { heel = hi; ok = false; break; }
            for (int k = 0; k < 30; k++) {
                double m = (lo + hi) / 2;
                if (rightingMoment(r, m) < f[2] * Math.cos(Math.toRadians(m))) lo = m; else hi = m;
            }
            double nh = (lo + hi) / 2;
            if (Math.abs(nh - heel) < 0.02) { heel = nh; break; }
            heel = nh;
        }
        f = aero(r, vaw, beta, heel, flat, dn);
        double side = f[1] * Math.cos(Math.toRadians(heel));
        double[] ap = appendage(r, v, side);
        double drag = hullResistance(r, v, heel) + ap[1];
        double thrust = f[0];
        return new double[]{thrust - drag, heel, ap[0], beta, vaw, ok ? 1 : 0};
    }

    /** Kleinste Flachheit, mit der gesucht wird (darunter killt das Segel). */
    public static final double MIN_FLAT = 0.2;

    /**
     * Seit Phase 8: das beste Gleichgewicht bei Fahrt V über alle Flachheiten (stetig statt in Stufen): erst die
     * größte Flachheit, bei der das Moment noch reicht (Halbierung), dann der größte Überschuss darunter (goldener
     * Schnitt). Gibt {Überschuss N, Krängung, Abdrift, β, V_A, möglich, Flachheit}.
     */
    public double[] best(Rig r, double v, double tws, double twa, boolean dn) {
        double fmax = 1;
        double[] top = balance(r, v, tws, twa, 1, dn);
        if (top[5] < 0.5) {
            double lo = MIN_FLAT, hi = 1;
            double[] b0 = balance(r, v, tws, twa, lo, dn);
            if (b0[5] < 0.5) return new double[]{b0[0] - 1e6, b0[1], b0[2], b0[3], b0[4], 0, lo};
            for (int k = 0; k < 14; k++) {
                double m = (lo + hi) / 2;
                if (balance(r, v, tws, twa, m, dn)[5] > 0.5) lo = m; else hi = m;
            }
            fmax = lo;
        }
        int it = switch (quality) { case LOW -> 7; case MEDIUM -> 10; case HIGH -> 12; case ULTRA -> 16; };
        double a = MIN_FLAT, b = fmax, gr = (Math.sqrt(5) - 1) / 2;
        double c = b - gr * (b - a), d = a + gr * (b - a);
        double fc = balance(r, v, tws, twa, c, dn)[0], fd = balance(r, v, tws, twa, d, dn)[0];
        for (int k = 0; k < it; k++) {
            if (fc > fd) { b = d; d = c; fd = fc; c = b - gr * (b - a); fc = balance(r, v, tws, twa, c, dn)[0]; }
            else { a = c; c = d; fc = fd; d = a + gr * (b - a); fd = balance(r, v, tws, twa, d, dn)[0]; }
        }
        double fl = (a + b) / 2;
        double[] at = balance(r, v, tws, twa, fl, dn);
        double[] edge = balance(r, v, tws, twa, fmax, dn);
        if (edge[5] > 0.5 && edge[0] > at[0]) { at = edge; fl = fmax; }
        return new double[]{at[0], at[1], at[2], at[3], at[4], at[5], fl};
    }

    /** Die Flachheiten in Stufen (Phase 7; seit Phase 8 nur noch für Vergleiche). */
    double[] flats() {
        int n = switch (quality) { case LOW -> 5; case MEDIUM -> 7; case HIGH -> 9; case ULTRA -> 17; };
        double[] f = new double[n];
        for (int i = 0; i < n; i++) f[i] = 1 - 0.8 * i / (n - 1.0);
        return f;
    }

    /**
     * Fahrt im Gleichgewicht (m/s) bei wahrem Wind tws (m/s) unter twa (Grad): schnellste Flachheit (stetig, seit Phase 8)
     * und Segelwahl. Gibt {Fahrt, Krängung, Abdrift, β, V_A, Flachheit, Vorwindsegel 1/0}.
     */
    public double[] speed(Rig r, double tws, double twa) {
        double[] best = {0, 0, 0, 0, 0, 1, 0};
        for (int s = 0; s < (r.downwindSail() && twa > 60 ? 2 : 1); s++) {
            boolean dn = s == 1;
            // von oben suchen: die schnellste Fahrt, bei der der Vortrieb noch reicht (bei kleiner Fahrt reißt der
            // Kiel ab, darum ist der Überschuss dort nicht verlässlich); seit Phase 8 doppelt so fein
            double top = Math.max(1, tws * 2.2 + 4), lo = -1, hi = top;
            int steps = switch (quality) { case LOW -> 48; case MEDIUM -> 72; case HIGH -> 96; case ULTRA -> 160; };
            double[] at = null;
            for (int k = steps; k >= 1; k--) {
                double v = top * k / steps;
                double[] b = best(r, v, tws, twa, dn);
                if (b[0] > 0 && b[5] > 0.5) { lo = v; hi = Math.min(top, v + top / steps); at = b; break; }
            }
            if (lo < 0) continue;
            for (int k = 0; k < 22; k++) {
                double m = (lo + hi) / 2;
                double[] b = best(r, m, tws, twa, dn);
                if (b[0] > 0 && b[5] > 0.5) { lo = m; at = b; } else hi = m;
            }
            if (lo > best[0]) best = new double[]{lo, at[1], at[2], at[3], at[4], at[6], dn ? 1 : 0};
        }
        return best;
    }

    /** Polare einer Takelage bei wahrem Wind (m/s): je Zeile {TWA Grad, Fahrt m/s, Krängung, Abdrift, Flachheit, Vorwindsegel}. */
    public double[][] polar(Rig rig, double windSpeed) {
        int step = switch (quality) { case LOW -> 10; case MEDIUM -> 5; case HIGH -> 5; case ULTRA -> 2; };
        int n = (180 - 30) / step + 1;
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            double twa = 30 + i * step;
            double[] s = speed(rig, windSpeed, twa);
            p[i] = new double[]{twa, s[0], s[1], s[2], s[5], s[6]};
        }
        return p;
    }

    private final java.util.Map<String, double[]> vmgCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** Beste Winkel: {Kreuzwinkel, VMG am Wind, Fahrt dort, Vorwindwinkel, VMG vor dem Wind, Fahrt dort} (m/s, Grad). */
    public double[] vmg(Rig r, double tws) {
        double bu = 0, au = 40, su = 0, bd = 0, ad = 150, sd = 0;
        double st = quality == Quality.ULTRA ? 0.5 : quality == Quality.LOW ? 2 : 1;
        double from = r.plan() == Plan.SQUARE ? 50 : 26, to = r.plan() == Plan.SQUARE ? 100 : 75;
        for (double a = from; a <= to; a += st) {
            double v = speed(r, tws, a)[0], g = v * Math.cos(Math.toRadians(a));
            if (g > bu) { bu = g; au = a; su = v; }
        }
        for (double a = 110; a <= 180; a += st) {
            double v = speed(r, tws, a)[0], g = -v * Math.cos(Math.toRadians(a));
            if (g > bd) { bd = g; ad = a; sd = v; }
        }
        return new double[]{au, bu, su, ad, bd, sd};
    }

    /**
     * Seit Phase 8: {@link #vmg} aus einem Speicher je Klasse, Stufe und Wind (auf 0,25 m/s gerundet), für Regatta und
     * Szene, die es oft fragen.
     */
    public double[] vmgCached(Rig r, double tws) {
        double tq = Math.max(0.5, Math.round(tws * 4) / 4.0);
        return vmgCache.computeIfAbsent(r.name() + "|" + quality + "|" + tq + "|" + airDensity, k -> vmg(r, tq)).clone();
    }

    /** Wendewinkel (Grad): zweimal der Kreuzwinkel. */
    public double tackAngle(Rig r, double tws) { return 2 * vmg(r, tws)[0]; }

    // ------------------------------------------------------------------ Bewegung

    /**
     * Kräfte am Segel eines Boots im jetzigen Zustand: {Vortrieb N, Querkraft N, krängendes Moment Nm, scheinbarer Wind
     * m/s, β Grad}. Meldet {@code onLuff}, wenn das Segel killt (zu hoch am Wind).
     */
    public double[] forces(Boat b, double t) {
        double[] aw = apparentWind(windSpeed(b.x, b.z, t), windFrom(b.x, b.z, t), b.speed, b.headingDeg);
        double flat = b.cloth != null ? Math.max(MIN_FLAT, Math.min(1, b.cloth.camber() / 0.12)) : b.flat;
        double[] f = b.capsized || b.furled ? new double[3] : aero(b.rig, aw[0], aw[1], b.heelDeg, flat, b.downwind, b.ease);
        if (!b.furled && aw[1] < 22) events.onLuff(b, Math.min(1, (22 - aw[1]) / 10));
        b.lastTackSide = aw[2];
        double[] r = {f[0], f[1], f[2], aw[0], aw[1]};
        System.arraycopy(r, 0, b.last, 0, 5);
        return r;
    }

    /** Unterschritte je Sekunde nach der Stufe. */
    int rate() { return switch (quality) { case LOW -> 30; case MEDIUM -> 60; case HIGH -> 120; case ULTRA -> 240; }; }

    /** Seit Phase 8: das Boot sofort kentern lassen (Regie, Patzer der Crew); nur Jollen und Kats. */
    public void capsize(Boat b) {
        if (!b.rig.canCapsize() || b.capsized) return;
        b.capsized = true;
        b.righting = false;
        b.capsizeTimer = b.rig.rightingTime() / Math.max(0.5, b.skill);
        if (b.rollDeg == 0) b.rollDeg = -b.windSide();
        b.rollRate = Math.signum(b.rollDeg) * 40;
        events.onCapsize(b);
    }

    /**
     * Rechnet ein Boot dt Sekunden weiter: Kurs (Rudergänger), Trimm (alle 0,5 s die beste Flachheit und Segelwahl bei
     * jetziger Fahrt), Fahrt aus Vortrieb und Widerstand, Krängung aus den Momenten, Abdrift, Lage über Grund (mit
     * Strömung aus {@link Water}). Seit Phase 8: Krängung mit Vorzeichen (die Crew sitzt immer in Luv und hängt nur so
     * weit aus, wie es nötig ist), Baum mit Halse, Kentern und Aufrichten, gefierte Schoten.
     */
    public void step(Boat b, double dt) {
        if (dt <= 0) return;
        Rig r = b.rig;
        int n = Math.max(1, (int) Math.ceil(dt * rate()));
        double h = dt / n, m = r.total() * 1.08;
        double inertia = r.total() * Math.pow(r.kind() == Kind.CATAMARAN ? 0.45 * r.beam() : 0.38 * r.beam(), 2);
        for (int s = 0; s < n; s++) {
            time += h;
            double tws = windSpeed(b.x, b.z, time), twf = windFrom(b.x, b.z, time);
            if (b.capsized) { capsizedStep(b, h, twf); continue; }
            // Rudergänger
            if (!Double.isNaN(b.targetHeadingDeg)) {
                double target = b.targetHeadingDeg;
                // Fahrt aufbauen (seit Phase 8): treibt das Boot hoch am Wind quer (Kiel oder Schwert abgerissen, etwa nach
                // der Wende), fällt der Rudergänger etwas ab, bis es wieder läuft
                double rel = ((twf - target) % 360 + 540) % 360 - 180;
                if (Math.abs(rel) < 60 && b.leewayDeg > 7 && r.kind() != Kind.SHIP) target -= Math.signum(rel) * Math.min(22, (b.leewayDeg - 7) * 2.5);
                double d = ((target - b.headingDeg) % 360 + 540) % 360 - 180;
                double turn = r.turnRate() * h * Math.min(1, 0.35 + b.speed / Math.max(0.3, hullSpeed(r)));
                b.headingDeg = (b.headingDeg + Math.max(-turn, Math.min(turn, d)) + 360) % 360;
            }
            double[] aw = apparentWind(tws, twf, b.speed, b.headingDeg);
            if (aw[2] != b.lastTackSide && b.lastTackSide != 0 && aw[1] < 90) events.onTack(b);
            b.lastTackSide = aw[2];
            b.trimTimer -= h;
            if (b.trimTimer <= 0) { trim(b, tws, aw); b.trimTimer = 0.5 / Math.max(0.5, b.skill); }
            double flat = b.cloth != null ? Math.max(MIN_FLAT, Math.min(1, b.cloth.camber() / 0.12)) : b.flat;
            // Reflex der Crew (seit Phase 8): krängt das Boot über seine Grenze, fiert sie sofort die Schot, ein Kind
            // langsamer und weniger als ein Könner; so kentert ein Kat nicht schon beim Anfahren, wohl aber in einer Bö
            double over = r.kind() == Kind.KITE ? 0 : (Math.abs(b.rollDeg) - 0.8 * r.maxHeelDeg()) / Math.max(1, Math.min(r.capsizeDeg(), 60) - 0.8 * r.maxHeelDeg());
            double want = Math.max(0, Math.min(1, over * 2.5)) * (0.25 + 0.75 * b.skill);
            b.reflex += (want - b.reflex) * Math.min(1, h * (2 + 10 * b.skill));
            double[] f = b.furled ? new double[3] : aero(r, aw[0], aw[1], Math.abs(b.rollDeg), flat, b.downwind, Math.max(b.ease, b.reflex));
            double side = f[1] * Math.cos(Math.toRadians(b.rollDeg));
            double[] ap = appendage(r, b.speed, side);
            double drag = hullResistance(r, b.speed, b.rollDeg) + ap[1];
            b.speed = Math.max(0, b.speed + (f[0] * Math.max(0.5, Math.min(1, b.skill + 0.05)) - drag) / m * h);
            // Krängung mit Vorzeichen: Wind von Steuerbord (Seite +1) legt das Boot nach Backbord (−)
            double ws = aw[2], ph = b.rollDeg, aph = Math.abs(ph);
            double hm = f[2] * Math.cos(Math.toRadians(ph));
            double crew = Math.min(crewMoment(r, aph) * Math.max(0.3, b.skill), Math.max(0, hm));
            double hull = rightingMoment(r, aph) - crewMoment(r, aph);
            double torque = -ws * (hm - crew) - Math.signum(ph) * hull;
            double k = Math.max(1, (rightingMoment(r, aph + 1) - rightingMoment(r, aph)) * 57.3 + r.total() * G * 0.05);
            double c = 2 * 0.5 * Math.sqrt(k * inertia);
            double acc = (torque - c * Math.toRadians(b.rollRate)) / inertia;
            b.rollRate += Math.toDegrees(acc) * h;
            boom(b, aw, h, inertia);
            b.rollDeg += b.rollRate * h;
            if (Math.abs(b.rollDeg) > 89) { b.rollDeg = Math.signum(b.rollDeg) * 89; b.rollRate = 0; }
            b.heelDeg = Math.abs(b.rollDeg);
            if (r.canCapsize() && b.heelDeg > r.capsizeDeg()) capsize(b);
            else if (!r.canCapsize() && r.kind() != Kind.KITE && b.heelDeg > 75) { b.rollDeg = Math.signum(b.rollDeg) * 75; b.rollRate = 0; events.onCapsize(b); }
            b.leewayDeg = ap[0];
            // Lage über Grund: Fahrt längs Kurs, Abdrift nach Lee, Strömung
            double lee = Math.toRadians(b.headingDeg - aw[2] * b.leewayDeg);
            double[] cur = {0, 0};
            if (water != null) water.current(b.x, b.z, time, cur);
            b.x += (Math.sin(lee) * b.speed + cur[0]) * h;
            b.z += (-Math.cos(lee) * b.speed + cur[1]) * h;
            b.last[0] = f[0]; b.last[1] = f[1]; b.last[2] = f[2]; b.last[3] = aw[0]; b.last[4] = aw[1];
            if (!b.furled && aw[1] < 22) events.onLuff(b, Math.min(1, (22 - aw[1]) / 10));
        }
        b.sheetDeg = Math.max(0, b.last[4] - 15 - 10 * b.flat) + 60 * b.ease;
    }

    /**
     * Der Baum (seit Phase 8): steht nach Lee im Schotwinkel. Kommt der Wind von der Seite, auf der der Baum steht, und
     * achterlicher als 160° (oder dreht das Boot durch den Wind von achtern), schlägt er über: je mehr Wind, desto
     * schneller, und der Stoß legt das Boot auf die neue Seite (Patenthalse) [G]. Bei Schiffen und Kites gibt es keinen Baum.
     */
    void boom(Boat b, double[] aw, double h, double inertia) {
        Rig r = b.rig;
        if (r.plan() == Plan.KITE) { b.boomDeg = 0; return; }
        double sheet = Math.min(85, Math.max(3, aw[1] - 15 - 10 * b.flat) + 60 * b.ease);
        double target = -aw[2] * sheet;
        if (!b.boomSet) { b.boomDeg = target; b.boomSet = true; return; }    // erster Schritt: der Baum steht schon richtig
        double side = Math.signum(b.boomDeg == 0 ? -aw[2] : b.boomDeg);
        if (side == Math.signum(target) || Math.abs(b.boomDeg) < 1e-6) {
            // trimmen: der Baum folgt der Schot
            double d = target - b.boomDeg, rate = 25;
            b.boomRate = Math.max(-rate, Math.min(rate, d / Math.max(h, 0.15)));
            b.boomDeg += b.boomRate * h;
            return;
        }
        // der Baum steht in Luv: bei der Wende (Wind von vorn) zieht ihn das killende Tuch langsam herüber, achterlich
        // drückt ihn der Wind mit Macht (Halse)
        boolean gybe = aw[1] > 90;
        double swing = gybe ? 40 + 12 * aw[0] * (r.kind() == Kind.SHIP ? 0.2 : 1) : 45;
        double before = b.boomDeg;
        b.boomRate = Math.signum(target) * swing;
        b.boomDeg += b.boomRate * h;
        if (Math.signum(before) != Math.signum(b.boomDeg)) {
            b.lastBoomSide = Math.signum(b.boomDeg);
            if (gybe && r.plan() != Plan.SQUARE) {
                double violence = Math.max(0, Math.min(1, (aw[0] - 2.5) / 8.0)) * (r.kind() == Kind.KEELBOAT ? 0.4 : r.kind() == Kind.SHIP ? 0.1 : 1);
                // Stoß nach der neuen Lee-Seite [G]: die Jolle bekommt bis 60°/s, das Kielboot ein Drittel davon
                double kick = violence * (r.kind() == Kind.DINGHY ? 110 : r.kind() == Kind.CATAMARAN ? 45 : 20) * (1.6 - 0.6 * b.skill);
                b.rollRate += Math.signum(b.boomDeg) * kick;
                events.onGybe(b, violence);
            }
        }
    }

    /** Gekentert: das Boot liegt auf der Seite, treibt, die Crew richtet es nach {@link Rig#rightingTime} auf. */
    void capsizedStep(Boat b, double h, double twf) {
        Rig r = b.rig;
        double sg = Math.signum(b.rollDeg == 0 ? 1 : b.rollDeg);
        b.speed = Math.max(0, b.speed - h * (0.8 + b.speed * 1.5));
        b.capsizeTimer -= h;
        if (!b.righting) {
            b.rollDeg += sg * Math.max(10, Math.abs(b.rollRate)) * h;
            b.rollRate = sg * Math.min(80, Math.abs(b.rollRate) + 60 * h);
            if (Math.abs(b.rollDeg) > 88) { b.rollDeg = sg * 88; b.rollRate = 0; }
            // das Boot dreht mit dem Bug in den Wind, während die Crew am Schwert steht [G]
            double d = ((twf - b.headingDeg) % 360 + 540) % 360 - 180, target = d > 0 ? d - 50 : d + 50;
            b.headingDeg = (b.headingDeg + Math.max(-4 * h, Math.min(4 * h, target)) + 360) % 360;
            if (b.capsizeTimer <= 0) b.righting = true;
        } else {
            b.rollRate = -sg * 30;
            b.rollDeg += b.rollRate * h;
            if (Math.signum(b.rollDeg) != sg || Math.abs(b.rollDeg) < 2) {
                b.rollDeg = 0;
                b.rollRate = 0;
                b.capsized = false;
                b.righting = false;
                b.trimTimer = 0;
                events.onRight(b);
            }
        }
        b.heelDeg = Math.abs(b.rollDeg);
        b.leewayDeg = 0;
        // treibt langsam nach Lee [G]
        double to = Math.toRadians(twf + 180), drift = 0.25;
        b.x += Math.sin(to) * drift * h;
        b.z -= Math.cos(to) * drift * h;
    }

    /** Trimm nachführen: beste Flachheit (stetig) und Segelwahl bei jetziger Fahrt (größter Überschuss an Vortrieb, Krängung in Grenzen). */
    void trim(Boat b, double tws, double[] aw) {
        double twa = Math.abs(((windFrom(b.x, b.z, time) - b.headingDeg) % 360 + 540) % 360 - 180);
        double bestNet = -Double.MAX_VALUE;
        for (int s = 0; s < (b.rig.downwindSail() && b.downwindAllowed && twa > 60 ? 2 : 1); s++) {
            // aus dem Stand trimmt die Crew wie für halbe Rumpfgeschwindigkeit (sonst reißt der Kiel ab und alles ist „zu viel“)
            double[] e = best(b.rig, Math.max(b.speed, 0.6 * hullSpeed(b.rig)), tws, twa, s == 1);
            double net = e[5] > 0.5 ? e[0] : e[0] - 1e6;
            if (net > bestNet) { bestNet = net; b.flat = e[6]; b.downwind = s == 1; }
        }
    }

    /** Rumpfgeschwindigkeit nach Froude Fr = 0,4 (m/s). */
    public static double hullSpeed(Rig r) { return 0.4 * Math.sqrt(G * r.lwl()); }

    /** Wie viel los ist (0..1), für {@code gl/}. */
    public double strength() { return Math.min(1, windSpeed / 12); }

    /** Laufzeit des Kerns (s). */
    public double time() { return time; }

    /** „J/70: 5,5 kn unter 42° am Wind, 4,2° Krängung …“ */
    public String describe(Boat b) {
        String st = b.state();
        return String.format(Locale.GERMAN, "%s: %.1f kn, Kurs %03.0f°, Krängung %.0f°, Abdrift %.1f°, β %.0f°, Flachheit %.0f %%%s%s",
                b.rig.name(), b.speed / KN, b.headingDeg, b.heelDeg, b.leewayDeg, b.last[4], b.flat * 100, b.downwind ? ", Vorwindsegel" : "",
                st.isEmpty() ? "" : ", " + st);
    }
}
