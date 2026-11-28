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
    static final int KOPF = (9 + 4 * NETZE + WELLEN + 2 + 3 * SEENETZE + Seegangsoptik.LANG + Seegangsoptik.BAENDER + Seegangsoptik.CDF / 2 + 3 + 1 + 3 + 2) * 16;
    /**
     * Seit S6c-2 (Seegang Werkbuch 82, SF93 A): Schlüssel des Bilds des Auflaufs im Bildziel (Puffer und Werte wie
     * {@code render.terrain.Gelaendebild.SWASH_BILD}; legt {@code lauf.Auflauf} ab, dieses Package kennt es nicht). Im
     * Rechteck zeigt das Netz den Stand des Lösers, wo er mindestens 3 cm Wasser trägt (wasser.glsl, SWASH_BINDUNG); aus mit
     * {@link #swashAn} = false.
     */
    public static final String SWASH_BILD = "Swash/Bild";
    /** Seit S6c-2: der Löser im Netz (aus mit {@code -Dwasser.swash=aus}: wie bisher). */
    public volatile boolean swashAn = !"aus".equals(System.getProperty("wasser.swash"));
    /** Seit S6c-2: Puffer, wenn kein Bild des Auflaufs abgelegt ist. */
    private Puffer swashLeer;
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
    /**
     * Seit S3c-5 (SF28 A): Schlüssel in {@code z.werte}, wenn das Wasser in diesem Bild das Netz zeichnet; Wert wie
     * {@code Seegang/Netz} (Stufen, n, innerer Bereich in UTM: Ost von, bis, Nord von, bis).
     */
    public static final String NETZ_GEZEICHNET = "Wasser/Netz";
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
    /**
     * Seit S8b (Seegang Werkbuch 104, SF156 A): Katzenpfoten ({@code core.szene.Katzenpfoten}, katzen.glsl) – der
     * Böenfaktor G = fg10 / U₁₀ am Auge (NaN: {@code Katzenpfoten.G_SONST}); aus: die Kräusel wie bis S7 (bitgleich).
     */
    public volatile double boeFaktor = Double.NaN;
    /**
     * Seit S9a (Seegang Werkbuch 106, SF168 A): Messschalter je Teil der Schattierung des Netzes (Bits wie MESS_* in wasser.glsl:
     * 1 Clipmap, 2 Weißwasser, 4 Schaumköpfe, 8 Schaumfeld, 16 Muster des Schaums, 32 Auflauf, 64 Werte des Seegangs, 128 Suche
     * B47, 256 Säule, 512 Schatten der Häuser). Ein Bit lässt den Teil weg; nur für die Messung, das Bild ändert sich. 0: wie bisher.
     */
    public volatile int mess;
    /**
     * Seit S9b-1 (Seegang Werkbuch 107, SF171 A): die Spiegelung des Geländes mit der groben Tiefe je Kachel und der
     * Höhenschranke (tiefe_grob.comp); dieselben Treffer, weniger Lesen. Aus: der alte Weg (zum Vergleich).
     */
    public volatile boolean spiegelGrob = !"aus".equals(System.getProperty("lauf.spiegelgrob"));
    /**
     * Seit S9b-2 (SF172 A): die Kräusel brechen ab, sobald eine Welle unter dem Bildpunkt liegt (die Wellen kommen nach der
     * Länge geordnet); dieselbe Summe. Aus: weiterzählen wie bisher (zum Vergleich).
     */
    public volatile boolean kraeuselAbbruch = !"aus".equals(System.getProperty("lauf.kraeuselabbruch"));
    /** Seit S9b-1: Kachel der groben Tiefe (Bildpunkte, wie local_size in tiefe_grob.comp). */
    public static final int GROB_KACHEL = 16;
    private final Kernel kGrob;
    private final Puffer grobLeer;
    private Puffer grobPuffer;
    private int grobB, grobH;
    public volatile boolean katzen = !"aus".equals(System.getProperty("lauf.katzen"));
    private final com.dan.core.szene.Katzenpfoten.Feld katzenFeld = new com.dan.core.szene.Katzenpfoten.Feld();
    /** Seit S8b: was der Kopf des letzten Bilds über die Katzenpfoten sagt {qx, qy, τ, σ, U, 1/L, Gewicht} (für Prüfungen). */
    public volatile double[] katzenKopf = new double[7];

    /** Kleinste Restvarianz der Neigung [G]: die Sonnenscheibe (Radius 0,27°) als Spiegelbild. */
    public double restMin = 1e-5;

    private final Geraet g;
    private final Konto konto;
    private final Zeichner zeichner, netzZeichner;
    /** Format des Siegers im Vorlauf (Nummer des Dreiecks, 0 keins). */
    static final int SIEGER = Bild.R32UI;
    private final Kernel kNetz;
    /** Seit S3c: ohne Netz des Seegangs ein Kopf mit snN.z = 0 (der Weg ohne Netz zeichnet überall). */
    private final Puffer netzLeer;
    /** Seit S4a: Kopf ohne Schaumfeld (Bindung 21 des Netzes, wenn render.seegang keins ablegt). */
    private final Puffer schaumLeer;
    private Puffer scheitel;
    /**
     * Seit S3c (Seegang Werkbuch, SF7 A): wo render.seegang eine Clipmap ablegt ({@code Seegang/Netz}), zeichnet das Wasser
     * dort ein Netz mit ihrer Verschiebung; der Weg ohne Netz (ein Dreieck über das Bild) nur noch außerhalb. Aus: wie bisher.
     */
    public volatile boolean netz = true;
    /**
     * Seit S3c-5 (B48, Seegang Werkbuch 19/20): Das Netz bricht einen Bildpunkt vor der Optik ab, wenn das Gelände davor
     * liegt (an; aus mit {@code -Dwasser.abbruch=aus}). Das Bild bleibt gleich, nur die Zeit ändert sich.
     */
    public volatile boolean abbruch = !"aus".equalsIgnoreCase(System.getProperty("wasser.abbruch", "an"));
    /**
     * Seit S3c-6 (N6, Seegang Werkbuch 22): Vorlauf der Tiefe für das Netz ({@code netz_vorlauf.frag}): erst nur der Fuß je
     * Bildpunkt mit Tiefe und Nummer des siegenden Dreiecks, dann die Schattierung nur für dieses (ohne Prüfung der Tiefe).
     * Das Bild bleibt gleich. Aus (Vorgabe; an mit {@code -Dwasser.vorlauf=an}): ein Durchgang wie bisher.
     */
    public volatile boolean vorlauf = "an".equalsIgnoreCase(System.getProperty("wasser.vorlauf", "aus"));
    /**
     * Seit S3c-8 (SF34 A, Seegang Werkbuch 26): das Netz schreibt je Bildpunkt eine Maske, und der alte Weg verwirft die
     * Bildpunkte des Netzes damit vor seiner Suche (vorher erst danach, rund 1,5 ms je Bild mit viel Wasser). Das Bild bleibt
     * gleich (N6d). Aus mit {@code -Dwasser.maske=aus}.
     */
    public volatile boolean maske = !"aus".equalsIgnoreCase(System.getProperty("wasser.maske", "an"));
    private Bild netzMaske;
    private boolean maskeGilt;
    /** Seit S3c-6 (N6): Zähler der Aufrufe je Bildpunkt im Netz ({@link #zaehlung}); nur für den Befund. */
    public volatile boolean zaehlen;
    private final Zeichner netzVorlauf, netzSchatten;
    /**
     * Seit S9c-1 (Seegang Werkbuch 108, SF175 A): die Spiegelung des Geländes im Netz in halber Auflösung – netz_halb.frag
     * legt Strahl und Himmel ab, spiegel_halb.comp rechnet einen von vier Bildpunkten, spiegel_misch.frag mischt dazu.
     * Aus ({@code -Dlauf.spiegelhalb=aus}): voll wie bis S9b. Gilt nur, wo das Netz zeichnet (der alte Weg bleibt voll).
     */
    public volatile boolean spiegelHalb = !"aus".equals(System.getProperty("lauf.spiegelhalb"));
    private final Zeichner netzZeichnerH, netzSchattenH, zMisch;
    private final Kernel kHalb;
    private Bild spA, spB;
    private Puffer shPuffer;
    private int spBreite, spHoehe;
    private final Bild siegerLeer;
    private final Puffer zaehlLeer;
    private Bild sieger;
    private Puffer zaehlPuffer;
    private int zaehlB, zaehlH;
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
        zeichner = Zeichner.lade(g, p + "wasser.vert", p + "wasser.frag", 16, null, art, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, X, P, P);   // 17 seit S6c-2, 18 seit S9b-1
        // seit S3c: das Netz der Clipmap des Seegangs (render.seegang.Seegangbild legt sie ab)
        // seit S3c-8 (SF34 A): zweites Ziel des Netzes die Maske (Nummer des Dreiecks) für den alten Weg
        Zeichner.Art artN = new Zeichner.Art();
        artN.farben = new int[] {Bild.RGBA16F, SIEGER};
        netzZeichner = Zeichner.lade(g, p + "netz.vert", p + "netz.frag", 16, null, artN, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, P, P, P, X, P, P, P, P);   // 22 seit S6c-2, 23 seit S9b-1
        // seit S3c-6 (N6): Vorlauf der Tiefe (Ziel: Nummer des Dreiecks) und Schattierung danach ohne Prüfung der Tiefe
        Zeichner.Art artV = new Zeichner.Art();
        artV.farben = new int[] {SIEGER};
        netzVorlauf = Zeichner.lade(g, p + "netz.vert", p + "netz_vorlauf.frag", 16, null, artV, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, P, P, P, X, P, P, P);
        Zeichner.Art artS = new Zeichner.Art();
        artS.farben = new int[] {Bild.RGBA16F, SIEGER};
        artS.tiefeTesten = artS.tiefeSchreiben = false;
        netzSchatten = Zeichner.lade(g, p + "netz.vert", p + "netz.frag", 16, null, artS, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, P, P, P, X, P, P, P, P);
        // seit S9c-1: dieselben mit aufgeschobener Spiegelung (zwei Ziele mehr), dazu Rechnen und Mischen
        Zeichner.Art artNH = new Zeichner.Art();
        artNH.farben = new int[] {Bild.RGBA16F, SIEGER, Bild.RGBA16F, Bild.RGBA16F};
        netzZeichnerH = Zeichner.lade(g, p + "netz.vert", p + "netz_halb.frag", 16, null, artNH, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, P, P, P, X, P, P, P, P);
        Zeichner.Art artSH = new Zeichner.Art();
        artSH.farben = new int[] {Bild.RGBA16F, SIEGER, Bild.RGBA16F, Bild.RGBA16F};
        artSH.tiefeTesten = artSH.tiefeSchreiben = false;
        netzSchattenH = Zeichner.lade(g, p + "netz.vert", p + "netz_halb.frag", 16, null, artSH, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, P, P, P, X, P, P, P, P);
        kHalb = Kernel.lade(g, p + "spiegel_halb.comp", 0, null, P, P, P, P, X, X, X, X, P, X, P);
        Zeichner.Art artM = new Zeichner.Art().ohneTiefe();
        artM.farben = new int[] {Bild.RGBA16F};
        artM.mischen = true;
        zMisch = Zeichner.lade(g, p + "voll.vert", p + "spiegel_misch.frag", 16, null, artM, X, X, P);
        siegerLeer = new Bild(g, konto, "Wasser, ohne Sieger des Vorlaufs", 1, 1, SIEGER);
        siegerLeer.schreibe(ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()));
        zaehlLeer = Puffer.speicher(g, konto, "Wasser, ohne Zähler des Netzes", 16);
        kNetz = Kernel.lade(g, p + "netz.comp", 0, null, P, P, P, P, X, X, X, X, P, P, P, P, X, P, P, P, P, P, P, P);   // 19 seit S6c-2
        swashLeer = Puffer.speicher(g, konto, "Wasser, ohne Auflauf", 64);
        // seit S9b-1: die grobe Tiefe (Kopf, Tiefe darunter, Kacheln) und ihr leerer Platz (sgKopf.w = 0: der alte Weg)
        kGrob = Kernel.lade(g, p + "tiefe_grob.comp", 0, null, P, X, P);
        grobLeer = Puffer.speicher(g, konto, "Wasser, ohne grobe Tiefe", 48);
        grobLeer.schreibe(new int[12]);
        swashLeer.schreibe(new float[16]);
        netzLeer = Puffer.speicher(g, konto, "Wasser, ohne Netz des Seegangs", 160);
        netzLeer.schreibe(new float[40]);
        // seit S4a (Seegang Werkbuch 35): Bindung 21 des Netzes, das Schaumfeld (render.seegang legt es unter Seegang/Schaum ab)
        schaumLeer = Puffer.speicher(g, konto, "Wasser, ohne Schaumfeld", 128);
        schaumLeer.schreibe(new int[32]);
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

    /** Seit S8d: das Licht des Himmels aufs Wasser (licht.comp; himmelLuft, himmelWasser, …), für {@link Unterwasser}. */
    public Puffer lichtPuffer() { return lichtPuffer; }

    /** Seit S3c: die Scheitel des Netzes des letzten Bilds (je Stelle zwei vec4, netz_daten.glsl), null ohne Netz; für Prüfungen. */
    public Puffer scheitel() { return scheitel; }

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
        // seit S9b-2 (SF172 A): die Kräusel brechen im Shader bei der ersten Welle unter dem Bildpunkt ab; dazu müssen sie nach
        // der Länge kommen, lang zuerst. Wasseroptik.wellen liefert sie so (Band für Band, im Band gleich lang); sonst ordnen
        if (!nachLaenge(w)) {
            w = w.clone();
            java.util.Arrays.sort(w, java.util.Comparator.comparingDouble(q -> Math.hypot(q[0], q[1])));
        }
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
        kopf.putFloat((float) (Math.toRadians(b.bildwinkel) / b.hoehe_px)).putFloat(spiegeln ? (spiegelHalb ? 2 : 1) : 0).putFloat(teil).putFloat(wirksam ? 1 : 0);
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
        // seit S3c-7 (SF33 A): z die Art des Übergangs am Rand eines feineren Netzes (Seegangsnetz.RAND_ART)
        for (int q = 0; q < SEENETZE; q++) kopf.putFloat((float) gew[q][0]).putFloat((float) gew[q][1]).putFloat(Seegangsnetz.RAND_ART).putFloat(0);
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
        kopf.putFloat((float) u10).putFloat((float) v10).putFloat(brandung ? 1 : 0).putFloat(abbruch ? 1 : 0);
        // seit S6c-2: das Rechteck des Auflaufs (relativ zum Auge dieses Bilds)
        double[] sw = swash(z);
        if (sw != null) {
            kopf.putFloat((float) (sw[0] - b.ost)).putFloat((float) (sw[1] - b.nord)).putFloat((float) sw[2]).putFloat((float) sw[3]);
            kopf.putFloat((float) sw[4]).putFloat((float) sw[5]).putFloat((float) sw[6]).putFloat((float) sw[9]);
            kopf.putInt((int) sw[7]).putInt((int) sw[8]).putInt(1).putInt(Float.floatToRawIntBits((float) sw[10]));   // w seit S6c-2b: Zeit der Karte (Bits)
        } else {
            kopf.putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0).putFloat(1).putFloat(1).putFloat(0);
            kopf.putInt(0).putInt(0).putInt(0).putInt(0);
        }
        // seit S8b: Katzenpfoten (das Feld treibt mit dem Wind des Bilds)
        double uu = Math.hypot(u10, v10);
        katzenFeld.schritt(zeit, u10, v10);
        double[] kq = katzenFeld.ursprung(b.ost, b.nord);
        double ks = katzen ? com.dan.core.szene.Katzenpfoten.sigma(boeFaktor) : 0, kw = katzen ? com.dan.core.szene.Katzenpfoten.gewicht(uu) : 0;
        if (kw <= 0) ks = 0;
        katzenKopf = new double[] {kq[0], kq[1], kq[2], ks, uu, 1 / com.dan.core.szene.Katzenpfoten.FLECK, kw};
        kopf.putFloat((float) kq[0]).putFloat((float) kq[1]).putFloat((float) kq[2]).putFloat((float) ks);
        kopf.putFloat((float) uu).putFloat((float) (1 / com.dan.core.szene.Katzenpfoten.FLECK)).putFloat((float) kw)
                .putFloat(mess | (spiegelGrob ? 0 : 1024) | (kraeuselAbbruch ? 0 : 2048));   // seit S9a: Messschalter, seit S9b die alten Wege
        kopf.flip();
        a.durchgang("Wasser, Kopf", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kopfPuffer, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, kopfPuffer.handle, 0, kopf));
    }

    /** Seit S6c-2: die Werte des Auflaufs im Bildziel, wenn sein Bild abgelegt ist und der Schalter an ist; sonst null. */
    private double[] swash(Bildziel z) {
        double[] sw = swashAn && z.puffer.containsKey(SWASH_BILD) ? z.werte.get(SWASH_BILD) : null;
        return sw != null && sw.length >= 12 ? sw : null;
    }

    @Override public synchronized void zeichnen(Ablauf a, Bildziel z) {
        z.werte.remove(NETZ_GEZEICHNET);
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
        // seit S9b-1 (SF171 A): die grobe Tiefe je Kachel für die Spiegelung
        Puffer sgP = grobLeer;
        if (spiegeln && spiegelGrob) {
            Puffer gp = grob(z.breite, z.hoehe);
            sgP = gp;
            a.durchgang("Wasser, grobe Tiefe löschen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(gp, VK_PIPELINE_STAGE_2_CLEAR_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                    c -> vkCmdFillBuffer(c, gp.handle, 0, 32, 0));
            a.rechne("Wasser, grobe Tiefe", kGrob, kGrob.satz(kopfPuffer, punkt.an(tu), gp), null,
                    (z.breite + GROB_KACHEL - 1) / GROB_KACHEL, (z.hoehe + GROB_KACHEL - 1) / GROB_KACHEL, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopfPuffer), Ablauf.textur(Ablauf.RECHNEN, tu), Ablauf.schreibt(gp)));
        }
        Bild durch = z.geteilt("Himmel/Durchlaessigkeit"), ansicht = z.geteilt("Himmel/Ansicht");
        Puffer werte = z.puffer("Himmel/Werte");
        a.rechne("Wasser, Himmelslicht", kLicht, kLicht.satz(kopfPuffer, staende, arten, werte, punkt.an(durch), punkt.an(ansicht), lichtPuffer, saeulePuffer), null, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopfPuffer, staende, arten, werte, saeulePuffer), Ablauf.textur(Ablauf.RECHNEN, durch, ansicht), Ablauf.schreibt(lichtPuffer)));
        Puffer see = seePuffer != null ? seePuffer : seeLeer;
        Puffer trueb = bodPuffer != null ? bodPuffer : seeLeer;
        Bild bauB = z.geteilt.getOrDefault("Bau/Schatten", bauLeer);
        Puffer bauW = z.puffer.getOrDefault("Bau/Schattenwerte", ohneBau);
        // seit S3c-6 (N6), seit S3c-7 auch für den alten Weg: Zähler je Bildpunkt (Schalter zaehlen) und der Schub
        boolean zl = zaehlen;
        Puffer zp = zl ? zaehler(z.breite, z.hoehe) : zaehlLeer;
        if (zl) a.durchgang("Wasser, Zähler des Netzes löschen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(zp, VK_PIPELINE_STAGE_2_CLEAR_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdFillBuffer(c, zp.handle, 0, VK_WHOLE_SIZE, 0));
        Ablauf.Zugriff[] zz = {new Ablauf.Zugriff(zp, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT, VK_IMAGE_LAYOUT_GENERAL)};
        ByteBuffer schubAlt = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        schubAlt.putInt(0, 0).putInt(4, zl ? 1 : 0).putInt(8, z.breite);
        maskeGilt = false;
        // seit S6c-2: das Bild des Auflaufs (Bindung 19 des Kernels, 22 des Netzes, 17 des alten Wegs)
        Puffer swP = swash(z) != null ? z.puffer.get(SWASH_BILD) : swashLeer;
        // seit S3c: das Netz der Clipmap, wo render.seegang eine ablegt
        Puffer sn = netz ? z.puffer.get("Seegang/Netz") : null;
        double[] snw = z.werte.get("Seegang/Netz");
        if (sn != null && snw != null) {
            z.werte.put(NETZ_GEZEICHNET, snw);                                 // seit S3c-5 (SF28 A): für die Brandung
            int st = (int) snw[0], n = (int) snw[1];
            long groesse = 32L * st * n * n;
            if (scheitel == null || scheitel.groesse < groesse) {
                if (scheitel != null) { g.ruhe(); scheitel.close(); }
                scheitel = Puffer.speicher(g, konto, "Wasser, Scheitel des Netzes", groesse);
            }
            Puffer ve = z.puffer("Seegang/Versatz"), no = z.puffer("Seegang/Normale"), au = z.puffer("Seegang/Aufgeloest"), sc = scheitel;
            Object[] bel = {kopfPuffer, staende, arten, werte, punkt.an(durch), punkt.an(ansicht), punkt.an(u), punkt.an(tu), saeulePuffer, lichtPuffer, see, trueb, punkt.an(bauB), bauW, sn, ve, no, au, sc};
            int gx = (n + 7) / 8;
            Object[] belK = java.util.Arrays.copyOf(bel, bel.length + 1);
            belK[bel.length] = swP;
            a.rechne("Wasser, Scheitel des Netzes", kNetz, kNetz.satz(belK), null, gx, gx, st,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopfPuffer, staende, arten, sn, ve, swP), Ablauf.schreibt(sc)));
            int scheitelZahl = 6 * (n - 1) * (n - 1);
            // seit S3c-6 (N6): Vorlauf der Tiefe und Zähler je Bildpunkt (Schalter vorlauf, zaehlen)
            boolean vl = vorlauf;
            Bild sg = vl ? sieger(z.breite, z.hoehe) : siegerLeer;
            Object[] belN = java.util.Arrays.copyOf(bel, bel.length + 5), belV = java.util.Arrays.copyOf(bel, bel.length + 4);
            belN[bel.length + 4] = sgP;                                         // seit S9b-1: Bindung 23, die grobe Tiefe
            belN[bel.length + 3] = belV[bel.length + 3] = swP;
            belN[bel.length] = punkt.an(sg);
            belV[bel.length] = punkt.an(siegerLeer);
            belN[bel.length + 1] = belV[bel.length + 1] = zp;
            Puffer sch = z.puffer.getOrDefault("Seegang/Schaum", schaumLeer);  // seit S4a: das Schaumfeld (Bindung 21)
            belN[bel.length + 2] = belV[bel.length + 2] = sch;
            ByteBuffer schub = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            schub.putInt(0, vl ? 1 : 0).putInt(4, zl ? 1 : 0).putInt(8, z.breite);
            Mittel[] liestN = {kopfPuffer, staende, arten, werte, saeulePuffer, lichtPuffer, see, trueb, bauW, sn, no, au, sc, sch, swP, sgP};
            if (vl) {
                long satzV = netzVorlauf.satz(belV);
                a.zeichne("Wasser, Netz, Vorlauf", new Bild[] {sg}, new float[][] {{0, 0, 0, 0}}, tiefe, null,
                        Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, liestN), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, u, tu, bauB, siegerLeer), zz),
                        c -> netzVorlauf.zeichne(c, satzV, schub, scheitelZahl, st));
            }
            boolean halb = spiegeln && spiegelHalb;                              // seit S9c-1 (SF175 A)
            Zeichner zn = vl ? (halb ? netzSchattenH : netzSchatten) : (halb ? netzZeichnerH : netzZeichner);
            long satzN = zn.satz(belN);
            Bild nm = netzMaske(z.breite, z.hoehe);
            maskeGilt = true;
            if (halb) {
                spiegelZiele(z.breite, z.hoehe);
                Bild sa = spA, sb = spB;
                Puffer shp = shPuffer;
                a.zeichne("Wasser, Netz", new Bild[] {hdr, nm, sa, sb}, new float[][] {null, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}, tiefe, null,
                        Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, liestN), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, u, tu, bauB, sg), zz),
                        c -> zn.zeichne(c, satzN, schub, scheitelZahl, st));
                int hx = (z.breite + 1) / 2, hy = (z.hoehe + 1) / 2;
                a.rechne("Wasser, Spiegelung halb", kHalb, kHalb.satz(kopfPuffer, staende, arten, werte, punkt.an(durch), punkt.an(ansicht), punkt.an(u), punkt.an(tu), sgP, punkt.an(sb), shp),
                        null, Kernel.gruppen(hx, 8), Kernel.gruppen(hy, 8), 1,
                        Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopfPuffer, staende, arten, werte, sgP), Ablauf.textur(Ablauf.RECHNEN, durch, ansicht, u, tu, sb), Ablauf.schreibt(shp)));
                long satzM = zMisch.satz(punkt.an(sa), punkt.an(sb), shp);
                ByteBuffer schubM = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                schubM.putInt(0, hx);
                a.zeichne("Wasser, Spiegelung mischen", new Bild[] {hdr}, null, null, null,
                        Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, shp), Ablauf.textur(Ablauf.ZEICHNEN, sa, sb)),
                        c -> zMisch.zeichne(c, satzM, schubM, 3, 1));
            } else
                a.zeichne("Wasser, Netz", new Bild[] {hdr, nm}, new float[][] {null, {0, 0, 0, 0}}, tiefe, null,
                        Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, liestN), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, u, tu, bauB, sg), zz),
                        c -> zn.zeichne(c, satzN, schub, scheitelZahl, st));
        } else sn = netzLeer;
        Puffer snF = sn;
        // seit S3c-8 (SF34 A): die Maske des Netzes, wenn es in diesem Bild gezeichnet hat und der Schalter an ist
        boolean mg = maskeGilt && maske;
        Bild nmA = mg ? netzMaske : siegerLeer;
        schubAlt.putInt(12, mg ? 1 : 0);
        long satz = zeichner.satz(kopfPuffer, staende, arten, werte, punkt.an(durch), punkt.an(ansicht), punkt.an(u), punkt.an(tu), saeulePuffer, lichtPuffer, see, trueb, punkt.an(bauB), bauW, snF, zp, punkt.an(nmA), swP, sgP);
        a.zeichne("Wasser", new Bild[] {hdr}, null, tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, new Mittel[] {kopfPuffer, staende, arten, werte, saeulePuffer, lichtPuffer, see, trueb, bauW, snF, swP, sgP}), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, u, tu, bauB, nmA), zz),
                c -> zeichner.zeichne(c, satz, schubAlt, 3, 1));
    }

    /**
     * η_Bild (m NHN) am Ort (x Ost, z Süd) relativ zum Auge des letzten Bilds, in float wie {@code wasser.glsl};
     * {@code Float.NaN}, wo kein Wasser ist. {@code gitter[0]}: Nummer des genutzten Gitters (−1 keins), darf null sein.
     * Die Regel steht in {@link Spiegelbild} (core.szene).
     */
    public synchronized float etaBild(float px, float pz, int[] gitter) { return bild.etaBild(px, pz, gitter); }

    /** Seit S3c-5 (B47): η eines Scheitels des Netzes wie {@code netz.comp}, mit dem Pegel des letzten Bilds ({@link Spiegelbild#etaScheitel}). */
    public synchronized float etaScheitel(float px, float pz, float dL) { return bild.etaScheitel(px, pz, dL, pegel); }

    /** η_Bild des letzten Bilds (core.szene). */
    public synchronized Spiegelbild spiegelbild() { return bild; }

    /** Der Pegel, mit dem die Suche im letzten Bild anfing (m NHN). */
    public synchronized float startwert() { return pegel; }

    /** Seit S9b-2: kommen die Wellen lang zuerst (Wellenzahl nicht fallend, bis auf Rundung im Band)? */
    static boolean nachLaenge(double[][] w) {
        for (int i = 1; i < w.length; i++)
            if (Math.hypot(w[i][0], w[i][1]) < Math.hypot(w[i - 1][0], w[i - 1][1]) * (1 - 1e-9)) return false;
        return true;
    }

    /**
     * Seit S9b-1: Kopf der groben Tiefe des letzten Bilds {Kachel, Kacheln quer, hoch, 1 = gesetzt, H und G des ganzen Bilds als
     * geordnete uint}; null ohne. Nur zur Prüfung (liest vom Gerät).
     */
    public int[] grobKopf() { Puffer p = grobPuffer; return p == null ? null : p.ints(8); }

    /** Seit S9c-1: Ziele der aufgeschobenen Spiegelung (zwei Bilder in voller, ein Puffer in halber Auflösung). */
    private void spiegelZiele(int b, int h) {
        if (spA == null || spBreite != b || spHoehe != h) {
            if (spA != null) { g.ruhe(); spA.close(); spB.close(); shPuffer.close(); }
            spA = new Bild(g, konto, "Wasser, Spiegelung: M · Himmel, M", b, h, Bild.RGBA16F);
            spB = new Bild(g, konto, "Wasser, Spiegelung: Strahl, Abstand", b, h, Bild.RGBA16F);
            shPuffer = Puffer.speicher(g, konto, "Wasser, Spiegelung in halber Auflösung", 16L * ((b + 1) / 2) * ((h + 1) / 2));
            spBreite = b;
            spHoehe = h;
        }
    }

    private Puffer grob(int b, int h) {
        if (grobPuffer == null || grobB != b || grobH != h) {
            if (grobPuffer != null) { g.ruhe(); grobPuffer.close(); }
            long n = (long) ((b + GROB_KACHEL - 1) / GROB_KACHEL) * ((h + GROB_KACHEL - 1) / GROB_KACHEL);
            grobPuffer = Puffer.speicher(g, konto, "Wasser, grobe Tiefe", 32 + 16 * n);
            grobB = b;
            grobH = h;
        }
        return grobPuffer;
    }

    private Bild netzMaske(int b, int h) {
        if (netzMaske == null || netzMaske.breite != b || netzMaske.hoehe != h) {
            if (netzMaske != null) { g.ruhe(); netzMaske.close(); }
            netzMaske = new Bild(g, konto, "Wasser, Maske des Netzes", b, h, SIEGER);
        }
        return netzMaske;
    }

    private Bild sieger(int b, int h) {
        if (sieger == null || sieger.breite != b || sieger.hoehe != h) {
            if (sieger != null) { g.ruhe(); sieger.close(); }
            sieger = new Bild(g, konto, "Wasser, Sieger des Vorlaufs", b, h, SIEGER);
        }
        return sieger;
    }

    private Puffer zaehler(int b, int h) {
        if (zaehlPuffer == null || zaehlB != b || zaehlH != h) {
            if (zaehlPuffer != null) { g.ruhe(); zaehlPuffer.close(); }
            zaehlPuffer = Puffer.speicher(g, konto, "Wasser, Zähler des Netzes", 32L * b * h);
            zaehlB = b;
            zaehlH = h;
        }
        return zaehlPuffer;
    }

    /**
     * Seit S3c-6 (N6): die Zähler des letzten Bilds mit {@link #zaehlen}: {0 Bildpunkte mit Optik im Netz, 1 Aufrufe bis
     * zur Optik, 2 am meisten an einem Bildpunkt, 3 Bildpunkte mit mehr als einem Aufruf bis zur Optik, 4 Aufrufe in
     * netz.frag, 5 davon mit Fuß, 6 Aufrufe im Vorlauf, 7 Bildpunkte mit einem Aufruf in netz.frag}; seit S3c-7 (SF32 A)
     * dazu {8 Bildpunkte mit B47 im Netz, 9 Bildpunkte, die der alte Weg im Netz erst nach der Suche verwirft, 10 Aufrufe
     * des alten Wegs, 11 Bildpunkte des alten Wegs bis zur Optik}. null ohne Zähler.
     */
    public synchronized long[] zaehlung() {
        if (zaehlPuffer == null) return null;
        g.ruhe();
        int[] v = zaehlPuffer.ints(8 * zaehlB * zaehlH);
        long[] r = new long[12];
        for (int i = 0; i < zaehlB * zaehlH; i++) {
            int o = 8 * i, vo = v[o], al = v[o + 1], fu = v[o + 2], op = v[o + 3];
            if (op > 0) r[0]++;
            r[1] += op;
            r[2] = Math.max(r[2], op);
            if (op > 1) r[3]++;
            r[4] += al;
            r[5] += fu;
            r[6] += vo;
            if (al > 0) r[7]++;
            if (v[o + 4] > 0) r[8]++;
            if (v[o + 5] > 0) r[9]++;
            r[10] += v[o + 6];
            if (v[o + 7] > 0) r[11]++;
        }
        return r;
    }

    /** Konstante für Tests: kein Wasser auf der Karte. */
    public static float kein() { return KEIN; }

    @Override public synchronized void close() {
        g.ruhe();
        zeichner.close();
        netzZeichner.close();
        netzVorlauf.close();
        netzSchatten.close();
        siegerLeer.close();
        zaehlLeer.close();
        if (sieger != null) sieger.close();
        if (netzMaske != null) netzMaske.close();
        if (zaehlPuffer != null) zaehlPuffer.close();
        kNetz.close();
        kGrob.close();
        netzZeichnerH.close();
        netzSchattenH.close();
        zMisch.close();
        kHalb.close();
        if (spA != null) { spA.close(); spB.close(); shPuffer.close(); }
        grobLeer.close();
        if (grobPuffer != null) grobPuffer.close();
        netzLeer.close();
        schaumLeer.close();
        swashLeer.close();
        if (scheitel != null) scheitel.close();
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
