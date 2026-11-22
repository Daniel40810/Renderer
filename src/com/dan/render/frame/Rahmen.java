package com.dan.render.frame;

import com.dan.core.gpu.Abtaster;
import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Auflage;
import com.dan.core.gpu.Bild;
import com.dan.core.gpu.Bildteil;
import com.dan.core.gpu.Bildziel;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Zeichner;
import com.dan.core.raum.Blick;
import com.dan.render.frame.film.Film;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Der Bildrahmen (render.frame, Werkbuch HalligHooge Teil A, 8.1 und 12.7, P6a): legt das {@link Bildziel} an (HDR in
 * RGBA16F, Tiefe in D32F), führt je Bild die {@link Bildteil}e aus, misst die Leuchtdichte, stellt die
 * {@link Belichtung} nach und gibt das Bild durch die {@link Filmkurve} in ein Ziel aus (ein Bild der Swapchain oder
 * ein eigenes, sRGB oder linear).
 * <p>
 * Ein Bild in einem Ablauf der Grafik-Warteschlange:
 * <ol>
 * <li>Blick, Szenenzeit und Vorbelichtung (die Belichtung des vorigen Bilds) ins Bildziel; HDR und Tiefe löschen
 * (Tiefe 0: unendlich fern).</li>
 * <li>Alle Teile vorbereiten, dann alle zeichnen.</li>
 * <li>Histogramm der Leuchtdichte (Schritt 4) in einen von drei geteilten Puffern, einmal über die Bildpunkte mit Tiefe
 * und einmal über alle (F49 A; welches gilt, wählt {@link Belichtung#waehle}). Gelesen wird der Puffer von vor zwei
 * Bildern: Die {@link com.dan.core.gpu.Flaeche} lässt höchstens zwei Bilder zugleich laufen, er ist also fertig, und
 * niemand wartet.</li>
 * <li>Ausgabe: HDR mal (Belichtung / Vorbelichtung), mit {@link Belichtung#nachtsehen} das Sehen mit den Stäbchen
 * (F50 A), durch die Kurve ins Ziel.</li>
 * <li>Seit 0.3 (P11c2): die {@link Auflage}n in das Ziel der Ausgabe, mit der Tiefe der Szene.</li>
 * </ol>
 */
public final class Rahmen implements AutoCloseable {
    public static final String VERSION = "0.3";
    private static final String PFAD = "/com/dan/render/frame/";
    private static final int RING = 3;
    public static final int SCHRITT = 4;

    private final Geraet g;
    private final Konto konto;
    public final Belichtung belichtung = new Belichtung();
    /** Die Kurve; nach einer Änderung {@link #filmGeaendert}. */
    public final Film film = new Film();
    /** Kurve aus (linear, nur Belichtung) – für Prüfungen. */
    public boolean kurve = true;
    private Bildziel ziel;
    private final Abtaster punkt;
    private final Kernel histogramm;
    private final Map<Integer, Zeichner> ausgaben = new HashMap<>();
    private final Puffer[] faecher = new Puffer[RING];
    private final double[] vorbel = new double[RING];
    private final Puffer tabelle, parameter;
    private long bild;
    private long[] letzte, letzterPuffer;

    public Rahmen(Geraet g, Konto konto, int breite, int hoehe) {
        this.g = g;
        this.konto = konto;
        film.grain = film.halation = film.weave = film.flicker = false;
        punkt = new Abtaster(g, false, false);
        histogramm = Kernel.lade(g, PFAD + "histogramm.comp", 20, null, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER, Kernel.Bindung.TEXTUR);
        for (int i = 0; i < RING; i++) {
            faecher[i] = Puffer.geteilt(g, konto, "Histogramm " + i, 4L * Belichtung.PUFFER);
            faecher[i].nullen();
        }
        tabelle = Puffer.speicher(g, konto, "Filmkurve", 16L * Filmkurve.N);
        parameter = Puffer.speicher(g, konto, "Filmkurve, Parameter", 4L * 24);
        filmGeaendert();
        groesse(breite, hoehe);
    }

    /** Die Tabelle der Kurve neu (nach einer Änderung an {@link #film}). */
    public void filmGeaendert() {
        Filmkurve k = new Filmkurve(film);
        tabelle.schreibe(k.tabelle);
        parameter.schreibe(k.parameter);
        belichtung.bild = x -> k.anwenden(x, x, x)[1];                // F50 A: Krawczyk im Bild
    }

    /** Neue Bildgröße: HDR und Tiefe neu (die geteilten Bilder legen die Teile beim nächsten Bild wieder ab). */
    public void groesse(int breite, int hoehe) {
        if (ziel != null && ziel.breite == breite && ziel.hoehe == hoehe) return;
        if (ziel != null) {
            ziel.hdr.close();
            ziel.tiefe.close();
        }
        ziel = new Bildziel(new Bild(g, konto, "HDR", breite, hoehe, Bild.RGBA16F), new Bild(g, konto, "Tiefe", breite, hoehe, Bild.D32F));
    }

    public Bildziel ziel() { return ziel; }
    public long bilder() { return bild; }
    /** Das zuletzt gelesene Histogramm, die gewählten Fächer (null vor dem dritten Bild). */
    public long[] letztesHistogramm() { return letzte; }
    /** Der zuletzt gelesene Puffer des Histogramms ({@link Belichtung#PUFFER} Werte, null vor dem dritten Bild). */
    public long[] letzterPuffer() { return letzterPuffer; }

    /**
     * Nimmt ein Bild in den Ablauf auf (der in einen Befehlspuffer der Grafik-Warteschlange aufgenommen wird).
     * {@code aus} ist das Ziel der Ausgabe in seiner Größe gleich dem Bildziel; {@code dt} die Zeit seit dem vorigen Bild
     * (für die Augenanpassung).
     */
    public void bild(Ablauf a, List<? extends Bildteil> teile, Blick blick, Instant zeit, Bild aus, double dt) {
        bild(a, teile, List.of(), blick, zeit, aus, dt);
    }

    /**
     * Wie oben, mit Auflagen (seit 0.3, P11c2): Sie bereiten nach den Teilen vor und legen nach der Ausgabe in
     * {@code aus} auf. Ohne Auflagen ist das Bild bitgleich zu 0.2.
     */
    public void bild(Ablauf a, List<? extends Bildteil> teile, List<? extends Auflage> auflagen, Blick blick, Instant zeit, Bild aus, double dt) {
        if (aus.breite != ziel.breite || aus.hoehe != ziel.hoehe) throw new IllegalArgumentException("Rahmen: Ausgabe " + aus + ", Bildziel " + ziel.breite + " × " + ziel.hoehe);
        int r = (int) (bild % RING);
        // Messung von vor zwei Bildern lesen (fertig, s. o.)
        if (bild >= 2) {
            int alt = (int) ((bild - 2) % RING);
            int[] f = faecher[alt].ints(Belichtung.PUFFER);
            letzterPuffer = new long[f.length];
            for (int i = 0; i < f.length; i++) letzterPuffer[i] = Integer.toUnsignedLong(f[i]);
            letzte = belichtung.waehle(letzterPuffer);
            belichtung.messung(letzte, vorbel[alt], dt);
        } else belichtung.schritt(dt);
        ziel.blick = blick.kopie().groesse(ziel.breite, ziel.hoehe);
        ziel.zeit = zeit;
        ziel.vorbelichtung = belichtung.faktor();
        vorbel[r] = ziel.vorbelichtung;
        a.zeichne("löschen", new Bild[] {ziel.hdr}, new float[][] {{0, 0, 0, 0}}, ziel.tiefe, 0f, null, c -> { });
        for (Bildteil t : teile) t.vorbereiten(a, ziel);
        for (Auflage t : auflagen) t.vorbereiten(a, ziel);
        for (Bildteil t : teile) t.zeichnen(a, ziel);
        messen(a, r);
        ausgeben(a, aus, belichtung.faktor() / ziel.vorbelichtung, 1 / ziel.vorbelichtung);
        for (Auflage t : auflagen) t.auflegen(a, ziel, aus);
        bild++;
    }

    private void messen(Ablauf a, int r) {
        Puffer f = faecher[r];
        a.durchgang("Histogramm löschen", new Ablauf.Zugriff[] {new Ablauf.Zugriff(f, VK_PIPELINE_STAGE_2_CLEAR_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdFillBuffer(c, f.handle, 0, VK_WHOLE_SIZE, 0));
        int gx = (ziel.breite + SCHRITT - 1) / SCHRITT, gy = (ziel.hoehe + SCHRITT - 1) / SCHRITT;
        try (MemoryStack s = stackPush()) {
            ByteBuffer p = s.malloc(20).putInt(0, ziel.breite).putInt(4, ziel.hoehe).putInt(8, SCHRITT).putFloat(12, (float) Belichtung.LOG_MIN).putFloat(16, (float) Belichtung.LOG_MAX);
            a.rechne("Histogramm", histogramm, histogramm.satz(punkt.an(ziel.hdr), f, punkt.an(ziel.tiefe)), p, Kernel.gruppen(gx, 16), Kernel.gruppen(gy, 16), 1,
                    Ablauf.und(Ablauf.textur(Ablauf.RECHNEN, ziel.hdr, ziel.tiefe), Ablauf.schreibt(f)));
        }
        // der Prozessor liest den Puffer in zwei Bildern
        a.durchgang("Histogramm zum Prozessor", new Ablauf.Zugriff[] {new Ablauf.Zugriff(f, VK_PIPELINE_STAGE_2_HOST_BIT, VK_ACCESS_2_HOST_READ_BIT, 0)}, c -> { });
    }

    private void ausgeben(Ablauf a, Bild aus, double faktor, double ohneVorbelichtung) {
        Zeichner z = ausgaben.computeIfAbsent(aus.format, fm -> {
            Zeichner.Art art = new Zeichner.Art().ohneTiefe();
            art.farben = new int[] {fm};
            return Zeichner.lade(g, PFAD + "voll.vert", PFAD + "ausgabe.frag", 20, null, art, Kernel.Bindung.TEXTUR, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
        });
        long satz = z.satz(punkt.an(ziel.hdr), tabelle, parameter);
        byte[] push = new byte[20];
        ByteBuffer.wrap(push).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(0, (float) faktor).putInt(4, kurve ? 1 : 0).putInt(8, Filmkurve.N)
                .putFloat(12, (float) ohneVorbelichtung).putInt(16, belichtung.nachtsehen ? 1 : 0);
        a.zeichne("Ausgabe", new Bild[] {aus}, null, null, null, Ablauf.und(Ablauf.textur(Ablauf.ZEICHNEN, ziel.hdr), Ablauf.liest(Ablauf.ZEICHNEN, tabelle, parameter)), c -> {
            try (MemoryStack s = stackPush()) {
                z.zeichne(c, satz, s.bytes(push), 3, 1);
            }
        });
    }

    @Override public void close() {
        for (Zeichner z : ausgaben.values()) z.close();
        histogramm.close();
        punkt.close();
        for (Puffer f : faecher) f.close();
        tabelle.close();
        parameter.close();
        ziel.hdr.close();
        ziel.tiefe.close();
    }
}
