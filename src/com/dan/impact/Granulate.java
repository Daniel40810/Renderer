package com.dan.impact;

import java.util.Locale;

/**
 * Der Stoff des Gleitkörpers im Nahfeld: Schüttdichte (kg/m³, nach der Auflockerung), Reibungswinkel innen und am Grund
 * (Grad), Kohäsion (Pa). Im Schnitt fließt er nach Mohr-Coulomb: Wo die Schubspannung die Grenze c + p′ tan φ nicht
 * erreicht, steht er; darüber gleitet er mit genau dieser Spannung (viskoplastisch, implizit gerechnet).
 *
 * @param name     Anzeige
 * @param density  Schüttdichte des aufgelockerten Körpers (kg/m³); Masse je Partikel = density · dx²
 * @param phi      innerer Reibungswinkel (Grad)
 * @param bedPhi   Reibungswinkel am Grund (Grad)
 * @param cohesion Kohäsion (Pa)
 * @param porosity Anteil der Poren am Schüttvolumen; unter Wasser füllen sie sich ({@code Impact.poreTime})
 */
public record Granulate(String name, double density, double phi, double bedPhi, double cohesion, double porosity) {

    /** Ohne Poren. */
    public Granulate(String name, double density, double phi, double bedPhi, double cohesion) { this(name, density, phi, bedPhi, cohesion, 0); }

    /**
     * Der Gleitkörper im Versuch FRITZ01: PP-BaSO₄-Granulat, Korn 2,64 t/m³, Schüttdichte 1,61 t/m³ (39 % Poren),
     * Masse je Meter Breite 98,5 · 10³ t; Reibungswinkel nennt FRITZ01 nicht: innen 43°, am Grund 24° [G nach FRITZ02]; Poren 39 %.
     */
    public static Granulate fritz01() { return new Granulate("Granulat (FRITZ01)", 1610, 43, 24, 0, 0.39); }

    /** Fels ohne Auflockerung wie der Strom aus {@code landslide} (ρ 2 700, φ 35°) [G], am Grund wie im Versuch 24°. */
    public static Granulate rock() { return new Granulate("Fels", 2700, 35, 24, 0); }

    /** Eis (Kalbung): ρ 917, φ 30°, am Grund 15° [G]. */
    public static Granulate ice() { return new Granulate("Eis", 917, 30, 15, 0); }

    public double tanPhi() { return Math.tan(Math.toRadians(phi)); }

    public double tanBed() { return Math.tan(Math.toRadians(bedPhi)); }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s (ρ %.0f kg/m³, φ %.0f°, am Grund %.0f°%s%s)", name, density, phi, bedPhi,
                cohesion > 0 ? String.format(Locale.GERMAN, ", c %.0f kPa", cohesion / 1000) : "",
                porosity > 0 ? String.format(Locale.GERMAN, ", Poren %.0f %%", porosity * 100) : "");
    }
}
