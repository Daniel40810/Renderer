package com.dan.fog;

/**
 * Wind von außen (eigene kleine Schnittstelle, Paketvertrag Punkt 4; seit Brocken Phase 5): etwa ein Windfeld über
 * Gelände. Ohne sie ({@code Fog.wind == null}) gilt wie bisher der eine Wind aus {@code windSpeed} und
 * {@code windFromDeg}. Die App verdrahtet etwa mit {@code fog.wind = feld::windAt}.
 */
@FunctionalInterface
public interface Wind {
    /** Wind am Ort (m/s) als {Ost, oben, Süd}; x Ost, y Höhe wie die Szene, z Süd. */
    double[] at(double x, double y, double z);
}
