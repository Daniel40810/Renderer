package com.dan.forest;

/**
 * Bäume, die fallen (aus LituyaBay1958, Werkbuch Welle W5, E11 „524 m“) [G]: in den Sekunden um die Ankunft einer
 * Wasserzunge, einer Lawine oder einer Druckwelle nimmt die App einen Baum aus {@code ForestGpu} und reicht ihn an
 * {@code gl/TreeFallGl}; gezeichnet wird derselbe Baum (Art, Variante, Ort, Drehung, Größe aus {@link ForestCulture}) mit
 * seinem Netz, aber gekippt. Danach zeigt {@code ForestGpu} den Platz in seiner Folge ({@link TreeStates}).
 * <ul>
 * <li>ein Luftstoß {@link #AIR_BLAST} s vor der Ankunft neigt ihn um einige Grad (oben mehr, er zittert);</li>
 * <li>{@link TreeStates#FELLED umgelegt} und {@link TreeStates#GONE fort}: er kippt um den Fuß in Laufrichtung, erst
 * langsam, dann schnell (u²(1,7 − 0,7u)), bis er auf dem Hang liegt (90° minus der Neigung in Fallrichtung), die Krone
 * peitscht nach, der Stamm federt; die Krone reißt ab, der Wurzelteller hebt sich; fort: danach treibt er mit dem Wasser
 * und sinkt (bis 12 m);</li>
 * <li>{@link TreeStates#BROKEN gebrochen}: der Stamm bricht in 15–45 % der Höhe, die Spitze kippt, der Stumpf bleibt;</li>
 * <li>{@link TreeStates#STRIPPED entrindet}: er bleibt stehen, schwingt aus (2,6 rad/s, Dämpfung 0,7/s), die Krone zerfällt
 * und geht mit dem Wasser fort.</li>
 * </ul>
 * Falldauer T = (0,8 s + 0,022 s/m · H) · (0,85 … 1,15): ein freier Fall um den Fuß dauert mit der Länge länger (für einen
 * starren Stab aus der Senkrechten über √(H/g)); gestaltet so, dass ein 50-m-Baum in rund 2 s liegt.
 * <p>
 * Je Baum {@link #FL} Werte für den Zeichner: x y z Drehung · Maßstab Tönung Ankunft Bruchhöhe · Fallrichtung x z,
 * Winkel am Ende, Falldauer · Folge, Drift (m/s), Höhe (m), Typ ({@link #put}).
 */
public final class TreeFall {
    private TreeFall() { }

    /** Werte je fallendem Baum. */
    public static final int FL = 16;
    /** Vorlauf des Luftstoßes vor der Ankunft (s). */
    public static final double AIR_BLAST = 0.6;

    /** Wie lange ein Baum nach seiner Ankunft fällt (s), je Folge; umgelegt bleibt er liegen (1e9). */
    public static double duration(int state, double tf) {
        switch (state) {
            case TreeStates.BROKEN: return tf + 0.6;      // gebrochen: dann der Stumpf von forest
            case TreeStates.STRIPPED: return 3.5;         // entrindet: Krone fort, Stamm schwingt aus
            case TreeStates.FELLED: return 1e9;           // umgelegt: liegt längs des Hangs, bis die App ihn abgibt
            case TreeStates.GONE: return tf + 6.0;        // fort: treibt und sinkt
            default: return 0;
        }
    }

    /** Falldauer (s) eines Baums der Höhe H (m); r ∈ [0, 1) streut ±15 %. */
    public static double fallTime(double H, double r) { return (0.8 + 0.022 * H) * (0.85 + 0.3 * r); }

    /** Winkel am Ende (rad): 90° minus die Neigung des Hangs in Fallrichtung (Anstieg dh über die Strecke dx), 0,5 … 1,9. */
    public static double restAngle(double dh, double dx) { return Math.max(0.5, Math.min(1.9, Math.PI / 2 - Math.atan(dh / dx))); }

    /** Bruchhöhe (m) eines gebrochenen Baums der Höhe H; r ∈ [0, 1). */
    public static double breakHeight(double H, double r) { return (0.15 + 0.3 * r) * H; }

    /** Drift eines fortgerissenen Stamms mit dem Wasser (m/s); r ∈ [0, 1). */
    public static double drift(double r) { return 8 + 8 * r; }

    /** Ein Baum in o ab q: Ort, Drehung, Maßstab, Tönung, Ankunft, Bruchhöhe, Fallrichtung, Winkel, Dauer, Folge, Drift, Höhe, Typ. */
    public static void put(float[] o, int q, float x, float y, float z, float yaw, float scale, float tint, double arrival, double breakH,
                           double fx, double fz, double rest, double tf, int state, double drift, double H, int type) {
        o[q] = x; o[q + 1] = y; o[q + 2] = z; o[q + 3] = yaw;
        o[q + 4] = scale; o[q + 5] = tint; o[q + 6] = (float) arrival; o[q + 7] = (float) breakH;
        o[q + 8] = (float) fx; o[q + 9] = (float) fz; o[q + 10] = (float) rest; o[q + 11] = (float) tf;
        o[q + 12] = state; o[q + 13] = (float) drift; o[q + 14] = (float) H; o[q + 15] = type;
    }

    /**
     * Neigung (rad) eines umgelegten oder fortgerissenen Baums tau s nach der Ankunft, ohne Peitsche und Nachfedern (wie
     * treefall.vert): Luftstoß bis zur Ankunft, dann Kippen bis zum Winkel am Ende.
     */
    public static double tilt(double tau, double tf, double rest) {
        double vor = smooth(-AIR_BLAST, 0, tau) * 0.05;
        if (tau <= 0) return vor;
        double u = Math.max(0, Math.min(1, tau / Math.max(tf, 0.3)));
        return vor * (1 - u) + rest * u * u * (1.7 - 0.7 * u);
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
}
