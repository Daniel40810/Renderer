package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Gitter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Rauheitsklassen je Pixel (Werkbuch HalligHooge Teil A, 10.4, Schritt 6) im Kachelformat von {@link Gelaende}, die
 * Nummer der Klasse als Wert: eine Stufe 1 m über den Kacheln der Landstufe und eine Stufe 10 m über dem Grund.
 * Manning-n je Klasse steht in {@code klassen.txt} [G]. Die Klassen werden durch {@code surf.z0} abgelöst, sobald
 * sediment und vegetation rechnen.
 * <p>
 * <b>Woher (1 m), in dieser Reihenfolge:</b>
 * <ol>
 * <li>Folien der DTK5, wo es Blätter gibt (je Pixel 4 × 4 Bildpunkte, Mehrheit [G]): Siedlung und Gebäude → Warft und
 * Bebauung; See blau mit Watt → Watt, ohne → Wasser; blaue Linien → Wasser (Gräben); Wiese → Grünland.</li>
 * <li>Flächen aus OSM, wo noch nichts steht: Gebäude, Wohnen, Hof → Warft und Bebauung; Wasser → Wasser; Sand, Strand,
 * Sandbank → Sand; Watt, Schlick → Watt; Salzwiese, Wiese, Gras, Acker → Grünland. Gräben und Bäche als
 * Linien mit Breite [G] → Wasser.</li>
 * <li>Was bleibt: gemessenes Land (DGM1 ohne Füllwert) → Grünland; sonst nach der Höhe: unter {@code wasserUnter}
 * Wasser, ab {@code sandUeber} Sand, dazwischen Watt [G].</li>
 * <li>Grünland in einem Deichring (Bauwerke) → Fenne, sonst Salzwiese.</li>
 * <li>Im Streifen ±{@code deichBreite} um die Deichlinien → Deich (über Grünland und Sand an Land); Lahnungen und
 * Buhnen aus OSM ±{@code lahnungBreite} → Deckwerk und Lahnung (über Watt, Sand, Wasser).</li>
 * </ol>
 * <b>10 m:</b> wo die 1-m-Stufe das Pixel trägt, die häufigste Klasse darin; sonst Flächen aus OSM und die Höhe des
 * Grunds wie oben.
 */
public final class Rauheit {
    public static final int WATT = 1, SAND = 2, FENNE = 3, SALZWIESE = 4, DEICH = 5, WARFT = 6, WASSER = 7, DECKWERK = 8;
    static final int GRUENLAND = 100;
    public static final String[] NAMEN = {"", "Watt", "Sand", "Fenne", "Salzwiese", "Deich", "Warft und Bebauung", "Wasser", "Deckwerk und Lahnung"};
    /** Manning-n je Klasse [G], Größenordnung nach Chow 1959 (Werkbuch 10.4, Schritt 6). */
    public double[] manning = {Double.NaN, 0.020, 0.022, 0.030, 0.035, 0.030, 0.060, 0.025, 0.035};
    /** Grenzen der Höhe (m NHN) und Breiten (m) [G]. */
    public double wasserUnter = -2.0, sandUeber = 1.0, deichBreite = 8, lahnungBreite = 1.5, grabenBreite = 2, bachBreite = 4, prielBreite = 10;

    /** OSM nach Klassen (Reihenfolge = Vorrang). */
    static final String[][] FLAECHEN = {
            {"6", "building=*; landuse=residential|farmyard|commercial|industrial|cemetery; amenity=*"},
            {"7", "natural=water; water=*; waterway=riverbank|dock; landuse=basin|reservoir"},
            {"2", "natural=sand|beach|shoal|dune"},
            {"1", "wetland=tidalflat; natural=mud"},
            {"100", "wetland=saltmarsh|marsh|wet_meadow; landuse=meadow|grass|farmland|village_green; natural=grassland|heath|scrub|wetland; leisure=park|pitch|playground"}};

    private final Gelaende gelaende;
    private final Gelaende.Stufe pixel, grund;
    private final Map<Long, float[]> land;
    private final Bauwerke bauwerke;
    private final Osm osm;
    private final Path dtk5;
    private final List<Osm.Flaeche>[] flaechen;
    private final List<double[][]> wasserLinien = new ArrayList<>(), lahnungen = new ArrayList<>();
    private final double[] wasserBreite;
    private final Map<Long, String> blaetter = new HashMap<>();
    /** Pixel je Klasse in der 1-m- und der 10-m-Stufe. */
    public final long[] zahl1 = new long[9], zahl10 = new long[9];
    public int kacheln1, kacheln10, kachelnDtk5;

    /**
     * @param land  gemessene Kacheln des Lands nach dem Füllwert (Schlüssel {@link Fuellwert#schluessel}, km), null: keine
     * @param dtk5  Ordner mit den Folien {@code dtk5_32EEE_NNNN_2_sh_<thema>.tif}, null: keine
     */
    public Rauheit(Gelaende gelaende, Map<Long, float[]> land, Bauwerke bauwerke, Osm osm, Path dtk5) throws IOException {
        this(gelaende, null, land, bauwerke, osm, dtk5);
    }

    /**
     * @param grundStufe Name der Stufe des Grunds (Rezept {@code grund.stufe}, etwa {@code 10m}); null: die zweitfeinste.
     *                   Nicht die gröbste: Im Stapel liegt seit P3d auch die Stufe 100 m ({@code grob}); bis P5b nahm die
     *                   Rauheit sie, wenn der Schritt {@code rauheit} nach einem früheren {@code grob} lief (Werkbuch 12.6,
     *                   Stand P5b, Abnahme).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Rauheit(Gelaende gelaende, String grundStufe, Map<Long, float[]> land, Bauwerke bauwerke, Osm osm, Path dtk5) throws IOException {
        this.gelaende = gelaende;
        Gelaende.Stufe[] st = gelaende.stufen();
        this.pixel = st[0];
        Gelaende.Stufe g = st.length > 1 ? st[1] : st[0];
        if (grundStufe != null) {
            g = null;
            for (Gelaende.Stufe x : st) if (x.name.equals(grundStufe)) g = x;
            if (g == null) throw new IOException("Rauheit: keine Stufe " + grundStufe + " im Stapel");
        }
        this.grund = g;
        this.land = land == null ? Map.of() : land;
        this.bauwerke = bauwerke;
        this.osm = osm;
        this.dtk5 = dtk5;
        flaechen = new List[FLAECHEN.length];
        for (int i = 0; i < FLAECHEN.length; i++) {
            flaechen[i] = new ArrayList<>();
            Osm.Filter f = Osm.Filter.lies(FLAECHEN[i][1]);
            if (osm != null) for (Osm.Flaeche q : osm.flaechen) if (f.passt(q.tags())) flaechen[i].add(q);
        }
        List<Double> br = new ArrayList<>();
        if (osm != null)
            for (Osm.Weg w : osm.wege) {
                String ww = w.tags().get("waterway"), mm = w.tags().get("man_made");
                if (w.tags().containsKey("tunnel")) continue;
                if ("ditch".equals(ww) || "drain".equals(ww)) { wasserLinien.add(new double[][] {w.ost(), w.nord()}); br.add(grabenBreite); }
                else if ("stream".equals(ww) || "canal".equals(ww)) { wasserLinien.add(new double[][] {w.ost(), w.nord()}); br.add(bachBreite); }
                else if ("river".equals(ww) || "tidal_channel".equals(ww)) { wasserLinien.add(new double[][] {w.ost(), w.nord()}); br.add(prielBreite); }
                if ("groyne".equals(mm) || "breakwater".equals(mm)) lahnungen.add(new double[][] {w.ost(), w.nord()});
            }
        wasserBreite = br.stream().mapToDouble(Double::doubleValue).toArray();
        if (dtk5 != null && Files.isDirectory(dtk5))
            try (var s = Files.list(dtk5)) {
                s.map(p -> p.getFileName().toString()).filter(n -> n.matches("dtk5_32\\d{3}_\\d{4}_2_sh_wies\\.tif")).forEach(n -> {
                    int e = Integer.parseInt(n.substring(7, 10)), nn = Integer.parseInt(n.substring(11, 15));
                    blaetter.put(Fuellwert.schluessel(e, nn), n.substring(0, n.length() - 9));
                });
            }
    }

    /** Baut beide Stufen nach {@code ordner/1m} und {@code ordner/10m} und schreibt {@code klassen.txt}. */
    public void bauen(File ordner) throws IOException {
        File o1 = new File(ordner, "1m"), o10 = new File(ordner, "10m");
        Stapel.leere(o1);
        Stapel.leere(o10);
        Gelaende.Stufe r1 = Gelaende.Stufe.anlegen(o1, pixel.zelle, pixel.kachelZellen, pixel.suedwestOst, pixel.suedwestNord, pixel.kachelnOst, pixel.kachelnNord,
                "Rauheitsklassen 1 m (Werkbuch HalligHooge Teil A, 10.4, Schritt 6); Klassen in ../klassen.txt");
        Gelaende.Stufe r10 = Gelaende.Stufe.anlegen(o10, grund.zelle, grund.kachelZellen, grund.suedwestOst, grund.suedwestNord, grund.kachelnOst, grund.kachelnNord,
                "Rauheitsklassen 10 m (Werkbuch HalligHooge Teil A, 10.4, Schritt 6); Klassen in ../klassen.txt");
        Map<Long, byte[]> fein = new HashMap<>();
        for (int ty = 0; ty < pixel.kachelnNord; ty++)
            for (int tx = 0; tx < pixel.kachelnOst; tx++) {
                if (!pixel.hat(tx, ty)) continue;
                byte[] k = kachel1(tx, ty);
                fein.put(((long) tx << 32) | ty, k);
                float[] v = new float[k.length];
                for (int p = 0; p < k.length; p++) {
                    v[p] = k[p] == 0 ? Float.NaN : k[p];
                    zahl1[k[p]]++;
                }
                r1.schreibe(tx, ty, v);
                kacheln1++;
            }
        for (int ty = 0; ty < grund.kachelnNord; ty++)
            for (int tx = 0; tx < grund.kachelnOst; tx++) {
                if (!grund.hat(tx, ty)) continue;
                float[] v = kachel10(tx, ty, fein);
                for (float x : v) if (!Float.isNaN(x)) zahl10[(int) x]++;
                r10.schreibe(tx, ty, v);
                kacheln10++;
            }
        StringBuilder b = new StringBuilder("# Rauheitsklassen (Werkbuch HalligHooge Teil A, 10.4, Schritt 6): Nummer, Manning-n (s/m^(1/3)) [G], Name\n");
        for (int c = 1; c < NAMEN.length; c++) b.append(String.format(Locale.ROOT, "%d %.3f %s\n", c, manning[c], NAMEN[c]));
        Files.writeString(new File(ordner, "klassen.txt").toPath(), b.toString(), StandardCharsets.UTF_8);
    }

    /** Klassen einer Kachel der 1-m-Stufe. */
    byte[] kachel1(int tx, int ty) throws IOException {
        int n = pixel.kachelZellen;
        double z0 = pixel.zelle, x0 = pixel.suedwestOst + tx * pixel.kachel(), yN = pixel.suedwestNord + (pixel.kachelnNord - ty) * pixel.kachel();
        float[] zt = pixel.kachel(tx, ty);
        byte[] k = new byte[n * n];
        boolean[] istLand = new boolean[n * n];
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++) {
                double x = x0 + (c + 0.5) * z0, y = yN - (r + 0.5) * z0;
                istLand[r * n + c] = !Float.isNaN(landwert(x, y));
            }
        // 1. DTK5
        if (dtk5(x0, yN, z0, n, k)) kachelnDtk5++;
        // 2. Flächen und Linien aus OSM
        for (int i = 0; i < FLAECHEN.length; i++) {
            byte w = (byte) Integer.parseInt(FLAECHEN[i][0]);
            for (Osm.Flaeche f : flaechen[i]) Raster.flaeche(f, x0, yN, z0, n, n, k, w, k);
        }
        for (int i = 0; i < wasserLinien.size(); i++)
            Raster.linie(wasserLinien.get(i)[0], wasserLinien.get(i)[1], wasserBreite[i] / 2, x0, yN, z0, n, n, k, (byte) WASSER,
                    p -> istLand[p] && (k[p] == 0 || k[p] == (byte) GRUENLAND));
        // 3. was bleibt; 4. Grünland nach dem Ring
        Gitter g = bauwerke == null ? null : bauwerke.gitter;
        IntStream.range(0, n).parallel().forEach(r -> {
            for (int c = 0; c < n; c++) {
                int p = r * n + c;
                double x = x0 + (c + 0.5) * z0, y = yN - (r + 0.5) * z0;
                if (k[p] == 0) {
                    if (istLand[p]) k[p] = (byte) GRUENLAND;
                    else {
                        float z = zt.length > 0 && !Float.isNaN(zt[p]) ? zt[p] : gelaende.hoehe(x, y, z0);
                        k[p] = Float.isNaN(z) ? 0 : (byte) (z < wasserUnter ? WASSER : z >= sandUeber ? SAND : WATT);
                    }
                }
                if (k[p] == (byte) GRUENLAND) {
                    boolean imRing = g != null && g.enthaelt(x, y) && bauwerke.ring[g.index(g.i(x), g.j(y))] > 0;
                    k[p] = (byte) (imRing ? FENNE : SALZWIESE);
                }
            }
        });
        // 5. Deich und Lahnung
        if (bauwerke != null)
            for (Bauwerke.Linie L : bauwerke.linien)
                Raster.linie(L.x(), L.y(), deichBreite, x0, yN, z0, n, n, k, (byte) DEICH, p -> {
                    if (k[p] == FENNE || k[p] == SALZWIESE) return true;
                    if (k[p] != SAND) return false;
                    return istLand[p] || zt.length > 0 && zt[p] >= sandUeber;
                });
        for (double[][] l : lahnungen)
            Raster.linie(l[0], l[1], lahnungBreite, x0, yN, z0, n, n, k, (byte) DECKWERK, p -> k[p] == WATT || k[p] == SAND || k[p] == WASSER);
        return k;
    }

    /** Gemessenes Land unter einem Punkt (NaN: keins). */
    private float landwert(double x, double y) {
        int e = (int) Math.floor(x / 1000), nn = (int) Math.floor(y / 1000);
        float[] t = land.get(Fuellwert.schluessel(e, nn));
        if (t == null) return Float.NaN;
        int n = (int) Math.round(Math.sqrt(t.length));
        double z = 1000.0 / n;
        int c = (int) Math.floor((x - e * 1000) / z), r = n - 1 - (int) Math.floor((y - nn * 1000) / z);
        return t[r * n + c];
    }

    /** Klassen aus den Folien der DTK5, wo es ein Blatt gibt; true, wenn eins da war. */
    private boolean dtk5(double x0, double yN, double z0, int n, byte[] k) throws IOException {
        int e = (int) Math.floor(x0 / 2000) * 2, nn = (int) Math.floor((yN - z0 * n) / 2000) * 2;
        String blatt = blaetter.get(Fuellwert.schluessel(e, nn));
        if (blatt == null) return false;
        String[] themen = {"hrot", "wige", "woge", "sebl", "watt", "babl", "wies"};
        byte[][] zahl = new byte[themen.length][n * n];
        for (int f = 0; f < themen.length; f++) {
            Path p = dtk5.resolve(blatt + "_" + themen[f] + ".tif");
            if (!Files.isRegularFile(p)) continue;
            try (GeoTiff t = GeoTiff.oeffne(p)) {
                int u = (int) Math.round(z0 / Math.abs(t.dx));                                                   // Bildpunkte je Pixel
                int c0 = (int) Math.round((x0 - t.x0) / t.dx), r0 = (int) Math.round((yN - t.y0) / t.dy);
                boolean eins = t.photometrie == 0;                                                               // 1 = schwarz
                int band = 100;
                for (int rb = 0; rb < n; rb += band) {
                    int hb = Math.min(band, n - rb);
                    float[] v = t.lies(c0, r0 + rb * u, n * u, hb * u);
                    for (int rr = 0; rr < hb * u; rr++)
                        for (int cc = 0; cc < n * u; cc++) {
                            float a = v[rr * n * u + cc];
                            if (Float.isNaN(a)) continue;
                            if ((a == 1) == eins) zahl[f][(rb + rr / u) * n + cc / u]++;
                        }
                }
            }
        }
        for (int p = 0; p < n * n; p++) {
            if (zahl[0][p] >= 8 || zahl[1][p] >= 8 || zahl[2][p] >= 8) k[p] = WARFT;
            else if (zahl[3][p] >= 8) k[p] = (byte) (zahl[4][p] >= 8 ? WATT : WASSER);
            else if (zahl[5][p] >= 4) k[p] = WASSER;
            else if (zahl[6][p] >= 8) k[p] = (byte) GRUENLAND;
        }
        return true;
    }

    /** Klassen einer Kachel der 10-m-Stufe. */
    float[] kachel10(int tx, int ty, Map<Long, byte[]> fein) {
        int n = grund.kachelZellen;
        double z0 = grund.zelle, x0 = grund.suedwestOst + tx * grund.kachel(), yN = grund.suedwestNord + (grund.kachelnNord - ty) * grund.kachel();
        float[] zt = grund.kachel(tx, ty);
        byte[] k = new byte[n * n];
        for (int i = 0; i < FLAECHEN.length; i++) {
            byte w = (byte) Integer.parseInt(FLAECHEN[i][0]);
            for (Osm.Flaeche f : flaechen[i]) Raster.flaeche(f, x0, yN, z0, n, n, k, w, k);
        }
        Gitter g = bauwerke == null ? null : bauwerke.gitter;
        float[] v = new float[n * n];
        int m = pixel.kachelZellen, f = (int) Math.round(z0 / pixel.zelle);
        IntStream.range(0, n).parallel().forEach(r -> {
            int[] zahl = new int[9];
            for (int c = 0; c < n; c++) {
                int p = r * n + c;
                double x = x0 + (c + 0.5) * z0, y = yN - (r + 0.5) * z0;
                if (Float.isNaN(zt[p])) { v[p] = Float.NaN; continue; }
                // häufigste Klasse der 1-m-Stufe
                Arrays.fill(zahl, 0);
                int da = 0;
                for (int b = 0; b < f; b++)
                    for (int a = 0; a < f; a++) {
                        double xs = x - z0 / 2 + (a + 0.5) * pixel.zelle, ys = y + z0 / 2 - (b + 0.5) * pixel.zelle;
                        double sc = (xs - pixel.suedwestOst) / pixel.zelle, sr = (pixel.suedwestNord + pixel.kachelnNord * pixel.kachel() - ys) / pixel.zelle;
                        if (sc < 0 || sr < 0 || sc >= pixel.spalten() || sr >= pixel.zeilen()) continue;
                        int ci = (int) sc, ri = (int) sr;
                        byte[] t = fein.get(((long) (ci / m) << 32) | (ri / m));
                        if (t == null) continue;
                        int q = t[(ri % m) * m + ci % m];
                        if (q > 0) { zahl[q]++; da++; }
                    }
                if (2 * da >= f * f) {
                    int best = 1;
                    for (int q = 2; q < 9; q++) if (zahl[q] > zahl[best]) best = q;
                    v[p] = best;
                    continue;
                }
                int q = k[p] & 0xff;
                if (q == 0) q = zt[p] < wasserUnter ? WASSER : zt[p] >= sandUeber ? SAND : WATT;
                if (q == GRUENLAND) {
                    boolean imRing = g != null && g.enthaelt(x, y) && bauwerke.ring[g.index(g.i(x), g.j(y))] > 0;
                    q = imRing ? FENNE : SALZWIESE;
                }
                v[p] = q;
            }
        });
        return v;
    }

    /** Fläche je Klasse in km² als Text. */
    public String bericht() {
        StringBuilder b = new StringBuilder();
        for (int c = 1; c < NAMEN.length; c++)
            b.append(String.format(Locale.GERMAN, "%s %.2f / %.1f km²%s", NAMEN[c], zahl1[c] * pixel.zelle * pixel.zelle / 1e6, zahl10[c] * grund.zelle * grund.zelle / 1e6,
                    c + 1 < NAMEN.length ? "; " : ""));
        return b.toString();
    }
}
