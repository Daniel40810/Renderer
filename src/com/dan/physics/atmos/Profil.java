package com.dan.physics.atmos;

/**
 * Das Profil der Luft über einem Ort (Werkbuch HalligHooge Teil A, 7.5 und 12.11, P10a): Temperatur, Feuchte, Druck
 * und Brechzahl in {@link #STUFEN} Höhen von 0 bis 100 m, wie das Feld {@code air.n} sie trägt.
 * <p>
 * <b>Höhen</b> [G]: Stufe 0 ist die Oberfläche selbst (z = 0, mit ihrer Temperatur), die Stufen 1–63 liegen
 * geometrisch von 1 cm bis 100 m (Faktor 1,160), weil sich das Profil am Boden mit ln z ändert. Zwischen den Stufen
 * ist die Brechzahl linear in z, ihr Gefälle also stückweise fest; ein Lichtstrahl läuft dort in Bögen.
 * <p>
 * <b>Über 100 m</b> gilt das Gefälle der Normalatmosphäre, dn/dz = −k n / R mit k = 0,13 [G] – dasselbe, mit dem das
 * Bild die Ferne senkt.
 * <p>
 * Kennt nur java.*.
 */
public final class Profil {
    /** Zahl der Höhen, wie im Feldkatalog für {@code air.n}. */
    public static final int STUFEN = 64;
    /** Unterste Höhe über der Oberfläche und oberste Höhe (m). */
    public static final double Z_UNTEN = 0.01, Z_OBEN = 100;
    /** Refraktionskoeffizient und Erdradius (m) für das Gefälle über der Tabelle [G], wie {@code core.raum.Erde}. */
    public static final double REFRAKTION = 0.13, ERDRADIUS = 6_371_000.0;
    private static final double[] Z = new double[STUFEN];

    static {
        for (int k = 1; k < STUFEN; k++) Z[k] = Z_UNTEN * Math.pow(Z_OBEN / Z_UNTEN, (k - 1) / (double) (STUFEN - 2));
        Z[STUFEN - 1] = Z_OBEN;
    }

    /** Höhe der Stufe k über der Oberfläche (m). */
    public static double hoehe(int k) { return Z[k]; }

    /**
     * Temperatur (K), spezifische Feuchte (kg/kg), Druck (Pa) und Brechung n − 1 je Stufe. Die Brechung steht ohne die
     * Eins da: Zwischen zwei Stufen ändert sie sich um 10⁻¹¹ bis 10⁻⁶, das trüge n selbst nicht (und ein Feld in float
     * schon gar nicht: {@code air.n} hält n − 1).
     */
    public final double[] t = new double[STUFEN], q = new double[STUFEN], p = new double[STUFEN], r = new double[STUFEN];

    /**
     * Rechnet Druck und Brechzahl aus Temperatur und Feuchte: der Druck hydrostatisch von der Oberfläche aus
     * (Trapezregel in 1/T_v), die Brechzahl nach Ciddor.
     *
     * @param p0     Druck an der Oberfläche (Pa)
     * @param lambda Wellenlänge (µm)
     * @param co2    Kohlendioxid (µmol/mol)
     */
    public Profil schliesse(double p0, double lambda, double co2) {
        p[0] = p0;
        double tv0 = t[0] * (1 + 0.608 * q[0]);
        for (int k = 1; k < STUFEN; k++) {
            double tv = t[k] * (1 + 0.608 * q[k]);
            p[k] = p[k - 1] * Math.exp(-Luft.G / Luft.RD * (Z[k] - Z[k - 1]) * 0.5 * (1 / tv0 + 1 / tv));
            tv0 = tv;
        }
        for (int k = 0; k < STUFEN; k++) r[k] = Luft.brechungFeucht(lambda, t[k], p[k], q[k], co2);
        return this;
    }

    /** Ein Profil aus einer Temperatur am Boden und einem festen Gefälle dT/dz (K/m), trocken; zur Prüfung. */
    public static Profil ausGefaelle(double t0, double dTdz, double p0, double lambda, double co2) {
        Profil pr = new Profil();
        for (int k = 0; k < STUFEN; k++) pr.t[k] = t0 + dTdz * Z[k];
        return pr.schliesse(p0, lambda, co2);
    }

    /** Ein Profil mit linearer Brechzahl n0 + g z; zur Prüfung. */
    public static Profil linear(double n0, double g) {
        Profil pr = new Profil();
        for (int k = 0; k < STUFEN; k++) pr.r[k] = (n0 - 1) + g * Z[k];
        return pr;
    }

    private static int stufe(double z) {
        if (z <= Z[1]) return 0;
        int k = 1 + (int) (Math.log(z / Z_UNTEN) / Math.log(Z_OBEN / Z_UNTEN) * (STUFEN - 2));
        if (k > STUFEN - 2) k = STUFEN - 2;
        while (k > 0 && z < Z[k]) k--;
        while (k < STUFEN - 2 && z >= Z[k + 1]) k++;
        return k;
    }

    /** Brechzahl in der Höhe z (m) über der Oberfläche; unter 0 der Wert der Oberfläche. */
    public double n(double z) {
        if (z <= 0) return 1 + r[0];
        if (z >= Z_OBEN) return 1 + r[STUFEN - 1] - REFRAKTION * (1 + r[STUFEN - 1]) * (z - Z_OBEN) / ERDRADIUS;
        int k = stufe(z);
        return 1 + (r[k] + (r[k + 1] - r[k]) * (z - Z[k]) / (Z[k + 1] - Z[k]));
    }

    /** Gefälle dn/dz (1/m) in der Höhe z (m); unter 0 null. */
    public double gefaelle(double z) {
        if (z < 0) return 0;
        if (z >= Z_OBEN) return -REFRAKTION * (1 + r[STUFEN - 1]) / ERDRADIUS;
        int k = stufe(z);
        return (r[k + 1] - r[k]) / (Z[k + 1] - Z[k]);
    }

    /** Temperatur (K) in der Höhe z (m), linear zwischen den Stufen; über 100 m der oberste Wert. */
    public double temperatur(double z) {
        if (z <= 0) return t[0];
        if (z >= Z_OBEN) return t[STUFEN - 1];
        int k = stufe(z);
        return t[k] + (t[k + 1] - t[k]) * (z - Z[k]) / (Z[k + 1] - Z[k]);
    }
}
