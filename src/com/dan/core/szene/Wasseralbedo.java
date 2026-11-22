package com.dan.core.szene;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Albedo des Wassers für den Himmel (P10d, F105 A, Werkbuch HalligHooge Teil A, 12.11): die Albedo eines Bodens nach
 * Lambert, über dem die Luft denselben Himmel zeigt wie über dem Wasser. Sie steht für J (die Mehrfachstreuung der Luft),
 * das einen Boden nach Lambert annimmt.
 * <p>
 * Zwei Teile, je Kanal R, G, B:
 * <ul>
 * <li><b>Fläche</b> ({@link #flaeche}): die Spiegelung der rauen Fläche. Wasser ist kein Boden nach Lambert: Es spiegelt
 * den hellen Himmel an der Kimm und die Sonne streifend viel stärker als die 6,6 % für Licht von überall. Gebacken von
 * {@code render.reference.Wasseralbedotabelle}: Referenz über einer Wasserkugel ohne Säule gegen Kugeln nach Lambert
 * (0,06, 0,23, 0,40), je Höhe der Sonne {@link #HOEHE} und Wind {@link #WIND}, abgelegt als {@code wasseralbedo.bin}.</li>
 * <li><b>Säule</b> ({@link #saeule}): das Licht aus dem Wasser, aus der Tafel der Säule ({@link Saeule}) für Licht des
 * Himmels von überall, tiefes Wasser [G], durch die Fläche hinein (1 − {@link #RHO_DIFFUS}) und hinaus (1 − Fresnel).</li>
 * </ul>
 */
public final class Wasseralbedo {
    public static final int MAGIE = 0x57_41_6c_31;
    /** Höhe der Sonne (Grad) und Wind in 10 m (m/s) der Stützstellen. */
    public static final double[] HOEHE = {0.5, 2, 4, 7, 11, 16, 23, 32, 45, 60, 80}, WIND = {1, 4, 8, 13, 20};
    public static final int NH = HOEHE.length, NW = WIND.length;
    /** Reflexion der glatten Fläche für Licht von überall (n = 1,34). */
    public static final double RHO_DIFFUS = 0.066;

    /** Fläche [Höhe][Wind][Kanal]. */
    public final float[] f;

    public Wasseralbedo(float[] f) {
        if (f.length != NH * NW * 3) throw new IllegalArgumentException("Wasseralbedo: " + f.length + " Werte statt " + NH * NW * 3);
        this.f = f.clone();
    }

    private static Wasseralbedo geladen;

    /** Die abgelegte Tabelle ({@code wasseralbedo.bin}). */
    public static synchronized Wasseralbedo lade() {
        if (geladen != null) return geladen;
        try (InputStream in = Wasseralbedo.class.getResourceAsStream("wasseralbedo.bin")) {
            if (in == null) throw new IOException("wasseralbedo.bin fehlt (gebacken von render.reference.Wasseralbedotabelle)");
            DataInputStream d = new DataInputStream(new BufferedInputStream(in));
            if (d.readInt() != MAGIE || d.readInt() != NH || d.readInt() != NW) throw new IOException("wasseralbedo.bin: falscher Kopf");
            float[] f = new float[NH * NW * 3];
            for (int i = 0; i < f.length; i++) f[i] = d.readFloat();
            geladen = new Wasseralbedo(f);
            return geladen;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lage in einer Reihe von Stützstellen (Index mit Bruch, an den Enden gehalten). */
    static double lage(double[] s, double v) {
        if (v <= s[0]) return 0;
        for (int i = 0; i + 1 < s.length; i++) if (v <= s[i + 1]) return i + (v - s[i]) / (s[i + 1] - s[i]);
        return s.length - 1;
    }

    /** Die Fläche je Kanal bei der Höhe der Sonne (Grad; unter der kleinsten gehalten) und dem Wind (m/s), bilinear. */
    public double[] flaeche(double hoeheGrad, double wind) {
        double a = lage(HOEHE, hoeheGrad), b = lage(WIND, wind);
        int i = Math.min(NH - 2, (int) a), j = Math.min(NW - 2, (int) b);
        double fa = a - i, fb = b - j;
        double[] r = new double[3];
        for (int c = 0; c < 3; c++)
            r[c] = (1 - fa) * ((1 - fb) * f[(i * NW + j) * 3 + c] + fb * f[(i * NW + j + 1) * 3 + c])
                    + fa * ((1 - fb) * f[((i + 1) * NW + j) * 3 + c] + fb * f[((i + 1) * NW + j + 1) * 3 + c]);
        return r;
    }

    /** Tiefe für die Säule (m) [G]: im Watt meist wenige Meter, die Säule ist bei Trübung dann fast gesättigt. */
    public static final double TIEFE = 3;

    /**
     * Die Säule je Kanal: Fluss aus dem Wasser je Fluss des Himmels auf die Fläche. Unter der Fläche das Licht des
     * Himmels von überall ({@link Saeule#himmelGewichte}), schwarzer Grund in {@link #TIEFE}; je Fach von μ_v die
     * Leuchtdichte mal Raumwinkel mal μ, hinaus mit 1 − Fresnel; hinein mit 1 − {@link #RHO_DIFFUS}.
     */
    public static double[] saeule(Saeule s, Wasseroptik w) {
        double[] k = w.daempfung(), bb = w.bb(), r = new double[3];
        double[][] hg = Saeule.himmelGewichte(w.n);
        for (int c = 0; c < 3; c++) {
            double u = k[c] > 0 ? bb[c] / k[c] : 0, x = k[c] * TIEFE, fl = 0;
            for (int m = 0; m < Saeule.NMU; m++) {
                double muv = Saeule.MU_V[m], l = 0;
                for (double[] q : hg) l += q[1] * s.l(u, x, q[0], muv);
                double ca = Math.sqrt(Math.max(0, 1 - w.n * w.n * (1 - muv * muv)));
                fl += l * Saeule.raumFach(m) * (1 - Wasseroptik.fresnel(ca, w.n));
            }
            r[c] = (1 - RHO_DIFFUS) * fl;
        }
        return r;
    }

    /** Albedo des Wassers je Kanal: Fläche plus Säule. */
    public double[] wasser(double hoeheGrad, double wind, Saeule s, Wasseroptik w) {
        double[] a = flaeche(hoeheGrad, wind), b = saeule(s, w);
        for (int c = 0; c < 3; c++) a[c] += b[c];
        return a;
    }
}
