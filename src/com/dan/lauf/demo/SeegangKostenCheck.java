package com.dan.lauf.demo;

import com.dan.core.gitter.Gitter;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Lauf;
import com.dan.physics.waves.Waves;
import com.dan.physics.waves.WavesModul;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.dan.core.pruef.Attest.f;

/**
 * Was der Seegang im Sturm kostet (P13d, A33; Werkbuch HalligHooge Teil A, 12.14, Datei 106): ein kurzer Lauf wie Xaver
 * (Kette G0–G3, Fenster wie P13a), {@code --vor} Stunden ohne Seegang, dann {@code --dauer} Stunden mit; das für jedes
 * Paar der Gitter des Seegangs aus {@code --gitter} (G2w, G3w in m). Kein Archiv, kein Sediment: Es geht nur um die Zeit.
 * <ul>
 * <li>Prüfstein 1: Summe der Rechenzeit aller Module gegen die Wanduhr im selben Fenster (90–100 %).</li>
 * <li>Prüfstein 2: die Stoppuhr im Seegang deckt seine Rechenzeit im Takt (≥ 95 %).</li>
 * <li>Auskunft: Sekunden je Stunde Modellzeit für Seegang und hydro, je Gitter und Teil, Teilschritte der Ausbreitung.</li>
 * </ul>
 * Attest {@code docs/status/P13d_Seegang.md}.
 */
public final class SeegangKostenCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", gitter = "500,100;1000,200";
    static Instant start = Instant.parse("2013-12-05T10:00:00Z");
    static double vor = 2, dauer = 2;

    public static void main(String[] args) {
        BildCheck.fenster = "400,850,700,400";
        for (int i = 0; i + 1 < args.length; i += 2) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--gebiet" -> gebiet = v;
                case "--start" -> start = Instant.parse(v);
                case "--vor" -> vor = Double.parseDouble(v);
                case "--dauer" -> dauer = Double.parseDouble(v);
                case "--gitter" -> gitter = v;
                case "--fenster3" -> BildCheck.fenster = v;
                default -> { }
            }
        }
        System.out.println("SeegangKostenCheck – was der Seegang im Sturm kostet (Werkbuch HalligHooge Teil A, P13d, A33)");
        long n0 = System.nanoTime();
        try {
            File gb = new File(gebiet);
            int[] f3 = BildCheck.fenster3(gb);
            A.info("Lauf", f("Start %s, %.1f h ohne Seegang, dann %.1f h mit; Fenster G3 %s; Gitter des Seegangs %s", start, vor, dauer, BildCheck.fenster, gitter));
            for (String paar : gitter.split(";")) {
                String[] t = paar.split(",");
                lauf(gb, f3, Double.parseDouble(t[0].trim()), Double.parseDouble(t[1].trim()));
            }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        Path p = A.schreibe(Path.of("docs", "status", "P13d_Seegang.md"), "P13d – SeegangKostenCheck (A33)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    static void lauf(File gb, int[] f3, double g2, double g3) throws Exception {
        String wer = f("Seegang %.0f m und %.0f m", g2, g3);
        A.teil(wer);
        Lauf.Einstellung e = BildCheck.einstellung(gb, f3, start);
        e.name = "P13d SeegangKostenCheck " + wer;
        e.seegang = Lauf.Einstellung.Seegang.KARTE;
        e.seegangG2 = g2;
        e.seegangG3 = g3;
        e.seegangAb = Math.floor(vor * 3600 / e.seegangKopplung) * e.seegangKopplung;
        double ende = e.seegangAb + dauer * 3600;
        try (Lauf l = Lauf.bauen(e)) {
            A.info("Lauf (" + wer + ")", l + f("; Aufbau %.1f s", l.aufbau));
            WavesModul w = l.waves;
            List<String> gn = new ArrayList<>();
            for (Gitter g : w.gitter) gn.add(f("%s %d × %d zu %.0f m", g.name, g.nx, g.ny, g.dx));
            A.info("Gitter des Seegangs (" + wer + ")", String.join("; ", gn) + f("; %d × %d Stützstellen; Kopplung %.0f s, Quellen bis %.0f s",
                    e.seegangStuetzen[0], e.seegangStuetzen[1], w.kopplung, w.dtQuellen));
            l.bis(e.seegangAb);
            double hy0 = l.takt.rechenzeit("hydro"), wv0 = l.takt.rechenzeit("waves"), al0 = l.takt.rechenzeitAlle(), su0 = w.stoppuhrSumme();
            double[][] st0 = kopie(w.stoppuhr), r0 = rechner(w);
            long n1 = System.nanoTime();
            for (double t = e.seegangAb + 1800; ; t = Math.min(t + 1800, ende)) {
                l.bis(t);
                double[] m = w.hsMax();
                System.out.printf(Locale.ROOT, "  %s: %.0f s gerechnet; Seegang %.0f s, hydro %.0f s; Hs bis %s m%n", l.zeitpunkt(l.zeit()), (System.nanoTime() - n1) / 1e9,
                        l.takt.rechenzeit("waves") - wv0, l.takt.rechenzeit("hydro") - hy0, java.util.Arrays.toString(java.util.Arrays.stream(m).map(x -> Math.round(x * 100) / 100.0).toArray()));
                if (t >= ende) break;
            }
            double wand = (System.nanoTime() - n1) / 1e9, h = dauer;
            double hy = l.takt.rechenzeit("hydro") - hy0, wv = l.takt.rechenzeit("waves") - wv0, al = l.takt.rechenzeitAlle() - al0, su = w.stoppuhrSumme() - su0;
            A.pruefe(al / wand >= 0.9 && al / wand <= 1.0 + 1e-9, "Prüfstein 1: Rechenzeit aller Module gegen die Wanduhr im selben Fenster (" + wer + ")",
                    f("%.0f s gegen %.0f s (%.1f %%)", al, wand, 100 * al / wand), "90–100 %");
            A.pruefe(su / wv >= 0.95, "Prüfstein 2: Stoppuhr im Seegang gegen seine Rechenzeit im Takt (" + wer + ")", f("%.1f s gegen %.1f s (%.1f %%)", su, wv, 100 * su / wv), "≥ 95 %");
            A.info("Rechenzeit je Stunde Modellzeit mit Seegang (" + wer + ")", f("Seegang %.1f s, hydro %.1f s, alle Module %.1f s, Wanduhr %.1f s (Seegang / hydro %.2f; Xaver P13a: 99,6 s und 29,3 s)",
                    wv / h, hy / h, al / h, wand / h, wv / hy));
            double[][] st1 = w.stoppuhr, r1 = rechner(w);
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < w.gitter.size(); k++) {
                Gitter g = w.gitter.get(k);
                double summe = 0;
                for (int q = 0; q < WavesModul.TEILE.length; q++) summe += st1[k][q] - st0[k][q];
                b.append(k > 0 ? "; " : "").append(f("%s: %.1f s je h", g.name, summe / h));
                for (int q = 0; q < WavesModul.TEILE.length; q++) b.append(f(", %s %.1f", WavesModul.TEILE[q], (st1[k][q] - st0[k][q]) / h));
                if (r1[k] != null && r0[k] != null) {
                    double sch = r1[k][3] - r0[k][3];
                    b.append(f(" (Ausbreitung %.1f, Quellen %.1f; %.1f Teilschritte je Schritt, %.0f Schritte)", (r1[k][0] - r0[k][0]) / h, (r1[k][1] - r0[k][1]) / h,
                            (r1[k][2] - r0[k][2]) / Math.max(1, sch), sch));
                }
            }
            A.info("Seegang je Gitter und Teil, Sekunden je Stunde Modellzeit (" + wer + ")", b.toString());
            A.info("Zeit (" + wer + ")", f("%.1f h Modellzeit, davon %.1f h mit Seegang in %.1f min", ende / 3600, h, wand / 60));
        }
    }

    static double[][] kopie(double[][] a) {
        double[][] b = new double[a.length][];
        for (int i = 0; i < a.length; i++) b[i] = a[i].clone();
        return b;
    }

    static double[][] rechner(WavesModul w) {
        double[][] r = new double[w.waves.size()][];
        for (int k = 0; k < r.length; k++) { Waves x = w.waves.get(k); double[] s = w.rechner.stoppuhr(x); r[k] = s == null ? null : s.clone(); }
        return r;
    }
}
