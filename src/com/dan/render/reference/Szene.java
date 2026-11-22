package com.dan.render.reference;

/**
 * Die Flächen der Szene für die Referenz (P7b): Lage relativ zum Auge (x Ost, y oben, z Süd, wie {@code core.raum.Blick}),
 * Erdkrümmung und Refraktion stecken in den Flächen (gesenkt um d² / 2 R_eff wie im Echtzeitbild). Zwei Arten: Boden
 * (Lambert, Albedo RGB) und Wasser (raues Dielektrikum, Wassersäule darunter bis zum Boden).
 */
public interface Szene {
    int BODEN = 1, WASSER = 2;
    /**
     * Seit P12c3: <b>Blatt</b> (Laub in einer Krone: Lambert nach beiden Seiten, Reflexion {@code albedo}, Durchlass
     * {@code durchlass} · albedo) und <b>Spiegel</b> (glatte Fläche über einem Körper: Fresnel nach Schlick mit
     * {@code f0}, Facetten nach Beckmann mit {@code alpha2}, darunter Lambert mit {@code albedo}; Glas der Fenster,
     * Wasser der Gestalt).
     */
    int BLATT = 3, SPIEGEL = 4;
    /** Seit P12c3: woher ein Treffer kommt (für die Prüfung): Gelände und Wasser, Bauten (Häuser, Körper), Gehölze. */
    int AUS_GELAENDE = 0, AUS_BAUTEN = 1, AUS_GEHOELZ = 2;

    /** Ein Treffer: Weg t, Normale (Einheitsvektor, nach oben zur Luft), Art, Albedo (linear RGB) beim Boden, σ² beim Wasser. */
    final class Treffer {
        public double t;
        public final double[] n = new double[3];
        public int art;
        public final double[] albedo = new double[3];
        public double sigma2;
        /**
         * Seit P12c3: beim Boden der Anteil des Wassers der Gestalt (0 … 1, wie {@code gelaende.frag}); ein Pfad aus der
         * Luft nimmt mit dieser Wahrscheinlichkeit den Spiegel des Gestaltwassers statt des Bodens.
         */
        public double spiegel;
        /** Seit P12c3: Spiegel (Schlick f0, Beckmann α²) und Blatt (Durchlass relativ zur Albedo). */
        public double f0, alpha2, durchlass;
        /** Seit P12c3: Leuchten der Fläche (cd/m², linear RGB), wenn {@code leuchtet}. */
        public boolean leuchtet;
        public final double[] leucht = new double[3];
        /** Seit P12c3: Herkunft ({@link #AUS_GELAENDE}, {@link #AUS_BAUTEN}, {@link #AUS_GEHOELZ}). */
        public int aus;
    }

    /** Nächster Treffer auf p + t d mit 0 < t < tMax (Wasser von oben und von unten), sonst false. */
    boolean schneide(double[] p, double[] d, double tMax, Treffer aus);

    /** Höhe des Auges (m NHN), für die Höhe in der Luft. */
    double augeHoehe();

    /**
     * Seit P10d (S8): der Schwebstoff des Wassers (g/m³) am Punkt p (relativ zum Auge), an dem ein Pfad ins Wasser
     * eintritt; NaN: der der ganzen Szene ({@code Wasseroptik.schwebstoff}).
     */
    default double truebung(double[] p) { return Double.NaN; }
}
