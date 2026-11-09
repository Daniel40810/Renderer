package com.dan.aurora;

/**
 * Das Erdmagnetfeld als exzentrischer Dipol aus den ersten acht Gauß-Koeffizienten des IGRF-13 für 2020 (Alken u. a.
 * 2021), Mittelpunkt und Achse nach Fraser-Smith (1987). Es liefert die magnetische Breite und Länge eines Orts, die
 * Inklination der Feldlinien, die Peilung zum Pol und die magnetische Ortszeit. Der Dipol trifft die korrigierten
 * geomagnetischen Breiten in Europa und Nordamerika auf etwa ±1,5°; die Deklination des echten Felds weicht davon
 * stärker ab, darum trägt {@link Ort} sie als eigenen Wert.
 */
public final class Magnetfeld {
    /** IGRF-13, Epoche 2020, in nT. */
    static final double G10 = -29404.8, G11 = -1450.9, H11 = 4652.5, G20 = -2499.6, G21 = 2982.0, H21 = -2991.6, G22 = 1677.0, H22 = -734.6;
    /** Erdradius des IGRF in km. */
    public static final double ERDRADIUS = 6371.2;

    /** Mittelpunkt des exzentrischen Dipols in km, erdfest: x zum Schnitt von Äquator und Nullmeridian, y nach 90° Ost, z zum Nordpol. */
    public final double[] mitte = new double[3];
    /** Achse des Dipols, Einheitsvektor zum geomagnetischen Nordpol. */
    public final double[] achse = new double[3];
    /** Bezugsmeridian der magnetischen Länge und die Richtung 90° östlich davon, beide senkrecht zur Achse. */
    private final double[] bezug = new double[3], ost = new double[3];
    /** Pol des zentrierten Dipols (Grad). */
    public final double polBreite, polLaenge;

    public Magnetfeld() {
        double b0q = G10 * G10 + G11 * G11 + H11 * H11, b0 = Math.sqrt(b0q), w3 = Math.sqrt(3);
        double l0 = 2 * G10 * G20 + w3 * (G11 * G21 + H11 * H21);
        double l1 = -G11 * G20 + w3 * (G10 * G21 + G11 * G22 + H11 * H22);
        double l2 = -H11 * G20 + w3 * (G10 * H21 - H11 * G22 + G11 * H22);
        double e = (l0 * G10 + l1 * G11 + l2 * H11) / (4 * b0q);
        mitte[0] = ERDRADIUS * (l1 - G11 * e) / (3 * b0q);
        mitte[1] = ERDRADIUS * (l2 - H11 * e) / (3 * b0q);
        mitte[2] = ERDRADIUS * (l0 - G10 * e) / (3 * b0q);
        achse[0] = -G11 / b0;
        achse[1] = -H11 / b0;
        achse[2] = -G10 / b0;
        polBreite = Math.toDegrees(Math.asin(achse[2]));
        polLaenge = Math.toDegrees(Math.atan2(achse[1], achse[0]));
        // Bezug: Richtung zum geographischen Nordpol, senkrecht zur Achse gemacht
        double d = achse[2];
        bezug[0] = -d * achse[0];
        bezug[1] = -d * achse[1];
        bezug[2] = 1 - d * achse[2];
        double n = Math.sqrt(bezug[0] * bezug[0] + bezug[1] * bezug[1] + bezug[2] * bezug[2]);
        for (int i = 0; i < 3; i++) bezug[i] /= n;
        ost[0] = achse[1] * bezug[2] - achse[2] * bezug[1];
        ost[1] = achse[2] * bezug[0] - achse[0] * bezug[2];
        ost[2] = achse[0] * bezug[1] - achse[1] * bezug[0];
    }

    /** Ortsvektor eines Punkts auf der Erdoberfläche in km, erdfest. */
    static double[] ortsvektor(double breite, double laenge) {
        double b = Math.toRadians(breite), l = Math.toRadians(laenge);
        return new double[]{ERDRADIUS * Math.cos(b) * Math.cos(l), ERDRADIUS * Math.cos(b) * Math.sin(l), ERDRADIUS * Math.sin(b)};
    }

    /** Magnetische Breite in Grad (exzentrischer Dipol), nördlich positiv. */
    public double breite(double breite, double laenge) {
        double[] p = ortsvektor(breite, laenge);
        double x = p[0] - mitte[0], y = p[1] - mitte[1], z = p[2] - mitte[2];
        double r = Math.sqrt(x * x + y * y + z * z);
        return Math.toDegrees(Math.asin((x * achse[0] + y * achse[1] + z * achse[2]) / r));
    }

    /** Magnetische Länge in Grad, östlich positiv, vom Meridian des geographischen Nordpols aus. */
    public double laenge(double breite, double laenge) {
        double[] p = ortsvektor(breite, laenge);
        double x = p[0] - mitte[0], y = p[1] - mitte[1], z = p[2] - mitte[2];
        return Math.toDegrees(Math.atan2(x * ost[0] + y * ost[1] + z * ost[2], x * bezug[0] + y * bezug[1] + z * bezug[2]));
    }

    /** Magnetische Länge einer Richtung (etwa zur Sonne), ohne den Versatz des Mittelpunkts. */
    double laengeRichtung(double breite, double laenge) {
        double[] p = ortsvektor(breite, laenge);
        return Math.toDegrees(Math.atan2(p[0] * ost[0] + p[1] * ost[1] + p[2] * ost[2], p[0] * bezug[0] + p[1] * bezug[1] + p[2] * bezug[2]));
    }

    /** Inklination der Feldlinien in Grad aus der magnetischen Breite: tan I = 2 tan Λ (Dipol); auf der Nordhalbkugel positiv (abwärts). */
    public static double inklination(double magnetischeBreite) {
        return Math.toDegrees(Math.atan(2 * Math.tan(Math.toRadians(magnetischeBreite))));
    }

    /** Peilung vom Ort zum Pol des zentrierten Dipols in Grad, östlich positiv (Nordhalbkugel: zum Nordpol, sonst zum Südpol). */
    public double peilungPol(double breite, double laenge) {
        double pb = breite >= 0 ? polBreite : -polBreite, pl = breite >= 0 ? polLaenge : polLaenge + 180;
        double b1 = Math.toRadians(breite), b2 = Math.toRadians(pb), dl = Math.toRadians(pl - laenge);
        double y = Math.sin(dl) * Math.cos(b2), x = Math.cos(b1) * Math.sin(b2) - Math.sin(b1) * Math.cos(b2) * Math.cos(dl);
        return Math.toDegrees(Math.atan2(y, x));
    }

    /** Tag im Jahr aus Monat (1..12) und Tag, ohne Schaltjahr. */
    public static int tagImJahr(int monat, int tag) {
        int[] k = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        return k[Math.max(1, Math.min(12, monat)) - 1] + tag;
    }

    /** Deklination der Sonne in Grad am Tag im Jahr (Näherung). */
    public static double sonnenDeklination(int tagImJahr) {
        return -23.44 * Math.cos(2 * Math.PI * (tagImJahr + 10) / 365.0);
    }

    /**
     * Magnetische Ortszeit in Stunden (0..24) für den Ort zur Weltzeit {@code ut} (Stunden) am Tag im Jahr: 12 Uhr, wenn
     * der Ort auf dem magnetischen Meridian des sonnennächsten Punkts liegt (Baker und Wing 1989, mit dem Dipol).
     */
    public double ortszeit(double breite, double laenge, double ut, int tagImJahr) {
        double sonneLaenge = 180 - 15 * ut, sonneBreite = sonnenDeklination(tagImJahr);
        double d = laenge(breite, laenge) - laengeRichtung(sonneBreite, sonneLaenge);
        double mlt = 12 + d / 15;
        mlt %= 24;
        return mlt < 0 ? mlt + 24 : mlt;
    }
}
