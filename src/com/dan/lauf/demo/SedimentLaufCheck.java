package com.dan.lauf.demo;

import com.dan.core.bus.Anmeldung;
import com.dan.core.bus.Bus;
import com.dan.core.bus.Modul;
import com.dan.core.feld.Feld;
import com.dan.core.feld.Katalog;
import com.dan.core.feld.Ort;
import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Kornfeld;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Attest;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Lauf;
import com.dan.physics.sediment.Sediment;
import com.dan.physics.sediment.SedimentModul;
import com.dan.physics.sediment.gpu.SedimentRechner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.dan.core.pruef.Attest.f;

/**
 * Die Prüfsteine von P9b, Teil Lauf (Werkbuch HalligHooge Teil A, 12.10, Feinplan P9b): das Sediment am Bus.
 * <ul>
 * <li><b>Teil 1, Läufe:</b> Sturm (23.10. bis 26.10., abgelegt ab 25.10., 06:00 UTC) und ruhiger Tag (28.10. bis 31.10.,
 * abgelegt der 30.10.) wie {@link SeegangCheck}, dazu das Sediment auf der Karte ab dem Ende des Anlaufs der Tide und im
 * Archiv. Prüfsteine 1 (Verdrahtung, außerhalb der Stände, NaN), 2 (Tiefe aus der Kontinuität gegen V(η)/Fläche),
 * 3 (Massenbilanz), 9 (Rechenzeit gegen hydro). Ein Lesemodul {@code pruefung} nimmt dabei auf, was die Feldvergleiche
 * brauchen ({@code <archiv>/pruef_sediment/}): je Tide und Zelle den größten Flut- und Ebbstrom und die größte
 * Kornschubspannung des Stroms, und für ein Fenster von 150 × 120 Zellen den Eingang des Sediments über die letzten
 * drei Stunden.</li>
 * <li><b>Teil 2, aus den Archiven:</b> Prüfstein 4 (Karte gegen Prozessor mit dem aufgenommenen Eingang), 5 und 6
 * (Strömung und Kornschubspannung gegen EasyGSH 2015), 7 (Trübung gegen Sentinel-2), 8 (Rippel)
 * ({@link SedimentFeldCheck}).</li>
 * <li><b>Teil 3, Bild</b> ({@link SedimentBildCheck}): Prüfsteine 10, 11, 12 und das Status-Bild für die Sichtprüfung.</li>
 * </ul>
 * Attest {@code docs/status/P9b_Sediment.md}. {@code --lauf sturm|ruhig|beide} wählt die Läufe von Teil 1.
 * <p>
 * <b>Seit P9c</b> rechnet Teil 1 auch die Läufe der Morphologie (Feinplan P9c, Prüfsteine 10–12, 17): Rückwirkung des Bodens
 * auf hydro, {@code --faktor}, {@code --boden-aus <Archiv>} (Boden eines früheren Laufs), {@code --sediment-ab <Zeit>},
 * {@code --seegang-gitter <G2w>,<G3w>} (m), {@code --rauheit}, {@code --attest <Name>} (dann zählen die Prüfsteine nach
 * P9c); {@code --p9b} rechnet wie in P9b (ohne Rückwirkung, Auflage, Spirale und Deckel).
 */
public final class SedimentLaufCheck {
    private static final Attest A = new Attest("com.dan.lauf", Lauf.VERSION);
    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", archiv, archivRuhig, nur = "1,2,3", lauf = "beide", seegang = "karte", sediment = "karte";
    static boolean neu;
    static int[] stuetzen = {28, 36};
    static double vorlauf = 12;
    // P9c: Morphologie
    static double faktor = 1, deckel = 0.05;
    static boolean rueck = true, rauheit, spirale = true, auflage = true;
    static String bodenAus, attest, sedimentAb;
    static double[] seegangGitter;
    /** Fenster der Aufnahme für Prüfstein 4 in Zellen des Gitters des Sediments {i0, j0, ni, nj}; null: 150 × 120 in der Mitte. */
    static int[] probe;
    static double probeStunden = 3;
    /** Länge einer Tide (s): M2. */
    static final double TIDE = 44714;
    static final String ORDNER = "pruef_sediment";
    static File easygsh, sentinel;

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            String a = args[i], v = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--gebiet" -> gebiet = v;
                case "--archiv" -> archiv = v;
                case "--archiv-ruhig" -> archivRuhig = v;
                case "--nur" -> nur = v;
                case "--lauf" -> lauf = v;
                case "--neu" -> neu = true;
                case "--seegang" -> seegang = v;
                case "--sediment" -> sediment = v;
                case "--prozessor" -> BildCheck.prozessor = true;
                case "--stuetzen" -> stuetzen = new int[] {Integer.parseInt(v.split(",")[0].trim()), Integer.parseInt(v.split(",")[1].trim())};
                case "--vorlauf" -> vorlauf = Double.parseDouble(v);
                case "--faktor" -> faktor = Double.parseDouble(v);
                case "--deckel" -> deckel = Double.parseDouble(v);
                case "--ohne-rueck" -> rueck = false;
                case "--rauheit" -> rauheit = true;
                case "--ohne-spirale" -> spirale = false;
                case "--ohne-auflage" -> auflage = false;
                case "--p9b" -> { rueck = false; spirale = false; auflage = false; deckel = 0; }
                case "--boden-aus" -> bodenAus = v;
                case "--attest" -> attest = v;
                case "--sediment-ab" -> sedimentAb = v;
                case "--seegang-gitter" -> seegangGitter = new double[] {Double.parseDouble(v.split(",")[0].trim()), Double.parseDouble(v.split(",")[1].trim())};
                case "--start" -> BildCheck.start = Instant.parse(v);
                case "--ab" -> BildCheck.ab = Instant.parse(v);
                case "--ende" -> BildCheck.ende = Instant.parse(v);
                case "--anlauf" -> BildCheck.anlauf = Double.parseDouble(v);
                case "--fenster3" -> BildCheck.fenster = v;
                case "--probe" -> { String[] t = v.split(","); probe = new int[] {Integer.parseInt(t[0].trim()), Integer.parseInt(t[1].trim()), Integer.parseInt(t[2].trim()), Integer.parseInt(t[3].trim())}; }
                case "--probe-stunden" -> probeStunden = Double.parseDouble(v);
                case "--easygsh" -> easygsh = new File(v);
                case "--sentinel" -> sentinel = new File(v);
                case "--breite" -> SedimentBildCheck.breite = Integer.parseInt(v);
                case "--hoehe" -> SedimentBildCheck.hoehe = Integer.parseInt(v);
                case "--bilder" -> SedimentBildCheck.bilder = Integer.parseInt(v);
                case "--fahne" -> SedimentBildCheck.fahneBlick = java.util.Arrays.stream(v.split(",")).mapToDouble(x -> Double.parseDouble(x.trim())).toArray();
                case "--rippel" -> SedimentBildCheck.rippelBlick = java.util.Arrays.stream(v.split(",")).mapToDouble(x -> Double.parseDouble(x.trim())).toArray();
                case "--rippelzeit" -> SedimentBildCheck.rippelZeit = Instant.parse(v);
                default -> { }
            }
        }
        File gb = new File(gebiet);
        File ar = archiv != null ? new File(archiv) : new File(gb.getAbsoluteFile().getParentFile(), "_p9b" + File.separator + "archiv");
        File arR = archivRuhig != null ? (archivRuhig.equals("-") ? null : new File(archivRuhig)) : new File(ar.getAbsoluteFile().getParentFile(), "archiv_ruhig");
        System.out.println("SedimentLaufCheck – P9b (Werkbuch HalligHooge Teil A, 12.10)");
        A.info("Gebiet, Archive", gb + "; " + ar + (arR != null ? "; " + arR : ""));
        long n0 = System.nanoTime();
        try {
            int[] f3 = BildCheck.fenster3(gb);
            if (nur.contains("1")) {
                if (!lauf.equals("ruhig")) teil1(gb, ar, f3, "Sturm", BildCheck.start, BildCheck.ab, BildCheck.ende);
                if (arR != null && !lauf.equals("sturm")) teil1(gb, arR, f3, "ruhiger Tag", BildCheck.startRuhig, BildCheck.abRuhig, BildCheck.endeRuhig);
            }
            if (nur.contains("2")) SedimentFeldCheck.teil2(A, gb, ar, arR, f3);
            if (nur.contains("3"))
                try (com.dan.core.gpu.Geraet g = new com.dan.core.gpu.Geraet()) {
                    A.info("Gerät", g.name + " (" + g.treiber + ")");
                    SedimentBildCheck.teil3(A, g, g.konto("SedimentBildCheck", 3L << 30), gb, arR != null ? arR : ar);
                }
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        String name = attest != null ? attest + ".md" : "P9b_Sediment" + (nur.equals("1") ? "_Lauf" + (lauf.equals("beide") ? "" : "_" + lauf) : "") + ".md";
        Path p = A.schreibe(Path.of("docs", "status", name), attest != null ? "P9c – SedimentLaufCheck (Morphologie: " + attest + ")" : "P9b – SedimentLaufCheck (Sediment im Lauf)");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Das Gitter des Sediments: G3 des Gebiets oder sein Fenster. */
    static Gitter gitter(File gb, int[] f3) throws IOException {
        Gitter g3 = Gebiet.lies(gb).gitter("G3");
        return f3 == null ? g3 : Gitter.utm("G3", g3.zone, g3.x0 + f3[0] * g3.dx, g3.y0 + f3[1] * g3.dy, g3.dx, f3[2], f3[3]);
    }

    static int[] probe(Gitter g) {
        if (probe != null) return probe;
        int ni = Math.min(150, g.nx), nj = Math.min(120, g.ny);
        return new int[] {(g.nx - ni) / 2, (g.ny - nj) / 2, ni, nj};
    }

    // ------------------------------------------------------------------ Teil 1

    static void teil1(File gb, File ar, int[] f3, String wer, Instant start, Instant ab, Instant ende) throws Exception {
        A.teil("1 Lauf mit Sediment und Archiv, " + wer);
        double dauer = (ende.toEpochMilli() - start.toEpochMilli()) / 1000.0;
        if (new File(ar, "sediment_G3" + File.separator + "lauf.txt").isFile() && !neu) {
            try (Archivquelle q = new Archivquelle(ar.toPath())) {
                A.info("Archiv liegt schon (--neu rechnet neu)", f("%s: %d Stände des Wassers, %d des Schwebstoffs, %d des Bodens; %s", ar, q.staende(), q.sedimentZeiten().length, q.bodenZeiten().length, q.kopf.antrieb));
            }
            return;
        }
        if (new File(ar, "G3" + File.separator + "lauf.txt").isFile()) throw new IllegalStateException(ar + ": dort liegt ein Archiv; bitte erst wegräumen");
        Lauf.Einstellung e = BildCheck.einstellung(gb, f3, start);
        e.archiv = ar;
        e.archivAb = Math.max(0, (ab.toEpochMilli() - start.toEpochMilli()) / 1000.0);
        e.name = "P9b SedimentLaufCheck " + start;
        e.seegang = seegang.equals("aus") ? Lauf.Einstellung.Seegang.AUS : seegang.equals("prozessor") ? Lauf.Einstellung.Seegang.PROZESSOR : Lauf.Einstellung.Seegang.KARTE;
        e.seegangStuetzen = stuetzen;
        e.seegangAb = Math.floor(Math.max(0, e.archivAb - vorlauf * 3600) / e.seegangKopplung) * e.seegangKopplung;
        e.sediment = sediment.equals("prozessor") ? Lauf.Einstellung.Sediment.PROZESSOR : Lauf.Einstellung.Sediment.KARTE;
        e.sedimentAb = sedimentAb != null ? Math.max(0, (Instant.parse(sedimentAb).toEpochMilli() - start.toEpochMilli()) / 1000.0) : e.anlauf;
        e.bodenRueck = rueck;
        e.rauheitSediment = rauheit;
        e.spirale = spirale;
        e.auflage = auflage;
        e.morphFaktor = faktor;
        e.bodenDeckel = deckel;
        if (bodenAus != null) e.bodenAnfang = new File(bodenAus);
        if (seegangGitter != null) { e.seegangG2 = seegangGitter[0]; e.seegangG3 = seegangGitter[1]; }
        if (attest != null) e.name = attest + " " + start;
        Gitter g = gitter(gb, f3);
        Aufnahme auf = new Aufnahme(g, e.seegang != Lauf.Einstellung.Seegang.AUS, e.kopplung, e.archivAb, dauer, probe(g), dauer - probeStunden * 3600);
        e.weitere = new ArrayList<>(List.of(auf));
        long n0 = System.nanoTime();
        try (Lauf l = Lauf.bauen(e)) {
            SedimentModul m = l.sediment;
            Sediment s = m.sediment;
            auf.sediment = s;
            A.info("Lauf", l + f("; Aufbau %.1f s", l.aufbau));
            A.info("Sediment", m + f("; rechnet ab %s; Boden %s; Schlick im Wasser am Anfang und am Rand %.0f g/m³", l.zeitpunkt(e.sedimentAb),
                    Kornfeld.vorhanden(gb) ? "aus dem Kornfeld des Gebiets" : "überall Sand 0,2 mm (kein Kornfeld im Gebiet)", 1000 * m.hintergrund));
            List<String> eigen = new ArrayList<>();
            for (String h : l.hinweise) if (!h.startsWith(auf.name() + " ")) eigen.add(h);
            A.pruefe(l.module().contains(m) && eigen.isEmpty(), "Prüfstein 1: sediment am Bus, Hinweise beim Verdrahten (" + wer + ")", eigen.isEmpty() ? "keine" : String.join("; ", eigen), "keine");
            long n1 = System.nanoTime();
            double hy0 = Double.NaN, sd0 = 0, wv0 = 0, al0 = 0;
            long nAb = n1;
            double csMax = 0, cmMax = 0;
            for (double t = Math.min(3600, dauer); ; t = Math.min(t + 3600, dauer)) {
                if (Double.isNaN(hy0) && t > e.sedimentAb) {
                    l.bis(e.sedimentAb);
                    hy0 = l.takt.rechenzeit("hydro");
                    sd0 = l.takt.rechenzeit("sediment");
                    wv0 = l.waves != null ? l.takt.rechenzeit("waves") : 0;          // seit P13d (A33): alle im selben Fenster
                    al0 = l.takt.rechenzeitAlle();
                    nAb = System.nanoTime();
                }
                l.bis(t);
                double cs = 0, cm = 0;
                for (int c = 0; c < s.nc; c++) { if (s.cs[c] > cs) cs = s.cs[c]; if (s.cm[c] > cm) cm = s.cm[c]; }
                csMax = Math.max(csMax, cs);
                cmMax = Math.max(cmMax, cm);
                double dzJetzt = 0;
                for (int c = 0; c < s.nc; c++) dzJetzt = Math.max(dzJetzt, Math.abs(s.dzS[c] + s.dzM[c]));
                String dtText = "";
                if (l.rechnung instanceof com.dan.physics.hydro.gpu.KarteRechnung kr && kr.kette(l.hydro.stufen.size() - 1) != null) {
                    double[] de = kr.kette(l.hydro.stufen.size() - 1).dtEbenen();
                    if (de != null) dtText = "; Schritt der Kette " + java.util.Arrays.stream(de).mapToObj(x -> String.format(Locale.ROOT, "%.2f", x)).collect(java.util.stream.Collectors.joining(" | ")) + " s";
                }
                System.out.printf(Locale.ROOT, "  %s: %.0f s gerechnet; Sand bis %.3f, Schlick bis %.3f kg/m³; %.0f Teilschritte je Intervall; |Δz| bis %.3f m, am Deckel %.0f%s; Speicher %.0f MB%n", l.zeitpunkt(l.zeit()),
                        (System.nanoTime() - n1) / 1e9, cs, cm, m.teilschritte / (double) Math.max(1, m.intervalle), dzJetzt, s.wirklich()[3], dtText,
                        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1e6);
                if (t >= dauer) break;
            }
            double sek = (System.nanoTime() - n1) / 1e9;
            A.pruefe(l.bus.ausserhalb() == 0, "Prüfstein 1: außerhalb der Stände gelesen (" + wer + ")", String.valueOf(l.bus.ausserhalb()), "0");
            A.pruefe(m.schlecht == 0, "Prüfstein 1: NaN oder negative Konzentration (" + wer + ")", m.schlecht + " Zellen über " + m.intervalle + " Intervalle", "0");
            A.pruefe(m.tiefeMittel() <= 1e-3 && m.tiefeAnteilUeber() <= 0.01, "Prüfstein 2: Tiefe aus der Kontinuität (water.q) gegen V(η)/Fläche am Ende der Intervalle (" + wer + ")",
                    f("im Mittel %.2e m; %.4f %% der Zellen über %.0f mm; größte %.3f m (%d Zellen über %d Intervalle)", m.tiefeMittel(), 100 * m.tiefeAnteilUeber(), 1000 * m.tiefeGrenze, m.tiefeGroesste,
                            m.tiefeZellen, m.intervalle), "im Mittel ≤ 1 mm, 99 % ≤ 5 mm [G]");
            double[] b = m.bilanz(), ms = s.masse();
            A.pruefe(b[0] <= 1e-3 && b[1] <= 1e-3, "Prüfstein 3: Massenbilanz über den Lauf, relativ zum Umsatz (" + wer + ")",
                    f("Sand %.1e (Umsatz %.3g t), Schlick %.1e (Umsatz %.3g t)", b[0], m.umsatz[0] / 1e3, b[1], m.umsatz[1] / 1e3), "≤ 1 · 10⁻³ [G]");
            A.info("Bestand am Ende (" + wer + ")", f("Sand im Wasser %.3g t, Boden %+.3g t, über den Rand hinaus %+.3g t (Schwebstoff) und %+.3g t (Geschiebe); Schlick im Wasser %.3g t, im Boden %.3g t (Anfang %.3g t), "
                            + "über den Rand hinaus %+.3g t; Abgleich der Tiefe: Sand %+.3g t, Schlick %+.3g t", ms[0] / 1e3, ms[1] / 1e3, s.aus[0] / 1e3, s.aus[2] / 1e3, ms[2] / 1e3, ms[3] / 1e3, m.anfang[3] / 1e3,
                    s.aus[1] / 1e3, m.abgleich[0] / 1e3, m.abgleich[1] / 1e3));
            double dzMax = 0, dzSum = 0;
            for (int c = 0; c < s.nc; c++) { double d = Math.abs(s.dzS[c] + s.dzM[c]); dzSum += d; if (d > dzMax) dzMax = d; }
            A.info("Größen (" + wer + ")", f("Sand im Wasser bis %.3f kg/m³, Schlick bis %.3f kg/m³; Bodenänderung im Mittel %.2e m, größte %.3f m; %.1f Teilschritte je Intervall", csMax, cmMax, dzSum / s.nc, dzMax,
                    m.teilschritte / (double) Math.max(1, m.intervalle)));
            // P9c: Rückwirkung, Faktor, Deckel, Auflage
            double[] wk = s.wirklich();
            double[] betrag = new double[s.nc];
            int ueber = 0;
            double uMittel = 0, auflageMax = 0;
            int nassZ = 0;
            double[] strom = new double[s.nc], welle = new double[s.nc];
            double ubMittel = 0;
            for (int c = 0; c < s.nc; c++) {
                betrag[c] = Math.abs(s.dzS[c] + s.dzM[c]);
                if (betrag[c] > 1) ueber++;
                auflageMax = Math.max(auflageMax, s.mAuflage[c]);
                if (s.h[c] >= s.hMin && s.ug != null) { strom[nassZ] = Math.hypot(s.ug[c], s.vg[c]); uMittel += strom[nassZ]; welle[nassZ] = s.ubot[c]; ubMittel += s.ubot[c]; nassZ++; }
            }
            java.util.Arrays.sort(betrag);
            java.util.Arrays.sort(strom, 0, nassZ);
            java.util.Arrays.sort(welle, 0, nassZ);
            double bil = 0;
            StringBuilder bt = new StringBuilder();
            for (int st = 0; st < l.hydro.stufen.size(); st++) { bil = Math.max(bil, l.bilanz(st)); bt.append(st > 0 ? ", " : "").append(f("%.1e", l.bilanz(st))); }
            double anteilDeckel = wk[3] / Math.max(1.0, (double) m.teilschritte * s.nc);
            if (attest != null) {
                A.pruefe(bil < 1e-5, "P9c Prüfstein 10: Wasserbilanz von hydro je Stufe mit bewegtem Boden (" + wer + ")", bt.toString(), "< 1 · 10⁻⁵");
                A.pruefe(anteilDeckel <= 1e-3 && dzMax <= 3, "P9c Prüfstein 12: Zellen am Deckel (Anteil der Teilschritte); größte Bodenänderung (" + wer + ")",
                        f("%.3e (%.0f Teilschritte von Zellen); %.3f m", anteilDeckel, wk[3], dzMax), "≤ 0,1 % [G]; ≤ 3 m [G]");
            }
            A.info("Boden (" + wer + ")", f("Faktor %.0f, Rückwirkung %s (hydro hat %d Zellstände neu gesetzt), Deckel %.2f; |Δz|: Median %.2e m, 90 %% %.3f m, 99 %% %.3f m, größte %.3f m, %d Zellen über 1 m; "
                            + "wirklich aufgenommen: Sand %+.3g t, Schlick der Mischschicht %+.3g t; lockere Auflage %.3g t (bis %.3f kg/m²)", s.faktor, e.bodenRueck ? "an" : "aus", l.hydro.bodenZellen, s.deckel,
                    betrag[s.nc / 2], betrag[(int) (0.9 * s.nc)], betrag[(int) (0.99 * s.nc)], dzMax, ueber, wk[0] / 1e3, wk[1] / 1e3, wk[2] / 1e3, auflageMax));
            A.info("Strom und Seegang zuletzt (" + wer + ")", nassZ > 0 ? f("über %d nasse Zellen: Strom im Mittel %.4f m/s, Median %.4f m/s, 95 %% %.4f m/s (Rauheit aus dem Sediment %s); "
                            + "u_bot im Mittel %.4f m/s, Median %.4f m/s, 95 %% %.4f m/s (Seegang %d × %d auf %.0f m und %.0f m)", nassZ, uMittel / nassZ, strom[nassZ / 2], strom[(int) (0.95 * nassZ)],
                    l.hydro.rauheit ? "an" : "aus", ubMittel / nassZ, welle[nassZ / 2], welle[(int) (0.95 * nassZ)], e.seegangStuetzen[0], e.seegangStuetzen[1], e.seegangG2, e.seegangG3) : "–");
            double hy = l.takt.rechenzeit("hydro") - hy0, sd = l.takt.rechenzeit("sediment") - sd0;
            // seit P13d (A33, Datei 106): alle Module über dasselbe Fenster (ab dem Sediment) und die Wanduhr dazu; bis P13c
            // stand der Seegang über seine Zeit ab seegangAb, „alles“ über den ganzen Lauf (Xaver: 99,6 gegen 89,8 s)
            double fh = Math.max(1e-9, (dauer - e.sedimentAb) / 3600), wv = l.waves != null ? l.takt.rechenzeit("waves") - wv0 : 0,
                    al = l.takt.rechenzeitAlle() - al0, wand = (System.nanoTime() - nAb) / 1e9;
            A.info("Rechenzeit je Stunde Modellzeit, solange das Sediment rechnet (" + wer + ")", f("hydro %.1f s, Sediment %.1f s, Seegang %.1f s, alle Module %.1f s, Wanduhr %.1f s "
                            + "(über %.1f h; absolut hydro %.0f s, Sediment %.0f s, Seegang %.0f s, alle Module %.0f s, Wanduhr %.0f s; ganzer Lauf %.1f s je Stunde; P9b, Lauf B2: hydro 29 s)",
                    hy / fh, sd / fh, wv / fh, al / fh, wand / fh, fh, hy, sd, wv, al, wand, sek / (dauer / 3600)));
            if (l.waves != null) A.info("Seegang, Stoppuhr seit dem Bau (" + wer + ")", l.waves.stoppuhrText());
            A.pruefe(sd <= 0.5 * hy, "Prüfstein 9: Rechenzeit des Sediments gegen hydro, solange es rechnet (" + wer + ")", f("Sediment %.0f s, hydro %.0f s (%.2f)", sd, hy, sd / hy), "≤ ½ hydro");
            A.info("Zeit (" + wer + ")", f("%.1f h Modellzeit in %.1f min; Archiv %d Stände des Wassers, %d des Seegangs, %d des Schwebstoffs, %d des Bodens, %.0f MB", dauer / 3600, sek / 60,
                    l.archiv.staende, l.archiv.seeStaende, l.archiv.sedStaende, l.archiv.bodenStaende, l.archiv.bytes / 1e6));
            auf.schreibe(new File(ar, ORDNER), f3, start);
            A.info("Aufnahme (" + wer + ")", f("%s: %d Tiden mit Flut- und Ebbstrom je Zelle; Eingang des Sediments für %d × %d Zellen ab Zelle %d, %d über %d Intervalle", new File(ar, ORDNER), auf.tiden,
                    auf.fenster[2], auf.fenster[3], auf.fenster[0], auf.fenster[1], auf.schritte.size()));
        }
        A.info("Teil 1 gesamt (" + wer + ")", f("%.1f min", (System.nanoTime() - n0) / 6e10));
    }

    // ------------------------------------------------------------------ Aufnahme für die Feldvergleiche

    /**
     * Liest mit, was das Sediment liest: je Tide ab dem Beginn des Archivs der größte Strom und die größte
     * Kornschubspannung des Stroms je Zelle bei steigendem (Flut) und fallendem Wasser (Ebbe), und für ein Fenster der
     * ganze Eingang über die letzten Stunden (Prüfstein 4).
     */
    static final class Aufnahme implements Modul {
        final Gitter g;
        final boolean seegang;
        final double kopplung, ab, ende, probeAb;
        final int[] fenster;
        final int tiden;
        /** Je Tide: größter Strom bei Flut, bei Ebbe (m/s), größte Kornschubspannung bei Flut, bei Ebbe (N/m²). */
        final float[][][] groesste;
        final List<float[][]> schritte = new ArrayList<>();
        final List<double[]> zeiten = new ArrayList<>();
        Sediment sediment;
        private Feld eta0, eta1, q, h, u, v, ub, tp, dir;

        Aufnahme(Gitter g, boolean seegang, double kopplung, double ab, double ende, int[] fenster, double probeAb) {
            this.g = g;
            this.seegang = seegang;
            this.kopplung = kopplung;
            this.ab = ab;
            this.ende = ende;
            this.fenster = fenster;
            this.probeAb = probeAb;
            tiden = Math.max(1, (int) Math.floor((ende - ab) / TIDE + 0.1));
            groesste = new float[tiden][4][g.zellen()];
        }

        @Override public String name() { return "pruefung"; }

        @Override public void anmelden(Anmeldung a) {
            a.kopplung(kopplung);
            a.liestImIntervall("water.eta", "m NHN", Ort.ZELLE, g).liestImIntervall("water.q", "m²/s", Ort.KANTE, g).liestImIntervall("water.h", "m", Ort.ZELLE, g)
                    .liestImIntervall("water.u", "m/s", Ort.ZELLE, g).liestImIntervall("water.v", "m/s", Ort.ZELLE, g);
            if (seegang) a.liest("waves.ubot", "m/s", Ort.ZELLE, g).liest("waves.tp", "s", Ort.ZELLE, g).liest("waves.dir", "°", Ort.ZELLE, g);
        }

        @Override public void beginn(double t0, Bus bus) {
            eta0 = bus.lesefeld(this, "water.eta");
            eta1 = bus.lesefeld(this, "water.eta");
            q = bus.lesefeld(this, "water.q");
            h = bus.lesefeld(this, "water.h");
            u = bus.lesefeld(this, "water.u");
            v = bus.lesefeld(this, "water.v");
            if (seegang) {
                ub = bus.lesefeld(this, "waves.ubot");
                tp = bus.lesefeld(this, "waves.tp");
                dir = bus.lesefeld(this, "waves.dir");
            }
        }

        @Override public void schritt(double t0, double t1, Bus bus) {
            boolean probe = t0 >= probeAb - 1e-6, statistik = t0 >= ab - 1e-6;
            if (!probe && !statistik) return;
            bus.lies(this, "water.eta", t0, eta0);
            bus.lies(this, "water.eta", t1, eta1);
            double mitte = 0.5 * (t0 + t1);
            bus.lies(this, "water.h", mitte, h);
            bus.lies(this, "water.u", mitte, u);
            bus.lies(this, "water.v", mitte, v);
            int tide = (int) Math.floor((t0 - ab) / TIDE);
            if (statistik && tide < tiden && sediment != null) {
                float[][] gr = groesste[tide];
                Sediment s = sediment;
                for (int c = 0; c < s.nc; c++) {
                    double hc = h.hol(c);
                    if (hc < s.hMin) continue;
                    double uu = Math.hypot(u.hol(c), v.hol(c)), tau = s.rho * Sediment.cdStrom(hc, s.d50[c] / 12) * uu * uu;
                    int k = eta1.hol(c) > eta0.hol(c) ? 0 : 1;
                    if (uu > gr[k][c]) gr[k][c] = (float) uu;
                    if (tau > gr[2 + k][c]) gr[2 + k][c] = (float) tau;
                }
            }
            if (!probe) return;
            bus.lies(this, "water.q", t1, q);
            if (seegang) {
                bus.lies(this, "waves.ubot", t0, ub);
                bus.lies(this, "waves.tp", t0, tp);
                bus.lies(this, "waves.dir", t0, dir);
            }
            int i0 = fenster[0], j0 = fenster[1], ni = fenster[2], nj = fenster[3], nc = ni * nj;
            float[][] a = new float[9][];
            Feld[] zellen = {eta0, eta1, h, u, v, ub, tp, dir};
            for (int k = 0; k < zellen.length; k++) {
                a[k] = new float[nc];
                if (zellen[k] == null) continue;
                for (int j = 0; j < nj; j++) for (int i = 0; i < ni; i++) a[k][i + ni * j] = (float) zellen[k].hol(g.index(i0 + i, j0 + j));
            }
            // Flüsse je Kante in der Folge eines Felds auf dem Fenster: erst die Kanten quer zu x, dann quer zu y
            a[8] = new float[(ni + 1) * nj + ni * (nj + 1)];
            for (int j = 0; j < nj; j++) for (int i = 0; i <= ni; i++) a[8][i + (ni + 1) * j] = (float) q.hol(q.kanteX(i0 + i, j0 + j));
            for (int j = 0; j <= nj; j++) for (int i = 0; i < ni; i++) a[8][(ni + 1) * nj + i + ni * j] = (float) q.hol(q.kanteY(i0 + i, j0 + j));
            schritte.add(a);
            zeiten.add(new double[] {t0, t1});
        }

        /** Schreibt {@code statistik.bin}, {@code aufnahme.bin} und {@code pruef.txt}. */
        void schreibe(File ordner, int[] f3, Instant start) throws IOException {
            Files.createDirectories(ordner.toPath());
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(new File(ordner, "statistik.bin").toPath()), 1 << 20))) {
                o.writeInt(tiden);
                o.writeInt(g.nx);
                o.writeInt(g.ny);
                for (float[][] t : groesste) for (float[] x : t) for (float w : x) o.writeFloat(w);
            }
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(new File(ordner, "aufnahme.bin").toPath()), 1 << 20))) {
                for (int w : fenster) o.writeInt(w);
                o.writeInt(seegang ? 1 : 0);
                o.writeInt(schritte.size());
                for (int k = 0; k < schritte.size(); k++) {
                    o.writeDouble(zeiten.get(k)[0]);
                    o.writeDouble(zeiten.get(k)[1]);
                    for (float[] x : schritte.get(k)) for (float w : x) o.writeFloat(w);
                }
            }
            List<String> z = new ArrayList<>();
            z.add("# Aufnahme von SedimentLaufCheck (Werkbuch HalligHooge Teil A, 12.10, P9b); Zahlen big endian");
            z.add("start " + start);
            z.add(String.format(Locale.ROOT, "gitter %s %d %.1f %.1f %.1f %d %d", g.name, g.zone, g.x0, g.y0, g.dx, g.nx, g.ny));
            z.add("fenster3 " + (f3 == null ? "-" : f3[0] + " " + f3[1] + " " + f3[2] + " " + f3[3]));
            z.add(String.format(Locale.ROOT, "statistik.bin: int Tiden, nx, ny; je Tide (%.0f s ab %.0f s) vier Felder float: größter Strom bei steigendem, bei fallendem Wasser (m/s), "
                    + "größte Kornschubspannung des Stroms ebenso (N/m²)", TIDE, ab));
            z.add("aufnahme.bin: int i0, j0, ni, nj, Seegang, Intervalle; je Intervall double t0, t1, dann float: eta(t0), eta(t1), h, u, v (Mitte), ubot, tp, dir (t0) je Zelle, q je Kante (erst quer zu x, dann quer zu y)");
            Files.write(new File(ordner, "pruef.txt").toPath(), z, StandardCharsets.UTF_8);
        }
    }

    /** Die Aufnahme eines Fensters aus {@code aufnahme.bin}. */
    record Eingang(int[] fenster, boolean seegang, List<double[]> zeiten, List<float[][]> schritte) {
        static Eingang lies(File ordner) throws IOException {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(new File(ordner, "aufnahme.bin").toPath()), 1 << 20))) {
                int[] w = {in.readInt(), in.readInt(), in.readInt(), in.readInt()};
                boolean see = in.readInt() == 1;
                int n = in.readInt(), nc = w[2] * w[3], nk = (w[2] + 1) * w[3] + w[2] * (w[3] + 1);
                List<double[]> z = new ArrayList<>();
                List<float[][]> s = new ArrayList<>();
                for (int k = 0; k < n; k++) {
                    z.add(new double[] {in.readDouble(), in.readDouble()});
                    float[][] a = new float[9][];
                    for (int q = 0; q < 9; q++) {
                        a[q] = new float[q == 8 ? nk : nc];
                        for (int i = 0; i < a[q].length; i++) a[q][i] = in.readFloat();
                    }
                    s.add(a);
                }
                return new Eingang(w, see, z, s);
            }
        }
    }

    /** Die Statistik je Tide aus {@code statistik.bin}: [Tide][4][Zelle]. */
    static float[][][] statistik(File ordner, Gitter g) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(new File(ordner, "statistik.bin").toPath()), 1 << 20))) {
            int n = in.readInt(), nx = in.readInt(), ny = in.readInt();
            if (nx != g.nx || ny != g.ny) throw new IOException(ordner + ": Statistik für " + nx + " × " + ny + " Zellen, das Gitter hat " + g.nx + " × " + g.ny);
            float[][][] a = new float[n][4][nx * ny];
            for (float[][] t : a) for (float[] x : t) for (int i = 0; i < x.length; i++) x[i] = in.readFloat();
            return a;
        }
    }

    /**
     * Prüfstein 4: das Sediment auf der Karte gegen den Prozessor mit dem aufgenommenen Eingang eines Fensters, beide vom
     * selben Anfang (Boden aus dem Kornfeld). Gibt {mittlere Abweichung von sed.c Sand, Schlick, der Bodenänderung, jeweils
     * relativ zum mittleren Betrag; größte Abweichungen absolut} zurück.
     */
    static double[] karteGegenProzessor(File gb, int[] f3, Eingang ein, boolean karte) throws Exception {
        Gebiet ge = Gebiet.lies(gb);
        Gitter g3 = ge.gitter("G3");
        int[] w = ein.fenster();
        int i0 = (f3 == null ? 0 : f3[0]) + w[0], j0 = (f3 == null ? 0 : f3[1]) + w[1];
        Gitter g = Gitter.utm("G3", g3.zone, g3.x0 + i0 * g3.dx, g3.y0 + j0 * g3.dy, g3.dx, w[2], w[3]);
        Subgrid.Packung p = ge.packung("G3").fenster(g3, i0, j0, w[2], w[3]);
        Kornfeld korn = Kornfeld.vorhanden(gb) ? Kornfeld.lies(gb) : null;
        Katalog kat = Katalog.grundstufe();
        String[] namen = {"water.eta", "water.eta", "water.h", "water.u", "water.v", "waves.ubot", "waves.tp", "waves.dir", "water.q"};
        Feld[] fd = new Feld[9];
        for (int k = 0; k < 9; k++) fd[k] = new Feld(kat.verlange(namen[k]), g, Feld.Zahl.FLOAT);
        SedimentModul.Rechner r1 = new SedimentModul.Prozessor(), r2 = karte ? new SedimentRechner() : new SedimentModul.Prozessor();
        try {
            SedimentModul a = new SedimentModul(g, p, korn, r1, 300, ein.seegang(), 0.02), b = new SedimentModul(g, p, korn, r2, 300, ein.seegang(), 0.02);
            System.arraycopy(ein.schritte().get(0)[0], 0, fd[0].f, 0, fd[0].laenge);
            a.beginne(fd[0]);
            b.beginne(fd[0]);
            for (int k = 0; k < ein.schritte().size(); k++) {
                float[][] s = ein.schritte().get(k);
                for (int q = 0; q < 9; q++) System.arraycopy(s[q], 0, fd[q].f, 0, fd[q].laenge);
                double tau = ein.zeiten().get(k)[1] - ein.zeiten().get(k)[0];
                Feld ub = ein.seegang() ? fd[5] : null, tp = ein.seegang() ? fd[6] : null, dir = ein.seegang() ? fd[7] : null;
                a.rechne(tau, fd[0], fd[8], fd[2], fd[3], fd[4], ub, tp, dir);
                b.rechne(tau, fd[0], fd[8], fd[2], fd[3], fd[4], ub, tp, dir);
            }
            Sediment x = a.sediment, y = b.sediment;
            double[] sum = new double[3], bez = new double[3], max = new double[3];
            for (int c = 0; c < x.nc; c++) {
                double[] u = {x.cs[c], x.cm[c], x.dzS[c] + x.dzM[c]}, v = {y.cs[c], y.cm[c], y.dzS[c] + y.dzM[c]};
                for (int q = 0; q < 3; q++) {
                    double d = Math.abs(u[q] - v[q]);
                    sum[q] += d;
                    bez[q] += Math.abs(u[q]);
                    if (d > max[q]) max[q] = d;
                }
            }
            double[] bl = b.bilanz();
            return new double[] {sum[0] / Math.max(1e-30, bez[0]), sum[1] / Math.max(1e-30, bez[1]), sum[2] / Math.max(1e-30, bez[2]), max[0], max[1], max[2], bez[0] / x.nc, bez[1] / x.nc, bez[2] / x.nc,
                    a.teilschritte, b.teilschritte, bl[0], bl[1]};
        } finally {
            r1.close();
            r2.close();
        }
    }
}
