package com.dan.physics.hydro;

import java.util.Arrays;

/**
 * Einseitige Kopplung zweier Gitter (Werkbuch HalligHooge Teil A, 12.6, Plan P5): Das feine Gitter bekommt an allen
 * vier Seiten den Wasserstand des groben, das grobe sieht das feine nicht (G0 → G1 → G2). Die Gitter dürfen in
 * verschiedener Lage liegen (G0 geographisch, G1 in UTM); umgerechnet wird über Länge und Breite.
 * <p>
 * Je Randkante des feinen Gitters wird der Wasserstand an der Mitte seiner Geisterzelle (eine halbe Zellweite vor dem
 * Rand) bilinear aus den Mitten der groben Zellen genommen, nur aus nassen. Ist keine der vier Zellen nass, ist die
 * Geisterzelle trocken (Boden gleich Wert, wie {@link Hydro#randBoden}): Aus ihr fließt nichts zu. In der Zeit wird
 * linear zwischen zwei Ständen des groben Gitters gelegt ({@link #stand}); das grobe rechnet darum ein
 * Kopplungsintervall voraus, dann das feine bis zum selben Zeitpunkt.
 * <p>
 * So läuft es auf dem Prozessor und auf der Karte gleich: Der Stand des groben kommt über {@link Stand} (aus einem
 * {@code Hydro} oder von der Karte gelesen), die Randwerte gibt das feine {@code Hydro} wie bisher an die Karte.
 * <p>
 * Seit Steuerhaus Y2 (Werkbuch HalligHooge 153): ein {@link #zusatz} (m) auf den Wasserstand am Rand, in der Zeit linear
 * zwischen zwei gesetzten Werten (der Regler „Aufschlag“ im Live-Lauf). Ohne Zusatz ist alles wie vorher; an einer
 * trockenen Geisterzelle geht der Boden mit, sie bleibt trocken.
 */
public final class Einseitig {
    /** Stand des groben Gitters: Wasserstand und ob die Zelle Wasser hat. */
    public interface Stand {
        double eta(int c);
        boolean nass(int c);

        /** Der Stand eines {@code Hydro} auf dem Prozessor. */
        static Stand von(Hydro h) {
            return new Stand() {
                public double eta(int c) { return h.eta[c]; }
                public boolean nass(int c) { return h.vol[c] > 0; }
            };
        }

        /** Ein Stand aus Feldern (etwa von der Karte gelesen). */
        static Stand aus(float[] eta, float[] vol) {
            return new Stand() {
                public double eta(int c) { return eta[c]; }
                public boolean nass(int c) { return vol[c] > 0; }
            };
        }
    }

    public final Hydro grob, fein;
    /** Je Randkante des feinen (Reihenfolge West j, Ost j, Süd i, Nord i): vier grobe Zellen und ihre Gewichte. */
    private final int[] zelle;
    private final double[] gewicht;
    private final int nRand;
    private double[] alt, neu, bodenAlt, bodenNeu;
    private double tAlt = Double.NaN, tNeu = Double.NaN;
    /** Randkanten, deren Geisterzelle über keiner groben Zelle liegt (außerhalb des groben Gitters). */
    public final int ausserhalb;
    /** Seit Y2: Zusatz auf den Wasserstand am Rand (m) zu zwei Zeiten; linear dazwischen, davor der ältere, danach der jüngere. */
    private double zT0 = Double.NaN, z0, zT1 = Double.NaN, z1;

    public Einseitig(Hydro grob, Hydro fein) {
        this.grob = grob;
        this.fein = fein;
        nRand = 2 * fein.ny + 2 * fein.nx;
        zelle = new int[4 * nRand];
        gewicht = new double[4 * nRand];
        int aus = 0;
        var gf = fein.gitter;
        var gg = grob.gitter;
        for (int r = 0; r < nRand; r++) {
            double x, y;
            if (r < fein.ny) { x = gf.x0 - 0.5 * gf.dx; y = gf.y(r); }
            else if (r < 2 * fein.ny) { x = gf.x1() + 0.5 * gf.dx; y = gf.y(r - fein.ny); }
            else if (r < 2 * fein.ny + fein.nx) { x = gf.x(r - 2 * fein.ny); y = gf.y0 - 0.5 * gf.dy; }
            else { x = gf.x(r - 2 * fein.ny - fein.nx); y = gf.y1() + 0.5 * gf.dy; }
            double[] ll = gf.grad(x, y), p = gg.punkt(ll[0], ll[1]);
            double fi = (p[0] - gg.x0) / gg.dx - 0.5, fj = (p[1] - gg.y0) / gg.dy - 0.5;
            int i0 = (int) Math.floor(fi), j0 = (int) Math.floor(fj);
            double a = fi - i0, b = fj - j0;
            int k = 0;
            for (int dj = 0; dj < 2; dj++)
                for (int di = 0; di < 2; di++) {
                    int i = Math.max(0, Math.min(gg.nx - 1, i0 + di)), j = Math.max(0, Math.min(gg.ny - 1, j0 + dj));
                    zelle[4 * r + k] = i + gg.nx * j;
                    gewicht[4 * r + k] = (di == 0 ? 1 - a : a) * (dj == 0 ? 1 - b : b);
                    k++;
                }
            if (fi < -0.5 || fj < -0.5 || fi > gg.nx - 0.5 || fj > gg.ny - 0.5) aus++;
        }
        ausserhalb = aus;
        for (int s = 0; s < 4; s++) {
            final int a0 = s == Hydro.WEST ? 0 : s == Hydro.OST ? fein.ny : s == Hydro.SUED ? 2 * fein.ny : 2 * fein.ny + fein.nx;
            final int n = s < 2 ? fein.ny : fein.nx;
            final boolean quer = s < 2;
            fein.rand[s] = Hydro.Rand.WASSERSTAND;
            fein.randAbstand[s] = 1;
            fein.randwert[s] = (t, x, y) -> wert(a0 + index(quer, n, x, y), t, false);
            fein.randBoden[s] = (t, x, y) -> wert(a0 + index(quer, n, x, y), t, true);
        }
    }

    private int index(boolean quer, int n, double x, double y) {
        var g = fein.gitter;
        int k = quer ? (int) Math.floor((y - g.y0) / g.dy) : (int) Math.floor((x - g.x0) / g.dx);
        return Math.max(0, Math.min(n - 1, k));
    }

    private double wert(int r, double t, boolean boden) {
        double v = wertOhne(r, t, boden);
        if (Double.isNaN(zT1)) return v;
        double z = zusatz(t);
        // am nassen Rand ist der Boden −10³⁰ und bleibt es; eine trockene Geisterzelle hebt sich mit und bleibt trocken
        return boden && v < -1e29 ? v : v + z;
    }

    private double wertOhne(int r, double t, boolean boden) {
        double[] a = boden ? bodenAlt : alt, b = boden ? bodenNeu : neu;
        if (a == null) throw new IllegalStateException("Einseitig: noch kein Stand des groben Gitters");
        if (!(t > tAlt) || tNeu == tAlt) return a[r];
        if (t >= tNeu) return b[r];
        double w = (t - tAlt) / (tNeu - tAlt);
        return a[r] + w * (b[r] - a[r]);
    }

    /**
     * Seit Y2: setzt den Zusatz (m) zur Zeit t; der vorige gesetzte Wert wird der ältere. Beim ersten Aufruf gilt er von
     * Anfang an. Zwischen den beiden Zeiten linear (wie die Stände).
     */
    public void zusatz(double t, double z) {
        if (Double.isNaN(zT1)) {
            zT0 = t;
            z0 = z;
        } else {
            zT0 = zT1;
            z0 = z1;
        }
        zT1 = t;
        z1 = z;
    }

    /** Seit Y2: der Zusatz zur Zeit t (m); 0, solange keiner gesetzt ist. */
    public double zusatz(double t) {
        if (Double.isNaN(zT1)) return 0;
        if (!(t > zT0) || zT1 == zT0) return t >= zT1 ? z1 : z0;
        if (t >= zT1) return z1;
        return z0 + (t - zT0) / (zT1 - zT0) * (z1 - z0);
    }

    /**
     * Legt einen Stand des groben Gitters zur Zeit t ab; der vorige wird der ältere. Beim ersten Aufruf gelten beide.
     * Der Boden ist −10³⁰, wo eine der vier Zellen nass ist, sonst gleich dem Wert (trocken).
     */
    public void stand(double t, Stand s) {
        double[] w = new double[nRand], bo = new double[nRand];
        for (int r = 0; r < nRand; r++) {
            double sw = 0, se = 0, sa = 0;
            for (int k = 0; k < 4; k++) {
                int c = zelle[4 * r + k];
                double g = gewicht[4 * r + k];
                sa += g * s.eta(c);
                if (s.nass(c)) {
                    sw += g;
                    se += g * s.eta(c);
                }
            }
            if (sw > 0) {
                w[r] = se / sw;
                bo[r] = -1e30;
            } else {
                w[r] = sa;
                bo[r] = sa;
            }
        }
        if (alt == null) {
            alt = w;
            bodenAlt = bo;
            neu = w.clone();
            bodenNeu = bo.clone();
            tAlt = tNeu = t;
            return;
        }
        alt = neu;
        bodenAlt = bodenNeu;
        tAlt = tNeu;
        neu = w;
        bodenNeu = bo;
        tNeu = t;
    }

    /** Die Randwerte des jüngsten Stands (zur Prüfung). */
    public double[] randwerte() { return neu == null ? new double[0] : Arrays.copyOf(neu, nRand); }
}
