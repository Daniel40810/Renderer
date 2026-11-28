package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Wasserpunkte;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Das Wasser unter den schwimmenden Körpern auf der Karte ({@code see_koerper.comp}; Seegang Werkbuch, S7b-1, Datei 88):
 * Je Bild nimmt die Suche die neueste {@link Wasserpunkte.Anfrage} des Laufs und rechnet an ihren Punkten die Fläche über
 * dem festen Ort und die rohen Summen der linearen Wellen je Band. Das Ergebnis liest der Prozessor {@link #RING} − 1
 * Bilder später aus einem geteilten Puffer, ohne auf die Karte zu warten, und legt es als {@link Wasserpunkte.Stand} ab.
 * Je Faden ein Punkt (die Synthese wie {@code see_punkte}, mit allen Wellen, d = 0).
 * <p>
 * Seit S7b-4 (Datei 89, Befund L5c: 1,0–2,7 ms je Bild): Hat die Anfrage dieselben Körper mit denselben Zahlen von Punkten
 * wie die vorige, beginnt die Suche über dem festen Ort bei ξ des letzten Bilds (ein Puffer, der von Bild zu Bild bleibt)
 * und rechnet höchstens {@link #ITER_WARM} Schritte. Seit S7d dazu das Kielwasser am Ort ({@link Kielwasserkarte}).
 * <p>
 * Seit S7d2 (Datei 90, Befund L5d 1: mit 234 statt 920 Punkten kaum billiger, die Latenz eines Fadens bestimmt die Zeit):
 * {@link #faeden} Fäden je Punkt teilen sich die Stützstellen ({@code see_koerper16.comp}, {@code see_koerper64.comp};
 * 1: {@code see_koerper.comp} wie bisher). An den Punkten eines Schiffs mit Kielwasser fehlt dessen eigenes
 * ({@link Wasserpunkte.Anfrage#quelle}, {@link Kielwasserkarte#lage}). Seit S7d6 (Seegang Werkbuch 94, SF133 A) rechnet
 * {@code see_koerper} das Kielwasser an den Punkten direkt aus den Spuren der Quellen (Bindung 24:
 * {@link Kielwasserkarte#quellen}) mit der größten Wellenzahl {@link #KIEL_KMAX}, nicht mehr aus den Kacheln.
 * <p>
 * Seit S7d3 (Datei 91, SF119 A, nach L5d2): Vorgabe 16 Fäden je Punkt (in SchwimmerCheck Teil 1 am schnellsten); mit
 * Startwert nur {@link #ITER_WARM} Schritt, Rückfall auf die volle Suche, wenn der Schritt über 2 cm bleibt. Im Kopf je Punkt
 * die Gewichte von feinem und grobem Gitter ({@link #gitter}).
 * <p>
 * Seit S7d4 (Datei 92, SF121 A, Befund L5d3 1: im selben Seegang rollte der Kutter bis 10,4° oder bis 23,5°, je nach
 * Ansicht): Mit einer {@link #koerperflaeche Körperfläche}, einer festen Fläche um den Hafen, nimmt die Suche die Energie
 * außerhalb des feinen Gitters des Auges aus ihr (see_gemein.glsl, KOERPERFLAECHE; Bindungen 25–31); die Phasen bleiben die
 * des Auges. Die Liste der aktiven Stützstellen ist dann die beider Flächen. Ohne Körperfläche rechnet sie wie bisher.
 */
public final class Koerpersuche implements AutoCloseable {
    public static final String VERSION = "0.6";
    /** Seit S7d6: größte Wellenzahl des Kielwassers an den Körpern (1/m) [G], wie {@code KIEL_KMAX} in see_koerper_rumpf.glsl: Wellen ab 3 m. */
    public static final double KIEL_KMAX = 2 * Math.PI / 3;
    /** Plätze im Ring, Punkte je Bild höchstens (die Eingabe geht über vkCmdUpdateBuffer, höchstens 64 KiB). */
    public static final int RING = 4, MAX = 3072;
    /** Schritte der Suche über dem festen Ort höchstens [G]: ohne Startwert, mit dem des letzten Bilds. */
    public static final int ITER = 8, ITER_WARM = 1;
    private static final int KOPF = 16, PUSH = 32, GRUPPE = 64, BINDUNGEN = 33;

    /** Fäden je Punkt, die es gibt; die Vorgabe [G, nach SchwimmerCheck Teil 1]. */
    public static final int[] FAEDEN = {1, 16, 64};
    public static final int FAEDEN_VORGABE = 16;

    private final Geraet g;
    private final Kernel[] kernels = new Kernel[FAEDEN.length];
    /** Fäden je Punkt für die nächsten Suchen (einer aus {@link #FAEDEN}). */
    public volatile int faeden = FAEDEN_VORGABE;
    private final Puffer[] ring;
    private final int nbAus, je;
    private final double[] zeit = new double[RING];
    private final double[][] omega = new double[RING][];
    private final Wasserpunkte.Anfrage[] anfragen = new Wasserpunkte.Anfrage[RING];
    private int naechster;
    /** Zuletzt: Punkte der letzten Suche; Zeit des Prozessors für das Lesen des letzten Ergebnisses (ms). */
    public volatile int zuletzt;
    public volatile double liesMs;

    private final ByteBuffer lesen;
    // seit S7b-4: ξ je Punkt von Bild zu Bild; die Anfrage, zu der er gehört (Körper und Zahlen); ein leerer Puffer für die Kacheln
    private final Puffer start, ohneKiel;
    private int[] startAnfang, startAnzahl;
    /** Zuletzt: ob die Suche mit Startwerten rechnete; Fäden je Punkt (Prüfungen). */
    public volatile boolean zuletztWarm;
    public volatile int zuletztFaeden;
    // seit S7d4: die Körperfläche (null: keine), ihr Gewicht (0 … 1, Überblenden); Kopf und vereinte Liste der Stützstellen
    private volatile SeegangKarte koerper;
    private volatile float koerperGewicht;
    private final Puffer kKopf, kLeer, kAktiv, kPhase;
    /** Zuletzt: Gewicht der Körperfläche in der letzten Suche (0: ohne). */
    public volatile float zuletztKoerper;

    /** Mit Puffern für {@link SeegangKarte#NB_MAX} Bänder (jede Fläche passt). */
    public Koerpersuche(Geraet g, Konto konto) { this(g, konto, SeegangKarte.NB_MAX); }

    /** nb: Bänder der Fläche (höchstens {@link SeegangKarte#NB_MAX}); die Puffer reichen für so viele. */
    public Koerpersuche(Geraet g, Konto konto, int nb) {
        this.g = g;
        this.nbAus = nb;
        this.je = 3 + 4 * nb;                                               // vec4 je Punkt höchstens
        ring = new Puffer[RING];
        for (int i = 0; i < RING; i++) ring[i] = Puffer.geteilt(g, konto, "Körpersuche " + i, KOPF + 16L * MAX + 16L * je * MAX);
        Kernel.Bindung[] b = new Kernel.Bindung[BINDUNGEN];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        for (int v = 0; v < FAEDEN.length; v++)
            kernels[v] = Kernel.lade(g, SeegangKarte.PFAD + (FAEDEN[v] == 1 ? "see_koerper.comp" : "see_koerper" + FAEDEN[v] + ".comp"), PUSH, null, b);
        lesen = ByteBuffer.allocateDirect(16 * je * MAX).order(ByteOrder.nativeOrder());
        start = Puffer.speicher(g, konto, "Körpersuche, Start", 16L * MAX);
        ohneKiel = Kielwasserkarte.leer(g, konto);
        kKopf = Puffer.speicher(g, konto, "Körpersuche, Kopf der Körperfläche", 64);
        kLeer = Puffer.speicher(g, konto, "Körpersuche, keine Körperfläche", 16);
        kAktiv = Puffer.speicher(g, konto, "Körpersuche, Stützstellen beider Flächen", 4L * (SeegangKarte.NB_MAX + 1 + SeegangKarte.NB_MAX * 64));
        kPhase = Puffer.speicher(g, konto, "Körpersuche, Phasen der Körperfläche", 8L * SeegangKarte.NB_MAX * 64);
    }

    /**
     * Seit S7d4 (SF121 A): die Körperfläche für die nächsten Suchen (null: keine) und ihr Gewicht (0 … 1; die Wartung
     * blendet sie damit ein). Sie muss dieselben Bänder und Richtungen haben wie die Fläche des Auges, sonst bleibt sie aus.
     */
    public void koerperflaeche(SeegangKarte k, double gewicht) {
        koerper = k;
        koerperGewicht = k == null ? 0f : (float) Math.max(0, Math.min(1, gewicht));
    }

    /** Seit S7d4: die Körperfläche oder null. */
    public SeegangKarte koerperflaeche() { return koerper; }

    /** Bänder, für die die Puffer reichen. */
    public int baender() { return nbAus; }

    /**
     * Gibt das Ergebnis der ältesten Suche im Ring (vor {@link #RING} − 1 Bildern) an w, wenn es eins gibt. Danach ist der
     * Platz frei für {@link #suche}.
     */
    public void lies(Wasserpunkte w) {
        int r = naechster;
        Wasserpunkte.Anfrage q = anfragen[r];
        anfragen[r] = null;
        if (q == null || w == null) return;
        long n0 = System.nanoTime();
        int m = Math.min(q.zahl, MAX), nb = omega[r].length;
        // die Karte schreibt je Punkt 3 + 4 nb vec4, dicht hintereinander: genau der Aufbau des Stands (KOPF + JE_BAND · nb)
        int jeStand = Wasserpunkte.KOPF + Wasserpunkte.JE_BAND * nb;
        ByteBuffer b = lesen;
        b.clear().limit(4 * jeStand * m);
        ring[r].lies(b, KOPF + 16L * MAX);
        b.rewind();
        float[] f = new float[jeStand * m];
        b.asFloatBuffer().get(f);
        w.stand(new Wasserpunkte.Stand(q, zeit[r], omega[r], f));
        liesMs = (System.nanoTime() - n0) / 1e6;
    }

    /**
     * Nimmt die Suche für die Punkte der Anfrage in den Ablauf auf, nach den Stufen der Karte ({@link SeegangKarte#bild}),
     * zur Modellzeit t der Karte. Ohne Punkte geschieht nichts.
     */
    public void suche(Ablauf a, SeegangKarte k, double t, Wasserpunkte.Anfrage q) { suche(a, k, t, q, null, true); }

    /**
     * Wie oben; kiel: die Kacheln des Kielwassers dieses Bilds (null: keins); warm: mit Startwerten, wo die Anfrage zur
     * vorigen passt.
     */
    public void suche(Ablauf a, SeegangKarte k, double t, Wasserpunkte.Anfrage q, Kielwasserkarte kiel, boolean warm) {
        int r = naechster;
        naechster = (naechster + 1) % RING;
        anfragen[r] = null;
        int m = q == null ? 0 : Math.min(q.zahl, MAX);
        zuletzt = m;
        if (m == 0) return;
        if (k.nb > nbAus) throw new IllegalStateException("Körpersuche: " + k.nb + " Bänder, Puffer für " + nbAus);
        anfragen[r] = q;
        zeit[r] = t;
        omega[r] = baenderOmega(k.o);
        Puffer p = ring[r];
        ByteBuffer ein = ByteBuffer.allocateDirect(KOPF + 16 * m).order(ByteOrder.nativeOrder());
        ein.putInt(m).putInt(0).putInt(0).putInt(0);
        boolean mitKiel = kiel != null && kiel.an();
        // seit S7d2: je Punkt die eigene Quelle (seit S7d6 ihre Nummer im Puffer der Quellen; −1: keine)
        float[] eigen = new float[m];
        Arrays.fill(eigen, -1);
        if (mitKiel && q.quelle != null)
            for (int kk = 0; kk < q.anfang.length && kk < q.quelle.length; kk++) {
                int l = kiel.lage(q.quelle[kk]);
                if (l < 0 || q.anfang[kk] < 0) continue;
                for (int i = q.anfang[kk]; i < Math.min(m, q.anfang[kk] + q.anzahl[kk]); i++) eigen[i] = l;
            }
        for (int i = 0; i < m; i++) ein.putFloat((float) (q.ost[i] - k.bx)).putFloat((float) (q.nord[i] - k.by)).putFloat(eigen[i]).putFloat(0);
        ein.flip();
        a.durchgang("Körpersuche, Punkte", new Ablauf.Zugriff[] {new Ablauf.Zugriff(p, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, p.handle, 0, ein));
        ByteBuffer pu = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        boolean passt = warm && startAnfang != null && Arrays.equals(startAnfang, q.anfang) && Arrays.equals(startAnzahl, q.anzahl);
        startAnfang = q.anfang.clone();
        startAnzahl = q.anzahl.clone();
        zuletztWarm = passt;
        Puffer kp = mitKiel ? kiel.quellen() : ohneKiel;                    // seit S7d6 (SF133 A): die Quellen, nicht die Kacheln
        pu.putInt(0, m).putInt(4, MAX).putInt(8, k.nb).putInt(12, ITER).putInt(16, passt ? 1 : 0).putInt(20, ITER_WARM).putInt(24, mitKiel ? 1 : 0).putInt(28, 0);
        Mittel[] lesen = k.lesenSynthese();
        Object[] bel = Arrays.copyOf(k.belegungSchaum(p), BINDUNGEN);
        bel[23] = start;
        bel[24] = kp;
        // seit S7d4: die Körperfläche (Bindungen 25–31) und die Stützstellen beider Flächen (statt 17)
        SeegangKarte kf = koerper;
        float kg = koerperGewicht;
        if (kf != null && (kf.nb != k.nb || kf.nd != k.nd || kf.koerperPuffer()[0] == null || kf.aktivAn() == null || k.aktivAn() == null)) kf = null;
        if (!(kg > 0)) kf = null;
        zuletztKoerper = kf != null ? kg : 0f;
        ByteBuffer kk = koerperKopf(k, kf, kg);
        bel[25] = kKopf;
        Puffer[] kpu = kf != null ? kf.koerperPuffer() : null;
        for (int j = 0; j < 6; j++) bel[26 + j] = kpu != null ? kpu[j] : kLeer;
        bel[32] = kf != null ? kPhase : kLeer;                             // seit S7d4b
        java.util.List<Mittel> zugriff = new java.util.ArrayList<>(Arrays.asList(lesen));
        zugriff.add(p);
        zugriff.add(start);
        zugriff.add(kp);
        zugriff.add(kKopf);
        if (kf != null) {
            ByteBuffer al = aktivVereint(k, kf), ph = phaseVersatz(k, kf);
            bel[17] = kAktiv;
            zugriff.add(kAktiv);
            zugriff.add(kPhase);
            for (Puffer x : kpu) zugriff.add(x);
            a.durchgang("Körpersuche, Körperfläche", new Ablauf.Zugriff[] {
                    new Ablauf.Zugriff(kKopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                    new Ablauf.Zugriff(kAktiv, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                    new Ablauf.Zugriff(kPhase, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                vkCmdUpdateBuffer(c, kKopf.handle, 0, kk);
                vkCmdUpdateBuffer(c, kAktiv.handle, 0, al);
                vkCmdUpdateBuffer(c, kPhase.handle, 0, ph);
            });
        } else
            a.durchgang("Körpersuche, Körperfläche", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kKopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                    c -> vkCmdUpdateBuffer(c, kKopf.handle, 0, kk));
        Mittel[] l2 = zugriff.toArray(new Mittel[0]);
        int v = 0;
        for (int j = 0; j < FAEDEN.length; j++) if (FAEDEN[j] == faeden) v = j;
        Kernel kernel = kernels[v];
        zuletztFaeden = FAEDEN[v];
        a.rechne("see_koerper", kernel, kernel.satz(bel), pu, FAEDEN[v] == 1 ? Kernel.gruppen(m, GRUPPE) : m, 1, 1, l2, new Mittel[] {p, start});
    }

    /**
     * Seit S7d3: Gewichte aus dem siebten Wert des Kopfs eines Punkts (wie see_koerper_rumpf.glsl). Seit S7d4 je 6 Bit:
     * {fein und grob des Auges, Fernfeld des Auges (der Rest), Anteile fein und grob der Körperfläche}. Den Anteil der Energie
     * aus dem feinen Gitter (Auge oder Körperfläche) gibt {@code [0] + [3]}.
     */
    public static double[] gitter(float wert) {
        int c = Math.round(wert);
        double wf = ((c >> 18) & 63) / 63.0, wg = ((c >> 12) & 63) / 63.0, kf = ((c >> 6) & 63) / 63.0, kg = (c & 63) / 63.0;
        return new double[] {wf, wg, Math.max(0, 1 - wf - wg), kf, kg};
    }

    /** Seit S7d4: der Kopf der Körperfläche (Bindung 25, wie see_gemein.glsl KW), Orte relativ zum B des Auges; ohne: Gewicht 0. */
    static ByteBuffer koerperKopf(SeegangKarte auge, SeegangKarte kf, float gewicht) {
        ByteBuffer b = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        int[] ki = new int[8];
        float[] kfl = new float[8];
        if (kf != null) {
            Oberflaeche.Stufe f = kf.o.fein, gr = kf.o.grob;
            if (f != null) { ki[0] = 1; ki[2] = f.nx; ki[3] = f.ny; ki[4] = f.rand; kfl[0] = (float) (f.x0 - auge.bx); kfl[1] = (float) (f.y0 - auge.by); kfl[2] = (float) f.dx; }
            if (gr != null) { ki[1] = 1; ki[5] = gr.nx; ki[6] = gr.ny; ki[7] = gr.rand; kfl[3] = (float) (gr.x0 - auge.bx); kfl[4] = (float) (gr.y0 - auge.by); kfl[5] = (float) gr.dx; }
            kfl[6] = gewicht;
            kfl[7] = kf.energieJeZelleGeladen() ? 1f : 0f;
        }
        for (int v : ki) b.putInt(v);
        for (float v : kfl) b.putFloat(v);
        b.flip();
        return b;
    }

    /**
     * Seit S7d4b (Befund L5d4 2): je Stützstelle (Δφ, Δτ) vom Ursprung U der Phasen des Auges zu dem der Körperfläche:
     * Δφ = k₀ (U_Auge − U_K) · e mod 2π, Δτ = (U_Auge − U_K) · e / c_g₀ mod Periode der Hülle (wie SeegangKarte.rechneZeit).
     * Die ebenen Wellen der Körperfläche (δΦ = 0) haben damit ihre eigene, feste Phase.
     */
    static ByteBuffer phaseVersatz(SeegangKarte auge, SeegangKarte kf) {
        com.dan.core.szene.Phasenfeld ra = auge.o.grob != null ? auge.o.grob.pa : auge.o.fein.pa;
        com.dan.core.szene.Phasenfeld rk = kf.o.grob != null ? kf.o.grob.pa : kf.o.fein.pa;
        double dx = ra.ursprungX - rk.ursprungX, dy = ra.ursprungY - rk.ursprungY;
        int nb = auge.nb, nd = auge.nd;
        ByteBuffer b = ByteBuffer.allocateDirect(8 * nb * nd).order(ByteOrder.nativeOrder());
        for (int bb = 0; bb < nb; bb++)
            for (int q = 0; q < nd; q++) {
                int bq = bb * nd + q;
                double th = ra.richtung[bq], sv = dx * Math.cos(th) + dy * Math.sin(th);
                b.putFloat((float) SeegangKarte.mod(ra.k0[bb] * sv, 2 * Math.PI)).putFloat((float) SeegangKarte.mod(sv / ra.cg0[bb], auge.o.puffer.periode[bq]));
            }
        b.flip();
        return b;
    }

    /** Seit S7d4: die Liste der Stützstellen, die in einer der beiden Flächen aktiv sind (Aufbau wie SeegangKarte.aktivListe). */
    static ByteBuffer aktivVereint(SeegangKarte auge, SeegangKarte kf) {
        int nb = auge.nb, nd = auge.nd, m = nb * nd;
        boolean[] a = auge.aktivAn(), c = kf.aktivAn();
        int[] l = new int[nb + 1 + m];
        int z = 0;
        for (int b = 0; b < nb; b++) {
            l[b] = z;
            for (int q = 0; q < nd; q++) if (a[b * nd + q] || c[b * nd + q]) l[nb + 1 + z++] = b * nd + q;
        }
        l[nb] = z;
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * l.length).order(ByteOrder.nativeOrder());
        for (int v : l) bb.putInt(v);
        bb.flip();
        return bb;
    }

    /** ω je Band der Fläche (rad/s). */
    static double[] baenderOmega(Oberflaeche o) { return o.omega.clone(); }

    /** Vergisst die Suchen im Ring (neues Szenario, Prüfungen): Die nächsten {@link #lies} geben nichts. */
    public void leeren() { Arrays.fill(anfragen, null); startAnfang = null; }

    /** Wie {@link #suche}, sofort abgeschickt und gewartet, ohne Startwerte (für Prüfungen); das Ergebnis geht gleich an w. */
    public void sofort(SeegangKarte k, double t, Wasserpunkte.Anfrage q, Wasserpunkte w) { sofort(k, t, q, w, null, false); }

    /** Wie oben, mit Kielwasser und wahlweise mit Startwerten (wie in der Anwendung). */
    public void sofort(SeegangKarte k, double t, Wasserpunkte.Anfrage q, Wasserpunkte w, Kielwasserkarte kiel, boolean warm) {
        Ablauf a = new Ablauf(g);
        int r = naechster;
        suche(a, k, t, q, kiel, warm);
        g.rechnen.einmal(c -> a.aufnehmen(c, null));
        naechster = r;
        lies(w);
    }

    @Override public void close() {
        g.ruhe();
        for (Puffer p : ring) if (p != null) p.close();
        start.close();
        ohneKiel.close();
        kKopf.close();
        kLeer.close();
        kAktiv.close();
        kPhase.close();
        for (Kernel k : kernels) if (k != null) k.close();
    }
}
