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
 * Die Zonen der Salzwiese (seit P13b, Werkbuch HalligHooge Teil A, 12.14; F151–F154 A): eine Karte zu 1 m über Fenne
 * und Salzwiese der {@link Gestalt} mit der Zone jedes Bildpunkts und seiner Höhe über dem örtlichen mittleren
 * Tidehochwasser (MThw). Ohne Grafik: {@code physics.vegetation} rechnet daraus Rauheit und Bedeckung, das Bild die Halme.
 * <p>
 * <b>Zonen</b> nach der Höhe über MThw [Literatur, G]: Pionierzone (Queller, Schlickgras) von {@code -0,40 m} bis MThw,
 * untere Salzwiese (Andel, Strandflieder) bis {@code +0,35 m}, obere (Rotschwingel, Strand-Beifuß) darüber; darunter
 * kein Bewuchs.
 * <p>
 * <b>Ablage</b> {@code vegetation/zonen/}: {@code zonen.txt} (Zelle, Bildpunkte je Kachel, Liste der Kacheln mit ihrer
 * Südwestecke) und je Kachel {@code E<ost>_N<nord>.rg8}: zeilenweise von Nord nach Süd je Bildpunkt zwei Byte: Zone,
 * Höhe über MThw (Byte b: b/100 − 1,28 m, 1 cm; 0: ohne Wert). Die Kacheln decken dieselben Flächen wie die Gestalt.
 */
public final class Zonen {
    public static final String ORDNER = "vegetation" + File.separator + "zonen", DATEI = "zonen.txt";
    public static final int KEINE = 0, PIONIER = 1, UNTEN = 2, OBEN = 3;
    /**
     * Seit F155 A: Fenne hinter dem Sommerdeich, ohne Tidezone (die täglichen Tiden erreichen sie nicht); Pflanzen wie
     * die obere Salzwiese, beweidet. Im Bild bleibt sie {@link Gestalt#FENNE}.
     */
    public static final int FENNE = 4;
    /**
     * Arten der Gestalt mit Zone (für das Bild): die Salzwiese nach Zone, die Fenne unten. Die Fenne über +0,35 m bleibt
     * {@link Gestalt#FENNE}. Wie die Gras-Arten 1–4 behandelt; 5 (Deckwerk) und ab 10 sind belegt.
     */
    public static final int ART_PIONIER = 6, ART_SALZ_UNTEN = 7, ART_SALZ_OBEN = 8, ART_FENNE_UNTEN = 9;
    /** Seit F158 A: Salzwiese der Karte unter der Pionierzone (unter MThw − 0,40 m): Schlick ohne Pflanzen im Bild. */
    public static final int ART_SCHLICK = 13;

    /** Die Art der Gestalt ohne Zone: 6–8 sind Salzwiese, 9 ist Fenne (das Bild mit ausgeschalteter Salzwiese). */
    public static int ohneZone(int art) { return art >= ART_PIONIER && art <= ART_SALZ_OBEN || art == ART_SCHLICK ? Gestalt.SALZWIESE : art == ART_FENNE_UNTEN ? Gestalt.FENNE : art; }

    public record Kachel(double ost, double nord, byte[] daten) { }

    public final double zelle;
    public final int n;
    public final List<Kachel> kacheln;

    public Zonen(double zelle, int n, List<Kachel> kacheln) {
        this.zelle = zelle;
        this.n = n;
        this.kacheln = Collections.unmodifiableList(new ArrayList<>(kacheln));
    }

    public static String name(int zone) {
        return switch (zone) { case PIONIER -> "Pionierzone"; case UNTEN -> "untere Salzwiese"; case OBEN -> "obere Salzwiese"; case FENNE -> "Fenne"; default -> "ohne"; };
    }

    /** Zone aus der Höhe über MThw (m) [Literatur, G]; NaN: keine. */
    public static int zone(double ueber, double pionierUnten, double grenzeOben) {
        if (Double.isNaN(ueber) || ueber < pionierUnten) return KEINE;
        if (ueber < 0) return PIONIER;
        return ueber < grenzeOben ? UNTEN : OBEN;
    }

    public static int hoeheByte(double ueber) { return Double.isNaN(ueber) ? 0 : Math.max(1, Math.min(255, (int) Math.round((ueber + 1.28) * 100))); }

    public static double hoehe(int b) { return b == 0 ? Double.NaN : b / 100.0 - 1.28; }

    public double kantenlaenge() { return zelle * n; }

    public Kachel kachel(double ost, double nord) {
        double s = kantenlaenge();
        for (Kachel k : kacheln) if (ost >= k.ost && ost < k.ost + s && nord >= k.nord && nord < k.nord + s) return k;
        return null;
    }

    /** {Zone, Höhenbyte} am Ort; {0, 0} außerhalb. */
    public int[] bei(double ost, double nord) {
        Kachel k = kachel(ost, nord);
        if (k == null) return new int[] {0, 0};
        int i = Math.max(0, Math.min(n - 1, (int) Math.floor((ost - k.ost) / zelle))), j = Math.max(0, Math.min(n - 1, (int) Math.floor((k.nord + kantenlaenge() - nord) / zelle)));
        int o = 2 * (j * n + i);
        return new int[] {k.daten[o] & 0xFF, k.daten[o + 1] & 0xFF};
    }

    /**
     * Die Gestalt mit den Zonen in der Art (für das Bild, F152 A): Salzwiese nach Zone ({@link #ART_PIONIER},
     * {@link #ART_SALZ_UNTEN}, {@link #ART_SALZ_OBEN}), Fenne unter +0,35 m (beweidet, auch in der Pionierzone) als
     * {@link #ART_FENNE_UNTEN}; die Zone {@link #FENNE} (F155 A) bleibt Fenne; Salzwiese unter der Pionierzone wird
     * {@link #ART_SCHLICK} (F158 A). Alles andere bleibt; {@link #ohneZone} macht es rückgängig.
     */
    public Gestalt aufGestalt(Gestalt g) {
        List<Gestalt.Kachel> neu = new ArrayList<>();
        for (Gestalt.Kachel k : g.kacheln) {
            byte[] d = k.daten().clone();
            int nn = g.n;
            Kachel zk = kachel(k.ost() + 0.5 * g.kantenlaenge(), k.nord() + 0.5 * g.kantenlaenge());
            if (zk == null) { neu.add(new Gestalt.Kachel(k.ost(), k.nord(), d)); continue; }
            for (int j = 0; j < nn; j++)
                for (int i = 0; i < nn; i++) {
                    int o = 2 * (j * nn + i), art = d[o] & 0xFF;
                    if (art != Gestalt.FENNE && art != Gestalt.SALZWIESE) continue;
                    double ost = k.ost() + (i + 0.5) * g.zelle, nord = k.nord() + g.kantenlaenge() - (j + 0.5) * g.zelle;
                    int zi = (int) Math.floor((ost - zk.ost) / zelle), zj = (int) Math.floor((zk.nord + kantenlaenge() - nord) / zelle);
                    if (zi < 0 || zj < 0 || zi >= n || zj >= n) continue;
                    int z = zk.daten[2 * (zj * n + zi)] & 0xFF, hb = zk.daten[2 * (zj * n + zi) + 1] & 0xFF;
                    int a = art;
                    if (art == Gestalt.SALZWIESE) a = z == PIONIER ? ART_PIONIER : z == OBEN ? ART_SALZ_OBEN : z == UNTEN ? ART_SALZ_UNTEN : z == KEINE && hb != 0 ? ART_SCHLICK : art;
                    else if (z == UNTEN || z == PIONIER) a = ART_FENNE_UNTEN;
                    d[o] = (byte) a;
                }
            neu.add(new Gestalt.Kachel(k.ost(), k.nord(), d));
        }
        return new Gestalt(g.zelle, g.n, neu);
    }

    /** Liest {@code <gebiet>/vegetation/zonen/}; null, wenn es die Ablage nicht gibt. */
    public static Zonen lies(File gebiet) throws IOException {
        File o = new File(gebiet, ORDNER), f = new File(o, DATEI);
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
                    byte[] d = Files.readAllBytes(new File(o, Gestalt.name(e, no)).toPath());
                    if (d.length != 2 * n * n) throw new IOException("Zonen: " + Gestalt.name(e, no) + " hat " + d.length + " Byte, erwartet " + 2 * n * n);
                    l.add(new Kachel(e, no, d));
                }
                default -> { }
            }
        }
        if (!(zelle > 0) || n <= 0) throw new IOException("Zonen: Zelle oder Bildpunkte fehlen in " + f);
        return new Zonen(zelle, n, l);
    }

    /** Schreibt die Ablage unter {@code <gebiet>/vegetation/zonen/}; {@code kopf}: Kommentar. */
    public void schreibe(File gebiet, String kopf) throws IOException {
        File o = new File(gebiet, ORDNER);
        Files.createDirectories(o.toPath());
        StringBuilder b = new StringBuilder();
        b.append("# Zonen der Salzwiese (Werkbuch HalligHooge Teil A, 12.14, Schritt zonen; F151–F154 A)\n");
        b.append("# ").append(kopf).append('\n');
        b.append("# Je Kachel E<ost>_N<nord>.rg8: zeilenweise von Nord, je Bildpunkt Zone (0 ohne, 1 Pionierzone, 2 untere, 3 obere Salzwiese)\n");
        b.append("# und Höhe über MThw (Byte b: b/100 − 1,28 m; 0 ohne Wert)\n");
        b.append(String.format(Locale.ROOT, "zelle %s%nbildpunkte %d%n", zelle, n));
        for (Kachel k : kacheln) {
            b.append(String.format(Locale.ROOT, "kachel %.0f %.0f%n", k.ost, k.nord));
            Files.write(new File(o, Gestalt.name(k.ost, k.nord)).toPath(), k.daten);
        }
        Files.writeString(new File(o, DATEI).toPath(), b.toString(), StandardCharsets.UTF_8);
    }
}
