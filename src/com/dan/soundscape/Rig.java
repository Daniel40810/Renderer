package com.dan.soundscape;

/**
 * Seit Timmendorf Phase 15: ein Hafen oder Liegeplatz mit Masten (Teil „Tuch und Takelage“). Im Wind schlagen die Fallen
 * gegen die Masten; jedes Fall bei 0,015 · (U − 2,5) je s und etwas mehr mit Seegang [G], die Schläge verteilen sich
 * über den Kreis radius um (x, z). y ist die Höhe des Decks (die Schläge kommen aus 3 bis 11 m darüber), woodShare der
 * Anteil der Holzmasten, strength 0..1 dämpft (etwa im Winter, wenn die Boote an Land stehen).
 */
public final class Rig {
    public final String name;
    public volatile double x, y, z, radius, masts, woodShare, strength = 1;

    public Rig(String name, double x, double y, double z, double radius, double masts) {
        this.name = name; this.x = x; this.y = y; this.z = z; this.radius = radius; this.masts = masts;
    }
}
