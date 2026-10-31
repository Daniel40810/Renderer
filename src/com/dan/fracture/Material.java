package com.dan.fracture;

import java.util.Locale;

/**
 * Der Stoff eines Körpers: Dichte, Steifigkeit, Festigkeit und wie er stößt. {@code fracture} kennt kein Eis und keinen
 * Fels; die Presets sind nur Zahlen. Die Festigkeit gilt je Zellfläche: Zug {@link #tensile}, Druck {@link #compressive}
 * und dazwischen Schub nach Mohr-Coulomb mit dem Reibungswinkel {@link #frictionAngle} – die Kohäsion folgt daraus so,
 * dass ein Pfeiler unter reinem Druck bei {@link #compressive} bricht.
 */
public final class Material {
    public String name = "Stoff";
    /** Dichte (kg/m³) und Elastizitätsmodul (Pa; ändert die Verformung, nicht die Spannung). */
    public double density = 900, young = 9.0e9;
    /** Zug- und Druckfestigkeit (Pa) und innerer Reibungswinkel (Grad). */
    public double tensile = 1.0e6, compressive = 5.0e6, frictionAngle = 30;
    /** Streuung der Festigkeit von Fläche zu Fläche (0 = gleich, 0,2 = ±20 %). */
    public double scatter = 0.2;
    /** Reibung und Stoßzahl beim Stoß mit Boden, Wand und anderen Stücken. */
    public double friction = 0.5, restitution = 0.1;
    /**
     * Seit Sylt Phase 11 (Lockergestein): die Sättigung des Porenraums 0..1, zu der Zug, Druck und Kohäsion gelten (NaN:
     * kein Feuchtemodell, die Festigkeit hängt nicht vom Wasser ab – Eis, Fels, Beton, Klei).
     */
    public double saturation = Double.NaN;
    /**
     * Scheinbare Kohäsion aus der Saugspannung der Menisken (Pa) bei halber Sättigung: trocken und gesättigt null, dazwischen
     * 4·S·(1 − S) davon (feuchter Sand steht, trockener und nasser rieselt) [G nach Lu und Likos 2006].
     */
    public double suction = 0;
    /** Verlust der eigenen Kohäsion und des Zugs bei voller Sättigung (0..1; toniger Boden wird nass weich). */
    public double wetLoss = 0;
    /**
     * Zerfall beim Aufprall (seit Sylt Phase 11): ein Stück zerfällt zu losem Sediment, wenn die Energie je Volumen des
     * Stoßes ½·ρ·Δv² über {@code crumble}·c liegt (0: nie; Eis, Fels, Beton, Klei zerfallen nicht).
     */
    public double crumble = 0;
    /** Auflockerung beim Zerfall: Volumen des losen Sediments je m³ Stück (Sturzkegel). */
    public double bulking = 1;

    /** Kohäsion (Pa) aus Druckfestigkeit und Reibungswinkel: c = σ_c (1 − sin φ) / (2 cos φ). Umgekehrt: {@link #mohrCoulomb}. */
    public double cohesion() {
        double p = Math.toRadians(frictionAngle);
        return compressive * (1 - Math.sin(p)) / (2 * Math.cos(p));
    }

    /** Reibungszahl der Coulomb-Geraden tan φ. */
    public double mu() { return Math.tan(Math.toRadians(frictionAngle)); }

    public Material copy() {
        Material m = new Material();
        m.name = name; m.density = density; m.young = young; m.tensile = tensile; m.compressive = compressive;
        m.frictionAngle = frictionAngle; m.scatter = scatter; m.friction = friction; m.restitution = restitution;
        m.saturation = saturation; m.suction = suction; m.wetLoss = wetLoss; m.crumble = crumble; m.bulking = bulking;
        return m;
    }

    /**
     * Seit Sylt Phase 11: Festigkeit nach Mohr-Coulomb aus der Kohäsion c (Pa), dem Reibungswinkel φ (Grad) und dem Zug
     * (Pa, Abschneiden der Geraden): die Druckfestigkeit folgt als σ_c = 2·c·cos φ / (1 − sin φ), so dass {@link #cohesion}
     * wieder genau c gibt.
     */
    public Material mohrCoulomb(double c, double phiDeg, double tensilePa) {
        frictionAngle = phiDeg;
        double p = Math.toRadians(phiDeg);
        compressive = 2 * c * Math.cos(p) / (1 - Math.sin(p));
        tensile = tensilePa;
        return this;
    }

    /** Hat der Stoff ein Feuchtemodell (Lockergestein)? */
    public boolean moist() { return !Double.isNaN(saturation); }

    /** Anteil der scheinbaren Kohäsion bei der Sättigung S: 4·S·(1 − S). */
    public static double meniscus(double s) { s = Math.max(0, Math.min(1, s)); return 4 * s * (1 - s); }

    /**
     * Zug, Druck und Kohäsion (Pa) bei der Sättigung S (seit Sylt Phase 11). Die eigene Kohäsion c₀ (Zementierung, Ton)
     * nimmt mit dem Wasser ab, c₀·(1 − wetLoss·S); dazu kommt die scheinbare aus den Menisken, suction·4S(1 − S). Der Zug
     * folgt der Kohäsion im selben Verhältnis wie bei der Bezugssättigung, die Druckfestigkeit aus c und φ. Ohne
     * Feuchtemodell die festen Werte.
     */
    public double[] strengthAt(double s) {
        double c0 = cohesion();
        if (!moist()) return new double[]{tensile, compressive, c0};
        double s0 = saturation;
        double own = Math.max(0, c0 - suction * meniscus(s0)) / Math.max(1e-9, 1 - wetLoss * s0);
        double c = own * (1 - wetLoss * Math.max(0, Math.min(1, s))) + suction * meniscus(s);
        double k = c0 > 0 ? c / c0 : 1;
        return new double[]{tensile * k, compressive * k, c};
    }

    /** Faktor der Festigkeit bei der Sättigung S gegen die Bezugssättigung (1 ohne Feuchtemodell). */
    public double wetFactor(double s) {
        if (!moist()) return 1;
        double c0 = cohesion();
        return c0 > 0 ? strengthAt(s)[2] / c0 : 1;
    }

    /** Tempo des Aufpralls (m/s), ab dem ein Stück zerfällt: √(2·crumble·c / ρ); unendlich ohne Zerfall. */
    public double crumbleSpeed() {
        return crumble <= 0 ? Double.POSITIVE_INFINITY : Math.sqrt(2 * crumble * cohesion() / density);
    }

    /**
     * Seit Sylt Phase 11: Geschiebelehm der Saale-Eiszeit am Roten Kliff, oben rot oxidiert, dicht gelagert und stark
     * überkonsolidiert [G]: 2 150 kg/m³, E 100 MPa, c 120 kPa, φ 32°, Zug 30 kPa (σ_c ≈ 450 kPa), erdfeucht (Sättigung
     * 0,6), Saugspannung 10 kPa, nass verliert er die Hälfte seiner eigenen Kohäsion. Spannen nachgesehen für steifen bis
     * halbfesten Geschiebemergel in Norddeutschland: einaxiale Druckfestigkeit 100–500 kPa, φ' 28–35°. Das Federnetz kennt
     * keine Querdehnung: eine senkrechte Wand steht bis σ_c/(ρg) ≈ 21 m; die Wand am Kliff ist 20–30 m hoch, aber geneigt.
     * Zerfällt beim Aufprall ab rund 10,6 m/s (Fall aus 5,7 m), Auflockerung 1,3.
     */
    public static Material geschiebelehm() {
        Material m = new Material().mohrCoulomb(1.2e5, 32, 3.0e4);
        m.name = "Geschiebelehm";
        m.density = 2150;
        m.young = 1.0e8;
        m.scatter = 0.3;
        m.friction = 0.6;
        m.restitution = 0.05;
        m.saturation = 0.6;
        m.suction = 1.0e4;
        m.wetLoss = 0.5;
        m.crumble = 1;
        m.bulking = 1.3;
        return m;
    }

    /**
     * Seit Timmendorf Phase 9: Geschiebemergel der Weichsel-Eiszeit am Brodtener Ufer [G]: jünger und weniger
     * überkonsolidiert als der Lehm am Roten Kliff, mit Sandlinsen und Quellhorizonten, darum weicher und nasser:
     * 2 100 kg/m³, E 60 MPa, c 60 kPa, φ 30°, Zug 15 kPa (σc ≈ 210 kPa), Sättigung 0,75, Saugspannung 8 kPa, nass 60 %
     * weniger eigene Kohäsion (nach langem Regen rutscht das Ufer). Zerfällt beim Aufprall wie der Lehm, Auflockerung 1,25.
     */
    public static Material geschiebemergel() {
        Material m = new Material().mohrCoulomb(6.0e4, 30, 1.5e4);
        m.name = "Geschiebemergel";
        m.density = 2100;
        m.young = 6.0e7;
        m.scatter = 0.35;
        m.friction = 0.55;
        m.restitution = 0.05;
        m.saturation = 0.75;
        m.suction = 8.0e3;
        m.wetLoss = 0.6;
        m.crumble = 1;
        m.bulking = 1.25;
        return m;
    }

    /**
     * Seit Sylt Phase 11: Kaolinsand des Tertiärs unter dem Lehm, weißer bis gelber Quarzsand mit etwas Kaolinit, kaum
     * verkittet [G]: 1 900 kg/m³, E 40 MPa, c 6 kPa bei Sättigung 0,3 (davon der größte Teil aus den Menisken), φ 35°, Zug
     * 1,5 kPa. Trocken und nass rieselt er, erdfeucht steht er senkrecht einige Meter (Saugspannung 6 kPa, nass 80 %
     * weniger eigene Kohäsion). Zerfällt schon beim leisen Aufprall, Auflockerung 1,15.
     */
    public static Material kaolinsand() {
        Material m = new Material().mohrCoulomb(6.0e3, 35, 1.5e3);
        m.name = "Kaolinsand";
        m.density = 1900;
        m.young = 4.0e7;
        m.scatter = 0.3;
        m.friction = 0.65;
        m.restitution = 0.02;
        m.saturation = 0.3;
        m.suction = 6.0e3;
        m.wetLoss = 0.8;
        m.crumble = 0.3;
        m.bulking = 1.15;
        return m;
    }

    /**
     * Seit Sylt Phase 11: Flugsand der Dünen oben auf dem Kliff [G]: 1 650 kg/m³, E 20 MPa, c 1,5 kPa bei Sättigung 0,2
     * (fast nur Menisken), φ 33°, Zug 0,3 kPa. Zerfällt bei jeder Berührung (Zerfall ab 1 m/s), Auflockerung 1,05.
     */
    public static Material flugsand() {
        Material m = new Material().mohrCoulomb(1.5e3, 33, 3.0e2);
        m.name = "Flugsand";
        m.density = 1650;
        m.young = 2.0e7;
        m.scatter = 0.3;
        m.friction = 0.65;
        m.restitution = 0.0;
        m.saturation = 0.2;
        m.suction = 2.0e3;
        m.wetLoss = 1.0;
        m.crumble = 0.5;
        m.bulking = 1.05;
        return m;
    }

    /**
     * Reines Eis im Labor: 917 kg/m³, E 9 GPa, Zug 1,0 MPa, Druck 5 MPa, Reibungswinkel 30° [G]. Nachgesehen
     * (Wikipedia „Ice“, Kennwerte): Zug 5 bis 18 kgf/cm² (0,5 bis 1,8 MPa), Druck 24 bis 60 kgf/cm² (2,4 bis 5,9 MPa);
     * beides hängt stark von Temperatur, Korngröße und Dehnrate ab (Übersicht: Petrovic 2003, „Review: Mechanical
     * properties of ice and snow“).
     */
    public static Material ice() {
        Material m = new Material();
        m.name = "Eis";
        m.density = 917;
        m.young = 9.0e9;
        m.tensile = 1.0e6;
        m.compressive = 5.0e6;
        m.frictionAngle = 30;
        m.friction = 0.15;
        m.restitution = 0.05;
        return m;
    }

    /**
     * Gletschereis im Großen [G]: 900 kg/m³ mit Blasen; Zug 400 kPa, Druck 2,5 MPa. Eine 60 bis 100 m hohe Wand trägt
     * unter ihrem Eigengewicht oben hinter der Kante Zug in der Größenordnung von 0,2 bis 0,25 · ρ·g·H (200 bis 250 kPa
     * bei 95 m Eis): Mit weniger Festigkeit stünde keine Front. Schwächer sind die Spalten (die Kalbung legt sie als
     * Schwächen hinein). Phase 6 nahm 150 kPa; Befund Phase 7: damit zerfiel jeder Abschnitt der Front sofort.
     */
    public static Material glacierIce() {
        Material m = ice();
        m.name = "Gletschereis";
        m.density = 900;
        m.tensile = 4.0e5;
        m.compressive = 2.5e6;
        m.scatter = 0.25;
        return m;
    }

    /** Fels (Granit) für einen Felssturz: 2 700 kg/m³, E 50 GPa, Zug 10 MPa, Druck 150 MPa, φ 45° [G]. */
    public static Material rock() {
        Material m = new Material();
        m.name = "Fels";
        m.density = 2700;
        m.young = 5.0e10;
        m.tensile = 1.0e7;
        m.compressive = 1.5e8;
        m.frictionAngle = 45;
        m.friction = 0.6;
        m.restitution = 0.2;
        return m;
    }

    /**
     * Seit Rungholt Phase 12: Klei eines mittelalterlichen Seedeichs, nass [G]: 1 900 kg/m³, E 30 MPa, Zug 15 kPa, Druck
     * 150 kPa, φ 25° (steifer, toniger Marschboden; die Werte liegen im Bereich undrainierter Scherfestigkeiten von
     * 20 bis 75 kPa, der Zug als kleiner Teil davon). Schwach wird er durch Erosion, nicht durch Spannung allein.
     */
    public static Material klei() {
        Material m = new Material();
        m.name = "Klei";
        m.density = 1900;
        m.young = 3.0e7;
        m.tensile = 1.5e4;
        m.compressive = 1.5e5;
        m.frictionAngle = 25;
        m.scatter = 0.3;
        m.friction = 0.6;
        m.restitution = 0.05;
        return m;
    }

    /** Beton einer Staumauer: 2 400 kg/m³, E 30 GPa, Zug 3 MPa, Druck 30 MPa, φ 37° [G]. */
    public static Material concrete() {
        Material m = new Material();
        m.name = "Beton";
        m.density = 2400;
        m.young = 3.0e10;
        m.tensile = 3.0e6;
        m.compressive = 3.0e7;
        m.frictionAngle = 37;
        m.friction = 0.6;
        m.restitution = 0.15;
        return m;
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s %.0f kg/m³ · Zug %s · Druck %s · φ %.0f°", name, density, pa(tensile), pa(compressive), frictionAngle);
    }

    static String pa(double p) {
        return p >= 1e6 ? String.format(Locale.GERMAN, "%.1f MPa", p / 1e6) : String.format(Locale.GERMAN, "%.0f kPa", p / 1e3);
    }
}
