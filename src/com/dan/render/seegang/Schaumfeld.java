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

import static org.lwjgl.vulkan.VK13.*;

/**
 * Das Schaumfeld (Seegang Werkbuch, S4a; SF42–SF45 A): Schaum mit Gedächtnis. Zwei Raster um das Auge [G, SF43 A], fein
 * {@link #N_FEIN} × {@link #N_FEIN} zu {@link #D_FEIN} m (256 m) und grob {@link #N_GROB} × {@link #N_GROB} zu
 * {@link #D_GROB} m (1 km); beide wandern mit dem Auge. Je Bild ({@link #schritt}) und Zelle ({@code see_schaum.comp}):
 * <ol>
 * <li><b>Transport</b> mit der Drift der {@link SeegangKarte} (Strömung, Stokes-Drift, {@link #WINDDRIFT} U10; die
 * Anwendung rechnet sie je Zelle des Nahfelds) im Flussverfahren, aufwind: Die Masse bleibt erhalten bis auf das, was
 * über den Rand des Fensters abfließt, und wo die Drift zusammenläuft, sammelt sich der Schaum (∂f/∂t + ∇·(u f) = 0).
 * Höchstens {@link #COURANT} Zellen je Teilschritt, höchstens {@link #TEILSCHRITTE} Teilschritte je Bild; bei großem
 * Zeitraffer wird der Weg des Transports gekappt (der Schaum lebt dann ohnehin nur wenige Bilder).</li>
 * <li><b>Zerfall</b> mit der Lebensdauer {@link #TAU_OFFEN} im offenen Wasser, {@link #TAU_WATT} im Watt (Tiefe unter
 * {@link #WATT_BIS} m) [G, SF45 A].</li>
 * <li><b>Quelle</b>, wo ein Kamm bricht: im tiefen und mittleren Wasser nach der linearen Schwelle zu u_s/c = 0,85
 * (Barthelemy u. a. 2018; linear nach Hulin u. a. 2025), im Flachen nach Battjes und Janssen (1978). Die Steilheit am Kamm
 * liest der Kernel aus der Clipmap: (1 − J) / λ = Σ a k coth(kh), weil die Verschiebung lagrangesch ist. Seit S4a-2
 * dazu die Köpfe der Windsee, die die Fläche nicht auflöst ({@link #WINDSEE}): auf den Kämmen wie die Schaumköpfe aus
 * P13c, mit der ganzen Bedeckung √(W_Callaghan · W_Monahan) aus U10 (wie F161 A), davon aktiv W / (1 + τ / (T_p / 4)).</li>
 * </ol>
 * Wert je Zelle: die Menge f (1 = frisch und deckend; der Anteil im Bild ist min(1, f), bis S4b die Optik baut).
 * <p>
 * <b>Für das Wasser</b> ({@code render.water}, das dieses Package nicht kennt) liegt das Feld unter {@link #SCHAUM}: ein
 * Kopf von {@link #KOPF} Byte – ivec4 (1 = an, Anfang der gültigen Kopie, 0, 0); je Raster vec4 (Ost, Nord der Ecke der
 * Zelle (0, 0) relativ zum Auge, Abstand, 0) und ivec4 (Platz der Zelle (0, 0) im Ring: i, j; Anfang des Rasters in der
 * Kopie; Zellen je Seite); vec4 (Breite des weichen Rands fein, grob in Zellen, 0, 0) –, danach zwei Kopien der Werte,
 * je Kopie erst fein, dann grob, je Raster Zeile für Zeile im Ring (Platz (I mod n, J mod n) der absoluten Zelle).
 * <p>
 * Das Feld gehört nicht zu einer Fläche: Die Anwendung hält eines über alle Flächen ({@code lauf.Flaechenwart}) und hängt
 * es an das {@link Seegangbild} der gezeigten Fläche; es rechnet mit deren Karte.
 */
public final class Schaumfeld implements AutoCloseable {
    public static final String VERSION = "0.4";
    /** Name des geteilten Puffers für das Wasser. */
    public static final String SCHAUM = "Seegang/Schaum";
    /** Die Raster [G, SF43 A]. */
    public static final int N_FEIN = 1024, N_GROB = 512;
    public static final double D_FEIN = 0.25, D_GROB = 2.0;
    /** Lebensdauer des Schaums (s, 1/e) im offenen Wasser und im Watt, Grenze des Watts (m Tiefe) [G, SF45 A]. */
    public static final double TAU_OFFEN = 4, TAU_WATT = 12, WATT_BIS = 2;
    /** Anteil von U10, mit dem der Wind den Schaum treibt [G, Plan 6.4]. */
    public static final double WINDDRIFT = 0.03;
    /** Höchstens so viele Zellen je Richtung und Teilschritt, höchstens so viele Teilschritte je Bild [G]. */
    public static final double COURANT = 0.4;
    public static final int TEILSCHRITTE = 4;
    /** Ein Schritt der Zeit über so viel (s), oder rückwärts, lässt das Feld leer neu anfangen [G]. */
    public static final double SPRUNG_S = 60;
    /** Breite des weichen Rands, über den das Wasser vom Feld in das gröbere bzw. die Statistik übergeht (Zellen) [G]. */
    public static final int RAND_FEIN = 32, RAND_GROB = 32;
    /** Bytes des Kopfs. */
    public static final int KOPF = 128;
    /** Schalter des Kernels; seit S4a-2 {@link #WINDSEE}: die Köpfe der Windsee, die die Fläche nicht auflöst. */
    public static final int QUELLE = 1, ZERFALL = 2, TRANSPORT = 4, PRUEFDRIFT = 8, DRIFT = 16, WINDSEE = 32;
    /** Seit S4b: die Zeit steht (Pause); die Quelle wirkt nur in Zellen, die neu ins Fenster kommen. */
    static final int STEHT = 64;
    private static final int PUSH = 88, GRUPPE = 16;

    private static final int[] N = {N_FEIN, N_GROB};
    private static final double[] D = {D_FEIN, D_GROB};
    private static final int PRO_KOPIE = N_FEIN * N_FEIN + N_GROB * N_GROB;
    private static final int[] BASIS = {0, N_FEIN * N_FEIN};

    private final Geraet g;
    private final Kernel kernel;
    private final Puffer feld;
    /** Die Kopie, die gilt (0 oder 1). */
    private int aktuell;
    /** Fenster je Raster: absolute Zelle (I0, J0) der Ecke; null vor dem ersten Schritt. */
    private long[] fi, fj;
    private double tLetzt = Double.NaN;
    private boolean leeren = true;

    /** Aus: kein Schritt, nichts abgelegt (das Wasser zeichnet die Statistik wie bis S3d). */
    public volatile boolean an = true;
    /** Schalter des Kernels ({@link #QUELLE}, {@link #ZERFALL}, {@link #TRANSPORT}; Prüfungen auch {@link #PRUEFDRIFT}). */
    public volatile int schalter = QUELLE | ZERFALL | TRANSPORT | WINDSEE;
    /** Prüfdrift u = (−a (x − x_p), 0): a (1/s) und x_p (Ost, absolut). */
    public volatile double pruefA, pruefOst;
    /** Lebensdauern (s) und Grenze des Watts (m), Vorgaben wie oben; Prüfungen dürfen sie ändern. */
    public volatile double tauOffen = TAU_OFFEN, tauWatt = TAU_WATT, wattBis = WATT_BIS;

    /**
     * Seit S4a-3: Faktor auf den Anteil W_A der Köpfe der Windsee; 1 wäre die Herleitung aus W_Z (S4a-2). 0,76 aus der
     * Kalibrierung im Lauf L2s (Sturm 25.10., U10 15,7 m/s: Feld gegen W_Z 1,31 mit 1, 0,99 mit 0,76; Probe Xaver,
     * U10 22,1 m/s: Bedeckung gegen Monahan 1,15).
     */
    public static final double WINDSEE_FAKTOR = 0.76;

    /** Der Faktor auf W_A (seit S4a-3); Prüfungen dürfen ihn ändern (Kalibrierung im SchaumCheck). */
    public volatile double windseeFaktor = WINDSEE_FAKTOR;

    // zur Auskunft: der letzte Schritt
    private int teileZuletzt;
    private double dtZuletzt, dtTransportZuletzt;
    private boolean gekapptZuletzt;

    public Schaumfeld(Geraet g, Konto konto) {
        this.g = g;
        feld = Puffer.speicher(g, konto, "Schaumfeld", KOPF + 4L * 2 * PRO_KOPIE);
        Kernel.Bindung[] b = new Kernel.Bindung[23];
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kernel = Kernel.lade(g, SeegangKarte.PFAD + "see_schaum.comp", PUSH, null, b);
    }

    /** Der Puffer für das Wasser ({@link #SCHAUM}). */
    public Puffer puffer() { return feld; }

    /** Der nächste {@link #schritt} fängt leer an (neues Szenario, Prüfungen). */
    public void leeren() { leeren = true; }

    /**
     * Ein Schritt bis zur Modellzeit t (s, in der Zeit der Karte) mit dem Auge (ex, ey), aufgenommen in den Ablauf a nach
     * den Stufen der Karte k ({@link SeegangKarte#bild(Ablauf, double, double)}): Transport, Zerfall, Quelle; danach der
     * Kopf für das Wasser. Steht die Zeit (Pause), ändert sich nichts; seit S4b bekommen nur Zellen, die neu ins Fenster
     * kommen, ihren Schaum aus der Quelle.
     */
    public void schritt(Ablauf a, SeegangKarte k, double t, double ex, double ey) {
        double dt = Double.isNaN(tLetzt) ? 0 : t - tLetzt;
        boolean neu = leeren || fi == null || !(dt >= 0) || dt > SPRUNG_S;
        tLetzt = t;
        if (neu) {
            dt = 0;
            leeren = false;
            a.durchgang("Schaumfeld leeren", new Ablauf.Zugriff[] {new Ablauf.Zugriff(feld, VK_PIPELINE_STAGE_2_CLEAR_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                    c -> vkCmdFillBuffer(c, feld.handle, 0, VK_WHOLE_SIZE, 0));
        }
        int sch = schalter, schK = sch | (k.hatDrift() ? DRIFT : 0) | (!neu && dt == 0 ? STEHT : 0);
        // die größte Drift je Raster: geladen, oder die Prüfdrift am Rand des Fensters
        double[] um = new double[2];
        for (int r = 0; r < 2; r++)
            if ((sch & TRANSPORT) != 0) {
                if ((sch & PRUEFDRIFT) != 0) um[r] = Math.abs(pruefA) * (Math.abs(ex - pruefOst) + N[r] * D[r] / 2.0 + D[r]);
                else if (k.hatDrift()) um[r] = k.driftMax();
            }
        int teile = 1;
        for (int r = 0; r < 2; r++)
            if (dt > 0 && um[r] > 0) teile = (int) Math.max(teile, Math.min(TEILSCHRITTE, Math.ceil(um[r] * dt / (COURANT * D[r]))));
        double dtZ = dt / teile;
        double[] dtT = new double[2];
        for (int r = 0; r < 2; r++) dtT[r] = um[r] > 0 ? Math.min(dtZ, COURANT * D[r] / um[r]) : dtZ;
        teileZuletzt = teile;
        dtZuletzt = dt;
        dtTransportZuletzt = dtT[0] * teile;
        gekapptZuletzt = dtT[0] < dtZ - 1e-12;
        // die neuen Fenster
        long[] ni = new long[2], nj = new long[2];
        int[][] schub = new int[2][2];
        for (int r = 0; r < 2; r++) {
            ni[r] = (long) Math.floor(ex / D[r]) - N[r] / 2;
            nj[r] = (long) Math.floor(ey / D[r]) - N[r] / 2;
            if (neu || fi == null) { schub[r][0] = N[r]; schub[r][1] = N[r]; }  // nichts lag vorher drin (das Feld ist ohnehin leer)
            else {
                schub[r][0] = (int) Math.max(-N[r], Math.min(N[r], ni[r] - fi[r]));
                schub[r][1] = (int) Math.max(-N[r], Math.min(N[r], nj[r] - fj[r]));
            }
        }
        Mittel[] lesen = k.lesenSchaum();
        long satz = kernel.satz(k.belegungSchaum(feld));
        for (int s = 0; s < teile; s++) {
            for (int r = 0; r < 2; r++) {
                int n = N[r];
                ByteBuffer p = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
                p.putFloat(0, (float) (ni[r] * D[r] - k.bx)).putFloat(4, (float) (nj[r] * D[r] - k.by)).putFloat(8, (float) D[r]).putInt(12, n);
                p.putInt(16, (int) Math.floorMod(ni[r], (long) n)).putInt(20, (int) Math.floorMod(nj[r], (long) n));
                p.putInt(24, s == 0 ? schub[r][0] : 0).putInt(28, s == 0 ? schub[r][1] : 0);
                p.putInt(32, KOPF / 4 + aktuell * PRO_KOPIE + BASIS[r]).putInt(36, KOPF / 4 + (1 - aktuell) * PRO_KOPIE + BASIS[r]);
                p.putFloat(40, (float) dtT[r]).putFloat(44, (float) dtZ).putInt(48, schK);
                p.putFloat(52, (float) pruefA).putFloat(56, (float) (pruefOst - k.bx));
                p.putFloat(60, (float) tauOffen).putFloat(64, (float) tauWatt).putFloat(68, (float) wattBis);
                p.putFloat(72, (float) Math.floorMod((long) Math.floor(k.bx), 4096L)).putFloat(76, (float) Math.floorMod((long) Math.floor(k.by), 4096L));
                p.putFloat(80, (float) (((t % 4096) + 4096) % 4096)).putFloat(84, (float) windseeFaktor);
                int gr = Kernel.gruppen(n, GRUPPE);
                a.rechne("see_schaum " + (r == 0 ? "fein" : "grob"), kernel, satz, p, gr, gr, 1, lesen, new Mittel[] {feld});
            }
            aktuell = 1 - aktuell;
        }
        fi = ni;
        fj = nj;
        ByteBuffer kb = kopfBytes(ex, ey);
        a.durchgang("Schaumfeld, Kopf", new Ablauf.Zugriff[] {new Ablauf.Zugriff(feld, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, feld.handle, 0, kb));
    }

    /** Der Kopf für das Wasser (Inhalt siehe Klasse), relativ zum Auge (ex, ey). */
    ByteBuffer kopfBytes(double ex, double ey) {
        ByteBuffer k = ByteBuffer.allocateDirect(KOPF).order(ByteOrder.nativeOrder());
        k.putInt(fi != null ? 1 : 0).putInt(aktuell * PRO_KOPIE).putInt(0).putInt(0);
        for (int r = 0; r < 2; r++) {
            long i0 = fi != null ? fi[r] : 0, j0 = fj != null ? fj[r] : 0;
            k.putFloat((float) (i0 * D[r] - ex)).putFloat((float) (j0 * D[r] - ey)).putFloat((float) D[r]).putFloat(0);
            k.putInt((int) Math.floorMod(i0, (long) N[r])).putInt((int) Math.floorMod(j0, (long) N[r])).putInt(BASIS[r]).putInt(N[r]);
        }
        k.putFloat(RAND_FEIN).putFloat(RAND_GROB).putFloat(0).putFloat(0);
        while (k.position() < KOPF) k.putFloat(0);
        k.flip();
        return k;
    }

    // ------------------------------------------------------------------ zur Prüfung (Karte in Ruhe)

    /** Die geltenden Werte des Rasters r (0 fein, 1 grob) in Fensterordnung: Index j · n + i, i nach Ost, j nach Nord. */
    public float[] lies(int r) {
        if (fi == null) return new float[N[r] * N[r]];
        float[] alle = feld.floats(KOPF / 4 + 2 * PRO_KOPIE);
        int n = N[r], ri = (int) Math.floorMod(fi[r], (long) n), rj = (int) Math.floorMod(fj[r], (long) n), b = KOPF / 4 + aktuell * PRO_KOPIE + BASIS[r];
        float[] w = new float[n * n];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) w[j * n + i] = alle[b + ((rj + j) % n) * n + (ri + i) % n];
        return w;
    }

    /** Setzt die geltenden Werte des Rasters r in Fensterordnung (wie {@link #lies}); nach einem ersten {@link #schritt}. */
    public void setze(int r, float[] w) {
        if (fi == null) throw new IllegalStateException("Schaumfeld: erst ein Schritt, dann setzen");
        int n = N[r];
        if (w.length != n * n) throw new IllegalArgumentException("Schaumfeld: " + w.length + " statt " + n * n);
        float[] alle = feld.floats(KOPF / 4 + 2 * PRO_KOPIE);
        int ri = (int) Math.floorMod(fi[r], (long) n), rj = (int) Math.floorMod(fj[r], (long) n), b = KOPF / 4 + aktuell * PRO_KOPIE + BASIS[r];
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) alle[b + ((rj + j) % n) * n + (ri + i) % n] = w[j * n + i];
        feld.schreibe(alle);
    }

    /** Masse des Rasters r: Σ f d² (m²; die frisch bedeckte Fläche zählt 1 je m²). */
    public double masse(int r) {
        double s = 0;
        for (float v : lies(r)) s += v;
        return s * D[r] * D[r];
    }

    /** Ecke (Ost, Nord, absolut) der Zelle (0, 0) des Rasters r; null vor dem ersten Schritt. */
    public double[] ecke(int r) { return fi == null ? null : new double[] {fi[r] * D[r], fj[r] * D[r]}; }

    public static int zellen(int r) { return N[r]; }

    public static double abstand(int r) { return D[r]; }

    /** Auskunft zum letzten Schritt: {Teilschritte, Δt (s), Weg des Transports in Zeit (s), gekappt 0/1}. */
    public double[] zuletzt() { return new double[] {teileZuletzt, dtZuletzt, dtTransportZuletzt, gekapptZuletzt ? 1 : 0}; }

    /** Speicher auf der Karte (Byte). */
    public long bytes() { return feld.groesse; }

    @Override public void close() {
        kernel.close();
        feld.close();
    }
}
