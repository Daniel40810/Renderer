package com.dan.render.brandung;

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
import com.dan.core.szene.Brandung;
import com.dan.core.szene.Kanten;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Brandung an den Böschungen (seit P13c, Werkbuch HalligHooge Teil A, 12.14; F160 A, F162 A): Brecher, Auflauf und
 * Gischt aus {@link Brandung} (Kern) an den {@link Kanten} des Gebiets ({@code brandung/kanten.txt}, Schritt
 * {@code brandung} der Aufbereitung). Je Bild, alles in {@link #zeichnen}:
 * <ol>
 * <li>Der Kern rechnet aus der {@link Quelle} (Wasserstand, Seegang, Wind, Zeit) die Netze und die Bündel der Gischt
 * (auf dem Prozessor; die Zeit steht in {@link #letzteMs}).</li>
 * <li>Licht des Himmels ({@code licht.comp}, wie render.bau): Himmel und Boden als 9 Glieder.</li>
 * <li>Brecher und Wasserfilm ({@code brecher.vert/.frag}) in HDR und Tiefe, dann die Tropfen ({@code gischt.vert/.frag})
 * gemischt, ohne die Tiefe zu schreiben.</li>
 * </ol>
 * Steht im Rahmen nach dem Wasser (dessen Tiefe verdeckt die Brecher unter der Fläche). Aus ({@link #an} = false) oder
 * ohne Quelle zeichnet er nichts; das Bild ist dann wie ohne ihn.
 */
public final class Brandungsbild implements Bildteil {
    public static final String VERSION = "0.1";
    static final int KOPF = 64 + 5 * 16, RING = 3;

    /** Was die Brandung je Bild braucht; lauf setzt sie aus dem Wasser (render.water kennt dieser Teil nicht). */
    public interface Quelle {
        /** Modellzeit des Bilds (s). */
        double zeit();
        /** Wasserstand und Seegang dieses Bilds. */
        Brandung.Wasser wasser();
        /** U₁₀ Ost, Nord (m/s). */
        double[] wind();
    }

    public final Kanten kanten;
    public final Brandung brandung;
    public volatile boolean an = !"aus".equals(System.getProperty("lauf.brandung"));
    public volatile Quelle quelle;
    /** Erdkrümmung mit Refraktion wie das Gelände; 0: ohne. */
    public volatile double kruemmung = 6_371_000 / (1 - 0.13);
    /** Albedo des Schaums (wie Seegangsoptik.ALBEDO) und des trüben Wassers [G]. */
    public volatile double albedoSchaum = 0.6, albedoWasser = 0.04;
    /** Zuletzt: Scheitel, Bündel, Rechenzeit des Kerns (ms). */
    public volatile int letzteScheitel, letzteBuendel;
    /** Seit S5b-2: die Gischt aus den Walzen ({@code render.seegang.Gischtsuche} findet die Orte), oder null. */
    public volatile com.dan.core.szene.Kammgischt kammgischt;
    /** Seit S5b-4: die Kämme der Fläche am Fuß der Kanten ({@code render.seegang.Fusssuche} findet sie), oder null. */
    public volatile com.dan.core.szene.Fusswellen fuesse;
    public volatile double letzteMs;

    private final Geraet g;
    private final Konto konto;
    private final Zeichner zBrecher, zGischt;
    private final Kernel kLicht;
    private final Puffer kopf, licht;
    private final Puffer[] netz = new Puffer[RING], buendel = new Puffer[RING];
    private final long[] groesseNetz = new long[RING], groesseBuendel = new long[RING];
    private final Abtaster punkt;
    private final ByteBuffer kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
    private int ring;

    /** Aus einem Gebiet ({@code brandung/kanten.txt}); null, wenn es keine Kanten hat. */
    public static Brandungsbild aus(Geraet g, Konto konto, File gebiet) throws IOException {
        Kanten k = Kanten.lies(gebiet);
        if (k == null || k.kanten.isEmpty()) return null;
        double e = 0, n = 0;
        for (Kanten.Kante x : k.kanten) { e += x.ost(); n += x.nord(); }
        return new Brandungsbild(g, konto, k, Math.round(e / k.kanten.size()), Math.round(n / k.kanten.size()));
    }

    public Brandungsbild(Geraet g, Konto konto, Kanten kanten, double e0, double n0) {
        this.g = g;
        this.konto = konto;
        this.kanten = kanten;
        this.brandung = new Brandung(e0, n0);
        String p = "/com/dan/render/brandung/";
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        Zeichner.Art art = new Zeichner.Art();
        art.farben = new int[] {Bild.RGBA16F};
        zBrecher = Zeichner.lade(g, p + "brecher.vert", p + "brecher.frag", 0, null, art, P, P, P, X, P, X, P);
        Zeichner.Art ag = new Zeichner.Art();
        ag.farben = new int[] {Bild.RGBA16F};
        ag.mischen = true;
        ag.tiefeSchreiben = false;
        zGischt = Zeichner.lade(g, p + "gischt.vert", p + "gischt.frag", 0, null, ag, P, P, P, X, P, X, P);
        kLicht = Kernel.lade(g, p + "licht.comp", 0, null, P, P, P, X, P, X, P);
        kopf = Puffer.speicher(g, konto, "Brandung, Kopf", KOPF);
        licht = Puffer.speicher(g, konto, "Brandung, Himmelslicht", 9 * 16);
        for (int i = 0; i < RING; i++) {
            groesseNetz[i] = 4L << 20;
            groesseBuendel[i] = 64L << 10;
            netz[i] = Puffer.geteilt(g, konto, "Brandung, Netz " + i, groesseNetz[i]);
            buendel[i] = Puffer.geteilt(g, konto, "Brandung, Bündel " + i, groesseBuendel[i]);
        }
        punkt = new Abtaster(g, false, false);
    }

    @Override public String name() { return "Brandung"; }

    /** Lage des Auges im Raum der Brandung (x = Ost − e0, y = Höhe, z = n0 − Nord), in double. */
    public double[] auge(Blick b) { return new double[] {b.ost - brandung.e0, b.hoehe, brandung.n0 - b.nord}; }

    @Override public void zeichnen(Ablauf a, Bildziel z) {
        Quelle q = quelle;
        if (!an || q == null) { letzteScheitel = letzteBuendel = 0; return; }
        Blick b = z.blick;
        double[] w = q.wind();
        brandung.tropfen = 48;
        // Blickfeld: halber waagerechter Winkel plus 15° [G]
        double rr = Math.toRadians(b.richtung), halb = Math.atan(Math.tan(Math.toRadians(b.bildwinkel) / 2) * b.breite / Math.max(1, b.hoehe_px));
        brandung.blickOst = Math.sin(rr);
        brandung.blickNord = Math.cos(rr);
        brandung.blickCos = Math.cos(Math.min(Math.PI, halb + Math.toRadians(15)));
        // seit S3c-5 (SF28 A): wo das Wasser das Netz zeichnet, keine Brecher und kein Wasserfilm aus P13c (S5/S6 bringen sie
        // neu aus der Fläche); die Gischt bleibt. Schlüssel "Wasser/Netz" wie render.water.Wasserbild.NETZ_GEZEICHNET.
        double[] wn = z.werte.get("Wasser/Netz");
        brandung.netzBereich = wn != null && wn.length >= 6 ? new double[] {wn[2], wn[3], wn[4], wn[5]} : null;
        brandung.fuesse = fuesse;                                          // seit S5b-4 (SF51 A): im Netz die Kämme der Fläche
        brandung.rechne(kanten, q.wasser(), q.zeit(), b.ost, b.nord, w[0], w[1]);
        // seit S5b-2 (SF50 A): die Gischt aus den Walzen der Fläche dazu
        com.dan.core.szene.Kammgischt kg = kammgischt;
        if (kg != null && kg.an) brandung.anhaengen(kg.buendel(q.zeit(), brandung.e0, brandung.n0, q.wasser(), w[0], w[1]));
        letzteMs = brandung.ms;
        letzteScheitel = brandung.scheitel;
        letzteBuendel = brandung.buendelZahl;
        if (brandung.scheitel == 0 && brandung.buendelZahl == 0) return;
        int r = ring++ % RING;
        long bn = 4L * Brandung.SCHEITEL * brandung.scheitel, bb = 4L * Brandung.BUENDEL * brandung.buendelZahl;
        if (bn > groesseNetz[r] || bb > groesseBuendel[r]) {
            g.ruhe();
            if (bn > groesseNetz[r]) {
                netz[r].close();
                groesseNetz[r] = Math.max(bn, 2 * groesseNetz[r]);
                netz[r] = Puffer.geteilt(g, konto, "Brandung, Netz " + r, groesseNetz[r]);
            }
            if (bb > groesseBuendel[r]) {
                buendel[r].close();
                groesseBuendel[r] = Math.max(bb, 2 * groesseBuendel[r]);
                buendel[r] = Puffer.geteilt(g, konto, "Brandung, Bündel " + r, groesseBuendel[r]);
            }
        }
        schreibe(netz[r], brandung.netz, Brandung.SCHEITEL * brandung.scheitel);
        schreibe(buendel[r], brandung.buendel, Brandung.BUENDEL * brandung.buendelZahl);
        // ---- Kopf
        double[] auge = auge(b);
        double[][] bas = b.basis();
        kopfB.clear();
        for (float f : b.ansichtProjektion()) kopfB.putFloat(f);
        float hx = (float) auge[0], hy = (float) auge[1], hz = (float) auge[2];
        double R = kruemmung;
        kopfB.putFloat(hx).putFloat(hy).putFloat(hz).putFloat(R > 0 ? (float) (1 / (2 * R)) : 0);
        kopfB.putFloat((float) (auge[0] - hx)).putFloat((float) (auge[1] - hy)).putFloat((float) (auge[2] - hz)).putFloat((float) b.hoehe);
        kopfB.putFloat((float) albedoSchaum).putFloat((float) albedoWasser).putFloat(brandung.tropfen).putFloat(0);
        kopfB.putFloat((float) bas[0][0]).putFloat((float) bas[0][1]).putFloat((float) bas[0][2]).putFloat(b.hoehe_px);
        kopfB.putFloat((float) bas[1][0]).putFloat((float) bas[1][1]).putFloat((float) bas[1][2]).putFloat((float) (Math.toRadians(b.bildwinkel) / b.hoehe_px));
        kopfB.flip();
        a.durchgang("Brandung, Kopf", new Ablauf.Zugriff[] {new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB));
        Bild ansicht = z.geteilt("Himmel/Ansicht"), durch = z.geteilt("Himmel/Durchlaessigkeit");
        Puffer hw = z.puffer("Himmel/Werte");
        Puffer pn = netz[r], pb = buendel[r];
        a.rechne("Brandung, Himmelslicht", kLicht, kLicht.satz(pn, kopf, hw, punkt.an(durch), licht, punkt.an(ansicht), pb), null, 1, 1, 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, hw), Ablauf.textur(Ablauf.RECHNEN, ansicht, durch), Ablauf.schreibt(licht)));
        int sn = brandung.scheitel, gn = brandung.buendelZahl * brandung.tropfen * 6;
        if (sn > 0) {
            long satz = zBrecher.satz(pn, kopf, hw, punkt.an(durch), licht, punkt.an(ansicht), pb);
            a.zeichne("Brandung, Brecher", new Bild[] {z.hdr}, null, z.tiefe, null,
                    Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, pn, kopf, hw, licht, pb), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht)),
                    c -> zBrecher.zeichne(c, satz, null, sn, 1, 0));
        }
        if (gn > 0) {
            long satz = zGischt.satz(pn, kopf, hw, punkt.an(durch), licht, punkt.an(ansicht), pb);
            a.zeichne("Brandung, Gischt", new Bild[] {z.hdr}, null, z.tiefe, null,
                    Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, pn, kopf, hw, licht, pb), Ablauf.textur(Ablauf.ZEICHNEN, durch, ansicht)),
                    c -> zGischt.zeichne(c, satz, null, gn, 1, 0));
        }
    }

    private static void schreibe(Puffer p, float[] werte, int n) {
        if (n <= 0) return;
        ByteBuffer b = memAlloc(4 * n);
        try {
            b.asFloatBuffer().put(werte, 0, n);
            p.schreibe(b, 0);
        } finally {
            memFree(b);
        }
    }

    @Override public void close() {
        zBrecher.close();
        zGischt.close();
        kLicht.close();
        punkt.close();
        kopf.close();
        licht.close();
        for (int i = 0; i < RING; i++) {
            netz[i].close();
            buendel[i].close();
        }
    }
}
