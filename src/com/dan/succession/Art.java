package com.dan.succession;

import java.util.Locale;

/**
 * Eine Baumart für die Wiederbewaldung (seit LituyaBay1958 Phase 14, Werkbuch 5.6, 9.7): Wuchs nach Chapman-Richards
 * H(t) = H<sub>∞</sub>·(1 − e<sup>−kt</sup>)<sup>p</sup>, Samen (ab welchem Alter, wie viele, wie weit), Schattentoleranz,
 * Lebensdauer und Anwuchs je Boden. Alle Felder sind öffentlich und lassen sich ändern. Die Vorgaben sind gestaltet [G]
 * nach Lehrbuchwerten für den Küstenregenwald Südostalaskas und gehören in Phase 14a gegen das DSM 2012 (Kronenhöhe im Band
 * nach 54 Jahren) geprüft.
 */
public final class Art {
    public final String name, latin;
    /** Chapman-Richards: Endhöhe (m), Rate (1/Jahr), Form. */
    public double hMax, k, p;
    /** Ab diesem Alter trägt ein Baum Samen. */
    public int reife;
    /** Samendruck eines reifen Bestands (Keimlinge je Platz und Jahr bei voller Saat und bestem Boden). */
    public double saat;
    /** Mittlere Flugweite der Samen (m). */
    public double weite;
    /** Schattentoleranz: 0 Lichtbaum … 1 Schattenbaum. */
    public double schatten;
    /** Lebensdauer (Jahre). */
    public int alter;
    /** Anwuchs je Boden (Faktor 0 … 1): {@link Boden#FELS}, {@link Boden#MINERAL}, {@link Boden#WALDBODEN}. */
    public double[] anwuchs = {0.1, 1, 0.5};
    /** Stammdurchmesser je Höhe (m/m) und Kronenansatz (Anteil der Höhe, für die Kraft der Strömung). */
    public double stamm = 0.02, kroneAb = 0.15;
    /** Fläche der Krone gegen die Strömung bei 40 m Höhe (m²), wächst mit dem Quadrat der Höhe. */
    public double krone = 100;
    /** Laubbaum (für Statistik und Bild). */
    public boolean laub;

    public Art(String name, String latin) {
        this.name = name;
        this.latin = latin;
    }

    /** Höhe nach t Jahren unter freiem Himmel. */
    public double hoehe(double t) { return t <= 0 ? 0 : hMax * Math.pow(1 - Math.exp(-k * t), p); }

    /** Alter, in dem die Art unter freiem Himmel die Höhe h erreicht (Umkehrung von {@link #hoehe}). */
    public double alterBei(double h) {
        if (h <= 0) return 0;
        double f = Math.min(0.999999, h / hMax);
        return -Math.log(1 - Math.pow(f, 1 / p)) / k;
    }

    /**
     * Roterle (<i>Alnus rubra</i>): Pionier auf Schutt und Mineralboden, wächst schnell (11 m nach 10 Jahren), bindet
     * Stickstoff, lichtbedürftig, lebt rund 90 Jahre; Samen leicht und weit [G].
     */
    public static Art roterle() {
        Art a = new Art("Roterle", "Alnus rubra");
        a.hMax = 25; a.k = 0.08; a.p = 1.3; a.reife = 6; a.saat = 0.9; a.weite = 70; a.schatten = 0.1; a.alter = 90;
        a.anwuchs = new double[]{0.12, 1, 0.25};
        a.stamm = 0.018; a.kroneAb = 0.3; a.krone = 70; a.laub = true;
        return a;
    }

    /** Sitka-Fichte (<i>Picea sitchensis</i>): wächst auf Mineralboden mit, mäßig schattentolerant, 35 m nach 54 Jahren [G]. */
    public static Art sitkaFichte() {
        Art a = new Art("Sitka-Fichte", "Picea sitchensis");
        a.hMax = 55; a.k = 0.025; a.p = 1.5; a.reife = 25; a.saat = 0.5; a.weite = 60; a.schatten = 0.45; a.alter = 500;
        a.anwuchs = new double[]{0.03, 0.35, 0.5};
        a.stamm = 0.02; a.kroneAb = 0.15; a.krone = 100;
        return a;
    }

    /** Westliche Hemlocktanne (<i>Tsuga heterophylla</i>): Schattenbaum, keimt am liebsten auf Moder und Totholz [G]. */
    public static Art hemlock() {
        Art a = new Art("Westliche Hemlocktanne", "Tsuga heterophylla");
        a.hMax = 45; a.k = 0.022; a.p = 1.5; a.reife = 25; a.saat = 0.7; a.weite = 50; a.schatten = 0.9; a.alter = 400;
        a.anwuchs = new double[]{0.01, 0.12, 0.9};
        a.stamm = 0.018; a.kroneAb = 0.12; a.krone = 90;
        return a;
    }

    @Override public String toString() { return String.format(Locale.GERMAN, "%s (%s)", name, latin); }
}
