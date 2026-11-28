package com.dan.render.seegang;

import com.dan.core.gpu.Ablauf;
import com.dan.core.gpu.Geraet;
import com.dan.core.gpu.Kernel;
import com.dan.core.gpu.Konto;
import com.dan.core.gpu.Mittel;
import com.dan.core.gpu.Puffer;
import com.dan.core.gpu.Stoppuhr;
import com.dan.core.szene.Dispersion;
import com.dan.core.szene.Oberflaeche;
import com.dan.core.szene.Phasenfeld;
import com.dan.core.szene.Profilpuffer;
import com.dan.core.szene.Seegangsform;
import com.dan.core.szene.Seegangsoptik;
import com.dan.core.szene.Wasseroptik;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;

/**
 * Die Oberfläche des Seegangs auf der Karte (Seegang Werkbuch, S3b): dieselbe Rechnung wie
 * {@link Oberflaeche#punkt} in float ({@code see_gemein.glsl}), für eine Liste von Punkten ({@code see_punkte}) und für
 * die Stufen einer Clipmap um das Auge ({@code see_stufe}, dann Normale und J in {@code see_normale}).
 * <p>
 * <b>Genauigkeit.</b> Orte sind relativ zu einem Bezugspunkt B nahe dem Auge. Was groß wird, rechnet der Prozessor je
 * Bild in double vor ({@link #zeit}): je Stützstelle die Phase k₀ (B − U) · e − ω t modulo 2π und die Laufzeit
 * (B − U) · e / c_g₀ − t modulo der Periode der Hülle (U der Ursprung der Phasenfelder), je Welle des Fernfelds
 * k · B + φ₀ − ω t modulo 2π. Wandert das Auge weit, wird B neu gesetzt ({@link #bezug}).
 * <p>
 * <b>Eingaben</b> ({@link #eingaben}): je Stufe des Nahfelds die gezeichnete Varianz und die Kennwerte der Form je
 * Zelle ({@link Oberflaeche#vorbereiten}), Tiefe und Gefälle, ein Phasenfeld ({@link Oberflaeche.Stufe#pa}; zwei Stände
 * und Wechsel blendet der Lauf auf dem Prozessor und lädt das Ergebnis, SF21 A); einmal die Hüllen und die Tafel nach
 * Battjes–Groenendijk. Das Fernfeld gilt je Bild mit Hs, Tp, Richtung am Bezugspunkt [G].
 * <p>
 * <b>Clipmap</b> [G]: {@code stufen} Stufen zu n × n Stellen, Abstand d₀ 2^L, die Mitte auf 2 d_L gerastert. Jede Stufe
 * zeichnet nur die Wellen, die sie auflöst (L ≥ 4 d_L voll ab 8 d_L, {@link Oberflaeche#aufgeloest}); die Form kommt aus
 * dem ganzen Spektrum. Ausgabe je Stelle: Verschiebung (ξx, ξy, η, |Z'|) und Normale (n, J).
 */
public final class SeegangKarte implements AutoCloseable {
    public static final String PFAD = "/com/dan/render/seegang/";
    private static final int PUSH = 16, GRUPPE = 64, BG_N = 512;
    /** Nur zur Messung der Rechenzeit (Befund B38), nach {@link #zeit} wirksam: ohne Hülle, ohne Phasenfeld, Energie aus einer Ecke. */
    public static final int D_HUELLE = 16, D_PHASE = 32, D_ENERGIE = 64, PHASE_JE_ZELLE = 128, E_JE_ZELLE = 256;
    /** Bits aus D_*; 0 im Betrieb. */
    public int diagnose;
    /** Zur Messung: eine Stufe des Nahfelds nicht lesen (nach {@link #zeit} wirksam). */
    public boolean ohneFein, ohneGrob;
    /**
     * Seit S3d (SF35 A, Seegang Werkbuch 27/28): Gewicht des Nahfelds (0 … 1) gegen das Fernfeld, für das Überblenden in
     * der Anwendung; 1 wie bisher (mit {@link #zeit} wirksam).
     */
    public volatile float nahGewicht = 1f;
    /**
     * Seit S3d: der Ort, an dem das Fernfeld seine Werte nimmt (UTM); NaN: am Bezug B wie bisher. Die Anwendung setzt ihn je
     * Bild aufs Auge, damit das Fernfeld beim Wechsel der Fläche nicht springt (mit {@link #zeit} wirksam).
     */
    public volatile double fernOst = Double.NaN, fernNord = Double.NaN;
    /**
     * Alle Stufen der Clipmap in einer Abgabe (Vorgabe, Befund B38) statt einer je Stufe: Eine Stufe allein hat nur
     * 128² Fäden, zu wenige, um die Wartezeit auf den Speicher zu verdecken.
     */
    public boolean eineAbgabe = true;
    /**
     * Phasenfelder je Zelle ablegen (alle Stützstellen einer Zelle beieinander) statt je Stützstelle; dasselbe Bild,
     * zur Messung (B38). Wirkt mit dem nächsten Laden der Phasen ({@link #eingaben}, {@link #phasen}).
     */
    public boolean phaseJeZelle;
    private boolean geladenJeZelle;
    /**
     * Varianz und Kennwerte je Zelle ablegen, wie bis L2c (alle Stützstellen einer Zelle beieinander), statt je Stützstelle
     * bzw. Kennwert (Vorgabe: benachbarte Fäden lesen benachbarte Zellen; L2c: die Varianz zu lesen kostete 4,2 von 7,6 ms,
     * B38). Dasselbe Bild, zur Messung. Wirkt mit dem nächsten {@link #eingaben}.
     */
    public boolean energieJeZelle;
    private boolean geladenEJeZelle;
    /**
     * Schwellen der Stützstellen relativ zu m0 und zur Neigung S (Vorgaben {@link Oberflaeche#E_REL}, {@link
     * Oberflaeche#S_REL}); nur zur Messung anders, dann weicht die Karte von der Referenz ab. Nach einer Änderung
     * {@link #eingaben} rufen (die Liste der aktiven Stützstellen hängt daran).
     */
    public double schwelle = Oberflaeche.E_REL, neigung = Oberflaeche.S_REL;
    private static final double BG_VON = Seegangsform.HTR_MIN, BG_BIS = 4.0;
    /** Indizes in Werte (wie see_gemein.glsl). */
    static final int I_NB = 0, I_ND = 1, I_N = 2, I_SCHALTER = 3, I_FEIN = 4, I_GROB = 5, I_FERN = 6, I_PUNKTE = 7,
            I_F_NX = 8, I_F_NY = 9, I_F_RAND = 10, I_G_NX = 11, I_G_NY = 12, I_G_RAND = 13, I_STUFEN = 14, I_BG = 15, I_FERN_N = 16, I_RAND = 17;
    static final int F_LAMBDA = 0, F_FALTUNG = 1, F_TIEFE_FERN = 2, F_E_MIN = 3, F_F_X0 = 4, F_F_Y0 = 5, F_F_DX = 6, F_G_X0 = 7, F_G_Y0 = 8, F_G_DX = 9,
            F_FERN_M0 = 10, F_FERN_SXI2 = 11, F_FERN_KP = 12, F_FERN_W = 13, F_BG_VON = 14, F_BG_BIS = 15, F_LAGRANGE = 16, F_LW_NENNER = 17,
            F_D0 = 18, F_MITTE = 19, F_E_REL = 35, F_K_UNTEN = 36, F_S_REL = 52, F_NAH = 53;
    /** Höchste Zahl der Bänder (Platz für die untere Schranke von |k| in den Werten). */
    public static final int NB_MAX = 16;

    public final Oberflaeche o;
    public final int stufen, n, nb, nd;
    public final double d0;
    private final Geraet g;
    private final Konto konto;
    private final boolean eigenesGeraet;
    private final Puffer werte, stuetz, huellen, fern, bg, versatz, normale, aufg;
    private Puffer eF, eG, pF, pG, zF, zG, kF, kG, punkte, aus, aktiv;
    /**
     * Seit S4a (Seegang Werkbuch 35): je Stelle der Clipmap der Kamm (λ der Verschiebung, k_p h; Bindung 19) und je Zelle
     * des Nahfelds die Drift des Schaums (Strömung, Stokes-Drift, Wind; vec2, fein und grob), für {@link Schaumfeld}.
     */
    private final Puffer kamm;
    private Puffer driftF, driftG;
    private boolean hatDrift;
    private double driftMax, windMax;
    /** Zahl der Stützstellen in der Liste (Befund B41), zur Auskunft. */
    private int aktivZahl;
    /** Zur Messung: alle Stützstellen in die Liste (wie bis L2e), wirkt mit dem nächsten {@link #eingaben}. */
    public boolean alleStuetzstellen;
    /** Zur Messung: nur die Stufen [stufenVon, stufenBis) rechnen (eine Abgabe). */
    public int stufenVon = 0, stufenBis = Integer.MAX_VALUE;
    /** Die geladenen Phasenfelder und die untere Schranke von |k| je Band daraus. */
    private Phasenfeld phF, phG;
    private final double[] kUnten = new double[NB_MAX];
    private final Kernel kPunkte, kStufe, kNormale;
    private final double[][] wellen = Seegangsoptik.wellen();
    private final int[] ii = new int[32];
    private final float[] ff = new float[64];
    /** Bezugspunkt B (m, wie die Stufen), Mitten der Stufen relativ zu B. */
    public double bx, by;
    private final double[] mitten;
    private double zeit = Double.NaN;

    public SeegangKarte(Geraet g, Konto konto, Oberflaeche o, int stufen, int n, double d0, boolean eigenesGeraet) {
        if (stufen > 8) throw new IllegalArgumentException("SeegangKarte: höchstens 8 Stufen");
        this.g = g; this.konto = konto; this.o = o; this.stufen = stufen; this.n = n; this.d0 = d0; this.eigenesGeraet = eigenesGeraet;
        nb = o.nb; nd = o.nd;
        mitten = new double[2 * stufen];
        werte = Puffer.speicher(g, konto, "see Werte", 4 * (32 + 64));
        stuetz = Puffer.speicher(g, konto, "see Stützstellen", 32L * nb * nd);
        huellen = Puffer.speicher(g, konto, "see Hüllen", 16L * Profilpuffer.N * nb * nd);
        fern = Puffer.speicher(g, konto, "see Fernfeld", 16L * Seegangsoptik.LANG);
        bg = Puffer.speicher(g, konto, "see BG", 16L * BG_N);
        versatz = Puffer.speicher(g, konto, "see Versatz", 16L * stufen * n * n);
        normale = Puffer.speicher(g, konto, "see Normale", 16L * stufen * n * n);
        aufg = Puffer.speicher(g, konto, "see Aufgelöst", 8L * stufen * n * n);
        kamm = Puffer.speicher(g, konto, "see Kamm", 16L * stufen * n * n);   // seit S5a vier Werte je Stelle
        driftF = Puffer.speicher(g, konto, "see Drift fein", 16);
        driftG = Puffer.speicher(g, konto, "see Drift grob", 16);
        eF = eG = pF = pG = zF = zG = kF = kG = null;
        punkte = Puffer.speicher(g, konto, "see Punkte", 16);
        aus = Puffer.speicher(g, konto, "see Aus", 32);
        aktiv = Puffer.speicher(g, konto, "see Aktiv", 16);
        Kernel.Bindung[] b = new Kernel.Bindung[20];                        // seit S4a: 19 der Kamm
        Arrays.fill(b, Kernel.Bindung.PUFFER);
        kPunkte = Kernel.lade(g, PFAD + "see_punkte.comp", PUSH, null, b);
        kStufe = Kernel.lade(g, PFAD + "see_stufe.comp", PUSH, null, b);
        kNormale = Kernel.lade(g, PFAD + "see_normale.comp", PUSH, null, b);
        hoch(o.puffer);
        bgTafel();
    }

    /** Mit eigenem Gerät (für Prüfungen); schließt es mit. */
    public static SeegangKarte mitGeraet(Oberflaeche o, int stufen, int n, double d0) {
        Geraet g = new Geraet();
        return new SeegangKarte(g, g.konto("seegang", 2L << 30), o, stufen, n, d0, true);
    }

    public Geraet geraet() { return g; }

    // ------------------------------------------------------------------ einmal

    private void hoch(Profilpuffer p) {
        float[] f = new float[4 * Profilpuffer.N * nb * nd];
        for (int bq = 0; bq < nb * nd; bq++) {
            double[] gb = p.g[bq];
            int o0 = bq * 4 * Profilpuffer.N;
            for (int j = 0; j < 4 * Profilpuffer.N; j++) f[o0 + j] = (float) gb[j];
        }
        huellen.schreibe(f);
    }

    private void bgTafel() {
        float[] t = new float[4 * BG_N];
        for (int i = 0; i < BG_N; i++) {
            double htr = BG_VON + (BG_BIS - BG_VON) * i / (BG_N - 1.0);
            double[] p3 = Seegangsform.bg(htr);
            t[4 * i] = (float) p3[0]; t[4 * i + 1] = (float) p3[1]; t[4 * i + 2] = (float) p3[2];
        }
        bg.schreibe(t);
    }

    private Puffer groesse(Puffer p, String name, long bytes) {
        if (p != null && p.groesse >= bytes) return p;
        if (p != null) p.close();
        return Puffer.speicher(g, konto, name, Math.max(16, bytes));
    }

    // ------------------------------------------------------------------ Eingaben

    /**
     * Die Stufen des Nahfelds auf die Karte: gezeichnete Varianz, Kennwerte, Tiefe und Gefälle, das Phasenfeld (pa).
     * Ruft {@link Oberflaeche#vorbereiten}.
     */
    public void eingaben() {
        o.vorbereiten();
        Oberflaeche.Stufe f = o.fein, gr = o.grob;
        int m = nb * nd;
        if (f != null) { eF = laden(eF, "see E fein", f.eZ, f.nc, m); pF = phase(pF, "see Phase fein", f.pa); zF = zelle(zF, "see Zelle fein", f); kF = laden(kF, "see Kenn fein", f.kenn, f.nc, f.kennWerte()); }
        if (gr != null) { eG = laden(eG, "see E grob", gr.eZ, gr.nc, m); pG = phase(pG, "see Phase grob", gr.pa); zG = zelle(zG, "see Zelle grob", gr); kG = laden(kG, "see Kenn grob", gr.kenn, gr.nc, gr.kennWerte()); }
        geladenEJeZelle = energieJeZelle;
        aktivListe();
        if (eF == null) { eF = Puffer.speicher(g, konto, "see E fein", 16); pF = Puffer.speicher(g, konto, "see Phase fein", 16); zF = Puffer.speicher(g, konto, "see Zelle fein", 16); kF = Puffer.speicher(g, konto, "see Kenn fein", 16); }
        if (eG == null) { eG = Puffer.speicher(g, konto, "see E grob", 16); pG = Puffer.speicher(g, konto, "see Phase grob", 16); zG = Puffer.speicher(g, konto, "see Zelle grob", 16); kG = Puffer.speicher(g, konto, "see Kenn grob", 16); }
        phF = f != null ? f.pa : null;
        phG = gr != null ? gr.pa : null;
        kUnten();
    }

    /**
     * Die Liste der Stützstellen, die in irgendeiner Zelle einer Stufe über einer der Schwellen liegen ({@link
     * Oberflaeche#aktiv}, Befund B41, SF23 A): je Band ihr Anfang, dann die Nummern. Die Referenz zählt nur diese; die
     * Synthese geht nur diese durch und liest für die übrigen keine Ecken.
     */
    private void aktivListe() {
        int m = nb * nd;
        boolean[] an = schwelle == Oberflaeche.E_REL && neigung == Oberflaeche.S_REL && o.aktiv != null ? o.aktiv.clone() : o.aktiv(schwelle, neigung);
        if (alleStuetzstellen) java.util.Arrays.fill(an, true);
        int[] l = new int[nb + 1 + m];
        int z = 0;
        for (int b = 0; b < nb; b++) {
            l[b] = z;
            for (int q = 0; q < nd; q++) if (an[b * nd + q]) l[nb + 1 + z++] = b * nd + q;
        }
        l[nb] = z;
        aktivZahl = z;
        aktivAn = an;
        aktiv = groesse(aktiv, "see Aktiv", 4L * l.length);
        aktiv.schreibe(l);
    }

    /** Seit S7d4: welche Stützstellen in der Liste der aktiven stehen (Index b · nd + q), für die Körpersuche. */
    private boolean[] aktivAn;

    boolean[] aktivAn() { return aktivAn; }

    /**
     * Seit S7d4 (Seegang Werkbuch 92): was die Körpersuche aus einer Körperfläche liest (Bindungen 26–31 von see_koerper):
     * Varianz fein und grob, Zelle fein und grob, Kennwerte fein und grob.
     */
    Puffer[] koerperPuffer() { return new Puffer[] {eF, eG, zF, zG, kF, kG}; }

    /** Seit S7d4: ob Varianz und Kennwerte je Zelle liegen (wie {@link #energieJeZelle} beim letzten {@link #eingaben}). */
    boolean energieJeZelleGeladen() { return geladenEJeZelle; }

    /** Zahl der Stützstellen in der Liste der aktiven (von nb · nd). */
    public int aktivZahl() { return aktivZahl; }

    /** Nur das Phasenfeld einer Stufe neu laden (der Lauf blendet Stände und Wechsel auf dem Prozessor). */
    public void phasen(Oberflaeche.Stufe s, Phasenfeld p) {
        if (s == o.fein) { pF = phase(pF, "see Phase fein", p); phF = p; }
        else { pG = phase(pG, "see Phase grob", p); phG = p; }
        kUnten();
    }

    /** Die untere Schranke von |k| des Bands b (rad/m), zur Auskunft. */
    public double kUnten(int b) { return kUnten[b]; }

    /**
     * Je Band die kleinste Wellenzahl der Dispersion über die geladenen Phasenfelder ({@link Phasenfeld#kMin}, Befund
     * B34): Die Synthese misst die Auflösung mit max(|∇Φ|, kUnten), und ist 2π / kUnten kleiner als 4 d, übergeht sie
     * das Band. Die Referenz ({@link Oberflaeche#kUnten}) nimmt die Felder der Stufen; lädt der Lauf ein anderes Feld
     * ({@link #phasen}), zählt dessen kMin.
     */
    private void kUnten() {
        if (nb > NB_MAX) throw new IllegalArgumentException("höchstens " + NB_MAX + " Bänder");
        java.util.Arrays.fill(kUnten, Double.POSITIVE_INFINITY);
        for (Phasenfeld p : new Phasenfeld[] {phF, phG})
            if (p != null && p.kMin != null) for (int b = 0; b < nb; b++) kUnten[b] = Math.min(kUnten[b], p.kMin[b]);
        for (int b = 0; b < nb; b++) if (Double.isInfinite(kUnten[b])) kUnten[b] = 1e30;
    }

    /** d liegt je Zelle (c · n + j); auf die Karte je j (j · nc + c), außer mit {@link #energieJeZelle}. */
    private Puffer laden(Puffer p, String name, double[] d, int nc, int n) {
        p = groesse(p, name, 4L * d.length);
        float[] f = new float[d.length];
        for (int i = 0; i < d.length; i++) f[energieJeZelle ? i : (i % n) * nc + i / n] = (float) d[i];
        p.schreibe(f);
        return p;
    }

    private Puffer phase(Puffer p, String name, Phasenfeld ph) {
        p = groesse(p, name, 8L * ph.dPhi.length);
        float[] f = new float[2 * ph.dPhi.length];
        int m = nb * nd, nc = ph.dPhi.length / m;
        for (int i = 0; i < ph.dPhi.length; i++) {
            int j = phaseJeZelle ? (i % nc) * m + i / nc : i;                   // i = bq · nc + c
            f[2 * j] = (float) ph.dPhi[i]; f[2 * j + 1] = (float) ph.dT[i];
        }
        p.schreibe(f);
        geladenJeZelle = phaseJeZelle;
        return p;
    }

    private Puffer zelle(Puffer p, String name, Oberflaeche.Stufe s) {
        p = groesse(p, name, 8L * s.nc);
        float[] f = new float[2 * s.nc];
        for (int c = 0; c < s.nc; c++) { f[2 * c] = (float) s.h[c]; f[2 * c + 1] = (float) s.gefaelle[c]; }
        p.schreibe(f);
        return p;
    }

    /** Setzt den Bezugspunkt B (vor {@link #zeit}). */
    public void bezug(double x, double y) { bx = x; by = y; zeit = Double.NaN; }

    // ------------------------------------------------------------------ je Bild

    /** Die Werte je Bild zur Zeit t: Phasen und Laufzeiten bei B, Fernfeld, Mitten der Stufen. */
    public void zeit(double t) {
        rechneZeit(t);
        stuetz.schreibe(stZeit);
        fern.schreibe(feZeit);
        werteSchreiben(0);
    }

    /**
     * Wie {@link #zeit(double)}, aber über den Ablauf (vkCmdUpdateBuffer) statt sofort: im Rahmen liest ein früheres Bild
     * die Puffer vielleicht noch (S3c). Die Werte nimmt danach {@link #stufen(Ablauf, double, double)} auf.
     */
    public void zeit(Ablauf ab, double t) {
        rechneZeit(t);
        ByteBuffer bs = ByteBuffer.allocateDirect(4 * stZeit.length).order(ByteOrder.nativeOrder()), bf = ByteBuffer.allocateDirect(4 * feZeit.length).order(ByteOrder.nativeOrder());
        bs.asFloatBuffer().put(stZeit);
        bf.asFloatBuffer().put(feZeit);
        ab.durchgang("see Zeit", new Ablauf.Zugriff[] {
                new Ablauf.Zugriff(stuetz, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0),
                new Ablauf.Zugriff(fern, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)}, c -> {
            vkCmdUpdateBuffer(c, stuetz.handle, 0, bs);
            vkCmdUpdateBuffer(c, fern.handle, 0, bf);
        });
    }

    private float[] stZeit, feZeit;

    private void rechneZeit(double t) {
        zeit = t;
        Phasenfeld ref = o.grob != null ? o.grob.pa : o.fein.pa;
        float[] st = new float[8 * nb * nd];
        for (int b = 0; b < nb; b++) {
            double w = ref.omega[b], k0 = ref.k0[b], cg0 = ref.cg0[b];
            for (int q = 0; q < nd; q++) {
                int bq = b * nd + q;
                double th = ref.richtung[bq], ex = Math.cos(th), ey = Math.sin(th);
                double sB = (bx - ref.ursprungX) * ex + (by - ref.ursprungY) * ey;
                double ph = mod(k0 * sB - w * t, 2 * Math.PI), tp = o.puffer.periode[bq], ta = mod(sB / cg0 - t, tp);
                st[8 * bq] = (float) ex; st[8 * bq + 1] = (float) ey; st[8 * bq + 2] = (float) ph; st[8 * bq + 3] = (float) ta;
                st[8 * bq + 4] = (float) w; st[8 * bq + 5] = (float) k0; st[8 * bq + 6] = (float) cg0; st[8 * bq + 7] = (float) tp;
            }
        }
        stZeit = st;
        // Fernfeld am Bezugspunkt
        float[] fe = new float[4 * Seegangsoptik.LANG];
        double m0f = 0, sxi2f = 0, kpf = 0;
        if (o.fernfeld != null) {
            double fo = fernOst, fn = fernNord;
            double[] w = Double.isNaN(fo) || Double.isNaN(fn) ? o.fernfeld.werte(bx, by) : o.fernfeld.werte(fo, fn);
            double[] amp = Seegangsoptik.amplituden(w[0], w[1], w[2], 1e-4, wellen, Seegangsoptik.baender(), Seegangsoptik.cdf());
            for (int j = 0; j < Seegangsoptik.LANG; j++) {
                double[] q = wellen[j];
                double k = Math.hypot(q[0], q[1]);
                fe[4 * j] = (float) q[0]; fe[4 * j + 1] = (float) q[1]; fe[4 * j + 2] = (float) amp[j];
                fe[4 * j + 3] = (float) mod(q[0] * bx + q[1] * by + q[2] - Wasseroptik.omega(k) * t, 2 * Math.PI);
                m0f += 0.5 * amp[j] * amp[j];
                sxi2f += k * k * 0.5 * amp[j] * amp[j];
            }
            if (w[1] > 0) kpf = Math.pow(2 * Math.PI / w[1], 2) / Dispersion.G;
        }
        feZeit = fe;
        Arrays.fill(ii, 0);
        Arrays.fill(ff, 0);
        ii[I_NB] = nb; ii[I_ND] = nd; ii[I_N] = n;
        ii[I_SCHALTER] = (o.mitForm ? 1 : 0) | (o.mitHoehen ? 2 : 0) | (o.mitLangwelle ? 4 : 0) | (o.mitVerschiebung ? 8 : 0) | (diagnose & (D_HUELLE | D_PHASE | D_ENERGIE)) | (geladenJeZelle ? PHASE_JE_ZELLE : 0) | (geladenEJeZelle ? E_JE_ZELLE : 0);
        ii[I_FEIN] = o.fein != null && !ohneFein ? 1 : 0; ii[I_GROB] = o.grob != null && !ohneGrob ? 1 : 0; ii[I_FERN] = o.fernfeld != null ? 1 : 0;
        ii[I_STUFEN] = stufen; ii[I_BG] = BG_N; ii[I_FERN_N] = Seegangsoptik.LANG; ii[I_RAND] = o.fernfeld != null ? randFern : 0;
        if (o.fein != null) {
            Oberflaeche.Stufe s = o.fein;
            ii[I_F_NX] = s.nx; ii[I_F_NY] = s.ny; ii[I_F_RAND] = s.rand;
            ff[F_F_X0] = (float) (s.x0 - bx); ff[F_F_Y0] = (float) (s.y0 - by); ff[F_F_DX] = (float) s.dx;
        }
        if (o.grob != null) {
            Oberflaeche.Stufe s = o.grob;
            ii[I_G_NX] = s.nx; ii[I_G_NY] = s.ny; ii[I_G_RAND] = s.rand;
            ff[F_G_X0] = (float) (s.x0 - bx); ff[F_G_Y0] = (float) (s.y0 - by); ff[F_G_DX] = (float) s.dx;
        }
        ff[F_LAMBDA] = (float) o.lambda; ff[F_FALTUNG] = (float) o.faltung; ff[F_TIEFE_FERN] = (float) o.tiefeFern; ff[F_E_MIN] = (float) Oberflaeche.E_MIN; ff[F_E_REL] = (float) schwelle; ff[F_S_REL] = (float) neigung;
        ff[F_NAH] = nahGewicht;
        ff[F_FERN_M0] = (float) m0f; ff[F_FERN_SXI2] = (float) sxi2f; ff[F_FERN_KP] = (float) kpf;
        ff[F_BG_VON] = (float) BG_VON; ff[F_BG_BIS] = (float) BG_BIS; ff[F_LAGRANGE] = (float) Seegangsform.LAGRANGE; ff[F_LW_NENNER] = (float) Seegangsform.LW_NENNER;
        ff[F_D0] = (float) d0;
        for (int b = 0; b < nb; b++) ff[F_K_UNTEN + b] = (float) kUnten[b];
        for (int l = 0; l < stufen; l++) { ff[F_MITTE + 2 * l] = (float) mitten[2 * l]; ff[F_MITTE + 2 * l + 1] = (float) mitten[2 * l + 1]; }
    }

    /** Die Werte über den Ablauf (vkCmdUpdateBuffer): im Rahmen liest ein früheres Bild sie vielleicht noch. */
    private void werteAufnehmen(Ablauf ab) {
        ii[I_PUNKTE] = 0;
        ByteBuffer p = ByteBuffer.allocateDirect(4 * (32 + 64)).order(ByteOrder.nativeOrder());
        for (int v : ii) p.putInt(v);
        for (float v : ff) p.putFloat(v);
        p.flip();
        ab.durchgang("see Werte", new Ablauf.Zugriff[] {new Ablauf.Zugriff(werte, VK_PIPELINE_STAGE_2_ALL_TRANSFER_BIT, VK_ACCESS_2_TRANSFER_WRITE_BIT, 0)},
                c -> vkCmdUpdateBuffer(c, werte.handle, 0, p));
    }

    private void werteSchreiben(int punkteZahl) {
        ii[I_PUNKTE] = punkteZahl;
        ByteBuffer p = ByteBuffer.allocateDirect(4 * (32 + 64)).order(ByteOrder.nativeOrder());
        for (int v : ii) p.putInt(v);
        for (float v : ff) p.putFloat(v);
        p.flip();
        werte.schreibe(p, 0);
    }

    static double mod(double x, double m) { double r = x % m; return r < 0 ? r + m : r; }

    private long satz(Kernel k) {
        return k.satz(werte, stuetz, huellen, eF, eG, pF, pG, zF, zG, kF, kG, fern, bg, punkte, aus, versatz, normale, aktiv, aufg, kamm);
    }

    // ------------------------------------------------------------------ seit S4a: für das Schaumfeld

    /**
     * Die Drift des Schaums je Zelle des Nahfelds und seit S4a-2 der Wind (m/s, je Zelle Drift Ost, Nord, U10 Ost, Nord;
     * Länge 4 nc der Stufe), fein und grob; null: keine. Sie liegt auf der Karte, bis die nächste kommt.
     */
    public void drift(float[] fein, float[] grob) {
        Oberflaeche.Stufe f = o.fein, gr = o.grob;
        if (fein != null && f != null && fein.length != 4 * f.nc) throw new IllegalArgumentException("Drift fein: " + fein.length + " statt " + 4 * f.nc);
        if (grob != null && gr != null && grob.length != 4 * gr.nc) throw new IllegalArgumentException("Drift grob: " + grob.length + " statt " + 4 * gr.nc);
        double m = 0, w = 0;
        if (fein != null && f != null) { driftF = groesse(driftF, "see Drift fein", 4L * fein.length); driftF.schreibe(fein); m = Math.max(m, betrag(fein, 0)); w = Math.max(w, betrag(fein, 2)); }
        if (grob != null && gr != null) { driftG = groesse(driftG, "see Drift grob", 4L * grob.length); driftG.schreibe(grob); m = Math.max(m, betrag(grob, 0)); w = Math.max(w, betrag(grob, 2)); }
        hatDrift = (fein != null && f != null) || (grob != null && gr != null);
        driftMax = m;
        windMax = w;
    }

    private static double betrag(float[] d, int o) {
        double m = 0;
        for (int i = 0; i + 3 < d.length; i += 4) m = Math.max(m, Math.hypot(d[i + o], d[i + o + 1]));
        return m;
    }

    /** Ist eine Drift geladen ({@link #drift})? */
    public boolean hatDrift() { return hatDrift; }

    /** Der größte Betrag der geladenen Drift (m/s), 0 ohne. */
    public double driftMax() { return driftMax; }

    /** Der größte Betrag von U10 in der geladenen Drift (m/s, Werte 3 und 4 je Zelle; seit S4a-3, zur Auskunft), 0 ohne. */
    public double windMax() { return windMax; }

    /** Kamm der Stufe L je Stelle: λ der Verschiebung, k_p h (seit S4a), Weißwasser w und Dicke t der Walze (seit S5a); vier Werte. */
    public float[] kamm(int l) {
        float[] all = kamm.floats(4 * stufen * n * n);
        return Arrays.copyOfRange(all, 4 * l * n * n, 4 * (l + 1) * n * n);
    }

    /** Die Belegung 0–21 des Schaumfelds (see_schaum.comp), dazu an 22 der Puffer des Felds. */
    Object[] belegungSchaum(Puffer feld) {
        return new Object[] {werte, stuetz, huellen, eF, eG, pF, pG, zF, zG, kF, kG, fern, bg, punkte, aus, versatz, normale, aktiv, aufg, kamm, driftF, driftG, feld};
    }

    /** Was das Schaumfeld von der Karte liest. */
    Mittel[] lesenSchaum() { return new Mittel[] {werte, zF, zG, versatz, normale, aufg, kamm, driftF, driftG}; }

    /** Seit S5b-4: was die Synthese an einem Punkt liest (see_gemein.glsl, punktK), für die Fußsuche (Belegung wie {@link #belegungSchaum}). */
    Mittel[] lesenSynthese() { return new Mittel[] {werte, stuetz, huellen, eF, eG, pF, pG, zF, zG, kF, kG, fern, bg, aktiv}; }

    private static ByteBuffer push(double d, int stufe) { return push(d, stufe, 0); }

    /** stufe < 0: die Stufen erste, erste + 1, … nach z der Abgabe. */
    private static ByteBuffer push(double d, int stufe, int erste) {
        ByteBuffer p = ByteBuffer.allocateDirect(PUSH).order(ByteOrder.nativeOrder());
        p.putFloat(0, (float) d).putInt(4, stufe).putInt(8, erste);
        return p;
    }

    /**
     * Die Fläche an Punkten (absolute Orte wie die Stufen, je zwei Werte) für die Größe d der Stelle, zur Zeit von
     * {@link #zeit}. Zurück je Punkt 8 Werte: η, ξx, ξy, |Z'|, Re Z, Im Z, λ_eff, Faktor der Hülle.
     */
    public float[] punkte(double[] xy, double d) {
        int np = xy.length / 2;
        punkte = groesse(punkte, "see Punkte", 8L * np);
        aus = groesse(aus, "see Aus", 32L * np);
        float[] p = new float[2 * np];
        for (int i = 0; i < np; i++) { p[2 * i] = (float) (xy[2 * i] - bx); p[2 * i + 1] = (float) (xy[2 * i + 1] - by); }
        punkte.schreibe(p);
        werteSchreiben(np);
        Ablauf ab = new Ablauf(g);
        ab.rechne("see_punkte", kPunkte, satz(kPunkte), push(d, 0), Kernel.gruppen(np, GRUPPE), 1, 1,
                new Mittel[] {werte, stuetz, huellen, eF, eG, pF, pG, zF, zG, kF, kG, fern, bg, punkte}, new Mittel[] {aus});
        g.rechnen.einmal(c -> ab.aufnehmen(c, null));
        werteSchreiben(0);
        return aus.floats(8 * np);
    }

    /** Abstand der Stellen der Stufe L. */
    public double abstand(int l) { return d0 * Math.pow(2, l); }

    /** Mitte der Stufe L (absolut). */
    public double[] mitte(int l) { return new double[] {bx + mitten[2 * l], by + mitten[2 * l + 1]}; }

    /** Ort der Stelle (i, j) der Stufe L (absolut). */
    public double[] ort(int l, int i, int j) {
        double dl = abstand(l);
        return new double[] {bx + mitten[2 * l] + (i - n / 2) * dl, by + mitten[2 * l + 1] + (j - n / 2) * dl};
    }

    /**
     * Rechnet alle Stufen der Clipmap um das Auge (absolut), zur Zeit von {@link #zeit}: Verschiebung, dann Normale und
     * J. Mit {@code uhr} je Stufe gemessen („see_stufe L“, „see_normale“).
     */
    /** Die fernen Stufen (ab dieser) rechnet {@link #bild} nur jedes zweite Bild (SF23 A) [G]. */
    public int ferneAb = 5;
    private long bildNr;

    /**
     * Seit S7d (Seegang Werkbuch 89): das Kielwasser dieses Bilds (null: keins). Hat es Schiffe in Fahrt, nehmen die Stufen,
     * die eine Abgabe rechnet, gleich danach seine Höhe und seinen Schaum dazu ({@code see_kiel_dazu.comp}).
     */
    public volatile Kielwasserkarte kiel;
    private Kernel kKiel;

    private void kielDazu(Ablauf ab, int von, int bis) {
        Kielwasserkarte kw = kiel;
        if (kw == null || !kw.an() || bis <= von) return;
        if (kKiel == null) {
            Kernel.Bindung[] b = new Kernel.Bindung[23];
            Arrays.fill(b, Kernel.Bindung.PUFFER);
            kKiel = Kernel.lade(g, PFAD + "see_kiel_dazu.comp", PUSH, null, b);
        }
        int gx = (n + 7) / 8;
        ab.rechne("see_kiel_dazu", kKiel, kKiel.satz(belegungSchaum(kw.kacheln())), push(0, -1, von), gx, gx, bis - von,
                new Mittel[] {werte, kw.kacheln(), versatz, kamm}, new Mittel[] {versatz, kamm});
    }

    /** Das nächste {@link #bild} rechnet alle Stufen (danach weiter im Wechsel). */
    public void neuerLauf() { bildNr = 0; }

    /**
     * Ein Bild im Lauf (SF23 A): die Stufen unter {@link #ferneAb} jedes Bild, die fernen jedes zweite; ihre Mitten und
     * Puffer bleiben dazwischen stehen. {@link #stufen} rechnet alle (für Prüfungen).
     */
    public void bild(double ex, double ey, Stoppuhr uhr) {
        boolean alle = bildNr++ % 2 == 0;
        int vorBis = stufenBis;
        stufenBis = alle ? stufen : Math.min(stufen, ferneAb);
        try { stufen(ex, ey, uhr); } finally { stufenBis = vorBis; }
    }

    public void stufen(double ex, double ey, Stoppuhr uhr) {
        Ablauf ab = new Ablauf(g);
        stufen(ab, ex, ey);
        g.rechnen.einmal(c -> { if (uhr != null) uhr.runde(c); ab.aufnehmen(c, uhr); });
    }

    /** Wie {@link #bild(double, double, Stoppuhr)}, aufgenommen in einen fremden Ablauf (das Bild im Rahmen, S3c). */
    public void bild(Ablauf ab, double ex, double ey) {
        boolean alle = bildNr++ % 2 == 0;
        int vorBis = stufenBis;
        stufenBis = alle ? stufen : Math.min(stufen, ferneAb);
        try { stufen(ab, ex, ey); } finally { stufenBis = vorBis; }
    }

    /** Die Stufen um das Auge (ex, ey), aufgenommen in den Ablauf ab (er wird nicht abgeschickt). */
    public void stufen(Ablauf ab, double ex, double ey) {
        int lv = eineAbgabe ? Math.max(0, stufenVon) : 0, lb = eineAbgabe ? Math.min(stufen, stufenBis) : stufen;
        for (int l = lv; l < lb; l++) {                                      // Stufen, die nicht gerechnet werden, behalten ihre Mitte
            double dl = abstand(l), r = 2 * dl;
            mitten[2 * l] = Math.floor((ex - bx) / r) * r;
            mitten[2 * l + 1] = Math.floor((ey - by) / r) * r;
            ff[F_MITTE + 2 * l] = (float) mitten[2 * l]; ff[F_MITTE + 2 * l + 1] = (float) mitten[2 * l + 1];
        }
        werteAufnehmen(ab);
        int gx = (n + 7) / 8;
        Mittel[] lesen = {werte, stuetz, huellen, eF, eG, pF, pG, zF, zG, kF, kG, fern, bg, aktiv};
        if (eineAbgabe) {
            int von = Math.max(0, stufenVon), bis = Math.min(stufen, stufenBis);
            ab.rechne("see_stufen", kStufe, satz(kStufe), push(0, -1, von), gx, gx, bis - von, lesen, new Mittel[] {versatz, aufg, kamm});
            kielDazu(ab, von, bis);                                           // seit S7d
            ab.rechne("see_normalen", kNormale, satz(kNormale), push(0, -1, von), gx, gx, bis - von, new Mittel[] {werte, versatz}, new Mittel[] {normale});
        } else {
            for (int l = 0; l < stufen; l++)
                ab.rechne("see_stufe " + l, kStufe, satz(kStufe), push(abstand(l), l), gx, gx, 1, lesen, new Mittel[] {versatz, aufg, kamm});
            kielDazu(ab, 0, stufen);                                          // seit S7d
            for (int l = 0; l < stufen; l++)
                ab.rechne("see_normale " + l, kNormale, satz(kNormale), push(abstand(l), l), gx, gx, 1, new Mittel[] {werte, versatz}, new Mittel[] {normale});
        }
    }

    /**
     * Breite des Übergangs ins Fernfeld am Rand der äußersten Stufe (Stellen) [G, S3c]: Dort geht die Synthese in die
     * Wellen des Fernfelds über, die der Weg jenseits der Clipmap zeichnet (derselbe Satz, dieselbe Phase). 0: kein Übergang.
     */
    public int randFern = 16;

    /** Anteil von fein und grob an der Stelle (i, j) der Stufe L (wie see_stufe.comp; 1 außer am Rand der äußersten Stufe). */
    public double nah(int l, int i, int j) {
        int rand = o.fernfeld != null ? randFern : 0;
        if (l != stufen - 1 || rand <= 0) return 1;
        return Math.max(0, Math.min(1, (double) Math.min(Math.min(i, j), Math.min(n - 1 - i, n - 1 - j)) / rand));
    }

    /** Verschiebung der Stufe L: je Stelle (ξx, ξy, η, |Z'|), Zeile j, Spalte i. */
    public float[] verschiebung(int l) {
        float[] all = versatz.floats(4 * stufen * n * n);
        return Arrays.copyOfRange(all, 4 * l * n * n, 4 * (l + 1) * n * n);
    }

    /** Normale und J der Stufe L: je Stelle (n_x, n_y, n_z, J). */
    public float[] normale(int l) {
        float[] all = normale.floats(4 * stufen * n * n);
        return Arrays.copyOfRange(all, 4 * l * n * n, 4 * (l + 1) * n * n);
    }

    /** Aufgelöste Neigungsvarianz Σ k² E g² und Varianz der Höhe Σ E g² der Stufe L je Stelle (zwei Werte; S3c). */
    public float[] aufgeloest(int l) {
        float[] all = aufg.floats(2 * stufen * n * n);
        return Arrays.copyOfRange(all, 2 * l * n * n, 2 * (l + 1) * n * n);
    }

    /** Die Puffer der Clipmap (für das Netz in S3c). */
    public Puffer versatzPuffer() { return versatz; }

    public Puffer aufgeloestPuffer() { return aufg; }

    public Puffer normalePuffer() { return normale; }

    /** Speicher auf der Karte (Byte). */
    public long bytes() {
        long s = werte.groesse + stuetz.groesse + huellen.groesse + fern.groesse + bg.groesse + versatz.groesse + normale.groesse + aufg.groesse + kamm.groesse;
        for (Puffer p : new Puffer[] {eF, eG, pF, pG, zF, zG, kF, kG, punkte, aus, aktiv, driftF, driftG}) if (p != null) s += p.groesse;
        return s;
    }

    @Override public void close() {
        for (Kernel k : new Kernel[] {kPunkte, kStufe, kNormale}) k.close();
        if (kKiel != null) kKiel.close();
        for (Puffer p : new Puffer[] {werte, stuetz, huellen, fern, bg, versatz, normale, aufg, kamm, eF, eG, pF, pG, zF, zG, kF, kG, punkte, aus, aktiv, driftF, driftG}) if (p != null) p.close();
        if (eigenesGeraet) g.close();
    }
}
