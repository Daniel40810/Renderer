package com.dan.understory;

/**
 * Was unter dem Kronendach eines Küstenregenwalds wächst oder liegt (Werkbuch LituyaBay1958 5.7), mit seinen Ansprüchen
 * an Licht und Feuchte. Licht ist der Anteil des vollen Tageslichts am Boden (0..1), Feuchte 0 (trocken) bis 1 (nass,
 * Quellhang). Die Dichte einer Art an einem Ort ist {@code dichte · Glocke(ln(Licht / lichtOpt) / lichtBreite) ·
 * Glocke((Feuchte − feuchteOpt) / feuchteBreite)}, mit Glocke(u) = exp(−u²/2). Werte nach Beschreibungen [G].
 */
public enum Kind {
    /** Moospolster (Rhytidiadelphus, Hylocomium, Kindbergia): Kissen auf Boden, Steinen und Strünken. */
    MOSS("Moospolster", "Hylocomium splendens u. a.", 0.07, 1.3, 0.85, 0.30, 0.22, 0.35, 0.9, 60),
    /** Farne (Schwertfarn Polystichum munitum, Frauenfarn Athyrium filix-femina): Trichter aus bogigen Wedeln. */
    FERN("Farn", "Polystichum munitum, Athyrium filix-femina", 0.15, 1.0, 0.70, 0.25, 0.15, 0.6, 1.4, 80),
    /** Teufelskeule (Oplopanax horridus): stachelige Stöcke bis 3 m mit riesigen Ahornblättern, rote Beerentrauben; nasse Hänge. */
    DEVILS_CLUB("Teufelskeule", "Oplopanax horridus", 0.12, 0.8, 0.95, 0.15, 0.05, 1.0, 3.0, 120),
    /** Beerensträucher (Heidelbeere Vaccinium ovalifolium, Lachsbeere Rubus spectabilis): in Lücken und am Rand. */
    BERRY("Beerenstrauch", "Vaccinium ovalifolium, Rubus spectabilis", 0.35, 0.9, 0.60, 0.30, 0.09, 0.5, 1.6, 100),
    /** Totholz mit Moos: liegende Stämme, oben bemoost, oft mit jungen Bäumen darauf (Ammenstämme). */
    DEADWOOD("Totholz", "Picea, Tsuga (liegend)", 0.10, 2.0, 0.70, 0.50, 0.004, 4, 15, 250),
    /** Nadelstreu: braune Flecken, wo es zu dunkel und zu trocken für Moos ist (unter dichter Hemlocktanne). */
    LITTER("Nadelstreu", "Picea, Tsuga (Streu)", 0.04, 0.9, 0.35, 0.25, 0.06, 1.0, 3.0, 50),
    /** Bartflechten (Usnea, Alectoria): hängende Bärte an Ästen und Stämmen; an Ankerpunkten der App. */
    LICHEN("Bartflechte", "Usnea, Alectoria", 0.30, 1.2, 0.90, 0.25, 1.0, 0.2, 0.6, 60);

    public final String name, latin;
    /** Licht (0..1) mit der höchsten Dichte und Breite der Glocke in ln(Licht). */
    public final double lightOpt, lightWidth;
    /** Feuchte mit der höchsten Dichte und Breite der Glocke. */
    public final double moistOpt, moistWidth;
    /** Höchste Dichte (je m²; Bartflechte: Anteil der Ankerpunkte). */
    public final double density;
    /** Größe von bis (m): Durchmesser, Länge oder Höhe je Art. */
    public final double sizeMin, sizeMax;
    /** Reichweite (m) bei Qualität HIGH. */
    public final double range;

    Kind(String name, String latin, double lightOpt, double lightWidth, double moistOpt, double moistWidth, double density, double sizeMin, double sizeMax, double range) {
        this.name = name; this.latin = latin; this.lightOpt = lightOpt; this.lightWidth = lightWidth; this.moistOpt = moistOpt; this.moistWidth = moistWidth;
        this.density = density; this.sizeMin = sizeMin; this.sizeMax = sizeMax; this.range = range;
    }

    /** Eignung 0..1 bei Licht (0..1) und Feuchte (0..1). */
    public double suitability(double light, double moisture) {
        double l = Math.log(Math.max(1e-4, light) / lightOpt) / lightWidth, m = (moisture - moistOpt) / moistWidth;
        return Math.exp(-0.5 * (l * l + m * m));
    }

    /** Licht am Boden unter dem Kronendach nach Beer-Lambert: exp(−k · Blattflächenindex). */
    public static double light(double lai, double k) { return Math.exp(-k * Math.max(0, lai)); }

    /** Lichtschwächung der Nadelwälder Südostalaskas (k ≈ 0,45) [G]. */
    public static final double K_CONIFER = 0.45;
}
