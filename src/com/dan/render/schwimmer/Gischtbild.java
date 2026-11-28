package com.dan.render.schwimmer;

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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Gischt am Bug im Bild (Seegang Werkbuch, Datei 104, S8a, SF158 A): Tropfen und Nebel aus den Bündeln, die die
 * Anwendung bei jedem Bugschlag wirft ({@code lauf.Bugschwall}, Kern {@code com.dan.spray.Parcel}). Jeder Tropfen ist
 * eine Formel aus Bündel, Nummer und Alter ({@code gischt.glsl}); die Karte zeichnet Tausende, ohne sie zu speichern.
 * <ul>
 * <li><b>Bündel</b> ({@link #buendel}) setzt die Anwendung je Bild: je Bündel {@link #JE} double – Ort beim Wurf (Ost,
 * Höhe, −Nord; m, UTM und NHN), Alter zur Zeit des Bilds (s); Geschwindigkeit (Ost, oben, Süd), Lebensdauer; k,
 * Streuung des Orts, der Geschwindigkeit, Anteil der Schwerkraft; Saat, Größe, Höhe des Wassers (m NHN), Masse; quer
 * (x, z), nach außen (x, z); Wind (x, z), Dauer des Wurfs, Art (0 Spritzwasser, 1 Nebel). Das Bild rechnet sie auf das
 * Auge um (in double).</li>
 * <li><b>Umkreis:</b> Bündel weiter als {@link #UMKREIS} vom Auge zeichnet es nicht (R23) [G].</li>
 * <li><b>Folge:</b> nach den Lichtern, als letzter Teil vor der Linse; testet die Tiefe, schreibt keine.</li>
 * </ul>
 */
public final class Gischtbild implements Bildteil {
    public static final String VERSION = "0.1";
    /** double je Bündel in {@link #buendel}. */
    public static final int JE = 24;
    /** Höchstens so viele Bündel zugleich [G]. */
    public static final int MAX = 128;
    /** Tropfen je Spritzwasser und je Nebel [G, wie spray Quality.MEDIUM]. */
    public static final int TROPFEN = 400, NEBEL = 20;
    /** Umkreis um das Auge (m) [G]. */
    public static final double UMKREIS = 300;
    static final int KOPF = 64 + 4 * 16;

    public volatile boolean an = true;
    /** Erdkrümmung mit Refraktion wie das Gelände; 0: ohne. */
    public volatile double kruemmung = 6_371_000 / (1 - 0.13);
    /** Sicht (m) für die Auslöschung der Luft [G]. */
    public volatile double sicht = 30_000;
    /** Belichtungszeit der Streifen (s) [G]: ein halbes Bild bei 30 Bildern je s (Verschluss 180°). */
    public volatile double verschluss = 1.0 / 60;
    /** Deckkraft der Gischt (0 … 1). */
    public volatile double staerke = 1;
    /** Zuletzt gezeichnete Bündel (für Prüfungen und die Kosten). */
    public volatile int gezeichnet;

    private final Geraet g;
    private final Zeichner z;
    private final Puffer kopf, buendelP;
    private final ByteBuffer kopfB, buendelB;
    private final Abtaster punkt;
    private volatile double[] werte;

    public Gischtbild(Geraet g, Konto konto) {
        this.g = g;
        kopf = Puffer.speicher(g, konto, "Gischt, Kopf", KOPF);
        buendelP = Puffer.speicher(g, konto, "Gischt, Bündel", 96L * MAX);
        kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        buendelB = ByteBuffer.allocateDirect(96 * MAX).order(ByteOrder.nativeOrder());
        punkt = new Abtaster(g, false, false);
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        art.mischen = true;
        art.tiefeSchreiben = false;
        String pf = "/com/dan/render/schwimmer/";
        z = Zeichner.lade(g, pf + "gischt.vert", pf + "gischt.frag", 0, null, art, P, P, P, X, X);
    }

    @Override public String name() { return "Gischt"; }

    /** Die Bündel für das nächste Bild (je Bündel {@link #JE} double, siehe oben); null: keine. */
    public void buendel(double[] b) { werte = b; }

    public double[] buendel() { return werte; }

    /** Schreibt die Bündel relativ zum Auge (ost, hoehe, nord) in den Puffer; gibt ihre Zahl zurück. */
    static int packen(double[] l, double ost, double hoehe, double nord, ByteBuffer aus, double umkreis) {
        aus.clear();
        int n = l == null ? 0 : Math.min(MAX, l.length / JE), m = 0;
        for (int i = 0; i < n; i++) {
            int o = JE * i;
            if (Double.isNaN(l[o]) || Math.hypot(l[o] - ost, l[o + 2] + nord) > umkreis) continue;
            aus.putFloat((float) (l[o] - ost)).putFloat((float) (l[o + 1] - hoehe)).putFloat((float) (l[o + 2] + nord)).putFloat((float) l[o + 3]);
            for (int k = 4; k < 12; k++) aus.putFloat((float) l[o + k]);
            aus.putFloat(Float.intBitsToFloat((int) l[o + 12])).putFloat((float) l[o + 13]).putFloat((float) (l[o + 14] - hoehe)).putFloat((float) l[o + 15]);
            for (int k = 16; k < 24; k++) aus.putFloat((float) l[o + k]);
            m++;
        }
        aus.flip();
        return m;
    }

    @Override public void zeichnen(Ablauf a, Bildziel ziel) {
        double[] l = werte;
        if (!an || l == null) { gezeichnet = 0; return; }
        Blick b = ziel.blick;
        int m = packen(l, b.ost, b.hoehe, b.nord, buendelB, UMKREIS);
        gezeichnet = m;
        if (m == 0) return;
        kopfB.clear();
        for (float f : b.ansichtProjektion()) kopfB.putFloat(f);
        double R = kruemmung;
        double[][] bas = b.basis();
        kopfB.putFloat(0).putFloat(0).putFloat(0).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0);
        kopfB.putFloat((float) verschluss).putFloat(TROPFEN).putFloat(NEBEL).putFloat((float) (3.912 / Math.max(100, sicht)));
        kopfB.putFloat((float) b.hoehe).putFloat((float) Math.max(0, Math.min(1, staerke))).putFloat((float) Math.max(0.5, b.hoehe)).putFloat((float) (Math.toRadians(b.bildwinkel) / b.hoehe_px));
        kopfB.putFloat((float) bas[2][0]).putFloat((float) bas[2][1]).putFloat((float) bas[2][2]).putFloat((float) b.nah);
        kopfB.flip();
        a.durchgang("Gischt, Kopf", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(buendelP, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
            vkCmdUpdateBuffer(c, buendelP.handle, 0, buendelB);
        });
        Puffer hw = ziel.puffer("Himmel/Werte");
        Bild ansicht = ziel.geteilt("Himmel/Ansicht"), durch = ziel.geteilt("Himmel/Durchlaessigkeit");
        long satz = z.satz(kopf, buendelP, hw, punkt.an(durch), punkt.an(ansicht));
        int instanzen = m * TROPFEN;
        a.zeichne("Gischt", new Bild[] {ziel.hdr}, null, ziel.tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, kopf, buendelP, hw), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht)),
                c -> z.zeichne(c, satz, null, 6, instanzen, 0));
    }

    /**
     * Prüfstein 35: rechnet Tropfen (Bündel k, Nummer i je Probe) auf der Karte aus den Bündeln l (relativ zum Auge
     * ost, hoehe, nord); je Probe 8 float wie {@code gischtprobe.comp}.
     */
    public static float[] probe(Geraet g, Konto konto, double[] l, double ost, double hoehe, double nord, int[] proben) {
        int n = proben.length / 2;
        Kernel.Bindung P = Kernel.Bindung.PUFFER;
        ByteBuffer bb = ByteBuffer.allocateDirect(96 * MAX).order(ByteOrder.nativeOrder());
        int m = packen(l, ost, hoehe, nord, bb, Double.POSITIVE_INFINITY);
        try (Kernel kp = Kernel.lade(g, "/com/dan/render/schwimmer/gischtprobe.comp", 16, null, P, P, P, P);
             Puffer k = Puffer.speicher(g, konto, "Probe Gischt, Kopf", KOPF);
             Puffer bu = Puffer.speicher(g, konto, "Probe Gischt, Bündel", 96L * Math.max(1, m));
             Puffer pr = Puffer.speicher(g, konto, "Probe Gischt, Proben", 8L * n);
             Puffer aus = Puffer.speicher(g, konto, "Probe Gischt, Aus", 32L * n)) {
            float[] f = new float[24 * Math.max(1, m)];
            bb.asFloatBuffer().get(f, 0, 24 * m);
            bu.schreibe(f);
            k.schreibe(new float[KOPF / 4]);
            pr.schreibe(proben);
            com.dan.core.gpu.Ablauf a = new com.dan.core.gpu.Ablauf(g, g.grafik);
            ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            p.putInt(0, n);
            a.rechne("Probe Gischt", kp, kp.satz(k, bu, pr, aus), p, Kernel.gruppen(n, 64), 1, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, k, bu, pr), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            return aus.floats(8 * n);
        }
    }

    @Override public void close() {
        z.close();
        punkt.close();
        kopf.close();
        buendelP.close();
    }
}
