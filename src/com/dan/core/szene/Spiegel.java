package com.dan.core.szene;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;

/**
 * Ein Gitter des Wassers für das Bild (P6d, Werkbuch HalligHooge Teil A, 12.7; seit P7a, F52 A, in der gemeinsamen
 * Szene, damit Echtzeitbild und Referenz dieselben Stände lesen): {@code water.eta} und nass oder trocken
 * je Zelle in zwei Ständen des Busses, dazu die Bauwerkskanten. Zellen i + nx · j von Südwest wie {@code core.gitter}.
 * <p>
 * <b>Stände.</b> Gehalten werden die zwei jüngsten (Leitidee 4: das Bild liegt zwischen ihnen, linear in der Zeit wie
 * {@code Bus.lies}). Ein jüngerer Stand ersetzt den älteren; ein Stand vor dem jüngsten (das Archiv rückwärts) setzt
 * neu an, dann gilt er allein. Gleiche Zeit ersetzt den jüngsten.
 * <p>
 * <b>Art je Zelle</b> (für die Karte): Bit 0 und 1 nass im Platz 0 bzw. 1, Bit 2 Bauwerk an der Westkante (zwischen
 * i − 1 und i), Bit 3 an der Südkante (zwischen j − 1 und j). Deich- und Sielkanten trennen gleich: Das Bild mittelt
 * nicht über ein Bauwerk.
 * <p>
 * <b>Schwellen (F47 A).</b> Je Zelle die Schwelle der West- und der Südkante (m NHN): der tiefste Punkt der Kante aus
 * der Subgrid-Packung. Fällt das Wasser über eine Kante (vollkommener Überfall: das Unterwasser steht weniger als 2/3 so
 * hoch über der Schwelle wie das Oberwasser), trennt sie den Spiegel im Bild wie ein Bauwerk. Ohne Packung
 * {@link #KEINE}: dann trennen nur Bauwerke.
 */
public final class Spiegel {
    public static final int NASS0 = 1, NASS1 = 2, WEST = 4, SUED = 8;
    /** Schwelle einer Kante ohne Packung: so tief, dass nie Überfall ist. */
    public static final float KEINE = -1.0e30f;

    public final Gitter gitter;
    public final int zellen;
    /** Art je Zelle (Bits {@link #NASS0}, {@link #NASS1}, {@link #WEST}, {@link #SUED}); lesen nur unter der Sperre des Spiegels. */
    public final int[] art;
    /** Schwellen der West- und Südkante je Zelle (m NHN, F47 A). */
    public final float[] schwelleW, schwelleS;
    /** η je Platz und Zelle (m NHN). */
    public final float[][] eta = new float[2][];
    final double[] zeit = {Double.NaN, Double.NaN};
    /** Platz des jüngeren Stands (−1: noch keiner). */
    public int platzB = -1;
    /** Was seit dem letzten Hochladen neu ist: Plätze (Bit 0, 1) und die Art (für das Echtzeitbild). */
    public int neu;
    public boolean artNeu = true;
    /** Bauwerkskanten in diesem Gitter. */
    public int bauwerke;
    /** Kanten mit Schwelle aus der Packung (F47 A). */
    public int schwellen;

    public Spiegel(Gitter gitter) {
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Wasser: nur Gitter in UTM, nicht " + gitter);
        this.gitter = gitter;
        zellen = gitter.zellen();
        art = new int[zellen];
        schwelleW = new float[zellen];
        schwelleS = new float[zellen];
        java.util.Arrays.fill(schwelleW, KEINE);
        java.util.Arrays.fill(schwelleS, KEINE);
        eta[0] = new float[zellen];
        eta[1] = new float[zellen];
    }

    /**
     * Bauwerke aus {@code bauwerke.txt} eines Gitters, dessen Fenster ab Zelle (i0, j0) dieses Gitter ist (0, 0: dasselbe
     * Gitter). Kanten auf dem Rand dieses Gitters zählen nicht. Gibt die Zahl der gesetzten Kanten zurück.
     */
    public int bauwerke(Gebiet.Bauten b, int i0, int j0) {
        int nx = gitter.nx, ny = gitter.ny, n = 0;
        for (int c = 0; c < zellen; c++) art[c] &= ~(WEST | SUED);
        for (Gebiet.Bauten.Kante k : b.kanten()) {
            int i = k.i() - i0, j = k.j() - j0;
            if (k.querX()) {
                if (i <= 0 || i >= nx || j < 0 || j >= ny) continue;
                art[i + nx * j] |= WEST;
            } else {
                if (i < 0 || i >= nx || j <= 0 || j >= ny) continue;
                art[i + nx * j] |= SUED;
            }
            n++;
        }
        bauwerke = n;
        artNeu = true;
        return n;
    }

    /**
     * Schwellen der Kanten (F47 A) aus der Packung {@code p} des Gitters {@code pg}, dessen Fenster ab Zelle (i0, j0) dieses
     * Gitter ist (0, 0: dasselbe Gitter): je Kante Basis plus tiefste Stufe der Tabelle, in float. Gibt die Zahl der
     * gesetzten Kanten zurück.
     */
    public synchronized int schwellen(Subgrid.Packung p, Gitter pg, int i0, int j0) {
        if (p.zellen() != pg.zellen()) throw new IllegalArgumentException("Schwellen: Packung mit " + p.zellen() + " Zellen zu " + pg);
        int nx = gitter.nx, ny = gitter.ny, n = 0, kx = (pg.nx + 1) * pg.ny;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int pi = i + i0, pj = j + j0, c = i + nx * j;
                schwelleW[c] = schwelleS[c] = KEINE;
                if (pi >= 0 && pj >= 0 && pi <= pg.nx && pj < pg.ny) {
                    schwelleW[c] = schwelle(p, pi + (pg.nx + 1) * pj);
                    n++;
                }
                if (pi >= 0 && pj >= 0 && pi < pg.nx && pj <= pg.ny) {
                    schwelleS[c] = schwelle(p, kx + pi + pg.nx * pj);
                    n++;
                }
            }
        schwellen = n;
        artNeu = true;
        return n;
    }

    /** Tiefster Punkt der Kante k (m NHN) aus der Packung, wie hydro ihn kennt: Basis plus erste Stufe. */
    public static float schwelle(Subgrid.Packung p, int k) {
        return (float) (p.basis()[p.zellen() + 2 * k] + Float.float16ToFloat(p.tabelle()[p.kantenVersatz(k)]));
    }

    /**
     * Ein Stand aus dem Bus: {@code water.eta}, {@code water.wetAge} und {@code water.h}. Nass ist eine Zelle mit
     * wetAge > 0 (hydro zählt sie ab einer mittleren Tiefe von 0,1 mm nass); im ersten Stand eines Laufs ist wetAge
     * überall 0, dann gilt h > 0.
     */
    public void stand(double t, float[] eta, float[] wetAge, float[] h) {
        boolean[] nass = new boolean[zellen];
        for (int c = 0; c < zellen; c++) nass[c] = wetAge[c] > 0 || wetAge[c] == 0 && h != null && h[c] > 0;
        stand(t, eta, nass);
    }

    /** Ein Stand: Wasserstand (m NHN, in einer trockenen Zelle die tiefste Pixelhöhe) und nass je Zelle. */
    public synchronized void stand(double t, float[] eta, boolean[] nass) {
        if (eta.length != zellen || nass.length != zellen) throw new IllegalArgumentException("Wasser " + gitter.name + ": " + eta.length + " Werte statt " + zellen);
        int p;
        boolean beide = false;
        if (platzB < 0 || t < zeit[platzB]) {
            p = 0;
            beide = true;
        } else if (t == zeit[platzB]) p = platzB;
        else p = 1 - platzB;
        schreibe(p, t, eta, nass);
        if (beide) schreibe(1, t, eta, nass);
        platzB = p;
    }

    private void schreibe(int p, double t, float[] e, boolean[] nass) {
        System.arraycopy(e, 0, eta[p], 0, zellen);
        int bit = p == 0 ? NASS0 : NASS1;
        for (int c = 0; c < zellen; c++) art[c] = nass[c] ? art[c] | bit : art[c] & ~bit;
        zeit[p] = t;
        neu |= 1 << p;
        artNeu = true;
    }

    /** Liegt ein Stand vor? */
    public synchronized boolean gesetzt() { return platzB >= 0; }

    /** Platz des älteren Stands (A); bei nur einem Stand sind beide Plätze gleich. */
    public synchronized int platzA() { return 1 - platzB; }

    /** Zeiten der Stände A und B (s). */
    public synchronized double[] zeiten() { return platzB < 0 ? new double[0] : new double[] {zeit[1 - platzB], zeit[platzB]}; }

    /** Gewicht des jüngeren Stands zur Zeit t, in float wie {@code Bus.lies}: (t − t_A) / (t_B − t_A), auf 0 … 1 begrenzt. */
    public synchronized float gewicht(double t) {
        if (platzB < 0) return 1;
        double a = zeit[1 - platzB], b = zeit[platzB];
        double w = b > a ? (t - a) / (b - a) : 1;
        return (float) Math.max(0, Math.min(1, w));
    }

    @Override public String toString() { return "Wasser " + gitter + (bauwerke > 0 ? ", " + bauwerke + " Bauwerkskanten" : "") + (schwellen > 0 ? ", Schwellen" : ""); }
}
