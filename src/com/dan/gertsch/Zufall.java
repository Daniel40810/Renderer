package com.dan.gertsch;

/**
 * Zufall ohne Zustand aus einem Ganzzahl-Hash (PCG-Hash nach Jarzynski und Olano 2020), in Java und GLSL gleich
 * ({@code gl/gertsch.glsl}): Rechnungen in 32 Bit ohne Vorzeichen, in Java mit {@code int} und {@code >>>}. So erzeugen
 * Prozessor und Grafikkarte aus dem seed eines Zweigs dieselben Blätter, ohne dass die Blätter hochgeladen werden.
 * <p>
 * Blatt i eines Zweigs mit seed S: Zug 0 (die Auswahl beim Zusammenfassen) ist {@code eins(S + pcg(i))}, Zug k ist
 * {@code eins(S + pcg(i) + k · 0x9E3779B9)}.
 */
public final class Zufall {
    private Zufall() { }

    /** PCG-Hash: 32 Bit hinein, 32 Bit heraus, gut gemischt. */
    public static int pcg(int v) {
        int s = v * 747796405 + (int) 2891336453L;
        int w = ((s >>> ((s >>> 28) + 4)) ^ s) * 277803737;
        return (w >>> 22) ^ w;
    }

    /** Gleichverteilt in [0, 1) aus den oberen 24 Bit (in float genau, wie in GLSL). */
    public static float eins(int h) { return (pcg(h) >>> 8) * (1f / 16777216f); }

    /** Zug k für Blatt i eines Zweigs mit seed S. */
    public static float zug(int seed, int i, int k) { return eins(seed + pcg(i) + k * 0x9E3779B9); }
}
