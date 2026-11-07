package com.dan.lens;

/**
 * Eine Fläche eines Linsenschnitts in Lichtrichtung: Krümmungsradius (mm, positiv: Mittelpunkt rechts, 0: eben),
 * Abstand zur nächsten Fläche auf der Achse (mm), das Glas dahinter ({@link Glass#AIR} für Luft), freier Halbmesser
 * (mm), ob sie die Blende ist, und die Vergütung (nur an Glas-Luft-Flächen wirksam).
 */
public record Surface(double radius, double thickness, Glass glass, double semiAperture, boolean stop, Coating coating) {

    public static Surface glass(double radius, double thickness, Glass g, double diameter, Coating c) {
        return new Surface(radius, thickness, g, diameter / 2, false, c);
    }

    public static Surface stop(double thickness, double diameter) {
        return new Surface(0, thickness, Glass.AIR, diameter / 2, true, Coating.NONE);
    }

    /** Krümmung 1 / R (mm⁻¹), 0 für eben. */
    public double curvature() { return radius == 0 ? 0 : 1 / radius; }

    Surface scaled(double k) { return new Surface(radius * k, thickness * k, glass, semiAperture * k, stop, coating); }

    Surface withCoating(Coating c) { return new Surface(radius, thickness, glass, semiAperture, stop, c); }
}
