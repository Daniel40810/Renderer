package com.dan.lauf;

import com.dan.core.raum.Blick;

/**
 * Der Schwimmerblick (Seegang Werkbuch, Datei 104, S8d, SF160 A): eine Kamera am Kopf eines Schwimmers im Wasser (Art
 * {@code kopf} der Flotte, {@code Vorgaben.kopf}). Der Kopf folgt der Fläche mit seiner eigenen Trägheit; seine Mitte liegt
 * in ruhigem Wasser im Pegel. Das Auge hebt und senkt sich gegen sie mit dem Schwimmzug [G]: von {@link #VERSATZ_UNTEN} bis
 * {@link #VERSATZ_OBEN} m (SF160: 5 cm unter dem Pegel bis 10 cm darüber), in {@link #ZUG} s. Daraus:
 * <ul>
 * <li><b>Wasserlinie auf der Linse:</b> Die Fläche am Kopf (η und Gefälle aus {@link Flotte#kopfWasser}) als Ebene geschnitten
 * mit der Scheibe vor dem Auge ({@link Linsenglas#BREITE} × {@link Linsenglas#HOEHE}) gibt, wie tief jeder Punkt der
 * Scheibe unter Wasser liegt; was darunter liegt, zeichnet {@code render.water.Unterwasser} (Snellsches Fenster,
 * Totalreflexion, Trübung, Lichtstreifen, Meniskus).</li>
 * <li><b>Auftauchen:</b> Lag die Mitte der Scheibe unter Wasser und taucht wieder auf, bleiben {@link #TROPFEN_AUF} Tropfen
 * zurück ({@link Linsenglas#auftauchen}; bis L6b eine Haut von 0,25 mm, die bis zum nächsten Untertauchen nie zerriss).
 * Liegt die ganze Scheibe unter Wasser, haftet kein Tropfen (die Scheibe ist ganz nass).</li>
 * <li><b>Bild über Wasser:</b> Die Bühne zeichnet die Welt von mindestens {@link #UEBER_FLAECHE} m über der Fläche aus
 * ({@link #augeBild}), damit die Wasserfläche nicht durch das Auge geht; den Teil unter der Linie ersetzt der Blick unter
 * Wasser.</li>
 * </ul>
 * Richtung gegen die Schwimmrichtung des Kopfs (Grad), Neigung gegen die Waagerechte. Der Kopf rollt nicht mit dem Wasser
 * [G: ein Schwimmer hält ihn gerade]; das Gefälle der Fläche kippt die Linie auf der Scheibe.
 */
public final class Schwimmerblick implements Kamera {
    public static final String VERSION = "0.1";
    /** Schwimmzug: Versatz des Auges gegen die Mitte des Kopfs unten und oben (m; die Mitte liegt in ruhigem Wasser im Pegel), Dauer (s) [G]. */
    public static final double VERSATZ_UNTEN = -0.05, VERSATZ_OBEN = 0.10, ZUG = 3.2;
    /** Mitte des Kopfs im Körper (m; {@code Vorgaben.kopf}: Kugel von 0,11 m über dem tiefsten Punkt). */
    static final double[] MITTE = {0, 0, 0.11};
    /** Mindesthöhe des Bilds über der Fläche (m). */
    public static final double UEBER_FLAECHE = 0.01;
    /** Tropfen nach dem Auftauchen [G]. */
    public static final int TROPFEN_AUF = 14;

    private final Flotte flotte;
    public final int nr;
    public double richtung, neigung, bildwinkel = 70;
    /** Schwimmzug an (false: das Auge bleibt {@link #festerVersatz} über der Mitte des Kopfs, für Prüfungen und Bilder). */
    public boolean zug = true;
    public double festerVersatz;
    /** Die Scheibe vor dem Auge. */
    public final Linsenglas linse;
    /** Seit dem letzten Bild: Auge (Ost, Nord, Höhe m NHN), η am Kopf (m NHN), Gefälle (∂η/∂Ost, ∂η/∂Nord), Grund (m NHN). */
    public final double[] auge = new double[3];
    public double eta = Double.NaN, gOst, gNord, grund = Double.NaN;
    /** Seit dem letzten Bild: Tiefe der Mitte der Scheibe unter der Fläche (m; unter Wasser positiv), Zunahme je m nach rechts und oben. */
    public double tiefe = Double.NaN, jeRechts, jeOben = -1;
    /** Zeit der Szene des letzten Bilds (s). */
    public double zeit;
    /** Wie oft die Scheibe überspült wurde. */
    public int ueberspuelt;
    private boolean warUnter, warGanzUnter;
    private double tLinse = Double.NaN;

    public Schwimmerblick(Flotte flotte, int nr) {
        this.flotte = flotte;
        this.nr = nr;
        this.linse = new Linsenglas(4211 + 37 * nr);
    }

    /** Versatz des Auges gegen die Mitte des Kopfs zur Zeit t (m). */
    public double versatz(double t) {
        if (!zug) return festerVersatz;
        double m = 0.5 * (VERSATZ_OBEN + VERSATZ_UNTEN), a = 0.5 * (VERSATZ_OBEN - VERSATZ_UNTEN);
        return m + a * Math.sin(2 * Math.PI * t / ZUG);
    }

    @Override public Blick weiter(double dt, double szenenzeit, Blick jetzt) {
        double[] l = flotte.bildLagen();
        if (l == null || 16 * nr + 15 >= l.length || Double.isNaN(l[16 * nr + 12])) return null;
        int o = 16 * nr;
        double kurs = (Math.toDegrees(Math.atan2(l[o], l[o + 1])) + 360) % 360;
        double[] m = Bordkamera.augeAus(l, nr, MITTE);                     // die Mitte des Kopfs, wie er in diesem Bild liegt
        auge[0] = m[0];
        auge[1] = m[1];
        auge[2] = m[2] + versatz(szenenzeit);
        double[] kw = flotte.kopfWasser;
        if (kw != null && (int) kw[0] == nr) {
            eta = kw[1]; gOst = kw[2]; gNord = kw[3]; grund = kw[5];
        }
        Blick b = jetzt.kopie();
        b.ost = auge[0];
        b.nord = auge[1];
        b.richtung = (kurs + richtung + 360) % 360;
        b.neigung = neigung;
        b.rollen = 0;
        b.bildwinkel = bildwinkel;
        b.hoehe = Double.isNaN(eta) ? auge[2] : Math.max(auge[2], eta + UEBER_FLAECHE);
        // die Scheibe in der Fläche am Kopf (Ebene): Tiefe = η + g·(P − Auge) − P_z
        if (!Double.isNaN(eta)) {
            double[][] bas = b.basis();                                     // Zeilen rechts, oben, vorn im Raum (x Ost, y oben, z Süd)
            double[] r = {bas[0][0], -bas[0][2], bas[0][1]}, u = {bas[1][0], -bas[1][2], bas[1][1]};
            tiefe = eta - auge[2];
            jeRechts = gOst * r[0] + gNord * r[1] - r[2];
            jeOben = gOst * u[0] + gNord * u[1] - u[2];
        }
        boolean unter = tiefe > 0;
        if (!Double.isNaN(tiefe) && tiefe + 0.5 * Linsenglas.BREITE * Math.abs(jeRechts) + 0.5 * Linsenglas.HOEHE * Math.abs(jeOben) > 0)
            linse.unterWasser(tiefe, jeRechts, jeOben);                    // seit L6b2: unter der Linie nimmt das Wasser die Tropfen auf
        boolean ganz = ganzUnter();
        if (ganz && !warGanzUnter) linse.glas.reset();                     // unter Wasser haftet kein Tropfen: das Glas ist ganz nass
        warGanzUnter = ganz;
        if (warUnter && !unter) {
            linse.auftauchen(TROPFEN_AUF, 977L * ueberspuelt + 31L * nr + 7);
            ueberspuelt++;
        }
        warUnter = unter;
        double dtS = Double.isNaN(tLinse) ? 0 : szenenzeit - tLinse;
        linse.schritt(dtS > 0 && dtS < 1 ? Math.min(0.25, dtS) : 0, szenenzeit, auge.clone(), 0, unter ? null : flotte.gischt);
        tLinse = szenenzeit;
        zeit = szenenzeit;
        return b;
    }

    /** Liegt die ganze Scheibe unter Wasser (seit dem letzten Bild)? */
    public boolean ganzUnter() {
        return !Double.isNaN(tiefe) && tiefe - 0.5 * Linsenglas.BREITE * Math.abs(jeRechts) - 0.5 * Linsenglas.HOEHE * Math.abs(jeOben) > 0;
    }

    /** Die Normale der Fläche am Kopf im Raum der Bühne (x Ost, y oben, z Süd). */
    public double[] normale() {
        double n = Math.sqrt(gOst * gOst + gNord * gNord + 1);
        return new double[] {-gOst / n, 1 / n, gNord / n};
    }

    /** Tiefe des Grunds unter der Fläche am Kopf (m; < 0: keiner bekannt). */
    public double grundTiefe() { return Double.isNaN(eta) || Double.isNaN(grund) ? -1 : Math.max(0, eta - grund); }
}
