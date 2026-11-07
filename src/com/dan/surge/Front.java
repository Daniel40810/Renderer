package com.dan.surge;

/**
 * Die Regeln der Front als Formeln ohne Grafikkarte (aus LituyaBay1958, Werkbuch Welle W3b, W4, W5), gleich denen in
 * {@code gl/surge_front.glsl} und {@code gl/surge_spray.glsl}: für Prüfungen, Entwürfe und Werkzeuge. Alle [G], gestaltet
 * nach den Zeugen von 1958 (eine Wand statt eines Buckels) und Bildern von Brechern; die Rechnung des Wassers selbst
 * kommt aus einem Löser (etwa {@code com.dan.wave}).
 */
public final class Front {
    private Front() { }

    /** Schnelligkeit der Front, mit der die Nachbarn einer Ecke ihre Zeit schätzen (m/s). */
    public static final double SPEED = 60;

    /** Länge des Aufsteilens (m) bei einer Welle von hs m über dem Pegel, Stärke 0..1. */
    public static double steepenLength(double hs, double strength) {
        double d = Math.max(0, Math.min(240, 5 * hs)) * smooth(6, 16, hs) * strength;
        return d * (1 - smooth(45, 80, hs));
    }

    /** Zeit, in der das Aufsteilen hinter der Front auf die Rechnung zurückläuft (s). */
    public static double steepenTime(double length) { return 2 * length / SPEED; }

    /** Anteil der Lippe an einer Ecke: since s nach der Ankunft, h m über dem Pegel, stand höchster Stand über dem Pegel. */
    public static double lipShare(double since, double h, double stand) {
        double hs = Math.max(stand, 1);
        double hrel = Math.max(0, Math.min(1.2, h / hs));
        double glocke = smooth(-0.8, 0.3, since) * (1 - smooth(0.9, 3.2, since));
        return glocke * smooth(6, 30, h) * smooth(0.45, 0.95, hrel);
    }

    /**
     * Höhe der Gischtwand über dem höchsten Stand (m): über Wasser 12 m + 110 m · k (k aus der Welle hw über dem Pegel,
     * k = ((hw − 2)/60)^0,7, höchstens 1), an Land 8 m + 1,3 × Tiefe der Zunge (höchstens 60 m); land 0..1.
     */
    public static double sprayHeight(double hw, double depth, double land) {
        double k = Math.pow(Math.max(0, Math.min(1, (hw - 2) / 60)), 0.7);
        return 12 + 110 * k + (8 + Math.min(1.3 * depth, 52) - (12 + 110 * k)) * land;
    }

    /** Wie weit die Wand age s nach der Ankunft aufgestiegen ist (0..1; in rund 1,5 s). */
    public static double sprayRise(double age) { return 1 - Math.exp(-Math.max(age, 0) / 1.5); }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
}
