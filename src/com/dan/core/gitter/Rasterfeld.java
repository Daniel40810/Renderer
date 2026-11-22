package com.dan.core.gitter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ein Feld auf einem Raster in UTM mit einem Kopf aus Text (seit P9b, Werkbuch HalligHooge Teil A, 12.10): für Daten, die
 * die Aufbereitung für eine Prüfung bereitlegt, etwa den Schwebstoff aus einer Szene von Sentinel-2.
 * <p>
 * Abgelegt als Text (UTF-8) mit den Zeilen {@code raster <Südwestecke Ost> <Südwestecke Nord> <Zelle> <nx> <ny>},
 * {@code datei <Name>} und weiteren Zeilen {@code <Schlüssel> <Wert>} (Zeilen mit {@code #} sind für Menschen), daneben
 * die Werte als float32 Little Endian, Zellen von Südwest, i schnell; NaN: kein Wert.
 */
public final class Rasterfeld {
    public final double x0, y0, zelle;
    public final int nx, ny;
    public final float[] werte;
    /** Die weiteren Zeilen des Kopfs: Schlüssel → Wert. */
    public final Map<String, String> kopf = new LinkedHashMap<>();

    public Rasterfeld(double x0, double y0, double zelle, int nx, int ny, float[] werte) {
        if (werte.length != nx * ny) throw new IllegalArgumentException("Rasterfeld: " + werte.length + " Werte statt " + nx * ny);
        this.x0 = x0; this.y0 = y0; this.zelle = zelle; this.nx = nx; this.ny = ny;
        this.werte = werte;
    }

    public static Rasterfeld lies(File text) throws IOException {
        double[] r = null;
        String datei = null;
        Map<String, String> k = new LinkedHashMap<>();
        for (String z : Files.readAllLines(text.toPath(), StandardCharsets.UTF_8)) {
            String t = z.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String[] w = t.split("\\s+", 2);
            if (w[0].equals("raster")) {
                String[] p = w[1].split("\\s+");
                r = new double[5];
                for (int i = 0; i < 5; i++) r[i] = Double.parseDouble(p[i]);
            } else if (w[0].equals("datei")) datei = w[1];
            else k.put(w[0], w.length > 1 ? w[1] : "");
        }
        if (r == null || datei == null) throw new IOException(text + ": die Zeile raster oder datei fehlt");
        int nx = (int) r[3], ny = (int) r[4];
        Rasterfeld f = new Rasterfeld(r[0], r[1], r[2], nx, ny, Gebiet.liesF32(new File(text.getParentFile(), datei), nx * ny));
        f.kopf.putAll(k);
        return f;
    }

    /** Schreibt den Text und daneben die Werte in {@code datei}; {@code zeilen}: Kommentare und Zeilen „Schlüssel Wert“. */
    public void schreibe(File text, String datei, List<String> zeilen) throws IOException {
        Files.createDirectories(text.getParentFile().toPath());
        Gebiet.schreibeF32(new File(text.getParentFile(), datei), werte);
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "raster %.1f %.1f %.1f %d %d%n", x0, y0, zelle, nx, ny));
        b.append("datei ").append(datei).append('\n');
        for (String z : zeilen) b.append(z).append('\n');
        Files.writeString(text.toPath(), b, StandardCharsets.UTF_8);
    }

    /** Der Wert des Pixels, in dem der Ort liegt (Ost, Nord in m); NaN außerhalb. */
    public double bei(double x, double y) {
        int i = (int) Math.floor((x - x0) / zelle), j = (int) Math.floor((y - y0) / zelle);
        return i < 0 || j < 0 || i >= nx || j >= ny ? Double.NaN : werte[i + nx * j];
    }
}
