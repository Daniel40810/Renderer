package com.dan.render.reference;

/**
 * Die Szene des Fotos mit Bauten und Gehölzen (seit P12c3, Werkbuch HalligHooge Teil A, 12.13, Datei 81): Gelände und
 * Wasser ({@link Gelaendeszene}, mit der Gestalt), Häuser und Körper ({@link Dreiecksbaum}) und Kronen
 * ({@link Kronen}). Der nächste Treffer der drei zählt.
 */
public final class Bauszene implements Szene {
    public final Gelaendeszene grund;
    public final Dreiecksbaum bauten;
    public final Kronen kronen;

    public Bauszene(Gelaendeszene grund, Dreiecksbaum bauten, Kronen kronen) {
        this.grund = grund;
        this.bauten = bauten;
        this.kronen = kronen;
    }

    @Override public boolean schneide(double[] p, double[] d, double tMax, Treffer aus) {
        boolean hit = grund.schneide(p, d, tMax, aus);
        double t = hit ? aus.t : tMax;
        if (bauten != null && bauten.schneide(p, d, t, aus)) {
            hit = true;
            t = aus.t;
        }
        if (kronen != null && kronen.schneide(p, d, t, aus)) hit = true;
        return hit;
    }

    @Override public double augeHoehe() { return grund.augeHoehe(); }

    @Override public double truebung(double[] p) { return grund.truebung(p); }
}
