package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Der Antrieb des Auflaufs aus der Synthese (Seegang Werkbuch, S6b-2, Datei 67; SF54 A, SF61 A, SF62 A): an Punkten längs
 * des seeseitigen Rands eines Rechtecks die Fläche mit dem tiefenintegrierten Fluss je Welle und der gebundenen langen
 * Welle ({@code see_antrieb.comp}, wie {@code core.szene.Oberflaeche.Punkt.etaAntrieb}, {@code .qx}, {@code .qy}), dann
 * verteilt auf Ziele ({@code see_antrieb_rand.comp}): je Ziel Stand über dem Bezug und Geschwindigkeit u = q / h senkrecht
 * und längs, in einen fremden Puffer an einen gegebenen Platz. So bleibt dieses Package frei vom Löser
 * ({@code physics.swash}); die Anwendung gibt Puffer, Plätze, Grund und Richtungen der Ziele.
 * <p>
 * Aus Stand und Fluss der <b>ganzen</b> Fläche nimmt der Rand des Lösers nur die einlaufende Welle (für lange Wellen ist
 * u + 2√(g h) der einlaufenden und der reflektierten zusammen gleich dem der einlaufenden allein); was zurückläuft,
 * verlässt das Rechteck.
 * <p>
 * <b>Richtung erhalten</b> (seit S6c-1b, Seegang Werkbuch 78, SF85 A): Der Löser rechnet Flachwasser ohne Dispersion; bei ω
 * hat er die Wellenzahl ω/√(g h). Die Synthese zeichnet die Wellen mit der linearen Wellenzahl (an der Küste mit noch
 * größerer, A15); schräge Anteile wechseln längs des Rands dann schneller, als der Löser sie führen kann, und klingen am
 * Rand ab (Datei 77: am Deich liefen 53 %, am Deckwerk 41 % der Energie hinein). Mit {@link #bezug} streckt
 * {@code see_antrieb} je Stützstelle Phase und Laufzeit längs der Punkte um den Bezugspunkt so, dass jede Welle im Löser
 * dieselbe Richtung hat wie in der Synthese: Φ′ = Φ_m + s (Φ − Φ_m), s = (ω/√(g h_R)) / |∇Φ_m|, T′ = T_m + (T − T_m) /
 * (|∇T_m| √(g h_R)), Fluss je Welle mit √(g h_R). Energie, Hülle und Richtung bleiben. Referenz:
 * {@code core.szene.Oberflaeche#punktRand}. {@code -Dseegang.randrichtung=aus}: wie bis S6c-1.
 */
public final class Randwelle implements AutoCloseable {
    private static final int PUSH_A = 32, PUSH_V = 32;
    /** Seit S6c-1b: Richtung im Löser erhalten ({@link #bezug}); {@code -Dseegang.randrichtung=aus} schaltet ab. */
    public static volatile boolean RICHTUNG = !"aus".equalsIgnoreCase(System.getProperty("seegang.randrichtung", "an"));

    private final Geraet g;
    private final Kernel kAntrieb, kVerteilen;
    private final Puffer rw, ziele;
    public final int maxPunkte, maxZiele;
    private double[] punkteWelt = new double[0];
    private int punkte, zahlZiele;
    private double bxGesetzt = Double.NaN, byGesetzt = Double.NaN;
    private double bezugOst = Double.NaN, bezugNord = Double.NaN, bezugTiefe = 0;

    public Randwelle(Geraet g, Konto konto, int maxPunkte, int maxZiele) {
        this.g = g;
        this.maxPunkte = maxPunkte;
        this.maxZiele = maxZiele;
        rw = Puffer.speicher(g, konto, "Randwelle", 16L * 3 * maxPunkte);
        ziele = Puffer.speicher(g, konto, "Randwelle Ziele", 32L * Math.max(1, maxZiele));
        Kernel.Bindung[] b = new Kernel.Bindung[23];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kAntrieb = Kernel.lade(g, SeegangKarte.PFAD + "see_antrieb.comp", PUSH_A, null, b);
        kVerteilen = Kernel.lade(g, SeegangKarte.PFAD + "see_antrieb_rand.comp", PUSH_V, null, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER, Kernel.Bindung.PUFFER);
    }

    /** Die Punkte längs des Rands (UTM, ost, nord abwechselnd); gelten ab dem nächsten {@link #rechne}. Seit S6c-1b ohne Bezug ({@link #bezug} danach). */
    public void punkte(double[] ostNord) {
        if (ostNord.length / 2 > maxPunkte) throw new IllegalArgumentException("Randwelle: höchstens " + maxPunkte + " Punkte");
        punkteWelt = ostNord.clone();
        punkte = ostNord.length / 2;
        bxGesetzt = Double.NaN;
        bezugTiefe = 0;                                                        // seit S6c-1b: der Bezug gehört zu den Punkten
    }

    public int punkte() { return punkte; }

    /**
     * Die Ziele, je acht Werte: Platz im Stand, Ort u längs der Punkte (0 … punkte − 1), Grund der Randzelle (m NHN), Art (seit Datei 73:
     * 0 die Welle, 1 trockenes Außen, 2 ruhendes Wasser am Spiegel),
     * Richtung ins Gitter (Ost, Nord), Richtung längs (Ost, Nord).
     */
    public void ziele(float[] werte) {
        int n = werte.length / 8;
        if (n > maxZiele) throw new IllegalArgumentException("Randwelle: höchstens " + maxZiele + " Ziele");
        ziele.schreibe(werte);
        zahlZiele = n;
    }

    public int ziele() { return zahlZiele; }

    /**
     * Seit S6c-1b: Bezugspunkt (UTM, die Mitte des Rands auf der Linie der Punkte) und Tiefe des Rands h_R (m) für die
     * erhaltene Richtung; die Punkte sollen auf einer Geraden durch ihn liegen. Tiefe ≤ 0 oder {@link #RICHTUNG} aus: wie bis
     * S6c-1. Gilt ab dem nächsten {@link #rechne}.
     */
    public void bezug(double ost, double nord, double tiefe) {
        bezugOst = ost;
        bezugNord = nord;
        bezugTiefe = tiefe;
    }

    /** Tiefe des Rands, mit der {@link #rechne} die Richtung erhält (0: aus). */
    public double bezugTiefe() { return RICHTUNG && bezugTiefe > 0 && Double.isFinite(bezugOst) && Double.isFinite(bezugNord) ? bezugTiefe : 0; }

    /** Punkte relativ zum Bezugspunkt B der Karte, wenn B sich seit dem letzten Mal bewegt hat (über den Ablauf). */
    private void punkteAufnehmen(Ablauf ab, SeegangKarte k) {
        if (k.bx == bxGesetzt && k.by == byGesetzt) return;
        bxGesetzt = k.bx;
        byGesetzt = k.by;
        ByteBuffer d = ByteBuffer.allocateDirect(16 * Math.max(1, punkte)).order(ByteOrder.nativeOrder());
        for (int i = 0; i < punkte; i++) d.putFloat((float) (punkteWelt[2 * i] - k.bx)).putFloat((float) (punkteWelt[2 * i + 1] - k.by)).putFloat(0).putFloat(0);
        d.flip();
        Puffer p = rw;
        ab.durchgang("Randwelle, Punkte", new Ablauf.Zugriff[] {new Ablauf.Zugriff(p, org.lwjgl.vulkan.VK13.VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT,
                org.lwjgl.vulkan.VK13.VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> org.lwjgl.vulkan.VK10.vkCmdUpdateBuffer(c, p.handle, 0, d));
    }

    /**
     * Nimmt die Synthese an den Punkten in den Ablauf auf, zu zwei Zeiten (Versatz gegen die Zeit der Karte in s; die
     * Zeit der Karte steht schon, {@link SeegangKarte#zeit}). Ohne Punkte geschieht nichts.
     */
    public void rechne(Ablauf ab, SeegangKarte k, double zv0, double zv1) { rechne(ab, k, zv0, zv1, 2); }

    /** Wie oben, mit {@code zeiten} = 1 nur zu zv0 (im Lauf je Bild eine neue Zeit, die vorige bleibt in Zeit 1 … des Rings der Anwendung). */
    public void rechne(Ablauf ab, SeegangKarte k, double zv0, double zv1, int zeiten) {
        if (punkte == 0) return;
        punkteAufnehmen(ab, k);
        ByteBuffer pu = ByteBuffer.allocateDirect(PUSH_A).order(ByteOrder.nativeOrder());
        pu.putInt(0, punkte).putInt(4, maxPunkte).putFloat(8, (float) zv0).putFloat(12, (float) zv1);
        double hr = bezugTiefe();                                              // seit S6c-1b: Richtung erhalten
        pu.putFloat(16, hr > 0 ? (float) (bezugOst - k.bx) : 0f).putFloat(20, hr > 0 ? (float) (bezugNord - k.by) : 0f).putFloat(24, (float) hr).putFloat(28, 0f);
        Mittel[] lesen = k.lesenSynthese();
        Mittel[] l2 = Arrays.copyOf(lesen, lesen.length + 1);
        l2[lesen.length] = rw;
        ab.rechne("see_antrieb", kAntrieb, kAntrieb.satz(k.belegungSchaum(rw)), pu, punkte, Math.max(1, Math.min(2, zeiten)), 1, l2, new Mittel[] {rw});
    }

    /**
     * Verteilt die Zeit {@code zeit} (0 oder 1 aus {@link #rechne}) auf die Ziele: schreibt nach {@code ziel} ab Platz
     * {@code ziel0} (vec4 je Platz). {@code wasserstand}: Spiegel des Wassers am Rechteck (m NHN), {@code bezug}:
     * Ruhewasser des Lösers (m NHN).
     */
    public void verteilen(Ablauf ab, Puffer ziel, int ziel0, int zeit, double wasserstand, double bezug) {
        if (zahlZiele == 0 || punkte == 0) return;
        ByteBuffer pu = ByteBuffer.allocateDirect(PUSH_V).order(ByteOrder.nativeOrder());
        pu.putInt(0, zahlZiele).putInt(4, maxPunkte).putInt(8, punkte).putInt(12, zeit).putInt(16, ziel0)
                .putFloat(20, (float) wasserstand).putFloat(24, (float) bezug).putFloat(28, 0);
        ab.rechne("see_antrieb_rand", kVerteilen, kVerteilen.satz(rw, ziele, ziel), pu, Kernel.gruppen(zahlZiele, 64), 1, 1, new Mittel[] {rw, ziele, ziel}, new Mittel[] {ziel});
    }

    /** Ergebnis der Punkte (für Prüfungen; wartet): je Punkt und Zeit (η des Antriebs, q_x, q_y, m0). */
    public float[] lies() {
        ByteBuffer b = ByteBuffer.allocateDirect(16 * 2 * Math.max(1, punkte)).order(ByteOrder.nativeOrder());
        rw.lies(b, 16L * maxPunkte);
        float[] f = new float[8 * punkte];
        b.asFloatBuffer().get(f);
        return f;
    }

    @Override public void close() {
        g.ruhe();
        kAntrieb.close();
        kVerteilen.close();
        rw.close();
        ziele.close();
    }
}
