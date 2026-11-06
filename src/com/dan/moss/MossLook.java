package com.dan.moss;

/**
 * Aussehen eines Mooses (seit LituyaBay1958 W6 Runde 7): Farben in drei Stufen vom Grund des Polsters zur Spitze der
 * Triebe, Größe der Triebe und Blättchen, Dichte, Wölbung. Farben linear (nicht sRGB), als Albedo.
 * <p>
 * Die Farbart (Verhältnis R : G : B zur Leuchtdichte) ist an Fotos gemessen ({@link com.dan.moss.demo.MossVorlage}:
 * scharfe, grüne Bildpunkte, je ein Drittel nach Leuchtdichte); die Höhe der Albedo selbst ist gestaltet [G], weil
 * Fotos keine absolute Helligkeit tragen (Moos im Grünen um 0,08–0,15 [G], Spitzen heller; Runde 7b: 1,4-fach gegen den
 * ersten Bau, an Sitka-Stämmen war das Moos sonst dunkler als die Rinde).
 */
public final class MossLook {
    /** Albedo (linear) am Grund, in der Mitte und an der Spitze eines Triebs. */
    public float[] base, mid, tip;
    /** Länge eines Triebs (m) von bis. */
    public float shootMin, shootMax;
    /** Breite eines Blättchens (m) und Blättchen je Trieb. */
    public float leaf;
    public int leavesPerShoot;
    /** Triebe je m² (Deckung des Polsters) und Wölbung der Polster (Anteil der Höhe aus großen Buckeln). */
    public float shootsPerM2, cushion;
    /** Kante einer Kachel (m). */
    public float tile;
    /** Halbmesser der Polster (m) von bis und ihre Höhe über dem Grund (m); Anteil der Fläche, den Polster decken. */
    public float clumpMin, clumpMax, clumpHeight, clumpCover;
    /** Zufällige Abweichung der Farbe je Trieb (Anteil). */
    public float jitter;
    /** Name für Prüfungen und Texte. */
    public final String name;

    public MossLook(String name) { this.name = name; }

    static float[] c(float y, float r, float g, float b) { return new float[]{y * r, y * g, y * b}; }

    /**
     * Astmoos, gefiedert (etwa *Hylocomium*, *Kindbergia*, *Isothecium* an Stämmen im Küstenregenwald): lockere,
     * gefiederte Triebe, an der Spitze gelbgrün. Farbart gemessen an Moos1–Moos3 (Mittel), Moos4 heller und gelber.
     */
    public static MossLook feather() {
        MossLook m = new MossLook("gefiedert");
        // Farbe/Y gemessen (dunkles, mittleres, helles Drittel): R 0,50 / 0,60 / 0,72, G 1,23 / 1,20 / 1,16, B 0,20 / 0,18 / 0,24
        m.base = c(0.03f, 0.50f, 1.23f, 0.20f);
        m.mid = c(0.09f, 0.60f, 1.20f, 0.18f);
        m.tip = c(0.19f, 0.78f, 1.15f, 0.16f);
        m.shootMin = 0.004f; m.shootMax = 0.012f;
        m.leaf = 0.0009f; m.leavesPerShoot = 20;
        m.shootsPerM2 = 600_000; m.cushion = 0.35f;
        m.clumpMin = 0.004f; m.clumpMax = 0.011f; m.clumpHeight = 0.008f; m.clumpCover = 0.8f;
        m.tile = 0.1f; m.jitter = 0.14f;
        return m;
    }

    /**
     * Polstermoos, kurz und dicht (etwa *Hypnum*, *Grimmia* auf Fels und Rinde): kurze Triebe, samtige Kissen. Farbart
     * gemessen an Moos2 und Moos3 (die Kissen), die Spitzen wie Moos4.
     */
    public static MossLook cushion() {
        MossLook m = new MossLook("Polster");
        m.base = c(0.028f, 0.55f, 1.21f, 0.27f);
        m.mid = c(0.085f, 0.66f, 1.18f, 0.24f);
        m.tip = c(0.155f, 0.85f, 1.13f, 0.20f);
        m.shootMin = 0.0018f; m.shootMax = 0.0045f;
        m.leaf = 0.0005f; m.leavesPerShoot = 8;
        m.shootsPerM2 = 2_600_000; m.cushion = 0.3f;
        m.clumpMin = 0.003f; m.clumpMax = 0.007f; m.clumpHeight = 0.005f; m.clumpCover = 0.85f;
        m.tile = 0.06f; m.jitter = 0.1f;
        return m;
    }

    /** Leuchtdichte einer linearen Farbe. */
    public static float luma(float[] c) { return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]; }
}
