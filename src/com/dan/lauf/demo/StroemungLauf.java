package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.lauf.Stroemung;

import java.io.File;
import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Der Nachlauf der Strömung über abgelegte Läufe (P11c1, Werkbuch HalligHooge Teil A, 12.12; Prüfsteine 15 und 16):
 * je Archiv {@link Stroemung} bis zum letzten Stand, Ablage in {@code hydro_G3}, dazu
 * <ul>
 * <li>Prüfstein 15: Wasserstand des Nachlaufs gegen das Archiv in Zellen, die in beiden nass sind, ab 2 h nach dem
 * Anfang: RMS ≤ 2 cm, in 95 % der Vergleiche ≤ 10 cm [G] (das Größte zur Auskunft);</li>
 * <li>Prüfstein 16: Massenbilanz des Teilbeckens (mittleres Drittel des Fensters) aus den Flüssen über seinen Rand gegen
 * die Änderung seines Volumens: ≤ 1 % der Spanne seines Volumens [G].</li>
 * </ul>
 * Aufruf: {@code --gebiet <Ordner> --archive <Ordner>,<Ordner>,… [--attest <Name>] [--hoechstens <h>] [--prozessor]
 * [--ohne-ablage] [--probe]}. Mit {@code --probe} endet der Lauf nur bei Fehlern mit Exit 1, nicht bei Verstößen. Attest {@code docs/status/<Name>.md} (Vorgabe {@code P11c1_Stroemung}).
 */
public final class StroemungLauf {
    public static void main(String[] args) throws Exception {
        String gebiet = null, archive = null, name = "P11c1_Stroemung";
        double hoechstens = Double.NaN;
        boolean probe = false;                                             // --probe: Exit 1 nur bei Fehlern, nicht bei Verstößen gegen Prüfsteine
        boolean karte = true, ablegen = true;
        for (int i = 0; i < args.length; i++) {
            String v = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--gebiet" -> gebiet = v;
                case "--archive" -> archive = v;
                case "--attest" -> name = v;
                case "--hoechstens" -> hoechstens = Double.parseDouble(v) * 3600;
                case "--prozessor" -> karte = false;
                case "--ohne-ablage" -> ablegen = false;
                case "--probe" -> probe = true;
                default -> { }
            }
        }
        Attest A = new Attest("com.dan.lauf.Stroemung", Stroemung.VERSION);
        System.out.println("StroemungLauf – com.dan.lauf.Stroemung " + Stroemung.VERSION + " (Werkbuch HalligHooge Teil A, P11c1)");
        int fehler = 0;
        for (String a : archive.split(",")) {
            Path p = Path.of(a.trim());
            String kurz = p.getParent().getFileName() + "\\" + p.getFileName();
            A.teil(kurz);
            long n0 = System.nanoTime();
            Stroemung.Einstellung e = new Stroemung.Einstellung();
            e.gebiet = new File(gebiet);
            e.archiv = p;
            e.karte = karte;
            e.ablegen = ablegen;
            e.hoechstens = hoechstens;
            try (Stroemung s = Stroemung.bauen(e)) {
                double bau = (System.nanoTime() - n0) / 1e9;
                A.info("Nachlauf", f("%s, %d Stände zu %.0f s (%.1f h), Boden %s; %s; Bau %.1f s", s.gitter(), s.a3.staende(), s.kopplung, (s.letzte - s.erste) / 3600,
                        s.aBoden != null ? "je Intervall aus boden_G3" : "fest", s.hydro.rechnung.geraet(), bau));
                for (String h : s.hinweise) A.info("Hinweis des Busses", h);
                s.fortschritt = System.out::println;
                s.rechne();
                double stunden = (s.letzte - s.erste) / 3600;
                A.info("Zeiten", f("Rechnen %.0f s (%.1f s je Stunde Szenenzeit), Ablage %.0f s, %d Stände abgelegt, %d Zellen mit neuem Boden", s.sekundenRechnen,
                        s.sekundenRechnen / Math.max(1e-9, stunden), s.sekundenAblage, s.staende, s.hydro.bodenZellen));
                double rms = s.rms(), q95 = s.quantil(0.95);
                A.pruefe(rms <= 0.02 && q95 <= 0.10, "Prüfstein 15: Wasserstand gegen das Archiv (nasse Zellen, ab 2 h)",
                        f("RMS %.1f cm, 95 %% ≤ %.1f cm, größte %.1f cm, Mittel (Archiv − Nachlauf) %+.1f cm; %d Vergleiche", 100 * rms, 100 * q95, 100 * s.groesste,
                                100 * s.summe / Math.max(1, s.vergleiche), s.vergleiche), "RMS ≤ 2 cm, 95 % ≤ 10 cm [G]");
                String[] kn = {"Randstreifen (" + Stroemung.RAND + " Zellen)", "innen flach (Tiefe < 0,5 m)", "innen tief"};
                StringBuilder sb = new StringBuilder();
                for (int q = 0; q < 3; q++) sb.append(q == 0 ? "" : "; ").append(f("%s: RMS %.1f cm, Anteil der Vergleiche %.0f %%, Anteil am Quadrat %.0f %%", kn[q],
                        100 * Math.sqrt(s.klasseQ[q] / Math.max(1, s.klasseN[q])), 100.0 * s.klasseN[q] / Math.max(1, s.vergleiche), 100 * s.klasseQ[q] / Math.max(1e-30, s.summeQ)));
                A.info("Prüfstein 15 aufgeteilt", sb.toString());
                A.info("Größte Abweichung", f("Zelle %d, %d (O %.0f, N %.0f), %.2f h nach dem ersten Stand, Tiefe im Archiv %.2f m", s.groessteI, s.groessteJ,
                        s.h3.gitter.x0 + (s.groessteI + 0.5) * s.h3.gitter.dx, s.h3.gitter.y0 + (s.groessteJ + 0.5) * s.h3.gitter.dy, s.groessteT / 3600, s.groessteH));
                double bf = s.bilanzFehler();
                int[] b = s.becken;
                A.pruefe(bf <= 0.01, "Prüfstein 16: Massenbilanz des Beckens (Zellen " + b[0] + "–" + b[2] + " × " + b[1] + "–" + b[3] + ")",
                        f("ΔV %.4g m³, Zufluss über den Rand %.4g m³, Spanne des Volumens %.4g m³: Fehler %.2g", s.volumen - s.volumenAnfang, s.zufluss,
                                s.volumenMax - s.volumenMin, bf), "≤ 1 % der Spanne [G]");
                double[] bil = s.hydro.bilanz(1);
                A.info("Bilanz von G3", f("V₀ %.4g m³, V %.4g m³, Zufluss %.4g m³, Abfluss %.4g m³, abgeschnitten %.3g m³", bil[0], bil[1], bil[2], bil[3], bil[4]));
            } catch (Throwable x) {
                A.fehler(x);
                fehler++;
            }
            A.info("Dauer", f("%.0f s", (System.nanoTime() - n0) / 1e9));
        }
        Path at = A.schreibe(Path.of("docs", "status", name + ".md"), "P11c1 – Strömung aus dem Nachlauf (" + name + ")");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(fehler == 0 && (probe || A.verstoesse() == 0) ? 0 : 1);
    }
}
