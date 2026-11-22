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
 * Die Kanten der Brandung (seit P13c, Werkbuch HalligHooge Teil A, 12.14; F160 A): Böschungen, an denen Wellen über
 * Wasser brechen – Deckwerk und Steinkante, Warften, Deiche, Abbruchkanten des Vorlands. Je Abschnitt von {@link #LAENGE}
 * m der Fuß (Ort und Höhe), die Richtung zur See (Einheitsvektor nach Ost und Nord, hangab), die Höhe der Krone, die
 * Neigung tan α zwischen Fuß und Krone und die Rauheit für den Auflauf γ_f nach EurOtop 2018 (Tafel 5.2) [G].
 * <p>
 * <b>Ablage</b> {@code brandung/kanten.txt}: Kopf mit Kommentaren, je Zeile ein Abschnitt
 * {@code ost nord nx ny fuss krone neigung rau art}. Ohne Grafik: {@code render.brandung} und die Prüfung lesen sie.
 */
public final class Kanten {
    public static final String ORDNER = "brandung", DATEI = "kanten.txt";
    /** Länge eines Abschnitts längs der Kante (m) [G]. */
    public static final double LAENGE = 5.0;

    /** Ein Abschnitt: Fuß (UTM), Richtung zur See, Höhen (m NHN), tan α, γ_f, Art der Gestalt (5 Deckwerk, 4 Warft, 3 Deich, sonst Kante). */
    public record Kante(double ost, double nord, double nx, double ny, double fuss, double krone, double neigung, double rau, int art) {
        /** Waagerechter Abstand vom Fuß bis zur Höhe z auf dem Hang (m, landwärts). */
        public double bis(double z) { return (z - fuss) / neigung; }
    }

    public final List<Kante> kanten;

    public Kanten(List<Kante> k) { kanten = Collections.unmodifiableList(new ArrayList<>(k)); }

    /** Name der Art für Berichte. */
    public static String artName(int art) {
        return switch (art) { case Gestalt.DECKWERK -> "Deckwerk"; case Gestalt.WARFT -> "Warft"; case Gestalt.DEICH -> "Deich"; default -> "Kante"; };
    }

    /** Liest {@code gebiet/brandung/kanten.txt}; null, wenn es sie nicht gibt. */
    public static Kanten lies(File gebiet) throws IOException {
        File f = new File(new File(gebiet, ORDNER), DATEI);
        if (!f.isFile()) return null;
        List<Kante> l = new ArrayList<>();
        for (String s : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            s = s.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            String[] t = s.split("\\s+");
            if (t.length < 9) throw new IOException("Kanten: Zeile mit " + t.length + " Werten in " + f + ": " + s);
            l.add(new Kante(Double.parseDouble(t[0]), Double.parseDouble(t[1]), Double.parseDouble(t[2]), Double.parseDouble(t[3]), Double.parseDouble(t[4]),
                    Double.parseDouble(t[5]), Double.parseDouble(t[6]), Double.parseDouble(t[7]), Integer.parseInt(t[8])));
        }
        return new Kanten(l);
    }

    /** Schreibt {@code gebiet/brandung/kanten.txt}. */
    public void schreibe(File gebiet, String kopf) throws IOException {
        File o = new File(gebiet, ORDNER);
        Files.createDirectories(o.toPath());
        StringBuilder b = new StringBuilder();
        b.append("# Kanten der Brandung (Werkbuch HalligHooge Teil A, 12.14, Schritt brandung; F160 A)\n# ").append(kopf).append('\n');
        b.append("# ost nord nx ny fuss krone neigung rau art   (Fuß UTM 32; Richtung zur See; m NHN; tan α; γ_f EurOtop; 5 Deckwerk, 4 Warft, 3 Deich, sonst Kante)\n");
        for (Kante k : kanten)
            b.append(String.format(Locale.ROOT, "%.2f %.2f %.4f %.4f %.3f %.3f %.4f %.2f %d%n", k.ost, k.nord, k.nx, k.ny, k.fuss, k.krone, k.neigung, k.rau, k.art));
        Files.writeString(new File(o, DATEI).toPath(), b.toString(), StandardCharsets.UTF_8);
    }
}
