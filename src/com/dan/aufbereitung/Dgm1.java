package com.dan.aufbereitung;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Die Kacheln des DGM1 Schleswig-Holstein (LVermGeo SH, CC BY 4.0): 1 × 1 km, 1 m, EPSG:25832, Höhen NHN, Name
 * {@code dgm1_32_<Ost km>_<Nord km>_1_sh_<Jahr>}. Gelesen wird die XYZ-Kachel des Massendownloads oder das GeoTIFF, das
 * {@code kartenmaterial.py dgm1-tif} daraus macht (im Unterordner {@code tif/}). Werkbuch HalligHooge Teil A, 10.3.
 */
public final class Dgm1 {
    /** Zellen je Kachelseite und Zellgröße (m). */
    public static final int N = 1000;
    public static final double ZELLE = 1.0;
    static final Pattern NAME = Pattern.compile("dgm1_32_(\\d{3})_(\\d{4})_1_sh_(\\d{4})\\.(xyz|tif)$");

    /** Eine Kachel: Südwestecke in km, Jahr der Befliegung, Datei. */
    public record Kachel(int ostKm, int nordKm, int jahr, Path datei) {
        public double ost0() { return ostKm * 1000.0; }
        public double nord0() { return nordKm * 1000.0; }
        public String name() { return String.format("dgm1_32_%d_%d_1_sh_%d", ostKm, nordKm, jahr); }
    }

    /** Was beim Lesen einer XYZ-Kachel auffiel. */
    public record Bericht(int punkte, int ausserhalb, int doppelt, int leer) {
        public boolean sauber() { return ausserhalb == 0 && doppelt == 0; }
    }

    private Dgm1() { }

    /**
     * Alle Kacheln unter {@code ordner} (auch in {@code tif/}), deren Jahr in {@code jahre} liegt (leer: alle) und die das
     * Rechteck [ost0, ost1) × [nord0, nord1) in m schneiden. Liegt eine Kachel als XYZ und als GeoTIFF vor, gilt das
     * GeoTIFF. Gibt es für denselben Ort zwei Jahre, gilt das jüngste.
     */
    public static List<Kachel> kacheln(Path ordner, Collection<Integer> jahre, double ost0, double nord0, double ost1, double nord1) throws IOException {
        Map<Long, Kachel> m = new TreeMap<>();
        if (!Files.isDirectory(ordner)) return new ArrayList<>();
        try (Stream<Path> s = Files.walk(ordner, 2)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Matcher x = NAME.matcher(p.getFileName().toString());
                if (!x.find()) continue;
                int e = Integer.parseInt(x.group(1)), n = Integer.parseInt(x.group(2)), j = Integer.parseInt(x.group(3));
                if (!jahre.isEmpty() && !jahre.contains(j)) continue;
                if ((e + 1) * 1000.0 <= ost0 || e * 1000.0 >= ost1 || (n + 1) * 1000.0 <= nord0 || n * 1000.0 >= nord1) continue;
                Kachel k = new Kachel(e, n, j, p);
                long key = (long) e * 100000 + n;
                Kachel alt = m.get(key);
                if (alt == null || alt.jahr < j || alt.jahr == j && p.toString().endsWith(".tif")) m.put(key, k);
            }
        }
        List<Kachel> l = new ArrayList<>(m.values());
        l.sort(Comparator.comparingInt(Kachel::nordKm).thenComparingInt(Kachel::ostKm));
        return l;
    }

    /** Liest eine Kachel: N × N Werte, Zeilen von Nord nach Süd, NaN ohne Punkt. */
    public static float[] lies(Kachel k, int[] bericht) throws IOException {
        if (k.datei.toString().endsWith(".tif")) {
            try (GeoTiff t = GeoTiff.oeffne(k.datei)) {
                if (Math.abs(t.dx - ZELLE) > 1e-9 || Math.abs(t.dy + ZELLE) > 1e-9) throw new IOException(k.datei + ": Zelle " + t.dx + " × " + t.dy + " statt 1 m");
                int c = (int) Math.round(t.spalte(k.ost0() + 0.5)), r = (int) Math.round(t.zeile(k.nord0() + N - 0.5));
                float[] v = t.lies(c, r, N, N);
                if (bericht != null) {
                    int leer = 0;
                    for (float f : v) if (Float.isNaN(f)) leer++;
                    bericht[0] = N * N - leer;
                    bericht[3] = leer;
                }
                return v;
            }
        }
        return xyz(Files.readAllBytes(k.datei), k.ost0(), k.nord0(), N, ZELLE, bericht);
    }

    /**
     * Liest XYZ-Text (je Zeile Ost, Nord, Höhe, getrennt durch Leerzeichen, Tabulator, Komma oder Semikolon) in ein Raster
     * mit der Südwestecke (ost0, nord0): n × n Zellen, Zeilen von Nord. {@code bericht}: Punkte, außerhalb, doppelt, leer.
     */
    public static float[] xyz(byte[] text, double ost0, double nord0, int n, double zelle, int[] bericht) {
        float[] v = new float[n * n];
        java.util.Arrays.fill(v, Float.NaN);
        int punkte = 0, aussen = 0, doppelt = 0;
        double[] z = new double[3];
        int[] pos = {0};
        while (pos[0] < text.length) {
            int k = 0;
            while (k < 3 && pos[0] < text.length) {
                if (!zahl(text, pos, z, k)) break;
                k++;
            }
            while (pos[0] < text.length && text[pos[0]] != '\n') pos[0]++;                    // Rest der Zeile
            pos[0]++;
            if (k < 3) continue;
            punkte++;
            double fi = (z[0] - ost0) / zelle - 0.5, fj = (nord0 + n * zelle - z[1]) / zelle - 0.5;
            int i = (int) Math.round(fi), j = (int) Math.round(fj);
            if (i < 0 || j < 0 || i >= n || j >= n || Math.abs(fi - i) > 1e-3 || Math.abs(fj - j) > 1e-3) { aussen++; continue; }
            if (!Float.isNaN(v[j * n + i])) doppelt++;
            v[j * n + i] = (float) z[2];
        }
        if (bericht != null) {
            int leer = 0;
            for (float f : v) if (Float.isNaN(f)) leer++;
            bericht[0] = punkte;
            bericht[1] = aussen;
            bericht[2] = doppelt;
            bericht[3] = leer;
        }
        return v;
    }

    /** Liest eine Zahl ab pos (überspringt Trenner, nicht den Zeilenumbruch); false am Zeilenende. */
    private static boolean zahl(byte[] t, int[] pos, double[] z, int k) {
        int p = pos[0];
        while (p < t.length && (t[p] == ' ' || t[p] == '\t' || t[p] == ',' || t[p] == ';' || t[p] == '\r')) p++;
        if (p >= t.length || t[p] == '\n') { pos[0] = p; return false; }
        int start = p;
        boolean neg = false;
        if (t[p] == '-' || t[p] == '+') { neg = t[p] == '-'; p++; }
        long ganz = 0;
        int stellen = 0, nachkomma = 0;
        boolean punkt = false, einfach = true;
        while (p < t.length) {
            byte b = t[p];
            if (b >= '0' && b <= '9') {
                if (stellen < 17) { ganz = ganz * 10 + (b - '0'); stellen++; if (punkt) nachkomma++; }
                else if (!punkt) einfach = false;
                p++;
            } else if (b == '.' && !punkt) { punkt = true; p++; }
            else if (b == 'e' || b == 'E') { einfach = false; p++; if (p < t.length && (t[p] == '-' || t[p] == '+')) p++; }
            else break;
        }
        if (p == start) { pos[0] = p + 1; return false; }
        double v;
        if (einfach) v = ganz / ZEHN[nachkomma];
        else v = Double.parseDouble(new String(t, start, p - start, java.nio.charset.StandardCharsets.US_ASCII));
        z[k] = neg && einfach ? -v : v;
        pos[0] = p;
        return true;
    }

    private static final double[] ZEHN = new double[18];
    static {
        ZEHN[0] = 1;
        for (int i = 1; i < ZEHN.length; i++) ZEHN[i] = ZEHN[i - 1] * 10;
    }
}
