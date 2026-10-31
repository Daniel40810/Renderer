package com.dan.reed;

/**
 * Wind von außen (eigene kleine Schnittstelle, Paketvertrag Punkt 4; seit Brocken Phase 5): etwa ein Windfeld über
 * Gelände. Ohne sie ({@code Reed.wind == null}) gilt wie bisher der eine Wind aus {@code windSpeed} und
 * {@code windFromDeg}. Die App verdrahtet etwa mit {@code reed.wind = feld::windAt}.
 */
@FunctionalInterface
public interface Wind {
    /** Wind am Ort (m/s) als {Ost, oben, Süd}; x Ost, y Höhe wie die Szene, z Süd. */
    double[] at(double x, double y, double z);
}
