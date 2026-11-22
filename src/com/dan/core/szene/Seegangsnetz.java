package com.dan.core.szene;

import com.dan.core.gitter.Gitter;

/**
 * Ein Gitter des Seegangs für das Bild (P8b, Werkbuch HalligHooge Teil A, 12.9, F76 A): die Kennwerte aus
 * {@code waves.*} in zwei Ständen, wie {@link Spiegel} für das Wasser (das Bild liegt linear zwischen ihnen; ein
 * jüngerer Stand ersetzt den älteren, ein früherer setzt neu an, gleiche Zeit ersetzt den jüngsten).
 * <p>
 * Je Platz und Zelle sechs Werte ({@link #FELDER}): Hs (m), Tp (s), die Richtung, wohin die Wellen laufen, als
 * Einheitsvektor im Gitter (cos, sin; 0 = Ost, gegen den Uhrzeigersinn), mss und diss (W/m²). Die Richtung kommt als
 * {@code waves.dir} (woher, von Gitternord im Uhrzeigersinn) und wird hier zum Vektor, damit das Bild sie bilinear
 * mitteln kann.
 */
public final class Seegangsnetz {
    public static final int FELDER = 6;
    public final Gitter gitter;
    public final int zellen;
    /** Werte je Platz: [Platz][Feld · zellen + Zelle]. */
    public final float[][] werte = new float[2][];
    final double[] zeit = {Double.NaN, Double.NaN};
    /** Platz des jüngeren Stands (−1: noch keiner). */
    public int platzB = -1;
    /** Was seit dem letzten Hochladen neu ist (Bit 0, 1: Plätze). */
    public int neu;

    public Seegangsnetz(Gitter gitter) {
        if (gitter.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Seegang: nur Gitter in UTM, nicht " + gitter);
        this.gitter = gitter;
        zellen = gitter.zellen();
        werte[0] = new float[FELDER * zellen];
        werte[1] = new float[FELDER * zellen];
    }

    /** Ein Stand: {@code waves.hs}, {@code .tp}, {@code .dir} (woher, Grad), {@code .mss}, {@code .diss}. */
    public synchronized void stand(double t, float[] hs, float[] tp, float[] dir, float[] mss, float[] diss) {
        int p;
        boolean beide = false;
        if (platzB < 0 || t < zeit[platzB]) {
            p = 0;
            beide = true;
        } else if (t == zeit[platzB]) p = platzB;
        else p = 1 - platzB;
        schreibe(p, t, hs, tp, dir, mss, diss);
        if (beide) schreibe(1, t, hs, tp, dir, mss, diss);
        platzB = p;
    }

    private void schreibe(int p, double t, float[] hs, float[] tp, float[] dir, float[] mss, float[] diss) {
        float[] w = werte[p];
        int n = zellen;
        for (int c = 0; c < n; c++) {
            w[c] = hs[c];
            w[n + c] = tp[c];
            // woher (von Gitternord im Uhrzeigersinn) → wohin, mathematisch im Gitter: θ = 270° − dir
            double th = Math.toRadians(270 - dir[c]);
            w[2 * n + c] = hs[c] > 0 ? (float) Math.cos(th) : 0f;
            w[3 * n + c] = hs[c] > 0 ? (float) Math.sin(th) : 0f;
            w[4 * n + c] = mss[c];
            w[5 * n + c] = diss[c];
        }
        zeit[p] = t;
        neu |= 1 << p;
    }

    public synchronized boolean gesetzt() { return platzB >= 0; }

    /** Gewicht des jüngeren Stands zur Zeit t (0 … 1) und der Platz des älteren; {s, platzA}. */
    public synchronized double[] gewicht(double t) {
        if (platzB < 0) return new double[] {0, 0};
        int a = 1 - platzB;
        double ta = zeit[a], tb = zeit[platzB];
        double s = tb > ta ? Math.max(0, Math.min(1, (t - ta) / (tb - ta))) : 1;
        return new double[] {s, a};
    }

    /** Die Zeiten der Plätze (für Prüfungen). */
    public synchronized double[] zeiten() { return zeit.clone(); }
}
