package com.dan.render.reference.demo;

import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Flaeche;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Referenz;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * P11d1, Stelle 2 (Werkbuch HalligHooge Datei 59, A26): die Fläche weit weg mit Himmel. Statistisch raue Fläche (Cox und
 * Munk, nichts aufgelöst), ohne Säule, Luft und Himmel, Sonne 30° im Rücken. Drei Rechnungen an denselben Blickrichtungen:
 * <ol><li>Echtzeit wie {@code wasser.frag}: Stärke aus der Tafel, Himmel an drei Höhen um das mittlere Spiegelbild (Gauß in der Höhe, nur in der Ebene des Blicks);</li>
 * <li>„genau“: derselbe Himmel über alle Neigungen gemittelt (Gitter der Neigungen wie {@link Flaeche#rechne}: Dichte mal sichtbare Fläche, Fresnel, G1 der Spiegelung);</li>
 * <li>die Referenz (Facetten nach Walter 2007) über Wasser.</li></ol>
 * 1 gegen 2 misst die Quadratur, 2 gegen 3 den Rest. Nur Messung. Aufruf {@code P11dFlaeche [punkte] [pfade] [albedo]}.
 */
public final class P11dFlaeche {
    static final double AUGE = 6;

    public static void main(String[] args) {
        int m = args.length > 0 ? Integer.parseInt(args[0]) : 64, n = args.length > 1 ? Integer.parseInt(args[1]) : 256;
        double alb = args.length > 2 ? Double.parseDouble(args[2]) : 0.06;
        Wasseroptik w = new Wasseroptik();
        float[] t = Flaeche.tafel(w.n);
        Atmosphere luft = Atmosphere.forVisibility(30, alb);
        Scattering sc = new Scattering(luft);
        double[] e = {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX};
        System.out.println(f("P11dFlaeche: Auge %.0f m, Albedo des Himmels im Echtzeitbild %.2f, %d Punkte je Band, %d Pfade", AUGE, alb, m, n));
        double[][] baender = {{2, 5}, {5, 10}, {10, 20}, {20, 45}};
        for (double wind : new double[] {3, 8, 18}) {
            double s2 = Wasseroptik.coxMunk(wind);
            for (double[] sonne : new double[][] {{30, 180}, {10, 90}}) {
                double hs = Math.toRadians(sonne[0]), azS = Math.toRadians(sonne[1]);
                // Blick nach −z; Sonne um azS gedreht (180: im Rücken, 90: von der Seite)
                double[] l = {Math.sin(azS) * Math.cos(hs), Math.sin(hs), -Math.cos(azS) * Math.cos(hs)};
                StringBuilder b = new StringBuilder(f("Wind %.0f (σ² %.4f), Sonne %.0f° bei %.0f°: ", wind, s2, sonne[0], sonne[1]));
                for (double[] bd : baender) {
                    SplittableRandom rz = new SplittableRandom(55 + (long) bd[0] + (long) wind);
                    double[] sE = new double[3], sG = new double[3], sR = new double[3];
                    double[][] sV = new double[3][3];
                    double v2 = 0;
                    for (int k = 0; k < m; k++) {
                        double ab = Math.toRadians(bd[0] + (bd[1] - bd[0]) * rz.nextDouble()), az = Math.toRadians(20) * (2 * rz.nextDouble() - 1);
                        double[] d = {Math.sin(az) * Math.cos(ab), -Math.sin(ab), -Math.cos(az) * Math.cos(ab)};
                        double weg = AUGE / Math.sin(ab);
                        double[][] st = sc.strecke(Atmosphere.BOTTOM + AUGE, d, l, e, null, null, weg, 8);
                        double[] L3 = drei(t, sc, d, l, e, s2, w.n), Lg = genau(sc, d, l, e, s2, w.n);
                        double[][] Lv = varianten(sc, d, l, e, s2, w.n);
                        for (int vi = 0; vi < 3; vi++) for (int c = 0; c < 3; c++) sV[vi][c] += st[0][c] + st[1][c] * Lv[vi][c];
                        Ebenen sz = new Ebenen(Double.NaN, -AUGE, new double[] {0, 0, 0}, AUGE);
                        Referenz ref = new Referenz(sz, luft, w, wind);
                        ref.spektral = false;
                        ref.ohneSaeule = true;
                        ref.lichter.clear();
                        ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
                        double[] r = ReferenceCheck.schaetze(ref, 6100 + 31L * k + (long) wind, n, (pv, out) -> {
                            java.util.Arrays.fill(pv.wl.nacht, 0);
                            double[] L = pv.verfolge(new double[3], d);
                            System.arraycopy(L, 0, out, 0, 3);
                        });
                        for (int c = 0; c < 3; c++) {
                            sE[c] += st[0][c] + st[1][c] * L3[c];
                            sG[c] += st[0][c] + st[1][c] * Lg[c];
                            sR[c] += r[c];
                        }
                        v2 += r[4] * r[4];
                    }
                    double yE = y(sE), yG = y(sG), yR = y(sR);
                    b.append(f("%.0f–%.0f°: Echtzeit/Referenz %.3f, genau/Referenz %.3f, Echtzeit/genau %.3f [eigene Momente 3 Höhen %.3f, Quantile 2 %.3f, Quantile 4 %.3f] (±%.1f %%); ", bd[0], bd[1], yE / yR, yG / yR, yE / yG,
                            y(sV[0]) / yG, y(sV[1]) / yG, y(sV[2]) / yG, 100 * Math.sqrt(v2) / sR[1]));
                }
                System.out.println(b);
            }
        }
    }

    /** Wie wasser.frag mit der Normalen nach oben: Stärke F aus der Tafel, Himmel an drei Höhen. */
    static double[] drei(float[] t, Scattering sc, double[] d, double[] l, double[] e, double s2, double eta) {
        double mu = -d[1];
        double[] fl = Flaeche.lies(t, mu, s2);
        double[] rt = {d[0], 0, d[2]};
        double lt = Math.hypot(rt[0], rt[2]);
        rt[0] /= lt;
        rt[2] /= lt;
        double e0 = Math.asin(Math.max(0, Math.min(1, fl[1])));
        double[] L = new double[3];
        for (int q = -1; q <= 1; q++) {
            double eh = Math.max(0, Math.min(1.5, e0 + 1.7320508 * q * fl[2]));
            double[] rk = {rt[0] * Math.cos(eh), Math.sin(eh), rt[2] * Math.cos(eh)};
            if (rk[1] < 0.002) rk = norm(new double[] {rk[0], 0.002, rk[2]});
            double[] Lk = sc.radiance(AUGE, rk, l, e, null, null);
            for (int c = 0; c < 3; c++) L[c] += (q == 0 ? 2.0 / 3.0 : 1.0 / 6.0) * Lk[c];
        }
        for (int c = 0; c < 3; c++) L[c] *= fl[0];
        return L;
    }

    /** Über alle Neigungen: Σ w F G1(r) L(r) / Σ w wie Flaeche.rechne, mit dem Himmel in der Richtung jeder Spiegelung. */
    static double[] genau(Scattering sc, double[] d, double[] l, double[] e, double s2, double eta) {
        int Q = 36;
        double alpha = Math.sqrt(s2), g = 6.5 * alpha / Math.sqrt(2), h = 2 * g / Q;
        double[] v = {-d[0], -d[1], -d[2]};
        double sw = 0;
        double[] L = new double[3];
        for (int a = 0; a < Q; a++)
            for (int bq = 0; bq < Q; bq++) {
                double sx = -g + (a + 0.5) * h, sy = -g + (bq + 0.5) * h, len = Math.sqrt(1 + sx * sx + sy * sy);
                double[] mm = {-sx / len, 1 / len, -sy / len};
                double vm = v[0] * mm[0] + v[1] * mm[1] + v[2] * mm[2];
                if (vm <= 0) continue;
                double wgt = Math.exp(-(sx * sx + sy * sy) / s2) * vm * len;
                sw += wgt;
                double[] r = {d[0] + 2 * vm * mm[0], d[1] + 2 * vm * mm[1], d[2] + 2 * vm * mm[2]};
                if (r[1] <= 0) continue;
                double wr = wgt * Wasseroptik.fresnel(vm, eta) * Flaeche.g1(r[1], alpha);
                double[] rr = r[1] < 0.002 ? norm(new double[] {r[0], 0.002, r[2]}) : r;
                double[] Lk = sc.radiance(AUGE, rr, l, e, null, null);
                for (int c = 0; c < 3; c++) L[c] += wr * Lk[c];
            }
        for (int c = 0; c < 3; c++) L[c] /= sw;
        return L;
    }

    /**
     * Momente der Spiegelung aus dem Gitter (Höhe e0, σ in der Höhe, σ im Azimut um die Richtung des Spiegelbilds,
     * Stärke F) und drei Regeln: 0 drei Höhen (wie das Bild), 1 Kreuz aus fünf Punkten (Mitte 1/3, je ±√3 σ 1/6), 2 3 × 3.
     */
    static double[][] varianten(Scattering sc, double[] d, double[] l, double[] e, double s2, double eta) {
        int Q = 64;
        double alpha = Math.sqrt(s2), g = 6.5 * alpha / Math.sqrt(2), h = 2 * g / Q;
        double[] v = {-d[0], -d[1], -d[2]};
        double[] fw = norm(new double[] {d[0], 0, d[2]}), sd = {-fw[2], 0, fw[0]};
        double sw = 0, sr = 0, se = 0, se2 = 0;
        java.util.List<double[]> el = new java.util.ArrayList<>();
        for (int a = 0; a < Q; a++)
            for (int bq = 0; bq < Q; bq++) {
                double sx = -g + (a + 0.5) * h, sy = -g + (bq + 0.5) * h, len = Math.sqrt(1 + sx * sx + sy * sy);
                double[] mm = {-sx / len, 1 / len, -sy / len};
                double vm = v[0] * mm[0] + v[1] * mm[1] + v[2] * mm[2];
                if (vm <= 0) continue;
                double wgt = Math.exp(-(sx * sx + sy * sy) / s2) * vm * len;
                sw += wgt;
                double[] r = {d[0] + 2 * vm * mm[0], d[1] + 2 * vm * mm[1], d[2] + 2 * vm * mm[2]};
                if (r[1] <= 0) continue;
                double wr = wgt * Wasseroptik.fresnel(vm, eta) * Flaeche.g1(r[1], alpha);
                double ee = Math.asin(Math.min(1, r[1]));
                sr += wr;
                se += wr * ee;
                se2 += wr * ee * ee;
                el.add(new double[] {ee, wr});
            }
        double F = sr / sw, e0 = se / sr, sE = Math.sqrt(Math.max(0, se2 / sr - e0 * e0));
        el.sort((x, y) -> Double.compare(x[0], y[0]));
        double[][] out = new double[3][3];
        double[] wq = {1.0 / 6, 2.0 / 3, 1.0 / 6};
        for (int qe = -1; qe <= 1; qe++) add(out[0], sc, fw, Math.max(0, e0 + 1.7320508 * qe * sE), wq[qe + 1] * F, l, e);
        int[] Ks = {2, 4};
        for (int ki = 0; ki < 2; ki++) {
            int K = Ks[ki];
            for (int k = 0; k < K; k++) {
                double ziel = (k + 0.5) / K * sr, acc = 0, eq = el.get(el.size() - 1)[0];
                for (double[] x : el) { acc += x[1]; if (acc >= ziel) { eq = x[0]; break; } }
                add(out[1 + ki], sc, fw, eq, F / K, l, e);
            }
        }
        return out;
    }

    static void add(double[] o, Scattering sc, double[] fw, double el, double wgt, double[] l, double[] e) {
        double[] dir = {Math.cos(el) * fw[0], Math.sin(el), Math.cos(el) * fw[2]};
        if (dir[1] < 0.002) dir = norm(new double[] {dir[0], 0.002, dir[2]});
        double[] L = sc.radiance(AUGE, dir, l, e, null, null);
        for (int c = 0; c < 3; c++) o[c] += wgt * L[c];
    }

    static double y(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    static double[] norm(double[] a) {
        double s = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[] {a[0] / s, a[1] / s, a[2] / s};
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMANY, s, a); }
}
