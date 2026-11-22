package com.dan.render.reference.demo;

import com.dan.core.szene.Atmosphere;
import com.dan.core.szene.Lichtquellen;
import com.dan.core.szene.Scattering;
import com.dan.core.szene.Wasseroptik;
import com.dan.render.reference.Ebenen;
import com.dan.render.reference.Licht;
import com.dan.render.reference.Referenz;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * P11d1, Stelle 3 (Werkbuch HalligHooge Datei 59, A25): ebener Boden nach Lambert (Albedo ρ überall, auch für die Luft),
 * Sonne und Himmel, Sicht 30 km; das Gelände des Echtzeitbilds wie {@code gelaende.frag} (ρ/π · (E_Sonne cos + E_Himmel),
 * E_Himmel = ∫ L cos dω über den Himmel aus {@link Scattering#radiance}, Luft zwischen Auge und Boden aus
 * {@link Scattering#strecke}) gegen die Referenz. Prüft die Beleuchtung des Geländes ohne die Szene. Nur Messung.
 */
public final class P11dGelaende {
    public static void main(String[] args) {
        int m = args.length > 0 ? Integer.parseInt(args[0]) : 24, n = args.length > 1 ? Integer.parseInt(args[1]) : 512;
        double rho = 0.23, auge = 4.7;
        Atmosphere luft = Atmosphere.forVisibility(30, rho);
        Scattering sc = new Scattering(luft);
        double[] e = {Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX, Lichtquellen.SONNE_LX};
        Wasseroptik w = new Wasseroptik();
        for (double hsG : new double[] {17.4, 3.2, 40}) {
            double hs = Math.toRadians(hsG);
            for (double azS : new double[] {150, 60}) {
                double a = Math.toRadians(azS);
                double[] l = {Math.sin(a) * Math.cos(hs), Math.sin(hs), -Math.cos(a) * Math.cos(hs)};
                double[] Eh = new double[3];
                for (int az = 0; az < 64; az++)
                    for (int b = 0; b < 32; b++) {
                        double cz = (b + 0.5) / 32, szz = Math.sqrt(1 - cz * cz), ph = 2 * Math.PI * (az + 0.5) / 64;
                        double[] L = sc.radiance(0.5, new double[] {szz * Math.sin(ph), cz, -szz * Math.cos(ph)}, l, e, null, null);
                        for (int c = 0; c < 3; c++) Eh[c] += L[c] * cz * 2 * Math.PI / (64 * 32);
                    }
                double[] t = sc.transmittance(Atmosphere.BOTTOM + 0.5, Math.sin(hs));
                StringBuilder sb = new StringBuilder(f("Sonne %.1f° (Azimut %.0f° zum Blick): ", hsG, azS));
                for (double[] bd : new double[][] {{1, 3}, {3, 10}, {10, 30}}) {
                    SplittableRandom rz = new SplittableRandom(91 + (long) bd[0]);
                    double[] sE = new double[3], sR = new double[3], sB = new double[3];
                    for (int k = 0; k < m; k++) {
                        double ab = Math.toRadians(bd[0] + (bd[1] - bd[0]) * rz.nextDouble()), az = Math.toRadians(20) * (2 * rz.nextDouble() - 1);
                        double[] d = {Math.sin(az) * Math.cos(ab), -Math.sin(ab), -Math.cos(az) * Math.cos(ab)};
                        double weg = auge / Math.sin(ab);
                        double[][] st = sc.strecke(Atmosphere.BOTTOM + auge, d, l, e, null, null, weg, 8);
                        Ebenen sz = new Ebenen(-auge, Double.NaN, new double[] {rho, rho, rho}, auge);
                        Referenz ref = new Referenz(sz, luft, w, 0);
                        ref.spektral = false;
                        ref.lichter.clear();
                        ref.lichter.add(new Licht(l, Lichtquellen.SONNE_RADIUS / 1.496e11, 0, 1, true));
                        double[] E0 = ref.verfolger(1).wl.sonne;
                        double[] r = ReferenceCheck.schaetze(ref, 7100 + 17L * k, n, (pv, out) -> {
                            java.util.Arrays.fill(pv.wl.nacht, 0);
                            double[] L = pv.verfolge(new double[3], d);
                            System.arraycopy(L, 0, out, 0, 3);
                        });
                        for (int c = 0; c < 3; c++) {
                            double Lb = rho / Math.PI * (E0[c] * t[c] * Math.sin(hs) + Eh[c]);
                            sE[c] += st[0][c] + st[1][c] * Lb;
                            sB[c] += st[1][c] * Lb;
                            sR[c] += r[c];
                        }
                    }
                    sb.append(f("%.0f–%.0f°: Echtzeit/Referenz R %.3f G %.3f B %.3f (Boden ohne Luft davor %.0f %% des Bilds); ", bd[0], bd[1], sE[0] / sR[0], sE[1] / sR[1], sE[2] / sR[2], 100 * sB[1] / sE[1]));
                }
                System.out.println(sb);
            }
        }
    }

    static String f(String s, Object... a) { return String.format(Locale.GERMANY, s, a); }
}
