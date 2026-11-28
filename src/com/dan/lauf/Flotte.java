package com.dan.lauf;

import com.dan.core.szene.Tonereignisse;
import com.dan.core.szene.Wasserpunkte;
import com.dan.physics.auftrieb.Bandwasser;
import com.dan.physics.auftrieb.Festmacher;
import com.dan.physics.auftrieb.Kette;
import com.dan.physics.auftrieb.Koerper;
import com.dan.physics.auftrieb.Schiffbau;
import com.dan.physics.auftrieb.Vorgaben;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * Die schwimmenden Körper eines Szenarios in der Anwendung (Seegang Werkbuch, S7b-2, Datei 88): Körper aus
 * {@code physics.auftrieb}, Wasser von der Karte ({@link Wasserpunkte}), ein eigener Faden, der sie rechnet, und die Lagen
 * für das Bild ({@code render.schwimmer}).
 * <ul>
 * <li><b>Je Bild</b> ({@link #vorBild}, im Faden der Bildschleife): die Lagen des letzten Schritts, mit ihrer Geschwindigkeit
 * auf die Zeit des Bilds fortgeschrieben, für das Bild; die Anfrage an die Karte (je Körper die Stellen seiner Säulen,
 * {@code Proben.saeule}); der Pegel je Körper ({@code spiegel}); dann rechnet der Faden bis zur Zeit des Bilds.</li>
 * <li><b>Zeit:</b> Bis zum Zeitraffer {@link #R_ECHT} [G] rechnen die Körper in der Zeit der Szene mit den Wellen der
 * Karte. Darüber rechnen sie in der Zeit der Uhr (höchstens {@link #SCHRITT_MAX} je Bild) im ruhigen Wasser auf dem Pegel
 * der Szene: Sie steigen und fallen mit der Tide, fallen trocken und schwimmen auf, ohne dass die Physik den Zeitraffer
 * nachrechnen muss. Angehalten steht alles; nach einem Sprung der Zeit ({@link #SPRUNG_S} [G], rückwärts) liegen alle neu
 * an ihrem Platz.</li>
 * <li><b>Fest:</b> {@code anleger} – Leinen vom Bug zu zwei Pollern an der Rampe und vom Heck zu zwei Pfählen, Fender am
 * Bug; die Leinen laufen in Ringen an den Pfählen (Höhe wie die Klampe, also ohne Zug aus der Tide) [G]. {@code anker} –
 * Kette zum Anker. {@code kette} – Tonne an der Kette zum Stein. {@code frei} – treibt. Seit S7e1 {@code pricke} – steht
 * fest im Grund, ohne Physik und ohne Punkte der Karte ({@link #PRICKE_IM_GRUND}).</li>
 * <li><b>Lichter</b> seit S7e1 (Seegang Werkbuch 99, SF148 A): je Schwimmer nach {@link Lichter}; {@link #lichter} gibt sie
 * je Bild in der Welt für {@code render.schwimmer.Lichterbild}.</li>
 * <li><b>Strom</b> seit S7b-4 (Datei 89, A25) je Körper am Ort ({@link Umwelt#strom}, aus dem Nachlauf der Strömung); Wind
 * am Auge, Grund aus dem Gelände.</li>
 * <li><b>Punkte der Karte</b> seit S7b-4: die Fähren in einem Raster von {@link #KARTE_SCHIFF} m, Tonnen und Kleines mit
 * einem Punkt, der Kutter je Säule ({@code Koerper.kartenRaster}, Befund L5c: 718 der 920 Punkte waren die Fähre). Seit
 * S7d6 (Seegang Werkbuch 93, SF132 A) die Schiffe {@link #KARTE_SCHIFF} m längs × {@link #KARTE_SCHIFF_QUER} m quer; der
 * Kutter wieder je Säule (im Raster verfehlte er Prüfstein 16 in jeder Nachrechnung).</li>
 * <li><b>Fahrplan</b> seit S7d (SF115 A): eine Fähre mit Weg und Fahrplan fährt ihn ({@link Lotse}); jedes Schiff in Fahrt
 * ist eine Quelle des Kielwassers ({@link #kielwasser}, im Faden der Bilder gesetzt).</li>
 * </ul>
 */
public final class Flotte implements AutoCloseable {
    public static final String VERSION = "0.8";
    /** Seit S7e1 (SF149 A): so tief steht eine Pricke im Grund (m) [G]; ohne Gelände am Ort steht ihr Fuß auf {@link #PRICKE_GRUND} m NHN [G]. */
    public static final double PRICKE_IM_GRUND = 1.0, PRICKE_GRUND = -1.0;
    /**
     * Seit S7d4 (Seegang Werkbuch 92, SF125 A, Befund L5d3 6: angelegt 8,9 m neben dem Platz, 10° schräg): Nach dem Festmachen
     * am Anleger holen die Leinen mit so viel (m/s) ein oder geben nach, bis sie die Länge haben, die sie am Platz der Szene
     * hätten (Verholen) [G]. Die Pfähle hinter dem Heck stehen seitdem fest zum Platz der Szene, nicht zur Lage beim
     * Festmachen.
     */
    public static final double VERHOL_V = 0.1;
    /** Raster der Punkte der Karte (m) [G]: Fähren und Ausflugsschiff; Tonnen, Treibgut und Enten haben einen Punkt. */
    public static final double KARTE_SCHIFF = 4.0, KARTE_KLEIN = 50;
    /** Seit S7d5 (Seegang Werkbuch 93, SF127 A, Befund L5d4 3): der Kutter im Raster 1,5 m statt je Säule (112 → rund 40 Punkte) [G]. */
    public static final double KARTE_KUTTER = 0, KARTE_SCHIFF_QUER = 2.0;
    /**
     * Seit S7e2 (Seegang Werkbuch 101, R20): der Adler-Express gröber, 6 m längs × 4 m quer [G] – see_koerper lag in L5e1 schon
     * bei 0,744 ms (Grenze 0,75); er ist schwer und lang gegen die Wellen am Anleger.
     */
    public static final double KARTE_ADLER = 6.0, KARTE_ADLER_QUER = 4.0;
    /** Seit S7d6: Fahrt und Zeit fürs Anlegen, mit denen der Lotse plant (für die Prüfungen). */
    public static final double FAHRT_PLAN = Lotse.FAHRT_PLAN, ANLEGEN_S = Lotse.ANLEGEN_S, BREMS_AB = Lotse.BREMS_AB;
    /** Seit S7e2: Höhe (m NHN), auf die das Bild ein Schiff legt, das weg ist. */
    public static final double WEG_TIEFE = -1e4;

    /** Seit S7d7: Zeit nach dem Plan des Lotsen vom Weg w (m vor dem Anleger) bis fest (s). */
    public static double planZeit(double w) { return Lotse.planZeit(w); }
    /** Fahrt über Wasser, ab der ein Schiff Kielwasser zieht (m/s) [G]. */
    public static final double KIEL_FAHRT = 1.0;
    /**
     * Seit S7d2 (Befund L5d 3): Kielwasser zieht nur ein Schiff in eigener Fahrt (Hebel ≠ 0 oder der Lotse unterwegs), mit
     * der Fahrt durchs Wasser längs des Kurses, über so viele s geglättet [G]: Ein Kutter vor Anker, den die Wellen hin und
     * her tragen, zieht keins.
     */
    public static final double KIEL_MITTEL = 10;
    /** Seit S7d2: ab dieser Krängung (Grad) gilt ein Schiff als gekentert; die Flotte schreibt dann auf, was sie weiß [G]. */
    public static final double KENTERN = 45;
    /** Bis zu diesem Zeitraffer rechnen die Körper in der Zeit der Szene mit den Wellen [G]. */
    public static final double R_ECHT = 4;
    /** Größter Schritt je Bild in der Zeit der Uhr über {@link #R_ECHT} (s) [G]. */
    public static final double SCHRITT_MAX = 0.05;
    /** Weiter als so weit (s) gilt ein Schritt der Szenenzeit als Sprung [G]. */
    public static final double SPRUNG_S = 60;
    /** Proben der Tonnen in der Szene (m) [G]: gröber als in der Prüfung von S7a (Spiere 0,1 m, Tonne 0,2–0,3 m). */
    public static final double SPIERE_D = 0.2, TONNE_D = 0.4;
    /** Die Welle an einer Probe gilt so lange (s), solange sie nicht mehr als 2 cm steigt ({@code Koerper.wasserTakt}) [G]. */
    public static final double WASSER_TAKT = 0.012;
    /**
     * Seit S8b (Seegang Werkbuch 104, SF161 A): Treibgut und Enten reiten die Wellen – je Stück Treibgut ein Punkt der Karte,
     * je Trupp Enten einer in der Mitte ihrer Plätze; die Enten nehmen Höhe und Neigung des Trupps (die Bänder des Punkts,
     * auf ihren Ort gedreht, wie bei den Säulen eines Schiffs). {@code -Dlauf.entenwellen=aus}: wie bis S7e2.
     */
    public static final boolean OHNE_WELLEN = "aus".equalsIgnoreCase(System.getProperty("lauf.entenwellen"));
    /**
     * Seit S8b (Seegang Werkbuch 104, Befund: der Punkt des Trupps hält Prüfstein 40 nicht – 57 mm schon an der Mitte, 192 mm
     * 5 m daneben): jede Ente fragt ihren eigenen Punkt, aber nur im Umkreis von {@link #KLEIN_UMKREIS} m um das Auge [G]
     * (weiter weg sieht niemand eine Ente schaukeln; das hält die Kosten der Karte). Treibgut ebenso. Mit
     * {@code -Dlauf.entenpunkte=trupp} wie SF161 A: ein Punkt je Trupp. Seit L6a 300 m statt 400 m (Befund: see_koerper
     * 0,756 ms in seegang/tonne mit 211 Punkten, Grenze 0,75 ms; eine Ente in 300 m ist rund 1 Bildpunkt hoch).
     */
    public static final boolean TRUPP_PUNKT = "trupp".equalsIgnoreCase(System.getProperty("lauf.entenpunkte"));
    public static final double KLEIN_UMKREIS = Double.parseDouble(System.getProperty("lauf.kleinumkreis", "300"));
    /**
     * Seit S8b (SF163 A): Eine Ente taucht ab, wenn ein Schiff in Fahrt (über {@link #AUSWEICH_FAHRT} m/s) sie bis
     * {@link #AUSWEICH_VOR} m vor dem Bug und bis {@link #AUSWEICH_QUER} m neben dem Rumpf hat [G].
     */
    public static final double AUSWEICH_VOR = 30, AUSWEICH_QUER = 10, AUSWEICH_FAHRT = 0.5;
    /**
     * Seit S8b (SF163 A): Treibgut im Kasten eines Rumpfs (plus {@link #SCHUB_RAND} m) schiebt der Rumpf quer hinaus, mit einer
     * Feder, die die Eindringtiefe in {@link #SCHUB_T} s schließt, kritisch gedämpft [G].
     */
    public static final double SCHUB_RAND = 0.3, SCHUB_T = 1.0;
    /** Pegel, wenn die Anwendung am Ort keinen Wasserstand hat (trocken) [G]: tief unter dem Watt. */
    public static final double TROCKEN = -10;

    /** Ein Schwimmer der Flotte. */
    public static final class Schwimmer {
        public final Schwimmerangabe angabe;
        public final Koerper koerper;
        public final Bandwasser wasser;
        public final Schiffbau.Netz netz;
        final List<Festmacher.Leine> ringe = new ArrayList<>();
        /**
         * Seit S7d8 (Seegang Werkbuch 97, SF142 A, Befund L5d7 2): alles, was {@link #festmachen} angelegt hat; ein neues
         * Festmachen nimmt es vorher ab. Bis S7d7 legte jedes Festmachen neue Leinen dazu.
         */
        final List<Festmacher> angelegt = new ArrayList<>();
        Kette kette;
        double[] grund;
        double grundX = Double.NaN, grundY, grundKurs;
        /** Seit S7d: der Lotse (null: keiner), ob die Leinen fest sind, die Quelle des Kielwassers (null: kein Schiff). */
        final Lotse lotse;
        boolean fest;
        public final com.dan.core.szene.Kielwasser.Quelle kiel;
        /** Seit S7c: Bugschlag nach Ochi (null: kein Schiff); im Faden der Flotte geprüft. */
        public final com.dan.physics.auftrieb.Bugschlag bug;
        /** Seit S7e1: steht fest (Pricke): keine Physik, keine Punkte der Karte. */
        public final boolean steht;
        /**
         * Seit S7e2 (Seegang Werkbuch 101): der Lotse sagt WEG – dann rechnet die Flotte den Körper nicht, fragt keine Punkte der
         * Karte und legt ihn im Bild tief unter das Wasser (bis S7e1 ruhte eine Fähre am Ende des Wegs, mit Punkten).
         */
        public volatile boolean weg;
        /** Seit S7e1 (SF148 A): die Lichter im Körper. */
        final List<Lichter.Licht> lichter;
        /** Seit S7e2 (SF151 A): eine Eiderente in ihrem Trupp (null: keine); sie sagt, wann sie {@link #weg} ist. */
        final Ente ente;
        /**
         * Seit S7e2 (Datei 100, 6; R20): Treibgut und Enten fragen keine Punkte der Karte; sie schwimmen auf dem Pegel des Bilds
         * mit dem Strom, ohne die Wellen. Seit S8b (Seegang Werkbuch 104, SF161 A) nur noch mit {@link #OHNE_WELLEN}: Treibgut
         * hat einen Punkt, jeder Trupp Enten einen in seiner Mitte ({@link #trupp}).
         */
        final boolean ohneKarte;
        /** Seit S8b (SF161 A): der Trupp einer Ente (Name ohne die laufende Nummer; null: keine Ente). */
        final String trupp;
        /** Seit S8b (SF163 A): Treibgut, das ein Rumpf wegschiebt. */
        final boolean treibgut;
        /** Seit S8b: liegt eben an einem Rumpf (für den Zähler der Stöße). */
        boolean geschoben;

        Schwimmer(Schwimmerangabe a, Koerper k, int nr, com.dan.core.szene.Kielwasser kw) {
            angabe = a;
            koerper = k;
            wasser = new Bandwasser(nr);
            Schiffbau.Netz n0 = Schiffbau.fuer(k, a.farbe);
            // seit S8d: der Kopf des Schwimmers trägt den Schwimmerblick und wird nicht gezeichnet (das Auge sitzt in ihm)
            netz = "kopf".equals(a.art) ? new Schiffbau.Netz(n0.name, new float[0], n0.material) : n0;
            lotse = a.route != null && a.fahrplan != null ? new Lotse(a.route, a.fahrplan, a.nachTide) : null;
            if (lotse != null) {                                                 // seit S7e2 (Seegang Werkbuch 101): je Schiff
                if (a.routeAb != null) lotse.wegAb(a.routeAb);
                lotse.zone = a.zone;
                lotse.sturmWeg = a.sturmWeg;
                lotse.fahrt = a.lotse.getOrDefault("fahrt", lotse.fahrt);
                lotse.fahrtPlan = a.lotse.getOrDefault("plan", lotse.fahrtPlan);
                lotse.anlegenS = a.lotse.getOrDefault("anlegen", lotse.anlegenS);
                lotse.hebelVoll = a.lotse.getOrDefault("hebel", lotse.hebelVoll);
                lotse.pegelMin = a.lotse.getOrDefault("pegel", lotse.pegelMin);
            }
            steht = "pricke".equals(a.fest);
            ente = "trupp".equals(a.fest) && a.festZahlen.length >= 2 ? new Ente(a.festZahlen[0], a.festZahlen[1], a.name.hashCode()) : null;
            treibgut = java.util.Set.of("balken", "fischkiste", "stamm").contains(a.art);
            ohneKarte = OHNE_WELLEN && (ente != null || treibgut);
            trupp = ente != null && a.name.lastIndexOf('.') > 0 ? a.name.substring(0, a.name.lastIndexOf('.')) : ente != null ? a.name : null;
            lichter = Lichter.fuer(a, k);
            if (k.form instanceof com.dan.physics.auftrieb.Form.Rumpf r) {
                double[] ka = k.form.kasten();
                kiel = kw.neu(a.name, ka[1] - ka[0], ka[3] - ka[2], k.hydro.tiefgang, true);
                bug = new com.dan.physics.auftrieb.Bugschlag(k);
            } else { kiel = null; bug = null; }
        }

        /** Der Lotse dieses Schwimmers (null: keiner), für Prüfungen. */
        public String phase() { return lotse == null ? null : lotsePhase; }
        /** Seit S7d8 (SF142 A): wie viele Leinen am Anleger fest sind (für Prüfungen; 4, wenn fest). */
        public int leinen() { return ringe.size(); }
        /** Seit S7d6 (SF136 A): Abstand vom Anleger längs des Wegs des Lotsen (m) zum Ort (x, y); NaN ohne Lotsen. */
        public double weg(double x, double y) { return lotse == null ? Double.NaN : lotse.sIst(x, y); }

        volatile String lotsePhase;
        /** Seit S7d2: warum sie liegt (leer: nach Fahrplan). */
        String lotseGrund = "";
        /** Seit S7d2: Fahrt durchs Wasser längs des Kurses, geglättet (m/s; im Faden der Bilder). */
        double fahrtGlatt = Double.NaN;
        /** Seit S7d2: größte Krängung seit dem letzten Setzen (Grad, Betrag); was die Flotte beim Kentern wusste (null: nicht gekentert). */
        public volatile double kraengungMax;
        public volatile String kentern;
        /** Seit S7d2: Fahrt durchs Wasser für das Kielwasser, wie zuletzt gesetzt (m/s; 0: keins). */
        public volatile double kielFahrt;
        /** Seit S7d4: Länge der Leinen am Platz der Szene (je Leine in {@link #ringe}; null: keine) und Zeit des letzten Verholens. */
        double[] leineSoll;
        double verholZeit = Double.NaN;
        /** Seit S7d4: wann die Leinen zuletzt fest wurden (s der Szene; NaN: nicht fest), für Prüfungen. */
        public volatile double festSeit = Double.NaN;
    }

    /** Die Umgebung, die die Anwendung je Bild gibt. */
    public interface Umwelt {
        /** Wasserstand am Ort (m NHN; NaN: kein Wasser). */
        double pegel(double ost, double nord);

        /** Wind in 10 m Höhe {nach Ost, nach Nord} (m/s). */
        double[] wind();

        /** Seit S7b-4: Strom am Ort {nach Ost, nach Nord} (m/s) oder null (keiner bekannt). */
        default double[] strom(double ost, double nord) { return null; }

        /** Seit S7e2 (SF151 A): Hs am Ort (m; 0 ohne Seegang), für die Enten. */
        default double hs(double ost, double nord) { return 0; }

        /** Seit S8b: das Auge des letzten Bilds {Ost, Nord} oder null (unbekannt: alle kleinen Schwimmer mit Wellen). */
        default double[] auge() { return null; }
    }

    public final List<Schwimmer> schwimmer = new ArrayList<>();
    public final Wasserpunkte wasserpunkte = new Wasserpunkte();
    /** Seit S7d: das Kielwasser der Schiffe in Fahrt (nur im Faden der Bilder lesen: {@link #vorBild} setzt es). */
    public final com.dan.core.szene.Kielwasser kielwasser = new com.dan.core.szene.Kielwasser();
    /**
     * Seit S8d (Seegang Werkbuch 104): das Wasser am Kopf des Schwimmers beim letzten Stand {Nummer, η (m NHN), ∂η/∂Ost,
     * ∂η/∂Nord, Zeit (s), Grund (m NHN)}; null ohne Kopf. Aus dem Wasser der Karte, wie der Kopf es spürt.
     */
    public volatile double[] kopfWasser;

    /** Seit S8c (Seegang Werkbuch 104, SF159 A): wohin die Flotte Bugschläge und das Typhon beim Ablegen meldet (null: nicht). */
    public volatile Tonereignisse ton;

    /**
     * Seit S8c: die Maschinen der Schiffe in eigener Fahrt (Hebel nicht 0), je Schiff 6 double – Ost, Nord, Höhe (m NHN, am
     * Maschinenraum), Länge (m), Hebel (Betrag 0 … 1), Nummer des Schwimmers – aus den Lagen des Bilds.
     */
    public double[] maschinen() {
        double[] l = bildLagen;
        List<double[]> m = new ArrayList<>();
        for (int k = 0; k < schwimmer.size(); k++) {
            Schwimmer s = schwimmer.get(k);
            if (s.kiel == null || s.weg || l == null || 16 * k + 15 >= l.length || Double.isNaN(l[16 * k + 12])) continue;
            double h = Math.abs(s.koerper.hebel);
            if (h <= 0.02) continue;
            double[] ka = s.koerper.form.kasten();
            double x = 0.5 * (ka[0] + ka[1]) - 0.2 * (ka[1] - ka[0]), z = 0.5 * (ka[4] + ka[5]);
            int o = 16 * k;
            m.add(new double[] {l[o] * x + l[o + 8] * z + l[o + 12], l[o + 1] * x + l[o + 9] * z + l[o + 13], l[o + 2] * x + l[o + 10] * z + l[o + 14], ka[1] - ka[0], h, k});
        }
        double[] r = new double[6 * m.size()];
        for (int i = 0; i < m.size(); i++) System.arraycopy(m.get(i), 0, r, 6 * i, 6);
        return r;
    }

    /** Seit S8a (Seegang Werkbuch 104, SF158 A): die Gischt am Bug; jeder Bugschlag wirft einen Schwall (im Faden der Flotte). */
    public final Bugschwall gischt = new Bugschwall();
    /** Seit S8b (SF163 A): Zähler für Prüfungen – Enten, die vor einem Schiff abtauchten; Stöße des Rumpfs gegen Treibgut. */
    public volatile int abgetaucht, geschoben;
    /** Seit S7d: Beginn der Zeit der Szene (ms seit 1970, UTC) für den Fahrplan; NaN: ohne Fahrplan. */
    public volatile double startMs = Double.NaN;
    private final DoubleBinaryOperator gelaende;

    // ---- Stand des Fadens (unter dem Schloss)
    private final Object schloss = new Object();
    private double[] lagen, geschw;                 // je Körper 16 bzw. 3
    private double tStand = Double.NaN;
    private double[][] saeulenOst, saeulenNord;
    /** Seit S8b (SF161 A): je Körper der Körper, dessen Punkt er nimmt (Enten eines Trupps; −1: der eigene). */
    private int[] truppVon = new int[0];
    private boolean neu;
    // Auftrag an den Faden
    private double tZiel = Double.NaN, raffer, wandDt;
    private double[] pegelJe, stromJe, tiefeJe = new double[0];
    private double windU, windV;
    private boolean auftrag, neuSetzen = true, ende;
    private final Thread faden;
    private long anfrageNr;
    /** Zuletzt: Zeit des Fadens je Bild (ms), Art des Schritts (0 steht, 1 Szene, 2 Uhr, 3 neu gesetzt). */
    public volatile double fadenMs;
    public volatile int art;
    /** Fehler im Faden (null: keiner). */
    public volatile String fehler;

    /**
     * Baut die Flotte aus den Angaben. gelaende: Höhe des Grunds (m NHN) am Ort, nur im Faden der Flotte gerufen (eine
     * eigene Abfrage des Geländes).
     */
    public Flotte(List<Schwimmerangabe> angaben, DoubleBinaryOperator gelaende) {
        this.gelaende = gelaende;
        int nr = 0;
        for (Schwimmerangabe a : angaben) {
            Koerper k = vorgabe(a.art);
            k.wasserTakt = WASSER_TAKT;
            double[] r = raster(a.art);
            if (!"pricke".equals(a.art)) k.kartenRaster(r[0], r[1]);       // seit S7b-4, seit S7d6 längs und quer; seit S7e1 Pricken ohne
            Schwimmer sw = new Schwimmer(a, k, nr++, kielwasser);
            sw.wasser.zuordnung(k.kartenpunkt);
            schwimmer.add(sw);
        }
        for (Schwimmer s : schwimmer) {
            s.wasser.grundSonst(Double.NEGATIVE_INFINITY);
            DoubleBinaryOperator gf = gelaende;
            s.wasser.grundFunktion(gf);
        }
        lagen = new double[16 * schwimmer.size()];
        java.util.Arrays.fill(lagen, Double.NaN);
        geschw = new double[3 * schwimmer.size()];
        saeulenOst = new double[schwimmer.size()][];
        saeulenNord = new double[schwimmer.size()][];
        faden = new Thread(this::laufen, "Flotte");
        faden.setDaemon(true);
        faden.start();
    }

    /** Raster der Punkte der Karte je Vorgabe (m, längs und quer) [G]; bis 0,8 m: je Säule ein Punkt. Seit S7d6 längs und quer. */
    public static double[] raster(String art) {
        return switch (art) {
            case "faehre", "haukeHaien" -> new double[] {KARTE_SCHIFF, KARTE_SCHIFF_QUER};   // seit S7d6 4 × 2 m
            case "adlerExpress" -> new double[] {KARTE_ADLER, KARTE_ADLER_QUER};               // seit S7e2 6 × 4 m (R20)
            case "krabbenkutter" -> new double[] {KARTE_KUTTER, KARTE_KUTTER};                  // seit S7d6 wieder je Säule
            default -> new double[] {KARTE_KLEIN, KARTE_KLEIN};
        };
    }

    /** Der Körper zu einer Vorgabe (Name wie {@code Vorgaben}). */
    public static Koerper vorgabe(String art) {
        return switch (art) {
            case "faehre" -> Vorgaben.faehre();
            case "adlerExpress" -> Vorgaben.adlerExpress();
            case "haukeHaien" -> Vorgaben.haukeHaien();
            case "krabbenkutter" -> Vorgaben.krabbenkutter();
            case "spierentonne" -> Vorgaben.spierentonne(SPIERE_D);
            case "spitztonne" -> Vorgaben.spitztonne(TONNE_D);
            case "leuchttonne" -> Vorgaben.leuchttonne(TONNE_D);
            case "balken", "fischkiste", "stamm" -> Vorgaben.treibgut(art, Vorgaben.TREIB_D);   // seit S7e2 (SF151 A): Wind aus den Flächen
            case "eiderente" -> { Koerper e = Vorgaben.eiderente(Vorgaben.ENTE_D); e.schrittMax = Vorgaben.SCHRITT_KLEIN; yield e; }   // seit S7e2 gröber (R20)
            case "pricke" -> Vorgaben.pricke();                               // seit S7e1
            case "kopf" -> Vorgaben.kopf(0.05);                               // seit S8d: Träger des Schwimmerblicks
            default -> throw new IllegalArgumentException("Schwimmer: Vorgabe „" + art + "“ gibt es nicht");
        };
    }

    /** Alle Netze hintereinander, mit der Nummer des Körpers und den Materialien über alle (für das Bild). */
    public float[][] netze() {
        int ns = 0, nm = 0;
        for (Schwimmer s : schwimmer) { ns += s.netz.scheitel.length; nm += s.netz.material.length; }
        float[] sch = new float[ns], mat = new float[nm];
        int os = 0, om = 0;
        for (int k = 0; k < schwimmer.size(); k++) {
            Schiffbau.Netz n = schwimmer.get(k).netz;
            int m0 = om / Schiffbau.MAT;
            System.arraycopy(n.scheitel, 0, sch, os, n.scheitel.length);
            for (int i = os; i < os + n.scheitel.length; i += Schiffbau.FLOATS) {
                sch[i + 3] += m0;
                sch[i + 7] = k;
            }
            System.arraycopy(n.material, 0, mat, om, n.material.length);
            os += n.scheitel.length;
            om += n.material.length;
        }
        return new float[][] {sch, mat};
    }

    /**
     * Je Bild im Faden der Bildschleife: Lagen für das Bild (auf die Zeit ts fortgeschrieben), Anfrage an die Karte, Pegel
     * je Körper; dann rechnet der Faden bis ts. zeitraffer: wie die Uhr (0 angehalten, rückwärts negativ); dtUhr: Zeit der
     * Uhr seit dem letzten Bild (s).
     */
    public double[] vorBild(double ts, double zeitraffer, double dtUhr, Umwelt u) {
        int n = schwimmer.size();
        double[] pg = new double[n];
        double[] w = u.wind();
        double[] aus = new double[16 * n];
        synchronized (schloss) {
            // Lagen für das Bild: Stand des Fadens, mit der Geschwindigkeit auf ts fortgeschrieben (nur in der Zeit der Szene)
            double dt = !Double.isNaN(tStand) && art == 1 ? Math.max(0, Math.min(0.1, ts - tStand)) : 0;
            for (int k = 0; k < n; k++) {
                System.arraycopy(lagen, 16 * k, aus, 16 * k, 16);
                aus[16 * k + 12] += geschw[3 * k] * dt;
                aus[16 * k + 13] += geschw[3 * k + 1] * dt;
                aus[16 * k + 14] += geschw[3 * k + 2] * dt;
            }
            // Anfrage an die Karte: die Säulen der Körper beim letzten Schritt
            if (neu) {
                neu = false;
                int z = 0;
                for (int k = 0; k < n; k++) z += saeulenOst[k] == null ? 0 : saeulenOst[k].length;
                double[] o = new double[z], nn = new double[z];
                int[] an = new int[n], zahl = new int[n];
                int i = 0;
                for (int k = 0; k < n; k++) {
                    an[k] = -1;
                    if (saeulenOst[k] == null) continue;
                    int m = saeulenOst[k].length;
                    if (i + m > com.dan.render.seegang.Koerpersuche.MAX) continue;
                    an[k] = i;
                    zahl[k] = m;
                    System.arraycopy(saeulenOst[k], 0, o, i, m);
                    System.arraycopy(saeulenNord[k], 0, nn, i, m);
                    i += m;
                }
                // seit S8b (SF161 A): die Enten eines Trupps nehmen den Punkt in seiner Mitte
                for (int k = 0; k < n && k < truppVon.length; k++)
                    if (truppVon[k] >= 0 && an[truppVon[k]] >= 0) { an[k] = an[truppVon[k]]; zahl[k] = zahl[truppVon[k]]; }
                com.dan.core.szene.Kielwasser.Quelle[] qu = new com.dan.core.szene.Kielwasser.Quelle[n];
                for (int k = 0; k < n; k++) qu[k] = schwimmer.get(k).kiel;       // seit S7d2: ohne das eigene Kielwasser
                wasserpunkte.anfrage(i == 0 ? null : new Wasserpunkte.Anfrage(++anfrageNr, o, nn, i, an, zahl, qu));
            }
            // Pegel und Strom je Körper am Ort des letzten Stands
            double[] st = new double[2 * n];
            for (int k = 0; k < n; k++) {
                double x = lagen[16 * k + 12], y = lagen[16 * k + 13];
                Schwimmerangabe a = schwimmer.get(k).angabe;
                if (Double.isNaN(x)) { x = a.ost; y = a.nord; }
                double p = u.pegel(x, y);
                pg[k] = Double.isNaN(p) ? TROCKEN : p;
                double[] sv = u.strom(x, y);
                if (sv != null && !Double.isNaN(sv[0]) && !Double.isNaN(sv[1])) { st[2 * k] = sv[0]; st[2 * k + 1] = sv[1]; }
            }
            // seit S7e2: Hs in der Mitte der Plätze der Enten, einmal je Bild
            if (entenMitte == null) {
                double sx = 0, sy = 0;
                int ne = 0;
                for (Schwimmer s : schwimmer) if (s.ente != null) { sx += s.ente.heimX; sy += s.ente.heimY; ne++; }
                entenMitte = ne == 0 ? new double[0] : new double[] {sx / ne, sy / ne};
            }
            if (entenMitte.length == 2) { double h = u.hs(entenMitte[0], entenMitte[1]); hsEnten = Double.isNaN(h) ? 0 : h; }
            pegelJe = pg;
            augeAuftrag = u.auge();                                          // seit S8b
            pegelBild = pg;                                                  // seit S7e1: für das Spiegelbild der Lichter
            stromJe = st;
            // seit S7d: das Kielwasser der Schiffe in Fahrt, auf die Zeit des Bilds (in der Zeit der Uhr keins)
            for (int k = 0; k < n; k++) {
                Schwimmer s = schwimmer.get(k);
                if (s.kiel == null) continue;
                double x = aus[16 * k + 12], y = aus[16 * k + 13];
                if (Double.isNaN(x)) continue;
                double kurs = (Math.toDegrees(Math.atan2(aus[16 * k], aus[16 * k + 1])) + 360) % 360;
                // seit S7d2: Fahrt durchs Wasser längs des Kurses, über KIEL_MITTEL s geglättet; nur in eigener Fahrt [G]
                double hk = Math.toRadians(kurs);
                double vl = (geschw[3 * k] - st[2 * k]) * Math.sin(hk) + (geschw[3 * k + 1] - st[2 * k + 1]) * Math.cos(hk);
                double dk = Double.isNaN(kielT) ? -1 : ts - kielT;
                if (dk < 0 || dk > SPRUNG_S || Double.isNaN(s.fahrtGlatt)) s.fahrtGlatt = vl;
                else s.fahrtGlatt += (1 - Math.exp(-dk / KIEL_MITTEL)) * (vl - s.fahrtGlatt);
                String ph = s.lotsePhase;
                boolean eigen = s.koerper.hebel != 0 || (ph != null && (ph.startsWith("KOMMT") || ph.startsWith("FAEHRT_AB")));
                double fahrt = art == 1 && eigen ? Math.max(0, s.fahrtGlatt) : 0;
                // Kielwasser erst ab KIEL_FAHRT [G]
                s.kielFahrt = fahrt < KIEL_FAHRT ? 0 : fahrt;
                kielwasser.setze(s.kiel, x, y, kurs, s.kielFahrt, k < tiefeJe.length ? tiefeJe[k] : 20, ts);
            }
            kielT = ts;
            windU = w[0];
            windV = w[1];
            raffer = zeitraffer;
            wandDt = dtUhr;
            if (!Double.isNaN(tZiel) && (ts < tZiel - 1e-9 || ts - tZiel > SPRUNG_S)) neuSetzen = true;
            tZiel = ts;
            auftrag = true;
            schloss.notifyAll();
        }
        bildLagen = aus;
        return aus;
    }

    private volatile double[] bildLagen;
    private volatile double[] pegelBild;

    /**
     * Seit S7e1 (SF148 A): die Lichter aller Schwimmer zur Zeit ts der Szene in der Welt, aus den Lagen lagen (je Körper 16
     * double, Körper → Welt; NaN: fehlt), je Licht {@code render.schwimmer.Lichterbild.JE} double: Ost, Nord, Höhe,
     * Lichtstärke (cd, mit der Kennung), R, G, B, Mitte des Sektors (rad, Azimut), halbe Breite (rad), Höhe des Wassers am
     * Schwimmer (m NHN; NaN: kein Spiegelbild), 0. Welche Lichter brennen, sagt der Zustand: in Fahrt (der Lotse kommt oder
     * fährt ab), fest, vor Anker; Tonnen immer (die Nacht entscheidet das Bild).
     */
    public double[] lichter(double ts, double[] lagen) {
        int je = 11;
        List<double[]> aus = new ArrayList<>();
        double[] pg = pegelBild;
        for (int k = 0; k < schwimmer.size(); k++) {
            Schwimmer s = schwimmer.get(k);
            if (s.lichter.isEmpty() || lagen == null || 16 * k + 15 >= lagen.length || Double.isNaN(lagen[16 * k + 12])) continue;
            int zustand = zustand(s);
            if (zustand == 0) continue;
            int o = 16 * k;
            double peg = pg != null && k < pg.length && pg[k] > TROCKEN + 1 ? pg[k] : Double.NaN;
            for (Lichter.Licht l : s.lichter) {
                if ((l.wann() & zustand) == 0 && (l.wann() & Lichter.IMMER) == 0) continue;
                double kf = l.kennung() == null ? 1 : l.kennung().hell(ts + l.versatz());
                if (kf <= 0) continue;
                double x = l.x(), y = l.y(), z = l.z();
                double[] w = new double[je];
                // Körper → Welt: Spalten c0 (vorn), c1 (Backbord), c2 (oben), t; der Punkt (0, 0, 0) des Netzes ist der Ursprung
                for (int i = 0; i < 3; i++) w[i] = lagen[o + i] * x + lagen[o + 4 + i] * y + lagen[o + 8 + i] * z + lagen[o + 12 + i];
                w[3] = l.cd() * kf;
                w[4] = l.rgb()[0]; w[5] = l.rgb()[1]; w[6] = l.rgb()[2];
                double m = Math.toRadians(l.mitte()), cx = Math.cos(m), cy = Math.sin(m);
                double we = lagen[o] * cx + lagen[o + 4] * cy, wn = lagen[o + 1] * cx + lagen[o + 5] * cy;
                w[7] = Math.atan2(we, wn);
                w[8] = l.halb() >= 180 ? Math.PI : Math.toRadians(l.halb());
                w[9] = peg;
                aus.add(w);
            }
        }
        double[] r = new double[je * aus.size()];
        for (int i = 0; i < aus.size(); i++) System.arraycopy(aus.get(i), 0, r, je * i, je);
        return r;
    }

    /** Seit S7e1: Zustand für die Lichter (Bits nach {@link Lichter}); 0: keine. */
    private static int zustand(Schwimmer s) {
        String ph = s.lotsePhase;
        if (s.lotse != null) {
            if (ph == null || ph.startsWith("WEG")) return 0;
            if (ph.contains("fest") || ph.startsWith("LIEGT")) return Lichter.FEST;
            return Lichter.FAHRT;
        }
        return switch (s.angabe.fest) {
            case "anker" -> Lichter.ANKER;
            case "anleger" -> Lichter.FEST;
            case "kette" -> Lichter.IMMER;
            default -> 0;
        };
    }
    private double kielT = Double.NaN;

    /** Seit S7c: die Lagen, die das letzte {@link #vorBild} dem Bild gab (je Körper 16 double, Körper → Welt), oder null. */
    public double[] bildLagen() { return bildLagen; }

    /** Stellt alle Körper mit dem nächsten Bild neu an ihren Platz (neues Szenario, Prüfungen). */
    public void neuSetzen() {
        synchronized (schloss) {
            neuSetzen = true;
            wasserpunkte.leeren();
        }
    }

    // ------------------------------------------------------------------ Faden

    private void laufen() {
        try {
            while (true) {
                double t, r, dw, wu, wv;
                double[] pg;
                boolean setzen;
                synchronized (schloss) {
                    while (!auftrag && !ende) schloss.wait();
                    if (ende) return;
                    auftrag = false;
                    t = tZiel; r = raffer; dw = wandDt; pg = pegelJe; wu = windU; wv = windV;
                    stromAuftrag = stromJe;
                    setzen = neuSetzen;
                    neuSetzen = false;
                }
                long t0 = System.nanoTime();
                Wasserpunkte.Stand st = wasserpunkte.stand();
                int a;
                if (setzen) { setzen(t, pg, wu, wv); a = 3; }
                else if (r == 0) a = 0;
                else if (Math.abs(r) <= R_ECHT) {
                    for (Schwimmer s : schwimmer) {
                        Koerper k = s.koerper;
                        if (s.steht) { k.zeit(t); continue; }                       // seit S7e1
                        double dt = t - k.zeit();
                        if (dt <= 0) continue;
                        if (s.wasser.stand() != st) s.wasser.stand(st);
                        umwelt(s, pg, wu, wv);
                        if (s.lotse != null) {
                            boolean warWeg = s.weg;
                            lotse(s, t, pg, wu, wv);                                // seit S7d
                            // seit S7e2: wer weg war und kommt, beginnt am Ort des Plans (auf dem Weg der Ankunft); wer weg ist, ruht
                            if (warWeg && !s.weg) nachPlan(s, t, pg, wu, wv, false);
                            if (s.weg) { k.zeit(t); continue; }
                        }
                        if (s.ente != null && ente(s, t, true, pg, wu, wv, dt)) { k.zeit(t); continue; }   // seit S7e2
                        if (s.treibgut) schieben(s);                                  // seit S8b (SF163 A): Antrieb aus dem Rumpf
                        k.schritt(s.wasser, dt);
                        if (s.bug != null && s.bug.pruefe(k, s.wasser, t)) {          // seit S7c; seit S8a der Schwall, seit S8c der Ton
                            gischt.schlag(k, s.bug, s.wasser, t, wu, wv);
                            Tonereignisse te = ton;
                            if (te != null) {
                                double[] p = k.punktWelt(s.bug.x, 0, s.bug.z);
                                te.melde(Tonereignisse.BUGSCHLAG, p[0], p[1], p[2] + 0.5, Math.min(1, s.bug.letzteV / 4), t);
                            }
                        }
                        kentern(s, t, st, pg, wu, wv);                            // seit S7d2
                    }
                    a = 1;
                } else {
                    double dt = Math.min(SCHRITT_MAX, Math.max(0, dw));
                    for (Schwimmer s : schwimmer) {
                        Koerper k = s.koerper;
                        if (s.steht) { k.zeit(t); continue; }                       // seit S7e1
                        if (s.wasser.stand() != null) s.wasser.stand(null);
                        umwelt(s, pg, wu, wv);
                        // seit S7d: eine Fähre in Fahrt steht im Zeitraffer, wo der Fahrplan sie hat
                        if (s.lotse != null && nachPlan(s, t, pg, wu, wv, true)) { k.zeit(t); continue; }
                        if (s.weg) { k.zeit(t); continue; }
                        if (s.ente != null && ente(s, t, false, pg, wu, wv, dt)) { k.zeit(t); continue; }  // seit S7e2
                        k.zeit(t - dt);
                        if (dt > 0) k.schritt(s.wasser, dt);
                        k.zeit(t);
                        kentern(s, t, null, pg, wu, wv);                          // seit S7d2
                    }
                    a = 2;
                }
                art = a;
                stand(t);
                fadenMs = (System.nanoTime() - t0) / 1e6;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            fehler = e.toString();
            e.printStackTrace();
        }
    }

    private double[] stromAuftrag;
    private volatile double[] augeAuftrag;

    /**
     * Seit S7e2 (SF151 A): die Ente im Schwimmer s zur Zeit t – ob sie fehlt (dann ruht ihr Körper), sonst ihr Antrieb für
     * dt. echt: Zeit der Szene (sonst Zeitraffer).
     */
    private boolean ente(Schwimmer s, double t, boolean echt, double[] pg, double wu, double wv, double dt) {
        Ente e = s.ente;
        Koerper k = s.koerper;
        if (Double.isNaN(e.grund)) { double g = gelaende.applyAsDouble(e.heimX, e.heimY); e.grund = Double.isNaN(g) ? Double.NEGATIVE_INFINITY : g; }
        int i = schwimmer.indexOf(s);
        double z0 = k.zeit();
        if (echt && !s.weg && schiffVor(k.lage()) && e.abtauchen(t)) abgetaucht++;   // seit S8b (SF163 A)
        boolean w = e.weg(t, echt, hsEnten, Math.hypot(wu, wv), pg[i], k, s.wasser);
        s.weg = w;
        if (w || k.zeit() != z0) return true;                              // fehlt, oder eben aufgetaucht (steht auf t)
        e.antrieb(k, dt);
        return false;
    }

    /** Seit S8b (SF161 A): Mitte der Plätze eines Trupps (Ost, Nord). */
    private double[] truppMitte(String trupp) {
        double[] m = truppMitten.get(trupp);
        if (m != null) return m;
        double x = 0, y = 0;
        int n = 0;
        for (Schwimmer s : schwimmer) if (trupp.equals(s.trupp)) { x += s.ente.heimX; y += s.ente.heimY; n++; }
        m = new double[] {x / Math.max(1, n), y / Math.max(1, n)};
        truppMitten.put(trupp, m);
        return m;
    }

    private final java.util.Map<String, double[]> truppMitten = new java.util.HashMap<>();

    /**
     * Seit S8b (SF163 A): Hat ein Schiff in Fahrt den Ort o (Ost, Nord) bis {@link #AUSWEICH_VOR} m vor dem Bug und bis
     * {@link #AUSWEICH_QUER} m neben dem Rumpf, oder liegt o im Rumpf? Im Faden der Flotte.
     */
    boolean schiffVor(double[] o) {
        for (Schwimmer sh : schwimmer) {
            if (sh.kiel == null || sh.weg) continue;
            Koerper k = sh.koerper;
            double[] v = k.geschwindigkeit(), ka = k.form.kasten();
            double kurs = Math.toRadians(k.kurs()), fo = Math.sin(kurs), fn = Math.cos(kurs);
            double[] bug = k.punktWelt(ka[1], 0, 0);
            double dx = o[0] - bug[0], dy = o[1] - bug[1];
            double laengs = dx * fo + dy * fn, quer = Math.abs(-dx * fn + dy * fo), halb = 0.5 * (ka[3] - ka[2]);
            boolean fahrt = Math.hypot(v[0], v[1]) > AUSWEICH_FAHRT;
            if (fahrt && laengs >= 0 && laengs <= AUSWEICH_VOR && quer <= halb + AUSWEICH_QUER) return true;
            if (laengs < 0 && laengs >= -(ka[1] - ka[0]) && quer <= halb + 1) return true;
        }
        return false;
    }

    /**
     * Seit S8b (SF163 A): Antrieb aus den Rümpfen für ein Stück Treibgut s – liegt es im Kasten eines Rumpfs (halbe Breite am
     * Ort plus {@link #SCHUB_RAND} und die halbe Breite des Stücks), schiebt eine Feder es quer hinaus (ω = 2π /
     * {@link #SCHUB_T}, kritisch gedämpft gegen die Geschwindigkeit des Rumpfs). Sonst kein Antrieb.
     */
    private void schieben(Schwimmer s) {
        Koerper p = s.koerper;
        double[] o = p.lage(), vp = p.geschwindigkeit(), kp = p.form.kasten();
        double r = SCHUB_RAND + 0.5 * (kp[3] - kp[2]);
        double fx = 0, fy = 0, om = 2 * Math.PI / SCHUB_T;
        boolean an = false;
        for (Schwimmer sh : schwimmer) {
            if (sh.kiel == null || sh.weg || sh == s) continue;
            Koerper k = sh.koerper;
            double[] ka = k.form.kasten(), vs = k.geschwindigkeit();
            double kurs = Math.toRadians(k.kurs()), fo = Math.sin(kurs), fn = Math.cos(kurs);
            double[] mitte = k.punktWelt(0, 0, 0);
            double dx = o[0] - mitte[0], dy = o[1] - mitte[1];
            double xb = dx * fo + dy * fn, yb = -dx * fn + dy * fo;          // im Schiff: vorn, Backbord
            if (xb < ka[0] - r || xb > ka[1] + r) continue;
            double xk = Math.max(ka[0], Math.min(ka[1], xb));
            double halb = halbeBreite(k, xk);
            double pen = halb + r - Math.abs(yb);
            if (pen <= 0) continue;
            double sg = yb >= 0 ? 1 : -1, nx = -fn * sg, ny = fo * sg;
            double vrel = (vp[0] - vs[0]) * nx + (vp[1] - vs[1]) * ny;
            // die Bordwand am Ort des Stücks weicht mit v_längs · (−dB/dx) nach außen (der Bug als Keil): darauf gedämpft
            double vl = (vs[0] - vp[0]) * fo + (vs[1] - vp[1]) * fn;
            double dB = (halbeBreite(k, Math.min(ka[1], xk + 0.25)) - halbeBreite(k, Math.max(ka[0], xk - 0.25))) / 0.5;
            double vWand = Math.max(0, vl * -dB);
            double f = p.masse * (om * om * pen - 2 * om * (vrel - vWand));
            fx += f * nx;
            fy += f * ny;
            an = true;
        }
        if (an && !s.geschoben) geschoben++;
        s.geschoben = an;
        p.antriebOst = fx;
        p.antriebNord = fy;
    }

    /** Halbe Breite eines Körpers an der Stelle x (m, im Körper), in der Höhe des Tiefgangs. */
    static double halbeBreite(Koerper k, double x) {
        if (k.form instanceof com.dan.physics.auftrieb.Form.Rumpf ru) return ru.halbeBreite(x, ru.tiefgang);
        double[] ka = k.form.kasten();
        return 0.5 * (ka[3] - ka[2]);
    }

    /** Seit S7e2: Hs bei den Enten (m), je Bild aus der Umwelt ({@link Umwelt#hs}). */
    private volatile double hsEnten;
    private double[] entenMitte;

    /** Pegel, Strom, Wind und Grund für einen Körper (der Grund je Säule neu, wenn er sich bewegt hat). */
    private void umwelt(Schwimmer s, double[] pg, double wu, double wv) {
        int k = schwimmer.indexOf(s);
        Koerper ko = s.koerper;
        double[] sj = stromAuftrag;
        double su = sj != null && 2 * k + 1 < sj.length ? sj[2 * k] : 0, sv = sj != null && 2 * k + 1 < sj.length ? sj[2 * k + 1] : 0;
        s.wasser.setze(pg[k], su, sv, wu, wv);
        double[] o = ko.lage();
        if (Double.isNaN(s.grundX) || Math.hypot(o[0] - s.grundX, o[1] - s.grundY) > 1.0 || Math.abs(ko.kurs() - s.grundKurs) > 5) {
            int m = ko.proben.saeulen;
            double[] x = new double[m], y = new double[m], g = new double[m];
            ko.saeulenWelt(pg[k], x, y, 0);
            for (int c = 0; c < m; c++) g[c] = gelaende.applyAsDouble(x[c], y[c]);
            s.grund = g;
            s.wasser.grund(g);
            s.grundX = o[0];
            s.grundY = o[1];
            s.grundKurs = ko.kurs();
        }
        // Leinen in Ringen an Pfählen: der Ring in der Höhe der Klampe
        for (Festmacher.Leine l : s.ringe) l.poller[2] = ko.punktWelt(l.klampe[0], l.klampe[1], l.klampe[2])[2];
    }

    /**
     * Seit S7d2 (Befund L5d 3): größte Krängung mitführen; kentert ein Schiff (über {@link #KENTERN}), schreibt die Flotte
     * einmal auf, was sie in diesem Augenblick weiß: Lage, Fahrt, Stand der Karte und sein Alter, η und Kielwasser an den
     * Punkten, Zug der Kette, Strom, Wind, Pegel, Hebel und Phase des Lotsen.
     */
    private void kentern(Schwimmer s, double t, Wasserpunkte.Stand st, double[] pg, double wu, double wv) {
        Koerper k = s.koerper;
        double kr = Math.abs(k.kraengung());
        if (kr > s.kraengungMax) s.kraengungMax = kr;
        if (kr <= KENTERN || s.kentern != null || s.kiel == null) return;
        int i = schwimmer.indexOf(s);
        double[] o = k.lage(), v = k.geschwindigkeit();
        StringBuilder b = new StringBuilder();
        java.util.Locale de = java.util.Locale.GERMAN;
        b.append(String.format(de, "t %.2f s: Krängung %.1f°, Trimm %.1f°, Kurs %.0f°; Lage %.1f / %.1f / %.2f m; v %.2f / %.2f / %.2f m/s",
                t, k.kraengung(), k.trimm(), k.kurs(), o[0], o[1], o[2], v[0], v[1], v[2]));
        Wasserpunkte.Stand sw = s.wasser.stand();
        if (sw != null && i < sw.anfrage.anfang.length && sw.anfrage.anfang[i] >= 0) {
            int a0 = sw.anfrage.anfang[i], m = sw.anfrage.anzahl[i];
            double eMin = 1e9, eMax = -1e9, kMin = 1e9, kMax = -1e9, wf = 0, wg = 0, kf = 0, kg = 0;
            for (int c = a0; c < a0 + m; c++) {
                int w0 = c * sw.je;
                eMin = Math.min(eMin, sw.werte[w0]); eMax = Math.max(eMax, sw.werte[w0]);
                kMin = Math.min(kMin, sw.werte[w0 + 8]); kMax = Math.max(kMax, sw.werte[w0 + 8]);
                double[] gg = com.dan.render.seegang.Koerpersuche.gitter(sw.werte[w0 + 7]);   // seit S7d3
                wf += gg[0] / m;
                wg += gg[1] / m;
                kf += gg[3] / m;                                               // seit S7d4: die Körperfläche
                kg += gg[4] / m;
            }
            b.append(String.format(de, "; Stand der Karte %.2f s alt, %d Punkte: η %.2f … %.2f m, Kielwasser %.3f … %.3f m; Gitter des Auges fein %.2f, grob %.2f; Körperfläche fein %.2f, grob %.2f",
                    t - sw.t, m, eMin, eMax, kMin, kMax, wf, wg, kf, kg));
        } else b.append(st == null ? "; ohne Karte (Uhr)" : "; kein Stand der Karte");
        if (s.kette != null) b.append(String.format(de, "; Kette an der Klüse %.0f N", s.kette.zugKlause()));
        double[] sj = stromAuftrag;
        if (sj != null && 2 * i + 1 < sj.length) b.append(String.format(de, "; Strom %.2f / %.2f m/s", sj[2 * i], sj[2 * i + 1]));
        b.append(String.format(de, "; Wind %.1f / %.1f m/s; Pegel %.2f m; Hebel %.2f, Ruder %.2f; Kielwasser-Fahrt %.2f m/s",
                wu, wv, i < pg.length ? pg[i] : Double.NaN, k.hebel, k.ruder, s.kielFahrt));
        if (s.lotsePhase != null) b.append("; Lotse ").append(s.lotsePhase);
        s.kentern = b.toString();
        System.out.println("Flotte: " + s.angabe.name + " gekentert – " + s.kentern);
    }

    /** Stellt alle Körper an ihren Platz: eingeschwommen auf dem Pegel oder, wo der Grund höher liegt, auf dem Grund. */
    private void setzen(double t, double[] pg, double wu, double wv) {
        gischt.leeren();                                                       // seit S8a
        for (int i = 0; i < schwimmer.size(); i++) {
            Schwimmer s = schwimmer.get(i);
            s.kraengungMax = 0;                                                // seit S7d2
            s.kentern = null;
            s.fahrtGlatt = Double.NaN;
            s.festSeit = Double.NaN;                                           // seit S7d4
            Koerper k = s.koerper;
            Schwimmerangabe a = s.angabe;
            if (s.steht) { stelle(s, t); continue; }                          // seit S7e1: Pricke
            k.festmacher().clear();
            s.ringe.clear();
            s.wasser.stand(null);
            s.grundX = Double.NaN;
            umwelt(s, pg, wu, wv);
            double kr0 = Vorgaben.ruheKraengung(k.form.name());              // seit S7e2: der Balken auf der Kante
            k.einschwimmen(s.wasser, a.ost, a.nord, a.kurs, kr0, 0, t);
            // auf dem Grund: so hoch, dass die tiefste Säule ihn berührt
            double hoch = 0;
            double[] gr = s.grund;
            int m = k.proben.saeulen;
            for (int c = 0; c < m && gr != null; c++) {
                double kiel = k.punktWelt(k.proben.saeuleX[c], k.proben.saeuleY[c], 0)[2];
                if (gr[c] > kiel) hoch = Math.max(hoch, gr[c] - kiel);
            }
            if (hoch > 0) {
                double[] o = k.lage();
                k.setzen(o[0], o[1], o[2] + hoch + 0.01, a.kurs, kr0, 0);
            }
            k.zeit(t);
            s.fest = false;
            s.weg = false;                                                     // seit S7e2 (Enten: der Takt beginnt neu)
            if (s.lotse != null) {                                             // seit S7d: wo der Fahrplan sie hat
                s.lotse.abSeit = Double.NaN;
                s.lotse.bremsSoll = Double.NaN;                                // seit S7d8
                if (!nachPlan(s, t, pg, wu, wv, false)) { festmachen(s); s.fest = true; }
            } else festmachen(s);
        }
        stand(t);
    }

    /** Seit S7e1: eine Pricke senkrecht in den Grund, {@link #PRICKE_IM_GRUND} m tief. */
    private void stelle(Schwimmer s, double t) {
        Koerper k = s.koerper;
        double g = gelaende.applyAsDouble(s.angabe.ost, s.angabe.nord);
        if (Double.isNaN(g)) g = PRICKE_GRUND;
        k.setzen(s.angabe.ost, s.angabe.nord, 0, 0, 0, 0);
        double z0 = k.punktWelt(0, 0, 0)[2];
        k.setzen(s.angabe.ost, s.angabe.nord, g - PRICKE_IM_GRUND - z0, 0, 0, 0);
        k.zeit(t);
    }

    /** Die Phase des Lotsen zur Zeit t der Szene (Wetter: Wind und Pegel am Anleger); seit S7d2 mit dem Grund, wenn sie liegt. */
    private Lotse.Phase phase(Schwimmer s, double t, double[] pg, double wu, double wv) {
        double ms = Double.isNaN(startMs) ? 0 : startMs + 1000 * t;
        int k = schwimmer.indexOf(s);
        double wind = Math.hypot(wu, wv);
        boolean wetter = !Double.isNaN(startMs) && wind < Lotse.WIND_MAX && pg[k] >= s.lotse.pegelMin;   // seit S7e2 je Schiff
        Lotse.Phase ph = s.lotse.phase(ms, wetter);
        java.util.Locale de = java.util.Locale.GERMAN;
        s.lotseGrund = wetter ? (ph == Lotse.Phase.LIEGT && s.lotse.nachTide && s.lotse.hochwasser.length == 0 ? " (kein Hochwasser bekannt)" : "")
                : Double.isNaN(startMs) ? " (ohne Uhr)" : wind >= Lotse.WIND_MAX ? String.format(de, " (Wind %.1f m/s)", wind)
                : String.format(de, " (Pegel %.2f m unter %.1f m)", pg[k], s.lotse.pegelMin);
        return ph;
    }

    /** Seit S7d2: die Hochwasser am Pegel (ms seit 1970, UTC) für Fähren, die nach der Tide fahren. */
    public void hochwasser(double[] ms) {
        double[] h = ms.clone();
        java.util.Arrays.sort(h);
        for (Schwimmer s : schwimmer) if (s.lotse != null) s.lotse.hochwasser = h;
    }

    /**
     * Eine Fähre mit Lotse an den Ort des Fahrplans, wenn sie nicht liegt (Sprung, neues Szenario, Zeitraffer): auf dem Weg,
     * mit Kurs und Fahrt des Plans. Gibt false, wenn sie am Anleger liegen soll; im Zeitraffer (schnell) kommt sie dann an
     * ihren Platz und an die Leinen, sonst bleibt sie, wo sie ist. schnell: die Höhe bleibt (kein neues Einschwimmen).
     */
    private boolean nachPlan(Schwimmer s, double t, double[] pg, double wu, double wv, boolean schnell) {
        Lotse.Phase ph = phase(s, t, pg, wu, wv);
        s.lotsePhase = ph.name() + s.lotseGrund;
        s.weg = ph == Lotse.Phase.WEG;                                         // seit S7e2
        Koerper k = s.koerper;
        if (ph == Lotse.Phase.LIEGT) {
            if (schnell && !s.fest) {
                k.festmacher().clear();
                s.ringe.clear();
                k.einschwimmen(s.wasser, s.angabe.ost, s.angabe.nord, s.angabe.kurs, 0, 0, t);
                festmachen(s);
                s.fest = true;
            }
            return false;
        }
        Lotse l = s.lotse;
        l.bremsSoll = Double.NaN;                                              // seit S7d8
        double ms = startMs + 1000 * t;
        double sw = l.sSoll(ms, ph);
        if (ph == Lotse.Phase.FAEHRT_AB) sw = Math.max(sw, 150);
        double[] p = l.punkt(sw, ph != Lotse.Phase.KOMMT);                    // seit S7e2: Abfahrt und weg auf dem Weg der Abfahrt
        double kurs = ph == Lotse.Phase.KOMMT ? (p[2] + 180) % 360 : p[2];
        boolean war = s.fest;
        k.festmacher().clear();
        s.ringe.clear();
        s.fest = false;
        l.los = true;
        if (schnell && !war) {
            double[] o = k.lage();
            k.setzen(p[0], p[1], o[2], kurs, 0, 0);
            k.zeit(t);
        } else k.einschwimmen(s.wasser, p[0], p[1], kurs, 0, 0, t);
        double v = ph == Lotse.Phase.WEG ? 0 : l.fahrt, h = Math.toRadians(kurs);
        k.geschwindigkeit(v * Math.sin(h), v * Math.cos(h), 0);
        k.hebel = ph == Lotse.Phase.WEG ? 0 : l.hebelVoll;
        k.ruder = 0;
        if (ph == Lotse.Phase.FAEHRT_AB) { l.abSeit = t - Lotse.RUECK_S - 1e3; l.abStufe = 2; }   // das Rückwärts und (seit S7d8) das Drehen sind vorbei
        return true;
    }

    /** Ein Schritt des Lotsen in der Zeit der Szene: Phase, Leinen los oder fest, Ruder und Hebel. */
    private void lotse(Schwimmer s, double t, double[] pg, double wu, double wv) {
        Lotse.Phase ph = phase(s, t, pg, wu, wv);
        s.lotsePhase = ph.name() + (s.fest ? ", fest" : "") + s.lotseGrund;
        s.weg = ph == Lotse.Phase.WEG && !s.fest;                              // seit S7e2
        Koerper k = s.koerper;
        Lotse l = s.lotse;
        // seit S7d8 (SF142 A, Befund L5d7 2): fest vor der Zeit des Plans (Phase noch KOMMT) liegt sie ebenso; bis S7d7
        // steuerte der Lotse dann weiter und die Flotte machte sie in jedem Schritt neu fest (5324 Leinen nach 40 s im
        // Container), das Verholen setzte aus
        if ((ph == Lotse.Phase.LIEGT || ph == Lotse.Phase.KOMMT) && s.fest) { k.hebel = 0; k.ruder = 0; verholen(s, t); return; }
        if (ph == Lotse.Phase.FAEHRT_AB && s.fest) {                          // Leinen los
            // seit S8c (SF159 A): ein langer Ton des Typhons beim Ablegen (KVR Regel 34 d / 35: „lang“ 4–6 s) [G], vom Mast über der Brücke
            Tonereignisse te = ton;
            if (te != null) {
                double[] mast = k.punktWelt(0.2 * k.form.kasten()[1], 0, deck(k) + 6);
                te.melde(Tonereignisse.TYPHON, mast[0], mast[1], mast[2], 1, t);
            }
            k.festmacher().clear();
            s.ringe.clear();
            s.leineSoll = null;
            s.festSeit = Double.NaN;
            s.fest = false;
            l.abSeit = t;
            l.abStufe = 0;                                                     // seit S7d8: rückwärts, drehen, Fahrt
            l.tVor = Double.NaN;
        }
        if (ph != Lotse.Phase.FAEHRT_AB) l.abSeit = Double.NaN;
        double[] anl = s.angabe.festZahlen.length >= 2 ? new double[] {s.angabe.festZahlen[0], s.angabe.festZahlen[1]} : new double[] {s.angabe.ost, s.angabe.nord};
        l.ms = Double.isNaN(startMs) ? Double.NaN : startMs + 1000 * t;               // seit S7d7: nach dem Fahrplan
        boolean anlegen = l.steuere(k, ph, t, anl);
        if (ph == Lotse.Phase.FAEHRT_AB)                                       // seit S7d8 (SF144 A): die Stufe der Abfahrt
            s.lotsePhase = ph.name() + (l.abStufe == 0 ? ", rückwärts" : l.abStufe == 1 ? ", dreht" : ", in Fahrt") + s.lotseGrund;
        if (anlegen && (ph == Lotse.Phase.KOMMT || ph == Lotse.Phase.LIEGT)) {
            k.hebel = 0;
            k.ruder = 0;
            l.fuehren(k, false);
            k.festmacher().remove(l.bugF);
            k.festmacher().remove(l.heckF);
            festmachen(s);
            s.fest = true;
            s.festSeit = t;
        }
    }

    /** Seit S7d4 (SF125 A): die Leinen mit {@link #VERHOL_V} auf ihre Länge am Platz der Szene. */
    private static void verholen(Schwimmer s, double t) {
        double[] soll = s.leineSoll;
        if (soll == null || s.ringe.size() != soll.length) return;
        double dt = Double.isNaN(s.verholZeit) ? 0 : Math.max(0, Math.min(1, t - s.verholZeit));
        s.verholZeit = t;
        double d = VERHOL_V * dt;
        for (int j = 0; j < soll.length; j++) {
            Festmacher.Leine l = s.ringe.get(j);
            l.laenge = l.laenge > soll[j] ? Math.max(soll[j], l.laenge - d) : Math.min(soll[j], l.laenge + d);
        }
    }

    /** Festmacher nach der Angabe [G]. */
    private void festmachen(Schwimmer s) {
        Koerper k = s.koerper;
        Schwimmerangabe a = s.angabe;
        double[] z = a.festZahlen;
        // seit S7d8 (SF142 A): was ein früheres Festmachen angelegt hat, vorher abnehmen
        k.festmacher().removeAll(s.angelegt);
        s.angelegt.clear();
        s.ringe.clear();
        int vorher = k.festmacher().size();
        festmachenNeu(s, k, a, z);
        s.angelegt.addAll(k.festmacher().subList(vorher, k.festmacher().size()));
    }

    private void festmachenNeu(Schwimmer s, Koerper k, Schwimmerangabe a, double[] z) {
        switch (a.fest) {
            case "anleger" -> {
                // Rampe (Ost, Nord) und Richtung des Anlegers vom Land ins Wasser (Grad); der Bug liegt an der Rampe
                double ro = z[0], rn = z[1], ri = Math.toRadians(z[2]);
                double ex = Math.sin(ri), ey = Math.cos(ri), qx = Math.cos(ri), qy = -Math.sin(ri);
                double[] k0 = k.form.kasten();
                double L = k0[1], B = k0[3], d = deck(k);
                double[] p0 = {ro, rn, 0}, nn = {ex, ey, 0};
                for (double sy : new double[] {-0.3, 0.3}) k.festmachen(new Festmacher.Fender(new double[] {L, sy * B, d - 0.6}, 0.35, p0, nn, 4e5));
                // Bugleinen zu Pollern an der Rampe, Heckleinen zu Pfählen schräg hinter dem Heck
                double[][] kl = {{L - 1.5, 0.8 * B, d}, {L - 1.5, -0.8 * B, d}, {-L + 2, 0.8 * B, d}, {-L + 2, -0.8 * B, d}};
                double[][] po = {{ro + 6 * qx, rn + 6 * qy}, {ro - 6 * qx, rn - 6 * qy}, null, null};
                double[] mitte = {a.ost, a.nord};                               // seit S7d4: die Pfähle stehen zum Platz der Szene
                for (int j = 2; j < 4; j++) {
                    double sg = j == 2 ? 1 : -1;
                    po[j] = new double[] {mitte[0] + (L + 6) * ex - sg * 10 * qx, mitte[1] + (L + 6) * ey - sg * 10 * qy};
                }
                // ex zeigt vom Land ins Wasser, also vom Bug zum Heck: das Heck liegt bei mitte + L ex (Bug an der Rampe)
                for (int j = 0; j < 4; j++) {
                    double[] p = k.punktWelt(kl[j][0], kl[j][1], kl[j][2]);
                    double[] pp = {po[j][0], po[j][1], p[2]};
                    double len = Math.sqrt((pp[0] - p[0]) * (pp[0] - p[0]) + (pp[1] - p[1]) * (pp[1] - p[1]));
                    Festmacher.Leine l = new Festmacher.Leine(kl[j], pp, len + 0.2, 1.5e6);
                    k.festmachen(l);
                    s.ringe.add(l);
                }
                // seit S7d4 (SF125 A): die Länge jeder Leine, wenn die Fähre an ihrem Platz läge (Schwerpunkt a.ost/a.nord, Kurs
                // a.kurs, waagerecht); das Verholen holt sie dorthin
                double[] o = k.lage();
                double dk = Math.toRadians(a.kurs - k.kurs()), cd = Math.cos(dk), sd = Math.sin(dk);
                s.leineSoll = new double[4];
                for (int j = 0; j < 4; j++) {
                    double[] p = k.punktWelt(kl[j][0], kl[j][1], kl[j][2]);
                    double vx = p[0] - o[0], vy = p[1] - o[1];
                    double nx = a.ost + cd * vx + sd * vy, ny = a.nord - sd * vx + cd * vy;   // Kurs im Uhrzeigersinn um dk gedreht
                    s.leineSoll[j] = Math.hypot(po[j][0] - nx, po[j][1] - ny) + 0.2;
                }
                s.verholZeit = Double.NaN;
            }
            case "anker" -> {
                double[] k0 = k.form.kasten();
                double L = k0[1];
                Kette c = Kette.stahl(z.length > 2 ? z[2] : 30, 0.016, new double[] {L - 0.3, 0, deck(k)});
                c.fest = true;
                c.haltekraft = 3e4;
                c.legen(s.wasser, z[0], z[1], k.punktWelt(L - 0.3, 0, deck(k)));
                k.festmachen(c);
                s.kette = c;
            }
            case "kette" -> {
                double len = z.length > 0 ? z[0] : 10;
                Kette c = Kette.stahl(len, 0.016, new double[] {0, 0, 0});
                c.fest = true;
                double[] o = k.lage();
                c.legen(s.wasser, o[0] - 0.3 * len, o[1], k.punktWelt(0, 0, 0));
                k.festmachen(c);
                s.kette = c;
            }
            default -> { }
        }
    }

    static double deck(Koerper k) {
        return k.form instanceof com.dan.physics.auftrieb.Form.Rumpf r ? r.deck(r.laenge / 2 - 1) : k.form.kasten()[5];
    }

    /** Legt den Stand nach einem Schritt ab (Lagen, Geschwindigkeit, Säulen für die nächste Anfrage). */
    private void stand(double t) {
        int n = schwimmer.size();
        double[] l = new double[16 * n], v = new double[3 * n];
        double[][] so = new double[n][], sn = new double[n][];
        double[] ti = new double[n];
        int[] tv = new int[n];
        java.util.Map<String, Integer> fuehrt = new java.util.HashMap<>();
        for (int k = 0; k < n; k++) {
            Schwimmer s = schwimmer.get(k);
            Koerper ko = s.koerper;
            ko.modell(l, 16 * k);
            if (s.weg) l[16 * k + 14] = WEG_TIEFE;                            // seit S7e2: im Bild tief unter dem Wasser
            double[] g = ko.geschwindigkeit();
            v[3 * k] = g[0]; v[3 * k + 1] = g[1]; v[3 * k + 2] = g[2];
            int m = s.steht || s.weg || s.ohneKarte ? 0 : ko.kartenpunkte;                  // seit S7b-4: die Punkte der Karte; seit S7e1 Pricken, seit S7e2 wer weg ist ohne
            double[] o = ko.lage();
            double pgl = s.wasser.pegel(o[0], o[1], t);
            tv[k] = -1;
            double[] au = augeAuftrag;                                        // seit S8b: kleine Schwimmer nur nah am Auge mit Wellen
            if (m > 0 && (s.ente != null || s.treibgut) && au != null && KLEIN_UMKREIS > 0 && Math.hypot(o[0] - au[0], o[1] - au[1]) > KLEIN_UMKREIS) m = 0;
            if (m > 0 && s.trupp != null && TRUPP_PUNKT) {                    // seit S8b (SF161 A, nur mit -Dlauf.entenpunkte=trupp): ein Punkt je Trupp
                Integer erste = fuehrt.get(s.trupp);
                if (erste == null) {
                    fuehrt.put(s.trupp, k);
                    double[] mi = truppMitte(s.trupp);
                    so[k] = new double[] {mi[0]};
                    sn[k] = new double[] {mi[1]};
                } else {
                    tv[k] = erste;
                    so[k] = new double[0];
                    sn[k] = new double[0];
                }
            } else {
                so[k] = new double[m];
                sn[k] = new double[m];
                if (m > 0) ko.kartenWelt(pgl, so[k], sn[k], 0);
            }
            // seit S8d: das Wasser an der Mitte des Kopfs (Schwimmerblick): η, Gefälle, Grund
            if ("kopf".equals(s.angabe.art)) {
                double[] w = new double[6];
                s.wasser.welle(o[0], o[1], 0, t, w);
                double gk = gelaende.applyAsDouble(o[0], o[1]);
                kopfWasser = new double[] {k, pgl + w[0], w[1], w[2], t, Double.isNaN(gk) ? pgl - 20 : gk};
            }
            // seit S7d: Tiefe am Bug für das Kielwasser
            if (s.kiel != null) {
                double[] bug = ko.punktWelt(ko.form.kasten()[1], 0, 0);
                double gr = gelaende.applyAsDouble(bug[0], bug[1]);
                ti[k] = Double.isNaN(gr) ? 20 : Math.max(0.3, pgl - gr);
            }
        }
        synchronized (schloss) {
            lagen = l;
            geschw = v;
            saeulenOst = so;
            saeulenNord = sn;
            truppVon = tv;
            tiefeJe = ti;
            tStand = t;
            neu = true;
        }
    }

    /**
     * Seit S7d4 (Seegang Werkbuch 92, SF121 A): Mitte der Plätze der Schwimmer, die an einem Anleger oder vor Anker liegen
     * (UTM Ost, Nord), für die Körperfläche, wenn die Szene keine nennt; null ohne solche.
     */
    public double[] koerpermitte() {
        double x = 0, y = 0;
        int n = 0;
        for (Schwimmer s : schwimmer)
            if ("anleger".equals(s.angabe.fest) || "anker".equals(s.angabe.fest)) { x += s.angabe.ost; y += s.angabe.nord; n++; }
        return n == 0 ? null : new double[] {x / n, y / n};
    }

    /** Der letzte Stand der Lagen (je Körper 16 double, Körper → Welt) und seine Zeit, für Prüfungen. */
    public double[] lagen() { synchronized (schloss) { return lagen.clone(); } }

    public double zeit() { synchronized (schloss) { return tStand; } }

    /** Wartet, bis der Faden den letzten Auftrag gerechnet hat (Prüfungen). */
    public void warte() throws InterruptedException {
        for (int i = 0; i < 20000; i++) {
            synchronized (schloss) { if (!auftrag) break; }
            Thread.sleep(1);
        }
        // der Faden nimmt den Auftrag, dann ist er fertig, wenn der Stand die Zeit hat
        for (int i = 0; i < 20000; i++) {
            synchronized (schloss) { if (!auftrag && tStand == tZiel) return; }
            Thread.sleep(1);
        }
    }

    @Override public void close() {
        synchronized (schloss) {
            ende = true;
            schloss.notifyAll();
        }
        try { faden.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
