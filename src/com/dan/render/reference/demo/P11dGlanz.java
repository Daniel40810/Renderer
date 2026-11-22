package com.dan.render.reference.demo;

import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Flaeche;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Referenz;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * P11d1, Stelle 2 (Werkbuch HalligHooge Datei 59, A24): das Glitzern der tiefen Sonne. Raue Wasserfläche ohne Luft und
 * ohne Säule, nur die Sonne; die Referenz (Facetten nach Walter 2007 mit Schattung nach Smith) gegen das Glitzern des
 * Echtzeitbilds ({@code glanz} in {@code wasser.glsl}: Cox und Munk ohne Schattung) und gegen dasselbe mit G1(v) · G1(l)
 * nach Smith (Walter, Gl. 27) bezogen auf die Normale. Blick zur Sonne hin, Fenster ±az° im Azimut, Bänder nach unten.
 * Nur Messung. Aufruf: {@code P11dGlanz [punkte] [pfade]}.
 */
public final class P11dGlanz {
    static final double AUGE = 7;

    public static void main(String[] args) {
        int m = args.length > 0 ? Integer.parseInt(args[0]) : 300, n = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        Wasseroptik w = new Wasseroptik();
        Atmosphere luft = Atmosphere.forVisibility(30, 0.06);
        System.out.println(f("P11dGlanz: Auge %.0f m, %d Punkte je Band, %d Pfade je Punkt", AUGE, m, n));
        double[][] baender = {{0.5, 2}, {2, 5}, {5, 10}};
        for (double wind : new double[] {3, 8}) {
            double s2 = Wasseroptik.coxMunk(wind), alpha = Math.sqrt(s2);
            for (double hsG : new double[] {3, 6, 12}) {
                double hs = Math.toRadians(hsG);
                double[] l = {0, Math.sin(hs), -Math.cos(hs)};
                StringBuilder b = new StringBuilder(f("Wind %.0f (σ² %.4f), Sonne %.0f°: ", wind, s2, hsG));
                for (double[] bd : baender) {
                    SplittableRandom rz = new SplittableRandom(77 + (long) (10 * bd[0]) + (long) hsG);
                    double sE = 0, sG = 0, sR = 0, v2 = 0;
                    double azW = Math.toRadians(Math.max(3, 40 * alpha));
                    for (int k = 0; k < m; k++) {
                        double ab = Math.toRadians(bd[0] + (bd[1] - bd[0]) * rz.nextDouble()), az = azW * (2 * rz.nextDouble() - 1);
                        double[] d = {Math.sin(az) * Math.cos(ab), -Math.sin(ab), -Math.cos(az) * Math.cos(ab)};
                        Ebenen sz = new Ebenen(-AUGE - 100, -AUGE, new double[] {0, 0, 0}, AUGE);
                        Referenz ref = new Referenz(sz, luft, w, wind);
                        ref.spektral = false;
                        ref.ohneSaeule = true;
                        ref.lichter.clear();
                        ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
                        double[] E = ref.verfolger(1).wl.sonne;
                        double[] r = ReferenceCheck.schaetze(ref, 3300 + 13L * k, n, (pv, out) -> {
                            pv.ohneLuft();
                            java.util.Arrays.fill(pv.wl.nacht, 0);
                            double[] L = pv.verfolge(new double[3], d);
                            System.arraycopy(L, 0, out, 0, 3);
                        });
                        double[] v = {-d[0], -d[1], -d[2]};
                        double g = glanz(v, l, s2, w.n, false), gg = glanz(v, l, s2, w.n, true);
                        sE += g * E[1];
                        sG += gg * E[1];
                        sR += r[1];
                        v2 += r[4] * r[4];
                    }
                    b.append(f("%.1f–%.0f°: Echtzeit/Referenz %.3f, mit Smith %.3f (Referenz %.3g ± %.1f %%); ", bd[0], bd[1], sE / sR, sG / sR, sR / m, 100 * Math.sqrt(v2) / sR));
                }
                System.out.println(b);
            }
        }
    }

    /** glanz aus wasser.glsl mit der Normalen nach oben (E = 1); mit smith: dazu G1(v) · G1(l) um die Normale. */
    static double glanz(double[] v, double[] l, double s2, double eta, boolean smith) {
        double[] n = {0, 1, 0};
        if (l[1] <= 0) return 0;
        double[] h = {l[0] + v[0], l[1] + v[1], l[2] + v[2]};
        double hl = Math.sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2]);
        for (int i = 0; i < 3; i++) h[i] /= hl;
        double mn = h[1], mv = v[1];
        if (mn <= 0 || mv <= 1e-3) return 0;
        double c2 = mn * mn, t2 = (1 - c2) / c2;
        double p = Math.exp(-t2 / s2) / (Math.PI * s2);
        double lh = l[0] * h[0] + l[1] * h[1] + l[2] * h[2];
        double r = Wasseroptik.fresnel(lh, eta) * p / (4 * mv * c2 * c2);
        if (smith) r *= Flaeche.g1(mv, Math.sqrt(s2)) * Flaeche.g1(l[1], Math.sqrt(s2));
        return r;
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMANY, s, a); }
}
