package com.dan.landslide;

/**
 * Stoff eines Stroms nach Voellmy und Savage-Hutter (Werkbuch 6.2): Dichte (kg/m³), Reibungsbeiwert μ an der Sohle
 * (Coulomb, μ = tan δ), Turbulenzbeiwert ξ (m/s², Voellmy: Widerstand g u² / ξ je Mächtigkeit) und innerer
 * Reibungswinkel φ (Grad) für den Erddruck nach Savage und Hutter. Die Werte der Presets sind übliche Spannen aus der
 * Lawinen- und Bergsturzrechnung (RAMMS, HEIM32-Rückrechnungen) [G].
 */
public final class Material {
    public String name = "Stoff";
    /** Dichte des bewegten Stoffs (kg/m³). */
    public double density = 2500;
    /** Reibungsbeiwert an der Sohle μ = tan δ. */
    public double mu = 0.15;
    /** Turbulenzbeiwert ξ (m/s²); unendlich: nur Coulomb. */
    public double xi = 1000;
    /** Innerer Reibungswinkel φ (Grad) für den Erddruck; NaN: isotrop (k = 1). */
    public double phi = 35;

    public Material() { }

    public Material(String name, double density, double mu, double xi, double phi) {
        this.name = name; this.density = density; this.mu = mu; this.xi = xi; this.phi = phi;
    }

    public Material copy() { return new Material(name, density, mu, xi, phi); }

    /** Bergsturz aus Fels: μ 0,15, ξ 1 000 m/s², φ 35° [G]. */
    public static Material rock() { return new Material("Fels", 2500, 0.15, 1000, 35); }

    /** Eislawine: μ 0,10, ξ 2 000 m/s², φ 30° [G]. */
    public static Material ice() { return new Material("Eis", 900, 0.10, 2000, 30); }

    /** Fließlawine aus Schnee: μ 0,20, ξ 1 500 m/s², φ 30° [G] (RAMMS: μ 0,155–0,3, ξ 1 000–3 000). */
    public static Material snow() { return new Material("Schnee", 300, 0.20, 1500, 30); }

    /** Schutt, Murgang: μ 0,20, ξ 500 m/s², φ 32° [G]. */
    public static Material debris() { return new Material("Schutt", 2000, 0.20, 500, 32); }

    /**
     * Sohlwinkel δ (Grad) aus μ.
     */
    public double delta() { return Math.toDegrees(Math.atan(mu)); }

    /**
     * Erddruckbeiwerte nach Savage und Hutter (1989): k = 2/cos²φ · [1 ∓ √(1 − cos²φ / cos²δ)] − 1; aktiv (−, beim
     * Auseinanderlaufen) und passiv (+, beim Stauchen). Ist φ &lt; δ oder φ NaN: beide 1.
     */
    public double[] earthPressure() {
        if (Double.isNaN(phi)) return new double[]{1, 1};
        double c2p = Math.pow(Math.cos(Math.toRadians(phi)), 2), c2d = Math.pow(Math.cos(Math.atan(mu)), 2);
        double r = 1 - c2p / c2d;
        if (r < 0) return new double[]{1, 1};
        double s = Math.sqrt(r);
        return new double[]{2 / c2p * (1 - s) - 1, 2 / c2p * (1 + s) - 1};
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "%s (ρ %.0f kg/m³, μ %.2f, ξ %.0f m/s², φ %.0f°)", name, density, mu, xi, phi);
    }
}
