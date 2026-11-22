package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Rasterfeld;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Lauf;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Feldvergleiche der Morphologie von P9c (Werkbuch HalligHooge Teil A, 12.10, Feinplan P9c), aus den Archiven der Läufe
 * M1 (mittleres Wetter; {@code --m1} ist der letzte Lauf des mittleren Wetters, {@code --m1a} der erste, wenn es mehrere
 * sind, die den Boden weiterreichen) und M2 (Sturm, mit dem Boden aus M1): Prüfsteine 13 (Japsand), 14 (Auftrag und Abtrag gegen
 * EasyGSH 1996–2016) und der Teil „Tide bleibt“ von 15. Der Boden nach dem Modelljahr ist der letzte Stand von M2
 * ({@code bed.dz}, gegen die Packung); die Höhe des Bodens davor die mittlere Höhe der Zelle aus der Packung von G3.
 * EasyGSH: {@code gebiet/pruef/boden_1996.txt}, {@code boden_2016.txt} (Aufbereitung, Schritt {@code morpho}).
 * Attest {@code docs/status/P9c_Feld.md}.
 */
public final class MorphoFeldCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    /** Kasten des Japsands (m, UTM 32): West, Süd, Ost, Nord; Schwelle (m NHN); Breite des Randstreifens (Zellen), der nicht zählt. */
    static double[] kasten = {462_000, 6_043_000, 468_500, 6_051_000};
    static double schwelle = 1.0;
    static int rand = 10;

    public static void main(String[] args) {
        String gebiet = "D:\\Daten\\HalligHooge\\gebiet", m1 = null, m2 = null, m1a = null;
        for (int i = 0; i + 1 < args.length; i++) {
            switch (args[i]) {
                case "--gebiet" -> gebiet = args[i + 1];
                case "--m1" -> m1 = args[i + 1];
                case "--m2" -> m2 = args[i + 1];
                case "--m1a" -> m1a = args[i + 1];
                default -> { }
            }
        }
        System.out.println("MorphoFeldCheck – P9c, Prüfsteine 13, 14, 15 (Werkbuch HalligHooge Teil A, 12.10)");
        try {
            File gb = new File(gebiet);
            feld(gb, new File(m1), new File(m2));
            tide(new File(m1a != null ? m1a : m1), new File(m1));
        } catch (Throwable e) {
            A.fehler(e);
        }
        Path p = A.schreibe(Path.of("docs", "status", "P9c_Feld.md"), "P9c – MorphoFeldCheck (Japsand, Auftrag und Abtrag, Tide)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Schwerpunkt {Ost, Nord, Fläche m², Volumen m³} dessen, was im Kasten über der Schwelle liegt; nachVolumen: gewichtet mit der Höhe über der Schwelle, sonst mit der Fläche. */
    static double[] schwerpunkt(Gitter g, double[] z, boolean nachVolumen) {
        double m = 0, mx = 0, my = 0, fl = 0, vol = 0, a = g.dx * g.dy;
        for (int j = 0; j < g.ny; j++) for (int i = 0; i < g.nx; i++) {
            double x = g.x(i), y = g.y(j), h = z[i + g.nx * j] - schwelle;
            if (!(h > 0) || x < kasten[0] || x > kasten[2] || y < kasten[1] || y > kasten[3]) continue;
            double w = nachVolumen ? h : 1;
            m += w; mx += w * x; my += w * y;
            fl += a; vol += h * a;
        }
        return new double[] {m > 0 ? mx / m : Double.NaN, m > 0 ? my / m : Double.NaN, fl, vol};
    }

    static void feld(File gb, File m1, File m2) throws Exception {
        A.teil("13, 14 Boden nach dem Modelljahr gegen EasyGSH 1996–2016");
        Gebiet ge = Gebiet.lies(gb);
        Gitter g3 = ge.gitter("G3"), g;
        float[] dz1, dz2;
        try (Archivquelle q = new Archivquelle(m1.toPath())) {
            g = q.sedimentGitter();
            double[] z = q.bodenZeiten();
            dz1 = new float[g.zellen()];
            q.boden(z[z.length - 1], null, null, dz1);
            A.info("M1", f("%s: %d Stände des Bodens bis %s; %s", m1, z.length, BildCheck.zeit(q.start, z[z.length - 1]), q.kopf.antrieb.get("sediment")));
        }
        try (Archivquelle q = new Archivquelle(m2.toPath())) {
            double[] z = q.bodenZeiten();
            dz2 = new float[g.zellen()];
            q.boden(z[z.length - 1], null, null, dz2);
            A.info("M2", f("%s: %d Stände des Bodens bis %s; %s", m2, z.length, BildCheck.zeit(q.start, z[z.length - 1]), q.kopf.antrieb.get("sediment")));
        }
        int i0 = (int) Math.round((g.x0 - g3.x0) / g3.dx), j0 = (int) Math.round((g.y0 - g3.y0) / g3.dy), n = g.zellen();
        Subgrid.Packung p = ge.packung("G3").fenster(g3, i0, j0, g.nx, g.ny);
        double[] vor = new double[n], nach = new double[n], e96 = new double[n], e16 = new double[n];
        Rasterfeld r96 = Rasterfeld.lies(new File(gb, "pruef" + File.separator + "boden_1996.txt")), r16 = Rasterfeld.lies(new File(gb, "pruef" + File.separator + "boden_2016.txt"));
        double a = g.dx * g.dy, aufM = 0, abM = 0, aufE = 0, abE = 0, aufM1 = 0, abM1 = 0;
        int innen = 0, mitE = 0;
        List<double[]> paare = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            double oben = p.basis()[c] + 100;
            vor[c] = oben - p.volumen(c, oben) / p.zellflaeche();
            nach[c] = vor[c] + dz2[c];
            int i = c % g.nx, j = c / g.nx;
            e96[c] = r96.bei(g.x(i), g.y(j));
            e16[c] = r16.bei(g.x(i), g.y(j));
            if (Math.min(Math.min(i, g.nx - 1 - i), Math.min(j, g.ny - 1 - j)) < rand) continue;
            innen++;
            if (dz2[c] > 0) aufM += dz2[c] * a; else abM -= dz2[c] * a;
            if (dz1[c] > 0) aufM1 += dz1[c] * a; else abM1 -= dz1[c] * a;
            if (Double.isNaN(e96[c]) || Double.isNaN(e16[c])) continue;
            mitE++;
            double d = (e16[c] - e96[c]) / 20;
            if (d > 0) aufE += d * a; else abE -= d * a;
            if (Math.abs(dz2[c]) > 0.01 || Math.abs(d) > 0.01) paare.add(new double[] {dz2[c], d});
        }
        // Prüfstein 13: Japsand
        double[] fv = schwerpunkt(g, vor, false), fn = schwerpunkt(g, nach, false), vv = schwerpunkt(g, vor, true), vn = schwerpunkt(g, nach, true);
        double[] f96 = schwerpunkt(g, e96, false), f16 = schwerpunkt(g, e16, false), v96 = schwerpunkt(g, e96, true), v16 = schwerpunkt(g, e16, true);
        double ostM = fn[0] - fv[0], ostE = (f16[0] - f96[0]) / 20, ostMv = vn[0] - vv[0], ostEv = (v16[0] - v96[0]) / 20;
        A.info("Kasten des Japsands", f("O %.1f–%.1f km, N %.1f–%.1f km (im Gitter %s), Boden über %+.1f m NHN, Zellen zu 20 m; EasyGSH 1996: %.2f km², 2016: %.2f km²; Modell vor dem Jahr %.2f km², danach %.2f km²",
                kasten[0] / 1e3, kasten[2] / 1e3, kasten[1] / 1e3, Math.min(kasten[3], g.y1()) / 1e3, g, schwelle, f96[2] / 1e6, f16[2] / 1e6, fv[2] / 1e6, fn[2] / 1e6));
        A.pruefe(ostM > 0 && ostE > 0 && ostM >= 0.5 * ostE && ostM <= 2 * ostE, "Prüfstein 13: Japsand, Schwerpunkt der Fläche über +1,0 m NHN wandert nach Osten; Rate gegen EasyGSH 1996–2016",
                f("Modell %+.1f m nach Osten, %+.1f m nach Norden in einem Modelljahr; EasyGSH %+.1f m/a nach Osten, %+.1f m/a nach Norden", ostM, fn[1] - fv[1], ostE, (f16[1] - f96[1]) / 20),
                "nach Osten; Rate auf Faktor 2 [G]");
        A.info("Japsand, Schwerpunkt gewichtet mit der Höhe über +1,0 m NHN (stetig, auch wenn keine Zelle die Schwelle kreuzt)",
                f("Modell %+.2f m nach Osten, %+.2f m nach Norden, Volumen über der Schwelle %+.3g m³; EasyGSH %+.2f m/a nach Osten, %+.2f m/a nach Norden, %+.3g m³/a", ostMv, vn[1] - vv[1], vn[3] - vv[3], ostEv,
                        (v16[1] - v96[1]) / 20, (v16[3] - v96[3]) / 20));
        // Prüfstein 14: Auftrag und Abtrag
        double[] x = new double[paare.size()], y = new double[paare.size()];
        for (int k = 0; k < x.length; k++) { x[k] = paare.get(k)[0]; y[k] = paare.get(k)[1]; }
        double rang = x.length >= 100 ? SedimentFeldCheck.rang(x, y) : Double.NaN;
        A.info("Prüfstein 14: Auftrag und Abtrag im Fenster je Jahr (ohne den Randstreifen)", f("Modell %.3g und %.3g Mio. m³ (davon M1 allein %.3g und %.3g); EasyGSH (2016 − 1996) / 20: %.3g und %.3g Mio. m³ über %d von %d Zellen "
                + "(enthält Vermessungsunterschiede); Verhältnis Modell zu EasyGSH %.2f und %.2f; Rangkorrelation der Bodenänderung je Zelle %.2f (%d Zellen über 1 cm)", aufM / 1e6, abM / 1e6, aufM1 / 1e6, abM1 / 1e6, aufE / 1e6,
                abE / 1e6, mitE, innen, aufM / aufE, abM / abE, rang, x.length));
        double[] b = new double[innen];
        int k = 0, ueber = 0;
        for (int c = 0; c < n; c++) {
            int i = c % g.nx, j = c / g.nx;
            if (Math.min(Math.min(i, g.nx - 1 - i), Math.min(j, g.ny - 1 - j)) < rand) continue;
            b[k++] = Math.abs(dz2[c]);
            if (Math.abs(dz2[c]) > 1) ueber++;
        }
        Arrays.sort(b);
        A.info("Bodenänderung nach dem Modelljahr", f("|Δz| im Median %.3f m, 90 %% %.3f m, 99 %% %.3f m, größte %.2f m; %d Zellen über 1 m (P9b ohne Rückwirkung: 62 im Sturm über drei Tage)", b[innen / 2], b[(int) (0.9 * innen)],
                b[(int) (0.99 * innen)], b[innen - 1], ueber));
    }

    /** Prüfstein 15, Teil Tide: Tidenhub in der tiefsten Rinne des Fensters gegen den Tidenhub in G2 außerhalb, erste Tide des ersten gegen letzte Tide des letzten Laufs von M1. */
    static void tide(File erst, File letzt) throws Exception {
        A.teil("15 Tide bleibt");
        try (Archivquelle q = new Archivquelle(erst.toPath()); Archivquelle q2 = erst.equals(letzt) ? null : new Archivquelle(letzt.toPath())) {
            List<Gitter> gs = q.gitter();
            int k3 = SeegangCheck.index(gs, "G3"), k2 = SeegangCheck.index(gs, "G2");
            Gitter g3 = gs.get(k3), g2 = gs.get(k2);
            double[] zs = q.zeiten();
            float[] e3 = new float[g3.zellen()], h3 = new float[g3.zellen()], a3 = new float[g3.zellen()], e2 = new float[g2.zellen()], h2 = new float[g2.zellen()], a2 = new float[g2.zellen()];
            float[] min3 = new float[g3.zellen()], min2 = new float[g2.zellen()];
            Arrays.fill(min3, Float.MAX_VALUE);
            Arrays.fill(min2, Float.MAX_VALUE);
            for (int i = 0; i < zs.length; i += 6) {                              // jede halbe Stunde: kleinste Tiefe je Zelle
                q.stand(zs[i], k3, e3, h3, a3);
                q.stand(zs[i], k2, e2, h2, a2);
                for (int c = 0; c < min3.length; c++) min3[c] = Math.min(min3[c], h3[c]);
                for (int c = 0; c < min2.length; c++) min2[c] = Math.min(min2[c], h2[c]);
            }
            int c3 = 0, c2 = -1;
            for (int c = 0; c < min3.length; c++) if (min3[c] > min3[c3]) c3 = c;
            for (int c = 0; c < min2.length; c++) {
                double x = g2.x(c % g2.nx), y = g2.y(c / g2.nx);
                if (x > g3.x0 - 2000 && x < g3.x1() + 2000 && y > g3.y0 - 2000 && y < g3.y1() + 2000) continue;
                if (c2 < 0 || min2[c] > min2[c2]) c2 = c;
            }
            double tide = 44714;
            double[] hub3 = new double[2], hub2 = new double[2];
            for (int w = 0; w < 2; w++) {
                Archivquelle qq = w == 1 && q2 != null ? q2 : q;
                double[] zw = qq.zeiten();
                double von = w == 0 ? zw[0] : zw[zw.length - 1] - tide, bis = von + tide, lo3 = 1e9, hi3 = -1e9, lo2 = 1e9, hi2 = -1e9;
                for (double t : zw) {
                    if (t < von - 1 || t > bis + 1) continue;
                    qq.stand(t, k3, e3, h3, a3);
                    qq.stand(t, k2, e2, h2, a2);
                    lo3 = Math.min(lo3, e3[c3]); hi3 = Math.max(hi3, e3[c3]);
                    lo2 = Math.min(lo2, e2[c2]); hi2 = Math.max(hi2, e2[c2]);
                }
                hub3[w] = hi3 - lo3;
                hub2[w] = hi2 - lo2;
            }
            double v0 = hub3[0] / hub2[0], v1 = hub3[1] / hub2[1], aenderung = (v1 - v0) * hub2[1];
            A.pruefe(Math.abs(aenderung) <= 0.05, "Prüfstein 15: Tidenhub in der tiefsten Rinne des Fensters gegen den in G2 außerhalb, erste Tide von " + erst.getName() + " gegen letzte Tide von " + letzt.getName() + " (das Modelljahr des mittleren Wetters dazwischen)",
                    f("Fenster O %.0f, N %.0f (kleinste Tiefe %.1f m): %.3f m und %.3f m; G2 O %.0f, N %.0f: %.3f m und %.3f m; Verhältnis %.4f und %.4f; Änderung des Tidenhubs im Fenster %+.3f m",
                            g3.x(c3 % g3.nx), g3.y(c3 / g3.nx), min3[c3], hub3[0], hub3[1], g2.x(c2 % g2.nx), g2.y(c2 / g2.nx), hub2[0], hub2[1], v0, v1, aenderung), "|Δ| ≤ 5 cm [G]");
        }
    }
}
