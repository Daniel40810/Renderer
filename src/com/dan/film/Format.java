package com.dan.film;

/**
 * Das Bildfenster auf dem Negativ (mm) und die anamorphotische Stauchung. Aus der Fläche je Bildpunkt folgt das Korn
 * (Selwyn: σ·√A fest), aus der Breite der Halbmesser der Halation in Bildpunkten. Seitenverhältnis des Bilds nach dem
 * Entzerren: Breite · Stauchung / Höhe.
 */
public record Format(String name, double widthMm, double heightMm, double squeeze) {
    /** Super 35, 3-perf, für 2,39 : 1 ausgeschnitten (heute üblich, sphärische Objektive). */
    public static final Format SUPER35_239 = new Format("Super 35, 2,39 : 1", 24.89, 10.41, 1);
    /** CinemaScope (seit 1953): ganzes Fenster 21,95 × 18,6 mm, anamorphotisch 2× (1958 der Breitfilm). */
    public static final Format CINEMASCOPE = new Format("CinemaScope, anamorphotisch 2×", 21.95, 18.60, 2);
    /** Academy 1,37 : 1. */
    public static final Format ACADEMY = new Format("Academy 1,37 : 1", 21.95, 16.00, 1);
    /** VistaVision, 8-perf längs (1954). */
    public static final Format VISTAVISION = new Format("VistaVision 8-perf", 37.72, 24.92, 1);

    public double aspect() { return widthMm * squeeze / heightMm; }

    /** Fläche des Negativs (µm²), die ein Bildpunkt eines Bilds deckt, das widthPx breit ist. */
    public double pixelAreaUm2(int widthPx) {
        double px = widthMm * 1000 / widthPx, py = heightMm * 1000 / (widthPx / aspect());
        return px * py;
    }

    @Override public String toString() { return name; }
}
