package com.dan.physics.auftrieb;

/**
 * Bugschlag als Ereignis (Seegang Werkbuch, S7c, Datei 89, SF111 A) nach Ochi (1964): Ein Schlag gilt, wenn der Boden am
 * Bug erst aus dem Wasser kommt und dann mit einer Geschwindigkeit gegen das Wasser über der Schwelle 0,093 √(g L) wieder
 * eintaucht (L: Länge in der Wasserlinie). Geprüft an einer Stelle {@link #STATION} der halben Länge vor der Mitte, am Boden
 * des Rumpfs dort [G]; das Wasser liefert die Fläche (ψ in der Tiefe 0) und ihre senkrechte Geschwindigkeit.
 */
public final class Bugschlag {
    public static final String VERSION = "0.1";
    /** Schwelle nach Ochi: v ≥ OCHI · √(g L). */
    public static final double OCHI = 0.093;
    /** Stelle der Prüfung als Anteil der halben Länge vor der Mitte [G] (0,8: ein Zehntel der Länge hinter dem Bug). */
    public static final double STATION = 0.8;

    public final double schwelle, x, z;
    /** Ob der Boden am Bug zuletzt über dem Wasser war; Zahl der Schläge; Geschwindigkeit (m/s, abwärts gegen das Wasser) und Zeit des letzten. */
    public boolean aus;
    public int zahl;
    public double letzteV = Double.NaN, letzteT = Double.NaN;
    /** Größte Geschwindigkeit beim Eintauchen gegen das Wasser (m/s), auch unter der Schwelle (Auskunft). */
    public double groessteV;

    private final double[] w = new double[6];

    public Bugschlag(Koerper k) {
        double[] ka = k.form.kasten();
        double halb = 0.5 * (ka[1] - ka[0]);
        x = 0.5 * (ka[0] + ka[1]) + STATION * halb;
        z = k.form instanceof Form.Rumpf r ? r.boden(x) : ka[4];
        schwelle = OCHI * Math.sqrt(Koerper.G * (ka[1] - ka[0]));
    }

    /** Nach einem Schritt zur Zeit t: true, wenn eben ein Schlag war. */
    public boolean pruefe(Koerper k, Wasser wa, double t) {
        double[] p = k.punktWelt(x, 0, z);
        double pegel = wa.pegel(p[0], p[1], t);
        wa.welle(p[0], p[1], 0, t, w);
        double eta = pegel + w[0];
        boolean jetztAus = p[2] > eta;
        boolean schlag = false;
        if (aus && !jetztAus) {
            double v = -(k.geschwindigkeitAn(p)[2] - w[5]);                   // abwärts gegen das Wasser
            groessteV = Math.max(groessteV, v);
            if (v >= schwelle) {
                zahl++;
                letzteV = v;
                letzteT = t;
                schlag = true;
            }
        }
        aus = jetztAus;
        return schlag;
    }
}
