package com.dan.shutter;

/**
 * Bewegungsunschärfe in Echtzeit nach McGuire, Hennessy, Bukowski und Osman, „A Reconstruction Filter for Plausible Motion
 * Blur“ (I3D 2012), mit den Verbesserungen aus McGuire u. a. 2013 und Jimenez 2014: Jeder Bildpunkt kennt seine Bewegung
 * während der Belichtung (aus der Tiefe und dem Blick des vorigen Bilds; das sind die Bewegungen der Kamera), die Kacheln
 * kennen die größte Bewegung ringsum; jeder Bildpunkt sammelt längs dieser Richtung, was über ihn hinweggestrichen ist –
 * Vordergrund über Hintergrund, Hintergrund hinter dem Rand des bewegten Vordergrunds hervor.
 * <p>Länge: Bewegung im Bild je Sekunde Szene · Belichtungszeit ({@link Shutter#openS}); zwischen zwei Bildern vergeht die
 * Szenenzeit Δt, also ist der Faktor T / Δt auf den Weg zwischen den Bildern. Höchstens 2k Bildpunkte (k die Kachel).
 */
public final class MotionBlur {
    /** An oder aus. */
    public volatile boolean enabled = true;
    /** Kachel (Bildpunkte bei 1 080 Zeilen; mit der Höhe des Bilds mitgezogen) – zugleich die halbe größte Länge. */
    public volatile int tile = 20;
    /** Punkte längs der Bewegung (ungerade). */
    public volatile int samples = 15;
    /** Weichheit des Tiefenvergleichs (m bei 1 m Abstand, wächst mit dem Abstand). */
    public volatile double softZ = 0.06;
    /** Stärke gegen die Physik (1 = wie belichtet). */
    public volatile double gain = 1;

    /** Kachel für ein Bild der Höhe h. */
    public int tileFor(int h) { return Math.max(6, (int) Math.round(tile * h / 1080.0)); }

    /** Faktor auf den Weg zwischen zwei Bildern: Belichtungszeit / Szenenzeit zwischen den Bildern (ohne Bewegung: 0). */
    public double scale(double openS, double dtSceneS) { return dtSceneS > 1e-6 ? gain * openS / dtSceneS : 0; }
}
