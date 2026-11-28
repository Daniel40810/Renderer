package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.raum.Blick;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Clipmap des Seegangs als Teil des Bilds (Seegang Werkbuch, S3c): rechnet je Bild die Synthese um das Auge
 * ({@link SeegangKarte#bild(Ablauf, double, double)}, die fernen Stufen jedes zweite Bild) und legt sie für das Wasser
 * ab. Das Wasser ({@code render.water}) kennt dieses Package nicht (Schichtenvertrag); es liest die geteilten Puffer:
 * <ul>
 * <li>{@code Seegang/Netz}: der Kopf des Netzes, 160 Byte – ivec4 (Stufen, n, 1 = gesetzt, Breite des Übergangs ins
 * Fernfeld); vec4 der innere Bereich relativ zum Auge, in dem der Weg ohne Netz schweigt (x von, x bis, z von, z bis;
 * x Ost, z Süd); je Stufe vec4 (x, z der Stelle (0, 0) relativ zum Auge, d_L, 0). Die Stelle (i, j) liegt bei
 * x + i d_L, z − j d_L.</li>
 * <li>{@code Seegang/Versatz} (ξ_Ost, ξ_Nord, η, |Z'|), {@code Seegang/Normale} (n_Ost, n_Nord, n_oben, J),
 * {@code Seegang/Aufgeloest} (Σ k² E g², Σ E g²) je Stufe und Stelle, Index (L n + j) n + i.</li>
 * </ul>
 * Dazu in {@code z.werte} unter {@code Seegang/Netz}: Stufen und n (für die Zahl der Scheitel beim Zeichnen), seit S3c-5
 * dazu der innere Bereich in UTM ({@link #netzWerte}).
 * Reihenfolge im Rahmen: vor dem Wasser. {@link #zeit} ist die Modellzeit des Bilds (s, wie die Zeit der Karte).
 * <p>
 * Seit S4a (Seegang Werkbuch 35): Hängt ein {@link Schaumfeld} an ({@link #schaum}), rechnet es nach der Clipmap einen
 * Schritt mit dieser Karte und liegt unter {@link Schaumfeld#SCHAUM} für das Wasser. Ohne Feld wie bisher.
 * <p>
 * Seit S5b-2 und S5b-4: Nach der Clipmap suchen {@link Gischtsuche} die Walzen für die Gischt und {@link Fusssuche} die
 * Kämme am Fuß der Kanten für die Brandung, wenn sie anhängen.
 */
public final class Seegangbild implements Bildteil {
    public static final String VERSION = "0.1";
    public static final String NETZ = "Seegang/Netz", VERSATZ = "Seegang/Versatz", NORMALE = "Seegang/Normale", AUFGELOEST = "Seegang/Aufgeloest";
    /** Bytes des Kopfs. */
    public static final int KOPF = 16 + 16 + 8 * 16;

    public final SeegangKarte karte;
    private final Puffer kopf;
    /** Modellzeit des Bilds (s), in der Zeit der Karte. */
    public volatile double zeit;
    /** Wenn gesetzt, gilt ihre Zeit je Bild statt {@link #zeit} (die Bühne gibt die Zeit des Wassers, damit das Fernfeld passt). */
    public volatile java.util.function.DoubleSupplier zeitQuelle;
    /** Aus: nichts gerechnet, nichts abgelegt (das Wasser zeichnet wie bisher). */
    public volatile boolean an = true;
    /** Seit S4a: das Schaumfeld, das mit dieser Karte rechnet (die Anwendung hält eines über alle Flächen); null: keins. */
    public volatile Schaumfeld schaum;
    /** Seit S5b-2 (SF50 A): Suche der Orte für die Gischt aus den Walzen und die Gischt selbst (beide oder keins). */
    public volatile Gischtsuche suche;
    public volatile com.dan.core.szene.Kammgischt gischt;
    /**
     * Seit S5b-4 (Seegang Werkbuch 46): Suche der Kämme am Fuß der Kanten und die Folge, die die Brandung daraus nimmt
     * (beide oder keins). Je Bild geht das Ergebnis von vor {@link Fusssuche#RING} − 1 Bildern an die Folge, dann sucht
     * die Karte die Füße, die die Brandung zuletzt gemeldet hat.
     */
    public volatile Fusssuche fuss;
    public volatile com.dan.core.szene.Fusswellen fuesse;

    /**
     * Seit S6b-2 (Seegang Werkbuch 67): etwas, das je Bild nach der Clipmap mit dieser Karte rechnet, im selben Ablauf –
     * etwa der Auflauf ({@code lauf.Auflauf}: sein Antrieb liest die Synthese). Dieses Package kennt es nicht.
     */
    public interface Anhang {
        /** Nach der Clipmap: Ablauf des Bilds, Bildziel, diese Karte (Zeit schon gesetzt), Modellzeit des Bilds. */
        void vorbereiten(Ablauf a, Bildziel z, SeegangKarte karte, double zeit);
    }

    /**
     * Seit S7b-1 (Seegang Werkbuch 88): das Wasser unter den schwimmenden Körpern (beide oder keins). Je Bild geht das
     * Ergebnis von vor {@link Koerpersuche#RING} − 1 Bildern an die Wasserpunkte, dann rechnet die Karte die neueste Anfrage.
     */
    public volatile Koerpersuche koerper;
    public volatile com.dan.core.szene.Wasserpunkte wasserpunkte;

    /**
     * Seit S7d (Seegang Werkbuch 89): das Kielwasser der fahrenden Schiffe (beide oder keins). Je Bild rechnet die Karte die
     * Kacheln vor der Clipmap; die Stufen und die Körper nehmen Höhe und Schaum dazu.
     */
    public volatile Kielwasserkarte kielkarte;
    public volatile com.dan.core.szene.Kielwasser kielwasser;

    /** Seit S6b-2: der Anhang dieser Fläche (null: keiner). */
    public volatile Anhang anhang;
    /** Zuletzt: Auge (Ost, Nord) und ob die fernen Stufen gerechnet wurden. */
    private double ex = Double.NaN, ey = Double.NaN;

    public Seegangbild(Geraet g, Konto konto, SeegangKarte karte) {
        this.karte = karte;
        kopf = Puffer.speicher(g, konto, "Seegang, Kopf des Netzes", KOPF);
    }

    @Override public String name() { return "Seegang"; }

    @Override public void vorbereiten(Ablauf a, Bildziel z) {
        z.puffer.remove(NETZ);
        z.werte.remove(NETZ);
        z.puffer.remove(Schaumfeld.SCHAUM);
        if (!an) return;
        Blick b = z.blick;
        ex = b.ost;
        ey = b.nord;
        if (Math.hypot(ex - karte.bx, ey - karte.by) > BEZUG_WEITE) {
            karte.bezug(Math.floor(ex / 64) * 64, Math.floor(ey / 64) * 64);
            karte.neuerLauf();                                               // alle Stufen neu: ihre Mitten hängen an B
        }
        java.util.function.DoubleSupplier zq = zeitQuelle;
        if (zq != null) zeit = zq.getAsDouble();
        // seit S3d-2: nach einem Sprung der Zeit alle Stufen in diesem Bild, sonst mischt es die fernen (jedes zweite Bild,
        // SF23 A) aus der alten Zeit mit den nahen aus der neuen (Befund L2n: +1,6 % Helligkeit für ein Bild)
        if (!Double.isNaN(zLetzt) && Math.abs(zeit - zLetzt) > ZEIT_SPRUNG_S) karte.neuerLauf();
        zLetzt = zeit;
        karte.zeit(a, zeit);
        Kielwasserkarte kk = kielkarte;                                         // seit S7d: das Kielwasser vor der Clipmap
        com.dan.core.szene.Kielwasser kw = kielwasser;
        if (kk != null && kw != null) kk.rechne(a, kw, karte.bx, karte.by, ex, ey, b.richtung);   // seit S7d5 die nahe Kachel vor dem Auge
        karte.kiel = kk != null && kw != null && kk.an() ? kk : null;
        karte.bild(a, ex, ey);
        // seit S4a: das Schaumfeld nach der Clipmap (es liest Versatz, Normale und Kamm dieses Bilds)
        Schaumfeld sf = schaum;
        if (sf != null && sf.an) {
            sf.schritt(a, karte, zeit, ex, ey);
            z.puffer.put(Schaumfeld.SCHAUM, sf.puffer());
        }
        // seit S5b-2: die Orte von vor Gischtsuche.RING − 1 Bildern an die Gischt, dann die neue Suche
        Gischtsuche gs = suche;
        com.dan.core.szene.Kammgischt kg = gischt;
        if (gs != null && kg != null && kg.an) {
            int[] m = new int[1];
            float[] o = gs.lies(m);
            kg.orte(zeit, o, m[0]);
            gs.suche(a, karte);
        }
        // seit S5b-4: die Kämme am Fuß (Ergebnis von vor Fusssuche.RING − 1 Bildern an die Folge, dann die neue Suche)
        Fusssuche fs = fuss;
        com.dan.core.szene.Fusswellen fw = fuesse;
        if (fs != null && fw != null) {
            fs.lies(fw);
            fs.suche(a, karte, zeit, fw.anfrage());
        }
        Koerpersuche ks = koerper;                                              // seit S7b-1: das Wasser unter den Körpern
        com.dan.core.szene.Wasserpunkte wp = wasserpunkte;
        if (ks != null && wp != null) {
            ks.lies(wp);
            ks.suche(a, karte, zeit, wp.anfrage(), karte.kiel, true);
        }
        Anhang anh = anhang;                                                    // seit S6b-2: der Auflauf
        if (anh != null) anh.vorbereiten(a, z, karte, zeit);
        ByteBuffer k = kopfBytes(ex, ey);
        a.durchgang("Seegang, Kopf des Netzes", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, kopf.handle, 0, k));
        z.puffer.put(NETZ, kopf);
        z.werte.put(NETZ, netzWerte(ex, ey));
        z.puffer.put(VERSATZ, karte.versatzPuffer());
        z.puffer.put(NORMALE, karte.normalePuffer());
        z.puffer.put(AUFGELOEST, karte.aufgeloestPuffer());
    }

    /**
     * Die Werte unter {@code Seegang/Netz} in {@code z.werte}: Stufen, n und seit S3c-5 (SF28 A) der innere Bereich, in
     * dem das Netz zeichnet, in UTM: Ost von, Ost bis, Nord von, Nord bis (wie im Kopf, eine Stelle vom Rand der äußersten
     * Stufe weg).
     */
    public double[] netzWerte(double ex, double ey) {
        int st = karte.stufen, n = karte.n;
        double[] m7 = karte.mitte(st - 1);
        double d7 = karte.abstand(st - 1), x0 = m7[0] - n / 2.0 * d7, y0 = m7[1] - n / 2.0 * d7;
        return new double[] {st, n, x0 + d7, x0 + (n - 2) * d7, y0 + d7, y0 + (n - 2) * d7};
    }

    /** Seit S3d-2: ein Schritt der Zeit von mehr als so viel (s) von Bild zu Bild gilt als Sprung [G]. */
    public static final double ZEIT_SPRUNG_S = 600;
    private double zLetzt = Double.NaN;

    /** Weiter als so weit (m) vom Auge setzt das Bild B neu, damit float relativ zu B hält [G]. */
    public static final double BEZUG_WEITE = 2000;

    /** Der Kopf des Netzes für ein Auge (ex, ey) (Inhalt siehe Klasse). */
    public ByteBuffer kopfBytes(double ex, double ey) {
        int st = karte.stufen, n = karte.n;
        ByteBuffer k = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        k.putInt(st).putInt(n).putInt(1).putInt(karte.randFern);
        double[] m7 = karte.mitte(st - 1);
        double d7 = karte.abstand(st - 1), x0 = m7[0] - n / 2.0 * d7 - ex, y0 = m7[1] - n / 2.0 * d7 - ey;
        // innen: eine Stelle vom Rand der äußersten Stufe weg (dort überlappen Netz und der Weg ohne Netz)
        k.putFloat((float) (x0 + d7)).putFloat((float) (x0 + (n - 2) * d7)).putFloat((float) (-(y0 + (n - 2) * d7))).putFloat((float) (-(y0 + d7)));
        for (int l = 0; l < 8; l++) {
            if (l < st) {
                double[] m = karte.mitte(l);
                double dl = karte.abstand(l);
                k.putFloat((float) (m[0] - n / 2.0 * dl - ex)).putFloat((float) (-(m[1] - n / 2.0 * dl - ey))).putFloat((float) dl).putFloat(0);
            } else k.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
        }
        k.flip();
        return k;
    }

    @Override public void zeichnen(Ablauf a, Bildziel z) { }

    /** Modellzeit aus einem Zeitpunkt, wenn die Karte in Sekunden ab t0 rechnet. */
    public static double sekunden(Instant t, Instant t0) { return (t.toEpochMilli() - t0.toEpochMilli()) / 1000.0; }

    @Override public void close() { kopf.close(); }
}
