package com.dan.clipmap;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Ein Ordner mit Höhenkacheln als Quelle. Format (wie die Aufbereitung von LituyaBay1958 es schreibt, aber ohne deren
 * Code): eine Beschreibung {@code STUFE.txt} mit {@code zelle}, {@code kachel_zellen}, {@code kacheln_ost},
 * {@code kacheln_nord}, {@code suedwest_ost}, {@code suedwest_nord}, dazu je Kachel eine Datei
 * {@code E<Ost>_N<Nord>.r32} (Südwestecke in m): float32 Little Endian, Zeile für Zeile von Nord nach Süd, in der Zeile von
 * West nach Ost, NaN = kein Wert. Die Zelle (i, j) einer Kachel hat ihre Mitte bei Ost + (i + ½)·zelle,
 * Nord + Kachel − (j + ½)·zelle.
 * <p>
 * Ost und Nord der Dateien werden über den Ursprung (e0, n0) in den Raum der Clipmap gelegt: x = Ost − e0,
 * z = n0 − Nord. Gelesene Kacheln bleiben in einem Speicher (zuletzt benutzt bleibt), Vorgabe 768 MB je Ordner.
 */
public final class TileFolder implements Source {
    public final File dir;
    public final String name;
    public final double cell, e0, n0;
    public final int tileCells, tilesEast, tilesNorth;
    public final double southWestEast, southWestNorth;
    private final double tileSize;
    /** Höchstens so viele Bytes gelesener Kacheln bleiben im Speicher. */
    public volatile long cacheBytes = 768L << 20;
    private final LinkedHashMap<Long, float[]> cache = new LinkedHashMap<>(64, 0.75f, true);
    private long held;
    private int reads;
    private double readMs;
    private static final float[] MISSING = new float[0];

    /** Liest {@code STUFE.txt} in dir; e0, n0: Ost und Nord des Ursprungs der Clipmap. */
    public TileFolder(File dir, double e0, double n0) throws IOException {
        this.dir = dir;
        this.name = dir.getName();
        this.e0 = e0;
        this.n0 = n0;
        File d = new File(dir, "STUFE.txt");
        if (!d.isFile()) throw new IOException("keine Beschreibung " + d.getPath());
        Properties p = new Properties();
        try (var r = Files.newBufferedReader(d.toPath(), StandardCharsets.UTF_8)) {
            for (String z : (Iterable<String>) r.lines()::iterator) {
                int k = z.indexOf('#');
                String s = (k >= 0 ? z.substring(0, k) : z).strip();
                int e = s.indexOf('=');
                if (e > 0) p.setProperty(s.substring(0, e).strip(), s.substring(e + 1).strip());
            }
        }
        cell = num(p, "zelle", d);
        tileCells = (int) num(p, "kachel_zellen", d);
        tilesEast = (int) num(p, "kacheln_ost", d);
        tilesNorth = (int) num(p, "kacheln_nord", d);
        southWestEast = num(p, "suedwest_ost", d);
        southWestNorth = num(p, "suedwest_nord", d);
        tileSize = tileCells * cell;
    }

    static double num(Properties p, String k, File f) throws IOException {
        String v = p.getProperty(k);
        if (v == null) throw new IOException(f.getPath() + ": " + k + " fehlt");
        return Double.parseDouble(v);
    }

    @Override public double finestCell() { return cell; }

    /** Ost, Nord, Ost, Nord der Ausdehnung (Westrand, Südrand, Ostrand, Nordrand). */
    public double[] extentEN() {
        return new double[]{southWestEast, southWestNorth, southWestEast + tilesEast * tileSize, southWestNorth + tilesNorth * tileSize};
    }

    /** Die Ausdehnung im Raum der Clipmap: xmin, zmin, xmax, zmax. */
    public double[] extent() {
        double[] e = extentEN();
        return new double[]{e[0] - e0, n0 - e[3], e[2] - e0, n0 - e[1]};
    }

    /** Der Wert der Zelle mit globaler Spalte col (von West) und Zeile row (von Nord), oder NaN. */
    public float cellValue(int col, int row) { return cellValue(col, row, new Ref()); }

    /** Zuletzt benutzte Kachel eines Aufrufers (spart die Suche im Speicher bei jedem Punkt). */
    static final class Ref {
        long key = Long.MIN_VALUE;
        float[] tile;
    }

    float cellValue(int col, int row, Ref ref) {
        if (col < 0 || row < 0 || col >= tilesEast * tileCells || row >= tilesNorth * tileCells) return Float.NaN;
        int tx = col / tileCells, ty = row / tileCells;
        long key = ((long) tx << 32) | (ty & 0xffffffffL);
        float[] t;
        if (ref.key == key) t = ref.tile;
        else {
            t = tile(tx, ty);
            ref.key = key;
            ref.tile = t;
        }
        if (t == MISSING) return Float.NaN;
        return t[(row - ty * tileCells) * tileCells + (col - tx * tileCells)];
    }

    /** Spalte und Zeile (gebrochen, Zellmitte = ganz) zum Ort (x, z). */
    public double col(double x) { return (x + e0 - southWestEast) / cell - 0.5; }
    public double row(double z) { return (southWestNorth + tilesNorth * tileSize - (n0 - z)) / cell - 0.5; }

    /** Ort der Zellmitte (für Prüfungen). */
    public double xOfCol(int col) { return southWestEast + (col + 0.5) * cell - e0; }
    public double zOfRow(int row) { return n0 - (southWestNorth + tilesNorth * tileSize - (row + 0.5) * cell); }

    /** Bilinear; fehlende Nachbarn zählen nicht (ab einem Viertel Gewicht). */
    public float bilinear(double x, double z) { return bilinear(x, z, new Ref()); }

    float bilinear(double x, double z, Ref ref) {
        double c = col(x), r = row(z);
        int i = (int) Math.floor(c), j = (int) Math.floor(r);
        double fx = c - i, fy = r - j;
        float a = cellValue(i, j, ref), b = cellValue(i + 1, j, ref), cc = cellValue(i, j + 1, ref), d = cellValue(i + 1, j + 1, ref);
        double wa = (1 - fx) * (1 - fy), wb = fx * (1 - fy), wc = (1 - fx) * fy, wd = fx * fy, s = 0, w = 0;
        if (!Float.isNaN(a)) { s += a * wa; w += wa; }
        if (!Float.isNaN(b)) { s += b * wb; w += wb; }
        if (!Float.isNaN(cc)) { s += cc * wc; w += wc; }
        if (!Float.isNaN(d)) { s += d * wd; w += wd; }
        return w >= 0.25 ? (float) (s / w) : Float.NaN;
    }

    /**
     * Höhe für eine Abtastung mit Zellweite {@code want}: bilinear; ist die Abtastung gröber als die Kachel, das Mittel aus
     * 2 × 2 (ab 1,5 Zellen) oder 4 × 4 Punkten (ab 6 Zellen) im Feld der Abtastung, damit die Ferne nicht flimmert.
     */
    @Override public float height(double x, double z, double want) { return height(x, z, want, new Ref()); }

    float height(double x, double z, double want, Ref ref) {
        double r = want / cell;
        int n = r < 1.5 ? 1 : r < 6 ? 2 : 4;
        if (n == 1) return bilinear(x, z, ref);
        double s = 0;
        int k = 0;
        for (int b = 0; b < n; b++)
            for (int a = 0; a < n; a++) {
                float v = bilinear(x + ((a + 0.5) / n - 0.5) * want, z + ((b + 0.5) / n - 0.5) * want, ref);
                if (!Float.isNaN(v)) { s += v; k++; }
            }
        return k * 2 >= n * n ? (float) (s / k) : Float.NaN;
    }

    /** Wie {@link #height}, für ein ganzes Rechteck; außerhalb der Ausdehnung NaN ohne Suche. */
    @Override public void fill(double x0, double z0, double c, int w, int h, float[] out) {
        Ref ref = new Ref();
        double[] e = extent();
        for (int j = 0; j < h; j++) {
            double z = z0 + j * c;
            for (int i = 0; i < w; i++) {
                double x = x0 + i * c;
                out[j * w + i] = x < e[0] - c || x > e[2] + c || z < e[1] - c || z > e[3] + c ? Float.NaN : height(x, z, c, ref);
            }
        }
    }

    private float[] tile(int tx, int ty) {
        long key = ((long) tx << 32) | (ty & 0xffffffffL);
        synchronized (cache) {
            float[] t = cache.get(key);
            if (t != null) return t;
        }
        float[] t = read(tx, ty);
        synchronized (cache) {
            float[] o = cache.put(key, t);
            if (o == null) held += t.length * 4L;
            var it = cache.entrySet().iterator();
            while (held > cacheBytes && cache.size() > 1 && it.hasNext()) {
                Map.Entry<Long, float[]> e = it.next();
                if (e.getKey() == key) continue;
                held -= e.getValue().length * 4L;
                it.remove();
            }
        }
        return t;
    }

    /** Die Datei der Kachel (tx von West, ty von Nord). */
    public File file(int tx, int ty) {
        double e = southWestEast + tx * tileSize, n = southWestNorth + (tilesNorth - 1 - ty) * tileSize;
        return new File(dir, String.format(Locale.ROOT, "E%d_N%d.r32", Math.round(e), Math.round(n)));
    }

    private float[] read(int tx, int ty) {
        File f = file(tx, ty);
        if (!f.isFile()) return MISSING;
        long t0 = System.nanoTime();
        int n = tileCells * tileCells;
        try (FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            if (ch.size() < n * 4L) return MISSING;
            ByteBuffer bb = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.LITTLE_ENDIAN);
            while (bb.hasRemaining() && ch.read(bb) >= 0) { /* lesen */ }
            bb.flip();
            float[] v = new float[n];
            bb.asFloatBuffer().get(v);
            synchronized (cache) {
                reads++;
                readMs += (System.nanoTime() - t0) / 1e6;
            }
            return v;
        } catch (IOException e) {
            System.err.println("clipmap: " + f + " nicht lesbar: " + e.getMessage());
            return MISSING;
        }
    }

    /** „kern_0.5m: 9 Kacheln im Speicher (144 MB), 12 gelesen, 23 ms je Kachel“ */
    @Override public String describe() {
        synchronized (cache) {
            long da = cache.values().stream().filter(t -> t.length > 0).count();
            return String.format(Locale.GERMAN, "%s: Zelle %s m, %d Kacheln im Speicher (%d MB), %d gelesen%s", name, fmt(cell), da, held >> 20, reads,
                    reads > 0 ? String.format(Locale.GERMAN, ", %.0f ms je Kachel", readMs / reads) : "");
        }
    }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }
}
