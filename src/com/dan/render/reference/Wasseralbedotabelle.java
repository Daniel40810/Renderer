package com.dan.render.reference;

import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Wasseralbedo;
import com.dan.core.szene.Wasseroptik;
import com.dan.core.szene.spektrum.Spektrum;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Backt die Fläche der Albedo des Wassers ({@link Wasseralbedo}, P10d, F105 A) mit der Referenz: Himmel über einer
 * Wasserkugel (rau nach Cox und Munk, ohne Säule: ein Pfad, der ins Wasser geht, endet) gegen den Himmel über Kugeln nach
 * Lambert mit 0,06, 0,23 und 0,40. Die Albedo, bei der die Kugel nach Lambert denselben Himmel gibt (stückweise linear
 * zwischen den drei), ist die Fläche.
 * <p>
 * Himmel: Mittel der Leuchtdichte (lineares sRGB) über Richtungen mit gleich verteiltem Azimut und gleich verteilter
 * Höhe von {@link #H_MIN} bis {@link #H_MAX} (dort liegt der Himmel der Ansichten), ohne {@link #UM_SONNE} um die
 * Sonne; Auge {@link #AUGE} über dem Wasser, Sicht {@link #SICHT_KM}. Alle Läufe einer Höhe der Sonne mit derselben
 * Saat (gemeinsamer Zufall), damit das Rauschen im Verhältnis klein bleibt.
 */
public final class Wasseralbedotabelle {
    public static final double[] LAMBERT = {0.06, 0.23, 0.40};
    public static final double H_MIN = 0.2, H_MAX = 25, UM_SONNE = 5, AUGE = 4.7, SICHT_KM = 30, TIEFE = 40;
    /** sin des Radius der Sonne bei 1 AE. */
    public static final double SIN_SONNE = 695_700_000 / 1.495978707e11;
    /** Richtungen je Lauf [G] und Saat. */
    public int proben = 600_000;
    public long saat = 20261118L;

    /** Licht der Sonne in der Höhe h (Grad), im Süden. */
    static Licht sonne(double h) {
        double r = Math.toRadians(h);
        return new Licht(new double[] {0, Math.sin(r), Math.cos(r)}, SIN_SONNE, com.dan.core.szene.Lichtquellen.RAND_U, 1, true);
    }

    /**
     * Mittlerer Himmel (lineares sRGB) über einer Kugel: Lambert mit der Albedo {@code alb} (wind NaN) oder Wasser ohne
     * Säule mit Wind (m/s; {@code alb} ist dann die Albedo des Grunds und zählt nicht). Mit {@code saeule} mit der Säule
     * des Wassers {@code w} (Prüfung).
     */
    public double[] himmel(double hSonne, double alb, double wind, boolean saeule, Wasseroptik w) {
        boolean wasser = !Double.isNaN(wind);
        Ebenen e = wasser ? new Ebenen(-AUGE - TIEFE, -AUGE, new double[] {0.1, 0.1, 0.1}, AUGE) : new Ebenen(-AUGE, Double.NaN, new double[] {alb, alb, alb}, AUGE);
        e.kugel = true;
        Referenz r = new Referenz(e, Atmosphere.forVisibility(SICHT_KM, 0.23), w, wasser ? wind : 0);
        r.ohneSaeule = wasser && !saeule;
        Licht l = sonne(hSonne);
        r.lichter.add(l);
        double cs = Math.cos(Math.toRadians(UM_SONNE));
        int teile = 64, je = proben / teile;
        double[][] sum = new double[teile][4];
        IntStream.range(0, teile).parallel().forEach(t -> {
            Pfadverfolger pv = r.verfolger(saat * 1_000_003L + t * 7919L + Math.round(hSonne * 1000));
            double[] acc = new double[3];
            for (int i = 0; i < je; i++) {
                pv.neueWellen();
                double az = 2 * Math.PI * pv.zufall().nextDouble(), hh = Math.toRadians(H_MIN + (H_MAX - H_MIN) * pv.zufall().nextDouble());
                double[] d = {Math.sin(az) * Math.cos(hh), Math.sin(hh), -Math.cos(az) * Math.cos(hh)};
                if (Vek.dot(d, l.richtung) > cs) continue;
                double[] L = pv.verfolge(new double[3], d);
                java.util.Arrays.fill(acc, 0);
                pv.wl.sammle(L, acc);
                for (int c = 0; c < 3; c++) sum[t][c] += acc[c];
                sum[t][3]++;
            }
        });
        double[] s = new double[4];
        for (double[] q : sum) for (int c = 0; c < 4; c++) s[c] += q[c];
        double[] m = {s[0] / s[3], s[1] / s[3], s[2] / s[3]};
        return r.spektral ? Spektrum.nachRgb(m) : m;
    }

    /** Albedo nach Lambert mit demselben Himmel wie {@code lw}, je Kanal, stückweise linear in den drei Läufen {@code ll}. */
    public static double[] aequivalent(double[] lw, double[][] ll) {
        double[] a = new double[3];
        for (int c = 0; c < 3; c++) {
            int k = lw[c] <= ll[1][c] ? 0 : 1;
            a[c] = LAMBERT[k] + (lw[c] - ll[k][c]) / (ll[k + 1][c] - ll[k][c]) * (LAMBERT[k + 1] - LAMBERT[k]);
        }
        return a;
    }

    /** Die drei Läufe nach Lambert bei einer Höhe der Sonne. */
    public double[][] lambert(double h, Wasseroptik w) {
        double[][] ll = new double[3][];
        for (int k = 0; k < 3; k++) ll[k] = himmel(h, LAMBERT[k], Double.NaN, false, w);
        return ll;
    }

    /** Die Tafel der Fläche [Höhe][Wind][Kanal]; meldet je Höhe eine Zeile. */
    public float[] backe(Consumer<String> log) {
        Wasseroptik w = new Wasseroptik();
        float[] f = new float[Wasseralbedo.NH * Wasseralbedo.NW * 3];
        for (int i = 0; i < Wasseralbedo.NH; i++) {
            long t0 = System.nanoTime();
            double h = Wasseralbedo.HOEHE[i];
            double[][] ll = lambert(h, w);
            StringBuilder z = new StringBuilder(String.format("Sonne %4.1f°:", h));
            for (int j = 0; j < Wasseralbedo.NW; j++) {
                double[] a = aequivalent(himmel(h, 0, Wasseralbedo.WIND[j], false, w), ll);
                for (int c = 0; c < 3; c++) f[(i * Wasseralbedo.NW + j) * 3 + c] = (float) a[c];
                z.append(String.format(" Wind %2.0f: %.3f %.3f %.3f;", Wasseralbedo.WIND[j], a[0], a[1], a[2]));
            }
            log.accept(z + String.format(" (%.0f s)", (System.nanoTime() - t0) / 1e9));
        }
        return f;
    }

    /** Schreibt die Tafel im Format von {@link Wasseralbedo#lade}. */
    public static void schreibe(float[] f, File datei) throws IOException {
        try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(datei)))) {
            d.writeInt(Wasseralbedo.MAGIE);
            d.writeInt(Wasseralbedo.NH);
            d.writeInt(Wasseralbedo.NW);
            for (float v : f) d.writeFloat(v);
        }
    }
}
