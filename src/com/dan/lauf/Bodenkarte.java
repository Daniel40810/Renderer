package com.dan.lauf;

import com.dan.core.gitter.Kornfeld;
import com.dan.core.szene.Gestalt;
import com.dan.physics.swash.Boden;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Der Boden des Auflaufs aus den Klassen des Geländes (Seegang Werkbuch 80, S6c-3, SF89 A; Abweichung A8): je Ort (UTM)
 * Manning-Beiwert und Durchlässigkeit für {@code physics.swash}. Bis S6c-3 rechnete der Löser überall Sand d50 0,2 mm;
 * Deich und Deckwerk nahmen darum Wasser auf, das ein Deich aus Klei mit Grasnarbe in Minuten nicht nimmt (Datei 79:
 * dicht statt Sand gab am Deich den Faktor 1,4, am Deckwerk 3,0 mehr Überlauf).
 * <ul>
 * <li><b>Gestalt</b> ({@code gestalt/}, 0,5 m, über dem Land): Deich und Warft Klei mit kurzer Grasnarbe; Fenne,
 * Salzwiese und Grasweg Klei mit höherem Gras; Deckwerk gesetzte Steine, dicht; Asphalt, Beton, Klinker, Holz glatt und
 * dicht; Schotter rau und durchlässig; Graben, Priel, Fething Schlick (Kennwerte {@link Boden}).</li>
 * <li><b>Sonst</b> (Watt, Sände, See: keine Gestalt) das Korn des Gebiets ({@code sediment/}, d50 aus EasyGSH, 100 m,
 * bilinear zwischen den Mitten wie {@link Kornfeld}): Manning aus k_s = 2,5 d50, Durchlässigkeit nach Hazen, im Schlick
 * {@link Boden#K_SCHLICK}. Ohne Kornfeld Sand d50 0,2 mm wie bis S6c-3.</li>
 * </ul>
 * Knotet {@code core} (Gestalt, Kornfeld) an {@code physics.swash} (Boden); darum in {@code lauf}.
 */
public final class Bodenkarte implements Auflauf.Bodenquelle {
    public static final String VERSION = "0.2";
    /**
     * Seit 0.2 (Seegang Werkbuch 83, S6c-2b): Durchlässigkeit unter dieser Grenze rechnet der Löser als dicht (m/s) [G].
     * Klei (1e-8) nimmt nach Green-Ampt in einer Viertelstunde Sturm rund 1 mm auf; das ist für Auflauf und Überlauf ohne
     * Belang, hielt aber jede einmal nasse Kachel als Sickerwasser im Löser wach (Kosten, L4o 1,14 ms je Bild).
     */
    public static final double K_DICHT_UNTER = 1e-7;
    /** Klassen (dritter Wert von {@link #werte}). */
    public static final int KORN = 0, KLEI_KURZ = 1, KLEI_GRAS = 2, DECKWERK = 3, GLATT = 4, SCHOTTER = 5, SCHLICK = 6, SAND = 7;
    /** Korn ohne Kornfeld (m). */
    public static final double D50_VORGABE = 0.2e-3;

    private final Gestalt gestalt;
    private final Kornfeld korn;

    public Bodenkarte(Gestalt gestalt, Kornfeld korn) {
        this.gestalt = gestalt;
        this.korn = korn;
    }

    /** Aus dem Gebiet (Kornfeld, wenn vorhanden) und der Gestalt (null: keine). */
    public static Bodenkarte aus(File gebiet, Gestalt gestalt) throws IOException {
        return new Bodenkarte(gestalt, gebiet != null && Kornfeld.vorhanden(gebiet) ? Kornfeld.lies(gebiet) : null);
    }

    public boolean hatGestalt() { return gestalt != null; }

    public boolean hatKorn() { return korn != null; }

    @Override public double[] werte(double ost, double nord) {
        double[] w = werteRoh(ost, nord);
        if (w[1] < K_DICHT_UNTER) w[1] = Boden.K_DICHT;
        return w;
    }

    /** Die Kennwerte nach der Klasse, vor der Grenze {@link #K_DICHT_UNTER} (bis 0.1 die Werte selbst). */
    public double[] werteRoh(double ost, double nord) {
        int art = gestalt != null ? gestalt.bei(ost, nord)[0] : Gestalt.KEINE;
        switch (art) {
            case Gestalt.DEICH, Gestalt.WARFT -> { return new double[] {Boden.N_GRAS_KURZ, Boden.K_KLEI, KLEI_KURZ}; }
            case Gestalt.FENNE, Gestalt.SALZWIESE, Gestalt.GRASWEG -> { return new double[] {Boden.N_GRAS, Boden.K_KLEI, KLEI_GRAS}; }
            case Gestalt.DECKWERK -> { return new double[] {Boden.N_STEIN, Boden.K_DICHT, DECKWERK}; }
            case Gestalt.ASPHALT, Gestalt.BETON, Gestalt.KLINKER, Gestalt.HOLZ -> { return new double[] {Boden.N_GLATT, Boden.K_DICHT, GLATT}; }
            case Gestalt.SCHOTTER -> { return new double[] {Boden.N_SCHOTTER, Boden.K_SCHOTTER, SCHOTTER}; }
            case Gestalt.GRABEN, Gestalt.PRIEL, Gestalt.FETHING -> {
                double d = Math.min(d50(ost, nord), Boden.D_SAND);
                return new double[] {Boden.manningSand(d), Boden.K_SCHLICK, SCHLICK};
            }
            default -> {
                double d = d50(ost, nord);
                return new double[] {Boden.manningSand(d), Boden.durchlaessigkeit(d), korn != null ? KORN : SAND};
            }
        }
    }

    @Override public String name(int klasse) {
        return switch (klasse) {
            case KLEI_KURZ -> "Klei, kurzes Gras (Deich, Warft)";
            case KLEI_GRAS -> "Klei, Gras (Fenne, Salzwiese)";
            case DECKWERK -> "Deckwerk";
            case GLATT -> "glatt (Asphalt, Beton, Klinker, Holz)";
            case SCHOTTER -> "Schotter";
            case SCHLICK -> "Schlick (Graben, Priel, Fething)";
            case SAND -> String.format(Locale.GERMAN, "Sand d50 %.1f mm", 1e3 * D50_VORGABE);
            default -> "Korn aus dem Kornfeld";
        };
    }

    /** d50 (m) am Ort: Kornfeld bilinear zwischen den Mitten der Pixel, am Rand der nächste Wert; sonst {@link #D50_VORGABE}. */
    public double d50(double ost, double nord) {
        if (korn == null) return D50_VORGABE;
        double fx = (ost - korn.x0) / korn.zelle - 0.5, fy = (nord - korn.y0) / korn.zelle - 0.5;
        fx = Math.max(0, Math.min(korn.nx - 1, fx));
        fy = Math.max(0, Math.min(korn.ny - 1, fy));
        int i = Math.min(korn.nx - 2, (int) fx), j = Math.min(korn.ny - 2, (int) fy);
        if (korn.nx < 2 || korn.ny < 2) return korn.werte[Kornfeld.D50][0];
        double ax = fx - i, ay = fy - j;
        float[] w = korn.werte[Kornfeld.D50];
        double v = (1 - ay) * ((1 - ax) * w[j * korn.nx + i] + ax * w[j * korn.nx + i + 1]) + ay * ((1 - ax) * w[(j + 1) * korn.nx + i] + ax * w[(j + 1) * korn.nx + i + 1]);
        return v > 0 && Double.isFinite(v) ? v : D50_VORGABE;
    }
}
