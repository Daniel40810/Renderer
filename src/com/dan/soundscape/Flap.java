package com.dan.soundscape;

/**
 * Seit Timmendorf Phase 15: eine Flagge, ein Wimpel oder ein loses Tuch, das im Wind schlägt (Teil „Tuch und
 * Takelage“). Ort (m), Fläche (m²), Frequenz des Flatterns (Hz; NaN = aus dem Wind nach {@link #frequency}) und Stärke
 * 0..1 (1 = flattert frei, 0 = still, etwa eingeholt). Die App setzt hz je Bild, wenn {@code cloth} sie kennt.
 */
public final class Flap {
    public final String name;
    public volatile double x, y, z, area, hz = Double.NaN, strength = 1;

    public Flap(String name, double x, double y, double z, double area) {
        this.name = name; this.x = x; this.y = y; this.z = z; this.area = area;
    }

    /**
     * Frequenz des Flatterns ohne Angabe der App (Hz): 0,6 · U / √A [G, nach der Strouhal-Zahl einer Fahne über ihrer
     * Länge; eine Strandflagge von 1,5 m² schlägt bei 6 m/s knapp dreimal je s], höchstens 12 Hz.
     */
    public static double frequency(double windSpeed, double area) {
        return Math.min(12, 0.6 * Math.max(0, windSpeed) / Math.sqrt(Math.max(0.05, area)));
    }
}
