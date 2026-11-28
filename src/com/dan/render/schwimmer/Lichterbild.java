package com.dan.render.schwimmer;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Lichter der Schwimmer im Bild (Seegang Werkbuch, S7e1, Datei 99; SF148 A): Positionslichter nach KVR,
 * Tonnenfeuer nach Kennung, Decksbeleuchtung. Je Licht ein Lichtpunkt mit Hof und sein Spiegelbild im Wasser
 * ({@code feuer.vert/.frag}).
 * <ul>
 * <li><b>Lichter</b> ({@link #lichter}) setzt die Anwendung je Bild: je Licht {@link #JE} double – Ost, Nord, Höhe (m, UTM
 * und NHN), Lichtstärke (cd; die Kennung schon eingerechnet), Farbe R G B (linear, größter Kanal 1), Mitte des Sektors
 * (Azimut von Nord über Ost, rad), halbe Breite des Sektors (rad; ≥ π: rundum), Höhe des Wassers am Licht (m NHN; NaN:
 * kein Spiegelbild), frei. Das Bild rechnet je Bild, ob das Auge im Sektor steht (Rand {@link #SEKTOR_RAND} [G]).</li>
 * <li><b>Folge:</b> als letzter Teil nach Himmel und Kimmung. Der Himmel schreibt jeden Bildpunkt ohne Tiefe neu; davor
 * gezeichnete Lichter vor dem Himmel gingen verloren. Die Lichter testen die Tiefe (Rümpfe, Land, Wasser davor verdecken)
 * und schreiben keine. Darum legt der Himmel keine Luftperspektive über sie; das Licht verliert auf dem Weg nach
 * exp(−β d) mit β = 3,912 / Sicht (Koschmieder) [G].</li>
 * <li><b>Nacht:</b> an, wenn die Sonne unter 2° steht, ganz ab −2° [G].</li>
 * </ul>
 */
public final class Lichterbild implements Bildteil {
    public static final String VERSION = "0.1";
    /** double je Licht in {@link #lichter}. */
    public static final int JE = 11;
    /** Höchstens so viele Lichter [G]. */
    public static final int MAX = 512;
    /** Weicher Rand eines Sektors (rad) [G]; die KVR verlangen, dass das Licht innerhalb von 1–3° außerhalb des Sektors erlischt. */
    public static final double SEKTOR_RAND = Math.toRadians(2);
    static final int KOPF = 64 + 4 * 16;

    public volatile boolean an = true;
    /** Erdkrümmung mit Refraktion wie das Gelände; 0: ohne. */
    public volatile double kruemmung = 6_371_000 / (1 - 0.13);
    /** Sicht (m) für die Auslöschung der Luft [G]. */
    public volatile double sicht = 30_000;
    /** Anteil des Hofs am Licht [G]. */
    public volatile double hof = 0.005;
    /** Der Streif des Spiegelbilds ist so viel mal höher als breit [G]. */
    public volatile double streif = 8;
    /** Das Spiegelbild rückt um diesen Anteil seines Abstands zum Auge, damit die Tiefe des Wassers es nicht verdeckt [G]. */
    public volatile double vorWasser = 0.02;
    /** Zuletzt gezeichnete Lichter (mit Lichtstärke über 0 im Sektor; für Prüfungen). */
    public volatile int gezeichnet;

    private final Zeichner z;
    private final Puffer kopf, lichter;
    private final ByteBuffer kopfB, lichterB;
    private volatile double[] werte;

    public Lichterbild(Geraet g, Konto konto) {
        kopf = Puffer.speicher(g, konto, "Lichter, Kopf", KOPF);
        lichter = Puffer.speicher(g, konto, "Lichter", 32L * MAX);
        kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        lichterB = ByteBuffer.allocateDirect(32 * MAX).order(ByteOrder.nativeOrder());
        Kernel.Bindung P = Kernel.Bindung.PUFFER;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        art.mischen = true;
        art.tiefeSchreiben = false;
        String pf = "/com/dan/render/schwimmer/";
        z = Zeichner.lade(g, pf + "feuer.vert", pf + "feuer.frag", 0, null, art, P, P, P);
    }

    @Override public String name() { return "Lichter"; }

    /** Die Lichter für das nächste Bild (je Licht {@link #JE} double, siehe oben); null: keine. */
    public void lichter(double[] l) { werte = l; }

    /** Die Lichter, mit denen das Bild zeichnet (für Prüfungen; null: keine). */
    public double[] lichter() { return werte; }

    /**
     * Lichtstärke zum Auge (Anteil 0 … 1) eines Lichts mit Sektor (Mitte, halbe Breite in rad; Azimut von Nord über Ost),
     * gesehen vom Auge (ost, nord) aus; weicher Rand {@link #SEKTOR_RAND}.
     */
    public static double sektor(double lo, double ln, double mitte, double halb, double ost, double nord) {
        if (halb >= Math.PI) return 1;
        double az = Math.atan2(ost - lo, nord - ln);
        double d = Math.abs(Math.IEEEremainder(az - mitte, 2 * Math.PI));
        return Math.max(0, Math.min(1, (halb - d) / SEKTOR_RAND + 0.5));
    }

    @Override public void zeichnen(Ablauf a, Bildziel ziel) {
        double[] l = werte;
        if (!an || l == null) { gezeichnet = 0; return; }
        Blick b = ziel.blick;
        int n = Math.min(MAX, l.length / JE), m = 0;
        lichterB.clear();
        for (int i = 0; i < n; i++) {
            int o = JE * i;
            double I = l[o + 3];
            if (!(I > 0) || Double.isNaN(l[o])) continue;
            double f = sektor(l[o], l[o + 1], l[o + 7], l[o + 8], b.ost, b.nord);
            if (f <= 0) continue;
            double pegel = l[o + 9];
            lichterB.putFloat((float) (l[o] - b.ost)).putFloat((float) (l[o + 2] - b.hoehe)).putFloat((float) (b.nord - l[o + 1])).putFloat((float) (I * f));
            lichterB.putFloat((float) l[o + 4]).putFloat((float) l[o + 5]).putFloat((float) l[o + 6]).putFloat(Double.isNaN(pegel) ? 1e9f : (float) (pegel - b.hoehe));
            m++;
        }
        lichterB.flip();
        gezeichnet = m;
        if (m == 0) return;
        double[][] bas = b.basis();
        kopfB.clear();
        for (float f : b.ansichtProjektion()) kopfB.putFloat(f);
        double R = kruemmung;
        kopfB.putFloat(0).putFloat(0).putFloat(0).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0);
        kopfB.putFloat((float) bas[0][0]).putFloat((float) bas[0][1]).putFloat((float) bas[0][2]).putFloat(b.hoehe_px);
        kopfB.putFloat((float) bas[1][0]).putFloat((float) bas[1][1]).putFloat((float) bas[1][2]).putFloat((float) (Math.toRadians(b.bildwinkel) / b.hoehe_px));
        kopfB.putFloat((float) (3.912 / Math.max(100, sicht))).putFloat((float) hof).putFloat((float) streif).putFloat((float) vorWasser);
        kopfB.flip();
        a.durchgang("Lichter, Kopf", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(lichter, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
            vkCmdUpdateBuffer(c, lichter.handle, 0, lichterB);
        });
        Puffer hw = ziel.puffer("Himmel/Werte");
        long satz = z.satz(kopf, lichter, hw);
        int sn = 12 * m;
        a.zeichne("Lichter", new Bild[] {ziel.hdr}, null, ziel.tiefe, null, Ablauf.liest(Ablauf.ZEICHNEN, kopf, lichter, hw),
                c -> z.zeichne(c, satz, null, sn, 1, 0));
    }

    @Override public void close() {
        z.close();
        kopf.close();
        lichter.close();
    }
}
