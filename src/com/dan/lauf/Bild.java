package com.dan.lauf;

import com.dan.core.gitter.Gebiet;
import com.dan.core.gitter.Gitter;
import com.dan.core.raum.Blick;
import com.dan.physics.atmos.Profil;
import com.dan.render.air.Kimmung;
import com.dan.render.terrain.Gelaendebild;
import com.dan.core.szene.Bodennetz;
import com.dan.core.szene.Seegangsnetz;
import com.dan.core.szene.Spiegel;
import com.dan.render.water.Wasserbild;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Stände für das Bild (P6e, Werkbuch HalligHooge Teil A, 12.7; F40 A): bringt {@code water.eta}, nass oder trocken
 * und den Wind aus einer {@link Quelle} – live vom Bus ({@link BildModul}) oder aus dem Archiv ({@link Archivquelle}) –
 * in die Bildmodule. Je Bild ruft man {@link #zu}: Es sucht die zwei Stände um die Szenenzeit, gibt sie den
 * {@link Spiegel}n von {@code render.water}, wenn sie neu sind, setzt die Zeit des Wassers und den Wind am Auge und gibt
 * dem Gelände die Nässe des jüngeren Stands.
 * <p>
 * <b>Stände wechseln.</b> Ist der jüngere bisherige Stand der ältere neue (vorwärts), kommt nur der neue jüngere dazu;
 * sonst (rückwärts, gesprungen) beide neu. Wird ein Stand vom Bus verdrängt, während das Bild ihn holt, versucht es
 * das nächste Bild noch einmal. Die Nässe des Geländes folgt dem jüngeren Stand (alle 300 s Modellzeit), nicht jedem
 * Bild [G].
 * <p>
 * Nur Gitter in UTM gehen ins Wasser (G0 ist geografisch); höchstens {@link Wasserbild#NETZE}.
 */
public final class Bild {
    /** Woher die Stände kommen: dieselbe Schnittstelle für den Bus und das Archiv. */
    public interface Quelle {
        /** Woher, kurz, für Attest und Einblendung („Bus“, „Archiv …“). */
        String herkunft();

        /** Die Gitter des Wassers, grob zuerst. */
        List<Gitter> gitter();

        /** Das Gitter des Winds (null: kein Wind). */
        Gitter windGitter();

        /** Zeiten der gehaltenen Stände (s seit Start), ältester zuerst; leer, solange keiner liegt. */
        double[] zeiten();

        /** Liest den Stand zur Zeit ts des Gitters k; false, wenn er nicht (mehr) gehalten wird. */
        boolean stand(double ts, int k, float[] eta, float[] h, float[] wetAge);

        /** Liest den Wind (m/s nach Ost und Nord) zur Zeit ts; false, wenn keiner da ist. */
        boolean wind(double ts, float[] u, float[] v);

        /** Die Gitter des Seegangs, grob zuerst (P8b); leer ohne Seegang. */
        default List<Gitter> seegangGitter() { return List.of(); }

        /** Zeiten der Stände des Seegangs (s seit Start), ältester zuerst. */
        default double[] seegangZeiten() { return new double[0]; }

        /** Liest den Seegang zur Zeit ts des Gitters k: {@code waves.hs}, {@code .tp}, {@code .dir}, {@code .mss}, {@code .diss} in {@code felder[0..4]}. */
        default boolean seegang(double ts, int k, float[][] felder) { return false; }

        /** Das Gitter des Sediments (P9b); null ohne Sediment. */
        default Gitter sedimentGitter() { return null; }

        /** Zeiten der Stände des Schwebstoffs (s seit Start), ältester zuerst. */
        default double[] sedimentZeiten() { return new double[0]; }

        /** Liest {@code sed.c} zur Zeit ts: je Zelle Sand, Schlick (kg/m³). */
        default boolean sediment(double ts, float[] c) { return false; }

        /** Zeiten der Stände des Bodens (s seit Start), ältester zuerst. */
        default double[] bodenZeiten() { return new double[0]; }

        /** Liest den Boden zur Zeit ts: {@code bed.ripple} (je Zelle Höhe, Länge, Richtung), {@code bed.mud}, {@code bed.dz}. */
        default boolean boden(double ts, float[] rippel, float[] schlick, float[] dz) { return false; }

        /** Seit P9c: Hat hydro mit dem bewegten Boden gerechnet (Rückwirkung von {@code bed.dz})? Nur dann hebt das Bild das Gelände mit. */
        default boolean bodenBewegt() { return false; }

        /** Das Gitter von atmos (P10b; {@code surf.wet}); null ohne atmos. */
        default Gitter atmosGitter() { return null; }

        /** Zeiten der Stände von atmos (s seit Start), ältester zuerst. */
        default double[] atmosZeiten() { return new double[0]; }

        /** Liest {@code surf.wet} zur Zeit ts: je Zelle die Sättigung des obersten Zentimeters (0 … 1). */
        default boolean naesse(double ts, float[] wet) { return false; }

        /** Das Gitter des Flugsands (P10c; {@code aeol.q}); null ohne aeolian. */
        default Gitter flugGitter() { return null; }

        /** Zeiten der Stände des Flugsands (s seit Start), ältester zuerst. */
        default double[] flugZeiten() { return new double[0]; }

        /** Liest {@code aeol.q} zur Zeit ts: je Zelle der Fluss nach Ost und nach Nord (kg/(m s)) bei {@code 2 c} und {@code 2 c + 1}. */
        default boolean flugsand(double ts, float[] q) { return false; }

        /** Das Gitter der Säulen der Luft (P10b; {@code air.n}); null ohne. */
        default Gitter luftGitter() { return null; }

        /** Zahl der Höhen je Säule in {@code air.n}. */
        default int luftStufen() { return 0; }

        /** Zeiten der Stände der Luft (s seit Start), ältester zuerst. */
        default double[] luftZeiten() { return new double[0]; }

        /** Liest {@code air.n} zur Zeit ts: je Säule s und Höhe k die Brechung n − 1 in {@code n[s * Stufen + k]}. */
        default boolean luft(double ts, float[] n) { return false; }

        /** Seit P11c1: das Gitter der Strömung (u, v, q; aus dem Nachlauf oder live) oder null. */
        default Gitter stroemungGitter() { return null; }

        /** Seit P11c1: Zeiten der Stände der Strömung. */
        default double[] stroemungZeiten() { return new double[0]; }

        /** Seit P11c1: u, v (m/s, in der Zelle, Achsen des Gitters) zur Zeit ts; false ohne. */
        default boolean stroemung(double ts, float[] u, float[] v) { return false; }

        /**
         * Seit W5b (F216 A): eta, h und wetAge einer Zelle des Gitters k zur Zeit ts in {@code aus[0..2]}; false wie
         * {@link #stand}. Die Vorgabe liest den ganzen Stand; ein Archiv liest nur die Zelle.
         */
        default boolean standZelle(double ts, int k, int zelle, float[] aus) {
            int n = gitter().get(k).zellen();
            float[] e = new float[n], h = new float[n], a = new float[n];
            if (!stand(ts, k, e, h, a)) return false;
            aus[0] = e[zelle];
            aus[1] = h[zelle];
            aus[2] = a[zelle];
            return true;
        }

        /**
         * Seit W5c (Werkbuch HalligHooge 131, F221 A): ob das Bild zur Zeit ts zeichnen kann, ohne auf ein Laufwerk zu
         * warten. Vorgabe: ja (der Bus hält alles im Speicher). Ein Archiv sagt ja, wenn die Stände um ts vorgelesen sind.
         */
        default boolean bereit(double ts) { return true; }

        /**
         * Seit W5c: wohin das Bild läuft – ein Archiv liest die Stände ab ts in Richtung des Zeitraffers in einem eigenen
         * Faden voraus (rund {@code sekunden} Echtzeit). Kehrt sofort zurück.
         */
        default void vorlesen(double ts, double zeitraffer, double sekunden) { }

        /** Seit W5b: der Wind (u, v) einer Zelle von {@link #windGitter} zur Zeit ts in {@code aus[0..1]}. */
        default boolean windZelle(double ts, int zelle, float[] aus) {
            int n = windGitter().zellen();
            float[] u = new float[n], v = new float[n];
            if (!wind(ts, u, v)) return false;
            aus[0] = u[zelle];
            aus[1] = v[zelle];
            return true;
        }

        /** Seit W5b: die Strömung (u, v) einer Zelle von {@link #stroemungGitter} zur Zeit ts in {@code aus[0..1]}. */
        default boolean stroemungZelle(double ts, int zelle, float[] aus) {
            Gitter g = stroemungGitter();
            if (g == null) return false;
            float[] u = new float[g.zellen()], v = new float[g.zellen()];
            if (!stroemung(ts, u, v)) return false;
            aus[0] = u[zelle];
            aus[1] = v[zelle];
            return true;
        }

        /** Seit P11c1: Fluss je Kante (m²/s, Mittel über das Intervall bis ts) zur Zeit ts; false ohne. */
        default boolean fluss(double ts, float[] q) { return false; }
    }

    /** Die Kimmung (P10b; null: ohne): bekommt je Bild die Luft im Blickfeld ({@link #blick}). */
    public Kimmung kimmung;
    /**
     * Seit P10d (Messung d): der Himmel (null: ohne); bekommt je Bild den Anteil des Wassers am Boden der Szene
     * ({@link #wasserAnteil}), aus dem er die Albedo seiner Luft nimmt.
     */
    public com.dan.render.air.Himmel himmel;
    /** Umkreis um das Auge, in dem der Anteil des Wassers zählt (m) [G]. */
    public static final double UMKREIS_ANTEIL = 15_000;
    /**
     * Seit P10d (F106 B): ob der Himmel seine Albedo aus {@link #wasserAnteil} nimmt (Messung d, F105 A). Aus: Wasser ist
     * für den Himmel kein Boden nach Lambert (Werkbuch, Datei 47); mit 0,23 fest hält der Himmel die Referenz besser. Der
     * Anteil wird weiter gerechnet (Auskunft); J mit spiegelndem Wasser geht nach P11.
     */
    public static boolean ALBEDO_AUS_ANTEIL = false;
    /**
     * Anteil der Zellen mit Wasser (h &gt; 5 cm, wie in {@link #blick}) im Gitter der Nässe im Umkreis {@link #UMKREIS_ANTEIL}
     * um das Auge, aus dem jüngeren Stand; neu, wenn ein Stand kommt oder das Auge mehr als 1 km wandert. NaN ohne Stand.
     */
    public double wasserAnteil = Double.NaN;
    private double anteilStand = Double.NaN, anteilOst, anteilNord;

    public final Quelle quelle;
    public final Wasserbild wasser;
    /** Das Gelände für die Nässe (null: ohne). */
    public final Gelaendebild gelaende;
    private final List<Gitter> gitter = new ArrayList<>();
    private final List<Integer> index = new ArrayList<>();
    private final Spiegel[] spiegel;
    /** Gitter (Index in {@link #gitter}), dessen wetAge die Nässe des Geländes gibt: G3, sonst das feinste. */
    private final int nassGitter;
    private final float[][] eta, h, age;
    private final float[] nassJung;
    private final Gitter wg;
    private float[] uA, vA, uB, vB;
    private double paarA = Double.NaN, paarB = Double.NaN;
    /** Wie oft ein Stand verdrängt war, bevor das Bild ihn hatte; wie oft Stände gesetzt wurden. */
    public long verpasst, gesetzt;
    /** Wind am Auge im letzten Bild (m/s). */
    public double windU, windV;
    // Seegang (P8b): die Netze und die zwei Stände um die Szenenzeit
    private final Seegangsnetz[] see;
    private final float[][][] seeFelder;
    private double seePaarA = Double.NaN, seePaarB = Double.NaN;
    // Sediment (P9b): Trübung in zwei Ständen um die Szenenzeit, vom Boden der jüngste Stand davor
    private final Bodennetz boden;
    private float[] sedC, bodRippel, bodSchlick, bodDz;
    private double sedPaarA = Double.NaN, sedPaarB = Double.NaN, bodStand = Double.NaN;
    // atmos (P10b): Nässe und Luft des jüngsten Stands bis zur Szenenzeit
    private final Gitter atmosGitter, luftGitter, flugGitter;
    private float[] flugQ;
    private double flugStand = Double.NaN;
    private final int luftStufen;
    private float[] nassWet, luftN;
    private double atmosStand = Double.NaN, luftStand = Double.NaN;
    /** Womit die Luft zuletzt an die Kimmung ging: Stand der Luft, Stand des Wassers, Ort und Richtung des Blicks. */
    private double kimmLuft = Double.NaN, kimmWasser = Double.NaN, kimmOst, kimmNord, kimmRichtung, kimmWinkel;
    /** Aus dem letzten {@link #blick}: Zahl der Säulen im Blickfeld, Höhe der Fläche (m NHN), Sperrhöhe (m). */
    public int kimmSaeulen;
    public double kimmFlaeche = Double.NaN, kimmSperr = Double.NaN;
    /** Das Profil, das die Kimmung zuletzt bekam: Höhen über der Fläche (m) und n − 1 (null: keins). */
    public double[] kimmHoehen, kimmBrechung;

    /**
     * @param ge Gebiet (für die Bauwerke; Gitter, die ein Fenster eines Gitters im Gebiet sind, bekommen die Kanten
     *           relativ zum Fenster)
     */
    public Bild(Quelle quelle, Gebiet ge, Wasserbild wasser, Gelaendebild gelaende) throws IOException {
        this.quelle = quelle;
        this.wasser = wasser;
        this.gelaende = gelaende;
        List<Gitter> alle = quelle.gitter();
        for (int k = 0; k < alle.size(); k++)
            if (alle.get(k).lage == Gitter.Lage.UTM) {
                gitter.add(alle.get(k));
                index.add(k);
            }
        if (gitter.size() > Wasserbild.NETZE) throw new IllegalArgumentException("Bild: " + gitter.size() + " Gitter, höchstens " + Wasserbild.NETZE);
        spiegel = new Spiegel[gitter.size()];
        eta = new float[gitter.size()][];
        h = new float[gitter.size()][];
        age = new float[gitter.size()][];
        int ng = gitter.size() - 1;
        for (int k = 0; k < gitter.size(); k++) {
            Gitter g = gitter.get(k);
            spiegel[k] = new Spiegel(g);
            eta[k] = new float[g.zellen()];
            h[k] = new float[g.zellen()];
            age[k] = new float[g.zellen()];
            if (g.name.equals("G3")) ng = k;
            if (ge != null) {
                Gebiet.Eintrag e = null;
                for (Gebiet.Eintrag x : ge.kette()) if (x.gitter().name.equals(g.name)) e = x;
                if (e != null) {
                    Gitter voll = e.gitter();
                    int i0 = (int) Math.round((g.x0 - voll.x0) / voll.dx), j0 = (int) Math.round((g.y0 - voll.y0) / voll.dy);
                    if (new File(e.ordner(), "bauwerke.txt").isFile()) spiegel[k].bauwerke(ge.bauwerke(g.name), i0, j0);
                    // Schwellen der Kanten aus der Packung (F47 A); die Packung selbst bleibt nicht im Bild
                    if (new File(e.ordner(), "subgrid.bin").isFile()) spiegel[k].schwellen(ge.packung(g.name), voll, i0, j0);
                }
            }
            wasser.spiegel(spiegel[k]);
        }
        nassGitter = ng;
        nassJung = new float[gitter.isEmpty() ? 0 : gitter.get(ng).zellen()];
        wg = quelle.windGitter();
        if (wg != null && wg.lage != Gitter.Lage.UTM) throw new IllegalArgumentException("Bild: Wind nur auf einem Gitter in UTM, nicht " + wg);
        if (wg != null) {
            uA = new float[wg.zellen()];
            vA = new float[wg.zellen()];
            uB = new float[wg.zellen()];
            vB = new float[wg.zellen()];
        }
        List<Gitter> sg = quelle.seegangGitter();
        see = new Seegangsnetz[sg.size()];
        seeFelder = new float[sg.size()][][];
        for (int k = 0; k < see.length; k++) {
            see[k] = new Seegangsnetz(sg.get(k));
            seeFelder[k] = new float[5][sg.get(k).zellen()];
            wasser.seegang(see[k]);
        }
        Gitter bg = quelle.sedimentGitter();
        if (bg != null) {
            boden = new Bodennetz(bg);
            sedC = new float[2 * bg.zellen()];
            bodRippel = new float[3 * bg.zellen()];
            bodSchlick = new float[bg.zellen()];
            bodDz = new float[bg.zellen()];
            wasser.boden(boden);
        } else {
            boden = null;
            wasser.boden(null);
            if (gelaende != null) gelaende.boden(null, null);
        }
        atmosGitter = quelle.atmosGitter();
        if (atmosGitter != null) nassWet = new float[atmosGitter.zellen()];
        flugGitter = quelle.flugGitter();
        if (flugGitter != null) flugQ = new float[2 * flugGitter.zellen()];
        else if (gelaende != null) gelaende.flugsand(null, null);
        if (gelaende != null) gelaende.nassAlsSaettigung = false;
        luftGitter = quelle.luftGitter();
        luftStufen = quelle.luftStufen();
        if (luftGitter != null) {
            if (luftStufen != Profil.STUFEN) throw new IllegalArgumentException("Bild: air.n mit " + luftStufen + " Höhen statt " + Profil.STUFEN);
            luftN = new float[luftGitter.zellen() * luftStufen];
        }
    }

    /** Ob die Quelle atmos hat (P10b): Dann kommt die Nässe des Geländes aus {@code surf.wet}, sonst aus {@code water.wetAge}. */
    public boolean mitAtmos() { return atmosGitter != null; }

    /** Seit P10c: Hält die Quelle Flugsand ({@code aeol.q})? */
    public boolean mitFlugsand() { return flugGitter != null; }

    /** Das Gitter des Flugsands und der jüngste gelesene Stand von {@code aeol.q} (je Zelle zwei Werte); null ohne. */
    public Gitter flugGitter() { return flugGitter; }

    public float[] flugsand() { return flugQ; }

    /** Das Netz des Sediments (P9b; null ohne). */
    public Bodennetz boden() { return boden; }

    /** Die Netze des Seegangs, grob zuerst (P8b). */
    public List<Seegangsnetz> seegang() { return List.of(see); }

    /** Das Gitter, dessen wetAge die Nässe des Geländes gibt (null: keins), und wetAge des jüngeren Stands (s), für die Referenz (P7c). */
    public Gitter nassGitter() { return gitter.isEmpty() ? null : gitter.get(nassGitter); }

    public float[] nassAlter() { return nassJung; }

    /** Seit P10b: das Gitter von atmos (null: keins) und {@code surf.wet} des jüngsten Stands bis zur Szenenzeit, für die Referenz. */
    public Gitter atmosGitter() { return atmosGitter; }

    public float[] naesse() { return nassWet; }

    /** Seit P10b: die Brechung n − 1 je Säule und Höhe des jüngsten Stands (null: keine Luft) und das Gitter der Säulen. */
    public Gitter luftGitter() { return luftGitter; }

    public float[] luft() { return Double.isNaN(luftStand) ? null : luftN; }

    /** Die Spiegel des Wassers, grob zuerst. */
    public List<Spiegel> spiegel() { return List.of(spiegel); }

    /** Die Zeiten der beiden Stände, zwischen denen das Bild liegt (NaN vor dem ersten). */
    public double[] paar() { return new double[] {paarA, paarB}; }

    /**
     * Je Bild: Wasser und Nässe zur Szenenzeit t (s seit Start), Wind am Auge (O, N in m). Gibt false, solange die Quelle
     * keinen Stand hat. Liegt t außerhalb der Stände, gilt der nähere.
     */
    public boolean zu(double t, double ost, double nord) {
        long n0 = System.nanoTime();
        try {
            return zuIntern(t, ost, nord, n0);
        } finally {
            double ganz = (System.nanoTime() - n0) / 1e9;
            zuTeile[5] = ganz - zuTeile[0] - zuTeile[1] - zuTeile[2] - zuTeile[3] - zuTeile[4];
            if (ganz > zuGroesst[6]) {
                System.arraycopy(zuTeile, 0, zuGroesst, 0, 6);
                zuGroesst[6] = ganz;
            }
        }
    }

    /** Seit W3b (Werkbuch HalligHooge 118, A47): Namen der Teile von {@link #zu} für {@link #zuTeile} und {@link #zuGroesst}. */
    public static final String[] ZU_TEILE = {"Stände", "Seegang", "Sediment", "Atmos", "Flugsand", "Rest"};
    /** Dauer (s) der Teile des letzten {@link #zu}. */
    public final double[] zuTeile = new double[6];
    /** Die Teile des längsten {@link #zu} seit {@link #zuGroesstWeg()}, an Stelle 6 die ganze Dauer (s). */
    public final double[] zuGroesst = new double[7];

    public void zuGroesstWeg() { java.util.Arrays.fill(zuGroesst, 0); }

    /** Die Teile als Text, etwa „Stände 17,8 s, Rest 0,1 s“ (nur Teile ab 1 ms). */
    public static String teileText(double[] t) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++)
            if (t[i] >= 0.001) sb.append(sb.length() > 0 ? ", " : "").append(ZU_TEILE[i]).append(String.format(java.util.Locale.GERMAN, " %.3f s", t[i]));
        return sb.length() == 0 ? "alle unter 1 ms" : sb.toString();
    }

    private boolean zuIntern(double t, double ost, double nord, long n0) {
        java.util.Arrays.fill(zuTeile, 0);
        double[] z = quelle.zeiten();
        if (z.length == 0) return false;
        int i = 0;
        while (i + 1 < z.length && z[i + 1] <= t) i++;
        double a = z[i], b = i + 1 < z.length && t > a ? z[i + 1] : a;
        if (t < z[0]) a = b = z[0];
        long nS = System.nanoTime();
        if (a != paarA || b != paarB) {
            boolean ok;
            if (paarB == a && a != b) ok = setze(b, 1);
            else {
                ok = setze(a, 0);
                if (ok && b != a) ok = setze(b, 1);
            }
            if (ok) {
                paarA = a;
                paarB = b;
                if (gelaende != null && !gitter.isEmpty() && atmosGitter == null) gelaende.naesse(gitter.get(nassGitter), nassJung);
            } else {
                paarA = paarB = Double.NaN;
                verpasst++;
            }
        }
        long n1 = System.nanoTime();
        zuTeile[0] = (n1 - nS) / 1e9;
        if (see.length > 0) seegangZu(t);
        long n2 = System.nanoTime();
        zuTeile[1] = (n2 - n1) / 1e9;
        if (boden != null) sedimentZu(t);
        long n3 = System.nanoTime();
        zuTeile[2] = (n3 - n2) / 1e9;
        if (atmosGitter != null || luftGitter != null) atmosZu(t);
        long n4 = System.nanoTime();
        zuTeile[3] = (n4 - n3) / 1e9;
        if (flugGitter != null) flugZu(t);
        zuTeile[4] = (System.nanoTime() - n4) / 1e9;
        // ein einzelner Stand (vor dem ersten, nach dem letzten): die Zeit des Wassers auf ihn, damit nur er gilt
        wasser.zeit = paarA == paarB && !Double.isNaN(paarB) ? paarB : t;
        // seit P10d (Messung d): Anteil des Wassers für die Albedo der Luft
        if (!Double.isNaN(paarB) && !gitter.isEmpty() && (paarB != anteilStand || Math.hypot(ost - anteilOst, nord - anteilNord) > 1000)) {
            wasserAnteil = anteil(ost, nord);
            anteilStand = paarB;
            anteilOst = ost;
            anteilNord = nord;
        }
        if (himmel != null) {
            // seit F106 B: der Himmel rechnet wieder mit 0,23 fest; die Mischung nach dem Anteil nur mit ALBEDO_AUS_ANTEIL
            himmel.wasserAnteil = ALBEDO_AUS_ANTEIL ? wasserAnteil : Double.NaN;
            // seit P10d (F105 A): das Wasser und der Wind für die Albedo des Wassers
            himmel.wasserOptik = wasser.optik;
            himmel.wind = Math.hypot(wasser.u10, wasser.v10);
        }
        if (wg != null && !Double.isNaN(paarA)) {
            double w = paarB > paarA ? Math.max(0, Math.min(1, (t - paarA) / (paarB - paarA))) : 1;
            double[] ua = probe(wg, uA, vA, ost, nord), ub = probe(wg, uB, vB, ost, nord);
            windU = ua[0] + w * (ub[0] - ua[0]);
            windV = ua[1] + w * (ub[1] - ua[1]);
            wasser.u10 = windU;
            wasser.v10 = windV;
        }
        return !Double.isNaN(paarA);
    }

    /**
     * Seit W2 (Werkbuch HalligHooge 113, Prüfstein 6): Anteil der Zellen mit Wasser (Tiefe über 5 cm) im Gitter der Nässe,
     * deren Mitte im Rechteck (UTM) liegt; NaN ohne Gitter oder Zelle.
     */
    public double nassAnteil(double ost0, double nord0, double ost1, double nord1) {
        if (gitter.isEmpty()) return Double.NaN;
        Gitter g = gitter.get(nassGitter);
        float[] hh = h[nassGitter];
        if (hh == null) return Double.NaN;
        long nass = 0, alle = 0;
        for (int j = 0; j < g.ny; j++) {
            double y = g.y0 + (j + 0.5) * g.dy;
            if (y < nord0 || y > nord1) continue;
            for (int i = 0; i < g.nx; i++) {
                double x = g.x0 + (i + 0.5) * g.dx;
                if (x < ost0 || x > ost1) continue;
                float v = hh[i + g.nx * j];
                if (Float.isNaN(v)) continue;
                alle++;
                if (v > 0.05f) nass++;
            }
        }
        return alle == 0 ? Double.NaN : (double) nass / alle;
    }

    /** Anteil der Zellen mit Wasser im Gitter der Nässe im Umkreis {@link #UMKREIS_ANTEIL} um (ost, nord); NaN ohne Zelle. */
    private double anteil(double ost, double nord) {
        Gitter g = gitter.get(nassGitter);
        float[] hh = h[nassGitter];
        double r = UMKREIS_ANTEIL, r2 = r * r;
        int i0 = Math.max(0, (int) Math.floor((ost - r - g.x0) / g.dx)), i1 = Math.min(g.nx - 1, (int) Math.ceil((ost + r - g.x0) / g.dx));
        int j0 = Math.max(0, (int) Math.floor((nord - r - g.y0) / g.dy)), j1 = Math.min(g.ny - 1, (int) Math.ceil((nord + r - g.y0) / g.dy));
        long nass = 0, alle = 0;
        for (int j = j0; j <= j1; j++) {
            double dy = g.y0 + (j + 0.5) * g.dy - nord;
            for (int i = i0; i <= i1; i++) {
                double dx = g.x0 + (i + 0.5) * g.dx - ost;
                if (dx * dx + dy * dy > r2) continue;
                float v = hh[i + g.nx * j];
                if (Float.isNaN(v)) continue;
                alle++;
                if (v > 0.05f) nass++;
            }
        }
        return alle == 0 ? Double.NaN : (double) nass / alle;
    }

    /** Die zwei Stände des Seegangs um t in die Netze (wie das Wasser: vorwärts nur der neue). */
    private void seegangZu(double t) {
        double[] z = quelle.seegangZeiten();
        if (z.length == 0) return;
        int i = 0;
        while (i + 1 < z.length && z[i + 1] <= t) i++;
        double a = z[i], b = i + 1 < z.length && t > a ? z[i + 1] : a;
        if (t < z[0]) a = b = z[0];
        if (a == seePaarA && b == seePaarB) return;
        boolean ok;
        if (seePaarB == a && a != b) ok = seeSetze(b);
        else {
            ok = seeSetze(a);
            if (ok && b != a) ok = seeSetze(b);
        }
        if (ok) {
            seePaarA = a;
            seePaarB = b;
        } else seePaarA = seePaarB = Double.NaN;
    }

    /** Die zwei Stände der Trübung um t in das Netz; vom Boden der jüngste Stand bis t ins Netz und ins Gelände. */
    private void sedimentZu(double t) {
        double[] z = quelle.sedimentZeiten();
        if (z.length > 0) {
            int i = 0;
            while (i + 1 < z.length && z[i + 1] <= t) i++;
            double a = z[i], b = i + 1 < z.length && t > a ? z[i + 1] : a;
            if (t < z[0]) a = b = z[0];
            if (a != sedPaarA || b != sedPaarB) {
                boolean ok;
                if (sedPaarB == a && a != b) ok = sedSetze(b);
                else {
                    ok = sedSetze(a);
                    if (ok && b != a) ok = sedSetze(b);
                }
                sedPaarA = ok ? a : Double.NaN;
                sedPaarB = ok ? b : Double.NaN;
            }
        }
        double[] zb = quelle.bodenZeiten();
        if (zb.length > 0) {
            int i = 0;
            while (i + 1 < zb.length && zb[i + 1] <= t) i++;
            if (zb[i] != bodStand && quelle.boden(zb[i], bodRippel, bodSchlick, bodDz)) {
                boden.boden(zb[i], bodRippel, bodSchlick, bodDz);
                bodStand = zb[i];
                if (gelaende != null) {
                    gelaende.bodenHoehe = quelle.bodenBewegt();
                    gelaende.boden(boden.gitter, boden.boden.werte[boden.boden.platzB]);
                }
            }
        }
    }

    /** Von atmos der jüngste Stand bis t: die Nässe ins Gelände, die Luft für die Kimmung ({@link #blick}). */
    /** Flugsand (P10c): der jüngste Stand von {@code aeol.q} bis zur Zeit t geht ans Gelände (Sandfegen). */
    private void flugZu(double t) {
        double[] z = quelle.flugZeiten();
        if (z.length == 0) return;
        int i = 0;
        while (i + 1 < z.length && z[i + 1] <= t) i++;
        if (z[i] != flugStand && quelle.flugsand(z[i], flugQ)) {
            flugStand = z[i];
            if (gelaende != null) gelaende.flugsand(flugGitter, flugQ);
        }
    }

    private void atmosZu(double t) {
        double[] z = quelle.atmosZeiten();
        if (atmosGitter != null && z.length > 0) {
            int i = 0;
            while (i + 1 < z.length && z[i + 1] <= t) i++;
            if (z[i] != atmosStand && quelle.naesse(z[i], nassWet)) {
                atmosStand = z[i];
                if (gelaende != null) {
                    gelaende.nassAlsSaettigung = true;
                    gelaende.naesse(atmosGitter, nassWet);
                }
            }
        }
        double[] zl = quelle.luftZeiten();
        if (luftGitter != null && zl.length > 0) {
            int i = 0;
            while (i + 1 < zl.length && zl[i + 1] <= t) i++;
            if (zl[i] != luftStand && quelle.luft(zl[i], luftN)) luftStand = zl[i];
        }
    }

    /**
     * Je Bild, nach {@link #zu}: gibt der {@link #kimmung} die Luft im Blickfeld (P10b, F95 A). Die Luft gilt dort als
     * waagerecht geschichtet: das Mittel der Brechung über die Säulen, die zu mehr als der Hälfte über Wasser stehen und
     * im Blickfeld liegen (waagerechter Bildwinkel und 5° dazu, 300 m bis 12 km vom Auge [G]); liegt dort keine, über
     * alle Säulen über Wasser, sonst über alle. Die Fläche ist der mittlere Wasserstand unter diesen Säulen; die
     * Sperrhöhe die halbe mittlere Wellenhöhe des feinsten Seegangs, mindestens 2 cm (F93 A). Neu gerechnet wird mit
     * jedem Stand der Luft oder des Wassers und wenn der Blick um 100 m oder 2° gewandert ist [G].
     *
     * @param seite Seitenverhältnis des Bilds (Breite durch Höhe)
     */
    public void blick(Blick b, double seite) {
        if (kimmung == null) return;
        if (luftGitter == null || Double.isNaN(luftStand) || Double.isNaN(paarB)) {
            kimmung.ohneLuft();
            kimmHoehen = kimmBrechung = null;
            kimmLuft = Double.NaN;
            return;
        }
        double halbW = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(b.bildwinkel) / 2) * seite)) + 5;
        if (luftStand == kimmLuft && paarB == kimmWasser && Math.hypot(b.ost - kimmOst, b.nord - kimmNord) < 100 && Math.abs(winkel(b.richtung - kimmRichtung)) < 2
                && halbW == kimmWinkel) return;
        kimmLuft = luftStand;
        kimmWasser = paarB;
        kimmOst = b.ost;
        kimmNord = b.nord;
        kimmRichtung = b.richtung;
        kimmWinkel = halbW;
        // Wasser je Säule aus dem Gitter der Nässe (G3): Anteil der Zellen mit Wasser, mittlerer Wasserstand
        Gitter wg3 = gitter.isEmpty() ? null : gitter.get(nassGitter);
        float[] hh = wg3 == null ? null : h[nassGitter], ee = wg3 == null ? null : eta[nassGitter];
        int ns = luftGitter.zellen(), K = luftStufen;
        double[][] summe = new double[3][K];
        double[] flaeche = new double[3];
        long[] zahl = new long[3], zellen = new long[3];
        for (int s = 0; s < ns; s++) {
            int si = s % luftGitter.nx, sj = s / luftGitter.nx;
            double x0 = luftGitter.x0 + si * luftGitter.dx, y0 = luftGitter.y0 + sj * luftGitter.dy;
            int nass = 0, alle = 0;
            double e = 0;
            if (wg3 != null) {
                int i0 = (int) Math.round((x0 - wg3.x0) / wg3.dx), j0 = (int) Math.round((y0 - wg3.y0) / wg3.dy);
                int i1 = (int) Math.round((x0 + luftGitter.dx - wg3.x0) / wg3.dx), j1 = (int) Math.round((y0 + luftGitter.dy - wg3.y0) / wg3.dy);
                for (int j = Math.max(0, j0); j < Math.min(wg3.ny, j1); j++)
                    for (int i = Math.max(0, i0); i < Math.min(wg3.nx, i1); i++) {
                        alle++;
                        if (hh[i + wg3.nx * j] > 0.05f) {
                            nass++;
                            e += ee[i + wg3.nx * j];
                        }
                    }
            }
            boolean wasser = alle > 0 && 2 * nass > alle;
            double dx = x0 + 0.5 * luftGitter.dx - b.ost, dy = y0 + 0.5 * luftGitter.dy - b.nord, d = Math.hypot(dx, dy);
            boolean imFeld = d >= 300 && d <= 12_000 && Math.abs(winkel(Math.toDegrees(Math.atan2(dx, dy)) - b.richtung)) <= halbW;
            for (int q = 0; q < 3; q++) {
                // 0: über Wasser im Blickfeld, 1: über Wasser, 2: alle
                if (q == 0 && !(wasser && imFeld) || q == 1 && !wasser) continue;
                for (int k = 0; k < K; k++) summe[q][k] += luftN[s * K + k];
                zahl[q]++;
                flaeche[q] += e;
                zellen[q] += nass;
            }
        }
        int q = zahl[0] > 0 ? 0 : zahl[1] > 0 ? 1 : 2;
        if (zahl[q] == 0 || zellen[q] == 0) {
            kimmung.ohneLuft();
            kimmSaeulen = 0;
            kimmHoehen = kimmBrechung = null;
            return;
        }
        double[] hoehen = new double[K], n = new double[K];
        for (int k = 0; k < K; k++) {
            hoehen[k] = Profil.hoehe(k);
            n[k] = summe[q][k] / zahl[q];
        }
        kimmSaeulen = q == 0 ? (int) zahl[0] : 0;
        kimmFlaeche = flaeche[q] / zellen[q];
        // Sperrhöhe: halbe mittlere Wellenhöhe des feinsten Seegangs, mindestens 2 cm
        double hs = 0;
        if (see.length > 0 && !Double.isNaN(seePaarB)) {
            float[] f = seeFelder[see.length - 1][0];
            double sum = 0;
            int m = 0;
            for (float v : f) if (v > 0) {
                sum += v;
                m++;
            }
            hs = m > 0 ? sum / m : 0;
        }
        kimmSperr = Math.max(0.02, hs / 2);
        kimmHoehen = hoehen;
        kimmBrechung = n;
        kimmung.sperr = kimmSperr;
        kimmung.luft(hoehen, n, kimmFlaeche);
    }

    /** Ein Winkel in Grad nach −180 … 180. */
    private static double winkel(double w) {
        w %= 360;
        return w > 180 ? w - 360 : w < -180 ? w + 360 : w;
    }

    private boolean sedSetze(double ts) {
        if (!quelle.sediment(ts, sedC)) return false;
        boden.truebung(ts, sedC);
        return true;
    }

    private boolean seeSetze(double ts) {
        for (int k = 0; k < see.length; k++) {
            float[][] f = seeFelder[k];
            if (!quelle.seegang(ts, k, f)) return false;
            see[k].stand(ts, f[0], f[1], f[2], f[3], f[4]);
        }
        return true;
    }

    /** Holt den Stand ts aus der Quelle und gibt ihn den Spiegeln; {@code jung}: 1, wenn er der jüngere wird, 0: er gilt allein. */
    private boolean setze(double ts, int jung) {
        for (int k = 0; k < gitter.size(); k++) if (!quelle.stand(ts, index.get(k), eta[k], h[k], age[k])) return false;
        if (wg != null) {
            if (jung == 1) {
                float[] x = uA;
                uA = uB;
                uB = x;
                x = vA;
                vA = vB;
                vB = x;
            }
            if (!quelle.wind(ts, uB, vB)) {
                java.util.Arrays.fill(uB, 0);
                java.util.Arrays.fill(vB, 0);
            }
            if (jung == 0) {
                System.arraycopy(uB, 0, uA, 0, uA.length);
                System.arraycopy(vB, 0, vA, 0, vA.length);
            }
        }
        for (int k = 0; k < gitter.size(); k++) spiegel[k].stand(ts, eta[k], age[k], h[k]);
        if (!gitter.isEmpty()) System.arraycopy(age[nassGitter], 0, nassJung, 0, nassJung.length);
        gesetzt++;
        return true;
    }

    /** Bilinear zwischen den Zellmitten eines Gitters in UTM (am Rand der nächste Wert). */
    static double[] probe(Gitter g, float[] u, float[] v, double ost, double nord) {
        double x = (ost - g.x0) / g.dx - 0.5, y = (nord - g.y0) / g.dy - 0.5;
        x = Math.max(0, Math.min(g.nx - 1, x));
        y = Math.max(0, Math.min(g.ny - 1, y));
        int i = (int) Math.min(g.nx - 2, Math.floor(x)), j = (int) Math.min(g.ny - 2, Math.floor(y));
        i = Math.max(0, i);
        j = Math.max(0, j);
        double fx = x - i, fy = y - j;
        double[] r = new double[2];
        float[][] f = {u, v};
        for (int c = 0; c < 2; c++) {
            float[] w = f[c];
            int n = g.nx;
            double a = w[i + n * j], b = w[Math.min(g.nx - 1, i + 1) + n * j], d = w[i + n * Math.min(g.ny - 1, j + 1)], e = w[Math.min(g.nx - 1, i + 1) + n * Math.min(g.ny - 1, j + 1)];
            r[c] = (a + (b - a) * fx) * (1 - fy) + (d + (e - d) * fx) * fy;
        }
        return r;
    }
}
