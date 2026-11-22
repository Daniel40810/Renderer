package com.dan.aufbereitung;

import com.dan.core.gitter.Gelaende;
import com.dan.core.gitter.Kornfeld;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Der Boden für das Sediment aus der Sedimentologie von EasyGSH-DB (Werkbuch HalligHooge Teil A, 12.10, P9b; Schritt
 * {@code sediment}). Die Quelle ist ein ZIP je Jahr mit weiteren ZIP darin:
 * <ul>
 * <li>{@code …_D50_<Jahr>.zip} und {@code …_Porositaet_<Jahr>.zip}: je ein GeoTIFF zu 100 m in UTM 32 (d50 in mm). Sie
 * werden nach {@code quellen/sediment} entpackt und mit {@link GeoTiff} gelesen.</li>
 * <li>{@code …_csv_<Jahr>.zip}: eine CSV mit einer Zeile je Rasterpunkt, Trenner {@code ;}: {@code X}, {@code Y},
 * {@code d50} und die Summenkurve der Kornverteilung über φ von −10 bis 10 in Schritten von 0,25 (Anteil feiner als die
 * Korngröße). Der Schlickanteil ist der Wert bei φ = 4 (63 µm). Die CSV wird im Strom durch beide ZIP gelesen.</li>
 * </ul>
 * Das Raster des Ergebnisses ist das der Quelle (Mitten der Pixel auf vollen 100 m) über dem verlangten Gebiet. Wo die
 * Quelle keinen Wert hat (Land), kommt der Mittelwert der Nachbarn mit Wert, Ring um Ring bis {@code reichweite}
 * Pixel; was dann noch fehlt, bekommt die Vorgabe [G].
 */
public final class Korn {
    /** Was die Aufbereitung berichtet. */
    public record Ergebnis(Kornfeld feld, long zeilen, long imRaster, int[] gemessen, int[] gefuellt, int[] vorgabe, double[] median, double d50Abweichung, int d50Punkte,
                           List<String> quellen) { }

    /** Ein Strom, der sich nicht schließen lässt (das innere ZIP darf das äußere nicht schließen). */
    private static final class Offen extends FilterInputStream {
        Offen(InputStream in) { super(in); }
        @Override public void close() { }
    }

    /**
     * @param zip        die Sedimentologie eines Jahres ({@code EasyGSH_DB_Sedimentologie_<Jahr>.zip})
     * @param quellen    Ordner für die entpackten GeoTIFF
     * @param west       Gebiet in UTM 32 (m): Mitten der Pixel von west bis ost und von sued bis nord, beide einschließlich
     * @param vorgabe    {d50 (m), Porosität, Schlickanteil} für Pixel ohne Wert und ohne Nachbarn mit Wert [G]
     * @param reichweite so viele Ringe weit wird aus Nachbarn gefüllt
     */
    public static Ergebnis backe(Path zip, Path quellen, double west, double sued, double ost, double nord, double[] vorgabe, int reichweite) throws IOException {
        Files.createDirectories(quellen);
        Path[] tif = new Path[2];
        List<String> namen = new ArrayList<>();
        float[] schlick = null, d50Csv = null;
        long zeilen = 0, drin = 0;
        int nx = 0, ny = 0;
        double x0 = 0, y0 = 0, zelle = 0;
        float[][] w = new float[3][];
        // Erster Durchgang: die GeoTIFF entpacken (sie liegen im ZIP vor der CSV)
        try (ZipInputStream aussen = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = aussen.getNextEntry()) != null; ) {
                String n = e.getName().toLowerCase(Locale.ROOT);
                int k = n.contains("_d50_") ? 0 : n.contains("porositaet") ? 1 : -1;
                if (k < 0 || !n.endsWith(".zip")) continue;
                ZipInputStream innen = new ZipInputStream(new Offen(aussen));
                for (ZipEntry i; (i = innen.getNextEntry()) != null; ) {
                    if (!i.getName().toLowerCase(Locale.ROOT).endsWith(".tif")) continue;
                    tif[k] = quellen.resolve(Path.of(i.getName()).getFileName().toString());
                    Files.copy(innen, tif[k], StandardCopyOption.REPLACE_EXISTING);
                    namen.add(e.getName() + " → " + i.getName());
                }
            }
        }
        if (tif[0] == null || tif[1] == null) throw new IOException(zip + ": GeoTIFF für d50 oder Porosität nicht gefunden");
        for (int k = 0; k < 2; k++) {
            try (GeoTiff q = GeoTiff.oeffne(tif[k])) {
                if (q.epsg != 0 && q.epsg != 25832 && q.epsg != 32632) throw new IOException(tif[k] + ": EPSG " + q.epsg + " statt 25832");
                if (q.dx <= 0 || q.dy >= 0 || Math.abs(q.dx + q.dy) > 1e-9 * q.dx) throw new IOException(tif[k] + ": quadratische Pixel, Norden oben erwartet");
                double d = q.dx;
                int c0 = (int) Math.floor((west - q.x0) / d + 1e-9), c1 = (int) Math.ceil((ost - q.x0) / d - 1e-9);
                int r0 = (int) Math.floor((q.y0 - nord) / d + 1e-9), r1 = (int) Math.ceil((q.y0 - sued) / d - 1e-9);
                int bx = c1 - c0, by = r1 - r0;
                double ex = q.x0 + c0 * d, ey = q.y0 - r1 * d;
                if (k == 0) { nx = bx; ny = by; x0 = ex; y0 = ey; zelle = d; }
                else if (bx != nx || by != ny || Math.abs(ex - x0) > 1e-6 || Math.abs(ey - y0) > 1e-6) throw new IOException(tif[k] + ": anderes Raster als d50");
                float[] oben = q.lies(c0, r0, nx, ny);
                float[] f = new float[nx * ny];
                double mal = k == 0 ? 1e-3 : 1;                                   // d50 steht in mm
                for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                    float v = oben[(ny - 1 - j) * nx + i];
                    boolean gut = !Float.isNaN(v) && v > 0 && (k == 0 ? v < 100 : v < 1);
                    f[j * nx + i] = gut ? (float) (v * mal) : Float.NaN;
                }
                w[k] = f;
            }
        }
        schlick = new float[nx * ny];
        d50Csv = new float[nx * ny];
        Arrays.fill(schlick, Float.NaN);
        Arrays.fill(d50Csv, Float.NaN);
        // Zweiter Durchgang: die CSV im Strom
        boolean gefunden = false;
        try (ZipInputStream aussen = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = aussen.getNextEntry()) != null; ) {
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("csv") || !n.endsWith(".zip")) continue;
                ZipInputStream innen = new ZipInputStream(new Offen(aussen));
                for (ZipEntry i; (i = innen.getNextEntry()) != null; ) {
                    if (!i.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) continue;
                    namen.add(e.getName() + " → " + i.getName());
                    gefunden = true;
                    BufferedReader r = new BufferedReader(new InputStreamReader(innen, StandardCharsets.ISO_8859_1), 1 << 20);
                    String kopf = r.readLine();
                    if (kopf == null) throw new IOException(i.getName() + ": leer");
                    String[] sp = kopf.split(";");
                    int sx = -1, sy = -1, sd = -1, s4 = -1;
                    for (int c = 0; c < sp.length; c++) {
                        String t = sp[c].strip();
                        if (t.equals("X")) sx = c;
                        else if (t.equals("Y")) sy = c;
                        else if (t.equals("d50")) sd = c;
                        else if (t.equals("4.0")) s4 = c;
                    }
                    if (sx < 0 || sy < 0 || sd < 0 || s4 < 0 || !(sx < sy && sy < sd && sd < s4)) throw new IOException(i.getName() + ": Spalten X, Y, d50, 4.0 nicht gefunden: " + kopf.substring(0, Math.min(200, kopf.length())));
                    double xm = x0 + 0.5 * zelle, ym = y0 + 0.5 * zelle;
                    for (String z; (z = r.readLine()) != null; ) {
                        zeilen++;
                        // nur so weit zerlegen wie nötig: erst X und Y, die Verteilung nur für Punkte im Raster
                        int p = 0;
                        for (int c = 0; c < sx; c++) p = z.indexOf(';', p) + 1;
                        int e1 = z.indexOf(';', p);
                        if (p <= 0 && sx > 0 || e1 < 0) continue;
                        double x = Double.parseDouble(z.substring(p, e1).strip());
                        double fi = (x - xm) / zelle;
                        int ii = (int) Math.round(fi);
                        if (ii < 0 || ii >= nx || Math.abs(fi - ii) > 0.01) continue;
                        p = e1 + 1;
                        for (int c = sx + 1; c < sy; c++) p = z.indexOf(';', p) + 1;
                        e1 = z.indexOf(';', p);
                        double y = Double.parseDouble(z.substring(p, e1).strip());
                        double fj = (y - ym) / zelle;
                        int jj = (int) Math.round(fj);
                        if (jj < 0 || jj >= ny || Math.abs(fj - jj) > 0.01) continue;
                        p = e1 + 1;
                        for (int c = sy + 1; c < sd; c++) p = z.indexOf(';', p) + 1;
                        e1 = z.indexOf(';', p);
                        double d = Double.parseDouble(z.substring(p, e1).strip());
                        p = e1 + 1;
                        for (int c = sd + 1; c < s4; c++) p = z.indexOf(';', p) + 1;
                        e1 = z.indexOf(';', p);
                        double a = Double.parseDouble((e1 < 0 ? z.substring(p) : z.substring(p, e1)).strip());
                        if (!(a >= 0 && a <= 1.0001)) continue;
                        drin++;
                        schlick[jj * nx + ii] = (float) Math.min(1, a);
                        if (d > 0) d50Csv[jj * nx + ii] = (float) (d * 1e-3);
                    }
                }
            }
        }
        if (!gefunden) throw new IOException(zip + ": die CSV der Kornverteilung nicht gefunden");
        w[2] = schlick;
        // Probe der Lage: d50 der CSV gegen das GeoTIFF am selben Pixel
        List<Double> ab = new ArrayList<>();
        for (int c = 0; c < nx * ny; c++) if (!Float.isNaN(d50Csv[c]) && !Float.isNaN(w[0][c])) ab.add(Math.abs(d50Csv[c] - w[0][c]) / (double) w[0][c]);
        ab.sort(null);
        double abw = ab.isEmpty() ? Double.NaN : ab.get(ab.size() / 2);
        int[] gemessen = new int[3], gefuellt = new int[3], vor = new int[3];
        double[] median = new double[3];
        for (int k = 0; k < 3; k++) {
            float[] f = w[k];
            List<Float> werte = new ArrayList<>();
            for (float v : f) if (!Float.isNaN(v)) werte.add(v);
            gemessen[k] = werte.size();
            werte.sort(null);
            median[k] = werte.isEmpty() ? Double.NaN : werte.get(werte.size() / 2);
            for (int ring = 0; ring < reichweite; ring++) {
                float[] neu = f.clone();
                int n = 0;
                for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
                    if (!Float.isNaN(f[j * nx + i])) continue;
                    double s = 0;
                    int m = 0;
                    for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                        int a = i + di, b = j + dj;
                        if (a < 0 || b < 0 || a >= nx || b >= ny) continue;
                        float v = f[b * nx + a];
                        if (!Float.isNaN(v)) { s += v; m++; }
                    }
                    if (m > 0) { neu[j * nx + i] = (float) (s / m); n++; }
                }
                System.arraycopy(neu, 0, f, 0, f.length);
                gefuellt[k] += n;
                if (n == 0) break;
            }
            for (int c = 0; c < f.length; c++) if (Float.isNaN(f[c])) { f[c] = (float) vorgabe[k]; vor[k]++; }
        }
        return new Ergebnis(new Kornfeld(x0, y0, zelle, nx, ny, new float[][] {w[0], w[1], w[2], new float[nx * ny]}), zeilen, drin, gemessen, gefuellt, vor, median, abw, ab.size(), namen);
    }

    /**
     * Füllt den Anteil festen Bodens je Pixel aus den Rauheitsklassen (Werkbuch 10.4, Schritt 6): je Pixel 10 × 10 Punkte,
     * die Klasse der Stufe 1 m, sonst der Stufe 10 m; fest sind Fenne, Salzwiese, Deich, Warft und Bebauung, Deckwerk und
     * Lahnung [G]. Gibt {Pixel mit festem Boden über der Hälfte, Pixel mit etwas festem Boden, Punkte ohne Klasse} zurück.
     */
    public static long[] fest(Kornfeld k, Gelaende.Stufe r1, Gelaende.Stufe r10) {
        final int n = 10;
        long[] zahl = new long[3];
        long[][] je = new long[k.ny][3];
        java.util.stream.IntStream.range(0, k.ny).parallel().forEach(j -> {
            Gelaende.Stufe.Leser l1 = r1 == null ? null : r1.leser(), l10 = r10 == null ? null : r10.leser();
            for (int i = 0; i < k.nx; i++) {
                int f = 0, ohne = 0;
                for (int b = 0; b < n; b++) for (int a = 0; a < n; a++) {
                    double x = k.x0 + (i + (a + 0.5) / n) * k.zelle, y = k.y0 + (j + (b + 0.5) / n) * k.zelle;
                    float q = l1 == null ? Float.NaN : l1.imPixel(x, y);
                    if (Float.isNaN(q) && l10 != null) q = l10.imPixel(x, y);
                    if (Float.isNaN(q)) { ohne++; continue; }
                    int c = Math.round(q);
                    if (c == Rauheit.FENNE || c == Rauheit.SALZWIESE || c == Rauheit.DEICH || c == Rauheit.WARFT || c == Rauheit.DECKWERK) f++;
                }
                float w = f / (float) (n * n);
                k.werte[Kornfeld.FEST][i + k.nx * j] = w;
                if (w > 0.5f) je[j][0]++;
                if (w > 0) je[j][1]++;
                je[j][2] += ohne;
            }
        });
        for (long[] z : je) for (int q = 0; q < 3; q++) zahl[q] += z[q];
        return zahl;
    }
}
