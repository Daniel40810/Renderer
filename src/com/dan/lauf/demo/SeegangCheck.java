package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.pruef.Attest;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Seegangsnetz;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Lauf;
import com.dan.physics.waves.Randspektrum;
import com.dan.physics.waves.Waves;
import com.dan.render.water.Seegangsprobe;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine von P8b (Werkbuch HalligHooge Teil A, 12.9, Feinplan P8b): der Seegang im Lauf und im Bild.
 * <ul>
 * <li><b>Teil 1, Läufe:</b> Sturm (23.10. bis 26.10., abgelegt ab 25.10., 06:00 UTC) und ruhiger Tag (28.10. bis 31.10.,
 * abgelegt der 30.10.) wie {@link BildCheck}, dazu der Seegang auf der Karte ab {@code --vorlauf} Stunden vor dem Ablegen
 * und im Archiv. Prüfsteine 1 (Verdrahtung), 6 (Spektrum heil am Ende), 7 (Rechenzeit des Seegangs gegen hydro, über
 * die Zeit, in der der Seegang rechnet).</li>
 * <li><b>Teil 2, Archiv:</b> Prüfsteine 2 (Rand aus ERA5), 3 (Schachtel), 4 (Sturm gegen ERA5 im Inneren), 5 (Sättigung
 * im Watt), 6 (kein NaN im Archiv); Status-Bild {@code docs/status/P8b_Seegang.png}.</li>
 * <li><b>Teil 3, Bild:</b> Prüfsteine 8 (Stufe 2 ohne Seegang gegen Stufe 1), 9 (lange Wellen im Shader gegen den
 * Prozessor), 10 (Bilder/s in 1080p aus dem Archiv); Status-Bild {@code docs/status/P8b_Bild.png} (Sichtprüfung,
 * Prüfstein 12).</li>
 * </ul>
 * Attest {@code docs/status/P8b_Seegang.md}.
 */
public final class SeegangCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", archiv, archivRuhig, nur = "1,2,3";
    static boolean neu;
    static int[] stuetzen = {28, 36};
    static double vorlauf = 12;
    /** Zeitbudget von hydro G2 → G3 im Zeitraffer 25 (P4d, Fall 14): 48 s je 20 min Szenenzeit; Grenze für Prüfstein 7. */
    static final double HYDRO_BUDGET = 48;
    static int breite = 1920, hoehe = 1080, bilder = 60;
    /** Fenster der Auswertung des Sturms gegen ERA5 (Prüfstein 4). */
    /** Zeit der Ansichten in Teil 3; null: der höchste Seegang auf dem feinsten Netz bei Tag (09–15 UTC). */
    static Instant bildZeit;
    /** Vorsilbe der Ausgaben (Attest, Bilder); P8b_ oder seit P8c mit {@code --phase P8c} dessen. */
    static String phase = "P8b";
    /** EasyGSH, Kennwerte 2015 (Feldvergleich, P8c, F75 A); null: neben dem Gebiet unter gezeiten\\easygsh_kennwerte\\2015. */
    static File easygsh;
    static Instant sturmVon = Instant.parse("2026-10-25T06:00:00Z"), sturmBis = Instant.parse("2026-10-26T00:00:00Z");

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            String a = args[i], v = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--gebiet" -> gebiet = v;
                case "--archiv" -> archiv = v;
                case "--archiv-ruhig" -> archivRuhig = v;
                case "--nur" -> nur = v;
                case "--neu" -> neu = true;
                case "--stuetzen" -> stuetzen = new int[] {Integer.parseInt(v.split(",")[0].trim()), Integer.parseInt(v.split(",")[1].trim())};
                case "--vorlauf" -> vorlauf = Double.parseDouble(v);
                case "--breite" -> breite = Integer.parseInt(v);
                case "--hoehe" -> hoehe = Integer.parseInt(v);
                case "--bilder" -> bilder = Integer.parseInt(v);
                case "--start" -> BildCheck.start = Instant.parse(v);
                case "--ab" -> BildCheck.ab = Instant.parse(v);
                case "--ende" -> BildCheck.ende = Instant.parse(v);
                case "--anlauf" -> BildCheck.anlauf = Double.parseDouble(v);
                case "--fenster3" -> BildCheck.fenster = v;
                case "--bildzeit" -> bildZeit = Instant.parse(v);
                case "--phase" -> phase = v;
                case "--easygsh" -> easygsh = new File(v);
                case "--sturm" -> { sturmVon = Instant.parse(v.split(",")[0].trim()); sturmBis = Instant.parse(v.split(",")[1].trim()); }
                default -> { }
            }
        }
        File gb = new File(gebiet);
        File ar = archiv != null ? new File(archiv) : new File(gb.getAbsoluteFile().getParentFile(), "_p8" + File.separator + "archiv");
        File arR = archivRuhig != null ? (archivRuhig.equals("-") ? null : new File(archivRuhig)) : new File(ar.getAbsoluteFile().getParentFile(), "archiv_ruhig");
        System.out.println("SeegangCheck – P8b (Werkbuch HalligHooge Teil A, 12.9)");
        A.info("Gebiet, Archive", gb + "; " + ar + (arR != null ? "; " + arR : ""));
        long n0 = System.nanoTime();
        try {
            Gebiet ge = Gebiet.lies(gb);
            int[] f3 = BildCheck.fenster3(gb);
            if (nur.contains("1")) {
                teil1(gb, ar, f3, "Sturm", BildCheck.start, BildCheck.ab, BildCheck.ende, true);
                if (arR != null) teil1(gb, arR, f3, "ruhiger Tag", BildCheck.startRuhig, BildCheck.abRuhig, BildCheck.endeRuhig, false);
            }
            if (nur.contains("2")) teil2(gb, ar, arR);
            if (nur.contains("3"))
                try (Geraet g = new Geraet()) {
                    A.info("Gerät", g.name + " (" + g.treiber + ")");
                    Konto konto = g.konto("SeegangCheck", 3L << 30);
                    teil3(g, konto, gb, ge, ar);
                }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        Path p = A.schreibe(Path.of("docs", "status", phase + "_Seegang.md"), phase + " – SeegangCheck (Seegang im Lauf und im Bild" + (phase.equals("P8b") ? ")" : ", mit Rückwirkung auf hydro)"));
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Teil 1

    static void teil1(File gb, File ar, int[] f3, String wer, Instant start, Instant ab, Instant ende, boolean zeit) throws Exception {
        A.teil("1 Lauf mit Seegang und Archiv, " + wer);
        double dauer = (ende.toEpochMilli() - start.toEpochMilli()) / 1000.0;
        if (new File(ar, "waves_G2w" + File.separator + "lauf.txt").isFile() && !neu) {
            try (Archivquelle q = new Archivquelle(ar.toPath())) {
                A.info("Archiv liegt schon (--neu rechnet neu)", f("%s: %d Stände des Wassers, %d des Seegangs; %s", ar, q.staende(), q.seegangZeiten().length, q.kopf.antrieb));
            }
            return;
        }
        if (new File(ar, "G3" + File.separator + "lauf.txt").isFile()) throw new IllegalStateException(ar + ": dort liegt ein Archiv; bitte erst wegräumen");
        Lauf.Einstellung e = BildCheck.einstellung(gb, f3, start);
        e.archiv = ar;
        e.archivAb = Math.max(0, (ab.toEpochMilli() - start.toEpochMilli()) / 1000.0);
        e.name = "P8b SeegangCheck " + start;
        e.seegang = Lauf.Einstellung.Seegang.KARTE;
        e.seegangStuetzen = stuetzen;
        e.seegangAb = Math.max(0, e.archivAb - vorlauf * 3600);
        e.seegangAb = Math.floor(e.seegangAb / e.seegangKopplung) * e.seegangKopplung;
        long n0 = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            A.info("Lauf", l + f("; Aufbau %.1f s", l.aufbau));
            A.pruefe(l.waves != null && l.module().contains(l.waves), "Prüfstein 1: waves am Bus (" + wer + ")", l.waves == null ? "fehlt" : l.waves.name() + " " + l.waves.version(), "angemeldet und verdrahtet");
            A.info("Seegang", f("Gitter %s; %d × %d Stützstellen; rechnet ab %s (%.0f h vor dem Ablegen); Kopplung %.0f s",
                    BildCheck.namen(l.waves.gitter), stuetzen[0], stuetzen[1], l.zeitpunkt(e.seegangAb), (e.archivAb - e.seegangAb) / 3600, e.seegangKopplung));
            A.info("Hinweise beim Verdrahten", l.hinweise.isEmpty() ? "keine" : String.join("; ", l.hinweise));
            long n1 = System.nanoTime();
            double hy0 = Double.NaN, wv0 = Double.NaN;
            for (double t = Math.min(3600, dauer); ; t = Math.min(t + 3600, dauer)) {
                if (Double.isNaN(hy0) && t > e.seegangAb) {
                    l.bis(e.seegangAb);
                    hy0 = l.takt.rechenzeit("hydro");
                    wv0 = l.takt.rechenzeit("waves");
                }
                l.bis(t);
                double[] m = l.waves.hsMax();
                System.out.printf("  %s: %.0f s gerechnet; Hs bis %s m%n", l.zeitpunkt(l.zeit()), (System.nanoTime() - n1) / 1e9, Arrays.toString(Arrays.stream(m).map(x -> Math.round(x * 100) / 100.0).toArray()));
                if (t >= dauer) break;
            }
            double s = (System.nanoTime() - n1) / 1e9;
            long unheil = l.waves.unheil();
            A.pruefe(unheil == 0, "Prüfstein 6: Spektrum am Ende endlich und nicht negativ (" + wer + ")", unheil + " Werte", "0");
            A.pruefe(l.bus.ausserhalb() == 0, "Lauf (" + wer + "): außerhalb der Stände gelesen", String.valueOf(l.bus.ausserhalb()), "0");
            double hy = l.takt.rechenzeit("hydro") - hy0, wv = l.takt.rechenzeit("waves") - wv0;
            A.info("Zeit", f("%.1f h Modellzeit in %.1f min (ohne Bild); Archiv %d Stände des Wassers, %d des Seegangs, %.0f MB", dauer / 3600, s / 60,
                    l.archiv.staende, l.archiv.seeStaende, l.archiv.bytes / 1e6));
            // Prüfstein 7, Maßstab berichtigt (Abnahme P8b, Entscheidung 16.11.2026): Die Vorgabe aus P8a maß den Seegang
            // gegen 48 s je 20 min Szenenzeit; das ist das Zeitbudget von hydro G2 → G3 bei Zeitraffer 25, nicht seine
            // Rechenzeit (die liegt im Archivlauf bei rund 7 s). Grenze ist das Budget; hydro steht zur Auskunft dabei.
            double je20 = wv / Math.max(1e-9, (dauer - e.seegangAb) / 1200.0);
            if (zeit) A.pruefe(je20 <= HYDRO_BUDGET, "Prüfstein 7: Rechenzeit des Seegangs je 20 min Szenenzeit, solange er rechnet (" + stuetzen[0] + " × " + stuetzen[1] + ")",
                    f("%.1f s (Seegang %.0f s, hydro %.0f s, %.2f)", je20, wv, hy, wv / hy), f("≤ %.0f s (Zeitbudget, Zeitraffer 25)", HYDRO_BUDGET));
            else A.info("Rechenzeit (" + wer + ")", f("Seegang %.0f s, hydro %.0f s (%.2f)", wv, hy, wv / hy));
        }
        A.info("Teil 1 gesamt (" + wer + ")", f("%.1f min", (System.nanoTime() - n0) / 6e10));
    }

    // ------------------------------------------------------------------ Teil 2

    static void teil2(File gb, File ar, File arR) throws Exception {
        A.teil("2 Aus dem Archiv");
        Wetterfeld wf = Wetterfeld.lies(gb, Wetterfeld.SEEGANG);
        try (Archivquelle q = new Archivquelle(ar.toPath())) {
            List<Gitter> wg = q.gitter(), sg = q.seegangGitter();
            if (sg.isEmpty()) throw new IllegalStateException(ar + ": kein Seegang im Archiv");
            Gitter g2w = sg.get(0), g3w = sg.size() > 1 ? sg.get(1) : null;
            int k2 = index(wg, "G2"), k3 = index(wg, "G3");
            double[] zs = q.seegangZeiten();
            Instant t0 = q.start;
            A.info("Archiv", f("%s: Wasser %s, %d Stände; Seegang %s, %d Stände (%s bis %s)", q.herkunft(), BildCheck.namen(wg), q.staende(), BildCheck.namen(sg), zs.length,
                    BildCheck.zeit(t0, zs[0]), BildCheck.zeit(t0, zs[zs.length - 1])));
            float[][] w2 = neu5(g2w), w3 = g3w == null ? null : neu5(g3w);
            float[] max2 = new float[g2w.zellen()], max3 = g3w == null ? null : new float[g3w.zellen()];       // größte Hs im Sturm (Feldvergleich)
            float[] eta = new float[wg.get(k2).zellen()], h2 = new float[eta.length], age = new float[eta.length];
            float[] eta3 = k3 < 0 ? null : new float[wg.get(k3).zellen()], h3 = k3 < 0 ? null : new float[wg.get(k3).zellen()], age3 = k3 < 0 ? null : new float[wg.get(k3).zellen()];
            Waves leer = new Waves(g2w.nx, g2w.ny, g2w.dx, g2w.dy, stuetzen[0], 0.04, 0.6, stuetzen[1]);
            Randspektrum rs = new Randspektrum(wf, g2w, leer);
            double s2 = 0, m2 = 0, s3 = 0, m3 = 0, sH = 0, sT = 0;
            int n2 = 0, n3 = 0, n4 = 0;
            long satt = 0, alle = 0, nan = 0;
            double gross = 0;
            // Prüfstein 5 zum Vergleich mit der Tiefe, die der Seegang gesehen hat: Er liest water.h am Anfang seines
            // Intervalls (eine Kopplung vor dem Stand) bilinear auf G3w, gleich wie wert() hier
            double dk = zs.length > 1 ? zs[1] - zs[0] : 1200;
            float[] h3a = k3 < 0 ? null : new float[wg.get(k3).zellen()], e3a = k3 < 0 ? null : new float[h3a.length], a3a = k3 < 0 ? null : new float[h3a.length];
            long sattA = 0, alleA = 0, ueber = 0, ueberFallend = 0, ueberFlach = 0;
            double grossA = 0;
            StringBuilder b4 = new StringBuilder();
            double hsSpitze = -1, tSpitze = zs[0];
            for (double ts : zs) {
                Instant t = t0.plusMillis(Math.round(ts * 1000));
                q.seegang(ts, 0, w2);
                if (w3 != null) q.seegang(ts, 1, w3);
                Instant tz = t0.plusMillis(Math.round(ts * 1000));
                if (!tz.isBefore(sturmVon) && !tz.isAfter(sturmBis)) {
                    for (int c = 0; c < max2.length; c++) max2[c] = Math.max(max2[c], w2[0][c]);
                    if (w3 != null) for (int c = 0; c < max3.length; c++) max3[c] = Math.max(max3[c], w3[0][c]);
                }
                nan += nanZahl(w2) + (w3 == null ? 0 : nanZahl(w3));
                q.stand(ts, k2, eta, h2, age);
                if (k3 >= 0) q.stand(ts, k3, eta3, h3, age3);
                boolean imSturm = !t.isBefore(sturmVon) && !t.isAfter(sturmBis);
                // Prüfstein 2: erste Zellreihe am Westrand gegen ERA5 an der Geisterzelle
                if (imSturm)
                    for (int j = 0; j < g2w.ny; j++) {
                        int c = g2w.index(0, j);
                        double h = wert(wg.get(k2), h2, g2w.x(0), g2w.y(j));
                        if (!(h > 5) || !(w2[0][c] > 0.05)) continue;
                        double e5 = rs.swh(g2w.rand(Gitter.RAND_WEST, j), t), ab = Math.abs(w2[0][c] / e5 - 1);
                        s2 += ab; m2 = Math.max(m2, ab); n2++;
                    }
                // Prüfstein 3: Randzellen von G3w gegen G2w am selben Ort
                if (w3 != null && imSturm)
                    for (int r = 0; r < g3w.randZellen(); r++) {
                        int[] z = g3w.randZelle(r);
                        int c = g3w.index(z[0], z[1]);
                        if (!(w3[0][c] > 0.1) || !(wert(wg.get(k3), h3, g3w.x(z[0]), g3w.y(z[1])) > 5)) continue;
                        double grob = wert(g2w, w2[0], g3w.x(z[0]), g3w.y(z[1])), ab = Math.abs(w3[0][c] / grob - 1);
                        if (!(grob > 0)) continue;
                        s3 += ab; m3 = Math.max(m3, ab); n3++;
                    }
                // Prüfstein 4: an den Punkten von ERA5 in G2w mit mehr als 15 m Wasser, stündlich
                if (imSturm && Math.abs(ts / 3600 - Math.rint(ts / 3600)) < 1e-9) {
                    Gradraster r = wf.raster;
                    for (int jj = 0; jj < r.ny(); jj++)
                        for (int ii = 0; ii < r.nx(); ii++) {
                            double[] p = g2w.punkt(r.laenge(ii), r.breite(jj));
                            if (!g2w.enthaelt(p[0], p[1]) || p[0] < g2w.x(0) || p[0] > g2w.x(g2w.nx - 1) || p[1] < g2w.y(0) || p[1] > g2w.y(g2w.ny - 1)) continue;
                            double h = wert(wg.get(k2), h2, p[0], p[1]);
                            if (!(h > 15)) continue;
                            Gradraster.Gewichte gw = r.gewichte(r.laenge(ii), r.breite(jj), null, 0, 1, 0);
                            double e5 = wf.wert(wf.groesse("swh"), t, gw), tp5 = wf.wert(wf.groesse("pp1d"), t, gw);
                            double hs = wert(g2w, w2[0], p[0], p[1]), tp = wert(g2w, w2[1], p[0], p[1]);
                            sH += Math.abs(hs / e5 - 1); sT += Math.abs(tp / tp5 - 1); n4++;
                            if (b4.length() < 900 && Math.abs(ts / 10800 - Math.rint(ts / 10800)) < 1e-9)
                                b4.append(f("%s %.2f° O %.2f° N (%.0f m): Hs %.2f/%.2f m, Tp %.1f/%.1f s; ", BildCheck.zeit(t0, ts).substring(11, 16), r.laenge(ii), r.breite(jj), h, hs, e5, tp, tp5));
                        }
                }
                // Prüfstein 5: Hs/h in den nassen Zellen von G3w
                if (w3 != null && k3 >= 0) {
                    double mitte = 0;
                    int nm = 0;
                    q.stand(ts - dk, k3, e3a, h3a, a3a);
                    for (int c = 0; c < g3w.zellen(); c++) {
                        double hs = w3[0][c];
                        if (!(hs > 0)) continue;
                        double x = g3w.x(c % g3w.nx), y = g3w.y(c / g3w.nx);
                        double h = wert(wg.get(k3), h3, x, y), ha = wert(wg.get(k3), h3a, x, y);
                        if (ha >= 0.05) {
                            alleA++;
                            double va = hs / ha;
                            if (va <= 0.8) sattA++;
                            grossA = Math.max(grossA, va);
                        }
                        if (!(h >= 0.05)) continue;
                        alle++;
                        double v = hs / h;
                        if (v <= 0.8) satt++;
                        else {
                            ueber++;
                            if (ha > h) ueberFallend++;
                            if (h < 0.3) ueberFlach++;
                        }
                        gross = Math.max(gross, v);
                        mitte += hs;
                        nm++;
                    }
                    if (nm > 0 && mitte / nm > hsSpitze) { hsSpitze = mitte / nm; tSpitze = ts; }
                }
            }
            A.hoechstens("Prüfstein 2: Hs am Westrand von G2w gegen ERA5, Mittel des Betrags", n2 == 0 ? Double.NaN : 100 * s2 / n2, 5, "%");
            A.info("Prüfstein 2: größte Abweichung; Werte", f("%.1f %%; %d Zellen × Stände (Tiefe > 5 m, Hs > 0,05 m, %s bis %s)", 100 * m2, n2, sturmVon, sturmBis));
            if (w3 != null) {
                A.hoechstens("Prüfstein 3: Hs in den Randzellen von G3w gegen G2w, Mittel des Betrags", n3 == 0 ? Double.NaN : 100 * s3 / n3, 5, "%");
                A.info("Prüfstein 3: größte Abweichung; Werte", f("%.1f %%; %d Zellen × Stände (Tiefe > 5 m wie Prüfstein 2, Hs > 0,1 m)", 100 * m3, n3));
            }
            if (n4 > 0) {
                A.hoechstens("Prüfstein 4: Hs gegen ERA5 im Inneren von G2w, Mittel des Betrags", 100 * sH / n4, 25, "%");
                A.hoechstens("Prüfstein 4: Tp gegen ERA5 (pp1d), Mittel des Betrags", 100 * sT / n4, 25, "%");
                A.info("Prüfstein 4: Werte (Modell/ERA5, alle 3 h)", n4 + " Punkte × Stunden; " + b4);
            } else A.pruefe(false, "Prüfstein 4: Punkte von ERA5 mit mehr als 15 m Wasser in G2w", "keiner", "≥ 1");
            if (w3 != null) {
                A.pruefe(alle > 0 && satt >= 0.999 * alle, "Prüfstein 5: Hs/h ≤ 0,8 in den nassen Zellen von G3w über alle Stände",
                        f("%.3f %% von %d (größtes Hs/h %.2f)", alle == 0 ? 0 : 100.0 * satt / alle, alle, gross), "≥ 99,9 %");
                A.info("Prüfstein 5: über 0,8; davon bei fallendem Wasser; davon unter 0,3 m Tiefe", f("%d; %d; %d", ueber, ueberFallend, ueberFlach));
                A.info("Prüfstein 5 mit der Tiefe, die der Seegang sah (water.h eine Kopplung vor dem Stand, " + f("%.0f s", dk) + ")",
                        f("%.3f %% von %d ≤ 0,8 (größtes Hs/h %.2f)", alleA == 0 ? 0 : 100.0 * sattA / alleA, alleA, grossA));
            }
            long nanR = 0, sattR = 0, alleR = 0;
            double grossR = 0;
            if (arR != null && new File(arR, "waves_G2w" + File.separator + "lauf.txt").isFile())
                try (Archivquelle qr = new Archivquelle(arR.toPath())) {
                    float[][] a2 = neu5(qr.seegangGitter().get(0)), a3 = qr.seegangGitter().size() > 1 ? neu5(qr.seegangGitter().get(1)) : null;
                    Gitter r3 = a3 == null ? null : qr.seegangGitter().get(1);
                    int kr3 = index(qr.gitter(), "G3");
                    float[] hr = kr3 < 0 ? null : new float[qr.gitter().get(kr3).zellen()], er = hr == null ? null : new float[hr.length], alr = hr == null ? null : new float[hr.length];
                    for (double ts : qr.seegangZeiten()) {
                        qr.seegang(ts, 0, a2);
                        nanR += nanZahl(a2);
                        if (a3 != null) { qr.seegang(ts, 1, a3); nanR += nanZahl(a3); }
                        // Prüfstein 5 (P8c: über beide Archive) auch am ruhigen Tag
                        if (a3 != null && hr != null) {
                            qr.stand(ts, kr3, er, hr, alr);
                            for (int c = 0; c < r3.zellen(); c++) {
                                double hs = a3[0][c];
                                if (!(hs > 0)) continue;
                                double h = wert(qr.gitter().get(kr3), hr, r3.x(c % r3.nx), r3.y(c / r3.nx));
                                if (!(h >= 0.05)) continue;
                                alleR++;
                                if (hs / h <= 0.8) sattR++;
                                grossR = Math.max(grossR, hs / h);
                            }
                        }
                    }
                }
            if (alleR > 0) A.pruefe(alle + alleR > 0 && satt + sattR >= 0.999 * (alle + alleR), "Prüfstein 5 über beide Archive (P8c, W7)",
                    f("%.3f %% von %d (ruhiger Tag allein %.3f %% von %d, größtes Hs/h %.2f)", 100.0 * (satt + sattR) / (alle + alleR), alle + alleR, 100.0 * sattR / alleR, alleR, grossR), "≥ 99,9 %");
            feldvergleich(gb, g2w, max2, g3w, max3);
            A.pruefe(nan == 0 && nanR == 0, "Prüfstein 6: kein NaN im Seegang der Archive", nan + " (Sturm), " + nanR + " (ruhiger Tag)", "0");
            A.info("Höchster mittlerer Seegang auf G3w", f("%s: %.2f m", BildCheck.zeit(t0, tSpitze), hsSpitze));
            statusbild(q, g2w, g3w, tSpitze, t0);
        }
    }

    // ------------------------------------------------------------------ Feldvergleich (P8c, F75 A)

    /** Ein Kennwert von EasyGSH als Raster: Werte, Lage der Ecke Nordwest und Weite (m), aus dem GeoTIFF. */
    record Kennwert(float[] v, int nx, int ny, double west, double nord, double weite) {
        /** Wert am Ort (UTM), nächstes Pixel; NaN außerhalb oder ohne Wert. */
        double bei(double x, double y) {
            int i = (int) Math.floor((x - west) / weite), j = (int) Math.floor((nord - y) / weite);
            if (i < 0 || j < 0 || i >= nx || j >= ny) return Double.NaN;
            return v[i + nx * j];
        }
    }

    /**
     * Liest ein GeoTIFF von EasyGSH (float, ein Band). Die Lage aus ModelTransformation (34264): x = a + 100 · Spalte; die
     * Werte von EasyGSH tragen dort 428 050 / 6 095 950, der Auftrag beim WCS war 428 000 bis 500 000 (720 Pixel), also gilt
     * der Wert für die Mitte des Pixels und die Ecke liegt eine halbe Weite davor.
     */
    static Kennwert kennwert(File f) throws Exception {
        javax.imageio.ImageReader r = ImageIO.getImageReadersByFormatName("tiff").next();
        try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(f)) {
            r.setInput(in);
            java.awt.image.Raster ra = r.read(0).getRaster();
            var dir = javax.imageio.plugins.tiff.TIFFDirectory.createFromMetadata(r.getImageMetadata(0));
            double[] t = dir.getTIFFField(34264).getAsDoubles();
            int nx = ra.getWidth(), ny = ra.getHeight();
            float[] v = new float[nx * ny];
            for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) v[i + nx * j] = ra.getSampleFloat(i, j, 0);
            return new Kennwert(v, nx, ny, t[3] - 0.5 * t[0], t[7] - 0.5 * t[5], t[0]);
        } finally {
            r.dispose();
        }
    }

    /**
     * Prüfstein 7 von P8c (F75 A): die größte Hs des Sturms je Zelle von G3w (nass, mit Wert in EasyGSH) zwischen dem
     * 95-%-Wert und dem Maximum von 2015; G2w zur Auskunft; Status-Bild {@code P8c_Feld.png}.
     */
    static void feldvergleich(File gb, Gitter g2w, float[] max2, Gitter g3w, float[] max3) throws Exception {
        File d = easygsh != null ? easygsh : new File(gb.getAbsoluteFile().getParentFile(), "gezeiten" + File.separator + "easygsh_kennwerte" + File.separator + "2015");
        File f95 = new File(d, "2015_100m_significant_wave_height_95q.tif"), fmx = new File(d, "2015_100m_max_significant_wave_height.tif");
        if (!f95.isFile() || !fmx.isFile()) {
            A.pruefe(false, "Prüfstein 7 (P8c): Feldvergleich gegen EasyGSH 2015", "Kennwerte fehlen in " + d, "95-%-Wert und Maximum der Wellenhöhe");
            return;
        }
        Kennwert q95 = kennwert(f95), qmx = kennwert(fmx);
        A.info("EasyGSH 2015", f("%s: %d × %d Pixel zu %.0f m ab O %.0f, N %.0f (Ecke Nordwest)", d, q95.nx, q95.ny, q95.weite, q95.west, q95.nord));
        String[] namen = {"G2w", "G3w"};
        Gitter[] gs = {g2w, g3w};
        float[][] mx = {max2, max3};
        for (int k = 0; k < 2; k++) {
            Gitter g = gs[k];
            if (g == null) continue;
            int unter = 0, drin = 0, ueber = 0, ohne = 0;
            byte[] klasse = new byte[g.zellen()];
            for (int c = 0; c < g.zellen(); c++) {
                double hs = mx[k][c];
                if (!(hs > 0.05)) continue;
                double x = g.x(c % g.nx), y = g.y(c / g.nx), a = q95.bei(x, y), b = qmx.bei(x, y);
                if (!Double.isFinite(a) || !Double.isFinite(b)) { ohne++; klasse[c] = 4; continue; }
                if (hs < a) { unter++; klasse[c] = 1; }
                else if (hs > b) { ueber++; klasse[c] = 3; }
                else { drin++; klasse[c] = 2; }
            }
            int n = unter + drin + ueber;
            String text = f("%.1f %% von %d Zellen (darunter %.1f %%, darüber %.1f %%; %d nasse Zellen ohne Wert in EasyGSH)", n == 0 ? 0 : 100.0 * drin / n, n,
                    n == 0 ? 0 : 100.0 * unter / n, n == 0 ? 0 : 100.0 * ueber / n, ohne);
            if (k == 1) A.pruefe(n > 0 && drin >= 0.9 * n, "Prüfstein 7 (P8c): größte Hs des Sturms in G3w zwischen 95-%-Wert und Maximum von EasyGSH 2015", text, "≥ 90 % [G]");
            else A.info("Feldvergleich G2w (Auskunft): größte Hs des Sturms zwischen 95-%-Wert und Maximum 2015", text);
            if (k == 1) feldbild(g, klasse, mx[k]);
        }
    }

    /** Status-Bild des Feldvergleichs: je Zelle von G3w unter (blau), zwischen (grün), über (rot) dem Rahmen von 2015; grau ohne Wert. */
    static void feldbild(Gitter g, byte[] klasse, float[] hs) throws Exception {
        int sk = 6, w = g.nx * sk, h = g.ny * sk;
        BufferedImage im = new BufferedImage(w, h + 24, BufferedImage.TYPE_INT_RGB);
        Color[] farbe = {new Color(30, 30, 30), new Color(60, 110, 220), new Color(60, 170, 80), new Color(210, 60, 50), new Color(150, 150, 150)};
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                int rgb = farbe[klasse[i + g.nx * j]].getRGB();
                for (int v = 0; v < sk; v++) for (int u = 0; u < sk; u++) im.setRGB(i * sk + u, (g.ny - 1 - j) * sk + v, rgb);
            }
        Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g2.drawString("Größte Hs im Sturm 25.10. auf G3w gegen EasyGSH 2015: blau unter 95 %, grün zwischen 95 % und Maximum, rot darüber, grau ohne Wert", 6, h + 16);
        g2.dispose();
        File f = Path.of("docs", "status", "P8c_Feld.png").toFile();
        f.getParentFile().mkdirs();
        ImageIO.write(im, "png", f);
        A.info("Status-Bild Feldvergleich", f.toString());
    }

    static int index(List<Gitter> l, String name) {
        for (int k = 0; k < l.size(); k++) if (l.get(k).name.equals(name)) return k;
        return -1;
    }

    static float[][] neu5(Gitter g) { return new float[5][g.zellen()]; }

    static long nanZahl(float[][] f) {
        long n = 0;
        for (float[] x : f) for (float v : x) if (!Float.isFinite(v)) n++;
        return n;
    }

    /** Bilinear zwischen den Zellmitten (am Rand der Randwert); NaN außerhalb des Gitters. */
    static double wert(Gitter g, float[] f, double x, double y) {
        if (!g.enthaelt(x, y)) return Double.NaN;
        double u = Math.max(0, Math.min(g.nx - 1, (x - g.x0) / g.dx - 0.5)), v = Math.max(0, Math.min(g.ny - 1, (y - g.y0) / g.dy - 0.5));
        int i = Math.max(0, Math.min(g.nx - 2, (int) u)), j = Math.max(0, Math.min(g.ny - 2, (int) v));
        int i1 = Math.min(i + 1, g.nx - 1), j1 = Math.min(j + 1, g.ny - 1);
        double a = u - i, b = v - j;
        return (f[g.index(i, j)] * (1 - a) + f[g.index(i1, j)] * a) * (1 - b) + (f[g.index(i, j1)] * (1 - a) + f[g.index(i1, j1)] * a) * b;
    }

    /** Hs auf G2w und G3w zur Zeit ts als Karten nebeneinander, mit Skala. */
    static void statusbild(Archivquelle q, Gitter g2w, Gitter g3w, double ts, Instant t0) throws Exception {
        float[][] a = neu5(g2w);
        q.seegang(ts, 0, a);
        float[][] b = g3w == null ? null : neu5(g3w);
        if (b != null) q.seegang(ts, 1, b);
        int sk = 4, w1 = g2w.nx * sk, hh = g2w.ny * sk, sk3 = g3w == null ? 0 : Math.max(1, hh / g3w.ny), w3 = g3w == null ? 0 : g3w.nx * sk3;
        double hmax = 0;
        for (float v : a[0]) hmax = Math.max(hmax, v);
        BufferedImage im = new BufferedImage(w1 + w3 + 90, hh + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        malen(im, g2w, a[0], 0, sk, hmax);
        if (b != null) malen(im, g3w, b[0], w1 + 10, sk3, hmax);
        for (int y = 0; y < hh; y++) {
            int rgb = farbe(hmax * (hh - 1 - y) / (hh - 1), hmax);
            for (int x = 0; x < 16; x++) im.setRGB(w1 + w3 + 30 + x, y, rgb);
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(f("%.1f m", hmax), w1 + w3 + 50, 12);
        g.drawString("0 m", w1 + w3 + 50, hh);
        g.drawString("Hs " + BildCheck.zeit(t0, ts) + " – links G2w (500 m), rechts G3w (100 m); grau trocken", 6, hh + 25);
        g.dispose();
        File png = new File("docs" + File.separator + "status" + File.separator + phase + "_Seegang.png");
        png.getParentFile().mkdirs();
        ImageIO.write(im, "png", png);
        A.info("Status-Bild", png.getPath());
    }

    static void malen(BufferedImage im, Gitter gi, float[] hs, int x0, int sk, double hmax) {
        for (int j = 0; j < gi.ny; j++)
            for (int i = 0; i < gi.nx; i++) {
                float v = hs[gi.index(i, j)];
                int rgb = v > 0 ? farbe(v, hmax) : 0xB0B0B0;
                for (int y = 0; y < sk; y++) for (int x = 0; x < sk; x++) im.setRGB(x0 + i * sk + x, (gi.ny - 1 - j) * sk + y, rgb);
            }
    }

    /** Blau über Grün und Gelb nach Rot. */
    static int farbe(double v, double max) {
        double u = Math.max(0, Math.min(1, v / Math.max(1e-6, max)));
        float hue = (float) (0.66 * (1 - u));
        return Color.HSBtoRGB(hue, 0.85f, 0.95f) & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ Teil 3

    static void teil3(Geraet g, Konto konto, File gb, Gebiet ge, File ar) throws Exception {
        A.teil("3 Bild");
        try (Archivquelle q = new Archivquelle(ar.toPath()); BildCheck.Szene sz = new BildCheck.Szene(g, konto, gb, ge, q, breite, hoehe)) {
            Instant t0 = q.start;
            double[] zs = q.seegangZeiten();
            // die Zeit mit dem höchsten Seegang auf dem feinsten Netz, bei Tag (09–15 UTC), damit die Sichtprüfung etwas sieht
            float[][] f = neu5(q.seegangGitter().get(q.seegangGitter().size() - 1));
            int fein = q.seegangGitter().size() - 1;
            double tsp = Double.NaN, beste = -1;
            for (double ts : zs) {
                int std = t0.plusMillis(Math.round(ts * 1000)).atZone(java.time.ZoneOffset.UTC).getHour();
                if (std < 9 || std >= 15) continue;
                q.seegang(ts, fein, f);
                double s = 0;
                for (float v : f[0]) s += v;
                if (s > beste) { beste = s; tsp = ts; }
            }
            if (Double.isNaN(tsp)) tsp = zs[zs.length / 2];
            double t = bildZeit != null ? (bildZeit.toEpochMilli() - t0.toEpochMilli()) / 1000.0 : tsp + 600;      // zwischen zwei Ständen
            var gel = sz.gelaende.gelaende;
            double westH = gel.hoehe(BildCheck.WEST_OST, BildCheck.WEST_NORD, 1) + 1.7;
            Blick west = new Blick(BildCheck.WEST_OST, BildCheck.WEST_NORD, westH, BildCheck.WEST_RICHTUNG, -2, 55).groesse(breite, hoehe);
            Blick japs = new Blick(466_800, 6_047_200, 4, 90, -2, 55).groesse(breite, hoehe);
            Blick vogel = new Blick(470_400, 6_044_300, 300, 20, -30, 60).groesse(breite, hoehe);
            A.info("Zeit der Ansichten", BildCheck.zeit(t0, t) + (bildZeit != null ? " (--bildzeit)" : " (höchster Seegang auf " + q.seegangGitter().get(fein).name + " bei Tag, 09–15 UTC, und 10 min danach)"));
            // Prüfstein 8: Stufe 2 mit Hs = 0 gegen Stufe 1
            float[] stufe1 = bild(sz, west, t0, t, false, false);
            float[] null2 = bild(sz, west, t0, t, true, true);
            int max = 0;
            for (int i = 0; i < stufe1.length; i++) if (i % 4 != 3) max = Math.max(max, Math.abs(Math.round(stufe1[i] * 255) - Math.round(null2[i] * 255)));
            A.pruefe(max <= 1, "Prüfstein 8: Stufe 2 ohne Seegang gegen Stufe 1 (Westspitze)", max + " von 255", "≤ 1");
            // Prüfstein 9: lange Wellen im Shader gegen den Prozessor
            float[] mit = bild(sz, west, t0, t, true, false);
            java.util.Random r = new java.util.Random(7);
            int n = 1000;
            float[] orte = new float[2 * n];
            double az = Math.toRadians(BildCheck.WEST_RICHTUNG);
            for (int i = 0; i < n; i++) {
                double d = 50 + 3000 * r.nextDouble(), a = az + Math.toRadians(40 * (r.nextDouble() - 0.5));
                orte[2 * i] = (float) (d * Math.sin(a));
                orte[2 * i + 1] = (float) (-d * Math.cos(a));
            }
            Seegangsprobe.Ergebnis e = Seegangsprobe.rechne(g, konto, sz.wasser, west.ost, west.nord, orte, 0.5f);
            double maxRel = 0, maxEig = 0, maxW = 0;
            int gezaehlt = 0;
            for (int i = 0; i < n; i++) {
                if (e.prozessor()[9 * i + 8] < 0.5 || e.karte()[9 * i + 8] < 0.5) continue;
                double p = e.prozessor()[9 * i + 6], k = e.karte()[9 * i + 6], mss = e.prozessor()[9 * i + 4];
                for (int fz = 0; fz < 6; fz++) maxW = Math.max(maxW, Math.abs(e.karte()[9 * i + fz] - e.prozessor()[9 * i + fz]) / Math.max(1e-3, Math.abs(e.prozessor()[9 * i + fz])));
                if (Math.max(p, mss) < 1e-9) continue;
                maxRel = Math.max(maxRel, Math.abs(k - p) / Math.max(p, mss));
                if (p > 1e-9) maxEig = Math.max(maxEig, Math.abs(k / p - 1));
                gezaehlt++;
            }
            // Maß: die Abweichung relativ zur Neigungsvarianz der langen Wellen am Ort, max(aufgelöst, mss). Relativ zum
            // aufgelösten Teil allein wird sie dort groß, wo er winzig ist: Liegt die Spitze über 0,6 Hz, sehen die Bänder nur
            // den steilen unteren Rand des JONSWAP, und die Rundung von Tp in float (10⁻⁵) wächst dort um das Hundertfache.
            A.hoechstens("Prüfstein 9: Neigungsvarianz der langen Wellen, Karte gegen Prozessor, relativ zu max(aufgelöst, mss) (" + gezaehlt + " Orte)", maxRel, 1e-4, "");
            A.info("Prüfstein 9: dieselbe relativ zum aufgelösten Teil allein; Werte am Ort (Hs, Tp, Richtung, mss, diss)", f("%.1e; größte relative Abweichung %.1e", maxEig, maxW));
            float[] japsBild = bild(sz, japs, t0, t, true, false), vogelBild = bild(sz, vogel, t0, t, true, false);
            // Prüfstein 10: Bilder/s aus dem Archiv mit Seegang
            sz.wasser.seegang = true;
            for (int k = 0; k < 5; k++) sz.zeichne(west, t0, t + k, null);
            long n0 = System.nanoTime();
            for (int k = 0; k < bilder; k++) sz.zeichne(west, t0, t + 5 + k * 2.0, null);
            double fps = bilder / ((System.nanoTime() - n0) * 1e-9);
            A.pruefe(fps >= 30, f("Prüfstein 10: Bilder/s aus dem Archiv mit Seegang, %d × %d (je Bild gewartet)", breite, hoehe), f("%.1f", fps), "≥ 30");
            statusbildAnsichten(new float[][] {mit, stufe1, japsBild, vogelBild},
                    new String[] {"Westspitze, Stufe 2 (Seegang)", "Westspitze, Stufe 1 (nur Wind)", "Japsand nach Osten, Stufe 2", "Vogelblick über Hooge, Stufe 2"}, t0, t);
        }
    }

    /** Ein Bild (8 Bit, RGBA als float 0 … 1); {@code stufe2}: Seegang an; {@code ohneSeegang}: die Netze mit Hs, mss, diss = 0. */
    static float[] bild(BildCheck.Szene sz, Blick b, Instant t0, double t, boolean stufe2, boolean ohneSeegang) {
        sz.wasser.seegang = stufe2;
        List<float[][]> alt = new ArrayList<>();
        sz.bild.zu(t, b.ost, b.nord);
        if (ohneSeegang)
            for (Seegangsnetz s : sz.bild.seegang())
                synchronized (s) {
                    alt.add(new float[][] {s.werte[0].clone(), s.werte[1].clone()});
                    Arrays.fill(s.werte[0], 0f);
                    Arrays.fill(s.werte[1], 0f);
                    s.neu = 3;
                }
        for (int k = 0; k < 12; k++) {
            sz.rahmen.belichtung.sofort();
            sz.zeichne(b, t0, t, null);
        }
        float[] p = sz.aus.lies();
        if (ohneSeegang) {
            List<Seegangsnetz> l = sz.bild.seegang();
            for (int k = 0; k < l.size(); k++)
                synchronized (l.get(k)) {
                    System.arraycopy(alt.get(k)[0], 0, l.get(k).werte[0], 0, alt.get(k)[0].length);
                    System.arraycopy(alt.get(k)[1], 0, l.get(k).werte[1], 0, alt.get(k)[1].length);
                    l.get(k).neu = 3;
                }
        }
        sz.wasser.seegang = true;
        return p;
    }

    static void statusbildAnsichten(float[][] bilder, String[] namen, Instant t0, double t) throws Exception {
        int w = breite / 2, h = hoehe / 2;
        BufferedImage alle = new BufferedImage(2 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        for (int n = 0; n < bilder.length; n++) {
            float[] p = bilder[n];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int i = 4 * ((2 * y) * breite + 2 * x);
                    int rr = Math.round(p[i] * 255), gg = Math.round(p[i + 1] * 255), bb = Math.round(p[i + 2] * 255);
                    alle.setRGB(x + (n % 2) * w, y + (n / 2) * h, (rr << 16) | (gg << 8) | bb);
                }
            Graphics2D g2 = alle.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, h / 36)));
            String txt = namen[n] + " · " + BildCheck.zeit(t0, t);
            int x0 = (n % 2) * w + 8, y0 = (n / 2) * h + h - 10;
            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(x0 - 4, y0 - g2.getFontMetrics().getAscent() - 2, g2.getFontMetrics().stringWidth(txt) + 8, g2.getFontMetrics().getHeight() + 2);
            g2.setColor(Color.WHITE);
            g2.drawString(txt, x0, y0);
            g2.dispose();
        }
        File png = new File("docs" + File.separator + "status" + File.separator + phase + "_Bild.png");
        png.getParentFile().mkdirs();
        ImageIO.write(alle, "png", png);
        A.info("Prüfstein 12: Status-Bild (Sichtprüfung)", png.getPath());
    }
}
