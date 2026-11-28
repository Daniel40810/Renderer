package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.szene.Fusswellen;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Sucht je Bild auf der Karte die Kämme am Fuß der aktiven Abschnitte der Brandung ({@code see_fuss.comp}; Seegang
 * Werkbuch, S5b-4, Datei 46): je Fuß den vorigen, den letzten und den nächsten Kamm (Zeit des Durchgangs, H = 2 |Z'|,
 * Weißwasser, lineare Höhe 2 |Z|), dazu η, θ, H_s und T_m01 am Fuß, mit der Auflösung der Stufe der Clipmap, die den
 * Fuß zeichnet.
 * Die Füße meldet die Brandung ({@link Fusswellen#anfrage}); das Ergebnis liest der Prozessor {@link #RING} − 1 Bilder
 * später aus einem geteilten Puffer, ohne auf die Karte zu warten, und gibt es an die {@link Fusswellen}.
 * <p>
 * Seit S5b-4b (Befund L3a: 1,66 ms für 500 Füße auf der 680M) rechnet je Fuß und Kamm eine Gruppe von 32 Fäden; sie
 * teilen sich die Stützstellen und das Fernfeld.
 * <p>
 * Seit S5b-4c (Befund L3b: 1,44 ms; die Suche ist durch die verstreuten Lesezugriffe begrenzt – jeder Fuß liest seine
 * eigenen Zellen – und kostet je Auswertung): Je Bild sucht die Karte nur jeden {@link #teile}-ten Fuß (reihum nach der
 * Nummer des Abschnitts) und nur den letzten Kamm ({@link #alleKaemme} aus). Jeder Kamm wird so innerhalb von
 * {@link #teile} Bildern nach seinem Durchgang als letzter gefunden; seine Zeit ist exakt, nur die Meldung kommt bis zu
 * {@link #teile} Bilder später. Der vorige und der nächste Kamm sind für die Brandung nicht nötig (sie schlägt erst eine
 * Laufzeit nach dem Durchgang auf). Findet die Suche den letzten Kamm nicht (etwa wo θ vor 0 umkehrt), sucht dieselbe
 * Gruppe den vorigen (Befund in der Arbeitsumgebung: zwei von 16 Füßen blieben nach einem Sprung der Zeit sonst leer).
 */
public final class Fusssuche implements AutoCloseable {
    /** Plätze im Ring, Füße je Bild höchstens (die Brandung hat höchstens 500 aktive Abschnitte). */
    public static final int RING = 4, MAX = 512;
    private static final int KOPF = 16, PUSH = 16, EIN = 16, AUS = 4 * Fusswellen.JE_FUSS;

    private final Geraet g;
    private final Kernel kernel;
    private final Puffer[] ring = new Puffer[RING];
    private final double[] zeit = new double[RING];
    private final int[][] kanten = new int[RING][];
    private final double[][] perioden = new double[RING][];
    private final int[] zahl = new int[RING];
    private int naechster;
    /** Zuletzt: Füße der letzten Suche. */
    public volatile int zuletzt;
    /** Seit S5b-4c: je Bild nur jeden teile-ten Fuß, reihum (1: alle) [G]. */
    public volatile int teile = 4;
    /** Seit S5b-4c: alle drei Kämme je Fuß (vorig, letzter, nächster) statt nur des letzten (für Prüfungen). */
    public volatile boolean alleKaemme;
    private long runde;

    public Fusssuche(Geraet g, Konto konto) {
        this.g = g;
        for (int i = 0; i < RING; i++) ring[i] = Puffer.geteilt(g, konto, "Fußsuche " + i, KOPF + (long) EIN * MAX + (long) AUS * MAX);
        Kernel.Bindung[] b = new Kernel.Bindung[23];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kernel = Kernel.lade(g, SeegangKarte.PFAD + "see_fuss.comp", PUSH, null, b);
    }

    /**
     * Gibt das Ergebnis der ältesten Suche im Ring (vor {@link #RING} − 1 Bildern) an w, wenn es eins gibt. Danach ist der
     * Platz frei für {@link #suche}.
     */
    public void lies(Fusswellen w) {
        int r = naechster;
        int m = zahl[r];
        zahl[r] = 0;
        if (m <= 0 || w == null) return;
        ByteBuffer b = ByteBuffer.allocateDirect(AUS * m).order(ByteOrder.nativeOrder());
        ring[r].lies(b, KOPF + (long) EIN * MAX);
        float[] f = new float[Fusswellen.JE_FUSS * m];
        b.asFloatBuffer().get(f);
        w.ergebnis(zeit[r], kanten[r], perioden[r], f, m);
    }

    /**
     * Nimmt die Suche für die Füße der Anfrage in den Ablauf auf, nach den Stufen der Karte ({@link SeegangKarte#bild}),
     * zur Modellzeit t der Karte. Ohne Füße geschieht nichts.
     */
    public void suche(Ablauf a, SeegangKarte k, double t, Fusswellen.Anfrage q) {
        int n = q == null ? 0 : q.zahl, te = Math.max(1, teile);
        long rd = runde++;
        int[] wahl = new int[Math.min(n, MAX)];
        int m = 0;
        for (int i = 0; i < n && m < MAX; i++) if (te == 1 || Math.floorMod(q.kante[i] + rd, te) == 0) wahl[m++] = i;
        int r = naechster;
        naechster = (naechster + 1) % RING;
        zahl[r] = m;
        zuletzt = m;
        if (m == 0) return;
        boolean drei = alleKaemme;
        zeit[r] = t;
        kanten[r] = new int[m];
        perioden[r] = new double[m];
        Puffer p = ring[r];
        ByteBuffer ein = ByteBuffer.allocateDirect(KOPF + EIN * m).order(ByteOrder.nativeOrder());
        ein.putInt(m).putInt(0).putInt(0).putInt(0);
        for (int j = 0; j < m; j++) {
            int i = wahl[j];
            double om = q.omega[i];
            kanten[r][j] = q.kante[i];
            perioden[r][j] = om > 0 ? 2 * Math.PI / om : 0;
            ein.putFloat((float) (q.ost[i] - k.bx)).putFloat((float) (q.nord[i] - k.by)).putFloat((float) om).putFloat(0);
        }
        ein.flip();
        a.durchgang("Fußsuche, Füße", new Ablauf.Zugriff[] {new Ablauf.Zugriff(p, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, p.handle, 0, ein));
        ByteBuffer pu = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        pu.putInt(0, m).putInt(4, MAX).putInt(8, drei ? 0 : 1);
        Mittel[] lesen = k.lesenSynthese();
        Mittel[] l2 = Arrays.copyOf(lesen, lesen.length + 1);
        l2[lesen.length] = p;
        a.rechne("see_fuss", kernel, kernel.satz(k.belegungSchaum(p)), pu, m, drei ? 3 : 1, 1, l2, new Mittel[] {p});   // je Gruppe (32 Fäden) ein Fuß und ein Kamm
    }

    /** Vergisst die Suchen im Ring (neues Szenario, Prüfungen): Die nächsten {@link #lies} geben nichts. */
    public void leeren() { Arrays.fill(zahl, 0); }

    /** Wie {@link #suche}, sofort abgeschickt und gewartet (für Prüfungen); das Ergebnis geht gleich an w. */
    public void sofort(SeegangKarte k, double t, Fusswellen.Anfrage q, Fusswellen w) {
        Ablauf a = new Ablauf(g);
        int r = naechster;
        suche(a, k, t, q);
        g.rechnen.einmal(c -> a.aufnehmen(c, null));
        naechster = r;
        lies(w);
    }

    @Override public void close() {
        g.ruhe();
        for (Puffer p : ring) if (p != null) p.close();
        kernel.close();
    }
}
