package com.dan.lichen;

/**
 * Aussehen einer Bartflechte (seit LituyaBay1958 W6 Runde 7). Farben linear (Albedo). Die Farbart (R : G : B zur
 * Leuchtdichte) ist an Fotos gemessen ({@link com.dan.lichen.demo.LichenVorlage}); Höhe der Albedo, Längen und
 * Verzweigung sind gestaltet [G] nach den Fotos und der Literatur (Brodo, Sharnoff &amp; Sharnoff: Lichens of North America).
 */
public final class LichenLook {
    public enum Form {
        /**
         * Gefiedert: lange Hauptachsen mit dicht stehenden, kurzen, fast rechtwinkligen Seitenfasern (Fibrillen) wie eine
         * Fischgräte, gelbgrün (etwa *Usnea longissima*, *U. filipendula*).
         */
        USNEA,
        /** Haarig: lange, dünne, wenig verzweigte, verworrene Fäden, blass graugrün (etwa *Alectoria sarmentosa*). */
        ALECTORIA,
        /**
         * Sehr lang, kaum verzweigt, die Achse dicht mit kurzen, rechtwinkligen Fasern besetzt (*Usnea longissima*,
         * „Methusalembart“, an der Küste Alaskas bis über 1 m lang).
         */
        LONGISSIMA,
        /** Strauchig: kurz, aufrecht oder abstehend, buschig verzweigt, graugrün (etwa *Usnea* jung, *Evernia*). */
        SHRUB
    }

    public final String name;
    public final Form form;
    /** Albedo (linear) am Ansatz, in der Mitte, an den Spitzen. */
    public float[] base, mid, tip;
    /** Hauptachsen je Büschel von bis; Länge einer Achse (m) von bis; Dicke einer Achse (m). */
    public int axesMin, axesMax;
    public float lengthMin, lengthMax, thickness;
    /** Seitenfasern: Abstand (m), Länge (m) von bis, Winkel zur Achse (rad). */
    public float fibrilSpacing, fibrilMin, fibrilMax, fibrilAngle;
    /** Verzweigung der Achsen: Wahrscheinlichkeit je m, Winkel (rad). */
    public float branchPerM, branchAngle;
    /** Schwere (0 abstehend .. 1 hängend), Verworrenheit (Krümmung je m, rad). */
    public float gravity, tangle;
    /** Zufällige Abweichung der Farbe je Faden. */
    public float jitter;

    public LichenLook(String name, Form form) { this.name = name; this.form = form; }

    static float[] c(float y, float r, float g, float b) { return new float[]{y * r, y * g, y * b}; }

    /** Gefiedert, gelbgrün; Farbart gemessen an Bartflechten2 (R 1,04, G 1,04, B 0,43 je Leuchtdichte). */
    public static LichenLook usnea() {
        LichenLook l = new LichenLook("Usnea", Form.USNEA);
        l.base = c(0.20f, 1.08f, 1.04f, 0.42f);
        l.mid = c(0.30f, 1.04f, 1.045f, 0.43f);
        l.tip = c(0.36f, 1.02f, 1.04f, 0.55f);
        l.axesMin = 8; l.axesMax = 14;
        l.lengthMin = 0.06f; l.lengthMax = 0.28f; l.thickness = 0.0008f;
        l.fibrilSpacing = 0.0018f; l.fibrilMin = 0.002f; l.fibrilMax = 0.008f; l.fibrilAngle = 1.2f;
        l.branchPerM = 16; l.branchAngle = 0.5f;
        l.gravity = 0.8f; l.tangle = 5f; l.jitter = 0.1f;
        return l;
    }

    /** Haarig, verworren; Farbart gemessen an Bartflechten1 (R 1,08, G 1,01, B 0,68). */
    public static LichenLook alectoria() {
        LichenLook l = new LichenLook("Alectoria", Form.ALECTORIA);
        l.base = c(0.16f, 1.11f, 1.006f, 0.62f);
        l.mid = c(0.24f, 1.077f, 1.011f, 0.67f);
        l.tip = c(0.29f, 1.053f, 1.01f, 0.75f);
        l.axesMin = 22; l.axesMax = 38;
        l.lengthMin = 0.08f; l.lengthMax = 0.40f; l.thickness = 0.0005f;
        l.fibrilSpacing = 0.025f; l.fibrilMin = 0.004f; l.fibrilMax = 0.02f; l.fibrilAngle = 0.5f;
        l.branchPerM = 10; l.branchAngle = 0.35f;
        l.gravity = 0.9f; l.tangle = 16f; l.jitter = 0.14f;
        return l;
    }

    /**
     * Sehr lang (*Usnea longissima*): 3–6 Achsen, 0,35–1,2 m, kaum verzweigt, Fasern dicht und kurz. Farbart wie
     * {@link #usnea()} (Bartflechten2), etwas blasser [G].
     */
    public static LichenLook longissima() {
        LichenLook l = new LichenLook("Usnea longissima", Form.LONGISSIMA);
        l.base = c(0.20f, 1.06f, 1.04f, 0.45f);
        l.mid = c(0.30f, 1.03f, 1.045f, 0.47f);
        l.tip = c(0.34f, 1.01f, 1.04f, 0.58f);
        l.axesMin = 3; l.axesMax = 6;
        l.lengthMin = 0.35f; l.lengthMax = 1.2f; l.thickness = 0.0008f;
        l.fibrilSpacing = 0.0022f; l.fibrilMin = 0.003f; l.fibrilMax = 0.012f; l.fibrilAngle = 1.4f;
        l.branchPerM = 2.5f; l.branchAngle = 0.3f;
        l.gravity = 0.95f; l.tangle = 3f; l.jitter = 0.1f;
        return l;
    }

    /** Strauchig, graugrün; Farbart gemessen an Bartflechten3 (R 0,88, G 1,05, B 0,89). */
    public static LichenLook shrub() {
        LichenLook l = new LichenLook("Strauch", Form.SHRUB);
        l.base = c(0.17f, 0.80f, 1.07f, 0.87f);
        l.mid = c(0.25f, 0.88f, 1.05f, 0.87f);
        l.tip = c(0.30f, 0.90f, 1.04f, 0.94f);
        l.axesMin = 6; l.axesMax = 11;
        l.lengthMin = 0.03f; l.lengthMax = 0.07f; l.thickness = 0.0009f;
        l.fibrilSpacing = 0.0025f; l.fibrilMin = 0.0015f; l.fibrilMax = 0.005f; l.fibrilAngle = 1.0f;
        l.branchPerM = 60; l.branchAngle = 0.5f;
        l.gravity = 0.15f; l.tangle = 4f; l.jitter = 0.1f;
        return l;
    }

    public static float luma(float[] c) { return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]; }
}
