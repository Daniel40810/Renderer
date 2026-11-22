package com.dan.core.gitter;

import com.dan.core.astro.Partialtide;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Harmonische Konstanten der Gezeiten auf einem {@link Gradraster} (Werkbuch HalligHooge Teil A, 7.2 und 12.6, P5b):
 * EOT20 ausgeschnitten auf G0 und etwas darüber hinaus, je Partialtide Real- und Imaginärteil in m. Mit Amplitude A und
 * Phase G (Greenwich, Grad) ist {@code re = A cos G}, {@code im = A sin G}; der Beitrag zum Wasserstand ist
 * {@code f · A · cos(V + u − G) = f · (re · cos(V + u) + im · sin(V + u))} wie in {@link Partialtide#wert}. An Land
 * NaN.
 * <p>
 * Abgelegt im Gebiet unter {@code tide/}: {@code eot20.txt} (Text, UTF-8: {@code raster …} nach
 * {@link Gradraster#zeile()}, {@code tiden <Namen>}, {@code werte <Datei>}; Zeilen mit {@code #} und andere Wörter sind
 * für Menschen und die Prüfung) und die Werte als float32 Little Endian, je Tide in der Folge von {@code tiden} alle
 * Punkte (j · nx + i) mit re, im.
 */
public final class Tidenfeld {
    public static final String ORDNER = "tide", TEXT = "eot20.txt", DATEN = "eot20.f32";

    public final Gradraster raster;
    public final List<Partialtide> tiden;
    /** Je Tide 2 · Punkte Werte: re, im (m); NaN an Land. */
    private final float[][] werte;

    public Tidenfeld(Gradraster raster, List<Partialtide> tiden, float[][] werte) {
        if (werte.length != tiden.size()) throw new IllegalArgumentException("Tidenfeld: " + werte.length + " Felder für " + tiden.size() + " Tiden");
        for (float[] w : werte) if (w.length != 2 * raster.punkte()) throw new IllegalArgumentException("Tidenfeld: " + w.length + " Werte statt " + 2 * raster.punkte());
        this.raster = raster;
        this.tiden = List.copyOf(tiden);
        this.werte = werte;
    }

    /** Die Werte einer Tide (re, im je Punkt), nicht kopiert. */
    public float[] werte(int tide) { return werte[tide]; }

    /** Index der Tide oder −1. */
    public int index(Partialtide p) { return tiden.indexOf(p); }

    /** Gewichte für einen Ort (gültig, wo M2 bzw. die erste Tide nicht NaN ist), siehe {@link Gradraster#gewichte}. */
    public Gradraster.Gewichte gewichte(double laenge, double breite, int umkreis) {
        int m = Math.max(0, index(Partialtide.M2));
        return raster.gewichte(laenge, breite, werte[m], 0, 2, umkreis);
    }

    /** Je Tide {re, im} (m) an einem Ort; null, wo kein gültiger Punkt im Umkreis liegt. */
    public double[][] konstanten(double laenge, double breite, int umkreis) {
        Gradraster.Gewichte g = gewichte(laenge, breite, umkreis);
        if (g == null) return null;
        double[][] k = new double[tiden.size()][2];
        for (int t = 0; t < k.length; t++) {
            k[t][0] = g.wert(werte[t], 0, 2);
            k[t][1] = g.wert(werte[t], 1, 2);
        }
        return k;
    }

    /** Amplitude (m) und Phase (Grad, 0..360) aus {re, im}. */
    public static double[] amplitudePhase(double[] k) {
        double g = Math.toDegrees(Math.atan2(k[1], k[0]));
        return new double[] {Math.hypot(k[0], k[1]), g < 0 ? g + 360 : g};
    }

    /**
     * Je Tide {f · cos(V + u), f · sin(V + u)} zur Zeit t; mit {@link #eta} für viele Orte zur selben Zeit (Knotenfaktoren
     * und Argument nur einmal).
     */
    public double[][] faktoren(Instant t) {
        double[] g = Partialtide.grundargumente(t);
        double n = Partialtide.knotenlaenge(t);
        double[][] f = new double[tiden.size()][2];
        for (int k = 0; k < f.length; k++) {
            Partialtide p = tiden.get(k);
            double[] fu = p.fu(n);
            double w = Math.toRadians(p.argumentGrad(g) + fu[1]);
            f[k][0] = fu[0] * Math.cos(w);
            f[k][1] = fu[0] * Math.sin(w);
        }
        return f;
    }

    /** Wasserstand der Gezeiten (m, ohne Mittel) aus den Konstanten eines Orts und den Faktoren einer Zeit. */
    public static double eta(double[][] konstanten, double[][] faktoren) {
        double s = 0;
        for (int k = 0; k < konstanten.length; k++) s += konstanten[k][0] * faktoren[k][0] + konstanten[k][1] * faktoren[k][1];
        return s;
    }

    // ------------------------------------------------------------------ Ablage

    /** Schreibt {@code tide/eot20.txt} und {@code tide/eot20.f32} in den Ordner des Gebiets; {@code kopf}: weitere Zeilen. */
    public void schreibe(File gebiet, String kopf) throws IOException {
        File o = new File(gebiet, ORDNER);
        Files.createDirectories(o.toPath());
        File d = new File(o, DATEN), dt = new File(o, DATEN + ".neu");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(dt), 1 << 20)) {
            ByteBuffer b = ByteBuffer.allocate(4 * 2 * raster.punkte()).order(ByteOrder.LITTLE_ENDIAN);
            for (float[] w : werte) {
                b.clear();
                for (float x : w) b.putFloat(x);
                out.write(b.array(), 0, b.position());
            }
        }
        Files.move(dt.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING);
        StringBuilder s = new StringBuilder();
        s.append("# Harmonische Konstanten der Gezeiten (Werkbuch HalligHooge Teil A, 12.6, P5b), Format in com.dan.core.gitter.Tidenfeld\n");
        s.append("# re = A cos G, im = A sin G in m, G Greenwich; NaN an Land; float32 Little Endian, je Tide alle Punkte (j nx + i) mit re, im\n");
        if (kopf != null && !kopf.isBlank()) s.append(kopf.strip()).append('\n');
        s.append(raster.zeile()).append('\n');
        s.append("tiden");
        for (Partialtide p : tiden) s.append(' ').append(p.name);
        s.append('\n').append("werte ").append(DATEN).append('\n');
        File t = new File(o, TEXT), tt = new File(o, TEXT + ".neu");
        Files.writeString(tt.toPath(), s, StandardCharsets.UTF_8);
        Files.move(tt.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Liegt ein Tidenfeld im Gebiet? */
    public static boolean da(File gebiet) { return new File(new File(gebiet, ORDNER), TEXT).isFile(); }

    /** Liest {@code tide/eot20.txt} und die Werte aus dem Ordner des Gebiets. */
    public static Tidenfeld lies(File gebiet) throws IOException {
        File o = new File(gebiet, ORDNER);
        Gradraster r = null;
        List<Partialtide> tiden = new ArrayList<>();
        String datei = DATEN;
        for (String z : Files.readAllLines(new File(o, TEXT).toPath(), StandardCharsets.UTF_8)) {
            String s = z.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+");
            switch (t[0]) {
                case "raster" -> r = Gradraster.aus(s);
                case "tiden" -> {
                    for (int k = 1; k < t.length; k++) {
                        Partialtide p = Partialtide.nach(t[k]);
                        if (p == null) throw new IOException(TEXT + ": unbekannte Tide " + t[k]);
                        tiden.add(p);
                    }
                }
                case "werte" -> datei = t[1];
                default -> { }
            }
        }
        if (r == null || tiden.isEmpty()) throw new IOException(new File(o, TEXT) + ": raster oder tiden fehlt");
        int n = 2 * r.punkte();
        byte[] roh = Files.readAllBytes(new File(o, datei).toPath());
        if (roh.length != 4L * n * tiden.size()) throw new IOException(datei + ": " + roh.length + " Byte statt " + 4L * n * tiden.size());
        ByteBuffer b = ByteBuffer.wrap(roh).order(ByteOrder.LITTLE_ENDIAN);
        float[][] w = new float[tiden.size()][n];
        for (int k = 0; k < w.length; k++) b.position(4 * n * k).slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(w[k]);
        return new Tidenfeld(r, tiden, w);
    }
}
