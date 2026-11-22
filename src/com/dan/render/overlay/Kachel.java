package com.dan.render.overlay;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Eine Kachel der {@link Einblendung} (seit W2, Werkbuch HalligHooge 113, F186 A): ein Bild fester Größe, in Java2D
 * gemalt, mit einer Lage am Rand des Bilds oder an einem Ort der Welt. Gemalt wird im Faden der Anwendung
 * ({@link #male}); die Einblendung lädt das Bild hoch, wenn es sich geändert hat, und zwar nur dann.
 * <p>
 * Die Bildpunkte werden beim Malen in linear vormultipliziertes RGBA (float16) umgerechnet: so filtert die Karte linear
 * ohne dunkle Säume, und das Mischen in ein sRGB-Ziel geschieht linear wie bei allen Auflagen.
 * <p>
 * Lage, Drehung und Deckung dürfen sich jederzeit ändern; sie kosten kein Hochladen.
 */
public final class Kachel {
    /** Woran eine Kachel am Bild hängt: die Ecke oder Mitte einer Kante, von der aus {@code dx, dy} zählen. */
    public enum Ecke { OBEN_LINKS, OBEN_MITTE, OBEN_RECHTS, LINKS_MITTE, MITTE, RECHTS_MITTE, UNTEN_LINKS, UNTEN_MITTE, UNTEN_RECHTS }

    public final String name;
    public final int breite, hoehe;

    // ---- Lage (vom Faden der Anwendung gesetzt, vom Faden der Karte gelesen)
    volatile boolean inWelt;
    volatile Ecke ecke = Ecke.OBEN_LINKS;
    volatile int dx, dy;
    volatile double ost, nord, h;
    /** Bezugspunkt der Kachel (0 … 1 von links oben), der über dem Ort der Welt steht. */
    volatile double bx = 0.5, by = 1;
    volatile boolean verdecken = true;

    /** An oder aus; eine ausgeschaltete Kachel kostet nichts. */
    public volatile boolean sichtbar = true;
    /** Deckung 0 … 1. */
    public volatile double deckung = 1;
    /** Drehung um die Mitte der Kachel (Bogenmaß, im Bild im Uhrzeigersinn). */
    public volatile double drehung;
    /**
     * Dreht mit dem Blick: zur {@link #drehung} kommt in jedem Bild −Richtung des Blicks (eine Kompassrose, deren Nord
     * nach Norden zeigt). Ohne Umweg über die Anwendung, also ohne Verzug.
     */
    public volatile boolean nachNorden;
    /**
     * Seit W3: gezeigte Größe in Bildpunkten (0: die eigene). Eine kleine Kachel einer Farbe deckt so eine große Fläche
     * (Balken und Schwarz des Kinos), ohne viel hochzuladen; gefiltert wird linear.
     */
    public volatile int zeigeBreite, zeigeHoehe;

    /** Ab diesem Abstand vom Auge (m) blendet eine Kachel in der Welt aus (ab 70 % davon weich); 0: ohne Grenze. */
    public volatile double weite;
    /** Kacheln in der Welt mit {@code ausweichen} überdecken einander und die Kacheln am Rand nicht: die spätere tritt zurück. */
    public volatile boolean ausweichen;
    /** Rang beim Ausweichen: kleiner zuerst. */
    public volatile int rang;

    // ---- Inhalt
    private final BufferedImage bild;
    private short[] daten;
    private boolean neu;
    long fassung;

    /** Zuletzt im Bild (vom Faden der Karte gesetzt): links, oben, Deckung nach Abstand; NaN, wenn nicht im Bild. */
    volatile double zuletztX = Double.NaN, zuletztY = Double.NaN, zuletztDrehung;

    /** Lage im letzten Bild: links, oben (Bildpunkte; NaN: nicht gezeichnet), Drehung (Bogenmaß). Für Prüfungen. */
    public double[] zuletzt() { return new double[] {zuletztX, zuletztY, zuletztDrehung}; }

    Kachel(String name, int breite, int hoehe) {
        if (breite < 1 || hoehe < 1 || breite > 4096 || hoehe > 4096) throw new IllegalArgumentException(name + ": Größe " + breite + " × " + hoehe);
        this.name = name;
        this.breite = breite;
        this.hoehe = hoehe;
        bild = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_ARGB);
    }

    /** Hängt die Kachel an das Bild: an {@code e}, um {@code dx, dy} Bildpunkte nach innen versetzt. */
    public Kachel imBild(Ecke e, int dx, int dy) {
        this.ecke = e;
        this.dx = dx;
        this.dy = dy;
        inWelt = false;
        return this;
    }

    /**
     * Hängt die Kachel an einen Ort der Welt (UTM, m NHN): der Bezugspunkt {@code (bx, by)} der Kachel (0 … 1) steht über
     * dem Bildpunkt des Orts. Mit {@code verdecken} blendet sie aus, wo die Szene vor dem Ort liegt.
     */
    public Kachel inWelt(double ost, double nord, double h, double bx, double by, boolean verdecken) {
        this.ost = ost;
        this.nord = nord;
        this.h = h;
        this.bx = bx;
        this.by = by;
        this.verdecken = verdecken;
        inWelt = true;
        return this;
    }

    /** Der Ort in der Welt (Ost, Nord, m NHN; null am Rand). */
    public double[] ort() { return inWelt ? new double[] {ost, nord, h} : null; }

    /** Malt die Kachel neu: {@code maler} bekommt eine leere, durchsichtige Fläche mit Kantenglättung. */
    public void male(Consumer<Graphics2D> maler) {
        synchronized (bild) {
            Graphics2D g = bild.createGraphics();
            try {
                g.setComposite(AlphaComposite.Clear);
                g.fillRect(0, 0, breite, hoehe);
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                maler.accept(g);
            } finally {
                g.dispose();
            }
            short[] d = new short[4 * breite * hoehe];
            int[] p = bild.getRGB(0, 0, breite, hoehe, null, 0, breite);
            for (int i = 0; i < p.length; i++) {
                int c = p[i], a8 = c >>> 24;
                float a = a8 / 255f;
                d[4 * i] = Float.floatToFloat16(LINEAR[(c >> 16) & 0xFF] * a);
                d[4 * i + 1] = Float.floatToFloat16(LINEAR[(c >> 8) & 0xFF] * a);
                d[4 * i + 2] = Float.floatToFloat16(LINEAR[c & 0xFF] * a);
                d[4 * i + 3] = Float.floatToFloat16(a);
            }
            synchronized (this) {
                daten = d;
                neu = true;
                fassung++;
            }
        }
    }

    /** Eine Kopie des zuletzt gemalten Bilds (sRGB, nicht vormultipliziert), etwa für Prüfungen. */
    public BufferedImage bild() {
        synchronized (bild) {
            BufferedImage k = new BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_ARGB);
            k.setRGB(0, 0, breite, hoehe, bild.getRGB(0, 0, breite, hoehe, null, 0, breite), 0, breite);
            return k;
        }
    }

    /** Die Bildpunkte zum Hochladen, wenn neu gemalt (sonst null); danach gelten sie als hochgeladen. */
    synchronized short[] nimmNeu() {
        if (!neu) return null;
        neu = false;
        return daten;
    }

    /** Hat die Kachel schon einmal Inhalt bekommen? */
    synchronized boolean gemalt() { return daten != null; }

    /** Gezeigte Breite und Höhe (seit W3: {@link #zeigeBreite}, sonst die eigene). */
    int zb() { int z = zeigeBreite; return z > 0 ? z : breite; }

    int zh() { int z = zeigeHoehe; return z > 0 ? z : hoehe; }

    /** Links oben der Kachel im Bild (W × H), für die Lage am Rand. */
    double[] amRand(int W, int H) {
        Ecke e = ecke;
        int sx = e.ordinal() % 3, sy = e.ordinal() / 3, b = zb(), h = zh();
        double x = sx == 0 ? dx : sx == 1 ? (W - b) / 2.0 + dx : W - b - dx;
        double y = sy == 0 ? dy : sy == 1 ? (H - h) / 2.0 + dy : H - h - dy;
        return new double[] {Math.round(x), Math.round(y)};
    }

    @Override public String toString() { return name + " (" + breite + " × " + hoehe + (inWelt ? ", Welt" : ", " + ecke) + ")"; }

    /** sRGB (8 bit) nach linear. */
    static final float[] LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            double c = i / 255.0;
            LINEAR[i] = (float) (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
    }
}
