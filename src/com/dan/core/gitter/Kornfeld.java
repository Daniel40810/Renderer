package com.dan.core.gitter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Der Boden am Anfang für das Sediment (Werkbuch HalligHooge Teil A, 12.10, P9b): Korngröße d50 (m), Porosität,
 * Schlickanteil (Anteil feiner als 63 µm) und Anteil festen Bodens (Salzwiese, Fenne, Deich, Warft, Deckwerk: gibt
 * kein Sediment ab) auf einem Raster in UTM, lückenlos (die Aufbereitung füllt, wo die Quelle keine Werte hat).
 * <p>
 * Abgelegt im Gebiet unter {@code sediment/}: {@code sediment.txt} (Text, UTF-8: {@code raster <Südwestecke Ost>
 * <Südwestecke Nord> <Zelle> <nx> <ny>}; Zeilen mit {@code #} und andere Wörter sind für Menschen und die Prüfung) und
 * je Feld eine Datei {@code d50.f32}, {@code porositaet.f32}, {@code schlick.f32}, {@code fest.f32} (float32 Little Endian, Zellen von
 * Südwest, i schnell). Zwischen den Mitten der Pixel bilinear, am Rand der nächste Wert.
 */
public final class Kornfeld {
    public static final String ORDNER = "sediment", TEXT = "sediment.txt";
    public static final String[] FELDER = {"d50", "porositaet", "schlick", "fest"};
    public static final int D50 = 0, POROSITAET = 1, SCHLICK = 2, FEST = 3;

    /** Südwestecke (m), Zelle (m), Zahl der Pixel. */
    public final double x0, y0, zelle;
    public final int nx, ny;
    /** Je Feld nx · ny Werte. */
    public final float[][] werte;

    public Kornfeld(double x0, double y0, double zelle, int nx, int ny, float[][] werte) {
        if (werte.length != FELDER.length) throw new IllegalArgumentException("Kornfeld: " + werte.length + " Felder statt " + FELDER.length);
        for (float[] w : werte) if (w.length != nx * ny) throw new IllegalArgumentException("Kornfeld: " + w.length + " Werte statt " + nx * ny);
        this.x0 = x0; this.y0 = y0; this.zelle = zelle; this.nx = nx; this.ny = ny;
        this.werte = werte;
    }

    /** Gibt es im Gebiet einen Boden für das Sediment? */
    public static boolean vorhanden(File gebiet) { return new File(new File(gebiet, ORDNER), TEXT).isFile(); }

    public static Kornfeld lies(File gebiet) throws IOException {
        File o = new File(gebiet, ORDNER), t = new File(o, TEXT);
        if (!t.isFile()) throw new IOException(t + " fehlt – zuerst die Aufbereitung mit --schritte sediment");
        double[] r = null;
        for (String z : Files.readAllLines(t.toPath(), StandardCharsets.UTF_8)) {
            String[] w = z.strip().split("\\s+");
            if (w.length == 6 && w[0].equals("raster")) {
                r = new double[5];
                for (int k = 0; k < 5; k++) r[k] = Double.parseDouble(w[k + 1]);
            }
        }
        if (r == null) throw new IOException(t + ": die Zeile raster fehlt");
        int nx = (int) r[3], ny = (int) r[4];
        float[][] w = new float[FELDER.length][];
        for (int k = 0; k < FELDER.length; k++) w[k] = Gebiet.liesF32(new File(o, FELDER[k] + ".f32"), nx * ny);
        return new Kornfeld(r[0], r[1], r[2], nx, ny, w);
    }

    /** Schreibt Raster und Felder; {@code zeilen}: weitere Zeilen für {@code sediment.txt} (Quelle, Zahlen der Aufbereitung). */
    public void schreibe(File gebiet, java.util.List<String> zeilen) throws IOException {
        File o = new File(gebiet, ORDNER);
        Files.createDirectories(o.toPath());
        for (int k = 0; k < FELDER.length; k++) Gebiet.schreibeF32(new File(o, FELDER[k] + ".f32"), werte[k]);
        StringBuilder b = new StringBuilder("# Boden für das Sediment (Werkbuch HalligHooge Teil A, 12.10, P9b): d50 (m), Porosität, Schlickanteil, Anteil festen Bodens\n");
        b.append(String.format(Locale.ROOT, "raster %.1f %.1f %.1f %d %d%n", x0, y0, zelle, nx, ny));
        for (String z : zeilen) b.append(z).append('\n');
        Files.writeString(new File(o, TEXT).toPath(), b, StandardCharsets.UTF_8);
    }

    /** Der Wert eines Felds am Ort (Ost, Nord in m): bilinear zwischen den Mitten der Pixel, am Rand der nächste. */
    public double wert(int feld, double x, double y) {
        double fx = Math.max(0, Math.min(nx - 1, (x - x0) / zelle - 0.5)), fy = Math.max(0, Math.min(ny - 1, (y - y0) / zelle - 0.5));
        int i = Math.min(nx - 2, (int) fx), j = Math.min(ny - 2, (int) fy);
        if (nx < 2 || ny < 2) return werte[feld][Math.min(nx - 1, (int) fx) + nx * Math.min(ny - 1, (int) fy)];
        double a = fx - i, b = fy - j;
        float[] w = werte[feld];
        return (1 - b) * ((1 - a) * w[i + nx * j] + a * w[i + 1 + nx * j]) + b * ((1 - a) * w[i + nx * (j + 1)] + a * w[i + 1 + nx * (j + 1)]);
    }

    /** Ein Feld an den Mitten der Zellen eines Gitters in UTM. */
    public double[] auf(Gitter g, int feld) {
        if (g.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Kornfeld: nur Gitter in UTM, nicht " + g);
        double[] a = new double[g.zellen()];
        for (int j = 0; j < g.ny; j++) for (int i = 0; i < g.nx; i++) a[g.index(i, j)] = wert(feld, g.x(i), g.y(j));
        return a;
    }
}
