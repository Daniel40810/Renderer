package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.Zonenkarte;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Ueberflutung;
import com.dan.core.pruef.Attest;
import com.dan.core.szene.Gestalt;
import com.dan.core.szene.Zonen;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Zonen der Salzwiese (P13b, Werkbuch HalligHooge Teil A, 12.14, Datei 88): Prüfstein 11 (MThw des Laufs B1
 * gegen den Median der Hochwasser der EasyGSH-DB 2015), 12 (Überflutungsdauer der EasyGSH je Zone, Auskunft), 5 (Zonen
 * gegen Gestalt und NDVI des DOP20).
 * <pre>
 * java -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.demo.ZonenCheck --gebiet D:\Daten\HalligHooge\gebiet
 *      --easygsh D:\Daten\HalligHooge\gezeiten\easygsh_kennwerte\2015 --dop D:\Daten\HalligHooge\luftbild\dop20
 * </pre>
 * Attest {@code docs/status/P13b_Zonen.md}.
 */
public final class ZonenCheck {
    private static final Attest A = new Attest("com.dan.aufbereitung (zonen)", "0.1");
    static String gebiet = "D:\\Daten\\HalligHooge\\gebiet", easygsh = "D:\\Daten\\HalligHooge\\gezeiten\\easygsh_kennwerte\\2015", dop = "D:\\Daten\\HalligHooge\\luftbild\\dop20";
    /** Kasten um Hooge für Prüfstein 11 (UTM 32). */
    static double[] kasten = {455000, 6035000, 485000, 6060000};

    public static void main(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--gebiet" -> gebiet = v;
                case "--easygsh" -> easygsh = v;
                case "--dop" -> dop = v;
                default -> { }
            }
        }
        System.out.println("ZonenCheck – Zonen der Salzwiese (Werkbuch HalligHooge Teil A, P13b)");
        long n0 = System.nanoTime();
        try {
            File gb = new File(gebiet);
            Ueberflutung u = Ueberflutung.lies(new File(gb, "vegetation"));
            Zonen z = Zonen.lies(gb);
            A.pruefe(u != null && z != null, "Statistik und Zonen im Gebiet", (u == null ? "Statistik fehlt" : u.gitter + ", " + u.tiden + " Tiden") + "; " + (z == null ? "Zonen fehlen" : z.kacheln.size() + " Kacheln"), "vorhanden");
            if (u != null) mthw(u, Path.of(easygsh));
            if (z != null) zonen(z, Gestalt.lies(new File(gb, "gestalt")), Path.of(easygsh), Path.of(dop));
        } catch (Throwable e) {
            A.fehler(e);
        }
        A.info("Rechenzeit", f("%.1f min", (System.nanoTime() - n0) / 6e10));
        Path p = A.schreibe(Path.of("docs", "status", "P13b_Zonen.md"), "P13b – ZonenCheck");
        if (p != null) System.out.println("\nAttest: " + p);
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    /** Ein Kennwert von EasyGSH (wie {@code com.dan.lauf.demo.SeegangCheck}): Werte, Ecke Nordwest, Weite (m). */
    record Kennwert(float[] v, int nx, int ny, double west, double nord, double weite) {
        /** Wert am Ort (UTM), nächstes Pixel; NaN außerhalb oder ohne Wert. */
        double bei(double x, double y) {
            int i = (int) Math.floor((x - west) / weite), j = (int) Math.floor((nord - y) / weite);
            if (i < 0 || j < 0 || i >= nx || j >= ny) return Double.NaN;
            float w = v[i + nx * j];
            return Float.isNaN(w) || w < -100 || w > 1e6 ? Double.NaN : w;
        }
    }

    /** GeoTIFF von EasyGSH (float, ein Band); ModelTransformation (34264) nennt die Mitte des Pixels. */
    static Kennwert kennwert(Path f) throws Exception {
        javax.imageio.ImageReader r = javax.imageio.ImageIO.getImageReadersByFormatName("tiff").next();
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(f.toFile())) {
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

    /** Prüfstein 11: MThw je Zelle von G2 (nass in jeder Tide) gegen {@code high_water_level_50q}. */
    static void mthw(Ueberflutung u, Path ordner) throws Exception {
        A.teil("Prüfstein 11: MThw des Laufs B1 gegen EasyGSH 2015");
        Path hw = ordner.resolve("2015_100m_high_water_level_50q.tif");
        {
            Kennwert t = kennwert(hw);
            Gitter g = u.gitter;
            List<Double> diff = new ArrayList<>();
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    int c = g.index(i, j);
                    double x = g.x(i), y = g.y(j);
                    if (x < kasten[0] || x > kasten[2] || y < kasten[1] || y > kasten[3] || u.gefuellt[c] != 0 || u.anteil[c] < 0.99) continue;
                    double e = t.bei(x, y);
                    if (!Double.isNaN(e)) diff.add(u.mthw[c] - e);
                }
            diff.sort(null);
            if (diff.isEmpty()) { A.pruefe(false, "Prüfstein 11: MThw gegen EasyGSH", "keine Zelle zum Vergleich", "Median ± 0,10 m"); return; }
            double med = diff.get(diff.size() / 2), p10 = diff.get(diff.size() / 10), p90 = diff.get(9 * diff.size() / 10);
            A.pruefe(Math.abs(med) <= 0.10, "Prüfstein 11: MThw (Lauf B1) minus Median der Hochwasser (EasyGSH 2015), Zellen von G2 um Hooge, immer nass",
                    f("Median %+.3f m (10 %%: %+.3f, 90 %%: %+.3f; %d Zellen)", med, p10, p90, diff.size()), "Median ± 0,10 m [G]");
        }
    }

    static void zonen(Zonen z, Gestalt gs, Path easy, Path dop) throws Exception {
        A.teil("Prüfsteine 5 und 12: Zonen");
        // Flächen je Zone und Art der Gestalt
        double[][] fl = new double[2][5];
        double px = z.zelle * z.zelle;
        for (Zonen.Kachel k : z.kacheln) {
            Gestalt.Kachel gk = gs == null ? null : gs.kachel(k.ost() + 0.5 * z.kantenlaenge(), k.nord() + 0.5 * z.kantenlaenge());
            for (int j = 0; j < z.n; j++)
                for (int i = 0; i < z.n; i++) {
                    int o = 2 * (j * z.n + i);
                    if ((k.daten()[o + 1] & 0xFF) == 0) continue;
                    int zone = k.daten()[o] & 0xFF, art = 0;
                    if (gk != null) {
                        double ost = k.ost() + (i + 0.5) * z.zelle, nord = k.nord() + z.kantenlaenge() - (j + 0.5) * z.zelle;
                        int pi = Math.min(gs.n - 1, (int) Math.floor((ost - gk.ost()) / gs.zelle)), pj = Math.min(gs.n - 1, (int) Math.floor((gk.nord() + gs.kantenlaenge() - nord) / gs.zelle));
                        art = gk.daten()[2 * (pj * gs.n + pi)] & 0xFF;
                    }
                    fl[art == Gestalt.SALZWIESE ? 1 : 0][zone] += px;
                }
        }
        String[] an = {"Fenne", "Salzwiese"};
        for (int a = 0; a < 2; a++) {
            double s = fl[a][0] + fl[a][1] + fl[a][2] + fl[a][3] + fl[a][4];
            A.info("Flächen je Zone, " + an[a], f("%.1f ha: ohne %.1f, Pionierzone %.1f, untere %.1f, obere Salzwiese %.1f, Fenne ohne Tide %.1f ha",
                    s / 1e4, fl[a][0] / 1e4, fl[a][1] / 1e4, fl[a][2] / 1e4, fl[a][3] / 1e4, fl[a][4] / 1e4));
        }
        // F155 A: Die Fenne hat keine Tidezone mehr; der Anteil je Klasse ist Auskunft (Prüfstein 5 der Fläche: in Lauf B verfehlt, A37)
        double fz = fl[0][1] + fl[0][2] + fl[0][3] + fl[0][4], sz = fl[1][1] + fl[1][2] + fl[1][3];
        A.info("Prüfstein 5, Fläche (Auskunft nach F155 A; in Lauf B verfehlt, A37)", f("Fenne: ohne Tidezone %.1f %% der bewachsenen Fläche; Vorland (Salzwiese): Pionier %.1f %%, untere %.1f %%, obere %.1f %%",
                100 * fl[0][4] / Math.max(1, fz), 100 * fl[1][1] / Math.max(1, sz), 100 * fl[1][2] / Math.max(1, sz), 100 * fl[1][3] / Math.max(1, sz)));
        // NDVI je Zone: die Tidezonen 1–3 liegen nach F155 A nur noch auf dem Vorland
        double[][] nd = Zonenkarte.ndviJeZone(z, dop);
        double[] m = new double[5];
        for (int q = 1; q <= 4; q++) m[q] = nd[q][1] > 0 ? nd[q][0] / nd[q][1] : Double.NaN;
        boolean reihe = (Double.isNaN(m[1]) || m[1] < m[2]) && !(m[2] > m[3]);
        A.pruefe(nd[2][1] > 0 && nd[3][1] > 0 && reihe, "Prüfstein 5: NDVI des DOP20 je Zone auf dem Vorland in der Reihenfolge Pionier < unten ≤ oben",
                f("Pionierzone %.3f (%,.0f), untere %.3f (%,.0f), obere %.3f (%,.0f)", m[1], nd[1][1], m[2], nd[2][1], m[3], nd[3][1]), "Reihenfolge ja");
        A.info("NDVI der Fenne ohne Tidezone", f("%.3f (%,.0f Bildpunkte)", m[4], nd[4][1]));
        // Überflutungsdauer der EasyGSH je Zone (Auskunft)
        Path ip = easy.resolve("2015_100m_mean_inundation_period_12m.tif");
        {
            Kennwert t = kennwert(ip);
            double[][] s = new double[5][2];
            for (Zonen.Kachel k : z.kacheln)
                for (int j = 0; j < z.n; j += 5)
                    for (int i = 0; i < z.n; i += 5) {
                        int o = 2 * (j * z.n + i), zone = k.daten()[o] & 0xFF;
                        if ((k.daten()[o + 1] & 0xFF) == 0) continue;
                        double v = t.bei(k.ost() + (i + 0.5) * z.zelle, k.nord() + z.kantenlaenge() - (j + 0.5) * z.zelle);
                        if (Double.isNaN(v)) continue;
                        s[zone][0] += v;
                        s[zone][1]++;
                    }
            A.info("Prüfstein 12: mittlere Überflutungsdauer der EasyGSH 2015 je Zone (min je Tide, 745 = immer nass; das Innere der Hallig ist in EasyGSH trocken, ohne Wert)",
                    f("ohne %.0f (%,.0f), Pionierzone %.0f (%,.0f), untere %.0f (%,.0f), obere Salzwiese %.0f (%,.0f), Fenne %.0f (%,.0f) (Stichprobe alle 5 m, in Klammern Punkte mit Wert)",
                            s[0][0] / Math.max(1, s[0][1]), s[0][1], s[1][0] / Math.max(1, s[1][1]), s[1][1], s[2][0] / Math.max(1, s[2][1]), s[2][1], s[3][0] / Math.max(1, s[3][1]), s[3][1],
                            s[4][0] / Math.max(1, s[4][1]), s[4][1]));
        }
    }
}
