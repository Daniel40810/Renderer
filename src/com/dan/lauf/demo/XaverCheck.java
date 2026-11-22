package com.dan.lauf.demo;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Gradraster;
import com.dan.core.gitter.Wetterfeld;
import com.dan.core.pruef.Attest;
import com.dan.core.pruef.Pegel;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Bauten;
import com.dan.lauf.Ansicht;
import com.dan.lauf.Archivquelle;
import com.dan.lauf.Buehne;
import com.dan.lauf.Lauf;
import com.dan.lauf.Szenario;
import com.dan.lauf.Szenen;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.dan.core.pruef.Attest.f;

/**
 * Sturmflut Xaver, 5./6.12.2013 (P13a, Werkbuch HalligHooge Teil A, 12.14, Datei 85): Antrieb, Scheitel, Landunter und
 * Bilder in der fertigen Landschaft. Der Lauf selbst (Kette G0–G3 mit Seegang und Sediment, Archiv) ist
 * {@link SedimentLaufCheck} mit {@code --start/--ab/--ende}; dieser Check liest, was er ablegt.
 * <ul>
 * <li><b>Teil antrieb</b> (Lauf A1): ERA5 Dezember 2013 im Gebiet ({@code antrieb/201312/}, {@link Wetterfeld#lies(File, String, Instant)}):
 * deckt Wind, Druck, Schub und Seegang den Lauf (3.12. 06 Uhr bis 6.12. 18 Uhr UTC); Kenngrößen über der Deutschen
 * Bucht. Prüfstein A1.</li>
 * <li><b>Teil scheitel</b> (Lauf A2): aus dem Archiv je Pegel der Scheitel der drei Hochwasser; Prüfstein 1 gegen die
 * Berichte (± 30 cm, F13), Prüfstein 2 Wasserstand auf der Hallig (AG Halligen 2050), Prüfstein 3 Freibord der Häuser
 * (Auskunft), Prüfstein 4 Stau an Husum (Auskunft, F38).</li>
 * <li><b>Teil wind</b> (P13d, Lauf D, A31 und F38): Wind von ERA5 gegen Helgoland und List (DWD, historische
 * 10-Minuten-Werte) am 5./6.12.2013, Attest {@code docs/status/P13d_Wind.md}; Prüfstein 8.</li>
 * <li><b>Teil bild</b> (Lauf A2): das Szenario {@code xaver} der Szenendatei zu drei Zeiten an vier Ansichten,
 * {@code docs/status/P13a_Xaver.png} (Prüfstein 10), Kosten je Bild (Prüfstein 9).</li>
 * </ul>
 * <pre>
 * java --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar com.dan.lauf.demo.XaverCheck --teil antrieb --gebiet D:\Daten\HalligHooge\gebiet
 * java ... XaverCheck --teil scheitel,bild --gebiet D:\Daten\HalligHooge\gebiet --archiv D:\Daten\HalligHooge\_p13\archiv_xaver --szenen ..\HalligHooge\src\szenen\Hooge.szenen
 * </pre>
 * Attest {@code docs/status/P13a_Xaver.md}.
 */
public final class XaverCheck {
    private static final Attest A = new Attest("com.dan.lauf (Xaver)", Lauf.VERSION);
    static final Instant LAUF_START = Instant.parse("2013-12-03T06:00:00Z"), LAUF_ENDE = Instant.parse("2013-12-06T18:00:00Z");
    /** Fenster der drei Hochwasser (UTC): 5.12. nachmittags, 6.12. früh (der höchste), 6.12. nachmittags. */
    static final Instant[] HW = {Instant.parse("2013-12-05T08:00:00Z"), Instant.parse("2013-12-05T20:00:00Z"), Instant.parse("2013-12-06T10:00:00Z"), Instant.parse("2013-12-06T18:00:00Z")};
    static final ZoneId MEZ = ZoneId.of("Europe/Berlin");
    static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("d.M. HH:mm").withZone(MEZ);
    /** Grenze von Prüfstein 1 (F13). */
    static final double GRENZE = 0.30;
    /** Hooge Anleger wie {@code pegel.ort} der Szenendatei (UTM 32). */
    static final double HOOGE_E = 470410, HOOGE_N = 6048090;
    /** Kasten um die Hallig für Prüfstein 2 (UTM 32) und Höhen der Fenne und Salzwiese (m NHN). */
    static final double[] HALLIG = {468000, 6045800, 472600, 6048600};
    static final double FENNE_UNTEN = 0.5, FENNE_OBEN = 2.6;
    /** AG Halligen 2050 (MELUR SH, April 2014): Wasserstand auf den Halligen bei Xaver 4,0 bis 4,5 m NHN. */
    static final double HALLIG_VON = 4.0, HALLIG_BIS = 4.5;
    static final double LANDUNTER = 2.01;

    /**
     * Ein Pegel mit den gemessenen Scheiteln (m NHN, NaN: kein Wert gefunden) für das erste und das zweite Hochwasser;
     * Zeiten wie in den Berichten (MEZ).
     */
    record Ref(String name, String stamm, double e, double n, double hw1, String t1, double hw2, String t2, String quelle, boolean pruefstein) { }

    static final Ref[] REF = {
            new Ref("Hooge Anleger", null, HOOGE_E, HOOGE_N, 3.66, "5.12. 15:34", 4.00, "6.12. 03:58", "LKN-SH, Bericht über die Sturmflut vom 05.–06.12.2013 (9,01 m PN)", true),
            new Ref("Wittdün", "WITTDUEN", Double.NaN, Double.NaN, 3.39, "5.12. 15:45", 3.76, "6.12. 03:44", "LKN-SH, Bericht 2013 (Kurzbericht des Landes: 3,84 m um 04:41)", true),
            new Ref("Hörnum", "HOERNUM", Double.NaN, Double.NaN, Double.NaN, "", 3.63, "6.12. 04:06", "LKN-SH, Bericht 2013", true),
            new Ref("Dagebüll", "DAGEBUELL", Double.NaN, Double.NaN, Double.NaN, "", 3.95, "6.12. 03:22", "Land SH, Kurzberichte zu Sturmfluten (Wert mit Stern)", true),
            new Ref("Husum", "HUSUM", Double.NaN, Double.NaN, 4.76, "", 4.97, "", "BSH 2013: MHW 1,69 m NN + 3,07 bzw. 3,28 m; Zuordnung der Spalten zu den Tiden zu prüfen", true),
            new Ref("Pellworm Anleger", "PELLWORM_ANLEGER", Double.NaN, Double.NaN, Double.NaN, "", Double.NaN, "", "kein Wert gefunden (LKN-Bericht nicht lesbar)", false),
    };
    /** Windstau an Husum am 6.12. früh laut BSH (m), für Prüfstein 4. */
    static final double STAU_HUSUM_BSH = 3.04;

    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", archiv = "D:\\Daten\\HalligHooge\\_p13\\archiv_xaver", szenen = "..\\HalligHooge\\src\\szenen\\Hooge.szenen", teil = "scheitel,bild";
    static String ansichten = "landunter_hanswarft,landunter_kirchwarft,warften,westkante";
    static String zeiten = "2013-12-05T13:30:00Z,2013-12-06T03:00:00Z,2013-12-06T09:00:00Z";
    static final int BREITE = 960, HOEHE = 540;

    public static void main(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--gebiet" -> gebiet = v;
                case "--archiv" -> archiv = v;
                case "--szenen" -> szenen = v;
                case "--teil" -> teil = v;
                case "--ansichten" -> ansichten = v;
                case "--zeiten" -> zeiten = v;
                case "--hw" -> { String[] t = v.split(","); for (int k = 0; k < 4; k++) HW[k] = Instant.parse(t[k].strip()); }   // nur zum Probieren an anderen Archiven
                default -> { }
            }
        }
        System.out.println("XaverCheck – Sturmflut Xaver, 5./6.12.2013 (Werkbuch HalligHooge Teil A, P13a), Teile " + teil);
        long n0 = System.nanoTime();
        File gb = new File(gebiet);
        try {
            if (teil.contains("antrieb")) antrieb(gb);
            if (teil.contains("scheitel")) scheitel(gb, new File(archiv));
            if (teil.contains("bild")) bild(new File(szenen));
            if (teil.contains("wind")) wind(gb);
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        String name = teil.equals("antrieb") ? "P13a_Xaver_Antrieb.md" : teil.equals("wind") ? "P13d_Wind.md" : "P13a_Xaver.md";
        Path p = A.schreibe(Path.of("docs", "status", name), (teil.equals("wind") ? "P13d" : "P13a") + " – XaverCheck (" + teil + ")");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ Wind gegen DWD (P13d, A31)

    /** Rauheitslänge über See für den Stationswind auf 10 m (m) [G], wie TideCheck (P5e). */
    static final double Z0_SEE = 2e-4;

    /**
     * Wind von ERA5 gegen Helgoland und List (P13d, A31, F38; Prüfstein 8, Auskunft): je volle Stunde ERA5 {@code u10},
     * {@code v10} bilinear am Ort und linear in der Zeit gegen das Stundenmittel der Station (10-Minuten-Werte mit Ende bis
     * 30 min vor und nach der Stunde), auf 10 m mit dem logarithmischen Profil über See (z0 = 0,2 mm [G]). Fenster: der
     * Sturm (5.12. 06 bis 6.12. 06 UTC), der Anstieg zum ersten Hochwasser (5.12. 06–14 UTC) und zum zweiten (5.12. 18 bis
     * 6.12. 03 UTC). Je Fenster: Mittel ERA5 / Station, U², mittlere Abweichung und RMS (m/s), die höchsten Stundenmittel mit
     * ihrer Zeit (Verzug ERA5 gegen Station) und die Richtung (gewichtet mit dem Wind über 5 m/s).
     */
    static void wind(File gb) throws Exception {
        A.teil("Wind: ERA5 gegen DWD am 5./6.12.2013 (A31, F38)");
        Map<String, com.dan.core.pruef.Windstation> st = com.dan.core.pruef.Windstation.lies(gb);
        Instant[][] fenster = {{Instant.parse("2013-12-05T06:00:00Z"), Instant.parse("2013-12-06T06:00:00Z")},
                {Instant.parse("2013-12-05T06:00:00Z"), Instant.parse("2013-12-05T14:00:00Z")},
                {Instant.parse("2013-12-05T18:00:00Z"), Instant.parse("2013-12-06T03:00:00Z")}};
        String[] namen = {"Sturm 5.12. 06 – 6.12. 06 UTC", "Anstieg zum 1. Hochwasser (5.12. 06–14 UTC)", "Anstieg zum 2. Hochwasser (5.12. 18 – 6.12. 03 UTC)"};
        Wetterfeld w = Wetterfeld.lies(gb, "era5", fenster[0][0]);
        int iu = w.groesse("u10"), iv = w.groesse("v10");
        int erwartet = 0, da = 0;
        StringBuilder reihe = new StringBuilder("zeit");
        List<double[][]> werte = new ArrayList<>();
        List<String> stationen = new ArrayList<>();
        for (com.dan.core.pruef.Windstation s : st.values()) {
            var gw = w.raster.gewichte(s.laenge, s.breite, null, 0, 1, 0);
            int nh = (int) ((fenster[0][1].getEpochSecond() - fenster[0][0].getEpochSecond()) / 3600) + 1;
            double[][] r = new double[nh][];
            for (int k = 0; k < nh; k++) {
                Instant t = fenster[0][0].plusSeconds(3600L * k);
                erwartet++;
                double us = s.mittel(t, 1800);
                double kk = (t.getEpochSecond() - w.start.getEpochSecond()) / (double) w.schritt;
                int k0 = (int) Math.floor(kk);
                double a = kk - k0, ux = 0, uy = 0;
                for (int m = 0; m < gw.index().length; m++) {
                    int pnt = gw.index()[m];
                    ux += gw.gewicht()[m] * ((1 - a) * w.feld(k0, iu)[pnt] + a * w.feld(Math.min(w.zeiten - 1, k0 + 1), iu)[pnt]);
                    uy += gw.gewicht()[m] * ((1 - a) * w.feld(k0, iv)[pnt] + a * w.feld(Math.min(w.zeiten - 1, k0 + 1), iv)[pnt]);
                }
                double de = (Math.toDegrees(Math.atan2(-ux, -uy)) + 360) % 360;
                if (!Double.isNaN(us)) da++;
                r[k] = new double[] {t.getEpochSecond(), Math.hypot(ux, uy), Double.isNaN(us) ? Double.NaN : s.auf10m(us, Z0_SEE), de, richtung(s, t)};
            }
            werte.add(r);
            stationen.add(s.name);
            reihe.append(',').append(s.name).append("_era5,").append(s.name).append("_station");
            StringBuilder b = new StringBuilder(f("Geber %.0f m über Grund, %.4f° N, %.4f° O; Quelle %s", s.geber, s.breite, s.laenge, s.quelle));
            for (int q = 0; q < fenster.length; q++) {
                double se = 0, ss = 0, se2 = 0, ss2 = 0, d = 0, d2 = 0, me = -1, ms = -1, dr = 0, gwr = 0;
                long te = 0, ts = 0;
                int n = 0;
                for (double[] x : r) {
                    if (x[0] < fenster[q][0].getEpochSecond() || x[0] > fenster[q][1].getEpochSecond() || Double.isNaN(x[2])) continue;
                    se += x[1]; ss += x[2]; se2 += x[1] * x[1]; ss2 += x[2] * x[2]; d += x[1] - x[2]; d2 += (x[1] - x[2]) * (x[1] - x[2]);
                    if (x[1] > me) { me = x[1]; te = (long) x[0]; }
                    if (x[2] > ms) { ms = x[2]; ts = (long) x[0]; }
                    if (!Double.isNaN(x[4]) && x[2] > 5) { dr += (((x[3] - x[4] + 540) % 360) - 180) * x[2]; gwr += x[2]; }
                    n++;
                }
                if (n == 0) { b.append(" – ").append(namen[q]).append(": keine Werte der Station"); continue; }
                b.append(f(" – %s (%d h): Mittel ERA5 %.1f gegen Station %.1f m/s (%.2f), U² %.2f, Abweichung %+.1f m/s, RMS %.1f m/s; höchstens ERA5 %.1f m/s um %s MEZ, Station %.1f m/s um %s MEZ (Verzug ERA5 %+.0f h); Richtung ERA5 %+.0f°",
                        namen[q], n, se / n, ss / n, se / ss, se2 / ss2, d / n, Math.sqrt(d2 / n), me, UHR.format(Instant.ofEpochSecond(te)), ms, UHR.format(Instant.ofEpochSecond(ts)),
                        (te - ts) / 3600.0, gwr > 0 ? dr / gwr : Double.NaN));
            }
            A.info("Wind " + s.name + " (" + s.kennung + ") gegen ERA5", b.toString());
        }
        A.pruefe(erwartet > 0 && da >= 0.9 * erwartet, "Prüfstein 8: Stundenmittel der Stationen am 5./6.12.2013 vorhanden", f("%d von %d (%s)", da, erwartet, String.join(", ", stationen)),
                "≥ 90 % (historische ZIP des DWD im Gebiet)");
        reihe.append('\n');
        if (!werte.isEmpty())
            for (int k = 0; k < werte.get(0).length; k++) {
                reihe.append(Instant.ofEpochSecond((long) werte.get(0)[k][0]));
                for (double[][] r : werte) reihe.append(f(",%.2f,%.2f", r[k][1], r[k][2]));
                reihe.append('\n');
            }
        Path p = Path.of("docs", "status", "P13d_Wind.csv");
        Files.createDirectories(p.getParent());
        Files.writeString(p, reihe);
        A.info("Reihen (Stundenmittel, Station auf 10 m)", p.toString());
    }

    /** Richtung (°, woher) der Station: der Wert mit Ende am nächsten an t. */
    static double richtung(com.dan.core.pruef.Windstation s, Instant t) {
        int i = Arrays.binarySearch(s.zeiten, t.getEpochSecond());
        if (i < 0) i = Math.min(s.zeiten.length - 1, -i - 1);
        return s.dd[i];
    }

    // ------------------------------------------------------------------ Antrieb (A1)

    static void antrieb(File gb) throws Exception {
        A.teil("Antrieb: ERA5 Dezember 2013");
        Wetterfeld w = Wetterfeld.lies(gb, "era5", LAUF_START);
        File o = Wetterfeld.ordnerFuer(gb, "era5", LAUF_START);
        boolean deckt = !w.start.isAfter(LAUF_START) && !w.ende().isBefore(LAUF_ENDE);
        A.pruefe(deckt && o != null && !o.equals(new File(gb, Wetterfeld.ORDNER)), "Prüfstein A1: Wind und Druck decken den Lauf (eigener Zeitraum unter antrieb/)",
                f("%s: %s bis %s, %d Zeiten, Größen %s", o, w.start, w.ende(), w.zeiten, String.join(" ", w.groessen)), "3.12. 06 bis 6.12. 18 Uhr UTC");
        A.pruefe(w.groesse("iews") >= 0 && w.groesse("inss") >= 0, "Prüfstein A1: Windschub (F34) im Zeitraum", String.join(" ", w.groessen), "iews, inss vorhanden");
        boolean see = Wetterfeld.da(gb, Wetterfeld.SEEGANG, LAUF_START) && Wetterfeld.da(gb, Wetterfeld.SEEGANG, LAUF_ENDE);
        A.pruefe(see, "Prüfstein A1: Seegang am Rand deckt den Lauf", Wetterfeld.zeiten(gb, Wetterfeld.SEEGANG), "3.12. 06 bis 6.12. 18 Uhr UTC");
        A.info("Oktober 2026 unverändert erreichbar", Wetterfeld.zeiten(gb, "era5"));
        // Kenngrößen über der Deutschen Bucht (53,5–56° N, 4–9,5° O) im Lauf
        Gradraster r = w.raster;
        int iu = w.groesse("u10"), iv = w.groesse("v10"), ip = w.groesse("msl");
        double vmax = 0, pmin = Double.POSITIVE_INFINITY, richtung = 0;
        Instant tv = null, tp = null;
        long nan = 0;
        for (int k = 0; k < w.zeiten; k++) {
            Instant t = w.zeit(k);
            if (t.isBefore(LAUF_START) || t.isAfter(LAUF_ENDE)) continue;
            float[] u = w.feld(k, iu), v = w.feld(k, iv), p = w.feld(k, ip);
            for (int j = 0; j < r.ny(); j++)
                for (int i = 0; i < r.nx(); i++) {
                    double la = r.laenge(i), br = r.breite(j);
                    if (la < 4 || la > 9.5 || br < 53.5 || br > 56) continue;
                    int q = j * r.nx() + i;
                    if (Float.isNaN(u[q]) || Float.isNaN(v[q]) || Float.isNaN(p[q])) { nan++; continue; }
                    double s = Math.hypot(u[q], v[q]);
                    if (s > vmax) { vmax = s; tv = t; richtung = (Math.toDegrees(Math.atan2(-u[q], -v[q])) + 360) % 360; }
                    if (p[q] < pmin) { pmin = p[q]; tp = t; }
                }
        }
        A.pruefe(nan == 0 && vmax >= 20 && richtung >= 225 && richtung <= 360, "Prüfstein A1: stärkster Wind über der Deutschen Bucht im Lauf (Plausibilität)",
                f("%.1f m/s aus %.0f° am %s (MEZ); %d Werte NaN", vmax, richtung, tv == null ? "–" : UHR.format(tv), nan), "≥ 20 m/s aus SW bis N [G]; 0 NaN");
        A.info("Kleinster Druck über der Deutschen Bucht im Lauf", f("%.1f hPa am %s (MEZ)", pmin / 100, tp == null ? "–" : UHR.format(tp)));
        if (see) {
            Wetterfeld s = Wetterfeld.lies(gb, Wetterfeld.SEEGANG, LAUF_START);
            int ih = s.groesse("swh");
            double hmax = 0;
            Instant th = null;
            Gradraster rs = s.raster;
            for (int k = 0; ih >= 0 && k < s.zeiten; k++) {
                Instant t = s.zeit(k);
                if (t.isBefore(LAUF_START) || t.isAfter(LAUF_ENDE)) continue;
                float[] h = s.feld(k, ih);
                for (int j = 0; j < rs.ny(); j++)
                    for (int i = 0; i < rs.nx(); i++) {
                        double la = rs.laenge(i), br = rs.breite(j);
                        if (la < 4 || la > 9.5 || br < 53.5 || br > 56) continue;
                        float x = h[j * rs.nx() + i];
                        if (!Float.isNaN(x) && x > hmax) { hmax = x; th = t; }
                    }
            }
            A.info("Größte Wellenhöhe am Rand (ERA5, Deutsche Bucht)", f("%.1f m am %s (MEZ)", hmax, th == null ? "–" : UHR.format(th)));
        }
    }

    // ------------------------------------------------------------------ Scheitel (A2)

    /** Kandidaten eines Pegels: Zellen im Umkreis auf einem Gitter des Archivs, der Stand je Zeit. */
    static final class Ort {
        final Ref ref;
        final double e, n;
        int k = -1;
        int[] zellen;
        double[] hSumme;
        float[][] eta;
        int beste = -1;

        Ort(Ref ref, double e, double n) { this.ref = ref; this.e = e; this.n = n; }
    }

    static void scheitel(File gb, File ar) throws Exception {
        A.teil("Scheitel und Landunter aus dem Archiv " + ar);
        Map<String, Pegel> pegel = Pegel.da(gb) ? Pegel.lies(gb) : Map.of();
        try (Archivquelle q = new Archivquelle(ar.toPath())) {
            List<Gitter> gs = q.gitter();
            double[] zt = q.zeiten();
            A.info("Archiv", f("%d Stände von %s bis %s (UTC); Gitter %s; Antrieb %s", zt.length, zeit(q, zt[0]), zeit(q, zt[zt.length - 1]), gs, q.kopf.antrieb));
            // Orte auf das feinste Gitter, das sie enthält
            List<Ort> orte = new ArrayList<>();
            for (Ref r : REF) {
                double e = r.e, n = r.n;
                if (r.stamm != null) {
                    Pegel pg = null;
                    for (Pegel x : pegel.values()) if (Pegel.datei(x.name).replace(".f32", "").equals(r.stamm)) pg = x;
                    if (pg == null) { A.info(r.name, "nicht in pegel.txt"); continue; }
                    double[] u = gs.get(0).punkt(pg.laenge, pg.breite);
                    e = u[0];
                    n = u[1];
                }
                Ort o = new Ort(r, e, n);
                for (int k = gs.size() - 1; k >= 0 && o.k < 0; k--) if (gs.get(k).enthaelt(e, n) && k <= 1) o.k = k;
                if (o.k < 0) { A.info(r.name, f("außerhalb der Gitter des Archivs (O %.0f, N %.0f)", e, n)); continue; }
                Gitter g = gs.get(o.k);
                double um = Math.max(300, 2.5 * g.dx);
                List<Integer> z = new ArrayList<>();
                for (int j = g.j(n - um); j <= g.j(n + um); j++)
                    for (int i = g.i(e - um); i <= g.i(e + um); i++)
                        if (i >= 0 && j >= 0 && i < g.nx && j < g.ny && Math.hypot(g.x(i) - e, g.y(j) - n) <= um) z.add(g.index(i, j));
                o.zellen = z.stream().mapToInt(Integer::intValue).toArray();
                o.hSumme = new double[o.zellen.length];
                o.eta = new float[o.zellen.length][zt.length];
                orte.add(o);
            }
            // G3 (Gitter 1): höchster Stand je Zelle und ob sie nass war
            int k3 = Math.min(1, gs.size() - 1);
            Gitter g3 = gs.get(k3);
            float[] maxEta = new float[g3.zellen()], bettBeiMax = new float[g3.zellen()];
            boolean[] nass = new boolean[g3.zellen()];
            Arrays.fill(maxEta, Float.NEGATIVE_INFINITY);
            float[][] eta = new float[gs.size()][], h = new float[gs.size()][], wet = new float[gs.size()][];
            for (int k = 0; k < gs.size(); k++) { eta[k] = new float[gs.get(k).zellen()]; h[k] = new float[gs.get(k).zellen()]; wet[k] = new float[gs.get(k).zellen()]; }
            double[] hooge = new double[zt.length];
            for (int s = 0; s < zt.length; s++) {
                for (int k = 0; k <= k3; k++) q.stand(zt[s], k, eta[k], h[k], wet[k]);
                for (Ort o : orte)
                    for (int c = 0; c < o.zellen.length; c++) {
                        int z = o.zellen[c];
                        float hh = h[o.k][z];
                        o.hSumme[c] += Float.isNaN(hh) ? 0 : hh;
                        o.eta[c][s] = hh > 0.05f ? eta[o.k][z] : Float.NaN;
                    }
                for (int z = 0; z < g3.zellen(); z++) {
                    float hh = h[k3][z], ee = eta[k3][z];
                    if (hh > 0.05f && !Float.isNaN(ee)) {
                        nass[z] = true;
                        if (ee > maxEta[z]) { maxEta[z] = ee; bettBeiMax[z] = ee - hh; }
                    }
                }
                if (s % 48 == 0) System.out.printf(Locale.ROOT, "  %s gelesen%n", zeit(q, zt[s]));
            }
            for (Ort o : orte) {
                double b = -1;
                for (int c = 0; c < o.zellen.length; c++) if (o.hSumme[c] > b) { b = o.hSumme[c]; o.beste = c; }
            }
            // Prüfstein 1
            A.teil("Prüfstein 1: Scheitel an den Pegeln (m NHN; Zeiten MEZ)");
            for (Ort o : orte) {
                Ref r = o.ref;
                float[] reihe = o.eta[o.beste];
                double[][] sch = new double[3][2];
                for (int w = 0; w < 3; w++) {
                    sch[w][0] = Double.NaN;
                    for (int s = 0; s < zt.length; s++) {
                        Instant t = q.start.plusMillis(Math.round(1000 * zt[s]));
                        if (t.isBefore(HW[w]) || !t.isBefore(HW[w + 1]) || Float.isNaN(reihe[s])) continue;
                        if (Double.isNaN(sch[w][0]) || reihe[s] > sch[w][0]) { sch[w][0] = reihe[s]; sch[w][1] = zt[s]; }
                    }
                }
                Gitter g = gs.get(o.k);
                int z = o.zellen[o.beste];
                String wo = f("%s Zelle %d/%d (%.0f m vom Pegel)", g.name, z % g.nx, z / g.nx, Math.hypot(g.x(z % g.nx) - o.e, g.y(z / g.nx) - o.n));
                String modell = f("5.12.: %s; 6.12. früh: %s; 6.12. nachm.: %s", wert(q, sch[0]), wert(q, sch[1]), wert(q, sch[2]));
                if (r.pruefstein && !Double.isNaN(r.hw2) && !Double.isNaN(sch[1][0])) {
                    double d = sch[1][0] - r.hw2;
                    A.pruefe(Math.abs(d) <= GRENZE, "Prüfstein 1: " + r.name + ", Scheitel 6.12. früh",
                            f("Modell %.2f m, gemessen %.2f m (%s): %+.2f m. %s. %s", sch[1][0], r.hw2, r.t2, d, modell, wo), "± 0,30 m (F13)");
                } else A.info(r.name, modell + "; " + wo + "; " + r.quelle);
                if (!Double.isNaN(r.hw1) && !Double.isNaN(sch[0][0]))
                    A.info(r.name + ", 5.12. nachmittags", f("Modell %.2f m, gemessen %.2f m %s: %+.2f m", sch[0][0], r.hw1, r.t1, sch[0][0] - r.hw1));
                A.info(r.name + ", Quelle", r.quelle);
                if (r.stamm != null && r.stamm.equals("HUSUM")) stau(pegel, q, reihe, sch[1], zt);
                if (r.stamm == null) {
                    for (int s = 0; s < zt.length; s++) hooge[s] = reihe[s];
                }
            }
            // Landunter am Pegel Hooge
            double ueber = 0;
            for (int s = 0; s < zt.length; s++) if (!Double.isNaN(hooge[s]) && hooge[s] > LANDUNTER) ueber += s + 1 < zt.length ? zt[s + 1] - zt[s] : 0;
            A.info("Landunter am Pegel Hooge (über 2,01 m NHN)", f("%.1f h von %.1f h im Archiv", ueber / 3600, (zt[zt.length - 1] - zt[0]) / 3600));
            // Prüfstein 2: Wasser auf der Hallig
            List<Float> hallig = new ArrayList<>();
            for (int z = 0; z < g3.zellen(); z++) {
                double x = g3.x(z % g3.nx), y = g3.y(z / g3.nx);
                if (!nass[z] || x < HALLIG[0] || x > HALLIG[2] || y < HALLIG[1] || y > HALLIG[3]) continue;
                if (bettBeiMax[z] >= FENNE_UNTEN && bettBeiMax[z] <= FENNE_OBEN) hallig.add(maxEta[z]);
            }
            hallig.sort(null);
            if (hallig.isEmpty()) A.pruefe(false, "Prüfstein 2: Wasserstand auf der Hallig (Fenne und Salzwiese, 0,5–2,6 m NHN)", "keine nasse Zelle", "4,0 bis 4,5 m NHN ± 0,30 m");
            else {
                double med = hallig.get(hallig.size() / 2), p10 = hallig.get(hallig.size() / 10), p90 = hallig.get(9 * hallig.size() / 10);
                A.pruefe(med >= HALLIG_VON - GRENZE && med <= HALLIG_BIS + GRENZE, "Prüfstein 2: höchster Wasserstand auf der Hallig (Fenne und Salzwiese, 0,5–2,6 m NHN), Median",
                        f("%.2f m NHN (10 %%: %.2f, 90 %%: %.2f; %d Zellen von %s)", med, p10, p90, hallig.size(), g3.name), "4,0 bis 4,5 m NHN ± 0,30 m (AG Halligen 2050)");
            }
            // Prüfstein 3: Freibord der Häuser
            freibord(gb, g3, maxEta, nass, hallig.isEmpty() ? Double.NaN : hallig.get(hallig.size() / 2));
        }
    }

    static String zeit(Archivquelle q, double ts) { return q.start.plusMillis(Math.round(1000 * ts)).toString(); }

    static String wert(Archivquelle q, double[] s) {
        return Double.isNaN(s[0]) ? "–" : f("%.2f m um %s", s[0], UHR.format(q.start.plusMillis(Math.round(1000 * s[1]))));
    }

    /** Prüfstein 4 (Auskunft, F38): Stau an Husum = Stand − Vorhersage aus der Analyse des Pegels (Partialtiden aus den Reihen von 2026). */
    static void stau(Map<String, Pegel> pegel, Archivquelle q, float[] reihe, double[] scheitel, double[] zt) {
        Pegel pg = null;
        for (Pegel x : pegel.values()) if (Pegel.datei(x.name).replace(".f32", "").equals("HUSUM")) pg = x;
        if (pg == null || pg.analyse == null || Double.isNaN(scheitel[0])) { A.info("Prüfstein 4: Stau an Husum", "ohne Analyse des Pegels nicht möglich"); return; }
        double smax = Double.NEGATIVE_INFINITY;
        Instant tmax = null;
        for (int s = 0; s < zt.length; s++) {
            Instant t = q.start.plusMillis(Math.round(1000 * zt[s]));
            if (t.isBefore(HW[1]) || !t.isBefore(HW[2]) || Float.isNaN(reihe[s])) continue;
            double st = reihe[s] - pg.vorhersage(t);
            if (st > smax) { smax = st; tmax = t; }
        }
        Instant ts = q.start.plusMillis(Math.round(1000 * scheitel[1]));
        double amScheitel = scheitel[0] - pg.vorhersage(ts);
        A.info("Prüfstein 4: Stau an Husum, 6.12. früh (Auskunft, F38)",
                f("Modell: im Scheitel %.2f m, größter %.2f m um %s; BSH: Windstau %.2f m – Verhältnis im Scheitel %.2f", amScheitel, smax, tmax == null ? "–" : UHR.format(tmax),
                        STAU_HUSUM_BSH, amScheitel / STAU_HUSUM_BSH));
    }

    static void freibord(File gb, Gitter g3, float[] maxEta, boolean[] istNass, double hallig) throws Exception {
        Bauten bt = Bauten.lies(new File(gb, "bau"));
        if (bt == null) { A.info("Prüfstein 3: Freibord der Häuser", "keine Häuser im Gebiet"); return; }
        int unter0 = 0, unter05 = 0, ohneWasser = 0;
        double min = Double.POSITIVE_INFINITY;
        String minName = "";
        List<Double> alle = new ArrayList<>();
        List<String> nass = new ArrayList<>();
        for (Bauten.Gebaeude g : bt.gebaeude) {
            double e = bt.e0 + 0.5 * (g.huelle[0] + g.huelle[2]), n = bt.n0 - 0.5 * (g.huelle[1] + g.huelle[3]);
            double r = 0.5 * Math.hypot(g.huelle[2] - g.huelle[0], g.huelle[3] - g.huelle[1]) + 40;
            double wasser = Double.NEGATIVE_INFINITY;
            for (int j = g3.j(n - r); j <= g3.j(n + r); j++)
                for (int i = g3.i(e - r); i <= g3.i(e + r); i++) {
                    if (i < 0 || j < 0 || i >= g3.nx || j >= g3.ny) continue;
                    int z = g3.index(i, j);
                    if (istNass[z] && Math.hypot(g3.x(i) - e, g3.y(j) - n) <= r) wasser = Math.max(wasser, maxEta[z]);
                }
            if (wasser == Double.NEGATIVE_INFINITY) {
                if (Double.isNaN(hallig)) continue;
                wasser = hallig;
                ohneWasser++;
            }
            double fb = g.boden - wasser;
            alle.add(fb);
            if (fb < 0) { unter0++; nass.add((g.name.isBlank() ? g.kennung : g.name) + f(" (Boden %.2f, Wasser %.2f m NHN)", g.boden, wasser)); }
            if (fb < 0.5) unter05++;
            if (fb < min) { min = fb; minName = (g.name.isBlank() ? g.kennung : g.name) + f(" (Boden %.2f m NHN)", g.boden); }
        }
        alle.sort(null);
        A.info("Prüfstein 3: Freibord der Häuser im Scheitel (Auskunft)", alle.isEmpty() ? "–"
                : f("%d Häuser; Boden minus höchstes Wasser im Umkreis von 40 m um die Hülle: kleinstes %.2f m (%s), Median %.2f m; %d unter 0,5 m, %d unter 0 (Wasser über dem Boden); "
                        + "%d ohne nasse Zelle im Umkreis (gegen den Wasserstand der Hallig)", alle.size(), min, minName, alle.get(alle.size() / 2), unter05, unter0, ohneWasser));
        if (!nass.isEmpty()) A.info("Prüfstein 3: Häuser mit Wasser über dem Boden", String.join("; ", nass));
    }

    // ------------------------------------------------------------------ Bilder (A2)

    record Probe(String text, float[] bild) { }

    static void bild(File datei) throws Exception {
        A.teil("Bilder des Szenarios xaver");
        Szenen sz = Szenen.lies(datei);
        Szenario s = null;
        for (Szenario x : sz.szenarien()) if (x.schluessel.equals("xaver")) s = x;
        A.pruefe(s != null, "Szenario xaver in der Szenendatei", s == null ? "fehlt" : s.name, "vorhanden");
        if (s == null) return;
        List<Probe> proben = new ArrayList<>();
        double ms = 0;
        int n = 0;
        try (Buehne b = Buehne.ohneFenster(sz.gebiet(), BREITE, HOEHE)) {
            b.oeffne(s);
            for (String zs : zeiten.split(",")) {
                double t = (Instant.parse(zs.strip()).toEpochMilli() - b.uhr().start.toEpochMilli()) / 1000.0;
                for (String an : ansichten.split(",")) {
                    Ansicht a = sz.ansicht(an.strip());
                    if (a == null) { A.info("Ansicht " + an, "nicht in der Szenendatei"); continue; }
                    Blick bl = new Blick();
                    a.setze(bl, b.gelaende, b.utm(), b.uhr().bei(t));
                    bl.groesse(BREITE, HOEHE);
                    b.standbild(bl, t);                       // Belichtung einschwingen
                    b.standbild(bl, t);
                    float[] p = b.standbild(bl, t);
                    proben.add(new Probe(a.name + ", " + UHR.format(Instant.parse(zs.strip())) + " MEZ", p));
                    Buehne.Bildzeiten z = b.bildzeiten(bl, t, 20);
                    double ganz = z.karte()[z.karte().length / 2];
                    ms = Math.max(ms, ganz);
                    n++;
                    A.info("Bild: " + a.name + ", " + UHR.format(Instant.parse(zs.strip())), f("%.2f ms auf der Karte (Median aus 20), %.0f Bilder/s", ganz, 1000 / Math.max(1e-3, ganz)));
                }
            }
        }
        A.pruefe(n > 0 && 1000 / ms >= 30, "Prüfstein 9: Kosten bei Xaver mit allem (Seegang, Schaum, Trübung, Häuser, Bewuchs), langsamstes Bild",
                f("%.2f ms, %.0f Bilder/s (%d Bilder)", ms, 1000 / Math.max(1e-3, ms), n), "≥ 30 Bilder/s");
        if (!proben.isEmpty()) statusBild(proben);
    }

    static void statusBild(List<Probe> proben) throws Exception {
        int w = BREITE / 2, h = HOEHE / 2, spalten = Math.max(1, ansichten.split(",").length), zeilen = (proben.size() + spalten - 1) / spalten;
        BufferedImage alle = new BufferedImage(spalten * w, zeilen * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = alle.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < proben.size(); i++) {
            Probe p = proben.get(i);
            int x = (i % spalten) * w, y = (i / spalten) * h;
            g.drawImage(HausCheck.bild(p.bild), x, y, w, h, null);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(x + 4, y + 4, g.getFontMetrics().stringWidth(p.text) + 8, 18);
            g.setColor(Color.WHITE);
            g.drawString(p.text, x + 8, y + 17);
        }
        g.dispose();
        Path d = Path.of("docs", "status");
        Files.createDirectories(d);
        File png = d.resolve("P13a_Xaver.png").toFile();
        ImageIO.write(alle, "png", png);
        for (int i = 0; i < proben.size(); i++) ImageIO.write(HausCheck.bild(proben.get(i).bild), "png", d.resolve(f("P13a_Xaver_%d.png", i + 1)).toFile());
        A.info("Bilder zur Sichtprüfung (Prüfstein 10)", png.getPath() + " und P13a_Xaver_1 … " + proben.size() + ".png");
    }
}
