package com.dan.core.szene;

import com.dan.core.gitter.Gelaende;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Boden: das Material des Geländes nach F41 A (P6b, Werkbuch HalligHooge Teil A, 12.7; seit P7a, F52 A, in der
 * gemeinsamen Szene, vorher {@code render.terrain.Material}): je Rauheitsklasse eine Albedo aus dem
 * Luftbild (trocken, linear, geeicht gegen Sentinel-2; Schritt luftbild der Aufbereitung, {@code luftbild/albedo.txt}),
 * innerhalb der Klasse die Struktur aus dem Luftbild ({@code luftbild/1m}, Leuchtdichte relativ zur Klasse), außerhalb
 * des Luftbilds die Albedo der Klasse allein. Dazu der Anteil von Watt und Sand, auf dem die Nässe wirkt.
 * <p>
 * Die Clipmap tastet mit Schritt c ab. Bis 4 m [G] mittelt eine Probe die c × c Pixel der Klassen in 1 m; gröber gelten
 * 4 × 4 Proben im Abstand c/4 aus den Klassen in 1 m oder 10 m. Die vier Kanäle R, G, B und Watt sind vier Quellen
 * über demselben Speicher: die erste rechnet ein Rechteck, die anderen nehmen es (die Clipmaps laden dieselben
 * Rechtecke nacheinander, {@code render.terrain.Gelaendebild}). Die Referenz liest je Treffer das Pixel in 1 m
 * ({@link #pixel}).
 */
public final class Boden {
    /** Ohne Klasse (außerhalb der Stufen): Watt. */
    public static final int OHNE = 1;
    public final double[][] albedo;
    public final Gelaende.Stufe k1, k10, struktur;
    public final double e0, n0;
    final ThreadLocal<Gelaende.Stufe.Leser[]> leser;
    private final Map<String, float[][]> speicher = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, float[][]> e) { return size() > 64; }
    };

    /**
     * Aus dem Gebiet: {@code rauheit/1m}, {@code rauheit/10m}, {@code luftbild/1m} (fehlt sie, ohne Struktur) und
     * {@code luftbild/albedo.txt}. (e0, n0): Ursprung des Raums der Clipmap (x = Ost − e0, z = n0 − Nord).
     */
    public static Boden aus(File gebiet, double e0, double n0) throws IOException {
        Gelaende.Stufe k1 = Gelaende.Stufe.lies(new File(gebiet, "rauheit/1m")), k10 = Gelaende.Stufe.lies(new File(gebiet, "rauheit/10m"));
        File s = new File(gebiet, "luftbild/1m");
        Gelaende.Stufe st = new File(s, Gelaende.BESCHREIBUNG).isFile() ? Gelaende.Stufe.lies(s) : null;
        return new Boden(albedo(new File(gebiet, "luftbild/albedo.txt")), k1, k10, st, e0, n0);
    }

    public Boden(double[][] albedo, Gelaende.Stufe k1, Gelaende.Stufe k10, Gelaende.Stufe struktur, double e0, double n0) {
        this.albedo = albedo;
        this.k1 = k1;
        this.k10 = k10;
        this.struktur = struktur;
        this.e0 = e0;
        this.n0 = n0;
        leser = ThreadLocal.withInitial(() -> new Gelaende.Stufe.Leser[] {k1.leser(), k10.leser(), struktur == null ? null : struktur.leser()});
        for (int q = 1; q < albedo.length; q++)
            if (Double.isNaN(albedo[q][0])) albedo[q] = albedo[OHNE].clone();
    }

    /** Liest {@code albedo.txt} (Klasse, R, G, B trocken, …); fehlende Klassen NaN. */
    public static double[][] albedo(File datei) throws IOException {
        double[][] a = new double[9][3];
        for (double[] x : a) Arrays.fill(x, Double.NaN);
        for (String s : Files.readAllLines(datei.toPath(), StandardCharsets.UTF_8)) {
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+");
            int q = Integer.parseInt(t[0]);
            for (int c = 0; c < 3; c++) a[q][c] = Double.parseDouble(t[1 + c]);
        }
        if (Double.isNaN(a[OHNE][0])) throw new IOException(datei + ": keine Albedo für Klasse " + OHNE);
        return a;
    }

    /** Wirkt die Nässe auf die Klasse q (Watt und Sand)? */
    public static boolean nass(int q) { return q == 1 || q == 2; }

    /** Klasse am Ort (UTM): aus 1 m ({@code fein}), sonst oder außerhalb aus 10 m; ohne Klasse {@link #OHNE}. */
    public int klasse(double ost, double nord, boolean fein) {
        Gelaende.Stufe.Leser[] l = leser.get();
        float v = fein ? l[0].imPixel(ost, nord) : Float.NaN;
        if (Float.isNaN(v)) v = l[1].imPixel(ost, nord);
        int q = Float.isNaN(v) ? OHNE : Math.round(v);
        return q <= 0 || q >= albedo.length ? OHNE : q;
    }

    /** Struktur aus dem Luftbild am Ort (Leuchtdichte relativ zur Klasse); außerhalb 1. */
    public double struktur(double ost, double nord) {
        Gelaende.Stufe.Leser l = leser.get()[2];
        if (l == null) return 1;
        float s = l.imPixel(ost, nord);
        return Float.isNaN(s) ? 1 : s;
    }

    /** Ein Punkt der Clipmap bei (x, z) mit Schritt c: {R, G, B, Watt}. */
    public float[] punkt(double x, double z, double c) {
        double ost = e0 + x, nord = n0 - z;
        double[] s = new double[4];
        int n;
        if (c <= 4.0001) {
            n = Math.max(1, (int) Math.round(c));
            double o = (n - 1) / 2.0;
            for (int b = 0; b < n; b++)
                for (int a = 0; a < n; a++) {
                    double pe = ost + a - o, pn = nord - b + o;
                    int q = klasse(pe, pn, true);
                    double st = struktur(pe, pn);
                    for (int k = 0; k < 3; k++) s[k] += albedo[q][k] * st;
                    if (nass(q)) s[3]++;
                }
            n *= n;
        } else {
            n = 16;
            for (int b = 0; b < 4; b++)
                for (int a = 0; a < 4; a++) {
                    double pe = ost + (a - 1.5) * c / 4, pn = nord - (b - 1.5) * c / 4;
                    int q = klasse(pe, pn, c < 20);
                    for (int k = 0; k < 3; k++) s[k] += albedo[q][k];
                    if (nass(q)) s[3]++;
                }
        }
        return new float[] {(float) (s[0] / n), (float) (s[1] / n), (float) (s[2] / n), (float) (s[3] / n)};
    }

    /** Ein Rechteck (Speicher für die anderen Kanäle). */
    public float[][] rechteck(double x0, double z0, double c, int w, int h) {
        String key = x0 + "|" + z0 + "|" + c + "|" + w + "|" + h;
        synchronized (speicher) {
            float[][] r = speicher.get(key);
            if (r != null) return r;
        }
        float[][] r = new float[4][w * h];
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                float[] p = punkt(x0 + i * c, z0 + j * c, c);
                for (int k = 0; k < 4; k++) r[k][j * w + i] = p[k];
            }
        synchronized (speicher) { speicher.put(key, r); }
        return r;
    }

    /**
     * Ein Pixel in 1 m am Ort (UTM), wie die Clipmap es am Auge mittelt: {R, G, B, Anteil Watt (0 oder 1)}, Albedo
     * trocken, linear, mit der Struktur des Luftbilds. Für die Referenz (P7c).
     */
    public double[] pixel(double ost, double nord) {
        double pe = Math.floor(ost) + 0.5, pn = Math.floor(nord) + 0.5;
        int q = klasse(pe, pn, true);
        double st = struktur(pe, pn);
        return new double[] {albedo[q][0] * st, albedo[q][1] * st, albedo[q][2] * st, nass(q) ? 1 : 0};
    }
}
