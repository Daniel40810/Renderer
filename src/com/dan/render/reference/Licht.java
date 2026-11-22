package com.dan.render.reference;

import java.util.SplittableRandom;

/**
 * Eine Scheibe am Himmel als Lichtquelle (Sonne oder Mond, P7b): Richtung (Einheitsvektor in der Szene), Winkelradius,
 * Randverdunklung u (Strahldichte ∝ 1 − u (1 − cos γ) über die Scheibe, γ der Winkel auf der Sternoberfläche).
 * Die Beleuchtungsstärke außerhalb der Luft je Bahn gibt {@link Wellen}; hier steht nur der Faktor (Abstand, Phase).
 * Gezogen wird gleichverteilt im Raumwinkel der Scheibe.
 */
public final class Licht {
    final double[] richtung;
    final double cosRadius, raumwinkel, randU;
    /** Faktor auf die Beleuchtungsstärke der Bahn (Sonne: (AE / Abstand)², Mond: Helligkeit zum Phasenwinkel). */
    final double faktor;
    final boolean sonne;

    public Licht(double[] richtung, double sinRadius, double randU, double faktor, boolean sonne) {
        this.richtung = Vek.norm(richtung);
        this.cosRadius = Math.sqrt(1 - sinRadius * sinRadius);
        this.raumwinkel = 2 * Math.PI * (1 - cosRadius);
        this.randU = randU;
        this.faktor = faktor;
        this.sonne = sonne;
    }

    // ------------------------------------------------------------------ seit P11a: lesen für Stufe 2 (ReferenzKarte)

    /** Richtung (Kopie). */
    public double[] richtung() { return richtung.clone(); }

    /** cos des Winkelradius. */
    public double cosRadius() { return cosRadius; }

    /** Randverdunklung u. */
    public double randU() { return randU; }

    /** Faktor auf die Beleuchtungsstärke der Bahn. */
    public double faktor() { return faktor; }

    /** Sonne (true) oder Mond. */
    public boolean sonne() { return sonne; }

    /** Richtung gleichverteilt auf der Scheibe. */
    double[] ziehe(SplittableRandom r) {
        double c = 1 - r.nextDouble() * (1 - cosRadius);
        return Phasen.umAchse(richtung, c, r);
    }

    /** Liegt d auf der Scheibe? */
    boolean trifft(double[] d) { return Vek.dot(d, richtung) >= cosRadius; }

    /**
     * Strahldichte in Richtung d (auf der Scheibe) als Faktor auf die Beleuchtungsstärke E der Bahn: L = E · s / Ω mit der
     * Randverdunklung s (Mittel 1 über die Scheibe).
     */
    double strahldichte(double[] d) {
        double c = Vek.dot(d, richtung);
        if (c < cosRadius) return 0;
        if (randU == 0) return faktor / raumwinkel;
        // Winkel auf der Scheibe: sin ρ / sin ρ_max = Abstand zur Mitte; cos γ = sqrt(1 − (ρ/ρ_max)²)
        double sr = Math.sqrt(Math.max(0, 1 - c * c)), sm = Math.sqrt(1 - cosRadius * cosRadius);
        double x = Math.min(1, sr / sm), cg = Math.sqrt(1 - x * x);
        return faktor / raumwinkel * (1 - randU * (1 - cg)) / (1 - randU / 3);
    }
}
