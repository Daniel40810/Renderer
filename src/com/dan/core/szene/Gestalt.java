package com.dan.core.szene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Die Gestalt des Geländes (seit P12b, Werkbuch HalligHooge Teil A, 12.13; F132 A): eine Karte zu 0,5 m über dem Land
 * mit der Art jedes Bildpunkts (Gras nach Klasse, Deckwerk, Wasser in Gräben, Prielen und Fethingen, Wegbeläge) und dem
 * Wasserstand des Wassers (F134 A). Ohne Grafik: Das Gelände ({@code render.terrain}) und später die Referenz lesen sie.
 * <p>
 * <b>Ablage</b> {@code gestalt/}: {@code gestalt.txt} (Zelle, Kantenlänge einer Kachel in Bildpunkten, Liste der
 * Kacheln mit ihrer Südwestecke) und je Kachel {@code E<ost>_N<nord>.rg8}: zeilenweise von Nord nach Süd je Bildpunkt
 * zwei Byte: Art, Wasserstand. Wasserstand w (Byte) in m NHN = w / 50 − 1 (−1 … 4,1 m, 2 cm); 0: kein Wasser.
 */
public final class Gestalt {
    public static final String DATEI = "gestalt.txt";
    /** Arten (Byte 0). 0: keine Gestalt (das Gelände wie bisher). */
    public static final int KEINE = 0, FENNE = 1, SALZWIESE = 2, DEICH = 3, WARFT = 4, DECKWERK = 5,
            GRABEN = 10, PRIEL = 11, FETHING = 12,
            ASPHALT = 20, KLINKER = 21, SCHOTTER = 22, BETON = 23, GRASWEG = 24, HOLZ = 25;

    public static boolean wasser(int art) { return art >= GRABEN && art <= FETHING; }

    public static boolean weg(int art) { return art >= ASPHALT && art <= HOLZ; }

    public static String name(int art) {
        return switch (art) {
            case FENNE -> "Fenne"; case SALZWIESE -> "Salzwiese"; case DEICH -> "Deich"; case WARFT -> "Warft"; case DECKWERK -> "Deckwerk";
            case GRABEN -> "Graben"; case PRIEL -> "Priel"; case FETHING -> "Fething";
            case ASPHALT -> "Asphalt"; case KLINKER -> "Klinker"; case SCHOTTER -> "Schotter"; case BETON -> "Beton"; case GRASWEG -> "Grasweg"; case HOLZ -> "Holz";
            default -> "keine";
        };
    }

    /** Wasserstand (m NHN) aus dem Byte; NaN für 0. */
    public static double stand(int b) { return b == 0 ? Double.NaN : b / 50.0 - 1; }

    /** Byte aus dem Wasserstand (m NHN), begrenzt auf 1 … 255. */
    public static int standByte(double h) { return Math.max(1, Math.min(255, (int) Math.round((h + 1) * 50))); }

    /** Eine Kachel: Südwestecke (m), Bildpunkte n × n, je zwei Byte. */
    public record Kachel(double ost, double nord, byte[] daten) { }

    public final double zelle;
    public final int n;
    public final List<Kachel> kacheln;

    public Gestalt(double zelle, int n, List<Kachel> kacheln) {
        this.zelle = zelle;
        this.n = n;
        this.kacheln = Collections.unmodifiableList(new ArrayList<>(kacheln));
    }

    public double kantenlaenge() { return zelle * n; }

    /** Hülle aller Kacheln: Ost0, Nord0, Ost1, Nord1. */
    public double[] huelle() {
        double[] h = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Kachel k : kacheln) {
            h[0] = Math.min(h[0], k.ost);
            h[1] = Math.min(h[1], k.nord);
            h[2] = Math.max(h[2], k.ost + kantenlaenge());
            h[3] = Math.max(h[3], k.nord + kantenlaenge());
        }
        return h;
    }

    /** Die Kachel, die (ost, nord) enthält; null außerhalb. */
    public Kachel kachel(double ost, double nord) {
        double s = kantenlaenge();
        for (Kachel k : kacheln) if (ost >= k.ost && ost < k.ost + s && nord >= k.nord && nord < k.nord + s) return k;
        return null;
    }

    /** {Art, Wasserstandsbyte} am Ort; {0, 0} außerhalb. */
    public int[] bei(double ost, double nord) {
        Kachel k = kachel(ost, nord);
        if (k == null) return new int[] {0, 0};
        int i = (int) Math.floor((ost - k.ost) / zelle), j = (int) Math.floor((k.nord + kantenlaenge() - nord) / zelle);
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        int o = 2 * (j * n + i);
        return new int[] {k.daten[o] & 0xFF, k.daten[o + 1] & 0xFF};
    }

    /** Liest {@code ordner/gestalt.txt} und die Kacheln; null, wenn es die Ablage nicht gibt. */
    public static Gestalt lies(File ordner) throws IOException {
        File f = new File(ordner, DATEI);
        if (!f.isFile()) return null;
        double zelle = Double.NaN;
        int n = 0;
        List<Kachel> l = new ArrayList<>();
        for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+");
            switch (t[0]) {
                case "zelle" -> zelle = Double.parseDouble(t[1]);
                case "bildpunkte" -> n = Integer.parseInt(t[1]);
                case "kachel" -> {
                    double e = Double.parseDouble(t[1]), no = Double.parseDouble(t[2]);
                    byte[] d = Files.readAllBytes(new File(ordner, name(e, no)).toPath());
                    if (d.length != 2 * n * n) throw new IOException("Gestalt: " + name(e, no) + " hat " + d.length + " Byte, erwartet " + 2 * n * n);
                    l.add(new Kachel(e, no, d));
                }
                default -> { }
            }
        }
        if (!(zelle > 0) || n <= 0) throw new IOException("Gestalt: Zelle oder Bildpunkte fehlen in " + f);
        return new Gestalt(zelle, n, l);
    }

    public static String name(double ost, double nord) { return String.format(Locale.ROOT, "E%.0f_N%.0f.rg8", ost, nord); }

    /** Schreibt die Ablage; {@code kopf}: Kommentar. */
    public void schreibe(File ordner, String kopf) throws IOException {
        Files.createDirectories(ordner.toPath());
        StringBuilder b = new StringBuilder();
        b.append("# Gestalt des Geländes (Werkbuch HalligHooge Teil A, 12.13, Schritt gestalt; F132–F134 A)\n");
        b.append("# ").append(kopf).append('\n');
        b.append("# Je Kachel E<ost>_N<nord>.rg8: zeilenweise von Nord, je Bildpunkt Art und Wasserstand (Byte w: w/50 − 1 m NHN, 0 kein Wasser)\n");
        b.append("# Arten: 1 Fenne, 2 Salzwiese, 3 Deich, 4 Warft, 5 Deckwerk, 10 Graben, 11 Priel, 12 Fething, 20 Asphalt, 21 Klinker, 22 Schotter, 23 Beton, 24 Grasweg, 25 Holz\n");
        b.append(String.format(Locale.ROOT, "zelle %s%nbildpunkte %d%n", zelle, n));
        for (Kachel k : kacheln) {
            b.append(String.format(Locale.ROOT, "kachel %.0f %.0f%n", k.ost, k.nord));
            Files.write(new File(ordner, name(k.ost, k.nord)).toPath(), k.daten);
        }
        Files.writeString(new File(ordner, DATEI).toPath(), b.toString(), StandardCharsets.UTF_8);
    }
}
