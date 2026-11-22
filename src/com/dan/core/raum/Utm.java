package com.dan.core.raum;

/**
 * UTM auf GRS80 (ETRS89 / UTM, etwa EPSG:25832) mit den Krüger-Reihen nach Karney 2011 bis zur dritten Ordnung in n
 * (Werkbuch HalligHooge Teil A, 5.1); der Fehler der Reihe liegt in einer Zone unter 0,1 mm. Alles in double.
 * <p>
 * Vorwärts dieselben Formeln wie {@code tools/kartenmaterial/kartenmaterial.py}. Rückwärts folgt die Breite aus der
 * konformen Breite nach Newton (Karney, Gl. 19–21) statt aus der Reihe dritter Ordnung, die dort 0,7 mm danebenliegt.
 */
public final class Utm {
    public static final double K0 = 0.9996, OST0 = 500_000.0;
    private static final double N = Erde.F / (2 - Erde.F), N2 = N * N, N3 = N2 * N;
    /** Radius des Kreises mit dem Umfang des Meridians (m). */
    static final double AA = Erde.A / (1 + N) * (1 + N2 / 4 + N2 * N2 / 64);
    private static final double[] ALPHA = {N / 2 - 2 * N2 / 3 + 5 * N3 / 16, 13 * N2 / 48 - 3 * N3 / 5, 61 * N3 / 240},
            BETA = {N / 2 - 2 * N2 / 3 + 37 * N3 / 96, N2 / 48 + N3 / 15, 17 * N3 / 480};

    /** Mittelmeridian der Zone (Grad). */
    public static double mittelmeridian(int zone) { return zone * 6 - 183; }

    /** Zone zu einer Länge (ohne die Ausnahmen bei Norwegen und Spitzbergen). */
    public static int zone(double lonGrad) { return (int) Math.floor((lonGrad + 180) / 6) + 1; }

    /** Länge, Breite (Grad) → {Ost, Nord} in m. */
    public static double[] vorwaerts(double lonGrad, double latGrad, int zone) {
        double lam = Math.toRadians(lonGrad - mittelmeridian(zone)), s = Math.sin(Math.toRadians(latGrad));
        double t = Math.sinh(atanh(s) - Erde.E * atanh(Erde.E * s));
        double xi0 = Math.atan2(t, Math.cos(lam)), eta0 = atanh(Math.sin(lam) / Math.sqrt(1 + t * t)), xi = xi0, eta = eta0;
        for (int j = 1; j <= 3; j++) {
            xi += ALPHA[j - 1] * Math.sin(2 * j * xi0) * Math.cosh(2 * j * eta0);
            eta += ALPHA[j - 1] * Math.cos(2 * j * xi0) * Math.sinh(2 * j * eta0);
        }
        return new double[] {OST0 + K0 * AA * eta, K0 * AA * xi};
    }

    /** {Ost, Nord} in m → {Länge, Breite} in Grad. */
    public static double[] rueckwaerts(double ost, double nord, int zone) {
        double xi = nord / (K0 * AA), eta = (ost - OST0) / (K0 * AA), xi0 = xi, eta0 = eta;
        for (int j = 1; j <= 3; j++) {
            xi0 -= BETA[j - 1] * Math.sin(2 * j * xi) * Math.cosh(2 * j * eta);
            eta0 -= BETA[j - 1] * Math.cos(2 * j * xi) * Math.sinh(2 * j * eta);
        }
        double c = Math.cos(xi0), sh = Math.sinh(eta0), ts = Math.sin(xi0) / Math.hypot(sh, c);            // Tangens der konformen Breite
        double tau = ts, e2m = 1 - Erde.E2;
        for (int i = 0; i < 6; i++) {
            double w = Math.sqrt(1 + tau * tau), sigma = Math.sinh(Erde.E * atanh(Erde.E * tau / w));
            double ti = tau * Math.sqrt(1 + sigma * sigma) - sigma * w, d = (ts - ti) / Math.sqrt(1 + ti * ti) * (1 + e2m * tau * tau) / (e2m * w);
            tau += d;
            if (Math.abs(d) < 1e-15 * Math.max(1, Math.abs(tau))) break;
        }
        return new double[] {Math.toDegrees(Math.atan2(sh, c)) + mittelmeridian(zone), Math.toDegrees(Math.atan(tau))};
    }

    /**
     * {Meridiankonvergenz (Grad, Gitternord gegen geographisch Nord, nach Osten positiv), Maßstab der Abbildung}
     * am Ort. Strecke im Gitter = Maßstab · Strecke in der Natur; ein Vektor in Gitterrichtung ist um die Konvergenz
     * gegen Ost/Nord gedreht.
     */
    public static double[] faktoren(double lonGrad, double latGrad, int zone) {
        double lam = Math.toRadians(lonGrad - mittelmeridian(zone)), phi = Math.toRadians(latGrad), s = Math.sin(phi), tau = Math.tan(phi);
        double t = Math.sinh(atanh(s) - Erde.E * atanh(Erde.E * s));
        double xi0 = Math.atan2(t, Math.cos(lam)), eta0 = atanh(Math.sin(lam) / Math.sqrt(1 + t * t)), p = 1, q = 0;
        for (int j = 1; j <= 3; j++) {
            p += 2 * j * ALPHA[j - 1] * Math.cos(2 * j * xi0) * Math.cosh(2 * j * eta0);
            q += 2 * j * ALPHA[j - 1] * Math.sin(2 * j * xi0) * Math.sinh(2 * j * eta0);
        }
        double gamma = Math.atan2(t * Math.sin(lam), Math.sqrt(1 + t * t) * Math.cos(lam)) + Math.atan2(q, p);
        double c = Math.cos(lam);
        double k = K0 * AA / Erde.A * Math.hypot(p, q) * Math.sqrt((1 - Erde.E2 * s * s) * (1 + tau * tau) / (t * t + c * c));
        return new double[] {Math.toDegrees(gamma), k};
    }

    private static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    private Utm() { }
}
