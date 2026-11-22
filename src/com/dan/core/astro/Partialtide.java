package com.dan.core.astro;

import com.dan.core.zeit.Zeit;

import java.time.Instant;
import java.util.List;

/**
 * Eine Partialtide (Werkbuch HalligHooge Teil A, 5.2 und 7.2): Name, Doodson-Zahlen, Phasenversatz und die
 * Knotenfaktoren f und u (Zyklus 18,61 Jahre). Für die Randwerte aus EOT20 und für die harmonische Analyse.
 * <p>
 * Das astronomische Argument ist V = a·τ + b·s + c·h + d·p + e·N′ + f·p₁ + Versatz, mit der mittleren Mondzeit
 * τ = 15°·UT − s + h, den mittleren Längen von Mond (s) und Sonne (h), dem Perigäum des Monds (p), dem Knoten
 * (N′ = −N) und dem Perihel (p₁). Eine Tide mit Amplitude A und Phase G (Greenwich) ist
 * {@code f · A · cos(V(t) + u − G)}. Der Versatz ist so gewählt, dass die Gleichgewichtstide die Phase 0 hat: bei
 * halbtägigen Tiden A·cos²φ, bei eintägigen A·sin 2φ.
 * <p>
 * Knotenfaktoren als Reihen in N nach Doodson (Pugh 1987, Tab. 4.3); Flachwassertiden aus ihren Eltern
 * (M4 = M2·M2, MS4 = M2·S2, MN4 = M2·N2, M6 = M2·M2·M2). Sonnentiden haben f = 1, u = 0.
 */
public final class Partialtide {
    /** Familien mit gleicher Knotenkorrektur. */
    public enum Knoten { KEINE, MM, MF, O1, K1, J1, M2, K2, M4, MS4, M6 }

    /** Änderung der Grundargumente in Grad je Stunde: τ, s, h, p, N′, p₁. */
    static final double[] RATE = {15.0 - 0.54901653 + 0.04106864, 0.54901653, 0.04106864, 0.00464183, 0.00220641, 0.00000196};

    public final String name;
    /** Faktoren von τ, s, h, p, N′, p₁. */
    private final int[] doodson;
    public final double versatzGrad;
    public final Knoten knoten;
    /** Amplitude der Gleichgewichtstide (m), Richtwert nach Cartwright; ordnet die Tiden nach ihrer Größe. */
    public final double gleichgewicht;

    private Partialtide(String name, double gleichgewicht, Knoten knoten, double versatzGrad, int... doodson) {
        this.name = name;
        this.gleichgewicht = gleichgewicht;
        this.knoten = knoten;
        this.versatzGrad = versatzGrad;
        this.doodson = doodson;
    }

    public static final Partialtide
            M2 = new Partialtide("M2", 0.24408, Knoten.M2, 0, 2, 0, 0, 0, 0, 0),
            S2 = new Partialtide("S2", 0.11355, Knoten.KEINE, 0, 2, 2, -2, 0, 0, 0),
            N2 = new Partialtide("N2", 0.04674, Knoten.M2, 0, 2, -1, 0, 1, 0, 0),
            K2 = new Partialtide("K2", 0.03090, Knoten.K2, 0, 2, 2, 0, 0, 0, 0),
            ZWEI_N2 = new Partialtide("2N2", 0.00618, Knoten.M2, 0, 2, -2, 0, 2, 0, 0),
            T2 = new Partialtide("T2", 0.00664, Knoten.KEINE, 0, 2, 2, -3, 0, 0, 1),
            NU2 = new Partialtide("NU2", 0.00888, Knoten.M2, 0, 2, -1, 2, -1, 0, 0),
            MU2 = new Partialtide("MU2", 0.00745, Knoten.M2, 0, 2, -2, 2, 0, 0, 0),
            K1 = new Partialtide("K1", 0.14192, Knoten.K1, 90, 1, 1, 0, 0, 0, 0),
            O1 = new Partialtide("O1", 0.10090, Knoten.O1, -90, 1, -1, 0, 0, 0, 0),
            P1 = new Partialtide("P1", 0.04696, Knoten.KEINE, -90, 1, 1, -2, 0, 0, 0),
            Q1 = new Partialtide("Q1", 0.01932, Knoten.O1, -90, 1, -2, 0, 1, 0, 0),
            J1 = new Partialtide("J1", 0.00794, Knoten.J1, 90, 1, 2, 0, -1, 0, 0),
            S1 = new Partialtide("S1", 0.00111, Knoten.KEINE, 180, 1, 1, -1, 0, 0, 0),
            MF = new Partialtide("MF", 0.04197, Knoten.MF, 0, 0, 2, 0, 0, 0, 0),
            MM = new Partialtide("MM", 0.02215, Knoten.MM, 0, 0, 1, 0, -1, 0, 0),
            SSA = new Partialtide("SSA", 0.01956, Knoten.KEINE, 0, 0, 0, 2, 0, 0, 0),
            SA = new Partialtide("SA", 0.00312, Knoten.KEINE, 0, 0, 0, 1, 0, 0, 0),
            M4 = new Partialtide("M4", 0, Knoten.M4, 0, 4, 0, 0, 0, 0, 0),
            MS4 = new Partialtide("MS4", 0, Knoten.MS4, 0, 4, 2, -2, 0, 0, 0),
            MN4 = new Partialtide("MN4", 0, Knoten.M4, 0, 4, -1, 0, 1, 0, 0),
            M6 = new Partialtide("M6", 0, Knoten.M6, 0, 6, 0, 0, 0, 0, 0);

    /** Die 17 Partialtiden von EOT20 (Hart-Davis u. a. 2021). */
    public static final List<Partialtide> EOT20 = List.of(ZWEI_N2, J1, K1, K2, M2, M4, MF, MM, N2, O1, P1, Q1, S1, S2, SA, SSA, T2);
    /** Alle Partialtiden, die der Kern kennt. */
    public static final List<Partialtide> ALLE = List.of(M2, S2, N2, K2, ZWEI_N2, T2, NU2, MU2, K1, O1, P1, Q1, J1, S1, MF, MM, SSA, SA, M4, MS4, MN4, M6);

    /** Die Partialtide mit diesem Namen (ohne Groß und Klein) oder null. */
    public static Partialtide nach(String name) {
        for (Partialtide p : ALLE) if (p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    /** Winkelgeschwindigkeit in Grad je Stunde. */
    public double gradJeStunde() {
        double w = 0;
        for (int i = 0; i < 6; i++) w += doodson[i] * RATE[i];
        return w;
    }

    /** Periode in Stunden. */
    public double periodeStunden() { return 360 / gradJeStunde(); }

    /** Zahl der Wellen je Tag: 0 langperiodisch, 1 eintägig, 2 halbtägig, 4 und 6 Flachwasser. */
    public int art() { return doodson[0]; }

    /** Astronomisches Argument V (Grad, 0..360) zum Zeitpunkt, ohne u. */
    public double argumentGrad(Instant t) { return argumentGrad(grundargumente(t)); }

    /** Astronomisches Argument V (Grad, 0..360) aus fertigen {@link #grundargumente} – für lange Reihen. */
    public double argumentGrad(double[] grund) {
        double v = versatzGrad;
        for (int i = 0; i < 6; i++) v += doodson[i] * grund[i];
        return FAngle.norm360(v);
    }

    /** Knotenfaktor f der Amplitude zum Zeitpunkt. */
    public double f(Instant t) { return fu(knotenlaenge(t))[0]; }

    /** Knotenkorrektur u der Phase (Grad) zum Zeitpunkt. */
    public double uGrad(Instant t) { return fu(knotenlaenge(t))[1]; }

    /** {f, u in Grad} zur Länge N des aufsteigenden Mondknotens (Grad). */
    public double[] fu(double nGrad) { return fu(knoten, nGrad); }

    static double[] fu(Knoten k, double nGrad) {
        double n = Math.toRadians(nGrad), c1 = Math.cos(n), c2 = Math.cos(2 * n), c3 = Math.cos(3 * n), s1 = Math.sin(n), s2 = Math.sin(2 * n), s3 = Math.sin(3 * n);
        switch (k) {
            case MM: return new double[] {1.000 - 0.130 * c1, 0};
            case MF: return new double[] {1.043 + 0.414 * c1, -23.7 * s1 + 2.7 * s2 - 0.4 * s3};
            case O1: return new double[] {1.009 + 0.187 * c1 - 0.015 * c2, 10.8 * s1 - 1.3 * s2 + 0.2 * s3};
            case K1: return new double[] {1.006 + 0.115 * c1 - 0.009 * c2, -8.9 * s1 + 0.7 * s2};
            case J1: return new double[] {1.0129 + 0.1676 * c1 - 0.0170 * c2 + 0.0016 * c3, -12.94 * s1 + 1.34 * s2 - 0.19 * s3};
            case M2: return new double[] {1.000 - 0.037 * c1, -2.1 * s1};
            case K2: return new double[] {1.024 + 0.286 * c1 + 0.008 * c2, -17.7 * s1 + 0.7 * s2};
            case MS4: return fu(Knoten.M2, nGrad);
            case M4: { double[] m = fu(Knoten.M2, nGrad); return new double[] {m[0] * m[0], 2 * m[1]}; }
            case M6: { double[] m = fu(Knoten.M2, nGrad); return new double[] {m[0] * m[0] * m[0], 3 * m[1]}; }
            default: return new double[] {1, 0};
        }
    }

    /** Länge N des aufsteigenden Mondknotens (Grad, 0..360). */
    public static double knotenlaenge(Instant t) { return FAngle.norm360(-grundargumente(t)[4]); }

    /**
     * Die Grundargumente τ, s, h, p, N′, p₁ in Grad (Meeus, Kap. 47 und 25; mittlere Längen zum Äquinoktium des Datums).
     */
    public static double[] grundargumente(Instant t) {
        double c = Zeit.jahrhunderte(t);
        double s = 218.3164477 + 481267.88123421 * c, h = 280.46646 + 36000.76983 * c, p = 83.3532465 + 4069.0137287 * c;
        double n = 125.04452 - 1934.136261 * c, p1 = 282.93735 + 1.71946 * c;
        double ut = ((t.getEpochSecond() % 86_400 + 86_400) % 86_400 + t.getNano() * 1e-9) / 3600;
        return new double[] {FAngle.norm360(15 * ut - s + h), FAngle.norm360(s), FAngle.norm360(h), FAngle.norm360(p), FAngle.norm360(-n), FAngle.norm360(p1)};
    }

    /** Beitrag der Tide zum Pegel (m): f·A·cos(V + u − G). */
    public double wert(Instant t, double amplitude, double phaseGrad) {
        double[] fu = fu(knotenlaenge(t));
        return fu[0] * amplitude * Math.cos(Math.toRadians(argumentGrad(t) + fu[1] - phaseGrad));
    }

    @Override public String toString() { return name; }
}
