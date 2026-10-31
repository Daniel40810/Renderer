package com.dan.longship;

/**
 * Die Schiffstypen als Datensätze: Hauptmaße und Linienriss in wenigen Zahlen, Riemen, Segel, Ausstattung. Maße nach
 * den Funden (Haithabu 1, Skuldelev, Gokstad), Einzelheiten gestaltet [G]. Alle Höhen über der Konstruktionswasserlinie
 * (y = 0), Längen von Steven zu Steven.
 */
public enum ShipType {
    /**
     * Königliches Langschiff nach Haithabu 1 (um 985 gebaut): 30,9 × 2,7 m, schlank, niedrig, bis 31 Riemenpaare; hier
     * 26 Paare [G], Schilde am Bord, Steven mit Tierkopf, Segel rund 90 m² [G].
     */
    LANGSCHIFF("Langschiff (Haithabu 1)", 30.9, 2.7, 0.85, 0.65, 1.55, 13, 26, 0.92, 4.6, 0.6, 11.0, 8.6, 11.5, true, true, false, 3.2, 26_000),
    /**
     * Handelsschiff Knorr nach Haithabu 3 (um 1023) als Typ [G]: 22 × 6 m, breit und hochbordig, wenige Riemen an Bug
     * und Heck, offener Laderaum mittschiffs, großes Rahsegel.
     */
    KNORR("Knorr (Handelsschiff)", 22.0, 6.0, 1.45, 1.05, 1.35, 12, 3, 1.0, 4.4, 0, 12.0, 10.5, 13.5, false, false, true, 1.4, 60_000),
    /** Faering: kleines Ruderboot mit zwei Riemenpaaren für Fischer und Fähre, ohne Mast. */
    FAERING("Faering (Ruderboot)", 6.6, 1.5, 0.3, 0.38, 0.42, 6, 2, 0.95, 2.6, -0.2, 0, 0, 0, false, false, false, 2.1, 900);

    public final String label;
    /** Länge über alles (m), größte Breite (m), Tiefgang des Kiels mittschiffs unter der Wasserlinie (m). */
    public final double length, beam, draft;
    /** Freibord mittschiffs (Bordkante über der Wasserlinie, m) und Sprung: wie viel die Bordkante an den Steven steigt (m). */
    public final double freeboard, sheer;
    /** Plankengänge je Seite (Klinker), Riemenpaare, Abstand der Riemen (m), Länge eines Riemens (m), Mitte der Riemenreihe (m, + vorn). */
    public final int strakes, oarPairs;
    public final double oarSpacing, oarLength, oarStart;
    /** Segel: Breite der Rah (m), Höhe des Segels (m), Höhe der Rah über der Wasserlinie (m); 0 = kein Mast. */
    public final double sailWidth, sailHeight, yardHeight;
    /** Schilde am Bord, Steven mit Tierkopf, offener Laderaum mittschiffs. */
    public final boolean shields, dragonHead, hold;
    /** Fahrt unter Riemen (m/s) und Wasserverdrängung (kg) für die Trägheit. */
    public final double rowSpeed, displacement;

    ShipType(String label, double length, double beam, double draft, double freeboard, double sheer, int strakes, int oarPairs,
             double oarSpacing, double oarLength, double oarStart, double sailWidth, double sailHeight, double yardHeight,
             boolean shields, boolean dragonHead, boolean hold, double rowSpeed, double displacement) {
        this.label = label;
        this.length = length;
        this.beam = beam;
        this.draft = draft;
        this.freeboard = freeboard;
        this.sheer = sheer;
        this.strakes = strakes;
        this.oarPairs = oarPairs;
        this.oarSpacing = oarSpacing;
        this.oarLength = oarLength;
        this.oarStart = oarStart;
        this.sailWidth = sailWidth;
        this.sailHeight = sailHeight;
        this.yardHeight = yardHeight;
        this.shields = shields;
        this.dragonHead = dragonHead;
        this.hold = hold;
        this.rowSpeed = rowSpeed;
        this.displacement = displacement;
    }

    /**
     * Längslage der Riemenpforten (m, + vorn). Gleichmäßig um {@link #oarStart}; mit Laderaum stehen sie nur auf den
     * kurzen Decks an Bug und Heck (zwei vorn, eine achtern).
     */
    public double[] oarStations() {
        double[] s = new double[oarPairs];
        if (hold) {
            double h = length / 2;
            for (int k = 0; k < oarPairs; k++) s[k] = k == 0 ? -h * 0.66 : h * (0.52 + 0.12 * (k - 1));
            return s;
        }
        for (int k = 0; k < oarPairs; k++) s[k] = oarStart + (k - (oarPairs - 1) / 2.0) * oarSpacing;
        return s;
    }

    /** Lage des Masts (m, + vorn). */
    public double mastStation() { return hold ? 0.4 : length * 0.02; }

    /** Hat einen Mast mit Rahsegel. */
    public boolean hasSail() { return sailWidth > 0; }

    /** Segelfläche (m²). */
    public double sailArea() { return sailWidth * sailHeight; }
}
