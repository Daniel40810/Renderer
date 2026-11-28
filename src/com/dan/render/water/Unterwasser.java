package com.dan.render.water;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;
import com.dan.core.szene.Wasseroptik;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Der Schwimmerblick unter Wasser (Seegang Werkbuch, Datei 104, S8d, SF160 A): ein Bildteil nach dem Wasser, der den Teil
 * des Bilds unter der Wasserlinie auf der Linse durch das Wasser von unten ersetzt ({@code unterwasser.glsl}). Die Linse
 * füllt das Bild; wo sie unter der Wasserlinie liegt, sieht sie
 * <ul>
 * <li>nach oben die Fläche von unten: im <b>Snellschen Fenster</b> (halber Winkel asin(1/n), bei n = 1,334 48,6°) den Himmel,
 * durch die Fläche gebrochen und um (1 − F) geschwächt, außerhalb Totalreflexion (das Licht des Wassers);</li>
 * <li>auf dem Weg die <b>Trübung</b>: Durchlässigkeit exp(−c s), dazu das Licht des Wassers selbst R∞ E_d(z) / π mit
 * E_d(z) = E_d(0) e^(−K_d z);</li>
 * <li>nach unten den Grund (Albedo [G]), wenn er im Wasser liegt.</li>
 * </ul>
 * <b>Optik</b> aus {@link Wasseroptik} (reines Wasser, Schwebstoff der Szene): a = a_w + a_p, b_b; Streuung b = {@link #B_STERN}
 * · C [S], c = a + b; K_d = √(a² + (0,425 μ₀ − 0,19) a b) / μ₀ (Kirk 1984, μ₀ = {@link #MU0}) [S]; R∞ = 0,33 b_b / (a + b_b)
 * (Gordon 1975, erstes Glied) [S]. Lichtstreifen [G] als Muster
 * auf dem Licht des Wassers, Kräusel der Fläche von unten [G]. An der Wasserlinie ein Meniskus [G]. Seit L6b das Licht des
 * Wassers selbst aus der Tafel der Säule ({@link #saeuleLu}), wie das Wasser von oben; R∞ bleibt Auskunft.
 * <p>
 * Die Anwendung setzt je Bild ({@link #setze}): Tiefe der Linse unter der Fläche und wie sie über die Linse zunimmt (die
 * Fläche am Auge als Ebene), Normale der Fläche am Auge, Tiefe des Grunds. Ohne Wasser an der Linse tut der Teil nichts.
 */
public final class Unterwasser implements Bildteil {
    public static final String VERSION = "0.1";
    /** Streuung des Schwebstoffs je Konzentration (m²/g) [S: 0,2–0,6 für mineralische Teilchen]. */
    public static final double B_STERN = 0.3;
    /** Albedo des Grunds (Wattboden, nass) [G]; Breite des Meniskus auf der Linse (m) [G]; Stärke der Kräusel und der Lichtstreifen [G]. */
    public static final double GRUND_ALBEDO = 0.12, MENISKUS = 0.0012, KRAEUSEL = 0.08, STREIFEN = 0.6;
    /** Kosinus des gebrochenen Sonnenstrahls im Wasser für K_d [G: Sonne rund 35° hoch]. */
    public static final double MU0 = 0.8;
    static final int KOPF = (10 + 9) * 16;

    public volatile boolean an = true;
    /** Stärke der Kräusel der Fläche von unten und der Lichtstreifen [G]; 0 für Prüfungen (Prüfstein 39 mit glatter Fläche). */
    public volatile double kraeusel = KRAEUSEL, streifen = STREIFEN;
    /** Zuletzt: ob gezeichnet wurde. */
    public volatile boolean gezeichnet;

    private final Geraet g;
    private final Konto konto;
    private final Wasseroptik optik;
    private final Puffer licht, leer;
    private final com.dan.core.szene.Saeule saeule = com.dan.core.szene.Saeule.lade();
    private final Zeichner z;
    private final Puffer kopf;
    private final ByteBuffer kopfB;
    private final Abtaster linear;
    private Bild kopie;
    /** Puffer und Bilder des Himmels aus dem letzten Bild (für {@link #probe(Blick, int, int, float[], double)}). */
    private volatile Object[] himmel;
    // Stand der Anwendung: Tiefe der Mitte der Linse unter der Fläche (m; NaN aus), Zunahme je m nach rechts und nach oben
    private volatile double tiefeM = Double.NaN, jeRechts, jeOben = -1, grundTiefe = -1, zeit, breiteL = 0.064, hoeheL = 0.036;
    private volatile double[] normale = {0, 1, 0};

    /**
     * licht: der Puffer des Lichts des Wassers ({@code Wasserbild.lichtPuffer}, aus licht.comp: Bestrahlung unter der Fläche
     * aus dem Himmel); null für Prüfungen ohne Wasserbild (dann ohne Himmel im Wasser).
     */
    public Unterwasser(Geraet g, Konto konto, Wasseroptik optik, Puffer licht) {
        this.g = g;
        this.konto = konto;
        this.optik = optik;
        if (licht == null) {
            leer = Puffer.speicher(g, konto, "Unterwasser, Licht (leer)", 64);
            leer.schreibe(new float[16]);
            this.licht = leer;
        } else {
            leer = null;
            this.licht = licht;
        }
        kopf = Puffer.speicher(g, konto, "Unterwasser, Kopf", KOPF);
        kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        linear = new Abtaster(g, true, false);
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art().ohneTiefe();
        art.farben = new int[] {Bild.RGBA16F};
        z = Zeichner.lade(g, "/com/dan/render/water/voll.vert", "/com/dan/render/water/unterwasser.frag", 0, null, art, P, P, X, X, P, X);
    }

    @Override public String name() { return "Unterwasser"; }

    /**
     * Für das nächste Bild: Tiefe der Mitte der Linse unter der Fläche (m, unter Wasser positiv; NaN: aus), wie sie je m nach
     * rechts und je m nach oben auf der Linse zunimmt (aufrecht 0 und −1), Normale der Fläche am Auge (Ost, oben, Süd),
     * Tiefe des Grunds unter der Fläche (m; < 0: keiner), Zeit (s), Breite und Höhe der Linse (m).
     */
    public void setze(double tiefe, double rechts, double oben, double[] n, double grund, double t, double breite, double hoehe) {
        tiefeM = tiefe;
        jeRechts = rechts;
        jeOben = oben;
        normale = n == null ? new double[] {0, 1, 0} : n.clone();
        grundTiefe = grund;
        zeit = t;
        breiteL = breite;
        hoeheL = hoehe;
    }

    /** Liegt ein Teil der Linse unter Wasser (mit dem Meniskus)? */
    public boolean nass() { return an && tiefsteTiefe() > -MENISKUS; }

    /** Die größte Tiefe auf der Linse (an einer Ecke; m, NaN aus). */
    public double tiefsteTiefe() {
        double d = tiefeM;
        return Double.isNaN(d) ? Double.NaN : d + 0.5 * breiteL * Math.abs(jeRechts) + 0.5 * hoeheL * Math.abs(jeOben);
    }

    /** Optik je Kanal: {c, K_d, R∞} (1/m, 1/m, 1). */
    public double[][] optik() {
        double[] ap = optik.aSchweb(), bb = optik.bb(), c = new double[3], kd = new double[3], r = new double[3];
        double b = B_STERN * optik.schwebstoff;
        for (int k = 0; k < 3; k++) {
            double a = Wasseroptik.POPE_FRY[k] + ap[k];
            c[k] = a + b;
            kd[k] = Math.sqrt(a * a + (0.425 * MU0 - 0.19) * a * b) / MU0;   // Kirk (1984)
            r[k] = 0.33 * bb[k] / (a + bb[k]);
        }
        return new double[][] {c, kd, r};
    }

    /** Mitte der Richtungen nach oben unter der Fläche, für die die Säule gilt (μ_v) [G]. */
    public static final double MU_V = 0.83;

    /**
     * Aus der Tafel der Säule (tief, x = {@code Saeule.X_MAX}) je Kanal: L nach oben (μ_v = {@link #MU_V}) je Einheit der
     * Bestrahlung unter der Fläche, für die acht Stufen μ_s der Sonne und (Zeile 8) für den Himmel, gleich hell in der Luft.
     * u = b_b / (a + b_b) wie das Wasser von oben.
     */
    public double[][] saeuleLu() {
        double[] ap = optik.aSchweb(), bb = optik.bb();
        double[][] gh = com.dan.core.szene.Saeule.himmelGewichte(optik.n);
        double[][] r = new double[9][3];
        for (int k = 0; k < 3; k++) {
            double a = Wasseroptik.POPE_FRY[k] + ap[k], u = bb[k] / (a + bb[k]);
            for (int q = 0; q < 8; q++) r[q][k] = saeule.l(u, com.dan.core.szene.Saeule.X_MAX, com.dan.core.szene.Saeule.MU_S[q], MU_V);
            double h = 0;
            for (double[] w : gh) h += w[1] * saeule.l(u, com.dan.core.szene.Saeule.X_MAX, w[0], MU_V);
            r[8][k] = h;
        }
        return r;
    }

    void kopfSchreiben(Blick b, int bw, int bh) {
        double[][] bas = b.basis();
        double ty = Math.tan(Math.toRadians(b.bildwinkel) / 2), tx = ty * bw / Math.max(1, bh);
        double[][] o = optik();
        double[] n = normale;
        kopfB.clear();
        kopfB.putFloat((float) bas[0][0]).putFloat((float) bas[0][1]).putFloat((float) bas[0][2]).putFloat((float) tx);
        kopfB.putFloat((float) bas[1][0]).putFloat((float) bas[1][1]).putFloat((float) bas[1][2]).putFloat((float) ty);
        kopfB.putFloat((float) bas[2][0]).putFloat((float) bas[2][1]).putFloat((float) bas[2][2]).putFloat((float) Math.max(0.5, b.hoehe));
        kopfB.putFloat((float) breiteL).putFloat((float) hoeheL).putFloat((float) tiefeM).putFloat((float) jeRechts);
        kopfB.putFloat((float) grundTiefe).putFloat((float) optik.n).putFloat((float) (zeit - 1000 * Math.floor(zeit / 1000))).putFloat(1);
        kopfB.putFloat((float) n[0]).putFloat((float) n[1]).putFloat((float) n[2]).putFloat((float) kraeusel);
        kopfB.putFloat((float) o[0][0]).putFloat((float) o[0][1]).putFloat((float) o[0][2]).putFloat((float) GRUND_ALBEDO);
        kopfB.putFloat((float) o[1][0]).putFloat((float) o[1][1]).putFloat((float) o[1][2]).putFloat((float) streifen);
        kopfB.putFloat((float) o[2][0]).putFloat((float) o[2][1]).putFloat((float) o[2][2]).putFloat((float) MENISKUS);
        kopfB.putFloat(bw).putFloat(bh).putFloat((float) jeOben).putFloat(0);
        double[][] lu = saeuleLu();
        for (int q = 0; q < 9; q++) kopfB.putFloat((float) lu[q][0]).putFloat((float) lu[q][1]).putFloat((float) lu[q][2]).putFloat(0);
        kopfB.flip();
    }

    /** Zuletzt kopierte Zeilen des Bilds (nur das Band des Meniskus; 0 ganz unter Wasser). */
    public volatile int kopiert;

    /**
     * Seit L6b2 (Kosten: die ganze Kopie des Bilds kostete im Mittel 0,61 ms, 99 % bis 1,7 ms): die Zeilen [von, bis) des
     * Bilds, in denen der Meniskus liegt (|Tiefe| < {@link #MENISKUS}, zwei Zeilen Rand); null, wenn keine.
     */
    public int[] band(int bw, int bh) {
        double A = tiefeM, B = jeRechts, C = jeOben, m = MENISKUS;
        if (Double.isNaN(A)) return null;
        if (Math.abs(C) < 1e-6) return new int[] {0, bh};
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double xl : new double[] {-0.5 * breiteL, 0.5 * breiteL})
            for (double dv : new double[] {-m, m}) {
                double yl = (dv - A - B * xl) / C, zeile = (0.5 - yl / hoeheL) * bh;
                lo = Math.min(lo, zeile);
                hi = Math.max(hi, zeile);
            }
        int von = (int) Math.max(0, Math.floor(lo) - 2), bis = (int) Math.min(bh, Math.ceil(hi) + 2);
        return bis > von ? new int[] {von, bis} : null;
    }

    @Override public void zeichnen(Ablauf a, Bildziel ziel) {
        if (!nass()) { gezeichnet = false; return; }
        gezeichnet = true;
        kopfSchreiben(ziel.blick, ziel.breite, ziel.hoehe);
        a.durchgang("Unterwasser, Kopf", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB));
        if (kopie == null || kopie.breite != ziel.breite || kopie.hoehe != ziel.hoehe) {
            if (kopie != null) { g.ruhe(); kopie.close(); }
            kopie = new Bild(g, konto, "Unterwasser, Kopie", ziel.breite, ziel.hoehe, ziel.hdr.format);
        }
        Bild hdr = ziel.hdr, k = kopie;
        int[] band = band(ziel.breite, ziel.hoehe);
        kopiert = band == null ? 0 : band[1] - band[0];
        if (band != null) a.durchgang("Unterwasser, Kopie", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(hdr, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
                new Ablauf.Zugriff(k, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)}, c -> {
            try (MemoryStack st = MemoryStack.stackPush()) {
                VkImageCopy.Buffer kp = VkImageCopy.calloc(1, st);
                kp.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                kp.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                kp.get(0).srcOffset().set(0, band[0], 0);
                kp.get(0).dstOffset().set(0, band[0], 0);
                kp.get(0).extent().set(hdr.breite, band[1] - band[0], 1);
                vkCmdCopyImage(c, hdr.handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, k.handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, kp);
            }
        });
        Puffer hw = ziel.puffer("Himmel/Werte");
        Bild durch = ziel.geteilt("Himmel/Durchlaessigkeit"), ansicht = ziel.geteilt("Himmel/Ansicht");
        himmel = new Object[] {hw, durch, ansicht};
        long satz = z.satz(kopf, hw, linear.an(durch), linear.an(ansicht), licht, linear.an(k));
        a.zeichne("Unterwasser", new Bild[] {hdr}, null, null, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, kopf, hw, licht), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, k)),
                c -> z.zeichne(c, satz, null, 3, 1));
    }

    /**
     * Prüfstein 39: je Bildpunkt uv (0 … 1, y nach unten) die Tiefe der Linse unter der Fläche, ob der Strahl im Snellschen
     * Fenster liegt (1; 0 Totalreflexion; −1 nach unten), die Durchlässigkeit und die Richtung, auf der Karte, mit dem Stand von
     * {@link #setze} und dem Blick b. tiefe: Tiefe der Strahlen (m; ≤ 0: die der Linse). Braucht die Werte des Himmels hw und
     * seine Bilder (aus dem letzten Bild der Bühne).
     */
    public float[] probe(Blick b, int bw, int bh, float[] uv, double tiefe) {
        Object[] h = himmel;
        if (h == null) throw new IllegalStateException("Unterwasser: noch kein Bild gezeichnet (Himmel fehlt für die Probe)");
        return probe(b, bw, bh, uv, tiefe, (Puffer) h[0], (Bild) h[1], (Bild) h[2]);
    }

    /** Wie oben mit den Werten und Bildern des Himmels. */
    public float[] probe(Blick b, int bw, int bh, float[] uv, double tiefe, Puffer hw, Bild durch, Bild ansicht) {
        int m = uv.length / 2;
        kopfSchreiben(b, bw, bh);
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        try (Kernel kp = Kernel.lade(g, "/com/dan/render/water/unterwasserprobe.comp", 16, null, P, P, X, X, P, P, P);
             Puffer pr = Puffer.speicher(g, konto, "Probe Unterwasser, Orte", 8L * m);
             Puffer aus = Puffer.speicher(g, konto, "Probe Unterwasser, Aus", 32L * m)) {
            pr.schreibe(uv);
            Ablauf a = new Ablauf(g, g.grafik);
            a.durchgang("Unterwasser, Kopf", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                    c -> vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB));
            ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            p.putInt(0, m).putInt(4, Float.floatToRawIntBits((float) tiefe));
            a.rechne("Probe Unterwasser", kp, kp.satz(kopf, hw, linear.an(durch), linear.an(ansicht), licht, pr, aus), p, Kernel.gruppen(m, 64), 1, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopf, hw, licht, pr), Ablauf.textur(Ablauf.RECHNEN, durch, ansicht), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            return aus.floats(8 * m);
        }
    }

    @Override public void close() {
        z.close();
        linear.close();
        kopf.close();
        if (leer != null) leer.close();
        if (kopie != null) kopie.close();
    }
}
