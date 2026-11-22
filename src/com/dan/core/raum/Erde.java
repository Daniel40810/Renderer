package com.dan.core.raum;

/**
 * Die Erde als Bezug (Werkbuch HalligHooge Teil A, 5.1): Ellipsoid GRS80 (ETRS89), die Kugel für Großkreise und
 * Erdkrümmung, Schwere, Coriolis-Parameter. Alles in double, Winkel in Grad, Längen in Metern.
 * <p>
 * <b>Höhenbezug:</b> NHN (DHHN2016), Höhen nach oben positiv. Grund {@code b}, Wasserstand {@code η}, Tiefe
 * {@code h = η − b}. Der Kern rechnet nicht zwischen Höhensystemen um; was nicht in NHN vorliegt, bringt die
 * Aufbereitung dorthin.
 */
public final class Erde {
    /** GRS80: große Halbachse (m), Abplattung, kleine Halbachse (m), erste Exzentrizität (Quadrat und Wert). */
    public static final double A = 6_378_137.0, F = 1 / 298.257222101, B = A * (1 - F), E2 = F * (2 - F), E = Math.sqrt(E2);
    /** Radius der Kugel (m), wie in {@code clipmap}. */
    public static final double R = 6_371_000.0;
    /** Winkelgeschwindigkeit der Erddrehung (rad/s). */
    public static final double OMEGA = 7.292115e-5;
    /** Normschwere (m/s²). */
    public static final double G0 = 9.80665;
    /** Refraktionskoeffizient der Normalatmosphäre und der Radius, mit dem sich Lichtstrahlen und Erde gemeinsam krümmen. */
    public static final double REFRAKTION = 0.13, R_OPTISCH = R / (1 - REFRAKTION);

    /** Coriolis-Parameter f = 2Ω sin φ (1/s). */
    public static double coriolis(double latGrad) { return 2 * OMEGA * Math.sin(Math.toRadians(latGrad)); }

    /** Normalschwere auf dem Ellipsoid nach Somigliana (GRS80), m/s². */
    public static double schwere(double latGrad) {
        double s = Math.sin(Math.toRadians(latGrad)), s2 = s * s;
        return 9.7803267715 * (1 + 0.001931851353 * s2) / Math.sqrt(1 - E2 * s2);
    }

    /** Meridiankrümmungsradius M(φ) in m. */
    public static double meridianRadius(double latGrad) {
        double s = Math.sin(Math.toRadians(latGrad)), w = 1 - E2 * s * s;
        return A * (1 - E2) / (w * Math.sqrt(w));
    }

    /** Querkrümmungsradius N(φ) in m. */
    public static double querRadius(double latGrad) {
        double s = Math.sin(Math.toRadians(latGrad));
        return A / Math.sqrt(1 - E2 * s * s);
    }

    /** Meter je Grad Länge auf der Breite φ (GRS80). */
    public static double meterJeGradLaenge(double latGrad) { return Math.toRadians(1) * querRadius(latGrad) * Math.cos(Math.toRadians(latGrad)); }

    /** Meter je Grad Breite auf der Breite φ (GRS80). */
    public static double meterJeGradBreite(double latGrad) { return Math.toRadians(1) * meridianRadius(latGrad); }

    /** Fläche (m²) des Ellipsoidstreifens zwischen zwei Breiten über {@code dLonGrad} Grad Länge (GRS80, geschlossen). */
    public static double zonenflaeche(double latSuedGrad, double latNordGrad, double dLonGrad) {
        return B * B / 2 * Math.toRadians(dLonGrad) * (zone(latNordGrad) - zone(latSuedGrad));
    }

    private static double zone(double latGrad) {
        double s = Math.sin(Math.toRadians(latGrad));
        return s / (1 - E2 * s * s) + Math.log((1 + E * s) / (1 - E * s)) / (2 * E);
    }

    /** Großkreisabstand auf der Kugel (m), Haversine. */
    public static double grosskreis(double lon1, double lat1, double lon2, double lat2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin((p2 - p1) / 2), b = Math.sin(dl / 2), h = a * a + Math.cos(p1) * Math.cos(p2) * b * b;
        return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** Geodätisch (GRS80) → erdfest kartesisch (m): x zum Nullmeridian am Äquator, z zum Nordpol. */
    public static double[] ecef(double lonGrad, double latGrad, double hoehe) {
        double l = Math.toRadians(lonGrad), p = Math.toRadians(latGrad), s = Math.sin(p), n = A / Math.sqrt(1 - E2 * s * s);
        return new double[] {(n + hoehe) * Math.cos(p) * Math.cos(l), (n + hoehe) * Math.cos(p) * Math.sin(l), (n * (1 - E2) + hoehe) * s};
    }

    /** Erdfest kartesisch → {Länge, Breite (Grad), Höhe über dem Ellipsoid (m)}. */
    public static double[] geodaetisch(double x, double y, double z) {
        double p = Math.hypot(x, y), lat = Math.atan2(z, p * (1 - E2)), h = 0;
        for (int i = 0; i < 8; i++) {
            double s = Math.sin(lat), n = A / Math.sqrt(1 - E2 * s * s);
            h = Math.abs(lat) < 1.2 ? p / Math.cos(lat) - n : z / s - n * (1 - E2);
            lat = Math.atan2(z, p * (1 - E2 * n / (n + h)));
        }
        return new double[] {Math.toDegrees(Math.atan2(y, x)), Math.toDegrees(lat), h};
    }

    /** Geozentrische Breite (Grad) zur geodätischen. */
    public static double geozentrisch(double latGrad) { return Math.toDegrees(Math.atan((1 - E2) * Math.tan(Math.toRadians(latGrad)))); }

    /**
     * Absenkung eines Punkts in der Entfernung r unter die Tangentialebene (m), mit Refraktion: r²/(2R'),
     * R' = R/(1 − 0,13). Wie {@code clipmap.drop}, jetzt im Kern.
     */
    public static double absenkung(double r) { return r * r / (2 * R_OPTISCH); }

    /** Absenkung mit eigenem Krümmungsradius (0 oder kleiner: keine Krümmung). */
    public static double absenkung(double r, double radius) { return radius > 0 ? r * r / (2 * radius) : 0; }

    /** Entfernung der Kimm (m) für ein Auge in der Höhe h über dem Wasser, mit Refraktion. */
    public static double kimmweite(double augeshoehe) { return Math.sqrt(2 * R_OPTISCH * Math.max(0, augeshoehe)); }

    private Erde() { }
}
