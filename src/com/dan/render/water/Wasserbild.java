package com.dan.render.water;

import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Saeule;
import com.dan.core.szene.Bodennetz;
import com.dan.core.szene.Seegangsnetz;
import com.dan.core.szene.Seegangsoptik;
import com.dan.core.szene.Spiegel;
import com.dan.core.szene.Spiegelbild;
import com.dan.core.szene.Wasseroptik;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkImageCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Das Wasser (P6d, Werkbuch HalligHooge Teil A, 12.7, F42 A): ein Spiegel aus {@code water.eta} der Gitter G4, G3, G2
 * und G1 ({@link Spiegel}, je Ort das feinste), in der Zeit linear zwischen den zwei Ständen des Busses, mit Kräuselung
 * aus dem Wind und der Optik aus {@link Wasseroptik}.
 * <p>
 * <b>Zeichnen.</b> Nach dem Gelände, vor dem Himmel. Ein Dreieck über das ganze Bild sucht je Bildpunkt, wo der Strahl
 * den Spiegel trifft (feste Stelle über η_Bild, höchstens 8 Schritte); liegt der Treffer vor dem Gelände und ist dort
 * Wasser, wird er gezeichnet und seine Tiefe geschrieben. Darum ist Wasser genau, wo η_Bild über dem gezeichneten
 * Gelände liegt, ohne eigenes Netz und ohne Naht. Was darunter liegt (Farbe und Tiefe des Geländes), liest das Wasser
 * aus einer Kopie, die es vor dem Zeichnen zieht.
 * <p>
 * Gelesen werden die geteilten {@code Himmel/Durchlaessigkeit}, {@code Himmel/Ansicht} und {@code Himmel/Werte}
 * (render.air); der Himmel legt danach die Luftperspektive über die Tiefe des Wassers. Reihenfolge im Rahmen
 * {@code List.of(gelaende, wasser, himmel)}.
 * <p>
 * <b>Grenzen der Grundstufe:</b> gespiegelt wird nur der Himmel (kein Gelände, keine Häuser); der Grund unter dem Wasser
 * steht ohne Brechung an seinem Bildpunkt; kein Schatten des Geländes auf dem Wasser; das Auge über dem Wasser.
 * <p>
 * <b>Säule (P7d, 12.8, F55 A):</b> Licht der Wassersäule und des Grunds aus der Tafel von {@link Saeule} (Mehrfachstreuung
 * nach Fournier-Forand aus Sonne, Mond und Himmel, über u = b_b / (a + b_b) je Kanal), neu geschrieben, wenn sich u
 * ändert; das Licht des Himmels aufs Wasser rechnet {@code licht.comp} je Bild. Der Grund geht nach Lambert ein, mit der
 * Albedo ρ = π L_u / E aus dem Bild darunter.
 * <p>
 * <b>Stufe 2 (P8b, 12.9, F76 A):</b> Mit Netzen des Seegangs ({@link #seegang}, aus {@code waves.*}) steuert der örtliche
 * Seegang einen festen Satz langer Wellen ({@link Seegangsoptik}); ihre Restvarianz und {@code waves.mss} gehen ins
 * Glitzern, {@code waves.diss} gibt Schaum. Ohne Netz des Seegangs ist alles wie in Stufe 1.
 * <p>
 * <b>Trübung am Ort (P9b, 12.10):</b> Mit einem Netz des Sediments ({@link #boden}) rechnet die Säule je Bildpunkt mit der
 * Konzentration C_opt am Ort statt mit dem Schwebstoff der Szene: Tafeln der Säule für {@link #STUFEN} Konzentrationen
 * ({@link #stufe}), der Shader mischt linear in ln C zwischen zwei Stufen, jede mit ihrer Dämpfung. 30 g/m³ ist eine
 * Stufe. Außerhalb des Netzes und zu seinem Rand hin gilt der Schwebstoff der Szene. Ohne Netz ist alles wie zuvor.
 */
public final class Wasserbild implements Bildteil {
    public static final String VERSION = "0.1";
    public static final int NETZE = 8, WELLEN = 48;
    /** Netze des Seegangs (P8b). */
    public static final int SEENETZE = 4;
    static final int KOPF = (9 + 4 * NETZE + WELLEN + 2 + 3 * SEENETZE + Seegangsoptik.LANG + Seegangsoptik.BAENDER + Seegangsoptik.CDF / 2 + 3 + 1) * 16;
    private static final double[][] LANG = Seegangsoptik.wellen();
    private static final double[] BAENDER = Seegangsoptik.baender(), CDF = Seegangsoptik.cdf(), CDQ = Seegangsoptik.cdq(), LAENGSTE = Seegangsoptik.laengste();
    private static final float KEIN = -1.0e30f;
    /** Werte je Zelle in {@link #arten}: Art, Schwelle der West-, der Südkante (float-Bits, F47 A). */
    static final int ART = 3;

    public final Wasseroptik optik = new Wasseroptik();
    /** Modellzeit des Bilds (s), in der Zeit der Stände. */
    public volatile double zeit;
    /** Wind in 10 m (m/s) nach Ost und Nord (air.u10, air.v10 am Auge). */
    public volatile double u10, v10;
    /** Stufen der Trübung für die Tafeln der Säule (P9b) [G]: C_k = 30 · {@link #STUFE_FAKTOR}^(k − 2) g/m³. */
    public static final int STUFEN = 6;
    public static final double STUFE_FAKTOR = Math.pow(10, 0.4);
    /** Breite der Überblendung zum Schwebstoff der Szene am Rand des Netzes (Zellen) [G]. */
    public static final double RAND_ZELLEN = com.dan.core.szene.Bodennetz.RAND_ZELLEN;
    /** Trübung am Ort aus dem Netz des Sediments (P9b); aus: der Schwebstoff der Szene, auch wenn ein Netz gesetzt ist. */
    public volatile boolean truebung = true;

    /** Konzentration der Stufe k (g/m³). */
    public static double stufe(int k) { return 30 * Math.pow(STUFE_FAKTOR, k - 2); }

    /** Stufe 2 (P8b): der Seegang aus den Netzen; aus: Stufe 1, auch wenn Netze gesetzt sind (Prüfstein 8). */
    public volatile boolean seegang = true;
    /**
     * Seit P13c (Werkbuch HalligHooge 12.14, F159 A, F161 A): mit Seegang die langen Wellen mit Höhe und spitzen Kämmen,
     * Weißwasser, Schaumköpfe auf den Kämmen mit der Bedeckung aus diss / {@code Seegangsoptik.D_KAL}, gefiltert nach
     * Bildpunkt. Aus (auch mit {@code -Dlauf.brandung=aus}): wie bis P13b (bitgleich).
     */
    public volatile boolean brandung = !"aus".equals(System.getProperty("lauf.brandung"));
    /** Kurze Wellen als Geometrie (sonst nur die Statistik). */
    public volatile boolean kraeuseln = true;
    /** Radius der Erde mit Refraktion (m), wie das Gelände; 0 schaltet die Krümmung ab. */
    public double kruemmung = 6_371_000 / (1 - 0.13);
    /** Spiegelung des Geländes im Bild (F45 A); sonst nur der Himmel. */
    public volatile boolean spiegeln = true;
    /** Kleinste Restvarianz der Neigung [G]: die Sonnenscheibe (Radius 0,27°) als Spiegelbild. */
    public double restMin = 1e-5;

    private final Geraet g;
    private final Konto konto;
    private final Zeichner zeichner;
    private final Abtaster punkt;
    private final Puffer kopfPuffer;
    private final ByteBuffer kopf;
    // P7d: Tafel der Säule (je Bild aus core.szene.Saeule, neu nur, wenn sich u ändert) und das Himmelslicht aufs Wasser
    private final Kernel kLicht;
    private final Puffer saeulePuffer, lichtPuffer;
    private final ByteBuffer saeuleBytes;
    private double[] saeuleU;
    private final List<Spiegel> netze = new ArrayList<>();
    private boolean neuGelegt = true;
    private Puffer staende, arten;
    private final Puffer[] zwischen = new Puffer[3];
    private ByteBuffer hoch;
    private int zwNr;
    private Bild unten, tiefeUnten;
    // Seegang (P8b)
    private final List<Seegangsnetz> seeNetze = new ArrayList<>();
    private boolean seeNeuGelegt;
    private Puffer seePuffer, seeLeer;
    /** Seit P12a: kein Schatten der Häuser ({@code core/gpu/bauschatten.glsl}). */
    private final Puffer ohneBau;
    private final Bild bauLeer;
    private final Puffer[] seeZwischen = new Puffer[3];
    private ByteBuffer seeHoch;
    private int seeNr;
    private final int[] seeBasis = new int[SEENETZE];
    // Trübung am Ort (P9b)
    private Bodennetz bodNetz;
    private boolean bodNeuGelegt;
    private Puffer bodPuffer;
    private final Puffer[] bodZwischen = new Puffer[3];
    private ByteBuffer bodHoch;
    private int bodNr;
    private double stufenN = Double.NaN;
    private long gesamt;
    /** Zuletzt hochgeladene Bytes. */
    public long hochgeladen;

    // der letzte Kopf: η_Bild für das Auge und die Zeit des letzten Bilds (core.szene, F52 A)
    private Spiegelbild bild = new Spiegelbild(List.of(), 0, 0, 0);
    private float pegel;

    public Wasserbild(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
        String p = "/com/dan/render/water/";
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        zeichner = Zeichner.lade(g, p + "wasser.vert", p + "wasser.frag", 0, null, art, P, P, P, P, X, X, X, X, P, P, P, P, X, P);
        kLicht = Kernel.lade(g, p + "licht.comp", 0, null, P, P, P, P, X, X, P, P);
        punkt = new Abtaster(g, false, false);
        kopfPuffer = Puffer.speicher(g, konto, "Wasser, Kopf", KOPF);
        kopf = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        saeulePuffer = Puffer.speicher(g, konto, "Wasser, Säule", 4L * (3 * Saeule.JE_KANAL + FLAECHE + STUFEN * 3 * Saeule.JE_KANAL));
        // seit P10d (F104 A): dazu je Stufe von μ_s das Licht ins Wasser und je Tafel und Kanal die Tafeln des Himmels (licht.comp)
        lichtPuffer = Puffer.speicher(g, konto, "Wasser, Himmelslicht", 32 + 8 * 16 + 4L * (1 + STUFEN) * 3 * (32 * 8 + 32));
        saeuleBytes = ByteBuffer.allocateDirect(4 * (3 * Saeule.JE_KANAL + FLAECHE)).order(ByteOrder.nativeOrder());
        seeLeer = Puffer.speicher(g, konto, "Wasser, Seegang leer", 16);
        // seit P12a: ohne Häuser liest wasser.frag diese Werte (Zahl der Karten 0: Faktor genau 1)
        ohneBau = Puffer.speicher(g, konto, "Wasser, ohne Schatten der Häuser", 256);
        ohneBau.schreibe(new float[64]);
        bauLeer = new Bild(g, konto, "Wasser, ohne Schatten der Häuser", 1, 1, Bild.R32F);
        bauLeer.schreibe(new float[] {1});
    }

    /** Setzt ein Netz des Seegangs (ersetzt eines mit gleichem Namen; P8b). Danach werden die Puffer neu angelegt. */
    public synchronized void seegang(Seegangsnetz s) {
        seeNetze.removeIf(x -> x.gitter.name.equals(s.gitter.name));
        if (seeNetze.size() >= SEENETZE) throw new IllegalStateException("Wasser: höchstens " + SEENETZE + " Netze des Seegangs");
        seeNetze.add(s);
        seeNetze.sort(Comparator.comparingDouble(x -> x.gitter.dx));
        seeNeuGelegt = true;
    }

    /** Setzt das Netz des Sediments für die Trübung am Ort (null nimmt es weg; P9b). Danach wird der Puffer neu angelegt. */
    public synchronized void boden(Bodennetz b) {
        bodNetz = b;
        bodNeuGelegt = true;
    }

    public synchronized Bodennetz boden() { return bodNetz; }

    private void anlegenBoden() {
        g.ruhe();
        if (bodPuffer != null) bodPuffer.close();
        for (int i = 0; i < bodZwischen.length; i++) {
            if (bodZwischen[i] != null) bodZwischen[i].close();
            bodZwischen[i] = null;
        }
        bodPuffer = null;
        if (bodNetz != null) {
            long n = 2L * bodNetz.zellen;
            bodPuffer = Puffer.speicher(g, konto, "Wasser, Trübung", 4 * n);
            for (int i = 0; i < bodZwischen.length; i++) bodZwischen[i] = Puffer.geteilt(g, konto, "Wasser, Trübung zwischen " + i, 4 * n);
            bodHoch = ByteBuffer.allocateDirect((int) (4 * n)).order(ByteOrder.nativeOrder());
            synchronized (bodNetz.truebung) {
                if (bodNetz.truebung.platzB >= 0) bodNetz.truebung.neu = 3;
            }
        }
        bodNeuGelegt = false;
    }

    /** Neue Stände der Trübung über einen Zwischenpuffer auf die Karte; beim ersten Mal dazu die Tafeln der Stufen. */
    private void hochladenBoden(Ablauf a) {
        if (bodPuffer == null) return;
        if (stufenN != optik.n) {
            // Tafeln der Säule je Stufe hinter die wirksame Fläche (zu groß für vkCmdUpdateBuffer: über einen Zwischenpuffer)
            int je = 3 * Saeule.JE_KANAL;
            ByteBuffer t = ByteBuffer.allocateDirect(4 * STUFEN * je).order(ByteOrder.nativeOrder());
            Wasseroptik w = new Wasseroptik();
            w.n = optik.n;
            for (int k = 0; k < STUFEN; k++) {
                w.schwebstoff = stufe(k);
                t.asFloatBuffer().put(Saeule.lade().tafel(w));
                t.position(t.position() + 4 * je);
            }
            t.flip();
            Puffer zw = Puffer.geteilt(g, konto, "Wasser, Stufen der Säule", t.limit());
            zw.schreibe(t, 0);
            long nach = 4L * (3 * Saeule.JE_KANAL + FLAECHE), laenge = t.limit();
            Puffer ziel = saeulePuffer;
            a.durchgang("Stufen der Säule hochladen", new Ablauf.Zugriff[] {
                    new Ablauf.Zugriff(zw, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                    new Ablauf.Zugriff(ziel, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                try (MemoryStack st = MemoryStack.stackPush()) {
                    VkBufferCopy.Buffer b = VkBufferCopy.calloc(1, st);
                    b.get(0).srcOffset(0).dstOffset(nach).size(laenge);
                    vkCmdCopyBuffer(c, zw.handle, ziel.handle, b);
                }
            });
            if (stufenZw != null) altZw.add(stufenZw);
            stufenZw = zw;
            stufenN = optik.n;
        }
        Bodennetz.Spur s = bodNetz.truebung;
        List<long[]> k = new ArrayList<>();
        bodHoch.clear();
        synchronized (s) {
            int n = s.zellen;
            for (int p = 0; p < 2; p++)
                if ((s.neu & (1 << p)) != 0) {
                    long von = bodHoch.position();
                    bodHoch.asFloatBuffer().put(s.werte[p]);
                    bodHoch.position((int) (von + 4L * n));
                    k.add(new long[] {von, 4L * p * n, 4L * n});
                }
            s.neu = 0;
        }
        if (k.isEmpty()) return;
        bodHoch.flip();
        Puffer zw = bodZwischen[bodNr++ % bodZwischen.length];
        zw.schreibe(bodHoch, 0);
        hochgeladen += bodHoch.limit();
        Puffer ziel = bodPuffer;
        a.durchgang("Trübung hochladen", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(zw, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                new Ablauf.Zugriff(ziel, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            try (MemoryStack st = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer b = VkBufferCopy.calloc(k.size(), st);
                for (int i = 0; i < k.size(); i++) b.get(i).srcOffset(k.get(i)[0]).dstOffset(k.get(i)[1]).size(k.get(i)[2]);
                vkCmdCopyBuffer(c, zw.handle, ziel.handle, b);
            }
        });
    }

    private Puffer stufenZw;
    private final List<Puffer> altZw = new ArrayList<>();

    /** Der Puffer des Seegangs auf der Karte (ohne Netze ein leerer), für Prüfungen. */
    public synchronized Puffer seegangPuffer() { return seePuffer != null ? seePuffer : seeLeer; }

    /** Die Netze des Seegangs, feinstes zuerst. */
    public synchronized List<Seegangsnetz> seeNetze() { return List.copyOf(seeNetze); }

    private void anlegenSee() {
        g.ruhe();
        if (seePuffer != null) seePuffer.close();
        for (int i = 0; i < seeZwischen.length; i++) {
            if (seeZwischen[i] != null) seeZwischen[i].close();
            seeZwischen[i] = null;
        }
        seePuffer = null;
        long n = 0;
        for (int k = 0; k < seeNetze.size(); k++) {
            seeBasis[k] = (int) n;
            n += 2L * Seegangsnetz.FELDER * seeNetze.get(k).zellen;
        }
        if (n > 0) {
            seePuffer = Puffer.speicher(g, konto, "Wasser, Seegang", 4 * n);
            for (int i = 0; i < seeZwischen.length; i++) seeZwischen[i] = Puffer.geteilt(g, konto, "Wasser, Seegang zwischen " + i, 4 * n);
            seeHoch = ByteBuffer.allocateDirect((int) (4 * n)).order(ByteOrder.nativeOrder());
        }
        for (Seegangsnetz s : seeNetze)
            synchronized (s) {
                if (s.platzB >= 0) s.neu = 3;
            }
        seeNeuGelegt = false;
    }

    /** Neue Stände des Seegangs über einen Zwischenpuffer auf die Karte. */
    private void hochladenSee(Ablauf a) {
        if (seePuffer == null) return;
        List<long[]> k = new ArrayList<>();
        seeHoch.clear();
        for (int q = 0; q < seeNetze.size(); q++) {
            Seegangsnetz s = seeNetze.get(q);
            synchronized (s) {
                int n = Seegangsnetz.FELDER * s.zellen;
                for (int p = 0; p < 2; p++)
                    if ((s.neu & (1 << p)) != 0) {
                        long von = seeHoch.position();
                        seeHoch.asFloatBuffer().put(s.werte[p]);
                        seeHoch.position((int) (von + 4L * n));
                        k.add(new long[] {von, 4L * (seeBasis[q] + (long) p * n), 4L * n});
                    }
                s.neu = 0;
            }
        }
        if (k.isEmpty()) return;
        seeHoch.flip();
        Puffer zw = seeZwischen[seeNr++ % seeZwischen.length];
        zw.schreibe(seeHoch, 0);
        hochgeladen += seeHoch.limit();
        Puffer ziel = seePuffer;
        a.durchgang("Seegang hochladen", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(zw, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                new Ablauf.Zugriff(ziel, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            try (MemoryStack st = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer b = VkBufferCopy.calloc(k.size(), st);
                for (int i = 0; i < k.size(); i++) b.get(i).srcOffset(k.get(i)[0]).dstOffset(k.get(i)[1]).size(k.get(i)[2]);
                vkCmdCopyBuffer(c, zw.handle, ziel.handle, b);
            }
        });
    }

    /** u = b_b / (a + b_b) je Kanal (P7d). */
    public double[] saeuleU() {
        double[] k = optik.daempfung(), b = optik.bb(), u = new double[3];
        for (int c = 0; c < 3; c++) u[c] = b[c] / k[c];
        return u;
    }

    /**
     * P7f, nur zum Messen: 0 das ganze Wasser; 1 nur die Spiegelung (Himmel und Gelände), 2 das Glitzern von Sonne und
     * Mond, 3 die Säule aus Sonne und Mond, 4 die Säule aus dem Himmelslicht, 5 der Grund. Die Teile summieren sich zum Ganzen.
     */
    public int teil;

    /**
     * P7g (F63 A): die wirksame Fläche aus den Neigungen, die ein Bildpunkt nicht auflöst ({@code core.szene.Flaeche}):
     * Spiegelung, Höhe des Spiegelbilds und Durchlass im Mittel über die sichtbaren Flanken. Aus: Fresnel und Spiegel an
     * der Normalen der aufgelösten Kräuselung wie bis P7f (so prüft {@code WaterCheck} seine Formeln).
     */
    public volatile boolean wirksam = WIRKSAM;
    /** Vorgabe für {@link #wirksam} neuer Wasserbilder. */
    public static boolean WIRKSAM = true;
    private static final int FLAECHE = com.dan.core.szene.Flaeche.NMU * com.dan.core.szene.Flaeche.NS * com.dan.core.szene.Flaeche.JE;

    /** Die Tafel der Säule, wie sie auf der Karte liegt (über das Spektrum gemittelt, für Prüfungen). */
    public float[] saeuleTafel() { return Saeule.lade().tafel(optik); }

    private void schreibeSaeule(Ablauf a) {
        double[] u = {optik.schwebstoff, optik.n};
        if (saeuleU != null && java.util.Arrays.equals(u, saeuleU)) return;
        saeuleU = u;
        float[] f = Saeule.lade().tafel(optik);
        saeuleBytes.clear();
        // dahinter die wirksame Fläche (P7g); seit P11d (acht Werte je Stelle) zusammen über 65536 Bytes, der Grenze von
        // vkCmdUpdateBuffer: darum in zwei Aufrufen, die Säule und die Fläche je für sich
        float[] fl = com.dan.core.szene.Flaeche.tafel(optik.n);
        saeuleBytes.asFloatBuffer().put(f).put(fl);
        ByteBuffer teilS = saeuleBytes.duplicate().order(ByteOrder.nativeOrder()), teilF = saeuleBytes.duplicate().order(ByteOrder.nativeOrder());
        teilS.position(0).limit(4 * f.length);
        teilF.position(4 * f.length).limit(4 * (f.length + fl.length));
        ByteBuffer bS = teilS.slice().order(ByteOrder.nativeOrder()), bF = teilF.slice().order(ByteOrder.nativeOrder());
        long versatz = 4L * f.length;
        a.durchgang("Wasser, Säule", new Ablauf.Zugriff[] {new Ablauf.Zugriff(saeulePuffer, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> {
                    vkCmdUpdateBuffer(c, saeulePuffer.handle, 0, bS);
                    vkCmdUpdateBuffer(c, saeulePuffer.handle, versatz, bF);
                });
    }

    @Override public String name() { return "Wasser"; }

    /** Setzt ein Gitter (ersetzt eines mit gleichem Namen). Danach werden die Puffer neu angelegt (wartet auf die Karte). */
    public synchronized void spiegel(Spiegel s) {
        netze.removeIf(x -> x.gitter.name.equals(s.gitter.name));
        if (netze.size() >= NETZE) throw new IllegalStateException("Wasser: höchstens " + NETZE + " Gitter");
        netze.add(s);
        netze.sort(Comparator.comparingDouble(x -> x.gitter.dx));
        neuGelegt = true;
    }

    /** Nimmt ein Gitter weg (etwa G4, wenn das Auge wandert). */
    public synchronized void weg(String name) {
        if (netze.removeIf(x -> x.gitter.name.equals(name))) neuGelegt = true;
    }

    /** Die Gitter, feinstes zuerst. */
    public synchronized List<Spiegel> netze() { return List.copyOf(netze); }

    public Puffer kopf() { return kopfPuffer; }

    public Puffer staende() { return staende; }

    public Puffer arten() { return arten; }

    // ------------------------------------------------------------------ je Bild

    @Override public synchronized void vorbereiten(Ablauf a, Bildziel z) {
        if (neuGelegt) anlegen();
        if (seeNeuGelegt) anlegenSee();
        if (bodNeuGelegt) anlegenBoden();
        if (netze.isEmpty()) return;
        hochladen(a);
        hochladenSee(a);
        hochladenBoden(a);
        schreibeKopf(a, z);
        schreibeSaeule(a);
    }

    private void anlegen() {
        g.ruhe();
        if (staende != null) staende.close();
        if (arten != null) arten.close();
        for (int i = 0; i < zwischen.length; i++) {
            if (zwischen[i] != null) zwischen[i].close();
            zwischen[i] = null;
        }
        staende = arten = null;
        gesamt = 0;
        for (Spiegel s : netze) gesamt += s.zellen;
        if (gesamt > 0) {
            staende = Puffer.speicher(g, konto, "Wasser, Stände", 8 * gesamt);
            arten = Puffer.speicher(g, konto, "Wasser, Art", 4L * ART * gesamt);
            for (int i = 0; i < zwischen.length; i++) zwischen[i] = Puffer.geteilt(g, konto, "Wasser, Zwischen " + i, (8L + 4L * ART) * gesamt);
            hoch = ByteBuffer.allocateDirect((int) ((8 + 4 * ART) * gesamt)).order(ByteOrder.nativeOrder());
        }
        for (Spiegel s : netze)
            synchronized (s) {
                if (s.platzB >= 0) s.neu = 3;
                s.artNeu = true;
            }
        neuGelegt = false;
    }

    /** Was an Ständen neu ist, über einen Zwischenpuffer (drei Plätze) auf die Karte. */
    private void hochladen(Ablauf a) {
        List<long[]> k = new ArrayList<>();            // Quelle, Ziel, Bytes, Ziel 0 Stände / 1 Art
        hoch.clear();
        long basis = 0;
        for (Spiegel s : netze) {
            synchronized (s) {
                for (int p = 0; p < 2; p++)
                    if ((s.neu & (1 << p)) != 0) {
                        long q = hoch.position();
                        hoch.asFloatBuffer().put(s.eta[p]);
                        hoch.position((int) (q + 4L * s.zellen));
                        k.add(new long[] {q, 4 * (2 * basis + (long) p * s.zellen), 4L * s.zellen, 0});
                    }
                if (s.artNeu) {
                    long q = hoch.position();
                    java.nio.IntBuffer ib = hoch.asIntBuffer();
                    for (int c = 0; c < s.zellen; c++) ib.put(s.art[c]).put(Float.floatToRawIntBits(s.schwelleW[c])).put(Float.floatToRawIntBits(s.schwelleS[c]));
                    hoch.position((int) (q + 4L * ART * s.zellen));
                    k.add(new long[] {q, 4L * ART * basis, 4L * ART * s.zellen, 1});
                }
                s.neu = 0;
                s.artNeu = false;
            }
            basis += s.zellen;
        }
        hochgeladen = hoch.position();
        if (k.isEmpty()) return;
        hoch.flip();
        Puffer zw = zwischen[zwNr++ % zwischen.length];
        zw.schreibe(hoch, 0);
        a.durchgang("Wasser hochladen", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(zw, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                new Ablauf.Zugriff(staende, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(arten, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            try (MemoryStack st = MemoryStack.stackPush()) {
                for (int ziel = 0; ziel < 2; ziel++) {
                    int n = 0;
                    for (long[] x : k) if (x[3] == ziel) n++;
                    if (n == 0) continue;
                    VkBufferCopy.Buffer b = VkBufferCopy.calloc(n, st);
                    int i = 0;
                    for (long[] x : k) if (x[3] == ziel) b.get(i++).srcOffset(x[0]).dstOffset(x[1]).size(x[2]);
                    vkCmdCopyBuffer(c, zw.handle, (ziel == 0 ? staende : arten).handle, b);
                }
            }
        });
    }

    private void schreibeKopf(Ablauf a, Bildziel z) {
        Blick b = z.blick;
        double[][] bas = b.basis();
        double t = 1 / Math.tan(Math.toRadians(b.bildwinkel) / 2), as = (double) b.breite / b.hoehe_px;
        bild = new Spiegelbild(netze, b.ost, b.nord, zeit);
        int[] kn = bild.kn;
        float[] kf = bild.kf;
        pegel = bild.startPegel();
        double s2 = Wasseroptik.coxMunk(Math.hypot(u10, v10));
        double[] ap = optik.aSchweb(), bb = optik.bb();
        double[][] w = Wasseroptik.wellen(u10, v10);
        kopf.clear();
        kopf.putFloat((float) b.hoehe).putFloat(kruemmung > 0 ? (float) (1 / (2 * kruemmung)) : 0).putFloat(bild.netze.length).putFloat(pegel);
        kopf.putFloat((float) (bas[0][0] * as / t)).putFloat((float) (bas[0][1] * as / t)).putFloat((float) (bas[0][2] * as / t)).putFloat(0);
        kopf.putFloat((float) (bas[1][0] / t)).putFloat((float) (bas[1][1] / t)).putFloat((float) (bas[1][2] / t)).putFloat(0);
        kopf.putFloat((float) bas[2][0]).putFloat((float) bas[2][1]).putFloat((float) bas[2][2]).putFloat((float) b.nah);
        kopf.putFloat(z.breite).putFloat(z.hoehe).putFloat((float) s2).putFloat((float) restMin);
        for (int c = 0; c < 3; c++) kopf.putFloat((float) Wasseroptik.POPE_FRY[c]);
        kopf.putFloat((float) optik.n);
        for (int c = 0; c < 3; c++) kopf.putFloat((float) ap[c]);
        kopf.putFloat((float) optik.schwebstoff);
        for (int c = 0; c < 3; c++) kopf.putFloat((float) bb[c]);
        kopf.putFloat(kraeuseln ? w.length : 0);
        kopf.putFloat((float) (Math.toRadians(b.bildwinkel) / b.hoehe_px)).putFloat(spiegeln ? 1 : 0).putFloat(teil).putFloat(wirksam ? 1 : 0);
        for (int q = 0; q < NETZE; q++) {
            for (int i = 0; i < 8; i++) kopf.putInt(kn[8 * q + i]);
            for (int i = 0; i < 8; i++) kopf.putFloat(kf[8 * q + i]);
        }
        for (int i = 0; i < WELLEN; i++) {
            if (i < w.length) {
                // Phase am Auge zur Zeit des Bilds, in double genommen (der Ort des Auges ist groß gegen die Wellenlänge)
                double k = Math.hypot(w[i][0], w[i][1]);
                double ph = w[i][0] * b.ost + w[i][1] * b.nord + w[i][2] - Wasseroptik.omega(k) * zeit;
                ph -= 2 * Math.PI * Math.floor(ph / (2 * Math.PI));
                kopf.putFloat((float) w[i][0]).putFloat((float) w[i][1]).putFloat((float) ph).putFloat((float) w[i][3]);
            } else kopf.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        }
        // Seegang (P8b): Netze, lange Wellen mit der Phase am Auge, Bänder, Tafel des JONSWAP
        int ns = seePuffer == null || !seegang ? 0 : seeNetze.size();
        kopf.putFloat(ns).putFloat(Seegangsoptik.LANG).putFloat((float) (brandung ? Seegangsoptik.D_KAL : Seegangsoptik.D_VOLL)).putFloat((float) Seegangsoptik.ALBEDO);
        kopf.putFloat((float) (b.ost - 4096 * Math.floor(b.ost / 4096))).putFloat((float) (b.nord - 4096 * Math.floor(b.nord / 4096)))
                .putFloat((float) (zeit - 4096 * Math.floor(zeit / 4096))).putFloat((float) Seegangsoptik.S);
        double[][] gew = new double[SEENETZE][];
        for (int q = 0; q < SEENETZE; q++) {
            Seegangsnetz s = q < ns ? seeNetze.get(q) : null;
            gew[q] = s == null ? new double[] {0, 0} : s.gewicht(zeit);
            kopf.putInt(s == null ? 0 : seeBasis[q]).putInt(s == null ? 0 : s.gitter.nx).putInt(s == null ? 0 : s.gitter.ny).putInt(s != null && s.gesetzt() ? 1 : 0);
        }
        for (int q = 0; q < SEENETZE; q++) {
            Seegangsnetz s = q < ns ? seeNetze.get(q) : null;
            if (s == null) kopf.putFloat(0).putFloat(0).putFloat(1).putFloat(1);
            else kopf.putFloat((float) (s.gitter.x0 - b.ost)).putFloat((float) (s.gitter.y0 - b.nord)).putFloat((float) s.gitter.dx).putFloat((float) (1 / s.gitter.dx));
        }
        for (int q = 0; q < SEENETZE; q++) kopf.putFloat((float) gew[q][0]).putFloat((float) gew[q][1]).putFloat(0).putFloat(0);
        for (double[] l : LANG) {
            double k = Math.hypot(l[0], l[1]);
            double ph = l[0] * b.ost + l[1] * b.nord + l[2] - Wasseroptik.omega(k) * zeit;
            ph -= 2 * Math.PI * Math.floor(ph / (2 * Math.PI));
            kopf.putFloat((float) l[0]).putFloat((float) l[1]).putFloat((float) ph).putFloat((float) l[3]);
        }
        for (int q = 0; q < Seegangsoptik.BAENDER; q++) kopf.putFloat((float) BAENDER[2 * q]).putFloat((float) BAENDER[2 * q + 1]).putFloat((float) LAENGSTE[q]).putFloat(0);
        for (double c : CDF) kopf.putFloat((float) c);
        for (double c : CDQ) kopf.putFloat((float) c);
        // Trübung am Ort (P9b)
        Bodennetz bn = bodPuffer != null && truebung && bodNetz != null && bodNetz.truebung.gesetzt() ? bodNetz : null;
        if (bn == null) kopf.putInt(0).putInt(0).putInt(0).putInt(0).putFloat(0).putFloat(0).putFloat(1).putFloat(1).putFloat(0).putFloat(1).putFloat(1).putFloat(1);
        else {
            double[] gw = bn.truebung.gewicht(zeit);
            kopf.putInt(1).putInt(bn.gitter.nx).putInt(bn.gitter.ny).putInt((int) gw[1]);
            kopf.putFloat((float) (bn.gitter.x0 - b.ost)).putFloat((float) (bn.gitter.y0 - b.nord)).putFloat((float) bn.gitter.dx).putFloat((float) (1 / bn.gitter.dx));
            kopf.putFloat((float) gw[0]).putFloat((float) stufe(0)).putFloat((float) (1 / Math.log(STUFE_FAKTOR))).putFloat((float) RAND_ZELLEN);
        }
        // seit P13c: Wind und Schalter der Brandung im Wasser
        kopf.putFloat((float) u10).putFloat((float) v10).putFloat(brandung ? 1 : 0).putFloat(0);
        kopf.flip();
        a.durchgang("Wasser, Kopf", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kopfPuffer, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, kopfPuffer.handle, 0, kopf));
    }

    @Override public synchronized void zeichnen(Ablauf a, Bildziel z) {
        if (netze.isEmpty() || staende == null) return;
        boolean irgend = false;
        for (Spiegel s : netze) irgend |= s.gesetzt();
        if (!irgend) return;
        if (unten == null || unten.breite != z.breite || unten.hoehe != z.hoehe) {
            if (unten != null) {
                g.ruhe();
                unten.close();
                tiefeUnten.close();
            }
            unten = new Bild(g, konto, "Wasser, Bild darunter", z.breite, z.hoehe, z.hdr.format);
            tiefeUnten = new Bild(g, konto, "Wasser, Tiefe darunter", z.breite, z.hoehe, z.tiefe.format);
        }
        Bild hdr = z.hdr, tiefe = z.tiefe, u = unten, tu = tiefeUnten;
        a.durchgang("Wasser, Kopie darunter", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(hdr, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
                new Ablauf.Zugriff(tiefe, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
                new Ablauf.Zugriff(u, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
                new Ablauf.Zugriff(tu, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)}, c -> {
            try (MemoryStack st = MemoryStack.stackPush()) {
                VkImageCopy.Buffer k = VkImageCopy.calloc(1, st);
                k.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                k.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                k.get(0).extent().set(hdr.breite, hdr.hoehe, 1);
                vkCmdCopyImage(c, hdr.handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, u.handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, k);
                k.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                k.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
                vkCmdCopyImage(c, tiefe.handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, tu.handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, k);
            }
        });
        Bild durch = z.geteilt("Himmel/Durchlaessigkeit"), ansicht = z.geteilt("Himmel/Ansicht");
        Puffer werte = z.puffer("Himmel/Werte");
        a.rechne("Wasser, Himmelslicht", kLicht, kLicht.satz(kopfPuffer, staende, arten, werte, punkt.an(durch), punkt.an(ansicht), lichtPuffer, saeulePuffer), null, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopfPuffer, staende, arten, werte, saeulePuffer), Ablauf.textur(Ablauf.RECHNEN, durch, ansicht), Ablauf.schreibt(lichtPuffer)));
        Puffer see = seePuffer != null ? seePuffer : seeLeer;
        Puffer trueb = bodPuffer != null ? bodPuffer : seeLeer;
        Bild bauB = z.geteilt.getOrDefault("Bau/Schatten", bauLeer);
        Puffer bauW = z.puffer.getOrDefault("Bau/Schattenwerte", ohneBau);
        long satz = zeichner.satz(kopfPuffer, staende, arten, werte, punkt.an(durch), punkt.an(ansicht), punkt.an(u), punkt.an(tu), saeulePuffer, lichtPuffer, see, trueb, punkt.an(bauB), bauW);
        a.zeichne("Wasser", new Bild[] {hdr}, null, tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, new Mittel[] {kopfPuffer, staende, arten, werte, saeulePuffer, lichtPuffer, see, trueb, bauW}), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, u, tu, bauB)),
                c -> zeichner.zeichne(c, satz, null, 3, 1));
    }

    /**
     * η_Bild (m NHN) am Ort (x Ost, z Süd) relativ zum Auge des letzten Bilds, in float wie {@code wasser.glsl};
     * {@code Float.NaN}, wo kein Wasser ist. {@code gitter[0]}: Nummer des genutzten Gitters (−1 keins), darf null sein.
     * Die Regel steht in {@link Spiegelbild} (core.szene).
     */
    public synchronized float etaBild(float px, float pz, int[] gitter) { return bild.etaBild(px, pz, gitter); }

    /** η_Bild des letzten Bilds (core.szene). */
    public synchronized Spiegelbild spiegelbild() { return bild; }

    /** Der Pegel, mit dem die Suche im letzten Bild anfing (m NHN). */
    public synchronized float startwert() { return pegel; }

    /** Konstante für Tests: kein Wasser auf der Karte. */
    public static float kein() { return KEIN; }

    @Override public synchronized void close() {
        g.ruhe();
        zeichner.close();
        kLicht.close();
        punkt.close();
        kopfPuffer.close();
        saeulePuffer.close();
        lichtPuffer.close();
        if (staende != null) staende.close();
        if (arten != null) arten.close();
        for (Puffer p : zwischen) if (p != null) p.close();
        if (seePuffer != null) seePuffer.close();
        seeLeer.close();
        ohneBau.close();
        bauLeer.close();
        for (Puffer p : seeZwischen) if (p != null) p.close();
        if (bodPuffer != null) bodPuffer.close();
        for (Puffer p : bodZwischen) if (p != null) p.close();
        if (stufenZw != null) stufenZw.close();
        for (Puffer p : altZw) p.close();
        if (unten != null) unten.close();
        if (tiefeUnten != null) tiefeUnten.close();
    }
}
