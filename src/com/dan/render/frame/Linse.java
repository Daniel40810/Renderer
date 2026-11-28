package com.dan.render.frame;

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
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Tropfen auf der Linse (Seegang Werkbuch, Datei 104, S8a, SF158 A): ein Bildteil, der das fertige HDR-Bild durch die
 * Tropfen auf der Scheibe vor der Kamera bricht ({@code linse.glsl}). Die Tropfen rechnet die Anwendung mit
 * {@code com.dan.wetglass} (Kugelkappen, Haften und Laufen); hier kommen sie als Zahlen an ({@link #tropfen}), wie
 * {@code Wetglass.Snapshot.drops}: je Tropfen x, y, Kontakthalbmesser a (m), Streckung, Laufrichtung x, y, 0, 0.
 * <ol>
 * <li>Höhenkarte ({@code linse_hoehe.comp}): je Zelle die höchste Kappe, {@link #NX} × {@link #NY} Zellen auf der
 * Scheibe.</li>
 * <li>Kopie des Bilds, dann ein Dreieck über das ganze Bild ({@code linse.frag}): durch die ebene Scheibe ins Wasser, an der
 * Kappe in die Luft, der neue Weg zeigt den Punkt der Szene (Brechzahl {@link #N_WASSER}).</li>
 * </ol>
 * Ohne Tropfen (und ohne Haut) tut der Teil nichts. Folge: als letzter Teil, nach der Gischt.
 */
public final class Linse implements Bildteil {
    public static final String VERSION = "0.1";
    /** Zellen der Höhenkarte [G]: bei 6,4 cm Breite 0,125 mm je Zelle. */
    public static final int NX = 512, NY = 288;
    /** Höchstens so viele Tropfen. */
    public static final int MAX = 1024;
    /** Brechzahl des Wassers [G]; Durchlässigkeit beider Flächen (Fresnel bei senkrechtem Einfall, zweimal) [G]. */
    public static final double N_WASSER = 1.334, DURCH = 0.96;
    static final int KOPF = 5 * 16;
    /**
     * Seit S9b-3 (Seegang Werkbuch 107): Kacheln der Höhenkarte (Zellen je Seite; wie local_size in linse_hoehe.comp), ihre
     * Zahl quer und hoch, und höchstens so viele Einträge in den Listen (ein Aufruf von vkCmdUpdateBuffer fasst 64 KiB).
     */
    public static final int KACHEL = 16, KX = NX / KACHEL, KY = NY / KACHEL, KLISTE = 16384 - 2 * KX * KY - 1;

    public volatile boolean an = true;
    /**
     * Seit S9b-3: die Höhenkarte je Kachel nur mit ihren Tropfen und das Bild nur in den Kacheln, in denen ein Tropfen bricht
     * (dasselbe Bild). Aus ({@code -Dlauf.linserechtecke=aus}): über alle Tropfen und das ganze Bild wie bis S9a.
     */
    public volatile boolean rechtecke = !"aus".equals(System.getProperty("lauf.linserechtecke"));
    /** Seit S9b-3: zuletzt gezeichnete Kacheln (−1: das ganze Bild). */
    public volatile int kacheln;
    /** Breite und Höhe der Scheibe (m); die Anwendung setzt sie mit den Tropfen. */
    public volatile double breite = 0.064, hoehe = 0.036;
    /** Zuletzt: Zahl der Tropfen, mit denen gezeichnet wurde. */
    public volatile int gezeichnet;
    /** Seit S8d: zuletzt gezeichnet (Tropfen oder eine Haut). */
    public volatile boolean aktiv;

    private final Geraet g;
    private final Konto konto;
    private final Kernel kHoehe;
    private final Zeichner z, zK;
    private final Puffer kopf, tropfenP, hoeheP, kachelP;
    private final ByteBuffer kopfB, tropfenB, kachelB;
    private final int[] kZahl = new int[KX * KY], kLage = new int[KX * KY + 1];
    private int[] kKasten = new int[4 * MAX];
    private int kAktiv;
    private final Abtaster linear;
    private Bild kopie;
    private volatile float[] werte;
    private volatile int zahl;
    private volatile double haut, linieTiefe = Double.NaN, linieRechts, linieOben;

    public Linse(Geraet g, Konto konto) {
        this.g = g;
        this.konto = konto;
        kopf = Puffer.speicher(g, konto, "Linse, Kopf", KOPF);
        tropfenP = Puffer.speicher(g, konto, "Linse, Tropfen", 32L * MAX);
        hoeheP = Puffer.speicher(g, konto, "Linse, Höhe", 16L * NX * NY);
        kachelP = Puffer.speicher(g, konto, "Linse, Kacheln", 4L * 16384);
        kachelB = ByteBuffer.allocateDirect(4 * 16384).order(ByteOrder.nativeOrder());
        kopfB = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        tropfenB = ByteBuffer.allocateDirect(32 * MAX).order(ByteOrder.nativeOrder());
        linear = new Abtaster(g, true, false);
        Kernel.Bindung P = Kernel.Bindung.PUFFER, X = Kernel.Bindung.TEXTUR;
        kHoehe = Kernel.lade(g, "/com/dan/render/frame/linse_hoehe.comp", 0, null, P, P, P, P);
        Zeichner.Art art = new Zeichner.Art().ohneTiefe();
        art.farben = new int[] {Bild.RGBA16F};
        z = Zeichner.lade(g, "/com/dan/render/frame/voll.vert", "/com/dan/render/frame/linse.frag", 0, null, art, P, P, P, X, P);
        zK = Zeichner.lade(g, "/com/dan/render/frame/linse_kachel.vert", "/com/dan/render/frame/linse.frag", 0, null, art, P, P, P, X, P);
    }

    @Override public String name() { return "Linse"; }

    /**
     * Die Tropfen für das nächste Bild (je Tropfen 8 float wie {@code Wetglass.Snapshot.drops}; Ursprung links unten, y
     * oben), ihre Zahl n und die Dicke einer Wasserhaut über der ganzen Scheibe (m; 0 ohne).
     */
    public void tropfen(float[] t, int n, double haut) {
        werte = t;
        zahl = t == null ? 0 : Math.min(MAX, Math.min(n, t.length / 8));
        this.haut = Math.max(0, haut);
    }

    /**
     * Seit S8d (Befund L6b2: Tropfen lagen auch unter der Wasserlinie): das Wasser vor der Scheibe als Tiefe ihrer Mitte unter
     * der Fläche (m; NaN: kein Wasser) und ihre Zunahme je m nach rechts und nach oben; wo die Scheibe unter Wasser liegt,
     * bricht kein Tropfen (das Glas ist dort ganz nass).
     */
    public void wasser(double tiefe, double jeRechts, double jeOben) {
        linieTiefe = tiefe;
        linieRechts = jeRechts;
        linieOben = jeOben;
    }

    /** Hat die Scheibe gerade Wasser? */
    public boolean nass() { return an && (zahl > 0 || haut > 0); }

    private void kopfSchreiben(double tanX, double tanY, int n, int bw, int bh) {
        kopfB.clear();
        kopfB.putFloat((float) breite).putFloat((float) hoehe).putFloat(NX).putFloat(NY);
        kopfB.putFloat((float) tanX).putFloat((float) tanY).putFloat(n).putFloat((float) haut);
        kopfB.putFloat((float) N_WASSER).putFloat((float) DURCH).putFloat(bw).putFloat(bh);
        double lt = linieTiefe;
        kopfB.putFloat((float) (Double.isNaN(lt) ? 0 : lt)).putFloat((float) linieRechts).putFloat((float) linieOben).putFloat(Double.isNaN(lt) ? 0 : 1);
        boolean k = rechtecke && kachelnSetzen(werte, n);
        kopfB.putFloat(k ? 1 : 0).putFloat(KX).putFloat(KY).putFloat(KACHEL);
        kopfB.flip();
        if (!k) { kachelB.clear(); kachelB.putInt(0).flip(); kAktiv = -1; }
        tropfenB.clear();
        float[] t = werte;
        for (int i = 0; i < 8 * n; i++) tropfenB.putFloat(t[i]);
        if (n == 0) tropfenB.putFloat(0);
        tropfenB.flip();
    }

    /**
     * Seit S9b-3: je Kachel die Tropfen, deren Kasten (wie in linse.glsl: ±2,3 a um die Mitte) eine Zelle der Kachel oder ihre
     * Nachbarn berührt, aufsteigend; dazu die Kacheln mit Tropfen (nur dort kann ein Bildpunkt brechen: die Höhe eines
     * Bildpunkts kommt aus der nächsten Zelle und ihren Nachbarn, und eine Zelle ist nur unter einem Kasten über 0). In
     * {@link #kachelB}: Lage je Kachel (KX · KY + 1), die Kacheln mit Tropfen, die Tropfen. false, wenn die Listen nicht passen.
     */
    private boolean kachelnSetzen(float[] t, int n) {
        int kxy = KX * KY;
        java.util.Arrays.fill(kZahl, 0);
        if (kKasten.length < 4 * n) kKasten = new int[4 * n];
        double cw = breite / NX, ch = hoehe / NY;
        long summe = 0;
        for (int i = 0; i < n; i++) {
            double x = t[8 * i], y = t[8 * i + 1], r = t[8 * i + 2] * 2.3;
            // Zellen, deren Mitte (j + 0,5) · Zelle im Kasten liegt, und eine Zelle Rand für Rundung und Nachbarn
            double ax = (x - r) / cw - 0.5, ex = (x + r) / cw - 0.5, ay = (y - r) / ch - 0.5, ey = (y + r) / ch - 0.5;
            if (!(ax <= ex && ay <= ey) || ex < -2 || ey < -2 || ax > NX + 1 || ay > NY + 1) { kKasten[4 * i] = kKasten[4 * i + 2] = 1; kKasten[4 * i + 1] = kKasten[4 * i + 3] = 0; continue; }
            int x0 = Math.max(0, Math.floorDiv((int) Math.max(-2, Math.ceil(ax)) - 1, KACHEL)), x1 = Math.min(KX - 1, Math.floorDiv((int) Math.min(NX + 1, Math.floor(ex)) + 1, KACHEL));
            int y0 = Math.max(0, Math.floorDiv((int) Math.max(-2, Math.ceil(ay)) - 1, KACHEL)), y1 = Math.min(KY - 1, Math.floorDiv((int) Math.min(NY + 1, Math.floor(ey)) + 1, KACHEL));
            kKasten[4 * i] = x0;
            kKasten[4 * i + 1] = x1;
            kKasten[4 * i + 2] = y0;
            kKasten[4 * i + 3] = y1;
            for (int ky = y0; ky <= y1; ky++)
                for (int kx = x0; kx <= x1; kx++) kZahl[ky * KX + kx]++;
            summe += (long) Math.max(0, x1 - x0 + 1) * Math.max(0, y1 - y0 + 1);
        }
        if (summe > KLISTE) return false;
        kLage[0] = 0;
        int aktiv = 0;
        for (int q = 0; q < kxy; q++) {
            kLage[q + 1] = kLage[q] + kZahl[q];
            if (kZahl[q] > 0) aktiv++;
        }
        kachelB.clear();
        for (int q = 0; q <= kxy; q++) kachelB.putInt(kLage[q]);
        for (int q = 0; q < kxy; q++) if (kZahl[q] > 0) kachelB.putInt(q);
        for (int q = aktiv; q < kxy; q++) kachelB.putInt(0);
        int basis = 2 * kxy + 1;
        java.util.Arrays.fill(kZahl, 0);
        for (int i = 0; i < n; i++)
            for (int ky = kKasten[4 * i + 2]; ky <= kKasten[4 * i + 3]; ky++)
                for (int kx = kKasten[4 * i]; kx <= kKasten[4 * i + 1]; kx++) {
                    int q = ky * KX + kx;
                    kachelB.putInt(4 * (basis + kLage[q] + kZahl[q]++), i);
                }
        kachelB.position(4 * (basis + (int) summe));
        kachelB.flip();
        kAktiv = aktiv;
        return true;
    }

    /** tan des halben Bildwinkels waagrecht und senkrecht ({@code bildwinkel} ist senkrecht). */
    static double[] tan(Blick b, int bw, int bh) {
        double ty = Math.tan(Math.toRadians(b.bildwinkel) / 2);
        return new double[] {ty * bw / Math.max(1, bh), ty};
    }

    @Override public void zeichnen(Ablauf a, Bildziel ziel) {
        int n = zahl;
        if (!an || (n == 0 && haut <= 0)) { gezeichnet = 0; aktiv = false; return; }
        gezeichnet = n;
        aktiv = true;
        double[] t = tan(ziel.blick, ziel.breite, ziel.hoehe);
        kopfSchreiben(t[0], t[1], n, ziel.breite, ziel.hoehe);
        // seit S9b-3: nur in den Kacheln mit Tropfen (ohne Haut); keine solche Kachel: das Bild bleibt, wie es ist
        int ka = kAktiv;
        boolean nurKacheln = ka >= 0 && haut <= 0;
        kacheln = nurKacheln ? ka : -1;
        if (nurKacheln && ka == 0) return;
        a.durchgang("Linse, Kopf", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(tropfenP, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(kachelP, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
            vkCmdUpdateBuffer(c, tropfenP.handle, 0, tropfenB);
            vkCmdUpdateBuffer(c, kachelP.handle, 0, kachelB);
        });
        a.rechne("Linse, Höhe", kHoehe, kHoehe.satz(kopf, tropfenP, hoeheP, kachelP), null, Kernel.gruppen(NX, 16), Kernel.gruppen(NY, 16), 1,
                Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopf, tropfenP, kachelP), Ablauf.schreibt(hoeheP)));
        if (kopie == null || kopie.breite != ziel.breite || kopie.hoehe != ziel.hoehe) {
            if (kopie != null) { g.ruhe(); kopie.close(); }
            kopie = new Bild(g, konto, "Linse, Kopie", ziel.breite, ziel.hoehe, ziel.hdr.format);
        }
        Bild hdr = ziel.hdr, k = kopie;
        a.durchgang("Linse, Kopie", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(hdr, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_READ_BIT, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
                new Ablauf.Zugriff(k, VK_PIPELINE_STAGE_2_COPY_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)}, c -> {
            try (MemoryStack st = MemoryStack.stackPush()) {
                VkImageCopy.Buffer kp = VkImageCopy.calloc(1, st);
                kp.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                kp.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
                kp.get(0).extent().set(hdr.breite, hdr.hoehe, 1);
                vkCmdCopyImage(c, hdr.handle, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, k.handle, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, kp);
            }
        });
        Zeichner zz = nurKacheln ? zK : z;
        long satz = zz.satz(kopf, tropfenP, hoeheP, linear.an(k), kachelP);
        a.zeichne("Linse", new Bild[] {hdr}, null, null, null,
                Ablauf.und(Ablauf.liest(Ablauf.ZEICHNEN, kopf, tropfenP, hoeheP, kachelP), Ablauf.textur(Ablauf.ZEICHNEN, k)),
                c -> zz.zeichne(c, satz, null, nurKacheln ? 6 * ka : 3, 1));
    }

    /**
     * Prüfstein 36: wohin die Bildpunkte uv (je zwei float, 0 … 1, y nach unten) durch die Tropfen t (n, wie
     * {@link #tropfen}) zeigen, auf der Karte; Bildwinkel bw (Grad, senkrecht), Bild breite × hoehe. Je Probe 4 float: uv aus,
     * Anteil des Lichts, 1 wenn Wasser.
     */
    public float[] probe(float[] t, int n, double bildwinkel, int bw, int bh, float[] uv) {
        int m = uv.length / 2;
        tropfen(t, n, 0);
        Blick b = new Blick();
        b.bildwinkel = bildwinkel;
        double[] tn = tan(b, bw, bh);
        kopfSchreiben(tn[0], tn[1], zahl, bw, bh);
        Kernel.Bindung P = Kernel.Bindung.PUFFER;
        try (Kernel kp = Kernel.lade(g, "/com/dan/render/frame/linseprobe.comp", 16, null, P, P, P, P, P);
             Puffer pr = Puffer.speicher(g, konto, "Probe Linse, Orte", 8L * m);
             Puffer aus = Puffer.speicher(g, konto, "Probe Linse, Aus", 16L * m)) {
            pr.schreibe(uv);
            Ablauf a = new Ablauf(g, g.grafik);
            a.durchgang("Linse, Kopf", new Ablauf.Zugriff[] {
                    new Ablauf.Zugriff(kopf, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                    new Ablauf.Zugriff(tropfenP, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(kachelP, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
                vkCmdUpdateBuffer(c, kopf.handle, 0, kopfB);
                vkCmdUpdateBuffer(c, tropfenP.handle, 0, tropfenB);
                vkCmdUpdateBuffer(c, kachelP.handle, 0, kachelB);
            });
            a.rechne("Linse, Höhe", kHoehe, kHoehe.satz(kopf, tropfenP, hoeheP, kachelP), null, Kernel.gruppen(NX, 16), Kernel.gruppen(NY, 16), 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopf, tropfenP, kachelP), Ablauf.schreibt(hoeheP)));
            ByteBuffer p = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
            p.putInt(0, m);
            a.rechne("Probe Linse", kp, kp.satz(kopf, tropfenP, hoeheP, pr, aus), p, Kernel.gruppen(m, 64), 1, 1,
                    Ablauf.und(Ablauf.liest(Ablauf.RECHNEN, kopf, tropfenP, hoeheP, pr), Ablauf.schreibt(aus)));
            g.grafik.einmal(c -> a.aufnehmen(c, null));
            return aus.floats(4 * m);
        }
    }

    /**
     * Prüfstein 36, Referenz in double: ein Tropfen (Kugelkappe mit 60°) mit Kontakthalbmesser a in der Mitte (cx, cy) der
     * Scheibe; wohin zeigt der Bildpunkt uv? Strahl der Kamera durch die ebene Scheibe ins Wasser (Snellius), Schnitt mit der
     * Kugel genau, Austritt in die Luft. Gibt {u, v, Anteil (0: Totalreflexion), 1 wenn Wasser}.
     */
    public static double[] referenz(double breite, double hoehe, double bildwinkel, int bw, int bh, double cx, double cy, double a, double u, double v) {
        double ty = Math.tan(Math.toRadians(bildwinkel) / 2), tx = ty * bw / bh;
        double px = u * breite, py = (1 - v) * hoehe;
        double s = Math.sin(Math.toRadians(60)), co = Math.cos(Math.toRadians(60));
        double R = a / s, zc = -a * co / s;                                  // Kugel: Halbmesser, Mitte unter der Scheibe
        double dx = px - cx, dy = py - cy;
        if (dx * dx + dy * dy >= a * a) return new double[] {u, v, 1, 0};
        double[] d = norm(new double[] {(2 * u - 1) * tx, (1 - 2 * v) * ty, 1});
        double[] dw = brechen(d, new double[] {0, 0, -1}, 1 / N_WASSER);
        // Schnitt des Strahls (px, py, 0) + s dw mit der Kugel um (cx, cy, zc)
        double ox = px - cx, oy = py - cy, oz = -zc;
        double b2 = ox * dw[0] + oy * dw[1] + oz * dw[2], c2 = ox * ox + oy * oy + oz * oz - R * R;
        double disc = b2 * b2 - c2;
        double sx = -b2 + Math.sqrt(Math.max(0, disc));
        double qx = px + sx * dw[0], qy = py + sx * dw[1], qz = sx * dw[2];
        double[] nrm = norm(new double[] {qx - cx, qy - cy, qz - zc});
        double[] o = brechen(dw, new double[] {-nrm[0], -nrm[1], -nrm[2]}, N_WASSER);
        if (o == null || o[2] <= 1e-3) return new double[] {u, v, 0, 1};
        return new double[] {0.5 + 0.5 * (o[0] / o[2]) / tx, 0.5 - 0.5 * (o[1] / o[2]) / ty, DURCH, 1};
    }

    static double[] norm(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return new double[] {a[0] / l, a[1] / l, a[2] / l};
    }

    /** refract wie GLSL: I einfallend, N gegen I, eta = n1 / n2; null bei Totalreflexion. */
    static double[] brechen(double[] i, double[] n, double eta) {
        double c = n[0] * i[0] + n[1] * i[1] + n[2] * i[2];
        double k = 1 - eta * eta * (1 - c * c);
        if (k < 0) return null;
        double f = eta * c + Math.sqrt(k);
        return new double[] {eta * i[0] - f * n[0], eta * i[1] - f * n[1], eta * i[2] - f * n[2]};
    }

    @Override public void close() {
        kHoehe.close();
        z.close();
        linear.close();
        kopf.close();
        tropfenP.close();
        hoeheP.close();
        kachelP.close();
        zK.close();
        if (kopie != null) kopie.close();
    }
}
