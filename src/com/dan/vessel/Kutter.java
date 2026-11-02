package com.dan.vessel;

/**
 * Vorgaben für die Boote von Lituya Bay 1958 (Werkbuch 2.3; LituyaBay1958 Phase 15). Belegt nach MILLER60 (USGS PP
 * 354-C, die Berichte von Ulrich und Swanson): alle drei Troller, „about 40 feet long“; Edrie vor Anker in „about 5
 * fathoms“ in einer kleinen Bucht am Südufer, rund 40 Faden Kette gesteckt, der Anker saß fest, die Kette riss, als das
 * Boot mit der Welle stieg (Front 50 bis 75 Fuß); Badger in „about 4 fathoms“ in Anchorage Cove am Nordufer beim
 * Eingang, neben der Sunmore, über La Chaussee Spit getragen (mehr als 80 Fuß über den Bäumen); Sunmore auf dem Weg
 * zum Eingang, verloren.
 * <p>
 * Gestaltet [G]: Form und Maße eines hölzernen Trollers aus Südostalaska der 1940er/50er (12,2 m über alles, 3,8 m breit,
 * 1,6 m Tiefgang mit Kiel, rund 17 t, Steuerhaus mittschiffs, Mast mit zwei Trollstangen), Schwerpunkt für GM 1,0 m, Kette
 * 3/8″ (10 mm, Güte 30), Maschine 110 PS. Die drei unterscheiden sich nur in kleinen Maßen.
 */
public final class Kutter {
    private Kutter() { }

    /** Wassertiefe am Ankerplatz der Edrie (m): 5 Faden. */
    public static final double EDRIE_TIEFE = 5 * 1.8288;
    /** Gesteckte Kette der Edrie (m): 40 Faden. */
    public static final double EDRIE_KETTE = 40 * 1.8288;
    /** Wassertiefe am Ankerplatz der Badger (m): 4 Faden. */
    public static final double BADGER_TIEFE = 4 * 1.8288;

    /** Ein Troller der Länge loa (m) [G]. */
    public static Hull troller(String name, double loa, double beam, double depth, double draft) {
        Hull h = new Hull(name, loa, beam, depth, draft);
        h.houseS0 = -0.02 * loa;
        h.houseS1 = 0.2 * loa;
        h.houseWidth = 0.6 * beam;
        h.houseHeight = 1.95;
        h.mastS = 0.28 * loa;
        h.mastHeight = 7.8;
        h.poleLength = 0.95 * loa;
        return h;
    }

    /** Metazentrische Höhe der Vorgaben (m) [G]: ein beladener Troller mit Eis im Raum, Mast und Stangen (Schwerpunkt dann rund 0,9 der Seitenhöhe). */
    public static final double GM = 1.0;

    /** Ein Boot auf dem Konstruktionstiefgang mit dem Schwerpunkt so hoch, dass GM herauskommt. */
    public static Boat mitGm(Hull h, double gm) {
        double m = h.massDesign();
        return new Boat(h, m, h.kb() + h.waterplaneInertia() / (m / 1025) - gm);
    }

    /** Edrie (Howard Ulrich und Sohn): 40 Fuß, vor Anker mit 40 Faden Kette, Anker festgehakt. */
    public static Boat edrie() { return mitGm(troller("Edrie", 12.2, 3.7, 2.45, 1.6), GM); }

    /** Badger (Bill und Vivian Swanson): 40 Fuß [G: etwas breiter]. */
    public static Boat badger() { return mitGm(troller("Badger", 12.5, 3.8, 2.5, 1.65), GM); }

    /** Sunmore (Orville und Mickey Wagner): 40 Fuß [G: etwas kürzer]. */
    public static Boat sunmore() { return mitGm(troller("Sunmore", 11.9, 3.6, 2.4, 1.55), GM); }

    /** Die Kette der Edrie: 40 Faden 3/8″ [G], Anker festgehakt (MILLER60: „unable to free the anchor“). */
    public static Chain edrieKette() {
        Chain c = Chain.proofCoil(EDRIE_KETTE, 0.0095);
        c.fouled = true;
        return c;
    }
}
