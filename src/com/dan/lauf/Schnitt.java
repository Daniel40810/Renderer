package com.dan.lauf;

import com.dan.render.analysis.Querschnitt;

/**
 * Ein Schnitt durch das Wasser für die Anwendung (seit P11c3, Werkbuch HalligHooge Teil A, 12.12): die Seite von
 * {@link Querschnitt} (render.analysis), die das Projekt sehen darf – es kennt nur {@code com.dan.lauf}. Enden auf den
 * Ecken des Gitters (UTM), Profil und Bilanz als eigene Datensätze.
 */
public final class Schnitt {
    final Querschnitt q;
    /** Enden (UTM) und Länge (m). */
    public final double ostA, nordA, ostB, nordB, laenge;

    Schnitt(Querschnitt q) {
        this.q = q;
        ostA = q.ostA;
        nordA = q.nordA;
        ostB = q.ostB;
        nordB = q.nordB;
        laenge = q.laenge;
    }

    /** Das Profil: Abstand von A (m), Grund (m NHN), Wasserstand (NaN trocken), Geschwindigkeit quer (nach links, m/s), Trübung (g/m³ oder NaN). */
    public record Profil(double[] s, double[] grund, double[] eta, double[] quer, double[] truebung) {
        public int punkte() { return s.length; }

        static Profil aus(Querschnitt.Profil p) { return p == null ? null : new Profil(p.s(), p.grund(), p.eta(), p.quer(), p.truebung()); }
    }

    /** Volumen durch den Schnitt im Fenster (m³): nach links, nach rechts, netto. */
    public record Bilanz(double links, double rechts, double netto) { }

    /** Die Bilanz aus der Reihe Q(t) (Zeiten = Enden der Intervalle, Q = Mittel über das Intervall), Intervalle mit Ende in (von, bis]. */
    public static Bilanz bilanz(double[] zeiten, double[] q, double von, double bis) {
        Querschnitt.Bilanz b = Querschnitt.bilanz(zeiten, q, von, bis);
        return new Bilanz(b.links(), b.rechts(), b.netto());
    }
}
