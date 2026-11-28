package com.dan.render.schwimmer;

import com.dan.core.gpu.Abtaster;
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
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkViewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Die schwimmenden Körper im Bild (Seegang Werkbuch, S7b-3, Datei 88; SF108 A): Netze im Raum der Körper, je Körper eine
 * Lage (Körper → Welt), im selben Licht wie Häuser und Gehölze und mit ihrem Schatten.
 * <ul>
 * <li><b>Netze</b> kommen als Zahlen ({@code physics.auftrieb.Schiffbau}, die Anwendung reicht sie herein): je Scheitel 12
 * float (Lage im Körper und Material, Normale und Nummer des Körpers, u v w), Materialien zu 8 float.</li>
 * <li><b>Lagen</b> ({@link #lagen}) setzt die Anwendung je Bild: je Körper 16 double, die Abbildung vom Körper in die Welt
 * (Spalten; x Ost, y Nord, z oben in m, UTM und NHN). Das Bild rechnet sie in double relativ zum Auge in den Raum von
 * render.bau um (x Ost, y oben, z Süd); die Netze bleiben auf der Karte. Ein Körper mit NaN in der Lage fehlt.</li>
 * <li><b>Schatten:</b> die Körper zeichnen sich in die Karte, die render.bau unter „Bau/Schatten“ ablegt (in der Folge
 * nach den Häusern), und empfangen ihn; ohne Häuser kein Schatten.</li>
 * </ul>
 * In der Folge des Rahmens vor dem Gelände und dem Wasser: Das Wasser verdeckt den Rumpf unter der Fläche über die Tiefe.
 */
public final class Schwimmerbild implements Bildteil {
    public static final String VERSION = "0.1";
    /** Bytes des Kopfs: vp, augeHi, augeLo. */
    static final int KOPF = 64 + 2 * 16;

    public volatile boolean an = true;
    /** Erdkrümmung mit Refraktion wie das Gelände; 0: ohne. */
    public volatile double kruemmung = 6_371_000 / (1 - 0.13);
    /** Zahl der Körper und Scheitel. */
    public final int koerper, scheitel;

    private final Geraet g;
    private final Zeichner zBild, zSchatten;
    private final Kernel kLicht;
    private final Puffer pScheitel, pMaterial, kopf, pLagen, licht, ohneBau;
    private final Bild leer;
    private final Abtaster punkt;
    private final ByteBuffer kopfB, lagenB;
    private volatile double[] lagen;
    /** Zuletzt gezeichnete Körper (für Prüfungen). */
    public volatile int gezeichnet;

    /**
     * @param netz      Scheitel aller Körper (je 12 float, Nummer des Körpers in Wert 7)
     * @param material  Materialien (je 8 float)
     * @param koerper   Zahl der Körper
     */
    public Schwimmerbild(Geraet g, Konto konto, float[] netz, float[] material, int koerper) {
        this.g = g;
        this.koerper = koerper;
        this.scheitel = netz.length / 12;
        pScheitel = Puffer.speicher(g, konto, "Schwimmer, Scheitel", 4L * Math.max(12, netz.length));
        pScheitel.schreibe(netz);
        pMaterial = Puffer.speicher(g, konto, "Schwimmer, Material", 4L * Math.max(8, material.length));
        pMaterial.schreibe(material);
        kopf = Puffer.speicher(g, konto, "Schwimmer, Kopf", KOPF);
        pLagen = Puffer.speicher(g, konto, "Schwimmer, Lagen", 64L * Math.max(1, koerper));
        licht = Puffer.speicher(g, konto, "Schwimmer, Himmelslicht", 9 * 16);
        ohneBau = Puffer.speicher(g, konto, "Schwimmer, ohne Schatten", 256);
        ohneBau.schreibe(new float[64]);
        leer = new Bild(g, konto, "Schwimmer, ohne Schatten", 1, 1, Bild.R32F);
        leer.schreibe(new float[] {1});
        kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        lagenB = ByteBuffer.allocateDirect(64 * Math.max(1, koerper)).order(ByteOrder.nativeOrder());
        String pf = "/com/dan/render/schwimmer/";
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        zBild = Zeichner.lade(g, pf + "schwimmer.vert", pf + "schwimmer.frag", 0, null, art, P, P, P, P, P, P, X, P, X, X);
        Zeichner.Art st = new Zeichner.Art();
        st.farben = new int[0];
        st.vergleich = VK_COMPARE_OP_LESS_OR_EQUAL;
        zSchatten = Zeichner.lade(g, pf + "schatten.vert", pf + "schatten.frag", 4, null, st, P, P, P, P, P);
        kLicht = Kernel.lade(g, pf + "licht.comp", 0, null, P, X, P);
        punkt = new Abtaster(g, false, false);
    }

    @Override public String name() { return "Schwimmer"; }

    /** Lagen der Körper (je 16 double, Spalten, Körper → Welt; NaN: fehlt); null: keiner. */
    public void lagen(double[] l) { lagen = l; }

    /** Die Lagen, mit denen das Bild zeichnet (für Prüfungen; null: keine). */
    public double[] lagen() { return lagen; }

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        double[] l = lagen;
        if (!an || l == null || koerper == 0) { gezeichnet = 0; return; }
        Blick b = z.blick;
        // ---- Lagen relativ zum Auge (double), in den Raum von render.bau: X = Ost, Y = oben, Z = Süd
        lagenB.clear();
        int n = 0;
        for (int k = 0; k < koerper; k++) {
            int o = 16 * k;
            boolean da = o + 15 < l.length && !Double.isNaN(l[o + 12]);
            if (!da) { for (int i = 0; i < 16; i++) lagenB.putFloat(0); continue; }
            n++;
            // Spalten der Welt-Matrix: c0 (vorn), c1 (Backbord), c2 (oben), t; Welt (E, N, U) → Raum (E, U, −N)
            for (int c = 0; c < 3; c++) lagenB.putFloat((float) l[o + 4 * c]).putFloat((float) l[o + 4 * c + 2]).putFloat((float) -l[o + 4 * c + 1]).putFloat(0);
            lagenB.putFloat((float) (l[o + 12] - b.ost)).putFloat((float) (l[o + 14] - b.hoehe)).putFloat((float) (b.nord - l[o + 13])).putFloat(1);
        }
        lagenB.flip();
        gezeichnet = n;
        if (n == 0) return;
        // ---- Kopf
        kopfB.clear();
        for (float f : b.ansichtProjektion()) kopfB.putFloat(f);
        double R = kruemmung;
        kopfB.putFloat(0).putFloat(0).putFloat(0).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0);
        kopfB.putFloat(0).putFloat(0).putFloat(0).putFloat((float) b.hoehe);
        kopfB.flip();
        a.durchgang("Schwimmer, Kopf", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(pLagen, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
            for (int off = 0; off < lagenB.limit(); off += 65536) {
                ByteBuffer teil = lagenB.duplicate();
                teil.position(off).limit(Math.min(lagenB.limit(), off + 65536));
                vkCmdUpdateBuffer(c, pLagen.handle, off, teil.slice().order(ByteOrder.nativeOrder()));
            }
        });
        Bild ansicht = z.geteilt("Himmel/Ansicht"), durch = z.geteilt("Himmel/Durchlaessigkeit");
        Puffer hw = z.puffer("Himmel/Werte");
        a.rechne("Schwimmer, Himmelslicht", kLicht, kLicht.satz(hw, punkt.an(ansicht), licht), null, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, hw), Ablauf.textur(Ablauf.RECHNEN, ansicht), Ablauf.schreibt(licht)));
        // ---- Schatten in die Karte von render.bau (nach den Häusern, vor Gelände und Wasser)
        Bild atlas = z.geteilt.get("Bau/Schatten");
        Puffer bauW = z.puffer.get("Bau/Schattenwerte");
        int sz = scheitel;
        if (atlas != null && bauW != null) {
            int N = atlas.hoehe, karten = Math.min(3, atlas.breite / Math.max(1, N));
            long satzS = zSchatten.satz(pScheitel, pMaterial, kopf, pLagen, bauW);
            ByteBuffer push = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            a.zeichne("Schwimmer, Schatten", new Bild[0], null, atlas, null, Ablauf.liest(Ablauf.ZEICHNEN, pScheitel, pMaterial, kopf, pLagen, bauW), c -> {
                try (MemoryStack s = stackPush()) {
                    for (int k = 0; k < karten; k++) {
                        vkCmdSetViewport(c, 0, VkViewport.calloc(1, s).x(k * N).y(0).width(N).height(N).minDepth(0).maxDepth(1));
                        VkRect2D.Buffer sc = VkRect2D.calloc(1, s);
                        sc.get(0).offset().set(k * N, 0);
                        sc.get(0).extent().set(N, N);
                        vkCmdSetScissor(c, 0, sc);
                        push.putInt(0, k);
                        zSchatten.zeichne(c, satzS, push, sz, 1, 0);
                    }
                }
            });
        }
        // ---- Körper
        Bild bauB = atlas != null && bauW != null ? atlas : leer;
        Puffer bw = atlas != null && bauW != null ? bauW : ohneBau;
        long satz = zBild.satz(pScheitel, pMaterial, kopf, pLagen, bw, hw, punkt.an(durch), licht, punkt.an(ansicht), punkt.an(bauB));
        a.zeichne("Schwimmer", new Bild[] {z.hdr}, null, z.tiefe, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, pScheitel, pMaterial, kopf, pLagen, bw, hw, licht), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht, bauB)),
                c -> zBild.zeichne(c, satz, null, sz, 1, 0));
    }

    @Override public void close() {
        zBild.close();
        zSchatten.close();
        kLicht.close();
        punkt.close();
        pScheitel.close();
        pMaterial.close();
        kopf.close();
        pLagen.close();
        licht.close();
        ohneBau.close();
        leer.close();
    }
}
