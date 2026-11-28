package com.dan.lauf.demo;

import com.dan.core.pruef.Attest;
import com.dan.lauf.SeegangNachlauf;

import java.io.File;
import java.nio.file.Path;

import static com.dan.core.pruef.Attest.f;

/**
 * Der Nachlauf des Seegangs über abgelegte Läufe (Seegang Werkbuch, S1; SF6 A): je Archiv {@link SeegangNachlauf} bis
 * zum letzten Stand, Ablage in {@code seegang_<Gitter>}, dazu je Gitter des Seegangs
 * <ul>
 * <li>Prüfstein S1.1: Windsee und Dünung zusammen ergeben m₀ der Zelle: |Hs_W² + Hs_D² − Hs²| / Hs² ≤ 1e-6 in jeder Zelle
 * mit Seegang;</li>
 * <li>Prüfstein S1.2: Hs des Nachlaufs gegen Hs des Archivs, Zellen mit Hs über 0,1 m, nach 3 h:
 * √(Σ(Hs_N − Hs_A)² / Σ Hs_A²) ≤ 10 % [G] (über alle Stände und der Mittelwert zur Auskunft);</li>
 * <li>Prüfstein S1.3: kein Wert des Spektrums, der nicht endlich oder negativ ist;</li>
 * <li>zur Auskunft: die Anteile von Schaumkronen und Brechen an {@code diss} des Schritts, das Brechen als Rate gegen
 * das abgelegte, die Bodenreibung, der Anteil der Windsee an m₀, Zeiten und die Stoppuhr des Seegangs.</li>
 * </ul>
 * Aufruf: {@code --gebiet <Ordner> --archive <Ordner>,<Ordner>,… [--attest <Name>] [--hoechstens <h>] [--vorlauf <h>]
 * [--stuetzen <nf>,<nd>] [--prozessor] [--ohne-ablage] [--ohne-strom] [--probe]}. Mit {@code --probe} endet der Lauf nur bei
 * Fehlern mit Exit 1, nicht bei Verstößen. Attest {@code docs/status/<Name>.md} (Vorgabe {@code S1_Nachlauf}).
 */
public final class SeegangNachlaufLauf {
    public static void main(String[] args) throws Exception {
        String gebiet = null, archive = null, name = "S1_Nachlauf";
        double hoechstens = Double.NaN, vorlauf = Double.NaN;
        int[] stuetzen = null;
        boolean probe = false, karte = true, ablegen = true, strom = true;
        for (int i = 0; i < args.length; i++) {
            String v = i + 1 < args.length ? args[i + 1] : null;
            switch (args[i]) {
                case "--gebiet" -> gebiet = v;
                case "--archive" -> archive = v;
                case "--attest" -> name = v;
                case "--hoechstens" -> hoechstens = Double.parseDouble(v) * 3600;
                case "--vorlauf" -> vorlauf = Double.parseDouble(v) * 3600;
                case "--stuetzen" -> {
                    String[] s = v.split(",");
                    stuetzen = new int[] {Integer.parseInt(s[0].trim()), Integer.parseInt(s[1].trim())};
                }
                case "--prozessor" -> karte = false;
                case "--ohne-ablage" -> ablegen = false;
                case "--ohne-strom" -> strom = false;
                case "--probe" -> probe = true;
                default -> { }
            }
        }
        Attest A = new Attest("com.dan.lauf.SeegangNachlauf", SeegangNachlauf.VERSION);
        System.out.println("SeegangNachlaufLauf – com.dan.lauf.SeegangNachlauf " + SeegangNachlauf.VERSION + " (Seegang Werkbuch, S1)");
        if (gebiet == null || archive == null) {
            System.out.println("Aufruf: --gebiet <Ordner> --archive <Ordner>,… [--attest <Name>] [--hoechstens <h>] [--vorlauf <h>] [--stuetzen nf,nd] [--prozessor] [--ohne-ablage] [--ohne-strom] [--probe]");
            System.exit(2);
        }
        int fehler = 0;
        for (String a : archive.split(",")) {
            Path p = Path.of(a.trim());
            String kurz = (p.getParent() != null ? p.getParent().getFileName() + "\\" : "") + p.getFileName();
            A.teil(kurz);
            long n0 = System.nanoTime();
            SeegangNachlauf.Einstellung e = new SeegangNachlauf.Einstellung();
            e.gebiet = new File(gebiet);
            e.archiv = p;
            e.karte = karte;
            e.ablegen = ablegen;
            e.hoechstens = hoechstens;
            e.stuetzen = stuetzen;
            e.strom = strom;
            if (!Double.isNaN(vorlauf)) e.vorlauf = vorlauf;
            try (SeegangNachlauf s = SeegangNachlauf.bauen(e)) {
                double bau = (System.nanoTime() - n0) / 1e9;
                StringBuilder gi = new StringBuilder();
                for (int g = 0; g < s.waves.gitter.size(); g++) {
                    var x = s.waves.gitter.get(g);
                    gi.append(g == 0 ? "" : ", ").append(f("%s %d × %d zu %.0f m", x.name, x.nx, x.ny, x.dx));
                }
                var w0 = s.waves.waves.get(0);
                A.info("Nachlauf", f("%s; %d × %d Stützstellen; Kopplung %.0f s, Quellen je %.0f s; %.1f h ab dem ersten Stand, Vorlauf %.1f h; Wind %s; %s; Bau %.1f s", gi, w0.nf, w0.nd,
                        s.kopplung, s.waves.dtQuellen, (s.letzte - s.erste) / 3600, (s.erste - s.beginn) / 3600, s.antrieb.schub, s.waves.rechner.geraet(), bau));
                for (String h : s.hinweise) A.info("Hinweis", h);
                s.fortschritt = System.out::println;
                s.rechne();
                double stunden = (s.letzte - s.beginn) / 3600;
                A.info("Zeiten", f("Rechnen %.0f s (%.1f s je Stunde Modellzeit mit Vorlauf), Diagnose und Ablage %.0f s, %d Stände %s", s.sekundenRechnen,
                        s.sekundenRechnen / Math.max(1e-9, stunden), s.sekundenAblage, s.staende, ablegen ? "abgelegt" : "gerechnet, nicht abgelegt"));
                A.info("Stoppuhr des Seegangs", s.waves.stoppuhrText());
                for (int g = 0; g < s.waves.gitter.size(); g++) {
                    String gn = s.waves.gitter.get(g).name;
                    A.pruefe(s.partFehler[g] <= 1e-6, "Prüfstein S1.1 (" + gn + "): Windsee und Dünung zusammen gegen m₀ der Zelle",
                            f("größte relative Abweichung %.2g", s.partFehler[g]), "≤ 1e-6");
                    double rs = s.relativ(g, true), ra = s.relativ(g, false);
                    if (s.nSpaet[g] == 0) A.info("Prüfstein S1.2 (" + gn + ")", f("Lauf kürzer als %.0f h: nicht geprüft; über alle Stände %.1f %%, Mittel %+.1f cm (%d Vergleiche)",
                            SeegangNachlauf.ANLAUF / 3600, 100 * ra, 100 * s.dsum[g] / Math.max(1, s.n[g]), s.n[g]));
                    else A.pruefe(Double.isFinite(rs) && rs <= 0.10, "Prüfstein S1.2 (" + gn + "): Hs gegen das Archiv nach " + (int) (SeegangNachlauf.ANLAUF / 3600) + " h (Hs im Archiv über "
                                    + SeegangNachlauf.HS_MIN + " m)",
                            f("relativ %.1f %% (%d Vergleiche); über alle Stände %.1f %%, Mittel (Nachlauf − Archiv) %+.1f cm, größte %.2f m", 100 * rs, s.nSpaet[g], 100 * ra,
                                    100 * s.dsum[g] / Math.max(1, s.n[g]), s.groesste[g]), "≤ 10 % [G]");
                    A.info("Senken (" + gn + ")", f("Anteile an diss des Schritts: Schaumkronen (Rate) %.3f, Brechen und Kappung (abgelegt als dbr) %.3f; Brechen als Rate auf dem Spektrum nach dem Schritt gegen dbr %.3f; Bodenreibung / diss %.3f",
                            s.summeWc[g] / Math.max(1e-30, s.summeDiss[g]), s.summeBrSchritt[g] / Math.max(1e-30, s.summeDiss[g]), s.summeBr[g] / Math.max(1e-30, s.summeBrSchritt[g]),
                            s.summeBf[g] / Math.max(1e-30, s.summeDiss[g])));
                    A.info("Windsee (" + gn + ")", f("Anteil an m₀ über alle Zellen und Stände %.1f %%", 100 * s.m0Windsee[g] / Math.max(1e-30, s.m0Alle[g])));
                }
                A.pruefe(s.unheil == 0, "Prüfstein S1.3: Werte des Spektrums, die nicht endlich oder negativ sind", Long.toString(s.unheil), "0");
            } catch (Throwable x) {
                A.fehler(x);
                fehler++;
            }
            A.info("Dauer", f("%.0f s", (System.nanoTime() - n0) / 1e9));
        }
        Path at = A.schreibe(Path.of("docs", "status", name + ".md"), "S1 – Seegang aus dem Nachlauf (" + name + ")");
        if (at != null) System.out.println("\nAttest: " + at);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(fehler == 0 && (probe || A.verstoesse() == 0) ? 0 : 1);
    }
}
