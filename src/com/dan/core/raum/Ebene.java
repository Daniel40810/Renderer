package com.dan.core.raum;

/**
 * Die Ebene einer Szene (Werkbuch HalligHooge Teil A, 5.1): ein Ursprung im UTM-Gitter, von dem aus Physik und Bild
 * zählen. Der Szenenraum ist der des Bestands ({@code clipmap}): x = Ost − Ost₀, y = Höhe in m NHN, z = Nord₀ − Nord.
 * <p>
 * Die Ebene ist die Gitterebene der Abbildung, nicht die Tangentialebene der Kugel: Strecken sind um den Maßstab
 * ({@link #massstab}, bei Hooge 0,99961) kürzer als in der Natur, Gitternord ist um die Konvergenz gegen geographisch
 * Nord gedreht. Beides steht hier, damit ein Modul es berücksichtigen kann; über 24 km sind es 9 m.
 * <p>
 * <b>Bild:</b> Die Lage wird in double geführt und erst relativ zum Auge zu float ({@link #relativ}).
 */
public final class Ebene {
    public final int zone;
    /** Ursprung im Gitter (m) und seine geographische Lage (Grad). */
    public final double ost0, nord0, lon0, lat0;
    /** Maßstab der Abbildung und Meridiankonvergenz (Grad) am Ursprung. */
    public final double massstab, konvergenzGrad;
    /** Coriolis-Parameter (1/s) und Schwere (m/s²) am Ursprung. */
    public final double coriolis, schwere;

    public Ebene(int zone, double ost0, double nord0) {
        this.zone = zone;
        this.ost0 = ost0;
        this.nord0 = nord0;
        double[] g = Utm.rueckwaerts(ost0, nord0, zone), f = Utm.faktoren(g[0], g[1], zone);
        lon0 = g[0];
        lat0 = g[1];
        konvergenzGrad = f[0];
        massstab = f[1];
        coriolis = Erde.coriolis(lat0);
        schwere = Erde.schwere(lat0);
    }

    public double x(double ost) { return ost - ost0; }
    public double z(double nord) { return nord0 - nord; }
    public double ost(double x) { return ost0 + x; }
    public double nord(double z) { return nord0 - z; }

    /** Länge, Breite (Grad) → {x, z} im Szenenraum. */
    public double[] ausGrad(double lonGrad, double latGrad) {
        double[] u = Utm.vorwaerts(lonGrad, latGrad, zone);
        return new double[] {x(u[0]), z(u[1])};
    }

    /** {x, z} im Szenenraum → {Länge, Breite} in Grad. */
    public double[] nachGrad(double x, double z) { return Utm.rueckwaerts(ost(x), nord(z), zone); }

    /** Strecke in der Natur (m) zu einer Strecke im Gitter. */
    public double wahr(double gitterstrecke) { return gitterstrecke / massstab; }

    /** Absenkung durch die Erdkrümmung (m, mit Refraktion) für einen Punkt (x, z), gesehen vom Auge. */
    public double absenkung(double x, double z, double augeX, double augeZ) { return Erde.absenkung(wahr(Math.hypot(x - augeX, z - augeZ))); }

    /** Lage relativ zum Auge als float: die Differenz wird in double gebildet und erst dann gekürzt. */
    public static void relativ(double x, double y, double z, double augeX, double augeY, double augeZ, float[] ziel, int ab) {
        ziel[ab] = (float) (x - augeX);
        ziel[ab + 1] = (float) (y - augeY);
        ziel[ab + 2] = (float) (z - augeZ);
    }
}
