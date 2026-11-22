package com.dan.render.analysis;

import com.dan.core.gitter.Gitter;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Auflage;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Der Messblick (seit P11c2, Werkbuch HalligHooge Teil A, 12.12): eine {@link Auflage} über dem fertigen Bild mit drei
 * Ebenen auf einem Gitter der Strömung (G3).
 * <ul>
 * <li><b>Partikel</b> ({@code partikel.comp}, {@code spur.vert}): {@link #zahl} Teilchen [G] ziehen mit der Strömung,
 * Runge-Kutta 2 in Teilschritten (höchstens eine halbe Zelle je Teilschritt); je Teilchen eine Spur aus {@link #K}
 * Punkten über {@link #spurDauer} Szenenzeit [G], Farbe nach der Geschwindigkeit. Sie sitzen auf dem Wasserstand.</li>
 * <li><b>LIC</b> ({@code lic.comp}, {@code lic.frag}): Line Integral Convolution, zwei mal zwei Bildpunkte je Zelle,
 * gerechnet, wenn ein Stand kommt oder das Gewicht zwischen zwei Ständen um mehr als ein Achtel wandert, verteilt auf
 * {@link #licStreifenZahl} Bilder (seit W5 8, vorher {@link #STREIFEN}) in ein zweites Bild (gezeigt wird das fertige); gelegt auf die Wasserfläche aus der Tiefe der
 * Szene.</li>
 * <li>Das Feld zum Gewicht w liegt je Bild in einem Bild ({@code feldbild.comp}, RGBA32F); Partikel und LIC lesen
 * daraus mit vier Zugriffen je Probe.</li>
 * <li><b>Isolinien</b> ({@link Isolinien}, {@code linie.vert}): Wasserstand, Tiefe oder Trübung, auf dem Prozessor in
 * einem eigenen Faden aus dem näheren der zwei Stände, nach Douglas und Peucker vereinfacht ({@link #toleranz} Zellen).</li>
 * </ul>
 * Linien und Spuren testen die Tiefe der Szene (Deiche und Warften verdecken sie) und werden um einen kleinen Faktor zum
 * Auge gezogen. Die Felder kommen von außen ({@link #staende}, {@link #zeit}); der Messblick kennt weder Bus noch Archiv.
 */
public final class Messblick implements Auflage {
    public static final String VERSION = "0.1";
    private static final String PFAD = "/com/dan/render/analysis/";
    /** Punkte je Spur (wie {@code K} in den Shadern). */
    public static final int K = 8;
    /** Werte je Partikel im Puffer. */
    public static final int WERTE = 8 + 4 * K;

    /** Was die Isolinien zeigen. */
    public enum Linienart { WASSERSTAND, TIEFE, TRUEBUNG }

    // ------------------------------------------------------------------ Einstellungen
    public volatile boolean partikel = true, lic = true, linien = true;
    public volatile Linienart art = Linienart.WASSERSTAND;
    /** Zahl der Partikel [G]. */
    public final int zahl;
    /** Länge der Spur in Szenenzeit (s) [G]. */
    public double spurDauer = 120;
    /** Unter dieser Tiefe (m) gilt eine Zelle als trocken [G]. */
    public double hMin = 0.05;
    /** Lebensdauer der Partikel (s Szenenzeit) [G]. */
    public double lebenMin = 600, lebenMax = 1800;
    /** Geschwindigkeit am Ende der Farbleiter (m/s) [G]. */
    public double vLeiter = 1.5;
    /** Deckung der Ebenen [G]. */
    public double licDeckung = 0.45, linienDeckung = 0.9, partikelDeckung = 0.85;
    /** Toleranz von Douglas und Peucker (Zellen) [G]. */
    public double toleranz = 0.1;
    /** Schritte der LIC je Richtung (halbe Bildpunkte) [G]. */
    public int licSchritte = 16;
    /** Faktor, um den Linien und Spuren zum Auge gezogen werden (umgekehrte Tiefe) [G]; Hub über dem Wasser (m). */
    public double tiefeFaktor = 1.0003, hub = 0.05;
    /** Stufen der Linien je Art; null: Wasserstand nach der Spanne im Feld, Tiefe und Trübung fest. */
    public double[] stufen;
    public static final double[] STUFEN_TIEFE = {0.5, 1, 2, 3, 5, 10, 15, 20};
    public static final double[] STUFEN_TRUEBUNG = {5, 10, 20, 50, 100, 200, 500};

    private final Geraet g;
    private final Konto konto;
    private final Kernel kPartikel, kLic, kFeldbild;
    private final Abtaster punkt;
    private final Map<Integer, Zeichner> zLinie = new HashMap<>(), zSpur = new HashMap<>(), zLic = new HashMap<>();
    private final Puffer[] feld = new Puffer[3];
    private final Puffer[] linienP = new Puffer[3];
    private Puffer teile, feldK;
    private final Bild[] licB = new Bild[2];
    private Bild feldB;
    /** In wie viele Streifen (je ein Bild) die LIC bis W4 zerfiel [G]. */
    public static final int STREIFEN = 4;
    /**
     * Seit W5 (Werkbuch HalligHooge 126, A43, F212 A): Streifen der LIC je neuer Rechnung, je Bild einer. 8 statt 4 halbiert
     * die Kosten je Bild, solange die LIC rechnet; ein neues Bild der LIC braucht dafür 8 statt 4 Bilder (bei 60 B/s 0,13 s).
     */
    public volatile int licStreifenZahl = 8;
    private int licZahl = 8;
    private int licAnzeige = -1, licZiel, licStreifen = -1;
    private long fbStand = -1;
    private double fbW = Double.NaN;
    private final Abtaster linear;
    private int feldNr = -1, linienNr = -1, linienN, linienPlatz;
    /** Seit P11c3: der Schnitt als Strecken (eigener Puffer, weiß). */
    private Puffer schnittP;
    private int schnittN;
    private ByteBuffer hoch;

    // Stand
    private Gitter gitter;
    private float[][] a, b;
    private float[] truebA, truebB;
    private double tA, tB, vMax;
    private long nummer;
    private boolean feldNeu, alleNeu = true;
    private double t = Double.NaN, tVorher = Double.NaN, w, seitSchieben;
    private final List<double[]> vorlauf = new ArrayList<>();
    private long bilder;
    private long licStand = -1;
    private double wLic = Double.NaN;

    // Isolinien im eigenen Faden
    private final ExecutorService rechner = Executors.newSingleThreadExecutor(r -> {
        Thread th = new Thread(r, "Messblick, Isolinien");
        th.setDaemon(true);
        return th;
    });
    private Future<float[]> linienArbeit;
    private String linienGewollt, linienDa;
    /** Für Prüfungen: die LIC in jedem Bild rechnen (der teuerste Fall). */
    public volatile boolean licJedesBild;

    /** Wartet, bis die laufende Rechnung der Isolinien fertig ist (für Standbilder); das nächste Bild lädt sie hoch. */
    public void linienAbwarten() {
        Future<float[]> f;
        synchronized (this) {
            f = linienArbeit;
        }
        if (f == null) return;
        try {
            f.get();
        } catch (Exception e) {
            // die Meldung kommt beim Hochladen
        }
    }

    /** Zeit der letzten Rechnung der Isolinien (ms, Prozessor), zur Auskunft. */
    public volatile double linienMs;

    public Messblick(Geraet g, int zahl) {
        this.g = g;
        this.zahl = zahl;
        konto = g.konto("Messblick", 512L << 20);
        kPartikel = Kernel.lade(g, PFAD + "partikel.comp", 64, null, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER);
        kLic = Kernel.lade(g, PFAD + "lic.comp", 32, null, Kernel.Bindung.TEXTUR, Kernel.Bindung.BILD);
        kFeldbild = Kernel.lade(g, PFAD + "feldbild.comp", 16, null, Kernel.Bindung.PUFFER, Kernel.Bindung.BILD);
        punkt = new Abtaster(g, false, false);
        linear = new Abtaster(g, true, false);
        teile = Puffer.speicher(g, konto, "Messblick, Partikel", 4L * WERTE * zahl);
        teile.nullen();
    }

    public Messblick(Geraet g) { this(g, 20_000); }

    @Override public String name() { return "Messblick"; }

    public Gitter gitter() { return gitter; }

    /** Zahl der Strecken der Isolinien, die gerade gezeichnet werden. */
    public int strecken() { return linienN / 2; }

    public long nummer() { return nummer; }

    // ------------------------------------------------------------------ Felder von außen

    /**
     * Zwei Stände auf einem Gitter: je Stand {u, v, η, h} (m/s nach Ost und Nord in der Zelle, m NHN, m); u, v dürfen
     * null sein (dann ruht das Wasser: keine Partikel, keine LIC). Die Trübung (g/m³) darf null sein. Die Felder werden
     * nicht kopiert und dürfen danach nicht mehr geändert werden.
     */
    public synchronized void staende(Gitter gitter, float[][] a, float[][] b, float[] truebungA, float[] truebungB, double tA, double tB) {
        boolean neuesGitter = this.gitter == null || this.gitter.nx != gitter.nx || this.gitter.ny != gitter.ny || this.gitter.x0 != gitter.x0 || this.gitter.y0 != gitter.y0;
        this.gitter = gitter;
        this.a = voll(a, gitter.zellen());
        this.b = voll(b, gitter.zellen());
        truebA = truebungA;
        truebB = truebungB;
        this.tA = tA;
        this.tB = tB;
        double m = 0;
        for (float[][] s : new float[][][] {this.a, this.b})
            for (int c = 0; c < gitter.zellen(); c++) m = Math.max(m, Math.hypot(s[0][c], s[1][c]));
        vMax = m;
        nummer++;
        feldNeu = true;
        if (neuesGitter) {
            alleNeu = true;
            licStand = -1;
            linienDa = null;
            linienN = 0;
        }
    }

    private static float[][] voll(float[][] s, int n) {
        float[][] r = new float[4][];
        for (int e = 0; e < 4; e++) r[e] = s[e] != null ? s[e] : new float[n];
        return r;
    }

    /** Die Szenenzeit des nächsten Bilds (s); das Gewicht zwischen den Ständen folgt daraus. */
    public synchronized void zeit(double t) {
        this.t = t;
        w = tB > tA ? Math.max(0, Math.min(1, (t - tA) / (tB - tA))) : 0;
    }

    /**
     * Seit P11c3: der Schnitt als Strecken (je Scheitel Ost, Nord in m vom Ursprung des Gitters, Höhe, –), zwei Scheitel je
     * Strecke; null nimmt ihn weg. Gezeichnet in Weiß über allen Ebenen, auch wenn der Messblick sonst nichts zeigt.
     */
    public synchronized void schnitt(float[] punkte) {
        schnittNeu = punkte == null ? new float[0] : punkte.length > 4 * SCHNITT_PLATZ ? java.util.Arrays.copyOf(punkte, 4 * SCHNITT_PLATZ) : punkte;
    }

    /** Höchstens so viele Scheitel hat ein Schnitt (je Platz im Ring). */
    static final int SCHNITT_PLATZ = 16_384;
    private float[] schnittNeu;
    private final Puffer[] schnittRing = new Puffer[3];
    private int schnittNr = -1;

    /** Im Faden des Bilds: einen neuen Schnitt in den nächsten Platz des Rings (die zwei Bilder unterwegs lesen die anderen). */
    private void schnittPflegen() {
        float[] p = schnittNeu;
        if (p == null) return;
        schnittNeu = null;
        if (p.length < 8) {
            schnittN = 0;
            return;
        }
        if (schnittRing[0] == null) for (int i = 0; i < 3; i++) schnittRing[i] = Puffer.geteilt(g, konto, "Messblick, Schnitt " + i, 16L * SCHNITT_PLATZ);
        schnittNr = (schnittNr + 1) % 3;
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * p.length).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(p);
        schnittRing[schnittNr].schreibe(bb, 0);
        schnittP = schnittRing[schnittNr];
        schnittN = p.length / 4;
    }

    /** Lässt die Partikel im nächsten Bild zusätzlich {@code schritte} Schritte über {@code sekunden} Szenenzeit ziehen (für Standbilder). */
    public synchronized void vorlauf(double sekunden, int schritte) {
        for (int i = 0; i < schritte; i++) vorlauf.add(new double[] {sekunden / schritte});
    }

    /** Alle Partikel im nächsten Bild neu setzen. */
    public synchronized void zuruecksetzen() { alleNeu = true; }

    // ------------------------------------------------------------------ je Bild

    @Override public synchronized void vorbereiten(Ablauf ab, Bildziel z) {
        if (gitter == null) return;
        int n = gitter.zellen();
        if (feld[0] == null || feld[0].groesse < 4L * 8 * n) anlegen(n);
        if (feldNeu) feldHoch(ab, n);
        feldbild(ab);
        bilder++;
        if (partikel) {
            List<double[]> schritte = new ArrayList<>(vorlauf);
            vorlauf.clear();
            if (!Double.isNaN(t)) {
                double dt = Double.isNaN(tVorher) ? 0 : t - tVorher;
                if (Math.abs(dt) > 3600) {
                    alleNeu = true;
                    dt = 0;
                }
                schritte.add(new double[] {dt});
            }
            tVorher = t;
            for (double[] s : schritte) partikelSchritt(ab, s[0], -1);
        } else tVorher = t;
        if (lic && vMax > 0) {
            // neu, wenn ein Stand kommt oder das Gewicht um ein Achtel wandert; gerechnet in STREIFEN Bildern in das zweite
            // Bild, gezeigt wird das fertige
            if (licStreifen < 0 && (licJedesBild || licStand != nummer || Math.abs(w - wLic) > 0.125)) {
                licStreifen = 0;
                licZahl = Math.max(1, licStreifenZahl);
                licZiel = licAnzeige < 0 ? 0 : 1 - licAnzeige;
                licStand = nummer;
                wLic = w;
            }
            if (licStreifen >= 0) {
                int lx = 2 * gitter.nx, ly = 2 * gitter.ny, zeilen = (ly + licZahl - 1) / licZahl, y0 = licStreifen * zeilen;
                ByteBuffer p = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).putInt(gitter.nx).putInt(gitter.ny).putInt(lx).putInt(ly)
                        .putFloat(y0).putFloat(licSchritte).putFloat(0.01f).putFloat(zeilen).flip();
                Bild ziel = licB[licZiel];
                ab.rechne("Messblick LIC rechnen", kLic, kLic.satz(punkt.an(feldB), ziel), p, Kernel.gruppen(lx, 16), Kernel.gruppen(zeilen, 16), 1,
                        Ablauf.und(Ablauf.textur(Ablauf.RECHNEN, feldB), Ablauf.schreibt(ziel)));
                if (++licStreifen == licZahl) {
                    licAnzeige = licZiel;
                    licStreifen = -1;
                }
            }
        }
        if (linien) linienPflegen();
        schnittPflegen();
    }

    /** Die zwei Stände über einen geteilten Zwischenpuffer (drei Plätze) in den Puffer auf der Karte. */
    private void feldHoch(Ablauf ab, int n) {
        feldNr = (feldNr + 1) % feld.length;
        FloatBuffer fb = hoch.clear().asFloatBuffer();
        for (float[][] s : new float[][][] {a, b}) for (int e = 0; e < 4; e++) fb.put(s[e]);
        hoch.limit(4 * 8 * n);
        Puffer zw = feld[feldNr];
        zw.schreibe(hoch, 0);
        long bytes = 4L * 8 * n;
        ab.durchgang("Messblick Feld hochladen", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(zw, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, 0),
                new Ablauf.Zugriff(feldK, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            try (org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
                vkCmdCopyBuffer(c, zw.handle, feldK.handle, org.lwjgl.vulkan.VkBufferCopy.calloc(1, st).srcOffset(0).dstOffset(0).size(bytes));
            }
        });
        feldNeu = false;
    }

    /** Das Feld zum Gewicht w je Zelle in {@code feldB}, wenn sich Stand oder Gewicht geändert hat. */
    private void feldbild(Ablauf ab) {
        if (fbStand == nummer && fbW == w) return;
        ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).putInt(gitter.nx).putInt(gitter.ny).putFloat((float) w).putFloat(0).flip();
        ab.rechne("Messblick Feldbild", kFeldbild, kFeldbild.satz(feldK, feldB), p, Kernel.gruppen(gitter.nx, 16), Kernel.gruppen(gitter.ny, 16), 1,
                new Mittel[] {feldK}, new Mittel[] {feldB});
        fbStand = nummer;
        fbW = w;
    }

    private void anlegen(int n) {
        g.ruhe();
        for (int i = 0; i < feld.length; i++) {
            if (feld[i] != null) feld[i].close();
            feld[i] = Puffer.geteilt(g, konto, "Messblick, Feld " + i, 4L * 8 * n);
        }
        if (feldK != null) feldK.close();
        feldK = Puffer.speicher(g, konto, "Messblick, Feld", 4L * 8 * n);
        for (int i = 0; i < 2; i++) {
            if (licB[i] != null) licB[i].close();
            licB[i] = new Bild(g, konto, "Messblick, LIC " + i, 2 * gitter.nx, 2 * gitter.ny, Bild.RGBA16F);
        }
        licAnzeige = licStreifen = -1;
        if (feldB != null) feldB.close();
        feldB = new Bild(g, konto, "Messblick, Feld zum Gewicht", gitter.nx, gitter.ny, Bild.RGBA32F);
        fbStand = -1;
        hoch = ByteBuffer.allocateDirect(4 * 8 * n).order(ByteOrder.nativeOrder());
        feldNeu = true;
    }

    /** Ein Schritt der Partikel über dt Szenenzeit; teilschritte &lt; 1: nach der größten Geschwindigkeit. */
    private void partikelSchritt(Ablauf ab, double dt, int teilschritte) {
        double kante = 0.5 * Math.min(gitter.dx, gitter.dy);
        int m = teilschritte >= 1 ? teilschritte : (int) Math.max(1, Math.min(64, Math.ceil(Math.abs(dt) * vMax / kante)));
        seitSchieben += Math.abs(dt);
        boolean schieben = seitSchieben >= spurDauer / (K - 1);
        if (schieben) seitSchieben = 0;
        ByteBuffer p = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        p.putInt(gitter.nx).putInt(gitter.ny).putInt(zahl).putInt((int) bilders())
                .putFloat((float) gitter.dx).putFloat((float) gitter.dy).putFloat((float) dt).putFloat((float) w)
                .putFloat((float) hMin).putFloat(schieben ? 1 : 0).putFloat(m).putFloat((float) lebenMin)
                .putFloat((float) lebenMax).putFloat(alleNeu ? 1 : 0).putFloat(0).putFloat(0).flip();
        alleNeu = false;
        ab.rechne("Messblick Partikel", kPartikel, kPartikel.satz(punkt.an(feldB), teile), p, Kernel.gruppen(zahl, 64), 1, 1,
                Ablauf.und(Ablauf.textur(Ablauf.RECHNEN, feldB), Ablauf.schreibt(teile)));
    }

    private long zaehler;

    private long bilders() { return ++zaehler; }

    @Override public synchronized void auflegen(Ablauf ab, Bildziel z, Bild aus) {
        if (gitter == null || feldNr < 0) return;
        Blick bl = z.blick;
        double offE = gitter.x0 - bl.ost, offN = gitter.y0 - bl.nord;
        if (lic && licAnzeige >= 0) {
            Zeichner zl = zLic.computeIfAbsent(aus.format, fm -> {
                Zeichner.Art art = new Zeichner.Art().ohneTiefe();
                art.farben = new int[] {fm};
                art.mischen = true;
                return Zeichner.lade(g, PFAD + "voll.vert", PFAD + "lic.frag", 96, null, art, Kernel.Bindung.TEXTUR, Kernel.Bindung.TEXTUR);
            });
            double[][] bas = bl.basis();
            double tt = 1 / Math.tan(Math.toRadians(bl.bildwinkel) / 2), as = (double) bl.breite / bl.hoehe_px;
            ByteBuffer p = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
            vec(p, bas[0], as / tt);
            vec(p, bas[1], 1 / tt);
            vec(p, bas[2], bl.nah);
            p.putFloat((float) offE).putFloat((float) offN).putFloat((float) bl.hoehe).putFloat((float) gitter.dx);
            p.putInt(gitter.nx).putInt(gitter.ny).putInt(aus.breite).putInt(aus.hoehe);
            p.putFloat((float) licDeckung).putFloat((float) w).putFloat((float) vLeiter).putFloat((float) hMin).flip();
            byte[] push = new byte[96];
            p.get(push);
            Bild lb = licB[licAnzeige];
            long satz = zl.satz(punkt.an(z.tiefe), linear.an(lb));
            ab.zeichne("Messblick LIC", new Bild[] {aus}, null, null, null, Ablauf.textur(Ablauf.ZEICHNEN, z.tiefe, lb), c -> {
                        try (org.lwjgl.system.MemoryStack s = org.lwjgl.system.MemoryStack.stackPush()) {
                            zl.zeichne(c, satz, s.bytes(push), 3, 1);
                        }
                    });
        }
        boolean mitLinien = linien && linienN > 0 && linienNr >= 0, mitSpuren = partikel && vMax > 0, mitSchnitt = schnittN > 0;
        if (!mitLinien && !mitSpuren && !mitSchnitt) return;
        float[] vp = bl.ansichtProjektion();
        List<Mittel> gelesen = new ArrayList<>();
        Zeichner zli = null, zsp = null;
        long satzLinie = 0, satzSpur = 0;
        byte[] pushLinie = null, pushSpur = null;
        if (mitLinien) {
            zli = zLinie.computeIfAbsent(aus.format, fm -> linienZeichner(fm, "linie.vert"));
            satzLinie = zli.satz(linienP[linienNr]);
            pushLinie = push(vp, offE, offN, bl.hoehe, new float[] {1, 1, 1, (float) linienDeckung}, new float[] {0, (float) hub, 0, 0});
            gelesen.add(linienP[linienNr]);
        }
        if (mitSpuren) {
            zsp = zSpur.computeIfAbsent(aus.format, fm -> linienZeichner(fm, "spur.vert"));
            satzSpur = zsp.satz(teile);
            // über der LIC weiß (die LIC trägt die Farbe der Geschwindigkeit), sonst nach der Geschwindigkeit
            pushSpur = push(vp, offE, offN, bl.hoehe, new float[] {0.95f, 0.97f, 1, (float) partikelDeckung}, new float[] {0, (float) hub, (float) vLeiter, lic ? 0 : 1});
            gelesen.add(teile);
        }
        Zeichner zsc = null;
        long satzSchnitt = 0;
        byte[] pushSchnitt = null;
        if (mitSchnitt) {
            zsc = zLinie.computeIfAbsent(aus.format, fm -> linienZeichner(fm, "linie.vert"));
            satzSchnitt = zsc.satz(schnittP);
            pushSchnitt = push(vp, offE, offN, bl.hoehe, new float[] {1, 1, 1, 1}, new float[] {1, 0, 0, 0});
            gelesen.add(schnittP);
        }
        Zeichner fzsc = zsc;
        long fssc = satzSchnitt;
        byte[] fpsc = pushSchnitt;
        int nSchnitt = schnittN;
        Zeichner fzli = zli, fzsp = zsp;
        long fsl = satzLinie, fss = satzSpur;
        byte[] fpl = pushLinie, fps = pushSpur;
        int nLinie = linienN;
        ab.zeichne("Messblick Linien", new Bild[] {aus}, null, z.tiefe, null, Ablauf.liest(Ablauf.ZEICHNEN, gelesen.toArray(new Mittel[0])), c -> {
            try (org.lwjgl.system.MemoryStack s = org.lwjgl.system.MemoryStack.stackPush()) {
                if (fzli != null) fzli.zeichne(c, fsl, s.bytes(fpl), nLinie, 1);
                if (fzsp != null) fzsp.zeichne(c, fss, s.bytes(fps), 2 * (K - 1) * zahl, 1);
                if (fzsc != null) fzsc.zeichne(c, fssc, s.bytes(fpsc), nSchnitt, 1);
            }
        });
    }

    private Zeichner linienZeichner(int format, String scheitel) {
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {format};
        art.tiefe = Bild.D32F;
        art.tiefeTesten = true;
        art.tiefeSchreiben = false;
        art.topologie = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
        art.mischen = true;
        return Zeichner.lade(g, PFAD + scheitel, PFAD + "linie.frag", 112, null, art, Kernel.Bindung.PUFFER);
    }

    private byte[] push(float[] vp, double offE, double offN, double augeH, float[] farbe, float[] art) {
        ByteBuffer p = ByteBuffer.allocate(112).order(ByteOrder.nativeOrder());
        for (float x : vp) p.putFloat(x);
        p.putFloat((float) offE).putFloat((float) offN).putFloat((float) augeH).putFloat((float) tiefeFaktor);
        for (float x : farbe) p.putFloat(x);
        for (float x : art) p.putFloat(x);
        return p.array();
    }

    private static void vec(ByteBuffer p, double[] v, double vierter) {
        p.putFloat((float) v[0]).putFloat((float) v[1]).putFloat((float) v[2]).putFloat((float) vierter);
    }

    // ------------------------------------------------------------------ Isolinien

    private void linienPflegen() {
        boolean nachB = w >= 0.5;
        Linienart la = art;
        if (la == Linienart.TRUEBUNG && (nachB ? truebB : truebA) == null) la = Linienart.WASSERSTAND;
        String gewollt = nummer + "/" + (nachB ? "B" : "A") + "/" + la + "/" + java.util.Arrays.toString(stufen) + "/" + toleranz;
        if (linienArbeit != null && linienArbeit.isDone()) {
            try {
                float[] v = linienArbeit.get();
                hochladenLinien(v);
                linienDa = linienGewollt;
            } catch (Exception e) {
                linienN = 0;
            }
            linienArbeit = null;
        }
        if (linienArbeit == null && !gewollt.equals(linienDa)) {
            float[][] s = nachB ? b : a;
            float[] tr = nachB ? truebB : truebA;
            Gitter gi = gitter;
            Linienart fa = la;
            double[] st = stufen;
            double tol = toleranz, hm = hMin;
            linienGewollt = gewollt;
            linienArbeit = rechner.submit(() -> {
                long t0 = System.nanoTime();
                float[] r = linienRechnen(gi, s, tr, fa, st, tol, hm);
                linienMs = (System.nanoTime() - t0) / 1e6;
                return r;
            });
        }
    }

    /** Die Isolinien eines Stands als Strecken: je Scheitel Ost, Nord (m vom Ursprung des Gitters), Höhe, s. Auch für Prüfungen. */
    public static float[] linienRechnen(Gitter gi, float[][] s, float[] trueb, Linienart la, double[] st, double tol, double hMin) {
        int n = gi.zellen();
        float[] f = new float[n], eta = s[2], h = s[3];
        for (int c = 0; c < n; c++) {
            boolean nass = h[c] > hMin;
            f[c] = !nass ? Float.NaN : switch (la) {
                case WASSERSTAND -> eta[c];
                case TIEFE -> h[c];
                case TRUEBUNG -> trueb[c];
            };
        }
        double[] stu = st != null ? st : switch (la) {
            case TIEFE -> STUFEN_TIEFE;
            case TRUEBUNG -> STUFEN_TRUEBUNG;
            case WASSERSTAND -> wasserstandStufen(f);
        };
        List<float[]> teile = new ArrayList<>();
        int zahl = 0;
        for (int k = 0; k < stu.length; k++) {
            float sk = stu.length == 1 ? 0.5f : (float) k / (stu.length - 1);
            for (Isolinien.Linie l : Isolinien.linien(f, eta, gi.nx, gi.ny, stu[k])) {
                Isolinien.Linie v = Isolinien.vereinfache(l, tol);
                float[] q = new float[8 * (v.punkte() - 1)];
                for (int i = 0; i + 1 < v.punkte(); i++) {
                    for (int e = 0; e < 2; e++) {
                        int j = i + e, o = 8 * i + 4 * e;
                        q[o] = (float) (v.x[j] * gi.dx);
                        q[o + 1] = (float) (v.y[j] * gi.dy);
                        q[o + 2] = Float.isNaN(v.z[j]) ? 0 : v.z[j];
                        q[o + 3] = sk;
                    }
                }
                teile.add(q);
                zahl += q.length;
            }
        }
        float[] r = new float[zahl];
        int o = 0;
        for (float[] q : teile) {
            System.arraycopy(q, 0, r, o, q.length);
            o += q.length;
        }
        return r;
    }

    /** Stufen des Wasserstands: ein runder Abstand (1, 2, 5 · 10^k m), rund zehn Stufen über die Spanne des Felds [G]. */
    static double[] wasserstandStufen(float[] f) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (float x : f)
            if (!Float.isNaN(x)) {
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
        if (!(hi > lo)) return new double[0];
        double roh = (hi - lo) / 10, e = Math.pow(10, Math.floor(Math.log10(roh))), m = roh / e;
        double d = (m < 1.5 ? 1 : m < 3.5 ? 2 : m < 7.5 ? 5 : 10) * e;
        List<Double> l = new ArrayList<>();
        for (double x = Math.ceil(lo / d) * d; x <= hi; x += d) l.add(Math.round(x / d) * d);
        double[] r = new double[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    private void hochladenLinien(float[] v) {
        int scheitel = v.length / 4;
        if (scheitel == 0) {
            linienN = 0;
            return;
        }
        if (linienP[0] == null || linienPlatz < scheitel) {
            g.ruhe();
            int platz = Math.max(scheitel + scheitel / 2, 65_536);
            for (int i = 0; i < linienP.length; i++) {
                if (linienP[i] != null) linienP[i].close();
                linienP[i] = Puffer.geteilt(g, konto, "Messblick, Linien " + i, 16L * platz);
            }
            linienPlatz = platz;
        }
        linienNr = (linienNr + 1) % linienP.length;
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * v.length).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(v);
        linienP[linienNr].schreibe(bb, 0);
        linienN = scheitel;
    }

    // ------------------------------------------------------------------ für Prüfungen

    /** Schreibt die Partikel (je {@link #WERTE} Werte); wartet auf die Karte. */
    public void partikelSetzen(float[] werte) {
        g.ruhe();
        teile.schreibe(werte);
    }

    /** Liest die Partikel; wartet auf die Karte. */
    public float[] partikelLesen() {
        g.ruhe();
        try (Puffer z = Puffer.geteilt(g, null, "Messblick, Partikel lesen", 4L * WERTE * zahl)) {
            g.grafik.einmal(c -> {
                try (org.lwjgl.system.MemoryStack s = org.lwjgl.system.MemoryStack.stackPush()) {
                    vkCmdCopyBuffer(c, teile.handle, z.handle, org.lwjgl.vulkan.VkBufferCopy.calloc(1, s).srcOffset(0).dstOffset(0).size(4L * WERTE * zahl));
                }
            });
            return z.floats(WERTE * zahl);
        }
    }

    /** Rechnet n Schritte der Partikel zu dt mit festen Teilschritten, ohne Bild (für Prüfungen); wartet. */
    public synchronized void partikelRechnen(double dt, int n, int teilschritte) {
        int nn = gitter.zellen();
        if (feld[0] == null || feld[0].groesse < 4L * 8 * nn) anlegen(nn);
        alleNeu = false;
        Ablauf ab = new Ablauf(g, g.grafik);
        if (feldNeu) feldHoch(ab, nn);
        feldbild(ab);
        for (int i = 0; i < n; i++) partikelSchritt(ab, dt, teilschritte);
        g.grafik.einmal(c -> ab.aufnehmen(c, null));
    }

    @Override public synchronized void close() {
        rechner.shutdownNow();
        g.ruhe();
        for (Zeichner z : zLinie.values()) z.close();
        for (Zeichner z : zSpur.values()) z.close();
        for (Zeichner z : zLic.values()) z.close();
        kPartikel.close();
        kLic.close();
        kFeldbild.close();
        punkt.close();
        linear.close();
        if (feldK != null) feldK.close();
        for (Bild x : licB) if (x != null) x.close();
        if (feldB != null) feldB.close();
        for (Puffer p : feld) if (p != null) p.close();
        for (Puffer p : linienP) if (p != null) p.close();
        for (Puffer p : schnittRing) if (p != null) p.close();
        teile.close();
    }
}
