package com.dan.render.reference;

import com.dan.core.raum.Blick;
import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Die Referenz Stufe 1 (P7b, Werkbuch HalligHooge Teil A, 8.5 und 12.8; F4, 1.2): ein Pfadverfolger in Java, double,
 * auf dem Prozessor, für ein Bild einer {@link Szene} aus einem {@link Blick}.
 * <ul>
 * <li>Der Bildpunkt ist eine Fläche: Strahlen gleichverteilt über ihn (Box).</li>
 * <li>Zwei unabhängige Hälften der Pfade (gerade und ungerade Nummer), damit das Rauschen gemessen werden kann
 * (Prüfstein 7).</li>
 * <li>Ergebnis linear in cd/m² je Kanal von sRGB (spektral über XYZ), wie das HDR-Bild des Echtzeitbilds ohne
 * Vorbelichtung.</li>
 * <li>Rechnet in allen Kernen (Zeilen parallel), jede Zeile mit eigenem Zufall aus {@link #saat}.</li>
 * </ul>
 */
public final class Referenz {
    public static final String VERSION = "0.2";

    public final Szene szene;
    public final Wellen.Szenenwerte werte;
    public final List<Licht> lichter = new ArrayList<>();
    /** Spektral (F53 A) oder in den drei Kanälen des Echtzeitbilds. */
    public boolean spektral = true;
    /** Wellenlängen je Pfad (spektral; seit P7c einstellbar, Vorgabe 4). */
    public int bahnen = 4;
    /** Dichte der Stöße in der Luft und Anteil der Keule (siehe {@link Pfadverfolger}). */
    public double dichte = 1, keule = 0.25;
    /** Ohne Wassersäule (Zerlegung für F55), siehe {@link Pfadverfolger#ohneSaeule}. */
    public boolean ohneSaeule;
    /** Nur Einfachstreuung in der Luft (Zerlegung P7e), siehe {@link Pfadverfolger#einfach}. */
    public boolean einfach;
    /** Zerlegung des Wassers (P7h), siehe {@link Pfadverfolger#teile}: {@link #bild} füllt dann {@link #teilBild}. */
    public boolean teile;
    /** Nach einem {@link #bild} mit {@link #teile}: [Teil 0 bis 3][Kanal][Bildpunkt], Mittel aller Pfade; sonst null. */
    public double[][][] teilBild;
    /** Neigungsvarianz der Wasseroberfläche (Cox und Munk) und Brechzahl. */
    public double sigma2, eta;
    public long saat = 1;
    /** Suchweite unter Wasser (m), siehe {@link Pfadverfolger#wasserWeite}. */
    public double wasserWeite = Double.POSITIVE_INFINITY;
    /** Seit P10b: gekrümmte Sehstrahlen durch die Luft über dem Wasser (null: gerade im gesenkten Bild). */
    public Luftstrahl luftstrahl;
    /** Gezählte Pfade und Zeit des letzten Bilds (s). */
    public final AtomicLong pfade = new AtomicLong();
    public double sekunden;
    /**
     * Seit P11b (Foto): bricht {@link #bild} ab; Zeilen, die noch nicht begonnen haben, bleiben 0. Das Ergebnis eines
     * abgebrochenen Bilds gilt nicht.
     */
    public volatile boolean abbrechen;

    public Referenz(Szene szene, Atmosphere luft, Wasseroptik wasser, double wind) {
        this.szene = szene;
        this.werte = new Wellen.Szenenwerte(luft, wasser);
        this.sigma2 = Wasseroptik.coxMunk(wind);
        this.eta = wasser.n;
    }

    /** Sonne und Mond zur Zeit t am Ort des Auges (UTM, Zone). */
    public Referenz gestirne(Instant t, double ost, double nord, int zone) {
        Lichtquellen.Stand s = Lichtquellen.bei(t, ost, nord, zone);
        lichter.clear();
        lichter.add(new Licht(s.sonneSicht(), s.sinSonne(), Lichtquellen.RAND_U, s.eSonne() / Lichtquellen.SONNE_LX, true));
        lichter.add(new Licht(s.mondSicht(), s.sinMond(), 0, s.eMond()[0] / (Lichtquellen.SONNE_LX * Lichtquellen.MONDFARBE[0]), false));
        return this;
    }

    /** Ein Pfadverfolger mit eigenem Zufall (für Prüfungen und Fäden). */
    public Pfadverfolger verfolger(long seed) {
        Pfadverfolger p = new Pfadverfolger(szene, new Wellen(spektral, werte, bahnen), lichter, sigma2, eta, seed);
        p.wasserWeite = wasserWeite;
        p.dichte(dichte);
        p.keule = keule;
        p.ohneSaeule = ohneSaeule;
        p.einfach = einfach;
        p.teile = teile;
        return p;
    }

    /**
     * Ein Bild breite × hoehe mit {@code n} Pfaden je Bildpunkt. Ergebnis [Hälfte][Kanal][Bildpunkt] (lineares sRGB in
     * cd/m², Mittel der Hälfte). Zeile 0 oben.
     */
    public double[][][] bild(Blick blick, int breite, int hoehe, int n) {
        Blick b = blick.kopie().groesse(breite, hoehe);
        double[][][] aus = new double[2][3][breite * hoehe];
        double[][][] tb = teile ? new double[4][3][breite * hoehe] : null;
        long t0 = System.nanoTime();
        pfade.set(0);
        IntStream.range(0, hoehe).parallel().forEach(y -> {
            if (abbrechen) return;
            Pfadverfolger pv = verfolger(saat * 1_000_003L + y * 7919L + 17);
            double[] acc = new double[3];
            for (int x = 0; x < breite; x++) {
                double[][] s = new double[2][3], st = tb != null ? new double[4][3] : null;
                for (int i = 0; i < n; i++) {
                    pv.neueWellen();
                    double[] d = b.strahl(x + pv.zufall().nextDouble(), y + pv.zufall().nextDouble());
                    if (luftstrahl != null) d = luftstrahl.gerade(d);
                    double[] L = pv.verfolge(new double[3], d);
                    java.util.Arrays.fill(acc, 0);
                    pv.wl.sammle(L, acc);
                    for (int c = 0; c < 3; c++) s[i & 1][c] += acc[c];
                    if (st != null)
                        for (int t = 0; t < 4; t++) {
                            java.util.Arrays.fill(acc, 0);
                            pv.wl.sammle(pv.LT[t], acc);
                            for (int c = 0; c < 3; c++) st[t][c] += acc[c];
                        }
                }
                if (st != null)
                    for (int t = 0; t < 4; t++) {
                        for (int c = 0; c < 3; c++) st[t][c] /= Math.max(1, n);
                        double[] rgb = spektral ? Spektrum.nachRgb(st[t]) : st[t];
                        for (int c = 0; c < 3; c++) tb[t][c][y * breite + x] = rgb[c];
                    }
                for (int h = 0; h < 2; h++) {
                    double[] v = s[h];
                    int m = h == 0 ? (n + 1) / 2 : n / 2;
                    for (int c = 0; c < 3; c++) v[c] /= Math.max(1, m);
                    double[] rgb = spektral ? Spektrum.nachRgb(v) : v;
                    for (int c = 0; c < 3; c++) aus[h][c][y * breite + x] = rgb[c];
                }
            }
            pfade.addAndGet((long) n * breite);
        });
        sekunden = (System.nanoTime() - t0) / 1e9;
        teilBild = tb;
        return aus;
    }

    /** Mittel der beiden Hälften. */
    public static double[][] mittel(double[][][] h) {
        double[][] m = new double[3][h[0][0].length];
        for (int c = 0; c < 3; c++)
            for (int i = 0; i < m[c].length; i++) m[c][i] = 0.5 * (h[0][c][i] + h[1][c][i]);
        return m;
    }
}
