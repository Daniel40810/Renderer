package com.dan.gertsch;

import java.util.Locale;

/**
 * Die Kennwerte eines Bilds, wie {@link Messung} sie findet: Tonstufen, Helligkeit, Farbton und Akzente, Größe der
 * hellen Flecken und dunklen Lücken, Richtung der Linien. Die gemessenen Vorlagen liegen als Zahlen in
 * {@link Vorlagen}; dieselbe Messung auf einem gerenderten Bild sagt später, wie nah es ihnen kommt.
 * <p>
 * Größen sind Anteile der Bildbreite (0,01 = ein Hundertstel der Breite), damit Vorlagen und Bilder verschiedener
 * Auflösung vergleichbar sind. Alle Felder sind offen.
 */
public final class Vorlage {
    /** Kurzname (etwa „sommer“), Titel zum Zeigen, Datei, aus der gemessen wurde. */
    public String name = "", titel = "", quelle = "";
    /** Größe des Originals und Breite, auf der gemessen wurde (Bildpunkte). */
    public int breite, hoehe, arbeitsbreite;
    /** Einstellungen der Messung (zum Wiederholen): Qualität, Zahl der Stufen, seed. */
    public String qualitaet = "HIGH";
    public int stufen;
    public long seed;

    /** Tonstufen, aufsteigend nach L*. */
    public Palette palette;
    /** Anteil der Fläche je zehn L* (0–10, 10–20, … 90–100). */
    public double[] helligkeit = new double[10];
    /** Perzentile 5, 50 und 95 von L*. */
    public double l5, l50, l95;
    /** Anteil des dunklen Grunds (L* unter {@link Messung#grundL}). */
    public double grund;
    /** Herrschender Farbton h_ab in Grad (Mittel der Farbvektoren, nach Buntheit gewichtet) und mittlere Buntheit C*. */
    public double farbton, buntheit;
    /** Anteil der Akzente (bunt und im Farbton weit weg vom herrschenden) und ihre mittlere Farbe {L*, a*, b*}. */
    public double akzent;
    public double[] akzentLab = new double[3];

    /** Leiter der Fleckgrößen: Durchmesser 2r als Anteil der Breite, je Radius der Granulometrie. */
    public double[] durchmesser = new double[0];
    /** Summenverteilung der hellen Flecken (Öffnung) und dunklen Lücken (Schließung) über die Leiter, letzter Wert 1. */
    public double[] hell = new double[0], dunkel = new double[0];
    /** Median der Durchmesser (Anteil der Breite): die Hälfte der Fleckmasse liegt in kleineren Flecken. */
    public double hellD50, dunkelD50;
    /**
     * Anteil der hellen (dunklen) Struktur, der in Flecken bis zur größten Stufe der Leiter steckt; der Rest sind
     * große Flächen (Schnee, Himmel, ein großes Blatt).
     */
    public double fleckHell, fleckDunkel;

    /** Gerichtete Energie je 15° (0° waagerecht, 90° senkrecht, 45° von links unten nach rechts oben), Summe 1. */
    public double[] richtung = new double[12];
    /** Anteil der Linien zwischen 60° und 120°, Kohärenz (0 Flecken ohne Richtung, 1 nur Linien), Hauptrichtung in Grad. */
    public double senkrecht, kohaerenz, hauptrichtung;

    /** Nur nach einer Messung: das Bild in Lab auf der Arbeitsbreite, und wie lange die Messung dauerte. */
    public Bild bild;
    public double sekunden;

    /** Kennwerte als Text, eine Größe je Zeile. */
    public String bericht() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.GERMAN, "%s (%s): %d × %d, gemessen auf %d, %s, %d Stufen, seed %d%n",
                titel.isEmpty() ? name : titel, quelle, breite, hoehe, arbeitsbreite, qualitaet, stufen, seed));
        sb.append("  Tonstufen   ");
        for (int i = 0; i < palette.size(); i++)
            sb.append(String.format(Locale.GERMAN, "L%.0f/%+.0f/%+.0f %.0f%%  ", palette.lab[i][0], palette.lab[i][1], palette.lab[i][2], 100 * palette.anteil[i]));
        sb.append(String.format(Locale.GERMAN, "%n  Helligkeit  L* 5 %% %.1f, Median %.1f, 95 %% %.1f, Grund %.1f %%%n", l5, l50, l95, 100 * grund));
        sb.append(String.format(Locale.GERMAN, "  Farbe       Farbton %.0f°, Buntheit %.1f, Akzente %.1f %% (L%.0f/%+.0f/%+.0f)%n",
                farbton, buntheit, 100 * akzent, akzentLab[0], akzentLab[1], akzentLab[2]));
        sb.append(String.format(Locale.GERMAN, "  Flecken     hell %.2f %% der Breite (in Flecken %.0f %%), dunkel %.2f %% (%.0f %%)%n",
                100 * hellD50, 100 * fleckHell, 100 * dunkelD50, 100 * fleckDunkel));
        sb.append(String.format(Locale.GERMAN, "  Richtung    senkrecht %.0f %%, Kohärenz %.2f, Hauptrichtung %.0f°%n", 100 * senkrecht, kohaerenz, hauptrichtung));
        return sb.toString();
    }

    /** Java-Quelltext, der diese Kennwerte als Preset baut (für {@link Vorlagen}). */
    public String alsJava(String methode, String javadoc) {
        StringBuilder s = new StringBuilder();
        s.append("    /** ").append(javadoc).append(" */\n");
        s.append("    public static Vorlage ").append(methode).append("() {\n");
        s.append("        Vorlage v = new Vorlage();\n");
        s.append(String.format(Locale.ROOT, "        v.name = \"%s\"; v.titel = \"%s\"; v.quelle = \"%s\";%n", name, titel, quelle));
        s.append(String.format(Locale.ROOT, "        v.breite = %d; v.hoehe = %d; v.arbeitsbreite = %d; v.qualitaet = \"%s\"; v.stufen = %d; v.seed = %dL;%n",
                breite, hoehe, arbeitsbreite, qualitaet, stufen, seed));
        s.append("        v.palette = new Palette(new double[][]{");
        for (int i = 0; i < palette.size(); i++)
            s.append(i == 0 ? "" : ", ").append(feld(palette.lab[i], 2));
        s.append("},\n                new double[]").append(feld(palette.anteil, 4)).append(");\n");
        s.append("        v.helligkeit = new double[]").append(feld(helligkeit, 4)).append(";\n");
        s.append(String.format(Locale.ROOT, "        v.l5 = %.2f; v.l50 = %.2f; v.l95 = %.2f; v.grund = %.4f;%n", l5, l50, l95, grund));
        s.append(String.format(Locale.ROOT, "        v.farbton = %.2f; v.buntheit = %.2f; v.akzent = %.4f; v.akzentLab = new double[]%s;%n",
                farbton, buntheit, akzent, feld(akzentLab, 2)));
        s.append("        v.durchmesser = new double[]").append(feld(durchmesser, 5)).append(";\n");
        s.append("        v.hell = new double[]").append(feld(hell, 4)).append(";\n");
        s.append("        v.dunkel = new double[]").append(feld(dunkel, 4)).append(";\n");
        s.append(String.format(Locale.ROOT, "        v.hellD50 = %.5f; v.dunkelD50 = %.5f; v.fleckHell = %.4f; v.fleckDunkel = %.4f;%n",
                hellD50, dunkelD50, fleckHell, fleckDunkel));
        s.append("        v.richtung = new double[]").append(feld(richtung, 4)).append(";\n");
        s.append(String.format(Locale.ROOT, "        v.senkrecht = %.4f; v.kohaerenz = %.4f; v.hauptrichtung = %.2f;%n", senkrecht, kohaerenz, hauptrichtung));
        s.append("        return v;\n    }\n");
        return s.toString();
    }

    static String feld(double[] v, int stellen) {
        StringBuilder s = new StringBuilder("{");
        for (int i = 0; i < v.length; i++) s.append(i == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%." + stellen + "f", v[i]));
        return s.append('}').toString();
    }
}
