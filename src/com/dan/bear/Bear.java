package com.dan.bear;

import java.util.Locale;

/**
 * Küstenbraunbären als Körper und Verhalten (Phase 18a von LituyaBay1958, Werkbuch 5.14). Kennt kein Gelände: Grund,
 * Wasser, Bäche, Strände und Störer kommen von außen. Diese Klasse hält die Art (Presets, offene Werte), die Gangarten
 * und die Regeln; {@link Sleuth} lässt eine Gruppe nach ihnen leben, {@link BearBody} gibt ihr den Körper,
 * {@code gl/BearGl} zeichnet sie.
 * <ul>
 * <li><b>Körper</b>: schwerer Rumpf mit Schulterbuckel, breiter Kopf, kleine runde Ohren, vier Beine mit Gelenken,
 * vorn lange Krallen ({@link BearBody}); Fell vom dunklen Schokoladenbraun bis Blond, mit hellen Spitzen, nass dunkler
 * und glänzend, im Wind bewegt.</li>
 * <li><b>Gangarten</b> ({@link Gait}): Gehen im Passgang-Takt (Fußfolge hinten links, vorn links, hinten rechts, vorn
 * rechts), Traben mit diagonalen Paaren, Galopp als Rotationsgalopp mit Beugen und Strecken der Wirbelsäule.</li>
 * <li><b>Verhalten</b>: umherziehen, schnüffeln, am Strand nach Muscheln graben, am Bach fischen (Lachse von Juli bis
 * September), fressen, ruhen, sich aufrichten und Witterung nehmen; vor Menschen und Booten erst wachsam, dann Flucht;
 * beim Beben im Galopp bergauf fort. Eine Bärin mit Jungen hält sie dicht bei sich.</li>
 * <li><b>Ereignisse</b>: {@code onCall} (Schnauben, Kieferklappern, Rufe der Jungen) für den Klang, {@code onTrack}
 * (Trittsiegel) für Sand und Schlick, {@code onCatch} (ein Lachs gefangen).</li>
 * </ul>
 * Zahlen mit [G] sind gesetzt, nicht gemessen (Maße und Geschwindigkeiten nach ADF&amp;G und NPS, gerundet).
 */
public final class Bear {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Gangarten mit Fußfolge (Phase je Bein LF, RF, LH, RH), Anteil Stand und Grenze der Geschwindigkeit. */
    public enum Gait {
        STAND(new double[]{0, 0, 0, 0}, 1.0),
        WALK(new double[]{0.25, 0.75, 0.0, 0.5}, 0.62),
        TROT(new double[]{0.0, 0.5, 0.5, 0.0}, 0.46),
        GALLOP(new double[]{0.58, 0.48, 0.0, 0.10}, 0.34);
        public final double[] phase;
        public final double duty;
        Gait(double[] phase, double duty) { this.phase = phase; this.duty = duty; }
    }

    /** Ein Ruf: Ort (m), Art, Stärke 0..1. */
    public enum CallKind { SCHNAUBEN, KIEFER, BRUMMEN, JUNGES }
    @FunctionalInterface public interface Call { void call(double x, double z, CallKind kind, double strength); }

    /** Stand des Packages: 18 = Körper, Gangarten und Verhalten (LituyaBay1958 Phase 18a). */
    public static final int STAGE = 18;

    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Art, Kopf-Rumpf-Länge (m) und Masse (kg) von Männchen, Weibchen, Jungen im ersten Sommer [G]. */
    public String species = "Küstenbraunbär";
    public double maleLength = 2.40, maleMass = 390, femaleLength = 1.95, femaleMass = 220, cubLength = 0.95, cubMass = 25;
    /** Grenzen der Gangarten für ein Tier von {@link #REF} m (m/s) [G]; größere Tiere schneller im selben Gang. */
    public double walkMax = 1.7, trotMax = 4.6, gallopTop = 13.0;
    /** Übliche Geschwindigkeiten (m/s) [G]: gehen, umherziehen beim Schnüffeln, traben, flüchten. */
    public double walkSpeed = 1.25, sniffSpeed = 0.45, trotSpeed = 3.2, fleeSpeed = 11.0;
    /** Ab diesem Abstand eines Menschen wachsam (richtet sich auf), darunter flüchtet er (m) [G]; Boote zählen halb. */
    public double alertDistance = 160, flightDistance = 80;
    /** Nach einer Flucht so lange scheu (s) [G]. */
    public double shyTime = 600;
    /** Lachse im Bach in diesen Monaten (Buckellachs und Silberlachs, Juli bis September) [G]. */
    public int salmonFrom = 7, salmonTo = 9;
    /** Erfolg je Sprung nach einem Lachs, Wartezeit zwischen den Sprüngen (s), Zeit zum Fressen (s) [G]. */
    public double catchChance = 0.25, pounceMin = 40, pounceMax = 180, eatMin = 60, eatMax = 150;
    /** Fell trocknet in so vielen Minuten halb [G]. */
    public double furHalfMinutes = 15;
    /** Höchstzahl der Tiere. */
    public int maxAnimals = 8;
    /** Bezugslänge der Gangarten (m). */
    public static final double REF = 2.2;
    public Call onCall;

    // ------------------------------------------------------------------ Presets
    /** Der Küstenbraunbär (Ursus arctos) an der Küste Südostalaskas: Lachse im Sommer. */
    public static Bear coastalBrown() { return new Bear(); }

    /** Ein Grizzly im Inland: kleiner, ohne Lachsbach [G]. */
    public static Bear grizzly() {
        Bear b = new Bear();
        b.species = "Grizzly"; b.maleLength = 2.0; b.maleMass = 250; b.femaleLength = 1.7; b.femaleMass = 150; b.salmonFrom = 13; b.salmonTo = 13;
        return b;
    }

    // ------------------------------------------------------------------ Regeln
    /** Lachse im Monat (1..12)? */
    public boolean salmon(int month) { return month >= salmonFrom && month <= salmonTo; }

    /** Gangart für eine Geschwindigkeit (m/s) und Länge (m). */
    public Gait gait(double v, double length) {
        double k = Math.sqrt(length / REF);
        if (v < 0.05) return Gait.STAND;
        if (v < walkMax * k) return Gait.WALK;
        if (v < trotMax * k) return Gait.TROT;
        return Gait.GALLOP;
    }

    /** Schrittfrequenz (Doppelschritte je s) in einer Gangart bei v (m/s) und Länge (m) [G]. */
    public static double frequency(Gait g, double v, double length) {
        double k = Math.sqrt(REF / length);
        return k * switch (g) {
            case STAND -> 0;
            case WALK -> 0.60 + 0.28 * v;
            case TROT -> 0.95 + 0.13 * v;
            case GALLOP -> 1.55 + 0.085 * v;
        };
    }

    /**
     * Die Beine im Gang: Schwung und Beugung je Bein in die Haltung p, für den Takt t (0..1, Anteil des Doppelschritts),
     * v (m/s) und Länge L (m). Im Stand läuft die Pfote mit v nach hinten und bleibt auf dem Grund, im Schwung kommt sie
     * im Bogen nach vorn; beides über die Gelenke gelöst ({@link BearBody#solveLeg}). Die Wirbelsäule (Neigung, Buckel) muss schon
     * in p stehen. Gibt die Höhe des Grunds unter der Hüfte zurück (Körperkoordinaten, negativ; Anteil der Länge): so stellt man den Bären auf.
     */
    public static double legs(Gait g, double t, double v, double L, float[] p) {
        if (g == Gait.STAND) {
            return stand(new double[4], new double[4], new boolean[]{true, true, true, true}, p);
        }
        double lift0 = 0;
        if (g == Gait.GALLOP) {
            double ph = 2 * Math.PI * t;
            p[BearBody.ARCH] = (float) (0.06 * Math.sin(ph));
            lift0 = 0.02 * Math.max(0, Math.sin(ph + 1.2));
        }
        double f = frequency(g, v, L), stride = v / Math.max(1e-6, f), half = Math.min(0.26, stride * g.duty / 2 / L);
        double lift = switch (g) { case WALK -> 0.045; case TROT -> 0.06; case GALLOP -> 0.09; default -> 0; };
        double[] dz = new double[4], up = new double[4];
        boolean[] stance = new boolean[4];
        for (int l = 0; l < 4; l++) {
            double u = t + g.phase[l];
            u -= Math.floor(u);
            stance[l] = u < g.duty;
            if (stance[l]) dz[l] = half - 2 * half * u / g.duty;
            else {
                double w = (u - g.duty) / (1 - g.duty);
                dz[l] = -half + 2 * half * (0.5 - 0.5 * Math.cos(Math.PI * w));
                up[l] = lift * Math.sin(Math.PI * w) * (l < 2 ? 1.0 : 0.85);
            }
        }
        return stand(dz, up, stance, p) - lift0;
    }

    /**
     * Pfoten auf eine Ebene: die im Stand um dz (Anteil der Länge) vor ihrem Platz, die übrigen dazu um up höher. Die
     * Ebene liegt so tief wie im Stand mit {@link #REST_BEND}, steigt aber, wo ein Bein sonst nicht hinreicht (die Hüfte
     * steigt im Pendel des Gangs). Gilt in Körperkoordinaten, die Wirbelsäule muss in p stehen. Gibt die Ebene.
     */
    public static double stand(double[] dz, double[] up, boolean[] stance, float[] p) {
        double[] ry = new double[4], rz = new double[4];
        float[] q = p.clone();
        for (int l = 0; l < 4; l++) { q[BearBody.SWING + l] = 0; q[BearBody.BEND + l] = (float) REST_BEND; }
        double plane = -Double.MAX_VALUE;
        for (int l = 0; l < 4; l++) { double[] so = BearBody.sole(l, q); ry[l] = so[1]; rz[l] = so[2]; plane = Math.max(plane, so[1]); }
        // reicht ein Bein im Stand nicht, steigt die Ebene
        for (int l = 0; l < 4; l++) {
            if (!stance[l]) continue;
            for (int k = 0; k < 40; k++) {
                BearBody.solveLeg(l, plane, rz[l] + dz[l], q);
                double[] so = BearBody.sole(l, q);
                if (Math.abs(so[1] - plane) + Math.abs(so[2] - rz[l] - dz[l]) < 0.002) break;
                plane += 0.002;
            }
        }
        for (int l = 0; l < 4; l++) BearBody.solveLeg(l, plane + up[l], rz[l] + dz[l], p);
        return plane;
    }

    /** Beugung der Beine im Stand: etwas gebeugt, damit die Pfote im Gang vor und zurück reicht [G]. */
    public static final double REST_BEND = 0.30;

    /** Zahl der Tiere je Qualitätsstufe (höchstens {@link #maxAnimals}). */
    public int animalsFor(Quality q) {
        double f = switch (q) { case LOW -> 0.4; case MEDIUM -> 0.65; case HIGH -> 0.85; case ULTRA -> 1.0; };
        return Math.max(1, (int) Math.round(maxAnimals * f));
    }

    /** „bear: Stufe 18 · Küstenbraunbär 2,4 m (390 kg) · Flucht 80 m · MEDIUM“ */
    public String describe() {
        return String.format(Locale.GERMAN, "bear: Stufe %d · %s %.1f m (%.0f kg) · Flucht %.0f m · %s", STAGE, species, maleLength, maleMass, flightDistance, quality);
    }
}
