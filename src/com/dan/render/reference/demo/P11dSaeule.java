package com.dan.render.reference.demo;

import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Flaeche;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Saeule;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Pfadverfolger;
import com.dan.render.reference.Referenz;

import java.util.Locale;
import java.util.stream.IntStream;

/**
 * P11d1, Stelle 1 (Werkbuch HalligHooge Datei 59): die Säule aus dem Himmel in tiefem Wasser, wie die Leiter aus P10d
 * (Messung e, Stufe 3: rau, Luft und Himmel, drei Kanäle), aber getrennt nach Sonne und Himmel: die Referenz nach dem Weg
 * der Pfade ({@link Pfadverfolger#teile}: 1 Säule aus der Quelle, 2 Säule aus dem Himmel), das Echtzeitbild nach seinen
 * zwei Gliedern. Für das Glied des Himmels mehrere Fassungen des Echtzeitbilds gegen dieselbe Referenz:
 * <ul><li>Durchlass der glatten Fläche je Richtung (wie {@code licht.comp}) oder der rauen ({@link Flaeche}, wie die Sonne seit F104 A);</li>
 * <li>Albedo der Tabellen der Luft 0,06 (wie die Leiter), 0,23 (wie die Anwendung) und höher (Wasser hellt den Himmel, A22).</li></ul>
 * Nur Messung, kein Prüfstein. Aufruf: {@code P11dSaeule [pfade] [sonne …]}.
 */
public final class P11dSaeule {
    static final double WIND = 8, TIEFE = 100;

    public static void main(String[] args) {
        int N = args.length > 0 ? Integer.parseInt(args[0]) : 400_000;
        double[] sonnen = args.length > 1 ? IntStream.range(1, args.length).mapToDouble(i -> Double.parseDouble(args[i])).toArray() : new double[] {15, 40};
        Saeule s = Saeule.lade();
        Wasseroptik w = new Wasseroptik();
        double[] kk = w.daempfung(), bb = w.bb(), uk = new double[3];
        for (int c = 0; c < 3; c++) uk[c] = bb[c] / kk[c];
        float[] t3 = s.tafel(uk, w.n), fl = Flaeche.tafel(w.n);
        double eta = w.n, s2 = Wasseroptik.coxMunk(WIND), hv = Math.toRadians(45);
        double cv = Math.sin(hv), cwv = Math.sqrt(1 - (1 - cv * cv) / (eta * eta));
        double hinaus = Flaeche.lies(fl, cv, s2)[3] / (eta * eta);
        double[] albedos = {0.06, 0.23, 0.40};
        Scattering[] sc = new Scattering[albedos.length];
        for (int a = 0; a < albedos.length; a++) sc[a] = new Scattering(Atmosphere.forVisibility(30, albedos[a]));
        Atmosphere luftRef = Atmosphere.forVisibility(30, 0.06);       // die Referenz nutzt die Albedo nicht (sie sieht das Wasser)
        System.out.println(f("P11dSaeule: %d Pfade, Wind %.0f m/s (σ² %.4f), Tiefe %.0f m, Blick 45° hinab von der Sonne weg, Kerne %d",
                N, WIND, s2, TIEFE, Runtime.getRuntime().availableProcessors()));
        for (double hsG : sonnen) {
            double hs = Math.toRadians(hsG), cs = Math.sin(hs), cws = Math.sqrt(1 - (1 - cs * cs) / (eta * eta));
            double[] l = {0, cs, -Math.cos(hs)};
            Ebenen sz = new Ebenen(-0.5 - TIEFE, -0.5, new double[] {0, 0, 0}, 10);
            Referenz ref = new Referenz(sz, luftRef, w, WIND);
            ref.spektral = false;
            ref.lichter.clear();
            ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
            long t0 = System.nanoTime();
            double[] e = teile(ref, 9100 + (long) hsG, N);          // [Teil 0..3][c], σ in [4+Teil][c]
            double sek = (System.nanoTime() - t0) / 1e9;
            double[] y = {0.2126, 0.7152, 0.0722};
            double rS = 0, rH = 0, sS = 0, sH = 0;
            for (int c = 0; c < 3; c++) {
                rS += y[c] * e[3 + c];
                rH += y[c] * e[6 + c];
                sS += y[c] * y[c] * e[15 + c] * e[15 + c];
                sH += y[c] * y[c] * e[18 + c] * e[18 + c];
            }
            System.out.println(f("\nSonne %.0f° (%.0f s): Referenz Säule aus der Sonne %.4g ± %.1f %%, aus dem Himmel %.4g ± %.1f %% (Anteil Himmel %.0f %%)",
                    hsG, sek, rS, 100 * Math.sqrt(sS) / rS, rH, 100 * Math.sqrt(sH) / rH, 100 * rH / (rS + rH)));
            System.out.println(f("   je Kanal Himmel R %.4g G %.4g B %.4g", e[6], e[7], e[8]));
            for (int a = 0; a < albedos.length; a++) {
                Scattering q = sc[a];
                double[] E = ref.verfolger(1).wl.sonne.clone();
                double[] t = q.transmittance(Atmosphere.BOTTOM + 0.5, cs);
                for (int c = 0; c < 3; c++) E[c] *= t[c];
                double hinein = Flaeche.lies(fl, cs, s2)[3];
                double[][] stG = new double[3][Saeule.NMU], stR = new double[3][Saeule.NMU];
                double[] luftE = new double[3];
                for (int az = 0; az < 64; az++)
                    for (int b = 0; b < 32; b++) {
                        double cz = (b + 0.5) / 32, szz = Math.sqrt(1 - cz * cz), ph = 2 * Math.PI * (az + 0.5) / 64;
                        double[] d = {szz * Math.sin(ph), cz, -szz * Math.cos(ph)};
                        double[] L = q.radiance(0.5, d, l, new double[] {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX}, null, null);
                        double dw = 2 * Math.PI / (64 * 32);
                        double gG = cz * (1 - Wasseroptik.fresnel(cz, eta)) * dw, gR = cz * Flaeche.lies(fl, cz, s2)[3] * dw;
                        double st = Saeule.himmelStufe(cz, eta);
                        int k0 = Math.min(Saeule.NMU - 2, (int) st);
                        double fk = st - k0;
                        for (int c = 0; c < 3; c++) {
                            luftE[c] += L[c] * cz * dw;
                            stG[c][k0] += L[c] * gG * (1 - fk);
                            stG[c][k0 + 1] += L[c] * gG * fk;
                            stR[c][k0] += L[c] * gR * (1 - fk);
                            stR[c][k0 + 1] += L[c] * gR * fk;
                        }
                    }
                double eS = 0, eHG = 0, eHR = 0, eL = 0;
                double[] kS = new double[3], kH = new double[3];
                for (int c = 0; c < 3; c++) {
                    double sonne = hinaus * Saeule.wertStufen(t3, c, kk[c], TIEFE, cws, cwv, -1, E[c] * cs * hinein, new double[Saeule.NMU], 0);
                    double himG = hinaus * Saeule.wertStufen(t3, c, kk[c], TIEFE, cws, cwv, -1, 0, stG[c], 0);
                    double himR = hinaus * Saeule.wertStufen(t3, c, kk[c], TIEFE, cws, cwv, -1, 0, stR[c], 0);
                    eS += y[c] * sonne;
                    eHG += y[c] * himG;
                    eHR += y[c] * himR;
                    eL += y[c] * luftE[c];
                    kS[c] = sonne / e[3 + c];
                    kH[c] = himG / e[6 + c];
                }
                System.out.println(f("   Albedo %.2f: E des Himmels %.4g lx; Echtzeit/Referenz Sonne %.3f (R %.3f G %.3f B %.3f); Himmel glatt %.3f (R %.3f G %.3f B %.3f), Himmel rau %.3f; Säule ganz glatt %.3f, rau %.3f",
                        albedos[a], eL, eS / rS, kS[0], kS[1], kS[2], eHG / rH, kH[0], kH[1], kH[2], eHR / rH, (eS + eHG) / (rS + rH), (eS + eHR) / (rS + rH)));
            }
        }
    }

    /** Die Referenz in Teilen: Mittel je Teil (0–3) und Kanal in [3 · Teil + c], σ in [12 + 3 · Teil + c]. */
    static double[] teile(Referenz ref, long saat, int n) {
        double hv = Math.toRadians(45);
        double[] v = {0, -Math.sin(hv), Math.cos(hv)};
        double[][] r = IntStream.range(0, 16).parallel().mapToObj(t -> {
            Pfadverfolger pv = ref.verfolger(saat + t);
            pv.teile = true;
            java.util.Arrays.fill(pv.wl.nacht, 0);
            double[] acc = new double[24];
            for (int i = 0; i < n / 16; i++) {
                pv.verfolge(new double[3], v);
                for (int q = 0; q < 4; q++)
                    for (int c = 0; c < 3; c++) {
                        double x = pv.LT[q][c];
                        acc[3 * q + c] += x;
                        acc[12 + 3 * q + c] += x * x;
                    }
            }
            return acc;
        }).toArray(double[][]::new);
        int nn = (n / 16) * 16;
        double[] e = new double[24];
        for (int k = 0; k < 12; k++) {
            double a = 0, b = 0;
            for (double[] x : r) {
                a += x[k];
                b += x[12 + k];
            }
            e[k] = a / nn;
            e[12 + k] = Math.sqrt(Math.max(0, b / nn - e[k] * e[k]) / nn);
        }
        return e;
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMANY, s, a); }
}
