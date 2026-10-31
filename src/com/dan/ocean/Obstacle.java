package com.dan.ocean;

/**
 * Ein fester Körper im Wasser, an dem die Wellen brechen: ein senkrechter Zylinder (Turmsockel, Pfahl, Mole aus
 * mehreren Zylindern). Die eigene Form des Packages; die App übersetzt ihre Collider hierher. Um jeden Körper legt
 * sich ein Brandungsring aus Schaum, der mit dem Seegang breiter und dichter wird. Seit Lighthouse Phase 17 lässt
 * {@code ring = false} ihn weg (innen bleibt das Wasser fort): dort zeichnet die App ihre eigene Brandung, etwa aus
 * {@code com.dan.surf}; Tonnen, Pfähle und Schiffe behalten den Ring.
 */
public record Obstacle(double x, double z, double radius, boolean ring) {
    public Obstacle {
        if (!(radius > 0)) throw new IllegalArgumentException("Radius muss positiv sein: " + radius);
    }

    /** Mit Brandungsring (wie bis Lighthouse Phase 16). */
    public Obstacle(double x, double z, double radius) { this(x, z, radius, true); }

    /** Abstand eines Punkts von der Oberfläche (negativ innen). */
    public double distance(double px, double pz) { return Math.hypot(px - x, pz - z) - radius; }
}
