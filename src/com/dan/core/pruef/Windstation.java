package com.dan.core.pruef;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Wind an einer Station des DWD (Werkbuch HalligHooge Teil A, 12.6, P5e, F36 A): 10-Minuten-Mittel der Geschwindigkeit
 * (m/s, in der Höhe des Gebers) und Richtung (°, woher), Zeit in UTC am Ende des Mittels. Abgelegt im Gebiet unter
 * {@code wind/}: {@code wind.txt} mit den Stationen (Name, Kennung, Länge, Breite, Höhe der Station, Höhe des Gebers über
 * Grund, Quelle) und je Station {@code <Name>.csv} ({@code zeit,ff,dd}; fehlende Werte leer). Schreibt die Aufbereitung
 * (Schritt {@code wind}), liest die Prüfung. Nur {@code java.*}.
 */
public final class Windstation {
    public static final String ORDNER = "wind", TEXT = "wind.txt";

    public final String name, kennung, quelle;
    public final double laenge, breite, hoehe, geber;
    public final long[] zeiten;
    public final float[] ff, dd;

    public Windstation(String name, String kennung, double laenge, double breite, double hoehe, double geber, String quelle,
                       long[] zeiten, float[] ff, float[] dd) {
        this.name = name;
        this.kennung = kennung;
        this.laenge = laenge;
        this.breite = breite;
        this.hoehe = hoehe;
        this.geber = geber;
        this.quelle = quelle;
        this.zeiten = zeiten;
        this.ff = ff;
        this.dd = dd;
    }

    /**
     * Mittel der Geschwindigkeit über die Werte mit Ende in (t − halb, t + halb] (s); NaN, wenn weniger als die Hälfte
     * der erwarteten Werte zu 600 s da ist.
     */
    public double mittel(Instant t, long halb) {
        long a = t.getEpochSecond() - halb, b = t.getEpochSecond() + halb;
        int i = java.util.Arrays.binarySearch(zeiten, a + 1);
        if (i < 0) i = -i - 1;
        double s = 0;
        int n = 0;
        for (; i < zeiten.length && zeiten[i] <= b; i++)
            if (!Float.isNaN(ff[i])) { s += ff[i]; n++; }
        return 2 * n >= (2 * halb) / 600 ? s / n : Double.NaN;
    }

    /** Wind in 10 m aus der Höhe des Gebers mit dem logarithmischen Profil, Rauheitslänge z0 (m). */
    public double auf10m(double u, double z0) {
        return geber > 0 ? u * Math.log(10 / z0) / Math.log(geber / z0) : u;
    }

    /** Schreibt die Stationen nach {@code <ordner>/wind/}. */
    public static void schreibe(File ordner, List<Windstation> stationen, String kopf) throws IOException {
        File o = new File(ordner, ORDNER);
        Files.createDirectories(o.toPath());
        StringBuilder t = new StringBuilder("# Wind an Stationen des DWD (10-Minuten-Mittel, UTC am Ende), Werkbuch HalligHooge Teil A, P5e (F36 A)\n");
        if (kopf != null) for (String z : kopf.split("\n")) t.append("# ").append(z).append('\n');
        t.append("# name kennung laenge breite hoehe geber quelle\n");
        for (Windstation w : stationen) {
            t.append(String.format(Locale.ROOT, "station %s %s %.4f %.4f %.1f %.1f %s%n", w.name, w.kennung, w.laenge, w.breite, w.hoehe, w.geber, w.quelle));
            StringBuilder c = new StringBuilder("zeit,ff,dd\n");
            for (int i = 0; i < w.zeiten.length; i++)
                c.append(Instant.ofEpochSecond(w.zeiten[i])).append(',').append(Float.isNaN(w.ff[i]) ? "" : String.format(Locale.ROOT, "%.1f", w.ff[i]))
                        .append(',').append(Float.isNaN(w.dd[i]) ? "" : String.format(Locale.ROOT, "%.0f", w.dd[i])).append('\n');
            Files.writeString(new File(o, w.name + ".csv").toPath(), c, StandardCharsets.UTF_8);
        }
        Files.writeString(new File(o, TEXT).toPath(), t, StandardCharsets.UTF_8);
    }

    /** Liest die Stationen aus {@code <ordner>/wind/}; leer, wenn es den Ordner nicht gibt. */
    public static Map<String, Windstation> lies(File ordner) throws IOException {
        Map<String, Windstation> m = new LinkedHashMap<>();
        File o = new File(ordner, ORDNER), t = new File(o, TEXT);
        if (!t.isFile()) return m;
        for (String z : Files.readAllLines(t.toPath(), StandardCharsets.UTF_8)) {
            if (!z.startsWith("station ")) continue;
            String[] s = z.trim().split("\\s+", 8);
            List<String> zeilen = Files.readAllLines(new File(o, s[1] + ".csv").toPath(), StandardCharsets.UTF_8);
            int n = zeilen.size() - 1;
            long[] zeiten = new long[n];
            float[] ff = new float[n], dd = new float[n];
            for (int i = 0; i < n; i++) {
                String[] w = zeilen.get(i + 1).split(",", -1);
                zeiten[i] = Instant.parse(w[0]).getEpochSecond();
                ff[i] = w[1].isEmpty() ? Float.NaN : Float.parseFloat(w[1]);
                dd[i] = w[2].isEmpty() ? Float.NaN : Float.parseFloat(w[2]);
            }
            m.put(s[1], new Windstation(s[1], s[2], Double.parseDouble(s[3]), Double.parseDouble(s[4]), Double.parseDouble(s[5]),
                    Double.parseDouble(s[6]), s.length > 7 ? s[7] : "", zeiten, ff, dd));
        }
        return m;
    }
}
