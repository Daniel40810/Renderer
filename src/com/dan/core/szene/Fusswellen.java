package com.dan.core.szene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Wellen am Fuß der Kanten aus der Fläche des Seegangs (Seegang Werkbuch, S5b-4, Datei 46; SF51 A). Die Karte sucht
 * je Bild am Fuß der aktiven Abschnitte im Netz die Kämme ({@code render.seegang.Fusssuche}, {@code see_fuss.comp}):
 * je Fuß den vorigen, den letzten und den nächsten Kamm mit der Zeit seines Durchgangs, seiner Höhe H = 2 |Z'|, dem
 * Weißwasser und der linearen Höhe 2 |Z| (nach Rayleigh verteilt, für den Rang der Welle), dazu H_s = 4 √m0, die
 * mittlere Periode T_m01 (der mittlere Abstand der Kämme) und seit S5b-4c die H_s der gezeichneten Wellen am Fuß. Diese Klasse sammelt sie zu einer Folge je Abschnitt, aus der
 * {@link Brandung} ihre Wellen nimmt statt aus dem eigenen Zufall: Überlauf, Gischt am Hang und Typ hängen dann an den
 * Kämmen, die man vor der Kante kommen sieht.
 * <p>
 * <b>Zustand.</b> Ein Kamm, den eine Suche zweimal findet (Zeiten näher als {@link #GLEICH} der Periode), ist derselbe;
 * er behält seine Nummer. Nach seinem Durchgang am Fuß ändert sich ein Kamm nicht mehr ({@link Kamm#fest}), damit die
 * Gischt einer Welle nicht von Bild zu Bild springt; ein vorhergesagter Kamm, den die nächste Suche nicht wiederfindet,
 * fällt weg. Die Fläche ist eine Funktion der Zeit; steht die Zeit, liefert jede
 * Suche dasselbe, und die Folge bleibt gleich. Springt die Zeit (rückwärts oder um mehr als {@link #SPRUNG} s), wird
 * alles vergessen; ist die letzte Suche eines Abschnitts älter als {@link #FRISCH} s Modellzeit (etwa im Zeitraffer),
 * folgt die Brandung wieder ihrer eigenen Wellenfolge.
 * <p>
 * Zwischen der Karte (Suche, einige Bilder später gelesen) und der Brandung (je Bild auf dem Prozessor) liegt diese
 * Klasse im Kern; alle Methoden sind synchronisiert.
 */
public final class Fusswellen {
    public static final String VERSION = "0.1";
    /** Floats je Fuß in der Ausgabe der Karte (see_fuss.comp): fünf vec4. */
    public static final int JE_FUSS = 20;
    /** Zwei Kämme mit Zeiten näher als dieser Anteil der Periode sind derselbe [G]. */
    public static final double GLEICH = 0.3;
    /** So lange (s Modellzeit) nach seinem Durchgang am Fuß bleibt ein Kamm in der Folge [G]. */
    public static final double BEHALTEN = 40;
    /** So alt (s Modellzeit) darf die letzte Suche höchstens sein, damit die Brandung der Folge folgt [G]. */
    public static final double FRISCH = 2;
    /** Ab diesem Sprung der Zeit (s) wird alles vergessen [G], wie {@link Kammgischt#SPRUNG}. */
    public static final double SPRUNG = 5;
    /** Kämme je Folge höchstens. */
    public static final int KAEMME_MAX = 16;
    /**
     * Seit S5b-4c: Zeichnet die Stufe am Fuß weniger als diesen Anteil von H_s (nur lange Wellen aufgelöst, weit vom
     * Auge), gibt {@link #folge} nichts; die Brandung nimmt dort ihre eigene Folge [G].
     */
    public static final double GEZEICHNET_MIN = 0.25;

    /**
     * Ein Kamm am Fuß: Zeit des Durchgangs (s), H = 2 |Z'| (m, gezeichnet), Weißwasser w, lineare Höhe 2 |Z| (m, vor dem
     * Faktor nach Battjes–Groenendijk; nach Rayleigh mit H_s verteilt), Nummer, fest.
     */
    public static final class Kamm {
        public double zeit, hoehe, weiss, linear;
        public long nummer;
        /** Nach dem Durchgang am Fuß gefunden: wird nicht mehr nachgeführt. */
        public boolean fest;
        /** Die Suche, die ihn zuletzt fand. */
        long gesehen;

        Kamm kopie() {
            Kamm k = new Kamm();
            k.zeit = zeit; k.hoehe = hoehe; k.weiss = weiss; k.linear = linear; k.nummer = nummer; k.fest = fest; k.gesehen = gesehen;
            return k;
        }
    }

    /** Die Folge an einem Fuß: Kämme nach der Zeit, dazu was die letzte Suche fand. */
    public static final class Folge {
        public final List<Kamm> kaemme = new ArrayList<>();
        /**
         * Zeit der letzten Suche (s), η, Phase θ, H_s (4 √m0) und T_m01 (s, mittlerer Abstand der Kämme) am Fuß, Periode
         * (s) der Anfrage, Stufe der Clipmap + 1 (negativ am Rand zum Fernfeld).
         */
        public double tSuche = Double.NaN, eta, theta, hs, tm01, periode;
        /** Seit S5b-4c: H_s der gezeichneten Wellen am Fuß, 4 √(Σ E g²) mit der Auflösung der Stufe (der Maßstab für 2 |Z|). */
        public double hsGezeichnet;
        public int stufe;

        Folge kopie() {
            Folge f = new Folge();
            for (Kamm k : kaemme) f.kaemme.add(k.kopie());
            f.tSuche = tSuche; f.eta = eta; f.theta = theta; f.hs = hs; f.tm01 = tm01; f.periode = periode; f.stufe = stufe; f.hsGezeichnet = hsGezeichnet;
            return f;
        }
    }

    /** Die Füße für die nächste Suche: Abschnitt (Index in den Kanten), Ort (UTM), ω (rad/s). */
    public static final class Anfrage {
        public int zahl;
        public int[] kante = new int[0];
        public double[] ost = new double[0], nord = new double[0], omega = new double[0];
    }

    private final Map<Integer, Folge> folgen = new HashMap<>();
    private Anfrage anfrage = new Anfrage();
    private double tLetzt = Double.NaN;
    private long nummer = 1;
    /** Zuletzt: Füße im Ergebnis, davon in der Clipmap, neue Kämme; Folgen; Suchen seit dem Leeren. */
    public volatile int fuesseZuletzt, gueltigZuletzt, neuZuletzt, folgenZahl;
    public volatile long suchen;
    /** Ein (Schalter für Prüfungen): aus, gibt {@link #folge} nichts, und die Brandung folgt ihrer eigenen Wellenfolge. */
    public volatile boolean an = true;

    /** Alles vergessen (neues Szenario, Prüfungen). */
    public synchronized void leeren() {
        folgen.clear();
        anfrage = new Anfrage();
        tLetzt = Double.NaN;
        suchen = 0;
        folgenZahl = 0;
    }

    /** Die Brandung meldet je Bild die Füße für die nächste Suche (die Felder werden kopiert). */
    public synchronized void anfragen(int zahl, int[] kante, double[] ost, double[] nord, double[] omega) {
        Anfrage a = new Anfrage();
        a.zahl = zahl;
        a.kante = Arrays.copyOf(kante, zahl);
        a.ost = Arrays.copyOf(ost, zahl);
        a.nord = Arrays.copyOf(nord, zahl);
        a.omega = Arrays.copyOf(omega, zahl);
        anfrage = a;
    }

    /** Die zuletzt gemeldeten Füße (unverändert bis zur nächsten Meldung). */
    public synchronized Anfrage anfrage() { return anfrage; }

    /**
     * Das Ergebnis einer Suche zur Modellzeit tSuche: je Fuß i der Abschnitt kante[i], die Periode (s) der Anfrage und
     * {@link #JE_FUSS} Werte ab aus[JE_FUSS i] (Aufbau wie see_fuss.comp).
     */
    public synchronized void ergebnis(double tSuche, int[] kante, double[] periode, float[] aus, int zahl) {
        if (!Double.isNaN(tLetzt) && (tSuche < tLetzt - 1e-6 || tSuche - tLetzt > SPRUNG)) folgen.clear();
        tLetzt = tSuche;
        suchen++;
        int gueltig = 0, neu = 0;
        for (int i = 0; i < zahl; i++) {
            int o = JE_FUSS * i;
            float st = aus[o];
            if (st == 0 || !Float.isFinite(st)) continue;
            gueltig++;
            Folge f = folgen.computeIfAbsent(kante[i], x -> new Folge());
            f.tSuche = tSuche;
            f.stufe = Math.round(st);
            f.eta = aus[o + 1];
            f.theta = aus[o + 2];
            f.hs = aus[o + 3];
            f.tm01 = aus[o + 16];
            f.hsGezeichnet = aus[o + 17];
            f.periode = periode[i];
            for (int k = 0; k < 3; k++) {
                int q = o + 4 + 4 * k;
                double dz = aus[q], h = aus[q + 1];
                if (!Double.isFinite(dz) || !(h >= 0)) continue;
                double zeit = tSuche + dz;
                Kamm da = null;
                double gleich = GLEICH * (f.tm01 > 0 ? f.tm01 : f.periode);
                for (Kamm c : f.kaemme) if (Math.abs(c.zeit - zeit) < gleich) { da = c; break; }
                if (da == null) {
                    da = new Kamm();
                    da.nummer = nummer++;
                    f.kaemme.add(da);
                    neu++;
                } else if (da.fest) { da.gesehen = suchen; continue; }
                da.zeit = zeit;
                da.hoehe = h;
                da.weiss = aus[q + 2];
                da.linear = aus[q + 3];
                da.fest = zeit <= tSuche;
                da.gesehen = suchen;
            }
            // ein vorhergesagter Kamm (noch nicht am Fuß), den diese Suche nicht mehr findet, war eine falsche Vorhersage
            long diese = suchen;
            f.kaemme.removeIf(c -> !c.fest && c.gesehen != diese);
            f.kaemme.sort((a, b) -> Double.compare(a.zeit, b.zeit));
            f.kaemme.removeIf(c -> c.zeit < tSuche - BEHALTEN);
            while (f.kaemme.size() > KAEMME_MAX) f.kaemme.remove(0);
        }
        fuesseZuletzt = zahl;
        gueltigZuletzt = gueltig;
        neuZuletzt = neu;
        folgenZahl = folgen.size();
    }

    /**
     * Die Folge des Abschnitts zur Modellzeit t (eine Kopie), oder null: keine, aus, die letzte Suche liegt mehr als
     * {@link #FRISCH} s neben t, sie hat keinen Kamm, oder die Stufe zeichnet am Fuß weniger als {@link #GEZEICHNET_MIN}
     * von H_s.
     */
    public synchronized Folge folge(int kante, double t) {
        if (!an) return null;
        Folge f = folgen.get(kante);
        if (f == null || !(Math.abs(t - f.tSuche) <= FRISCH) || f.kaemme.isEmpty() || !(f.hsGezeichnet >= GEZEICHNET_MIN * f.hs)) return null;
        return f.kopie();
    }

    /**
     * Seit S5b-4c: {gezeichnete H_s, H_s, Zahl der Kämme} der letzten Suche am Fuß des Abschnitts, oder null, wenn er nie
     * in der Clipmap gesucht wurde (für Prüfungen).
     */
    public synchronized double[] stand(int kante) {
        Folge f = folgen.get(kante);
        return f == null ? null : new double[] {f.hsGezeichnet, f.hs, f.kaemme.size()};
    }
}
