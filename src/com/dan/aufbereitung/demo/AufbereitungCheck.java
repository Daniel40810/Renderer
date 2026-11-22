package com.dan.aufbereitung.demo;

import com.dan.aufbereitung.Aufbereitung;
import com.dan.aufbereitung.Dgm1;
import com.dan.aufbereitung.Fuellwert;
import com.dan.aufbereitung.GeoTiff;
import com.dan.aufbereitung.Rezept;
import com.dan.aufbereitung.Stapel;
import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;
import com.dan.core.gitter.Subgrid;
import com.dan.core.pruef.Attest;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.dan.core.pruef.Attest.f;

/**
 * Prüfung der Aufbereitung ohne Fenster (Werkbuch HalligHooge Teil A, P3, Prüfsteine in 12.4). Endet mit „Alles in
 * Ordnung.“ oder mit der Zahl der Verstöße (Rückgabewert 1) und schreibt das Attest nach
 * {@code docs/status/P3_Aufbereitung.md} (mit {@code --ohne-attest} nur auf die Konsole).
 *
 * <pre>
 * AufbereitungCheck [--rezept &lt;datei&gt; [--daten &lt;ordner&gt;] [--ziel &lt;ordner&gt;] [--ohne-stapel]] [--ohne-attest]
 * </pre>
 *
 * Ohne Rezept prüft sie an erzeugten Daten: GeoTIFF in 68 Arten, ein Bit je Pixel mit CCITT Gruppe 4, XYZ, JSON und
 * OSM, Füllwert über Kachelgrenzen, den Stapel mit Saum, Hof und Mitteln, das Feingelände unter einem Gitter mit
 * Subgrid, Bauwerke und Rauheit einer erzeugten Hallig. Mit Rezept dazu die Quellen auf dem eigenen Rechner
 * (Vergleichswerte aus tifffile 2025.5.10, numpy und PIL), den gebauten Stapel gegen seine Quellen (Prüfsteine 2, 3,
 * 5, 6, 7 aus 12.4; {@code --ohne-stapel} lässt ihn aus), die Bauwerke von G3 (Prüfsteine 10–13) und die Rauheit
 * (Prüfstein 17). Die Grenzwerte standen fest, bevor gemessen wurde; [G] heißt gesetzt.
 */
public final class AufbereitungCheck {
    static final Attest A = new Attest("com.dan.aufbereitung", Aufbereitung.VERSION);
    /** Prüfstein 6: erlaubte Stufe im Saum über der des Grunds [G], und bis zu welchem Unterschied die Randpixel im Umkreis als gleich gelten (Höhe und Abstand zum Grund) [G]. */
    static final double STUFE = 0.25, GLEICH = 0.5;
    /** Neigung der Rampe im Saum beim Bau [G] (Rezept: naht.steigung). */
    static final double STEIGUNG = 0.2;

    public static void main(String[] args) throws Exception {
        List<String> a = List.of(args);
        int ri = a.indexOf("--rezept");
        if (ri >= 0 && (ri + 1 >= a.size() || a.get(ri + 1).startsWith("--"))) {
            System.out.println("AufbereitungCheck: --rezept braucht den Pfad zum Rezept, z. B. --rezept ..\\HalligHooge\\gebiet\\Hooge.rezept");
            System.out.println("AufbereitungCheck [--rezept <datei> [--daten <ordner>] [--ziel <ordner>] [--ohne-stapel]] [--ohne-attest]");
            System.exit(2);
        }
        boolean attest = !a.contains("--ohne-attest");
        System.out.println("AufbereitungCheck – com.dan.aufbereitung " + Aufbereitung.VERSION + " (Werkbuch HalligHooge Teil A, P3)");
        Path tmp = Files.createTempDirectory("aufbereitung-check");
        try {
            teil("1 GeoTIFF, erzeugt", () -> geotiff(tmp));
            teil("2 Ein Bit je Pixel (CCITT Gruppe 4)", () -> PruefungP3c.einBit(tmp));
            teil("3 XYZ", AufbereitungCheck::xyz);
            teil("4 JSON und OSM", PruefungP3c::osm);
            teil("5 Füllwert", AufbereitungCheck::fuellwert);
            teil("6 Stapel aus erzeugten Daten", () -> stapel(tmp));
            teil("7 Bauwerke und Rauheit aus erzeugten Daten", () -> PruefungP3c.bauwerkeErzeugt(tmp));
            teil("12 Leser für P5b: HDF5, GRIB, PEGELONLINE, ERA5 (erzeugt)", () -> PruefungP5b.leser(tmp));
            int r = a.indexOf("--rezept");
            if (r >= 0) {
                Rezept rz = Rezept.lies(Path.of(a.get(r + 1)));
                for (int i = 0; i + 1 < a.size(); i++)
                    if (a.get(i).startsWith("--") && !a.get(i).equals("--rezept") && !a.get(i).equals("--ohne-attest") && !a.get(i).equals("--ohne-stapel"))
                        rz.setze(a.get(i).substring(2), a.get(i + 1));
                Path daten = Path.of(rz.text("daten")), ziel = Path.of(rz.text("ziel"));
                teil("8 Quellen auf dem eigenen Rechner", () -> quellen(daten, ziel));
                if (!a.contains("--ohne-stapel")) teil("9 Stapel des Gebiets gegen seine Quellen", () -> gebiet(rz, daten, ziel, tmp));
                if (rz.hat("g3.gebiet")) {
                    teil("10 Bauwerke des Gebiets", () -> PruefungP3c.bauwerkeGebiet(rz, daten, ziel));
                    teil("11 Rauheit des Gebiets", () -> PruefungP3c.rauheitGebiet(rz, daten, ziel));
                }
            } else {
                A.teil("8 Quellen, Stapel, Bauwerke und Rauheit des Gebiets");
                A.info("ohne Rezept", "nicht geprüft (--rezept)");
            }
        } finally {
            loesche(tmp.toFile());
        }
        if (attest) {
            Path p = A.schreibe(Path.of("docs", "status", "P3_Aufbereitung.md"), "P3 – AufbereitungCheck");
            if (p != null) System.out.println("\nAttest: " + p);
        }
        System.out.println();
        System.out.println(A.pruefsteine() + " Prüfsteine. " + A.urteil());
        System.exit(A.verstoesse() == 0 ? 0 : 1);
    }

    interface Teil { void lauf() throws Exception; }

    static void teil(String name, Teil t) {
        A.teil(name);
        try {
            t.lauf();
        } catch (Throwable e) {
            A.fehler(e);
        }
    }

    // ------------------------------------------------------------------ 1 GeoTIFF

    static void geotiff(Path tmp) throws IOException {
        int w = 37, h = 29;
        int arten = 0, falsch = 0;
        String erster = "";
        for (ByteOrder o : new ByteOrder[] {ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN})
            for (boolean kacheln : new boolean[] {false, true})
                for (int komp : new int[] {1, 5, 8, 32773})
                    for (int art = 0; art < 4; art++) {
                        TiffSchreiber s = new TiffSchreiber();
                        s.ordnung = o;
                        s.kacheln = kacheln;
                        s.kompression = komp;
                        s.block = kacheln ? 16 : 5;
                        switch (art) {
                            case 0 -> { s.format = 3; s.bits = 32; s.praediktor = 1; }
                            case 1 -> { s.format = 3; s.bits = 32; s.praediktor = 3; }
                            case 2 -> { s.format = 2; s.bits = 16; s.praediktor = 2; }
                            default -> { s.format = 1; s.bits = 8; s.praediktor = 2; s.proben = 3; }
                        }
                        double[][] v = muster(w, h, s.format, s.bits, s.proben);
                        Path p = tmp.resolve("t" + arten + ".tif");
                        s.schreibe(p, v, w, h);
                        int f = vergleiche(p, v, w, h, s.proben);
                        if (f > 0 && erster.isEmpty()) erster = f("%s, %s, Kompression %d, Art %d: %d falsch", o, kacheln ? "Kacheln" : "Streifen", komp, art, f);
                        falsch += f;
                        arten++;
                    }
        A.pruefe(falsch == 0, f("%d Arten: II/MM × Streifen/Kacheln × ohne/LZW/Deflate/PackBits × float32, float32 mit Prädiktor 3, int16 und 3 × uint8 mit Prädiktor 2; "
                + "Bild ganz und Fenster mit Rand außerhalb", arten), falsch == 0 ? "alle Werte gleich" : f("%d Werte falsch (%s)", falsch, erster), "gleich");
        // BigTIFF, Lage, Leerwert
        TiffSchreiber s = new TiffSchreiber();
        s.bigTiff = true;
        s.kacheln = true;
        s.kompression = 8;
        s.praediktor = 3;
        s.transformation = true;
        s.punkt = true;
        s.x0 = 312995;
        s.y0 = 6113005;
        s.dx = 10;
        s.dy = -10;
        s.leer = "-9999";
        double[][] v = muster(w, h, 3, 32, 1);
        v[3][4] = -9999;
        Path p = tmp.resolve("big.tif");
        s.schreibe(p, v, w, h);
        try (GeoTiff t = GeoTiff.oeffne(p)) {
            float[] r = t.lies(0, 0, w, h);
            boolean werte = true;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (!(y == 3 && x == 4) && r[y * w + x] != (float) v[y][x]) werte = false;
            A.pruefe(t.bigTiff && werte && Float.isNaN(r[3 * w + 4]), "BigTIFF, Deflate mit Prädiktor 3, Leerwert −9999", werte ? "Werte gleich, Leerwert NaN" : "falsch", "gleich");
            A.pruefe(t.x0 == 312995 && t.y0 == 6113005 && t.dx == 10 && t.dy == -10 && t.epsg == 25832 && !t.geografisch,
                    "Lage aus ModelTransformation mit PixelIsPoint (Anknüpfpunkt auf der Pixelmitte)", f("Ecke %.1f / %.1f, Schritt %.0f / %.0f, EPSG %d", t.x0, t.y0, t.dx, t.dy, t.epsg),
                    "Ecke 312995 / 6113005, Schritt 10 / −10, EPSG 25832");
        }
        s = new TiffSchreiber();
        s.x0 = -15;
        s.y0 = 64;
        s.dx = 1 / 120.0;
        s.dy = -1 / 120.0;
        s.epsg = 4326;
        s.format = 2;
        s.bits = 16;
        s.leer = "-32767.0";
        p = tmp.resolve("geo.tif");
        s.schreibe(p, muster(w, h, 2, 16, 1), w, h);
        try (GeoTiff t = GeoTiff.oeffne(p)) {
            A.pruefe(t.x0 == -15 && t.y0 == 64 && Math.abs(t.dx - 1 / 120.0) < 1e-15 && t.geografisch && t.epsg == 4326 && t.leer == -32767,
                    "Lage aus PixelScale und Tiepoint, geographisch, Leerwert als Text mit Komma-Stelle", f("Ecke %.0f / %.0f, EPSG %d, leer %.0f", t.x0, t.y0, t.epsg, t.leer),
                    "Ecke −15 / 64, EPSG 4326, leer −32767");
        }
    }

    static double[][] muster(int w, int h, int format, int bits, int proben) {
        double[][] v = new double[h][w * proben];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                for (int p = 0; p < proben; p++)
                    v[y][x * proben + p] = format == 3 ? (float) (5 * Math.sin(x * 0.3) * Math.cos(y * 0.2) + 0.01 * x - 0.7 * p)
                            : bits == 16 ? ((x * 37 + y * 1013) % 30000) - 15000 : (x * 3 + y * 5 + p * 50) & 0xff;
        return v;
    }

    static int vergleiche(Path p, double[][] v, int w, int h, int proben) throws IOException {
        int falsch = 0;
        try (GeoTiff t = GeoTiff.oeffne(p)) {
            for (int b = 0; b < proben; b++) {
                float[] g = t.lies(0, 0, w, h, b);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (g[y * w + x] != (float) v[y][x * proben + b]) falsch++;
                int fx = 5, fy = 7, fw = 40, fh = 30;                                      // Fenster ragt rechts und unten hinaus
                float[] f = t.lies(fx, fy, fw, fh, b);
                for (int y = 0; y < fh; y++)
                    for (int x = 0; x < fw; x++) {
                        int gx = fx + x, gy = fy + y;
                        float soll = gx < w && gy < h ? (float) v[gy][gx * proben + b] : Float.NaN;
                        if (Float.compare(f[y * fw + x], soll) != 0) falsch++;
                    }
            }
        }
        return falsch;
    }

    // ------------------------------------------------------------------ 2 XYZ

    static void xyz() {
        String t = "X Y Z\r\n"
                + "470000.50 6047003.50 1.30\r\n"
                + "470001.50 6047003.50 -0.25\n"
                + "470002.50,6047000.50,4.5e0\n"
                + "470003.5\t6047000.5\t-1.234567\n"
                + "470001.50 6047003.50 -0.26\n"                                            // doppelt
                + "470009.50 6047000.50 9\n"                                                // außerhalb
                + "470000.50 6047000.50 2";
        int[] b = new int[4];
        float[] v = Dgm1.xyz(t.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 470000, 6047000, 4, 1.0, b);
        boolean werte = v[0] == 1.30f && v[1] == -0.26f && v[3 * 4 + 2] == 4.5f && v[3 * 4 + 3] == -1.234567f && v[3 * 4] == 2f && Float.isNaN(v[5]);
        A.pruefe(werte, "Werte an ihren Pixeln (Zeilen von Nord), Trenner Leerzeichen, Komma, Tabulator, CRLF, Kopfzeile, Exponent", werte ? "gleich" : Arrays.toString(v), "gleich");
        A.pruefe(b[0] == 7 && b[1] == 1 && b[2] == 1 && b[3] == 11, "Bericht: Punkte, außerhalb, doppelt, leer", f("%d, %d, %d, %d", b[0], b[1], b[2], b[3]), "7, 1, 1, 11");
    }

    // ------------------------------------------------------------------ 3 Füllwert

    static void fuellwert() {
        int n = 50;
        Map<Long, float[]> k = new HashMap<>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) {
            float[] t = new float[n * n];
            Arrays.fill(t, 2.0f);
            k.put(Fuellwert.schluessel(x, y), t);
        }
        float[] a = k.get(Fuellwert.schluessel(0, 0)), b = k.get(Fuellwert.schluessel(1, 0)), c = k.get(Fuellwert.schluessel(0, 1));
        // 1: über die Ostgrenze A|B: 200 Pixel in A, 30 in B → weg
        for (int y = 0; y < 10; y++) {
            for (int x = 30; x < 50; x++) a[y * n + x] = 1.30f;
            for (int x = 0; x < 3; x++) b[y * n + x] = 1.3015f;
        }
        // 2: über die Nordgrenze A|C: 60 Pixel in A (Zeilen 0–2 am Nordrand), 50 in C (Südrand) → weg
        for (int x = 0; x < 20; x++) for (int y = 0; y < 3; y++) a[y * n + x] = 1.30f;
        for (int x = 0; x < 10; x++) for (int y = n - 5; y < n; y++) c[y * n + x] = 1.30f;
        // 3: klein im Inneren von A: 30 Pixel → bleibt; 4: knapp außerhalb der Toleranz → bleibt
        for (int y = 20; y < 25; y++) for (int x = 10; x < 16; x++) a[y * n + x] = 1.30f;
        for (int y = 30; y < 45; y++) for (int x = 10; x < 30; x++) a[y * n + x] = 1.305f;
        Fuellwert.Ergebnis e = new Fuellwert(1.30, 0.002, 100).entferne(k, n, 1.0);
        boolean ok = Float.isNaN(b[5 * n + 1]) && Float.isNaN(c[(n - 1) * n + 3]) && a[22 * n + 12] == 1.30f && a[35 * n + 15] == 1.305f;
        A.pruefe(e.pixel() == 340 && e.flaechen() == 2 && ok, "Flächen über Ost- und Nordgrenze von Kacheln (Teile mit 30 bzw. 50 Pixeln) entfernt; 30 Pixel im Inneren und 1,305 m bleiben",
                f("%d Pixel in %d Flächen entfernt, %d kleine Flächen behalten", e.pixel(), e.flaechen(), e.flaechenBehalten()), "340 Pixel in 2 Flächen");
    }

    // ------------------------------------------------------------------ 4 Stapel aus erzeugten Daten

    static void stapel(Path tmp) throws IOException {
        // Grund: 10 m wie TrilaWatt (Mitten auf vollen 10 m, MM, Streifen zu 8 Zeilen), E 469 000–474 000, N 6 044 000–6 049 000
        int gw = 501, gh = 501;
        double[][] g = new double[gh][gw];
        for (int r = 0; r < gh; r++)
            for (int c = 0; c < gw; c++) {
                double x = 469000 + c * 10, y = 6049000 - r * 10;
                g[r][c] = (float) (-2 + 0.0006 * (x - 469000) + 0.5 * Math.sin(y / 37.0) + (Math.abs(x - 470400) < 15 ? -3 : 0));   // mit einer Rinne
            }
        TiffSchreiber s = new TiffSchreiber();
        s.ordnung = ByteOrder.BIG_ENDIAN;
        s.block = 8;
        s.transformation = true;
        s.x0 = 468995;
        s.y0 = 6049005;
        s.dx = 10;
        s.dy = -10;
        Path gq = tmp.resolve("grund.tif");
        s.schreibe(gq, g, gw, gh);
        // Land: 1-m-Kacheln 470–472 × 6045–6047 mit Füllwert 1,30, eine Hallig (Scheibe), eine Warft, eine Kaje mit Becken
        int n = 1000;
        Map<Long, float[]> land = new HashMap<>();
        Map<Long, float[]> quelle = new HashMap<>();
        Random zufall = new Random(7);
        for (int e = 470; e <= 472; e++)
            for (int nn = 6045; nn <= 6047; nn++) {
                float[] t = new float[n * n];
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        double x = e * 1000 + i + 0.5, y = nn * 1000 + 999.5 - j;
                        double r = Math.hypot(x - 471500, y - 6046500);
                        float z = 1.30f;
                        if (r < 700) z = (float) Math.round((1.5 + 0.0003 * (x - 471500) + 0.02 * zufall.nextGaussian()) * 100) / 100f;
                        if (r < 60) z = (float) Math.round((4.5 - r / 30) * 100) / 100f;
                        if (r >= 700 && r < 712 && Math.abs(y - 6046500) < 40 && x > 471500) z = 3.60f;          // Kaje am Ostrand
                        if (r >= 718 && r < 740 && Math.abs(y - 6046500) < 40 && x > 471500) z = 0.50f;          // Becken davor, 6 m Spalt mit Füllwert
                        t[j * n + i] = z;
                    }
                land.put(Fuellwert.schluessel(e, nn), t);
                quelle.put(Fuellwert.schluessel(e, nn), t.clone());
            }
        Fuellwert.Ergebnis fe = new Fuellwert(1.30, 0.002, 100).entferne(land, n, 1.0);
        File st = tmp.resolve("stapel").toFile();
        Gelaende.Stufe grund, vorher;
        try (GeoTiff t = GeoTiff.oeffne(gq)) {
            grund = Stapel.grund(t, new File(st, "10m"), 469000, 6044000, 474000, 6049000, 200, "Probe");
            vorher = Stapel.grund(t, tmp.resolve("vorher").toFile(), 469000, 6044000, 474000, 6049000, 200, "Probe vorher");
        }
        long t0 = System.nanoTime();
        Stapel.Ergebnis se = Stapel.land(land, n, 1.0, grund, new File(st, "1m"), 469000, 6044000, 5, 5, 10, 40, STEIGUNG, "Probe");
        A.info("Aufbau", f("%s; Füllwert: %d Pixel; %.1f s", se, fe.pixel(), (System.nanoTime() - t0) / 1e9));
        Gelaende.Stufe l1 = Gelaende.Stufe.lies(new File(st, "1m"));
        grund = Gelaende.Stufe.lies(new File(st, "10m"));
        pruefeStapel(l1, grund, vorher, land, quelle, 10, 40, STEIGUNG, new Random(11), 20000, "erzeugt");
        // Gitter mit Subgrid aus dem Stapel
        Gelaende gel = Gelaende.ordner(st);
        Gitter g3 = Gitter.utmBereich("P3", 32, 470000, 6045000, 473000, 6048000, 20);
        t0 = System.nanoTime();
        Subgrid sg = Subgrid.bauen(g3, 20, gel.feingelaende(g3, 20));
        double zeit = (System.nanoTime() - t0) / 1e9;
        double[] z = new double[3000 * 3000];
        gel.feingelaende(g3, 20).lies(0, 0, 3000, 3000, z);
        double schlimmst = 0;
        for (double eta : new double[] {-2, -1, 0, 1, 2, 3, 5}) {
            double vs = 0, vd = 0;
            for (int c = 0; c < g3.zellen(); c++) vs += sg.volumen(c, eta);
            for (double h : z) vd += Math.max(0, eta - h);
            schlimmst = Math.max(schlimmst, Math.abs(vs - vd) / g3.flaeche());
        }
        A.hoechstens(f("Subgrid 20 m aus dem 1-m-Stapel (%.1f s): Volumen bei η = −2 … +5 m gegen die Summe über die Pixel, als mittlere Tiefe", zeit), schlimmst, 1e-3, "m");
        Gitter g2 = Gitter.utmBereich("P2", 32, 470000, 6045000, 473000, 6048000, 200);
        Subgrid s2 = Subgrid.bauen(g2, 20, gel.feingelaende(g2, 20));
        double ab = 0;
        for (double eta : new double[] {-1, 0, 1, 2}) {
            double v2 = 0, v3 = 0;
            for (int c = 0; c < g2.zellen(); c++) v2 += s2.volumen(c, eta);
            for (int c = 0; c < g3.zellen(); c++) v3 += sg.volumen(c, eta);
            ab = Math.max(ab, Math.abs(v2 - v3) / Math.max(v3, 1));
        }
        A.info("Schachtel 200 m (aus der 10-m-Stufe) gegen 20 m (aus 1 m), Volumen über das Gebiet bei η = −1 … +2 m", f("größte Abweichung %.2f %%", 100 * ab));
    }

    /**
     * Die Prüfsteine 2, 3, 5, 6 und 7 aus 12.4 für einen Stapel. {@code land}: die gemessenen Kacheln nach dem Füllwert;
     * {@code vorher}: die Grundstufe ohne Mittel. Stichproben mit {@code zufall}.
     */
    static void pruefeStapel(Gelaende.Stufe l1, Gelaende.Stufe grund, Gelaende.Stufe vorher, Map<Long, float[]> land, Map<Long, float[]> roh, double saum, double saumMax, double steigung,
                             Random zufall, int proben, String wo) throws IOException {
        int n = l1.kachelZellen;
        double k = l1.kachel();
        int f = (int) Math.round(grund.zelle / l1.zelle);
        // Prüfstein 2: Land gleich der Quelle
        List<Long> keys = new ArrayList<>(land.keySet());
        keys.sort(Long::compare);
        int geprueft = 0, falsch = 0;
        for (int i = 0; i < proben && !keys.isEmpty(); i++) {
            long key = keys.get(zufall.nextInt(keys.size()));
            float[] t = land.get(key);
            int p = zufall.nextInt(n * n);
            if (Float.isNaN(t[p])) continue;
            double x = Fuellwert.spalte(key) * k + (p % n + 0.5) * l1.zelle, y = Fuellwert.zeile(key) * k + k - (p / n + 0.5) * l1.zelle;
            float s = l1.wert((int) Math.round(l1.spalte(x)), (int) Math.round(l1.zeile(y)));
            geprueft++;
            if (Float.compare(s, t[p]) != 0) falsch++;
        }
        A.pruefe(falsch == 0 && geprueft > 0, f("Prüfstein 2 (%s): gemessene Pixel des Lands, Stichproben", wo), f("%d von %d gleich", geprueft - falsch, geprueft), "bitgleich");
        // Pro Kachel der Landstufe: Land, Saum, Grund vorher
        Set<Long> aenderbar = new HashSet<>();
        long paare = 0, ueber = 0, stufenSaum = 0, paareUngleich = 0;
        double groessteUngleich = 0;
        double groesste = 0;
        String groessteOrt = "";
        int weitPx = (int) Math.ceil(saumMax / l1.zelle - 1e-9), m = weitPx + 2;
        int w = n + 2 * m;
        long fuellPixel = 0;
        Map<Long, float[]> stufe = new HashMap<>();
        for (int ty = 0; ty < l1.kachelnNord; ty++)
            for (int tx = 0; tx < l1.kachelnOst; tx++) {
                float[] t = l1.kachel(tx, ty);
                if (t.length == 0) continue;
                stufe.put(Fuellwert.schluessel(tx, l1.kachelnNord - 1 - ty), t.clone());
                double ox = l1.suedwestOst + tx * k - m * l1.zelle, oy = l1.suedwestNord + (l1.kachelnNord - ty) * k + m * l1.zelle;
                float[] lw = new float[w * w];
                for (int v = 0; v < w; v++)
                    for (int u = 0; u < w; u++) {
                        double x = ox + (u + 0.5) * l1.zelle, y = oy - (v + 0.5) * l1.zelle;
                        int ek = (int) Math.floor(x / k), nk = (int) Math.floor(y / k);
                        float[] q = land.get(Fuellwert.schluessel(ek, nk));
                        lw[v * w + u] = q == null ? Float.NaN : q[(n - 1 - (int) Math.floor((y - nk * k) / l1.zelle)) * n + (int) Math.floor((x - ek * k) / l1.zelle)];
                    }
                // Saum (eigene Rechnung der Breite je Randpixel) und die Höhen der Randpixel, die ein Pixel erreichen
                boolean[] imSaum = new boolean[w * w];
                float[] minZ = new float[w * w], maxZ = new float[w * w];                    // Höhe der Randpixel, deren Saum das Pixel erreicht
                float[] minD = new float[w * w], maxD = new float[w * w];                    // und ihr Unterschied zum Grund
                Arrays.fill(minZ, Float.POSITIVE_INFINITY);
                Arrays.fill(maxZ, Float.NEGATIVE_INFINITY);
                Arrays.fill(minD, Float.POSITIVE_INFINITY);
                Arrays.fill(maxD, Float.NEGATIVE_INFINITY);
                for (int v = 0; v < w; v++)
                    for (int u = 0; u < w; u++) {
                        int p = v * w + u;
                        if (Float.isNaN(lw[p])) continue;
                        boolean rand = u > 0 && Float.isNaN(lw[p - 1]) || u < w - 1 && Float.isNaN(lw[p + 1]) || v > 0 && Float.isNaN(lw[p - w]) || v < w - 1 && Float.isNaN(lw[p + w]);
                        if (!rand) continue;
                        double delta = lw[p] - vorher.bilinear(ox + (u + 0.5) * l1.zelle, oy - (v + 0.5) * l1.zelle);
                        if (Double.isNaN(delta)) continue;
                        double se = Math.min(saumMax, Math.max(saum, Math.abs(delta) / steigung));        // Breite des Saums an diesem Randpixel
                        int r = (int) Math.ceil(se / l1.zelle);
                        for (int b = -r; b <= r; b++)
                            for (int a = -r; a <= r; a++) {
                                int uu = u + a, vv = v + b;
                                if (uu < 0 || vv < 0 || uu >= w || vv >= w) continue;
                                int q = vv * w + uu, d2 = a * a + b * b;
                                if (Math.sqrt(d2) * l1.zelle < se && Float.isNaN(lw[q])) {
                                    imSaum[q] = true;
                                    if (lw[p] < minZ[q]) minZ[q] = lw[p];
                                    if (lw[p] > maxZ[q]) maxZ[q] = lw[p];
                                    if (delta < minD[q]) minD[q] = (float) delta;
                                    if (delta > maxD[q]) maxD[q] = (float) delta;
                                }
                            }
                    }
                // Pixel des Grunds mit Land oder Saum in der Kachel
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        int p = (j + m) * w + i + m;
                        if (!Float.isNaN(lw[p]) || imSaum[p]) {
                            double x = l1.ost(tx * n + i), y = l1.nord(ty * n + j);
                            aenderbar.add(Fuellwert.schluessel((int) Math.round(grund.spalte(x)), (int) Math.round(grund.zeile(y))));
                        }
                    }
                // Prüfstein 6: Stufen im Saum
                for (int j = 0; j < n; j++)
                    for (int i = 0; i < n; i++) {
                        int p = (j + m) * w + i + m;
                        for (int d = 0; d < 2; d++) {
                            int i2 = i + (d == 0 ? 1 : 0), j2 = j + (d == 1 ? 1 : 0);
                            if (i2 >= n || j2 >= n) continue;
                            int q = (j2 + m) * w + i2 + m;
                            if (!Float.isNaN(lw[p]) || !Float.isNaN(lw[q]) || !(imSaum[p] || imSaum[q])) continue;
                            float a = t[j * n + i], b = t[j2 * n + i2];
                            if (Float.isNaN(a) || Float.isNaN(b)) continue;
                            paare++;
                            double st = Math.abs(a - b);
                            double gr = Math.abs(vorher.bilinear(l1.ost(tx * n + i), l1.nord(ty * n + j)) - vorher.bilinear(l1.ost(tx * n + i2), l1.nord(ty * n + j2)));
                            double spanne = Math.max(Math.max(maxZ[p], maxZ[q]) - Math.min(minZ[p], minZ[q]), Math.max(maxD[p], maxD[q]) - Math.min(minD[p], minD[q]));
                            boolean gleich = !(spanne > GLEICH);
                            double grenze = gleich ? STUFE + gr : Math.max(STUFE + gr, spanne);
                            if (!gleich) { paareUngleich++; groessteUngleich = Math.max(groessteUngleich, st); }
                            if (st > STUFE) stufenSaum++;
                            if (st > grenze + 1e-6) {
                                ueber++;
                                if (st - grenze > groesste) {
                                    groesste = st - grenze;
                                    groessteOrt = f("O %.1f N %.1f", 0.5 * (l1.ost(tx * n + i) + l1.ost(tx * n + i2)), 0.5 * (l1.nord(ty * n + j) + l1.nord(ty * n + j2)));
                                }
                                if (Boolean.getBoolean("p3.zeige") && ueber <= 60)
                                    System.out.printf("  6: O %.1f N %.1f: %.3f / %.3f, Grund %.3f, Stufe %.3f > %.3f; Ränder z %.2f…%.2f, Δ %.2f…%.2f%n", l1.ost(tx * n + i), l1.nord(ty * n + j),
                                            a, b, vorher.bilinear(l1.ost(tx * n + i), l1.nord(ty * n + j)), st, grenze, Math.min(minZ[p], minZ[q]), Math.max(maxZ[p], maxZ[q]),
                                            Math.min(minD[p], minD[q]), Math.max(maxD[p], maxD[q]));
                            }
                        }
                    }
            }
        A.pruefe(ueber == 0 && paare > 0, f("Prüfstein 6 (%s): Stufe zwischen Nachbarpixeln im Saum ≤ %.2f m [G] plus Stufe des Grunds, wo die Randpixel, die das Paar erreichen, auf %.1f m [G] gleich hoch "
                        + "sind und gleich weit über dem Grund liegen; sonst höchstens der größere der beiden Unterschiede", wo, STUFE, GLEICH),
                f("%d Paare, %d über der Grenze%s; %d Paare an Rändern verschiedener Höhe oder verschiedenen Abstands zum Grund (Stufe dort bis %.2f m); %d Paare über %.2f m", paare, ueber,
                        ueber > 0 ? f(" (bis %.3f m darüber, bei %s)", groesste, groessteOrt) : "", paareUngleich, groessteUngleich, stufenSaum, STUFE), "0 über der Grenze");
        // Prüfstein 5: kein Füllwert mehr unter den Pixeln, die als gemessen gelten (im Stapel gleich dem DGM1 vor dem Füllwert)
        Map<Long, float[]> gemessen = new HashMap<>();
        long gemessenePixel = 0;
        for (Map.Entry<Long, float[]> e : stufe.entrySet()) {
            int tx = Fuellwert.spalte(e.getKey()), tyS = Fuellwert.zeile(e.getKey()), ty = l1.kachelnNord - 1 - tyS;
            float[] t = e.getValue(), g = new float[n * n];
            Arrays.fill(g, Float.NaN);
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    float v = t[j * n + i];
                    if (Float.isNaN(v)) continue;
                    double x = l1.ost(tx * n + i), y = l1.nord(ty * n + j);
                    int ek = (int) Math.floor(x / k), nk = (int) Math.floor(y / k);
                    float[] q = roh.get(Fuellwert.schluessel(ek, nk));
                    if (q == null) continue;
                    float r = q[(n - 1 - (int) Math.floor((y - nk * k) / l1.zelle)) * n + (int) Math.floor((x - ek * k) / l1.zelle)];
                    if (Float.compare(r, v) == 0) { g[j * n + i] = v; gemessenePixel++; }
                }
            gemessen.put(e.getKey(), g);
        }
        Fuellwert.Ergebnis fe = new Fuellwert(1.30, 0.002, 100).entferne(gemessen, n, l1.zelle);
        fuellPixel = fe.pixel();
        Fuellwert.Ergebnis fs = new Fuellwert(1.30, 0.002, 100).entferne(stufe, n, l1.zelle);
        A.pruefe(fuellPixel == 0, f("Prüfstein 5 (%s): zusammenhängende Flächen mit 1,30 ± 0,002 m ab 100 m² unter den gemessenen Pixeln der Landstufe (gleich dem DGM1 vor dem Füllwert)", wo),
                f("%d Flächen, %d Pixel (von %d gemessenen); in der ganzen Stufe mit Saum und Hof %d Flächen, %d Pixel (Höhenlinie 1,30 m im Saum)", fe.flaechen(), fuellPixel, gemessenePixel,
                        fs.flaechen(), fs.pixel()), "keine");
        // Prüfstein 3: Grund gleich vorher, wo kein Land und kein Saum ist; Abtastung in 1 m außerhalb der Landstufe gleich dem Grund vorher
        int ungleich = 0, g3 = 0;
        for (int i = 0; i < proben; i++) {
            int c = zufall.nextInt(grund.spalten()), r = zufall.nextInt(grund.zeilen());
            if (aenderbar.contains(Fuellwert.schluessel(c, r))) continue;
            float a = grund.wert(c, r), b = vorher.wert(c, r);
            if (Float.isNaN(a) && Float.isNaN(b)) continue;
            g3++;
            if (Float.compare(a, b) != 0) ungleich++;
        }
        Gelaende gel = new Gelaende(l1, grund);
        int aussen = 0, falschAussen = 0;
        double[] e = l1.ausdehnung();
        for (int i = 0; i < proben; i++) {
            int c = zufall.nextInt(l1.spalten()), r = zufall.nextInt(l1.zeilen());
            if (!Float.isNaN(l1.wert(c, r))) continue;
            double x = l1.ost(c), y = l1.nord(r);
            if (x < e[0] || y < e[1]) continue;
            float s = gel.hoehe(x, y, l1.zelle), v = vorher.bilinear(x, y);
            if (Float.isNaN(s) && Float.isNaN(v)) continue;
            aussen++;
            if (Float.compare(s, v) != 0) falschAussen++;
        }
        A.pruefe(ungleich == 0 && falschAussen == 0 && g3 > 0, f("Prüfstein 3 (%s): Grund ohne Land und Saum gleich der Quelle; Abtastung in 1 m außerhalb der Landstufe gleich dem Grund der Quelle", wo),
                f("%d von %d Pixeln des Grunds, %d von %d Punkten gleich", g3 - ungleich, g3, aussen - falschAussen, aussen), "bitgleich");
        // Prüfstein 7: Mittel nach oben
        double groessterFehler = 0;
        int mittel = 0, ungleichMittel = 0;
        float hoechstes = 0;
        for (long key : aenderbar) {
            int c = Fuellwert.spalte(key), r = Fuellwert.zeile(key);
            if (c < 0 || r < 0 || c >= grund.spalten() || r >= grund.zeilen()) continue;
            double gx = grund.ost(c), gy = grund.nord(r), summe = 0;
            int zahl = 0;
            for (int b = 0; b < f; b++)
                for (int a = 0; a < f; a++) {
                    double x = gx - grund.zelle / 2 + (a + 0.5) * l1.zelle, y = gy + grund.zelle / 2 - (b + 0.5) * l1.zelle;
                    float z = l1.wert((int) Math.round(l1.spalte(x)), (int) Math.round(l1.zeile(y)));
                    if (Float.isNaN(z)) z = vorher.bilinear(x, y);
                    if (!Float.isNaN(z)) { summe += z; zahl++; }
                }
            if (zahl == 0) continue;
            mittel++;
            float soll = (float) (summe / zahl), ist = grund.wert(c, r);
            if (Float.compare(soll, ist) != 0) ungleichMittel++;
            groessterFehler = Math.max(groessterFehler, Math.abs(ist - summe / zahl));
            hoechstes = Math.max(hoechstes, Math.abs(soll));
        }
        A.pruefe(ungleichMittel == 0 && mittel > 0, f("Prüfstein 7 (%s): Pixel des Grunds mit Land oder Saum tragen das Mittel des zusammengesetzten Felds, auf float gerundet", wo),
                f("%d Pixel, %d ungleich; Abstand zum Mittel in double bis %.2g m (Rundung auf float bei Höhen bis %.1f m)", mittel, ungleichMittel, groessterFehler, hoechstes), "gleich");
    }

    // ------------------------------------------------------------------ 5 Quellen

    /** Vergleichswerte aus tifffile 2025.5.10 und numpy (12.11.2026): Datei, Fenster, Band, Summe, Punkte (Spalte, Zeile, Wert). */
    record Referenz(String datei, int x, int y, int w, int h, int band, double summe, double[][] punkte) { }

    static final Referenz[] REFERENZEN = {
            new Referenz("bathymetrie/gebco/GEBCO_2025_schelf_30s.tif", 1900, 800, 300, 200, 0, -4803653.0, new double[][] {{2000, 850, -94.0}, {2199, 999, -45.0}, {1900, 900, -87.0}}),
            new Referenz("bathymetrie/emodnet/emodnet_mean_7.55_54.50.tif", 500, 300, 700, 600, 0, -1600552.401949676,
                    new double[][] {{733, 450, -11.789765357971191}, {1199, 899, -2.499225616455078}, {500, 600, -18.66512680053711}}),
            new Referenz("bathymetrie/easygsh_bathymetrie/2016/DGM_2016_10m_DB_Bathymetrie_466_6041.tif", 0, 200, 600, 700, 0, -1623994.3919269524,
                    new double[][] {{200, 375, -0.1915999948978424}, {599, 899, -4.255000114440918}, {0, 550, -1.9876999855041504}}),
            new Referenz("hoehe/copernicus_glo30/Copernicus_DSM_COG_10_N54_00_E008_00_DEM/Copernicus_DSM_COG_10_N54_00_E008_00_DEM.tif", 1000, 1500, 1300, 900, 0, 216874.3839602138,
                    new double[][] {{1433, 1725, -0.30280688405036926}, {2299, 2399, 1.0067715644836426}, {1000, 1950, 0.0}}),
            new Referenz("luftbild/sentinel2/S2B_32UMF_20260501_0_L2A/TCI.tif", 6000, 4500, 700, 600, 0, 15253443.0, new double[][] {{6233, 4650, 22}, {6699, 5099, 43}, {6000, 4800, 255}}),
            new Referenz("luftbild/sentinel2/S2B_32UMF_20260501_0_L2A/TCI.tif", 6000, 4500, 700, 600, 1, 22511227.0, new double[][] {{6233, 4650, 43}, {6699, 5099, 53}, {6000, 4800, 255}}),
            new Referenz("luftbild/sentinel2/S2B_32UMF_20260501_0_L2A/TCI.tif", 6000, 4500, 700, 600, 2, 18387824.0, new double[][] {{6233, 4650, 34}, {6699, 5099, 40}, {6000, 4800, 255}}),
            new Referenz("@ziel/quellen/topography_2021_ger.tif", 15000, 6000, 900, 700, 0, -2894605.2732054126,
                    new double[][] {{15300, 6175, -2.2181599140167236}, {15899, 6699, 1.546170949935913}, {15000, 6350, -12.99380111694336}}),
    };

    static void quellen(Path daten, Path ziel) throws IOException {
        for (Referenz r : REFERENZEN) {
            Path p = r.datei.startsWith("@ziel/") ? ziel.resolve(r.datei.substring(6)) : daten.resolve(r.datei);
            String name = p.getFileName() + (r.band > 0 || r.datei.contains("TCI") ? " Band " + r.band : "");
            if (!Files.isRegularFile(p)) {
                A.pruefe(false, name, "Datei fehlt: " + p, "vorhanden");
                continue;
            }
            try (GeoTiff t = GeoTiff.oeffne(p)) {
                float[] v = t.lies(r.x, r.y, r.w, r.h, r.band);
                double s = 0;
                for (float x : v) if (!Float.isNaN(x)) s += x;
                boolean punkte = true;
                for (double[] q : r.punkte) {
                    float a = v[((int) q[1] - r.y) * r.w + (int) q[0] - r.x];
                    if (!(a == (float) q[2] || Float.isNaN(a) && q[2] == 0 && !Double.isNaN(t.leer) && t.leer == 0)) punkte = false;
                }
                double rel = Math.abs(s - r.summe) / Math.max(1, Math.abs(r.summe));
                A.pruefe(punkte && rel < 1e-12, f("%s (%s, Kompression %d, Prädiktor %d, %s): Summe eines Fensters %d × %d und drei Punkte", name, t.ordnung == ByteOrder.BIG_ENDIAN ? "MM" : "II",
                        t.kompression, t.praediktor, t.gekachelt ? "Kacheln" : "Streifen", r.w, r.h), f("Summe %.6f, Punkte %s", s, punkte ? "gleich" : "FALSCH"), "gleich tifffile");
            }
        }
    }

    // ------------------------------------------------------------------ 6 Stapel des Gebiets

    static void gebiet(Rezept rz, Path daten, Path ziel, Path tmp) throws IOException {
        File st = ziel.resolve("gelaende").toFile();
        Gelaende.Stufe l1 = Gelaende.Stufe.lies(new File(st, rz.text("land.stufe", "1m"))), grund = Gelaende.Stufe.lies(new File(st, rz.text("grund.stufe", "10m")));
        double[] g2 = rz.zahlen("grund.gebiet", 4), g3 = rz.zahlen("land.gebiet", 4);
        String gd = rz.text("grund.datei");
        Path q = gd.contains("!") ? ziel.resolve("quellen").resolve(Path.of(gd.substring(gd.indexOf('!') + 1)).getFileName().toString()) : pfad(daten, gd);
        Gelaende.Stufe vorher;
        try (GeoTiff t = GeoTiff.oeffne(q)) {
            vorher = Stapel.grund(t, tmp.resolve("vorher").toFile(), g2[0], g2[1], g2[2], g2[3], grund.kachelZellen, "Grund vorher");
        }
        List<Dgm1.Kachel> kl = Dgm1.kacheln(pfad(daten, rz.text("land.ordner")), rz.ganze("land.jahre"), g3[0], g3[1], g3[2], g3[3]);
        Map<Long, float[]> land = new HashMap<>(), roh = new HashMap<>();
        for (Dgm1.Kachel k : kl) {
            float[] v = Dgm1.lies(k, null);
            land.put(Fuellwert.schluessel(k.ostKm(), k.nordKm()), v);
            roh.put(Fuellwert.schluessel(k.ostKm(), k.nordKm()), v.clone());
        }
        Fuellwert.Ergebnis fe = new Fuellwert(rz.zahl("land.fuellwert"), rz.zahl("land.toleranz"), rz.zahl("land.flaeche")).entferne(land, Dgm1.N, Dgm1.ZELLE);
        A.info("Gebiet", f("%s · %s; %d Kacheln Land, Füllwert %d Pixel", l1, grund, kl.size(), fe.pixel()));
        pruefeStapel(l1, grund, vorher, land, roh, rz.zahl("naht.saum"), rz.zahl("naht.saum.max"), rz.zahl("naht.steigung"), new Random(12), 10000, "Gebiet");
    }

    static Path pfad(Path daten, String p) {
        Path q = Path.of(p.replace('\\', File.separatorChar));
        return q.isAbsolute() ? q : daten.resolve(q);
    }

    static void loesche(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File x : l) loesche(x);
        f.delete();
    }
}
